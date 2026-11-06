package ca.devmesh.seerrtv.viewmodel

import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.model.MediaInfo
import ca.devmesh.seerrtv.model.Request
import ca.devmesh.seerrtv.model.hasActiveDownload

/**
 * Download progress for the home Requests row. The row is loaded once; while something in it is
 * downloading (or waiting to start), the home screen re-reads Seerr's processing requests and
 * patches progress into the cards already on screen — same items, same order, so focus and
 * scroll position never move under the user.
 */
internal object RequestDownloads {
    const val ACTIVE_POLL_MS = DownloadPolling.ACTIVE_MS
    const val WAITING_POLL_MS = DownloadPolling.WAITING_MS

    private const val REQUEST_APPROVED = 2
    private const val REQUEST_COMPLETED = 5
    private const val MEDIA_AVAILABLE = 5

    /** Approved and not yet fully available: what Seerr's `filter=processing` returns. */
    private fun Media.isAwaitingAvailability() =
        request?.status == REQUEST_APPROVED && mediaInfo?.status != MEDIA_AVAILABLE

    private fun key(info: MediaInfo) = "${info.mediaType}:${info.tmdbId}"

    /** Downloading titles first, everything else keeps the server's order. */
    fun downloadingFirst(items: List<Media>): List<Media> =
        items.sortedByDescending { it.mediaInfo?.hasActiveDownload() == true }

    // The row always polls while home is on screen: it lists every request, and new ones can
    // be made from the web app or another device at any time.

    /** How often to poll for the row as loaded, before the first poll has answered. */
    fun pollIntervalForRow(items: List<Media>): Long =
        if (items.any { it.mediaInfo?.hasActiveDownload() == true }) ACTIVE_POLL_MS else WAITING_POLL_MS

    /** How often to poll after a poll, from the server's own list of processing requests. */
    fun pollIntervalForProcessing(processing: List<Request>): Long =
        if (processing.any { it.media.hasActiveDownload() }) ACTIVE_POLL_MS else WAITING_POLL_MS

    data class Patch(
        val items: List<Media>,
        val changed: Boolean,
        /** Titles that were downloading but left the processing list: imported or removed. */
        val finished: List<MediaInfo>,
        /** Downloading titles the row doesn't hold yet, e.g. requested from the web since. */
        val newDownloads: Set<String> = emptySet()
    )

    fun patch(items: List<Media>, processing: List<Request>): Patch {
        val latest = processing.associate { key(it.media) to it.media }
        val finished = mutableListOf<MediaInfo>()
        var changed = false
        val patched = items.map { item ->
            val info = item.mediaInfo ?: return@map item
            val fresh = latest[key(info)]
            val next = when {
                fresh != null -> info.copy(
                    downloadStatus = fresh.downloadStatus,
                    downloadStatus4k = fresh.downloadStatus4k,
                    status = fresh.status,
                    status4k = fresh.status4k
                )
                // Left the processing list: imported, made available, or removed. The download
                // usually ends a minute or more before Seerr marks the title available, so a
                // title still counts while its request is approved, not only while downloading.
                info.hasActiveDownload() || item.isAwaitingAvailability() -> {
                    finished += info
                    info.copy(downloadStatus = emptyList(), downloadStatus4k = emptyList())
                }
                else -> info
            }
            when {
                next != info || (fresh == null && item.isAwaitingAvailability()) -> {
                    changed = true
                    item.withMediaInfo(next).let { patchedItem ->
                        // No longer in Seerr's approved-and-unavailable list: stop treating it as
                        // waiting, so its availability is fetched once rather than every poll.
                        if (fresh == null) patchedItem.copy(
                            request = patchedItem.request?.copy(status = REQUEST_COMPLETED)
                        ) else patchedItem
                    }
                }
                else -> item
            }
        }
        val held = items.mapNotNull { it.mediaInfo?.let(::key) }.toSet()
        val newDownloads = processing
            .filter { it.media.hasActiveDownload() }
            .map { key(it.media) }
            .filterNot { it in held }
            .toSet()
        return Patch(patched, changed, finished, newDownloads)
    }

    /** Applies a title's fresh availability once its download has gone through. */
    fun applyStatus(items: List<Media>, fresh: MediaInfo): List<Media> = items.map { item ->
        val info = item.mediaInfo
        if (info == null || key(info) != key(fresh)) item
        else item.withMediaInfo(info.copy(status = fresh.status, status4k = fresh.status4k))
    }

    private fun Media.withMediaInfo(info: MediaInfo) = copy(
        mediaInfo = info,
        status = info.status,
        status4k = info.status4k,
        // The request modal opens from this request, so its copy of the media must move too.
        request = request?.copy(
            media = request.media.copy(
                downloadStatus = info.downloadStatus,
                downloadStatus4k = info.downloadStatus4k,
                status = info.status,
                status4k = info.status4k
            )
        )
    )
}
