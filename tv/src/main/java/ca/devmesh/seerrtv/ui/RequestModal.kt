package ca.devmesh.seerrtv.ui

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.lifecycle.viewModelScope
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.model.AuthType
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.MediaType
import ca.devmesh.seerrtv.model.Profile
import ca.devmesh.seerrtv.model.SonarrRootFolder
import ca.devmesh.seerrtv.navigation.NavigationManager
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.Tag
import ca.devmesh.seerrtv.model.SonarrLookupResult
import ca.devmesh.seerrtv.ui.components.ActionButton
import ca.devmesh.seerrtv.ui.components.AuthenticationErrorHandler
import ca.devmesh.seerrtv.ui.components.CustomCheckmarkIcon
import ca.devmesh.seerrtv.ui.components.RequestStatus
import ca.devmesh.seerrtv.util.SafeKeyEventHandler
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import ca.devmesh.seerrtv.viewmodel.RequestViewModel
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel
import kotlin.collections.emptyList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ServerOption(val id: Int, val name: String, val type: ServerType, val is4k: Boolean = false)
enum class ServerType { RADARR, SONARR }

sealed class ModalSelection {
    data class Seasons(val selectedSeasons: Set<Int>) : ModalSelection()
    data class QualityProfile(val profile: Profile) : ModalSelection()
    data class RootFolder(val folder: SonarrRootFolder) : ModalSelection()
    data class Tags(val selectedTags: Set<Int>) : ModalSelection()
}

data class SeasonData(
    val seasonNumber: Int,
    val seasonName: String,
    val episodeCount: Int, // Number of episodes
    val status: Int? // Status of the season, can be null
)

