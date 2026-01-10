package ca.devmesh.seerrtv.ui.position

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import ca.devmesh.seerrtv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Singleton to manage user scroll position in various scrollable components
 * across the app. This helps save and restore scroll positions when the user
 * navigates away and returns to a screen.
 * 
 * NOTE: This implementation has been moved to the 'position' subpackage to avoid 
 * redeclaration conflicts with any existing implementation.
 */
object ScrollPositionManager {
    // Map to track user selection indices by specific keys (e.g., category name)
    private val lastUserViewIndices = mutableMapOf<String, Int>()
    
    // SnapshotStateMap to track scroll positions with (offset, index) pairs
    // This is useful for more complex scroll state restoration
    val positions = mutableStateMapOf<String, Pair<Int, Int>>()
    
    // Track scroll positions specifically for carousel LazyRows
    private val carouselScrollPositions = mutableMapOf<String, Int>()
    
    // Track categories that have recently been reset
    private val recentlyResetCategories = mutableMapOf<String, Long>()
    
    // Track categories that have been recently navigated to
    private val recentlyNavigatedCategories = mutableMapOf<String, Long>()
    
    // Maximum time any reset lock should be active, regardless of other conditions
    private const val MAX_LOCK_DURATION_MS = 200L // 200 ms absolute maximum
    
    // Track the last saved index for each category to determine navigation direction
    private val lastSavedIndices = mutableMapOf<String, Int>()
    
    // Time threshold to consider an index "fresh" enough not to be overridden (3 seconds)
    private const val INDEX_FRESHNESS_THRESHOLD_MS = 3000L // 3 seconds
    
    // Track the last known navigation direction for each category
    private val lastNavigationDirection = mutableMapOf<String, Boolean>()

    // Constants
    private const val RESET_TIMEOUT_MS = 100L // 100ms to clear reset status

    // Coroutine scope for cleanup operations
    private val cleanupScope = CoroutineScope(Dispatchers.Default)
    
    // Job to manage the cleanup coroutine
    private var cleanupJob: kotlinx.coroutines.Job? = null

    // Cache for time calculations to avoid repeated System.currentTimeMillis() calls
    private var lastCleanupTime = 0L

