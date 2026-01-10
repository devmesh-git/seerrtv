package ca.devmesh.seerrtv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.Request

object RequestStatus {
    private data class StatusInfo(
        val textResId: Int,
        val backgroundColor: Color,
        val textColor: Color
    )

    // MediaRequestStatus enum values for both Overseerr and Jellyseerr
    // (PENDING = 1, APPROVED = 2, DECLINED = 3, FAILED = 4, COMPLETED = 5)
    private val requestStatusMap = mapOf(
        1 to StatusInfo(R.string.requestStatus_pending, Color(0xFFBF9829), Color(0xFFDDFCE8)),
        2 to StatusInfo(R.string.requestStatus_approved, Color(0xFF2AA55B), Color(0xFFDDFCE8)),
        3 to StatusInfo(R.string.requestStatus_declined, Color(0xFFF44336), Color(0xFFDDFCE8)),
        4 to StatusInfo(R.string.requestStatus_failed, Color(0xFFF44336), Color(0xFFDDFCE8)),
        5 to StatusInfo(R.string.requestStatus_completed, Color(0xFF2AA55B), Color(0xFFDDFCE8))
    )

    // MediaStatus enum values for both Overseerr and Jellyseerr
    //  Overseerr: (UNKNOWN = 1, PENDING = 2, PROCESSING = 3, PARTIALLY_AVAILABLE = 4, AVAILABLE = 5)
    // Jellyseerr: (UNKNOWN = 1, PENDING = 2, PROCESSING = 3, PARTIALLY_AVAILABLE = 4, AVAILABLE = 5, BLACKLISTED = 6, DELETED = 7)

    // We also handle null/0 as "not requested"
    private val mediaStatusMap = mapOf(
        0 to StatusInfo(R.string.requestStatus_notRequested, Color(0xFF6964EE), Color(0xFFDDFCE8)),
        1 to StatusInfo(R.string.common_notRequested, Color(0xFF6964EE), Color(0xFFDDFCE8)),
        2 to StatusInfo(R.string.requestStatus_pending, Color(0xFFBF9829), Color(0xFFDDFCE8)),
        3 to StatusInfo(
            R.string.common_requested,
            Color(0xFF6964EE),
            Color(0xFFDDFCE8)
        ),  // Status 3 now shows "Requested"
        4 to StatusInfo(
            R.string.requestStatus_partiallyAvailable,
            Color(0xFF2AA55B),
            Color(0xFFDDFCE8)
        ),
        5 to StatusInfo(R.string.requestStatus_available, Color(0xFF2AA55B), Color(0xFFDDFCE8)),
        6 to StatusInfo(R.string.requestStatus_blacklisted, Color(0xFF000000), Color(0xFFDDFCE8)),
        7 to StatusInfo(R.string.requestStatus_deleted, Color(0xFFDC2626), Color(0xFFDDFCE8))
    )

    @Composable
    private fun StatusBadge(
        modifier: Modifier = Modifier,
        status: Int,
        statusMap: Map<Int, StatusInfo>,
        style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
        is4k: Boolean = false
    ) {
        val statusInfo = statusMap[status] ?: StatusInfo(
            R.string.common_unknown,
            Color(0xFF9CA3AF),
            Color(0xFFDDFCE8)
        )

        // For 4K items, append "4K" to the status text
        val displayText = if (is4k) {
            "4K " + stringResource(statusInfo.textResId)
        } else {
            stringResource(statusInfo.textResId)
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = statusInfo.backgroundColor,
            modifier = modifier
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = displayText,
                    style = style,
                    color = statusInfo.textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }

    @Composable
    fun MediaRequestStatus(
        modifier: Modifier = Modifier,
        status: Int,
        style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
    ) {
        StatusBadge(
            modifier = modifier,
            status = status,
            statusMap = requestStatusMap,
            style = style
        )
    }

    @Composable
    fun MediaStatus(
        modifier: Modifier = Modifier,
        status: Int?,
        is4k: Boolean = false,
        style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
    ) {
        StatusBadge(
            modifier = modifier,
            status = status ?: 0,
            statusMap = mediaStatusMap,
            style = style,
            is4k = is4k
        )
    }

    /**
     * Dual status badges for both regular and 4K requests
     * Shows both badges when both tiers have requests, matching Jellyseerr design
     */
    @Composable
    fun DualMediaStatus(
        modifier: Modifier = Modifier,
        regularRequest: Request?,
        fourKRequest: Request?,
        regularStatus: Int?,
        fourKStatus: Int?,
        style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
    ) {
        val hasRegular = regularRequest != null
        val hasFourK = fourKRequest != null
        
        // Debug logging to understand status values
        Log.d("DualMediaStatus", "Regular: request=$hasRegular, status=$regularStatus")
        Log.d("DualMediaStatus", "4K: request=$hasFourK, status=$fourKStatus")
        
        // Show badges based on requests and availability
        // Show regular badge if there's a request or if regular status has meaningful content (4=partially available, 5=available)
        // Show 4K badge if there's a request or if 4K status has meaningful content (but not just "not requested")
        val showRegular = hasRegular || (regularStatus != null && regularStatus >= 4)
        val showFourK = hasFourK || (fourKStatus != null && fourKStatus > 1)
        
        // Special case: If both statuses are 5 but no requests exist, assume only 4K is available
        // This handles the Jellyseerr API behavior where both statuses are set to 5
        val bothAvailable = (regularStatus == 5 && fourKStatus == 5)
        val noRequests = !hasRegular && !hasFourK
        val showOnly4K = bothAvailable && noRequests
        
        // Apply the special case logic
        val finalShowRegular = if (showOnly4K) false else showRegular
        val finalShowFourK = showFourK
        
        Log.d("DualMediaStatus", "Final badges: regular=$finalShowRegular, 4K=$finalShowFourK, showOnly4K=$showOnly4K")
        
        if (finalShowRegular || finalShowFourK) {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Regular tier status badge
                if (finalShowRegular) {
                    MediaStatus(
                        status = regularStatus,
                        is4k = false,
                        style = style
                    )
                }
                
                // 4K tier status badge
                if (finalShowFourK) {
                    MediaStatus(
                        status = fourKStatus,
                        is4k = true,
                        style = style
                    )
                }
            }
        }
    }
} 