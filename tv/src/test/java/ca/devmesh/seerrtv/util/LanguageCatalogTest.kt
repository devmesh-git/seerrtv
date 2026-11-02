package ca.devmesh.seerrtv.util

import ca.devmesh.seerrtv.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageCatalogTest {

    @Test
    fun `italian is included in supported languages`() {
        assertTrue(LanguageCatalog.codes.contains("it"))
        assertEquals(R.string.language_italian, LanguageCatalog.nameRes("it"))
    }

    @Test
    fun `all language codes are unique`() {
        val codes = LanguageCatalog.codes
        assertEquals(codes.toSet().size, codes.size)
    }

    @Test
    fun `unknown language code falls back to english`() {
        assertEquals(R.string.language_english, LanguageCatalog.nameRes("unknown"))
        assertEquals(R.string.language_english, LanguageCatalog.nameRes(""))
    }

    @Test
    fun `all registered languages have valid resource ids`() {
        for ((code, resId) in LanguageCatalog.languages) {
            assertTrue("Language code should not be blank", code.isNotBlank())
            assertTrue("Resource ID should be positive for $code", resId != 0)
        }
    }
}
