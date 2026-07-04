package ca.devmesh.seerrtv.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testValidIntegerQuota() {
        val userJson = """
            {
                "id": 1,
                "createdAt": "2026-06-05T00:00:00Z",
                "updatedAt": "2026-06-05T00:00:00Z",
                "displayName": "Test User",
                "movieQuotaLimit": 10,
                "movieQuotaDays": 30
            }
        """.trimIndent()

        val user = json.decodeFromString<User>(userJson)
        assertEquals(1, user.id)
        assertEquals("Test User", user.displayName)
        assertEquals(10, user.movieQuotaLimit)
        assertEquals(30, user.movieQuotaDays)
    }

    @Test
    fun testNullQuota() {
        val userJson = """
            {
                "id": 1,
                "createdAt": "2026-06-05T00:00:00Z",
                "updatedAt": "2026-06-05T00:00:00Z",
                "displayName": "Test User",
                "movieQuotaLimit": null,
                "movieQuotaDays": null
            }
        """.trimIndent()

        val user = json.decodeFromString<User>(userJson)
        assertEquals(1, user.id)
        assertNull(user.movieQuotaLimit)
        assertNull(user.movieQuotaDays)
    }

    @Test
    fun testCorruptedStringQuota() {
        val userJson = """
            {
                "id": 1,
                "createdAt": "2026-06-05T00:00:00Z",
                "updatedAt": "2026-06-05T00:00:00Z",
                "displayName": "Test User",
                "movieQuotaLimit": "movieQuotaLimit",
                "movieQuotaDays": "movieQuotaDays",
                "tvQuotaLimit": "tvQuotaLimit",
                "tvQuotaDays": "tvQuotaDays"
            }
        """.trimIndent()

        val user = json.decodeFromString<User>(userJson)
        assertEquals(1, user.id)
        assertNull(user.movieQuotaLimit)
        assertNull(user.movieQuotaDays)
        assertNull(user.tvQuotaLimit)
        assertNull(user.tvQuotaDays)
    }

    @Test
    fun testAuthMeOmittingRequiredFields() {
        // A Seerr /auth/me payload that omits createdAt, updatedAt, and displayName.
        // These fields differ between Overseerr/Seerr backend variants; deserialization
        // must not throw when they are absent (regression for the "quotalimit in json"
        // setup failure reported after migrating from Overseerr to Seerr).
        val userJson = """
            {
                "id": 42,
                "email": "user@example.com",
                "permissions": 2,
                "movieQuotaLimit": "5",
                "movieQuotaDays": "7"
            }
        """.trimIndent()

        val user = json.decodeFromString<User>(userJson)
        assertEquals(42, user.id)
        assertEquals("", user.displayName)
        assertNull(user.createdAt)
        assertNull(user.updatedAt)
        assertEquals(5, user.movieQuotaLimit)
        assertEquals(7, user.movieQuotaDays)
    }

    @Test
    fun testNumericStringQuota() {
        val userJson = """
            {
                "id": 1,
                "createdAt": "2026-06-05T00:00:00Z",
                "updatedAt": "2026-06-05T00:00:00Z",
                "displayName": "Test User",
                "movieQuotaLimit": "15",
                "movieQuotaDays": "7"
            }
        """.trimIndent()

        val user = json.decodeFromString<User>(userJson)
        assertEquals(1, user.id)
        assertEquals(15, user.movieQuotaLimit)
        assertEquals(7, user.movieQuotaDays)
    }
}
