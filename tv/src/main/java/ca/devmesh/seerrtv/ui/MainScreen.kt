package ca.devmesh.seerrtv.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.model.Request
import ca.devmesh.seerrtv.navigation.NavigationManager
import ca.devmesh.seerrtv.navigation.rememberNavigationManager
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.ui.components.AuthenticationErrorHandler
import ca.devmesh.seerrtv.ui.position.ScrollPositionManager
import ca.devmesh.seerrtv.util.CommonUtil
import ca.devmesh.seerrtv.util.Permission
import ca.devmesh.seerrtv.ui.focus.AppFocusManager
import ca.devmesh.seerrtv.ui.focus.AppFocusState
import ca.devmesh.seerrtv.ui.focus.TopBarFocus
import ca.devmesh.seerrtv.ui.focus.DpadController
import ca.devmesh.seerrtv.ui.focus.createMainScreenDpadConfig
import ca.devmesh.seerrtv.viewmodel.CategoryCard
import ca.devmesh.seerrtv.viewmodel.DiscoveryType
import ca.devmesh.seerrtv.viewmodel.MediaCategory
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel
import coil3.compose.AsyncImage
import coil3.ImageLoader
import coil3.request.crossfade
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Ordered list of categories - defined once for the entire file
private val ORDERED_CATEGORIES = listOf(
    MediaCategory.RECENTLY_ADDED,
    MediaCategory.RECENT_REQUESTS,
    MediaCategory.TRENDING,
    MediaCategory.POPULAR_MOVIES,
    MediaCategory.MOVIE_GENRES,
    MediaCategory.UPCOMING_MOVIES,
    MediaCategory.STUDIOS,
    MediaCategory.POPULAR_SERIES,
    MediaCategory.SERIES_GENRES,
    MediaCategory.UPCOMING_SERIES,
    MediaCategory.NETWORKS
)

// Function to check if a category is a CategoryCard category
private fun isCategoryCardCategory(category: MediaCategory): Boolean {
    return category == MediaCategory.MOVIE_GENRES ||
            category == MediaCategory.SERIES_GENRES ||
            category == MediaCategory.STUDIOS ||
            category == MediaCategory.NETWORKS
}

// Unified focus management system - single source of truth for navigation focus
sealed class MainScreenFocusState {
    // Category row focus - no specific item selected
    data class CategoryRow(val category: MediaCategory) : MainScreenFocusState()

    // Media item focus - specific item within a category
    data class MediaItem(val category: MediaCategory, val index: Int) : MainScreenFocusState()

    override fun toString(): String {
        return when (this) {
            is CategoryRow -> "CategoryRow(${category.name})"
            is MediaItem -> "MediaItem(${category.name}, $index)"
        }
    }
}



// Enum to track what type of category is currently selected
enum class CategoryType {
    MEDIA,             // Regular media (movies, TV shows)
    CATEGORY_CARD     // Category cards (genres, studios, networks)
}

// Removed duplicate updateSelectedIndex function - using the one inside MainScreen

