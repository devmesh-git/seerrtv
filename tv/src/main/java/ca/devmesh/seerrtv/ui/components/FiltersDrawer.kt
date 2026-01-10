package ca.devmesh.seerrtv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ca.devmesh.seerrtv.SeerrTV
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import ca.devmesh.seerrtv.BuildConfig
import android.util.Log
import java.util.Locale
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.res.stringResource
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.BrowseModels
import ca.devmesh.seerrtv.model.MediaType
import ca.devmesh.seerrtv.util.SafeKeyEventHandler
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import ca.devmesh.seerrtv.ui.KeyUtils
import ca.devmesh.seerrtv.viewmodel.MediaDiscoveryViewModel
import kotlinx.coroutines.delay

/**
 * Helper function to handle vertical navigation key events
 * Returns true if the event was handled, false otherwise
 */
private fun handleVerticalNavigation(
    keyEvent: KeyEvent,
    selectedIndex: Int,
    maxIndex: Int,
    onIndexChange: (Int) -> Unit,
    onEnter: ((Int) -> Unit)? = null,
    onBack: (() -> Unit)? = null
): Boolean {
    return when {
        keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
            if (selectedIndex > 0) {
                onIndexChange(selectedIndex - 1)
            }
            true
        }
        keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
            if (selectedIndex < maxIndex) {
                onIndexChange(selectedIndex + 1)
            }
            true
        }
        KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyDown -> {
            onEnter?.invoke(selectedIndex)
            onEnter != null
        }
        keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyDown -> {
            onBack?.invoke()
            onBack != null
        }
        else -> false
    }
}

/**
 * Filter screen navigation states
 */
sealed class FilterScreen {
    object Categories : FilterScreen()
    object ReleaseDate : FilterScreen()
    object FirstAirDate : FilterScreen()
    object Genres : FilterScreen()
    object Keywords : FilterScreen()
    object Language : FilterScreen()
    object ContentRating : FilterScreen()
    object Runtime : FilterScreen()
    object UserScore : FilterScreen()
    object VoteCount : FilterScreen()
    object Studios : FilterScreen()
    object Networks : FilterScreen()
    object StreamingServices : FilterScreen()
    object Region : FilterScreen()
}

/**
 * Slide-in filters drawer panel from the left.
 * Contains all filter sections with D-pad navigation matching RequestModal style.
 */
@Composable
fun FiltersDrawer(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    viewModel: MediaDiscoveryViewModel,
    filters: BrowseModels.MediaFilters,
    onFiltersChange: (BrowseModels.MediaFilters) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    onScreenChange: (FilterScreen) -> Unit = {}
) {
    val context = LocalContext.current
    
    LaunchedEffect(isVisible, filters.mediaType) {
        if (isVisible) {
            viewModel.loadFilterOptions()
            viewModel.loadGenres(filters.mediaType)
            if (filters.mediaType == MediaType.MOVIE) {
                viewModel.loadStudios()
            } else {
                viewModel.loadNetworks()
            }
            // Load watch providers for default region or current watchRegion
            val defaultRegion = SharedPreferencesUtil.getDefaultStreamingRegion(context)
            val region = filters.watchRegion ?: defaultRegion
            viewModel.loadWatchProviders(filters.mediaType, region)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
        modifier = modifier
    ) {
        FiltersDrawerContent(
            viewModel = viewModel,
            filters = filters,
            onFiltersChange = onFiltersChange,
            onClearAll = onClearAll,
            onDismiss = onDismiss,
            onScreenChange = onScreenChange
        )
    }
}

/**
 * Main content of the filters drawer matching RequestModal style
 */
@Composable
private fun FiltersDrawerContent(
    viewModel: MediaDiscoveryViewModel,
    filters: BrowseModels.MediaFilters,
    onFiltersChange: (BrowseModels.MediaFilters) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    onScreenChange: (FilterScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFilterCount by remember { mutableIntStateOf(filters.activeCount()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var currentScreen by remember { mutableStateOf<FilterScreen>(FilterScreen.Categories) }
    
    
    // Focus management
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    // Get filter sections for navigation
    val filterSections = getFilterSections(filters.mediaType)
    
    // Update active count when filters change
    LaunchedEffect(filters) {
        activeFilterCount = filters.activeCount()
    }
    
    // Notify parent of screen changes
    LaunchedEffect(currentScreen) {
        if (BuildConfig.DEBUG) {
            Log.d("FiltersDrawer", "📞 Calling onScreenChange with: $currentScreen")
        }
        onScreenChange(currentScreen)
    }
    
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // Restore focus when returning to Categories
    LaunchedEffect(currentScreen) {
        if (currentScreen == FilterScreen.Categories) {
            focusRequester.requestFocus()
        }
    }
    
    // Handle back button - same approach as SortMenu (let parent BackHandler handle Categories)
    // This prevents race conditions with DpadController re-registration
    BackHandler(enabled = currentScreen != FilterScreen.Categories) {
        if (BuildConfig.DEBUG) {
            Log.d("FiltersDrawer", "🔙 BackHandler: Handling back from sub-screen: $currentScreen")
        }
        
        // Back from sub-screen returns to categories
        when (currentScreen) {
            FilterScreen.Categories -> {
                // Shouldn't reach here as BackHandler is disabled for Categories
                // Parent BackHandler will handle closing the drawer
            }
            else -> {
                currentScreen = FilterScreen.Categories
                selectedIndex = 0
            }
        }
    }
    
    // Auto-scroll to selected item - improved to handle all items including buttons
    LaunchedEffect(selectedIndex, currentScreen, activeFilterCount) {
        if (selectedIndex >= 0 && currentScreen == FilterScreen.Categories) {
            val hasClearAllButton = activeFilterCount > 0
            val clearAllOffset = if (hasClearAllButton) 1 else 0
            val totalItems = filterSections.size + clearAllOffset + 1 // +1 for Back button
            
            // Get visible items info if available
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val firstVisible = listState.firstVisibleItemIndex
                val lastVisible = firstVisible + layoutInfo.visibleItemsInfo.size - 1
                
                when {
                    // If selected item is above visible area, scroll up
                    selectedIndex < firstVisible -> {
                        listState.animateScrollToItem(selectedIndex)
                    }
                    // If selected item is below visible area, scroll down
                    selectedIndex > lastVisible -> {
                        listState.animateScrollToItem(selectedIndex)
                    }
                    // If at the bottom (Back button), ensure it's visible
                    selectedIndex >= totalItems - 1 -> {
                        // Scroll to show the Back button at the bottom
                        val targetIndex = (totalItems - 1).coerceAtLeast(0)
                        listState.animateScrollToItem(targetIndex)
                    }
                }
            } else {
                // Fallback: just scroll to the selected index if layout info not available
                if (selectedIndex < totalItems) {
                    listState.animateScrollToItem(selectedIndex)
                }
            }
        }
    }
    
    Box(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(Color(0xFF121827))
            .zIndex(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { _ ->
                    // Don't intercept Back - let BackHandler handle it (same approach as SortMenu)
                    // This prevents race conditions with DpadController re-registration
                    false
                }
                .onKeyEvent { keyEvent ->
                    return@onKeyEvent SafeKeyEventHandler.handleKeyEventWithContext(
                        keyEvent = keyEvent,
                        context = "FiltersDrawer"
                    ) { keyEvent ->
                        when {
                            keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                                if (selectedIndex > 0) {
                                    selectedIndex--
                                    if (BuildConfig.DEBUG) {
                                        Log.d("FiltersDrawer", "⬆️ Up pressed, selectedIndex: $selectedIndex")
                                    }
                                }
                                true
                            }
                            keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                                // Calculate max index dynamically
                                val hasClearAllButton = activeFilterCount > 0
                                val clearAllOffset = if (hasClearAllButton) 1 else 0
                                val maxIndex = filterSections.size + clearAllOffset // Back button is the last item
                                if (selectedIndex < maxIndex) {
                                    selectedIndex++
                                    if (BuildConfig.DEBUG) {
                                        Log.d("FiltersDrawer", "⬇️ Down pressed, selectedIndex: $selectedIndex (max: $maxIndex)")
                                    }
                                }
                                true
                            }
                            (KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) || keyEvent.key == Key.DirectionRight) && keyEvent.type == KeyEventType.KeyDown -> {
                                if (BuildConfig.DEBUG) {
                                    Log.d("FiltersDrawer", "✅ Enter pressed, selectedIndex: $selectedIndex")
                                }
                                // Handle filter section selection based on current screen
                                when (currentScreen) {
                                    FilterScreen.Categories -> {
                                        val hasClearAllButton = activeFilterCount > 0
                                        val clearAllOffset = if (hasClearAllButton) 1 else 0
                                        
                                        when {
                                            hasClearAllButton && selectedIndex == 0 -> {
                                                // Clear All button selected
                                                if (BuildConfig.DEBUG) {
                                                    Log.d("FiltersDrawer", "🗑️ Clear All Filters activated")
                                                }
                                                onClearAll()
                                            }
                                            selectedIndex < filterSections.size + clearAllOffset -> {
                                                // Filter section selected
                                                val filterIndex = selectedIndex - clearAllOffset
                                                val selectedFilter = getFilterTypeFromIndex(filterIndex, filters.mediaType)
                                                currentScreen = selectedFilter
                                                selectedIndex = 0 // Reset to first item in sub-screen
                                                if (BuildConfig.DEBUG) {
                                                    Log.d("FiltersDrawer", "🎯 Navigating to filter: $selectedFilter")
                                                }
                                            }
                                            selectedIndex == filterSections.size + clearAllOffset -> {
                                                // Back button selected (same as pressing remote back button)
                                                if (BuildConfig.DEBUG) {
                                                    Log.d("FiltersDrawer", "🔙 Back button activated")
                                                }
                                                // Clear focus before dismissing to ensure proper focus return
                                                focusManager.clearFocus(force = true)
                                                onDismiss()
                                            }
                                        }
                                    }
                                    else -> {
                                        // Sub-screens handle their own filter logic internally
                                        // No action needed here
                                    }
                                }
                                true
                            }
                            keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyDown -> {
                                if (BuildConfig.DEBUG) {
                                    Log.d("FiltersDrawer", "🔙 Back pressed, currentScreen: $currentScreen")
                                }
                                when (currentScreen) {
                                    FilterScreen.Categories -> {
                                        // Don't handle Back for Categories - let parent BackHandler handle it
                                        // This matches SortMenu behavior and prevents race conditions
                                        if (BuildConfig.DEBUG) {
                                            Log.d("FiltersDrawer", "🔙 Back from Categories - letting parent BackHandler handle it")
                                        }
                                        false // Don't consume, let it propagate to parent BackHandler
                                    }
                                    else -> {
                                        // Back from sub-screen returns to categories
                                        currentScreen = FilterScreen.Categories
                                        selectedIndex = 0
                                        true // Consume the event
                                    }
                                }
                            }
                            else -> false
                        }
                    }
                }
        ) {
            // Render content based on current screen
            when (currentScreen) {
                FilterScreen.Categories -> {
                    RenderCategoriesScreen(
                        filters = filters,
                        activeFilterCount = activeFilterCount,
                        selectedIndex = selectedIndex,
                        listState = listState,
                        filterSections = filterSections,
                        mediaType = filters.mediaType
                    )
                }
                else -> {
                    RenderFilterSubScreen(
                        currentScreen = currentScreen,
                        viewModel = viewModel,
                        filters = filters,
                        onFiltersChange = onFiltersChange
                    )
                }
            }
        }
    }
}

