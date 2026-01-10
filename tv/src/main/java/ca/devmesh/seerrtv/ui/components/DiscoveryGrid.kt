package ca.devmesh.seerrtv.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.SearchResult
import ca.devmesh.seerrtv.ui.MediaItem
import ca.devmesh.seerrtv.ui.FocusedItem
import ca.devmesh.seerrtv.ui.position.GridPositionManager
import coil3.ImageLoader
import kotlinx.coroutines.flow.collect

/**
 * Reusable grid component for displaying search results in a grid layout.
 * Extracted from MediaDiscoveryScreen to be shared across different screens.
 */
@Composable
fun DiscoveryGrid(
    results: List<SearchResult>,
    isLoading: Boolean,
    hasMoreResults: Boolean,
    gridState: LazyGridState,
    numberOfColumns: Int = 6,
    selectedRow: Int,
    selectedColumn: Int,
    focusedItem: FocusedItem,
    onEndReached: () -> Unit,
    screenKey: String,
    imageLoader: ImageLoader,
    context: Context,
    modifier: Modifier = Modifier
) {
    // Track current selection from GridPositionManager
    val currentSelection = remember { mutableStateOf(Pair(selectedRow, selectedColumn)) }
    
    // Update currentSelection whenever selectedRow or selectedColumn changes
    LaunchedEffect(selectedRow, selectedColumn) {
        currentSelection.value = Pair(selectedRow, selectedColumn)
    }
    
    // Handle end reached for infinite scroll - monitor scroll position
    // Use a more reliable approach that checks both visible items and scroll position
    LaunchedEffect(gridState, results.size, hasMoreResults, isLoading) {
        snapshotFlow { 
            val layoutInfo = gridState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val firstVisibleIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1
            val totalItems = layoutInfo.totalItemsCount
            Pair(lastVisibleIndex, totalItems)
        }
            .collect { (lastVisibleIndex, totalItems) ->
                // Only proceed if we have results and more are available
                if (results.isEmpty() || !hasMoreResults || isLoading) {
                    return@collect
                }
                
                // Trigger when we're near the end (within 3 rows) or at the bottom
                val threshold = numberOfColumns * 3 // Prefetch 3 rows ahead
                val itemsRemaining = results.size - lastVisibleIndex - 1
                val isNearEnd = itemsRemaining <= threshold && lastVisibleIndex >= 0
                val isAtBottom = lastVisibleIndex >= results.size - 1 && results.size > 0
                
                if (isNearEnd || isAtBottom) {
                    onEndReached()
                }
            }
    }
    
    Box(
        modifier = modifier.fillMaxHeight()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(numberOfColumns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                start = 0.dp,
                top = 16.dp,
                end = 0.dp,
                bottom = 0.dp
            ),
            modifier = Modifier.fillMaxHeight()
        ) {
            items(results.size) { index ->
                val result = results[index]
                val row = index / numberOfColumns
                val column = index % numberOfColumns
                val isItemSelected = remember(currentSelection.value, focusedItem) {
                    focusedItem == FocusedItem.Grid &&
                            row == currentSelection.value.first &&
                            column == currentSelection.value.second
                }
                MediaItem(
                    result = result,
                    imageLoader = imageLoader,
                    context = context,
                    isSelected = isItemSelected,
                    modifier = Modifier.width(120.dp)
                )
            }

            // Loading indicator when loading more items
            if (isLoading && results.isNotEmpty()) {
                item(span = { GridItemSpan(currentLineSpan = numberOfColumns) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp) // Increased height for better visibility
                            .background(Color(0xFF1A1D29).copy(alpha = 0.8f)) // Add background
                            .padding(16.dp), // Add padding
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Get loading text from resources
                            val loadingMoreText = stringResource(id = R.string.common_loadingMore)
                            
                            Text(
                                text = loadingMoreText,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Initial loading indicator
        if (isLoading && results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(id = R.string.common_loadingMore),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
