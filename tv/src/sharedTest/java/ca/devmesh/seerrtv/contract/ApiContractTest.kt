package ca.devmesh.seerrtv.contract

import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.data.seerrApiJson
import ca.devmesh.seerrtv.model.Discover
import ca.devmesh.seerrtv.model.Movie
import ca.devmesh.seerrtv.model.Person
import ca.devmesh.seerrtv.model.RadarrServer
import ca.devmesh.seerrtv.model.RequestResponse
import ca.devmesh.seerrtv.model.SearchResponse
import ca.devmesh.seerrtv.model.TV
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes committed fixtures through the app's real [seerrApiJson] configuration.
 *
 * This class lives in `src/sharedTest` and is compiled into **two** source sets:
 *
 *  - `src/test` — plain JVM, fast, runs on every build.
 *  - `src/androidTest` — instrumented, and because `testBuildType` is `release` it runs inside
 *    the **minified** APK on a device.
 *
 * The second one is the point. R8 strips and renames; a JVM unit test never sees any of that, so
 * it cannot tell you whether a serializer survived obfuscation. Running the identical assertions
 * inside the shipped artifact can. If a keep rule regresses, the generated serializer for one of
 * these types disappears and the corresponding test throws where the JVM copy still passes.
 *
 * Fixtures are synthetic apart from `settings_public.json` and `status.json`, which were captured
 * from a real instance and scrubbed of hostnames, URLs and identifiers.
 */
class ApiContractTest {

    // Test names here are camelCase rather than the backtick-quoted sentences used elsewhere in
    // src/test. Those become method names containing spaces, which the JVM accepts but dex does
    // not below DEX version 040 (API 30) — and this class is also dexed for the instrumented run
    // at the app's minSdk of 23, where R8 fails outright with
    // "Space characters in SimpleName ... are not allowed".

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "Missing fixture /fixtures/$name. On the instrumented run this also fails when the " +
                "resource was not packaged into the test APK."
        }.bufferedReader().use { it.readText() }

    /**
     * The `discover` endpoints are the busiest decode path in the app — nine call sites — and backs every
     * browse row. Exercises [Discover] plus the nested Media and MediaInfo types.
     */
    @Test
    fun decodesDiscoverPageWithMovieAndTvEntries() {
        val discover = seerrApiJson.decodeFromString<Discover>(fixture("discover_movies.json"))

        assertEquals(1, discover.page)
        assertEquals(42, discover.totalPages)
        assertEquals(2, discover.results.size)

        val movie = discover.results.first { it.mediaType == "movie" }
        assertEquals("Example Voyage", movie.title)
        assertEquals(1241982, movie.id)
        assertEquals(5, movie.mediaInfo?.status)

        val tv = discover.results.first { it.mediaType == "tv" }
        assertEquals("Example Chronicles", tv.name)
        assertEquals(366524, tv.mediaInfo?.tvdbId)
    }

    /**
     * The highest-value case in this class.
     *
     * `SearchResult` is a sealed interface, and polymorphic serialization is the classic R8
     * casualty: subclasses without an explicit `@SerialName` fall back to their fully qualified
     * class name as the discriminator, obfuscation renames the class, and decoding dies in the
     * shipped build while every JVM test stays green. SeerrTV avoids that by dispatching on the
     * `mediaType` *value* through `SearchResultSerializer`, so the discriminator never depends on
     * a class name. This test pins that property in the minified build rather than assuming it.
     */
    @Test
    fun searchDispatchesEachResultToTheRightConcreteType() {
        val search = seerrApiJson.decodeFromString<SearchResponse>(fixture("search_mixed.json"))

        assertEquals(3, search.results.size)
        assertTrue("expected a Movie", search.results[0] is Movie)
        assertTrue("expected a TV", search.results[1] is TV)
        assertTrue("expected a Person", search.results[2] is Person)

        assertEquals("Example Movie Result", (search.results[0] as Movie).title)
        assertEquals("Example Series Result", (search.results[1] as TV).name)
    }

    /**
     * The request list was never exercised by hand, because driving the request UI writes to a
     * real Seerr instance. A fixture covers the decode half with no side effects.
     */
    @Test
    fun decodesRequestListWithMovieAndTvRequests() {
        val requests = seerrApiJson.decodeFromString<RequestResponse>(fixture("request_list.json"))

        assertEquals(27, requests.pageInfo.results)
        assertEquals(2, requests.results.size)

        val movieRequest = requests.results.first { it.type == "movie" }
        assertEquals("/data/media/movies", movieRequest.rootFolder)
        assertEquals("HD-1080p", movieRequest.profileName)
        assertEquals("example-user", movieRequest.requestedBy.username)

        val tvRequest = requests.results.first { it.type == "tv" }
        assertTrue(tvRequest.is4k)
        assertEquals(2, tvRequest.seasons.size)
        assertEquals(listOf(1, 2), tvRequest.seasons.map { it.seasonNumber })
    }

    /**
     * Root folders and their free space drive the request modal's folder picker, added in 0.30.0.
     */
    @Test
    fun decodesRadarrServersIncludingTheDefaultAndItsActiveDirectory() {
        val servers = seerrApiJson.decodeFromString(
            ListSerializer(RadarrServer.serializer()),
            fixture("radarr_servers.json")
        )

        assertEquals(2, servers.size)
        val default = servers.single { it.isDefault }
        assertEquals("/data/media/movies", default.activeDirectory)
        assertEquals(4, default.activeProfileId)
        assertTrue(servers.any { it.is4k })
    }

    /**
     * Captured from a live instance and scrubbed. Guards the settings that gate visibility —
     * `hideAvailable` and `hideBlocklisted` are applied client-side, so a decode regression here
     * would silently show titles that should be filtered out.
     */
    @Test
    fun decodesPublicSettingsIncludingTheClientSideVisibilityFlags() {
        val settings = seerrApiJson.decodeFromString<SeerrApiService.PublicSettingsResponse>(
            fixture("settings_public.json")
        )

        // The captured instance had both flags off and a Plex-type media server. The point is
        // less the values than that they decode at all: the payload carries 27 keys this type
        // does not declare, so `ignoreUnknownKeys` has to be doing its job, and the three
        // visibility flags have to land as Booleans rather than staying null.
        assertEquals(1, settings.mediaServerType)
        assertEquals(false, settings.hideAvailable)
        assertEquals(false, settings.hideBlocklisted)
    }
}
