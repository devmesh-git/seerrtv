package ca.devmesh.seerrtv.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Resolves Seerr user [avatar] values to absolute image URLs.
 * Plex thumbs are typically absolute; Jellyfin/Emby use root-relative `/avatarproxy/...` paths.
 *
 * The result is handed straight to Coil, which loads it on OkHttp's async dispatcher. OkHttp
 * rethrows non-IOException throwables out of `AsyncCall.run`, so a URL with a missing or invalid
 * host surfaces as an uncaught `IllegalArgumentException` from `HttpUrl.Builder.host` at
 * connection time and takes the whole process down — Coil cannot catch it. Everything returned
 * here is therefore validated first; anything that is not a loadable absolute http(s) URL
 * resolves to null, which callers already treat as "no remote avatar".
 */
object AvatarUrlResolver {

    /** Matches a leading URI scheme, e.g. `http:`, `ftp:`, `javascript:`. */
    private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:")

    fun resolve(avatar: String?, seerrOrigin: String): String? {
        val trimmed = avatar?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        // Reject non-http(s) absolute URLs outright instead of pasting them onto the origin,
        // which would produce nonsense like "https://host/ftp://example.com/a.png".
        if (SCHEME_PREFIX.containsMatchIn(trimmed) && !trimmed.isHttpScheme()) return null

        val candidate = when {
            trimmed.isHttpScheme() -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> {
                // Origin-relative: the origin must itself be a usable http(s) base. When the
                // configured hostname is blank, getSeerrOrigin() yields "http://", and
                // "http://".trimEnd('/') collapses to "http:" — which would silently produce
                // hostless URLs like "http:/avatarproxy/abc".
                val origin = seerrOrigin.trim()
                if (!isLoadableHttpUrl(origin)) return null
                val base = origin.trimEnd('/')
                if (trimmed.startsWith("/")) base + trimmed else "$base/${trimmed.trimStart('/')}"
            }
        }

        return candidate.takeIf { isLoadableHttpUrl(it) }
    }

    private fun String.isHttpScheme(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    /**
     * True only if [url] is an absolute http(s) URL that OkHttp can actually connect to.
     *
     * Parsing alone is not sufficient. OkHttp's parser is deliberately lenient about slashes and
     * "repairs" hostless input — `"http:/avatarproxy/abc"` parses successfully with host
     * `avatarproxy`, and `"https:///a/b"` likewise resolves to a host that was never in the
     * string. Those would not crash, but they would connect somewhere the user never configured,
     * so an explicit authority is required first.
     *
     * The host is then re-run through `HttpUrl.Builder.host`, because `okhttp3.Address` rebuilds
     * it that way at connection time; a host that survives parsing but fails there is exactly the
     * `IllegalArgumentException` this guard exists to prevent.
     */
    private fun isLoadableHttpUrl(url: String): Boolean {
        if (!url.isHttpScheme()) return false
        if (!hasExplicitAuthority(url)) return false
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (parsed.host.isEmpty()) return false
        return try {
            HttpUrl.Builder().scheme(parsed.scheme).host(parsed.host).build()
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** True if [url] has a non-empty authority between `://` and the next `/`, `?` or `#`. */
    private fun hasExplicitAuthority(url: String): Boolean {
        val separator = url.indexOf("://")
        if (separator < 0) return false
        val authority = url.substring(separator + 3).takeWhile { it != '/' && it != '?' && it != '#' }
        return authority.isNotBlank()
    }
}
