package ca.devmesh.seerrtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the per-route language-parameter rule enforced by [appendDisplayLocale].
 *
 * Background: on `discover/movies` and `discover/tv` the Seerr server binds `language` to the
 * ORIGINAL-LANGUAGE filter, not the display locale (display comes from the account setting via
 * `req.locale`). Sending the discovery locale to those two routes silently filtered results by
 * original language — a non-English user's Popular/Upcoming/genre/keyword rows showed only
 * titles whose original language matched their locale. Every other route uses
 * `req.query.language ?? req.locale`, so the locale IS honoured there. These tests pin both
 * sides of that split so a future edit can't collapse it.
 */
class AppendDisplayLocaleTest {

    // --- Collision routes: locale must NOT be appended ---

    @Test
    fun `discover movies is left untouched`() {
        assertEquals("discover/movies", appendDisplayLocale("discover/movies", "de"))
    }

    @Test
    fun `discover tv is left untouched`() {
        assertEquals("discover/tv", appendDisplayLocale("discover/tv", "de"))
    }

    @Test
    fun `discover movies with existing query params is left untouched`() {
        val endpoint = "discover/movies?genre=28&page=1"
        assertEquals(endpoint, appendDisplayLocale(endpoint, "ja"))
    }

    @Test
    fun `discover tv with keyword filter is left untouched`() {
        val endpoint = "discover/tv?keywords=180547&page=2"
        assertEquals(endpoint, appendDisplayLocale(endpoint, "fr"))
    }

    // --- Non-collision routes: locale IS appended, with the right separator ---

    @Test
    fun `studio subpath receives the locale`() {
        assertEquals(
            "discover/movies/studio/2?page=1&language=de",
            appendDisplayLocale("discover/movies/studio/2?page=1", "de")
        )
    }

    @Test
    fun `network subpath receives the locale`() {
        assertEquals(
            "discover/tv/network/213?page=1&language=de",
            appendDisplayLocale("discover/tv/network/213?page=1", "de")
        )
    }

    @Test
    fun `genreslider receives the locale with a question mark separator`() {
        assertEquals(
            "discover/genreslider/movie?language=de",
            appendDisplayLocale("discover/genreslider/movie", "de")
        )
    }

    @Test
    fun `detail endpoint without query gets a question mark separator`() {
        assertEquals("movie/603?language=ja", appendDisplayLocale("movie/603", "ja"))
    }

    @Test
    fun `endpoint with existing query gets an ampersand separator`() {
        assertEquals(
            "search?query=matrix&language=ja",
            appendDisplayLocale("search?query=matrix", "ja")
        )
    }

    @Test
    fun `trending receives the locale`() {
        assertEquals("discover/trending?language=zh", appendDisplayLocale("discover/trending", "zh"))
    }

    // --- English is the server default and is never appended anywhere ---

    @Test
    fun `english is a no-op on a normal route`() {
        assertEquals("discover/trending", appendDisplayLocale("discover/trending", "en"))
    }

    @Test
    fun `english is a no-op on a collision route`() {
        assertEquals("discover/movies", appendDisplayLocale("discover/movies", "en"))
    }
}