@Composable
fun MainScreen(
    context: Context,
    selectedCategory: MutableState<MediaCategory>,
    previousCategory: MutableState<MediaCategory>,
    selectedMediaIndex: MutableState<Int>,
    viewModel: SeerrViewModel,
    imageLoader: ImageLoader,
    navController: NavController,
    appFocusManager: AppFocusManager,
    dpadController: DpadController,
    currentBackStackEntry: String? = null,
    onFocusStateChange: (isInTopBar: Boolean, isSearchFocused: Boolean, isSettingsFocused: Boolean, showRefreshHint: Boolean, isRefreshRowVisible: Boolean) -> Unit = { _, _, _, _, _ -> },
    navigationManager: NavigationManager =
        rememberNavigationManager(
            scope = rememberCoroutineScope(),
            navController = navController
        )
) {
    // Use AppFocusManager as the single source of truth for focus
    val currentAppFocus = appFocusManager.currentFocus

    // Helper function to update selected index with proper state management
    fun updateSelectedIndex(
        category: MutableState<MediaCategory>,
        index: MutableState<Int>,
        newIndex: Int,
        force: Boolean = false
    ) {
        // Validate the new index
        val currentCategory = category.value
        val maxIndex = if (isCategoryCardCategory(currentCategory)) {
            val categoryData = viewModel.categoryCardData.value[currentCategory]
            if (categoryData is ApiResult.Success) categoryData.data.size - 1 else 0
        } else {
            val categoryData = viewModel.categoryData.value[currentCategory]
            if (categoryData is ApiResult.Success) categoryData.data.size - 1 else 0
        }
        
        // Safety check: Ensure maxIndex is at least 0 to prevent coerceIn error with empty lists
        val safeMaxIndex = maxIndex.coerceAtLeast(0)
        val validIndex = newIndex.coerceIn(0, safeMaxIndex)
        
        if (validIndex != index.value || force) {
            index.value = validIndex
            
            // Update ScrollPositionManager
            ScrollPositionManager.saveUserIndex("${currentCategory.name}_index", validIndex)
            
            if (BuildConfig.DEBUG) {
                Log.d("MainScreen", "🔢 Updated index for ${currentCategory.name}: ${index.value}")
            }
        }
    }

    // Function to handle category card selection and navigation
    fun handleCategoryCardSelection(categoryCard: CategoryCard) {
        when (categoryCard.discoveryType) {
            DiscoveryType.MOVIE_GENRE -> navigationManager.navigateToMediaDiscovery(
                categoryCard.discoveryType,
                categoryCard.id.toString(),
                categoryCard.name,
                categoryCard.imagePath ?: ""
            )

            DiscoveryType.SERIES_GENRE -> navigationManager.navigateToMediaDiscovery(
                categoryCard.discoveryType,
                categoryCard.id.toString(),
                categoryCard.name,
                categoryCard.imagePath ?: ""
            )

            DiscoveryType.STUDIO -> navigationManager.navigateToMediaDiscovery(
                categoryCard.discoveryType,
                categoryCard.id.toString(),
                categoryCard.name,
                categoryCard.imagePath ?: ""
            )

            DiscoveryType.NETWORK -> navigationManager.navigateToMediaDiscovery(
                categoryCard.discoveryType,
                categoryCard.id.toString(),
                categoryCard.name,
                categoryCard.imagePath ?: ""
            )

            else -> {
                // Fallback for other discovery types
                if (BuildConfig.DEBUG) {
                    Log.d("MainScreen", "Unhandled discovery type: ${categoryCard.discoveryType}")
                }
            }
        }
    }

    // CONSOLIDATED: Bidirectional sync between AppFocusManager and local state
    LaunchedEffect(currentAppFocus, selectedCategory.value, selectedMediaIndex.value) {
        when (currentAppFocus) {
            is AppFocusState.MainScreen -> {
                val mainScreenFocus = currentAppFocus.focus
                when (mainScreenFocus) {
                    // TopBar focus is now handled by TopBarController
                    is MainScreenFocusState.CategoryRow -> {
                        // Sync category from focus to local state
                        if (mainScreenFocus.category != selectedCategory.value) {
                            if (BuildConfig.DEBUG) {
                                Log.d("MainScreen", "🔄 Focus sync: CategoryRow - ${selectedCategory.value} -> ${mainScreenFocus.category}")
                            }
                            selectedCategory.value = mainScreenFocus.category
                            updateSelectedIndex(selectedCategory, selectedMediaIndex, 0, force = true)
                            viewModel.forceCarouselReset(mainScreenFocus.category, animate = true)
                        }
                        // Sync local state back to focus if needed
                        else if (selectedCategory.value != mainScreenFocus.category) {
                            appFocusManager.setFocus(AppFocusState.MainScreen(MainScreenFocusState.CategoryRow(selectedCategory.value)))
                        }
                    }
                    is MainScreenFocusState.MediaItem -> {
                        // Sync category and index from focus to local state
                        var needsUpdate = false
                        if (mainScreenFocus.category != selectedCategory.value) {
                            if (BuildConfig.DEBUG) {
                                Log.d("MainScreen", "🔄 Focus sync: MediaItem category - ${selectedCategory.value} -> ${mainScreenFocus.category}")
                            }
                            selectedCategory.value = mainScreenFocus.category
                            needsUpdate = true
                        }
                        if (mainScreenFocus.index != selectedMediaIndex.value) {
                            if (BuildConfig.DEBUG) {
                                Log.d("MainScreen", "🔄 Focus sync: MediaItem index - ${selectedMediaIndex.value} -> ${mainScreenFocus.index}")
                            }
                            updateSelectedIndex(selectedCategory, selectedMediaIndex, mainScreenFocus.index, force = true)
                            needsUpdate = true
                        }
                        
                        // Sync local state back to focus if needed
                        if (!needsUpdate) {
                            val shouldUpdate = mainScreenFocus.category != selectedCategory.value ||
                                    mainScreenFocus.index != selectedMediaIndex.value
                            if (shouldUpdate) {
                                appFocusManager.setFocus(AppFocusState.MainScreen(MainScreenFocusState.MediaItem(selectedCategory.value, selectedMediaIndex.value)))
                            }
                        }
                    }
                }
            }
            is AppFocusState.TopBar -> {
                // Handle top bar focus changes - no navigation needed
                if (BuildConfig.DEBUG) {
                    Log.d("MainScreen", "🔄 Focus sync: TopBar focus - no navigation needed")
                }
            }
            else -> {
                if (BuildConfig.DEBUG) {
                    Log.d("MainScreen", "🔄 Focus sync: Other focus state - $currentAppFocus")
                }
            }
        }
    }

    // Consolidated UI state management
    val uiState = remember {
        object {
            // These are now derived from AppFocusManager but kept for backward compatibility
            var isInTopBar by mutableStateOf(false)
            var showRequestActionModal by mutableStateOf(false)
            var isRefreshRowVisible by mutableStateOf(false)
            var showRefreshHint by mutableStateOf(false)
            var backPressCount by mutableIntStateOf(0)
            var ignoreKeyEvents by mutableStateOf(false)
            var enteredTopBarTime by mutableLongStateOf(0L)
            var hasShownRefreshHint by mutableStateOf(false)
            var cameFromDownNavigation by mutableStateOf(false)
            var downNavigationTimestamp by mutableLongStateOf(0L)
        }
    }

    // Consolidated media-related state
    val mediaState = remember {
        object {
            var selectedMedia by mutableStateOf<Media?>(null)
            var selectedRequest by mutableStateOf<Request?>(null)
            var selectedCategoryCard by mutableStateOf<CategoryCard?>(null)
            var selectedCategoryType by mutableStateOf(CategoryType.MEDIA)
            var preloadedMediaDetails by mutableStateOf<ApiResult<MediaDetails>?>(null)
        }
    }

    // Pre-load media details once when a request is selected (cache-only; no force refresh)
    LaunchedEffect(mediaState.selectedRequest) {
        mediaState.selectedRequest?.let { request ->
            val id = request.media.tmdbId.toString()
            val type = request.media.mediaType
            viewModel.getMediaDetails(id, type, forceRefresh = false).collect { result ->
                if (result !is ApiResult.Loading) {
                    mediaState.preloadedMediaDetails = result
                    return@collect
                }
            }
        }
    }

    // Access state collections directly
    val mainFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // CONSOLIDATED: Top bar state management and parent notification
    LaunchedEffect(currentAppFocus, uiState.showRefreshHint, uiState.isRefreshRowVisible) {
        val isInTopBar = currentAppFocus is AppFocusState.TopBar
        
        val isSearchFocused = when (currentAppFocus) {
            is AppFocusState.TopBar -> currentAppFocus.focus == TopBarFocus.Search
            else -> false
        }
        
        val isSettingsFocused = when (currentAppFocus) {
            is AppFocusState.TopBar -> currentAppFocus.focus == TopBarFocus.Settings
            else -> false
        }
        
        // Handle top bar entry/exit for refresh hint timing
        if (isInTopBar && !uiState.isInTopBar) {
            // User just entered the top bar
            uiState.enteredTopBarTime = System.currentTimeMillis()
            uiState.hasShownRefreshHint = false
            uiState.showRefreshHint = true

            // Auto-hide the hint after 7 seconds
            coroutineScope.launch {
                delay(7000)
                uiState.showRefreshHint = false
            }
        } else if (!isInTopBar && uiState.isInTopBar) {
            // User left the top bar
            uiState.showRefreshHint = false
            uiState.hasShownRefreshHint = false
        }
        
        // Update the cached top bar state
        uiState.isInTopBar = isInTopBar
        
        // Notify parent about focus state changes
        onFocusStateChange(
            isInTopBar,
            isSearchFocused,
            isSettingsFocused,
            uiState.showRefreshHint,
            uiState.isRefreshRowVisible
        )
    }

    // Collected states
    val categoryData by viewModel.categoryData.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isInitialLoad by viewModel.isInitialLoad.collectAsState()
    val showAuthenticationError by viewModel.showAuthenticationError.collectAsState()

    // Collect the category card data
    val categoryCardData by viewModel.categoryCardData.collectAsState()
    
//    // Debug StateFlow observation (only in debug builds)
//    if (BuildConfig.DEBUG) {
//        LaunchedEffect(Unit) {
//            viewModel.categoryData.collect { data ->
//                data.forEach { (category, result) ->
//                    when (result) {
//                        is ApiResult.Success -> { /* Debug logging can be added here if needed */ }
//                        is ApiResult.Loading -> { /* Debug logging can be added here if needed */ }
//                        is ApiResult.Error -> { /* Debug logging can be added here if needed */ }
//                    }
//                }
//            }
//        }
//    }

    // Collect the current selected media (for category cards)
    val currentCategoryMedia by viewModel.currentSelectedMedia.collectAsState()

    // Force update counter for MediaDetailsArea - can be optimized later
    val forceDetailsUpdate = remember { mutableIntStateOf(0) }

    // CONSOLIDATED: Single selection helper function
    fun setSelection(categoryType: CategoryType) {
        appFocusManager.setFocus(
            AppFocusState.MainScreen(
                MainScreenFocusState.MediaItem(
                    selectedCategory.value,
                    selectedMediaIndex.value
                )
            )
        )
        mediaState.selectedCategoryType = categoryType
    }

    // CONSOLIDATED: Force UI update helper to reduce redundant triggers
    fun forceUIUpdate() {
        forceDetailsUpdate.intValue += 1
    }

    // BackHandler is disabled; DpadController handles back and exit confirmation
    BackHandler(enabled = false) {}

    // Combined loading and refresh effect
    LaunchedEffect(isInitialLoad) {
        if (isInitialLoad) {
            viewModel.loadAllCategories()
        }
    }

    // Define visibleCategories at the MainScreen level for use throughout the function
    // Only calculate visible categories after initial load is complete to avoid filtering out categories that are still loading
    val visibleCategories = if (isInitialLoad) {
        // During initial load, show all categories to avoid premature filtering
        ORDERED_CATEGORIES.filter { category ->
            // Check RECENT_VIEW permission for RECENTLY_ADDED category
            if (category == MediaCategory.RECENTLY_ADDED &&
                !CommonUtil.hasPermission(
                    viewModel.getCurrentUserPermissions() ?: 0,
                    Permission.RECENT_VIEW
                )
            ) {
                return@filter false
            }
            true // Show all categories during initial load
        }
    } else {
        // After initial load, apply proper filtering based on API results
        ORDERED_CATEGORIES.filter { category ->
            // Check RECENT_VIEW permission for RECENTLY_ADDED category
            if (category == MediaCategory.RECENTLY_ADDED &&
                !CommonUtil.hasPermission(
                    viewModel.getCurrentUserPermissions() ?: 0,
                    Permission.RECENT_VIEW
                )
            ) {
                return@filter false
            }

            // For category cards (genres, studios, networks), check categoryCardData
            if (isCategoryCardCategory(category)) {
                when (categoryCardData[category]) {
                    is ApiResult.Success -> true  // Show category cards if API call succeeded
                    is ApiResult.Loading -> true  // Show while loading
                    is ApiResult.Error -> true    // Show error states
                    else -> false
                }
            } else {
                // For regular media categories, check categoryData
                when (val result = categoryData[category]) {
                    is ApiResult.Success -> result.data.isNotEmpty()  // Show media categories if they have items
                    is ApiResult.Loading -> true  // Show while loading
                    is ApiResult.Error -> true    // Show error states
                    else -> false
                }
            }
        }
    }
    
    // Navigation handlers for DpadController (defined after visibleCategories to avoid scope issues)
    val handleUp: () -> Unit = {
        when (val focus = currentAppFocus) {
            is AppFocusState.MainScreen -> {
                when (focus.focus) {
                    // TopBar navigation is now handled by TopBarController
                    is MainScreenFocusState.CategoryRow, is MainScreenFocusState.MediaItem -> {
                        // Navigate up to previous category or top bar
                        val currentIndex = visibleCategories.indexOf(selectedCategory.value)
                        if (currentIndex == 0) {
                            // Navigate to TopBar - handoff control to TopBarController
                            appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Search))
                        } else if (currentIndex > 0) {
                            // Save the current category before changing
                            val oldCategory = selectedCategory.value
                            previousCategory.value = oldCategory
                            
                            // Change to the new category
                            selectedCategory.value = visibleCategories[currentIndex - 1]
                            
                            // Clear indices for both old and new categories
                            ScrollPositionManager.clearStoredIndex("${oldCategory.name}_index")
                            ScrollPositionManager.clearStoredIndex("${selectedCategory.value.name}_index")
                            
                            // Force the LazyRow to scroll to start position
                            ScrollPositionManager.saveScrollPosition("${selectedCategory.value.name}_scroll", 0)
                            viewModel.forceCarouselReset(selectedCategory.value, animate = true)
                            
                            // Reset selection index
                            updateSelectedIndex(selectedCategory, selectedMediaIndex, 0, force = true)
                            
                            // Update selection type based on category
                            setSelection(
                                if (isCategoryCardCategory(selectedCategory.value)) CategoryType.CATEGORY_CARD else CategoryType.MEDIA
                            )
                        }
                    }
                }
            }
            else -> {
                // Other focus states don't affect MainScreen navigation
            }
        }
    }
    
    val handleDown: () -> Unit = {
        when (val focus = currentAppFocus) {
            is AppFocusState.MainScreen -> {
                when (focus.focus) {
                    // TopBar navigation is now handled by TopBarController
                    is MainScreenFocusState.CategoryRow, is MainScreenFocusState.MediaItem -> {
                        val currentIndex = visibleCategories.indexOf(selectedCategory.value)
                        if (currentIndex < visibleCategories.size - 1) {
                            // Save the current category before changing
                            val oldCategory = selectedCategory.value
                            
                            // Update the previous category reference
                            previousCategory.value = oldCategory
                            
                            // Change to the new category
                            selectedCategory.value = visibleCategories[currentIndex + 1]
                            
                            // Clear and reset indices for both old and new categories
                            ScrollPositionManager.clearStoredIndex("${oldCategory.name}_index")
                            ScrollPositionManager.clearStoredIndex("${selectedCategory.value.name}_index")
                            
                            // Force the LazyRow to scroll to start position
                            ScrollPositionManager.saveScrollPosition("${selectedCategory.value.name}_scroll", 0)
                            viewModel.forceCarouselReset(selectedCategory.value, animate = true)
                            
                            // Mark that we came from DOWN navigation
                            uiState.cameFromDownNavigation = true
                            uiState.downNavigationTimestamp = System.currentTimeMillis()
                            
                            // Force reset the selection index for the new category to 0
                            updateSelectedIndex(selectedCategory, selectedMediaIndex, 0, force = true)
                            
                            // Temporarily disable post-navigation state to prevent index restoration
                            ScrollPositionManager.setPostNavigationState(false)
                            
                            // Mark the new category as navigated to
                            ScrollPositionManager.markCategoryNavigated("${selectedCategory.value.name}_index")
                            
                            // Update selection type based on category
                            setSelection(
                                if (isCategoryCardCategory(selectedCategory.value)) CategoryType.CATEGORY_CARD else CategoryType.MEDIA
                            )
                        }
                    }
                }
            }
            else -> {
                // Other focus states don't affect MainScreen navigation
            }
        }
    }
    
    val handleLeft: () -> Unit = {
        when (val focus = currentAppFocus) {
            is AppFocusState.MainScreen -> {
                when (focus.focus) {
                    // TopBar navigation is now handled by TopBarController
                    is MainScreenFocusState.CategoryRow, is MainScreenFocusState.MediaItem -> {
                        // Get the appropriate data for the current category
                        val currentCategory = selectedCategory.value
                        
                        // Special fast path for moving to index 0 - don't block this for usability
                        if (selectedMediaIndex.value == 1) {
                            // Going from index 1 to 0 should never be blocked
                            updateSelectedIndex(selectedCategory, selectedMediaIndex, 0, force = true)
                            
                            if (BuildConfig.DEBUG) {
                                Log.d("MainScreen", "⬅️ Fast path to index 0 in ${currentCategory.name}")
                            }
                            
                            // Update the selection
                            setSelection(CategoryType.MEDIA)
                        }
                        // For other position changes, use standard navigation with reset check
                        else if (selectedMediaIndex.value > 1) {
                            // Standard left navigation for indices > 1
                            updateSelectedIndex(selectedCategory, selectedMediaIndex, selectedMediaIndex.value - 1)
                            
                            if (BuildConfig.DEBUG) {
                                Log.d("MainScreen", "⬅️ Moving left in ${currentCategory.name}, to index ${selectedMediaIndex.value}")
                            }
                            
                            // Update the selection
                            setSelection(CategoryType.MEDIA)
                        }
                    }
                }
            }
            else -> {
                // Other focus states don't affect MainScreen navigation
            }
        }
    }
    
    val handleRight: () -> Unit = {
        if (BuildConfig.DEBUG) {
            Log.d("MainScreen", "🔄 handleRight called - currentAppFocus: $currentAppFocus")
        }
        when (val focus = currentAppFocus) {
            is AppFocusState.MainScreen -> {
                when (focus.focus) {
                    // TopBar navigation is now handled by TopBarController
                    is MainScreenFocusState.CategoryRow, is MainScreenFocusState.MediaItem -> {
                        // Get the current category and check if it was recently reset
                        val currentCategory = selectedCategory.value
                        if (isCategoryCardCategory(currentCategory)) {
                            val currentCategoryData = viewModel.categoryCardData.value[currentCategory]
                            if (currentCategoryData is ApiResult.Success) {
                                val mediaCount = currentCategoryData.data.size
                                
                                // Check if we're at the last item and if more data is being loaded
                                val isAtLastItem = selectedMediaIndex.value >= mediaCount - 1
                                val isLoadingMore = viewModel.isLoadingMoreCategory(currentCategory)
                                val hasMorePages = currentCategoryData.paginationInfo?.hasMorePages != false
                                
                                // Only allow moving right if not at last item or we've reached the absolute end
                                if (!isAtLastItem || !hasMorePages) {
                                    // Use updateSelectedIndex to ensure ScrollPositionManager is updated
                                    val newIndex = (selectedMediaIndex.value + 1).coerceAtMost(mediaCount - 1)
                                    updateSelectedIndex(selectedCategory, selectedMediaIndex, newIndex)
                                    
                                    if (BuildConfig.DEBUG) {
                                        Log.d("MainScreen", "➡️ Moving right in ${currentCategory.name}, to index $newIndex")
                                    }
                                    
                                    // Update the selection
                                    setSelection(CategoryType.CATEGORY_CARD)
                                }
                                
                                if (BuildConfig.DEBUG && isAtLastItem) {
                                    Log.d("MainScreen", "🔚 At end of category cards: ${currentCategory.name}, loading=${isLoadingMore}, hasMore=${hasMorePages}")
                                }
                                
                                // Check if we need to load more data
                                if (isAtLastItem && hasMorePages && !isLoadingMore) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d("MainScreen", "📊 Triggering load more for category cards: ${currentCategory.name}")
                                    }
                                    
                                    // Set loading flag directly here for immediate UI feedback
                                    viewModel.setLoadMoreFlag(currentCategory, true)
                                    
                                    viewModel.loadMoreForCategory(context, currentCategory)
                                }
                            }
                        } else {
                            val currentCategoryData = categoryData[currentCategory]
                            if (currentCategoryData is ApiResult.Success) {
                                val mediaCount = currentCategoryData.data.size
                                
                                // Check if we're at the last item and if more data is being loaded
                                val isAtLastItem = selectedMediaIndex.value >= mediaCount - 1
                                val isLoadingMore = viewModel.isLoadingMoreCategory(currentCategory)
                                val hasMorePages = currentCategoryData.paginationInfo?.hasMorePages != false
                                
                                // Only allow moving right if not at last item or we've reached the absolute end
                                if (!isAtLastItem || !hasMorePages) {
                                    // Use updateSelectedIndex to ensure ScrollPositionManager is updated
                                    val newIndex = (selectedMediaIndex.value + 1).coerceAtMost(mediaCount - 1)
                                    updateSelectedIndex(selectedCategory, selectedMediaIndex, newIndex)
                                    
                                    if (BuildConfig.DEBUG) {
                                        Log.d("MainScreen", "➡️ Moving right in ${currentCategory.name}, to index $newIndex")
                                    }
                                    
                                    // Update the selection
                                    setSelection(CategoryType.MEDIA)
                                }
                                
                                if (BuildConfig.DEBUG && isAtLastItem) {
                                    Log.d("MainScreen", "🔚 At end of media: ${currentCategory.name}, loading=${isLoadingMore}, hasMore=${hasMorePages}")
                                }
                                
                                // Check if we need to load more data
                                if (isAtLastItem && hasMorePages && !isLoadingMore) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d("MainScreen", "📊 Triggering load more for media: ${currentCategory.name}")
                                    }
                                    
                                    // Set loading flag directly here for immediate UI feedback
                                    viewModel.setLoadMoreFlag(currentCategory, true)
                                    
                                    viewModel.loadMoreForCategory(context, currentCategory)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                // Other focus states don't affect MainScreen navigation
                if (BuildConfig.DEBUG) {
                    Log.d("MainScreen", "🔄 handleRight: Other focus state - $focus")
                }
            }
        }
    }
    
    val handleEnter: () -> Unit = {
        when (val focus = currentAppFocus) {
            is AppFocusState.MainScreen -> {
                when (focus.focus) {
                    // TopBar actions are now handled by TopBarController
                    is MainScreenFocusState.CategoryRow, is MainScreenFocusState.MediaItem -> {
                        // Handle media/category card selection
                        val currentCategory = selectedCategory.value
                        val currentIndex = selectedMediaIndex.value
                        
                        if (isCategoryCardCategory(currentCategory)) {
                            val categoryCardData = viewModel.categoryCardData.value[currentCategory]
                            if (categoryCardData is ApiResult.Success && currentIndex < categoryCardData.data.size) {
                                val selectedCard = categoryCardData.data[currentIndex]
                                handleCategoryCardSelection(selectedCard)
                            }
                        } else {
                            val mediaData = categoryData[currentCategory]
                            if (mediaData is ApiResult.Success && currentIndex < mediaData.data.size) {
                                val selectedItem = mediaData.data[currentIndex]
                                if (currentCategory == MediaCategory.RECENT_REQUESTS) {
                                    uiState.showRequestActionModal = true
                                    mediaState.selectedRequest = selectedItem.request
                                } else {
                                    navigationManager.navigateToDetails(
                                        selectedItem.id.toString(),
                                        selectedItem.mediaType
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                // Other focus states don't affect MainScreen navigation
            }
        }
    }
    
    // Debug logging (only in debug builds)
    if (BuildConfig.DEBUG) {
        LaunchedEffect(categoryData, categoryCardData) {
            // Debug logging can be added here if needed
        }
    }

    // Register MainScreen with the centralized DpadController
    val dpadConfig = createMainScreenDpadConfig(
        route = "main",
        focusManager = appFocusManager,
        onUp = handleUp,
        onDown = handleDown,
        onLeft = handleLeft,
        onRight = handleRight,
        onEnter = handleEnter,
        onRefresh = {
            // Handle refresh when triggered by DpadController
            uiState.showRefreshHint = false
            uiState.isRefreshRowVisible = true

            // Trigger the actual refresh (spinner shows while isRefreshing=true)
            coroutineScope.launch {
                viewModel.refreshAllCategories()
                // Keep focus management the same after refresh completes
                if (visibleCategories.isNotEmpty()) {
                    selectedCategory.value = visibleCategories[0]
                    updateSelectedIndex(selectedCategory, selectedMediaIndex, 0, force = true)
                    viewModel.forceCarouselReset(selectedCategory.value, animate = true)
                    mediaState.selectedCategoryType = CategoryType.MEDIA
                }
                appFocusManager.setFocus(AppFocusState.MainScreen(MainScreenFocusState.CategoryRow(selectedCategory.value)))
            }

            // Fallback auto-hide in case isRefreshing callback is missed
            coroutineScope.launch {
                delay(3000)
                if (uiState.isRefreshRowVisible) uiState.isRefreshRowVisible = false
            }
        },
        onBack = {
            // Show exit confirmation on MainScreen via double-back
            uiState.backPressCount++
            if (uiState.backPressCount == 1) {
                Toast.makeText(
                    context,
                    context.getString(R.string.mainScreen_pressBackAgainToExit),
                    Toast.LENGTH_SHORT
                ).show()
                coroutineScope.launch {
                    delay(2000)
                    uiState.backPressCount = 0
                }
            } else if (uiState.backPressCount == 2) {
                (context as? Activity)?.finish()
            }
        }
    )
    
    // Register the screen configuration with the DpadController
    LaunchedEffect(dpadConfig) {
        dpadController.registerScreen(dpadConfig)
    }

    // CONSOLIDATED: UI state management effects (refresh hint, modal handling, navigation)
    LaunchedEffect(Unit) {
        // Effect for refresh hint timer
        launch {
            snapshotFlow { uiState.showRefreshHint }
                .collect { showRefreshHint ->
                    if (showRefreshHint) {
                        delay(3000)
                        uiState.showRefreshHint = false
                    }
                }
        }

        // Effect for modal state management
        launch {
            snapshotFlow { uiState.showRequestActionModal }
                .collect { showModal ->
                    if (!showModal) {
                        // Modal was just closed, temporarily ignore key events
                        uiState.ignoreKeyEvents = true
                        delay(300) // Match the modal exit animation duration
                        uiState.ignoreKeyEvents = false
                        mainFocusRequester.requestFocus()
                    }
                }
        }

        // Effect for handling navigation events
        launch {
            navController.currentBackStackEntryFlow.collect { backStackEntry ->
                if (backStackEntry.destination.route == "main") {
                    // Only reset focus if we're not returning from discovery
                    if (currentBackStackEntry != "discovery_to_main") {
                        appFocusManager.setFocus(
                            AppFocusState.MainScreen(
                                MainScreenFocusState.CategoryRow(selectedCategory.value)
                            )
                        )
                        mainFocusRequester.requestFocus()
                        if (selectedMediaIndex.value == -1) {
                            updateSelectedIndex(selectedCategory, selectedMediaIndex, 0, force = true)
                        }
                        
                        if (BuildConfig.DEBUG) {
                            Log.d("MainScreen", "🔙 Back to main route, focusing category ${selectedCategory.value.name}")
                        }
                    } else if (BuildConfig.DEBUG) {
                        Log.d("MainScreen", "🔙 Back to main route from discovery - skipping focus reset")
                    }
                }
            }
        }
    }

    // Combined comprehensive effect for handling all currentBackStackEntry related logic
    LaunchedEffect(currentBackStackEntry) {
        // Log the current entry and refresh state immediately
        Log.d(
            "MainScreen",
            "🚀 NAVIGATION ENTRY: Processing $currentBackStackEntry with refreshRequired=${viewModel.refreshRequired().value}"
        )

        if (currentBackStackEntry == "discovery_to_main" || currentBackStackEntry == "details_to_main") {

            // Common setup for both navigation types
            ScrollPositionManager.setPostNavigationState(true)

            // For discovery_to_main specifically with category cards - special handling
            if (currentBackStackEntry == "discovery_to_main" && isCategoryCardCategory(
                    selectedCategory.value
                )
            ) {
                // Force save the current index for this category to ensure it's available
                val categoryKey = "${selectedCategory.value.name}_index"

                // Get the current index or the saved index, whichever is higher
                val currentIndex = selectedMediaIndex.value
                val savedIndex = ScrollPositionManager.getUserIndex(categoryKey)
                val bestIndex = maxOf(currentIndex, savedIndex)

                // Save whichever index is highest to ensure we don't lose our position

                // Always use the forced save to ensure it overrides any automatic saves
                ScrollPositionManager.saveUserIndex(categoryKey, bestIndex)

                // Also update the selectedMediaIndex to ensure UI consistency
                if (bestIndex != currentIndex) {
                    selectedMediaIndex.value = bestIndex
                }


                // After saving, force the UI to respect this value
                forceUIUpdate()
                
                // Set focus to the specific media item to preserve selection
                appFocusManager.setFocus(
                    AppFocusState.MainScreen(
                        MainScreenFocusState.MediaItem(selectedCategory.value, bestIndex)
                    )
                )

                // CRITICAL SOLUTION:
                // Since previous approaches couldn't prevent index 0 from grabbing focus,
                // wait for a bit and then force the right index with a series of delayed corrections
                if (bestIndex > 0) {
                    // First attempt - after UI has settled
                    coroutineScope.launch {
                        delay(100)
                        // Only update if we have a valid saved index
                        updateSelectedIndex(
                            selectedCategory,
                            selectedMediaIndex,
                            bestIndex,
                            force = true
                        )
                        forceUIUpdate()


                        // Second attempt - a bit later to override any subsequent focus
                        delay(200)
                        updateSelectedIndex(
                            selectedCategory,
                            selectedMediaIndex,
                            bestIndex,
                            force = true
                        )
                        forceUIUpdate()


                        // Third attempt - final override after everything else is done
                        delay(500)
                        updateSelectedIndex(
                            selectedCategory,
                            selectedMediaIndex,
                            bestIndex,
                            force = true
                        )
                        forceUIUpdate()

                    }
                }
            }
            // For category cards (applies to both navigation scenarios)
            else if (isCategoryCardCategory(selectedCategory.value)) {
                val categoryKey = "${selectedCategory.value.name}_index"
                val savedIndex = ScrollPositionManager.getUserIndex(categoryKey)

                if (savedIndex >= 0) {
                    // Force the saved index to be selected

                    // Critical: Override the selection index immediately, BEFORE LazyRow composition
                    updateSelectedIndex(selectedCategory, selectedMediaIndex, savedIndex, force = true)
                    forceUIUpdate()

                    // After a brief delay, force it again to ensure it takes effect
                    coroutineScope.launch {
                        delay(100)
                        updateSelectedIndex(selectedCategory, selectedMediaIndex, savedIndex, force = true)
                        forceUIUpdate()

                    }
                }
            }

            // Ensure we restore proper selection state based on category type
            if (isCategoryCardCategory(selectedCategory.value)) {
                // For category card categories, make sure the selection type is CATEGORY_CARD
                setSelection(CategoryType.CATEGORY_CARD)

                // For discovery screen returns specifically, we need to update the selection with additional debug
                if (currentBackStackEntry == "discovery_to_main") {
                    // Force visual highlight update by triggering a counter update
                    forceUIUpdate()

                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "MainScreen",
                            "🔍 Discovery->Main: initial forcing update for category card, index=${selectedMediaIndex.value}"
                        )
                    }

                    // Force carousel scroll to show the selected item
                    coroutineScope.launch {
                        delay(150) // Short delay to ensure state is updated

                        // Force a recomposition of the category rows
                        forceUIUpdate()

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "MainScreen",
                                "🎯 Discovery->Main: second forcing selection update for category card at index ${selectedMediaIndex.value}"
                            )
                        }
                    }
                }

                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MainScreen",
                        "🔢 Restored category card selection at index ${selectedMediaIndex.value} for ${selectedCategory.value.name}"
                    )
                }
            } else {
                // For media categories, make sure the selection type is MEDIA_CARD
                mediaState.selectedCategoryType = CategoryType.MEDIA

                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MainScreen",
                        "🔢 Restored media selection at index ${selectedMediaIndex.value} for ${selectedCategory.value.name}"
                    )
                }
            }

            // Only refresh if a request was made
            if (viewModel.refreshRequired().value) {
                Log.d(
                    "MainScreen",
                    "🔄 Request was made, refreshing RECENT_REQUESTS category"
                )

                // Critical: Explicitly block all key events during refresh
                uiState.ignoreKeyEvents = true

                coroutineScope.launch {
                    try {
                        // Clear existing data - this is essential for a full refresh
                        viewModel.clearCategoryData(MediaCategory.RECENT_REQUESTS)

                        // Force reset pagination for the category
                        viewModel.resetApiPagination(MediaCategory.RECENT_REQUESTS)

                        Log.d(
                            "MainScreen",
                            "⏱️ Waiting briefly for server to update before fetching new data"
                        )

                        // Give the server more time to update its database
                        // For new requests, allow ~2 seconds for backend processing
                        delay(2000)

                        // Force carousel reset first to clear any cached state
                        viewModel.forceCarouselReset(MediaCategory.RECENT_REQUESTS, animate = true)

                        // Clear image caches
                        val imageLoader = ImageLoader.Builder(context)
                            .build()
                        imageLoader.memoryCache?.clear()
                        imageLoader.diskCache?.clear()

                        // Now perform the refresh - this is similar to the full refresh logic but focused on one category
                        viewModel.refreshCategoryWithForce(MediaCategory.RECENT_REQUESTS)

                        // Allow sufficient time for the refresh to complete and data to load
                        delay(1500)

                        // Force a second carousel reset to ensure updated data is displayed
                        viewModel.forceCarouselReset(MediaCategory.RECENT_REQUESTS, animate = true)

                        // Reset the forced update counter to trigger UI update
                        forceUIUpdate()

                        // Re-enable key events
                        uiState.ignoreKeyEvents = false

                        Log.d("MainScreen", "✅ RECENT_REQUESTS refresh completed")
                    } catch (e: Exception) {
                        Log.e("MainScreen", "Error refreshing RECENT_REQUESTS: ${e.message}", e)
                        uiState.ignoreKeyEvents = false
                    }
                }

                // Clear the refresh required flag
                viewModel.clearRefreshRequired()

                // If user is already on RECENT_REQUESTS category, ensure selection remains valid
                if (selectedCategory.value == MediaCategory.RECENT_REQUESTS) {
                    coroutineScope.launch {
                        // Wait for refresh to complete
                        delay(2000)

                        val categoryResult = categoryData[MediaCategory.RECENT_REQUESTS]
                        if (categoryResult is ApiResult.Success) {
                            // Reset to the first item (the newly added request should be here)
                            updateSelectedIndex(selectedCategory, selectedMediaIndex, 0, force = true)

                            // Update selected media with latest data
                            if (categoryResult.data.isNotEmpty()) {
                                mediaState.selectedMedia = categoryResult.data[0]
                            }

                            // Force a final UI update
                            forceUIUpdate()

                            Log.d("MainScreen", "🔄 Selection reset to show newest request")
                        }
                    }
                }
            }

            // Force an immediate update based on the current selection
            val category = selectedCategory.value
            val index = selectedMediaIndex.value

            if (isCategoryCardCategory(category)) {
                // For category cards, update the selection
                val categoryCardData = viewModel.categoryCardData.value[category]
                if (categoryCardData is ApiResult.Success) {
                    if (index >= 0 && index < categoryCardData.data.size) {
                        // Get the current card
                        val card = categoryCardData.data[index]

                        // Update the selected card
                        mediaState.selectedCategoryCard = card

                        // Update the view model's current media
                        val categoryMedia = viewModel.categoryCardToMedia(card)
                        viewModel.updateCurrentMedia(categoryMedia)

                        // Force details area to update
                        forceUIUpdate()

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "MainScreen",
                                "🎯 Immediately restored category card selection on return: ${card.name}"
                            )
                        }
                    }
                }
            } else {
                // This is the existing code for regular media
                val currentCategoryData = categoryData[category]
                if (currentCategoryData is ApiResult.Success) {
                    if (index >= 0 && index < currentCategoryData.data.size) {
                        // Update selected media directly without waiting for recomposition
                        val media = currentCategoryData.data[index]
                        mediaState.selectedMedia = media

                        // Ensure correct selection type is set
                        if (isCategoryCardCategory(category)) {
                            mediaState.selectedCategoryType = CategoryType.CATEGORY_CARD
                        } else {
                            mediaState.selectedCategoryType = CategoryType.MEDIA
                        }

                        // Force details area to update
                        forceUIUpdate()

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "MainScreen",
                                "🎯 Immediately restored selection on return from details: ${media.title ?: media.name}"
                            )
                        }
                    }
                }
            }

            // Add a brief delay before allowing scrolling animations
            // This helps prevent flickering when returning from details screen
            delay(300)

            // Log refresh status when returning from any screen for debugging
            Log.d(
                "MainScreen",
                "🚨 CHECKING REFRESH FLAG: refreshRequired=${viewModel.refreshRequired().value}"
            )

            // Only refresh if a request was made
        }
    }

    // OPTIMIZED: Combined category and selection type management
    LaunchedEffect(selectedCategory.value) {
        // Handle category changes
        if (selectedCategory.value != previousCategory.value) {
            // Get the category key for index storage
            val categoryKey = "${selectedCategory.value.name}_index"

            // Check if we came from DOWN navigation
            val fromDownNavigation = uiState.cameFromDownNavigation &&
                    (System.currentTimeMillis() - uiState.downNavigationTimestamp < 500)

            // Check if there's a saved index for this category
            val savedIndex = ScrollPositionManager.getUserIndex(categoryKey)

            // Only restore the saved index if NOT from DOWN navigation AND the category wasn't 
            // recently reset AND there's a valid saved index greater than 0
            if (savedIndex > 0 && !fromDownNavigation) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MainScreen",
                        "🔢 Restored index $savedIndex for ${selectedCategory.value.name} during category switch"
                    )
                }
                updateSelectedIndex(selectedCategory, selectedMediaIndex, savedIndex, force = true)
            } else {
                // No saved index or category was reset or from DOWN navigation - use index 0
                updateSelectedIndex(selectedCategory, selectedMediaIndex, 0, force = true)

                if (BuildConfig.DEBUG) {
                    if (fromDownNavigation) {
                        Log.d(
                            "MainScreen",
                            "🔄 Keeping index at 0 for ${selectedCategory.value.name} due to DOWN navigation"
                        )
                    }
                }
            }

            // Mark this category as navigated to for index protection
            ScrollPositionManager.markCategoryNavigated(categoryKey)

            previousCategory.value = selectedCategory.value

            // Update the selection type based on category type
            if (isCategoryCardCategory(selectedCategory.value)) {
                mediaState.selectedCategoryType = CategoryType.CATEGORY_CARD
            } else {
                mediaState.selectedCategoryType = CategoryType.MEDIA
            }

            // Reset the DOWN navigation flag after processing
            if (fromDownNavigation) {
                uiState.cameFromDownNavigation = false
            }
        }
    }

    // OPTIMIZED: Combined media selection effects into a single LaunchedEffect with multiple conditions
    LaunchedEffect(
        selectedCategory.value,
        selectedMediaIndex.value,
        categoryData,
        categoryCardData,
        forceDetailsUpdate.intValue
    ) {
        // 1. Handle regular media selection
        val currentCategoryData = categoryData[selectedCategory.value]
        if (!isCategoryCardCategory(selectedCategory.value) && currentCategoryData is ApiResult.Success) {
            val newMedia = currentCategoryData.data.getOrNull(selectedMediaIndex.value)

            // Add debug logging for selection updates
            if (BuildConfig.DEBUG) {
                Log.d(
                    "MainScreen",
                    "🔄 Selection check: category=${selectedCategory.value.name}, index=${selectedMediaIndex.value}, " +
                            "forceUpdate=${forceDetailsUpdate.intValue}, mediaListSize=${currentCategoryData.data.size}"
                )
                Log.d(
                    "MainScreen",
                    "🔄 Current media: ${mediaState.selectedMedia?.title ?: mediaState.selectedMedia?.name}, " +
                            "New media: ${newMedia?.title ?: newMedia?.name}"
                )
            }

            // Update selection when needed
            if (newMedia != null && (newMedia != mediaState.selectedMedia || forceDetailsUpdate.intValue > 0)) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MainScreen",
                        "✅ Updating selected media: ${mediaState.selectedMedia?.title ?: mediaState.selectedMedia?.name} -> ${newMedia.title ?: newMedia.name}"
                    )
                }
                // Save previous media
                // Store previous media if needed
                // Update selected media
                mediaState.selectedMedia = newMedia

                // Also update the ViewModel's currentSelectedMedia
                viewModel.updateCurrentMedia(newMedia)

                // Ensure correct selection type is set
                mediaState.selectedCategoryType = CategoryType.MEDIA
            }
        }

        // 2. Handle category card selection
        if (isCategoryCardCategory(selectedCategory.value)) {
            val currentCategoryCardData = categoryCardData[selectedCategory.value]
            if (currentCategoryCardData is ApiResult.Success) {
                val cardList = currentCategoryCardData.data
                if (cardList.isNotEmpty() && selectedMediaIndex.value < cardList.size) {
                    // Get the currently highlighted category card
                    val selectedCard = cardList[selectedMediaIndex.value]
                    // Update the selectedCategoryCard
                    mediaState.selectedCategoryCard = selectedCard
                    // Convert it to a Media object and update the ViewModel
                    val categoryMedia = viewModel.categoryCardToMedia(selectedCard)
                    viewModel.updateCurrentMedia(categoryMedia)

                    // Ensure the correct selection type is set
                    setSelection(CategoryType.CATEGORY_CARD)

                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "MainScreen",
                            "🎭 Updating selected category card: ${selectedCard.name}, ${selectedCategory.value.name}, index=${selectedMediaIndex.value}"
                        )
                    }
                }
            }
        } else {
            // Clear the category card selection when not in a category card category
            mediaState.selectedCategoryCard = null
        }
    }

    // OPTIMIZED: Combined backdrop update effects
    LaunchedEffect(selectedCategory.value, mediaState.selectedMedia, mediaState.selectedCategoryCard) {
        if (BuildConfig.DEBUG) {
            Log.d(
                "MainScreen", "🔄 Selection change detected: category=${selectedCategory.value}, " +
                        "media=${mediaState.selectedMedia?.title ?: mediaState.selectedMedia?.name ?: "none"}, " +
                        "card=${mediaState.selectedCategoryCard?.name ?: "none"}"
            )
        }

        // Ensure backdrop is updated based on selection
        if (isCategoryCardCategory(selectedCategory.value) && mediaState.selectedCategoryCard != null) {
            // For category cards, create media representation
            val categoryMedia = viewModel.categoryCardToMedia(mediaState.selectedCategoryCard!!)
            viewModel.updateCurrentMedia(categoryMedia)
        } else if (mediaState.selectedMedia != null) {
            // For regular media
            viewModel.updateCurrentMedia(mediaState.selectedMedia)
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            // Refresh has completed; briefly show "Refreshed" message if the row is visible
            // and then hide the refresh row after a short delay
            selectedCategory.value = selectedCategory.value
            if (uiState.isRefreshRowVisible) {
                // Allow the "Refreshed" text to be visible before hiding
                delay(1500)
                uiState.isRefreshRowVisible = false
            }
        }
    }

    // Handle config navigation
    fun handleConfigNavigation() {
        navigationManager.navigateToConfig()
    }

    // Request focus to the main container when the screen is first composed
    LaunchedEffect(Unit) {
        // Request focus once to the main container
        mainFocusRequester.requestFocus()

        if (BuildConfig.DEBUG) {
            Log.d("MainScreen", "🎯 Requested focus to main container")
        }
    }

    // Set initial focus when data becomes available
    LaunchedEffect(visibleCategories, isInitialLoad, categoryData, currentBackStackEntry) {
        // Only set initial focus when we have data loaded and not in initial load state
        // Skip this logic when returning from discovery to preserve restored focus state
        if (!isInitialLoad && visibleCategories.isNotEmpty() && currentBackStackEntry != "discovery_to_main") {
            // Check if selectedCategory is null or not in visible categories
            val currentCategory = selectedCategory.value
            val shouldSetInitialFocus = currentCategory == MediaCategory.RECENTLY_ADDED || currentCategory !in visibleCategories
            
            if (shouldSetInitialFocus) {
                // After initial load, prioritize RECENTLY_ADDED if it's available in the filtered list
                val targetCategory = if (visibleCategories.contains(MediaCategory.RECENTLY_ADDED)) {
                    MediaCategory.RECENTLY_ADDED
                } else {
                    visibleCategories[0]
                }
                
                if (targetCategory != selectedCategory.value) {
                    selectedCategory.value = targetCategory
                    updateSelectedIndex(selectedCategory, selectedMediaIndex, 0, force = true)
                }
                
                // Set focus to the target category
                appFocusManager.setFocus(
                    AppFocusState.MainScreen(
                        MainScreenFocusState.CategoryRow(targetCategory)
                    )
                )
                
                if (BuildConfig.DEBUG) {
                    Log.d("MainScreen", "🚀 NAVIGATION ENTRY: Processing null with refreshRequired=false")
                    Log.d("MainScreen", "🔄 Selection change detected: category=${selectedCategory.value}, media=none, card=none")
                    Log.d("MainScreen", "🎯 Requested focus to main container")
                    Log.d("MainScreen", "🔙 Back to main route, focusing category ${selectedCategory.value}")
                }
            }
        } else if (currentBackStackEntry == "discovery_to_main") {
            if (BuildConfig.DEBUG) {
                Log.d("MainScreen", "🚀 NAVIGATION ENTRY: Skipping initial focus setup - preserving restored focus state from discovery")
            }
        }
    }

    // Add lifecycle observer to detect when app comes back from background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Clear any navigation locks when the app is resumed
                    // This prevents navigation from getting stuck in locked state
                    ScrollPositionManager.clearAllNavigationLocks()

                    // Also reset any UI state that might be blocking navigation
                    uiState.ignoreKeyEvents = false

                    if (BuildConfig.DEBUG) {
                        Log.d("MainScreen", "🔄 App resumed, cleared navigation locks")
                    }
                }

                else -> { /* Ignore other lifecycle events */
                }
            }
        }

        // Register the observer
        lifecycleOwner.lifecycle.addObserver(observer)

        // Clean up when the composable leaves the composition
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Main UI container with centralized key handling
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(mainFocusRequester)
            .focusable()
    ) {
        // Backdrop with crossfade
        Crossfade(
            targetState = currentCategoryMedia ?: mediaState.selectedMedia, // Use currentCategoryMedia first
            modifier = Modifier.fillMaxSize(),
            label = ""
        ) { media -> MediaBackdrop(context, media, imageLoader) }

        // Content
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = uiState.isRefreshRowVisible,
                enter = slideInVertically() + expandVertically(),
                exit = slideOutVertically() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Text(
                            stringResource(id = R.string.mainScreen_refreshed),
                            color = Color.White
                        )
                    }
                }
            }

            // Top bar is now handled by the persistent MainTopBar component

            Spacer(modifier = Modifier.weight(1f))

            // Pass the forced update parameter to ensure the details area refreshes
            // We need to decide which media object to display
            // If there's a special category card selected, create a media object for it
            val mediaToDisplay = remember(
                selectedCategory.value,
                selectedMediaIndex.value,
                categoryCardData,
                categoryData,
                forceDetailsUpdate.intValue
            ) {
                // Get the currently selected category
                val currentCategory = selectedCategory.value

                // For CategoryCard categories, get the card at the current index
                if (isCategoryCardCategory(currentCategory)) {
                    val apiResult = categoryCardData[currentCategory]
                    if (apiResult is ApiResult.Success) {
                        val cardList = apiResult.data
                        // Ensure valid index before accessing the list
                        if (cardList.isNotEmpty() && selectedMediaIndex.value >= 0 && selectedMediaIndex.value < cardList.size) {
                            // Get the currently highlighted category card and convert to media
                            return@remember viewModel.categoryCardToMedia(cardList[selectedMediaIndex.value])
                        }
                    }
                    // Fallback to null if we can't get a card
                    null
                } else {
                    // For regular media categories, get the media at the current index
                    val apiResult = categoryData[currentCategory]
                    if (apiResult is ApiResult.Success) {
                        val mediaList = apiResult.data
                        // Ensure valid index before accessing the list
                        if (mediaList.isNotEmpty() && selectedMediaIndex.value >= 0 && selectedMediaIndex.value < mediaList.size) {
                            // Get the currently highlighted media
                            return@remember mediaList[selectedMediaIndex.value]
                        }
                    }
                    // Fallback to null if we can't get media
                    null
                }
            }

            // Show the details area with the appropriate media object
            MediaDetailsArea(mediaToDisplay, forceDetailsUpdate.intValue)

            // Derive isInTopBar directly from currentAppFocus to avoid staleness
            val isInTopBarNow = remember(currentAppFocus) {
                when (currentAppFocus) {
                    is AppFocusState.TopBar -> true
                    // TopBar focus is now handled by TopBarController
                    else -> false
                }
            }

            if (BuildConfig.DEBUG) {
                Log.d("MainScreen", "➡️ Passing isInTopBar=$isInTopBarNow to ScrollableCategoriesSection (focus=$currentAppFocus)")
            }

            ScrollableCategoriesSection(
                categories = visibleCategories.also { 
                    if (BuildConfig.DEBUG) {
                        Log.d("MainScreen", "🎬 Rendering ScrollableCategoriesSection with ${it.size} categories: ${it.map { cat -> cat.name }}")
                    }
                },
                categoryData = categoryData,
                categoryCardData = categoryCardData,
                selectedCategory = selectedCategory,
                selectedMediaIndex = selectedMediaIndex,
                context = context,
                imageLoader = imageLoader,
                viewModel = viewModel,
                isInTopBar = isInTopBarNow, // Use direct focus-derived value
                // Add a way to trigger the force update from child components
                // Pass selectedCategoryCard
            )
        }


        // Add authentication error handler
        AuthenticationErrorHandler(
            isVisible = showAuthenticationError,
            onRetry = {
                viewModel.hideAuthenticationError()
                viewModel.retryLastAction()
            },
            onReconfigure = {
                viewModel.hideAuthenticationError()
                handleConfigNavigation()
            }
        )

        // Add RequestActionModal
        mediaState.selectedRequest?.let { request ->
            if (BuildConfig.DEBUG) {
                Log.d(
                    "MainScreen",
                    "🎬 Rendering modal for request ID: ${request.id}, Media TMDB ID: ${request.media.tmdbId}"
                )
            }
            val currentUserPermissions = viewModel.getCurrentUserPermissions() ?: 0
            val canDeleteRequest =
                remember(currentUserPermissions, request.requestedBy.id) {
                    CommonUtil.canDeleteRequest(
                        userPermissions = currentUserPermissions,
                        isRequestor =
                            viewModel.getCurrentUserId() == request.requestedBy.id,
                        isRequestPending = false
                    )
                }
            RequestActionModal(
                isVisible = uiState.showRequestActionModal,
                request = request,
                imageLoader = imageLoader,
                onDismiss = {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainScreen", "🔚 Dismissing modal for request ID: ${request.id}")
                    }
                    uiState.showRequestActionModal = false
                    mediaState.selectedRequest =
                        null  // Reset the selected request when modal is dismissed
                    // Request focus back to main screen after modal is dismissed
                    mainFocusRequester.requestFocus()
                },
                onDelete = {
                    coroutineScope.launch {
                        viewModel.deleteRequest(request.id)
                        uiState.showRequestActionModal = false
                        viewModel.refreshCategory(MediaCategory.RECENT_REQUESTS)
                    }
                },
                onMediaClick = { mediaId, mediaType ->
                    uiState.showRequestActionModal = false
                    navigationManager.navigateToDetails(mediaId, mediaType)
                },
                canDeleteRequest = canDeleteRequest,
                viewModel = viewModel,
                initialMediaDetails = mediaState.preloadedMediaDetails
            )
        }
    }


    // CONSOLIDATED: Timeout management for various UI flags
    LaunchedEffect(uiState.cameFromDownNavigation, uiState.ignoreKeyEvents) {
        when {
            uiState.cameFromDownNavigation -> {
                // Keep the flag active for 1 second to ensure effects complete
                delay(1000)
                uiState.cameFromDownNavigation = false
                
                if (BuildConfig.DEBUG) {
                    Log.d("MainScreen", "🔄 Reset DOWN navigation flag (1 second timeout)")
                }
            }
            uiState.ignoreKeyEvents -> {
                // Safety timeout - never block key events for more than 300 ms
                delay(300)
                if (uiState.ignoreKeyEvents) {
                    uiState.ignoreKeyEvents = false
                    if (BuildConfig.DEBUG) {
                        Log.d("MainScreen", "⚠️ SAFETY: Reset ignoreKeyEvents flag after 300 ms timeout")
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryPlaceholder(category: MediaCategory, context: Context) {
    Column {
        // Cache the category title string since it won't change
        val title = remember(category) { context.getString(category.titleResId) }

        // Explicitly show the category title in the placeholder
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        Box(modifier = Modifier.height(200.dp)) { MediaRowPlaceholder() }
    }
}

@Composable
fun ScrollableCategoriesSection(
    categories: List<MediaCategory>,
    categoryData: Map<MediaCategory, ApiResult<List<Media>>>,
    categoryCardData: Map<MediaCategory, ApiResult<List<CategoryCard>>>,
    selectedCategory: MutableState<MediaCategory>,
    selectedMediaIndex: MutableState<Int>,
    context: Context,
    imageLoader: ImageLoader,
    viewModel: SeerrViewModel,
    isInTopBar: Boolean
) {
    if (BuildConfig.DEBUG) {
        Log.d("ScrollableCategoriesSection", "🎬 ScrollableCategoriesSection called with ${categories.size} categories: ${categories.map { it.name }}")
        Log.d("ScrollableCategoriesSection", "🎬 Selected category: ${selectedCategory.value.name}")
        Log.d("ScrollableCategoriesSection", "🎯 isInTopBar=$isInTopBar")
    }
    // Use a stable list state that doesn't get recreated on every recomposition
    val listState = rememberLazyListState()

    // Keep track of categories that have previously loaded data
    // This helps distinguish between initial loading and pagination loading
    // Use a stable, remembered value to avoid recreating this set on recompositions
    val categoriesWithData = remember { mutableStateOf(setOf<MediaCategory>()) }

    // Update the set of categories that have data - only when categoryData changes
    LaunchedEffect(categoryData) {
        val updatedSet = categoriesWithData.value.toMutableSet()
        categoryData.forEach { (category, result) ->
            if (result is ApiResult.Success && result.data.isNotEmpty()) {
                updatedSet.add(category)
            }
        }
        categoriesWithData.value = updatedSet
    }


    // Create a map of selected media indices for each category
    val selectedMediaIndices = remember {
        mutableMapOf<MediaCategory, MutableState<Int>>().apply {
            categories.forEach { category -> this[category] = mutableIntStateOf(0) }
        }
    }

    // Update the selected index for the current category
    LaunchedEffect(selectedCategory.value, selectedMediaIndex.value) {
        val category = selectedCategory.value
        selectedMediaIndices[category]?.value = selectedMediaIndex.value
    }
    
    // Clear all media selections when entering top bar
    LaunchedEffect(isInTopBar) {
        if (isInTopBar) {
            selectedMediaIndices.values.forEach { it.value = -1 }
        }
    }

    // Use a stable modifier for the Box 
    val boxModifier = remember { Modifier.padding(start = 8.dp) }

    Box(modifier = boxModifier) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = 16.dp
            ), // Added significant bottom padding to ensure last item is fully visible
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Use the derived value directly to avoid recreating the list on recomposition
            itemsIndexed(categories) { index, category ->
                if (BuildConfig.DEBUG && index == 0) {
                    Log.d("ScrollableCategoriesSection", "🎬 LazyColumn rendering ${categories.size} categories")
                }
                val isSelected = category == selectedCategory.value && !isInTopBar
                if (BuildConfig.DEBUG && index < 2) {
                    Log.d(
                        "ScrollableCategoriesSection",
                        "Row ${category.name}: isInTopBar=${isInTopBar}, isSelected=${isSelected}, selectedCategory=${selectedCategory.value.name}"
                    )
                }

                // Determine if this is a CategoryCard category or a Media category
                val isCategoryCardType = category in listOf(
                    MediaCategory.MOVIE_GENRES,
                    MediaCategory.SERIES_GENRES,
                    MediaCategory.STUDIOS,
                    MediaCategory.NETWORKS
                )

                // Get the appropriate API result based on category type
                val apiResult = if (isCategoryCardType) {
                    categoryCardData[category] ?: ApiResult.Loading()
                } else {
                    categoryData[category] ?: ApiResult.Loading()
                }

                // Only show placeholder for initial loading (when category has never had data)
                val shouldShowPlaceholder =
                    apiResult is ApiResult.Loading &&
                            !categoriesWithData.value.contains(category) &&
                            index < 3

                if (shouldShowPlaceholder) {
                    // Show placeholder only for initial loading
                    CategoryPlaceholder(category = category, context = context)
                } else {
                    // For all other cases, including pagination loading, show the CarouselSection
                    if (BuildConfig.DEBUG && index < 2) {
                        Log.d(
                            "ScrollableCategoriesSection",
                            "➡️ Calling CarouselSection for ${category.name} with isSelected=${isSelected}, isInTopBar=${isInTopBar}"
                        )
                    }
                    CarouselSection(
                        category = category,
                        isSelected = isSelected,
                        apiResult = apiResult,
                        selectedMediaIndex = selectedMediaIndices[category] ?: remember { mutableIntStateOf(0) },
                        context = context,
                        viewModel = viewModel,
                        imageLoader = imageLoader,
                        isInTopBar = isInTopBar,
                    )
                }
            }
        }
    }

    // Optimize scrolling by animating only when the selected category changes
    LaunchedEffect(selectedCategory.value) {
        val index = categories.indexOf(selectedCategory.value)
        if (index != -1) {
            // Calculate if we're near the end of the list
            val isNearEnd = index >= categories.size - 3

            // Use different scroll offset based on position in list
            if (isNearEnd) {
                // For items near the end, use a negative offset to push them up in the viewport
                // This ensures the last few categories are fully visible and not cut off
                listState.animateScrollToItem(index = index, scrollOffset = -100)
            } else {
                // For other items, use the regular top alignment
                listState.animateScrollToItem(index = index, scrollOffset = 0)
            }
        }
    }
}

@Composable
fun MediaBackdrop(context: Context, media: Media?, imageLoader: ImageLoader) {
    // Get backdrop path only once and cache it
    val backdropPath = remember(media) { media?.backdropPath }

    // Determine if this is a special category item (genre, studio, network)
    val isSpecialCategory = remember(media) {
        media?.genreName != null || media?.studioName != null || media?.networkName != null
    }

    // Determine if this is a logo (studios/networks) or backdrop image (genres)
    val isLogo = remember(media) {
        media?.studioName != null || media?.networkName != null
    }

    // Use stable modifiers
    val fullSizeModifier = remember { Modifier.fillMaxSize() }

    // Remember the image request to avoid recreating it on each recomposition
    val imageRequest = remember(backdropPath, media) {
        if (!backdropPath.isNullOrEmpty()) {
            ImageRequest.Builder(context)
                // Use smaller backdrop size for better performance
                .data("https://image.tmdb.org/t/p/w1280$backdropPath") // Instead of 'original'
                .memoryCacheKey("backdrop_$backdropPath")
                .diskCacheKey("backdrop_$backdropPath")
                .crossfade(true)
                .build()
        } else if (isSpecialCategory && media?.logoPath != null) {
            // If it's a special category with a logo path, use that instead
            ImageRequest.Builder(context)
                .data(media.logoPath)
                .memoryCacheKey("backdrop_$media.logoPath")
                .diskCacheKey("backdrop_$media.logoPath")
                .crossfade(true)
                .build()
        } else null
    }

    Box(modifier = fullSizeModifier) {
        if (imageRequest != null) {
            // Only try to load image if we have a valid request
            AsyncImage(
                model = imageRequest,
                imageLoader = imageLoader,
                contentDescription = stringResource(R.string.mainScreen_mediaBackdrop),
                modifier = fullSizeModifier,
                contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop,
                alignment = if (isLogo) Alignment.Center else Alignment.TopCenter
            )
        } else {
            // Plain background if no image available - use a stable modifier
            Box(
                modifier = remember {
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121827))
                }
            )
        }

        // Gradient overlay - always present
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF121827).copy(alpha = 0.5f),
                            Color(0xFF121827).copy(alpha = 0.7f),
                            Color(0xFF121827).copy(alpha = 0.9f),
                            Color(0xFF121827)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
    }
}

