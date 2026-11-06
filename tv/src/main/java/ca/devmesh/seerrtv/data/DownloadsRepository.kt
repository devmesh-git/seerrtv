package ca.devmesh.seerrtv.data

import ca.devmesh.seerrtv.model.DownloadRequest
import ca.devmesh.seerrtv.model.DownloadRequestPage
import ca.devmesh.seerrtv.model.DownloadsResponse
import ca.devmesh.seerrtv.model.QueueDownload
import java.security.MessageDigest

/**
 * Reads only processing requests visible to the authenticated user. Never shares carousel
 * pagination.
 */
internal suspend fun loadDownloads(
    page: suspend (skip: Int, take: Int) -> ApiResult<DownloadRequestPage>
): ApiResult<DownloadsResponse> {
    val requests = mutableListOf<DownloadRequest>()
    var skip = 0
    val take = 100
    // Bound work even if a server returns broken pagination or its dataset keeps changing.
    repeat(20) {
        when (val result = page(skip, take)) {
            is ApiResult.Success -> {
                requests.addAll(result.data.results)
                skip += result.data.results.size
                if (result.data.results.size < take || skip >= result.data.pageInfo.results) {
                    return ApiResult.Success(DownloadsResponse(mergeDownloads(requests)))
                }
            }
            is ApiResult.Error -> return result
            ApiResult.Loading ->
                return ApiResult.Error(IllegalStateException("Download request incomplete"))
        }
    }
    return ApiResult.Error(IllegalStateException("Too many processing requests"))
}

internal fun mergeDownloads(requests: List<DownloadRequest>): List<QueueDownload> {
    val downloads = linkedMapOf<String, QueueDownload>()
    for (request in requests) {
        if (request.type !in setOf("movie", "tv")) continue
        val media = request.media
        val service = if (request.is4k) media.serviceId4k else media.serviceId
        val queue = if (request.is4k) media.downloadStatus4k else media.downloadStatus
        for ((index, progress) in queue.withIndex()) {
            val episode = progress.episode
            // A series queue may contain seasons not requested by this user. Unknown scope is
            // omitted.
            if (
                request.type == "tv" &&
                    (episode == null ||
                        request.seasons.none { it.seasonNumber == episode.seasonNumber })
            )
                continue
            val identity =
                progress.downloadId?.takeIf { it.isNotBlank() }
                    ?: "queue:$index:${episode?.seasonNumber}:${episode?.episodeNumber}"
            val key = "${request.type}:${media.id}:$service:${request.is4k}:$identity"
            val previous = downloads[key]
            val requester = request.requestedBy?.let { it.displayName ?: it.username }
            downloads[key] =
                QueueDownload(
                    // Stable UI key; raw server download identifiers never appear in UI or logs.
                    id =
                        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(
                            ""
                        ) {
                            "%02x".format(it)
                        },
                    mediaId = media.id,
                    tmdbId = media.tmdbId,
                    mediaType = request.type,
                    is4k = request.is4k,
                    episodes =
                        (previous?.episodes.orEmpty() + listOfNotNull(episode))
                            .distinct()
                            .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })),
                    requestedBy =
                        (previous?.requestedBy.orEmpty() + listOfNotNull(requester)).distinct(),
                    download = previous?.download ?: progress.copy(downloadId = null),
                )
        }
    }
    return downloads.values.toList()
}
