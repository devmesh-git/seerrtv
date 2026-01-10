package ca.devmesh.seerrtv.ui.components

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.MediaType

@Composable
fun OverviewSection(
    media: MediaDetails,
    isOverviewFocused: Boolean,
    isFullOverviewShown: Boolean,
    isReadMoreFocused: Boolean,
    onToggleFullOverview: (Boolean) -> Unit,
    statusInfo: MediaStatusInfo? = null,
    showFourKStatus: Boolean = true,
    currentFocusArea: Int,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Dual status badges and issue button above title (like Jellyseerr)
        statusInfo?.let { info ->
            if (info.hasRegularRequest || (showFourKStatus && info.hasFourKRequest) || info.isAvailable || info.isPartiallyAvailable) {
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RequestStatus.DualMediaStatus(
                        regularRequest = info.regularRequest,
                        fourKRequest = if (showFourKStatus) info.fourKRequest else null,
                        regularStatus = media.mediaInfo?.status,
                        fourKStatus = if (showFourKStatus) media.mediaInfo?.status4k else null,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    // Issue button only shown when media is available or partially available
                    if (info.isAvailable || info.isPartiallyAvailable) {
                        ActionIssueInlineButton(
                            isFocused = (currentFocusArea == 17), // FocusArea.ISSUE
                            hasIssues = !media.mediaInfo?.issues.isNullOrEmpty(),
                            context = context
                        )
                    }
                }
            }
        }

        val baseTitle = media.name ?: media.title ?: stringResource(R.string.mediaDetails_unknownTitle)
        val year = when (media.mediaType) {
            MediaType.MOVIE -> media.releaseDate?.take(4)
            MediaType.TV -> media.firstAirDate?.take(4)
            else -> null
        }

        // Debug logging for year extraction
        android.util.Log.d("MediaDetails", "Title: $baseTitle")
        android.util.Log.d("MediaDetails", "MediaType: ${media.mediaType}")
        android.util.Log.d("MediaDetails", "ReleaseDate: ${media.releaseDate}")
        android.util.Log.d("MediaDetails", "FirstAirDate: ${media.firstAirDate}")
        android.util.Log.d("MediaDetails", "Extracted Year: $year")
        android.util.Log.d("MediaDetails", "Year is null: ${year == null}")
        android.util.Log.d("MediaDetails", "Year is empty: ${year?.isEmpty()}")

        // Check if title is too long (more than 30 characters) and put year on new line
        val isTitleLong = baseTitle.length > 30

        if (isTitleLong && year != null && year.isNotEmpty()) {
            // Long title: put year on new line
            Column(
                modifier = Modifier.alpha(if (isOverviewFocused) 1f else 0.9f)
            ) {
                Text(
                    text = baseTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "($year)",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.7f // Make year 70% of title size
                    ),
                    modifier = Modifier.offset(y = 2.dp) // Slight upward offset to align with title baseline
                )
            }
        } else {
            // Short title: use Row layout
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (isOverviewFocused) 1f else 0.9f)
            ) {
                Text(
                    text = baseTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (year != null && year.isNotEmpty()) {
                    android.util.Log.d("MediaDetails", "Displaying year: ($year)")
                    Text(
                        text = " ($year)",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.7f // Make year 70% of title size
                        ),
                        modifier = Modifier.offset(y = 2.dp) // Slight upward offset to align with title baseline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (media.mediaType) {
            MediaType.MOVIE -> MovieMetadata(media)
            MediaType.TV -> TVSeriesMetadata(media)
            else -> {
                // Handle case where type is not set or unknown
                Text(
                    text = stringResource(id = R.string.mediaDetails_unknownType),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val truncatedOverview = if (!isFullOverviewShown) {
            val firstBreakAfter300 = media.overview.indexOf("\n\n", 300)
            if (firstBreakAfter300 != -1) {
                media.overview.substring(0, firstBreakAfter300) + "..."
            } else if (media.overview.length > 500) {
                media.overview.take(500) + "..."
            } else {
                media.overview
            }
        } else {
            media.overview
        }

        val hasHiddenText = truncatedOverview != media.overview

        Text(
            text = truncatedOverview,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.alpha(if (isOverviewFocused) 1f else 0.8f)
        )

        if (hasHiddenText || isFullOverviewShown) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isFullOverviewShown) stringResource(id = R.string.common_readLess) else stringResource(
                    id = R.string.common_readMore
                ),
                color = if (isReadMoreFocused) Color.White else Color.LightGray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { onToggleFullOverview(!isFullOverviewShown) }
            )
        }
    }
}

@Composable
fun MovieMetadata(media: MediaDetails) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CertificationBox(certification = getMovieCertification(media))

        Text(
            text = "${media.runtime ?: stringResource(id = R.string.mediaDetails_unknownDuration)} ${
                stringResource(
                    id = R.string.mediaDetails_minutes
                )
            } | ${media.genres.joinToString(", ") { it.name }}",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
fun TVSeriesMetadata(media: MediaDetails) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${stringResource(id = R.string.mediaDetails_tvSeries)} | ${
                media.genres.joinToString(
                    ", "
                ) { it.name }
            }",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
fun CertificationBox(certification: String) {
    if (certification == "N/A") return
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Color.White,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 0.dp)
    ) {
        Text(
            text = certification,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
    Text(text = "|")
}

//@Composable
//fun MediaInfoTable(
//    modifier: Modifier = Modifier
//) {
//    Column(
//        modifier = modifier
//            .fillMaxWidth()
//            .border(
//                1.dp,
//                Color(0xFF3370FF),
//                RoundedCornerShape(8.dp)
//            )
//            .padding(8.dp)
//    ) {
//        // Add media info table content here
//        Text(
//            text = "Media Info",
//            style = MaterialTheme.typography.bodyMedium,
//            color = Color.White
//        )
//    }
//}

private fun getMovieCertification(mediaDetails: MediaDetails): String {
    var certification = mediaDetails.releases?.results?.find { it.iso_3166_1 == "US" }
        ?.releaseDates?.firstOrNull()?.certification ?: "N/A"
    if (certification == "") {
        certification = "N/A"
    }
    return certification
}
