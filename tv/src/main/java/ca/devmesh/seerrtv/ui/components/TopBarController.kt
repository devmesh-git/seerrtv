package ca.devmesh.seerrtv.ui.components

import androidx.compose.runtime.*
import androidx.navigation.NavController
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.ui.focus.AppFocusManager
import ca.devmesh.seerrtv.ui.focus.AppFocusState
import ca.devmesh.seerrtv.ui.focus.DpadController
import ca.devmesh.seerrtv.ui.focus.TopBarFocus
import ca.devmesh.seerrtv.ui.focus.createTopBarDpadConfig
import android.util.Log

/**
 * TopBar controller that handles its own DPAD navigation and business logic.
 * This makes TopBar behave like its own screen with its own handlers.
 */
@Composable
fun TopBarController(
    navController: NavController,
    appFocusManager: AppFocusManager,
    dpadController: DpadController,
    onOpenSettingsMenu: () -> Unit,
    onRefresh: (() -> Unit)? = null
) {
    // TopBar business logic handlers
    val handleUp: () -> Unit = {
        when (val focus = appFocusManager.currentFocus) {
            is AppFocusState.TopBar -> {
                // When in top bar, UP should highlight Search and trigger refresh
                if (BuildConfig.DEBUG) {
                    Log.d("TopBarController", "🔄 handleUp: Highlighting Search in top bar")
                }
                appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Search))
                
                // Trigger refresh if available
                if (onRefresh != null) {
                    if (BuildConfig.DEBUG) {
                        Log.d("TopBarController", "🔄 handleUp: Triggering refresh")
                    }
                    onRefresh()
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("TopBarController", "🔄 handleUp: No refresh handler available")
                    }
                }
            }
            else -> {
                if (BuildConfig.DEBUG) {
                    Log.d("TopBarController", "🔄 handleUp: Not in TopBar focus - $focus")
                }
            }
        }
    }
    
    val handleDown: () -> Unit = {
        when (val focus = appFocusManager.currentFocus) {
            is AppFocusState.TopBar -> {
                // Return to the calling screen based on current route
                val currentRoute = navController.currentDestination?.route
                val baseRoute = currentRoute?.substringBefore("/")
                when {
                    currentRoute == "main" -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("TopBarController", "🔄 handleDown: Returning to MainScreen")
                        }
                        appFocusManager.setFocus(AppFocusState.MainScreen(ca.devmesh.seerrtv.ui.MainScreenFocusState.CategoryRow(ca.devmesh.seerrtv.viewmodel.MediaCategory.RECENTLY_ADDED)))
                    }
                    baseRoute == "details" -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("TopBarController", "🔄 handleDown: Returning control to MediaDetails")
                        }
                        appFocusManager.setFocus(AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Overview))
                    }
                    baseRoute == "search" -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("TopBarController", "🔄 handleDown: Returning to Discovery Screen")
                        }
                        appFocusManager.setFocus(AppFocusState.DiscoveryScreen(ca.devmesh.seerrtv.ui.focus.DiscoveryFocusState.Search))
                    }
                    baseRoute == "mediaDiscovery" -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("TopBarController", "🔄 handleDown: Returning to Discovery Grid")
                        }
                        appFocusManager.setFocus(AppFocusState.DiscoveryScreen(ca.devmesh.seerrtv.ui.focus.DiscoveryFocusState.Grid(0, 0)))
                    }
                    baseRoute == "browse" -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("TopBarController", "🔄 handleDown: Returning to Browse Screen")
                        }
                        appFocusManager.setFocus(AppFocusState.BrowseScreen(ca.devmesh.seerrtv.ui.BrowseFocusState.Search))
                    }
                    baseRoute == "person" -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("TopBarController", "🔄 handleDown: Returning to Person screen (first available carousel)")
                        }
                        // Prefer KnownFor (Cast), else Crew
                        // We can't inspect Person VM here, so we pick Cast by default; screen will remap if Crew is the first available
                        appFocusManager.setFocus(AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Cast))
                    }
                    else -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("TopBarController", "🔄 handleDown: Unknown route - $currentRoute")
                        }
                    }
                }
            }
            else -> {
                if (BuildConfig.DEBUG) {
                    Log.d("TopBarController", "🔄 handleDown: Not in TopBar focus - $focus")
                }
            }
        }
    }
    
    // Ensure an initial focus target when entering TopBar on routes where Search icon is hidden
    LaunchedEffect(appFocusManager.currentFocus) {
        val currentRoute = navController.currentDestination?.route
        val baseRoute = currentRoute?.substringBefore("/")
        if (appFocusManager.currentFocus is AppFocusState.TopBar) {
            if (baseRoute == "search") {
                // Default to Settings so the user sees a highlight
                appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Settings))
            }
        }
    }

    val handleLeft: () -> Unit = {
        when (val focus = appFocusManager.currentFocus) {
            is AppFocusState.TopBar -> {
                val currentRoute = navController.currentDestination?.route
                val onMoviesBrowse = currentRoute == "browse/movies"
                val onSeriesBrowse = currentRoute == "browse/series"
                // On browse screens only 3 icons are visible; move left between them with no wrap.
                // Visual order: Settings | Series/Movies | Search
                when (focus.focus) {
                    TopBarFocus.Search -> {
                        if (onMoviesBrowse) {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Series))
                        } else if (onSeriesBrowse) {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Movies))
                        } else {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Movies))
                        }
                    }
                    TopBarFocus.Movies -> {
                        if (onSeriesBrowse) {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Settings))
                        } else {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Series))
                        }
                    }
                    TopBarFocus.Series -> {
                        if (onMoviesBrowse) {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Settings))
                        } else if (onSeriesBrowse) {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Movies))
                        } else {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Settings))
                        }
                    }
                    TopBarFocus.Settings -> {
                        // Left from Settings = end (no wrap on any screen)
                    }
                }
            }
            else -> {
                if (BuildConfig.DEBUG) {
                    Log.d("TopBarController", "🔄 handleLeft: Not in TopBar focus - $focus")
                }
            }
        }
    }
    
    val handleRight: () -> Unit = {
        when (val focus = appFocusManager.currentFocus) {
            is AppFocusState.TopBar -> {
                val currentRoute = navController.currentDestination?.route
                val onMoviesBrowse = currentRoute == "browse/movies"
                val onSeriesBrowse = currentRoute == "browse/series"
                // On browse screens only 3 icons are visible; move right between them with no wrap.
                when (focus.focus) {
                    TopBarFocus.Settings -> {
                        if (onMoviesBrowse) {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Series))
                        } else if (onSeriesBrowse) {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Movies))
                        } else {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Series))
                        }
                    }
                    TopBarFocus.Series -> {
                        if (onMoviesBrowse) {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Search))
                        } else {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Movies))
                        }
                    }
                    TopBarFocus.Movies -> {
                        if (onSeriesBrowse) {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Search))
                        } else {
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Search))
                        }
                    }
                    TopBarFocus.Search -> {
                        // Right from Search = end (no wrap on any screen)
                    }
                }
            }
            else -> {
                if (BuildConfig.DEBUG) {
                    Log.d("TopBarController", "🔄 handleRight: Not in TopBar focus - $focus")
                }
            }
        }
    }
    
    val handleEnter: () -> Unit = {
        when (val focus = appFocusManager.currentFocus) {
            is AppFocusState.TopBar -> {
                val currentRoute = navController.currentDestination?.route
                when (focus.focus) {
                    TopBarFocus.Search -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("TopBarController", "🔄 handleEnter: Navigating to search")
                        }
                        navController.navigate("search")
                    }
                    TopBarFocus.Movies -> {
                        if (currentRoute == "browse/movies") {
                            // Already on Movies browse; don't nest
                            if (BuildConfig.DEBUG) {
                                Log.d("TopBarController", "🔄 handleEnter: Already on movies browse, no-op")
                            }
                        } else {
                            if (BuildConfig.DEBUG) {
                                Log.d("TopBarController", "🔄 handleEnter: Navigating to movies browse")
                            }
                            navController.navigate("browse/movies")
                        }
                    }
                    TopBarFocus.Series -> {
                        if (currentRoute == "browse/series") {
                            // Already on Series browse; don't nest
                            if (BuildConfig.DEBUG) {
                                Log.d("TopBarController", "🔄 handleEnter: Already on series browse, no-op")
                            }
                        } else {
                            if (BuildConfig.DEBUG) {
                                Log.d("TopBarController", "🔄 handleEnter: Navigating to series browse")
                            }
                            navController.navigate("browse/series")
                        }
                    }
                    TopBarFocus.Settings -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("TopBarController", "🔄 handleEnter: Opening settings menu")
                        }
                        onOpenSettingsMenu()
                    }
                }
            }
            else -> {
                if (BuildConfig.DEBUG) {
                    Log.d("TopBarController", "🔄 handleEnter: Not in TopBar focus - $focus")
                }
            }
        }
    }
    
    // Register TopBar with DPAD controller
    val topBarConfig = createTopBarDpadConfig(
        route = "topbar",
        focusManager = appFocusManager,
        onUp = handleUp,
        onDown = handleDown,
        onLeft = handleLeft,
        onRight = handleRight,
        onEnter = handleEnter,
        onRefresh = onRefresh,
        onBack = {
            // TopBar back handling - could be used for special cases
            if (BuildConfig.DEBUG) {
                Log.d("TopBarController", "🔄 handleBack: TopBar back pressed")
            }
        }
    )
    
    LaunchedEffect(topBarConfig) {
        dpadController.registerScreen(topBarConfig)
        if (BuildConfig.DEBUG) {
            Log.d("TopBarController", "📱 Registered TopBar with DPAD controller")
        }
    }
}