/**
 * Render the main categories screen
 */
@Composable
private fun RenderCategoriesScreen(
    filters: BrowseModels.MediaFilters,
    activeFilterCount: Int,
    selectedIndex: Int,
    listState: LazyListState,
    filterSections: List<String>,
    mediaType: MediaType,
) {
    // Header
    Text(
        text = stringResource(R.string.filter_title),
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White
    )
    if (activeFilterCount > 0) {
        Text(
            text = "$activeFilterCount Active",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    
    // Determine if Clear All button should be shown
    val hasClearAllButton = activeFilterCount > 0
    val clearAllOffset = if (hasClearAllButton) 1 else 0
    
    // Filter sections list with buttons included
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Add Clear All Filters button at the top when filters are active
        if (hasClearAllButton) {
            item {
                FilterMenuItem(
                    title = stringResource(R.string.filter_clearAll),
                    isFocused = selectedIndex == 0
                )
            }
        }
        
        // Filter sections
        items(filterSections) { sectionTitle ->
            val sectionIndex = filterSections.indexOf(sectionTitle)
            val isActive = isFilterActive(sectionTitle, filters, mediaType)
            FilterMenuItemWithIndicator(
                title = sectionTitle,
                isFocused = sectionIndex + clearAllOffset == selectedIndex,
                isActive = isActive
            )
        }
        
        // Spacer before button
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Close button - normal size, left-aligned (same style as SortMenu)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                ActionButton(
                    text = stringResource(R.string.filter_close),
                    isFocused = selectedIndex == filterSections.size + clearAllOffset,
                    backgroundColor = Color.Gray,
                    fillMaxWidth = false
                )
            }
        }
    }
}

/**
 * Render individual filter sub-screens
 */
