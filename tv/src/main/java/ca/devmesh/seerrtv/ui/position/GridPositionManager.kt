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
     * Request a position change. Returns true unless the screen has nothing to select.
     *
     * An out-of-range [position] is clamped rather than refused. Refusal used to be silent and
     * unrecoverable: MediaDiscoveryScreen's DPAD handlers only move when this returns true, so a
     * selection saved against a longer list — the shared discovery ViewModel resets itself to
     * page 1 whenever another target uses it — made every direction reject its own move and left
     * the grid unnavigable until the process was killed.
     */
    fun requestPositionChange(
        screenKey: String,
        position: Int,
        offset: Int,
        row: Int,
        column: Int,
        totalItems: Int
    ): Boolean {
        if (totalItems <= 0) {
            if (BuildConfig.DEBUG) {
                Log.d("GridPositionManager", "❌ Position change rejected: $screenKey has no items")
            }
            return false
        }

        val safePosition = position.coerceIn(0, totalItems - 1)
        // A clamped position no longer refers to the item the offset was measured against.
        val safeOffset = if (safePosition == position) offset else 0

        if (BuildConfig.DEBUG && safePosition != position) {
            Log.d("GridPositionManager", "✂️ Clamped position $position to $safePosition for $screenKey ($totalItems items)")
        }

        // Save the new position and selection
        // Store actual row position instead of hardcoding to 0
        gridPositions[screenKey] = Pair(safePosition, safeOffset)
        gridSelections[screenKey] = Pair(row.coerceAtLeast(0), column.coerceAtLeast(0))

        if (BuildConfig.DEBUG) {
            Log.d("GridPositionManager", "✅ Position change approved for $screenKey: pos=$safePosition, offset=$safeOffset, row=$row, col=$column")
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
     * Get the saved selection clamped to what [totalItems] can actually address, repairing the
     * stored entry when it has drifted out of range.
     *
     * Selections outlive the list they were made against: this manager is a process-wide object
     * keyed per screen, while the results behind it are refetched from page 1 whenever the shared
     * discovery ViewModel is pointed at another target. Reading an unvalidated selection is what
     * left the studio/network grids unnavigable — every DPAD handler computed a move from a row
     * that no longer existed, failed its own bounds check, and did nothing.
     */
    fun getValidSelection(screenKey: String, totalItems: Int, numberOfColumns: Int): Pair<Int, Int>? {
        if (totalItems <= 0 || numberOfColumns <= 0) return null
        val saved = gridSelections[screenKey] ?: return null

        val maxRow = (totalItems - 1) / numberOfColumns
        val row = saved.first.coerceIn(0, maxRow)
        // The last row is usually short, so the column ceiling depends on the row we landed on.
        val itemsInRow = if (row == maxRow) totalItems - row * numberOfColumns else numberOfColumns
        val column = saved.second.coerceIn(0, itemsInRow - 1)

        val valid = Pair(row, column)
        if (valid != saved) {
            gridSelections[screenKey] = valid
            // The stored scroll index was measured against the same stale list.
            gridPositions[screenKey] = Pair((row * numberOfColumns).coerceIn(0, totalItems - 1), 0)
            if (BuildConfig.DEBUG) {
                Log.d("GridPositionManager", "✂️ Repaired stale selection for $screenKey: $saved -> $valid ($totalItems items)")
            }
        }

        return valid
    }
    
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