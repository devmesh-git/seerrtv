package ca.devmesh.seerrtv.util

import kotlin.math.abs

object AvatarUtils {
    /**
     * Generates a two-letter avatar from a name or email-like string.
     * - If name has 2+ words, use first letter of first + last word.
     * - If 1 word, use first 2 letters.
     * - If no usable letters, returns "??".
     */
    fun generateInitialsFromNameOrEmail(name: String?, email: String?): String {
        val source = when {
            !name.isNullOrBlank() -> name
            !email.isNullOrBlank() -> email.substringBefore('@')
            else -> ""
        }.trim()

        if (source.isBlank()) return "??"

        // Only keep A-Z letters for avatar purposes.
        val letters = source.filter { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return "??"

        val parts = source.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val cleanedParts = parts.map { it.filter { ch -> ch.isLetter() }.uppercase() }.filter { it.isNotBlank() }

        val initials = when {
            cleanedParts.size >= 2 -> {
                "${cleanedParts.first().first()}${cleanedParts.last().first()}"
            }
            cleanedParts.size == 1 && cleanedParts.first().length >= 2 -> {
                cleanedParts.first().take(2)
            }
            cleanedParts.size == 1 && cleanedParts.first().isNotEmpty() -> {
                val first = cleanedParts.first().first()
                // Second letter fallback: take next letter cyclically from the same token
                val second = cleanedParts.first()[cleanedParts.first().indexOf(first).coerceAtMost(cleanedParts.first().lastIndex)]
                "$first$second"
            }
            else -> "??"
        }

        val normalized = initials.take(2).uppercase()
        return normalized
            .map { if (it in 'A'..'Z') it else '?' }
            .joinToString("")
    }

    /**
     * Resolves conflicts so avatar initials are unique across profiles.
     * Guarantees a 2-letter result in the range A-Z that isn't in `existingInitials`.
     */
    fun resolveUniqueInitials(desiredInitials: String, existingInitials: Set<String>, seed: String): String {
        val desired = desiredInitials.take(2).uppercase()
        val existing = existingInitials.map { it.take(2).uppercase() }.toSet()

        if (desired.length == 2 && desired.all { it in 'A'..'Z' } && !existing.contains(desired)) {
            return desired
        }

        // Deterministically pick from 26*26 combos, so conflicts always resolve.
        val seedHash = abs(seed.hashCode())
        val total = 26 * 26
        for (i in 0 until total) {
            val pairIndex = (seedHash + i) % total
            val first = ('A'.code + (pairIndex / 26)).toChar()
            val second = ('A'.code + (pairIndex % 26)).toChar()
            val candidate = "" + first + second
            if (!existing.contains(candidate)) return candidate
        }

        // Should be practically unreachable unless >676 profiles exist.
        return "ZZ"
    }
}

