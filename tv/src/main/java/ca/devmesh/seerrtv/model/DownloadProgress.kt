package ca.devmesh.seerrtv.model

/**
 * Overall progress, 0..1, of everything downloading for a title in either tier, or null when
 * nothing is. Seerr fills [MediaInfo.downloadStatus] from its download-sync job, so the same
 * data reaches every list that carries a mediaInfo (requests, discover, search).
 */
fun MediaInfo.activeDownloadProgress(): Float? =
    combinedDownloadProgress(downloadStatus, downloadStatus4k)

/** True when either tier has an active download. */
fun MediaInfo.hasActiveDownload(): Boolean =
    downloadStatus.isNotEmpty() || downloadStatus4k.isNotEmpty()

internal fun combinedDownloadProgress(vararg tiers: List<DownloadStatus>): Float? {
    // A season pack appears once per episode with one shared downloadId: count its bytes once.
    // Entries without an ID can't be matched, so each counts on its own.
    val downloads = tiers.flatMap { tier ->
        tier.withIndex()
            .distinctBy { (index, item) -> item.downloadId?.takeIf { it.isNotBlank() } ?: "#$index" }
            .map { it.value }
    }
    if (downloads.isEmpty()) return null
    var total = 0L
    var left = 0L
    for (item in downloads) {
        val size = item.size.coerceAtLeast(0L)
        total += size
        // No sizeLeft means the client hasn't reported progress yet, not that it finished.
        left += (item.sizeLeft ?: size).coerceIn(0L, size)
    }
    if (total <= 0L) return 0f
    return ((total - left).toDouble() / total).toFloat().coerceIn(0f, 1f)
}
