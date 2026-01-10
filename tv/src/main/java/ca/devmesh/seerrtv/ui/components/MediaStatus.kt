package ca.devmesh.seerrtv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.Request
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel
import ca.devmesh.seerrtv.model.RelatedVideo

// Data class to hold media status information
data class MediaStatusInfo(
    val isPartiallyAvailable: Boolean,
    val isAvailable: Boolean,
    val hasCast: Boolean,
    val hasCrew: Boolean,
    val hasTags: Boolean,
    val hasTrailer: Boolean,
    val hasRequest: Boolean,
    // Dual-tier request support
    val regularRequest: Request? = null,
    val fourKRequest: Request? = null,
    val hasRegularRequest: Boolean = false,
    val hasFourKRequest: Boolean = false
)

@Composable
fun TVMessage(
    message: String,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000) // Wait 3 seconds
        onDismiss()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(32.dp)
                .width(400.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E293B),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.mediaDetails_information),
                    tint = Color(0xFF3370FF), // Blue color to match your UI
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Helper function to process media status and determine availability states
fun processMediaStatus(media: MediaDetails, viewModel: SeerrViewModel): MediaStatusInfo {
    val mediaInfo = media.mediaInfo

    // Get both regular and 4K requests
    val (regularRequest, fourKRequest) = viewModel.getRequestsForMedia(media.id)
    val hasRegularRequest = regularRequest != null
    val hasFourKRequest = fourKRequest != null
    val hasRequest = hasRegularRequest || hasFourKRequest

    // Determine overall availability states
    val regularStatus = mediaInfo?.status ?: 0
    val fourKStatus = mediaInfo?.status4k ?: 0

    // Media is available if either tier is available
    val isAvailable = regularStatus == 5 || fourKStatus == 5
    val isPartiallyAvailable = regularStatus == 4 || fourKStatus == 4
    return MediaStatusInfo(
        isPartiallyAvailable = isPartiallyAvailable,
        isAvailable = isAvailable,
        hasCast = media.credits.cast.isNotEmpty(),
        hasCrew = media.credits.crew.isNotEmpty(),
        hasTags = !media.keywords.isNullOrEmpty(),
        hasTrailer = (media.relatedVideos?.isNotEmpty() == true && getTrailerUrl(media.relatedVideos) != null),
        hasRequest = hasRequest,
        // Dual-tier request support
        regularRequest = regularRequest,
        fourKRequest = fourKRequest,
        hasRegularRequest = hasRegularRequest,
        hasFourKRequest = hasFourKRequest
    )
}

private fun getTrailerUrl(relatedVideos: List<RelatedVideo>?): String? {
    return relatedVideos?.find { it.type == "Trailer" }?.url
}

// Public helper for other modules/files
fun findTrailerUrl(relatedVideos: List<RelatedVideo>?): String? {
    return relatedVideos?.find { it.type == "Trailer" }?.url
}
