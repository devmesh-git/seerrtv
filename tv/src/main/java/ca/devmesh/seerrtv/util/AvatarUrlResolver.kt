package ca.devmesh.seerrtv.util

/**
 * Resolves Seerr user [avatar] values to absolute image URLs.
 * Plex thumbs are typically absolute; Jellyfin/Emby use root-relative `/avatarproxy/...` paths.
 */
object AvatarUrlResolver {
    fun resolve(avatar: String?, seerrOrigin: String): String? {
        val trimmed = avatar?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> seerrOrigin.trimEnd('/') + trimmed
            else -> "${seerrOrigin.trimEnd('/')}/${trimmed.trimStart('/')}"
        }
    }
}
