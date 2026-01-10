package ca.devmesh.seerrtv.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.CastMember
import ca.devmesh.seerrtv.model.CrewMember
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.MediaType
import ca.devmesh.seerrtv.model.SimilarMediaItem
import ca.devmesh.seerrtv.navigation.NavigationManager
import ca.devmesh.seerrtv.ui.state.FocusArea
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Cast, Crew, and Similar Media carousels for MediaDetails screen
 */
@Composable
fun MediaDetailsCarousels(
    media: MediaDetails,
    viewModel: SeerrViewModel,
    imageLoader: ImageLoader,
    currentFocusArea: Int,
    hasCast: Boolean,
    hasCrew: Boolean,
    hasSimilarMedia: Boolean,
    similarMediaItems: List<SimilarMediaItem>,
    selectedCastIndex: Int,
    selectedCrewIndex: Int,
    selectedSimilarMediaIndex: Int,
    onCastCountUpdate: (Int) -> Unit,
    onCrewCountUpdate: (Int) -> Unit,
    navigationManager: NavigationManager,
    coroutineScope: CoroutineScope,
    context: Context
) {
    // Cast and Crew Section
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 16.dp)
    ) {
        // Cast section
        if (hasCast) {
            onCastCountUpdate(media.credits.cast.size)
            Text(
                text = context.getString(R.string.mediaDetails_cast),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            PersonCarousel(
                people = media.credits.cast,
                imageLoader = imageLoader,
                selectedIndex = selectedCastIndex,
                isFocused = currentFocusArea == FocusArea.CAST,
                getPersonName = { (it as CastMember).name },
                getPersonRole = { (it as CastMember).character },
                getPersonProfilePath = { (it as CastMember).profilePath }
            )
        }
        // Crew section
        if (hasCrew) {
            Text(
                text = context.getString(R.string.mediaDetails_crew),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Simplified crew section - just show crew without creator combination
            onCrewCountUpdate(media.credits.crew.size)

            PersonCarousel(
                people = media.credits.crew,
                imageLoader = imageLoader,
                selectedIndex = selectedCrewIndex,
                isFocused = currentFocusArea == FocusArea.CREW,
                getPersonName = { (it as CrewMember).name },
                getPersonRole = { (it as CrewMember).job },
                getPersonProfilePath = { (it as CrewMember).profilePath }
            )
        }
    }

    // Similar Media Section - Simplified
    if (hasSimilarMedia && similarMediaItems.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 16.dp)
        ) {
            Text(
                text = if (media.mediaType == MediaType.MOVIE) {
                    context.getString(R.string.mediaDetails_similarMovies)
                } else {
                    context.getString(R.string.mediaDetails_similarSeries)
                },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            SimilarMediaCarousel(
                similarMedia = similarMediaItems,
                imageLoader = imageLoader,
                selectedIndex = selectedSimilarMediaIndex,
                isFocused = currentFocusArea == FocusArea.SIMILAR_MEDIA,
                onMediaClick = { mediaId, mediaType ->
                    navigationManager.navigateToDetails(
                        mediaId.toString(),
                        mediaType
                    )
                },
                onLoadNextPage = {
                    coroutineScope.launch {
                        viewModel.loadNextSimilarMediaPage(
                            media.id,
                            media.mediaType?.name?.lowercase() ?: "",
                            selectedSimilarMediaIndex
                        )
                    }
                }
            )
        }
    }
}

