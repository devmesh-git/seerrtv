package ca.devmesh.seerrtv.navigation

import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.ui.focus.AppFocusManager
import ca.devmesh.seerrtv.ui.focus.DpadController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * NavigationCoordinator serves as a single source of truth for navigation state management.
 * 
 * This coordinator eliminates conflicts between multiple navigation listeners by:
 * 1. Providing a single navigation listener that coordinates all navigation events
 * 2. Managing focus state restoration in a centralized way
 * 3. Handling back navigation with proper debouncing
 * 4. Preserving all existing business rules and functionality
 * 
 * The coordinator acts as a wrapper around existing systems, ensuring no breaking changes.
 */
class NavigationCoordinator(
    navController: NavController,
    private val appFocusManager: AppFocusManager,
    private val dpadController: DpadController,
    private val scope: CoroutineScope
) {
    // Single source of truth for current route
    private val _currentRoute = MutableStateFlow<String?>(null)

    // Single source of truth for previous route
    private val _previousRoute = MutableStateFlow<String?>(null)

    // Single source of truth for navigation entry processing
    private val _currentBackStackEntry = MutableStateFlow<String?>(null)
    val currentBackStackEntry: StateFlow<String?> = _currentBackStackEntry
    
    // Debouncing state
    private var ignoreBackUntil: Long = 0L
    
    // Single navigation listener - replaces all others
    private val navigationListener = NavController.OnDestinationChangedListener { _, destination, _ ->
        handleRouteChange(destination)
    }
    
    init {
        // Register the single navigation listener
        navController.addOnDestinationChangedListener(navigationListener)
        
        if (BuildConfig.DEBUG) {
            Log.d("NavigationCoordinator", "🚀 NavigationCoordinator initialized - single source of truth established")
        }
    }
    
    /**
     * Centralized route change handling - replaces all duplicate navigation listeners
     */
    private fun handleRouteChange(destination: NavDestination) {
        val newRoute = destination.route?.split("/")?.firstOrNull() ?: ""
        val previousRouteValue = _currentRoute.value
        
        if (BuildConfig.DEBUG) {
            Log.d("NavigationCoordinator", "🔄 Route changed: $previousRouteValue -> $newRoute (full route: ${destination.route})")
        }
        
        // Update route state
        _previousRoute.value = _currentRoute.value
        _currentRoute.value = newRoute
        
        // Handle focus state management
        handleFocusStateManagement(previousRouteValue, newRoute)
        
        // Handle navigation entry processing
        handleNavigationEntryProcessing(previousRouteValue, newRoute)
        
        // Handle DPAD controller state
        handleDpadControllerState(previousRouteValue, newRoute)
        
        // Handle back debouncing
        handleBackDebouncing(previousRouteValue, newRoute)
    }
    
    /**
     * Centralized focus state management - eliminates conflicts between multiple listeners
     */
    private fun handleFocusStateManagement(previousRoute: String?, newRoute: String) {
        // Save focus state for the previous route (with business rule preservation)
        if (previousRoute != null && previousRoute != newRoute) {
            // Skip saving when returning from details to avoid conflicts (preserves existing business rule)
            if (!(previousRoute == "details" && newRoute == "main")) {
                appFocusManager.saveFocusState(previousRoute)
                dpadController.saveState(previousRoute)
                
                if (BuildConfig.DEBUG) {
                    Log.d("NavigationCoordinator", "💾 Saved focus state for route '$previousRoute'")
                }
            }
        }
        
        // Restore focus state for the new route (with business rule preservation)
        val shouldRestoreFocus = when {
            // Skip restoration when entering mediaDiscovery (preserves existing business rule)
            newRoute == "mediaDiscovery" -> false
            // Skip restoration when returning from discovery (preserves existing business rule)
            previousRoute == "mediaDiscovery" && newRoute == "main" -> false
            // Skip restoration when returning from details (preserves existing business rule)
            previousRoute == "details" && newRoute == "main" -> false
            else -> true
        }
        
        if (shouldRestoreFocus) {
            val savedFocusState = appFocusManager.restoreFocusState(newRoute)
            if (savedFocusState != null) {
                appFocusManager.setFocus(savedFocusState)
                
                if (BuildConfig.DEBUG) {
                    Log.d("NavigationCoordinator", "🔄 Restored focus state for route '$newRoute': $savedFocusState")
                }
            }
        } else {
            if (BuildConfig.DEBUG) {
                Log.d("NavigationCoordinator", "⏭️ Skipped focus restoration for route '$newRoute' (business rule)")
            }
        }
    }
    
    /**
     * Centralized navigation entry processing - replaces duplicate logic in MainScreen
     */
    private fun handleNavigationEntryProcessing(previousRoute: String?, newRoute: String) {
        // Determine navigation entry type based on business rules
        val entryType = when {
            previousRoute == "mediaDiscovery" && newRoute == "main" -> "discovery_to_main"
            previousRoute == "details" && newRoute == "main" -> "details_to_main"
            else -> null
        }
        
        if (entryType != null) {
            _currentBackStackEntry.value = entryType
            
            if (BuildConfig.DEBUG) {
                Log.d("NavigationCoordinator", "🎯 Set currentBackStackEntry to: $entryType")
            }
            
            // Clear the entry after processing (preserves existing business rule)
            scope.launch {
                kotlinx.coroutines.delay(300) // Preserve existing delay
                _currentBackStackEntry.value = null
                
                if (BuildConfig.DEBUG) {
                    Log.d("NavigationCoordinator", "🧹 Cleared currentBackStackEntry")
                }
            }
        }
    }
    
    /**
     * Centralized DPAD controller state management
     */
    private fun handleDpadControllerState(previousRoute: String?, newRoute: String) {
        // Unregister previous screen
        if (previousRoute != null && previousRoute != newRoute) {
            dpadController.unregisterScreen(previousRoute)
        }
        
        // Set current route and register new screen
        dpadController.setCurrentRoute(newRoute)
    }
    
    /**
     * Centralized back debouncing - preserves existing business rules
     */
    private fun handleBackDebouncing(previousRoute: String?, newRoute: String) {
        when {
            // Debounce back after returning from discovery (preserves existing business rule)
            previousRoute == "mediaDiscovery" && newRoute == "main" -> {
                ignoreBackUntil = System.currentTimeMillis() + 350L
                if (BuildConfig.DEBUG) {
                    Log.d("NavigationCoordinator", "🔕 Debouncing back on main until $ignoreBackUntil after discovery return")
                }
            }
            // Debounce back after returning from details (preserves existing business rule)
            previousRoute == "details" && newRoute == "main" -> {
                ignoreBackUntil = System.currentTimeMillis() + 350L
                if (BuildConfig.DEBUG) {
                    Log.d("NavigationCoordinator", "🔕 Debouncing back on main until $ignoreBackUntil after details return")
                }
            }
        }
    }
}

/**
 * Composable to create and remember a NavigationCoordinator
 */
@androidx.compose.runtime.Composable
fun rememberNavigationCoordinator(
    navController: NavController,
    appFocusManager: AppFocusManager,
    dpadController: DpadController,
    scope: CoroutineScope
): NavigationCoordinator {
    return androidx.compose.runtime.remember(navController, appFocusManager, dpadController, scope) {
        NavigationCoordinator(navController, appFocusManager, dpadController, scope)
    }
}
