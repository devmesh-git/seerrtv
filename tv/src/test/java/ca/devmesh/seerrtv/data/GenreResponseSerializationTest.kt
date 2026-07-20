package ca.devmesh.seerrtv.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the decode path shared by all four genre endpoints.
 *
 * Background: 0.28.06 routed these endpoints through `executeApiCall<HttpResponse>`, whose success
 * branch called `decodeFromString<HttpResponse>` — a type with no serializer. That threw
 * SerializationException at runtime and was swallowed into ApiResult.Error, so every Genres row
 * showed "Error loading media" and the genre filter drawer was silently empty.
 *
 * The fix decodes `List<GenreResponse>` directly. These tests pin the two response shapes that now
 * flow through that single path, so a change to [SeerrApiService.GenreResponse] can't quietly break
 * either one again.
 */
class GenreResponseSerializationTest {

    /** Mirrors the Json config in SeerrApiService. */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun decode(payload: String): List<SeerrApiService.GenreResponse> =
        json.decodeFromString(payload)

    /**
     * `discover/genreslider/{movie,tv}` — home-screen carousel. Includes backdrops, which the
     * carousel renders as card art.
     */
    @Test
    fun `decodes genreslider payload with backdrops`() {
        val payload = """
            [
              {"id":28,"name":"Action","backdrops":["/a1.jpg","/a2.jpg"]},
              {"id":35,"name":"Comedy","backdrops":["/c1.jpg"]}
            ]
        """.trimIndent()

        val genres = decode(payload)

        assertEquals(2, genres.size)
        assertEquals(28, genres[0].id)
        assertEquals("Action", genres[0].name)
        assertEquals(listOf("/a1.jpg", "/a2.jpg"), genres[0].backdrops)
        assertEquals(listOf("/c1.jpg"), genres[1].backdrops)
    }

    /**
     * `genres/{movie,tv}` — browse filters drawer. This endpoint omits `backdrops` entirely, which
     * is why the field carries a default; without it the filter drawer fails to parse.
     */
    @Test
    fun `decodes filter payload without backdrops`() {
        val payload = """[{"id":28,"name":"Action"},{"id":35,"name":"Comedy"}]"""

        val genres = decode(payload)

        assertEquals(2, genres.size)
        assertTrue(genres.all { it.backdrops.isEmpty() })
        assertEquals(listOf("Action", "Comedy"), genres.map { it.name })
    }

    /** Server-shape drift between Overseerr/Jellyseerr versions must not fail the whole row. */
    @Test
    fun `ignores unknown fields`() {
        val payload = """[{"id":28,"name":"Action","backdrops":[],"someNewField":"whatever"}]"""

        val genres = decode(payload)

        assertEquals(1, genres.size)
        assertEquals("Action", genres[0].name)
    }

    /** An empty catalog is a valid response, not an error. */
    @Test
    fun `decodes empty list`() {
        assertEquals(emptyList<SeerrApiService.GenreResponse>(), decode("[]"))
    }
}
