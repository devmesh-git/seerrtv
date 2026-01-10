package ca.devmesh.seerrtv.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.ui.components.EnhancedMediaCarousel
import ca.devmesh.seerrtv.ui.components.MediaCard
import ca.devmesh.seerrtv.viewmodel.MediaCategory
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel
import coil3.ImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CarouselSection(
    category: MediaCategory,
    isSelected: Boolean,
    apiResult: ApiResult<*>,  // Accepts any ApiResult type
    selectedMediaIndex: MutableState<Int>,
    context: Context,
    viewModel: SeerrViewModel,
    imageLoader: ImageLoader,
    isInTopBar: Boolean = false,
) {
    // Debug logging to help track what's happening with categories
    LaunchedEffect(category, apiResult) {
        if (BuildConfig.DEBUG) {
            Log.d(
                "CarouselSection",
                "Rendering category: ${category.name}, state: ${apiResult.javaClass.simpleName}"
            )
        }
    }

    // Determine if this is a CategoryCard category
    val isCategoryCardType = category in listOf(
        MediaCategory.MOVIE_GENRES,
        MediaCategory.SERIES_GENRES,
        MediaCategory.STUDIOS,
        MediaCategory.NETWORKS
    )
    Column {
        // Show the category title
        Text(
            text = context.getString(category.titleResId),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(
                start = 0.dp,
                top = 0.dp,
                bottom = if (isSelected) 8.dp else 4.dp
            )
        )

        if (isCategoryCardType) {
            // Category Card handling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                // IMPORTANT FIX: Use viewModel.getCategoryCardList directly instead of apiResult.data
                // This ensures we always show all loaded cards, not just the current page
                val cardList = viewModel.getCategoryCardList(category)

                // Extract pagination info
                val paginationInfo = (apiResult as? ApiResult.Success)?.paginationInfo
                val isEndOfData = paginationInfo?.hasMorePages == false

                if (apiResult is ApiResult.Loading && cardList.isEmpty()) {
                    // Only show placeholder when we have no data and are loading initially
                    MediaRowPlaceholder(showProgressIndicator = true)
                } else if (apiResult is ApiResult.Error && cardList.isEmpty()) {
                    // Show error state only if we have no data
                    ErrorMediaRow()
                } else if (cardList.isEmpty()) {
                    // Show empty state
                    EmptyMediaRow()
                } else {
                    // Use the unified carousel for consistent behavior
                    // When in top bar, force selected index to -1 to visually clear selection
                    val effectiveSelectedIndex = if (isInTopBar) {
                        remember(category) { mutableIntStateOf(-1) }
                    } else {
                        selectedMediaIndex
                    }

                    EnhancedMediaCarousel(
                        items = cardList,
                        selectedIndex = effectiveSelectedIndex,
                        context = context,
                        isCarouselSelected = isSelected && !isInTopBar,
                        category = category,
                        viewModel = viewModel,
                        isEndOfData = isEndOfData,
                        renderItem = { item, isItemSelected ->
                            // For CategoryCard type, we know the item type matches
                            CategoryCardComponent(
                                card = item,
                                isSelected = isItemSelected && !isInTopBar,
                                imageLoader = imageLoader,
                                context = context,
                                category = category
                            )
                        }
                    )
                }
            }
        } else {
            // Media handling - now using MediaCarousel instead of MediaRow
            Box(modifier = Modifier.height(210.dp)) { // Maintain consistent height for media
                // Maintain a stable media list to prevent recreation when loading more data
                val stableMediaList = remember { mutableStateOf(emptyList<Media>()) }
                // For keeping track of mutable operations, we'll use a separate mutable list
                val mediaList = remember { mutableStateListOf<Media>() }
                // Track if we've reached the end of data
                val isEndOfData = remember { mutableStateOf(false) }
                // Add state for data transition to temporarily disable navigation
                val isDataTransitioning = remember { mutableStateOf(false) }
                
                // Coroutine scope for managing async operations
                val coroutineScope = rememberCoroutineScope()

                // Update the media list without recreating the carousel
                LaunchedEffect(apiResult) {
                    when (apiResult) {
                        is ApiResult.Success<*> -> {
                            // Cast to the correct type, ensuring we have a List<Media>
                            @Suppress("UNCHECKED_CAST")
                            val mediaApiResult = apiResult as? ApiResult.Success<List<Media>>
                            if (mediaApiResult == null) {
                                if (BuildConfig.DEBUG) {
                                    Log.e(
                                        "CarouselSection",
                                        "Error: Received unexpected data type for category ${category.name}"
                                    )
                                }
                                return@LaunchedEffect
                            }

                            // Set transitioning flag to true while processing new data
                            isDataTransitioning.value = true

                            // Update end of data flag based on pagination info
                            isEndOfData.value = mediaApiResult.paginationInfo?.hasMorePages == false

                            val data = mediaApiResult.data
                            if (BuildConfig.DEBUG) {
                                Log.d(
                                    "CarouselSection",
                                    "📋 Category: ${category.name}, updating media list: ${mediaList.size} -> ${data.size}"
                                )
                            }

                            // Check if we've reached the end of data - safely access nullable PaginationInfo
                            val noMorePages = mediaApiResult.paginationInfo?.hasMorePages == false

                            // Update end of data flag - need to track if we've reached end
                            isEndOfData.value = noMorePages

                            // Update media list with new data
                            if (data.isEmpty() && mediaList.isEmpty()) {
                                // Just log that we got empty results - no retries needed as we trust
                                // the API pagination info
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "CarouselSection",
                                        "⚠️ Category: ${category.name}, received empty results, no data to display"
                                    )
                                }
                            } else if (data.isNotEmpty()) {
                                // IMPORTANT: Always clear the list and replace with the latest data to ensure
                                // we don't keep deleted items when refreshing
                                mediaList.clear()
                                mediaList.addAll(data)
                                
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "CarouselSection",
                                        "♻️ Category: ${category.name}, completely replaced data with ${data.size} items"
                                    )
                                }

                                // Update our stable reference
                                stableMediaList.value = mediaList.toList()

                                // CRITICAL: Pass the updated size to the component
                                viewModel.updateCategorySize(category, mediaList.size)

                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "CarouselSection",
                                        "🔄 Category: ${category.name}, Updated list size: ${mediaList.size}"
                                    )
                                }
                            }

                            // Clear transitioning flag after a short delay
                            coroutineScope.launch {
                                delay(300)
                                isDataTransitioning.value = false
                            }
                        }

                        is ApiResult.Error -> {
                            // Clear transitioning state in case of error too
                            isDataTransitioning.value = false

                            // Show error with Snackbar or other UI element
                            if (BuildConfig.DEBUG) {
                                Log.e(
                                    "CarouselSection",
                                    "Error loading category ${category.name}: ${apiResult.exception.message}"
                                )
                            }
                        }

                        is ApiResult.Loading -> {
                            // Loading state is handled by the MediaCarousel
                            if (BuildConfig.DEBUG) {
                                Log.d("CarouselSection", "Loading category: ${category.name}")
                            }

                            // Initial state, trigger load if list is empty - moved from null case
                            if (mediaList.isEmpty()) {
                                // Use direct load method instead
                                viewModel.loadMoreForCategory(context, category)
                            }
                        }
                    }
                }

                if (apiResult is ApiResult.Loading && stableMediaList.value.isEmpty()) {
                    // Only show placeholder when we have no data and are loading initially
                    MediaRowPlaceholder(
                        showProgressIndicator = true
                    )
                } else if (apiResult is ApiResult.Error && stableMediaList.value.isEmpty()) {
                    // Show error state only if we have no data
                    ErrorMediaRow()
                } else if (stableMediaList.value.isEmpty()) {
                    // Show empty state
                    EmptyMediaRow()
                } else {
                    // Use the unified MediaCarousel that works for both types
                    key(category) { // Key on category only, not on data
                        // When in top bar, force selected index to -1 to visually clear selection
                        val effectiveSelectedIndex = if (isInTopBar) {
                            remember(category) { mutableIntStateOf(-1) }
                        } else {
                            selectedMediaIndex
                        }

                        if (BuildConfig.DEBUG && isInTopBar) {
                            Log.d(
                                "CarouselSection",
                                "TopBar active → ${category.name}: isSelected=$isSelected, effectiveSelectedIndex=${effectiveSelectedIndex.value}"
                            )
                        }

                        EnhancedMediaCarousel(
                            items = stableMediaList.value,
                            selectedIndex = effectiveSelectedIndex,
                            context = context,
                            isCarouselSelected = isSelected && !isInTopBar,
                            category = category,
                            viewModel = viewModel,
                            isEndOfData = isEndOfData.value,
                            renderItem = { item, isItemSelected ->
                                MediaCard(
                                    mediaContent = item,
                                    context = context,
                                    imageLoader = imageLoader,
                                    isSelected = isItemSelected && !isInTopBar,
                                    modifier = Modifier
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyMediaRow() {
    // Use remember for static content
    val content = remember {
        @Composable {
            val errorText = stringResource(id = R.string.carouselSection_error_noMediaFound)
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorText, 
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
    content()
}

@Composable
fun ErrorMediaRow() {
    // Use remember for static content
    val content = remember {
        @Composable {
            val errorText = stringResource(id = R.string.carouselSection_error_loadingFailed)
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    content()
} 