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

/**
 * Centralized state manager for MediaDetails screen
 * Consolidates all state variables while preserving existing interfaces
 */
@Stable
class MediaDetailsStateManager {
    // Core media state
    var mediaDetailsState by mutableStateOf<ApiResult<MediaDetails>?>(null)
    var statusInfo by mutableStateOf<MediaStatusInfo?>(null)

    // Focus and navigation states
    var currentFocusArea by mutableIntStateOf(FocusArea.OVERVIEW)
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

