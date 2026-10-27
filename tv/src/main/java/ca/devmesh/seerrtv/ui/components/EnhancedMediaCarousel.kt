package ca.devmesh.seerrtv.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.ui.position.ScrollPositionManager
import ca.devmesh.seerrtv.viewmodel.CategoryCard
import ca.devmesh.seerrtv.viewmodel.MediaCategory
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

// Placeholder marker class
class CarouselPlaceholderItem

/**
 * A unified carousel component that can display both MediaCards and CategoryCard items.
 * This component handles autoloading more data when the user reaches the end of the list,
 * maintains the selection during recomposition, and provides consistent loading behavior.
 * The component now uses smooth scrolling animations for a more natural user experience.
 */
@Composable
fun <T> EnhancedMediaCarousel(
    items: List<T>,
    selectedIndex: MutableState<Int>,
    context: Context,
    isCarouselSelected: Boolean,
    category: MediaCategory,
    viewModel: SeerrViewModel,
    isEndOfData: Boolean,
    heightDp: Int = if (items.isNotEmpty() && items[0] is CategoryCard) 100 else 210,
    renderItem: @Composable (item: T, isSelected: Boolean) -> Unit
) {
    // Add coroutineScope for animations and async operations
    val coroutineScope = rememberCoroutineScope()

    // Track last scrolled index to prevent duplicate scrolling
    val lastScrolledIndex = remember { mutableIntStateOf(-1) }
    
    // Track last scroll operation time for debouncing
    val lastScrollTime = remember { mutableLongStateOf(0L) }
    
    // Track if we've already scrolled for the current selection
    val hasScrolledForSelection = remember { mutableStateOf(false) }
    
    // Debounce time in milliseconds
    val debounceTime = 200L

    // The scroll started for the latest selection (cancelled when the next one arrives)
    val selectionScrollJob = remember { arrayOfNulls<Job>(1) }


    // Category key for position storage
    val categoryKey = category.name

    // Get saved scroll position or default to 0
    val savedPosition = remember(categoryKey) {
        ScrollPositionManager.positions[categoryKey] ?: Pair(0, 0)
    }

    // Create a stable list state that survives recompositions
    val scrollState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedPosition.first.coerceAtMost(
            (items.size - 1).coerceAtLeast(0)
        ),
        initialFirstVisibleItemScrollOffset = savedPosition.second
    )
    
    // Store item type for debugging and conditional logic
    val itemType = remember {
        when {
            items.isNotEmpty() && items[0] is Media -> "Media"
            items.isNotEmpty() && items[0] is CategoryCard -> "CategoryCard"
            else -> "Unknown"
        }
    }
    
    // These ViewModel flows hold every row's entry (or the latest event for any row). Each is
    // collected as a State but only read inside derivedStateOf, which picks this row's slice,
    // so a change to another row no longer recomposes this one.
    val categoryStatesState = viewModel.categoryStates.collectAsStateWithLifecycle()
    val categoryState by remember(category) { derivedStateOf { categoryStatesState.value[category] } }

    // Get placeholder visibility from the ViewModel
    val showPlaceholdersState = viewModel.showPlaceholders.collectAsStateWithLifecycle()
    val shouldShowPlaceholder by remember(category) {
        derivedStateOf { showPlaceholdersState.value[category] == true }
    }

    // Track old data size for proper placeholder positioning
    val oldDataSize = remember(category.name) { mutableIntStateOf(0) }
    
    // Key for this carousel's scroll position in ScrollPositionManager
    val scrollKey = "${category.name}_scroll"
    
    // Track if a reset was recently performed to avoid overriding with saved position
    val wasResetPerformed = remember { mutableStateOf(false) }
    
    // Listen for carousel reset events from ViewModel (only this row's)
    val resetEventsState = viewModel.carouselResetEvents.collectAsStateWithLifecycle()
    val resetEvent by remember(category) {
        derivedStateOf { resetEventsState.value?.takeIf { it.first == category } }
    }
    
    // Remember the scroll position and derive state from it
    val scrollPosition = remember(scrollState) {
        derivedStateOf { scrollState.firstVisibleItemIndex }
    }

    // Use the derived state in LaunchedEffect
    LaunchedEffect(scrollPosition.value) {
        val currentPosition = scrollPosition.value
        // Only save scroll position if we're actually scrolling (not during initial composition)
        // And not during a reset operation
        if (currentPosition > 0 && !wasResetPerformed.value) {
            ScrollPositionManager.saveScrollPosition(scrollKey, currentPosition)
            
            if (BuildConfig.DEBUG) {
                Log.d(
                    "EnhancedMediaCarousel", 
                    "📜 Saved scroll position for ${category.name}: $currentPosition"
                )
            }
        }
    }
    
    /**
     * Helper function to perform a scroll operation safely with better error handling.
     * Cancellable: a newer selection cancels the scroll it supersedes (see selectionScrollJob).
     * It used to run in NonCancellable and swallow cancellation, so an older scroll carried on
     * after a newer one had started and the two fought (rubber-banding when a key was held).
     * 
     * @param targetPosition The position to scroll to
     * @param logTag A tag for logging to indicate what triggered the scroll
     * @param animated Whether to use smooth animation for scrolling
     */
    suspend fun safeScrollToItem(
        targetPosition: Int,
        logTag: String = "SafeScroll",
        animated: Boolean = true
    ) {
        try {
            // Skip if we're already at the target position. The offset matters too: a scroll
            // cancelled by a newer press can stop part-way into a card, leaving the row on the
            // right index but shifted (e.g. the first card half off screen).
            if (scrollState.firstVisibleItemIndex == targetPosition &&
                scrollState.firstVisibleItemScrollOffset == 0
            ) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaCarousel",
                        "$logTag ${category.name}: Already at target position $targetPosition"
                    )
                }
                return
            }

            // Log scroll operation with more context
            if (BuildConfig.DEBUG) {
                Log.d(
                    "MediaCarousel",
                    "$logTag ${category.name}: Scrolling from ${scrollState.firstVisibleItemIndex} to $targetPosition ${if(animated) "with animation" else "instantly"}"
                )
            }
            
            withContext(Dispatchers.Main.immediate) {
                if (animated) {
                    scrollState.animateScrollToItem(targetPosition)
                } else {
                    scrollState.scrollToItem(targetPosition)
                }
            }

            // If we're not at the target and not using animation, try once more
            if (!animated && scrollState.firstVisibleItemIndex != targetPosition) {
                delay(50.milliseconds)
                withContext(Dispatchers.Main.immediate) {
                    scrollState.scrollToItem(targetPosition)
                }
            }
            
            // Only log if we didn't reach the target position
            if (BuildConfig.DEBUG && scrollState.firstVisibleItemIndex != targetPosition) {
                Log.w(
                    "MediaCarousel",
                    "$logTag ${category.name}: Scroll incomplete - reached ${scrollState.firstVisibleItemIndex} instead of $targetPosition"
                )
            }
        } catch (e: CancellationException) {
            // Superseded by a newer scroll: stop here instead of carrying on
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(
                    "MediaCarousel",
                    "$logTag ${category.name}: Scroll operation failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Performs scrolling if needed and not recently scrolled to the same position.
     * This is the SINGLE source of truth for all scrolling operations.
     */
    suspend fun performScrollIfNeeded(
        targetIndex: Int,
        logTag: String = "Scroll"
    ) {
        // Ensure index is within bounds
        val validIndex = targetIndex.coerceIn(0, items.size - 1)
        if (validIndex != targetIndex) {
            if (BuildConfig.DEBUG) {
                Log.w(
                    "MediaCarousel",
                    "⚠️ ${category.name}: Index $targetIndex out of bounds, adjusted to $validIndex"
                )
            }
        }
        
        // Don't scroll if we recently scrolled to this exact index
        if (validIndex == lastScrolledIndex.intValue) {
            return
        }
        
        // Check if we need to debounce
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScrollTime.longValue < debounceTime) {
            return
        }
                
        // Use a stable total items count
        val totalItemsCount = items.size
        
        // Basic navigation information
        val categoryKey = "${category.name}_index"
        ScrollPositionManager.isNavigatingRight(categoryKey)
        
        // Calculate the target position using our simplified function
        val targetPosition = calculateOptimalScrollPosition(
            index = validIndex,
            isCategoryCard = itemType == "CategoryCard",
            totalItems = totalItemsCount,
            scrollState = scrollState,
            category = category
        )
        
        // Update tracking variables
        lastScrolledIndex.intValue = validIndex
        lastScrollTime.longValue = System.currentTimeMillis()
        
        // Perform the actual scrolling operation
        safeScrollToItem(
            targetPosition = targetPosition,
            logTag = logTag
        )
        
        // Add a short delay and then verify the item is actually visible
        delay(30.milliseconds)
        
        // Calculate current position information
        val maxVisibleItems = if (itemType == "CategoryCard") 4 else 6
        val positionOnScreen = validIndex - scrollState.firstVisibleItemIndex + 1
        val isOffScreen = positionOnScreen !in 1..maxVisibleItems
        
        // Emergency handling for off-screen items only
        if (isOffScreen) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    "MediaCarousel",
                    "⚠️ ${category.name}: EMERGENCY - Item off screen (pos=$positionOnScreen), fixing position"
                )
            }
            
            // For media cards, center at position 3
            // For category cards, center at position 3
            val centerPosition = if (itemType == "CategoryCard") 2 else 2
            val emergencyPosition = (validIndex - centerPosition).coerceAtLeast(0)
            val maxScrollPosition = (totalItemsCount - maxVisibleItems).coerceAtLeast(0)
            val safeEmergencyPosition = emergencyPosition.coerceAtMost(maxScrollPosition)
            
            // Force immediate (non-animated) scroll to ensure visibility
            safeScrollToItem(
                targetPosition = safeEmergencyPosition,
                logTag = "EmergencySafety",
                animated = false // Use instant scroll for emergency correction
            )
        }
    }

    // Store the scroll position when the row leaves composition
    DisposableEffect(category.name) {
        onDispose {
            // Store scroll position on disposal
            if (scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 0) {
                ScrollPositionManager.positions[categoryKey] = Pair(
                    scrollState.firstVisibleItemIndex,
                    scrollState.firstVisibleItemScrollOffset
                )

                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaCarousel",
                        "💾 Saved scroll position for $categoryKey: ${scrollState.firstVisibleItemIndex}, ${scrollState.firstVisibleItemScrollOffset}"
                    )
                }
            }
        }
    }

    // React to carousel updates from the ViewModel (only this row's)
    val carouselUpdatesState = viewModel.carouselUpdates.collectAsStateWithLifecycle()
    val carouselUpdate by remember(category) {
        derivedStateOf { carouselUpdatesState.value?.takeIf { it.category == category } }
    }

    // COMBINED EFFECT: Unified carousel update and selection handler
    LaunchedEffect(
        carouselUpdate,
        category,
        isCarouselSelected,
        items.size,
        selectedIndex.value,
        isEndOfData,
        shouldShowPlaceholder
    ) {
        // Reset scroll tracking when selection changes
        if (isCarouselSelected) {
            hasScrolledForSelection.value = false
        }

        // PART 1: Handle carousel updates
        val update = carouselUpdate
        if (update != null && update.category == category) {
            viewModel.clearCarouselUpdate()

            if (update.itemsAfter > update.itemsBefore && isCarouselSelected) {
                val currentSelectedIndex = selectedIndex.value
                val newItemsAdded = update.itemsAfter - update.itemsBefore
                val previousSize = update.itemsBefore
                val wasAtEndOfList = currentSelectedIndex >= previousSize - 3

                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaCarousel",
                        "📈 ${category.name}: Processing update - $newItemsAdded new items, selection at $currentSelectedIndex" +
                                if (wasAtEndOfList) " (WAS AT END)" else ""
                    )
                }
                
                val categoryKey = "${category.name}_index"
                ScrollPositionManager.saveUserIndex(categoryKey, currentSelectedIndex)
                
                if (items.size > previousSize) {
                    oldDataSize.intValue = previousSize
                    
                    coroutineScope.launch {
                        try {
                            // For media cards, position the card in position 4 after new data is loaded
                            if (itemType != "CategoryCard") {
                                val centerPosition = 3  // Position 4 for media cards
                                val targetPosition = (currentSelectedIndex - centerPosition).coerceAtLeast(0)
                                val maxVisibleItems = 6
                                val maxScrollPosition = (items.size - maxVisibleItems).coerceAtLeast(0)
                                val finalPosition = targetPosition.coerceAtMost(maxScrollPosition)
                                
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "MediaCarousel",
                                        "🔄 ${category.name}: After update - positioning card at position 4 (scroll position $finalPosition)"
                                    )
                                }
                                
                                safeScrollToItem(
                                    targetPosition = finalPosition,
                                    logTag = "CarouselUpdate"
                                )
                            } else {
                                performScrollIfNeeded(
                                    targetIndex = currentSelectedIndex,
                                    logTag = "CarouselUpdate"
                                )
                            }
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) {
                                Log.e("MediaCarousel", "⚠️ ${category.name}: Update scroll failed: ${e.message}")
                            }
                        }
                    }
                }
            }
        }

        // PART 2: Handle selection and navigation
        if (isCarouselSelected && items.isNotEmpty() && !hasScrolledForSelection.value) {
            val categoryKey = "${category.name}_index"
            val savedIndex = ScrollPositionManager.getUserIndex(categoryKey)
            if (savedIndex >= 0) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaCarousel",
                        "🚀 ${category.name}: Restoring index $savedIndex after navigation"
                    )
                }

                // First, update state variables
                selectedIndex.value = savedIndex
                lastScrolledIndex.intValue = -1

                // Then handle scroll position. Only the latest selection's scroll runs: the one
                // for the previous press is cancelled, including its delayed safety check, which
                // otherwise jumped the row back while the newer animation was still running.
                selectionScrollJob[0]?.cancel()
                selectionScrollJob[0] = coroutineScope.launch {
                    try {
                        // Get current scroll position
                        val currentScrollPosition = scrollState.firstVisibleItemIndex
                            
                        // Previously, only right navigation would maintain scroll position,
                        // but we should maintain scroll position for BOTH left and right
                        // Skip the directional check entirely and always maintain position
                            
                        val categoryKey = "${category.name}_index"
                        val directionText = if (ScrollPositionManager.isNavigatingRight(categoryKey)) "RIGHT" else "LEFT"
                            
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "MediaCarousel",
                                "🚀 ${category.name}: Maintaining scroll position $currentScrollPosition during $directionText navigation"
                            )
                        }
                                
                        // Calculate target position based on current scroll
                        val targetPosition = calculateOptimalScrollPosition(
                            index = savedIndex,
                            isCategoryCard = itemType == "CategoryCard",
                            totalItems = items.size,
                            scrollState = scrollState,
                            category = category
                        )
                                
                        // Only scroll if we need to adjust position (or settle a part-way offset)
                        if (targetPosition != currentScrollPosition ||
                            scrollState.firstVisibleItemScrollOffset != 0
                        ) {
                            // Don't call performScrollIfNeeded because it will recalculate the position
                            // Instead, scroll directly to the calculated position
                            safeScrollToItem(
                                targetPosition = targetPosition,
                                logTag = "PostNavigation"
                            )
                        }
                                
                        // SAFETY CHECK - Verify the selected item is visible after navigation
                        // This helps with rapid navigation where animations might not keep up
                        delay(50.milliseconds) // Brief delay to let any running animations progress
                            
                        // Calculate position information
                        val positionOnScreen = savedIndex - scrollState.firstVisibleItemIndex + 1
                        val maxVisibleItems = if (itemType == "CategoryCard") 4 else 6
                        val isOffScreen = positionOnScreen !in 1..maxVisibleItems
                            
                        // Check key conditions based on simplified rules
                        val isFirstCardInPosition1 = scrollState.firstVisibleItemIndex == 0
                        val isLastCardInPosition6 = savedIndex == items.size - 1 && 
                                                  (positionOnScreen == 6 || (items.size <= 6 && positionOnScreen == items.size))

                        // Emergency handling for off-screen items
                        if (isOffScreen) {
                            if (BuildConfig.DEBUG) {
                                Log.d(
                                    "MediaCarousel",
                                    "⚠️ ${category.name}: SAFETY CHECK - Item off screen (pos=$positionOnScreen), fixing position"
                                )
                            }
                                
                            // Determine proper position based on our simplified rules
                            val emergencyTargetPosition: Int
                                
                            if (itemType != "CategoryCard" && isFirstCardInPosition1) {
                                // RULE 1: If first card is in position 1, keep at start
                                emergencyTargetPosition = 0
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "MediaCarousel",
                                        "📌 ${category.name}: Applying RULE 1 - First card in position 1, keeping at start"
                                    )
                                }
                            } else if (isLastCardInPosition6) {
                                // RULE 2: If last card is in position 6, keep at end
                                val maxScrollPosition = (items.size - maxVisibleItems).coerceAtLeast(0)
                                emergencyTargetPosition = maxScrollPosition
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "MediaCarousel",
                                        "📌 ${category.name}: Applying RULE 2 - Last card in position 6, setting to end"
                                    )
                                }
                            } else {
                                // RULE 3: Default - center card at position 3
                                val centerPosition = if (itemType == "CategoryCard") 2 else 2  // Position 3
                                val idealPosition = (savedIndex - centerPosition).coerceAtLeast(0)
                                val maxScrollPosition = (items.size - maxVisibleItems).coerceAtLeast(0)
                                emergencyTargetPosition = idealPosition.coerceAtMost(maxScrollPosition)
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "MediaCarousel",
                                        "📌 ${category.name}: Applying RULE 3 - Centering card at position 3 (scroll=$emergencyTargetPosition)"
                                    )
                                }
                            }
                                
                            // Force immediate (non-animated) scroll to ensure visibility
                            safeScrollToItem(
                                targetPosition = emergencyTargetPosition,
                                logTag = "EmergencySafety",
                                animated = false // Use instant scroll for emergency correction
                            )
                        }

                        // Mark that we've handled the scroll for this selection
                        hasScrolledForSelection.value = true
                            
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) {
                            Log.e("MediaCarousel", "⚠️ ${category.name}: Navigation scroll failed: ${e.message}")
                        }
                    }
                }
            }

            // PART 3: Handle auto-loading
            if (!isEndOfData) {
                val isLoadingMore = categoryState?.isLoading == true
                val isInitialLoad = viewModel.isInitialLoad.value
                val categoryHasMorePages = categoryState?.hasMorePages != false
                val isAtLastCard = selectedIndex.value == items.size - 1

                if (isAtLastCard && !isLoadingMore && !isInitialLoad && 
                    !shouldShowPlaceholder && categoryHasMorePages) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "MediaCarousel", 
                            "🔄 ${category.name}: Auto-loading more $itemType (index=${selectedIndex.value})"
                        )
                    }

                    delay(50.milliseconds)
                    val currentIndex = selectedIndex.value
                    viewModel.loadMoreForCategory(context, category)
                    
                    coroutineScope.launch {
                        delay(500.milliseconds)
                        val newScrollPosition = if (itemType == "CategoryCard") {
                            (currentIndex - 2).coerceAtLeast(0)
                        } else {
                            (currentIndex - 3).coerceAtLeast(0)
                        }
                        
                        safeScrollToItem(
                            targetPosition = newScrollPosition,
                            logTag = "PostLoadAdjust",
                            animated = true // Use smooth animation for post-load adjustment
                        )
                    }
                }
            }
        }
    }

    // Remove the separate selection change LaunchedEffect since we've consolidated it above
    // COMBINED EFFECT: Unified placeholder and selection handler
    LaunchedEffect(
        shouldShowPlaceholder,
        isCarouselSelected,
        selectedIndex.value,
        items.size
    ) {
        if (isCarouselSelected) {
            if (shouldShowPlaceholder) {
                oldDataSize.intValue = items.size
                
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaCarousel",
                        "📊 ${category.name}: Loading started, captured old data size: ${oldDataSize.intValue}"
                    )
                }
                
                delay(100.milliseconds)
                
                val placeholderPosition = if (itemType == "CategoryCard") {
                    (selectedIndex.value + 1).coerceAtMost(items.size)
                } else if (oldDataSize.intValue > 0 && items.size > oldDataSize.intValue) {
                    oldDataSize.intValue
                } else {
                    items.size
                }
                
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaCarousel",
                        "🔄 ${category.name}: $itemType loading with placeholder at position $placeholderPosition"
                    )
                }
                
                val isAtEndOfList = selectedIndex.value >= oldDataSize.intValue - 3
                val shouldForceScroll = itemType == "CategoryCard" || isAtEndOfList
                
                if (shouldForceScroll) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "MediaCarousel",
                            "🔍 ${category.name}: ${if (isAtEndOfList) "End-of-list" else "Category Card"} loading - forcing scroll to ensure placeholder visibility"
                        )
                    }
                    
                    lastScrolledIndex.intValue = -1
                    
                    if (itemType == "CategoryCard") {
                        delay(150.milliseconds)
                    }
                    
                    coroutineScope.launch {
                        performScrollIfNeeded(
                            targetIndex = selectedIndex.value,
                            logTag = "PlaceholderAppeared"
                        )
                    }
                }
            } else if (items.isNotEmpty()) {
                delay(200.milliseconds)
                val currentIndex = selectedIndex.value
                
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaCarousel",
                        "⏱️ ${category.name}: Placeholder disappeared, positioning $itemType at index $currentIndex"
                    )
                }
                
                val wasAtEndOfList = currentIndex >= oldDataSize.intValue - 2
                val isCategoryCarousel = itemType == "CategoryCard"
                
                // After loading new data, we want to position the highlighted card in position 4
                // Calculate the target position based on the centerPosition (3 for media items)
                val centerPosition = if (isCategoryCarousel) 2 else 3  // Position 3 for categories, 4 for media
                val targetPosition = (currentIndex - centerPosition).coerceAtLeast(0)
                val maxVisibleItems = if (isCategoryCarousel) 4 else 6
                val maxScrollPosition = (items.size - maxVisibleItems).coerceAtLeast(0)
                val finalPosition = targetPosition.coerceAtMost(maxScrollPosition)
                
                if (isCategoryCarousel) {
                    lastScrolledIndex.intValue = -1
                    
                    if (wasAtEndOfList && items.isNotEmpty()) {
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "MediaCarousel",
                                "🎯 ${category.name}: DIRECT SCROLL after placeholder removed - target position $finalPosition"
                            )
                        }
                        
                        coroutineScope.launch {
                            withContext(kotlinx.coroutines.NonCancellable) {
                                try {
                                    withContext(Dispatchers.Main.immediate) {
                                        scrollState.animateScrollToItem(finalPosition)
                                    }
                                    
                                    delay(100.milliseconds)
                                    
                                    if (scrollState.firstVisibleItemIndex != finalPosition) {
                                        if (BuildConfig.DEBUG) {
                                            Log.d(
                                                "MediaCarousel",
                                                "⚡ ${category.name}: Regular scroll failed, trying OFFSET SCROLLING. Current: ${scrollState.firstVisibleItemIndex}, Target: $finalPosition"
                                            )
                                        }
                                        
                                        val layoutInfo = scrollState.layoutInfo
                                        val visibleItems = layoutInfo.visibleItemsInfo
                                        
                                        if (visibleItems.isNotEmpty()) {
                                            val itemWidth = visibleItems.first().size
                                            val itemSpacing = 16
                                            val currentFirstVisible = scrollState.firstVisibleItemIndex
                                            val itemsToMoveFinal = finalPosition - currentFirstVisible
                                            val pixelsToMove = itemsToMoveFinal * (itemWidth + itemSpacing)
                                            
                                            if (BuildConfig.DEBUG) {
                                                Log.d(
                                                    "MediaCarousel",
                                                    "⚡ ${category.name}: OFFSET SCROLLING - currentFirst: $currentFirstVisible, " +
                                                    "itemsToMove: $itemsToMoveFinal, pixelsToMove: $pixelsToMove, " +
                                                    "currentOffset: ${scrollState.firstVisibleItemScrollOffset}, targetOffset: ${scrollState.firstVisibleItemScrollOffset + pixelsToMove}"
                                                )
                                            }
                                            
                                            withContext(Dispatchers.Main.immediate) {
                                                val newOffset = scrollState.firstVisibleItemScrollOffset + pixelsToMove

                                                coroutineScope.launch {
                                                    // Use animated scrolling for smoother offset transition
                                                    scrollState.animateScrollToItem(
                                                        index = currentFirstVisible,
                                                        scrollOffset = newOffset.coerceAtLeast(0)
                                                    )
                                                }.join()
                                            }
                                            
                                            delay(100.milliseconds)
                                            
                                            if (BuildConfig.DEBUG) {
                                                Log.d(
                                                    "MediaCarousel",
                                                    "⚡ ${category.name}: After offset scrolling - " +
                                                    "position: ${scrollState.firstVisibleItemIndex}, " +
                                                    "offset: ${scrollState.firstVisibleItemScrollOffset}"
                                                )
                                            }
                                            
                                            if (scrollState.firstVisibleItemIndex != finalPosition) {
                                                delay(50.milliseconds)
                                                
                                                withContext(Dispatchers.Main.immediate) {
                                                    coroutineScope.launch {
                                                        // Use animated scrolling for final adjustment
                                                        scrollState.animateScrollToItem(
                                                            index = finalPosition,
                                                            scrollOffset = 0
                                                        )
                                                    }.join()
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (BuildConfig.DEBUG) {
                                        val success = scrollState.firstVisibleItemIndex == finalPosition
                                        Log.d(
                                            "MediaCarousel", 
                                            "🎯 ${category.name}: Final scroll status: ${if (success) "SUCCEEDED" else "FAILED"} - at position ${scrollState.firstVisibleItemIndex}" 
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("MediaCarousel", "⚠️ ${category.name}: Extreme scroll failed: ${e.message}")
                                }
                            }
                        }
                        
                        return@LaunchedEffect
                    }
                }
                
                if (wasAtEndOfList && items.isNotEmpty()) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "MediaCarousel",
                            "🔄 ${category.name}: Transitioning from loading state - repositioning to center view"
                        )
                    }
                    
                    coroutineScope.launch {
                        performScrollIfNeeded(
                            targetIndex = currentIndex,
                            logTag = "PlaceholderRemoved"
                        )
                    }
                } else {
                    val layoutInfo = scrollState.layoutInfo
                    val visibleItemsIndices = layoutInfo.visibleItemsInfo.map { it.index }
                    val isSelectedVisible = currentIndex in visibleItemsIndices
                    val isProperlyPositioned = isCarouselItemProperlyPositioned(
                        layoutInfo,
                        currentIndex,
                        isCategoryCard = isCategoryCarousel
                    )
                    
                    val needsRepositioning = !isSelectedVisible || !isProperlyPositioned
                    
                    if (needsRepositioning || wasAtEndOfList) {
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "MediaCarousel",
                                "🔄 ${category.name}: Repositioning needed after placeholder removed (visible=$isSelectedVisible, positioned=$isProperlyPositioned, wasAtEndOfList=$wasAtEndOfList)"
                            )
                        }
                        
                        coroutineScope.launch {
                            // For media cards, ensure we're using the finalPosition calculated above
                            // to position cards in position 4 after loading
                            if (wasAtEndOfList) {
                                safeScrollToItem(
                                    targetPosition = finalPosition,
                                    logTag = "PlaceholderRemoved"
                                )
                            } else {
                                performScrollIfNeeded(
                                    targetIndex = currentIndex,
                                    logTag = "PlaceholderRemoved"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Effect to handle carousel reset events
    LaunchedEffect(resetEvent) {
        // Safe way to handle the delegated property - assign to local variable first
        val currentResetEvent = resetEvent
        if (currentResetEvent != null && currentResetEvent.first == category) {
            // Extract the category and animation flag from the pair
            currentResetEvent.first
            val shouldAnimate = currentResetEvent.second
            
            // Reset was requested for this specific carousel
            if (BuildConfig.DEBUG) {
                Log.d("EnhancedMediaCarousel", "🔄 Handling reset event for ${category.name}, animate=$shouldAnimate")
            }
            
            // Mark that a reset was performed
            wasResetPerformed.value = true
            
            // Scroll to the beginning of the list and update the selection
            if (scrollState.firstVisibleItemIndex > 0) {
                if (shouldAnimate) {
                    // Use smooth animation for better user experience
                    coroutineScope.launch {
                        // Animate scrolling to position 0 using the standard animation
                        // Note: We can't directly customize animation parameters in this version of Compose,
                        // but we can slow it down by using multiple small animations
                        
                        // First, calculate 1/2 way point for smoother, slower animation feel
                        val halfway = scrollState.firstVisibleItemIndex / 2
                        if (halfway > 0) {
                            scrollState.animateScrollToItem(halfway)
                            delay(150.milliseconds) // Small delay between animations makes it appear slower
                        }
                        
                        // Then complete the animation to position 0
                        scrollState.animateScrollToItem(0)
                        
                        if (BuildConfig.DEBUG) {
                            Log.d("EnhancedMediaCarousel", "🎬 ENHANCED reset animation for ${category.name} with two-step animation")
                        }
                    }
                } else {
                    // Jump immediately without animation
                    coroutineScope.launch {
                        safeScrollToItem(0, "Reset", animated = false)
                        
                        if (BuildConfig.DEBUG) {
                            Log.d("EnhancedMediaCarousel", "⚡ INSTANT reset for ${category.name}")
                        }
                    }
                }
            }
            
            // Reset the ScrollPositionManager scroll position
            ScrollPositionManager.saveScrollPosition(scrollKey, 0)

            // After a short delay, allow position restoration again
            coroutineScope.launch {
                delay(1000.milliseconds)
                wasResetPerformed.value = false
            }
        }
    }

    // Include the placeholder item if we're showing the loading placeholder
    @Suppress("UNCHECKED_CAST")
    val rowItems = if (shouldShowPlaceholder && items.isNotEmpty()) {
        val mutableList = items.toMutableList()
        
        // Calculate placeholder position based on context
        val placeholderPosition = when {
            // For category cards, place at old data boundary
            itemType == "CategoryCard" && oldDataSize.intValue > 0 -> {
                val pos = oldDataSize.intValue.coerceAtMost(mutableList.size)
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaCarousel",
                        "📋 ${category.name}: Category card loading - placeholder at $pos"
                    )
                }
                pos
            }
            // For media cards with old data, place at boundary
            oldDataSize.intValue > 0 && oldDataSize.intValue < items.size -> {
                val pos = oldDataSize.intValue.coerceAtMost(mutableList.size)
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaCarousel",
                        "📋 ${category.name}: Media card loading - placeholder at $pos"
                    )
                }
                pos
            }
            // At the end of the list
            else -> {
                val pos = items.size
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaCarousel",
                        "📋 ${category.name}: End loading - placeholder at $pos"
                    )
                }
                pos
            }
        }
        
        // Ensure valid position
        val safePosition = placeholderPosition.coerceIn(0, mutableList.size)
        if (safePosition != placeholderPosition) {
            if (BuildConfig.DEBUG) {
                Log.w(
                    "MediaCarousel",
                    "⚠️ ${category.name}: Adjusted placeholder from $placeholderPosition to $safePosition"
                )
            }
        }
        
        // Add placeholder and log confirmation
        mutableList.add(safePosition, CarouselPlaceholderItem() as T)
        if (BuildConfig.DEBUG) {
            Log.d(
                "MediaCarousel", 
                "✅ ${category.name}: Added placeholder at $safePosition (size=${mutableList.size})"
            )
        }
        
        mutableList
    } else {
        items
    }

    // Use consistent spacing for cards
    val itemSpacing = if (itemType == "CategoryCard") 20.dp else 12.dp

    // Snapshot row content (not just size + first item) so later items can update when the
    // first row is unchanged — e.g. watchlist refresh or TMDB id normalization.
    // Rebuilt only when the row's list changes, not on every recomposition (it joins every
    // item into one string, and rows grow as they paginate).
    val rowItemsContentKey = remember(rowItems) {
        rowItems.mapIndexed { index, item ->
            when (item) {
                is Media -> "${item.id}:${item.tmdbId}:${item.mediaType}@$index"
                is CategoryCard -> "${item.id}:${item.name}@$index"
                is CarouselPlaceholderItem -> "ph@$index"
                else -> "${item.hashCode()}@$index"
            }
        }.joinToString("|")
    }
    val stableRowItems = remember(rowItems.size, rowItemsContentKey) {
        if (rowItems.isEmpty()) emptyList() else rowItems.toList()
    }
    // One key per item that stays the same while the item stays in the row. Keys used to
    // include the selection flag, so every D-pad press disposed and rebuilt the card losing
    // focus and the card gaining it (new image request, focus-scale animation never ran).
    // A movie and a series can share a TMDB id, hence the type; a repeat of the same item
    // gets a suffix, because duplicate keys crash a lazy list.
    val itemKeys = remember(stableRowItems) {
        uniqueLazyKeys(stableRowItems) { item ->
            when (item) {
                is Media -> "m_${item.mediaType}_${item.id}"
                is CategoryCard -> "c_${item.type}_${item.id}"
                is CarouselPlaceholderItem -> "ph"
                else -> "i_${item.hashCode()}"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
    ) {
        // Calculate an effective selected index that disables selection when carousel isn't selected
        val effectiveSelectedIndex = if (!isCarouselSelected || selectedIndex.value < 0) -1 else selectedIndex.value

        if (BuildConfig.DEBUG) {
            Log.d(
                "MediaCarousel",
                "Render ${category.name}: isCarouselSelected=${isCarouselSelected}, selectedIndex=${selectedIndex.value}, effectiveSelectedIndex=${effectiveSelectedIndex}"
            )
        }

        // Hard guard: if carousel is not selected, force internal selection to -1
        LaunchedEffect(isCarouselSelected) {
            if (!isCarouselSelected && selectedIndex.value != -1) {
                selectedIndex.value = -1
                if (BuildConfig.DEBUG) {
                    Log.d("MediaCarousel", "Force-clear selection for ${category.name} due to carousel not selected")
                }
            }
        }

        LazyRow(
            state = scrollState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            // Add key modifier to force recomposition of the entire LazyRow
            modifier = Modifier
                .fillMaxSize()
        ) {
            // CRITICAL FIX: Add safety check to prevent IndexOutOfBoundsException
            // Only render items if rowItems is not empty
            if (stableRowItems.isNotEmpty()) {
                itemsIndexed(
                    items = stableRowItems,
                    key = { index, _ -> itemKeys[index] },
                    contentType = { _, item ->
                        when (item) {
                            is CarouselPlaceholderItem -> 0
                            is CategoryCard -> 1
                            else -> 2
                        }
                    }
                ) { index, item ->
                if (item is CarouselPlaceholderItem) {
                    CarouselLoadingPlaceholder(
                        isShown = true,
                        isCategoryCard = itemType == "CategoryCard"
                    )
                } else {
                    // CRITICAL FIX: Use the selectedIndex directly instead of getting it from ScrollPositionManager
                    // This ensures the UI stays in sync with the selection state
                    val isSelected = index == effectiveSelectedIndex && isCarouselSelected
                    
                    if (BuildConfig.DEBUG && index < 3) { // Only log first few items to avoid spam
                        Log.d(
                            "MediaCarousel",
                            "${category.name}[$index]: isSelected=$isSelected (index==effectiveSelectedIndex=${index == effectiveSelectedIndex} && isCarouselSelected=$isCarouselSelected)"
                        )
                    }
                    
                    // Keep the ScrollPositionManager in step with the selection. In a SideEffect
                    // so it runs once the composition is applied, not while composing.
                    if (isSelected) {
                        SideEffect {
                            val categoryKey = "${category.name}_index"
                            if (ScrollPositionManager.getUserIndex(categoryKey) != index) {
                                ScrollPositionManager.saveUserIndex(categoryKey, index)
                            }
                        }
                    }

                    Box {
                        // Render the specific item component using the provided lambda
                        renderItem(item, isSelected)
                    }
                }
            }
            } // Close the if (stableRowItems.isNotEmpty()) block
            else {
                // Log when we have empty rowItems to help debug the race condition
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MediaCarousel",
                        "⚠️ ${category.name}: Skipping itemsIndexed due to empty stableRowItems (stableSize=${stableRowItems.size}, rowItems.size=${rowItems.size}, items.size=${items.size}, shouldShowPlaceholder=$shouldShowPlaceholder)"
                    )
                }
            }
        }
    }

    // Restore scroll position from ScrollPositionManager on initial composition
    LaunchedEffect(items, category) {
        // Only restore if we have items, this is the selected carousel,
        // and a reset was NOT recently performed
        if (items.isNotEmpty() && isCarouselSelected && !wasResetPerformed.value) {
            val savedPosition = ScrollPositionManager.getScrollPosition(scrollKey)
            
            // Only restore if we have a non-zero saved position
            if (savedPosition > 0) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "EnhancedMediaCarousel", 
                        "🔍 Restoring scroll position for ${category.name}: $savedPosition"
                    )
                }
                
                // Use animateScrollToItem for smoother visual transition
                scrollState.animateScrollToItem(savedPosition.coerceIn(0, items.size - 1))
            }
        }
    }
}