@Composable
fun MediaDetailsArea(media: Media?, forceUpdate: Int = 0) {
    // Cache media ID for key comparison to minimize recompositions
    val mediaId = media?.id

    // Determine if this is a special category item (genre, studio, network)
    val isSpecialCategory = remember(media) {
        media?.genreName != null || media?.studioName != null || media?.networkName != null
    }

    // Get the category name if it's a special category
    val categoryName = remember(media) {
        when {
            media?.genreName != null -> media.genreName
            media?.studioName != null -> media.studioName
            media?.networkName != null -> media.networkName
            else -> null
        }
    }

    // Using key() to force recomposition only when these values change
    key(mediaId, forceUpdate) {
        if (BuildConfig.DEBUG) {
            Log.d(
                "MediaDetailsArea",
                "🖼️ Composing details area: media=${media?.title ?: media?.name}, forceUpdate=$forceUpdate"
            )
        }

        // Pre-compute the display title once for this composition
        val displayTitle = remember(media) {
            when {
                media == null -> ""
                isSpecialCategory -> categoryName ?: ""
                media.mediaType == "movie" -> media.title ?: ""
                else -> media.name ?: ""
            }
        }

        // Create a stable modifier that won't cause recompositions
        val boxModifier = remember {
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(start = 8.dp, top = 8.dp, bottom = 16.dp, end = 0.dp)
        }

        // Create a stable column modifier that doesn't use align
        val columnModifier = remember {
            Modifier.fillMaxWidth()
        }

        Box(modifier = boxModifier) {
            Column(
                modifier = columnModifier.then(Modifier.align(Alignment.BottomStart))
            ) {
                if (isSpecialCategory) {
                    // And show the category name as the subtitle
                    Text(
                        text = categoryName ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    // For regular media, show title and overview as before
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = media?.overview ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MediaRowPlaceholder(
    showProgressIndicator: Boolean = false
) {
    if (showProgressIndicator) {
        // Cache the gradient and the entire placeholder UI to prevent 
        // recompositions from recreating the same UI elements
        val loadingPlaceholder = remember {
            @Composable {
                val gradientBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1F2937),
                        Color(0xFF111827)
                    )
                )

                // Animation for the loading card
                val infiniteTransition = rememberInfiniteTransition(label = "loading_pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "loading_alpha"
                )

                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(200.dp)
                        .background(
                            brush = gradientBrush,
                            shape = RoundedCornerShape(15.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = alpha * 0.3f),
                            shape = RoundedCornerShape(15.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color.White,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.common_loadingMore),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.mainScreen_loading_pleaseWait),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        loadingPlaceholder()
    } else {
        // Create the placeholder items once since they don't change
        val placeholderItems = remember {
            @Composable {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(200.dp) // Match the height in MediaRow
                ) {
                    items(8) { MediaCardPlaceholder(modifier = Modifier.width(134.dp)) }
                }
            }
        }
        placeholderItems()
    }
}

@Composable
fun MediaCardPlaceholder(modifier: Modifier = Modifier) {
    // Create the gradient brush once
    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1F2937), Color(0xFF111827))
        )
    }

    Box(
        modifier = modifier
            .height(185.dp)
            .background(
                brush = gradientBrush,
                shape = RoundedCornerShape(15.dp)
            )
    )
}

