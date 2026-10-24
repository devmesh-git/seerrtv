package ca.devmesh.seerrtv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Plex deep link format.
 *
 * Why this matters: the Plex Android TV app (2026.20.0 and later) opens its Home screen for the
 * older `plex://server://...` and `plex://preplay/...` links, so "Play on Plex" silently landed on
 * the wrong screen. The format asserted here is the one that opens the item's details page.
 */
class PlexDeepLinkTest {

    private val serverId = "4c1e980fd1ac2e50148274ea22483303c8c9e4c7"

    @Test
    fun `movie link opens the library item`() {
        assertEquals(
            "plex://libraries/$serverId/unmatched/%2Flibrary%2Fmetadata%2F8531",
            PlexDeepLink.build(serverId, "8531", "movie")
        )
    }

    @Test
    fun `tv link carries the tv segment`() {
        assertEquals(
            "plex://libraries/$serverId/unmatched/tv/%2Flibrary%2Fmetadata%2F8493",
            PlexDeepLink.build(serverId, "8493", "tv")
        )
    }

    @Test
    fun `media type match ignores case`() {
        assertEquals(
            PlexDeepLink.build(serverId, "8493", "tv"),
            PlexDeepLink.build(serverId, "8493", "TV")
        )
    }

    @Test
    fun `unknown or missing media type is built as a movie link`() {
        val movie = PlexDeepLink.build(serverId, "1", "movie")
        assertEquals(movie, PlexDeepLink.build(serverId, "1", null))
        assertEquals(movie, PlexDeepLink.build(serverId, "1", ""))
        assertEquals(movie, PlexDeepLink.build(serverId, "1", "person"))
    }

    @Test
    fun `blank server id or rating key gives no link`() {
        assertNull(PlexDeepLink.build("", "8531", "movie"))
        assertNull(PlexDeepLink.build("   ", "8531", "movie"))
        assertNull(PlexDeepLink.build(serverId, "", "movie"))
        assertNull(PlexDeepLink.build(serverId, "  ", "tv"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(
            "plex://libraries/$serverId/unmatched/%2Flibrary%2Fmetadata%2F8531",
            PlexDeepLink.build(" $serverId ", " 8531 ", "movie")
        )
    }

    @Test
    fun `path segments are percent encoded`() {
        val link = PlexDeepLink.build("a b/c", "1?2", "movie")!!
        // a space must be %20 (not '+'), and '/' or '?' inside a value must not leak into the path
        assertTrue(link, link.startsWith("plex://libraries/a%20b%2Fc/unmatched/"))
        assertTrue(link, link.endsWith("%2Flibrary%2Fmetadata%2F1%3F2"))
    }

    @Test
    fun `the old formats are gone`() {
        val link = PlexDeepLink.build(serverId, "8531", "movie")!!
        assertTrue(link, !link.contains("server://"))
        assertTrue(link, !link.contains("preplay"))
    }
}
