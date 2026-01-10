package ca.devmesh.seerrtv.ui

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.res.stringResource
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.model.BrowseModels
import ca.devmesh.seerrtv.model.MediaType
import ca.devmesh.seerrtv.navigation.rememberNavigationManager
import ca.devmesh.seerrtv.navigation.LocalNavController
import ca.devmesh.seerrtv.ui.components.AuthenticationErrorHandler
import ca.devmesh.seerrtv.ui.components.DiscoveryGrid
import ca.devmesh.seerrtv.ui.components.MainTopBar
import ca.devmesh.seerrtv.ui.components.ActionButton
import ca.devmesh.seerrtv.ui.components.CustomSearchBar
import ca.devmesh.seerrtv.ui.components.SortMenu
import ca.devmesh.seerrtv.ui.components.FiltersDrawer
import ca.devmesh.seerrtv.ui.components.FilterScreen
import ca.devmesh.seerrtv.ui.components.TopBarMode
import ca.devmesh.seerrtv.ui.focus.AppFocusManager
import ca.devmesh.seerrtv.ui.focus.AppFocusState
import ca.devmesh.seerrtv.ui.focus.DpadController
import ca.devmesh.seerrtv.ui.focus.TopBarFocus
import ca.devmesh.seerrtv.ui.focus.ScreenDpadConfig
import ca.devmesh.seerrtv.ui.focus.DpadTransitions
import ca.devmesh.seerrtv.ui.focus.DpadSection
import ca.devmesh.seerrtv.ui.position.GridPositionManager
import ca.devmesh.seerrtv.viewmodel.MediaDiscoveryViewModel
import coil3.ImageLoader

/**
 * Shared composable for both Movies and Series browsing screens.
 * Provides grid-based browsing with inline search, sorting, and filtering.
 */
