package ca.devmesh.seerrtv.ui.state

/**
 * Constants defining focusable areas in the MediaDetails screen
 */
object FocusArea {
    const val NONE = -1
    const val OVERVIEW = 0
    const val READ_MORE = 1
    const val TAGS = 2
    const val CAST = 5
    const val CREW = 6
    const val SIMILAR_MEDIA = 10
    const val FOURK_REGULAR_OPTION = 8
    const val FOURK_4K_OPTION = 9

    // Action button focus areas
    const val PLAY = 3
    const val REQUEST_HD = 4
    const val REQUEST_4K = 11
    const val REQUEST_SINGLE = 12
    const val MANAGE_HD = 13
    const val MANAGE_4K = 14
    const val MANAGE_SINGLE = 15
    const val TRAILER = 16
    const val ISSUE = 17 // Issue reporting/details
    const val WATCHLIST_ACTION = 18

    /**
     * The areas that live in the details screen's action-button column. Which of these exist
     * depends on availability, permissions and 4K capability, so the set can change while the
     * screen loads — an area that drops out of the live button order draws no highlight at all
     * and must be re-homed to a button that does exist.
     */
    val ACTION_BUTTONS = setOf(
        PLAY,
        REQUEST_HD,
        REQUEST_4K,
        REQUEST_SINGLE,
        MANAGE_HD,
        MANAGE_4K,
        MANAGE_SINGLE,
        WATCHLIST_ACTION,
        TRAILER
    )
}

