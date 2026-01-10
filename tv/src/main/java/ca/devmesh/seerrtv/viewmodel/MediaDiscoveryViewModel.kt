package ca.devmesh.seerrtv.viewmodel

import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.model.BrowseModels
import ca.devmesh.seerrtv.model.FilterLanguage
import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.model.MediaType
import ca.devmesh.seerrtv.model.Movie
import ca.devmesh.seerrtv.model.SearchResult
import ca.devmesh.seerrtv.model.TV
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.app.Application

@HiltViewModel
class MediaDiscoveryViewModel @Inject constructor(
    application: Application,
    private val apiService: SeerrApiService
) : AndroidViewModel(application) {

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _hasMoreResults = MutableStateFlow(true)
    val hasMoreResults: StateFlow<Boolean> = _hasMoreResults

    // Browse-specific state flows
    private val _currentFilters = MutableStateFlow<BrowseModels.MediaFilters?>(null)
    val currentFilters: StateFlow<BrowseModels.MediaFilters?> = _currentFilters

    private val _currentSort = MutableStateFlow<BrowseModels.SortOption>(BrowseModels.SortOption.default())
    val currentSort: StateFlow<BrowseModels.SortOption> = _currentSort

    private val _activeFilterCount = MutableStateFlow(0)
    val activeFilterCount: StateFlow<Int> = _activeFilterCount

    // Filter options state
    private val _availableRegions = MutableStateFlow<List<ca.devmesh.seerrtv.model.Region>>(emptyList())
    val availableRegions: StateFlow<List<ca.devmesh.seerrtv.model.Region>> = _availableRegions

    private val _availableLanguages = MutableStateFlow<List<FilterLanguage>>(emptyList())
    val availableLanguages: StateFlow<List<FilterLanguage>> = _availableLanguages

    private val _contentRatings = MutableStateFlow<List<ca.devmesh.seerrtv.model.ContentRating>>(emptyList())
    val contentRatings: StateFlow<List<ca.devmesh.seerrtv.model.ContentRating>> = _contentRatings

    private val _keywordSearchResults = MutableStateFlow<List<ca.devmesh.seerrtv.model.Keyword>>(emptyList())
    val keywordSearchResults: StateFlow<List<ca.devmesh.seerrtv.model.Keyword>> = _keywordSearchResults
    
    private val _studioSearchResults = MutableStateFlow<List<ca.devmesh.seerrtv.data.SeerrApiService.CompanySearchResult>>(emptyList())
    val studioSearchResults: StateFlow<List<ca.devmesh.seerrtv.data.SeerrApiService.CompanySearchResult>> = _studioSearchResults
    
    private val _selectedStudioName = MutableStateFlow<String?>(null)
    val selectedStudioName: StateFlow<String?> = _selectedStudioName
    
    private val _networkSearchResults = MutableStateFlow<List<ca.devmesh.seerrtv.data.SeerrApiService.NetworkResponse>>(emptyList())
    val networkSearchResults: StateFlow<List<ca.devmesh.seerrtv.data.SeerrApiService.NetworkResponse>> = _networkSearchResults

    private val _availableGenres = MutableStateFlow<List<ca.devmesh.seerrtv.data.SeerrApiService.GenreResponse>>(emptyList())
    val availableGenres: StateFlow<List<ca.devmesh.seerrtv.data.SeerrApiService.GenreResponse>> = _availableGenres

    private val _availableStudios = MutableStateFlow<List<ca.devmesh.seerrtv.data.SeerrApiService.StudioResponse>>(emptyList())
    val availableStudios: StateFlow<List<ca.devmesh.seerrtv.data.SeerrApiService.StudioResponse>> = _availableStudios

    private val _availableNetworks = MutableStateFlow<List<ca.devmesh.seerrtv.data.SeerrApiService.NetworkResponse>>(emptyList())
    val availableNetworks: StateFlow<List<ca.devmesh.seerrtv.data.SeerrApiService.NetworkResponse>> = _availableNetworks

    private val _availableWatchProviders = MutableStateFlow<List<ca.devmesh.seerrtv.model.Provider>>(emptyList())
    val availableWatchProviders: StateFlow<List<ca.devmesh.seerrtv.model.Provider>> = _availableWatchProviders

    private val _selectedWatchRegion = MutableStateFlow<String>("US")
    val selectedWatchRegion: StateFlow<String> = _selectedWatchRegion

    private val authErrorState = AuthenticationErrorState()
    val showAuthenticationError = authErrorState.showAuthenticationError

    private var searchJob: Job? = null
    private var lastAction: (() -> Unit)? = null
    private var currentQuery: String = ""
    private var currentDiscoveryMode: DiscoveryMode = DiscoveryMode.NONE
    private var currentKeywordId: String = ""
    private var lastLoadTimestamp: Long = 0
    private val cooldownPeriodMs = 0L // Completely disable cooldown for now

    private enum class DiscoveryMode {
        NONE,
        SEARCH,
        MOVIE_KEYWORDS,
        TV_KEYWORDS,
        MOVIE_GENRE,
        SERIES_GENRE,
        STUDIO,
        NETWORK,
        MOVIE_BROWSE,
        TV_BROWSE
    }

    fun debouncedSearch(query: String) {
        searchJob?.cancel()
        if (query.length >= 3) {
            // Reset search state when query changes
            if (query != currentQuery) {
                currentQuery = query
                currentDiscoveryMode = DiscoveryMode.SEARCH
                _searchResults.value = emptyList()
                apiService.resetPaginationState("search")
                _hasMoreResults.value = true
            }

            setLastAction {
                searchJob = viewModelScope.launch {
                    delay(500) // Debounce delay
                    search(query, false)
                }
            }
        } else if (query.isEmpty()) {
            _searchResults.value = emptyList()
            currentQuery = ""
            currentDiscoveryMode = DiscoveryMode.NONE
        }
    }

    private suspend fun search(query: String, loadMore: Boolean = false) {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            when (val result = apiService.search(query, loadMore)) {
                is ApiResult.Success -> {
                    val newResults = result.data.results
                    
                    if (loadMore) {
                        // Filter out any duplicates before appending to existing results
                        val currentIds = _searchResults.value.map { it.id }.toSet()
                        val uniqueNewResults = newResults.filter { it.id !in currentIds }
                        
                        if (BuildConfig.DEBUG) {
                            if (uniqueNewResults.size < newResults.size) {
                                Log.d("MediaDiscoveryViewModel", "⚠️ Filtered out ${newResults.size - uniqueNewResults.size} duplicate items from search")
                            }
                        }
                        
                        _searchResults.value = _searchResults.value + uniqueNewResults
                    } else {
                        _searchResults.value = newResults
                    }
                    
                    // Update pagination state
                    _hasMoreResults.value = result.paginationInfo?.hasMorePages ?: (newResults.isNotEmpty())
                    
                    // Debug logging
                    Log.d("MediaDiscoveryViewModel", "Search results: loaded ${newResults.size} items, hasMore=${_hasMoreResults.value}")
                }
                is ApiResult.Error -> {
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                    if (!loadMore) {
                        _searchResults.value = emptyList()
                    }
                }
                is ApiResult.Loading -> {
                    // Loading state already set
                }
            }
        } finally {
            _isLoading.value = false
            // Still update timestamp here for consistency
            lastLoadTimestamp = System.currentTimeMillis()
        }
    }

    fun discoverMoviesByKeyword(keywordId: String) {
        // Reset when keyword changes
        if (keywordId != currentKeywordId || currentDiscoveryMode != DiscoveryMode.MOVIE_KEYWORDS) {
            currentKeywordId = keywordId
            currentDiscoveryMode = DiscoveryMode.MOVIE_KEYWORDS
            _searchResults.value = emptyList()
            apiService.resetPaginationState("movie_keywords")
            _hasMoreResults.value = true
        }

        setLastAction {
            viewModelScope.launch {
                loadMoviesByKeyword(keywordId, false)
            }
        }
    }

    private suspend fun loadMoviesByKeyword(keywordId: String, loadMore: Boolean = false) {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            when (val result = apiService.discoverMoviesByKeyword(keywordId, loadMore)) {
                is ApiResult.Success -> {
                    val newResults = result.data.results.map { it.toSearchResult("movie") }
                    
                    if (loadMore) {
                        // Filter out any duplicates before appending to existing results
                        val currentIds = _searchResults.value.map { it.id }.toSet()
                        val uniqueNewResults = newResults.filter { it.id !in currentIds }
                        
                        if (BuildConfig.DEBUG) {
                            if (uniqueNewResults.size < newResults.size) {
                                Log.d("MediaDiscoveryViewModel", "⚠️ Filtered out ${newResults.size - uniqueNewResults.size} duplicate movie keyword items")
                            }
                        }
                        
                        _searchResults.value = _searchResults.value + uniqueNewResults
                    } else {
                        _searchResults.value = newResults
                    }
                    
                    // Update pagination state
                    _hasMoreResults.value = result.paginationInfo?.hasMorePages ?: (newResults.isNotEmpty())
                    
                    // Debug logging
                    Log.d("MediaDiscoveryViewModel", "Movie keyword search: loaded ${newResults.size} items, hasMore=${_hasMoreResults.value}")
                }
                is ApiResult.Error -> {
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                    if (!loadMore) {
                        _searchResults.value = emptyList()
                    }
                }
                is ApiResult.Loading -> {
                    // Loading state already set
                }
            }
        } finally {
            _isLoading.value = false
            lastLoadTimestamp = System.currentTimeMillis()
        }
    }

    fun discoverTVByKeyword(keywordId: String) {
        // Reset when keyword changes
        if (keywordId != currentKeywordId || currentDiscoveryMode != DiscoveryMode.TV_KEYWORDS) {
            currentKeywordId = keywordId
            currentDiscoveryMode = DiscoveryMode.TV_KEYWORDS
            _searchResults.value = emptyList()
            apiService.resetPaginationState("tv_keywords")
            _hasMoreResults.value = true
        }

        setLastAction {
            viewModelScope.launch {
                loadTVByKeyword(keywordId, false)
            }
        }
    }

    private suspend fun loadTVByKeyword(keywordId: String, loadMore: Boolean = false) {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            when (val result = apiService.discoverTVByKeyword(keywordId, loadMore)) {
                is ApiResult.Success -> {
                    val newResults = result.data.results.map { it.toSearchResult("tv") }
                    
                    if (loadMore) {
                        // Filter out any duplicates before appending to existing results
                        val currentIds = _searchResults.value.map { it.id }.toSet()
                        val uniqueNewResults = newResults.filter { it.id !in currentIds }
                        
                        if (BuildConfig.DEBUG) {
                            if (uniqueNewResults.size < newResults.size) {
                                Log.d("MediaDiscoveryViewModel", "⚠️ Filtered out ${newResults.size - uniqueNewResults.size} duplicate TV keyword items")
                            }
                        }
                        
                        _searchResults.value = _searchResults.value + uniqueNewResults
                    } else {
                        _searchResults.value = newResults
                    }
                    
                    // Update pagination state
                    _hasMoreResults.value = result.paginationInfo?.hasMorePages ?: (newResults.isNotEmpty())
                    
                    // Debug logging
                    Log.d("MediaDiscoveryViewModel", "TV keyword search: loaded ${newResults.size} items, hasMore=${_hasMoreResults.value}")
                }
                is ApiResult.Error -> {
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                    if (!loadMore) {
                        _searchResults.value = emptyList()
                    }
                }
                is ApiResult.Loading -> {
                    // Loading state already set
                }
            }
        } finally {
            _isLoading.value = false
            lastLoadTimestamp = System.currentTimeMillis()
        }
    }

    /**
     * Load popular movies for initial browse screen
     */
    fun loadPopularMovies() {
        currentDiscoveryMode = DiscoveryMode.MOVIE_BROWSE
        _searchResults.value = emptyList()
        _activeFilterCount.value = 0
        
        setLastAction {
            viewModelScope.launch {
                loadPopularMoviesInternal()
            }
        }
    }

    /**
     * Load popular TV series for initial browse screen
     */
    fun loadPopularSeries() {
        currentDiscoveryMode = DiscoveryMode.TV_BROWSE
        _searchResults.value = emptyList()
        _activeFilterCount.value = 0
        
        setLastAction {
            viewModelScope.launch {
                loadPopularSeriesInternal()
            }
        }
    }

    private suspend fun loadPopularMoviesInternal() {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            Log.d("MediaDiscoveryViewModel", "🎬 Loading popular movies...")
            val result = apiService.discoverMovies(reset = true)
            
            when (result) {
                is ApiResult.Success<List<Media>> -> {
                    _searchResults.value = result.data.map { it.toSearchResult("movie") }
                    _hasMoreResults.value = result.data.size >= 20 // Assume more if we got a full page
                    Log.d("MediaDiscoveryViewModel", "✅ Loaded ${result.data.size} popular movies")
                }
                is ApiResult.Error -> {
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                }
                is ApiResult.Loading -> {
                    // Handle loading state if needed
                }
            }
        } catch (e: Exception) {
            Log.e("MediaDiscoveryViewModel", "❌ Exception loading popular movies", e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun loadPopularSeriesInternal() {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            Log.d("MediaDiscoveryViewModel", "📺 Loading popular series...")
            val result = apiService.getPopularSeries(reset = true)
            
            when (result) {
                is ApiResult.Success<List<Media>> -> {
                    _searchResults.value = result.data.map { it.toSearchResult("tv") }
                    _hasMoreResults.value = result.data.size >= 20 // Assume more if we got a full page
                    Log.d("MediaDiscoveryViewModel", "✅ Loaded ${result.data.size} popular series")
                }
                is ApiResult.Error -> {
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                }
                is ApiResult.Loading -> {
                    // Handle loading state if needed
                }
            }
        } catch (e: Exception) {
            Log.e("MediaDiscoveryViewModel", "❌ Exception loading popular series", e)
        } finally {
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (!_hasMoreResults.value) {
            Log.d("MediaDiscoveryViewModel", "❌ LOAD MORE SKIPPED: No more results available (hasMoreResults=false)")
            return
        }
        
        if (_isLoading.value) {
            Log.d("MediaDiscoveryViewModel", "❌ LOAD MORE SKIPPED: Already loading data (isLoading=true)")
            return
        }
        
        // Check cooldown
        val currentTime = System.currentTimeMillis()
        val timeSinceLastLoad = currentTime - lastLoadTimestamp
        if (timeSinceLastLoad < cooldownPeriodMs) {
            Log.d("MediaDiscoveryViewModel", "❌ LOAD MORE SKIPPED: Cooldown period active (${timeSinceLastLoad}ms < ${cooldownPeriodMs}ms)")
            return
        }
        
        // Set last load timestamp immediately to prevent double calls
        lastLoadTimestamp = currentTime
        Log.d("MediaDiscoveryViewModel", "✅ EXECUTING LOAD MORE: mode=$currentDiscoveryMode, query='$currentQuery', keywordId='$currentKeywordId', results=${_searchResults.value.size}")
        
        viewModelScope.launch {
            when (currentDiscoveryMode) {
                DiscoveryMode.SEARCH -> search(currentQuery, true)
                DiscoveryMode.MOVIE_KEYWORDS -> loadMoviesByKeyword(currentKeywordId, true)
                DiscoveryMode.TV_KEYWORDS -> loadTVByKeyword(currentKeywordId, true)
                DiscoveryMode.MOVIE_GENRE -> loadMoviesByGenre(currentKeywordId, true)
                DiscoveryMode.SERIES_GENRE -> loadTVByGenre(currentKeywordId, true)
                DiscoveryMode.STUDIO -> loadMoviesByStudio(currentKeywordId, true)
                DiscoveryMode.NETWORK -> loadTVByNetwork(currentKeywordId, true)
                DiscoveryMode.MOVIE_BROWSE -> {
                    val filters = _currentFilters.value
                    val sort = _currentSort.value
                    if (filters != null) {
                        loadMoviesWithBrowse(filters, sort, true)
                    }
                }
                DiscoveryMode.TV_BROWSE -> {
                    val filters = _currentFilters.value
                    val sort = _currentSort.value
                    if (filters != null) {
                        loadSeriesWithBrowse(filters, sort, true)
                    }
                }
                DiscoveryMode.NONE -> {
                    Log.d("MediaDiscoveryViewModel", "❓ LOAD MORE: No active discovery mode")
                } // Nothing to load
            }
        }
    }

    fun hideAuthenticationError() {
        authErrorState.hideError()
    }

    fun retryLastAction() {
        lastAction?.invoke()
    }

    private fun setLastAction(action: () -> Unit) {
        lastAction = action
        action.invoke()
    }

    private fun ca.devmesh.seerrtv.model.Media.toSearchResult(type: String): SearchResult {
        return when (type) {
            "movie" -> Movie(
                id = this.id,
                mediaType = "movie",
                title = this.title ?: "",
                originalTitle = this.originalTitle,
                releaseDate = this.releaseDate,
                posterPath = this.posterPath,
                voteAverage = this.voteAverage ?: 0.0,
                voteCount = this.voteCount ?: 0,
                popularity = this.popularity ?: 0.0,
                adult = this.adult == true,
                genreIds = this.genreIds ?: emptyList(),
                originalLanguage = this.originalLanguage,
                overview = this.overview,
                video = this.video == true,
                backdropPath = this.backdropPath,
                mediaInfo = this.mediaInfo
            )
            "tv" -> TV(
                id = this.id,
                mediaType = "tv",
                name = this.name ?: "",
                originalName = this.originalTitle,
                firstAirDate = this.releaseDate,
                posterPath = this.posterPath,
                voteAverage = this.voteAverage ?: 0.0,
                voteCount = this.voteCount ?: 0,
                popularity = this.popularity ?: 0.0,
                genreIds = this.genreIds ?: emptyList(),
                originCountry = emptyList(),
                originalLanguage = this.originalLanguage,
                overview = this.overview,
                backdropPath = this.backdropPath,
                mediaInfo = this.mediaInfo
            )
            else -> throw IllegalArgumentException("Unsupported media type: $type")
        }
    }

    fun discoverMoviesByGenre(genreId: String) {
        // Reset when genre changes
        if (genreId != currentKeywordId || currentDiscoveryMode != DiscoveryMode.MOVIE_GENRE) {
            currentKeywordId = genreId
            currentDiscoveryMode = DiscoveryMode.MOVIE_GENRE
            _searchResults.value = emptyList()
            apiService.resetPaginationState("movie_genre")
            _hasMoreResults.value = true
        }

        setLastAction {
            viewModelScope.launch {
                loadMoviesByGenre(genreId, false)
            }
        }
    }

    private suspend fun loadMoviesByGenre(genreId: String, loadMore: Boolean = false) {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            when (val result = apiService.discoverMoviesByGenre(genreId, loadMore)) {
                is ApiResult.Success -> {
                    val newResults = result.data.results.map { it.toSearchResult("movie") }
                    
                    if (loadMore) {
                        // Filter out any duplicates before appending to existing results
                        val currentIds = _searchResults.value.map { it.id }.toSet()
                        val uniqueNewResults = newResults.filter { it.id !in currentIds }
                        
                        if (BuildConfig.DEBUG) {
                            if (uniqueNewResults.size < newResults.size) {
                                Log.d("MediaDiscoveryViewModel", "⚠️ Filtered out ${newResults.size - uniqueNewResults.size} duplicate items")
                            }
                        }
                        
                        _searchResults.value = _searchResults.value + uniqueNewResults
                    } else {
                        _searchResults.value = newResults
                    }
                    
                    // Update pagination state
                    _hasMoreResults.value = result.paginationInfo?.hasMorePages ?: (newResults.isNotEmpty())
                    
                    // Debug logging
                    Log.d("MediaDiscoveryViewModel", "Movie genre search: loaded ${newResults.size} items, hasMore=${_hasMoreResults.value}")
                }
                is ApiResult.Error -> {
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                    if (!loadMore) {
                        _searchResults.value = emptyList()
                    }
                }
                is ApiResult.Loading -> {
                    // Loading state already set
                }
            }
        } finally {
            _isLoading.value = false
            lastLoadTimestamp = System.currentTimeMillis()
        }
    }

    fun discoverTVByGenre(genreId: String) {
        // Reset when genre changes
        if (genreId != currentKeywordId || currentDiscoveryMode != DiscoveryMode.SERIES_GENRE) {
            currentKeywordId = genreId
            currentDiscoveryMode = DiscoveryMode.SERIES_GENRE
            _searchResults.value = emptyList()
            apiService.resetPaginationState("tv_genre")
            _hasMoreResults.value = true
        }

        setLastAction {
            viewModelScope.launch {
                loadTVByGenre(genreId, false)
            }
        }
    }

    private suspend fun loadTVByGenre(genreId: String, loadMore: Boolean = false) {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            when (val result = apiService.discoverTVByGenre(genreId, loadMore)) {
                is ApiResult.Success -> {
                    val newResults = result.data.results.map { it.toSearchResult("tv") }
                    
                    if (loadMore) {
                        // Filter out any duplicates before appending to existing results
                        val currentIds = _searchResults.value.map { it.id }.toSet()
                        val uniqueNewResults = newResults.filter { it.id !in currentIds }
                        
                        if (BuildConfig.DEBUG) {
                            if (uniqueNewResults.size < newResults.size) {
                                Log.d("MediaDiscoveryViewModel", "⚠️ Filtered out ${newResults.size - uniqueNewResults.size} duplicate TV genre items")
                            }
                        }
                        
                        _searchResults.value = _searchResults.value + uniqueNewResults
                    } else {
                        _searchResults.value = newResults
                    }
                    
                    // Update pagination state
                    _hasMoreResults.value = result.paginationInfo?.hasMorePages ?: (newResults.isNotEmpty())
                    
                    // Debug logging
                    Log.d("MediaDiscoveryViewModel", "TV genre search: loaded ${newResults.size} items, hasMore=${_hasMoreResults.value}")
                }
                is ApiResult.Error -> {
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                    if (!loadMore) {
                        _searchResults.value = emptyList()
                    }
                }
                is ApiResult.Loading -> {
                    // Loading state already set
                }
            }
        } finally {
            _isLoading.value = false
            lastLoadTimestamp = System.currentTimeMillis()
        }
    }

    fun discoverMoviesByStudio(studioId: String) {
        // Reset when studio changes
        if (studioId != currentKeywordId || currentDiscoveryMode != DiscoveryMode.STUDIO) {
            currentKeywordId = studioId
            currentDiscoveryMode = DiscoveryMode.STUDIO
            _searchResults.value = emptyList()
            apiService.resetPaginationState("movie_studio")
            _hasMoreResults.value = true
        }

        setLastAction {
            viewModelScope.launch {
                loadMoviesByStudio(studioId, false)
            }
        }
    }

    private suspend fun loadMoviesByStudio(studioId: String, loadMore: Boolean = false) {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            when (val result = apiService.discoverMoviesByStudio(studioId, loadMore)) {
                is ApiResult.Success -> {
                    val newResults = result.data.results.map { it.toSearchResult("movie") }
                    
                    if (loadMore) {
                        // Filter out any duplicates before appending to existing results
                        val currentIds = _searchResults.value.map { it.id }.toSet()
                        val uniqueNewResults = newResults.filter { it.id !in currentIds }
                        
                        if (BuildConfig.DEBUG) {
                            if (uniqueNewResults.size < newResults.size) {
                                Log.d("MediaDiscoveryViewModel", "⚠️ Filtered out ${newResults.size - uniqueNewResults.size} duplicate movie studio items")
                            }
                        }
                        
                        _searchResults.value = _searchResults.value + uniqueNewResults
                    } else {
                        _searchResults.value = newResults
                    }
                    
                    // Update pagination state
                    _hasMoreResults.value = result.paginationInfo?.hasMorePages ?: (newResults.isNotEmpty())
                    
                    // Debug logging
                    Log.d("MediaDiscoveryViewModel", "Movie studio search: loaded ${newResults.size} items, hasMore=${_hasMoreResults.value}")
                }
                is ApiResult.Error -> {
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                    if (!loadMore) {
                        _searchResults.value = emptyList()
                    }
                }
                is ApiResult.Loading -> {
                    // Loading state already set
                }
            }
        } finally {
            _isLoading.value = false
            lastLoadTimestamp = System.currentTimeMillis()
        }
    }

    fun discoverTVByNetwork(networkId: String) {
        // Reset when network changes
        if (networkId != currentKeywordId || currentDiscoveryMode != DiscoveryMode.NETWORK) {
            currentKeywordId = networkId
            currentDiscoveryMode = DiscoveryMode.NETWORK
            _searchResults.value = emptyList()
            apiService.resetPaginationState("tv_network")
            _hasMoreResults.value = true
        }

        setLastAction {
            viewModelScope.launch {
                loadTVByNetwork(networkId, false)
            }
        }
    }

    private suspend fun loadTVByNetwork(networkId: String, loadMore: Boolean = false) {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            when (val result = apiService.discoverTVByNetwork(networkId, loadMore)) {
                is ApiResult.Success -> {
                    val newResults = result.data.results.map { it.toSearchResult("tv") }
                    
                    if (loadMore) {
                        // Filter out any duplicates before appending to existing results
                        val currentIds = _searchResults.value.map { it.id }.toSet()
                        val uniqueNewResults = newResults.filter { it.id !in currentIds }
                        
                        if (BuildConfig.DEBUG) {
                            if (uniqueNewResults.size < newResults.size) {
                                Log.d("MediaDiscoveryViewModel", "⚠️ Filtered out ${newResults.size - uniqueNewResults.size} duplicate TV network items")
                            }
                        }
                        
                        _searchResults.value = _searchResults.value + uniqueNewResults
                    } else {
                        _searchResults.value = newResults
                    }
                    
                    // Update pagination state
                    _hasMoreResults.value = result.paginationInfo?.hasMorePages ?: (newResults.isNotEmpty())
                    
                    // Debug logging
                    Log.d("MediaDiscoveryViewModel", "TV network search: loaded ${newResults.size} items, hasMore=${_hasMoreResults.value}")
                }
                is ApiResult.Error -> {
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                    if (!loadMore) {
                        _searchResults.value = emptyList()
                    }
                }
                is ApiResult.Loading -> {
                    // Loading state already set
                }
            }
        } finally {
            _isLoading.value = false
            lastLoadTimestamp = System.currentTimeMillis()
        }
    }

    // Browse methods for Movies/Series screens

    /**
     * Browse movies with filters and sorting
     */
    fun browseMovies(filters: BrowseModels.MediaFilters, sort: BrowseModels.SortOption, loadMore: Boolean = false) {
        val filtersChanged = _currentFilters.value != filters
        val sortChanged = _currentSort.value != sort
        val modeChanged = currentDiscoveryMode != DiscoveryMode.MOVIE_BROWSE
        
        if (modeChanged || filtersChanged || sortChanged) {
            // Reset when filters/sort change
            currentDiscoveryMode = DiscoveryMode.MOVIE_BROWSE
            _currentFilters.value = filters
            _currentSort.value = sort
            if (!loadMore) {
                // Only clear results if not loading more
                _searchResults.value = emptyList()
            }
            _activeFilterCount.value = filters.activeCount()
            
            if (BuildConfig.DEBUG) {
                Log.d("MediaDiscoveryViewModel", "🔄 browseMovies: filtersChanged=$filtersChanged, sortChanged=$sortChanged, modeChanged=$modeChanged, loadMore=$loadMore")
            }
        }

        setLastAction {
            viewModelScope.launch {
                loadMoviesWithBrowse(filters, sort, loadMore)
            }
        }
    }

    /**
     * Browse TV series with filters and sorting
     */
    fun browseSeries(filters: BrowseModels.MediaFilters, sort: BrowseModels.SortOption, loadMore: Boolean = false) {
        val filtersChanged = _currentFilters.value != filters
        val sortChanged = _currentSort.value != sort
        val modeChanged = currentDiscoveryMode != DiscoveryMode.TV_BROWSE
        
        if (modeChanged || filtersChanged || sortChanged) {
            // Reset when filters/sort change
            currentDiscoveryMode = DiscoveryMode.TV_BROWSE
            _currentFilters.value = filters
            _currentSort.value = sort
            if (!loadMore) {
                // Only clear results if not loading more
                _searchResults.value = emptyList()
            }
            _activeFilterCount.value = filters.activeCount()
            
            if (BuildConfig.DEBUG) {
                Log.d("MediaDiscoveryViewModel", "🔄 browseSeries: filtersChanged=$filtersChanged, sortChanged=$sortChanged, modeChanged=$modeChanged, loadMore=$loadMore")
            }
        }

        setLastAction {
            viewModelScope.launch {
                loadSeriesWithBrowse(filters, sort, loadMore)
            }
        }
    }

    /**
     * Apply new filters and reset pagination
     */
    fun applyFilters(filters: BrowseModels.MediaFilters) {
        val activeCount = filters.activeCount()
        if (BuildConfig.DEBUG) {
            Log.d("MediaDiscoveryViewModel", "🔍 applyFilters called - watchProviders: ${filters.watchProviders}, watchRegion: ${filters.watchRegion}, activeCount: $activeCount")
            Log.d("MediaDiscoveryViewModel", "🔍 Filter details - genres: ${filters.genres.size}, keywords: ${filters.keywords.size}, watchProviders: ${filters.watchProviders.size}")
        }
        
        // Update filter count immediately - this ensures UI updates right away
        _activeFilterCount.value = activeCount
        
        // Check if filters actually changed
        val filtersChanged = _currentFilters.value != filters
        
        _currentFilters.value = filters
        
        // Reset pagination and results when filters change
        if (filtersChanged) {
            _searchResults.value = emptyList()
            _hasMoreResults.value = true
            
            if (BuildConfig.DEBUG) {
                Log.d("MediaDiscoveryViewModel", "🔄 Filters changed, clearing results and resetting pagination")
            }
        }
        
        // Trigger new search with current sort
        val currentSort = _currentSort.value
        when (_currentFilters.value?.mediaType) {
            MediaType.MOVIE -> {
                if (BuildConfig.DEBUG) {
                    Log.d("MediaDiscoveryViewModel", "🎬 Calling browseMovies with filters")
                }
                browseMovies(filters, currentSort, loadMore = false)
            }
            MediaType.TV -> {
                if (BuildConfig.DEBUG) {
                    Log.d("MediaDiscoveryViewModel", "📺 Calling browseSeries with filters")
                }
                browseSeries(filters, currentSort, loadMore = false)
            }
            null -> {
                // No media type set yet, this shouldn't happen
                Log.w("MediaDiscoveryViewModel", "applyFilters called but no media type set")
            }
        }
    }

    fun loadFilterOptions() {
        viewModelScope.launch {
            // Load regions
            when (val result = apiService.getRegions()) {
                is ApiResult.Success -> _availableRegions.value = result.data
                is ApiResult.Error -> Log.e("MediaDiscoveryViewModel", "Failed to load regions", result.exception)
                else -> {}
            }
            
            // Load languages (static list for now)
            _availableLanguages.value = apiService.getLanguages()
            
            // Load content ratings (static list)
            _contentRatings.value = apiService.getContentRatings()
            
            // Load genres based on current media type (defaulting to Movie if not set, or load both?)
            // Ideally we load based on the active tab.
            // For now, let's load both and filter in UI or just load based on a parameter?
            // The drawer knows the media type.
            // Let's add a method to load genres for a specific type.
        }
    }
    
    fun loadGenres(mediaType: MediaType) {
        viewModelScope.launch {
            val result = if (mediaType == MediaType.MOVIE) {
                apiService.getMovieGenres(getApplication())
            } else {
                apiService.getTVGenres(getApplication())
            }
            
            when (result) {
                is ApiResult.Success -> _availableGenres.value = result.data
                is ApiResult.Error -> Log.e("MediaDiscoveryViewModel", "Failed to load genres", result.exception)
                else -> {}
            }
        }
    }
    
    fun loadStudios() {
        viewModelScope.launch {
            // Load first page of studios
            when (val result = apiService.getStudios(reset = true)) {
                is ApiResult.Success -> _availableStudios.value = result.data
                is ApiResult.Error -> Log.e("MediaDiscoveryViewModel", "Failed to load studios", result.exception)
                else -> {}
            }
        }
    }
    
    fun loadNetworks() {
        viewModelScope.launch {
            // Load first page of networks
            when (val result = apiService.getNetworks(reset = true)) {
                is ApiResult.Success -> _availableNetworks.value = result.data
                is ApiResult.Error -> Log.e("MediaDiscoveryViewModel", "Failed to load networks", result.exception)
                else -> {}
            }
        }
    }

    fun loadWatchProviders(mediaType: MediaType, region: String) {
        viewModelScope.launch {
            _selectedWatchRegion.value = region
            when (val result = apiService.getWatchProviders(mediaType, region)) {
                is ApiResult.Success -> _availableWatchProviders.value = result.data
                is ApiResult.Error -> {
                    Log.e("MediaDiscoveryViewModel", "Failed to load watch providers", result.exception)
                    _availableWatchProviders.value = emptyList()
                }
                else -> {}
            }
        }
    }
    
    fun searchKeywords(query: String) {
        if (query.length < 2) {
            _keywordSearchResults.value = emptyList()
            return
        }
        
        viewModelScope.launch {
            when (val result = apiService.searchKeywords(query)) {
                is ApiResult.Success -> _keywordSearchResults.value = result.data
                is ApiResult.Error -> {
                    Log.e("MediaDiscoveryViewModel", "Failed to search keywords", result.exception)
                    _keywordSearchResults.value = emptyList()
                }
                else -> {}
            }
        }
    }
    
    // Note: Studio/Network search currently relies on pre-defined lists in API service or would need new endpoints.
    // For now we'll leave placeholders or implement if we find endpoints.
    // Based on plan, we might need to implement searchStudios/searchNetworks in API service first if we want dynamic search.
    // But for now, let's just clear them to avoid confusion.
    fun clearKeywordSearch() {
        _keywordSearchResults.value = emptyList()
    }
    
    fun searchStudios(query: String) {
        if (BuildConfig.DEBUG) {
            Log.d("MediaDiscoveryViewModel", "🔍 searchStudios called with query: '$query'")
        }
        
        if (query.length < 2) {
            _studioSearchResults.value = emptyList()
            if (BuildConfig.DEBUG) {
                Log.d("MediaDiscoveryViewModel", "⚠️ Query too short, clearing results")
            }
            return
        }
        
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d("MediaDiscoveryViewModel", "🌐 Making API call to search studios...")
            }
            when (val result = apiService.searchStudios(query)) {
                is ApiResult.Success -> {
                    _studioSearchResults.value = result.data
                    if (BuildConfig.DEBUG) {
                        Log.d("MediaDiscoveryViewModel", "✅ Studio search success: ${result.data.size} results")
                        result.data.forEach { studio ->
                            Log.d("MediaDiscoveryViewModel", "  - ${studio.name} (ID: ${studio.id})")
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.e("MediaDiscoveryViewModel", "❌ Failed to search studios", result.exception)
                    _studioSearchResults.value = emptyList()
                }
                else -> {}
            }
        }
    }
    
    fun clearStudioSearch() {
        _studioSearchResults.value = emptyList()
    }
    
    fun setSelectedStudioName(name: String) {
        _selectedStudioName.value = name
    }
    
    fun clearSelectedStudio() {
        _selectedStudioName.value = null
    }
        


    /**
     * Apply new sort and reset pagination
     */
    fun applySort(sort: BrowseModels.SortOption) {
        _currentSort.value = sort
        
        // Reset pagination and results
        _searchResults.value = emptyList()
        _hasMoreResults.value = true
        
        // Trigger new search with current filters
        val currentFilters = _currentFilters.value
        if (currentFilters != null) {
            when (currentFilters.mediaType) {
                MediaType.MOVIE -> browseMovies(currentFilters, sort)
                MediaType.TV -> browseSeries(currentFilters, sort)
            }
        }
    }

    /**
     * Clear all filters and reset to defaults
     */
    fun clearFilters() {
        val currentFilters = _currentFilters.value
        if (currentFilters != null) {
            val defaultFilters = BrowseModels.MediaFilters.default(currentFilters.mediaType)
            applyFilters(defaultFilters)
        }
    }

    /**
     * Calculate and update active filter count
     */
    private fun calculateActiveFilterCount() {
        val filters = _currentFilters.value
        _activeFilterCount.value = filters?.activeCount() ?: 0
    }

    private suspend fun loadMoviesWithBrowse(filters: BrowseModels.MediaFilters, sort: BrowseModels.SortOption, loadMore: Boolean = false) {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            when (val result = apiService.browseMedia(
                mediaType = MediaType.MOVIE,
                filters = filters,
                sort = sort,
                query = currentQuery,
                loadMore = loadMore
            )) {
                is ApiResult.Success -> {
                    val newResults = result.data.results.map { it.toSearchResult("movie") }
                    
                    if (loadMore) {
                        // Filter out any duplicates before appending to existing results
                        val currentIds = _searchResults.value.map { it.id }.toSet()
                        val uniqueNewResults = newResults.filter { it.id !in currentIds }
                        
                        if (BuildConfig.DEBUG) {
                            if (uniqueNewResults.size < newResults.size) {
                                Log.d("MediaDiscoveryViewModel", "⚠️ Filtered out ${newResults.size - uniqueNewResults.size} duplicate movie browse items")
                            }
                        }
                        
                        _searchResults.value = _searchResults.value + uniqueNewResults
                    } else {
                        _searchResults.value = newResults
                    }
                    
                    // Update pagination state
                    _hasMoreResults.value = result.paginationInfo?.hasMorePages ?: (newResults.isNotEmpty())
                    
                    if (BuildConfig.DEBUG) {
                        Log.d("MediaDiscoveryViewModel", "Movie browse: loaded ${newResults.size} items, hasMore=${_hasMoreResults.value}")
                    }
                }
                is ApiResult.Error -> {
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                    if (!loadMore) {
                        _searchResults.value = emptyList()
                    }
                }
                is ApiResult.Loading -> {
                    // Loading state already set
                }
            }
        } finally {
            _isLoading.value = false
            lastLoadTimestamp = System.currentTimeMillis()
        }
    }

    private suspend fun loadSeriesWithBrowse(filters: BrowseModels.MediaFilters, sort: BrowseModels.SortOption, loadMore: Boolean = false) {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            when (val result = apiService.browseMedia(
                mediaType = MediaType.TV,
                filters = filters,
                sort = sort,
                query = currentQuery,
                loadMore = loadMore
            )) {
                is ApiResult.Success -> {
                    val newResults = result.data.results.map { it.toSearchResult("tv") }
                    
                    if (loadMore) {
                        // Filter out any duplicates before appending to existing results
                        val currentIds = _searchResults.value.map { it.id }.toSet()
                        val uniqueNewResults = newResults.filter { it.id !in currentIds }
                        
                        if (BuildConfig.DEBUG) {
                            if (uniqueNewResults.size < newResults.size) {
                                Log.d("MediaDiscoveryViewModel", "⚠️ Filtered out ${newResults.size - uniqueNewResults.size} duplicate TV browse items")
                            }
                        }
                        
                        _searchResults.value = _searchResults.value + uniqueNewResults
                    } else {
                        _searchResults.value = newResults
                    }
                    
                    // Update pagination state
                    _hasMoreResults.value = result.paginationInfo?.hasMorePages ?: (newResults.isNotEmpty())
                    
                    if (BuildConfig.DEBUG) {
                        Log.d("MediaDiscoveryViewModel", "TV browse: loaded ${newResults.size} items, hasMore=${_hasMoreResults.value}")
                    }
                }
                is ApiResult.Error -> {
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                    if (!loadMore) {
                        _searchResults.value = emptyList()
                    }
                }
                is ApiResult.Loading -> {
                    // Loading state already set
                }
            }
        } finally {
            _isLoading.value = false
            lastLoadTimestamp = System.currentTimeMillis()
        }
    }

    /**
     * Update the current sort option without triggering a reload.
     * Call browseMovies() or browseSeries() after to apply the new sort.
     */
    fun setSort(sort: BrowseModels.SortOption) {
        _currentSort.value = sort
    }

    /**
     * Update the current filters without triggering a reload.
     * Call browseMovies() or browseSeries() after to apply the new filters.
     */
    fun setFilters(filters: BrowseModels.MediaFilters) {
        _currentFilters.value = filters
        _activeFilterCount.value = filters.activeCount()
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }
}