package ca.devmesh.seerrtv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import ca.devmesh.seerrtv.BuildConfig
import android.util.Log
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.BrowseModels
import ca.devmesh.seerrtv.model.MediaType
import ca.devmesh.seerrtv.util.SafeKeyEventHandler
import ca.devmesh.seerrtv.ui.KeyUtils

/**
 * Sort menu for media browsing.
 * Opens as a left slide-out overlay with D-pad navigation matching RequestModal style.
 */
@Composable
fun SortMenu(
    selectedSort: BrowseModels.SortOption,
    mediaType: MediaType,
    onSortSelected: (BrowseModels.SortOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember {
        mutableIntStateOf(0) // Start at first item
    }

    // Focus management
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val visibleItemsCount = 7

    // Get localized strings for sort options
    val sortPopularity = stringResource(R.string.sort_popularity)
    val sortReleaseDate = stringResource(R.string.sort_releaseDate)
    val sortFirstAirDate = stringResource(R.string.sort_firstAirDate)
    val sortTmdbRating = stringResource(R.string.sort_tmdbRating)
    val sortTitleAz = stringResource(R.string.sort_title_az)


    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Auto-scroll to selected item
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            if (selectedIndex >= visibleItemsCount - 1) {
                listState.animateScrollToItem(selectedIndex - visibleItemsCount + 2)
            } else if (selectedIndex == 0 && listState.firstVisibleItemIndex > 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
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
                    .onKeyEvent { keyEvent ->
                        return@onKeyEvent SafeKeyEventHandler.handleKeyEventWithContext(
                            keyEvent = keyEvent,
                            context = "SortMenu"
                        ) { keyEvent ->
                            when {
                                keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                                    if (selectedIndex > 0) {
                                        selectedIndex--
                                        if (BuildConfig.DEBUG) {
                                            Log.d(
                                                "SortMenu",
                                                "⬆️ Up pressed, selectedIndex: $selectedIndex"
                                            )
                                        }
                                    }
                                    true
                                }

                                keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                                    val maxIndex =
                                        4 // 4 sort options + 1 close button = 5 total items (0-4)
                                    if (selectedIndex < maxIndex) {
                                        selectedIndex++
                                        if (BuildConfig.DEBUG) {
                                            Log.d(
                                                "SortMenu",
                                                "⬇️ Down pressed, selectedIndex: $selectedIndex"
                                            )
                                        }
                                    }
                                    true
                                }

                                (KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) || keyEvent.key == Key.DirectionRight) && keyEvent.type == KeyEventType.KeyDown -> {
                                    if (selectedIndex == 4) {
                                        // Close button selected
                                        if (BuildConfig.DEBUG) {
                                            Log.d(
                                                "SortMenu",
                                                "✅ Enter pressed on Close button - dismissing menu"
                                            )
                                        }
                                        onDismiss()
                                    } else {
                                        // Sort option selected - toggle direction
                                        val baseSortOptions = when (mediaType) {
                                            MediaType.MOVIE -> listOf(
                                                sortPopularity to listOf(
                                                    BrowseModels.SortOption.PopularityDesc,
                                                    BrowseModels.SortOption.PopularityAsc
                                                ),
                                                sortReleaseDate to listOf(
                                                    BrowseModels.SortOption.ReleaseDateDesc,
                                                    BrowseModels.SortOption.ReleaseDateAsc
                                                ),
                                                sortTmdbRating to listOf(
                                                    BrowseModels.SortOption.TMDBRatingDesc,
                                                    BrowseModels.SortOption.TMDBRatingAsc
                                                ),
                                                sortTitleAz to listOf(
                                                    BrowseModels.SortOption.TitleAsc,
                                                    BrowseModels.SortOption.TitleDesc
                                                )
                                            )

                                            MediaType.TV -> listOf(
                                                sortPopularity to listOf(
                                                    BrowseModels.SortOption.PopularityDesc,
                                                    BrowseModels.SortOption.PopularityAsc
                                                ),
                                                sortFirstAirDate to listOf(
                                                    BrowseModels.SortOption.FirstAirDateDesc,
                                                    BrowseModels.SortOption.FirstAirDateAsc
                                                ),
                                                sortTmdbRating to listOf(
                                                    BrowseModels.SortOption.TMDBRatingDesc,
                                                    BrowseModels.SortOption.TMDBRatingAsc
                                                ),
                                                sortTitleAz to listOf(
                                                    BrowseModels.SortOption.TitleAsc,
                                                    BrowseModels.SortOption.TitleDesc
                                                )
                                            )
                                        }

                                        if (selectedIndex >= 0 && selectedIndex < baseSortOptions.size) {
                                            val (baseName, options) = baseSortOptions[selectedIndex]

                                            if (BuildConfig.DEBUG) {
                                                Log.d(
                                                    "SortMenu",
                                                    "🔍 Debug: baseName=$baseName, selectedSort=${selectedSort.displayName}, options=${options.map { it.displayName }}"
                                                )
                                            }

                                            // Find the current sort option in the list
                                            val currentIndex =
                                                options.indexOfFirst { it == selectedSort }
                                            if (BuildConfig.DEBUG) {
                                                Log.d(
                                                    "SortMenu",
                                                    "🔍 Debug: currentIndex=$currentIndex"
                                                )
                                            }

                                            if (currentIndex >= 0) {
                                                // Toggle to the other option in the list
                                                val newSort = options[1 - currentIndex]

                                                if (BuildConfig.DEBUG) {
                                                    Log.d(
                                                        "SortMenu",
                                                        "✅ Enter pressed, toggling: ${newSort.displayName}"
                                                    )
                                                }
                                                onSortSelected(newSort)
                                            } else {
                                                // Current sort is not in this category, select the first option (descending)
                                                val newSort = options.first()

                                                if (BuildConfig.DEBUG) {
                                                    Log.d(
                                                        "SortMenu",
                                                        "🔄 Cross-category navigation: selecting ${newSort.displayName} from $baseName"
                                                    )
                                                }
                                                onSortSelected(newSort)
                                            }
                                        }
                                    }
                                    true
                                }

                                else -> false
                            }
                        }
                    }
            ) {
                // Header
                Text(
                    text = stringResource(R.string.sort_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Sort options list with toggle behavior
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f)
                ) {
                    // Get sort options based on media type
                    val availableSortOptions = BrowseModels.SortOption.forMediaType(mediaType)

                    // Group sort options by base type (without direction)
                    val baseSortOptions = when (mediaType) {
                        MediaType.MOVIE -> listOf(
                            sortPopularity to listOf(
                                BrowseModels.SortOption.PopularityDesc,
                                BrowseModels.SortOption.PopularityAsc
                            ),
                            sortReleaseDate to listOf(
                                BrowseModels.SortOption.ReleaseDateDesc,
                                BrowseModels.SortOption.ReleaseDateAsc
                            ),
                            sortTmdbRating to listOf(
                                BrowseModels.SortOption.TMDBRatingDesc,
                                BrowseModels.SortOption.TMDBRatingAsc
                            ),
                            sortTitleAz to listOf(
                                BrowseModels.SortOption.TitleAsc,
                                BrowseModels.SortOption.TitleDesc
                            )
                        )

                        MediaType.TV -> listOf(
                            sortPopularity to listOf(
                                BrowseModels.SortOption.PopularityDesc,
                                BrowseModels.SortOption.PopularityAsc
                            ),
                            sortFirstAirDate to listOf(
                                BrowseModels.SortOption.FirstAirDateDesc,
                                BrowseModels.SortOption.FirstAirDateAsc
                            ),
                            sortTmdbRating to listOf(
                                BrowseModels.SortOption.TMDBRatingDesc,
                                BrowseModels.SortOption.TMDBRatingAsc
                            ),
                            sortTitleAz to listOf(
                                BrowseModels.SortOption.TitleAsc,
                                BrowseModels.SortOption.TitleDesc
                            )
                        )
                    }

                    items(baseSortOptions.size) { index ->
                        val (baseName, options) = baseSortOptions[index]
                        val currentOption =
                            options.find { option: BrowseModels.SortOption -> option == selectedSort }
                                ?: options.first()
                        val isSelected = options.contains(selectedSort)
                        val isFocused = index == selectedIndex

                        // Create the menu item
                        SortMenuItem(
                            sortOption = currentOption,
                            isSelected = isSelected,
                            isFocused = isFocused,
                            baseName = baseName
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close button at bottom - same style as FiltersDrawer back button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    ActionButton(
                        text = stringResource(R.string.filter_close),
                        isFocused = selectedIndex == 4, // 4 is the close button index (after 4 sort options)
                        backgroundColor = Color.Gray,
                        fillMaxWidth = false
                    )
                }
            }
        }
    }
}

