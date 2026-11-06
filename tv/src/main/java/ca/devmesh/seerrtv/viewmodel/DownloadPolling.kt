package ca.devmesh.seerrtv.viewmodel

/**
 * One cadence for every place that shows download progress: the home Requests row, the media
 * details screen and the request modal. Seerr refreshes its download data once a minute (its
 * download-sync job), so a faster poll would only repeat the same numbers; 15 s means a change
 * reaches the screen at most 15 s after Seerr has it.
 */
internal object DownloadPolling {
    /** Something is downloading. */
    const val ACTIVE_MS = 15_000L

    /** Requested/approved but nothing downloading yet: it may start any minute. */
    const val WAITING_MS = 30_000L
}
