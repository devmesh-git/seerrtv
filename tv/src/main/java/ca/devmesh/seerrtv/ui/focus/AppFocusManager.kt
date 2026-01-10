package ca.devmesh.seerrtv.ui.focus

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.ui.MainScreenFocusState
import ca.devmesh.seerrtv.ui.BrowseFocusState

/**
 * Global focus manager that coordinates focus across different screens.
 * This extends the existing MainScreen focus management to work across the entire app.
 */
class AppFocusManager {
    // Main focus state - single source of truth for app-wide focus
    var currentFocus by mutableStateOf<AppFocusState>(AppFocusState.MainScreen(MainScreenFocusState.CategoryRow(ca.devmesh.seerrtv.viewmodel.MediaCategory.RECENTLY_ADDED)))
        private set

    // Focus transition history for debugging and handling back navigation
    private val focusHistory = mutableListOf<AppFocusState>()

    // Focus state storage for each screen/route
    private val savedFocusStates = mutableMapOf<String, AppFocusState>()

    // Set focus with proper logging and side effect handling
    fun setFocus(newFocus: AppFocusState) {
        // No change - avoid unnecessary updates
        if (newFocus == currentFocus) return

        if (BuildConfig.DEBUG) {
            Log.d("AppFocusManager", "🔄 App focus changing: $currentFocus -> $newFocus")
        }

        // Store previous focus for history
        focusHistory.add(currentFocus)
        if (focusHistory.size > 10) focusHistory.removeAt(0)

        // Set new focus state
        currentFocus = newFocus
    }

    // Helper for moving to top bar focus
    fun focusTopBar(isSearch: Boolean) {
        setFocus(AppFocusState.TopBar(if (isSearch) TopBarFocus.Search else TopBarFocus.Settings))
    }

    // Helper for moving to discovery screen focus
    fun focusDiscoveryScreen(focus: DiscoveryFocusState) {
        setFocus(AppFocusState.DiscoveryScreen(focus))
    }

    // Helper for moving to browse screen focus
    fun focusBrowseScreen(focus: BrowseFocusState) {
        setFocus(AppFocusState.BrowseScreen(focus))
    }

    // Save focus state for a specific route
    fun saveFocusState(route: String) {
        savedFocusStates[route] = currentFocus
        if (BuildConfig.DEBUG) {
            Log.d("AppFocusManager", "💾 Saved focus state for route '$route': $currentFocus")
        }
    }

    // Restore focus state for a specific route
    fun restoreFocusState(route: String): AppFocusState? {
        val savedState = savedFocusStates[route]
        if (savedState != null && BuildConfig.DEBUG) {
            Log.d("AppFocusManager", "🔄 Restoring focus state for route '$route': $savedState")
        }
        return savedState
    }

    // Clear all saved focus states
    fun clearSavedStates() {
        savedFocusStates.clear()
        if (BuildConfig.DEBUG) {
            Log.d("AppFocusManager", "🧹 Cleared all saved focus states")
        }
    }

}

/**
 * App-wide focus states that extend the existing MainScreen focus management
 */
sealed class AppFocusState {
    // Top bar focus states
    data class TopBar(val focus: TopBarFocus) : AppFocusState()
    
    // Screen-level states
    data class MainScreen(val focus: MainScreenFocusState) : AppFocusState()
    data class DiscoveryScreen(val focus: DiscoveryFocusState) : AppFocusState()
    data class BrowseScreen(val focus: BrowseFocusState) : AppFocusState()
    data class DetailsScreen(val focus: DetailsFocusState) : AppFocusState()

    override fun toString(): String {
        return when (this) {
            is TopBar -> "TopBar(${focus})"
            is MainScreen -> "MainScreen(${focus})"
            is DiscoveryScreen -> "DiscoveryScreen(${focus})"
            is BrowseScreen -> "BrowseScreen(${focus})"
            is DetailsScreen -> "DetailsScreen(${focus})"
        }
    }
}

/**
 * Top bar focus states
 */
enum class TopBarFocus {
    Search, Movies, Series, Settings
}

/**
 * Discovery screen focus states - matches MediaDiscoveryScreen.FocusedItem
 */
sealed class DiscoveryFocusState {
    object Search : DiscoveryFocusState()
    data class Grid(val row: Int, val column: Int) : DiscoveryFocusState()
    
    override fun toString(): String = when (this) {
        is Search -> "Search"
        is Grid -> "Grid(row=$row, col=$column)"
    }
}

/**
 * Details screen focus states - matches MediaDetails FocusArea constants
 */
sealed class DetailsFocusState {
    object Overview : DetailsFocusState()
    object ReadMore : DetailsFocusState()
    object Tags : DetailsFocusState()
    object Cast : DetailsFocusState()
    object Crew : DetailsFocusState()
    object SimilarMedia : DetailsFocusState()
    object FourKRegularOption : DetailsFocusState()
    object FourK4KOption : DetailsFocusState()
    
    // Action button focus areas
    object Play : DetailsFocusState()
    object RequestHD : DetailsFocusState()
    object Request4K : DetailsFocusState()
    object RequestSingle : DetailsFocusState()
    object ManageHD : DetailsFocusState()
    object Manage4K : DetailsFocusState()
    object ManageSingle : DetailsFocusState()
    object Trailer : DetailsFocusState()
    
    // Issue button focus areas
    object Issue : DetailsFocusState()
    object PlayIssueSplitLeft : DetailsFocusState()
    object PlayIssueSplitRight : DetailsFocusState()
    
    override fun toString(): String = when (this) {
        is Overview -> "Overview"
        is ReadMore -> "ReadMore"
        is Tags -> "Tags"
        is Cast -> "Cast"
        is Crew -> "Crew"
        is SimilarMedia -> "SimilarMedia"
        is FourKRegularOption -> "FourKRegularOption"
        is FourK4KOption -> "FourK4KOption"
        is Play -> "Play"
        is RequestHD -> "RequestHD"
        is Request4K -> "Request4K"
        is RequestSingle -> "RequestSingle"
        is ManageHD -> "ManageHD"
        is Manage4K -> "Manage4K"
        is ManageSingle -> "ManageSingle"
        is Trailer -> "Trailer"
        is Issue -> "Issue"
        is PlayIssueSplitLeft -> "PlayIssueSplitLeft"
        is PlayIssueSplitRight -> "PlayIssueSplitRight"
    }
}

/**
 * Composable to create and remember an AppFocusManager instance
 */
@Composable
fun rememberAppFocusManager(): AppFocusManager {
    return remember { AppFocusManager() }
}
