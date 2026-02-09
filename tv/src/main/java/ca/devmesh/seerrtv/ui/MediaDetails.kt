package ca.devmesh.seerrtv.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.MediaInfo
import ca.devmesh.seerrtv.model.MediaServerType
import ca.devmesh.seerrtv.model.MediaType
import ca.devmesh.seerrtv.navigation.NavigationManager
import ca.devmesh.seerrtv.navigation.rememberNavigationManager
import ca.devmesh.seerrtv.ui.focus.AppFocusManager
import ca.devmesh.seerrtv.ui.focus.AppFocusState
import ca.devmesh.seerrtv.ui.focus.TopBarFocus
import ca.devmesh.seerrtv.ui.focus.DetailsFocusState
import ca.devmesh.seerrtv.ui.focus.createMediaDetailsDpadConfig
import ca.devmesh.seerrtv.ui.focus.rememberAppFocusManager
import ca.devmesh.seerrtv.ui.state.FocusArea
import ca.devmesh.seerrtv.ui.state.MediaDetailsStateManager
import ca.devmesh.seerrtv.ui.state.ReturnState
import ca.devmesh.seerrtv.ui.state.ReturnStateSaver
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.ui.components.AuthenticationErrorHandler
import ca.devmesh.seerrtv.ui.components.MediaCard
import ca.devmesh.seerrtv.ui.components.MediaDetailsActionButtons
import ca.devmesh.seerrtv.ui.components.MediaDetailsContentLayout
import ca.devmesh.seerrtv.ui.components.MediaDetailsCarousels
import ca.devmesh.seerrtv.ui.components.findTrailerUrl
import ca.devmesh.seerrtv.ui.components.getActionButtonStates
import ca.devmesh.seerrtv.ui.components.getTrailerYouTubeVideoId
import ca.devmesh.seerrtv.ui.components.TVMessage
import ca.devmesh.seerrtv.ui.components.processMediaStatus
import ca.devmesh.seerrtv.ui.focus.DpadController
import ca.devmesh.seerrtv.util.CommonUtil
import ca.devmesh.seerrtv.util.Permission
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import ca.devmesh.seerrtv.viewmodel.RequestViewModel
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel
import ca.devmesh.seerrtv.model.Request
import ca.devmesh.seerrtv.model.SimilarMediaItem
import coil3.compose.AsyncImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A stable state holder for the RequestActionModal to prevent recreation during recomposition
 */
data class RequestActionState(
    val requestId: Int,
    val request: Request,
    var isVisible: Boolean = true
)

private fun getPlexPlayUrl(mediaInfo: MediaInfo): String? {
    // Simplified approach - try iOSPlexUrl first (Overseerr style)
    val iOSPlexUrl = mediaInfo.iOSPlexUrl
    if (!iOSPlexUrl.isNullOrBlank()) {
        val uri = iOSPlexUrl.toUri()
        val serverId = uri.getQueryParameter("server")
        val metadataKey = uri.getQueryParameter("metadataKey")
        val metadataId = metadataKey?.split("/")?.lastOrNull()

        if (serverId != null && metadataId != null) {
            return "plex://server://$serverId/com.plexapp.plugins.library/library/metadata/$metadataId"
        }
    }

    // Fallback to mediaUrl parsing (Jellyseerr style)
    val mediaUrl = mediaInfo.mediaUrl
    if (!mediaUrl.isNullOrBlank()) {
        val uri = mediaUrl.toUri()
        val serverId = uri.getQueryParameter("server")
        val metadataKey = uri.getQueryParameter("metadataKey")
        val metadataId = metadataKey?.split("/")?.lastOrNull()

        if (serverId != null && metadataId != null) {
            return "plex://server://$serverId/com.plexapp.plugins.library/library/metadata/$metadataId"
        }
    }

    return null
}

private fun getJellyfinPlayUrl(mediaInfo: MediaInfo): String? {
    // First try externalServiceId (Overseerr provides this)
    val externalServiceId = mediaInfo.externalServiceId
    if (externalServiceId != null) {
        return externalServiceId.toString()
    }

    // Fallback to mediaUrl parsing (Jellyseerr style)
    val mediaUrl = mediaInfo.mediaUrl ?: return null
    val uri = mediaUrl.toUri()
    val fragment = uri.fragment
    if (fragment.isNullOrBlank()) {
        return null
    }
    // Fragment is like: details?id=...&context=...&serverId=...
    val params = fragment.substringAfter('?').split('&').associate {
        val (k, v) = it.split('=', limit = 2).let { arr ->
            arr[0] to arr.getOrElse(1) { "" }
        }
        k to v
    }
    val itemId = params["id"]
    return itemId
}

private fun getEmbyPlayUrl(mediaInfo: MediaInfo): String? {
    // Prefer mediaUrl parsing first (more reliable for both movies and series)
    // For series, externalServiceId might point to a different entity than the playable item
    val mediaUrl = mediaInfo.mediaUrl
    if (!mediaUrl.isNullOrBlank()) {
        val uri = mediaUrl.toUri()
        val fragment = uri.fragment
        if (!fragment.isNullOrBlank()) {
            // Fragment is like: !/item?id=...&context=...&serverId=...
            // or: details?id=...&context=...&serverId=...
            val params = fragment.substringAfter('?').split('&').associate {
                val (k, v) = it.split('=', limit = 2).let { arr ->
                    arr[0] to arr.getOrElse(1) { "" }
                }
                k to v
            }
            val itemId = params["id"]
            if (!itemId.isNullOrBlank()) {
                return itemId
            }
        }
    }

    // Fallback to externalServiceId (Overseerr provides this)
    val externalServiceId = mediaInfo.externalServiceId
    if (externalServiceId != null) {
        return externalServiceId.toString()
    }

    return null
}

