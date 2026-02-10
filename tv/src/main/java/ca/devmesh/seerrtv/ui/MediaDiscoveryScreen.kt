package ca.devmesh.seerrtv.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.model.Collection
import ca.devmesh.seerrtv.model.Movie
import ca.devmesh.seerrtv.model.Person
import ca.devmesh.seerrtv.model.SearchResult
import ca.devmesh.seerrtv.model.TV
import ca.devmesh.seerrtv.navigation.LocalNavController
import ca.devmesh.seerrtv.navigation.NavigationManager
import ca.devmesh.seerrtv.navigation.rememberNavigationManager
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.ui.components.AuthenticationErrorHandler
import ca.devmesh.seerrtv.ui.components.CustomSearchBar
import ca.devmesh.seerrtv.ui.components.DiscoveryGrid
import ca.devmesh.seerrtv.ui.components.MediaCard
import ca.devmesh.seerrtv.ui.focus.AppFocusManager
import ca.devmesh.seerrtv.ui.focus.AppFocusState
import ca.devmesh.seerrtv.ui.focus.DpadController
import ca.devmesh.seerrtv.ui.focus.DiscoveryFocusState
import ca.devmesh.seerrtv.ui.focus.TopBarFocus
import ca.devmesh.seerrtv.ui.focus.createMediaDiscoveryDpadConfig
import ca.devmesh.seerrtv.ui.position.GridPositionManager
import ca.devmesh.seerrtv.viewmodel.DiscoveryType
import ca.devmesh.seerrtv.viewmodel.MediaDiscoveryViewModel
import coil3.compose.AsyncImage
import coil3.ImageLoader
import coil3.request.crossfade
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun logDiscovery(message: String) {
    Log.d("MediaDiscoveryScreen", message)
}