/**
 * Individual sort menu item with selection state matching RequestModal style
 * Shows current direction and indicates toggleable behavior
 */
@Composable
private fun SortMenuItem(
    sortOption: BrowseModels.SortOption,
    isSelected: Boolean,
    isFocused: Boolean,
    baseName: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = when {
                    isFocused -> Color(0xFF2C3E50)
                    isSelected -> Color(0xFF1E293B)
                    else -> Color.Transparent
                }
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sort option text with direction indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = baseName,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )

            // Direction indicator (arrow) - determine from displayName
            val isDescending =
                sortOption.displayName.contains("↓") || sortOption.displayName.contains("Z→A")
            Text(
                text = if (isDescending) "↓" else "↑",
                color = if (isSelected) Color.White else Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Selection indicator
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Sort menu trigger button that shows the current selection
 */
@Composable
fun SortMenuButton(
    selectedSort: BrowseModels.SortOption,
    onMenuOpen: () -> Unit,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onMenuOpen,
        modifier = modifier
            .focusable()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    // Handle focus state
                }
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) Color(0xFF4A4A4A) else Color(0xFF2A2A2A)
        )
    ) {
        Text(
            text = "Sort: ${selectedSort.displayName}",
            color = Color.White,
            fontSize = 12.sp
        )
    }
}
