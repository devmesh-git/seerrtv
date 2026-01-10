package ca.devmesh.seerrtv.ui.position

import android.util.Log
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.model.BrowseModels

/**
 * Manages grid positions and selections for the MediaDiscoveryScreen's vertical grid.
 * This is separate from ScrollPositionManager to avoid conflicts with horizontal scroll management.
 */
object GridPositionManager {
    // Store grid positions as Pair<position, offset>
    private val gridPositions = mutableMapOf<String, Pair<Int, Int>>()
    
    // Store grid selections as Pair<row, column>
    private val gridSelections = mutableMapOf<String, Pair<Int, Int>>()
    
    // Track returning from details state for each screen
    private val returningFromDetailsFlags = mutableMapOf<String, Boolean>()
    
    // Store filter and sort state for browse screens
    private val browseFilters = mutableMapOf<String, BrowseModels.MediaFilters>()
    private val browseSorts = mutableMapOf<String, BrowseModels.SortOption>()
    
    /**
     * Request a position change. Returns true if the change is valid and was applied.
     */
    fun requestPositionChange(
        screenKey: String,
        position: Int,
        offset: Int,
        row: Int,
        column: Int,
        totalItems: Int
    ): Boolean {
        // Validate the position
        if (position >= totalItems) {
            if (BuildConfig.DEBUG) {
                Log.d("GridPositionManager", "❌ Position change rejected: position $position exceeds total items $totalItems")
            }
            return false
        }

        // Save the new position and selection
        // Store actual row position instead of hardcoding to 0
        gridPositions[screenKey] = Pair(position, offset)
        gridSelections[screenKey] = Pair(row, column)

        if (BuildConfig.DEBUG) {
            Log.d("GridPositionManager", "✅ Position change approved for $screenKey: pos=$position, offset=$offset, row=$row, col=$column")
        }

        return true
    }
    
    /**
     * Mark a screen as returning from details
     */
    fun markReturningFromDetails(screenKey: String, isReturning: Boolean) {
        returningFromDetailsFlags[screenKey] = isReturning
        if (BuildConfig.DEBUG) {
            Log.d("GridPositionManager", "🔙 Set returning state for $screenKey: $isReturning")
        }
    }
    
    /**
     * Check if a screen is returning from details
     */
    fun isReturningFromDetails(screenKey: String): Boolean {
        return returningFromDetailsFlags[screenKey] == true
    }
    
    /**
     * Clear returning flag after position has been restored
     */
    fun clearReturningFlag(screenKey: String) {
        returningFromDetailsFlags.remove(screenKey)
        if (BuildConfig.DEBUG) {
            Log.d("GridPositionManager", "🔄 Cleared returning flag for $screenKey")
        }
    }
    
    /**
     * Get the saved grid position
     */
    fun getSavedPosition(screenKey: String): Pair<Int, Int>? = gridPositions[screenKey]
    
    /**
     * Get the saved selection state
     */
    fun getSavedSelection(screenKey: String): Pair<Int, Int>? = gridSelections[screenKey]
    
    /**
     * Save selection state for a screen
     */
    fun saveSelection(screenKey: String, row: Int, column: Int) {
        gridSelections[screenKey] = Pair(row, column)
        if (BuildConfig.DEBUG) {
            Log.d("GridPositionManager", "💾 Saved selection for $screenKey: row=$row, column=$column")
        }
    }
    
    /**
     * Save position state for a screen
     */
    fun savePosition(screenKey: String, firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        gridPositions[screenKey] = Pair(firstVisibleItemIndex, firstVisibleItemScrollOffset)
        if (BuildConfig.DEBUG) {
            Log.d("GridPositionManager", "💾 Saved position for $screenKey: index=$firstVisibleItemIndex, offset=$firstVisibleItemScrollOffset")
        }
    }
    
    /**
     * Save filter state for a browse screen
     */
    fun saveBrowseFilters(screenKey: String, filters: BrowseModels.MediaFilters) {
        browseFilters[screenKey] = filters
        if (BuildConfig.DEBUG) {
            Log.d("GridPositionManager", "💾 Saved browse filters for $screenKey: ${filters.activeCount()} active")
        }
    }
    
    /**
     * Get saved filter state for a browse screen
     */
    fun getSavedBrowseFilters(screenKey: String): BrowseModels.MediaFilters? = browseFilters[screenKey]
    
    /**
     * Save sort state for a browse screen
     */
    fun saveBrowseSort(screenKey: String, sort: BrowseModels.SortOption) {
        browseSorts[screenKey] = sort
        if (BuildConfig.DEBUG) {
            Log.d("GridPositionManager", "💾 Saved browse sort for $screenKey: ${sort.displayName}")
        }
    }
    
    /**
     * Get saved sort state for a browse screen
     */
    fun getSavedBrowseSort(screenKey: String): BrowseModels.SortOption? = browseSorts[screenKey]
    
    /**
     * Clear all saved state for a screen (position, selection, filters, sort)
     */
    fun clearScreenState(screenKey: String) {
        gridPositions.remove(screenKey)
        gridSelections.remove(screenKey)
        returningFromDetailsFlags.remove(screenKey)
        browseFilters.remove(screenKey)
        browseSorts.remove(screenKey)
        if (BuildConfig.DEBUG) {
            Log.d("GridPositionManager", "🗑️ Cleared all state for $screenKey")
        }
    }
    
    /**
     * Clear only filter and sort state for a screen (keep position/selection)
     */
    fun clearBrowseState(screenKey: String) {
        browseFilters.remove(screenKey)
        browseSorts.remove(screenKey)
        if (BuildConfig.DEBUG) {
            Log.d("GridPositionManager", "🗑️ Cleared browse state for $screenKey")
        }
    }

}