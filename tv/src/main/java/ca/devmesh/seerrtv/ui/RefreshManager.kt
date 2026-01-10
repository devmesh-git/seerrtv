package ca.devmesh.seerrtv.ui

import android.content.Context
import android.util.Log
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.viewmodel.MediaCategory
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel
import coil3.ImageLoader

class RefreshManager {
    fun handleIssueSuccess(
        context: Context,
        viewModel: SeerrViewModel,
        mediaId: String,
        mediaType: String,
        setMessage: (String) -> Unit
    ) {
        setMessage(context.getString(R.string.issue_createdSuccessfully))
        viewModel.forceRefreshMediaDetails(mediaId, mediaType)
    }

    fun handleRequestSuccess(
        context: Context,
        viewModel: SeerrViewModel,
        mediaId: String,
        mediaType: String,
        mediaDetailsState: ApiResult<MediaDetails>?,
        setMessage: (String) -> Unit
    ) {
        viewModel.forceRefreshMediaDetails(mediaId, mediaType)

        setMessage(context.getString(R.string.requestModal_requestedSuccessfully))
        viewModel.setRefreshRequired(true)

        (mediaDetailsState as? ApiResult.Success<MediaDetails>)?.data?.let { details ->
            Log.d("RefreshManager", "Updating media across categories after request")
            viewModel.setMediaToRefresh(details)
        }

        viewModel.clearCategoryData(MediaCategory.RECENT_REQUESTS)
        viewModel.resetApiPagination(MediaCategory.RECENT_REQUESTS)
        viewModel.refreshCategoryWithForce(MediaCategory.RECENT_REQUESTS)

        try {
            val imageLoader = ImageLoader.Builder(context).build()
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
            Log.d("RefreshManager", "Cleared image caches after request")
        } catch (_: Exception) {}
    }

    fun handleAutoRequestSuccess(
        context: Context,
        viewModel: SeerrViewModel,
        mediaId: String,
        mediaType: String,
        mediaDetailsState: ApiResult<MediaDetails>?,
        setMessage: (String) -> Unit
    ) {
        handleRequestSuccess(context, viewModel, mediaId, mediaType, mediaDetailsState, setMessage)
    }

    fun handleRequestDeleteSuccess(
        viewModel: SeerrViewModel,
        mediaId: String,
        mediaType: String
    ) {
        viewModel.forceRefreshMediaDetails(mediaId, mediaType)
        viewModel.clearCategoryData(MediaCategory.RECENT_REQUESTS)
        viewModel.resetApiPagination(MediaCategory.RECENT_REQUESTS)
        viewModel.refreshCategoryWithForce(MediaCategory.RECENT_REQUESTS)
    }
}