@Composable
fun MediaDetails(
    context: Context,
    imageLoader: ImageLoader,
    mediaId: String,
    mediaType: String,
    navController: NavController,
    viewModel: SeerrViewModel,
    dpadController: DpadController,
    appFocusManager: AppFocusManager = rememberAppFocusManager(),
    navigationManager: NavigationManager = rememberNavigationManager(
        scope = rememberCoroutineScope(),
        navController = navController
    ),
    initialShowRequestModal: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val refreshManager = remember { RefreshManager() }
    val focusManager = remember { FocusManager() }
    var showMessage by remember { mutableStateOf<String?>(null) }

    // Determine if we're returning from PersonScreen ONCE at entry to avoid re-creating flows on focus changes
    val isReturningFromPersonScreenAtEntry = remember(mediaId, mediaType) {
        val focus = appFocusManager.currentFocus
        focus is AppFocusState.DetailsScreen && focus.focus != DetailsFocusState.Overview
    }

    // Clear no-ratings tracking when opening media details to try fetching ratings again
    LaunchedEffect(mediaId, mediaType) {
        viewModel.clearNoRatingsTracking(mediaId, mediaType)
    }
    // Use collectAsStateWithLifecycle to collect the flow efficiently.
    // Force refresh when entering from MainScreen to ensure fresh data.
    // Note: MediaDownloadStatus will handle polling, so we only fetch once initially
    // Remember the flow instance so recomposition/focus changes don't recreate and refetch
    val mediaDetailsFlow = remember(mediaId, mediaType, isReturningFromPersonScreenAtEntry) {
        viewModel.getMediaDetails(
            mediaId = mediaId,
            mediaType = mediaType,
            forceRefresh = !isReturningFromPersonScreenAtEntry
        )
    }
    val mediaDetailsState by mediaDetailsFlow.collectAsStateWithLifecycle()

    // Collect StateFlow values only when needed to avoid unnecessary recompositions
    // Only collect these when we actually need them (e.g., when showing server configuration)
    // Use lazy collection to reduce initial load overhead
    val radarrData by viewModel.radarrData.collectAsStateWithLifecycle()
    val sonarrData by viewModel.sonarrData.collectAsStateWithLifecycle()

    // Unified modal handling
    val modalManager = remember { ModalManager() }

    // First-entry control per media key: ensures we only auto-scroll/top and set default highlight once
    val detailsKey = remember(mediaId, mediaType) { "$mediaId:$mediaType" }
    var isFirstEntry by rememberSaveable(detailsKey) { mutableStateOf(true) }

    // State management for return from PersonScreen - persists across back stack pops
    var returnState by rememberSaveable(detailsKey + "_return", stateSaver = ReturnStateSaver) {
        mutableStateOf(
            ReturnState(
                isPending = false,
                focusArea = FocusArea.OVERVIEW,
                activeCarouselIndex = 0,
                scrollOffset = 0
            )
        )
    }
    
    // Clear returnState if we're not returning from PersonScreen navigation
    // This prevents old state from being restored when navigating from MainScreen
    LaunchedEffect(Unit) {
        val focus = appFocusManager.currentFocus
        val isReturningFromPersonScreen = focus is AppFocusState.DetailsScreen && 
            focus.focus != DetailsFocusState.Overview
        
        if (!isReturningFromPersonScreen && returnState.isPending) {
            // We're not returning from PersonScreen, but returnState has old data - clear it
            returnState = returnState.copy(
                isPending = false,
                focusArea = FocusArea.OVERVIEW,
                activeCarouselIndex = 0,
                scrollOffset = 0
            )
            // Debug logging removed to reduce instruction count
        }
    }

    // Use the passed AppFocusManager instance for MediaDetails

    // Helper function to convert FocusArea constants to DetailsFocusState
    fun focusAreaToDetailsFocusState(focusArea: Int): DetailsFocusState {
        return when (focusArea) {
            FocusArea.NONE -> DetailsFocusState.Overview // No focus state
            FocusArea.OVERVIEW -> DetailsFocusState.Overview
            FocusArea.READ_MORE -> DetailsFocusState.ReadMore
            FocusArea.TAGS -> DetailsFocusState.Tags
            FocusArea.CAST -> DetailsFocusState.Cast
            FocusArea.CREW -> DetailsFocusState.Crew
            FocusArea.SIMILAR_MEDIA -> DetailsFocusState.SimilarMedia
            FocusArea.FOURK_REGULAR_OPTION -> DetailsFocusState.FourKRegularOption
            FocusArea.FOURK_4K_OPTION -> DetailsFocusState.FourK4KOption
            FocusArea.PLAY -> DetailsFocusState.Play
            FocusArea.REQUEST_HD -> DetailsFocusState.RequestHD
            FocusArea.REQUEST_4K -> DetailsFocusState.Request4K
            FocusArea.REQUEST_SINGLE -> DetailsFocusState.RequestSingle
            FocusArea.MANAGE_HD -> DetailsFocusState.ManageHD
            FocusArea.MANAGE_4K -> DetailsFocusState.Manage4K
            FocusArea.MANAGE_SINGLE -> DetailsFocusState.ManageSingle
            FocusArea.TRAILER -> DetailsFocusState.Trailer
            FocusArea.ISSUE -> DetailsFocusState.Issue

            else -> DetailsFocusState.Overview
        }
    }

    // Consolidated error handling utility
    class ErrorHandler() {
        fun handleActivityNotFoundError(
            packageName: String,
            appType: String,
            e: ActivityNotFoundException
        ) {
            Log.d("ErrorHandler", "$appType app not found with package: $packageName")
            Log.d("ErrorHandler", "Exception: ${e.message}")
        }

        fun handleUnexpectedError(operation: String, packageName: String? = null, e: Exception) {
            val context = if (packageName != null) " for package: $packageName" else ""
            Log.e("ErrorHandler", "Unexpected error $operation$context: ${e.message}", e)
        }

        fun handlePlaybackError(appType: String, e: Exception) {
            Log.e("ErrorHandler", "Error starting $appType playback: ${e.message}", e)
            showMessage = when (appType.lowercase()) {
                "plex" -> context.getString(R.string.mediaDetails_plexNotFound)
                "jellyfin" -> context.getString(R.string.mediaDetails_jellyfinNotFound)
                "emby" -> context.getString(R.string.mediaDetails_embyNotFound)
                else -> context.getString(R.string.mediaDetails_noMediaPlayerFound)
            }
        }
    }

    // Initialize the error handler
    val errorHandler = remember(context) { ErrorHandler() }

    // Initialize centralized state manager
    val stateManager = remember { MediaDetailsStateManager() }

    // Update state manager with current media details
    stateManager.mediaDetailsState = mediaDetailsState
    // statusInfo is now managed by stateManager

    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    // Define button visibility variables before any effects that use them
    val currentMediaType = stateManager.currentMediaType

    // Determine 4K capability from available servers AND user permissions
    val has4kCapability = when (currentMediaType) {
        MediaType.MOVIE -> {
            val radarrServers = radarrData?.allServers
            val has4kServers = radarrServers?.any { it.server.is4k } ?: false
            val userPermissions = viewModel.getCurrentUserPermissions() ?: 0
            val has4kPermission = CommonUtil.hasAnyPermission(
                userPermissions,
                Permission.REQUEST_4K,
                Permission.REQUEST_4K_MOVIE
            )
            val has4k = has4kServers && has4kPermission
            Log.d(
                "MediaDetails",
                "Movie 4K capability: $has4k (servers: $has4kServers, permission: $has4kPermission, servers: ${radarrServers?.size ?: 0})"
            )
            has4k
        }

        MediaType.TV -> {
            val sonarrServers = sonarrData?.allServers
            val has4kServers = sonarrServers?.any { it.server.is4k } ?: false
            val userPermissions = viewModel.getCurrentUserPermissions() ?: 0
            val has4kPermission = CommonUtil.hasAnyPermission(
                userPermissions,
                Permission.REQUEST_4K,
                Permission.REQUEST_4K_TV
            )
            val has4k = has4kServers && has4kPermission
            Log.d(
                "MediaDetails",
                "TV 4K capability: $has4k (servers: $has4kServers, permission: $has4kPermission, servers: ${sonarrServers?.size ?: 0})"
            )
            has4k
        }

        else -> false
    }

    // Check issue permissions
    val userPermissions = viewModel.getCurrentUserPermissions()
    val canViewIssues = CommonUtil.canViewIssues(userPermissions)
    val canCreateIssues = CommonUtil.canCreateIssues(userPermissions)
    val hasAnyIssuePermission = canViewIssues || canCreateIssues

    // Add logging for availability
    LaunchedEffect(
        stateManager.isAvailable,
        stateManager.isPartiallyAvailable,
        stateManager.isMediaPlayable
    ) {
        // Availability debug info removed to reduce instruction count
    }

    // Show request modal immediately if initialShowRequestModal is true
    LaunchedEffect(initialShowRequestModal, mediaDetailsState) {
        if (initialShowRequestModal && mediaDetailsState is ApiResult.Success && !stateManager.showedRequestModal) {
            delay(500)
            modalManager.openRequest(stateManager.currentFocusArea, stateManager.is4kRequest, null)
            stateManager.showedRequestModal = true
        }
    }

    // Media details state management
    LaunchedEffect(mediaDetailsState) {
        when (val details = mediaDetailsState) {
            is ApiResult.Success<MediaDetails> -> {
                val media = details.data
                // Get dual-tier status information
                val newStatusInfo = processMediaStatus(media, viewModel)
                stateManager.statusInfo = newStatusInfo
                stateManager.isPartiallyAvailable = newStatusInfo.isPartiallyAvailable
                stateManager.isAvailable = newStatusInfo.isAvailable
                stateManager.hasCast = newStatusInfo.hasCast
                stateManager.hasCrew = newStatusInfo.hasCrew
                stateManager.hasTags = newStatusInfo.hasTags
                stateManager.hasTrailer = newStatusInfo.hasTrailer
            }

            else -> {
                // Reset states when not in success state
                stateManager.isPartiallyAvailable = false
                stateManager.isAvailable = false
                stateManager.hasCast = false
                stateManager.hasCrew = false
                stateManager.hasTags = false
                stateManager.hasTrailer = false
                stateManager.hasSimilarMedia = false
                stateManager.similarMediaItems = emptyList()
            }
        }
    }

    // Add LaunchedEffect to fetch similar media
    LaunchedEffect(mediaDetailsState) {
        when (val details = mediaDetailsState) {
            is ApiResult.Success<MediaDetails> -> {
                val media = details.data
                // Clear previous similar media pages and fetch first page
                viewModel.clearSimilarMediaPages()
                viewModel.getSimilarMedia(media.id, media.mediaType?.name?.lowercase() ?: "", 1)
            }

            else -> {
                stateManager.similarMediaItems = emptyList()
                stateManager.hasSimilarMedia = false
            }
        }
    }

    // Observe similar media state
    val similarMediaState by viewModel.similarMediaState.collectAsStateWithLifecycle()
    LaunchedEffect(similarMediaState) {
        when (val state = similarMediaState) {
            is ApiResult.Success<List<SimilarMediaItem>> -> {
                stateManager.similarMediaItems = state.data
                stateManager.hasSimilarMedia =
                    state.data.isNotEmpty() // Only show if we have results
            }

            is ApiResult.Error -> {
                // Only hide on error, not on loading
                stateManager.similarMediaItems = emptyList()
                stateManager.hasSimilarMedia = false
            }

            is ApiResult.Loading, null -> {
                // Keep existing state during loading to prevent flicker
                // Don't change hasSimilarMedia or similarMediaItems
            }
        }
    }

    var isPlaying by remember { mutableStateOf(false) }

    fun triggerMediaPlayer() {
        // No debounce here; handled in handleMediaPlayerTrigger
        stateManager.mediaPlayerTrigger++
    }

    fun triggerWatchTrailer() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - stateManager.lastTrailerTriggerTime > 1000) { // 1000ms debounce
            stateManager.watchTrailerTrigger++
            stateManager.lastTrailerTriggerTime = currentTime
        }
    }

    // Removed force refresh-on-entry to preserve restored state and avoid unnecessary refetches

    // Helper functions for media playback
    fun tryPlexPlayback(media: MediaDetails): Boolean {
        val mediaInfo = media.mediaInfo ?: return false
        val plexPlayUrl = getPlexPlayUrl(mediaInfo) ?: return false

        // Try both Android and Android TV Plex packages
        val packages = listOf("com.plexapp.android", "com.plexapp.androidtv")

        for (packageName in packages) {
            try {
                val playbackIntent = Intent().apply {
                    action = Intent.ACTION_VIEW
                    data = plexPlayUrl.toUri()
                    putExtra("android.intent.extra.START_PLAYBACK", true)
                    setPackage(packageName)
                }

                context.startActivity(playbackIntent)
                return true
            } catch (e: ActivityNotFoundException) {
                errorHandler.handleActivityNotFoundError(packageName, "Plex", e)
                // Continue to next package
            } catch (e: Exception) {
                errorHandler.handleUnexpectedError("trying Plex playback", packageName, e)
                // Continue to next package
            }
        }

        showMessage = context.getString(R.string.mediaDetails_plexNotFound)
        errorHandler.handlePlaybackError("Plex", Exception("Plex app not found with any package"))
        return false
    }

    fun tryJellyfinPlayback(media: MediaDetails): Boolean {
        val mediaInfo = media.mediaInfo ?: return false
        val itemId = getJellyfinPlayUrl(mediaInfo)
        if (itemId.isNullOrBlank()) return false

        try {
            val playbackIntent = Intent().apply {
                action = Intent.ACTION_VIEW
                data = itemId.toUri()
                putExtra("source", 30)
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                setClassName(
                    "org.jellyfin.androidtv",
                    "org.jellyfin.androidtv.ui.startup.StartupActivity"
                )
            }
            context.startActivity(playbackIntent)
            return true
        } catch (e: ActivityNotFoundException) {
            errorHandler.handleActivityNotFoundError("", "Jellyfin", e)
            showMessage = context.getString(R.string.mediaDetails_jellyfinNotFound)
        } catch (e: Exception) {
            errorHandler.handleUnexpectedError("starting Jellyfin playback", null, e)
            showMessage = context.getString(R.string.mediaDetails_jellyfinNotFound)
        }
        return false
    }

    fun tryEmbyPlayback(media: MediaDetails): Boolean {
        val mediaInfo = media.mediaInfo ?: return false
        val itemId = getEmbyPlayUrl(mediaInfo)
        if (itemId.isNullOrBlank()) {
            Log.w("MediaDetails", "⚠️ Emby: No itemId found in mediaInfo")
            return false
        }

        Log.d("MediaDetails", "🎬 Emby playback - itemId: $itemId")
        Log.d("MediaDetails", "🎬 Emby playback - mediaUrl: ${mediaInfo.mediaUrl}")
        Log.d("MediaDetails", "🎬 Emby playback - externalServiceId: ${mediaInfo.externalServiceId}")

        // Extract serverId from mediaUrl to maintain authentication context
        val serverId = try {
            mediaInfo.mediaUrl?.let { url ->
                val uri = url.toUri()
                // Try to get serverId from fragment first (web URL style)
                val fragment = uri.fragment
                if (!fragment.isNullOrBlank() && fragment.contains("serverId=")) {
                    fragment.substringAfter("serverId=").substringBefore("&")
                } else {
                    // Try query parameters
                    uri.getQueryParameter("serverId")
                }
            }
        } catch (e: Exception) {
            Log.w("MediaDetails", "⚠️ Could not extract serverId: ${e.message}")
            null
        }
        
        Log.d("MediaDetails", "🎬 Emby playback - extracted serverId: $serverId")

        // Launch Emby TV app explicitly with StartupActivity
        // Note: Emby may prompt for password due to how StartupActivity initializes sessions.
        // WORKAROUND: Configure Emby's auto-login settings:
        //   1. Open Emby TV app
        //   2. Go to Settings -> User Settings
        //   3. Enable "Remember me" and "Auto-login"
        //   4. Disable "Require PIN" or use a simple PIN
        // Try Emby TV with deep link format (preferred method)
        try {
            Log.d("MediaDetails", "🎬 Launching Emby TV with deep link - itemId: $itemId, serverId: $serverId")
            
            // Construct the Emby deep link URI: emby://item/{ITEM_ID}
            val embyDeepLink = if (!serverId.isNullOrBlank()) {
                "emby://item/$serverId/$itemId"
            } else {
                "emby://item/$itemId"
            }
            
            Log.d("MediaDetails", "🎬 Emby TV deep link: $embyDeepLink")
            
            val playbackIntent = Intent().apply {
                action = Intent.ACTION_VIEW
                data = embyDeepLink.toUri()
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                setPackage("tv.emby.embyatv")
            }
            context.startActivity(playbackIntent)
            Log.d("MediaDetails", "✅ Emby TV app launched successfully via deep link")
            return true
        } catch (e: ActivityNotFoundException) {
            Log.w("MediaDetails", "⚠️ Emby TV deep link failed: ${e.message}")
            // Continue to next method
        } catch (e: Exception) {
            Log.e("MediaDetails", "❌ Error launching Emby TV via deep link: ${e.message}", e)
            // Continue to next method
        }
        
        // Try Emby TV with StartupActivity and intent extras (fallback)
        try {
            Log.d("MediaDetails", "🎬 Launching Emby TV with StartupActivity - itemId: $itemId, serverId: $serverId")
            
            val playbackIntent = Intent().apply {
                action = Intent.ACTION_VIEW
                // Pass itemId as an intent extra
                putExtra("itemId", itemId)
                putExtra("ItemId", itemId)
                if (!serverId.isNullOrBlank()) {
                    putExtra("serverId", serverId)
                    putExtra("ServerId", serverId)
                }
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                setClassName("tv.emby.embyatv", "tv.emby.embyatv.startup.StartupActivity")
            }
            context.startActivity(playbackIntent)
            Log.d("MediaDetails", "✅ Emby TV app launched successfully via StartupActivity")
            Log.i("MediaDetails", "ℹ️ If password prompt appears, enable 'Auto-login' in Emby Settings -> User Settings")
            return true
        } catch (e: ActivityNotFoundException) {
            Log.w("MediaDetails", "⚠️ Emby TV app not found: ${e.message}")
            errorHandler.handleActivityNotFoundError("tv.emby.embyatv", "Emby", e)
            // Continue to regular Android app
        } catch (e: Exception) {
            Log.e("MediaDetails", "❌ Error launching Emby TV: ${e.message}", e)
            errorHandler.handleUnexpectedError("starting Emby playback", "tv.emby.embyatv", e)
            // Continue to next package
        }

        // Try regular Emby Android app as fallback using deep link
        try {
            Log.d("MediaDetails", "🎬 Launching Emby Android app via deep link")
            
            // Construct the Emby deep link URI: emby://items/{SERVER_ID}/{ITEM_ID}
            val embyUri = if (!serverId.isNullOrBlank()) {
                "emby://items/$serverId/$itemId"
            } else {
                "emby://items/$itemId"  // Fallback without server ID
            }
            
            Log.d("MediaDetails", "🎬 Emby deep link URI: $embyUri")
            
            val playbackIntent = Intent().apply {
                action = Intent.ACTION_VIEW
                data = embyUri.toUri()
                setPackage("com.mb.android")
            }
            
            context.startActivity(playbackIntent)
            Log.d("MediaDetails", "✅ Emby Android app launched successfully via deep link")
            Log.i("MediaDetails", "ℹ️ If password prompt appears, enable 'Auto-login' in Emby Settings")
            return true
        } catch (e: ActivityNotFoundException) {
            Log.w("MediaDetails", "⚠️ Emby Android app not found: ${e.message}")
            errorHandler.handleActivityNotFoundError("com.mb.android", "Emby", e)
            showMessage = context.getString(R.string.mediaDetails_embyNotFound)
        } catch (e: Exception) {
            Log.e("MediaDetails", "❌ Error launching Emby Android: ${e.message}", e)
            errorHandler.handleUnexpectedError("starting Emby playback", "com.mb.android", e)
            showMessage = context.getString(R.string.mediaDetails_embyNotFound)
        }
    
    Log.w("MediaDetails", "⚠️ Both Emby TV and Android apps failed or not found")
    return false
    }

    fun handleMediaPlayerTrigger() {
        if (isPlaying) {
            return
        }
        isPlaying = true
        when (val state = mediaDetailsState) {
            is ApiResult.Success<MediaDetails> -> {
                val media = state.data
                val mediaServerType = SharedPreferencesUtil.getMediaServerType(context)
                Log.d("MediaDetails", "🎬 Attempting playback with media server type: $mediaServerType")
                var playbackSuccessful: Boolean
                when (mediaServerType) {
                    MediaServerType.PLEX -> {
                        playbackSuccessful = tryPlexPlayback(media)
                    }

                    MediaServerType.JELLYFIN -> {
                        // Confirmed Jellyfin, but try Emby as fallback in case detection was wrong
                        Log.d("MediaDetails", "Trying Jellyfin playback (confirmed type)...")
                        playbackSuccessful = tryJellyfinPlayback(media)
                        if (!playbackSuccessful) {
                            Log.d("MediaDetails", "Jellyfin failed, trying Emby as fallback...")
                            playbackSuccessful = tryEmbyPlayback(media)
                            if (playbackSuccessful) {
                                // Detected Emby, update saved type
                                Log.d("MediaDetails", "✓ Emby playback succeeded - updating detected type to EMBY")
                                SharedPreferencesUtil.saveDetectedMediaServerType(context, MediaServerType.EMBY)
                            }
                        }
                    }

                    MediaServerType.EMBY -> {
                        // Confirmed Emby, but try Jellyfin as fallback in case detection was wrong
                        Log.d("MediaDetails", "Trying Emby playback (confirmed type)...")
                        playbackSuccessful = tryEmbyPlayback(media)
                        if (!playbackSuccessful) {
                            Log.d("MediaDetails", "Emby failed, trying Jellyfin as fallback...")
                            playbackSuccessful = tryJellyfinPlayback(media)
                            if (playbackSuccessful) {
                                // Detected Jellyfin, update saved type
                                Log.d("MediaDetails", "✓ Jellyfin playback succeeded - updating detected type to JELLYFIN")
                                SharedPreferencesUtil.saveDetectedMediaServerType(context, MediaServerType.JELLYFIN)
                            }
                        }
                    }

                    MediaServerType.NOT_CONFIGURED -> {
                        // Try all three in order
                        Log.d("MediaDetails", "Not configured - trying all media players...")
                        playbackSuccessful = tryPlexPlayback(media)
                        if (!playbackSuccessful) {
                            playbackSuccessful = tryJellyfinPlayback(media)
                        }
                        if (!playbackSuccessful) {
                            playbackSuccessful = tryEmbyPlayback(media)
                        }
                    }
                }
                if (!playbackSuccessful) {
                    errorHandler.handlePlaybackError("media", Exception("No media player found"))
                    showMessage = when (mediaServerType) {
                        MediaServerType.PLEX -> {
                            context.getString(R.string.mediaDetails_plexNotFound)
                        }

                        MediaServerType.JELLYFIN, MediaServerType.EMBY -> {
                            // Generic message for Jellyfin/Emby since we try both
                            context.getString(R.string.mediaDetails_noMediaPlayerFound)
                        }

                        else -> {
                            context.getString(R.string.mediaDetails_noMediaPlayerFound)
                        }
                    }
                }
            }

            else -> {}
        }
    }

    fun handleWatchTrailerTrigger() {
        when (val state = mediaDetailsState) {
            is ApiResult.Success<MediaDetails> -> {
                val videoId = getTrailerYouTubeVideoId(state.data.relatedVideos)
                val trailerUrl = findTrailerUrl(state.data.relatedVideos)
                if (videoId == null && trailerUrl == null) return@handleWatchTrailerTrigger
                if (SharedPreferencesUtil.useTrailerWebView(context)) {
                    if (videoId != null) {
                        stateManager.trailerOverlayVideoId = videoId
                    } else {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, trailerUrl!!.toUri())
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            showMessage = context.getString(R.string.mediaDetails_youTubeNotFound)
                        }
                    }
                } else {
                    try {
                        // Use full URL so YouTube app opens and plays the video (vnd.youtube:id can open app without playing)
                        val uri = when {
                            !trailerUrl.isNullOrBlank() -> trailerUrl.toUri()
                            videoId != null -> android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId")
                            else -> return@handleWatchTrailerTrigger
                        }
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        showMessage = context.getString(R.string.mediaDetails_youTubeNotFound)
                    }
                }
            }
            else -> {}
        }
    }

    LaunchedEffect(showMessage) {
        if (showMessage != null) {
            delay(500) // Add a 500ms delay before allowing the message to be dismissed
        }
    }

    LaunchedEffect(Unit) {
        // Increment trigger when screen is composed/recomposed
        stateManager.compositionTrigger++
    }

    DisposableEffect(Unit) {
        onDispose {
            stateManager.isDisposed = true
        }
    }

    LaunchedEffect(stateManager.mediaPlayerTrigger) {
        if (stateManager.mediaPlayerTrigger > 0) {
            handleMediaPlayerTrigger()
        }
    }

    LaunchedEffect(stateManager.watchTrailerTrigger) {
        if (stateManager.watchTrailerTrigger > 0) {
            handleWatchTrailerTrigger()
        }
    }

    // Add LaunchedEffect in the main composable to reset isPlaying
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            delay(1500)
            isPlaying = false
        }
    }

    // Track if we just restored state to prevent auto-effects from interfering
    var hasJustRestored by remember { mutableStateOf(false) }
    
    // Clear returnState when navigating away from MediaDetails to prevent unwanted restoration
    DisposableEffect(Unit) {
        onDispose {
            // Clear the returnState when this screen is disposed (navigating away)
            // This prevents the old state from being restored when returning from MainScreen
            returnState = returnState.copy(
                isPending = false,
                focusArea = FocusArea.OVERVIEW,
                activeCarouselIndex = 0,
                scrollOffset = 0
            )
            // Debug logging removed to reduce instruction count
        }
    }
    
    // CRITICAL: Initialize stateManager from AppFocusManager on first composition
    // This ensures that if we're returning from navigation, we immediately have the correct focus
    val initialFocusRestored = remember {
        val focus = appFocusManager.currentFocus
        // Debug logging removed to reduce instruction count
        if (focus is AppFocusState.DetailsScreen && focus.focus != DetailsFocusState.Overview) {
            // We're returning from navigation - restore the focus immediately
            val restoredFocusArea = when (focus.focus) {
                DetailsFocusState.Cast -> FocusArea.CAST
                DetailsFocusState.Crew -> FocusArea.CREW
                DetailsFocusState.SimilarMedia -> FocusArea.SIMILAR_MEDIA
                DetailsFocusState.Tags -> FocusArea.TAGS
                DetailsFocusState.ReadMore -> FocusArea.READ_MORE
                DetailsFocusState.Play -> FocusArea.PLAY
                DetailsFocusState.RequestHD -> FocusArea.REQUEST_HD
                DetailsFocusState.Request4K -> FocusArea.REQUEST_4K
                DetailsFocusState.RequestSingle -> FocusArea.REQUEST_SINGLE
                DetailsFocusState.ManageHD -> FocusArea.MANAGE_HD
                DetailsFocusState.Manage4K -> FocusArea.MANAGE_4K
                DetailsFocusState.ManageSingle -> FocusArea.MANAGE_SINGLE
                DetailsFocusState.Trailer -> FocusArea.TRAILER
                DetailsFocusState.Issue -> FocusArea.ISSUE
                else -> FocusArea.OVERVIEW
            }
            stateManager.currentFocusArea = restoredFocusArea
            hasJustRestored = true
            isFirstEntry = false
            // Debug logging removed to reduce instruction count
            true
        } else {
            // Debug logging removed to reduce instruction count
            false
        }
    }
    
    // Restore scroll position and carousel indices when returning from navigation
    LaunchedEffect(initialFocusRestored) {
        if (initialFocusRestored) {
            // Debug logging removed to reduce instruction count
            // Restore the active carousel index based on the focus area
            when (returnState.focusArea) {
                FocusArea.CAST -> stateManager.selectedCastIndex = returnState.activeCarouselIndex
                FocusArea.CREW -> stateManager.selectedCrewIndex = returnState.activeCarouselIndex
                FocusArea.TAGS -> stateManager.selectedTagIndex = returnState.activeCarouselIndex
                FocusArea.SIMILAR_MEDIA -> stateManager.selectedSimilarMediaIndex = returnState.activeCarouselIndex
            }
            
            // Debug logging removed to reduce instruction count
            
            // Wait for carousel LaunchedEffects to trigger their auto-scroll
            delay(100)
            
            // Then restore the vertical scroll position
            if (returnState.scrollOffset > 0) {
                val targetScroll = returnState.scrollOffset.coerceIn(0, scrollState.maxValue)
                scrollState.scrollTo(targetScroll)
                // Debug logging removed to reduce instruction count
            }
            
            // Clear the pending state and reset all saved values since we've restored everything
            returnState = returnState.copy(
                isPending = false,
                focusArea = FocusArea.OVERVIEW,
                activeCarouselIndex = 0,
                scrollOffset = 0
            )
            // Debug logging removed to reduce instruction count
        }
    }
    
    // Sync current focus area with AppFocusManager - only when focus actually changes
    // Skip syncing if we just initialized from AppFocusManager to prevent circular updates
    LaunchedEffect(stateManager.currentFocusArea) {
        if (BuildConfig.DEBUG) {
            Log.d(
                "MediaDetails",
                "🔄 Focus area changed to: $stateManager.currentFocusArea (TAGS=${FocusArea.TAGS})"
            )
        }
        
        // Skip the first sync if we initialized from AppFocusManager
        if (initialFocusRestored && stateManager.currentFocusArea != FocusArea.OVERVIEW) {
            // Already synced during initialization
            return@LaunchedEffect
        }
        
        // Only sync focus if we're not in a cleared state (FocusArea.NONE)
        // This prevents overriding TopBar focus when we clear MediaDetails highlights
        if (stateManager.currentFocusArea != FocusArea.NONE) {
            val detailsFocusState = focusAreaToDetailsFocusState(stateManager.currentFocusArea)
            appFocusManager.setFocus(AppFocusState.DetailsScreen(detailsFocusState))
        }
    }

    // Sync AppFocusManager changes back to local stateManager.currentFocusArea - with debouncing
    LaunchedEffect(appFocusManager.currentFocus) {
        val focus = appFocusManager.currentFocus
        
        // Skip if we just initialized from AppFocusManager (on first composition)
        if (initialFocusRestored && stateManager.currentFocusArea != FocusArea.OVERVIEW) {
            return@LaunchedEffect
        }
        
        // Debounce to avoid rapid fire updates
        delay(10)

        when (focus) {
            is AppFocusState.DetailsScreen -> {
                // Only update local focus area if we're not in a cleared state
                // This prevents overriding FocusArea.NONE when TopBar has focus
                if (stateManager.currentFocusArea != FocusArea.NONE) {
                    val newFocusArea = when (focus.focus) {
                        DetailsFocusState.Overview -> FocusArea.OVERVIEW
                        DetailsFocusState.ReadMore -> FocusArea.READ_MORE
                        DetailsFocusState.Tags -> FocusArea.TAGS
                        DetailsFocusState.Cast -> FocusArea.CAST
                        DetailsFocusState.Crew -> FocusArea.CREW
                        DetailsFocusState.SimilarMedia -> FocusArea.SIMILAR_MEDIA
                        DetailsFocusState.FourKRegularOption -> FocusArea.FOURK_REGULAR_OPTION
                        DetailsFocusState.FourK4KOption -> FocusArea.FOURK_4K_OPTION
                        DetailsFocusState.Play -> FocusArea.PLAY
                        DetailsFocusState.RequestHD -> FocusArea.REQUEST_HD
                        DetailsFocusState.Request4K -> FocusArea.REQUEST_4K
                        DetailsFocusState.RequestSingle -> FocusArea.REQUEST_SINGLE
                        DetailsFocusState.ManageHD -> FocusArea.MANAGE_HD
                        DetailsFocusState.Manage4K -> FocusArea.MANAGE_4K
                        DetailsFocusState.ManageSingle -> FocusArea.MANAGE_SINGLE
                        DetailsFocusState.Trailer -> FocusArea.TRAILER
                        DetailsFocusState.Issue -> FocusArea.ISSUE
                        else -> FocusArea.OVERVIEW
                    }
                    if (newFocusArea != stateManager.currentFocusArea) {
                        stateManager.currentFocusArea = newFocusArea
                    }
                }
            }

            is AppFocusState.TopBar -> {
                // When TopBar gains focus, clear MediaDetails highlights
                // This ensures no MediaDetails components remain highlighted
                stateManager.currentFocusArea = FocusArea.NONE
            }

            else -> {
                // Focus is on a different screen, clear MediaDetails highlights
                stateManager.currentFocusArea = FocusArea.NONE
            }
        }
    }


    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Global back suppression: consume Back while modals are open or trailer overlay is visible or within debounce window after modal Back
        val nowTs = System.currentTimeMillis()
        val shouldSuppressBack = (nowTs - stateManager.lastIssueBackKeyTime < 600) ||
                modalManager.showIssueReport || modalManager.showIssueDetails ||
                stateManager.trailerOverlayVideoId != null
        BackHandler(enabled = shouldSuppressBack) {
            when {
                stateManager.trailerOverlayVideoId != null -> {
                    stateManager.trailerOverlayVideoId = null
                }
                modalManager.showIssueReport -> {
                    modalManager.closeIssueReport()
                    stateManager.lastIssueModalCloseTime = System.currentTimeMillis()
                }
                modalManager.showIssueDetails -> {
                    modalManager.closeIssueDetails()
                    stateManager.lastIssueModalCloseTime = System.currentTimeMillis()
                }
                else -> {
                    // no-op: just consume
                }
            }
        }
        when (val details = mediaDetailsState) {
            is ApiResult.Success<MediaDetails> -> {
                val media = details.data
                val canRequestMedia =
                    remember(media.mediaType, viewModel.getCurrentUserPermissions()) {
                        CommonUtil.canRequest(
                            userPermissions = viewModel.getCurrentUserPermissions(),
                            mediaType = media.mediaType,
                            is4k = false
                        )
                    }

                // Dynamic focus order for new action buttons
                val actionButtonStates = getActionButtonStates(
                    media = media,
                    viewModel = viewModel,
                    canRequest = canRequestMedia,
                    has4kCapability = has4kCapability,
                    hasTrailer = stateManager.hasTrailer
                )

                val buttonFocusOrder = buildList {
                    if (actionButtonStates["play"]?.isVisible == true) {
                        // Standalone play button
                        add(FocusArea.PLAY)
                    }
                    if (actionButtonStates["request"]?.isVisible == true) {
                        if (actionButtonStates["request"]?.isSplit == true) {
                            add(FocusArea.REQUEST_HD)
                            add(FocusArea.REQUEST_4K)
                        } else {
                            add(FocusArea.REQUEST_SINGLE)
                        }
                    }
                    if (actionButtonStates["manage"]?.isVisible == true) {
                        if (actionButtonStates["manage"]?.isSplit == true) {
                            add(FocusArea.MANAGE_HD)
                            add(FocusArea.MANAGE_4K)
                        } else {
                            add(FocusArea.MANAGE_SINGLE)
                        }
                    }
                    if (actionButtonStates["trailer"]?.isVisible == true) {
                        add(FocusArea.TRAILER)
                    }
                }

                // Group consecutive split buttons so vertical navigation skips the right half
                val actionGroupStarts = remember(buttonFocusOrder) {
                    val starts = mutableListOf<Int>()
                    var i = 0
                    while (i < buttonFocusOrder.size) {
                        val id = buttonFocusOrder[i]
                        if (
                            i + 1 < buttonFocusOrder.size &&
                            ((id == FocusArea.REQUEST_HD && buttonFocusOrder[i + 1] == FocusArea.REQUEST_4K) ||
                                    (id == FocusArea.MANAGE_HD && buttonFocusOrder[i + 1] == FocusArea.MANAGE_4K))
                        ) {
                            starts.add(i)
                            i += 2
                        } else {
                            starts.add(i)
                            i += 1
                        }
                    }
                    starts.toList()
                }

                fun nextActionGroupFirst(current: Int): Int? {
                    val idx = buttonFocusOrder.indexOf(current)
                    if (idx == -1) return actionGroupStarts.firstOrNull()
                        ?.let { buttonFocusOrder[it] }
                    val gIndex = actionGroupStarts.indexOfLast { it <= idx }
                    return actionGroupStarts.getOrNull(gIndex + 1)?.let { buttonFocusOrder[it] }
                }

                fun prevActionGroupFirst(current: Int): Int? {
                    val idx = buttonFocusOrder.indexOf(current)
                    if (idx == -1) return actionGroupStarts.lastOrNull()
                        ?.let { buttonFocusOrder[it] }
                    val gIndex = actionGroupStarts.indexOfLast { it <= idx }
                    return if (gIndex > 0) actionGroupStarts.getOrNull(gIndex - 1)
                        ?.let { buttonFocusOrder[it] } else null
                }

                // Note: returnState.isPending restoration is now handled by initialFocusRestored mechanism above
                // Small guard window to prevent init effects from overriding restored state
                LaunchedEffect(hasJustRestored) {
                    if (hasJustRestored) {
                        // Allow time for the UI to fully settle and scroll position to be applied
                        // Extended delay to ensure auto-scroll guard stays active during scroll restoration
                        delay(500)
                        hasJustRestored = false
                    }
                }

                LaunchedEffect(buttonFocusOrder) {
                    focusManager.setButtonOrder(buttonFocusOrder)
                }

                // Handle returning from TopBar: automatically focus on first available action button
                LaunchedEffect(appFocusManager.currentFocus, buttonFocusOrder) {
                    val focus = appFocusManager.currentFocus
                    if (focus is AppFocusState.DetailsScreen &&
                        focus.focus is DetailsFocusState.Overview &&
                        stateManager.currentFocusArea == FocusArea.NONE &&
                        !returnState.isPending
                    ) {
                        if (hasJustRestored) return@LaunchedEffect
                        // Don't override initial focus setting on first entry
                        if (isFirstEntry) return@LaunchedEffect
                        // We're returning from TopBar (stateManager.currentFocusArea == NONE) 
                        // and received Overview state - automatically focus on first available action button
                        val targetArea = if (buttonFocusOrder.isNotEmpty()) buttonFocusOrder.first() else FocusArea.OVERVIEW
                        stateManager.currentFocusArea = targetArea
                        // Keep AppFocusManager in sync with the chosen target to avoid being overridden back to Overview
                        val targetDetailsFocus = focusAreaToDetailsFocusState(targetArea)
                        appFocusManager.setFocus(AppFocusState.DetailsScreen(targetDetailsFocus))
                    }
                }

                // Handle ISSUE button activation and modal routing (unified manager)
                val internalMediaId = media.mediaInfo?.id ?: -1
                if (internalMediaId == -1) {
                    Log.w(
                        "MediaDetails",
                        "⚠️ media.mediaInfo.id is null; opening modal but submit will be blocked"
                    )
                }
                if (modalManager.showIssueReport) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1000f)
                    ) {
                        IssueReportModal(
                            isVisible = modalManager.showIssueReport,
                            mediaId = internalMediaId,
                            isSeries = (media.mediaType?.name?.equals(
                                "tv",
                                ignoreCase = true
                            ) == true),
                            onBackKeyDown = {
                                stateManager.lastIssueBackKeyTime = System.currentTimeMillis()
                            },
                            onClose = {
                                modalManager.closeIssueReport()
                                stateManager.lastIssueModalCloseTime = System.currentTimeMillis()
                                // Restore focus via saved area
                                stateManager.currentFocusArea =
                                    modalManager.lastFocusedArea.takeIf { it != -1 }
                                        ?: FocusArea.OVERVIEW
                            },
                            onSuccess = {
                                refreshManager.handleIssueSuccess(
                                    context = context,
                                    viewModel = viewModel,
                                    mediaId = mediaId,
                                    mediaType = mediaType,
                                    setMessage = { showMessage = it }
                                )
                            }
                        )
                    }
                }

                // IssueDetails is managed by modalManager
                if (modalManager.showIssueDetails) {
                    // Use existing data - no need to force refresh as MediaDownloadStatus handles polling
                    // The issues data is already available from the current mediaDetailsState
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1000f)
                    ) {
                        IssueDetailsModal(
                            isVisible = modalManager.showIssueDetails,
                            issues = media.mediaInfo?.issues,
                            onBackKeyDown = {
                                stateManager.lastIssueBackKeyTime = System.currentTimeMillis()
                            },
                            onClose = {
                                modalManager.closeIssueDetails()
                                stateManager.lastIssueModalCloseTime = System.currentTimeMillis()
                                stateManager.currentFocusArea =
                                    modalManager.lastFocusedArea.takeIf { it != -1 }
                                        ?: FocusArea.OVERVIEW
                            },
                            onCreateNewIssue = {
                                modalManager.closeIssueDetails()
                                modalManager.openIssueReport(stateManager.currentFocusArea)
                            },
                            onRefresh = {
                                // One-shot refresh after a successful comment add to update issue thread
                                viewModel.forceRefreshMediaDetails(mediaId, mediaType)
                            },
                            canCreateIssues = canCreateIssues
                        )
                    }
                }

                // Register MediaDetails with centralized DpadController using original business logic
                val dpadConfig = createMediaDetailsDpadConfig(
                    route = "details",
                    focusManager = appFocusManager,
                    onUp = {
                        // Original business logic for Up navigation
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "MediaDetails",
                                "🔄 onUp called, stateManager.currentFocusArea: $stateManager.currentFocusArea"
                            )
                        }
                        when (stateManager.currentFocusArea) {
                            FocusArea.OVERVIEW -> {
                                // Navigate to TopBar when at the top of details
                                appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Search))
                            }

                            FocusArea.PLAY,
                            FocusArea.REQUEST_HD, FocusArea.REQUEST_4K, FocusArea.REQUEST_SINGLE,
                            FocusArea.MANAGE_HD, FocusArea.MANAGE_4K, FocusArea.MANAGE_SINGLE,
                            FocusArea.TRAILER -> {
                                // Up should move to the first half (left) of the previous action group
                                val target = prevActionGroupFirst(stateManager.currentFocusArea)
                                if (target != null) {
                                    stateManager.currentFocusArea = target
                                } else {
                                    // At the top-most action group, move to ISSUE if available and user has permission, otherwise TopBar
                                    if (hasAnyIssuePermission && stateManager.statusInfo?.let { it.isAvailable || it.isPartiallyAvailable } == true) {
                                        stateManager.currentFocusArea = FocusArea.ISSUE
                                    } else {
                                        // Skip ISSUE and go to TopBar
                                        appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Search))
                                    }
                                }
                            }

                            FocusArea.TAGS -> {
                                if (stateManager.hasTags) {
                                    val currentRowIndex =
                                        stateManager.leftmostTags.indexOfLast { it <= stateManager.selectedTagIndex }
                                    if (currentRowIndex > 0) {
                                        val previousRowStart =
                                            stateManager.leftmostTags[currentRowIndex - 1]
                                        val offset =
                                            stateManager.selectedTagIndex - stateManager.leftmostTags[currentRowIndex]
                                        stateManager.selectedTagIndex =
                                            (previousRowStart + offset).coerceAtMost(stateManager.leftmostTags[currentRowIndex] - 1)
                                    } else {
                                        stateManager.currentFocusArea = FocusArea.OVERVIEW
                                    }
                                } else {
                                    stateManager.currentFocusArea = FocusArea.OVERVIEW
                                }
                            }

                            FocusArea.CAST -> {
                                // Up from CAST goes to bottom-most visible action button
                                stateManager.currentFocusArea =
                                    buttonFocusOrder.lastOrNull() ?: FocusArea.OVERVIEW
                            }

                            FocusArea.CREW -> {
                                if (stateManager.hasCast) {
                                    stateManager.currentFocusArea = FocusArea.CAST
                                    stateManager.selectedCastIndex = 0
                                } else {
                                    stateManager.currentFocusArea =
                                        buttonFocusOrder.lastOrNull() ?: FocusArea.OVERVIEW
                                }
                            }

                            FocusArea.ISSUE -> {
                                // From Issue, go directly to TopBar
                                appFocusManager.setFocus(AppFocusState.TopBar(TopBarFocus.Search))
                            }

                            FocusArea.FOURK_REGULAR_OPTION -> {
                                // Navigate up to close the modal and return to request button
                                Log.d(
                                    "MediaDetails",
                                    "🔄 FOURK_REGULAR_OPTION Up navigation - closing modal, returning to request button"
                                )
                                stateManager.currentFocusArea = buttonFocusOrder.find {
                                    it == FocusArea.REQUEST_HD || it == FocusArea.REQUEST_SINGLE
                                } ?: FocusArea.OVERVIEW
                            }

                            FocusArea.FOURK_4K_OPTION -> {
                                // Navigate up to close the modal and return to request button
                                Log.d(
                                    "MediaDetails",
                                    "🔄 FOURK_4K_OPTION Up navigation - closing modal, returning to request button"
                                )
                                stateManager.currentFocusArea = buttonFocusOrder.find {
                                    it == FocusArea.REQUEST_4K || it == FocusArea.REQUEST_SINGLE
                                } ?: FocusArea.OVERVIEW
                            }

                            FocusArea.SIMILAR_MEDIA -> {
                                if (stateManager.hasCrew) {
                                    stateManager.currentFocusArea = FocusArea.CREW
                                    stateManager.selectedCrewIndex = 0
                                } else if (stateManager.hasCast) {
                                    stateManager.currentFocusArea = FocusArea.CAST
                                    stateManager.selectedCastIndex = 0
                                } else {
                                    // Navigate to the last visible action button
                                    stateManager.currentFocusArea =
                                        buttonFocusOrder.lastOrNull() ?: FocusArea.OVERVIEW
                                }
                            }

                            else -> {}
                        }
                    },
                    onDown = {
                        // Check if user is navigating from TopBar back to MediaDetails
                        val currentFocus = appFocusManager.currentFocus
                        // Debug logging removed to reduce instruction count
                        if (currentFocus is AppFocusState.TopBar) {
                            // User is coming from TopBar, navigate to first available action button or Overview
                            val targetArea = if (buttonFocusOrder.isNotEmpty()) buttonFocusOrder.first() else FocusArea.OVERVIEW
                            stateManager.currentFocusArea = targetArea
                            val targetDetailsFocus = focusAreaToDetailsFocusState(targetArea)
                            appFocusManager.setFocus(AppFocusState.DetailsScreen(targetDetailsFocus))
                            return@createMediaDetailsDpadConfig
                        }

                        // Original business logic for Down navigation
                        when (stateManager.currentFocusArea) {
                            FocusArea.OVERVIEW -> {
                                if (stateManager.hasTags) {
                                    // Preselect the leftmost tag in the top row to prevent scroll-to-top jitter
                                    stateManager.selectedTagIndex =
                                        stateManager.leftmostTags.firstOrNull() ?: 0
                                    stateManager.currentFocusArea = FocusArea.TAGS
                                } else if (stateManager.hasCast) {
                                    FocusArea.CAST
                                } else if (stateManager.hasCrew) {
                                    FocusArea.CREW
                                } else {
                                    // Navigate to issue button if available and user has permission, otherwise to action buttons
                                    if (hasAnyIssuePermission && stateManager.statusInfo?.let { it.isAvailable || it.isPartiallyAvailable } == true) {
                                        FocusArea.ISSUE
                                    } else {
                                        stateManager.currentFocusArea =
                                            buttonFocusOrder.firstOrNull() ?: FocusArea.OVERVIEW
                                    }
                                }
                            }

                            FocusArea.ISSUE -> {
                                // Navigate to the first visible action button
                                stateManager.currentFocusArea =
                                    buttonFocusOrder.firstOrNull() ?: FocusArea.OVERVIEW
                            }

                            FocusArea.PLAY,
                            FocusArea.REQUEST_HD, FocusArea.REQUEST_4K, FocusArea.REQUEST_SINGLE,
                            FocusArea.MANAGE_HD, FocusArea.MANAGE_4K, FocusArea.MANAGE_SINGLE,
                            FocusArea.TRAILER -> {
                                // Down should move to the first half (left) of the next action group
                                val target = nextActionGroupFirst(stateManager.currentFocusArea)
                                if (target != null) {
                                    stateManager.currentFocusArea = target
                                } else if (stateManager.hasCast) {
                                    stateManager.currentFocusArea = FocusArea.CAST
                                    stateManager.selectedCastIndex = 0
                                } else if (stateManager.hasCrew) {
                                    stateManager.currentFocusArea = FocusArea.CREW
                                    stateManager.selectedCrewIndex = 0
                                } else if (stateManager.hasSimilarMedia) {
                                    stateManager.currentFocusArea = FocusArea.SIMILAR_MEDIA
                                    stateManager.selectedSimilarMediaIndex = 0
                                }
                            }

                            FocusArea.TAGS -> {
                                val currentRowIndex =
                                    stateManager.leftmostTags.indexOfLast { it <= stateManager.selectedTagIndex }
                                if (currentRowIndex < stateManager.leftmostTags.size - 1) {
                                    val nextRowStart =
                                        stateManager.leftmostTags[currentRowIndex + 1]
                                    val offset =
                                        stateManager.selectedTagIndex - stateManager.leftmostTags[currentRowIndex]
                                    stateManager.selectedTagIndex =
                                        (nextRowStart + offset).coerceAtMost(
                                            (media.keywords?.size ?: 0) - 1
                                        )
                                } else {
                                    // If we're in the bottom row or there's only one row, always go to the cast
                                    if (stateManager.hasCast) {
                                        stateManager.currentFocusArea = FocusArea.CAST
                                        stateManager.selectedCastIndex = 0
                                    } else if (stateManager.hasCrew) {
                                        stateManager.currentFocusArea = FocusArea.CREW
                                        stateManager.selectedCrewIndex = 0
                                    } else {
                                        stateManager.currentFocusArea =
                                            buttonFocusOrder.firstOrNull() ?: FocusArea.OVERVIEW
                                    }
                                }
                            }

                            FocusArea.CAST -> {
                                // Down from CAST goes to CREW (if present), otherwise SIMILAR
                                if (stateManager.hasCrew) {
                                    stateManager.currentFocusArea = FocusArea.CREW
                                    stateManager.selectedCrewIndex = 0
                                } else if (stateManager.hasSimilarMedia) {
                                    stateManager.currentFocusArea = FocusArea.SIMILAR_MEDIA
                                    stateManager.selectedSimilarMediaIndex = 0
                                }
                            }

                            FocusArea.CREW -> {
                                // Down from CREW goes to SIMILAR if present
                                if (stateManager.hasSimilarMedia) {
                                    stateManager.currentFocusArea = FocusArea.SIMILAR_MEDIA
                                    stateManager.selectedSimilarMediaIndex = 0
                                }
                            }

                            else -> {}
                        }
                    },
                    onLeft = {
                        // Original business logic for Left navigation
                        when (stateManager.currentFocusArea) {
                            FocusArea.TAGS -> {
                                if (stateManager.leftmostTags.contains(stateManager.selectedTagIndex)) {
                                    buttonFocusOrder.firstOrNull()?.let {
                                        stateManager.currentFocusArea = it
                                    }
                                } else {
                                    stateManager.selectedTagIndex--
                                }
                            }

                            FocusArea.CAST -> {
                                if (stateManager.selectedCastIndex > 0) {
                                    stateManager.selectedCastIndex--
                                }
                            }

                            FocusArea.CREW -> {
                                if (stateManager.selectedCrewIndex > 0) {
                                    stateManager.selectedCrewIndex--
                                }
                            }

                            FocusArea.SIMILAR_MEDIA -> {
                                if (stateManager.selectedSimilarMediaIndex > 0) {
                                    stateManager.selectedSimilarMediaIndex--
                                }
                            }

                            // Enable left-right toggling across split buttons
                            FocusArea.REQUEST_4K -> {
                                // Move back to HD half
                                stateManager.currentFocusArea = FocusArea.REQUEST_HD
                            }

                            FocusArea.MANAGE_4K -> {
                                // Move back to HD half
                                stateManager.currentFocusArea = FocusArea.MANAGE_HD
                            }

                            else -> {}
                        }
                    },
                    onRight = {
                        // Original business logic for Right navigation
                        when (stateManager.currentFocusArea) {
                            FocusArea.PLAY -> {
                                stateManager.currentFocusArea = FocusArea.TAGS
                            }

                            FocusArea.REQUEST_HD -> {
                                // Navigate to 4K half of split button
                                stateManager.currentFocusArea = FocusArea.REQUEST_4K
                            }

                            FocusArea.REQUEST_4K -> {
                                // Navigate to tags
                                if (stateManager.hasTags) {
                                    stateManager.currentFocusArea = FocusArea.TAGS
                                    stateManager.selectedTagIndex =
                                        stateManager.leftmostTags.first()
                                } else if (stateManager.hasCast) {
                                    stateManager.currentFocusArea = FocusArea.CAST
                                    stateManager.selectedCastIndex = 0
                                } else if (stateManager.hasCrew) {
                                    stateManager.currentFocusArea = FocusArea.CREW
                                    stateManager.selectedCrewIndex = 0
                                }
                            }

                            FocusArea.REQUEST_SINGLE -> {
                                // Navigate to tags
                                if (stateManager.hasTags) {
                                    stateManager.currentFocusArea = FocusArea.TAGS
                                    stateManager.selectedTagIndex =
                                        stateManager.leftmostTags.first()
                                } else if (stateManager.hasCast) {
                                    stateManager.currentFocusArea = FocusArea.CAST
                                    stateManager.selectedCastIndex = 0
                                } else if (stateManager.hasCrew) {
                                    stateManager.currentFocusArea = FocusArea.CREW
                                    stateManager.selectedCrewIndex = 0
                                }
                            }

                            FocusArea.MANAGE_HD -> {
                                // Navigate to 4K half of split button
                                stateManager.currentFocusArea = FocusArea.MANAGE_4K
                            }

                            FocusArea.MANAGE_4K -> {
                                // Navigate to tags
                                if (stateManager.hasTags) {
                                    stateManager.currentFocusArea = FocusArea.TAGS
                                    stateManager.selectedTagIndex =
                                        stateManager.leftmostTags.first()
                                } else if (stateManager.hasCast) {
                                    stateManager.currentFocusArea = FocusArea.CAST
                                    stateManager.selectedCastIndex = 0
                                } else if (stateManager.hasCrew) {
                                    stateManager.currentFocusArea = FocusArea.CREW
                                    stateManager.selectedCrewIndex = 0
                                }
                            }

                            FocusArea.MANAGE_SINGLE -> {
                                // Navigate to tags
                                if (stateManager.hasTags) {
                                    stateManager.currentFocusArea = FocusArea.TAGS
                                    stateManager.selectedTagIndex =
                                        stateManager.leftmostTags.first()
                                } else if (stateManager.hasCast) {
                                    stateManager.currentFocusArea = FocusArea.CAST
                                    stateManager.selectedCastIndex = 0
                                } else if (stateManager.hasCrew) {
                                    stateManager.currentFocusArea = FocusArea.CREW
                                    stateManager.selectedCrewIndex = 0
                                }
                            }

                            FocusArea.TRAILER -> {
                                if (stateManager.hasTags) {
                                    stateManager.currentFocusArea = FocusArea.TAGS
                                    stateManager.selectedTagIndex =
                                        stateManager.leftmostTags.first()
                                } else if (stateManager.hasCast) {
                                    stateManager.currentFocusArea = FocusArea.CAST
                                    stateManager.selectedCastIndex = 0
                                } else if (stateManager.hasCrew) {
                                    stateManager.currentFocusArea = FocusArea.CREW
                                    stateManager.selectedCrewIndex = 0
                                }
                            }

                            FocusArea.ISSUE -> {
                                // Navigate right from ISSUE to Tags/Cast/Crew
                                if (stateManager.hasTags) {
                                    stateManager.currentFocusArea = FocusArea.TAGS
                                    stateManager.selectedTagIndex =
                                        stateManager.leftmostTags.first()
                                } else if (stateManager.hasCast) {
                                    stateManager.currentFocusArea = FocusArea.CAST
                                    stateManager.selectedCastIndex = 0
                                } else if (stateManager.hasCrew) {
                                    stateManager.currentFocusArea = FocusArea.CREW
                                    stateManager.selectedCrewIndex = 0
                                }
                            }

                            FocusArea.TAGS -> {
                                if (stateManager.selectedTagIndex < (media.keywords?.size
                                        ?: 0) - 1
                                ) {
                                    stateManager.selectedTagIndex++
                                }
                            }

                            FocusArea.CAST -> {
                                if (stateManager.selectedCastIndex < (media.credits.cast.size) - 1) {
                                    stateManager.selectedCastIndex++
                                }
                            }

                            FocusArea.CREW -> {
                                if (stateManager.selectedCrewIndex < (media.credits.crew.size) - 1) {
                                    stateManager.selectedCrewIndex++
                                }
                            }

                            FocusArea.SIMILAR_MEDIA -> {
                                if (stateManager.selectedSimilarMediaIndex < stateManager.similarMediaItems.size - 1) {
                                    stateManager.selectedSimilarMediaIndex++
                                }
                            }

                            else -> {}
                        }
                    },
                    onEnter = {
                        // Original business logic for Enter key with debouncing
                        val now = System.currentTimeMillis()
                        val sinceNav = now - stateManager.lastScreenNavigationTime
                        if (sinceNav < 300) {
                            Log.d(
                                "MediaDetails",
                                "⏸️ Enter ignored globally (since navigation ${sinceNav}ms < 300ms), focus=$stateManager.currentFocusArea"
                            )
                            return@createMediaDetailsDpadConfig
                        }

                        Log.d(
                            "MediaDetails",
                            "🎯 Enter key pressed - stateManager.currentFocusArea: $stateManager.currentFocusArea"
                        )

                        when (stateManager.currentFocusArea) {
                            FocusArea.PLAY -> {
                                // Check if enough time has passed since screen navigation to prevent Enter key from previous screen
                                val currentTime = System.currentTimeMillis()
                                val timeSinceNavigation =
                                    currentTime - stateManager.lastScreenNavigationTime

                                if (timeSinceNavigation > 300) {
                                    Log.d(
                                        "MediaDetails",
                                        "✅ Enter key accepted for PLAY_BUTTON (navigation: ${timeSinceNavigation}ms)"
                                    )
                                    triggerMediaPlayer()
                                } else {
                                    Log.d(
                                        "MediaDetails",
                                        "⏸️ Enter key ignored for PLAY_BUTTON (navigation: ${timeSinceNavigation}ms < 300ms)"
                                    )
                                }
                            }

                            FocusArea.READ_MORE -> {
                                if (stateManager.hasReadMoreButton) {
                                    stateManager.isFullOverviewShown =
                                        !stateManager.isFullOverviewShown
                                }
                            }

                            FocusArea.CAST -> {
                                // Check if enough time has passed since screen navigation to prevent Enter key from previous screen
                                val currentTime = System.currentTimeMillis()
                                val timeSinceNavigation =
                                    currentTime - stateManager.lastScreenNavigationTime

                                if (timeSinceNavigation > 300) {
                                    Log.d(
                                        "MediaDetails",
                                        "✅ Enter key accepted for CAST (navigation: ${timeSinceNavigation}ms)"
                                    )
                                    // Save return state before navigating to cast person
                                    // Debug logging removed to reduce instruction count
                                    returnState = returnState.copy(
                                        isPending = true,
                                        focusArea = FocusArea.CAST,
                                        activeCarouselIndex = stateManager.selectedCastIndex,
                                        scrollOffset = scrollState.value
                                    )
                                    navigationManager.navigateToPerson(media.credits.cast[stateManager.selectedCastIndex].id.toString())
                                } else {
                                    Log.d(
                                        "MediaDetails",
                                        "⏸️ Enter key ignored for CAST (navigation: ${timeSinceNavigation}ms < 300ms)"
                                    )
                                }
                            }

                            FocusArea.CREW -> {
                                // Check if enough time has passed since screen navigation to prevent Enter key from previous screen
                                val currentTime = System.currentTimeMillis()
                                val timeSinceNavigation =
                                    currentTime - stateManager.lastScreenNavigationTime

                                if (timeSinceNavigation > 300) {
                                    Log.d(
                                        "MediaDetails",
                                        "✅ Enter key accepted for CREW (navigation: ${timeSinceNavigation}ms)"
                                    )
                                    // Ensure AppFocusManager is synced with Crew focus before navigation
                                    appFocusManager.setFocus(AppFocusState.DetailsScreen(DetailsFocusState.Crew))
                                    // Debug logging removed to reduce instruction count
                                    
                                    // Save return state before navigating to crew person
                                    // Note: scrollOffset was already saved by auto-scroll logic, don't override it
                                    // Debug logging removed to reduce instruction count
                                    returnState = returnState.copy(
                                        isPending = true,
                                        focusArea = FocusArea.CREW,
                                        activeCarouselIndex = stateManager.selectedCrewIndex
                                        // scrollOffset already saved by auto-scroll logic
                                    )
                                    navigationManager.navigateToPerson(media.credits.crew[stateManager.selectedCrewIndex].id.toString())
                                } else {
                                    Log.d(
                                        "MediaDetails",
                                        "⏸️ Enter key ignored for CREW (navigation: ${timeSinceNavigation}ms < 300ms)"
                                    )
                                }
                            }

                            FocusArea.REQUEST_HD -> {
                                // Ensure the request tier is correctly set before opening the modal
                                stateManager.is4kRequest = false
                                modalManager.openRequest(stateManager.currentFocusArea, false, null)
                            }

                            FocusArea.REQUEST_4K -> {
                                // Ensure the request tier is correctly set before opening the modal
                                stateManager.is4kRequest = true
                                modalManager.openRequest(stateManager.currentFocusArea, true, null)
                            }

                            FocusArea.REQUEST_SINGLE -> {
                                // Single request maps to regular (HD) tier
                                stateManager.is4kRequest = false
                                modalManager.openRequest(stateManager.currentFocusArea, false, null)
                            }

                            FocusArea.MANAGE_HD -> {
                                modalManager.openRequestAction(
                                    stateManager.currentFocusArea,
                                    false,
                                    null
                                )
                            }

                            FocusArea.MANAGE_4K -> {
                                modalManager.openRequestAction(
                                    stateManager.currentFocusArea,
                                    true,
                                    null
                                )
                            }

                            FocusArea.MANAGE_SINGLE -> {
                                // Handle single manage button - determine tier based on which request exists
                                val (regularRequest, fourKRequest) = viewModel.getRequestsForMedia(
                                    mediaId.toInt()
                                )
                                val localRegularRequest = regularRequest
                                val localFourKRequest = fourKRequest
                                stateManager.is4kRequest =
                                    localFourKRequest != null && localRegularRequest == null
                                Log.d(
                                    "MediaDetailsButtons",
                                    "enter: MANAGE_SINGLE resolved stateManager.is4kRequest=${stateManager.is4kRequest} (hd=${localRegularRequest != null}, 4k=${localFourKRequest != null})"
                                )
                                modalManager.openRequestAction(
                                    stateManager.currentFocusArea,
                                    stateManager.is4kRequest,
                                    null
                                )
                            }

                            FocusArea.TRAILER -> {
                                // Check if enough time has passed since screen navigation to prevent Enter key from previous screen
                                val currentTime = System.currentTimeMillis()
                                val timeSinceNavigation =
                                    currentTime - stateManager.lastScreenNavigationTime

                                if (timeSinceNavigation > 300) {
                                    Log.d(
                                        "MediaDetails",
                                        "✅ Enter key accepted for TRAILER (navigation: ${timeSinceNavigation}ms)"
                                    )
                                    triggerWatchTrailer()
                                } else {
                                    Log.d(
                                        "MediaDetails",
                                        "⏸️ Enter key ignored for TRAILER (navigation: ${timeSinceNavigation}ms < 300ms)"
                                    )
                                }
                            }

                            FocusArea.ISSUE -> {
                                // Check if enough time has passed since screen navigation to prevent Enter key from previous screen
                                val currentTime = System.currentTimeMillis()
                                val timeSinceNavigation =
                                    currentTime - stateManager.lastScreenNavigationTime

                                if (timeSinceNavigation > 300) {
                                    Log.d(
                                        "MediaDetails",
                                        "✅ Enter key accepted for ISSUE (navigation: ${timeSinceNavigation}ms)"
                                    )
                                    val issues = media.mediaInfo?.issues
                                    when {
                                        // If user can view and issues exist, show details
                                        canViewIssues && !issues.isNullOrEmpty() -> {
                                            modalManager.openIssueDetails(stateManager.currentFocusArea)
                                        }
                                        // If user can create, open report modal
                                        canCreateIssues -> {
                                            modalManager.openIssueReport(stateManager.currentFocusArea)
                                        }
                                        // This shouldn't happen if button visibility is correct, but handle gracefully
                                        else -> {
                                            Log.w("MediaDetails", "ISSUE button pressed but user lacks permissions")
                                        }
                                    }
                                } else {
                                    Log.d(
                                        "MediaDetails",
                                        "⏸️ Enter key ignored for ISSUE (navigation: ${timeSinceNavigation}ms < 300ms)"
                                    )
                                }
                            }

                            FocusArea.TAGS -> {
                                // Check if enough time has passed since screen navigation to prevent Enter key from previous screen
                                val currentTime = System.currentTimeMillis()
                                val timeSinceNavigation =
                                    currentTime - stateManager.lastScreenNavigationTime

                                if (timeSinceNavigation > 300) {
                                    val keyword =
                                        media.keywords?.getOrNull(stateManager.selectedTagIndex)
                                    if (keyword != null) {
                                        Log.d(
                                            "MediaDetails",
                                            "✅ Enter key accepted for TAGS (navigation: ${timeSinceNavigation}ms)"
                                        )
                                        val encodedKeywordText =
                                            java.net.URLEncoder.encode(keyword.name, "UTF-8")
                                        val type = when (media.mediaType) {
                                            MediaType.MOVIE -> "MOVIE_KEYWORDS"
                                            MediaType.TV -> "TV_KEYWORDS"
                                            else -> "SEARCH"
                                        }
                                        navigationManager.navigateToKeywordDiscovery(
                                            type = type,
                                            keywordId = keyword.id.toString(),
                                            keywordText = encodedKeywordText
                                        )
                                    }
                                } else {
                                    Log.d(
                                        "MediaDetails",
                                        "⏸️ Enter key ignored for TAGS (navigation: ${timeSinceNavigation}ms < 300ms)"
                                    )
                                }
                            }

                            FocusArea.FOURK_4K_OPTION -> {
                                // Add debouncing to prevent Enter key from being processed immediately after modal opens
                                val currentTime = System.currentTimeMillis()
                                val timeSinceModalOpen =
                                    currentTime - stateManager.last4kModalOpenTime

                                if (timeSinceModalOpen > 300) { // 300ms debouncing for 4K modal (increased for OS key clearing)
                                    Log.d(
                                        "MediaDetails",
                                        "🎯 FOURK_4K_OPTION selected - closing modal, setting stateManager.is4kRequest=true and triggering request (debounced: ${timeSinceModalOpen}ms)"
                                    )
                                    stateManager.is4kRequest = true
                                    // Open request modal directly for 4K
                                    modalManager.openRequest(
                                        stateManager.currentFocusArea,
                                        true,
                                        null
                                    )
                                } else {
                                    Log.d(
                                        "MediaDetails",
                                        "⏸️ FOURK_4K_OPTION Enter key ignored - too soon after modal open (${timeSinceModalOpen}ms)"
                                    )
                                }
                            }

                            FocusArea.FOURK_REGULAR_OPTION -> {
                                // Add debouncing to prevent Enter key from being processed immediately after modal opens
                                val currentTime = System.currentTimeMillis()
                                val timeSinceModalOpen =
                                    currentTime - stateManager.last4kModalOpenTime

                                if (timeSinceModalOpen > 300) { // 300ms debouncing for 4K modal (increased for OS key clearing)
                                    Log.d(
                                        "MediaDetails",
                                        "🎯 FOURK_REGULAR_OPTION selected - closing modal, setting stateManager.is4kRequest=false and triggering request (debounced: ${timeSinceModalOpen}ms)"
                                    )
                                    stateManager.is4kRequest = false
                                    // Open request modal directly for regular tier
                                    modalManager.openRequest(
                                        stateManager.currentFocusArea,
                                        false,
                                        null
                                    )
                                } else {
                                    Log.d(
                                        "MediaDetails",
                                        "⏸️ FOURK_REGULAR_OPTION Enter key ignored - too soon after modal open (${timeSinceModalOpen}ms)"
                                    )
                                }
                            }

                            FocusArea.SIMILAR_MEDIA -> {
                                // Check if enough time has passed since screen navigation to prevent Enter key from previous screen
                                val currentTime = System.currentTimeMillis()
                                val timeSinceNavigation =
                                    currentTime - stateManager.lastScreenNavigationTime

                                if (timeSinceNavigation > 300) {
                                    Log.d(
                                        "MediaDetails",
                                        "✅ Enter key accepted for SIMILAR_MEDIA (navigation: ${timeSinceNavigation}ms)"
                                    )
                                    val selectedMedia =
                                        stateManager.similarMediaItems.getOrNull(stateManager.selectedSimilarMediaIndex)
                                    if (selectedMedia != null) {
                                        // Ensure AppFocusManager is synced with SimilarMedia focus before navigation
                                        appFocusManager.setFocus(AppFocusState.DetailsScreen(DetailsFocusState.SimilarMedia))
                                        // Debug logging removed to reduce instruction count
                                        
                                        // Save return state before navigating to similar media details
                                        // Note: scrollOffset was already saved by auto-scroll logic, don't override it
                                        // Debug logging removed to reduce instruction count
                                        returnState = returnState.copy(
                                            isPending = true,
                                            focusArea = FocusArea.SIMILAR_MEDIA,
                                            activeCarouselIndex = stateManager.selectedSimilarMediaIndex
                                            // scrollOffset already saved by auto-scroll logic
                                        )
                                        navigationManager.navigateToDetails(
                                            selectedMedia.id.toString(),
                                            selectedMedia.mediaType
                                        )
                                    }
                                } else {
                                    Log.d(
                                        "MediaDetails",
                                        "⏸️ Enter key ignored for SIMILAR_MEDIA (navigation: ${timeSinceNavigation}ms < 300ms)"
                                    )
                                }
                            }

                            else -> false
                        }
                    },
                    onBack = {
                        // Debounce global Back handling immediately after an issue modal closes
                        val now = System.currentTimeMillis()
                        if (now - stateManager.lastIssueModalCloseTime < 600) {
                            return@createMediaDetailsDpadConfig
                        }
                        // Also ignore back if a modal just received Back key down to prevent race during close
                        if (now - stateManager.lastIssueBackKeyTime < 600) {
                            return@createMediaDetailsDpadConfig
                        }
                        // Don't handle BACK for issue modals - let them handle it themselves
                        // Only handle BACK for the main MediaDetails screen
                        if (!modalManager.showIssueReport && !modalManager.showIssueDetails) {
                            // Clear AppFocusManager state when navigating back to MainScreen
                            // This prevents unwanted state restoration when returning to MediaDetails from MainScreen
                            appFocusManager.clearSavedStates()
                            // Debug logging removed to reduce instruction count
                            navController.popBackStack()
                        }
                        // Note: Issue modals handle their own BACK button events via BackHandler
                    }
                )

                LaunchedEffect(dpadConfig) {
                    dpadController.registerScreen(dpadConfig)
                }

                // Set initial focus/scroll ONLY on first entry (not when returning from PersonScreen)
                LaunchedEffect(isFirstEntry, actionButtonStates, buttonFocusOrder) {
                    if (!isFirstEntry) return@LaunchedEffect
                    if (returnState.isPending) return@LaunchedEffect
                    if (hasJustRestored) return@LaunchedEffect
                    if (initialFocusRestored) {
                        // Debug logging removed to reduce instruction count
                        return@LaunchedEffect
                    }
                    // Initial focus selection when nothing is highlighted: prefer topmost action -> topmost carousel -> overview
                    if (stateManager.currentFocusArea == FocusArea.OVERVIEW ||
                        stateManager.currentFocusArea == FocusArea.NONE) {
                        when {
                            // 1) Top-most action button (left half if split is already first in order)
                            buttonFocusOrder.isNotEmpty() -> {
                                val topButton = buttonFocusOrder.first()
                                stateManager.currentFocusArea = topButton
                                // Debug logging removed to reduce instruction count
                            }
                            // 2) First available carousel (top-most): CAST -> CREW -> SIMILAR_MEDIA
                            stateManager.hasCast -> {
                                stateManager.currentFocusArea = FocusArea.CAST
                                stateManager.selectedCastIndex = 0
                                // Debug logging removed to reduce instruction count
                            }
                            stateManager.hasCrew -> {
                                stateManager.currentFocusArea = FocusArea.CREW
                                stateManager.selectedCrewIndex = 0
                                // Debug logging removed to reduce instruction count
                            }
                            stateManager.hasSimilarMedia -> {
                                stateManager.currentFocusArea = FocusArea.SIMILAR_MEDIA
                                stateManager.selectedSimilarMediaIndex = 0
                                // Debug logging removed to reduce instruction count
                            }
                            // 3) Fallback to overview
                            else -> {
                                stateManager.currentFocusArea = FocusArea.OVERVIEW
                                // Debug logging removed to reduce instruction count
                            }
                        }
                    }

                    // Allow one brief correction pass for capability-driven UI changes (e.g., split request)
                    // This ensures focus remains on the top-most visible action after recompute
                    delay(150)
                    val visibleActionsPost = buttonFocusOrder.toSet()
                    val isRequestNowSplit = actionButtonStates["request"]?.isSplit == true
                    if (
                        (stateManager.currentFocusArea == FocusArea.REQUEST_SINGLE && isRequestNowSplit) ||
                        !visibleActionsPost.contains(stateManager.currentFocusArea)
                    ) {
                        buttonFocusOrder.firstOrNull()?.let { correctedTop ->
                            stateManager.currentFocusArea = correctedTop
                            // Debug logging removed to reduce instruction count
                        }
                    }

                    // Scroll to top on first entry
                    scrollState.scrollTo(0)
                    isFirstEntry = false
                }

                LaunchedEffect(stateManager.mediaPlayerTrigger) {
                    if (stateManager.mediaPlayerTrigger > 0) {
                        handleMediaPlayerTrigger()
                    }
                }

                // Backdrop
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121827)) // Fallback background color
                ) {
                    // Backdrop image
                    if (!media.backdropPath.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("https://image.tmdb.org/t/p/original${media.backdropPath}")
                                .build(),
                            contentDescription = null,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF121827).copy(alpha = 0.65f),
                                        Color(0xFF121827).copy(alpha = 0.85f),
                                        Color(0xFF121827).copy(alpha = 1.0f),
                                        Color(0xFF121827)
                                    ),
                                    startY = 0f,
                                    endY = 1.5f * 1000f // Adjust this value to fine-tune the gradient end point
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 50.dp) // Push content below the top bar
                            .verticalScroll(scrollState)
                            .focusRequester(focusRequester)
                            .focusable()
                            .onGloballyPositioned { stateManager.isCompositionReady = true }
                    ) {
                        // Load server configuration when the screen loads to enable 4K capability detection
                        LaunchedEffect(media.mediaType) {
                            if (returnState.isPending) return@LaunchedEffect
                            if (hasJustRestored) return@LaunchedEffect
                            media.mediaType?.let { mediaType ->
                                // Record the time when this screen loads to prevent Enter key events from previous screen
                                stateManager.lastScreenNavigationTime = System.currentTimeMillis()
                                Log.d(
                                    "MediaDetails",
                                    "🔄 Loading server configuration for media type: $mediaType"
                                )
                                viewModel.getDataForMediaType(mediaType.toString())

                                // Wait a bit for the data to load and then log the result
                                delay(100)
                                when (mediaType) {
                                    MediaType.MOVIE -> {
                                        val radarrServers = radarrData?.allServers
                                        Log.d(
                                            "MediaDetails",
                                            "📡 RADARR servers loaded: ${radarrServers?.size ?: 0} servers, 4K capable: ${radarrServers?.any { it.server.is4k } ?: false}")
                                    }

                                    MediaType.TV -> {
                                        val sonarrServers = sonarrData?.allServers
                                        Log.d(
                                            "MediaDetails",
                                            "📡 SONARR servers loaded: ${sonarrServers?.size ?: 0} servers, 4K capable: ${sonarrServers?.any { it.server.is4k } ?: false}")
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                        ) {
                            // Main content (poster, overview, info table)
                            Row(modifier = Modifier.fillMaxWidth()) {
                                // Left column: Poster and Request button
                                Column(modifier = Modifier.width(200.dp)) {
                                    // Poster
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f)
                                    ) {
                                        val mediaDetails = media
                                        val mediaForCard = remember(mediaDetails) {
                                            Media(
                                                id = mediaDetails.id,
                                                mediaType = mediaDetails.mediaType?.name?.lowercase()
                                                    ?: "",
                                                title = mediaDetails.title ?: "",
                                                name = mediaDetails.name ?: "",
                                                posterPath = mediaDetails.posterPath ?: "",
                                                backdropPath = mediaDetails.backdropPath ?: "",
                                                overview = mediaDetails.overview,
                                                mediaInfo = mediaDetails.mediaInfo?.copy() // Create a copy to avoid reference issues
                                            )
                                        }
                                        MediaCard(
                                            mediaContent = mediaForCard,
                                            context = context,
                                            imageLoader = imageLoader,
                                            isSelected = false,
                                            cardWidth = 200.dp,
                                            cardHeight = 300.dp,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Action buttons section
                                    MediaDetailsActionButtons(
                                        media = media,
                                        viewModel = viewModel,
                                        currentFocusArea = stateManager.currentFocusArea,
                                        actionButtonStates = actionButtonStates,
                                        canRequestMedia = canRequestMedia,
                                        has4kCapability = has4kCapability,
                                        onFocusChange = { newFocus ->
                                            stateManager.currentFocusArea = newFocus
                                        },
                                        context = context
                                    )

                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                // Middle and Right columns: Overview and Info Table
                                MediaDetailsContentLayout(
                                    media = media,
                                    viewModel = viewModel,
                                    currentFocusArea = stateManager.currentFocusArea,
                                    isFullOverviewShown = stateManager.isFullOverviewShown,
                                    onToggleFullOverview = { stateManager.isFullOverviewShown = it },
                                    hasTags = stateManager.hasTags,
                                    statusInfo = stateManager.statusInfo,
                                    has4kCapability = has4kCapability,
                                    hasReadMoreButton = stateManager.hasReadMoreButton,
                                    onLeftmostTagsUpdated = { stateManager.leftmostTags = it },
                                    onTagPositionsUpdated = { 
                                        stateManager.tagPositions = it
                                        if (BuildConfig.DEBUG) {
                                            Log.d(
                                                "MediaDetails",
                                                "🏷️ Tag positions updated: ${it.size} positions, current focus: $stateManager.currentFocusArea"
                                            )
                                        }
                                    },
                                    selectedTagIndex = stateManager.selectedTagIndex,
                                    context = context,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Cast, Crew, and Similar Media Carousels
                        MediaDetailsCarousels(
                            media = media,
                            viewModel = viewModel,
                            imageLoader = imageLoader,
                            currentFocusArea = stateManager.currentFocusArea,
                            hasCast = stateManager.hasCast,
                            hasCrew = stateManager.hasCrew,
                            hasSimilarMedia = stateManager.hasSimilarMedia,
                            similarMediaItems = stateManager.similarMediaItems,
                            selectedCastIndex = stateManager.selectedCastIndex,
                            selectedCrewIndex = stateManager.selectedCrewIndex,
                            selectedSimilarMediaIndex = stateManager.selectedSimilarMediaIndex,
                            onCastCountUpdate = { stateManager.castCount = it },
                            onCrewCountUpdate = { stateManager.crewCount = it },
                            navigationManager = navigationManager,
                            coroutineScope = coroutineScope,
                            context = context
                        )
                    }
                }
                // Scroll the screen to keep the highlighted area visible
                LaunchedEffect(
                    stateManager.currentFocusArea,
                    stateManager.selectedTagIndex,
                    stateManager.compositionTrigger,
                    stateManager.isCompositionReady
                ) {
                    if (!stateManager.isCompositionReady) return@LaunchedEffect
                    // Skip auto-scroll during state restoration to preserve manually restored scroll position
                    if (returnState.isPending || hasJustRestored) return@LaunchedEffect
                    when (stateManager.currentFocusArea) {
                        // Top regions and all action buttons should keep the view near the top
                        FocusArea.OVERVIEW,
                        FocusArea.READ_MORE,
                        FocusArea.ISSUE -> {
                            scrollState.animateScrollTo(0)
                        }

                        // Action buttons: center vertically within reasonable bounds based on button index
                        FocusArea.PLAY,
                        FocusArea.REQUEST_HD,
                        FocusArea.REQUEST_4K,
                        FocusArea.REQUEST_SINGLE,
                        FocusArea.MANAGE_HD,
                        FocusArea.MANAGE_4K,
                        FocusArea.MANAGE_SINGLE,
                        FocusArea.TRAILER,
                            -> {
                            // Do not auto-scroll when focusing the 4K half of the split Request button
                            if (stateManager.currentFocusArea == FocusArea.REQUEST_4K) {
                                return@LaunchedEffect
                            }
                            val visibleButtons = buildList {
                                if (actionButtonStates["play"]?.isVisible == true) add(FocusArea.PLAY)
                                if (actionButtonStates["request"]?.isVisible == true) {
                                    if (actionButtonStates["request"]?.isSplit == true) {
                                        add(FocusArea.REQUEST_HD)
                                        add(FocusArea.REQUEST_4K)
                                    } else {
                                        add(FocusArea.REQUEST_SINGLE)
                                    }
                                }
                                if (actionButtonStates["manage"]?.isVisible == true) {
                                    if (actionButtonStates["manage"]?.isSplit == true) {
                                        add(FocusArea.MANAGE_HD)
                                        add(FocusArea.MANAGE_4K)
                                    } else {
                                        add(FocusArea.MANAGE_SINGLE)
                                    }
                                }
                                if (actionButtonStates["trailer"]?.isVisible == true) add(FocusArea.TRAILER)
                            }
                            val idx = visibleButtons.indexOf(stateManager.currentFocusArea)
                                .coerceAtLeast(0)
                            if (idx == 0) {
                                // Keep top-most button at top so title and issue button remain visible
                                scrollState.animateScrollTo(0)
                            } else {
                                // Scroll to a more conservative position to keep action buttons visible
                                val target = (scrollState.maxValue * 0.2f).toInt()
                                    .coerceIn(0, scrollState.maxValue)
                                scrollState.animateScrollTo(target)
                            }
                        }

                        // Tags: ensure the selected tag is visible with proper bounds checking
                        FocusArea.TAGS -> {
                            if (BuildConfig.DEBUG) {
                                Log.d(
                                    "MediaDetails",
                                    "🏷️ TAGS focus triggered - selectedIndex: ${stateManager.selectedTagIndex}, tagPositions: ${stateManager.tagPositions.size}, hasTags: ${stateManager.hasTags}, currentScroll: ${scrollState.value}, maxScroll: ${scrollState.maxValue}"
                                )
                            }
                            
                            val selectedY = stateManager.tagPositions
                                .firstOrNull { it.first == stateManager.selectedTagIndex }
                                ?.second?.toInt()
                            if (selectedY != null) {
                                // Adjust tag position to account for current scroll position and content offset
                                // Tags are positioned after the action buttons and overview, so we need to add that offset
                                val contentOffset = 400 // Approximate offset for poster, action buttons, and overview
                                val adjustedTagY = selectedY + contentOffset
                                // Calculate the viewport height based on actual screen dimensions
                                // Android TV typically has 1080p resolution, minus top bar (50dp) and padding
                                // This gives us approximately 900-950 pixels of visible content height
                                val viewportHeight = 900f // Fixed viewport height for consistent scrolling behavior
                                val currentScroll = scrollState.value
                                val viewportTop = currentScroll
                                val viewportBottom = currentScroll + viewportHeight
                                
                                // Check if the tag is outside the viewport
                                val tagTop = adjustedTagY
                                val tagBottom = adjustedTagY + 60 // More accurate tag height (padding + text + icon)
                                
                                val targetScroll = when {
                                    // Tag is above viewport - scroll up to show it
                                    tagTop < viewportTop -> {
                                        // Position tag near top of viewport with some padding
                                        (tagTop - 120).coerceAtLeast(0) // Increased padding from 100 to 120
                                    }
                                    // Tag is below viewport - scroll down to show it
                                    tagBottom > viewportBottom -> {
                                        // Position tag near bottom of viewport with some padding
                                        (tagBottom - viewportHeight + 120).coerceAtMost(scrollState.maxValue.toFloat()).toInt() // Increased padding from 100 to 120
                                    }
                                    // Tag is already visible - no need to scroll
                                    else -> currentScroll
                                }
                                
                                // Only scroll if we need to move
                                if (targetScroll != currentScroll) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(
                                            "MediaDetails",
                                            "🏷️ Scrolling to tag ${stateManager.selectedTagIndex}: current=$currentScroll, target=$targetScroll, tagY=$selectedY->$adjustedTagY, viewport=$viewportTop-$viewportBottom"
                                        )
                                    }
                                    scrollState.animateScrollTo(targetScroll)
                                } else {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(
                                            "MediaDetails",
                                            "🏷️ Tag ${stateManager.selectedTagIndex} already visible at Y=$selectedY->$adjustedTagY, viewport=$viewportTop-$viewportBottom, currentScroll=$currentScroll, no scrolling needed"
                                        )
                                    }
                                }
                            } else {
                                // Fallback: if we can't find the tag position, scroll to show tags section
                                // This is approximately where tags start (after action buttons and overview)
                                val tagsSectionStart = (scrollState.maxValue * 0.3f).toInt()
                                    .coerceIn(0, scrollState.maxValue)
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "MediaDetails",
                                        "🏷️ Tag position not found for index ${stateManager.selectedTagIndex}, scrolling to tags section at $tagsSectionStart"
                                    )
                                }
                                scrollState.animateScrollTo(tagsSectionStart)
                            }
                        }

                        // Cast and Crew live below; scroll to approximate sections
                        FocusArea.CAST -> {
                            val target = (scrollState.maxValue * 0.65f).toInt()
                                .coerceIn(0, scrollState.maxValue)
                            scrollState.animateScrollTo(target)
                        }

                            FocusArea.CREW -> {
                                if (stateManager.hasSimilarMedia) {
                                    val target = (scrollState.maxValue * 0.75f).toInt()
                                        .coerceIn(0, scrollState.maxValue)
                                    scrollState.animateScrollTo(target)
                                    // Save the scroll position after auto-scroll completes
                                    if (!returnState.isPending) {
                                        returnState = returnState.copy(
                                            scrollOffset = target
                                        )
                                        // Debug logging removed to reduce instruction count
                                    }
                                } else {
                                    // If Similar section is not visible, crew is the last section
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                    // Save the scroll position after auto-scroll completes
                                    if (!returnState.isPending) {
                                        returnState = returnState.copy(
                                            scrollOffset = scrollState.maxValue
                                        )
                                        // Debug logging removed to reduce instruction count
                                    }
                                }
                            }

                        // Similar media is at the bottom
                        FocusArea.SIMILAR_MEDIA -> {
                            scrollState.animateScrollTo(scrollState.maxValue)
                            // Save the scroll position after auto-scroll completes
                            if (!returnState.isPending) {
                                returnState = returnState.copy(
                                    scrollOffset = scrollState.maxValue
                                )
                                // Debug logging removed to reduce instruction count
                            }
                        }

                        else -> { /* no-op */
                        }
                    }
                }

                if (modalManager.showRequestModal && mediaDetailsState is ApiResult.Success) {
                    // Get the RequestViewModel
                    val requestViewModel: RequestViewModel = hiltViewModel()

                    // Set up the connection to the main ViewModel
                    requestViewModel.setSeerrViewModel(viewModel)
                    Log.d(
                        "MediaDetails",
                        "🔗 Connected RequestViewModel to SeerrViewModel for direct refresh"
                    )

                    // Get the current 4K request state
                    val currentIs4kRequest = stateManager.is4kRequest

                    // Check if modal is needed using the new auto-request system
                    val shouldShowModal = requestViewModel.shouldShowModal(
                        mediaDetails = (mediaDetailsState as ApiResult.Success<MediaDetails>).data,
                        is4kRequest = currentIs4kRequest,
                        isFolderSelectionEnabled = SharedPreferencesUtil.isFolderSelectionEnabled(
                            context
                        )
                    )

                    if (shouldShowModal) {
                        // Show modal when user input is needed
                    RequestModal(
                        context = context,
                        mediaDetails = (mediaDetailsState as ApiResult.Success<MediaDetails>).data,
                        mainViewModel = viewModel,
                        onCancel = {
                            modalManager.closeRequest()
                            // Restore focus to request button when modal closes
                            stateManager.currentFocusArea =
                                modalManager.lastFocusedArea.takeIf { it != -1 }
                                    ?: buttonFocusOrder.find {
                                        it == FocusArea.REQUEST_HD || it == FocusArea.REQUEST_SINGLE ||
                                                it == FocusArea.MANAGE_HD || it == FocusArea.MANAGE_SINGLE
                                    } ?: FocusArea.OVERVIEW
                        },
                        onRequest = {
                            modalManager.closeRequest()
                            // Restore focus to request button when modal closes
                            stateManager.currentFocusArea =
                                modalManager.lastFocusedArea.takeIf { it != -1 }
                                    ?: buttonFocusOrder.find {
                                        it == FocusArea.REQUEST_HD || it == FocusArea.REQUEST_SINGLE ||
                                                it == FocusArea.MANAGE_HD || it == FocusArea.MANAGE_SINGLE
                                    } ?: FocusArea.OVERVIEW
                            refreshManager.handleRequestSuccess(
                                context = context,
                                viewModel = viewModel,
                                mediaId = mediaId,
                                mediaType = mediaType,
                                mediaDetailsState = mediaDetailsState,
                                setMessage = { showMessage = it }
                            )
                        },
                        viewModel = requestViewModel,
                        navigationManager = navigationManager,
                        is4kRequest = currentIs4kRequest,
                        imageLoader = imageLoader
                    )
                    } else {
                        // Auto-submit the request directly when no choices are needed
                        LaunchedEffect(Unit) {
                            // Debug logging removed to reduce instruction count
                            requestViewModel.submitRequestDirectly(
                                mediaDetails = (mediaDetailsState as ApiResult.Success<MediaDetails>).data,
                                is4kRequest = currentIs4kRequest,
                                onSuccess = {
                                    modalManager.closeRequest()
                                    // Restore focus to request button when modal closes
                                    stateManager.currentFocusArea =
                                        modalManager.lastFocusedArea.takeIf { it != -1 }
                                            ?: buttonFocusOrder.find {
                                                it == FocusArea.REQUEST_HD || it == FocusArea.REQUEST_SINGLE ||
                                                        it == FocusArea.MANAGE_HD || it == FocusArea.MANAGE_SINGLE
                                            } ?: FocusArea.OVERVIEW

                                    refreshManager.handleAutoRequestSuccess(
                                        context = context,
                                        viewModel = viewModel,
                                        mediaId = mediaId,
                                        mediaType = mediaType,
                                        mediaDetailsState = mediaDetailsState,
                                        setMessage = { showMessage = it }
                                    )
                                },
                                onError = { error ->
                                    Log.e("MediaDetails", "Auto-request failed: $error")
                                    showMessage = context.getString(R.string.mediaDetails_errorSubmittingRequest, error)
                                    // Restore focus to request button when auto-request fails
                                    stateManager.currentFocusArea =
                                        modalManager.lastFocusedArea.takeIf { it != -1 }
                                            ?: buttonFocusOrder.find {
                                                it == FocusArea.REQUEST_HD || it == FocusArea.REQUEST_SINGLE ||
                                                        it == FocusArea.MANAGE_HD || it == FocusArea.MANAGE_SINGLE
                                            } ?: FocusArea.OVERVIEW
                                }
                            )
                        }
                    }
                }

                // Handle the RequestActionModal with a stable approach
                val mediaDetailsData = (mediaDetailsState as? ApiResult.Success<MediaDetails>)?.data

                // Use mutableStateOf to properly handle state updates
                var requestActionState by remember(
                    mediaDetailsData?.id,
                    modalManager.showRequestActionModal,
                    stateManager.is4kRequest
                ) {
                    mutableStateOf(
                        if (modalManager.showRequestActionModal && mediaDetailsData != null) {
                            // Determine which request to show based on the current context
                            // If user just made a 4K request, show the 4K request; otherwise show any available request
                            val request = if (stateManager.is4kRequest) {
                                // User is in 4K context, try to get 4K request first
                                viewModel.getRequestForMedia(mediaDetailsData.id, true)
                                    ?: viewModel.getRequestForMedia(mediaDetailsData.id, false)
                            } else {
                                // User is in regular context, try to get regular request first
                                viewModel.getRequestForMedia(mediaDetailsData.id, false)
                                    ?: viewModel.getRequestForMedia(mediaDetailsData.id, true)
                            }

                            if (request != null) {
                                RequestActionState(
                                    isVisible = true,
                                    request = request,
                                    requestId = request.id
                                )
                            } else null
                        } else null
                    )
                }

                // Handle modal dismissal in a way that prevents recompositions from reopening it
                LaunchedEffect(requestActionState?.isVisible) {
                    if (requestActionState?.isVisible == false) {
                        modalManager.closeRequestAction()
                        // Restore focus back to where the user was before opening the modal
                        stateManager.currentFocusArea = modalManager.lastFocusedArea
                        // Focus restoration is handled by setting currentFocusArea above
                    }
                }

                // Render the RequestActionModal only when we have a valid state
                if (requestActionState?.isVisible == true) {
                    Log.d(
                        "MediaDetails",
                        "Rendering RequestActionModal for request ${requestActionState?.request?.id}"
                    )

                    // Memoize the initialMediaDetails to prevent recompositions
                    val stableInitialMediaDetails = remember(mediaDetailsData) {
                        mediaDetailsState as? ApiResult.Success<MediaDetails>
                    }

                    RequestActionModal(
                        isVisible = requestActionState?.isVisible == true,
                        request = requestActionState!!.request,
                        imageLoader = imageLoader,
                        onDismiss = {
                            // Debug logging removed to reduce instruction count
                            requestActionState = requestActionState?.copy(isVisible = false)
                            modalManager.closeRequestAction()
                        },
                        onDelete = {
                            Log.d(
                                "MediaDetails",
                                "Request delete action received for ${requestActionState?.request?.id}"
                            )

                            // Don't immediately close the modal - let RequestActionModal handle its own dismissal
                            // The modal will call onDismiss after the deletion is complete

                            // Execute the deletion
                            coroutineScope.launch {
                                val requestId = requestActionState?.request?.id ?: 0
                                // Debug logging removed to reduce instruction count

                                // Delete the request
                                viewModel.deleteRequest(requestId)

                                // Give the backend time to process the deletion
                                delay(1000)

                                // Refresh data after deletion
                                refreshManager.handleRequestDeleteSuccess(
                                    viewModel = viewModel,
                                    mediaId = mediaId,
                                    mediaType = mediaType
                                )

                                // Now dismiss the modal after the deletion and refresh are complete
                                delay(500) // Small delay to ensure UI updates are processed
                                requestActionState = requestActionState?.copy(isVisible = false)
                                modalManager.closeRequestAction()

                                Log.d(
                                    "MediaDetails",
                                    "Request deletion completed, UI refreshed, and modal dismissed"
                                )
                            }
                        },
                        onMediaClick = { mediaId, mediaType ->
                            // Debug logging removed to reduce instruction count
                            requestActionState = requestActionState?.copy(isVisible = false)
                            modalManager.closeRequestAction()

                            // Add a small delay before navigation to avoid UI conflicts
                            coroutineScope.launch {
                                delay(100)
                                navController.navigate("details/$mediaId/$mediaType") {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        showDetailsButton = false,
                        canDeleteRequest = remember(requestActionState?.request) {
                            val request = requestActionState?.request
                            if (request != null) {
                                val currentUserPermissions =
                                    viewModel.getCurrentUserPermissions() ?: 0
                                CommonUtil.canDeleteRequest(
                                    userPermissions = currentUserPermissions,
                                    isRequestor = viewModel.getCurrentUserId() == request.requestedBy.id,
                                    isRequestPending = request.status == 1
                                )
                            } else {
                                false
                            }
                        },
                        viewModel = viewModel,
                        initialMediaDetails = stableInitialMediaDetails
                    )
                }


            }

            is ApiResult.Error -> {
                // Check if it's an authentication error (403)
                if (details.exception.message?.contains("403") == true ||
                    details.exception.message?.contains("permission") == true
                ) {
                    AuthenticationErrorHandler(
                        isVisible = true,
                        onRetry = {
                            // Hide any existing error state
                            viewModel.hideAuthenticationError()
                            // Force a refresh of the media details
                            viewModel.forceRefreshMediaDetails(mediaId, mediaType)
                            // Set needs refresh flag
                            viewModel.setNeedsRefresh(true)
                        },
                        onReconfigure = {
                            if (!stateManager.isNavigating) {
                                stateManager.isNavigating = true
                                // Clear config and navigate
                                SharedPreferencesUtil.clearConfig(context)
                                navController.navigate("config") {
                                    popUpTo(navController.graph.startDestinationId) {
                                        inclusive = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                    )
                } else {
                    // For other errors, show the error message
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = details.exception.localizedMessage ?: context.getString(R.string.mediaDetails_unknownError),
                            color = Color.Red,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is ApiResult.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        // Display the message if showMessage is not null
        showMessage?.let { message ->
            TVMessage(
                message = message,
                onDismiss = {
                    showMessage = null
                }
            )
        }

        // Trailer overlay: fullscreen Dialog so it covers the top bar; in-app WebView when user enabled "In-app player" in Settings
        stateManager.trailerOverlayVideoId?.let { videoId ->
            Dialog(
                onDismissRequest = { stateManager.trailerOverlayVideoId = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true
                )
            ) {
                TrailerOverlay(
                    videoId = videoId,
                    onClose = { stateManager.trailerOverlayVideoId = null }
                )
            }
        }
    }
}