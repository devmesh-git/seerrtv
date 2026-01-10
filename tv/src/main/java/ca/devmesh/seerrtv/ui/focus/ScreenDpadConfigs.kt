package ca.devmesh.seerrtv.ui.focus

/**
 * Predefined DPAD configurations for different screens.
 * This provides a config-driven approach for screen-specific DPAD behavior.
 */

/**
 * Creates the DPAD configuration for the Main screen
 */
fun createMainScreenDpadConfig(
    route: String,
    focusManager: AppFocusManager,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onEnter: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
): ScreenDpadConfig {
    return ScreenDpadConfig(
        route = route,
        focusManager = focusManager,
        sections = listOf(
            DpadSection.TopBar,
            DpadSection.Grid
        ),
        transitions = DpadTransitions(
            upTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // Triggers refresh
                DpadSection.Grid to DpadSection.TopBar
            ),
            downTransitions = mapOf(
                DpadSection.TopBar to DpadSection.Grid,
                DpadSection.Grid to DpadSection.Grid // Stays in grid
            ),
            leftTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // Navigate between search/settings
                DpadSection.Grid to DpadSection.Grid // Navigate within grid
            ),
            rightTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // Navigate between search/settings
                DpadSection.Grid to DpadSection.Grid // Navigate within grid
            )
        ),
        onUp = onUp,
        onDown = onDown,
        onLeft = onLeft,
        onRight = onRight,
        onEnter = onEnter,
        onRefresh = onRefresh,
        onBack = onBack
    )
}

/**
 * Creates the DPAD configuration for the TopBar (persistent across screens)
 */
fun createTopBarDpadConfig(
    route: String,
    focusManager: AppFocusManager,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onEnter: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
): ScreenDpadConfig {
    return ScreenDpadConfig(
        route = route,
        focusManager = focusManager,
        sections = listOf(
            DpadSection.TopBar
        ),
        transitions = DpadTransitions(
            upTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar // Triggers refresh when available
            ),
            downTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar // Returns to calling screen
            ),
            leftTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar // Navigate between search/settings
            ),
            rightTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar // Navigate between search/settings
            )
        ),
        onUp = onUp,
        onDown = onDown,
        onLeft = onLeft,
        onRight = onRight,
        onEnter = onEnter,
        onRefresh = onRefresh,
        onBack = onBack
    )
}

/**
 * Creates the DPAD configuration for the Media Discovery screen
 */
fun createMediaDiscoveryDpadConfig(
    route: String,
    focusManager: AppFocusManager,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onEnter: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
): ScreenDpadConfig {
    return ScreenDpadConfig(
        route = route,
        focusManager = focusManager,
        sections = listOf(
            DpadSection.TopBar,
            DpadSection.Search,
            DpadSection.Grid
        ),
        transitions = DpadTransitions(
            upTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // No refresh by default
                DpadSection.Search to DpadSection.TopBar,
                DpadSection.Grid to DpadSection.Search
            ),
            downTransitions = mapOf(
                DpadSection.TopBar to DpadSection.Search,
                DpadSection.Search to DpadSection.Grid,
                DpadSection.Grid to DpadSection.Grid // Stays in grid
            ),
            leftTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // Navigate within top bar
                DpadSection.Search to DpadSection.Search, // Navigate within search
                DpadSection.Grid to DpadSection.Grid // Navigate within grid
            ),
            rightTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // Navigate within top bar
                DpadSection.Search to DpadSection.Search, // Navigate within search
                DpadSection.Grid to DpadSection.Grid // Navigate within grid
            )
        ),
        onUp = onUp,
        onDown = onDown,
        onLeft = onLeft,
        onRight = onRight,
        onEnter = onEnter,
        onRefresh = onRefresh,
        onBack = onBack
    )
}

/**
 * Creates the DPAD configuration for the Media Browse screen
 */
fun createMediaBrowseDpadConfig(
    route: String,
    focusManager: AppFocusManager,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onEnter: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
): ScreenDpadConfig {
    return ScreenDpadConfig(
        route = route,
        focusManager = focusManager,
        sections = listOf(
            DpadSection.TopBar,
            DpadSection.Search,
            DpadSection.Grid
        ),
        transitions = DpadTransitions(
            upTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // No refresh by default
                DpadSection.Search to DpadSection.TopBar,
                DpadSection.Grid to DpadSection.Search
            ),
            downTransitions = mapOf(
                DpadSection.TopBar to DpadSection.Search,
                DpadSection.Search to DpadSection.Grid,
                DpadSection.Grid to DpadSection.Grid // Stays in grid
            ),
            leftTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // Navigate within top bar
                DpadSection.Search to DpadSection.Search, // Navigate within search
                DpadSection.Grid to DpadSection.Grid // Navigate within grid
            ),
            rightTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // Navigate within top bar
                DpadSection.Search to DpadSection.Search, // Navigate within search
                DpadSection.Grid to DpadSection.Grid // Navigate within grid
            )
        ),
        onUp = onUp,
        onDown = onDown,
        onLeft = onLeft,
        onRight = onRight,
        onEnter = onEnter,
        onRefresh = onRefresh,
        onBack = onBack
    )
}

/**
 * Creates the DPAD configuration for the Media Details screen
 */
