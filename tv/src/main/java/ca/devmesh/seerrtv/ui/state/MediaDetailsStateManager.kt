package ca.devmesh.seerrtv.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.MediaType
import ca.devmesh.seerrtv.model.SimilarMediaItem
import ca.devmesh.seerrtv.ui.components.MediaStatusInfo
import ca.devmesh.seerrtv.ui.focus.AppFocusManager
import ca.devmesh.seerrtv.ui.focus.AppFocusState
import ca.devmesh.seerrtv.ui.focus.toDetailsFocusState
import ca.devmesh.seerrtv.ui.focus.toFocusArea

/**
 * Centralized state manager for MediaDetails screen
 * Consolidates all state variables while preserving existing interfaces
 */
@Stable
class MediaDetailsStateManager(private val appFocusManager: AppFocusManager) {
    // Core media state
    var mediaDetailsState by mutableStateOf<ApiResult<MediaDetails>?>(null)
    var statusInfo by mutableStateOf<MediaStatusInfo?>(null)

    /**
     * The highlighted area, projected from [AppFocusManager] rather than stored alongside it.
     *
     * This used to be independent state mirrored to and from `appFocusManager.currentFocus` by a
     * pair of effects, which had no notion of which side was authoritative: both fired on the same
     * change and the manager-to-local direction was debounced, so anything written inside that
     * window was echoed back over. The guards that tried to suppress the echo could not tell
     * "nothing has been decided yet" from "the user is on the overview text", because both were
     * [FocusArea.OVERVIEW] — which is how returning from an external app could leave the screen
     * with no highlight at all.
     *
     * With one owner there is no echo to suppress, and [FocusArea.NONE] now means exactly one
     * thing: some other surface (top bar, another screen, a freshly restored process) holds the
     * focus, so the details screen has no highlight and needs to seed one.
     *
     * Reading [AppFocusManager.currentFocus] here is a snapshot read, so composables that read
     * this property recompose on focus changes just as they did with the old backing state.
     */
    var currentFocusArea: Int
        get() = (appFocusManager.currentFocus as? AppFocusState.DetailsScreen)
            ?.focus
            ?.toFocusArea()
            ?: FocusArea.NONE
        set(value) {
            // NONE is not the details screen's to assert — whoever took the focus already did.
            val target = value.toDetailsFocusState() ?: return
            appFocusManager.setFocus(AppFocusState.DetailsScreen(target))
        }
    var selectedCastIndex by mutableIntStateOf(0)
    var selectedCrewIndex by mutableIntStateOf(0)
    var selectedTagIndex by mutableIntStateOf(0)
    var selectedSimilarMediaIndex by mutableIntStateOf(0)
    var castCount by mutableIntStateOf(0)
    var crewCount by mutableIntStateOf(0)

    // UI state
    var isCompositionReady by mutableStateOf(false)
    var isFullOverviewShown by mutableStateOf(false)
    var isNavigating by mutableStateOf(false)
    var isDisposed by mutableStateOf(false)

    // Button activation states (preserved for integration)
    var is4kRequest by mutableStateOf(false)
    var showedRequestModal by mutableStateOf(false)

    // Content visibility states
    var hasReadMoreButton by mutableStateOf(false)
    var hasCast by mutableStateOf(false)
    var hasCrew by mutableStateOf(false)
    var hasTags by mutableStateOf(false)
    var hasTrailer by mutableStateOf(false)
    var isAvailable by mutableStateOf(false)
    var isPartiallyAvailable by mutableStateOf(false)
    var hasSimilarMedia by mutableStateOf(false)

    // Data states
    var leftmostTags by mutableStateOf(listOf<Int>())
    var tagPositions by mutableStateOf(listOf<Pair<Int, Float>>())
    var similarMediaItems by mutableStateOf<List<SimilarMediaItem>>(emptyList())

    // Timing states
    var lastScreenNavigationTime by mutableLongStateOf(0L)
    var last4kModalOpenTime by mutableLongStateOf(0L)
    var lastTrailerTriggerTime by mutableLongStateOf(0L)
    var compositionTrigger by mutableIntStateOf(0)
    var lastIssueModalCloseTime by mutableLongStateOf(0L)
    var lastIssueBackKeyTime by mutableLongStateOf(0L)

    // Trigger states
    var mediaPlayerTrigger by mutableIntStateOf(0)
    var watchTrailerTrigger by mutableIntStateOf(0)

    // Trailer overlay: when non-null, overlay is visible with this YouTube video ID
    var trailerOverlayVideoId by mutableStateOf<String?>(null)

    // Computed properties
    val currentMediaType: MediaType?
        get() = (mediaDetailsState as? ApiResult.Success)?.data?.mediaType

    val isMediaPlayable: Boolean
        get() = statusInfo?.let { it.isAvailable || it.isPartiallyAvailable } ?: false
}

/**
 * State management for return from PersonScreen
 * Only tracks the active carousel and its position, not all carousels simultaneously
 */
data class ReturnState(
    val isPending: Boolean = false,
    val focusArea: Int = FocusArea.OVERVIEW,
    val activeCarouselIndex: Int = 0, // Index within the active carousel
    val scrollOffset: Int = 0
)

val ReturnStateSaver: Saver<ReturnState, Any> = Saver(
    save = { state ->
        arrayListOf(
            state.isPending,
            state.focusArea,
            state.activeCarouselIndex,
            state.scrollOffset
        )
    },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        val list = saved as ArrayList<*>
        ReturnState(
            isPending = list[0] as Boolean,
            focusArea = list[1] as Int,
            activeCarouselIndex = list[2] as Int,
            scrollOffset = list[3] as Int
        )
    }
)

