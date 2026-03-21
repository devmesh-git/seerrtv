package ca.devmesh.seerrtv.model

import androidx.compose.ui.graphics.Color

/**
 * Limited palette so the avatar UI stays consistent across the app.
 * Stored in `UserProfile.avatarColor` as `key`.
 */
enum class AvatarColor(val key: String, val argb: Long) {
    PURPLE("PURPLE", 0xFF9D29BC),
    BLUE("BLUE", 0xFF3370FF),
    TEAL("TEAL", 0xFF11B8A6),
    PINK("PINK", 0xFFFF4DA6),
    ORANGE("ORANGE", 0xFFFF8A4C);

    companion object {
        fun fromKey(key: String?): AvatarColor {
            if (key.isNullOrBlank()) return PURPLE
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PURPLE
        }
    }

    fun toColor(): Color = Color(argb)
}

