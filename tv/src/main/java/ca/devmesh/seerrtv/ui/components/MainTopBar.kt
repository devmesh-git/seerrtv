package ca.devmesh.seerrtv.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.AvatarColor
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import ca.devmesh.seerrtv.model.UserProfile

/**
 * Top bar mode enum for different navigation states
 */
enum class TopBarMode {
    MAIN,
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
    isDownloadsFocused: Boolean = false,
    isSettingsFocused: Boolean = false,
    isAvatarFocused: Boolean = false,
    showRefreshHint: Boolean = false,
    isInTopBar: Boolean = false,
    isRefreshRowVisible: Boolean = false,
    showSearchIcon: Boolean = true,
    showMoviesIcon: Boolean = true,
    showSeriesIcon: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            // Remove black background to allow content to show through
    ) {
        // Refresh hint - only shown when in top bar and not refreshing. It is centered in the
        // space left of the icons so longer translations can't run underneath them.
        RefreshHint(
            visible = showRefreshHint && isInTopBar && !isRefreshRowVisible,
            modifier = Modifier.weight(1f)
        )

        // Icons row with clock
        Row(
            modifier = Modifier
                .padding(top = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeProfile: UserProfile? = SharedPreferencesUtil.getActiveProfile(context)
            val avatarInitials = activeProfile?.avatarInitials?.take(2) ?: "??"
            val avatarColor = AvatarColor.fromKey(activeProfile?.avatarColor).toColor()
            val remoteAvatarUrl = activeProfile?.remoteAvatarUrl?.takeIf { it.isNotBlank() }
                ?: SharedPreferencesUtil.getRemoteAvatarUrl(context)

            TopBarIcon(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.mainScreen_settings),
                isFocused = isSettingsFocused
            )
            Spacer(modifier = Modifier.width(8.dp))

            TopBarIcon(
                icon = AppIcons.Download,
                label = stringResource(R.string.downloads_title),
                isFocused = isDownloadsFocused
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Series icon (hidden when already on Series browse to avoid nesting)
            if (showSeriesIcon) {
                TopBarIcon(
                    icon = AppIcons.Tv,
                    label = stringResource(R.string.browse_series),
                    isFocused = isSeriesFocused
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Movies icon (hidden when already on Movies browse to avoid nesting)
            if (showMoviesIcon) {
                TopBarIcon(
                    icon = AppIcons.Movie,
                    label = stringResource(R.string.browse_movies),
                    isFocused = isMoviesFocused
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Search icon (optional)
            if (showSearchIcon) {
                TopBarIcon(
                    icon = Icons.Default.Search,
                    label = stringResource(R.string.common_search),
                    isFocused = isSearchFocused
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Clock component
            Clock(context)

            Spacer(modifier = Modifier.width(4.dp))

            // Profile avatar (to enter profile selection) - right of the clock
            Box(contentAlignment = Alignment.Center) {
                ProfileAvatar(
                    modifier = Modifier
                        .size(48.dp)
                        .then(
                            if (isAvatarFocused) {
                                Modifier.border(2.dp, Color.White, CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    initials = avatarInitials,
                    backgroundColor = if (isAvatarFocused) avatarColor else avatarColor.copy(alpha = 0.85f),
                    remoteAvatarUrl = remoteAvatarUrl,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
                FocusLabel(
                    text = stringResource(R.string.topBar_profiles),
                    visible = isAvatarFocused,
                    // The avatar sits at the screen edge, so keep a long label from running off it
                    alignment = Alignment.TopEnd
                )
            }
        }
    }
}

/**
 * A top bar icon with a focus circle, and its name shown underneath while focused.
 * TV remotes have no hover, so the label is what tells Movies and Series apart.
 */
@Composable
private fun TopBarIcon(
    icon: ImageVector,
    label: String,
    isFocused: Boolean
) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (isFocused) Color.Gray.copy(alpha = 0.5f) else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        FocusLabel(text = label, visible = isFocused)
    }
}

/**
 * Pill label drawn just below a 48dp top bar item. It is laid out outside the item's bounds
 * so showing it never shifts the other icons in the row.
 */
@Composable
private fun BoxScope.FocusLabel(
    text: String,
    visible: Boolean,
    alignment: Alignment = Alignment.TopCenter
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .matchParentSize()
            .wrapContentSize(alignment, unbounded = true)
            .offset(y = 52.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/**
 * Refresh hint component that shows "Press Up to Refresh" message.
 */
@Composable
private fun RefreshHint(visible: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
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
}
