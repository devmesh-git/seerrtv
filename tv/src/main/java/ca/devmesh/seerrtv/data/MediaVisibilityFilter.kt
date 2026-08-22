package ca.devmesh.seerrtv.data

import ca.devmesh.seerrtv.util.CommonUtil
import ca.devmesh.seerrtv.util.Permission

/**
 * Which visibility rules apply to a given endpoint's results.
 *
 * Seerr hides blocklisted and already-available media entirely in its web client — the API
 * returns those titles to every caller — so each client has to reproduce the filtering itself.
 * The web app applies two different combinations depending on the surface, and these policies
 * mirror them 1:1:
 *
 *  - [DISCOVER] — discover rows, browse grids, genre/keyword/studio/network, similar and
 *    recommendations, watchlist. `ListView`/`MediaSlider` permission filter *plus* the
 *    `hideAvailable` / `hideBlocklisted` public settings (`useDiscover`).
 *  - [SEARCH] — search results. Seerr's search page passes
 *    `{ hideAvailable: false, hideBlocklisted: false }`, so only the permission filter runs.
 *  - [NONE] — surfaces Seerr leaves untouched: Recently Added (an `allavailable` query that
 *    `hideAvailable` would empty out), requests, person credits.
 */
enum class VisibilityPolicy { DISCOVER, SEARCH, NONE }

/**
 * The current user's media-visibility rules, resolved from their Seerr permissions and the
 * server's public settings.
 *
 * Mirrors `src/hooks/useDiscover.ts`, `src/components/Common/ListView/index.tsx` and
 * `src/components/MediaSlider/index.tsx` in seerr-team/seerr. Pure and synchronous so it can be
 * applied inline while unwrapping API responses, and unit-tested without Android or the network.
 */
data class MediaVisibility(
    val userPermissions: Int,
    val hideAvailable: Boolean,
    val hideBlocklisted: Boolean
) {
    /** True when the user may see blocklisted titles at all (Seerr's always-on permission gate). */
    private val canSeeBlocklisted: Boolean
        get() = CommonUtil.hasAnyPermission(
            userPermissions,
            Permission.MANAGE_BLOCKLIST,
            Permission.VIEW_BLOCKLIST
        )

    /** True when the "Hide Blocklisted Items" setting applies to this user. */
    private val hidesBlocklistedBySetting: Boolean
        get() = hideBlocklisted &&
            CommonUtil.hasPermission(userPermissions, Permission.MANAGE_BLOCKLIST)

    /**
     * Whether an item must be dropped from a list rendered under [policy].
     *
     * [status]/[status4k] are Seerr media statuses (see [STATUS_BLOCKLISTED]); pass null when the
     * item carries no `mediaInfo`, which is the normal case for anything the server isn't
     * tracking (and always the case on Overseerr, which has no blocklist).
     */
    fun isHidden(
        status: Int?,
        status4k: Int? = null,
        mediaType: String? = null,
        policy: VisibilityPolicy = VisibilityPolicy.DISCOVER
    ): Boolean {
        if (policy == VisibilityPolicy.NONE) return false

        val isBlocklisted = status == STATUS_BLOCKLISTED || status4k == STATUS_BLOCKLISTED

        // Permission layer — always on, on every filtered surface.
        if (isBlocklisted && !canSeeBlocklisted) return true

        if (policy != VisibilityPolicy.DISCOVER) return false

        // Settings layer — Seerr only applies it to movie/tv entries, which also drops people
        // from mixed lists such as trending while a setting is active.
        if (!isFilterableMediaType(mediaType)) return false

        if (isBlocklisted && hidesBlocklistedBySetting) return true
        if (hideAvailable && (status == STATUS_AVAILABLE || status == STATUS_PARTIALLY_AVAILABLE)) return true

        return false
    }

    /** True when neither layer can ever hide anything, so callers can skip filtering entirely. */
    fun hidesNothing(): Boolean = canSeeBlocklisted && !hideAvailable && !hidesBlocklistedBySetting

    companion object {
        // ca.devmesh.seerrtv MediaStatus mirror (server/constants/media.ts).
        const val STATUS_PARTIALLY_AVAILABLE = 4
        const val STATUS_AVAILABLE = 5
        const val STATUS_BLOCKLISTED = 6

        /** Used before the user's permissions are known; hides nothing. */
        val UNRESTRICTED = MediaVisibility(
            userPermissions = Permission.ADMIN.value,
            hideAvailable = false,
            hideBlocklisted = false
        )

        private fun isFilterableMediaType(mediaType: String?): Boolean =
            when (mediaType?.lowercase()) {
                "movie", "tv" -> true
                else -> false
            }
    }
}