    init {
        // Start the cleanup job when the object is initialized
        cleanupJob = cleanupScope.launch {
            try {
                while (isActive) {
                    val currentTime = System.currentTimeMillis()
                    lastCleanupTime = currentTime

                    // Create lists of keys to remove to avoid concurrent modification
                    val resetKeysToRemove = recentlyResetCategories.entries
                        .filter { (_, timestamp) -> 
                            // Remove reset locks if they're older than the recency threshold OR if they've been 
                            // active too long (safety mechanism to prevent stuck navigation)
                            (currentTime - timestamp > RESET_TIMEOUT_MS) ||
                            (currentTime - timestamp > MAX_LOCK_DURATION_MS)
                        }
                        .map { it.key }

                    val navKeysToRemove = recentlyNavigatedCategories.entries
                        .filter { (_, timestamp) -> currentTime - timestamp > INDEX_FRESHNESS_THRESHOLD_MS }
                        .map { it.key }

                    // Remove the entries safely
                    resetKeysToRemove.forEach { key ->
                        recentlyResetCategories.remove(key)
                        if (BuildConfig.DEBUG) {
                            val timeSinceReset = currentTime - (recentlyResetCategories[key] ?: currentTime)
                            if (timeSinceReset > MAX_LOCK_DURATION_MS) {
                                Log.d("ScrollPosManager", "⚠️ SAFETY: Removed stuck reset lock for $key (exceeded maximum duration)")
                            } else {
                                Log.d("ScrollPosManager", "🧹 CLEANUP: Removed old reset category $key")
                            }
                        }
                    }

                    navKeysToRemove.forEach { key ->
                        recentlyNavigatedCategories.remove(key)
                        if (BuildConfig.DEBUG) {
                            Log.d("ScrollPosManager", "🧹 CLEANUP: Removed old navigation category $key")
                        }
                    }

                    delay(500) // Check every half second
                }
            } catch (_: CancellationException) {
                // Normal during app exit; avoid error noise
                if (BuildConfig.DEBUG) {
                    Log.d("ScrollPosManager", "🛑 Cleanup job cancelled during shutdown")
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("ScrollPosManager", "❌ Cleanup job failed", e)
                }
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d("ScrollPosManager", "🚀 Started scroll position cleanup job")
        }
    }

    /**
     * Clean up resources when the manager is no longer needed
     */
    fun cleanup() {
        cleanupJob?.cancel()
        cleanupJob = null
        if (BuildConfig.DEBUG) {
            Log.d("ScrollPosManager", "🛑 Stopped scroll position cleanup job")
        }
    }

    /**
     * Determine if we're navigating right based on navigation history
     * @param category The category key to check
     * @return True if we're navigating right (new index > previous index)
     */
    fun isNavigatingRight(category: String): Boolean {
        val currentIndex = lastUserViewIndices[category] ?: 0
        val lastIndex = lastSavedIndices[category] ?: 0
        
        // If the indices are the same, use the last known direction
        val isRight = if (currentIndex == lastIndex) {
            // Use last known direction or default to false
            lastNavigationDirection[category] == true
        } else {
            // Determine direction based on indices
            currentIndex > lastIndex
        }
        
        // Store the current direction for future reference
        lastNavigationDirection[category] = isRight
        
        if (BuildConfig.DEBUG) {
            Log.d("ScrollPosManager", "🔄 Navigation direction for $category: ${if (isRight) "RIGHT" else "LEFT"} (current=$currentIndex, last=$lastIndex)")
        }
        
        // Always update the last saved index to track history properly
        lastSavedIndices[category] = currentIndex
        
        return isRight
    }

    /**
     * Save the current index for a specific category or scrollable component
     * 
     * @param category The key identifying the scrollable component
     * @param index The current index to save
     */
    fun saveUserIndex(category: String, index: Int) {
        val oldIndex = lastUserViewIndices[category] ?: 0
        lastUserViewIndices[category] = index
        
        if (BuildConfig.DEBUG) {
            Log.d("ScrollPosManager", "📊 UPDATED: $category index $oldIndex -> $index")
        }
    }

    /**
     * Get the saved index for a specific category or scrollable component
     * @param category The key identifying the scrollable component
     * @return The saved index, or 0 if no index was saved
     */
    fun getUserIndex(category: String): Int {
        return lastUserViewIndices[category] ?: 0
    }

    /**
     * Set the post-navigation state (functionality removed)
     * @param isPostNavigation No longer used but kept for API compatibility
     */
    fun setPostNavigationState(isPostNavigation: Boolean) {
        if (BuildConfig.DEBUG) {
            Log.d("ScrollPosManager", "🚀 POST-NAV PROTECTION: DISABLED (locking removed)")
            
            // Log the current state of all saved indices
            if (isPostNavigation && lastUserViewIndices.isNotEmpty()) {
                val indicesStr = lastUserViewIndices.entries.joinToString { "${it.key}=${it.value}" }
                Log.d("ScrollPosManager", "📜 CURRENT INDICES: $indicesStr")
            }
        }
    }
    
    /**
     * Mark a category as navigated to (for logging only, no locks applied)
     * @param category The category key
     */
    fun markCategoryNavigated(category: String) {
        val index = lastUserViewIndices[category] ?: 0
        
        if (BuildConfig.DEBUG) {
            Log.d("ScrollPosManager", "🏁 NAVIGATION: $category marked as navigated with index $index")
        }
    }

    /**
     * Save the scroll position for a LazyRow component
     * @param category The key identifying the carousel
     * @param position The scroll position (first visible item index)
     */
    fun saveScrollPosition(category: String, position: Int) {
        val oldPosition = carouselScrollPositions[category] ?: 0
        carouselScrollPositions[category] = position
        
        if (BuildConfig.DEBUG) {
            Log.d("ScrollPosManager", "📜 SCROLL: $category position $oldPosition -> $position")
        }
    }
    
    /**
     * Get the saved scroll position for a LazyRow component
     * @param category The key identifying the carousel
     * @return The saved scroll position, or 0 if no position was saved
     */
    fun getScrollPosition(category: String): Int {
        return carouselScrollPositions[category] ?: 0
    }

    /**
     * Clear the stored index for a specific category and reset its position to 0
     * @param category The key identifying the scrollable component
     */
    fun clearStoredIndex(category: String) {
        // Clear stored indices
        lastUserViewIndices.remove(category)
        
        // Clear carousel scroll position
        carouselScrollPositions.remove(category)
        
        // Set new index to 0
        lastUserViewIndices[category] = 0
        
        // Reset position to (0,0)
        positions[category] = Pair(0, 0)
        
        // Reset carousel scroll position to 0
        carouselScrollPositions[category] = 0
        
        if (BuildConfig.DEBUG) {
            Log.d("ScrollPosManager", "🗑️ CLEARED: Reset $category to index 0 and position (0,0)")
        }
    }

    /**
     * Clear all saved positions
     */
    fun clear() {
        lastUserViewIndices.clear()
        positions.clear()
        
        if (BuildConfig.DEBUG) {
            Log.d("ScrollPosManager", "🧹 CLEARED: All stored positions and indices")
        }
    }
    
    /**
     * Clear all navigation locks (no-op now but kept for API compatibility)
     */
    fun clearAllNavigationLocks() {
        if (BuildConfig.DEBUG) {
            Log.d("ScrollPosManager", "🔓 UNLOCKED: No locks to clear (locking disabled)")
        }
    }

    /**
     * Reset the position data for a specific category key
     * This method clears both index and scroll positions
     * @param category The category key to reset
     */
    fun resetPositionFor(category: String) {
        if (BuildConfig.DEBUG) {
            Log.d("ScrollPosManager", "🗑️ CLEARED: Reset $category to index 0 and position (0,0)")
        }
        
        // Clear the user index
        lastUserViewIndices[category] = 0
        
        // Clear the position
        positions[category] = Pair(0, 0)
        
        // Clear the scroll position
        carouselScrollPositions[category] = 0
        
        // Update the last saved index
        lastSavedIndices[category] = 0
    }
} 