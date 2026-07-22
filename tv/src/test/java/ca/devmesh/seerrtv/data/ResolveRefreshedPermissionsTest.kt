package ca.devmesh.seerrtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the rule that a refreshed `auth/me` payload without a readable permissions value must
 * never degrade the user's known permissions to 0.
 *
 * Background: the resume-time token check re-persists the authenticated user on every app
 * resume (e.g. returning from the external YouTube trailer player). Before 0.28.12 that path
 * stored `user.permissions ?: 0`, so a payload whose permissions field was absent or undecodable
 * ([SafeIntSerializer] returns null for those) stripped every permission in memory — the details
 * screen's Request button disappeared until the next full re-login. These tests pin the
 * fallback rule and the one deliberate asymmetry: an explicit 0 from the server is respected,
 * only an unreadable/absent value falls back.
 */
class ResolveRefreshedPermissionsTest {

    @Test
    fun `fresh value wins over last known`() {
        assertEquals(2, resolveRefreshedPermissions(fresh = 2, lastKnown = 98368))
    }

    @Test
    fun `unreadable fresh value keeps last known permissions`() {
        assertEquals(98368, resolveRefreshedPermissions(fresh = null, lastKnown = 98368))
    }

    @Test
    fun `explicit zero from the server is respected, not treated as unreadable`() {
        assertEquals(0, resolveRefreshedPermissions(fresh = 0, lastKnown = 98368))
    }

    @Test
    fun `nothing known anywhere resolves to no permissions`() {
        assertEquals(0, resolveRefreshedPermissions(fresh = null, lastKnown = null))
    }
}