@Composable
fun MediaDiscoveryScreen(
    viewModel: MediaDiscoveryViewModel,
    imageLoader: ImageLoader,
    context: Context,
    appFocusManager: AppFocusManager,
    dpadController: DpadController,
    discoveryType: DiscoveryType,
    initialKeyword: String = "",
    keywordText: String,
    timestamp: Long,
    image: String? = null,
    navigationManager: NavigationManager = rememberNavigationManager(
        scope = rememberCoroutineScope(),
        navController = LocalNavController.current
    )
) {
    val numberOfColumns = 6
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasMoreResults by viewModel.hasMoreResults.collectAsStateWithLifecycle()
    val showAuthenticationError by viewModel.showAuthenticationError.collectAsState()
    var focusedItem by rememberSaveable { mutableStateOf(FocusedItem.Search) }
    var selectedRow by rememberSaveable { mutableIntStateOf(0) }
    var selectedColumn by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf(if (discoveryType == DiscoveryType.SEARCH) initialKeyword else "") }

    // Create a unique key for this screen's state
    val screenKey = remember(discoveryType, initialKeyword) {
        when (discoveryType) {
            DiscoveryType.SEARCH -> "search_$initialKeyword"
            DiscoveryType.MOVIE_KEYWORDS -> "movie_keywords_$initialKeyword"
            DiscoveryType.TV_KEYWORDS -> "tv_keywords_$initialKeyword"
            DiscoveryType.MOVIE_GENRE -> "movie_genre_$initialKeyword"
            DiscoveryType.SERIES_GENRE -> "series_genre_$initialKeyword"
            DiscoveryType.STUDIO -> "studio_$initialKeyword"
            DiscoveryType.NETWORK -> "network_$initialKeyword"
        }
    }

    // Get saved position and selection
    val savedPosition = remember(screenKey) { GridPositionManager.getSavedPosition(screenKey) }
    val savedSelection = remember(screenKey) { GridPositionManager.getSavedSelection(screenKey) }

    // Initialize selection state from saved values
    LaunchedEffect(savedSelection) {
        savedSelection?.let { (row, column) ->
            selectedRow = row
            selectedColumn = column
        }
    }

    // Create grid state with saved position
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedPosition?.first ?: 0,
        initialFirstVisibleItemScrollOffset = savedPosition?.second ?: 0
    )

    // Track current selection from GridPositionManager
    val currentSelection = remember { mutableStateOf(savedSelection ?: Pair(0, 0)) }

    // Update currentSelection whenever selectedRow or selectedColumn changes
    LaunchedEffect(selectedRow, selectedColumn) {
        currentSelection.value = Pair(selectedRow, selectedColumn)
        if (BuildConfig.DEBUG) {
            Log.d(
                "MediaDiscoveryScreen",
                "🔄 Updated currentSelection to (${selectedRow}, ${selectedColumn})"
            )
        }
    }

    // Add state to track focused item changes
    val currentFocusedItem = remember { mutableStateOf(focusedItem) }
    LaunchedEffect(focusedItem) {
        currentFocusedItem.value = focusedItem
    }

    // Function to update focus and selection
    val updateFocusAndSelection: (FocusedItem, Int, Int) -> Boolean =
        { newFocus, newRow, newColumn ->
            if (BuildConfig.DEBUG) {
                Log.d(
                    "MediaDiscoveryScreen",
                    "🎯 Attempting to update focus to $newFocus, row=$newRow, col=$newColumn"
                )
            }

            // Only update position in GridPositionManager if we're moving to grid
            if (newFocus == FocusedItem.Grid) {
                // Update local state since position change was already approved in key handler
                focusedItem = newFocus
                selectedRow = newRow
                selectedColumn = newColumn
                currentSelection.value = Pair(newRow, newColumn)
                appFocusManager.focusDiscoveryScreen(DiscoveryFocusState.Grid(newRow, newColumn))

                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaDiscoveryScreen",
                        "✅ Updated selection to row=$newRow, col=$newColumn"
                    )
                }
                true
            } else {
                // For non-grid focus (i.e., search), we can update directly
                focusedItem = newFocus
                selectedRow = newRow
                selectedColumn = newColumn
                appFocusManager.focusDiscoveryScreen(DiscoveryFocusState.Search)
                if (BuildConfig.DEBUG) {
                    logDiscovery("✅ Updated focus to search")
                }
                true
            }
        }

    val coroutineScope = rememberCoroutineScope()
    val composeFocusManager = LocalFocusManager.current
    var isSearchTextFieldFocused by remember { mutableStateOf(false) }
    var refocusControllerTick by remember { mutableIntStateOf(0) }
    var isSearchMode by rememberSaveable { mutableStateOf(discoveryType == DiscoveryType.SEARCH) }
    val controllerFocusRequester = remember { FocusRequester() }

    // Track whether we're loading initial results or loading more
    var isInitialLoad by remember { mutableStateOf(true) }
    var previousResultCount by remember { mutableIntStateOf(0) }

    // Trigger to request keyboard focus in the search field on DPAD Enter
    var searchKeyboardTrigger by remember { mutableIntStateOf(0) }

    // Pagination handling
    val isScrolling = remember { mutableStateOf(false) }
    val isNearBottom = remember { mutableStateOf(false) }

    // Add state to track if we're returning from details
    var isReturningFromDetails by remember { mutableStateOf(false) }
    var pendingScrollRestore by remember { mutableStateOf(false) }

    // Update isReturningFromDetails from GridPositionManager (more stable across recompositions)
    LaunchedEffect(Unit) {
        // Check if we're returning from details using the more stable GridPositionManager
        val isReturningFromManager = GridPositionManager.isReturningFromDetails(screenKey)
        isReturningFromDetails = isReturningFromManager
        pendingScrollRestore = isReturningFromManager

        if (BuildConfig.DEBUG) {
            Log.d(
                "MediaDiscoveryScreen",
                "🔍 Checking return state from manager - isReturningFromDetails=$isReturningFromDetails"
            )
        }
    }

    // Add effect to handle scroll position restoration immediately on composition
    LaunchedEffect(Unit) {
        // Get the return state from the manager since it's more stable
        val isReturningFromManager = GridPositionManager.isReturningFromDetails(screenKey)

        // Try to restore immediately if returning from details
        if ((isReturningFromDetails || isReturningFromManager) && pendingScrollRestore) {
            val savedPosition = GridPositionManager.getSavedPosition(screenKey)
            val savedSelection = GridPositionManager.getSavedSelection(screenKey)

            if (BuildConfig.DEBUG) {
                Log.d(
                    "MediaDiscoveryScreen",
                    "🔍 Immediate position restoration attempt - savedPosition=$savedPosition, savedSelection=$savedSelection, manager=$isReturningFromManager"
                )
            }

            if (savedPosition != null && savedSelection != null) {
                // Restore selection from manager using centralized updater
                updateFocusAndSelection(FocusedItem.Grid, savedSelection.first, savedSelection.second)
                currentFocusedItem.value = FocusedItem.Grid
                // Make sure we don't reset this selection
                isInitialLoad = false
                // Scroll immediately to the saved grid position so the item is visible and can be highlighted
                gridState.scrollToItem(
                    index = savedPosition.first,
                    scrollOffset = savedPosition.second
                )
                // Clear returning flags since we've restored immediately
                pendingScrollRestore = false
                GridPositionManager.clearReturningFlag(screenKey)
                // Ensure controller has focus after immediate restore
                controllerFocusRequester.requestFocus()
            }
        }
    }

    // Update the DisposableEffect to mark when we're returning from details
    DisposableEffect(Unit) {
        onDispose {
            // Set returning flag in the more stable GridPositionManager
            GridPositionManager.markReturningFromDetails(screenKey, true)
            isReturningFromDetails = true
            pendingScrollRestore = true

            if (BuildConfig.DEBUG) {
                Log.d(
                    "MediaDiscoveryScreen",
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
                    "MediaDiscoveryScreen",
                    "💾 Saved grid state for $screenKey at index ${gridState.firstVisibleItemIndex}"
                )
            }
        }
    }

    // Add effect to handle initial grid focus for non-search types
    LaunchedEffect(discoveryType, searchResults.size) {
        // Check again for the returning state from manager (more stable)
        val isReturningFromManager = GridPositionManager.isReturningFromDetails(screenKey)

        if (BuildConfig.DEBUG) {
            Log.d(
                "MediaDiscoveryScreen",
                "🔍 Initial focus check - isInitialLoad=$isInitialLoad, isReturningFromDetails=$isReturningFromDetails, " +
                        "hasResults=${searchResults.isNotEmpty()}, managerReturningState=$isReturningFromManager"
            )
        }

        // Only set initial focus if not returning from details (check both local and manager state)
        if (discoveryType != DiscoveryType.SEARCH && searchResults.isNotEmpty() && isInitialLoad
            && !isReturningFromDetails && !isReturningFromManager
        ) {
            // Request initial position from manager first
            if (GridPositionManager.requestPositionChange(
                    screenKey = screenKey,
                    position = 0,
                    offset = 0,
                    row = 0,
                    column = 0,
                    totalItems = searchResults.size
                )
            ) {
                // Only update local state after manager approves
                focusedItem = FocusedItem.Grid
                selectedRow = 0
                selectedColumn = 0
                currentSelection.value = Pair(0, 0)
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaDiscoveryScreen",
                        "🎯 Initial focus set to first grid item on first load"
                    )
                }
            }
        } else if ((isReturningFromDetails || isReturningFromManager) && searchResults.isNotEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    "MediaDiscoveryScreen",
                    "🔍 Skipping initial focus reset because we're returning from details screen (local=$isReturningFromDetails, manager=$isReturningFromManager)"
                )
            }
        }
    }

    // Add effect to handle scroll position restoration after data load
    LaunchedEffect(searchResults.size, isLoading) {
        // Again check the returning state from manager (more stable)
        val isReturningFromManager = GridPositionManager.isReturningFromDetails(screenKey)

        if ((isReturningFromDetails || isReturningFromManager) && !isLoading && searchResults.isNotEmpty() && pendingScrollRestore) {
            val savedPosition = GridPositionManager.getSavedPosition(screenKey)
            val savedSelection = GridPositionManager.getSavedSelection(screenKey)

            if (BuildConfig.DEBUG) {
                Log.d(
                    "MediaDiscoveryScreen",
                    "🔍 Attempting to restore position - isReturningFromDetails=$isReturningFromDetails, " +
                            "managerReturningState=$isReturningFromManager, " +
                            "pendingScrollRestore=$pendingScrollRestore, savedPosition=$savedPosition, savedSelection=$savedSelection"
                )
            }

            if (savedPosition != null && savedSelection != null) {
                // Only restore if we have enough items
                if (savedPosition.first < searchResults.size) {
                    // Restore selection and focus centrally
                    updateFocusAndSelection(FocusedItem.Grid, savedSelection.first, savedSelection.second)
                    currentFocusedItem.value = FocusedItem.Grid

                    // Ensure isInitialLoad is false to prevent overrides
                    isInitialLoad = false

                    // Restore scroll position with a slight delay to ensure layout is ready
                    delay(100)
                    gridState.scrollToItem(
                        index = savedPosition.first,
                        scrollOffset = savedPosition.second
                    )

                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "MediaDiscoveryScreen",
                            "🔄 Restored position after data load: index=${savedPosition.first}, offset=${savedPosition.second}, selection=(${selectedRow}, ${selectedColumn})"
                        )
                    }

                    pendingScrollRestore = false

                    // Clear the returning flag in the manager now that we've restored position
                    GridPositionManager.clearReturningFlag(screenKey)
                    // Ensure DPAD host is focused after restoration
                    controllerFocusRequester.requestFocus()
                } else {
                    // If saved index is out of bounds due to paged data, set a minimal restore
                    updateFocusAndSelection(FocusedItem.Grid, 0, 0)
                    controllerFocusRequester.requestFocus()
                }
            } else {
                    // If we don't have enough items yet, trigger load more
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "MediaDiscoveryScreen",
                            "⏳ Waiting for more items before restoring position..."
                        )
                    }
                    viewModel.loadMore()
            }
        }
    }

    // Sync local selection/highlight when TopBarController sends focus back to discovery grid
    LaunchedEffect(appFocusManager.currentFocus) {
        when (val focus = appFocusManager.currentFocus) {
            is AppFocusState.DiscoveryScreen -> {
                when (val df = focus.focus) {
                    is DiscoveryFocusState.Grid -> {
                        // Avoid overriding restoration flow
                        if (!pendingScrollRestore) {
                            val row = df.row
                            val col = df.column
                            // Persist selection in manager then update local highlight
                            if (searchResults.isNotEmpty()) {
                                GridPositionManager.requestPositionChange(
                                    screenKey = screenKey,
                                    position = row * numberOfColumns,
                                    offset = 0,
                                    row = row,
                                    column = col,
                                    totalItems = searchResults.size
                                )
                            }
                            focusedItem = FocusedItem.Grid
                            selectedRow = row
                            selectedColumn = col
                            currentSelection.value = Pair(row, col)
                            currentFocusedItem.value = FocusedItem.Grid
                            // Ensure row is visible and controller holds focus
                            ensureRowIsVisible(row, gridState, coroutineScope, numberOfColumns, screenKey)
                            controllerFocusRequester.requestFocus()
                        }
                    }
                    is DiscoveryFocusState.Search -> {
                        // Clear grid highlight when Search (or no search bar surrogate) is focused
                        focusedItem = FocusedItem.Search
                        currentFocusedItem.value = FocusedItem.Search
                    }
                }
            }
            else -> {}
        }
    }

    // Detect when we're near the bottom of the grid to load more data
    LaunchedEffect(gridState) {
        snapshotFlow {
            // Create a pair of layoutInfo and scrolling state to react to both
            Pair(gridState.layoutInfo, gridState.isScrollInProgress)
        }.collect { (layoutInfo, scrolling) ->
            isScrolling.value = scrolling

            if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val lastVisibleItem = layoutInfo.visibleItemsInfo.last()
                val totalItems = layoutInfo.totalItemsCount

                // Consider near bottom if last visible item index is within threshold of total
                val nearBottomThreshold = 6 // Reduced from 12 to be more aggressive
                val isNearBottomNow = lastVisibleItem.index >= totalItems - nearBottomThreshold &&
                        totalItems > 0 && hasMoreResults && !isLoading

                // Enhanced logging - log all positions without condition
                Log.d(
                    "MediaDiscoveryScreen",
                    "📜 Scroll position: ${lastVisibleItem.index}/$totalItems | " +
                            "Distance from bottom: ${totalItems - lastVisibleItem.index} items | " +
                            "First visible: ${layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0} | " +
                            "Visible items: ${layoutInfo.visibleItemsInfo.size} | " +
                            "Near bottom: $isNearBottomNow | " +
                            "Has more: $hasMoreResults | " +
                            "Loading: $isLoading | " +
                            "Scrolling: $scrolling"
                )

                // Always allow loading more when near bottom and not currently loading
                if (isNearBottomNow && !isNearBottom.value) {
                    isNearBottom.value = true
                    Log.d(
                        "MediaDiscoveryScreen",
                        "⬇️ TRIGGERING LOAD MORE - at item ${lastVisibleItem.index}/$totalItems"
                    )
                    viewModel.loadMore()
                } else if (!isNearBottomNow) {
                    isNearBottom.value = false
                }
            }
        }
    }

    // Handle media selection
    fun handleMediaSelection(mediaId: String, mediaType: String) {
        when (mediaType) {
            "person" -> navigationManager.navigateToPerson(mediaId)
            else -> navigationManager.navigateToDetails(mediaId, mediaType)
        }
    }

    // Handle config navigation
    fun handleConfigNavigation() {
        navigationManager.navigateToConfig()
    }

    LaunchedEffect(discoveryType, initialKeyword, timestamp) {
        // Load when: no results yet, OR we navigated to a different category (e.g. switched genres).
        // Skip load only when returning from details to the same category (preserves scroll position).
        val isReturningFromDetailsForThisScreen = GridPositionManager.isReturningFromDetails(screenKey)
        if (searchResults.isEmpty() || !isReturningFromDetailsForThisScreen) {
            if (discoveryType != DiscoveryType.SEARCH) {
                // Mark as initial load when switching to keyword discovery
                isInitialLoad = true
                when (discoveryType) {
                    DiscoveryType.MOVIE_KEYWORDS -> viewModel.discoverMoviesByKeyword(initialKeyword)
                    DiscoveryType.TV_KEYWORDS -> viewModel.discoverTVByKeyword(initialKeyword)
                    DiscoveryType.MOVIE_GENRE -> viewModel.discoverMoviesByGenre(initialKeyword)
                    DiscoveryType.SERIES_GENRE -> viewModel.discoverTVByGenre(initialKeyword)
                    DiscoveryType.STUDIO -> viewModel.discoverMoviesByStudio(initialKeyword)
                    DiscoveryType.NETWORK -> viewModel.discoverTVByNetwork(initialKeyword)
                }
            } else if (initialKeyword.isNotEmpty()) {
                // Mark as initial load when using initial keyword for search
                isInitialLoad = true
                searchQuery = initialKeyword
                viewModel.debouncedSearch(initialKeyword)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1D29))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 60.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                )
                .focusRequester(controllerFocusRequester)
                .focusable()
        ) {
            // Only show search bar when discovery type is SEARCH
            if (discoveryType == DiscoveryType.SEARCH) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isSearchSelected = when (val f = appFocusManager.currentFocus) {
                        is AppFocusState.DiscoveryScreen -> f.focus is DiscoveryFocusState.Search
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
                            // Mark as initial load for new search
                            isInitialLoad = true
                            // Scroll back to the top
                            coroutineScope.launch {
                                gridState.scrollToItem(0)
                            }
                        },
                        isSelected = isSearchSelected,
                        modifier = Modifier.weight(1f),
                        onFocus = {
                            focusedItem = FocusedItem.Search
                            isSearchTextFieldFocused = true
                            if (!isSearchMode) {
                                isSearchMode = true
                                searchQuery = ""
                            }
                        },
                        hasSearchResults = searchResults.isNotEmpty(),
                        onNavigateToResults = {
                            // Request position change from manager
                            if (GridPositionManager.requestPositionChange(
                                    screenKey = screenKey,
                                    position = 0,
                                    offset = 0,
                                    row = 0,
                                    column = 0,
                                    totalItems = searchResults.size
                                )
                            ) {
                                selectedRow = 0
                                selectedColumn = 0
                                focusedItem = FocusedItem.Grid
                                // Ensure first row is visible
                                coroutineScope.launch {
                                    gridState.scrollToItem(0)
                                }
                            }
                        },
                        requestKeyboardTrigger = searchKeyboardTrigger,
                        onFocusLost = {
                            isSearchTextFieldFocused = false
                            appFocusManager.focusDiscoveryScreen(DiscoveryFocusState.Search)
                            focusedItem = FocusedItem.Search
                            refocusControllerTick++
                        }
                    )
                }
            }

            // Display backdrop if we're not searching
            if (discoveryType in listOf(
                    DiscoveryType.MOVIE_KEYWORDS,
                    DiscoveryType.TV_KEYWORDS,
                    DiscoveryType.MOVIE_GENRE,
                    DiscoveryType.SERIES_GENRE,
                    DiscoveryType.STUDIO,
                    DiscoveryType.NETWORK
                )
            ) {
                CategoryBackdrop(
                    categoryType = discoveryType,
                    keywordText = keywordText,
                    context = context
                )
            }

            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                // Display category image only if not already shown in the backdrop
                if (image != null && discoveryType !in listOf(
                        DiscoveryType.MOVIE_GENRE,
                        DiscoveryType.SERIES_GENRE,
                        DiscoveryType.STUDIO,
                        DiscoveryType.NETWORK
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        // Image with gradient overlay
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(image)
                                .crossfade(true)
                                .build(),
                            contentDescription = keywordText,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Add a gradient overlay for better text readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x00000000),
                                            Color(0x99000000)
                                        )
                                    )
                                )
                        )

                        // Category title on image
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = keywordText,
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.7f),
                                        offset = Offset(2f, 2f),
                                        blurRadius = 4f
                                    )
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                // Replace the LaunchedEffect that was auto-focusing with a simpler one that just updates counts
                LaunchedEffect(searchResults.size) {
                    if (searchResults.isNotEmpty() && !isLoading) {
                        if (isInitialLoad || (previousResultCount > searchResults.size)) {
                            Log.d(
                                "MediaDiscoveryScreen",
                                "🔍 Initial results loaded: ${searchResults.size} items"
                            )
                            isInitialLoad = false
                        } else {
                            Log.d(
                                "MediaDiscoveryScreen",
                                "📊 More results loaded: ${searchResults.size - previousResultCount} new items"
                            )
                        }
                        // Update previous count
                        previousResultCount = searchResults.size
                    }
                }

                DiscoveryGrid(
                    results = searchResults,
                    isLoading = isLoading,
                    hasMoreResults = hasMoreResults,
                    gridState = gridState,
                    numberOfColumns = numberOfColumns,
                    selectedRow = selectedRow,
                    selectedColumn = selectedColumn,
                    focusedItem = focusedItem,
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
            } else if (!isLoading) {
                // No results state
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Display category backdrop if suitable
                        if (discoveryType in listOf(
                                DiscoveryType.MOVIE_KEYWORDS,
                                DiscoveryType.TV_KEYWORDS,
                                DiscoveryType.MOVIE_GENRE,
                                DiscoveryType.SERIES_GENRE,
                                DiscoveryType.STUDIO,
                                DiscoveryType.NETWORK
                            )
                        ) {
                            CategoryBackdrop(
                                categoryType = discoveryType,
                                keywordText = keywordText,
                                context = context
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                        }

                        Text(
                            text = context.getString(R.string.mediaDiscovery_noResults),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Initial loading indicator
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Display category backdrop if suitable
                        if (discoveryType in listOf(
                                DiscoveryType.MOVIE_KEYWORDS,
                                DiscoveryType.TV_KEYWORDS,
                                DiscoveryType.MOVIE_GENRE,
                                DiscoveryType.SERIES_GENRE,
                                DiscoveryType.STUDIO,
                                DiscoveryType.NETWORK
                            )
                        ) {
                            CategoryBackdrop(
                                categoryType = discoveryType,
                                keywordText = keywordText,
                                context = context
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                        } else {
                            // Display category image if available but not shown in backdrop
                            image?.let { imagePath ->
                                Box(
                                    modifier = Modifier
                                        .width(240.dp)
                                        .height(135.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(imagePath)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = keywordText,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = keywordText,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }
            }
        }

        // Add authentication error dialog on top of everything else
        if (showAuthenticationError) {
            AuthenticationErrorHandler(
                isVisible = true,
                onRetry = {
                    viewModel.hideAuthenticationError()
                    viewModel.retryLastAction()
                },
                onReconfigure = {
                    viewModel.hideAuthenticationError()
                    handleConfigNavigation()
                }
            )
        }
    }

    // Robustly return focus to the DPAD host after IME dismissal/focus loss
    LaunchedEffect(refocusControllerTick) {
        if (refocusControllerTick > 0) {
            // Next frame
            kotlinx.coroutines.android.awaitFrame()
            controllerFocusRequester.requestFocus()
            // Extra nudge after a short delay to survive IME animations
            delay(120)
            controllerFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(Unit) {
        // Register MediaDiscovery with the centralized DpadController
        val dpadConfig = createMediaDiscoveryDpadConfig(
            route = if (discoveryType == DiscoveryType.SEARCH) "search" else "mediaDiscovery",
            focusManager = appFocusManager,
            onUp = {
                // Up within discovery: move up a row, or go to TopBar if at first row in search mode
                val selection = GridPositionManager.getSavedSelection(screenKey)
                if (focusedItem == FocusedItem.Grid && selection != null) {
                    val (row, col) = selection
                    if (row > 0) {
                        val newRow = row - 1
                        
                        // Fix edge case: if the new row has fewer items, adjust column to last available item
                        val itemsInRow = if (newRow == (searchResults.size - 1) / numberOfColumns) {
                            // Last row: calculate remaining items
                            searchResults.size - (newRow * numberOfColumns)
                        } else {
                            numberOfColumns
                        }
                        
                        val adjustedCol = if (col >= itemsInRow) itemsInRow - 1 else col
                        
                        if (GridPositionManager.requestPositionChange(
                                screenKey = screenKey,
                                position = newRow * numberOfColumns,
                                offset = 0,
                                row = newRow,
                                column = adjustedCol,
                                totalItems = searchResults.size
                            )
                        ) {
                            updateFocusAndSelection(FocusedItem.Grid, newRow, adjustedCol)
                            ensureRowIsVisible(
                                newRow,
                                gridState,
                                coroutineScope,
                                numberOfColumns,
                                screenKey
                            )
                        }
                    } else if (row == 0) {
                        // If search bar is visible (SEARCH mode), move into it; else TopBar
                        if (discoveryType == DiscoveryType.SEARCH) {
                            updateFocusAndSelection(FocusedItem.Search, 0, 0)
                            // Stop here so the same Up press does not continue to TopBar
                            return@createMediaDiscoveryDpadConfig
                        } else {
                            // Clear grid highlight before moving to TopBar
                            focusedItem = FocusedItem.Search
                            currentFocusedItem.value = FocusedItem.Search
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Search))
                            return@createMediaDiscoveryDpadConfig
                        }
                    }
                }
                // If already in search (in-screen) and Up is pressed, escalate to TopBar
                if (focusedItem == FocusedItem.Search) {
                    appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Settings))
                }
            },
            onDown = {
                // If coming from TopBar, drop into grid/search start; otherwise move down a row
                when (appFocusManager.currentFocus) {
                    is AppFocusState.TopBar -> {
                        if (discoveryType == DiscoveryType.SEARCH) {
                            // In SEARCH mode, return to in-screen search (not directly to grid)
                            appFocusManager.focusDiscoveryScreen(DiscoveryFocusState.Search)
                            focusedItem = FocusedItem.Search
                        } else {
                            appFocusManager.focusDiscoveryScreen(DiscoveryFocusState.Grid(0, 0))
                            focusedItem = FocusedItem.Grid
                            selectedRow = 0
                            selectedColumn = 0
                            currentSelection.value = Pair(0, 0)
                            ensureRowIsVisible(
                                0,
                                gridState,
                                coroutineScope,
                                numberOfColumns,
                                screenKey
                            )
                        }
                    }
                    else -> {}
                }
                // When there's no search bar, ensure grid highlight shows correctly on resume
                if (discoveryType != DiscoveryType.SEARCH && focusedItem != FocusedItem.Grid && searchResults.isNotEmpty()) {
                    updateFocusAndSelection(FocusedItem.Grid, 0, 0)
                }
                if (focusedItem == FocusedItem.Grid && searchResults.isNotEmpty()) {
                    val selection = GridPositionManager.getSavedSelection(screenKey)
                    if (selection != null) {
                        val (row, col) = selection
                        val maxRow = (searchResults.size - 1) / numberOfColumns
                        if (row < maxRow) {
                            val newRow = row + 1
                            val targetPosition = newRow * numberOfColumns
                            
                            // Fix edge case: if the new row has fewer items, adjust column to last available item
                            val itemsInRow = if (newRow == maxRow) {
                                // Last row: calculate remaining items
                                searchResults.size - (newRow * numberOfColumns)
                            } else {
                                numberOfColumns
                            }
                            
                            val adjustedCol = if (col >= itemsInRow) itemsInRow - 1 else col
                            
                            if (GridPositionManager.requestPositionChange(
                                    screenKey = screenKey,
                                    position = targetPosition,
                                    offset = 0,
                                    row = newRow,
                                    column = adjustedCol,
                                    totalItems = searchResults.size
                                )
                            ) {
                                updateFocusAndSelection(FocusedItem.Grid, newRow, adjustedCol)
                                ensureRowIsVisible(
                                    newRow,
                                    gridState,
                                    coroutineScope,
                                    numberOfColumns,
                                    screenKey
                                )
                            }
                        }
                    }
                } else if (focusedItem == FocusedItem.Search && searchResults.isNotEmpty()) {
                    // Move from search to first grid item
                    // Ensure the text field and IME release focus so DPAD goes to grid
                    if (isSearchTextFieldFocused) {
                        composeFocusManager.clearFocus(force = true)
                    }
                    if (GridPositionManager.requestPositionChange(
                            screenKey = screenKey,
                            position = 0,
                            offset = 0,
                            row = 0,
                            column = 0,
                            totalItems = searchResults.size
                        )
                    ) {
                        updateFocusAndSelection(FocusedItem.Grid, 0, 0)
                        ensureRowIsVisible(0, gridState, coroutineScope, numberOfColumns, screenKey)
                    }
                }
            },
            onLeft = {
                if (focusedItem == FocusedItem.Grid) {
                    val current = GridPositionManager.getSavedSelection(screenKey)
                    if (current != null) {
                        val (row, col) = current
                        if (col > 0) {
                            val newCol = col - 1
                            if (GridPositionManager.requestPositionChange(
                                    screenKey = screenKey,
                                    position = row * numberOfColumns,
                                    offset = 0,
                                    row = row,
                                    column = newCol,
                                    totalItems = searchResults.size
                                )
                            ) {
                                updateFocusAndSelection(FocusedItem.Grid, row, newCol)
                                ensureRowIsVisible(row, gridState, coroutineScope, numberOfColumns, screenKey)
                            }
                        } else if (col == 0 && discoveryType == DiscoveryType.SEARCH) {
                            updateFocusAndSelection(FocusedItem.Search, row, 0)
                        }
                    }
                }
            },
            onRight = {
                if (focusedItem == FocusedItem.Grid) {
                    val current = GridPositionManager.getSavedSelection(screenKey)
                    if (current != null) {
                        val (row, col) = current
                        val nextIndex = row * numberOfColumns + col + 1
                        if (col < numberOfColumns - 1 && nextIndex < searchResults.size) {
                            val newCol = col + 1
                            if (GridPositionManager.requestPositionChange(
                                    screenKey = screenKey,
                                    position = row * numberOfColumns,
                                    offset = 0,
                                    row = row,
                                    column = newCol,
                                    totalItems = searchResults.size
                                )
                            ) {
                                updateFocusAndSelection(FocusedItem.Grid, row, newCol)
                                ensureRowIsVisible(row, gridState, coroutineScope, numberOfColumns, screenKey)
                            }
                        }
                    }
                } else if (focusedItem == FocusedItem.Search && searchResults.isNotEmpty()) {
                    if (GridPositionManager.requestPositionChange(
                            screenKey = screenKey,
                            position = 0,
                            offset = 0,
                            row = 0,
                            column = 0,
                            totalItems = searchResults.size
                        )
                    ) {
                        updateFocusAndSelection(FocusedItem.Grid, 0, 0)
                        ensureRowIsVisible(0, gridState, coroutineScope, numberOfColumns, screenKey)
                    }
                }
            },
            onEnter = {
                if (focusedItem == FocusedItem.Search) {
                    // Request keyboard focus in the search field
                    searchKeyboardTrigger += 1
                } else if (focusedItem == FocusedItem.Grid) {
                    val selection = GridPositionManager.getSavedSelection(screenKey)
                    if (selection != null) {
                        val (row, column) = selection
                        val index = row * numberOfColumns + column
                        if (index < searchResults.size) {
                            when (val result = searchResults[index]) {
                                is Movie -> handleMediaSelection(result.id.toString(), "movie")
                                is TV -> handleMediaSelection(result.id.toString(), "tv")
                                is Person -> handleMediaSelection(result.id.toString(), "person")
                                is Collection -> handleMediaSelection(result.id.toString(), "collection")
                            }
                        }
                    }
                }
            },
            onBack = {
                navigationManager.navigateBack()
            }
        )

        // Register screen with DPAD controller
        dpadController.registerScreen(dpadConfig)

        // Only set search focus if we're not returning from details and there are no results
        val isReturningFromManager = GridPositionManager.isReturningFromDetails(screenKey)
        if (discoveryType == DiscoveryType.SEARCH && initialKeyword.isEmpty() &&
            !isReturningFromDetails && !isReturningFromManager) {
            focusedItem = FocusedItem.Search
            appFocusManager.focusDiscoveryScreen(DiscoveryFocusState.Search)
        }

        controllerFocusRequester.requestFocus()
    }
}


enum class FocusedItem {
    Search, Grid
}

private fun ensureRowIsVisible(
    row: Int,
    gridState: LazyGridState,
    coroutineScope: CoroutineScope,
    numberOfColumns: Int,
    screenKey: String
) {
    if (BuildConfig.DEBUG) {
        logDiscovery("🎯 ensureRowIsVisible called for row $row")
    }

    // Get current selection to preserve column
    val currentSelection = GridPositionManager.getSavedSelection(screenKey)
    if (currentSelection == null) {
        if (BuildConfig.DEBUG) {
            logDiscovery("❌ No saved selection found in ensureRowIsVisible")
        }
        return
    }

    // Calculate item index for the start of this row
    val rowStartIndex = row * numberOfColumns

    if (BuildConfig.DEBUG) {
        logDiscovery("📏 Row indices: start=$rowStartIndex")
        logDiscovery("👀 Current scroll position: ${gridState.firstVisibleItemIndex}")
    }

    coroutineScope.launch {
        gridState.animateScrollToItem(index = rowStartIndex)
        if (BuildConfig.DEBUG) {
            logDiscovery("🔄 Scrolled to row start index: $rowStartIndex")
        }
    }
}

@Composable
fun MediaItem(
    result: SearchResult,
    imageLoader: ImageLoader,
    context: Context,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    // Use the enhanced MediaCard component with proper sizing for discovery screen
    MediaCard(
        mediaContent = result,
        context = context,
        imageLoader = imageLoader,
        isSelected = isSelected,
        cardHeight = 180.dp,
        cardWidth = 120.dp,
        showLabel = true,  // Show the label text below the card
        modifier = modifier
    )
}

@Composable
fun CategoryBackdrop(
    categoryType: DiscoveryType,
    keywordText: String,
    context: Context
) {
    // Compact header with small label and large title; no gradient
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        // No gradient background to keep it minimal and consistent with app style

        val categoryTypeText = when (categoryType) {
            DiscoveryType.MOVIE_GENRE -> "Movie Genre"
            DiscoveryType.SERIES_GENRE -> "Series Genre"
            DiscoveryType.STUDIO -> "Studio"
            DiscoveryType.NETWORK -> "Network"
            else -> ""
        }

        val searchTitle = when (categoryType) {
            DiscoveryType.SEARCH -> context.getString(R.string.mediaDiscovery_searchResults)
            DiscoveryType.MOVIE_KEYWORDS -> context.getString(R.string.mediaDiscovery_movieKeywordSearch) + ": $keywordText"
            DiscoveryType.TV_KEYWORDS -> context.getString(R.string.mediaDiscovery_tvKeywordSearch) + ": $keywordText"
            else -> keywordText
        }

        // Lay out texts with clear separation (label top, title bottom)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = categoryTypeText,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                maxLines = 1
            )
            Text(
                text = searchTitle,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(1f, 1f), blurRadius = 2f),
                    letterSpacing = 0.5.sp
                ),
                maxLines = 1
            )
        }
    }
}