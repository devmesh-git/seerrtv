package ca.devmesh.seerrtv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.util.TvAppInfo

/**
 * A horizontal row showing installed Android TV apps. Used only in the launcher build.
 * [selectedIndex] is the focused app. Parent handles Enter key to launch via [onLaunchApp].
 */
@Composable
fun InstalledAppsRow(
    apps: List<TvAppInfo>,
    selectedIndex: Int,
    isRowFocused: Boolean,
    isReorderMode: Boolean = false,
    holdToReorderProgress: Float = 0f,
    onLaunchApp: (TvAppInfo) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val title = if (isReorderMode) stringResource(R.string.mainScreen_appsRowReorderTitle) else stringResource(R.string.mainScreen_appsRow)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(134.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        if (apps.isEmpty()) {
            Text(
                text = "No other TV apps installed",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LaunchedEffect(selectedIndex) {
                if (selectedIndex in apps.indices) {
                    listState.animateScrollToItem(selectedIndex)
                }
            }
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(94.dp)
            ) {
                itemsIndexed(apps) { index, app ->
                    val isSelected = isRowFocused && index == selectedIndex
                    val showHoldProgress = isSelected && holdToReorderProgress > 0f
                    Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                        AppTile(
                            app = app,
                            isSelected = isSelected,
                            holdToReorderProgress = if (showHoldProgress) holdToReorderProgress else 0f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: TvAppInfo,
    isSelected: Boolean,
    holdToReorderProgress: Float = 0f
) {
    val scale by animateFloatAsState(if (isSelected) 1.08f else 1f, label = "appTileScale")
    val bitmap = remember(app.packageName) {
        app.icon?.let { drawable ->
            try {
                drawable.toBitmap(128, 128).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(
        modifier = Modifier
            .scale(scale)
            .size(width = 140.dp, height = 72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) Color.White.copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = if (isSelected) Color.White else Color(0xFFACAEB2),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.label.toString(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Gray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (app.label.firstOrNull()?.uppercaseChar() ?: '?').toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
            Text(
                text = app.label.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
            if (holdToReorderProgress > 0f) {
                LinearProgressIndicator(
                    progress = { holdToReorderProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}
