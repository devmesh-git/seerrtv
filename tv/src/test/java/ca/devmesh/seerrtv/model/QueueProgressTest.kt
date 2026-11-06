package ca.devmesh.seerrtv.model

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class QueueProgressTest {
    private val now = Instant.parse("2026-11-06T18:00:00Z").toEpochMilli()

    @Test
    fun progressIsFiniteAndClamped() {
        assertEquals(0.0, QueueProgress().percent(), 0.001)
        assertEquals(0.0, QueueProgress(progress = Double.NaN).percent(), 0.001)
        assertEquals(100.0, QueueProgress(size = 100.0, sizeLeft = -10.0).percent(), 0.001)
        assertEquals(67.4, QueueProgress(progress = 67.4).percent(), 0.001)
    }

    @Test
    fun staleClientRatesAreNotPresentedAsLive() {
        val progress =
            QueueProgress(
                status = "downloading",
                liveStatus = "paused",
                liveUpdatedAt = "2026-11-06T18:00:00Z",
                downloadSpeed = 12_500_000.0,
            )
        assertEquals("paused", progress.state(now))
        assertTrue(progress.needsAttention(now))
        assertEquals(12_500_000.0, progress.speed(now)!!, 0.001)
        assertEquals("downloading", progress.state(now + 16_000))
        assertNull(progress.speed(now + 16_000))
    }

    @Test
    fun unknownAndPausedEtaAreNotInvented() {
        assertNull(
            QueueProgress(status = "paused", estimatedCompletionTime = "2026-11-06T18:10:00Z")
                .remainingSeconds(now)
        )
        assertNull(
            QueueProgress(liveUpdatedAt = "2026-11-06T18:00:00Z", etaSeconds = 8_640_000.0)
                .remainingSeconds(now)
        )
        assertEquals(
            478L,
            QueueProgress(
                    status = "downloading",
                    liveUpdatedAt = "2026-11-06T18:00:00Z",
                    etaSeconds = 480.0,
                )
                .remainingSeconds(now + 2000),
        )
    }

    @Test
    fun failedImportTakesPriorityOverClientCompletion() {
        val progress = QueueProgress(status = "completed", trackedDownloadState = "importFailed")
        assertEquals("failed", progress.state(now))
        assertTrue(progress.needsAttention(now))
    }

    @Test
    fun completedQueueEntryHasFullProgressWhileAwaitingImport() {
        val progress = QueueProgress(size = 100.0, sizeLeft = 0.0, status = "completed")
        assertEquals(100.0, progress.percent(), 0.001)
        assertEquals("completed", progress.state(now))
        assertFalse(progress.needsAttention(now))
        assertNull(progress.remainingSeconds(now))
        assertNull(progress.speed(now))
    }
}