@Composable
fun MediaBrowseScreen(
    mediaType: MediaType,
    viewModel: MediaDiscoveryViewModel,
    appFocusManager: AppFocusManager,
    dpadController: DpadController,
    imageLoader: ImageLoader,
    context: Context
) {
    val navigationManager = rememberNavigationManager(
        scope = rememberCoroutineScope(),
        navController = LocalNavController.current
    )
    val numberOfColumns = 6
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasMoreResults by viewModel.hasMoreResults.collectAsStateWithLifecycle()
    val currentFilters by viewModel.currentFilters.collectAsStateWithLifecycle()
    val currentSort by viewModel.currentSort.collectAsStateWithLifecycle()
    val activeFilterCount by viewModel.activeFilterCount.collectAsStateWithLifecycle()
    val showAuthenticationError by viewModel.showAuthenticationError.collectAsStateWithLifecycle()

    // Focus and selection state
    var focusedItem by rememberSaveable { mutableStateOf(BrowseFocusedItem.Search) }
    var selectedRow by rememberSaveable { mutableIntStateOf(0) }
    var selectedColumn by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var refocusControllerTick by remember { mutableIntStateOf(0) }
    var searchKeyboardTrigger by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // Create a unique key for this screen's state
    val screenKey = remember(mediaType) {
        when (mediaType) {
            MediaType.MOVIE -> "browse_movies"
            MediaType.TV -> "browse_series"
        }
    }

    // Computed properties for cleaner code - check these first
    val isReturningFromDetails by remember(screenKey) {
        derivedStateOf { GridPositionManager.isReturningFromDetails(screenKey) }
    }
    
    // Track if this is the initial composition (not returning from details)
    var isInitialComposition by remember(screenKey) { mutableStateOf(true) }
    var hasSetInitialFocus by remember(screenKey) { mutableStateOf(false) }
    
    // Get saved position and selection - only use if returning from details
    // Check isReturningFromDetails at composition time to avoid restoring stale data
    val shouldRestorePosition = remember(screenKey) { 
        GridPositionManager.isReturningFromDetails(screenKey)
    }
    val savedPosition = remember(screenKey) { 
        if (shouldRestorePosition) GridPositionManager.getSavedPosition(screenKey) else null
    }
    val savedSelection = remember(screenKey) { 
        if (shouldRestorePosition) GridPositionManager.getSavedSelection(screenKey) else null
    }

    // Create grid state - only use saved position if returning from details
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedPosition?.first ?: 0,
        initialFirstVisibleItemScrollOffset = savedPosition?.second ?: 0
    )
    
    // Clear saved state if we're not returning (fresh start)
    LaunchedEffect(screenKey) {
        if (!shouldRestorePosition) {
            // Clear any stale saved state when opening fresh (not returning from details)
            GridPositionManager.clearReturningFlag(screenKey)
            if (BuildConfig.DEBUG) {
                Log.d("MediaBrowseScreen", "🧹 Cleared stale state for $screenKey (fresh start)")
            }
        }
    }

    // State for modal visibility
    val showSortMenuState = remember(screenKey) { mutableStateOf(false) }
    val showFiltersDrawerState = remember(screenKey) { mutableStateOf(false) }
    
    // State for current filter screen
    var currentFilterScreen by remember(screenKey) { mutableStateOf<FilterScreen>(FilterScreen.Categories) }
    
    // Create delegated properties for easier access
    var showSortMenu by showSortMenuState
    var showFiltersDrawer by showFiltersDrawerState
    
    // Computed properties for cleaner code
    val isModalOpen by remember(showSortMenu, showFiltersDrawer) {
        derivedStateOf { showSortMenu || showFiltersDrawer }
    }

    // Consolidated position restoration - only restore when returning from details
    LaunchedEffect(isReturningFromDetails, searchResults.size, isLoading, isInitialComposition) {
        // Only restore if we're actually returning from details AND have data AND not initial composition
        if (!isReturningFromDetails || isInitialComposition) {
            // Not returning from details or initial composition - don't restore
            if (BuildConfig.DEBUG && !isReturningFromDetails && (GridPositionManager.getSavedPosition(screenKey) != null || GridPositionManager.getSavedSelection(screenKey) != null)) {
                Log.d("MediaBrowseScreen", "🧹 Not returning from details but found saved state - will be ignored")
            }
            return@LaunchedEffect
        }
        
        if (isLoading || searchResults.isEmpty()) {
            return@LaunchedEffect
        }

        // Get fresh values from manager
        val currentSavedPosition = GridPositionManager.getSavedPosition(screenKey)
        val currentSavedSelection = GridPositionManager.getSavedSelection(screenKey)

        if (BuildConfig.DEBUG) {
            Log.d(
                "MediaBrowseScreen",
                "🔍 Attempting to restore position - savedPosition=$currentSavedPosition, savedSelection=$currentSavedSelection"
            )
        }

        if (currentSavedPosition != null && currentSavedSelection != null) {
            // Only restore if we have enough items
            if (currentSavedPosition.first < searchResults.size) {
                // Restore selection and focus
                selectedRow = currentSavedSelection.first
                selectedColumn = currentSavedSelection.second
                focusedItem = BrowseFocusedItem.Grid
                appFocusManager.focusBrowseScreen(BrowseFocusState.Grid(currentSavedSelection.first, currentSavedSelection.second))

                // Restore scroll position with a slight delay to ensure layout is ready
                coroutineScope.launch {
                    delay(100)
                    gridState.scrollToItem(
                        index = currentSavedPosition.first,
                        scrollOffset = currentSavedPosition.second
                    )
                }

                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaBrowseScreen",
                        "🔄 Restored position: index=${currentSavedPosition.first}, offset=${currentSavedPosition.second}, selection=(${selectedRow}, ${selectedColumn})"
                    )
                }

                // Clear the returning flag in the manager now that we've restored position
                GridPositionManager.clearReturningFlag(screenKey)
            } else {
                // If saved index is out of bounds due to paged data, set a minimal restore
                selectedRow = 0
                selectedColumn = 0
                focusedItem = BrowseFocusedItem.Grid
                appFocusManager.focusBrowseScreen(BrowseFocusState.Grid(0, 0))
                GridPositionManager.clearReturningFlag(screenKey)
            }
        } else {
            // If we don't have enough items yet, trigger load more
            if (BuildConfig.DEBUG) {
                Log.d("MediaBrowseScreen", "⏳ Waiting for more items before restoring position...")
            }
            viewModel.loadMore()
        }
    }

    // Save position when navigating away
    DisposableEffect(Unit) {
        onDispose {
            // Set returning flag in GridPositionManager
            GridPositionManager.markReturningFromDetails(screenKey, true)

            if (BuildConfig.DEBUG) {
                Log.d(
                    "MediaBrowseScreen",
                    "💾 Saving position before navigation: row=$selectedRow, col=$selectedColumn, firstVisibleIndex=${gridState.firstVisibleItemIndex}"
                )
            }

            // Request position change through manager
            GridPositionManager.requestPositionChange(
                screenKey = screenKey,
                position = gridState.firstVisibleItemIndex,
                offset = gridState.firstVisibleItemScrollOffset,
                row = selectedRow,
                column = selectedColumn,
                totalItems = searchResults.size
            )

            if (BuildConfig.DEBUG) {
                Log.d(
                    "MediaBrowseScreen",
                    "💾 Saved grid state for $screenKey at index ${gridState.firstVisibleItemIndex}"
                )
            }
        }
    }

    // Set focus to Search by default when first arriving (not returning from details)
    LaunchedEffect(Unit) {
        // Only set to Search if we're NOT returning from details
        if (!isReturningFromDetails && !hasSetInitialFocus) {
            // Always set to Search on first composition when not returning
            if (BuildConfig.DEBUG) {
                Log.d("MediaBrowseScreen", "🔍 Setting focus to Search by default (initial composition, not returning from details)")
            }
            focusedItem = BrowseFocusedItem.Search
            appFocusManager.focusBrowseScreen(BrowseFocusState.Search)
            hasSetInitialFocus = true
            isInitialComposition = false
        } else if (!isReturningFromDetails && hasSetInitialFocus) {
            // Check if focus is already set to something other than Search
            // If it's Grid, that means AppFocusManager restored it - we want Search instead
            if (focusedItem == BrowseFocusedItem.Grid) {
                if (BuildConfig.DEBUG) {
                    Log.d("MediaBrowseScreen", "🔍 Setting focus to Search by default (not returning from details)")
                }
                focusedItem = BrowseFocusedItem.Search
                appFocusManager.focusBrowseScreen(BrowseFocusState.Search)
            }
        }
    }
    
    // Trigger keyboard when search box is selected (but not on initial composition)
    LaunchedEffect(focusedItem) {
        if (focusedItem == BrowseFocusedItem.Search && hasSetInitialFocus && !isInitialComposition) {
            // Increment trigger to request keyboard focus
            searchKeyboardTrigger++
            if (BuildConfig.DEBUG) {
                Log.d("MediaBrowseScreen", "🔍 Search box selected - triggering keyboard")
            }
        }
    }

    // Load initial popular content when screen opens
    LaunchedEffect(mediaType) {
        when (mediaType) {
            MediaType.MOVIE -> viewModel.loadPopularMovies()
            MediaType.TV -> viewModel.loadPopularSeries()
        }
    }

    // Handle search query changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3 || searchQuery.isEmpty()) {
            // Trigger browse with current filters and sort
            val filters = currentFilters ?: BrowseModels.MediaFilters.default(mediaType)
            val sort = currentSort
            when (mediaType) {
                MediaType.MOVIE -> viewModel.browseMovies(filters, sort)
                MediaType.TV -> viewModel.browseSeries(filters, sort)
            }
        }
    }

    // Create custom D-pad configuration for MediaBrowseScreen
    // Use remember with Unit key to create config only once and never recreate it
    // Handlers will capture mutable state by reference through the enclosing scope
    // Depend on screenKey to ensure config is recreated when switching between movies/series
    val browseConfig = remember(screenKey) {
        ScreenDpadConfig(
            route = screenKey,
            focusManager = appFocusManager,
        sections = listOf(
            DpadSection.TopBar,
            DpadSection.Search,
            DpadSection.Grid
        ),
        transitions = DpadTransitions(
            upTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar,
                DpadSection.Search to DpadSection.TopBar,
                DpadSection.Grid to DpadSection.Search
            ),
            downTransitions = mapOf(
                DpadSection.TopBar to DpadSection.Search,
                DpadSection.Search to DpadSection.Grid,
                DpadSection.Grid to DpadSection.Grid
            ),
            leftTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar,
                DpadSection.Search to DpadSection.Search,
                DpadSection.Grid to DpadSection.Grid
            ),
            rightTransitions = mapOf(
                DpadSection.TopBar to DpadSection.TopBar,
                DpadSection.Search to DpadSection.Search,
                DpadSection.Grid to DpadSection.Grid
            )
        ),
        onUp = {
            // Skip D-pad processing if a modal is open
            if (isModalOpen) return@ScreenDpadConfig
            
            when (focusedItem) {
                BrowseFocusedItem.Search,
                BrowseFocusedItem.Sort,
                BrowseFocusedItem.Filters -> {
                    // Move to top bar
                    val topBarFocus = if (mediaType == MediaType.MOVIE) TopBarFocus.Movies else TopBarFocus.Series
                    appFocusManager.setFocus(AppFocusState.TopBar(topBarFocus))
                }

                BrowseFocusedItem.Grid -> {
                    if (selectedRow == 0) {
                        // Move to search bar
                        focusedItem = BrowseFocusedItem.Search
                        appFocusManager.focusBrowseScreen(BrowseFocusState.Search)
                    } else {
                        // Move up in grid
                        val (newRow, newColumn) = navigateGridUp(selectedRow, selectedColumn, searchResults.size, numberOfColumns)
                        selectedRow = newRow
                        selectedColumn = newColumn
                        
                        appFocusManager.focusBrowseScreen(BrowseFocusState.Grid(newRow, newColumn))
                        ensureRowIsVisible(newRow, gridState, coroutineScope, numberOfColumns)
                    }
                }
            }
        },
        onDown = {
            // Skip D-pad processing if a modal is open
            if (isModalOpen) return@ScreenDpadConfig
            
            // Check if we are currently in the Top Bar
            if (appFocusManager.currentFocus is AppFocusState.TopBar) {
                // Move from Top Bar to Search
                focusedItem = BrowseFocusedItem.Search
                appFocusManager.focusBrowseScreen(BrowseFocusState.Search)
                return@ScreenDpadConfig
            }
            
            when (focusedItem) {
                BrowseFocusedItem.Search,
                BrowseFocusedItem.Sort,
                BrowseFocusedItem.Filters -> {
                    // Move directly to grid (first media item)
                    focusedItem = BrowseFocusedItem.Grid
                    selectedRow = 0
                    selectedColumn = 0
                    appFocusManager.focusBrowseScreen(BrowseFocusState.Grid(0, 0))
                }

                BrowseFocusedItem.Grid -> {
                    // Move down in grid
                    val (newRow, newColumn) = navigateGridDown(selectedRow, selectedColumn, searchResults.size, numberOfColumns)
                    selectedRow = newRow
                    selectedColumn = newColumn
                    
                    appFocusManager.focusBrowseScreen(BrowseFocusState.Grid(newRow, newColumn))
                    ensureRowIsVisible(newRow, gridState, coroutineScope, numberOfColumns)
                }
            }
        },
        onLeft = {
            // Skip D-pad processing if a modal is open
            if (isModalOpen) return@ScreenDpadConfig
            
            when (focusedItem) {
                BrowseFocusedItem.Sort -> {
                    // Move to search
                    focusedItem = BrowseFocusedItem.Search
                    appFocusManager.focusBrowseScreen(BrowseFocusState.Search)
                }

                BrowseFocusedItem.Filters -> {
                    // Move to sort
                    focusedItem = BrowseFocusedItem.Sort
                    appFocusManager.focusBrowseScreen(BrowseFocusState.Sort)
                }

                BrowseFocusedItem.Grid -> {
                    // Move left in grid
                    val (newRow, newColumn) = navigateGridLeft(selectedRow, selectedColumn, searchResults.size, numberOfColumns)
                    val rowChanged = newRow != selectedRow
                    if (rowChanged || newColumn != selectedColumn) {
                        selectedRow = newRow
                        selectedColumn = newColumn
                        appFocusManager.focusBrowseScreen(BrowseFocusState.Grid(newRow, newColumn))
                        if (rowChanged) {
                            ensureRowIsVisible(newRow, gridState, coroutineScope, numberOfColumns)
                        }
                    }
                }

                else -> { /* No action */
                }
            }
        },
        onRight = {
            // Skip D-pad processing if a modal is open
            if (isModalOpen) return@ScreenDpadConfig
            
            when (focusedItem) {
                BrowseFocusedItem.Search -> {
                    // Move to sort
                    focusedItem = BrowseFocusedItem.Sort
                    appFocusManager.focusBrowseScreen(BrowseFocusState.Sort)
                }

                BrowseFocusedItem.Sort -> {
                    // Move to filters
                    focusedItem = BrowseFocusedItem.Filters
                    appFocusManager.focusBrowseScreen(BrowseFocusState.Filters)
                }

                BrowseFocusedItem.Filters -> {
                    // Move to grid
                    focusedItem = BrowseFocusedItem.Grid
                    appFocusManager.focusBrowseScreen(BrowseFocusState.Grid(0, 0))
                }

                BrowseFocusedItem.Grid -> {
                    // Move right in grid
                    val (newRow, newColumn) = navigateGridRight(selectedRow, selectedColumn, searchResults.size, numberOfColumns)
                    val rowChanged = newRow != selectedRow
                    if (rowChanged || newColumn != selectedColumn) {
                        selectedRow = newRow
                        selectedColumn = newColumn
                        appFocusManager.focusBrowseScreen(BrowseFocusState.Grid(newRow, newColumn))
                        if (rowChanged) {
                            ensureRowIsVisible(newRow, gridState, coroutineScope, numberOfColumns)
                        }
                    }
                }
            }
        },
        onEnter = {
            // Don't handle Enter events when modals are actually open - let them handle their own navigation
            if (isModalOpen) {
                if (BuildConfig.DEBUG) {
                    Log.d("MediaBrowseScreen", "Enter event ignored - modal is open: showSortMenu=$showSortMenu, showFiltersDrawer=$showFiltersDrawer")
                }
                return@ScreenDpadConfig
            }
            
            if (BuildConfig.DEBUG) {
                Log.d("MediaBrowseScreen", "Enter event - focusedItem=$focusedItem, showSortMenu=$showSortMenu, showFiltersDrawer=$showFiltersDrawer")
            }
            
            when (focusedItem) {
                BrowseFocusedItem.Search -> {
                    // Trigger keyboard when Enter is pressed on search field
                    searchKeyboardTrigger++
                    if (BuildConfig.DEBUG) {
                        Log.d("MediaBrowseScreen", "🔍 Enter pressed on search - triggering keyboard")
                    }
                }

                BrowseFocusedItem.Sort -> {
                    // Open sort menu
                    if (BuildConfig.DEBUG) {
                        Log.d("MediaBrowseScreen", "Sort button pressed - opening sort menu (current showSortMenu=$showSortMenu)")
                    }
                    showSortMenu = true
                    if (BuildConfig.DEBUG) {
                        Log.d("MediaBrowseScreen", "Sort button pressed - showSortMenu set to true")
                    }
                }

                BrowseFocusedItem.Filters -> {
                    // Open filters drawer
                    showFiltersDrawer = true
                    if (BuildConfig.DEBUG) {
                        Log.d("MediaBrowseScreen", "Filters button pressed - opening filters drawer")
                    }
                }

                BrowseFocusedItem.Grid -> {
                    // Navigate to media details
                    val index = selectedRow * numberOfColumns + selectedColumn
                    if (index < searchResults.size) {
                        val result = searchResults[index]
                        navigationManager.navigateToDetails(
                            mediaId = result.id.toString(),
                            mediaType = result.mediaType ?: "unknown",
                            popUpTo = screenKey
                        )
                    }
                }
            }
        },
        onBack = {
            // Check if any modal is open - if so, close the modal instead of navigating back
            // Same approach for both modals (no race condition)
            if (showSortMenu) {
                if (BuildConfig.DEBUG) {
                    Log.d("MediaBrowseScreen", "🔙 Back pressed - closing sort menu")
                }
                showSortMenu = false
            } else if (showFiltersDrawer && currentFilterScreen == FilterScreen.Categories) {
                // Close filters drawer from Categories screen (same as SortMenu)
                if (BuildConfig.DEBUG) {
                    Log.d("MediaBrowseScreen", "🔙 Back pressed - closing filters drawer")
                }
                showFiltersDrawer = false
            } else {
                // Navigate back to main screen
                navigationManager.navigateBack()
            }
        }
        )
    }

    // Handle back button when modal is open to prevent navigation
    // Same approach for both modals - consistent behavior, no race conditions
    BackHandler(enabled = showSortMenu || (showFiltersDrawer && currentFilterScreen == FilterScreen.Categories)) {
        if (BuildConfig.DEBUG) {
            Log.d("MediaBrowseScreen", "🔙 BackHandler: Intercepting back button - modal is open")
            Log.d("MediaBrowseScreen", "🔙 showSortMenu: $showSortMenu, showFiltersDrawer: $showFiltersDrawer, currentFilterScreen: $currentFilterScreen")
        }
        
        if (showSortMenu) {
            showSortMenu = false
        } else if (showFiltersDrawer && currentFilterScreen == FilterScreen.Categories) {
            // Close filters drawer from Categories screen (same as SortMenu)
            showFiltersDrawer = false
        }
    }
    
    // Register/unregister DpadController based on modal state
    LaunchedEffect(showSortMenu, showFiltersDrawer) {
        if (isModalOpen) {
            // Unregister when modals are open to let them handle events
            dpadController.unregisterScreen(screenKey)
            if (BuildConfig.DEBUG) {
                Log.d("MediaBrowseScreen", "📱 Unregistered MediaBrowse from DPAD controller (modal open)")
            }
        } else {
            // Register when no modals are open
            dpadController.registerScreen(browseConfig)
            dpadController.setCurrentRoute(screenKey)
            if (BuildConfig.DEBUG) {
                Log.d("MediaBrowseScreen", "📱 Registered MediaBrowse with DPAD controller")
            }
        }
    }

    // Save position and selection when they change
    LaunchedEffect(selectedRow, selectedColumn) {
        GridPositionManager.saveSelection(screenKey, selectedRow, selectedColumn)
    }

    // Track scroll position changes without reading frequently changing values in LaunchedEffect
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            GridPositionManager.savePosition(screenKey, index, offset)
        }
    }

    // Track previous filters to detect changes
    var previousFilters by remember(screenKey) { mutableStateOf<BrowseModels.MediaFilters?>(null) }
    
    // Reset grid position and save filters when they change
    LaunchedEffect(currentFilters) {
        if (previousFilters != null && previousFilters != currentFilters) {
            // Filters changed - reset grid to top
            coroutineScope.launch {
                gridState.scrollToItem(0)
                selectedRow = 0
                selectedColumn = 0
                if (BuildConfig.DEBUG) {
                    Log.d("MediaBrowseScreen", "🔄 Filters changed, resetting grid position to top")
                }
            }
        }
        
        // Save filters
        currentFilters?.let { filters ->
            GridPositionManager.saveBrowseFilters(screenKey, filters)
        }
        
        previousFilters = currentFilters
    }

    LaunchedEffect(currentSort) {
        GridPositionManager.saveBrowseSort(screenKey, currentSort)
    }

    // Handle authentication errors
    if (showAuthenticationError) {
        AuthenticationErrorHandler(
            isVisible = true,
            onRetry = { viewModel.retryLastAction() },
            onReconfigure = { viewModel.hideAuthenticationError() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Persistent top bar
            MainTopBar(
                context = context,
                activeMode = when (mediaType) {
                    MediaType.MOVIE -> TopBarMode.MOVIES
                    MediaType.TV -> TopBarMode.SERIES
                },
                onModeChange = { mode ->
                    when (mode) {
                        TopBarMode.MOVIES -> navigationManager.navigateToMoviesBrowse()
                        TopBarMode.SERIES -> navigationManager.navigateToSeriesBrowse()
                        else -> { /* Handle other modes */
                        }
                    }
                },
                isSearchFocused = appFocusManager.currentFocus is AppFocusState.TopBar &&
                        (appFocusManager.currentFocus as AppFocusState.TopBar).focus == TopBarFocus.Search,
                isMoviesFocused = appFocusManager.currentFocus is AppFocusState.TopBar &&
                        (appFocusManager.currentFocus as AppFocusState.TopBar).focus == TopBarFocus.Movies,
                isSeriesFocused = appFocusManager.currentFocus is AppFocusState.TopBar &&
                        (appFocusManager.currentFocus as AppFocusState.TopBar).focus == TopBarFocus.Series,
                isSettingsFocused = appFocusManager.currentFocus is AppFocusState.TopBar &&
                        (appFocusManager.currentFocus as AppFocusState.TopBar).focus == TopBarFocus.Settings,
                showRefreshHint = false,
                isInTopBar = appFocusManager.currentFocus is AppFocusState.TopBar,
                isRefreshRowVisible = false
            )

            // Header text
            Text(
                text = when (mediaType) {
                    MediaType.MOVIE -> stringResource(R.string.browse_movies)
                    MediaType.TV -> stringResource(R.string.browse_series)
                },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            // Search bar with action buttons in same row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isSearchSelected = when (val f = appFocusManager.currentFocus) {
                    is AppFocusState.BrowseScreen -> f.focus == BrowseFocusState.Search
                    else -> false
                }
                CustomSearchBar(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        searchQuery = newValue
                        viewModel.debouncedSearch(newValue)
                        // Reset row and column selection when search query changes
                        selectedRow = 0
                        selectedColumn = 0
                        // Scroll back to the top
                        coroutineScope.launch {
                            gridState.scrollToItem(0)
                        }
                    },
                    isSelected = isSearchSelected,
                    modifier = Modifier.weight(1f),
                    onFocus = {
                        focusedItem = BrowseFocusedItem.Search
                    },
                    hasSearchResults = searchResults.isNotEmpty(),
                    onNavigateToResults = {
                        // Move to first grid item
                        selectedRow = 0
                        selectedColumn = 0
                        focusedItem = BrowseFocusedItem.Grid
                        // Ensure first row is visible
                        coroutineScope.launch {
                            gridState.scrollToItem(0)
                        }
                    },
                    requestKeyboardTrigger = searchKeyboardTrigger,
                    onFocusLost = {
                        appFocusManager.focusBrowseScreen(BrowseFocusState.Search)
                        focusedItem = BrowseFocusedItem.Search
                        refocusControllerTick++
                    }
                )

                // Sort button
                val isSortSelected = when (val f = appFocusManager.currentFocus) {
                    is AppFocusState.BrowseScreen -> f.focus == BrowseFocusState.Sort
                    else -> false
                }
                if (BuildConfig.DEBUG) {
                    Log.d("MediaBrowseScreen", "Rendering Sort button - isSortSelected: $isSortSelected")
                }
                Box(
                    modifier = Modifier
                        .width(160.dp) // Increased fixed width to prevent wrapping
                        .focusable()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                focusedItem = BrowseFocusedItem.Sort
                                appFocusManager.focusBrowseScreen(BrowseFocusState.Sort)
                            }
                        }
                ) {
                    ActionButton(
                        text = stringResource(R.string.sort_title),
                        isFocused = isSortSelected,
                        backgroundColor = Color(0xFF2A2E3B)
                    )
                }

                // Filters button with active count
                val isFiltersSelected = when (val f = appFocusManager.currentFocus) {
                    is AppFocusState.BrowseScreen -> f.focus == BrowseFocusState.Filters
                    else -> false
                }
                if (BuildConfig.DEBUG) {
                    Log.d("MediaBrowseScreen", "Rendering Filters button - isFiltersSelected: $isFiltersSelected, activeFilterCount: $activeFilterCount")
                }
                Box(
                    modifier = Modifier
                        .width(160.dp) // Fixed width to prevent wrapping
                        .focusable()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                focusedItem = BrowseFocusedItem.Filters
                                appFocusManager.focusBrowseScreen(BrowseFocusState.Filters)
                            }
                        }
                ) {
                    ActionButton(
                        text = if (activeFilterCount > 0)
                            "${stringResource(R.string.filter_title)} ($activeFilterCount)"
                        else
                            stringResource(R.string.filter_title),
                        isFocused = isFiltersSelected,
                        backgroundColor = if (activeFilterCount > 0) Color(0xFF4A4A4A) else Color(0xFF2A2E3B)
                    )
                }
            }

            // Grid of results
            if (searchResults.isNotEmpty() || isLoading) {
                DiscoveryGrid(
                    results = searchResults,
                    isLoading = isLoading,
                    hasMoreResults = hasMoreResults,
                    gridState = gridState,
                    numberOfColumns = numberOfColumns,
                    selectedRow = selectedRow,
                    selectedColumn = selectedColumn,
                    focusedItem = when (focusedItem) {
                        BrowseFocusedItem.Grid -> FocusedItem.Grid
                        else -> FocusedItem.Search
                    },
                    onEndReached = {
                        if (hasMoreResults && !isLoading) {
                            viewModel.loadMore()
                        }
                    },
                    screenKey = screenKey,
                    imageLoader = imageLoader,
                    context = context,
                    modifier = Modifier.fillMaxHeight()
                )
            } else {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.browse_noResults),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (activeFilterCount > 0) {
                            Text(
                                text = "Try adjusting your filters",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                text = "Try a different search term",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Sort Menu Modal - render as overlay with BackHandler
        if (showSortMenu) {
            if (BuildConfig.DEBUG) {
                Log.d("MediaBrowseScreen", "🎨 Rendering SortMenu modal - showSortMenu=$showSortMenu")
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                SortMenu(
                    selectedSort = currentSort,
                    mediaType = mediaType,
                    onSortSelected = { newSort ->
                        // Trigger new search with updated sort (this will also update the sort state)
                        when (mediaType) {
                            MediaType.MOVIE -> viewModel.browseMovies(currentFilters ?: BrowseModels.MediaFilters.default(mediaType), newSort)
                            MediaType.TV -> viewModel.browseSeries(currentFilters ?: BrowseModels.MediaFilters.default(mediaType), newSort)
                        }
                    },
                    onDismiss = {
                        showSortMenu = false
                    }
                )
            }
        }

        // Filters Drawer Modal
        if (showFiltersDrawer) {
            FiltersDrawer(
                isVisible = showFiltersDrawer,
                viewModel = viewModel,
                filters = currentFilters ?: BrowseModels.MediaFilters.default(mediaType),
                onFiltersChange = { newFilters ->
                    viewModel.applyFilters(newFilters)
                },
                onClearAll = {
                    val emptyFilters = BrowseModels.MediaFilters.default(mediaType)
                    viewModel.applyFilters(emptyFilters)
                },
                onDismiss = {
                    showFiltersDrawer = false
                },
                onScreenChange = { screen ->
                    currentFilterScreen = screen
                    if (BuildConfig.DEBUG) {
                        Log.d("MediaBrowseScreen", "🎯 Filter screen changed to: $screen")
                    }
                }
            )
        }
    }
}

