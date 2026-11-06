package ca.devmesh.seerrtv.model

import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable data class DownloadsResponse(val results: List<QueueDownload> = emptyList())

@Serializable
data class QueueDownload(
    val id: String,
    val mediaId: Int = 0,
    val tmdbId: Int,
    val mediaType: String,
    val is4k: Boolean = false,
    val episodes: List<QueueEpisode> = emptyList(),
    val requestedBy: List<String> = emptyList(),
    val download: QueueProgress,
)

@Serializable data class QueueEpisode(val seasonNumber: Int, val episodeNumber: Int)

@Serializable
data class QueueProgress(
    val downloadId: String? = null,
    val episode: QueueEpisode? = null,
    val size: Double = 0.0,
    val sizeLeft: Double? = null,
    val progress: Double? = null,
    val status: String = "unknown",
    val liveStatus: String? = null,
    val downloadSpeed: Double? = null,
    val etaSeconds: Double? = null,
    val liveUpdatedAt: String? = null,
    val estimatedCompletionTime: String? = null,
    val trackedDownloadStatus: String? = null,
    val trackedDownloadState: String? = null,
) {
    fun percent(): Double =
        (progress ?: if (size > 0 && sizeLeft != null) (1 - sizeLeft / size) * 100 else 0.0)
            .takeIf { it.isFinite() }
            ?.coerceIn(0.0, 100.0) ?: 0.0

    fun isLive(now: Long): Boolean =
        liveUpdatedAt?.let {
            runCatching { now - Instant.parse(it).toEpochMilli() in 0..15_000 }.getOrDefault(false)
        } ?: false

    fun state(now: Long): String =
        when {
            trackedDownloadStatus == "error" || trackedDownloadState == "importFailed" -> "failed"
            isLive(now) -> liveStatus ?: status
            else -> status
        }.lowercase()

    fun needsAttention(now: Long): Boolean =
        state(now) in setOf("paused", "stalled", "failed", "warning", "error")

    fun speed(now: Long): Double? = downloadSpeed?.takeIf {
        isLive(now) && it.isFinite() && it >= 0
    }

    fun remainingSeconds(now: Long): Long? {
        if (state(now) in setOf("paused", "stalled", "failed", "completed", "queued")) return null
        if (isLive(now)) {
            val seconds =
                etaSeconds?.takeIf { it.isFinite() && it >= 0 && it < 8_640_000 } ?: return null
            val elapsed = runCatching {
                (now - Instant.parse(liveUpdatedAt).toEpochMilli()) / 1000
            }.getOrDefault(0)
            return (seconds.toLong() - elapsed).coerceAtLeast(0)
        }
        return estimatedCompletionTime
            ?.let {
                runCatching { (Instant.parse(it).toEpochMilli() - now) / 1000 }.getOrNull()
            }
            ?.takeIf { it in 0..8_639_999 }
    }
}

// Minimal request DTOs keep download polling independent from the home carousel pagination.
@Serializable
data class DownloadRequestPage(
    val results: List<DownloadRequest> = emptyList(),
    val pageInfo: DownloadPageInfo = DownloadPageInfo(),
)

@Serializable data class DownloadPageInfo(val results: Int = 0)

@Serializable
data class DownloadRequest(
    val id: Int,
    val type: String,
    val is4k: Boolean = false,
    val media: DownloadMedia,
    val seasons: List<DownloadSeason> = emptyList(),
    val requestedBy: DownloadRequester? = null,
)

@Serializable data class DownloadSeason(val seasonNumber: Int)

@Serializable
data class DownloadRequester(val displayName: String? = null, val username: String? = null)

@Serializable
data class DownloadMedia(
    val id: Int,
    val tmdbId: Int,
    val serviceId: Int? = null,
    val serviceId4k: Int? = null,
    val downloadStatus: List<QueueProgress> = emptyList(),
    val downloadStatus4k: List<QueueProgress> = emptyList(),
)
