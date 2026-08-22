package ca.devmesh.seerrtv.util

import ca.devmesh.seerrtv.data.SeerrApiService.SeerrConfig
import ca.devmesh.seerrtv.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards which profile an `auth/me` identity (display name, initials, remote avatar) is written to.
 *
 * Why this matters: adding a second profile authenticates *before* the new profile exists.
 * `ConfigViewModel.validateAndSaveConfig` calls `testAuth()` first — which persists the new
 * account's identity — and only then calls `appendNewProfileWithValidatedConfig`, which creates
 * the profile and makes it active. While the sync targeted the raw active-profile id, that window
 * stamped the *previous* profile with the new account's name and avatar. The new profile was then
 * named from the same freshly-saved display name, so the picker showed two identically-labelled,
 * identically-avatared profiles with no way to tell them apart — the credentials were intact, but
 * the user could not tell which entry was which.
 *
 * The pending-creation case below is the one that actually matters; the rest pin the fallback
 * behaviour shared with [SharedPreferencesUtil.resolveSettingsTargetProfileId].
 */
class IdentitySyncTargetProfileTest {

    private fun profile(id: String, name: String) = UserProfile(
        id = id,
        name = name,
        avatarInitials = name.take(2).uppercase(),
        avatarColor = "PURPLE",
        config = SeerrConfig(
            protocol = "http",
            hostname = "seerr.example.com",
            authType = "local",
            isSubmitted = true,
            createdAt = "0"
        )
    )

    private val existing = profile("id-existing", "test user")
    private val second = profile("id-second", "admin")
    private val profiles = listOf(existing, second)

    @Test
    fun `no identity is written while a new profile is being created`() {
        assertNull(
            SharedPreferencesUtil.resolveIdentitySyncTargetProfileId(
                profiles = profiles,
                activeId = existing.id,
                pendingNewProfileCreation = true
            )
        )
    }

    @Test
    fun `pending creation is respected even when the active id is stale`() {
        assertNull(
            SharedPreferencesUtil.resolveIdentitySyncTargetProfileId(
                profiles = profiles,
                activeId = "id-that-no-longer-exists",
                pendingNewProfileCreation = true
            )
        )
    }

    @Test
    fun `outside creation the active profile receives the identity`() {
        assertEquals(
            second.id,
            SharedPreferencesUtil.resolveIdentitySyncTargetProfileId(
                profiles = profiles,
                activeId = second.id,
                pendingNewProfileCreation = false
            )
        )
    }

    @Test
    fun `a stale active id falls back to the first profile rather than skipping the sync`() {
        assertEquals(
            existing.id,
            SharedPreferencesUtil.resolveIdentitySyncTargetProfileId(
                profiles = profiles,
                activeId = "id-that-no-longer-exists",
                pendingNewProfileCreation = false
            )
        )
    }

    @Test
    fun `a missing active id falls back to the first profile`() {
        assertEquals(
            existing.id,
            SharedPreferencesUtil.resolveIdentitySyncTargetProfileId(
                profiles = profiles,
                activeId = null,
                pendingNewProfileCreation = false
            )
        )
    }

    @Test
    fun `no profiles means nothing to sync`() {
        assertNull(
            SharedPreferencesUtil.resolveIdentitySyncTargetProfileId(
                profiles = emptyList(),
                activeId = null,
                pendingNewProfileCreation = false
            )
        )
    }
}