/**
 * Focus states for the browse screen
 */
enum class BrowseFocusedItem {
    Search, Sort, Filters, Grid
}

/**
 * Calculate the number of items in a specific row.
 */
private fun calculateItemsInRow(row: Int, totalItems: Int, columns: Int): Int {
    val maxRow = (totalItems - 1) / columns
    return if (row == maxRow) {
        // Last row: calculate remaining items
        totalItems - (row * columns)
    } else {
        columns
    }
}

/**
 * Adjust column to be within valid range for the given row.
 */
private fun adjustColumnForRow(column: Int, row: Int, totalItems: Int, columns: Int): Int {
    val itemsInRow = calculateItemsInRow(row, totalItems, columns)
    return minOf(column, itemsInRow - 1)
}

/**
 * Navigate up in the grid, returning new (row, column) position.
 */
private fun navigateGridUp(selectedRow: Int, selectedColumn: Int, totalItems: Int, columns: Int): Pair<Int, Int> {
    if (selectedRow == 0) {
        return Pair(selectedRow, selectedColumn) // Can't go up from first row
    }
    val newRow = selectedRow - 1
    val adjustedColumn = adjustColumnForRow(selectedColumn, newRow, totalItems, columns)
    return Pair(newRow, adjustedColumn)
}

/**
 * Navigate down in the grid, returning new (row, column) position.
 */
