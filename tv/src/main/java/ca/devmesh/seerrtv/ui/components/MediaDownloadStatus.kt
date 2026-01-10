package ca.devmesh.seerrtv.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.model.DownloadStatus
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel

private const val TAG = "MediaDownloadStatus"

/**
 * A composable that displays download status(es) for media content.
 * Fetches and refreshes media details automatically.
 *
 * @param tmdbId The TMDB ID of the media content
 * @param mediaType The type of media (movie, tv, etc)
 * @param initialMediaDetails Optional initial MediaDetails to use before fetching from API
 * @param showAllDownloads Whether to show all download items or just the first one
 * @param filterBy4K Optional filter: null shows all, true shows only 4K, false shows only non-4K
 */
@Composable
fun MediaDownloadStatus(
    tmdbId: String,
    mediaType: String,
    initialMediaDetails: MediaDetails? = null,
    showAllDownloads: Boolean = false,
    filterBy4K: Boolean? = null,
    mediaDetailsState: ApiResult<MediaDetails>,
    mainViewModel: SeerrViewModel? = null
) {
    // Create a stable key from tmdbId and mediaType to ensure component stability
    val stableKey = remember(tmdbId, mediaType) { "$tmdbId:$mediaType" }
    
    // Use key to ensure stable identity across recompositions
    key(stableKey) {
        // Use the shared view model when provided; fallback to Hilt injection
        val viewModel: SeerrViewModel = mainViewModel ?: hiltViewModel()
        
        // Use the mediaDetailsState provided by the parent to avoid duplicate orchestration
        val providedState = mediaDetailsState
        
        // Keep track of previous download statuses with type information to prevent flickering
        val lastValidDownloadStatuses = remember { mutableStateOf<List<DownloadStatusWithType>>(emptyList()) }
        
        // Handle lifecycle events for proper cleanup
        val lifecycleOwner = LocalLifecycleOwner.current
        
        // Start polling only once when component is first composed
        LaunchedEffect(stableKey) {
            Log.d(TAG, "🎬 Starting polling for media $tmdbId ($mediaType)")
            viewModel.startPollingMediaDetails(tmdbId, mediaType, initialMediaDetails)
        }
        
        // Handle lifecycle events - only stop polling on destroy, not on pause/resume
        DisposableEffect(lifecycleOwner, stableKey) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_DESTROY -> {
                        Log.d(TAG, "💀 Lifecycle destroyed, stopping polling for $tmdbId")
                        viewModel.stopPollingMediaDetails()
                    }
                    else -> { /* ignore other events to avoid frequent stop/start */ }
                }
            }
            
            lifecycleOwner.lifecycle.addObserver(observer)
            
            onDispose {
                Log.d(TAG, "🧹 Cleaning up resources for $tmdbId")
                lifecycleOwner.lifecycle.removeObserver(observer)
                viewModel.stopPollingMediaDetails()
            }
        }
        
        // Extract download status(es) from media details
        val downloadStatuses = when (providedState) {
            is ApiResult.Success<MediaDetails> -> {
                val details = providedState.data
                val regularStatuses = details.mediaInfo?.downloadStatus ?: emptyList()
                val fourKStatuses = details.mediaInfo?.downloadStatus4k ?: emptyList()

                // Debug logging for incoming arrays
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "📥 Incoming download arrays: regular=${regularStatuses.size}, fourK=${fourKStatuses.size}")
                    Log.d(TAG, "REGULAR IDs: ${regularStatuses.joinToString { it.externalId.toString() }}")
                    Log.d(TAG, "REGULAR Titles: ${regularStatuses.joinToString { it.title }}")
                    Log.d(TAG, "FOUR_K IDs: ${fourKStatuses.joinToString { it.externalId.toString() }}")
                    Log.d(TAG, "FOUR_K Titles: ${fourKStatuses.joinToString { it.title }}")
                }
                
                // Prepare typed lists
                val regularTyped = regularStatuses.map { DownloadStatusWithType(it, DownloadType.REGULAR) }
                val fourKTyped = fourKStatuses.map { DownloadStatusWithType(it, DownloadType.FOUR_K) }

                // If a filter is specified, return that tier directly without cross-tier de-duplication
                val filtered = when (filterBy4K) {
                    // Tier-specific views: use only the server-provided list for that tier
                    true -> fourKTyped
                    false -> regularTyped
                    null -> {
                        // Show both arrays as-is (regular followed by 4K) without deduplication
                        (regularTyped + fourKTyped)
                    }
                }

                if (filtered.isNotEmpty()) {
                    lastValidDownloadStatuses.value = filtered
                }
                filtered
            }
            else -> lastValidDownloadStatuses.value
        }
        
        // Always render the Column, but only show content when we have download statuses
        Column {
            // Use AnimatedVisibility to smooth transitions when the status list changes
            AnimatedVisibility(
                visible = downloadStatuses.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    if (showAllDownloads) {
                        // Show all download statuses
                        downloadStatuses.forEachIndexed { index, statusWithType ->
                            // Use key to ensure stable identity for each status item
                            key(statusWithType.downloadStatus.externalId) {
                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(1.dp))
                                }
                                RenderDownloadStatus(statusWithType)
                            }
                        }
                    } else {
                        // Only show the first download status
                        downloadStatuses.firstOrNull()?.let { statusWithType ->
                            key(statusWithType.downloadStatus.externalId) {
                                RenderDownloadStatus(statusWithType)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderDownloadStatus(downloadStatusWithType: DownloadStatusWithType) {
    // Only log in debug builds to reduce log spam
    if (BuildConfig.DEBUG) {
        Log.d(TAG, "🔄 Rendering download status: ${downloadStatusWithType.downloadStatus.status} (${downloadStatusWithType.type})")
    }
    
    // Use the existing DownloadStatusItem component with type information
    DownloadStatusItem(
        downloadStatus = downloadStatusWithType.downloadStatus,
        is4K = downloadStatusWithType.type == DownloadType.FOUR_K
    )
}

// Data class to hold download status with type information
private data class DownloadStatusWithType(
    val downloadStatus: DownloadStatus,
    val type: DownloadType
)

// Enum to distinguish between download types
private enum class DownloadType {
    REGULAR,
    FOUR_K
} 