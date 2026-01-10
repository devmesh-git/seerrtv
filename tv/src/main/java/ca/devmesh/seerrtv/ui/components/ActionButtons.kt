package ca.devmesh.seerrtv.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.MediaServerType
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel
import androidx.core.net.toUri

// Tier states for dual-tier request system
enum class TierState {
    REQUEST,    // Can request this tier
    REQUEST_MORE, // Can request more seasons for this tier
    MANAGE,     // Has request, can manage
    PLAY        // Available, can play
}

/**
 * Action button state data class to hold button visibility and state information
 */
data class ActionButtonState(
    val isVisible: Boolean,
    val isSplit: Boolean,
    val leftTier: TierState? = null,
    val rightTier: TierState? = null,
    val singleTier: TierState? = null
)

/**
 * Play button - always single button when any playable content exists
 */
@Composable
fun ActionPlayButton(
    isFocused: Boolean,
    onEnter: () -> Unit,
    context: Context
) {
    val mediaServerType = SharedPreferencesUtil.getMediaServerType(context)
    val (buttonText, buttonIcon, buttonColor) = getTierButtonInfo(TierState.PLAY, context, mediaServerType)

    Box(
        modifier = Modifier
        .fillMaxWidth()
        .height(40.dp)
        .background(
            buttonColor.copy(alpha = if (isFocused) 0.8f else 1f),
            RoundedCornerShape(16.dp)
        )
        .border(
            width = if (isFocused) 2.dp else 0.dp,
            color = if (isFocused) Color.White else Color.Transparent,
            shape = RoundedCornerShape(16.dp)
        )
        .padding(horizontal = 12.dp)
        .focusable()
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                onEnter()
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                buttonIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = buttonText,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Request button - can be single or split based on tier availability
 */
@Composable
fun ActionRequestButton(
    media: MediaDetails,
    viewModel: SeerrViewModel,
    isFocused: Boolean,
    canRequest: Boolean,
    has4kCapability: Boolean,
    currentFocusArea: Int,
    context: Context,
    leftTier: TierState? = null,
    rightTier: TierState? = null,
    singleTier: TierState? = null
) {
    val mediaServerType = SharedPreferencesUtil.getMediaServerType(context)
    
    // Use provided tier states if available, otherwise fall back to getTierStates
    val (regularState, fourKState) = if (leftTier != null || rightTier != null || singleTier != null) {
        // Use provided tier states - don't default to REQUEST if tier is null
        val regularTier = leftTier ?: singleTier
        val fourKTier = rightTier
        Pair(regularTier, fourKTier)
    } else {
        // Fall back to getTierStates for backward compatibility
        getTierStates(media, viewModel, canRequest, has4kCapability)
    }

    // Determine if we should show split button
    // Show split only when BOTH tiers are actually request-eligible (REQUEST or REQUEST_MORE)
    // If only one tier is requestable, use single button
    val showSplit = has4kCapability && canRequest &&
        ((leftTier == TierState.REQUEST || leftTier == TierState.REQUEST_MORE) && 
         (rightTier == TierState.REQUEST || rightTier == TierState.REQUEST_MORE))
    
    Log.d("ActionRequestButton", "Debug: leftTier=$leftTier, rightTier=$rightTier, regularState=$regularState, fourKState=$fourKState, showSplit=$showSplit, currentFocusArea=$currentFocusArea")

    if (showSplit) {
        // Split button layout
        val (leftText, _, leftColor) = getTierButtonInfo(regularState ?: TierState.REQUEST, context, mediaServerType)
        val (rightText, _, rightColor) = getTierButtonInfo(fourKState ?: TierState.REQUEST, context, mediaServerType)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.Transparent, RoundedCornerShape(16.dp))
        ) {
            // Left button (HD tier)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        leftColor.copy(alpha = if (isFocused && currentFocusArea == 4) 0.8f else 1f), // FocusArea.REQUEST_HD
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .border(
                        width = if (isFocused && currentFocusArea == 4) 2.dp else 0.dp, // FocusArea.REQUEST_HD
                        color = if (isFocused && currentFocusArea == 4) Color.White else Color.Transparent, // FocusArea.REQUEST_HD
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // In split mode, 4K capability is guaranteed true; always show HD badge on the left half
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF10B981), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "HD",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = leftText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Right button (4K tier)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        rightColor.copy(alpha = if (isFocused && currentFocusArea == 11) 0.8f else 1f), // FocusArea.REQUEST_4K
                        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                    )
                    .border(
                        width = if (isFocused && currentFocusArea == 11) 2.dp else 0.dp, // FocusArea.REQUEST_4K
                        color = if (isFocused && currentFocusArea == 11) Color.White else Color.Transparent, // FocusArea.REQUEST_4K
                        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 4K badge
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF3B82F6), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "4K",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = rightText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    } else {
        // Single button layout
        // Decide which tier this single button represents
        val isSingleFourK = (fourKState == TierState.REQUEST || fourKState == TierState.REQUEST_MORE) && 
                           (regularState != TierState.REQUEST && regularState != TierState.REQUEST_MORE)
        val effectiveTier = if (isSingleFourK) fourKState else (regularState ?: TierState.REQUEST)
        
        Log.d("ActionRequestButton", "Single button: isSingleFourK=$isSingleFourK, effectiveTier=$effectiveTier")
        val (singleText, singleIcon, singleColor) = getTierButtonInfo(effectiveTier, context, mediaServerType)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(
                    singleColor.copy(alpha = if (isFocused) 0.8f else 1f),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // When two tiers exist but only one is applicable, show the tier badge
                if (has4kCapability) {
                    if (isSingleFourK) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF3B82F6), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "4K",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (effectiveTier == TierState.REQUEST || effectiveTier == TierState.REQUEST_MORE) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF10B981), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "HD",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Icon(
                            singleIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Icon(
                        singleIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = singleText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Manage button - can be single or split based on existing requests
 */
@Composable
fun ActionManageButton(
    isFocused: Boolean,
    has4kCapability: Boolean,
    currentFocusArea: Int,
    context: Context,
    leftTier: TierState? = null,
    rightTier: TierState? = null,
    singleTier: TierState? = null
) {
    val mediaServerType = SharedPreferencesUtil.getMediaServerType(context)

    // Determine if we should show split button based on the tier states
    val showSplit = has4kCapability && leftTier != null && rightTier != null

    if (showSplit) {
        // Split button layout - both tiers have requests
        val (leftText, _, leftColor) = getTierButtonInfo(leftTier, context, mediaServerType)
        val (rightText, _, rightColor) = getTierButtonInfo(rightTier, context, mediaServerType)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.Transparent, RoundedCornerShape(16.dp))
        ) {
            // Left button (HD tier)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        leftColor.copy(alpha = if (isFocused && currentFocusArea == 13) 0.8f else 1f), // FocusArea.MANAGE_HD
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .border(
                        width = if (isFocused && currentFocusArea == 13) 2.dp else 0.dp, // FocusArea.MANAGE_HD
                        color = if (isFocused && currentFocusArea == 13) Color.White else Color.Transparent, // FocusArea.MANAGE_HD
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF10B981), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "HD",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = leftText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Right button (4K tier)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        rightColor.copy(alpha = if (isFocused && currentFocusArea == 14) 0.8f else 1f), // FocusArea.MANAGE_4K
                        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                    )
                    .border(
                        width = if (isFocused && currentFocusArea == 14) 2.dp else 0.dp, // FocusArea.MANAGE_4K
                        color = if (isFocused && currentFocusArea == 14) Color.White else Color.Transparent, // FocusArea.MANAGE_4K
                        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 4K badge
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF3B82F6), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "4K",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = rightText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    } else {
        // Single button layout - only one tier has a request
        val (singleText, singleIcon, singleColor) = getTierButtonInfo(singleTier ?: TierState.MANAGE, context, mediaServerType)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(
                    singleColor.copy(alpha = if (isFocused) 0.8f else 1f),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Single button: no badges, just icon and text
                Icon(
                    singleIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = singleText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Trailer button - always single button when trailer is available
 */
@Composable
fun ActionTrailerButton(
    isFocused: Boolean,
    context: Context
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                Color(0xFF3370FF).copy(alpha = if (isFocused) 0.8f else 1f),
                RoundedCornerShape(16.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = context.getString(R.string.mediaDetails_watchTrailer),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun ActionIssueInlineButton(
    isFocused: Boolean,
    hasIssues: Boolean,
    context: Context
) {
    val buttonText = if (hasIssues) {
        context.getString(R.string.issue_button_view_issues)
    } else {
        context.getString(R.string.issue_button_report_issues)
    }
    
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (hasIssues) Color(0xFFF44336) else Color(0xFFFFA500), // Red for view, orange for report
        modifier = Modifier
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable()
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

// Helper function to get tier states for dual-tier system
private fun getTierStates(
    media: MediaDetails,
    viewModel: SeerrViewModel,
    canRequest: Boolean,
    has4kCapability: Boolean
): Pair<TierState, TierState> {
    val (regularRequest, fourKRequest) = viewModel.getRequestsForMedia(media.id)
    val mediaInfo = media.mediaInfo

    val regularState = when {
        !canRequest -> TierState.REQUEST // Can't request, but show as request state
        mediaInfo?.status == 5 -> TierState.MANAGE // If available, show manage (this will be filtered out by visibility logic)
        regularRequest != null -> TierState.MANAGE
        else -> TierState.REQUEST
    }

    val fourKState = when {
        !has4kCapability -> TierState.REQUEST // No 4K servers available - treat as request state (won't be shown)
        !canRequest -> TierState.REQUEST // Can't request, but show as request state
        mediaInfo?.status4k == 5 -> TierState.MANAGE // If available, show manage (this will be filtered out by visibility logic)
        fourKRequest != null -> TierState.MANAGE
        else -> TierState.REQUEST
    }

    return Pair(regularState, fourKState)
}

// Helper function to get button text and icon for a tier state
private fun getTierButtonInfo(
    tierState: TierState,
    context: Context,
    mediaServerType: MediaServerType
): Triple<String, ImageVector, Color> {
    return when (tierState) {
        TierState.REQUEST -> {
            val text = context.getString(R.string.requestModal_submit)
            Triple(text, Icons.Default.Add, Color(0xFF6964EE))
        }
        TierState.REQUEST_MORE -> {
            val text = context.getString(R.string.mediaDetails_requestMore)
            Triple(text, Icons.Default.Add, Color(0xFF6964EE))
        }
        TierState.MANAGE -> {
            val text = context.getString(R.string.mediaDetails_manageRequest)
            Triple(text, Icons.Default.Info, Color(0xFF2196F3))
        }
        TierState.PLAY -> {
            val text = when (mediaServerType) {
                MediaServerType.PLEX -> context.getString(R.string.mediaDetails_playOnPlex)
                MediaServerType.JELLYFIN -> context.getString(R.string.mediaDetails_playOnJellyfin)
                MediaServerType.EMBY -> context.getString(R.string.mediaDetails_playOnEmby)
                MediaServerType.NOT_CONFIGURED -> context.getString(R.string.mediaDetails_noMediaPlayerFound)
            }
            Triple(text, Icons.Default.PlayArrow, Color(0xFF00C853))
        }
    }
}

// Helper function to check if Request More should be shown for a specific tier
private fun shouldShowRequestMore(
    media: MediaDetails,
    viewModel: SeerrViewModel,
    is4k: Boolean
): Boolean {
    // Only applicable for TV shows
    if (media.mediaType?.name?.lowercase() != "tv") {
        return false
    }
    
    val mediaInfo = media.mediaInfo
    val seasons = media.seasons ?: return false
    
    // Get existing requests from viewModel
    val (regularRequest, fourKRequest) = viewModel.getRequestsForMedia(media.id)
    val currentTierRequest = if (is4k) fourKRequest else regularRequest
    
    // Get the media status for this tier
    val status = if (is4k) mediaInfo?.status4k ?: 0 else mediaInfo?.status ?: 0
    val isPartiallyAvailable = status == 4
    
    // If there's an existing request for this tier, check for unrequested seasons
    if (currentTierRequest != null) {
        // Get seasons that are already requested for this tier
        val requestedSeasons = currentTierRequest.seasons.map { it.seasonNumber }.toSet()
        
        // Get all available seasons
        val availableSeasons = seasons.map { it.seasonNumber }.toSet()
        
        // Check if there are seasons that haven't been requested yet
        val unrequestedSeasons = availableSeasons - requestedSeasons
        
        return unrequestedSeasons.isNotEmpty() || isPartiallyAvailable
    }
    
    // If there's no existing request but the media is partially available,
    // we can still "request more" to get the missing seasons
    if (isPartiallyAvailable) {
        Log.d("MediaDetails", "shouldShowRequestMore: mediaId=${media.id}, is4k=$is4k, " +
              "no existing request but partially available, result=true")
        return true
    }
    
    Log.d("MediaDetails", "shouldShowRequestMore: mediaId=${media.id}, is4k=$is4k, " +
          "no existing request and not partially available, result=false")
    
    return false
}

/**
 * Helper function to determine action button states based on requirements matrix
 * Implements the exact logic from MediaDetails_ActionButtons_Requirements.md
 */
fun getActionButtonStates(
    media: MediaDetails,
    viewModel: SeerrViewModel,
    canRequest: Boolean,
    has4kCapability: Boolean,
    hasTrailer: Boolean
): Map<String, ActionButtonState> {
    val (regularRequest, fourKRequest) = viewModel.getRequestsForMedia(media.id)
    val mediaInfo = media.mediaInfo

    val regularStatus = mediaInfo?.status ?: 0
    val fourKStatus = mediaInfo?.status4k ?: 0

    val isRegularAvailable = regularStatus == 5
    val isFourKAvailable = fourKStatus == 5
    val isRegularPartiallyAvailable = regularStatus == 4
    val isFourKPartiallyAvailable = fourKStatus == 4
    val isRegularRequested = regularRequest != null
    val isFourKRequested = fourKRequest != null

    // Treat partially available as playable to surface the Play button for series with some episodes available
    val hasAnyPlayableContent = isRegularAvailable || isFourKAvailable ||
        isRegularPartiallyAvailable || isFourKPartiallyAvailable
    val hasAnyRequests = isRegularRequested || isFourKRequested
    val bothAvailable = isRegularAvailable && isFourKAvailable

    Log.d(
        "MediaDetailsButtons",
        "computeStates: mediaId=${media.id} canRequest=${canRequest} has4k=${has4kCapability} hasTrailer=${hasTrailer} " +
            "status{hd=${regularStatus} 4k=${fourKStatus}} requested{hd=${isRegularRequested} 4k=${isFourKRequested}} " +
            "playable=${hasAnyPlayableContent} requests=${hasAnyRequests} bothAvailable=${bothAvailable}"
    )

    // Determine PLAY button visibility according to requirements matrix
    // PLAY: visible when any playable content exists (available or partially available) OR a playback identifier exists
    val hasPlayUrl = mediaInfo?.mediaUrl != null || mediaInfo?.plexUrl != null || mediaInfo?.iOSPlexUrl != null
    val hasPlexIdentifier = !mediaInfo?.ratingKey.isNullOrBlank() || !mediaInfo?.iOSPlexUrl.isNullOrBlank()
    val hasJellyfinIdentifier = mediaInfo?.externalServiceId != null ||
        (mediaInfo?.mediaUrl?.toUri()?.fragment?.contains("id=") == true)
    val hasAnyPlaybackHandle = hasPlayUrl || hasPlexIdentifier || hasJellyfinIdentifier
    val playVisible = hasAnyPlayableContent && hasAnyPlaybackHandle

    // Determine REQUEST button visibility according to requirements matrix
    // REQUEST: visible when user can request and corresponding tier is not fully available
    val needsRequestMore = shouldShowRequestMore(media, viewModel, false) || shouldShowRequestMore(media, viewModel, true)
    val canRequestRegular = canRequest && !isRegularRequested && !isRegularAvailable
    val canRequestFourK = has4kCapability && canRequest && !isFourKRequested && !isFourKAvailable
    
    // REQUEST button should be visible if ANY tier can be requested
    val requestVisible = canRequestRegular || canRequestFourK || needsRequestMore

    // Determine REQUEST button split/single logic according to requirements matrix
    val requestLeftTier = when {
        shouldShowRequestMore(media, viewModel, false) -> TierState.REQUEST_MORE
        canRequestRegular -> TierState.REQUEST
        else -> null
    }
    val requestRightTier = when {
        shouldShowRequestMore(media, viewModel, true) -> TierState.REQUEST_MORE
        canRequestFourK -> TierState.REQUEST
        else -> null
    }
    val requestSingleTier = when {
        !has4kCapability && shouldShowRequestMore(media, viewModel, false) -> TierState.REQUEST_MORE
        !has4kCapability && canRequestRegular -> TierState.REQUEST
        else -> null
    }

    // REQUEST button should be split when both tiers are relevant
    val requestSplit = has4kCapability && (requestLeftTier != null) && (requestRightTier != null)

    // Determine MANAGE button visibility according to requirements matrix
    // MANAGE: visible when at least one request exists
    val manageVisible = hasAnyRequests

    // MANAGE button should be split when both tiers have requests
    val manageSplit = has4kCapability && isRegularRequested && isFourKRequested
    val manageLeftTier = if (isRegularRequested) TierState.MANAGE else null
    val manageRightTier = if (isFourKRequested) TierState.MANAGE else null
    val manageSingleTier = if ((isRegularRequested && !isFourKRequested) || (!isRegularRequested && isFourKRequested)) TierState.MANAGE else null

    val states = mapOf(
        "play" to ActionButtonState(
            isVisible = playVisible,
            isSplit = false,
            singleTier = TierState.PLAY
        ),
        "request" to ActionButtonState(
            isVisible = requestVisible,
            isSplit = requestSplit,
            leftTier = requestLeftTier,
            rightTier = requestRightTier,
            singleTier = requestSingleTier
        ),
        "manage" to ActionButtonState(
            isVisible = manageVisible,
            isSplit = manageSplit,
            leftTier = manageLeftTier,
            rightTier = manageRightTier,
            singleTier = manageSingleTier
        ),
        "trailer" to ActionButtonState(
            isVisible = hasTrailer,
            isSplit = false,
            singleTier = null // Trailer doesn't use TierState
        )
    )

    Log.d(
        "MediaDetailsButtons",
        "statesVisible: play=${states["play"]?.isVisible} request{visible=${states["request"]?.isVisible} split=${states["request"]?.isSplit} " +
            "left=${states["request"]?.leftTier != null} right=${states["request"]?.rightTier != null} single=${states["request"]?.singleTier != null}} " +
            "manage{visible=${states["manage"]?.isVisible} split=${states["manage"]?.isSplit} " +
            "left=${states["manage"]?.leftTier != null} right=${states["manage"]?.rightTier != null} single=${states["manage"]?.singleTier != null}} " +
            "trailer=${states["trailer"]?.isVisible}"
    )

    return states
}