class RequestModalController(
    private val initialMenuItems: List<String>,
    private val mediaDetails: MediaDetails,
    private val onCancel: () -> Unit,
    private val onRequest: () -> Unit,
    private val viewModel: RequestViewModel,
    private val mainViewModel: SeerrViewModel,
    val servers: List<ServerOption>,
    context: Context,
    private val is4kRequest: Boolean = false
) : ViewModel() {
    private var lastMainMenuIndex: Int = 0
    var currentFocus by mutableStateOf<FocusState>(FocusState.MainMenu(0))
    var selectedOption by mutableStateOf<String?>(null)
    var selectedSeasons by mutableStateOf(emptySet<Int>())
    var selectedQualityProfile by mutableStateOf<Profile?>(null)
    var selectedRootFolder by mutableStateOf<SonarrRootFolder?>(null)
    var selectedTags by mutableStateOf<Set<Int>>(emptySet())
    var requestStatus by mutableStateOf<RequestStatus>(RequestStatus.Idle)
    private val _isLoading = MutableStateFlow(true)
    private val _selectedServer = MutableStateFlow(servers.firstOrNull())
    val selectedServer: StateFlow<ServerOption?> = _selectedServer
    private val _qualityProfiles = MutableStateFlow<List<Profile>>(emptyList())
    val qualityProfiles: StateFlow<List<Profile>> = _qualityProfiles
    private val _rootFolders = MutableStateFlow<List<SonarrRootFolder>>(emptyList())
    val rootFolders: StateFlow<List<SonarrRootFolder>> = _rootFolders
    private val _availableTags = MutableStateFlow<List<Tag>>(emptyList())
    val availableTags: StateFlow<List<Tag>> = _availableTags
    var showMessage by mutableStateOf<Pair<String, Boolean>?>(null)
    private val _servers = MutableStateFlow<List<ServerOption>>(emptyList())
    private val _availableServers = MutableStateFlow<List<ServerOption>>(emptyList())
    val availableServers: StateFlow<List<ServerOption>> = _availableServers

    // Lookup mode states
    var isLookupMode by mutableStateOf(false)
    var lookupResults by mutableStateOf<List<SonarrLookupResult>>(emptyList())
    var selectedLookupIndex by mutableIntStateOf(0)
    var isLoadingLookup by mutableStateOf(false)
    var lookupError by mutableStateOf<String?>(null)
    var selectedTvdbId by mutableStateOf<Int?>(null)

    // Store string resources as constants at initialization time
    private val serverMenuText = context.getString(R.string.requestModal_serverMenu)
    private val seasonsMenuText = context.getString(R.string.requestModal_seasonsMenu)
    private val qualityMenuText = context.getString(R.string.requestModal_qualityMenu)
    private val folderMenuText = context.getString(R.string.requestModal_folderMenu)
    private val tagsMenuText = context.getString(R.string.requestModal_tagsMenu)
    private val selectSeasonError = context.getString(R.string.requestModal_selectSeasonError)
    private val requestSuccessText = context.getString(R.string.requestModal_requestedSuccessfully)
    private val errorSubmittingText = context.getString(R.string.requestModal_errorSubmittingRequest)
    private val lookupErrorText = context.getString(R.string.requestModal_lookupError)

    private val _menuItems = MutableStateFlow(initialMenuItems)
    val menuItems: StateFlow<List<String>> = _menuItems
    var seasons: List<SeasonData> = emptyList()
    private val isFolderSelectionEnabled: Boolean = SharedPreferencesUtil.isFolderSelectionEnabled(context)
    private var lastAction: (() -> Unit)? = null

    private fun filterMenuItems(items: List<String>): List<String> {
        val profileCount = qualityProfiles.value.size
        val folderCount = rootFolders.value.size
        val tagsCount = availableTags.value.size
        val shouldShowServer = shouldShowServerSelection(is4kRequest)
        val shouldShowSeasons = mediaDetails.mediaType == MediaType.TV && mediaDetails.seasons?.isNotEmpty() == true
        val shouldShowQuality = profileCount > 1
        val shouldShowFolder = mediaDetails.mediaType == MediaType.TV && folderCount > 1 && isFolderSelectionEnabled
        val shouldShowTags = tagsCount > 0

        return items.filter {
            when (it) {
                serverMenuText -> shouldShowServer
                seasonsMenuText -> shouldShowSeasons
                qualityMenuText -> shouldShowQuality
                folderMenuText -> shouldShowFolder
                tagsMenuText -> shouldShowTags
                else -> false
            }
        }
    }

    // Update menu items whenever relevant data changes
    init {
        viewModelScope.launch {
            // Combine all relevant flows that affect menu visibility
            combine(
                availableServers,
                qualityProfiles,
                rootFolders,
                availableTags
            ) { servers, profiles, folders, tags ->
                // Always update menu items when data changes, regardless of server availability
                // This ensures proper filtering even when no backend servers are configured
                _menuItems.value = filterMenuItems(initialMenuItems)

                if (profiles.size == 1) {
                    selectedQualityProfile = profiles[0]
                }
                // For root folders, we still show the menu if there's only one option
                if (folders.size == 1 && !isFolderSelectionEnabled) {
                    selectedRootFolder = folders[0]
                }
                // Don't auto-select tags by default

                // Set initial focus or validate current focus
                validateMainMenuFocus()
            }.collect { }
        }
        
        // Note: Controller is recreated when is4kRequest changes, so no need to monitor changes
    }

    /**
     * Check if lookup is required for TV series without tvdbId
     */
    private fun isLookupRequired(): Boolean {
        return mediaDetails.mediaType == MediaType.TV &&
               mediaDetails.mediaInfo?.tvdbId == null
    }

    /**
     * Perform Sonarr lookup for TV series without tvdbId
     */
    private fun performSonarrLookup(context: Context) {
        if (mediaDetails.mediaType != MediaType.TV) return

        // Set lookup mode immediately to prevent main modal from showing
        isLookupMode = true
        isLoadingLookup = true
        lookupError = null

        viewModelScope.launch {
            try {
                val tmdbId = mediaDetails.mediaInfo?.tmdbId ?: mediaDetails.id
                Log.d("RequestModalController", "Performing Sonarr lookup for TMDB ID: $tmdbId")

                when (val result = viewModel.lookupSonarrSeries(tmdbId)) {
                    is ApiResult.Success -> {
                        lookupResults = result.data.take(6) // Limit to first 6 results
                        Log.d("RequestModalController", "Found ${lookupResults.size} series matches (limited to 6)")

                        if (lookupResults.isNotEmpty()) {
                            // Set initial focus to first item and scroll to it
                            currentFocus = FocusState.LookupSelection(0)
                            selectedLookupIndex = 0
                            // Auto-select if only one result
                            if (lookupResults.size == 1) {
                                selectLookupResult(0)
                            }
                        } else {
                            lookupError = context.getString(R.string.requestModal_noSeriesFound)
                        }
                    }
                    is ApiResult.Error -> {
                        Log.e("RequestModalController", "Lookup failed: ${result.exception.message}")
                        lookupError = context.getString(R.string.requestModal_errorLookingUpSeries, result.exception.message)
                    }
                    is ApiResult.Loading -> {
                        // Should not happen in this context
                    }
                }
            } catch (e: Exception) {
                Log.e("RequestModalController", "Lookup error", e)
                lookupError = "$lookupErrorText: ${e.message}"
            } finally {
                isLoadingLookup = false
            }
        }
    }

    /**
     * Select a series from lookup results and continue with normal flow
     */
    fun selectLookupResult(index: Int) {
        if (index < 0 || index >= lookupResults.size) return

        val selectedSeries = lookupResults[index]
        Log.d("RequestModalController", "Selected series: ${selectedSeries.title} (TVDB ID: ${selectedSeries.tvdbId})")

        // Store the selected TVDB ID for use in the request
        selectedTvdbId = selectedSeries.tvdbId

        // Exit lookup mode and continue with normal initialization
        isLookupMode = false
        lookupResults = emptyList()

        // Re-run server configuration to get proper server data for this series
        getServerConfiguration()
    }

    sealed class FocusState {
        data class MainMenu(val index: Int) : FocusState()
        data class SubMenu(val index: Int) : FocusState()
        object CancelButton : FocusState()
        object RequestButton : FocusState()
        data class LookupSelection(val index: Int) : FocusState()
        object LookupCancelButton : FocusState()
    }
    sealed class RequestStatus {
        object Idle : RequestStatus()
        object Loading : RequestStatus()
        data class Success(val message: String) : RequestStatus()
        data class Error(val message: String) : RequestStatus()
    }

    // Track when modal was opened to prevent Enter key leakage
    private val modalOpenTime = System.currentTimeMillis()
    
    init {
        Log.d("RequestModalController", "Initializing RequestModalController - is4kRequest: $is4kRequest")
        resetAllSelections()

        // Check if lookup is required for TV series without tvdbId
        if (isLookupRequired()) {
            Log.d("RequestModalController", "TV series without tvdbId detected - performing Sonarr lookup")
            performSonarrLookup(context)
        } else {
            // Apply initial menu filtering immediately
            _menuItems.value = filterMenuItems(initialMenuItems)
            getServerConfiguration()
        }
    }
    
    /**
     * Reset all selections when switching between 4K and regular requests
     * This ensures clean state for each new request type
     */
    private fun resetAllSelections() {
        Log.d("RequestModalController", "Resetting all selections for new request type")
        selectedOption = null
        selectedSeasons = emptySet()
        selectedQualityProfile = null
        selectedRootFolder = null
        selectedTags = emptySet()
        currentFocus = FocusState.MainMenu(0)
        lastMainMenuIndex = 0
        _qualityProfiles.value = emptyList()
        _rootFolders.value = emptyList()
        _availableTags.value = emptyList()
        _selectedServer.value = null
        _availableServers.value = emptyList()
        _servers.value = emptyList()
        // Reset lookup states
        isLookupMode = false
        lookupResults = emptyList()
        selectedLookupIndex = 0
        isLoadingLookup = false
        lookupError = null
        selectedTvdbId = null
        // Don't filter menu items during reset - wait until quality profiles are loaded
        _menuItems.value = initialMenuItems
        showMessage = null
        requestStatus = RequestStatus.Idle
        _isLoading.value = true
    }

    private fun getServerConfiguration() {
        viewModelScope.launch {
            // Use shared server data from the main ViewModel directly
            mediaDetails.mediaType?.let { mediaType ->
                // Set up available servers based on shared configuration
                when (mediaType) {
                    MediaType.MOVIE -> {
                        mainViewModel.radarrData.first { it != null }?.let { radarrData ->
                            val serversList = radarrData.allServers.map { server ->
                                server.server.let { srv ->
                                    ServerOption(srv.id, srv.name, ServerType.RADARR, srv.is4k)
                                }
                            }
                            Log.d("RequestModalController", "Got ${serversList.size} RADARR servers")
                            _servers.value = serversList
                            // Immediately select the first server if there's only one
                            if (serversList.size == 1) {
                                Log.d("RequestModalController", "Auto-selecting single RADARR server: ${serversList.first().name}")
                                selectServer(serversList.first())
                            }
                        }
                    }
                    MediaType.TV -> {
                        mainViewModel.sonarrData.first { it != null }?.let { sonarrData ->
                            val serversList = sonarrData.allServers.map { server ->
                                server.server.let { srv ->
                                    ServerOption(srv.id, srv.name, ServerType.SONARR, srv.is4k)
                                }
                            }
                            Log.d("RequestModalController", "Got ${serversList.size} SONARR servers")
                            _servers.value = serversList
                            // Immediately select the first server if there's only one
                            if (serversList.size == 1) {
                                Log.d("RequestModalController", "Auto-selecting single SONARR server: ${serversList.first().name}")
                                selectServer(serversList.first())
                            }
                        }
                    }
                }
                
                // Update available servers for the current tier
                updateAvailableServersForTier()
                
                // Select the first available server by default (if multiple servers)
                if (selectedServer.value == null && _availableServers.value.isNotEmpty()) {
                    Log.d("RequestModalController", "Selecting first available server: ${_availableServers.value.first().name}")
                    selectServer(_availableServers.value.first())
                }
            }
            _isLoading.value = false
            
            // Debug log final server selection state
            if (selectedServer.value != null) {
                Log.d("RequestModalController", "Final server selection: ${selectedServer.value?.name}")
            } else {
                Log.e("RequestModalController", "No server selected after initialization!")
                if (_availableServers.value.isNotEmpty()) {
                    Log.d("RequestModalController", "Attempting emergency server selection with ${_availableServers.value.size} available servers")
                    selectServer(_availableServers.value.first())
                }
            }
        }
        
        // Set up season data if this is a TV show
        if (mediaDetails.mediaType == MediaType.TV) {
            seasons = mediaDetails.seasons?.map { season ->
                val mediaInfoSeason = mediaDetails.mediaInfo?.seasons?.find { it.seasonNumber == season.seasonNumber }
                // Check for a request status for this season - 4K-aware
                val requestSeasonStatus = mediaDetails.mediaInfo?.requests
                    ?.filter { it.is4k == is4kRequest } // Only consider requests for the current tier
                    ?.flatMap { it.seasons }
                    ?.find { it.seasonNumber == season.seasonNumber }
                    ?.status
                // Use the appropriate status based on the request tier
                val seasonStatus = if (is4kRequest) {
                    requestSeasonStatus ?: mediaInfoSeason?.status4k
                } else {
                    requestSeasonStatus ?: mediaInfoSeason?.status
                }
                SeasonData(
                    seasonNumber = season.seasonNumber,
                    seasonName = season.name ?: "",
                    episodeCount = season.episodeCount ?: 0,
                    status = seasonStatus
                )
            }?.sortedByDescending { it.seasonNumber } ?: emptyList()
            Log.d("RequestModalController", "Final processed seasons for ${if (is4kRequest) "4K" else "regular"} request: $seasons")
            // Debug log each season's status
            seasons.forEach { season ->
                Log.d("RequestModalController", "Season ${season.seasonNumber}: status=${season.status}, name=${season.seasonName}")
            }
        }
    }

    // Helper functions for 4K-aware server selection
    private fun getDefaultServer(is4k: Boolean, mediaType: MediaType?): ServerOption? {
        return when (mediaType) {
            MediaType.MOVIE -> {
                val radarrServers = mainViewModel.radarrData.value?.allServers ?: emptyList()
                // Prefer default within the requested tier (4K vs regular)
                (radarrServers.find { it.server.is4k == is4k && it.server.isDefault }
                    ?: radarrServers.find { it.server.is4k == is4k })
                    ?.let { ServerOption(it.server.id, it.server.name, ServerType.RADARR, it.server.is4k) }
            }
            MediaType.TV -> {
                val sonarrServers = mainViewModel.sonarrData.value?.allServers ?: emptyList()
                (sonarrServers.find { it.server.is4k == is4k && it.server.isDefault }
                    ?: sonarrServers.find { it.server.is4k == is4k })
                    ?.let { ServerOption(it.server.id, it.server.name, ServerType.SONARR, it.server.is4k) }
            }
            else -> null
        }
    }

    private fun getServersForTier(is4k: Boolean, mediaType: MediaType?): List<ServerOption> {
        return when (mediaType) {
            MediaType.MOVIE -> {
                val radarrServers = mainViewModel.radarrData.value?.allServers ?: emptyList()
                radarrServers.filter { it.server.is4k == is4k }
                    .map { ServerOption(it.server.id, it.server.name, ServerType.RADARR, it.server.is4k) }
            }
            MediaType.TV -> {
                val sonarrServers = mainViewModel.sonarrData.value?.allServers ?: emptyList()
                sonarrServers.filter { it.server.is4k == is4k }
                    .map { ServerOption(it.server.id, it.server.name, ServerType.SONARR, it.server.is4k) }
            }
            else -> emptyList()
        }
    }

    // Tier-based server selection visibility logic
    fun shouldShowServerSelection(is4k: Boolean): Boolean {
        val tierServers = getServersForTier(is4k, mediaDetails.mediaType)
        return tierServers.size > 1
    }
    
    // Update available servers based on current tier
    private fun updateAvailableServersForTier() {
        val tierServers = getServersForTier(is4kRequest, mediaDetails.mediaType)
        _availableServers.value = tierServers
        Log.d("RequestModalController", "Updated available servers for tier (4K: $is4kRequest): ${tierServers.size} servers")
        
        // Validate that we have servers for the requested tier
        if (tierServers.isEmpty()) {
            Log.w("RequestModalController", "No servers available for tier (4K: $is4kRequest). This may indicate a configuration issue.")
            // Fall back to regular servers if 4K servers are not available
            if (is4kRequest) {
                Log.d("RequestModalController", "Falling back to regular servers for 4K request")
                val regularServers = getServersForTier(false, mediaDetails.mediaType)
                _availableServers.value = regularServers
            }
        }
    }

    fun toggleSeasonSelection(seasonNumber: Int?) {
        selectedSeasons = when (seasonNumber) {
            null -> {
                // Get all selectable seasons (status null, 0, 1, or 7 for seasons that can be requested)
                val selectableSeasons = seasons.filter {
                    it.status == null || it.status == 0 || it.status == 1 || it.status == 7
                }.map { it.seasonNumber }.toSet()

                // If all selectable seasons are currently selected, clear the selection
                // Otherwise, select all selectable seasons
                if (selectableSeasons.all { it in selectedSeasons }) {
                    emptySet()
                } else {
                    selectableSeasons
                }
            }
            in selectedSeasons -> selectedSeasons - seasonNumber
            else -> selectedSeasons + seasonNumber
        }
        onSubMenuItemSelected(ModalSelection.Seasons(selectedSeasons))
    }

    fun toggleTagSelection(tagId: Int?) {
        selectedTags = when (tagId) {
            null -> {
                // Get all available tags
                val allTags = availableTags.value.map { it.id }.toSet()

                // If all tags are currently selected, clear the selection
                // Otherwise, select all tags (Jellyseerr behavior)
                if (allTags.all { it in selectedTags }) {
                    emptySet()
                } else {
                    allTags
                }
            }
            in selectedTags -> selectedTags - tagId
            else -> selectedTags + tagId
        }
        onSubMenuItemSelected(ModalSelection.Tags(selectedTags))
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        // Handle lookup mode navigation first
        if (isLookupMode) {
            return handleLookupKeyEvent(event)
        }

        return when {
            event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                moveFocusUp()
                true
            }
            event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                moveFocusDown()
                true
            }
            event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown -> {
                when (currentFocus) {
                    is FocusState.MainMenu -> openSubmenu()
                    FocusState.CancelButton -> currentFocus = FocusState.RequestButton
                    else -> {}
                }
                true
            }
            event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown -> {
                when (currentFocus) {
                    is FocusState.SubMenu -> closeSubmenu()
                    FocusState.RequestButton -> currentFocus = FocusState.CancelButton
                    else -> {}
                }
                true
            }
            (KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) || event.key == Key.DirectionRight) && event.type == KeyEventType.KeyDown -> {
                when (currentFocus) {
                    FocusState.CancelButton -> {
                        // Add a delay before closing to ensure the event is not handled by the parent
                        viewModelScope.launch {
                            delay(300)
                            onCancel()
                        }
                        true // Always consume the event when cancel is focused
                    }
                    else -> {
                        handleEnter()
                        true
                    }
                }
            }
            event.key == Key.Back && event.type == KeyEventType.KeyDown -> {
                handleBack()
                true
            }
            else -> false
        }
    }

    fun handleLookupKeyEvent(event: KeyEvent): Boolean {
        // Only handle lookup-specific focus states
        return when (currentFocus) {
            is FocusState.LookupSelection -> {
                when {
                    event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                        val currentIndex = (currentFocus as FocusState.LookupSelection).index
                        if (currentIndex > 0) {
                            selectedLookupIndex = currentIndex - 1
                            currentFocus = FocusState.LookupSelection(selectedLookupIndex)
                        }
                        true
                    }
                    event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                        val currentIndex = (currentFocus as FocusState.LookupSelection).index
                        if (currentIndex < lookupResults.size - 1) {
                            selectedLookupIndex = currentIndex + 1
                            currentFocus = FocusState.LookupSelection(selectedLookupIndex)
                        } else {
                            // Move to cancel button
                            currentFocus = FocusState.LookupCancelButton
                        }
                        true
                    }
                    (KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) || event.key == Key.DirectionRight) && event.type == KeyEventType.KeyDown -> {
                        selectLookupResult((currentFocus as FocusState.LookupSelection).index)
                        true
                    }
                    else -> false
                }
            }
            FocusState.LookupCancelButton -> {
                when {
                    event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                        // Move to last lookup item
                        selectedLookupIndex = lookupResults.size - 1
                        currentFocus = FocusState.LookupSelection(selectedLookupIndex)
                        true
                    }
                    (KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) || event.key == Key.DirectionRight) && event.type == KeyEventType.KeyDown -> {
                        onCancel()
                        true
                    }
                    else -> false
                }
            }
            else -> false // Not in lookup mode, let main handler deal with it
        }
    }

    private fun moveFocusUp() {
        currentFocus = when (currentFocus) {
            is FocusState.MainMenu -> {
                val newIndex = (currentFocus as FocusState.MainMenu).index - 1
                if (menuItems.value.isEmpty()) {
                    FocusState.CancelButton
                } else if (newIndex >= 0) {
                    FocusState.MainMenu(newIndex)
                } else {
                    FocusState.MainMenu(0)
                }
            }
            is FocusState.SubMenu -> {
                val newIndex = (currentFocus as FocusState.SubMenu).index - 1
                // Check if in Seasons or Tags submenu to allow navigation to ALL item
                if ((selectedOption == seasonsMenuText || selectedOption == tagsMenuText) && newIndex == -1) {
                    FocusState.SubMenu(-1) // Allow focus on ALL item
                } else if (newIndex >= 0) {
                    FocusState.SubMenu(newIndex)
                } else {
                    currentFocus // Stay in the current focus if at the top
                }
            }
            FocusState.RequestButton -> {
                if (menuItems.value.isEmpty()) {
                    FocusState.CancelButton
                } else {
                    FocusState.MainMenu(menuItems.value.lastIndex)
                }
            }
            FocusState.CancelButton -> {
                if (menuItems.value.isEmpty()) {
                    FocusState.RequestButton
                } else {
                    FocusState.MainMenu(menuItems.value.lastIndex)
                }
            }
            is FocusState.LookupSelection -> {
                val newIndex = (currentFocus as FocusState.LookupSelection).index - 1
                if (newIndex >= 0) {
                    selectedLookupIndex = newIndex
                    FocusState.LookupSelection(newIndex)
                } else {
                    currentFocus // Stay at top
                }
            }
            FocusState.LookupCancelButton -> {
                selectedLookupIndex = lookupResults.size - 1
                FocusState.LookupSelection(selectedLookupIndex)
            }
        }
    }

    private fun moveFocusDown() {
        currentFocus = when (currentFocus) {
            is FocusState.MainMenu -> {
                val newIndex = (currentFocus as FocusState.MainMenu).index + 1
                if (newIndex < menuItems.value.size) {
                    lastMainMenuIndex = newIndex
                    FocusState.MainMenu(newIndex)
                } else {
                    FocusState.CancelButton
                }
            }
            is FocusState.SubMenu -> {
                val newIndex = (currentFocus as FocusState.SubMenu).index + 1
                val subMenuSize = getSubMenuSize()
                if (newIndex < subMenuSize) {
                    FocusState.SubMenu(newIndex)
                } else {
                    currentFocus
                }
            }
            FocusState.CancelButton -> FocusState.RequestButton
            FocusState.RequestButton -> currentFocus
            is FocusState.LookupSelection -> {
                val newIndex = (currentFocus as FocusState.LookupSelection).index + 1
                if (newIndex < lookupResults.size) {
                    selectedLookupIndex = newIndex
                    FocusState.LookupSelection(newIndex)
                } else {
                    // Move to cancel button when at the bottom of the list
                    FocusState.LookupCancelButton
                }
            }
            FocusState.LookupCancelButton -> {
                selectedLookupIndex = 0
                FocusState.LookupSelection(selectedLookupIndex)
            }
        }
    }

    private fun openSubmenu() {
        if (currentFocus is FocusState.MainMenu) {
            val index = (currentFocus as FocusState.MainMenu).index
            if (menuItems.value.isNotEmpty() && index < menuItems.value.size) {
                val newSelectedOption = menuItems.value[index]
                selectedOption = newSelectedOption
                // For menus with headers, seasons starts with header, tags starts with first item
                val initialFocusIndex = if (newSelectedOption == seasonsMenuText) {
                    -1 // Header/ALL toggle for seasons
                } else {
                    0 // First item for other menus including tags
                }
                currentFocus = FocusState.SubMenu(initialFocusIndex)
            }
        }
    }

    private fun closeSubmenu() {
        if (currentFocus is FocusState.SubMenu) {
            currentFocus = FocusState.MainMenu(lastMainMenuIndex)
            selectedOption = null
        }
    }

    private fun handleEnter() {
        // Add debouncing to prevent Enter key from parent button being processed immediately
        val currentTime = System.currentTimeMillis()
        val timeSinceModalOpen = currentTime - modalOpenTime
        
        if (timeSinceModalOpen < 500) { // 500ms debouncing
            Log.d("RequestModalController", "⏸️ Enter key ignored - too soon after modal open (${timeSinceModalOpen}ms)")
            return
        }
        
        Log.d("RequestModalController", "✅ Enter key accepted - modal open for ${timeSinceModalOpen}ms")
        
        when (currentFocus) {
            is FocusState.MainMenu -> {
                // Safety check: if menu is empty, move focus to cancel button
                if (menuItems.value.isEmpty()) {
                    currentFocus = FocusState.CancelButton
                } else {
                    openSubmenu()
                }
            }
            is FocusState.SubMenu -> {
                val index = (currentFocus as FocusState.SubMenu).index
                when (selectedOption) {
                    serverMenuText -> {
                        val serversList = availableServers.value
                        if (index >= 0 && index < serversList.size) {
                            selectServer(serversList[index])
                        }
                    }
                    seasonsMenuText -> {
                        // Check if the selected season is available before toggling
                        if (seasons.isNotEmpty()) {
                            val selectedSeason = if (index == -1) null else seasons.getOrNull(index)
                            if (selectedSeason?.status == null || selectedSeason.status == 0 || selectedSeason.status == 1 || selectedSeason.status == 7) { // Allow toggling for not requested, pending, or deleted seasons
                                toggleSeasonSelection(selectedSeason?.seasonNumber)
                            }
                        }
                    }
                    qualityMenuText -> {
                        if (index >= 0 && index < qualityProfiles.value.size) {
                            onSubMenuItemSelected(ModalSelection.QualityProfile(qualityProfiles.value[index]))
                        }
                    }
                    folderMenuText -> {
                        if (index >= 0 && index < rootFolders.value.size) {
                            onSubMenuItemSelected(ModalSelection.RootFolder(rootFolders.value[index]))
                        }
                    }
                    tagsMenuText -> {
                        if (index == -1) {
                            // Header/ALL toggle
                            toggleTagSelection(null)
                        } else if (index >= 0 && index < availableTags.value.size) {
                            toggleTagSelection(availableTags.value[index].id)
                        }
                    }
                    else -> Log.e("RequestModalController", "Unknown selected option: $selectedOption")
                }
            }
            FocusState.CancelButton -> {
                onCancel()
                return
            }
            FocusState.RequestButton -> onRequestSubmit(onRequest)
            is FocusState.LookupSelection -> {
                selectLookupResult((currentFocus as FocusState.LookupSelection).index)
            }
            FocusState.LookupCancelButton -> {
                onCancel()
                return
            }
        }
    }

    fun handleBack() {
        when (currentFocus) {
            is FocusState.SubMenu -> {
                Log.d("RequestModalController", "Back pressed while in submenu - closing submenu")
                closeSubmenu()
            }
            else -> {
                Log.d("RequestModalController", "Back pressed while in main menu - closing modal")
                // Add a small delay to ensure the back event is properly consumed
                viewModelScope.launch {
                    delay(100)
                    onCancel()
                }
            }
        }
    }

    private fun getSubMenuSize(): Int {
        return when (selectedOption) {
            serverMenuText -> availableServers.value.size
            seasonsMenuText -> seasons.size
            qualityMenuText -> qualityProfiles.value.size
            folderMenuText -> rootFolders.value.size
            tagsMenuText -> availableTags.value.size
            else -> 0
        }
    }

    fun onSubMenuItemSelected(selection: ModalSelection) {
        when (selection) {
            is ModalSelection.Seasons -> selectedSeasons = selection.selectedSeasons
            is ModalSelection.QualityProfile -> selectedQualityProfile = selection.profile
            is ModalSelection.RootFolder -> selectedRootFolder = selection.folder
            is ModalSelection.Tags -> selectedTags = selection.selectedTags
        }
    }

    fun onRequestSubmit(onSuccess: () -> Unit) {
        setLastAction {
            viewModelScope.launch {
                try {
                    _isLoading.value = true
                    showMessage = null

                    // Log current state for debugging
                    Log.d("RequestModalController", "Starting request submission")
                    Log.d("RequestModalController", "Servers available: ${availableServers.value.size}")
                    Log.d("RequestModalController", "Current server: ${selectedServer.value?.name ?: "NULL"}")
                    
                    if (mediaDetails.mediaType == MediaType.TV && selectedSeasons.isEmpty()) {
                        showMessage = Pair(selectSeasonError, true)
                        return@launch
                    }

                    // Enhanced server selection - try all possible ways to get a server
                    if (selectedServer.value == null) {
                        Log.d("RequestModalController", "No server selected, attempting to find one")
                        
                        // Try to select the first server if there's only one
                        if (availableServers.value.size == 1) {
                            Log.d("RequestModalController", "Found exactly one server, selecting it")
                            selectServer(availableServers.value.first())
                            // Give a short delay for the selection to propagate
                            delay(100)
                        } 
                        // If there are multiple servers, just select the first one
                        else if (availableServers.value.isNotEmpty()) {
                            Log.d("RequestModalController", "Found ${availableServers.value.size} servers, selecting the first one")
                            selectServer(availableServers.value.first())
                            // Give a short delay for the selection to propagate
                            delay(100)
                        }
                    }
                    
                    // If no quality profile is selected yet but there's only one available, auto-select it
                    if (selectedQualityProfile == null && qualityProfiles.value.size == 1) {
                        selectedQualityProfile = qualityProfiles.value.first()
                    }
                    
                    // If no root folder is selected yet but there's only one available, auto-select it
                    if (selectedRootFolder == null && rootFolders.value.size == 1 && mediaDetails.mediaType == MediaType.TV) {
                        selectedRootFolder = rootFolders.value.first()
                    }
                    
                    // Check server after our selection attempts
                    val currentServer = selectedServer.value
                    
                    // If no servers available, proceed without server selection
                    if (availableServers.value.isEmpty()) {
                        Log.d("RequestModalController", "No servers available, proceeding with default server")
                    } else {
                        Log.d("RequestModalController", "Using server: ${currentServer?.name ?: "DEFAULT"}")
                    }

                    val request = buildRequestMap(is4kRequest)
                    when (val result = viewModel.requestMedia(request)) {
                        is ApiResult.Success -> {
                            showMessage = Pair(requestSuccessText, false)
                            // Directly set the refresh flag on the main ViewModel to ensure refresh happens
                            viewModel.getSeerrViewModel()?.setRefreshRequired(true)
                            
                            // CRITICAL: Directly force a refresh of the RECENT_REQUESTS category
                            viewModel.forceRefreshRecentRequests()
                            
                            Log.d(
                                "RequestModalController", 
                                "🚨 NEW REQUEST SUBMITTED - Directly forcing refresh of RECENT_REQUESTS category"
                            )
                            onSuccess()
                        }
                        is ApiResult.Error -> {
                            // Check if this is the expected "Request for this media already exists" error
                            // This happens when requesting 4K version of a movie that already has HD version
                            val errorMessage = result.exception.message ?: ""
                            if (result.statusCode == 409 && errorMessage.contains("Request for this media already exists")) {
                                Log.d("RequestModalController", "Request already exists for this media (expected when requesting different tier) - treating as success")
                                showMessage = Pair(requestSuccessText, false)
                                // Still refresh to ensure UI is up to date
                                viewModel.getSeerrViewModel()?.setRefreshRequired(true)
                                viewModel.forceRefreshRecentRequests()
                                onSuccess()
                            } else {
                                showMessage = Pair("$errorSubmittingText: ${result.exception.message}", true)
                            }
                        }
                        is ApiResult.Loading -> {
                            // Handle loading state if needed
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RequestModalController", "Error submitting request", e)
                    showMessage = Pair("$errorSubmittingText: ${e.message}", true)
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun buildRequestMap(is4k: Boolean = false): Map<String, Any?> {
        val request = mutableMapOf<String, Any?>(
            "mediaId" to mediaDetails.id,
            "mediaType" to mediaDetails.mediaType.toString().lowercase(),
            "is4k" to is4k
        )

        // Include selected tags if any are selected
        if (selectedTags.isNotEmpty()) {
            request["tags"] = selectedTags.map { it.toString() }
        }

        // Only include userId when using API key authentication
        if (viewModel.getAuthType() == AuthType.ApiKey) {
            viewModel.getCurrentUserId()?.let { userId ->
                request["userId"] = userId
            }
        }

        when (mediaDetails.mediaType) {
            MediaType.MOVIE -> {
                // mediaId already carries the TMDB id for movies; omit duplicate tmdbId field
                // Only include profileId if there is more than one profile
                if (qualityProfiles.value.size > 1) {
                    selectedQualityProfile?.let { request["profileId"] = it.id }
                }
            }
            MediaType.TV -> {
                // Use selectedTvdbId if available (from lookup), otherwise use mediaInfo.tvdbId
                val tvdbId = selectedTvdbId ?: mediaDetails.mediaInfo?.tvdbId
                tvdbId?.let { request["tvdbId"] = it }
                // Only include profileId if there is more than one profile
                if (qualityProfiles.value.size > 1) {
                    selectedQualityProfile?.let { request["profileId"] = it.id }
                }
                // Only include rootFolder if folder selection is enabled and there is more than one folder
                if (isFolderSelectionEnabled && rootFolders.value.size > 1) {
                    selectedRootFolder?.let { request["rootFolder"] = it.path }
                }
                if (selectedSeasons.isNotEmpty()) {
                    request["seasons"] = selectedSeasons.toList() // Remove the +1 mapping
                }
            }
            null -> {}
        }

        // Implement tier-based server selection logic
        val tierServers = getServersForTier(is4k, mediaDetails.mediaType)
        val multipleServersInTier = tierServers.size > 1
        val userSelected = selectedServer.value
        
        // Include serverId ONLY when there are multiple servers in this tier
        // AND the user selected a non-default server to override routing
        if (multipleServersInTier && userSelected != null) {
            val defaultServer = getDefaultServer(is4k, mediaDetails.mediaType)
            if (userSelected.id != defaultServer?.id) {
                request["serverId"] = userSelected.id
            }
        }

        return request
    }

    fun selectServer(server: ServerOption) {
        Log.d("RequestModalController", "Selecting server: ${server.name} (ID: ${server.id})")
        
        // Validate that the selected server is compatible with the current tier
        if (server.is4k != is4kRequest) {
            Log.w("RequestModalController", "Server tier mismatch: selected ${if (server.is4k) "4K" else "regular"} server for ${if (is4kRequest) "4K" else "regular"} request")
        }
        
        _selectedServer.value = server
        
        // Verify server was set correctly
        if (_selectedServer.value?.id != server.id) {
            Log.e("RequestModalController", "Server selection failed! Selected: ${_selectedServer.value?.name}, Attempted: ${server.name}")
        }
        
        // Reset selections before updating with new server's data
        selectedQualityProfile = null
        selectedRootFolder = null
        selectedTags = emptySet()
        updateQualityProfilesAndRootFolders(server)
    }

    private fun updateQualityProfilesAndRootFolders(server: ServerOption?) {
        viewModelScope.launch {
            server?.let {
                Log.d("RequestModalController", "Updating quality profiles and root folders for server: ${it.name} (${it.type})")
                when (it.type) {
                    ServerType.RADARR -> {
                        mainViewModel.radarrData.value?.allServers?.find { radarr -> radarr.server.id == it.id }?.let { radarr ->
                            _qualityProfiles.value = radarr.profiles
                            _rootFolders.value = emptyList()
                            _availableTags.value = radarr.tags ?: emptyList()

                            Log.d("RequestModalController", "RADARR server ${it.name}: ${radarr.profiles.size} quality profiles, ${radarr.tags?.size ?: 0} tags")

                            // Set the active profile from the server configuration or the first available profile
                            selectedQualityProfile = radarr.profiles.find { profile ->
                                profile.id == radarr.server.activeProfileId
                            } ?: radarr.profiles.firstOrNull()

                            // Don't auto-select tags by default
                            selectedTags = emptySet()

                            Log.d("RequestModalController", "Selected quality profile: ${selectedQualityProfile?.name ?: "NONE"}, tags: ${selectedTags.size}")
                        }
                    }
                    ServerType.SONARR -> {
                        mainViewModel.sonarrData.value?.allServers?.find { sonarr -> sonarr.server.id == it.id }?.let { sonarr ->
                            _qualityProfiles.value = sonarr.profiles
                            _rootFolders.value = sonarr.rootFolders
                            _availableTags.value = sonarr.tags

                            Log.d("RequestModalController", "SONARR server ${it.name}: ${sonarr.profiles.size} quality profiles, ${sonarr.rootFolders.size} root folders, ${sonarr.tags.size} tags")

                            // Set the active profile from the server configuration or the first available profile
                            selectedQualityProfile = sonarr.profiles.find { profile ->
                                profile.id == sonarr.server.activeProfileId
                            } ?: sonarr.profiles.firstOrNull()

                            // Always select the first root folder if available
                            selectedRootFolder = sonarr.rootFolders.firstOrNull()

                            // Don't auto-select tags by default
                            selectedTags = emptySet()

                            Log.d("RequestModalController", "Selected quality profile: ${selectedQualityProfile?.name ?: "NONE"}, root folder: ${selectedRootFolder?.path ?: "NONE"}, tags: ${selectedTags.size}")
                        }
                    }
                }
                
                // Update menu items after quality profiles and root folders are set
                _menuItems.value = filterMenuItems(initialMenuItems)
            }
        }
    }

    fun retryLastAction() {
        lastAction?.invoke()
    }

    private fun setLastAction(action: () -> Unit) {
        lastAction = action
        action.invoke()
    }

    // Add a safety check function to validate main menu focus
    fun validateMainMenuFocus() {
        when {
            menuItems.value.isNotEmpty() -> {
                // If we have menu items and we're not in a submenu, ensure we're on a valid menu item
                if (currentFocus !is FocusState.SubMenu) {
                    val currentIndex = if (currentFocus is FocusState.MainMenu) {
                        (currentFocus as FocusState.MainMenu).index
                    } else {
                        0
                    }
                    currentFocus = FocusState.MainMenu(
                        if (currentIndex >= menuItems.value.size) 0 else currentIndex
                    )
                }
            }
            else -> {
                // If there are no menu items and we're not in a submenu, move focus to the cancel button
                if (currentFocus !is FocusState.SubMenu) {
                    currentFocus = FocusState.CancelButton
                }
            }
        }
    }
}

@Composable
fun rememberRequestModalController(
    menuItems: List<String>,
    mediaDetails: MediaDetails,
    onCancel: () -> Unit,
    onRequest: () -> Unit,
    viewModel: RequestViewModel,
    mainViewModel: SeerrViewModel,
    servers: List<ServerOption>,
    context: Context,
    is4kRequest: Boolean = false
): RequestModalController {
    return remember(mediaDetails, is4kRequest) {
        Log.d("RequestModal", "Creating new RequestModalController - mediaDetails: ${mediaDetails.id}, is4kRequest: $is4kRequest")
        RequestModalController(
            initialMenuItems = menuItems,
            mediaDetails = mediaDetails,
            onCancel = onCancel,
            onRequest = onRequest,
            viewModel = viewModel,
            mainViewModel = mainViewModel,
            servers = servers,
            context = context,
            is4kRequest = is4kRequest
        ).apply {
            // Validate focus based on available menu items
            validateMainMenuFocus()
        }
    }
}

@Composable
fun RequestModal(
    context: Context,
    mediaDetails: MediaDetails,
    onCancel: () -> Unit,
    onRequest: () -> Unit,
    viewModel: RequestViewModel = hiltViewModel(),
    mainViewModel: SeerrViewModel,
    navigationManager: NavigationManager,
    is4kRequest: Boolean = false,
    imageLoader: coil3.ImageLoader
) {
    // Initial menu items without filtering
    val initialMenuItems = listOf(
        context.getString(R.string.requestModal_serverMenu),
        context.getString(R.string.requestModal_seasonsMenu),
        context.getString(R.string.requestModal_qualityMenu),
        context.getString(R.string.requestModal_folderMenu),
        context.getString(R.string.requestModal_tagsMenu)
    )
    
    val controller = rememberRequestModalController(
        menuItems = initialMenuItems,
        mediaDetails = mediaDetails,
        onCancel = onCancel,
        onRequest = onRequest,
        viewModel = viewModel,
        mainViewModel = mainViewModel,
        servers = emptyList(),
        context = context,
        is4kRequest = is4kRequest
    )
    val focusRequester = remember { FocusRequester() }

    // Collect states
    val servers by controller.availableServers.collectAsState()
    val showAuthenticationError by viewModel.showAuthenticationError.collectAsState()

    // Debounced back button handling to prevent both modal and underlying screen from closing
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val backPressDebounceMs = 300L // 300ms debounce to prevent accidental double-presses
    
    // Handle back press with debouncing and proper modal navigation
    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime > backPressDebounceMs) {
            lastBackPressTime = currentTime
            Log.d("RequestModal", "Back button pressed - handling with controller logic")
            // Use the controller's back handling logic which properly manages submenus
            controller.handleBack()
        } else {
            Log.d("RequestModal", "Back button debounced - ignoring rapid back press")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                return@onKeyEvent SafeKeyEventHandler.handleKeyEventWithContext(
                    keyEvent = keyEvent,
                    context = "RequestModal"
                ) { keyEvent ->
                    // Always handle back key events to prevent them from reaching the underlying screen
                    if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyDown) {
                        Log.d("RequestModal", "Back key event intercepted - handling with controller")
                        controller.handleBack()
                        true // Consume the event
                    } else if (controller.isLookupMode) {
                        // When in lookup mode, handle key events through the controller
                        controller.handleKeyEvent(keyEvent) // This will delegate to handleLookupKeyEvent and return true if handled
                    } else {
                        // Handle other key events normally for main menu
                        controller.handleKeyEvent(keyEvent)
                    }
                }
            }
            // Consume all clicks to prevent them from reaching the underlying screen
            .clickable { }
    ) {
        // Show lookup screen when in lookup mode
        if (controller.isLookupMode) {
            SeriesLookupSelection(
                results = controller.lookupResults,
                selectedIndex = controller.selectedLookupIndex,
                isLoading = controller.isLoadingLookup,
                error = controller.lookupError,
                context = context,
                imageLoader = imageLoader,
                isCancelButtonFocused = controller.currentFocus == RequestModalController.FocusState.LookupCancelButton
            )
        } else {
        // Main menu
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(Color(0xFF121827))
                .padding(16.dp)
                .zIndex(1f)
        ) {
            Text(
                stringResource(id = R.string.requestModal_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))

            val menuItems by controller.menuItems.collectAsState()
            menuItems.forEachIndexed { index, item ->
                MenuOption(
                    text = item,
                    selectedValue = when (item) {
                        context.getString(R.string.requestModal_serverMenu) -> controller.selectedServer.collectAsState().value?.name
                            ?: context.getString(R.string.requestModal_notSelected)
                        context.getString(R.string.requestModal_seasonsMenu) -> "${controller.selectedSeasons.size}/${controller.seasons.size} " + context.getString(R.string.requestModal_selected)
                        context.getString(R.string.requestModal_qualityMenu) -> controller.selectedQualityProfile?.name
                            ?: context.getString(R.string.requestModal_notSelected)
                        context.getString(R.string.requestModal_folderMenu) -> controller.selectedRootFolder?.path ?: context.getString(R.string.requestModal_notSelected)
                        context.getString(R.string.requestModal_tagsMenu) -> "${controller.selectedTags.size}/${controller.availableTags.collectAsState().value.size} " + context.getString(R.string.requestModal_selected)
                        else -> context.getString(R.string.requestModal_notSelected)
                    },
                    isSelected = controller.currentFocus == RequestModalController.FocusState.MainMenu(index),
                    isFocused = controller.currentFocus == RequestModalController.FocusState.MainMenu(index)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ActionButton(
                        text = stringResource(id = R.string.common_cancel),
                        isFocused = controller.currentFocus == RequestModalController.FocusState.CancelButton,
                        backgroundColor = Color.Gray
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    ActionButton(
                        text = stringResource(id = R.string.requestModal_submit),
                        isFocused = controller.currentFocus == RequestModalController.FocusState.RequestButton,
                        backgroundColor = Color(0xFF6200EE),
                        enabled = controller.requestStatus !is RequestModalController.RequestStatus.Loading
                    )
                }
            }
        }

        // Options panel
        AnimatedVisibility(
            visible = controller.selectedOption != null,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.offset(x = 300.dp).zIndex(0f)
        ) {
            Box(
                modifier = Modifier
                    .width(
                        if (controller.selectedOption == context.getString(R.string.requestModal_seasonsMenu)) {
                            400.dp // Width for seasons menu
                        } else {
                            250.dp // Width for other menus
                        }
                    )
                    .fillMaxHeight()
                    .background(Color(0xFF1E293B))
                    .padding(16.dp)
            ) {
                when (controller.selectedOption) {
                    context.getString(R.string.requestModal_seasonsMenu) -> SeasonsSelection(
                        seasons = controller.seasons,
                        selectedSeasons = controller.selectedSeasons,
                        focusedIndex = (controller.currentFocus as? RequestModalController.FocusState.SubMenu)?.index
                            ?: -1,
                        onSeasonToggle = { controller.toggleSeasonSelection(it) }
                    )

                    context.getString(R.string.requestModal_qualityMenu) -> QualityProfileSelection(
                        profiles = controller.qualityProfiles.collectAsState().value,
                        selectedProfile = controller.selectedQualityProfile,
                        focusedIndex = (controller.currentFocus as? RequestModalController.FocusState.SubMenu)?.index
                            ?: -1,
                        onProfileSelected = { profile ->
                            controller.onSubMenuItemSelected(ModalSelection.QualityProfile(profile))
                        }
                    )

                    context.getString(R.string.requestModal_folderMenu) -> RootFolderSelection(
                        folders = controller.rootFolders.collectAsState().value,
                        selectedFolder = controller.selectedRootFolder,
                        focusedIndex = (controller.currentFocus as? RequestModalController.FocusState.SubMenu)?.index
                            ?: -1,
                        onFolderSelected = { folder ->
                            controller.onSubMenuItemSelected(ModalSelection.RootFolder(folder))
                        }
                    )

                    context.getString(R.string.requestModal_serverMenu) -> ServerSelection(
                        servers = servers,
                        selectedServer = controller.selectedServer.collectAsState().value,
                        focusedIndex = (controller.currentFocus as? RequestModalController.FocusState.SubMenu)?.index
                            ?: -1,
                        onServerSelected = { controller.selectServer(it) }
                    )

                    context.getString(R.string.requestModal_tagsMenu) -> TagSelection(
                        tags = controller.availableTags.collectAsState().value,
                        selectedTags = controller.selectedTags,
                        focusedIndex = (controller.currentFocus as? RequestModalController.FocusState.SubMenu)?.index
                            ?: -1,
                        onTagToggle = { controller.toggleTagSelection(it) }
                    )
                }
            }
        }

        controller.showMessage?.let { (message, isError) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f) // This ensures the message appears on top
            ) {
                StatusMessage(
                    message = message,
                    isError = isError,
                    onDismiss = {
                        controller.showMessage = null
                        focusRequester.requestFocus()
                    }
                )
            }
        }


        // Add authentication error dialog on top of everything else
        if (showAuthenticationError) {
            AuthenticationErrorHandler(
                isVisible = true,
                onRetry = {
                    viewModel.hideAuthenticationError()
                    controller.retryLastAction()
                },
                onReconfigure = {
                    viewModel.hideAuthenticationError()
                    navigationManager.navigateToConfig()
                }
            )
        }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun MenuOption(text: String, selectedValue: String, isSelected: Boolean, isFocused: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isFocused -> Color(0xFF2C3E50)
                    isSelected -> Color(0xFF1E293B)
                    else -> Color.Transparent
                }
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, color = Color.White)
        Text(selectedValue, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
    }
}


@Composable
fun SeasonsSelection(
    seasons: List<SeasonData>,
    selectedSeasons: Set<Int>,
    focusedIndex: Int,
    onSeasonToggle: (Int?) -> Unit
) {
    val listState = rememberLazyListState()
    val visibleItemsCount = 7
    val columnWidth = 85.dp

    LaunchedEffect(focusedIndex) {
        val targetIndex = focusedIndex + 1 // +1 to account for the "ALL" item
        if (targetIndex >= 0) {
            if (targetIndex >= visibleItemsCount - 1) {
                listState.animateScrollToItem(targetIndex - visibleItemsCount + 2)
            } else if (targetIndex == 0 && listState.firstVisibleItemIndex > 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    Column {
        Text(
            stringResource(R.string.requestModal_seasonsMenu),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(state = listState) {
            // Header row containing the ALL toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                focusedIndex == -1 -> Color(0xFF2C3E50) // Focused state
                                else -> Color(0xFF1E293B) // Header background (distinct from regular items)
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFF475569), // Subtle border
                            shape = RoundedCornerShape(4.dp)
                        )
                        .height(56.dp)
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val selectableSeasons = seasons.filter { it.status == null || it.status == 0 || it.status == 1 || it.status == 7 }
                    val allSelectableSeasonsSelected = selectableSeasons.isNotEmpty() &&
                        selectableSeasons.all { it.seasonNumber in selectedSeasons }
                    
                    Checkbox(
                        checked = allSelectableSeasonsSelected,
                        onCheckedChange = { onSeasonToggle(null) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White,
                            uncheckedColor = Color.White,
                            checkmarkColor = Color(0xFF1E293B)
                        )
                    )
                    Text(
                        text = stringResource(R.string.requestModal_season),
                        color = Color(0xFFB8C5D1), // Lighter gray for header text
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.width(columnWidth) // Fixed width for alignment
                    )
                    Text(
                        text = stringResource(R.string.requestModal_episodes),
                        color = Color(0xFFB8C5D1), // Lighter gray for header text
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.width(columnWidth) // Fixed width for alignment
                    )
                    Text(
                        text = stringResource(R.string.mediaInfoTable_status),
                        color = Color(0xFFB8C5D1), // Lighter gray for header text
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.weight(1f) // Takes up remaining space
                    )
                }
            }
            // Iterate through all the seasons
            itemsIndexed(seasons) { index, season ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index == focusedIndex) Color(0xFF2C3E50) else Color.Transparent)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Debug log the season status for UI rendering
                    Log.d("SeasonsSelection", "Season ${season.seasonNumber}: status=${season.status}, showing checkbox=${season.status == null || season.status == 0 || season.status == 1 || season.status == 7}")
                    
                    if (season.status == null || season.status == 0 || season.status == 1 || season.status == 7) {
                        // Show checkbox for not requested, pending, or deleted seasons
                        Checkbox(
                            checked = season.seasonNumber in selectedSeasons,
                            onCheckedChange = { onSeasonToggle(season.seasonNumber) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color.White,
                                uncheckedColor = Color.White,
                                checkmarkColor = Color(0xFF1E293B)
                            )
                        )
                    } else {
                        // Show checkmark for any other status (requested, available, etc.)
                        Spacer(modifier = Modifier.width(10.dp))
                        CustomCheckmarkIcon(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // Season Number/Description
                    Text(
                        text = season.seasonName,
                        color = Color.White,
                        modifier = Modifier
                            .width(columnWidth)
                            .wrapContentHeight(),
                        maxLines = 4
                    )
                    // Episode Count
                    Text(
                        text = season.episodeCount.toString(),
                        color = Color.White,
                        modifier = Modifier
                            .width(columnWidth) // Fixed width for alignment
                    )
                    // Status using RequestStatus component
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp)
                            .heightIn(min = 16.dp)
                    ) {
                        RequestStatus.MediaStatus(
                            status = season.status,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
fun QualityProfileSelection(
    profiles: List<Profile>,
    selectedProfile: Profile?,
    focusedIndex: Int,
    onProfileSelected: (Profile) -> Unit
) {
    val listState = rememberLazyListState()
    val visibleItemsCount = 7

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) {
            if (focusedIndex >= visibleItemsCount - 1) {
                listState.animateScrollToItem(focusedIndex - visibleItemsCount + 2)
            } else if (focusedIndex == 0 && listState.firstVisibleItemIndex > 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    Column {
        Text(
            stringResource(R.string.requestModal_selectQualityProfile),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(state = listState) {
            itemsIndexed(profiles) { index, profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                focusedIndex == index -> Color(0xFF2C3E50)
                                profile.id == selectedProfile?.id -> Color(0xFF1E293B)
                                else -> Color.Transparent
                            }
                        )
                        .padding(vertical = 8.dp)
                        .clickable { onProfileSelected(profile) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = profile.id == selectedProfile?.id,
                        onClick = { onProfileSelected(profile) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color.White,
                            unselectedColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(profile.name, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun RootFolderSelection(
    folders: List<SonarrRootFolder>,
    selectedFolder: SonarrRootFolder?,
    focusedIndex: Int,
    onFolderSelected: (SonarrRootFolder) -> Unit
) {
    val listState = rememberLazyListState()
    val visibleItemsCount = 7

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) {
            if (focusedIndex >= visibleItemsCount - 1) {
                listState.animateScrollToItem(focusedIndex - visibleItemsCount + 2)
            } else if (focusedIndex == 0 && listState.firstVisibleItemIndex > 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    Column {
        Text(
            stringResource(R.string.requestModal_selectRootFolder),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(state = listState) {
            itemsIndexed(folders) { index, folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                focusedIndex == index -> Color(0xFF2C3E50)
                                folder == selectedFolder -> Color(0xFF1E293B)
                                else -> Color.Transparent
                            }
                        )
                        .padding(vertical = 8.dp)
                        .clickable { onFolderSelected(folder) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = folder == selectedFolder,
                        onClick = { onFolderSelected(folder) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color.White,
                            unselectedColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(folder.path, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun TagSelection(
    tags: List<Tag>,
    selectedTags: Set<Int>,
    focusedIndex: Int,
    onTagToggle: (Int?) -> Unit
) {
    val listState = rememberLazyListState()
    val visibleItemsCount = 7

    LaunchedEffect(focusedIndex) {
        val targetIndex = focusedIndex + 1 // +1 to account for the "ALL" item
        if (targetIndex >= 0) {
            if (targetIndex >= visibleItemsCount - 1) {
                listState.animateScrollToItem(targetIndex - visibleItemsCount + 2)
            } else if (targetIndex == 0 && listState.firstVisibleItemIndex > 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    Column {
        Text(
            stringResource(R.string.requestModal_selectTags),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(state = listState) {
            // Header row containing the ALL toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                focusedIndex == -1 -> Color(0xFF2C3E50) // Focused state
                                else -> Color(0xFF1E293B) // Header background (distinct from regular items)
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFF475569), // Subtle border
                            shape = RoundedCornerShape(4.dp)
                        )
                        .height(56.dp)
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val allTagsSelected = tags.isNotEmpty() && tags.all { it.id in selectedTags }

                    Checkbox(
                        checked = allTagsSelected,
                        onCheckedChange = { onTagToggle(null) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White,
                            uncheckedColor = Color.White,
                            checkmarkColor = Color(0xFF1E293B)
                        )
                    )
                    Text(
                        text = stringResource(R.string.requestModal_tag),
                        color = Color(0xFFB8C5D1), // Lighter gray for header text
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            // Iterate through all the tags
            itemsIndexed(tags) { index, tag ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index == focusedIndex) Color(0xFF2C3E50) else Color.Transparent)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = tag.id in selectedTags,
                        onCheckedChange = { onTagToggle(tag.id) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White,
                            uncheckedColor = Color.White,
                            checkmarkColor = Color(0xFF1E293B)
                        )
                    )
                    Text(
                        text = tag.label,
                        color = Color.White,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ServerSelection(
    servers: List<ServerOption>,
    selectedServer: ServerOption?,
    focusedIndex: Int,
    onServerSelected: (ServerOption) -> Unit
) {
    val listState = rememberLazyListState()
    val visibleItemsCount = 7

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) {
            if (focusedIndex >= visibleItemsCount - 1) {
                listState.animateScrollToItem(focusedIndex - visibleItemsCount + 2)
            } else if (focusedIndex == 0 && listState.firstVisibleItemIndex > 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    Column {
        Text(stringResource(R.string.requestModal_selectServer), style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(state = listState) {
            itemsIndexed(servers) { index, server ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                focusedIndex == index -> Color(0xFF2C3E50)
                                server == selectedServer -> Color(0xFF1E293B)
                                else -> Color.Transparent
                            }
                        )
                        .padding(vertical = 8.dp)
                        .clickable { onServerSelected(server) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = server == selectedServer,
                        onClick = { onServerSelected(server) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color.White,
                            unselectedColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(server.name, color = Color.White)

                    // Add 4K indicator if server supports 4K
                    if (server.is4k) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1E3A8A), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "4K",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesLookupSelection(
    results: List<SonarrLookupResult>,
    selectedIndex: Int,
    isLoading: Boolean,
    error: String?,
    context: Context,
    imageLoader: coil3.ImageLoader,
    isCancelButtonFocused: Boolean
) {
    Column(
        modifier = Modifier
            .width(400.dp)
            .fillMaxHeight()
            .background(Color(0xFF0F1419))
            .padding(16.dp)
    ) {

        // Header section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1F2E), RoundedCornerShape(8.dp))
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.requestModal_requestSeriesTitle),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            // Show series title if available
            if (results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = results.first().title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFB8C5D1),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Info message box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1E293B)
        ) {
            Row(
                modifier = Modifier.padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = stringResource(R.string.requestModal_unableToMatch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        if (isLoading) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.requestModal_lookupLoading),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        } else if (error != null) {
            // Error state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.common_error),
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    // Cancel button for error state
                    ActionButton(
                        text = stringResource(R.string.common_cancel),
                        isFocused = true,
                        backgroundColor = Color.Gray
                    )
                }
            }
        } else if (results.isNotEmpty()) {
            // Results list with constrained height
            val listState = rememberLazyListState()
            val visibleItemsCount = 3 // Show 3 items at a time

            LaunchedEffect(selectedIndex) {
                if (selectedIndex >= 0) {
                    // Scroll to make the selected item visible
                    val targetIndex = selectedIndex
                    if (targetIndex >= visibleItemsCount - 1) {
                        listState.animateScrollToItem(targetIndex - visibleItemsCount + 2)
                    } else if (targetIndex == 0 && listState.firstVisibleItemIndex > 0) {
                        listState.animateScrollToItem(0)
                    } else {
                        listState.animateScrollToItem(targetIndex)
                    }
                }
            }

            // Constrain the LazyColumn to leave space for the cancel button
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f) // Take available space but leave room for button
            ) {
                itemsIndexed(results) { index, series ->
                    SeriesLookupItem(
                        series = series,
                        isSelected = index == selectedIndex && !isCancelButtonFocused,
                        context = context,
                        imageLoader = imageLoader
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cancel button at bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                ActionButton(
                    text = stringResource(R.string.common_cancel),
                    isFocused = isCancelButtonFocused,
                    backgroundColor = Color.Gray
                )
            }
        } else {
            // No results state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.requestModal_noSeriesFound),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    // Cancel button for no results state
                    ActionButton(
                        text = stringResource(R.string.common_cancel),
                        isFocused = true,
                        backgroundColor = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesLookupItem(
    series: SonarrLookupResult,
    isSelected: Boolean,
    context: Context,
    imageLoader: coil3.ImageLoader
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFF2C3E50) else Color.Transparent)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Poster image (left side)
        Box(
            modifier = Modifier
                .size(85.dp, 127.dp)
                .background(Color(0xFF1E293B))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Color.White else Color(0xFF475569),
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Find the poster/banner image
            val posterImage = series.images.find { it.coverType == "poster" }
                ?: series.images.find { it.coverType == "banner" }
                ?: series.images.firstOrNull()

            if (posterImage != null) {
                val imageUrl = posterImage.remoteUrl
                if (!imageUrl.isNullOrBlank()) {
                    Log.d("SeriesLookupItem", "Loading image for ${series.title}: $imageUrl")

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .build(),
                        contentDescription = "${series.title} poster",
                        imageLoader = imageLoader,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { state ->
                            Log.e("SeriesLookupItem", "Error loading image for ${series.title}: ${state.result.throwable.message}")
                        },
                        onLoading = { state ->
                            Log.d("SeriesLookupItem", "Loading image for ${series.title}")
                        },
                        onSuccess = { state ->
                            Log.d("SeriesLookupItem", "Successfully loaded image for ${series.title}")
                        }
                    )
                } else {
                    Log.w("SeriesLookupItem", "No remoteUrl available for ${series.title}")
                }
            } else {
                // Fallback to title initial if no image available
                Text(
                    text = series.title.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Series details (right side)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Year and title
            Text(
                text = "${series.year}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = series.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))

            // Overview/description
            if (!series.overview.isNullOrBlank()) {
                Text(
                    text = series.overview.take(150) + if (series.overview.length > 150) "..." else "",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4
                )
            }
        }
    }
}

@Composable
fun StatusMessage(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var canDismiss by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        delay(1000) // Wait 1 second before allowing dismissal
        canDismiss = true
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
            color = Color(0xFF1E293B), // Dark blue to match the UI theme
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (canDismiss && keyEvent.type == KeyEventType.KeyUp &&
                            KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode)
                        ) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isError) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = if (isError) stringResource(R.string.common_error) else stringResource(R.string.requestModal_success),
                    tint = if (isError) Color(0xFFF44336) else Color(0xFF4CAF50), // Red for error, green for success
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                if (canDismiss) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.mediaDetails_ok),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color(0xFFBB86FC), shape = RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}