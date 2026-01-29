package ca.devmesh.seerrtv.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the direct-flavor update flow version comparison.
 * Verifies that an upgrade is offered when GitHub release is newer than the installed version,
 * and not offered when installed version is same or newer (e.g. local dev build 0.26.7 vs GitHub 0.26.6).
 */
class UpdateManagerVersionTest {

    @Test
    fun whenLatestGreaterThanCurrent_thenUpdateIsAvailable() {
        // GitHub has 0.26.6, user has 0.26.5 → should offer update
        assert(compareVersions("0.26.6", "0.26.5") > 0)
        assert(compareVersions("0.27.0", "0.26.6") > 0)
        assert(compareVersions("1.0.0", "0.26.6") > 0)
    }

    @Test
    fun whenCurrentGreaterThanOrEqualLatest_thenNoUpdate() {
        // User has 0.26.7, GitHub has 0.26.6 → should NOT offer update (matches logs)
        assertEquals(-1, compareVersions("0.26.6", "0.26.7"))
        assert(compareVersions("0.26.6", "0.26.7") <= 0)
        // Same version
        assertEquals(0, compareVersions("0.26.6", "0.26.6"))
        assert(compareVersions("0.26.6", "0.26.6") <= 0)
    }

    @Test
    fun semanticComparison_majorMinorPatch() {
        assertEquals(1, compareVersions("1.0.0", "0.9.9"))
        assertEquals(-1, compareVersions("0.9.9", "1.0.0"))
        assertEquals(0, compareVersions("0.26.6", "0.26.6"))
        assertEquals(1, compareVersions("0.26.7", "0.26.6"))
        assertEquals(-1, compareVersions("0.26.5", "0.26.6"))
    }

    @Test
    fun hasUpdateLogic_matchesUpdateManager() {
        // hasUpdate = compareVersions(latestVersionName, currentVersionName) > 0
        val latest = "0.26.6"
        val currentNewer = "0.26.7"
        val currentOlder = "0.26.5"
        assertEquals("Newer local build should not get update", false, compareVersions(latest, currentNewer) > 0)
        assertEquals("Older local build should get update", true, compareVersions(latest, currentOlder) > 0)
    }
}