private fun navigateGridDown(selectedRow: Int, selectedColumn: Int, totalItems: Int, columns: Int): Pair<Int, Int> {
    val maxRow = (totalItems - 1) / columns
    val newRow = minOf(maxRow, selectedRow + 1)
    val adjustedColumn = adjustColumnForRow(selectedColumn, newRow, totalItems, columns)
    return Pair(newRow, adjustedColumn)
}

/**
 * Navigate left in the grid, returning new (row, column) position.
 */
private fun navigateGridLeft(selectedRow: Int, selectedColumn: Int, totalItems: Int, columns: Int): Pair<Int, Int> {
    if (selectedColumn > 0) {
        return Pair(selectedRow, selectedColumn - 1)
    } else if (selectedRow > 0) {
        val newRow = selectedRow - 1
        val itemsInRow = calculateItemsInRow(newRow, totalItems, columns)
        return Pair(newRow, itemsInRow - 1)
    }
    return Pair(selectedRow, selectedColumn) // Can't go left
}

/**
 * Navigate right in the grid, returning new (row, column) position.
 */
private fun navigateGridRight(selectedRow: Int, selectedColumn: Int, totalItems: Int, columns: Int): Pair<Int, Int> {
    val maxRow = (totalItems - 1) / columns
    val currentIndex = selectedRow * columns + selectedColumn
    
    if (selectedColumn < columns - 1 && (currentIndex + 1) < totalItems) {
        return Pair(selectedRow, selectedColumn + 1)
    } else if (selectedRow < maxRow) {
        val newRow = selectedRow + 1
        val adjustedColumn = adjustColumnForRow(0, newRow, totalItems, columns)
        return Pair(newRow, adjustedColumn)
    }
    return Pair(selectedRow, selectedColumn) // Can't go right
}

