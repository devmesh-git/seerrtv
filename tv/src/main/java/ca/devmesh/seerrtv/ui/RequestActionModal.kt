package ca.devmesh.seerrtv.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.Request
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.ui.components.ActionButton
import ca.devmesh.seerrtv.ui.components.AutoUpdatingHumanizedDate
import ca.devmesh.seerrtv.ui.components.MediaCard
import ca.devmesh.seerrtv.ui.components.MediaDownloadStatus
import ca.devmesh.seerrtv.ui.components.RequestStatus
import ca.devmesh.seerrtv.util.CommonUtil
import ca.devmesh.seerrtv.util.Permission
import ca.devmesh.seerrtv.viewmodel.MediaCategory
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel
import coil3.ImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Modal component for request actions
 */
@Composable
fun RequestActionModal(
    isVisible: Boolean,
    request: Request,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onMediaClick: (String, String) -> Unit,
    canDeleteRequest: Boolean = false,
    viewModel: SeerrViewModel? = null,
    showDetailsButton: Boolean = true,
    initialMediaDetails: ApiResult<MediaDetails>? = null,
    // Dual-tier support
    regularRequest: Request? = null,
    fourKRequest: Request? = null
) {
    // Basic state for the modal
    var currentFocus by remember { mutableStateOf<FocusState>(FocusState.CancelButton) }
    var uiVisibleState by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    
    // Focus tracking
    val focusRequester = remember { FocusRequester() }
    val focusRequestedState = remember { mutableStateOf(false) }
    
    // Confirmation states for dangerous actions
    var approveConfirmationActive by remember { mutableStateOf(false) }
    var declineConfirmationActive by remember { mutableStateOf(false) }
    var deleteConfirmationActive by remember { mutableStateOf(false) }
    var adminDeleteConfirmationActive by remember { mutableStateOf(false) }
    
    // Jobs for timing out confirmations
    var approveConfirmationJob by remember { mutableStateOf<Job?>(null) }
    var declineConfirmationJob by remember { mutableStateOf<Job?>(null) }
    var deleteConfirmationJob by remember { mutableStateOf<Job?>(null) }
    var adminDeleteConfirmationJob by remember { mutableStateOf<Job?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    

    
    // Effect to handle opening and closing animation
    LaunchedEffect(isVisible) {
        if (isVisible) {
            Log.d("RequestActionModal", "🎭 Modal becoming visible for request ${request.id}")
            // Set UI visible for animation purposes
            uiVisibleState = true
            
            // Reset focus state when becoming visible
            focusRequestedState.value = false
            currentFocus = FocusState.CancelButton
            
            // Request focus after a delay to ensure UI is fully composed
            delay(500)
            
            // Only request focus if the modal is still visible
            if (uiVisibleState) {
                try {
                    Log.d("RequestActionModal", "🔍 Requesting focus for modal")
                    focusRequester.requestFocus()
                    focusRequestedState.value = true
                    Log.d("RequestActionModal", "✅ Focus requested successfully")
                } catch (e: Exception) {
                    Log.e("RequestActionModal", "❌ Failed to request focus: ${e.message}", e)
                    
                    // Retry focus request after a short delay if it failed
                    delay(300)
                    try {
                        Log.d("RequestActionModal", "🔄 Retrying focus request")
                        focusRequester.requestFocus()
                        focusRequestedState.value = true
                        Log.d("RequestActionModal", "✅ Focus request retry successful")
                    } catch (e: Exception) {
                        Log.e("RequestActionModal", "❌ Focus request retry failed: ${e.message}", e)
                    }
                }
            }
        } else {
            Log.d("RequestActionModal", "🎭 Modal becoming invisible")
            // Animation handled by the DialogBox
            uiVisibleState = false
            
            // Reset states when modal is dismissed
            currentFocus = FocusState.CancelButton
            focusRequestedState.value = false
            
            // Reset confirmation states
            approveConfirmationActive = false
            declineConfirmationActive = false
            deleteConfirmationActive = false
            adminDeleteConfirmationActive = false
            
            // Cancel confirmation jobs
            approveConfirmationJob?.cancel()
            declineConfirmationJob?.cancel()
            deleteConfirmationJob?.cancel()
            adminDeleteConfirmationJob?.cancel()
            
            approveConfirmationJob = null
            declineConfirmationJob = null
            deleteConfirmationJob = null
            adminDeleteConfirmationJob = null
        }
    }
    
    // Add a separate effect to handle focus requests
    LaunchedEffect(uiVisibleState) {
        if (uiVisibleState && !focusRequestedState.value) {
            // Wait for UI to be fully composed
            delay(800)
            
            // Only request focus if the modal is still visible
            if (uiVisibleState) {
                try {
                    Log.d("RequestActionModal", "🔍 Secondary focus request for modal")
                    focusRequester.requestFocus()
                    focusRequestedState.value = true
                    Log.d("RequestActionModal", "✅ Secondary focus request successful")
                } catch (e: Exception) {
                    Log.e("RequestActionModal", "❌ Secondary focus request failed: ${e.message}", e)
                }
            }
        }
    }
    
    // Add LaunchedEffect to handle scrolling based on focus state
    val scrollState = rememberScrollState()
    LaunchedEffect(currentFocus) {
        when (currentFocus) {
            // First row buttons or media card - scroll to top
            FocusState.MainMenu(-1), FocusState.CancelButton, FocusState.DetailsButton -> {
                coroutineScope.launch {
                    scrollState.animateScrollTo(0)
                }
            }
            // Second row buttons - scroll to middle
            FocusState.ApproveButton, FocusState.DeclineButton -> {
                coroutineScope.launch {
                    // Scroll to a position that ensures these buttons are visible
                    val middlePosition = (scrollState.maxValue * 0.6f).toInt().coerceAtMost(scrollState.maxValue)
                    scrollState.animateScrollTo(middlePosition)
                }
            }
            // Third row buttons - scroll to bottom
            FocusState.DeleteButton, FocusState.AdminDeleteButton -> {
                coroutineScope.launch {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
            else -> {} // Do nothing for other states
        }
    }
    
    // Only render the UI when visible to reduce composition costs
    if (!isVisible) {
        return
    }

    val context = LocalContext.current

    val isAdmin = remember(viewModel) {
        val permissions = viewModel?.getCurrentUserPermissions() ?: 0
        val isAdmin = (permissions and 2) != 0
        Log.d("RequestActionModal", "👤 User permissions: $permissions, isAdmin: $isAdmin")
        isAdmin
    }
    val isJellyseerr = remember(viewModel) {
        val serverType = viewModel?.getServerType()
        val isJellyseerr = serverType == SeerrApiService.ServerType.JELLYSEERR || serverType == SeerrApiService.ServerType.SEERR
        Log.d("RequestActionModal", "🖥️ Server type: $serverType, isJellyseerr: $isJellyseerr")
        isJellyseerr
    }

    // Safely collect states 
    val radarrData = viewModel?.radarrData?.collectAsState()?.value
    val sonarrData = viewModel?.sonarrData?.collectAsState()?.value

    // Log server data
    LaunchedEffect(radarrData, sonarrData) {
        Log.d("RequestActionModal", "📊 Radarr servers: ${radarrData?.allServers?.size ?: 0}")
        Log.d("RequestActionModal", "📊 Sonarr servers: ${sonarrData?.allServers?.size ?: 0}")
        
        // Ensure we have the correct data for this media type
        viewModel?.getDataForMediaType(request.media.mediaType)
    }

    val showAdminDelete = remember(isAdmin, isJellyseerr, radarrData, sonarrData, request) {
        val shouldShow = if (!isAdmin || !isJellyseerr) {
            false
        } else {
            when (request.media.mediaType.lowercase()) {
                "movie" -> radarrData?.allServers?.isNotEmpty() == true
                "tv" -> sonarrData?.allServers?.isNotEmpty() == true
                else -> false
            }
        }
        Log.d("RequestActionModal", "🗑️ Admin delete button: isAdmin=$isAdmin, isJellyseerr=$isJellyseerr, mediaType=${request.media.mediaType}")
        Log.d("RequestActionModal", "🗑️ Admin delete button: hasRadarrServers=${radarrData?.allServers?.isNotEmpty() == true}, hasSonarrServers=${sonarrData?.allServers?.isNotEmpty() == true}")
        Log.d("RequestActionModal", "🗑️ Admin delete button visibility: $shouldShow")
        shouldShow
    }

    val canManageRequests = remember(viewModel) {
        val permissions = viewModel?.getCurrentUserPermissions() ?: 0
        CommonUtil.hasAnyPermission(permissions, Permission.ADMIN, Permission.MANAGE_REQUESTS)
    }

    // Handle dismissal with animation
    fun handleDismiss() {
        if (!isDismissing) {
            isDismissing = true
            coroutineScope.launch {
                delay(300)
                isDismissing = false
                onDismiss()
            }
        }
    }

    // Function to request focus on a specific element
    fun requestFocus(state: FocusState) {
        currentFocus = state
    }

    // Handle back button press to prevent it from reaching the underlying screen
    BackHandler(enabled = uiVisibleState) {
        if (BuildConfig.DEBUG) {
            Log.d("RequestActionModal", "BackHandler: Back button pressed - dismissing modal (isVisible=$isVisible, uiVisibleState=$uiVisibleState)")
        }
        handleDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()
        .clickable { /* Consume clicks to prevent them from reaching layers below */ }
        .onKeyEvent { event ->
            // Consume all directional key events at the outer level
            when (event.key) {
                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
                Key.Back -> {
                    // Let BackHandler handle this, but consume the event
                    if (BuildConfig.DEBUG) {
                        Log.d("RequestActionModal", "Outer Box: Back key consumed, BackHandler should handle")
                    }
                    true
                }
                else -> false
            }
        }
    ) {
        AnimatedVisibility(
            visible = uiVisibleState,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(durationMillis = 300)
            ),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(500.dp)
                    .background(Color(0xFF1F2937))
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        // Only handle key down events
                        if (event.type != KeyEventType.KeyDown) {
                            return@onKeyEvent false
                        }

                        when {
                            KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) -> {
                                when (currentFocus) {
                                    FocusState.MainMenu(-1) -> {
                                        if (showDetailsButton && initialMediaDetails is ApiResult.Success) {
                                            val details = initialMediaDetails.data
                                            onMediaClick(
                                                request.media.tmdbId.toString(),
                                                details.mediaType?.name?.lowercase() ?: ""
                                            )
                                        }
                                        true
                                    }

                                    FocusState.CancelButton -> {
                                        handleDismiss()
                                        true
                                    }


                                    FocusState.DetailsButton -> {
                                        if (initialMediaDetails is ApiResult.Success) {
                                            val details = initialMediaDetails.data
                                            onMediaClick(
                                                request.media.tmdbId.toString(),
                                                details.mediaType?.name?.lowercase() ?: ""
                                            )
                                        }
                                        true
                                    }

                                    FocusState.DeleteButton -> {
                                        if (deleteConfirmationActive) {
                                            // Log that we're deleting the request
                                            if (BuildConfig.DEBUG) {
                                                Log.d("RequestActionModal", "🗑️ Deleting request: ${request.id}")
                                            }
                                            
                                            // Call the parent's onDelete callback - let the parent handle deletion and dismissal
                                            onDelete()
                                            
                                            // The parent will handle modal dismissal and UI refresh
                                        } else {
                                            deleteConfirmationActive = true
                                            deleteConfirmationJob?.cancel()
                                            deleteConfirmationJob = coroutineScope.launch {
                                                delay(5000)
                                                deleteConfirmationActive = false
                                                deleteConfirmationJob = null
                                            }
                                        }
                                        true
                                    }

                                    FocusState.AdminDeleteButton -> {
                                        if (adminDeleteConfirmationActive) {
                                            coroutineScope.launch {
                                                request.media.id.toString().let { mediaId ->
                                                    // Log that we're deleting the media
                                                    if (BuildConfig.DEBUG) {
                                                        Log.d("RequestActionModal", "🗑️ Deleting media with ID: $mediaId")
                                                    }
                                                    
                                                    // Delete the media and file
                                                    viewModel?.deleteMediaFile(mediaId)
                                                    viewModel?.deleteMedia(mediaId)
                                                    
                                                    // Give the backend time to process the deletion
                                                    delay(1500)
                                                    
                                                    // Dismiss the modal
                                                    handleDismiss()
                                                    
                                                    // Force a full refresh of the RECENT_REQUESTS category
                                                    viewModel?.clearCategoryData(MediaCategory.RECENT_REQUESTS)
                                                    
                                                    // Reset API pagination for the category
                                                    viewModel?.resetApiPagination(MediaCategory.RECENT_REQUESTS)
                                                    
                                                    // Perform a complete refresh
                                                    viewModel?.refreshCategoryWithForce(MediaCategory.RECENT_REQUESTS)
                                                    
                                                    // Log the completion of the refresh process
                                                    if (BuildConfig.DEBUG) {
                                                        Log.d(
                                                            "RequestActionModal",
                                                            "🔄 Complete forced refresh of RECENT_REQUESTS after admin media deletion"
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            adminDeleteConfirmationActive = true
                                            adminDeleteConfirmationJob?.cancel()
                                            adminDeleteConfirmationJob = coroutineScope.launch {
                                                delay(5000)
                                                adminDeleteConfirmationActive = false
                                                adminDeleteConfirmationJob = null
                                            }
                                        }
                                        true
                                    }

                                    FocusState.ApproveButton -> {
                                        if (approveConfirmationActive) {
                                            coroutineScope.launch {
                                                request.id.let { requestId ->
                                                    viewModel?.updateRequestStatus(
                                                        requestId,
                                                        2
                                                    )
                                                    handleDismiss()
                                                }
                                            }
                                        } else {
                                            approveConfirmationActive = true
                                            approveConfirmationJob?.cancel()
                                            approveConfirmationJob = coroutineScope.launch {
                                                delay(5000)
                                                approveConfirmationActive = false
                                                approveConfirmationJob = null
                                            }
                                        }
                                        true
                                    }

                                    FocusState.DeclineButton -> {
                                        if (declineConfirmationActive) {
                                            coroutineScope.launch {
                                                request.id.let { requestId ->
                                                    viewModel?.updateRequestStatus(
                                                        requestId,
                                                        3
                                                    )
                                                    handleDismiss()
                                                }
                                            }
                                        } else {
                                            declineConfirmationActive = true
                                            declineConfirmationJob?.cancel()
                                            declineConfirmationJob = coroutineScope.launch {
                                                delay(5000)
                                                declineConfirmationActive = false
                                                declineConfirmationJob = null
                                            }
                                        }
                                        true
                                    }

                                    else -> false
                                }
                            }
                            event.key == Key.Back -> {
                                // Back button is now handled by BackHandler above
                                // This should not be reached, but keeping as fallback
                                if (BuildConfig.DEBUG) {
                                    Log.d("RequestActionModal", "onKeyEvent: Back button fallback - this should not be reached")
                                }
                                handleDismiss()
                                true
                            }
                            event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                                when (currentFocus) {
                                    FocusState.CancelButton -> {
                                        if (showDetailsButton) {
                                            requestFocus(FocusState.MainMenu(-1))
                                            true
                                        } else false
                                    }

                                    FocusState.DetailsButton -> {
                                        requestFocus(FocusState.MainMenu(-1))
                                        true
                                    }

                                    FocusState.DeleteButton -> {
                                        requestFocus(
                                            if (canManageRequests && request.status == 1) {
                                                FocusState.ApproveButton
                                            } else {
                                                FocusState.CancelButton
                                            }
                                        )
                                        true
                                    }

                                    FocusState.AdminDeleteButton -> {
                                        requestFocus(
                                            if (canManageRequests && request.status == 1) {
                                                FocusState.DeclineButton
                                            } else if (showDetailsButton) {
                                                FocusState.DetailsButton
                                            } else {
                                                FocusState.CancelButton
                                            }
                                        )
                                        true
                                    }

                                    FocusState.DeclineButton -> {
                                        requestFocus(if (showDetailsButton) {
                                            FocusState.DetailsButton
                                        } else {
                                            FocusState.CancelButton
                                        })
                                        true
                                    }

                                    FocusState.ApproveButton -> {
                                        requestFocus(FocusState.CancelButton)
                                        true
                                    }

                                    else -> true  // Consume the event even if not handled
                                }
                            }

                            event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                                when (currentFocus) {
                                    FocusState.MainMenu(-1) -> {
                                        requestFocus(FocusState.CancelButton)
                                        true
                                    }

                                    FocusState.CancelButton -> {
                                        requestFocus(
                                            if (canManageRequests && request.status == 1) {
                                                FocusState.ApproveButton
                                            } else if (canDeleteRequest) {
                                                FocusState.DeleteButton
                                            } else {
                                                currentFocus
                                            }
                                        )
                                        true
                                    }

                                    FocusState.DetailsButton -> {
                                        requestFocus(
                                            if (canManageRequests && request.status == 1) {
                                                FocusState.DeclineButton
                                            } else if (showAdminDelete) {
                                                FocusState.AdminDeleteButton
                                            } else if (canDeleteRequest) {
                                                FocusState.DeleteButton
                                            } else {
                                                currentFocus
                                            }
                                        )
                                        true
                                    }

                                    FocusState.ApproveButton -> {
                                        if (canDeleteRequest) {
                                            requestFocus(FocusState.DeleteButton)
                                            true
                                        } else false
                                    }

                                    FocusState.DeclineButton -> {
                                        requestFocus(if (showAdminDelete) {
                                            FocusState.AdminDeleteButton
                                        } else if (canDeleteRequest) {
                                            FocusState.DeleteButton
                                        } else {
                                            currentFocus
                                        })
                                        true
                                    }

                                    else -> true  // Consume the event even if not handled
                                }
                            }

                            event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown -> {
                                when (currentFocus) {
                                    FocusState.CancelButton -> {
                                        // Check for Details button
                                        if (showDetailsButton) {
                                            requestFocus(FocusState.DetailsButton)
                                            true
                                        } else false
                                    }

                                    FocusState.ApproveButton -> {
                                        if (canManageRequests && request.status == 1) {
                                            requestFocus(FocusState.DeclineButton)
                                            true
                                        } else false
                                    }

                                    FocusState.DeleteButton -> {
                                        if (showAdminDelete) {
                                            requestFocus(FocusState.AdminDeleteButton)
                                            true
                                        } else false
                                    }

                                    else -> true  // Consume the event even if not handled
                                }
                            }

                            event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown -> {
                                when (currentFocus) {
                                    FocusState.DetailsButton -> {
                                        requestFocus(FocusState.CancelButton)
                                        true
                                    }

                                    FocusState.DeclineButton -> {
                                        if (canManageRequests && request.status == 1) {
                                            requestFocus(FocusState.ApproveButton)
                                            true
                                        } else false
                                    }

                                    FocusState.AdminDeleteButton -> {
                                        if (canDeleteRequest) {
                                            requestFocus(FocusState.DeleteButton)
                                            true
                                        } else false
                                    }

                                    else -> true  // Consume the event even if not handled
                                }
                            }

                            else -> false
                        }
                    }
            ) {
                // Use Column to make everything scrollable together
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .verticalScroll(scrollState),
                ) {
                    // Media Card with exact same dimensions as list
                    when (initialMediaDetails) {
                        is ApiResult.Loading -> {
                            // You could add a loading indicator here if desired
                        }

                        is ApiResult.Success -> {
                            val details = initialMediaDetails.data
                            
                            // Prepare seasons data for display for TV series
                            val seasonsToDisplay = if (details.mediaType?.name?.lowercase() == "tv") {
                                when {
                                    regularRequest != null && fourKRequest != null -> {
                                        // Dual-tier case - combine both
                                        val regularSeasons = regularRequest.seasons
                                        val fourKSeasons = fourKRequest.seasons
                                        regularSeasons + fourKSeasons
                                    }
                                    else -> {
                                        // Single-tier case - use current request seasons
                                        request.seasons
                                    }
                                }
                            } else {
                                emptyList()
                            }
                            
                            Row()
                            {
                                Box(
                                    modifier = Modifier
                                        .width(134.dp)
                                        .height(185.dp)
                                ) {
                                    MediaCard(
                                        mediaContent = Media(
                                            id = request.media.tmdbId,
                                            mediaType = request.media.mediaType.lowercase(),
                                            title = details.title ?: "",
                                            name = details.name ?: "",
                                            posterPath = details.posterPath ?: "",
                                            backdropPath = details.backdropPath ?: "",
                                            overview = details.overview,
                                            mediaInfo = request.media
                                        ),
                                        context = context,
                                        imageLoader = imageLoader,
                                        isSelected = currentFocus == FocusState.MainMenu(-1),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    // Title
                                    Text(
                                        text = run {
                                            val details = initialMediaDetails.data
                                            when (details.mediaType?.name?.lowercase()) {
                                                "movie" -> details.title
                                                "tv" -> details.name
                                                else -> details.id.toString()
                                            } ?: details.id.toString()
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White
                                    )
                                    // Profile information
                                    val unknownMediaTypeText =
                                        stringResource(R.string.requestAction_unknownMediaType)
                                    val unknownProfileText =
                                        stringResource(R.string.requestAction_unknownProfile)

                                    // Prefer name passed on request; fall back to server lookup only if ids exist
                                    val profileName = when (request.media.mediaType.lowercase()) {
                                        "movie" -> {
                                            request.profileName
                                                ?: run {
                                                    val sid = request.serverId
                                                    val pid = request.profileId
                                                    if (sid != null && pid != null) {
                                                        val server = radarrData?.allServers?.find { it.server.id == sid }
                                                        val profile = server?.profiles?.find { it.id == pid }
                                                        profile?.name
                                                    } else null
                                                }
                                                ?: stringResource(R.string.requestAction_unknownProfile)
                                        }

                                        "tv" -> {
                                            request.profileName
                                                ?: run {
                                                    val sid = request.serverId
                                                    val pid = request.profileId
                                                    if (sid != null && pid != null) {
                                                        val server = sonarrData?.allServers?.find { it.server.id == sid }
                                                        val profile = server?.profiles?.find { it.id == pid }
                                                        profile?.name
                                                    } else null
                                                }
                                                ?: stringResource(R.string.requestAction_unknownProfile)
                                        }

                                        else -> unknownMediaTypeText
                                    }
                                    if (profileName != unknownProfileText && profileName != unknownMediaTypeText) {
                                        Text(
                                            text = profileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // Requested date and user
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.common_requested) + " ",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                        
                                        AutoUpdatingHumanizedDate(
                                            date = request.createdAt,
                                            style = MaterialTheme.typography.bodyMedium,
                                            isVisible = isVisible
                                        )
                                        Text(
                                            text = " " + stringResource(R.string.requestAction_by) + " ${request.requestedBy.displayName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                    // Modified date and user (if available)
                                    if (request.modifiedBy != null) {
                                        Row(modifier = Modifier.padding(top = 4.dp)) {
                                            Text(
                                                text = stringResource(R.string.requestAction_modified) + " ",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White
                                            )
                                            AutoUpdatingHumanizedDate(
                                                date = request.updatedAt,
                                                style = MaterialTheme.typography.bodyMedium,
                                                isVisible = isVisible
                                            )
                                            Text(
                                                text = " " + stringResource(R.string.requestAction_by) + " ${request.modifiedBy.displayName}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Left column - Request Status
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = stringResource(R.string.requestAction_requestStatus),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Show dual request status if both requests exist
                                    if (regularRequest != null && fourKRequest != null) {
                                        RequestStatus.DualMediaStatus(
                                            regularRequest = regularRequest,
                                            fourKRequest = fourKRequest,
                                            regularStatus = regularRequest.status,
                                            fourKStatus = fourKRequest.status,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    } else {
                                        // Show single request status
                                        RequestStatus.MediaRequestStatus(
                                            status = request.status,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                // Right column - Media Status
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = stringResource(R.string.requestAction_mediaStatus),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Show dual media status if both requests exist
                                    if (regularRequest != null && fourKRequest != null) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            RequestStatus.DualMediaStatus(
                                                regularRequest = regularRequest,
                                                fourKRequest = fourKRequest,
                                                regularStatus = if (regularRequest.is4k) regularRequest.media.status4k else regularRequest.media.status,
                                                fourKStatus = if (fourKRequest.is4k) fourKRequest.media.status4k else fourKRequest.media.status,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            // Seasons shown below the status for TV series
                                            if (details.mediaType?.name?.lowercase() == "tv" && seasonsToDisplay.isNotEmpty()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    seasonsToDisplay.sortedBy { it.seasonNumber }.forEach { season ->
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    color = if (request.is4k) Color(0xFF8B5CF6) else Color(0xFF6964EE),
                                                                    shape = RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "S${season.seasonNumber}",
                                                                style = MaterialTheme.typography.bodySmall.copy(
                                                                    color = Color.White,
                                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                                                ),
                                                                softWrap = false,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Show single media status
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            RequestStatus.MediaStatus(
                                                status = if (request.is4k) request.media.status4k else request.media.status,
                                                is4k = request.is4k,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            // Seasons shown below the status for TV series
                                            if (details.mediaType?.name?.lowercase() == "tv" && seasonsToDisplay.isNotEmpty()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    seasonsToDisplay.sortedBy { it.seasonNumber }.forEach { season ->
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    color = if (request.is4k) Color(0xFF8B5CF6) else Color(0xFF6964EE),
                                                                    shape = RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "S${season.seasonNumber}",
                                                                style = MaterialTheme.typography.bodySmall.copy(
                                                                    color = Color.White,
                                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                                                ),
                                                                softWrap = false,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }


                            val downloadStatusList = request.media.downloadStatus
                            Log.d("RequestActionModal","downloadStatusList: $downloadStatusList")

                            // Add spacing above the MediaDownloadStatus component
                            Spacer(modifier = Modifier.height(16.dp))

                            // Use the MediaDownloadStatus component for download status
                            MediaDownloadStatus(
                                tmdbId = request.media.tmdbId.toString(),
                                mediaType = request.media.mediaType.lowercase(),
                                initialMediaDetails = details,
                                showAllDownloads = true,
                                filterBy4K = request.is4k,
                                mediaDetailsState = ApiResult.Success(details)
                            )
                        }
                        is ApiResult.Error -> {
                            // You could add an error state here if desired
                        }
                        null -> {
                            // Handle null case - could show loading or placeholder
                        }
                    }
                    
                    // Add spacer to separate content from buttons
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Button sections - now part of the scrollable content
                    // First row: Cancel + Request More/Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp) // Consistent horizontal spacing
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ActionButton(
                                text = stringResource(id = R.string.common_cancel),
                                isFocused = currentFocus == FocusState.CancelButton,
                                backgroundColor = Color.Gray
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            if (initialMediaDetails is ApiResult.Success && showDetailsButton) {
                                // Always show Information button when showDetailsButton is true
                                ActionButton(
                                    text = stringResource(id = R.string.mediaDetails_information),
                                    isFocused = currentFocus == FocusState.DetailsButton,
                                    backgroundColor = Color(0xFF6964EE)
                                )
                            }
                        }
                    }

                    // Second row: Approve/Decline buttons
                    if (canManageRequests && request.status == 1) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp) // Consistent horizontal spacing
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ActionButton(
                                    text = if (approveConfirmationActive)
                                        stringResource(R.string.common_areYouSure)
                                    else
                                        stringResource(R.string.requestAction_approve),
                                    isFocused = currentFocus == FocusState.ApproveButton,
                                    backgroundColor = Color(0xFF2AA55B)
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                ActionButton(
                                    text = if (declineConfirmationActive)
                                        stringResource(R.string.common_areYouSure)
                                    else
                                        stringResource(R.string.requestAction_decline),
                                    isFocused = currentFocus == FocusState.DeclineButton,
                                    backgroundColor = Color(0xFFF44336)
                                )
                            }
                        }
                    }

                    // Third row: Delete buttons
                    if (canDeleteRequest) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp) // Consistent horizontal spacing
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ActionButton(
                                    text = if (deleteConfirmationActive)
                                        stringResource(R.string.common_areYouSure)
                                    else
                                        stringResource(R.string.requestAction_delete),
                                    isFocused = currentFocus == FocusState.DeleteButton,
                                    backgroundColor = Color(0xFFF44336)
                                )
                            }

                            if (showAdminDelete) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ActionButton(
                                        text = if (adminDeleteConfirmationActive)
                                            stringResource(R.string.common_areYouSure)
                                        else
                                            stringResource(
                                                if (request.media.mediaType == "movie")
                                                    R.string.requestAction_removeFromRadarr
                                                else
                                                    R.string.requestAction_removeFromSonarr
                                            ),
                                        isFocused = currentFocus == FocusState.AdminDeleteButton,
                                        backgroundColor = Color(0xFFF44336)
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    
                    // Add bottom padding for better spacing
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}



sealed class FocusState {
    data class MainMenu(val index: Int) : FocusState()
    object CancelButton : FocusState()
    object DetailsButton : FocusState()
    object DeleteButton : FocusState()
    object AdminDeleteButton : FocusState()
    object ApproveButton : FocusState()
    object DeclineButton : FocusState()
}
