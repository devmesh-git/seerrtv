package ca.devmesh.seerrtv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.model.SonarrLookupResult
import ca.devmesh.seerrtv.model.AuthType
import ca.devmesh.seerrtv.model.MediaRequestBody
import ca.devmesh.seerrtv.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class RequestViewModel @Inject constructor(
    private val apiService: SeerrApiService
) : ViewModel() {

    private val authErrorState = AuthenticationErrorState()
    val showAuthenticationError = authErrorState.showAuthenticationError
    
    // Reference to the main ViewModel to allow carousel refreshing
    private var mainViewModel: SeerrViewModel? = null

    /**
     * Sets the main SeerrViewModel reference for direct communication
     * This allows the RequestViewModel to update carousel state directly
     */
    fun setSeerrViewModel(viewModel: SeerrViewModel) {
        mainViewModel = viewModel
    }
    
    /**
     * Gets the main SeerrViewModel reference
     * @return The SeerrViewModel or null if not set
     */
    fun getSeerrViewModel(): SeerrViewModel? {
        return mainViewModel
    }

    /**
     * Lookup TV series in Sonarr by TMDB ID for series without tvdbId
     */
    suspend fun lookupSonarrSeries(tmdbId: Int): ApiResult<List<SonarrLookupResult>> {
        return apiService.lookupSonarrSeries(tmdbId)
    }

    fun getCurrentUserId(): Int? {
        return apiService.getCurrentUserInfo()?.id
    }

    fun getAuthType(): AuthType {
        return apiService.getAuthType()
    }


    // Helper methods to get server data for modal decision making
    fun getAvailableServers(mediaType: String, is4kRequest: Boolean): List<ca.devmesh.seerrtv.ui.ServerOption> {
        return when (mediaType.uppercase()) {
            "MOVIE" -> {
                val radarrData = mainViewModel?.radarrData?.value
                radarrData?.allServers?.filter { it.server.is4k == is4kRequest }
                    ?.map { server ->
                        ca.devmesh.seerrtv.ui.ServerOption(
                            id = server.server.id,
                            name = server.server.name,
                            type = ca.devmesh.seerrtv.ui.ServerType.RADARR,
                            is4k = server.server.is4k
                        )
                    } ?: emptyList()
            }
            "TV" -> {
                val sonarrData = mainViewModel?.sonarrData?.value
                sonarrData?.allServers?.filter { it.server.is4k == is4kRequest }
                    ?.map { server ->
                        ca.devmesh.seerrtv.ui.ServerOption(
                            id = server.server.id,
                            name = server.server.name,
                            type = ca.devmesh.seerrtv.ui.ServerType.SONARR,
                            is4k = server.server.is4k
                        )
                    } ?: emptyList()
            }
            else -> emptyList()
        }
    }

    fun getQualityProfiles(mediaType: String, is4kRequest: Boolean): List<ca.devmesh.seerrtv.model.Profile> {
        return when (mediaType.uppercase()) {
            "MOVIE" -> {
                val radarrData = mainViewModel?.radarrData?.value
                radarrData?.allServers?.filter { it.server.is4k == is4kRequest }
                    ?.flatMap { it.profiles } ?: emptyList()
            }
            "TV" -> {
                val sonarrData = mainViewModel?.sonarrData?.value
                sonarrData?.allServers?.filter { it.server.is4k == is4kRequest }
                    ?.flatMap { it.profiles } ?: emptyList()
            }
            else -> emptyList()
        }
    }

    fun getRootFolders(mediaType: String, is4kRequest: Boolean): List<ca.devmesh.seerrtv.model.SonarrRootFolder> {
        return when (mediaType.uppercase()) {
            "TV" -> {
                val sonarrData = mainViewModel?.sonarrData?.value
                sonarrData?.allServers?.filter { it.server.is4k == is4kRequest }
                    ?.flatMap { it.rootFolders } ?: emptyList()
            }
            else -> emptyList()
        }
    }

    // Consolidated method to check if modal is needed
    fun shouldShowModal(
        mediaDetails: ca.devmesh.seerrtv.model.MediaDetails,
        is4kRequest: Boolean,
        isFolderSelectionEnabled: Boolean
    ): Boolean {
        val mediaType = mediaDetails.mediaType?.toString() ?: return false
        
        // Check if server selection is needed
        val availableServers = getAvailableServers(mediaType, is4kRequest)
        val needsServerSelection = availableServers.size > 1
        
        // Check if seasons selection is needed (for TV shows)
        val needsSeasonsSelection = mediaDetails.mediaType == MediaType.TV &&
            mediaDetails.seasons?.isNotEmpty() == true
        
        // Check if quality profile selection is needed
        val qualityProfiles = getQualityProfiles(mediaType, is4kRequest)
        val needsQualitySelection = qualityProfiles.size > 1
        
        // Check if root folder selection is needed
        val rootFolders = getRootFolders(mediaType, is4kRequest)
        val needsFolderSelection = mediaDetails.mediaType == MediaType.TV &&
            rootFolders.size > 1 && isFolderSelectionEnabled
        
        // ADD DEBUG LOGGING HERE:
        Log.d("MediaDetailsButtons", "requestVM.shouldShowModal:")
        Log.d("MediaDetailsButtons", "  mediaType=$mediaType is4k=$is4kRequest servers=${availableServers.size} needsServer=$needsServerSelection")
        Log.d("MediaDetailsButtons", "  profiles=${qualityProfiles.size} needsQuality=$needsQualitySelection seasonsSelect=$needsSeasonsSelection folders=${rootFolders.size} needsFolder=$needsFolderSelection")
        
        val result = needsServerSelection || needsSeasonsSelection || needsQualitySelection || needsFolderSelection
        Log.d("MediaDetailsButtons", "  result=$result")
        
        return result
    }

    // Direct submission method for auto-requests
    fun submitRequestDirectly(
        mediaDetails: ca.devmesh.seerrtv.model.MediaDetails,
        is4kRequest: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Build the request with default selections
                val request = buildDefaultRequest(mediaDetails, is4kRequest)
                
                when (val result = requestMedia(request)) {
                    is ApiResult.Success -> {
                        // Directly set the refresh flag on the main ViewModel to ensure refresh happens
                        getSeerrViewModel()?.setRefreshRequired(true)
                        
                        // CRITICAL: Directly force a refresh of the RECENT_REQUESTS category
                        forceRefreshRecentRequests()
                        
                        Log.d("RequestViewModel", "🚨 AUTO-REQUEST SUBMITTED - Directly forcing refresh of RECENT_REQUESTS category")
                        onSuccess()
                    }
                    is ApiResult.Error -> {
                        // Check if this is the expected "Request for this media already exists" error
                        // This happens when requesting 4K version of a movie that already has HD version
                        val errorMessage = result.exception.message ?: ""
                        if (result.statusCode == 409 && errorMessage.contains("Request for this media already exists")) {
                            Log.d("RequestViewModel", "Auto-request already exists for this media (expected when requesting different tier) - treating as success")
                            // Still refresh to ensure UI is up to date
                            getSeerrViewModel()?.setRefreshRequired(true)
                            forceRefreshRecentRequests()
                            onSuccess()
                        } else {
                            onError("Error submitting request: ${result.exception.message}")
                        }
                    }
                    is ApiResult.Loading -> {
                        // Handle loading state if needed
                    }
                }
            } catch (e: Exception) {
                Log.e("RequestViewModel", "Error submitting request", e)
                onError("Error submitting request: ${e.message}")
            }
        }
    }

    private fun buildDefaultRequest(mediaDetails: ca.devmesh.seerrtv.model.MediaDetails, is4kRequest: Boolean): Map<String, Any?> {
        val request = mutableMapOf(
            "mediaId" to mediaDetails.id,
            "mediaType" to mediaDetails.mediaType.toString().lowercase(),
            "is4k" to is4kRequest,
            "tags" to emptyList<String>()
        )

        // Only include userId when using API key authentication
        if (getAuthType() == AuthType.ApiKey) {
            getCurrentUserId()?.let { userId ->
                request["userId"] = userId
            }
        }

        // Auto-select first available server if only one is available
        val mediaType = mediaDetails.mediaType?.toString() ?: return request
        val availableServers = getAvailableServers(mediaType, is4kRequest)
        if (availableServers.size == 1) {
            request["serverId"] = availableServers.first().id
        }

        when (mediaDetails.mediaType) {
            MediaType.MOVIE -> {
                // mediaId already carries the TMDB id for movies; omit duplicate tmdbId field
                // Use first quality profile if only one available
                val profiles = getQualityProfiles(mediaType, is4kRequest)
                if (profiles.size == 1) {
                    request["profileId"] = profiles.first().id
                }
            }
            MediaType.TV -> {
                mediaDetails.mediaInfo?.tvdbId?.let { request["tvdbId"] = it }
                // Use first quality profile if only one available
                val profiles = getQualityProfiles(mediaType, is4kRequest)
                if (profiles.size == 1) {
                    request["profileId"] = profiles.first().id
                }
                // Use first root folder if only one available
                val folders = getRootFolders(mediaType, is4kRequest)
                if (folders.size == 1) {
                    request["rootFolder"] = folders.first().path
                }
                // Select all available seasons - 4K-aware
                val availableSeasons = mediaDetails.seasons?.filter { season ->
                    val mediaInfoSeason = mediaDetails.mediaInfo?.seasons?.find { it.seasonNumber == season.seasonNumber }
                    val requestSeasonStatus = mediaDetails.mediaInfo?.requests
                        ?.filter { it.is4k == is4kRequest } // Only consider requests for the current tier
                        ?.flatMap { it.seasons }
                        ?.find { it.seasonNumber == season.seasonNumber }
                        ?.status
                    // Use the appropriate status based on the request tier
                    val status = if (is4kRequest) {
                        requestSeasonStatus ?: mediaInfoSeason?.status4k
                    } else {
                        requestSeasonStatus ?: mediaInfoSeason?.status
                    }
                    status == null || status < 2
                }?.map { it.seasonNumber } ?: emptyList()
                if (availableSeasons.isNotEmpty()) {
                    request["seasons"] = availableSeasons
                }
            }
            else -> {
                // Handle null case or any other unexpected values
            }
        }

        return request
    }

    suspend fun requestMedia(request: Map<String, Any?>): ApiResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = MediaRequestBody(
                    mediaType = request["mediaType"] as String,
                    mediaId = request["mediaId"] as Int,
                    tvdbId = request["tvdbId"] as? Int,
                    seasons = (request["seasons"] as? List<*>)?.mapNotNull { it as? Int },
                    is4k = request["is4k"] as? Boolean == true,
                    serverId = request["serverId"] as? Int,
                    profileId = request["profileId"] as? Int,
                    rootFolder = request["rootFolder"] as? String,
                    languageProfileId = request["languageProfileId"] as? Int,
                    userId = request["userId"] as? Int,
                    tags = (request["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                )
                val response = apiService.requestMedia(requestBody)
                when (response) {
                    is ApiResult.Success -> response
                    is ApiResult.Error -> {
                        if (handleApiError(response.exception, response.statusCode)) {
                            authErrorState.showError()
                        }
                        response
                    }
                    is ApiResult.Loading -> response
                }
            } catch (e: Exception) {
                Log.e("RequestViewModel", "Error requesting media: ${e.message}")
                ApiResult.Error(e)
            }
        }
    }

    private fun handleApiError(exception: Exception, statusCode: Int? = null): Boolean {
        return when (statusCode) {
            401, 403 -> {
                Log.w("RequestViewModel", "Authentication error: ${exception.message}")
                true // Indicates authentication error
            }
            else -> {
                Log.e("RequestViewModel", "API error: ${exception.message}")
                false // Not an authentication error
            }
        }
    }

    fun hideAuthenticationError() {
        authErrorState.hideError()
    }
    
    /**
     * Forces a refresh of the RECENT_REQUESTS category directly after a request
     * This method will be called after a successful media request
     */
    fun forceRefreshRecentRequests() {
        viewModelScope.launch {
            mainViewModel?.let { vm ->
                Log.d("RequestViewModel", "🔄 Direct force refresh of RECENT_REQUESTS - waiting 1 second for backend to process")
                delay(1000)
                vm.setRefreshRequired(true)
                vm.clearCategoryData(MediaCategory.RECENT_REQUESTS)
                vm.resetApiPagination(MediaCategory.RECENT_REQUESTS)
                vm.refreshCategoryWithForce(MediaCategory.RECENT_REQUESTS)
            } ?: Log.e("RequestViewModel", "🚨 Cannot refresh RECENT_REQUESTS - SeerrViewModel reference is null")
        }
    }
}
