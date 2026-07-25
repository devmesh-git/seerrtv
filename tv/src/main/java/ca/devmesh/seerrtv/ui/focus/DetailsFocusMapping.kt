package ca.devmesh.seerrtv.ui.focus

import ca.devmesh.seerrtv.ui.state.FocusArea

/**
 * The one place the app-wide [DetailsFocusState] and the details screen's [FocusArea] ints are
 * translated into each other.
 *
 * [AppFocusManager] owns the details screen's focus; `MediaDetailsStateManager.currentFocusArea`
 * is a projection of it, so these two functions are the only bridge between the two vocabularies.
 * Both `when`s are exhaustive over their sealed/constant domain, so adding a focus state on either
 * side is a compile error here rather than a silently unmapped area at runtime.
 */
fun DetailsFocusState.toFocusArea(): Int = when (this) {
    DetailsFocusState.Overview -> FocusArea.OVERVIEW
    DetailsFocusState.ReadMore -> FocusArea.READ_MORE
    DetailsFocusState.Tags -> FocusArea.TAGS
    DetailsFocusState.Cast -> FocusArea.CAST
    DetailsFocusState.Crew -> FocusArea.CREW
    DetailsFocusState.SimilarMedia -> FocusArea.SIMILAR_MEDIA
    DetailsFocusState.FourKRegularOption -> FocusArea.FOURK_REGULAR_OPTION
    DetailsFocusState.FourK4KOption -> FocusArea.FOURK_4K_OPTION
    DetailsFocusState.Play -> FocusArea.PLAY
    DetailsFocusState.RequestHD -> FocusArea.REQUEST_HD
    DetailsFocusState.Request4K -> FocusArea.REQUEST_4K
    DetailsFocusState.RequestSingle -> FocusArea.REQUEST_SINGLE
    DetailsFocusState.ManageHD -> FocusArea.MANAGE_HD
    DetailsFocusState.Manage4K -> FocusArea.MANAGE_4K
    DetailsFocusState.ManageSingle -> FocusArea.MANAGE_SINGLE
    DetailsFocusState.WatchlistAction -> FocusArea.WATCHLIST_ACTION
    DetailsFocusState.Trailer -> FocusArea.TRAILER
    DetailsFocusState.Issue -> FocusArea.ISSUE
    // Declared but never set; fold onto the halves they represent rather than losing the focus.
    DetailsFocusState.PlayIssueSplitLeft -> FocusArea.PLAY
    DetailsFocusState.PlayIssueSplitRight -> FocusArea.ISSUE
}

/**
 * Null for [FocusArea.NONE] (and any unmapped value): "the details screen does not hold the focus"
 * is not something the details screen can assert — it is the result of some *other* surface, such
 * as the top bar, owning [AppFocusManager.currentFocus].
 */
fun Int.toDetailsFocusState(): DetailsFocusState? = when (this) {
    FocusArea.OVERVIEW -> DetailsFocusState.Overview
    FocusArea.READ_MORE -> DetailsFocusState.ReadMore
    FocusArea.TAGS -> DetailsFocusState.Tags
    FocusArea.CAST -> DetailsFocusState.Cast
    FocusArea.CREW -> DetailsFocusState.Crew
    FocusArea.SIMILAR_MEDIA -> DetailsFocusState.SimilarMedia
    FocusArea.FOURK_REGULAR_OPTION -> DetailsFocusState.FourKRegularOption
    FocusArea.FOURK_4K_OPTION -> DetailsFocusState.FourK4KOption
    FocusArea.PLAY -> DetailsFocusState.Play
    FocusArea.REQUEST_HD -> DetailsFocusState.RequestHD
    FocusArea.REQUEST_4K -> DetailsFocusState.Request4K
    FocusArea.REQUEST_SINGLE -> DetailsFocusState.RequestSingle
    FocusArea.MANAGE_HD -> DetailsFocusState.ManageHD
    FocusArea.MANAGE_4K -> DetailsFocusState.Manage4K
    FocusArea.MANAGE_SINGLE -> DetailsFocusState.ManageSingle
    FocusArea.WATCHLIST_ACTION -> DetailsFocusState.WatchlistAction
    FocusArea.TRAILER -> DetailsFocusState.Trailer
    FocusArea.ISSUE -> DetailsFocusState.Issue
    else -> null
}
