package ca.devmesh.seerrtv.util

import ca.devmesh.seerrtv.data.SeerrApiService.SeerrConfig
import ca.devmesh.seerrtv.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the rule that decides which profile a settings write lands on.
 *
 * Why this matters: every profile-scoped setting (app language, discovery language, streaming
 * region, folder selection, clock format, trailer player) is read through `getActiveProfile`,
 * which falls back to the first profile when `active_profile_id` is missing or names a profile
 * that no longer exists. Writes must resolve the target the same way. When they instead required
 * an exact id match, a write in that fallback state fell through to the legacy global key that
 * the getters ignore whenever any profile exists — the setting looked saved, every read returned
 * the old value, and the change only appeared after the next cold start migrated the stale global
 * key into the first profile. That is the "changing App Language does nothing until you restart"
 * shape, so the fallback cases below are the ones that actually matter.
 */
class SettingsTargetProfileTest {

    private fun profile(id: String, name: String) = UserProfile(
        id = id,
        name = name,
        avatarInitials = name.take(2).uppercase(),
        avatarColor = "PURPLE",
        config = SeerrConfig(
            protocol = "http",
            hostname = "seerr.example.com",
            authType = "plex",
            isSubmitted = true,
            createdAt = "0"
        )
    )

    private val first = profile("id-first", "first")
    private val second = profile("id-second", "second")
    private val profiles = listOf(first, second)

    @Test
    fun `resolves to the active profile when the stored id matches`() {
        assertEquals(
            "id-second",
            SharedPreferencesUtil.resolveSettingsTargetProfileId(profiles, "id-second")
        )
    }

    @Test
    fun `falls back to the first profile when the stored id is stale`() {
        // active_profile_id survives a profile deletion, or was written by an older build.
        assertEquals(
            "id-first",
            SharedPreferencesUtil.resolveSettingsTargetProfileId(profiles, "id-deleted")
        )
    }

    @Test
    fun `falls back to the first profile when no active id is stored`() {
        assertEquals(
            "id-first",
            SharedPreferencesUtil.resolveSettingsTargetProfileId(profiles, null)
        )
    }

    @Test
    fun `falls back to the first profile when the active id is blank`() {
        assertEquals(
            "id-first",
            SharedPreferencesUtil.resolveSettingsTargetProfileId(profiles, "")
        )
    }

    @Test
    fun `returns null when there are no profiles`() {
        // The only case where the legacy global setting keys are still the right destination.
        assertNull(SharedPreferencesUtil.resolveSettingsTargetProfileId(emptyList(), "id-first"))
        assertNull(SharedPreferencesUtil.resolveSettingsTargetProfileId(emptyList(), null))
    }

    @Test
    fun `the pre-fix predicate diverged from reads on exactly the fallback cases`() {
        // Pins the defect this change closes. Every setter used to gate on this predicate, so a
        // false here meant the write went to the legacy global key while the read kept resolving
        // to a profile. Reproduced rather than assumed: the two stale/absent cases disagree.
        fun legacyWroteToProfile(activeId: String?) =
            !activeId.isNullOrBlank() && profiles.any { it.id == activeId }

        assertEquals(true, legacyWroteToProfile("id-second"))
        assertEquals(false, legacyWroteToProfile("id-deleted"))
        assertEquals(false, legacyWroteToProfile(null))
        assertEquals(false, legacyWroteToProfile(""))

        // The reads never had that gap — they resolved to a profile in all four states.
        listOf("id-second", "id-deleted", null, "").forEach { activeId ->
            assertEquals(
                "read resolved to no profile for active id: $activeId",
                true,
                profiles.isNotEmpty()
            )
            assertNotNull(SharedPreferencesUtil.resolveSettingsTargetProfileId(profiles, activeId))
        }
    }

    @Test
    fun `write target always matches the profile a read would resolve to`() {
        // The invariant the setters and getters have to share, across every id state.
        listOf("id-second", "id-deleted", "", null).forEach { activeId ->
            val readProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
            assertEquals(
                "read/write disagreement for active id: $activeId",
                readProfile.id,
                SharedPreferencesUtil.resolveSettingsTargetProfileId(profiles, activeId)
            )
        }
    }
}