@Composable
fun CategoryCardComponent(
    card: CategoryCard,
    isSelected: Boolean,
    imageLoader: ImageLoader,
    context: Context,
    category: MediaCategory
) {
    val cardHeight = 100.dp
    val cardWidth = 200.dp
    val scale by animateFloatAsState(if (isSelected) 1.15f else 1f, label = "")

    Box(
        modifier =
            Modifier
                .width(cardWidth)
                .height(cardHeight)
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Color(0xFF1A1E24)
                )
                .border(
                    width =
                        if (isSelected) 2.dp
                        else 0.5.dp,
                    color =
                        if (isSelected) Color.White
                        else
                            Color(
                                0xFFACAEB2
                            ),
                    shape = RoundedCornerShape(8.dp)
                )
    ) {
        when (category) {
            MediaCategory.MOVIE_GENRES, MediaCategory.SERIES_GENRES -> {
                if (card.imagePath != null) {
                    AsyncImage(
                        model =
                            ImageRequest.Builder(context)
                                .data(
                                    "https://image.tmdb.org/t/p/w500${card.imagePath}"
                                )
                                .crossfade(true)
                                .build(),
                        contentDescription = card.name,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color(0x99000000),
                                            Color(0xDD000000)
                                        )
                                )
                            )
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = card.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            MediaCategory.STUDIOS, MediaCategory.NETWORKS -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                brush =
                                    Brush.linearGradient(
                                        colors =
                                            listOf(
                                                Color(
                                                    0xFF1F2327
                                                ),
                                                Color(
                                                    0xFF171B21
                                                )
                                            ),
                                        start = Offset(0f, 0f),
                                        end =
                                            Offset(
                                                cardWidth.value *
                                                        1.5f,
                                                0f
                                            )
                                    ),
                                shape = RoundedCornerShape(8.dp)
                            ),
                    contentAlignment = Alignment.Center
                ) {
                    if (card.imagePath != null) {
                        val imageUrl =
                            "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)${card.imagePath}"

                        AsyncImage(
                            model =
                                ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .build(),
                            contentDescription = card.name,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Fit,
                            colorFilter = null,
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.90f)
                                    .fillMaxHeight(0.90f)
                                    .padding(8.dp)
                        )
                    } else {
                        Text(
                            text = card.name,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2A2E3B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = card.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

