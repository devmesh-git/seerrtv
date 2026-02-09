package ca.devmesh.seerrtv.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.devmesh.seerrtv.R

/**
 * Top bar mode enum for different navigation states
 */
enum class TopBarMode {
    MAIN,
    SEARCH,
    MOVIES,
    SERIES
}

/**
 * Persistent top bar component that displays Search, Movies, Series, and Settings icons with clock.
 * This component is designed to be mounted at the NavHost level and visible across multiple screens.
 */
@Composable
fun MainTopBar(
    modifier: Modifier = Modifier,
    context: Context,
    activeMode: TopBarMode = TopBarMode.MAIN,
    onModeChange: (TopBarMode) -> Unit = {},
    isSearchFocused: Boolean = false,
    isMoviesFocused: Boolean = false,
    isSeriesFocused: Boolean = false,
    isSettingsFocused: Boolean = false,
    showRefreshHint: Boolean = false,
    isInTopBar: Boolean = false,
    isRefreshRowVisible: Boolean = false,
    showSearchIcon: Boolean = true,
    showMoviesIcon: Boolean = true,
    showSeriesIcon: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            // Remove black background to allow content to show through
    ) {
        // Refresh hint - only shown when in top bar and not refreshing
        AnimatedVisibility(
            visible = showRefreshHint && isInTopBar && !isRefreshRowVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) { 
            RefreshHint() 
        }

        // Icons row with clock
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Settings icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (isSettingsFocused)
                            Color.Gray.copy(alpha = 0.5f)
                        else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(id = R.string.mainScreen_settings),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            
            // Series icon (hidden when already on Series browse to avoid nesting)
            if (showSeriesIcon) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isSeriesFocused)
                                Color.Gray.copy(alpha = 0.5f)
                            else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Series",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // Movies icon (hidden when already on Movies browse to avoid nesting)
            if (showMoviesIcon) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isMoviesFocused)
                                Color.Gray.copy(alpha = 0.5f)
                            else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Movies",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // Search icon (optional)
            if (showSearchIcon) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isSearchFocused)
                                Color.Gray.copy(alpha = 0.5f)
                            else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.common_search),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            
            // Clock component
            Clock(context)
        }
    }
}

/**
 * Refresh hint component that shows "Press Up to Refresh" message.
 * This is a separate composable to maintain the same behavior as the original.
 */
@Composable
private fun RefreshHint() {
    // This composable doesn't change, so optimize by avoiding recreating content
    val content = remember {
        @Composable {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.mainScreen_pressUpToRefresh),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    content()
}
