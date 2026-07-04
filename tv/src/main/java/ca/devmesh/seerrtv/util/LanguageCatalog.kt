package ca.devmesh.seerrtv.util

import ca.devmesh.seerrtv.R

/**
 * Single source of truth for the languages the app offers.
 *
 * The list is the set of languages the app is actually translated into — it MUST mirror the
 * `res/values-*` translation folders (base `values/` = English). Every language picker and the
 * browse original-language filter derives from this one list, so the choices stay consistent and
 * can't drift apart. To add a language: ship its `values-<code>/strings.xml`, add its
 * `language_<name>` string to every locale, then add one entry here.
 *
 * Order here is the display order used everywhere.
 */
object LanguageCatalog {
    val languages: List<Pair<String, Int>> = listOf(
        "en" to R.string.language_english,
        "de" to R.string.language_german,
        "es" to R.string.language_spanish,
        "fr" to R.string.language_french,
        "ja" to R.string.language_japanese,
        "nl" to R.string.language_dutch,
        "pt" to R.string.language_portuguese,
        "zh" to R.string.language_chinese,
        "et" to R.string.language_estonian,
        "hu" to R.string.language_hungarian,
    )

    /** ISO codes in display order. */
    val codes: List<String> = languages.map { it.first }

    /** String resource for a language's display name; falls back to English for unknown codes. */
    fun nameRes(code: String): Int = languages.firstOrNull { it.first == code }?.second ?: R.string.language_english
}