/**
 * Ensures the specified row is visible in the grid by scrolling to it.
 * This function handles the vertical scrolling behavior for D-pad navigation.
 */
private fun ensureRowIsVisible(
    row: Int,
    gridState: LazyGridState,
    coroutineScope: CoroutineScope,
    numberOfColumns: Int
) {
    if (BuildConfig.DEBUG) {
        Log.d("MediaBrowseScreen", "🎯 ensureRowIsVisible called for row $row")
    }

    // Calculate item index for the start of this row
    val rowStartIndex = row * numberOfColumns

    if (BuildConfig.DEBUG) {
        Log.d("MediaBrowseScreen", "📏 Row indices: start=$rowStartIndex")
        Log.d("MediaBrowseScreen", "👀 Current scroll position: ${gridState.firstVisibleItemIndex}")
    }

    coroutineScope.launch {
        gridState.animateScrollToItem(index = rowStartIndex)
        if (BuildConfig.DEBUG) {
            Log.d("MediaBrowseScreen", "🔄 Scrolled to row start index: $rowStartIndex")
        }
    }
}

/**
 * Focus states for the browse screen
 */
sealed class BrowseFocusState {
    object Search : BrowseFocusState()
    object Sort : BrowseFocusState()
    object Filters : BrowseFocusState()
    data class Grid(val row: Int, val column: Int) : BrowseFocusState()
}

