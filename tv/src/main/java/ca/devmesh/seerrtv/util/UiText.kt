package ca.devmesh.seerrtv.util

/**
 * Caps dynamic server/user text before Compose measures/layouts it.
 * Long emoji-heavy strings can block the main thread during text layout.
 */
fun String.uiTruncateForDisplay(maxChars: Int, ellipsis: String = "…"): String {
    if (length <= maxChars) return this
    return take(maxChars).trimEnd() + ellipsis
}
