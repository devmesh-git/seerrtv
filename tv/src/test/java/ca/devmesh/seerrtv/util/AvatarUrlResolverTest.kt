package ca.devmesh.seerrtv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards avatar URL resolution.
 *
 * Why this matters: the resolved string is handed straight to Coil, which loads it on OkHttp's
 * async dispatcher. OkHttp rethrows non-IOException throwables out of `AsyncCall.run`, so an
 * unparseable/hostless URL surfaces as an uncaught `IllegalArgumentException` from
 * `HttpUrl.Builder.host` and takes the whole process down — Coil cannot catch it. Anything that
 * isn't a valid absolute http(s) URL must therefore resolve to null (no avatar) rather than be
 * passed through.
 */
class AvatarUrlResolverTest {

    private val origin = "https://seerr.example.com"

    @Test
    fun `absolute https url passes through`() {
        assertEquals(
            "https://plex.tv/users/abc/avatar",
            AvatarUrlResolver.resolve("https://plex.tv/users/abc/avatar", origin)
        )
    }

    @Test
    fun `root relative path is joined to the origin`() {
        assertEquals(
            "https://seerr.example.com/avatarproxy/abc",
            AvatarUrlResolver.resolve("/avatarproxy/abc", origin)
        )
    }

    @Test
    fun `bare relative path is joined to the origin`() {
        assertEquals(
            "https://seerr.example.com/avatarproxy/abc",
            AvatarUrlResolver.resolve("avatarproxy/abc", origin)
        )
    }

    @Test
    fun `protocol relative url gets https`() {
        assertEquals(
            "https://cdn.example.com/a.png",
            AvatarUrlResolver.resolve("//cdn.example.com/a.png", origin)
        )
    }

    @Test
    fun `null and blank avatars resolve to null`() {
        assertNull(AvatarUrlResolver.resolve(null, origin))
        assertNull(AvatarUrlResolver.resolve("", origin))
        assertNull(AvatarUrlResolver.resolve("   ", origin))
    }

    // --- The crash-relevant cases ---

    /**
     * When the configured hostname is blank, getSeerrOrigin() returns "http://".
     * `"http://".trimEnd('/')` strips BOTH slashes, leaving "http:", which previously produced
     * hostless URLs like "http:/avatarproxy/abc" and fed them to OkHttp.
     */
    @Test
    fun `hostless origin resolves to null instead of a malformed url`() {
        assertNull(AvatarUrlResolver.resolve("/avatarproxy/abc", "http://"))
        assertNull(AvatarUrlResolver.resolve("avatarproxy/abc", "http://"))
        assertNull(AvatarUrlResolver.resolve("/avatarproxy/abc", ""))
    }

    @Test
    fun `absolute url with no host resolves to null`() {
        assertNull(AvatarUrlResolver.resolve("http://", origin))
        assertNull(AvatarUrlResolver.resolve("https:///avatarproxy/abc", origin))
    }

    @Test
    fun `absolute url with an invalid host resolves to null`() {
        assertNull(AvatarUrlResolver.resolve("http://exa mple.com/a.png", origin))
    }

    @Test
    fun `non http schemes resolve to null`() {
        assertNull(AvatarUrlResolver.resolve("ftp://example.com/a.png", origin))
        assertNull(AvatarUrlResolver.resolve("javascript:alert(1)", origin))
    }
}