fun createMediaDetailsDpadConfig(
    route: String,
    focusManager: AppFocusManager,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onEnter: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
): ScreenDpadConfig {
    return ScreenDpadConfig(
        route = route,
        focusManager = focusManager,
        sections = listOf(
            DpadSection.TopBar,
            DpadSection.Details,
            DpadSection.Actions,
            DpadSection.SimilarMedia
        ),
        transitions = DpadTransitions(
            upTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // Navigate within TopBar (no refresh)
                DpadSection.Details to DpadSection.TopBar,
                DpadSection.Actions to DpadSection.Details,
                DpadSection.SimilarMedia to DpadSection.Actions
            ),
            downTransitions = mapOf(
                DpadSection.TopBar to DpadSection.Details,
                DpadSection.Details to DpadSection.Actions,
                DpadSection.Actions to DpadSection.SimilarMedia,
                DpadSection.SimilarMedia to DpadSection.SimilarMedia // Stays in similar media
            ),
            leftTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // Navigate between search/settings
                DpadSection.Details to DpadSection.Details, // Navigate within details
                DpadSection.Actions to DpadSection.Actions, // Navigate within actions
                DpadSection.SimilarMedia to DpadSection.SimilarMedia // Navigate within similar media
            ),
            rightTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar, // Navigate between search/settings
                DpadSection.Details to DpadSection.Details, // Navigate within details
                DpadSection.Actions to DpadSection.Actions, // Navigate within actions
                DpadSection.SimilarMedia to DpadSection.SimilarMedia // Navigate within similar media
            )
        ),
        onUp = onUp,
        onDown = onDown,
        onLeft = onLeft,
        onRight = onRight,
        onEnter = onEnter,
        onRefresh = onRefresh,
        onBack = onBack
    )
}

/**
 * Creates the DPAD configuration for the Person screen
 */
fun createPersonScreenDpadConfig(
    route: String,
    focusManager: AppFocusManager,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onEnter: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
): ScreenDpadConfig {
    return ScreenDpadConfig(
        route = route,
        focusManager = focusManager,
        sections = listOf(
            DpadSection.Details,
            DpadSection.Grid
        ),
        transitions = DpadTransitions(
            upTransitions = mapOf(
                DpadSection.Details to DpadSection.Details, // Navigate within details
                DpadSection.Grid to DpadSection.Details
            ),
            downTransitions = mapOf(
                DpadSection.Details to DpadSection.Grid,
                DpadSection.Grid to DpadSection.Grid // Stays in grid
            ),
            leftTransitions = mapOf(
                DpadSection.Details to DpadSection.Details, // Navigate within details
                DpadSection.Grid to DpadSection.Grid // Navigate within grid
            ),
            rightTransitions = mapOf(
                DpadSection.Details to DpadSection.Details, // Navigate within details
                DpadSection.Grid to DpadSection.Grid // Navigate within grid
            )
        ),
        onUp = onUp,
        onDown = onDown,
        onLeft = onLeft,
        onRight = onRight,
        onEnter = onEnter,
        onRefresh = onRefresh,
        onBack = onBack
    )
}

/**
 * Creates the DPAD configuration for the Config screen
 */
fun createConfigScreenDpadConfig(
    route: String,
    focusManager: AppFocusManager,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
): ScreenDpadConfig {
    return ScreenDpadConfig(
        route = route,
        focusManager = focusManager,
        sections = listOf(
            DpadSection.List
        ),
        transitions = DpadTransitions(
            upTransitions = mapOf(
                DpadSection.List to DpadSection.List // Navigate within list
            ),
            downTransitions = mapOf(
                DpadSection.List to DpadSection.List // Navigate within list
            ),
            leftTransitions = mapOf(
                DpadSection.List to DpadSection.List // Navigate within list
            ),
            rightTransitions = mapOf(
                DpadSection.List to DpadSection.List // Navigate within list
            )
        ),
        onRefresh = onRefresh,
        onBack = onBack
    )
}

/**
 * Helper function to get the appropriate DPAD configuration for a given route
 */
fun getDpadConfigForRoute(
    route: String,
    focusManager: AppFocusManager,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
): ScreenDpadConfig {
    return when (route) {
        "main" -> createMainScreenDpadConfig(route, focusManager, onRefresh, onBack)
        "search", "mediaDiscovery" -> createMediaDiscoveryDpadConfig(route, focusManager, onRefresh, onBack)
        "browse_movies", "browse_series" -> createMediaBrowseDpadConfig(route, focusManager, onRefresh, onBack)
        "details" -> createMediaDetailsDpadConfig(route, focusManager, onRefresh, onBack)
        "person" -> createPersonScreenDpadConfig(
            route = route,
            focusManager = focusManager,
            onRefresh = onRefresh,
            onBack = onBack
        )
        "config" -> createConfigScreenDpadConfig(route, focusManager, onRefresh, onBack)
        else -> {
            // Default configuration for unknown routes
            ScreenDpadConfig(
                route = route,
                focusManager = focusManager,
                sections = listOf(DpadSection.Grid),
                transitions = DpadTransitions(
                    upTransitions = mapOf(DpadSection.Grid to DpadSection.Grid),
                    downTransitions = mapOf(DpadSection.Grid to DpadSection.Grid),
                    leftTransitions = mapOf(DpadSection.Grid to DpadSection.Grid),
                    rightTransitions = mapOf(DpadSection.Grid to DpadSection.Grid)
                ),
                onRefresh = onRefresh,
                onBack = onBack
            )
        }
    }
}