@Composable
private fun RenderFilterSubScreen(
    currentScreen: FilterScreen,
    viewModel: MediaDiscoveryViewModel,
    filters: BrowseModels.MediaFilters,
    onFiltersChange: (BrowseModels.MediaFilters) -> Unit
) {
    // Header with back navigation
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "← ${getFilterTitle(currentScreen)}",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    
    // Filter-specific content
    when (currentScreen) {
        FilterScreen.ReleaseDate -> {
            ReleaseDateFilterContent(
                filters = filters,
                onFiltersChange = onFiltersChange
            )
        }
        FilterScreen.FirstAirDate -> {
            // First Air Date uses same UI as Release Date but for series
            ReleaseDateFilterContent(
                filters = filters,
                onFiltersChange = onFiltersChange
            )
        }
        FilterScreen.Genres -> {
            val genres by viewModel.availableGenres.collectAsState()
            FilterMultiListSelection(
                items = genres,
                selectedItems = genres.filter { filters.genres.contains(it.id) },
                itemLabel = { it.name },
                onItemsSelected = { selectedGenres ->
                    onFiltersChange(filters.copy(genres = selectedGenres.map { it.id }))
                }
            )
        }
        FilterScreen.Keywords -> {
            val searchResults by viewModel.keywordSearchResults.collectAsState()
            
            // Note: We need a way to track selected keywords objects, but MediaFilters only stores IDs.
            // For now, we can only show selected if they are in the search results.
            // A better approach would be to fetch selected keywords details, but we'll start with this.
            val selectedKeywords = searchResults.filter { filters.keywords.contains(it.id) }
            
            FilterSearchSelection(
                searchResults = searchResults,
                selectedItems = selectedKeywords,
                itemLabel = { it.name },
                onSearch = { query -> viewModel.searchKeywords(query) },
                onItemSelected = { keyword ->
                    val currentIds = filters.keywords
                    val newIds = if (currentIds.contains(keyword.id)) {
                        currentIds - keyword.id
                    } else {
                        currentIds + keyword.id
                    }
                    onFiltersChange(filters.copy(keywords = newIds))
                },
                onClearSearch = { viewModel.clearKeywordSearch() }
            )
        }
        FilterScreen.Language -> {
            val languages by viewModel.availableLanguages.collectAsState()
            FilterListSelection(
                items = languages,
                selectedItem = languages.find { it.iso_639_1 == filters.originalLanguage },
                itemLabel = { it.english_name },
                onItemSelected = { language ->
                    onFiltersChange(filters.copy(originalLanguage = language.iso_639_1))
                },
                onClear = {
                    onFiltersChange(filters.copy(originalLanguage = null))
                }
            )
        }
        FilterScreen.ContentRating -> {
            val ratings by viewModel.contentRatings.collectAsState()
            // Multi-select for content ratings
            FilterMultiListSelection(
                items = ratings,
                selectedItems = ratings.filter { filters.contentRatings.contains(it.certification) },
                itemLabel = { "${it.certification} - ${it.meaning}" },
                onItemsSelected = { selectedRatings ->
                    onFiltersChange(filters.copy(contentRatings = selectedRatings.map { it.certification }))
                }
            )
        }
        FilterScreen.Runtime -> {
            FilterRangeSelection(
                minValue = 0f,
                maxValue = 400f,
                currentMin = filters.runtimeMin?.toFloat(),
                currentMax = filters.runtimeMax?.toFloat(),
                step = 10f,
                onRangeChanged = { min, max ->
                    onFiltersChange(filters.copy(runtimeMin = min?.toInt(), runtimeMax = max?.toInt()))
                },
                onClear = {
                    onFiltersChange(filters.copy(runtimeMin = null, runtimeMax = null))
                }
            )
        }
        FilterScreen.UserScore -> {
            FilterRangeSelection(
                minValue = 0f,
                maxValue = 10f,
                currentMin = filters.userScoreMin,
                currentMax = filters.userScoreMax,
                step = 0.5f,
                valueFormatter = { String.format(Locale.US, "%.1f", it) },
                onRangeChanged = { min, max ->
                    onFiltersChange(filters.copy(userScoreMin = min, userScoreMax = max))
                },
                onClear = {
                    onFiltersChange(filters.copy(userScoreMin = null, userScoreMax = null))
                }
            )
        }
        FilterScreen.VoteCount -> {
            FilterRangeSelection(
                minValue = 0f,
                maxValue = 10000f,
                currentMin = filters.voteCountMin?.toFloat(),
                currentMax = filters.voteCountMax?.toFloat(),
                step = 100f,
                onRangeChanged = { min, max ->
                    onFiltersChange(filters.copy(voteCountMin = min?.toInt(), voteCountMax = max?.toInt()))
                },
                onClear = {
                    onFiltersChange(filters.copy(voteCountMin = null, voteCountMax = null))
                }
            )
        }
        FilterScreen.Studios -> {
            val searchResults by viewModel.studioSearchResults.collectAsState()
            val selectedStudioName by viewModel.selectedStudioName.collectAsState()
            
            FilterSearchSelectionSingle(
                searchResults = searchResults,
                selectedItemId = filters.studio,
                currentSelectionLabel = selectedStudioName,
                itemId = { it.id },
                itemLabel = { it.name },
                onSearch = { query -> viewModel.searchStudios(query) },
                onItemSelected = { studio ->
                    // Single selection: set or clear
                    val newStudio = if (filters.studio == studio.id) {
                        viewModel.clearSelectedStudio()
                        null // Deselect if already selected
                    } else {
                        viewModel.setSelectedStudioName(studio.name)
                        studio.id // Set new selection
                    }
                    onFiltersChange(filters.copy(studio = newStudio))
                },
                onClearSelection = {
                    viewModel.clearSelectedStudio()
                    onFiltersChange(filters.copy(studio = null))
                },
                onClearSearch = { viewModel.clearStudioSearch() }
            )
        }
        FilterScreen.Networks -> {
            val networks by viewModel.availableNetworks.collectAsState()
            FilterMultiListSelection(
                items = networks,
                selectedItems = networks.filter { filters.networks.contains(it.id) },
                itemLabel = { it.name },
                onItemsSelected = { selectedNetworks ->
                    onFiltersChange(filters.copy(networks = selectedNetworks.map { it.id }))
                }
            )
        }
        FilterScreen.StreamingServices -> {
            StreamingServicesFilterContent(
                viewModel = viewModel,
                filters = filters,
                onFiltersChange = onFiltersChange
            )
        }
        FilterScreen.Region -> {
            val regions by viewModel.availableRegions.collectAsState()
            FilterListSelection(
                items = regions,
                selectedItem = regions.find { it.iso_3166_1 == filters.region },
                itemLabel = { it.english_name },
                onItemSelected = { region ->
                    onFiltersChange(filters.copy(region = region.iso_3166_1))
                },
                onClear = {
                    onFiltersChange(filters.copy(region = null))
                }
            )
        }
        else -> {
            Text("Unknown Filter - ${stringResource(R.string.filter_comingSoon)}", color = Color.White)
        }
    }
}

/**
 * Get filter sections based on media type
 */
@Composable
private fun getFilterSections(mediaType: MediaType): List<String> {
    return if (mediaType == MediaType.MOVIE) {
        // Movies filter order
        listOf(
            stringResource(R.string.filter_releaseDate),
            stringResource(R.string.filter_studios),
            stringResource(R.string.filter_genres),
            stringResource(R.string.filter_keywords),
            stringResource(R.string.filter_originalLanguage),
            stringResource(R.string.filter_contentRating),
            stringResource(R.string.filter_runtime),
            stringResource(R.string.filter_userScore),
            stringResource(R.string.filter_voteCount),
            stringResource(R.string.filter_streamingServices)
        )
    } else {
        // Series filter order
        listOf(
            stringResource(R.string.filter_firstAirDate),
            stringResource(R.string.filter_genres),
            stringResource(R.string.filter_keywords),
            stringResource(R.string.filter_originalLanguage),
            stringResource(R.string.filter_contentRating),
            stringResource(R.string.filter_runtime),
            stringResource(R.string.filter_userScore),
            stringResource(R.string.filter_voteCount),
            stringResource(R.string.filter_streamingServices)
        )
    }
}

/**
 * Filter screen mappings by media type
 */
private val movieFilterScreens = listOf(
    FilterScreen.ReleaseDate,
    FilterScreen.Studios,
    FilterScreen.Genres,
    FilterScreen.Keywords,
    FilterScreen.Language,
    FilterScreen.ContentRating,
    FilterScreen.Runtime,
    FilterScreen.UserScore,
    FilterScreen.VoteCount,
    FilterScreen.StreamingServices
)

private val seriesFilterScreens = listOf(
    FilterScreen.FirstAirDate,
    FilterScreen.Genres,
    FilterScreen.Keywords,
    FilterScreen.Language,
    FilterScreen.ContentRating,
    FilterScreen.Runtime,
    FilterScreen.UserScore,
    FilterScreen.VoteCount,
    FilterScreen.StreamingServices
)

/**
 * Get filter type from index
 */
private fun getFilterTypeFromIndex(index: Int, mediaType: MediaType): FilterScreen {
    val screens = if (mediaType == MediaType.MOVIE) movieFilterScreens else seriesFilterScreens
    return screens.getOrNull(index) ?: FilterScreen.Categories
}

/**
 * Get filter title for display
 */
@Composable
private fun getFilterTitle(screen: FilterScreen): String {
    return when (screen) {
        FilterScreen.ReleaseDate -> stringResource(R.string.filter_releaseDate)
        FilterScreen.FirstAirDate -> stringResource(R.string.filter_firstAirDate)
        FilterScreen.Genres -> stringResource(R.string.filter_genres)
        FilterScreen.Keywords -> stringResource(R.string.filter_keywords)
        FilterScreen.Language -> stringResource(R.string.filter_originalLanguage)
        FilterScreen.ContentRating -> stringResource(R.string.filter_contentRating)
        FilterScreen.Runtime -> stringResource(R.string.filter_runtime)
        FilterScreen.UserScore -> stringResource(R.string.filter_userScore)
        FilterScreen.VoteCount -> stringResource(R.string.filter_voteCount)
        FilterScreen.Studios -> stringResource(R.string.filter_studios)
        FilterScreen.Networks -> stringResource(R.string.filter_networks)
        FilterScreen.StreamingServices -> stringResource(R.string.filter_streamingServices)
        FilterScreen.Region -> stringResource(R.string.filter_region)
        else -> "Filter"
    }
}


