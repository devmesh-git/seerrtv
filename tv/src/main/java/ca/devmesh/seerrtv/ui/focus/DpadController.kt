package ca.devmesh.seerrtv.ui.focus

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.type
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.util.SafeKeyEventHandler
import ca.devmesh.seerrtv.ui.MainScreenFocusState
import ca.devmesh.seerrtv.ui.BrowseFocusState

/**
 * Centralized DPAD controller that manages navigation across all screens.
 * This builds upon the existing SafeKeyEventHandler patterns and integrates with AppFocusManager.
 */
interface DpadController {
    /**
     * Register a screen with its DPAD configuration
     */
    fun registerScreen(config: ScreenDpadConfig)
    
    /**
     * Unregister a screen when it's no longer active
     */
    fun unregisterScreen(route: String)
    
    /**
     * Handle key events for the current screen
     */
    fun onKeyEvent(event: KeyEvent): Boolean
    
    /**
     * Save the current DPAD state for a route
     */
    fun saveState(route: String)
    
    /**
     * Restore the DPAD state for a route
     */
    fun restoreState(route: String)
    
    /**
     * Get the current screen configuration
     */
    fun getCurrentScreenConfig(): ScreenDpadConfig?
    
    /**
     * Set the current route for the controller
     */
    fun setCurrentRoute(route: String)

    /**
     * Get a screen configuration by route name
     */
    fun getScreenConfig(route: String): ScreenDpadConfig?
}

/**
 * Configuration for a screen's DPAD behavior
 */
data class ScreenDpadConfig(
    val route: String,
    val focusManager: AppFocusManager,
    val sections: List<DpadSection>,
    val transitions: DpadTransitions,
    val onUp: (() -> Unit)? = null,
    val onDown: (() -> Unit)? = null,
    val onLeft: (() -> Unit)? = null,
    val onRight: (() -> Unit)? = null,
    val onEnter: (() -> Unit)? = null,
    val onRefresh: (() -> Unit)? = null,
    val onBack: (() -> Unit)? = null
)

/**
 * Represents a focusable section within a screen
 */
sealed class DpadSection {
    object TopBar : DpadSection()
    object Search : DpadSection()
    object Grid : DpadSection()
    object List : DpadSection()
    object Details : DpadSection()
    object Actions : DpadSection()
    object SimilarMedia : DpadSection()
    
    override fun toString(): String = when (this) {
        is TopBar -> "TopBar"
        is Search -> "Search"
        is Grid -> "Grid"
        is List -> "List"
        is Details -> "Details"
        is Actions -> "Actions"
        is SimilarMedia -> "SimilarMedia"
    }
}

/**
 * Defines how focus transitions work between sections
 */
data class DpadTransitions(
    val upTransitions: Map<DpadSection, DpadSection>,
    val downTransitions: Map<DpadSection, DpadSection>,
    val leftTransitions: Map<DpadSection, DpadSection>,
    val rightTransitions: Map<DpadSection, DpadSection>
)

/**
 * Implementation of the centralized DPAD controller
 */
class DpadControllerImpl : DpadController {
    private val registeredScreens = mutableMapOf<String, ScreenDpadConfig>()
    private var currentRoute: String? = null
    private val savedStates = mutableMapOf<String, DpadState>()
    // Time window during which we will consume Back KeyUp to avoid system handling after we popped
    private var consumeBackKeyUpUntil: Long = 0L
    
    override fun registerScreen(config: ScreenDpadConfig) {
        registeredScreens[config.route] = config
        if (BuildConfig.DEBUG) {
            Log.d("DpadController", "📱 Registered screen: ${config.route}")
        }
    }
    
    override fun unregisterScreen(route: String) {
        registeredScreens.remove(route)
        if (BuildConfig.DEBUG) {
            Log.d("DpadController", "📱 Unregistered screen: $route")
        }
    }
    
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val currentConfig = getCurrentScreenConfig() ?: return false

