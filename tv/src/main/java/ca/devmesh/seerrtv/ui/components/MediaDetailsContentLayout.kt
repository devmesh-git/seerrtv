package ca.devmesh.seerrtv.ui.components

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.ui.MediaInfoTable
import ca.devmesh.seerrtv.ui.state.FocusArea
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel

/**
 * Content layout for MediaDetails screen
 * Displays overview, download status, tags, and info table
 */
@Composable
fun MediaDetailsContentLayout(
    media: MediaDetails,
    viewModel: SeerrViewModel,
    currentFocusArea: Int,
    isFullOverviewShown: Boolean,
    onToggleFullOverview: (Boolean) -> Unit,
    hasTags: Boolean,
    statusInfo: MediaStatusInfo?,
    has4kCapability: Boolean,
    hasReadMoreButton: Boolean,
    onLeftmostTagsUpdated: (List<Int>) -> Unit,
    onTagPositionsUpdated: (List<Pair<Int, Float>>) -> Unit,
    selectedTagIndex: Int,
    context: Context,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row {
            // Middle column: Overview
            Column(
                modifier = Modifier.weight(0.65f)
            ) {
                OverviewSection(
                    media = media,
                    isOverviewFocused = currentFocusArea == FocusArea.OVERVIEW,
                    isFullOverviewShown = isFullOverviewShown,
                    isReadMoreFocused = currentFocusArea == FocusArea.READ_MORE && hasReadMoreButton,
                    onToggleFullOverview = onToggleFullOverview,
                    statusInfo = statusInfo,
                    showFourKStatus = has4kCapability,
                    currentFocusArea = currentFocusArea,
                    context = context
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Add MediaDownloadStatus component above tags
                // Always start polling - the component handles its own lifecycle
                MediaDownloadStatus(
                    tmdbId = media.id.toString(),
                    mediaType = media.mediaType?.name?.lowercase()
                        ?: "",
                    initialMediaDetails = media,
                    showAllDownloads = true,
                    mediaDetailsState = ApiResult.Success(media),
                    mainViewModel = viewModel
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (hasTags) {
                    FlexibleTagLayout(
                        keywords = media.keywords,
                        selectedIndex = selectedTagIndex,
                        isFocused = currentFocusArea == FocusArea.TAGS,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(vertical = 8.dp),
                        onLeftmostTagsUpdated = onLeftmostTagsUpdated,
                        onTagPositionsUpdated = onTagPositionsUpdated
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            // Right column: Info Table
            Column(
                modifier = Modifier.weight(0.35f)
            ) {
                MediaInfoTable(
                    mediaDetails = media,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Color(0xFF3370FF),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                )
            }
        }
    }
}