/**
 * Individual filter menu item matching RequestModal style
 */
@Composable
private fun FilterMenuItem(
    title: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (isFocused) Color(0xFF2C3E50) else Color.Transparent
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Filter menu item with active indicator
 */
@Composable
private fun FilterMenuItemWithIndicator(
    title: String,
    isFocused: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (isFocused) Color(0xFF2C3E50) else Color.Transparent
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
        
        if (isActive) {
            Text(
                text = "●",
                color = Color(0xFF4CAF50), // Green dot
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Check if a filter screen has active filters
 */
private fun isFilterScreenActive(screen: FilterScreen, filters: BrowseModels.MediaFilters): Boolean {
    return when (screen) {
        FilterScreen.ReleaseDate, FilterScreen.FirstAirDate -> 
            filters.releaseFrom != null || filters.releaseTo != null
        FilterScreen.Genres -> filters.genres.isNotEmpty()
        FilterScreen.Keywords -> filters.keywords.isNotEmpty()
        FilterScreen.Language -> filters.originalLanguage != null
        FilterScreen.ContentRating -> filters.contentRatings.isNotEmpty()
        FilterScreen.Runtime -> filters.runtimeMin != null || filters.runtimeMax != null
        FilterScreen.UserScore -> filters.userScoreMin != null || filters.userScoreMax != null
        FilterScreen.VoteCount -> filters.voteCountMin != null || filters.voteCountMax != null
        FilterScreen.Studios -> filters.studio != null
        FilterScreen.Networks -> filters.networks.isNotEmpty()
        FilterScreen.StreamingServices -> filters.watchProviders.isNotEmpty()
        FilterScreen.Region -> filters.region != null
        FilterScreen.Categories -> false
    }
}

/**
 * Map section title string to FilterScreen
 */
@Composable
private fun getFilterScreenFromTitle(sectionTitle: String, mediaType: MediaType): FilterScreen? {
    return when (sectionTitle) {
        stringResource(R.string.filter_releaseDate) -> FilterScreen.ReleaseDate
        stringResource(R.string.filter_firstAirDate) -> FilterScreen.FirstAirDate
        stringResource(R.string.filter_genres) -> FilterScreen.Genres
        stringResource(R.string.filter_keywords) -> FilterScreen.Keywords
        stringResource(R.string.filter_originalLanguage) -> FilterScreen.Language
        stringResource(R.string.filter_contentRating) -> FilterScreen.ContentRating
        stringResource(R.string.filter_runtime) -> FilterScreen.Runtime
        stringResource(R.string.filter_userScore) -> FilterScreen.UserScore
        stringResource(R.string.filter_voteCount) -> FilterScreen.VoteCount
        stringResource(R.string.filter_studios) -> FilterScreen.Studios
        stringResource(R.string.filter_networks) -> FilterScreen.Networks
        stringResource(R.string.filter_streamingServices) -> FilterScreen.StreamingServices
        stringResource(R.string.filter_region) -> FilterScreen.Region
        else -> null
    }
}

/**
 * Check if a filter is active (backward compatibility)
 */
@Composable
private fun isFilterActive(sectionTitle: String, filters: BrowseModels.MediaFilters, mediaType: MediaType): Boolean {
    val screen = getFilterScreenFromTitle(sectionTitle, mediaType) ?: return false
    return isFilterScreenActive(screen, filters)
}

/**
 * Date picker state
 */
private data class DatePickerState(
    val showDatePicker: Boolean = false,
    val title: String = "",
    val currentDate: String? = null,
    val callback: ((String) -> Unit)? = null
)

/**
 * Release Date Filter Content
 */
@Composable
private fun ReleaseDateFilterContent(
    filters: BrowseModels.MediaFilters,
    onFiltersChange: (BrowseModels.MediaFilters) -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var datePickerState by remember { mutableStateOf(DatePickerState()) }
    val focusRequester = remember { FocusRequester() }
    
    // Get strings in composable context
    val fromLabel = stringResource(R.string.filter_from)
    val toLabel = stringResource(R.string.filter_to)
    val releaseDateLabel = stringResource(R.string.filter_releaseDate)
    
    val dateOptions = listOf(
        fromLabel,
        toLabel,
        stringResource(R.string.filter_clearAll)
    )
    
    LaunchedEffect(datePickerState.showDatePicker) {
        if (!datePickerState.showDatePicker) {
            focusRequester.requestFocus()
        }
    }
    
    // Show date picker if needed
    if (datePickerState.showDatePicker) {
        DatePicker(
            title = datePickerState.title,
            currentDate = datePickerState.currentDate,
            onDateSelected = { dateStr ->
                datePickerState.callback?.invoke(dateStr)
                datePickerState = datePickerState.copy(showDatePicker = false)
            },
            onDismiss = {
                datePickerState = datePickerState.copy(showDatePicker = false)
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    handleVerticalNavigation(
                        keyEvent = keyEvent,
                        selectedIndex = selectedIndex,
                        maxIndex = dateOptions.size - 1,
                        onIndexChange = { selectedIndex = it },
                        onEnter = { index ->
                            // Handle date selection
                            when (index) {
                                0 -> {
                                    // Open date picker for Release Date From
                                    datePickerState = DatePickerState(
                                        showDatePicker = true,
                                        title = "$releaseDateLabel - $fromLabel",
                                        currentDate = filters.releaseFrom,
                                        callback = { dateStr ->
                                            onFiltersChange(filters.copy(releaseFrom = dateStr))
                                        }
                                    )
                                    if (BuildConfig.DEBUG) {
                                        Log.d("ReleaseDateFilter", "Opening date picker for Release Date From")
                                    }
                                }
                                1 -> {
                                    // Open date picker for Release Date To
                                    datePickerState = DatePickerState(
                                        showDatePicker = true,
                                        title = "$releaseDateLabel - $toLabel",
                                        currentDate = filters.releaseTo,
                                        callback = { dateStr ->
                                            onFiltersChange(filters.copy(releaseTo = dateStr))
                                        }
                                    )
                                    if (BuildConfig.DEBUG) {
                                        Log.d("ReleaseDateFilter", "Opening date picker for Release Date To")
                                    }
                                }
                                2 -> {
                                    // Clear all dates
                                    val newFilters = filters.copy(releaseFrom = null, releaseTo = null)
                                    onFiltersChange(newFilters)
                                    if (BuildConfig.DEBUG) {
                                        Log.d("ReleaseDateFilter", "Cleared all dates")
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            dateOptions.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(
                            if (selectedIndex == index) Color.White.copy(alpha = 0.2f) else Color.Transparent
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option,
                        color = if (selectedIndex == index) Color.White else Color.Gray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Show current date value
                    val currentValue = when (index) {
                        0 -> filters.releaseFrom ?: stringResource(R.string.filter_notSet)
                        1 -> filters.releaseTo ?: stringResource(R.string.filter_notSet)
                        2 -> stringResource(R.string.filter_clearAllDates)
                        else -> stringResource(R.string.filter_notSet)
                    }
                    
                    Text(
                        text = currentValue,
                        color = if (selectedIndex == index) Color.White else Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Date Picker Component for D-pad navigation
 */
@Composable
private fun DatePicker(
    title: String,
    currentDate: String?,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(2024) }
    var selectedMonth by remember { mutableIntStateOf(1) }
    var selectedDay by remember { mutableIntStateOf(1) }
    var selectedField by remember { mutableIntStateOf(0) } // 0=Year, 1=Month, 2=Day
    val focusRequester = remember { FocusRequester() }
    
    // Parse current date if available
    LaunchedEffect(currentDate) {
        currentDate?.let { dateStr ->
            try {
                val parts = dateStr.split("-")
                if (parts.size == 3) {
                    selectedYear = parts[0].toInt()
                    selectedMonth = parts[1].toInt()
                    selectedDay = parts[2].toInt()
                }
            } catch (_: Exception) {
                // Use defaults if parsing fails
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                when {
                    keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                        if (selectedField > 0) {
                            selectedField--
                        }
                        true
                    }
                    keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                        if (selectedField < 2) {
                            selectedField++
                        }
                        true
                    }
                    keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                        when (selectedField) {
                            0 -> selectedYear = (selectedYear + 1).coerceAtMost(2030)
                            1 -> selectedMonth = ((selectedMonth % 12) + 1)
                            2 -> selectedDay = ((selectedDay % 31) + 1)
                        }
                        true
                    }
                    keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                        when (selectedField) {
                            0 -> selectedYear = (selectedYear - 1).coerceAtLeast(1900)
                            1 -> selectedMonth = if (selectedMonth == 1) 12 else selectedMonth - 1
                            2 -> selectedDay = if (selectedDay == 1) 31 else selectedDay - 1
                        }
                        true
                    }
                    KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyDown -> {
                        // Confirm date selection
                        val dateStr = String.format(Locale.US, "%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay)
                        onDateSelected(dateStr)
                        onDismiss()
                        true
                    }
                    keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyDown -> {
                        onDismiss()
                        true
                    }
                    else -> false
                }
            }
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Date display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            // Year
            Text(
                text = selectedYear.toString(),
                color = if (selectedField == 0) Color.White else Color.Gray,
                style = MaterialTheme.typography.headlineMedium
            )
            
            Text(
                text = " - ",
                color = Color.Gray,
                style = MaterialTheme.typography.headlineMedium
            )
            
            // Month
            Text(
                text = String.format(Locale.US, "%02d", selectedMonth),
                color = if (selectedField == 1) Color.White else Color.Gray,
                style = MaterialTheme.typography.headlineMedium
            )
            
            Text(
                text = " - ",
                color = Color.Gray,
                style = MaterialTheme.typography.headlineMedium
            )
            
            // Day
            Text(
                text = String.format(Locale.US, "%02d", selectedDay),
                color = if (selectedField == 2) Color.White else Color.Gray,
                style = MaterialTheme.typography.headlineMedium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Instructions
        Text(
            text = "Use D-pad to navigate and adjust values. Press Enter to confirm, Back to cancel.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Generic list selection component for filters
 */
@Composable
private fun <T> FilterListSelection(
    items: List<T>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    onItemSelected: (T) -> Unit,
    onClear: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    
    // Add "Clear" option at the top
    val totalItems = items.size + 1
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // Auto-scroll to selected item - only if item is not visible
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < totalItems) {
            // Small delay to ensure UI has updated
            delay(50)
            
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val firstVisible = listState.firstVisibleItemIndex
                val lastVisible = firstVisible + layoutInfo.visibleItemsInfo.size - 1
                
                // Only scroll if the selected item is outside the visible area
                when {
                    selectedIndex < firstVisible -> {
                        listState.animateScrollToItem(selectedIndex)
                    }
                    selectedIndex > lastVisible -> {
                        listState.animateScrollToItem(selectedIndex)
                    }
                    // Item is already visible, no need to scroll
                }
            } else {
                // Fallback: scroll if layout info not available
                listState.animateScrollToItem(selectedIndex)
            }
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                handleVerticalNavigation(
                    keyEvent = keyEvent,
                    selectedIndex = selectedIndex,
                    maxIndex = totalItems - 1,
                    onIndexChange = { selectedIndex = it },
                    onEnter = { index ->
                        if (index == 0) {
                            onClear()
                        } else {
                            onItemSelected(items[index - 1])
                        }
                    }
                )
            }
    ) {
        // Clear option
        item {
            FilterMenuItemWithIndicator(
                title = stringResource(R.string.filter_clearSelection),
                isFocused = selectedIndex == 0,
                isActive = selectedItem == null
            )
        }
        
        // Items
        items(items.size) { index ->
            val item = items[index]
            FilterMenuItemWithIndicator(
                title = itemLabel(item),
                isFocused = selectedIndex == index + 1,
                isActive = item == selectedItem
            )
        }
    }
}

/**
 * Generic multi-select list component for filters
 */
@Composable
private fun <T> FilterMultiListSelection(
    items: List<T>,
    selectedItems: List<T>,
    itemLabel: (T) -> String,
    onItemsSelected: (List<T>) -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // Auto-scroll to selected item - only if item is not visible
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < items.size) {
            // Small delay to ensure UI has updated
            delay(50)
            
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val firstVisible = listState.firstVisibleItemIndex
                val lastVisible = firstVisible + layoutInfo.visibleItemsInfo.size - 1
                
                // Only scroll if the selected item is outside the visible area
                when {
                    selectedIndex < firstVisible -> {
                        listState.animateScrollToItem(selectedIndex)
                    }
                    selectedIndex > lastVisible -> {
                        listState.animateScrollToItem(selectedIndex)
                    }
                    // Item is already visible, no need to scroll
                }
            } else {
                // Fallback: scroll if layout info not available
                listState.animateScrollToItem(selectedIndex)
            }
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                handleVerticalNavigation(
                    keyEvent = keyEvent,
                    selectedIndex = selectedIndex,
                    maxIndex = items.size - 1,
                    onIndexChange = { selectedIndex = it },
                    onEnter = { index ->
                        val item = items[index]
                        val newSelection = if (selectedItems.contains(item)) {
                            selectedItems - item
                        } else {
                            selectedItems + item
                        }
                        onItemsSelected(newSelection)
                    }
                )
            }
    ) {
        items(items.size) { index ->
            val item = items[index]
            val isSelected = selectedItems.contains(item)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (selectedIndex == index) Color(0xFF2C3E50) else Color.Transparent
                    )
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = itemLabel(item),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                if (isSelected) {
                    Text(
                        text = "✓",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * Search selection component for filters (Keywords, Studios, etc.)
 */
@Composable
private fun <T> FilterSearchSelection(
    searchResults: List<T>,
    selectedItems: List<T>,
    itemLabel: (T) -> String,
    onSearch: (String) -> Unit,
    onItemSelected: (T) -> Unit,
    onClearSearch: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) } // 0 is search bar, 1+ are results
    val listState = rememberLazyListState()
    val searchFieldFocusRequester = remember { FocusRequester() }
    val resultsListFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Debounce search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            kotlinx.coroutines.delay(500)
            onSearch(searchQuery)
        } else if (searchQuery.isEmpty()) {
            onClearSearch()
        }
    }
    
    LaunchedEffect(Unit) {
        searchFieldFocusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
    ) {
        // Search Bar with OutlinedTextField
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .background(if (selectedIndex == 0) Color(0xFF2C3E50) else Color(0xFF1E2732), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search keywords...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFieldFocusRequester)
                    .onFocusChanged { focusState ->
                        selectedIndex = if (focusState.isFocused) 0 else selectedIndex
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent when (event.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    // Navigate to results
                                    focusManager.clearFocus(force = true)
                                    if (searchResults.isNotEmpty()) {
                                        selectedIndex = 1
                                        resultsListFocusRequester.requestFocus()
                                        if (BuildConfig.DEBUG) {
                                            Log.d("FilterSearchSelection", "⬇️ Navigating to results, requesting focus")
                                        }
                                    }
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_UP,
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    focusManager.clearFocus(force = true)
                                    true
                                }
                                else -> {
                                    if (KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode)) {
                                        // Request focus to open keyboard
                                        searchFieldFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            }
                        }
                        false
                    }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Results
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .focusRequester(resultsListFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (BuildConfig.DEBUG) {
                        Log.d("FilterSearchSelection", "🎮 LazyColumn key event: ${keyEvent.nativeKeyEvent.keyCode}, selectedIndex: $selectedIndex")
                    }
                    when {
                        keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                            if (selectedIndex > 1) {
                                selectedIndex--
                            } else if (selectedIndex == 1) {
                                selectedIndex = 0
                                searchFieldFocusRequester.requestFocus()
                            }
                            true
                        }
                        keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                            if (selectedIndex < searchResults.size) {
                                selectedIndex++
                            }
                            true
                        }
                        KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyDown -> {
                            if (selectedIndex > 0 && selectedIndex <= searchResults.size) {
                                if (BuildConfig.DEBUG) {
                                    Log.d("FilterSearchSelection", "🎯 Selecting keyword at index ${selectedIndex - 1}")
                                }
                                onItemSelected(searchResults[selectedIndex - 1])
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            items(searchResults.size) { index ->
                val item = searchResults[index]
                val isSelected = selectedItems.contains(item)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (selectedIndex == index + 1) Color(0xFF2C3E50) else Color.Transparent
                        )
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = itemLabel(item),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    if (isSelected) {
                        Text(
                            text = "✓",
                            color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Range selection component for filters (Runtime, Score, etc.)
 */
@Composable
private fun FilterRangeSelection(
    minValue: Float,
    maxValue: Float,
    currentMin: Float?,
    currentMax: Float?,
    step: Float = 1f,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    onRangeChanged: (Float?, Float?) -> Unit,
    onClear: () -> Unit
) {
    var selectedMin by remember { mutableFloatStateOf(currentMin ?: minValue) }
    var selectedMax by remember { mutableFloatStateOf(currentMax ?: maxValue) }
    var activeSlider by remember { mutableIntStateOf(0) } // 0 = Min, 1 = Max, 2 = Apply, 3 = Clear
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                when {
                    keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                        if (activeSlider > 0) {
                            activeSlider--
                        }
                        true
                    }
                    keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                        if (activeSlider < 3) {
                            activeSlider++
                        }
                        true
                    }
                    keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                        when (activeSlider) {
                            0 -> selectedMin = (selectedMin - step).coerceAtLeast(minValue)
                            1 -> selectedMax = (selectedMax - step).coerceAtLeast(selectedMin)
                        }
                        true
                    }
                    keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                        when (activeSlider) {
                            0 -> selectedMin = (selectedMin + step).coerceAtMost(selectedMax)
                            1 -> selectedMax = (selectedMax + step).coerceAtMost(maxValue)
                        }
                        true
                    }
                    KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyDown -> {
                        when (activeSlider) {
                            2 -> onRangeChanged(selectedMin, selectedMax)
                            3 -> {
                                selectedMin = minValue
                                selectedMax = maxValue
                                onClear()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            .padding(12.dp)
    ) {
        // Instructions
        Text(
            text = "Use ◀ ▶ to adjust • Use ▲ ▼ to navigate",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Min Value Slider
        FilterSliderItem(
            label = "Min: ${valueFormatter(selectedMin)}",
            value = selectedMin,
            min = minValue,
            max = maxValue,
            isFocused = activeSlider == 0
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Max Value Slider
        FilterSliderItem(
            label = "Max: ${valueFormatter(selectedMax)}",
            value = selectedMax,
            min = minValue,
            max = maxValue,
            isFocused = activeSlider == 1
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Buttons
        ActionButton(
            text = stringResource(R.string.filter_applyRange),
            isFocused = activeSlider == 2,
            backgroundColor = if (activeSlider == 2) Color(0xFF6200EE) else Color.Gray
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ActionButton(
            text = stringResource(R.string.filter_clearFilter),
            isFocused = activeSlider == 3,
            backgroundColor = if (activeSlider == 3) Color.Red else Color.Gray
        )
    }
}

@Composable
private fun FilterSliderItem(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    isFocused: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isFocused) Color(0xFF2C3E50) else Color(0xFF1A1A1A),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF6200EE) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        // Label
        Text(
            text = label,
            color = if (isFocused) Color.White else Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Value display with arrows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left arrow indicator
            Text(
                text = if (isFocused) "◀" else " ",
                color = if (isFocused && value > min) Color(0xFF6200EE) else Color.Gray,
                style = MaterialTheme.typography.headlineSmall
            )
            
            // Current value
            Text(
                text = value.toInt().toString(),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            
            // Right arrow indicator
            Text(
                text = if (isFocused) "▶" else " ",
                color = if (isFocused && value < max) Color(0xFF6200EE) else Color.Gray,
                style = MaterialTheme.typography.headlineSmall
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Enhanced progress bar with thumb
        var trackWidth by remember { mutableStateOf(0.dp) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .onGloballyPositioned { coordinates ->
                    trackWidth = coordinates.size.width.dp
                }
        ) {
            val thumbSize = 16.dp
            val thumbPosition = ((value - min) / (max - min))
            // Calculate offset accounting for thumb size to keep it within bounds
            val thumbOffset = if (trackWidth > 0.dp) {
                trackWidth * thumbPosition - thumbSize * thumbPosition
            } else {
                0.dp
            }
            
            // Background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color(0xFF404040), RoundedCornerShape(4.dp))
            )
            
            // Progress track
            Box(
                modifier = Modifier
                    .fillMaxWidth((value - min) / (max - min))
                    .height(8.dp)
                    .background(
                        if (isFocused) Color(0xFF6200EE) else Color.Gray,
                        RoundedCornerShape(4.dp)
                    )
            )
            
            // Thumb indicator
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .background(
                        if (isFocused) Color(0xFF6200EE) else Color.White,
                        CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Range display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = min.toInt().toString(),
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = max.toInt().toString(),
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Search selection component for single-selection filters (Studios)
 * Similar to FilterSearchSelection but only allows one item to be selected
 */
@Composable
private fun <T> FilterSearchSelectionSingle(
    searchResults: List<T>,
    selectedItemId: Int?,
    currentSelectionLabel: String?,
    itemId: (T) -> Int,
    itemLabel: (T) -> String,
    onSearch: (String) -> Unit,
    onItemSelected: (T) -> Unit,
    onClearSelection: () -> Unit,
    onClearSearch: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(if (currentSelectionLabel != null) 0 else 1) }
    val listState = rememberLazyListState()
    val searchFieldFocusRequester = remember { FocusRequester() }
    val resultsListFocusRequester = remember { FocusRequester() }
    val currentSelectionFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Debounce search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            kotlinx.coroutines.delay(500)
            onSearch(searchQuery)
        } else if (searchQuery.isEmpty()) {
            onClearSearch()
        }
    }
    
    LaunchedEffect(Unit) {
        if (currentSelectionLabel != null) {
            currentSelectionFocusRequester.requestFocus()
        } else {
            searchFieldFocusRequester.requestFocus()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
    ) {
        // Show currently selected studio if any
        if (currentSelectionLabel != null) {
            Text(
                text = "Current Selection:",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selectedIndex == 0) Color(0xFF2C3E50) else Color(0xFF1E2732))
                    .padding(vertical = 8.dp, horizontal = 16.dp)
                    .focusRequester(currentSelectionFocusRequester)
                    .focusable()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) selectedIndex = 0
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when {
                                event.key == Key.DirectionDown -> {
                                    selectedIndex = 1
                                    searchFieldFocusRequester.requestFocus()
                                    true
                                }
                                KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) -> {
                                    onClearSelection()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                Text(
                    text = currentSelectionLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✓",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Select to clear",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Search Bar with OutlinedTextField
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .background(
                    if (selectedIndex == (if (currentSelectionLabel != null) 1 else 0)) 
                        Color(0xFF2C3E50) 
                    else 
                        Color(0xFF1E2732), 
                    RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search studios...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFieldFocusRequester)
                    .onFocusChanged { focusState ->
                        selectedIndex = if (focusState.isFocused) {
                            if (currentSelectionLabel != null) 1 else 0
                        } else selectedIndex
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent when (event.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (currentSelectionLabel != null && selectedIndex == 1) {
                                        selectedIndex = 0
                                        currentSelectionFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    // Navigate to results
                                    focusManager.clearFocus(force = true)
                                    if (searchResults.isNotEmpty()) {
                                        selectedIndex = (if (currentSelectionLabel != null) 2 else 1)
                                        resultsListFocusRequester.requestFocus()
                                    }
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    focusManager.clearFocus(force = true)
                                    true
                                }
                                else -> {
                                    if (KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode)) {
                                        // Request focus to open keyboard
                                        searchFieldFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            }
                        }
                        false
                    }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Auto-scroll to keep selected item visible
        LaunchedEffect(selectedIndex) {
            val offset = if (currentSelectionLabel != null) 2 else 1
            val resultIndex = selectedIndex - offset
            if (resultIndex >= 0 && resultIndex < searchResults.size) {
                listState.animateScrollToItem(resultIndex)
            }
        }
        
        // Results
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .focusRequester(resultsListFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    val offset = if (currentSelectionLabel != null) 2 else 1
                    when {
                        keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                            if (selectedIndex > offset) {
                                selectedIndex--
                            } else if (selectedIndex == offset) {
                                selectedIndex = if (currentSelectionLabel != null) 1 else 0
                                searchFieldFocusRequester.requestFocus()
                            }
                            true
                        }
                        keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                            if (selectedIndex < searchResults.size + offset - 1) {
                                selectedIndex++
                            }
                            true
                        }
                        KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyDown -> {
                            val resultIndex = selectedIndex - offset
                            if (resultIndex >= 0 && resultIndex < searchResults.size) {
                                onItemSelected(searchResults[resultIndex])
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            items(searchResults.size) { index ->
                val item = searchResults[index]
                val isSelected = selectedItemId == itemId(item)
                val offset = if (currentSelectionLabel != null) 2 else 1
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (selectedIndex == index + offset) Color(0xFF2C3E50) else Color.Transparent
                        )
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = itemLabel(item),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    if (isSelected) {
                        Text(
                            text = "✓",
                            color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}


/**
 * Confirmation dialog for updating default streaming region
 */
@Composable
private fun RegionChangeConfirmationDialog(
    regionName: String,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    // Focus management for TV remote
    var focusedButton by remember { mutableIntStateOf(0) } // 0 = Yes, 1 = No
    val yesFocusRequester = remember { FocusRequester() }
    val noFocusRequester = remember { FocusRequester() }

    Surface(
        modifier = Modifier
            .padding(32.dp)
            .widthIn(max = 500.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .onKeyEvent { keyEvent ->
                    when {
                        keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                            focusedButton = if (focusedButton == 0) 1 else 0 // cycle
                            true
                        }
                        keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                            focusedButton = if (focusedButton == 1) 0 else 1 // cycle
                            true
                        }
                        KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyDown -> {
                            if (focusedButton == 0) {
                                onYes()
                            } else {
                                onNo()
                            }
                            true
                        }
                        KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyUp -> {
                            true
                        }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LaunchedEffect(focusedButton) {
                if (focusedButton == 0) {
                    yesFocusRequester.requestFocus()
                } else {
                    noFocusRequester.requestFocus()
                }
            }
            Text(
                text = stringResource(R.string.filter_regionChangeConfirm, regionName),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CustomButton(
                    onClick = onYes,
                    isFocused = focusedButton == 0,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(yesFocusRequester)
                        .onFocusChanged { if (it.isFocused) focusedButton = 0 }
                ) {
                    Text(stringResource(R.string.common_yes), color = Color.White)
                }
                CustomButton(
                    onClick = onNo,
                    isFocused = focusedButton == 1,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(noFocusRequester)
                        .onFocusChanged { if (it.isFocused) focusedButton = 1 }
                ) {
                    Text(stringResource(R.string.common_no), color = Color.White)
                }
            }
        }
    }
}

/**
 * Streaming Services Filter Content
 * Displays region selector and grid of streaming providers
 */
@Composable
private fun StreamingServicesFilterContent(
    viewModel: MediaDiscoveryViewModel,
    filters: BrowseModels.MediaFilters,
    onFiltersChange: (BrowseModels.MediaFilters) -> Unit
) {
    val context = LocalContext.current
    val imageLoader = SeerrTV.imageLoader
    val focusManager = LocalFocusManager.current
    
    val regions by viewModel.availableRegions.collectAsState()
    val watchProviders by viewModel.availableWatchProviders.collectAsState()
    val selectedWatchRegion by viewModel.selectedWatchRegion.collectAsState()
    
    var selectedProviderIndex by remember { mutableIntStateOf(0) }
    var selectedRegionIndex by remember { mutableIntStateOf(0) }
    var isRegionSelectorFocused by remember { mutableStateOf(true) } // Start with region focused
    var isRegionDropdownExpanded by remember { mutableStateOf(false) }
    var showRegionChangeDialog by remember { mutableStateOf(false) }
    var pendingRegion by remember { mutableStateOf<ca.devmesh.seerrtv.model.Region?>(null) }
    
    // Helper function to close the dialog - using remember to avoid linter warnings
    val closeDialog = remember {
        {
            showRegionChangeDialog = false
            pendingRegion = null
        }
    }
    
    val gridState = rememberLazyGridState()
    val regionListState = rememberLazyListState()
    val regionButtonFocusRequester = remember { FocusRequester() }
    val regionListFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }
    
    // Load providers when drawer opens or region changes
    LaunchedEffect(filters.watchRegion, filters.mediaType) {
        val defaultRegion = SharedPreferencesUtil.getDefaultStreamingRegion(context)
        val region = filters.watchRegion ?: defaultRegion
        viewModel.loadWatchProviders(filters.mediaType, region)
    }
    
    // Initialize region selector with current watchRegion or default region
    LaunchedEffect(regions, filters.watchRegion) {
        val defaultRegion = SharedPreferencesUtil.getDefaultStreamingRegion(context)
        val currentRegion = filters.watchRegion ?: defaultRegion
        val regionIndex = regions.indexOfFirst { it.iso_3166_1 == currentRegion }
        if (regionIndex >= 0) {
            selectedRegionIndex = regionIndex
        }
    }
    
    // Auto-scroll region list to keep selected item visible when expanded
    LaunchedEffect(selectedRegionIndex, isRegionDropdownExpanded) {
        if (isRegionDropdownExpanded && selectedRegionIndex >= 0 && selectedRegionIndex < regions.size) {
            regionListState.animateScrollToItem(selectedRegionIndex)
        }
    }
    
    // Request focus on list when dropdown expands
    LaunchedEffect(isRegionDropdownExpanded) {
        if (isRegionDropdownExpanded) {
            kotlinx.coroutines.delay(50)
            regionListFocusRequester.requestFocus()
        }
    }
    
    // Auto-scroll grid to keep selected item visible
    LaunchedEffect(selectedProviderIndex) {
        if (!isRegionSelectorFocused && selectedProviderIndex >= 0 && selectedProviderIndex < watchProviders.size) {
            val row = selectedProviderIndex / 4
            gridState.animateScrollToItem(row * 4)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
    ) {
        // Region Selector - Pulldown
        Text(
            text = "Region:",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Region pulldown button - shows only selected region
        val defaultRegion = SharedPreferencesUtil.getDefaultStreamingRegion(context)
        val currentRegion = regions.find { it.iso_3166_1 == (filters.watchRegion ?: defaultRegion) }
            ?: regions.find { it.iso_3166_1 == defaultRegion }
            ?: regions.firstOrNull()
        val displayRegionName = currentRegion?.english_name ?: "United States"
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(regionButtonFocusRequester)
                .focusable()
                .onFocusChanged { focusState ->
                    isRegionSelectorFocused = focusState.isFocused
                }
                .onPreviewKeyEvent { keyEvent ->
                    when {
                        KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyDown -> {
                            // Toggle dropdown only on Enter
                            isRegionDropdownExpanded = !isRegionDropdownExpanded
                            true
                        }
                        keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown && !isRegionDropdownExpanded -> {
                            // Move focus to grid when not expanded
                            isRegionSelectorFocused = false
                            gridFocusRequester.requestFocus()
                            true
                        }
                        keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown && !isRegionDropdownExpanded -> {
                            // Move focus to grid on down arrow (don't open dropdown)
                            isRegionSelectorFocused = false
                            gridFocusRequester.requestFocus()
                            true
                        }
                        else -> false
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isRegionSelectorFocused) Color(0xFF2C3E50) else Color(0xFF1E2732),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = if (isRegionSelectorFocused) 2.dp else 1.dp,
                        color = if (isRegionSelectorFocused) Color.White else Color.Gray.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayRegionName,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                // Dropdown chevron
                Text(
                    text = if (isRegionDropdownExpanded) "⌄" else "⌄",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        // Expanded region list
        AnimatedVisibility(
            visible = isRegionDropdownExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LazyColumn(
                state = regionListState,
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
                    .focusRequester(regionListFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { keyEvent ->
                        when {
                            keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                                if (selectedRegionIndex > 0) {
                                    selectedRegionIndex--
                                } else {
                                    // Move focus back to button when at top
                                    isRegionDropdownExpanded = false
                                    regionButtonFocusRequester.requestFocus()
                                }
                                true
                            }
                            keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                                if (selectedRegionIndex < regions.size - 1) {
                                    selectedRegionIndex++
                                }
                                true
                            }
                            KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyDown -> {
                                if (selectedRegionIndex >= 0 && selectedRegionIndex < regions.size) {
                                    val selectedRegion = regions[selectedRegionIndex]
                                    val newWatchRegion = selectedRegion.iso_3166_1
                                    val defaultRegion = SharedPreferencesUtil.getDefaultStreamingRegion(context)
                                    
                                    // Check if the selected region is different from the stored default
                                    if (newWatchRegion != defaultRegion) {
                                        // Show confirmation dialog
                                        pendingRegion = selectedRegion
                                        showRegionChangeDialog = true
                                    } else {
                                        // Just update the filter without confirmation
                                        onFiltersChange(filters.copy(watchRegion = newWatchRegion))
                                        viewModel.loadWatchProviders(filters.mediaType, newWatchRegion)
                                    }
                                    // Collapse dropdown after selection
                                    isRegionDropdownExpanded = false
                                    regionButtonFocusRequester.requestFocus()
                                }
                                true
                            }
                            keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyDown -> {
                                // Collapse dropdown on back
                                isRegionDropdownExpanded = false
                                regionButtonFocusRequester.requestFocus()
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                items(regions.size) { index ->
                    val region = regions[index]
                    val isSelected = filters.watchRegion == region.iso_3166_1
                    val isFocused = selectedRegionIndex == index
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isFocused) Color(0xFF2C3E50) else Color(0xFF1E2732)
                            )
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = region.english_name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        if (isSelected) {
                            Text(
                                text = "✓",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Provider Grid
        Text(
            text = "Streaming Services:",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier
                .weight(1f)
                .focusRequester(gridFocusRequester)
                .focusable()
                .onFocusChanged { focusState ->
                    isRegionSelectorFocused = !focusState.isFocused
                }
                .onPreviewKeyEvent { keyEvent ->
                    when {
                        keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                            // If in top row (indices 0-3), move focus to region pulldown
                            if (selectedProviderIndex < 4) {
                                isRegionSelectorFocused = true
                                regionButtonFocusRequester.requestFocus()
                            } else {
                                // Move up one row
                                selectedProviderIndex -= 4
                            }
                            true
                        }
                        keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                            val maxIndex = watchProviders.size - 1
                            val nextIndex = selectedProviderIndex + 4
                            if (nextIndex <= maxIndex) {
                                selectedProviderIndex = nextIndex
                            } else if (selectedProviderIndex < maxIndex) {
                                // Move to last item if we can't go down a full row
                                selectedProviderIndex = maxIndex
                            }
                            true
                        }
                        keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                            if (selectedProviderIndex > 0 && selectedProviderIndex % 4 != 0) {
                                selectedProviderIndex--
                            }
                            true
                        }
                        keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                            if (selectedProviderIndex < watchProviders.size - 1 && selectedProviderIndex % 4 != 3) {
                                selectedProviderIndex++
                            }
                            true
                        }
                        KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyDown -> {
                            if (selectedProviderIndex >= 0 && selectedProviderIndex < watchProviders.size) {
                                val provider = watchProviders[selectedProviderIndex]
                                val currentProviders = filters.watchProviders
                                val newProviders = if (currentProviders.contains(provider.id)) {
                                    currentProviders - provider.id
                                } else {
                                    currentProviders + provider.id
                                }
                                // Ensure watchRegion is set when providers are selected
                                val newWatchRegion = filters.watchRegion ?: selectedWatchRegion
                                onFiltersChange(filters.copy(
                                    watchProviders = newProviders,
                                    watchRegion = if (newProviders.isNotEmpty()) newWatchRegion else null
                                ))
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            items(watchProviders.size) { index ->
                val provider = watchProviders[index]
                val isSelected = filters.watchProviders.contains(provider.id)
                // Only show as focused if grid has focus AND this is the selected index
                val isFocused = !isRegionSelectorFocused && selectedProviderIndex == index
                
                // Animate scale for focused card
                val scale by animateFloatAsState(
                    targetValue = if (isFocused) 1.1f else 1.0f,
                    label = "providerCardScale"
                )
                
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .scale(scale)
                        .background(
                            color = if (isFocused) Color(0xFF2C3E50) else Color(0xFF1E2732),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = when {
                                isFocused -> 2.dp
                                isSelected -> 2.dp
                                else -> 0.dp
                            },
                            color = when {
                                isFocused -> Color.White
                                isSelected -> Color(0xFF4CAF50)
                                else -> Color.Transparent
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Provider Logo
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("https://image.tmdb.org/t/p/w500${provider.logoPath}")
                                .memoryCacheKey("provider_${provider.id}")
                                .diskCacheKey("provider_${provider.id}")
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = provider.name,
                            modifier = Modifier
                                .size(60.dp)
                                .fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Provider Name
                        Text(
                            text = provider.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Selection indicator - green circle with black checkmark in bottom right
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .background(
                                    color = Color(0xFF4CAF50),
                                    shape = CircleShape
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓",
                                color = Color.Black,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Request focus on region button initially
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100) // Small delay to ensure parent has finished setup
        focusManager.clearFocus(force = true)
        regionButtonFocusRequester.requestFocus()
    }
    
    // Show confirmation dialog when user selects a different region
    val currentPendingRegion = pendingRegion
    if (showRegionChangeDialog && currentPendingRegion != null) {
        Dialog(
            onDismissRequest = closeDialog
        ) {
            RegionChangeConfirmationDialog(
                regionName = currentPendingRegion.english_name,
                onYes = {
                    // Save as default and update filter
                    SharedPreferencesUtil.setDefaultStreamingRegion(context, currentPendingRegion.iso_3166_1)
                    onFiltersChange(filters.copy(watchRegion = currentPendingRegion.iso_3166_1))
                    viewModel.loadWatchProviders(filters.mediaType, currentPendingRegion.iso_3166_1)
                    closeDialog()
                },
                onNo = {
                    // Just update filter without saving as default
                    onFiltersChange(filters.copy(watchRegion = currentPendingRegion.iso_3166_1))
                    viewModel.loadWatchProviders(filters.mediaType, currentPendingRegion.iso_3166_1)
                    closeDialog()
                }
            )
        }
    }
}