// Loading placeholder composable
@Composable
fun CarouselLoadingPlaceholder(isShown: Boolean, isCategoryCard: Boolean = false) {
    if (!isShown) return

    // Match item dimensions precisely to avoid layout shifts when placeholder appears/disappears
    val width = if (isCategoryCard) 200.dp else 134.dp  // Use exact MediaCard width (134dp)
    val height = if (isCategoryCard) 100.dp else 185.dp  // Use exact MediaCard height
    val cornerRadius = if (isCategoryCard) 8.dp else 15.dp

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF1A1E24))
            .border(
                width = 0.5.dp,
                color = Color(0xFFACAEB2),
                shape = RoundedCornerShape(cornerRadius)
            ),
            contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(30.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Loading more...",
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Helper function to determine if an item is properly positioned in the visible area
fun isCarouselItemProperlyPositioned(
    layoutInfo: LazyListLayoutInfo,
    index: Int,
    isCategoryCard: Boolean = false
): Boolean {
    // Find the item info for the selected index
    val selectedItemInfo = layoutInfo.visibleItemsInfo.find { it.index == index } ?: return false

    // For category cards, use a different positioning approach with 4 visible items
    if (isCategoryCard) {
        // For the first three items (0,1,2), they should be at the far left start
        // This ensures the first card is fully visible and not cut in half
        if (index <= 2) {
            // For these indices, we want to ensure the first items are at the left edge
            // Check if the firstVisibleItem is index 0 and, it's properly aligned at the left edge
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            return firstVisibleItem?.index == 0 && 
                   (firstVisibleItem.offset >= layoutInfo.viewportStartOffset - 20)
        }
        
        // For category cards, index 3+ should be at position 3 (third visible card)
        // and index (total-1) should be at position 4 (fourth visible card)
        
        // Find visible items
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        
        // For last items, verify they're at the correct end position (3 or 4)
        if (index >= (visibleItemsInfo.lastOrNull()?.index?.minus(1) ?: 0)) {
            // Check if the item is at its expected end position (3 or 4)
            val correctVisiblePosition = if (index == visibleItemsInfo.lastOrNull()?.index) 3 else 2
            
            if (visibleItemsInfo.size > correctVisiblePosition) {
                val expectedItem = visibleItemsInfo.getOrNull(correctVisiblePosition)
                if (expectedItem != null) {
                    val itemCenter = selectedItemInfo.offset + selectedItemInfo.size / 2
                    val targetCenter = expectedItem.offset + expectedItem.size / 2
                    
                    // Allow a small margin of error (15% of card width)
                    return abs(itemCenter - targetCenter) < (selectedItemInfo.size * 0.15f)
                }
            }
        }
        
        // For other items, they should be at position 3 (third visible item)
        if (visibleItemsInfo.size >= 3) {
            val thirdVisibleItem = visibleItemsInfo.getOrNull(2)
            if (thirdVisibleItem != null) {
                // Item should be at approximately the position of the third visible card
                val itemCenter = selectedItemInfo.offset + selectedItemInfo.size / 2
                val targetCenter = thirdVisibleItem.offset + thirdVisibleItem.size / 2
                
                // Allow some margin of error (15% of the card width)
                return abs(itemCenter - targetCenter) < (selectedItemInfo.size * 0.15f)
            }
        }
    }

    // For media cards, maintain the original logic
    // For small indices (0-2), we want them at the start and fully visible
    if (index <= 2) {
        // The item should be fully visible and aligned with the start of the viewport
        // We allow a very small offset to account for padding
        return selectedItemInfo.offset >= layoutInfo.viewportStartOffset &&
                selectedItemInfo.offset <= layoutInfo.viewportStartOffset + 20
    }

    // For other indices, check if it's roughly centered
    val viewportCenter = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
    val itemCenter = selectedItemInfo.offset + selectedItemInfo.size / 2
    
    // Allow a reasonable margin of error for "centered"
    // Category cards are wider, so we need a wider centering threshold
    val centeringThreshold = if (isCategoryCard) {
        // For category cards (30% of viewport width)
        (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) * 0.3f
    } else {
        // For media cards (20% of viewport width)
        (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) * 0.2f
    }
    
    return abs(viewportCenter - itemCenter) < centeringThreshold
}

/**
 * Calculate the optimal position for a carousel item using a simple set of rules:
 *
 * FOR MEDIA CARDS (6 visible items):
 * 1. Items 0-3: Show from the beginning (position 0)
 * 2. Normal items: Center at position 4 (scroll position = index-3)
 * 3. Second-to-last item: Position at 5 (scroll position = totalItems-5)
 * 4. Last item: Position at 6 (scroll position = totalItems-6)
 * 5. During loading: Show selected item at position 5 (scroll position = index-4)
 * 6. After loading: Return to center position 4
 *
 * FOR CATEGORY CARDS (4 visible items):
 * 1. Items 0-2: Show from the beginning (position 0)
 * 2. Normal items: Center at position 3 (scroll position = index-2)
 * 3. Second-to-last item: Position at 3 (scroll position = totalItems-3)
 * 4. Last item: Position at 4 (scroll position = totalItems-4)
 * 5. During loading: Show selected item at position 3 (scroll position = index-2)
 * 6. After loading: MAINTAIN selected item at position 3 (not moving to position 5)
 */
private fun calculateOptimalScrollPosition(
    index: Int,
    isCategoryCard: Boolean,
    totalItems: Int = 0,
    scrollState: LazyListState,
    category: MediaCategory
): Int {
    // Early return for empty lists to avoid division by zero
    if (totalItems <= 0) return 0
    
    // Constants for positioning - precompute these values
    val maxVisibleItems = if (isCategoryCard) 4 else 6               // 4 visible items for categories, 6 for media
    val maxScrollPosition = (totalItems - maxVisibleItems).coerceAtLeast(0)
    val currentScrollPosition = scrollState.firstVisibleItemIndex
    val positionOnScreen = index - currentScrollPosition + 1
    val categoryKey = "${category.name}_index"
    val isNavigatingRight = ScrollPositionManager.isNavigatingRight(categoryKey)
    
    // Log parameters in debug mode
    if (BuildConfig.DEBUG) {
        Log.d("calculatePosition", "PARAMS: idx=$index pos=$positionOnScreen cat=${if(isCategoryCard) "Category" else "Media"} total=$totalItems scroll=$currentScrollPosition")
    }
    
    // CATEGORY CARD CAROUSEL LOGIC
    if (isCategoryCard) {
        // Handle emergency cases first - item completely off-screen
        val isOffScreen = positionOnScreen !in 1..maxVisibleItems
        if (isOffScreen) {
            // Center highlighted card at position 3
            val targetPosition = (index - 2).coerceAtLeast(0).coerceAtMost(maxScrollPosition)
            if (BuildConfig.DEBUG) {
                Log.d("calculatePosition", "📌 [Category] EMERGENCY: Item off screen (pos=$positionOnScreen), centering at position 3")
            }
            return targetPosition
        }
        
        // Check if the first item is visible
        val firstItemVisible = currentScrollPosition == 0
        
        // RULE 1: If first card is in position 1, keep carousel at the start
        if (firstItemVisible) {
            // If we're at the beginning but selected index is getting larger (moving right),
            // we need to start scrolling once we move beyond position 2
            if (isNavigatingRight && index > 1) {
                // Calculate position to center at position 3
                val targetPosition = (index - 2).coerceAtLeast(0)
                if (BuildConfig.DEBUG) {
                    Log.d("calculatePosition", "📌 [Category] RULE 1B: First card visible but selected index > 1, centering at position 3 (scroll=$targetPosition)")
                }
                return targetPosition
            }
            
            // Otherwise, keep at the beginning
            if (BuildConfig.DEBUG) {
                Log.d("calculatePosition", "📌 [Category] RULE 1A: First card is visible, keeping at start (position 0)")
            }
            return 0 // Keep carousel at start
        }
        
        // RULE 2: Handle the end of the list specially to enable load more logic
        if (index >= totalItems - 2) {
            // We're at one of the last 2 items - ensure we can see "load more" placeholder
            val targetPosition = (totalItems - maxVisibleItems).coerceAtLeast(0)
            if (BuildConfig.DEBUG) {
                Log.d("calculatePosition", "📌 [Category] RULE 2: Near end of list, positioning to show load more (scroll=$targetPosition)")
            }
            return targetPosition
        }
        
        // RULE 3: For all other cases, center the selected item at position 3
        else {
            // Both left and right navigation maintain centering for a consistent experience
            val targetPosition = (index - 2).coerceAtLeast(0).coerceAtMost(maxScrollPosition)
            
            if (BuildConfig.DEBUG) {
                val direction = if (isNavigatingRight) "RIGHT" else "LEFT"
                Log.d("calculatePosition", "📌 [Category] RULE 3: Centering card at position 3 during $direction nav (scroll=$targetPosition)")
            }
            return targetPosition
        }
    }
    
    // SIMPLIFIED MEDIA CARD CAROUSEL LOGIC
    else {
        // Handle emergency cases first - item completely off-screen
        val isOffScreen = positionOnScreen !in 1..maxVisibleItems
        if (isOffScreen) {
            // Center highlighted card at position 4
            val targetPosition = (index - 3).coerceAtLeast(0).coerceAtMost(maxScrollPosition)
            if (BuildConfig.DEBUG) {
                Log.d("calculatePosition", "📌 [Media] EMERGENCY: Item off screen (pos=$positionOnScreen), centering at position 4")
            }
            return targetPosition
        }
        
        // Check if the first item is visible
        val firstItemVisible = currentScrollPosition == 0
        
        // RULE 1: If first card is in position 1, keep carousel at the start
        if (firstItemVisible) {
            // If we're at the beginning but selected index is getting larger (moving right),
            // we need to start scrolling once we move beyond position 3
            if (isNavigatingRight && index > 2) {
                // Calculate position to center at position 4
                val targetPosition = (index - 3).coerceAtLeast(0)
                if (BuildConfig.DEBUG) {
                    Log.d("calculatePosition", "📌 [Media] RULE 1B: First card visible but selected index > 2, centering at position 4 (scroll=$targetPosition)")
                }
                return targetPosition
            }
            
            // Otherwise, keep at the beginning
            if (BuildConfig.DEBUG) {
                Log.d("calculatePosition", "📌 [Media] RULE 1A: First card is visible, keeping at start (position 0)")
            }
            return 0 // Keep carousel at start
        }
        
        // RULE 2: Handle the end of the list specially to enable load more logic
        if (index >= totalItems - 2) {
            // We're at one of the last 2 items - ensure we can see "load more" placeholder
            val targetPosition = (totalItems - maxVisibleItems).coerceAtLeast(0)
            if (BuildConfig.DEBUG) {
                Log.d("calculatePosition", "📌 [Media] RULE 2: Near end of list, positioning to show load more (scroll=$targetPosition)")
            }
            return targetPosition
        }
        
        // RULE 3: For all other cases, center the selected item at position 4
        else {
            // Both left and right navigation maintain centering for a consistent experience
            val targetPosition = (index - 3).coerceAtLeast(0).coerceAtMost(maxScrollPosition)
            
            if (BuildConfig.DEBUG) {
                val direction = if (isNavigatingRight) "RIGHT" else "LEFT"
                Log.d("calculatePosition", "📌 [Media] RULE 3: Centering card at position 4 during $direction nav (scroll=$targetPosition)")
            }
            return targetPosition
        }
    }
}

