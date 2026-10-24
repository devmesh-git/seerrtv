package ca.devmesh.seerrtv.util

import java.net.URLEncoder

/**
 * Builds the `plex://` deep link that opens a library item in the Plex Android TV app.
 *
 * Recent Plex for Android TV builds (verified on 2026.20.0) ignore the older
 * `plex://server://<id>/com.plexapp.plugins.library/library/metadata/<key>` and
 * `plex://preplay/?metadataKey=...` forms: the app launches but stays on its Home screen. The form
 * below opens the item's details page instead, for both movies and shows.
 *
 *  - movie: `plex://libraries/<serverId>/unmatched/%2Flibrary%2Fmetadata%2F<ratingKey>`
 *  - show:  `plex://libraries/<serverId>/unmatched/tv/%2Flibrary%2Fmetadata%2F<ratingKey>`
 *
 * `unmatched` is simply the path segment Plex expects; it works for normally matched items too.
 * For a show, [ratingKey] must be the show's own key (Seerr's `mediaInfo.ratingKey`), not a season
 * or episode key. No Plex token is part of the link.
 *
 * This is a pure function (no `android.net.Uri`) so it can be unit tested on the JVM.
 */
object PlexDeepLink {

    /** Seerr's `mediaInfo.mediaType` value for a series. Anything else is built as a movie link. */
    private const val MEDIA_TYPE_TV = "tv"

    /**
     * @param serverId Plex machine identifier (the `server` value Seerr puts in its Plex links)
     * @param ratingKey the item's Plex rating key (the last segment of `/library/metadata/<key>`)
     * @param mediaType Seerr media type, `"movie"` or `"tv"`
     * @return the deep link, or null when [serverId] or [ratingKey] is blank
     */
    fun build(serverId: String, ratingKey: String, mediaType: String?): String? {
        val server = serverId.trim()
        val key = ratingKey.trim()
        if (server.isEmpty() || key.isEmpty()) return null

        val tvSegment = if (mediaType.equals(MEDIA_TYPE_TV, ignoreCase = true)) "tv/" else ""
        return "plex://libraries/${encode(server)}/unmatched/$tvSegment" +
            "%2Flibrary%2Fmetadata%2F${encode(key)}"
    }

    /** Percent-encodes one path segment ([URLEncoder] encodes a space as `+`, which is wrong in a path). */
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