        // Proactively consume Back KeyUp shortly after we handled Back on KeyDown
        if (event.type == KeyEventType.KeyUp) {
            if (event.key == Key.Back) {
                val now = System.currentTimeMillis()
                if (now <= consumeBackKeyUpUntil) {
                    if (BuildConfig.DEBUG) {
                        Log.d("DpadController", "🔕 Consuming Back KeyUp within protection window")
                    }
                    return true
                }
            }
            return false
        }
        
        if (BuildConfig.DEBUG) {
            Log.d("DpadController", "🎯 Key event received: ${event.key} (${event.type})")
        }
        
        return SafeKeyEventHandler.handleKeyEventWithContext(
            keyEvent = event,
            context = "DpadController"
        ) { keyEvent ->
            val result = when (keyEvent.key) {
                Key.DirectionUp -> handleUp(currentConfig)
                Key.DirectionDown -> handleDown(currentConfig)
                Key.DirectionLeft -> handleLeft(currentConfig)
                Key.DirectionRight -> handleRight(currentConfig)
                Key.Enter, Key.DirectionCenter -> handleEnter(currentConfig)
                Key.Back -> handleBack(currentConfig)
                else -> false
            }
            
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "🎯 Key event handled: ${keyEvent.key} -> $result")
            }
            
            result
        }
    }
    
    private fun handleUp(config: ScreenDpadConfig): Boolean {
        if (config.onUp != null) {
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "⬆️ Up: ${config.route}")
            }
            config.onUp()
            return true
        }
        
        // Fallback to generic transition logic
        val currentFocus = config.focusManager.currentFocus
        val currentSection = getCurrentSection(currentFocus)
        
        // Check for refresh handler (Up from TopBar)
        if (currentSection == DpadSection.TopBar && config.onRefresh != null) {
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "🔄 Triggering refresh for ${config.route}")
            }
            config.onRefresh()
            return true
        }
        
        // Handle normal up transition
        val targetSection = config.transitions.upTransitions[currentSection]
        if (targetSection != null) {
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "⬆️ Up: $currentSection -> $targetSection")
            }
            return transitionToSection(config, targetSection)
        }
        
        return false
    }
    
    private fun handleDown(config: ScreenDpadConfig): Boolean {
        if (config.onDown != null) {
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "⬇️ Down: ${config.route}")
            }
            config.onDown()
            return true
        }
        
        // Fallback to generic transition logic
        val currentFocus = config.focusManager.currentFocus
        val currentSection = getCurrentSection(currentFocus)
        
        val targetSection = config.transitions.downTransitions[currentSection]
        if (targetSection != null) {
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "⬇️ Down: $currentSection -> $targetSection")
            }
            return transitionToSection(config, targetSection)
        }
        
        return false
    }
    
    private fun handleLeft(config: ScreenDpadConfig): Boolean {
        if (config.onLeft != null) {
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "⬅️ Left: ${config.route}")
            }
            config.onLeft()
            return true
        }
        
        // Fallback to generic transition logic
        val currentFocus = config.focusManager.currentFocus
        val currentSection = getCurrentSection(currentFocus)
        
        val targetSection = config.transitions.leftTransitions[currentSection]
        if (targetSection != null) {
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "⬅️ Left: $currentSection -> $targetSection")
            }
            return transitionToSection(config, targetSection)
        }
        
        return false
    }
    
    private fun handleRight(config: ScreenDpadConfig): Boolean {
        if (config.onRight != null) {
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "➡️ Right: ${config.route}")
            }
            config.onRight()
            return true
        }
        
        // Fallback to generic transition logic
        val currentFocus = config.focusManager.currentFocus
        val currentSection = getCurrentSection(currentFocus)
        
        val targetSection = config.transitions.rightTransitions[currentSection]
        if (targetSection != null) {
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "➡️ Right: $currentSection -> $targetSection")
            }
            return transitionToSection(config, targetSection)
        }
        
        return false
    }
    
    private fun handleEnter(config: ScreenDpadConfig): Boolean {
        // Check if TopBar is registered and currently focused
        val topBarConfig = registeredScreens["topbar"]
        if (topBarConfig != null) {
            val currentFocus = topBarConfig.focusManager.currentFocus
            if (currentFocus is AppFocusState.TopBar) {
                if (BuildConfig.DEBUG) {
                    Log.d("DpadController", "⏎ Enter: TopBar focused, routing to TopBar onEnter")
                }
                // Route to TopBar's onEnter handler
                if (topBarConfig.onEnter != null) {
                    topBarConfig.onEnter()
                    return true
                }
                return false
            }
        }
        
        if (config.onEnter != null) {
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "⏎ Enter: ${config.route}")
            }
            config.onEnter()
            return true
        }
        
        // Fallback to generic enter logic
        val currentFocus = config.focusManager.currentFocus
        val currentSection = getCurrentSection(currentFocus)
        
        if (BuildConfig.DEBUG) {
            Log.d("DpadController", "⏎ Enter: $currentSection")
        }
        
        // Handle enter based on current section
        return when (currentSection) {
            DpadSection.TopBar -> {
                // TopBar enter handling is managed by the individual screen
                false
            }
            DpadSection.Search -> {
                // Search enter handling is managed by the individual screen
                false
            }
            DpadSection.Grid, DpadSection.List -> {
                // Grid/List enter handling is managed by the individual screen
                false
            }
            else -> false
        }
    }
    
    private fun handleBack(config: ScreenDpadConfig): Boolean {
        if (config.onBack != null) {
            if (BuildConfig.DEBUG) {
                Log.d("DpadController", "🔙 Back: ${config.route}")
            }
            config.onBack()
            // After handling Back on KeyDown, consume the upcoming KeyUp to prevent system back
            consumeBackKeyUpUntil = System.currentTimeMillis() + 350L
            return true
        }
        return false
    }
    
    private fun getCurrentSection(focus: AppFocusState): DpadSection {
        return when (focus) {
            is AppFocusState.TopBar -> DpadSection.TopBar
            is AppFocusState.MainScreen -> {
                when (focus.focus) {
                    // TopBar navigation now handled by TopBarController
                    is MainScreenFocusState.CategoryRow, is MainScreenFocusState.MediaItem -> DpadSection.Grid
                }
            }
            is AppFocusState.DiscoveryScreen -> {
                when (focus.focus) {
                    is DiscoveryFocusState.Search -> DpadSection.Search
                    is DiscoveryFocusState.Grid -> DpadSection.Grid
                }
            }
            is AppFocusState.BrowseScreen -> {
                when (focus.focus) {
                    is BrowseFocusState.Search -> DpadSection.Search
                    is BrowseFocusState.Sort -> DpadSection.Search // Treat sort as search section
                    is BrowseFocusState.Filters -> DpadSection.Search // Treat filters as search section
                    is BrowseFocusState.Grid -> DpadSection.Grid
                }
            }
            is AppFocusState.DetailsScreen -> {
                when (focus.focus) {
                    is DetailsFocusState.Overview, is DetailsFocusState.ReadMore, is DetailsFocusState.Tags,
                    is DetailsFocusState.Cast, is DetailsFocusState.Crew -> DpadSection.Details
                    is DetailsFocusState.Play, is DetailsFocusState.RequestHD, is DetailsFocusState.Request4K,
                    is DetailsFocusState.RequestSingle, is DetailsFocusState.ManageHD, is DetailsFocusState.Manage4K,
                    is DetailsFocusState.ManageSingle, is DetailsFocusState.Trailer -> DpadSection.Actions
                    is DetailsFocusState.SimilarMedia -> DpadSection.SimilarMedia
                    else -> DpadSection.Details
                }
            }
        }
    }
    
    private fun transitionToSection(config: ScreenDpadConfig, targetSection: DpadSection): Boolean {
        return when (targetSection) {
            DpadSection.TopBar -> {
                // Check if TopBar has its own configuration (TopBarController)
                val topBarConfig = registeredScreens["topbar"]
                if (topBarConfig != null) {
                    // TopBar has its own configuration, don't handle transitions here
                    // The TopBarController will handle its own navigation
                    false
                } else {
                    // Fallback for screens that don't have TopBarController
                    val currentFocus = config.focusManager.currentFocus
                    if (currentFocus is AppFocusState.TopBar) {
                        // Toggle between Search and Settings
                        val newFocus = if (currentFocus.focus == TopBarFocus.Search) {
                            TopBarFocus.Settings
                        } else {
                            TopBarFocus.Search
                        }
                        config.focusManager.setFocus(AppFocusState.TopBar(newFocus))
                    } else {
                        config.focusManager.focusTopBar(true) // Default to search
                    }
                    true
                }
            }
            DpadSection.Search -> {
                when (config.route) {
                    "main" -> {
                        config.focusManager.focusTopBar(true) // Default to search
                        true
                    }
                    "search", "mediaDiscovery" -> {
                        config.focusManager.focusDiscoveryScreen(DiscoveryFocusState.Search)
                        true
                    }
                    else -> false
                }
            }
            DpadSection.Grid -> {
                when (config.route) {
                    "main" -> {
                        // For MainScreen Grid navigation, we need to implement the actual logic
                        // This should be handled by the screen-specific navigation handlers
                        // For now, return false to let the generic transition logic handle it
                        false
                    }
                    "search", "mediaDiscovery" -> {
                        config.focusManager.focusDiscoveryScreen(DiscoveryFocusState.Grid(0, 0))
                        true
                    }
                    else -> false
                }
            }
            else -> false
        }
    }
    
    override fun getCurrentScreenConfig(): ScreenDpadConfig? {
        // Check if TopBar is registered and currently focused
        val topBarConfig = registeredScreens["topbar"]
        if (topBarConfig != null) {
            val currentFocus = topBarConfig.focusManager.currentFocus
            if (currentFocus is AppFocusState.TopBar) {
                return topBarConfig
            }
        }
        
        // Check if we're in a BrowseScreen
        val browseConfig = registeredScreens["browse_movies"] ?: registeredScreens["browse_series"]
        if (browseConfig != null) {
            val currentFocus = browseConfig.focusManager.currentFocus
            if (currentFocus is AppFocusState.BrowseScreen) {
                return browseConfig
            }
        }
        
        // Fallback to current route
        return currentRoute?.let { registeredScreens[it] }
    }

    override fun getScreenConfig(route: String): ScreenDpadConfig? {
        return registeredScreens[route]
    }
    
    override fun saveState(route: String) {
        val config = registeredScreens[route] ?: return
        val state = DpadState(
            currentFocus = config.focusManager.currentFocus,
            timestamp = System.currentTimeMillis()
        )
        savedStates[route] = state
        if (BuildConfig.DEBUG) {
            Log.d("DpadController", "💾 Saved DPAD state for $route: $state")
        }
    }
    
    override fun restoreState(route: String) {
        val state = savedStates[route] ?: return
        val config = registeredScreens[route] ?: return
        
        config.focusManager.setFocus(state.currentFocus)
        if (BuildConfig.DEBUG) {
            Log.d("DpadController", "🔄 Restored DPAD state for $route: $state")
        }
    }
    
    override fun setCurrentRoute(route: String) {
        currentRoute = route
    }
}

/**
 * Represents the saved state of a screen's DPAD navigation
 */
data class DpadState(
    val currentFocus: AppFocusState,
    val timestamp: Long
)

/**
 * Composable to create and remember a DpadController instance
 */
@Composable
fun rememberDpadController(): DpadController {
    return remember { DpadControllerImpl() }
}
