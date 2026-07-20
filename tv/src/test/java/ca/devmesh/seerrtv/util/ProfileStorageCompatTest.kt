package ca.devmesh.seerrtv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards upgrade safety for saved profiles.
 *
 * Two things must hold for an existing install to survive an upgrade:
 *
 * 1. **A profiles blob written by an older build still decodes**, with every setting intact.
 *    `UserProfile` and its embedded `SeerrConfig` carry required fields with no defaults
 *    (`name`, `avatarInitials`, `avatarColor`, `config`; `protocol`, `hostname`, `authType`,
 *    `isSubmitted`, `createdAt`), so adding another one — or renaming a `@SerialName` — silently
 *    breaks every stored profile. The fixture below is the real on-disk shape written by 0.28.10.
 *
 * 2. **An unreadable blob is never mistaken for an empty one.** `ensureProfilesInitialized`
 *    deletes the stored JSON when it finds no profiles; doing that to a blob that merely failed
 *    to parse would turn a recoverable problem into permanent loss of every profile and setting.
 *    [SharedPreferencesUtil.decodeProfiles] returns null for unreadable input and an empty list
 *    only for a genuinely empty one, which is what keeps those two cases apart.
 */
class ProfileStorageCompatTest {

    /** Exact field shape written by 0.28.10, with credentials replaced by placeholders. */
    private val legacyProfilesJson = """
        [{"id":"e5774b30-9341-4912-aac4-25c2de72170c","name":"moem87","email":null,
        "avatar_initials":"MO","avatar_color":"PURPLE",
        "remote_avatar_url":"https://plex.tv/users/abc/avatar?c=1",
        "pin_hash":"","config":{"protocol":"http","hostname":"seerr.example.com",
        "cloudflare_enabled":false,"cf_client_id":"","cf_client_secret":"","auth_type":"plex",
        "api_key":"","username":"","password":"","jellyfin_hostname":"","jellyfin_port":8096,
        "jellyfin_use_ssl":false,"jellyfin_url_base":"/","jellyfin_email":"",
        "plex_client_id":"f637851f-b1d5-44ae-a6fc-21c03b0f60f3","plex_auth_token":"token",
        "is_submitted":true,"created_at":"1783155866983"},
        "settings":{"app_language":"es","discovery_language":"es",
        "default_streaming_region":"CA","folder_selection_enabled":true,
        "use_24_hour_clock":false,"use_trailer_webview":true},
        "created_at":1783155866984,"updated_at":1783157562750}]
    """.trimIndent().replace("\n", "")

    @Test
    fun `a profiles blob written by 0-28-10 still decodes with every setting intact`() {
        val profiles = SharedPreferencesUtil.decodeProfiles(legacyProfilesJson)
        assertNotNull("0.28.10 profiles JSON no longer decodes", profiles)
        assertEquals(1, profiles!!.size)

        val profile = profiles.first()
        assertEquals("e5774b30-9341-4912-aac4-25c2de72170c", profile.id)
        assertEquals("moem87", profile.name)
        assertEquals("MO", profile.avatarInitials)
        assertEquals("PURPLE", profile.avatarColor)
        assertEquals("https://plex.tv/users/abc/avatar?c=1", profile.remoteAvatarUrl)

        // Every non-default value must survive; defaults would silently mask a mapping break.
        assertEquals("es", profile.settings.appLanguage)
        assertEquals("es", profile.settings.discoveryLanguage)
        assertEquals("CA", profile.settings.defaultStreamingRegion)
        assertEquals(true, profile.settings.folderSelectionEnabled)
        assertEquals(false, profile.settings.use24HourClock)
        assertEquals(true, profile.settings.useTrailerWebView)

        assertEquals("seerr.example.com", profile.config.hostname)
        assertEquals("plex", profile.config.authType)
        assertEquals(true, profile.config.isSubmitted)
    }

    @Test
    fun `unknown fields from a newer build are ignored rather than failing the decode`() {
        // Forward compatibility: a downgrade must not wipe profiles written by a newer version.
        val withFutureField = legacyProfilesJson.replace(
            "\"pin_hash\":\"\"",
            "\"pin_hash\":\"\",\"some_future_field\":\"whatever\""
        )
        assertNotEquals("fixture rewrite did not apply", legacyProfilesJson, withFutureField)
        val profiles = SharedPreferencesUtil.decodeProfiles(withFutureField)
        assertNotNull("an unknown field must not break decoding", profiles)
        assertEquals("es", profiles!!.first().settings.appLanguage)
    }

    @Test
    fun `a genuinely empty list decodes to an empty list, not null`() {
        // This is the only case ensureProfilesInitialized may clear the stored JSON for.
        assertEquals(emptyList<Any>(), SharedPreferencesUtil.decodeProfiles("[]"))
    }

    @Test
    fun `unreadable blobs decode to null so they are never mistaken for empty`() {
        // Each of these would previously have surfaced as "no profiles" and had the stored JSON
        // deleted, permanently destroying every profile and its settings.
        assertNull("truncated/partial write", SharedPreferencesUtil.decodeProfiles("""[{"id":"a","""))
        assertNull("not JSON at all", SharedPreferencesUtil.decodeProfiles("not json"))
        assertNull("empty string", SharedPreferencesUtil.decodeProfiles(""))
        assertNull(
            "missing a required field with no default (the shape a future field change takes)",
            SharedPreferencesUtil.decodeProfiles("""[{"id":"a","name":"x"}]""")
        )
    }
}
