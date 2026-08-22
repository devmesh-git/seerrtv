package ca.devmesh.seerrtv.data

import ca.devmesh.seerrtv.util.Permission
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins SeerrTV's copy of Seerr's content-visibility rules.
 *
 * Seerr's API hands blocklisted and already-available titles to every caller and hides them in
 * the web client instead (`ListView`, `MediaSlider`, `useDiscover`). SeerrTV showed everything
 * because it never reproduced that filtering — a blocklisted title stayed visible on TV for
 * users who could not see it in a browser. These tests pin each branch of the port, including
 * the two deliberate asymmetries: search ignores the settings layer, and the permission layer
 * ignores the settings entirely.
 */
class MediaVisibilityFilterTest {

    private val blocklisted = MediaVisibility.STATUS_BLOCKLISTED
    private val available = MediaVisibility.STATUS_AVAILABLE
    private val partiallyAvailable = MediaVisibility.STATUS_PARTIALLY_AVAILABLE

    private fun visibility(
        permissions: Int = Permission.REQUEST.value,
        hideAvailable: Boolean = false,
        hideBlocklisted: Boolean = false
    ) = MediaVisibility(permissions, hideAvailable, hideBlocklisted)

    // --- Permission layer: always on, no server setting involved --------------------------------

    @Test
    fun `user without blocklist permissions never sees blocklisted media`() {
        assertTrue(visibility().isHidden(status = blocklisted, mediaType = "movie"))
    }

    @Test
    fun `view blocklist permission keeps blocklisted media visible`() {
        val user = visibility(Permission.REQUEST.value or Permission.VIEW_BLOCKLIST.value)
        assertFalse(user.isHidden(status = blocklisted, mediaType = "movie"))
    }

    @Test
    fun `manage blocklist permission keeps blocklisted media visible while the setting is off`() {
        val user = visibility(Permission.MANAGE_BLOCKLIST.value)
        assertFalse(user.isHidden(status = blocklisted, mediaType = "tv"))
    }

    @Test
    fun `admin sees blocklisted media`() {
        assertFalse(visibility(Permission.ADMIN.value).isHidden(status = blocklisted, mediaType = "movie"))
    }

    @Test
    fun `blocklisted 4k status hides the title too`() {
        assertTrue(visibility().isHidden(status = 1, status4k = blocklisted, mediaType = "movie"))
    }

    @Test
    fun `permission layer applies to search as well`() {
        assertTrue(
            visibility().isHidden(
                status = blocklisted,
                mediaType = "movie",
                policy = VisibilityPolicy.SEARCH
            )
        )
    }

    @Test
    fun `permission layer ignores media type so people and collections are unaffected`() {
        // A person result never carries a blocklisted status; nothing to hide either way.
        assertFalse(visibility().isHidden(status = null, mediaType = "person"))
    }

    // --- Settings layer: discover surfaces only --------------------------------------------------

    @Test
    fun `hide blocklisted setting hides it from a blocklist manager`() {
        val admin = visibility(Permission.MANAGE_BLOCKLIST.value, hideBlocklisted = true)
        assertTrue(admin.isHidden(status = blocklisted, mediaType = "movie"))
    }

    @Test
    fun `hide blocklisted setting does not apply to a view-only blocklist user`() {
        // Mirrors Seerr: useDiscover gates the setting on MANAGE_BLOCKLIST, not VIEW_BLOCKLIST.
        val viewer = visibility(Permission.VIEW_BLOCKLIST.value, hideBlocklisted = true)
        assertFalse(viewer.isHidden(status = blocklisted, mediaType = "tv"))
    }

    @Test
    fun `hide blocklisted setting does not reach search`() {
        val admin = visibility(Permission.MANAGE_BLOCKLIST.value, hideBlocklisted = true)
        assertFalse(
            admin.isHidden(status = blocklisted, mediaType = "movie", policy = VisibilityPolicy.SEARCH)
        )
    }

    @Test
    fun `hide available hides available and partially available titles for everyone`() {
        val user = visibility(hideAvailable = true)
        assertTrue(user.isHidden(status = available, mediaType = "movie"))
        assertTrue(user.isHidden(status = partiallyAvailable, mediaType = "tv"))
    }

    @Test
    fun `hide available leaves pending and unknown titles alone`() {
        val user = visibility(hideAvailable = true)
        assertFalse(user.isHidden(status = 2, mediaType = "movie"))
        assertFalse(user.isHidden(status = null, mediaType = "movie"))
    }

    @Test
    fun `hide available ignores the 4k status`() {
        // Seerr checks mediaInfo.status only, so a title available in 4K but not in HD stays.
        val user = visibility(hideAvailable = true)
        assertFalse(user.isHidden(status = 2, status4k = available, mediaType = "movie"))
    }

    @Test
    fun `settings layer only applies to movie and tv entries`() {
        val user = visibility(hideAvailable = true)
        assertFalse(user.isHidden(status = available, mediaType = "person"))
        assertFalse(user.isHidden(status = available, mediaType = null))
    }

    @Test
    fun `settings layer does not reach search`() {
        val user = visibility(hideAvailable = true)
        assertFalse(user.isHidden(status = available, mediaType = "movie", policy = VisibilityPolicy.SEARCH))
    }

    // --- Policy NONE and the fast path -----------------------------------------------------------

    @Test
    fun `none policy passes everything through`() {
        val user = visibility(hideAvailable = true, hideBlocklisted = true)
        assertFalse(user.isHidden(status = blocklisted, mediaType = "movie", policy = VisibilityPolicy.NONE))
        assertFalse(user.isHidden(status = available, mediaType = "movie", policy = VisibilityPolicy.NONE))
    }

    @Test
    fun `hidesNothing is true only when no rule can fire`() {
        assertTrue(visibility(Permission.VIEW_BLOCKLIST.value).hidesNothing())
        assertTrue(MediaVisibility.UNRESTRICTED.hidesNothing())
        assertFalse(visibility().hidesNothing())
        assertFalse(visibility(Permission.ADMIN.value, hideAvailable = true).hidesNothing())
        assertFalse(visibility(Permission.ADMIN.value, hideBlocklisted = true).hidesNothing())
    }

    // --- Legacy backends --------------------------------------------------------------------------

    @Test
    fun `overseerr responses without media info are untouched`() {
        // Overseerr has no blocklist, and untracked titles carry no mediaInfo on any backend.
        val user = visibility(hideAvailable = true, hideBlocklisted = true)
        assertFalse(user.isHidden(status = null, status4k = null, mediaType = "movie"))
    }
}
