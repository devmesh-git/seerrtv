package ca.devmesh.seerrtv.viewmodel

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.model.*
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.ui.position.ScrollPositionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Data model class for Category Cards that represent discovery options like genres, studios, networks
 * @property id Unique identifier for this category
 * @property name Display name of the category
 * @property type Type of category (MOVIE_GENRE, SERIES_GENRE, STUDIO, NETWORK)
 * @property imagePath Path to the image to display (backdrop for genres, logo for studios/networks)
 * @property discoveryType The type of discovery to perform when this card is selected
 */
data class CategoryCard(
    val id: Int,
    val name: String,
    val type: MediaCategory,
    val imagePath: String? = null, // For genres, this is a backdrop path. For studios/networks, this is a logo path
    val discoveryType: DiscoveryType
)

/**
 * Discovery types for MediaDiscoveryScreen
 */
enum class DiscoveryType {
    SEARCH,
    MOVIE_KEYWORDS,
    TV_KEYWORDS,
    MOVIE_GENRE,
    SERIES_GENRE,
    STUDIO,
    NETWORK
}

enum class MediaCategory(@param:StringRes val titleResId: Int) {
    RECENTLY_ADDED(R.string.category_recentlyAdded),
    RECENT_REQUESTS(R.string.category_recentRequests),
    TRENDING(R.string.category_trending),
    POPULAR_MOVIES(R.string.category_popularMovies),
    UPCOMING_MOVIES(R.string.category_upcomingMovies),
    POPULAR_SERIES(R.string.category_popularSeries),
    UPCOMING_SERIES(R.string.category_upcomingSeries),

    // New category types
    MOVIE_GENRES(R.string.category_movieGenres),
    SERIES_GENRES(R.string.category_seriesGenres),
    STUDIOS(R.string.category_studios),
    NETWORKS(R.string.category_networks)
}

fun getCategoryTitle(context: Context, category: MediaCategory): String {
    return context.getString(category.titleResId)
}

// Add a new data class to track carousel state changes
data class CarouselUpdateEvent(
    val category: MediaCategory,
    val itemsBefore: Int,
    val itemsAfter: Int,
    val timestamp: Long = System.currentTimeMillis()
)

// Add a comprehensive data class to track category state
data class CategoryState(
    val category: MediaCategory,
    val itemCount: Int = 0,
    val isLoading: Boolean = false,
    val hasMorePages: Boolean = true,
    val loadingStartTime: Long = 0L,
    val minimumLoadingTimeMs: Long = 2000 // Ensure placeholder shows for at least 2 seconds
)

@HiltViewModel
class SeerrViewModel @Inject constructor(
    private val apiService: SeerrApiService,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private data class CachedMediaDetails(
        val details: ApiResult<MediaDetails>,
        val timestamp: Long,
        val cacheTimeMs: Long
    )

    companion object {
        private const val MAX_CACHE_SIZE = 100
        private const val DEFAULT_CACHE_TIME_MS = 10_000L
        private const val TAG = "SeerrViewModel"

        
        // Polling constants for automatic media details updates
        private const val MIN_UPDATE_INTERVAL_MS = 5000L         // Minimum time between updates
        private const val FAST_UPDATE_INTERVAL_MS = 15_000L      // Fast polling when downloading every 15 seconds
        private const val SLOW_UPDATE_INTERVAL_MS = 30_000L      // Slow polling when not downloading every 30 seconds
        private const val MAX_FAILURE_COUNT = 3                  // Max failures before stopping updates
    }


    private val _categoryData =
        MutableStateFlow<Map<MediaCategory, ApiResult<List<Media>>>>(emptyMap())
    val categoryData: StateFlow<Map<MediaCategory, ApiResult<List<Media>>>> =
        _categoryData.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val cacheTimestamps = mutableMapOf<MediaCategory, Long>()
    private val categoryMediaLists = mutableMapOf<MediaCategory, MutableList<Media>>()
    private val _mediaDetailsCache = MutableStateFlow<Map<String, CachedMediaDetails>>(emptyMap())
    private val _currentMediaDetails =
        MutableStateFlow<ApiResult<MediaDetails>>(ApiResult.Loading())
    private var currentMediaId: String? = null
    private var currentMediaType: String? = null
    private var initialLoadDone = false
    private val _needsRefresh = MutableStateFlow(false)
    private val _isInitialLoad = MutableStateFlow(true)
    val isInitialLoad: StateFlow<Boolean> = _isInitialLoad.asStateFlow()
    private val authErrorState = AuthenticationErrorState()
    val showAuthenticationError = authErrorState.showAuthenticationError
    private var lastAction: (() -> Unit)? = null
    private var _requestMade = MutableStateFlow(false)
    private val _mediaToRefresh = MutableStateFlow<MediaDetails?>(null)
    
    // Polling state for automatic media details updates
    private val _isPollingActive = MutableStateFlow(false)
    private val _pollingLoadingState = MutableStateFlow(LoadingState.IDLE)
    private var pollingJob: Job? = null
    private var pollingCycleCount = 0
    private var pollingFailureCount = 0
    private var lastPollingUpdateTimeMs = 0L
    private var isCollectingPollingFlow = false
    private var pollingTmdbId: String? = null
    private var pollingMediaType: String? = null

    // Add job tracking
    private val activeJobs = mutableMapOf<String, Job>()
    private val mediaDetailsJobs = mutableMapOf<String, Job>()
    private val fetchLocks = mutableMapOf<String, Mutex>()
    private val lastFetchStartMs = mutableMapOf<String, Long>()
    private val inCooldownUntilMs = mutableMapOf<String, Long>()

    // Ratings caching and in-flight coordination
    private data class CachedRatings(
        val data: RatingsResponse,
        val timestamp: Long
    )
    private val ratingsCache = mutableMapOf<String, CachedRatings>()
    private val ratingsJobs = mutableMapOf<String, Job>()

    // Track media items that have no ratings available (404 responses) to avoid repeated fetches
    private val noRatingsAvailable = mutableSetOf<String>()

    private suspend fun getRatingsCached(
        mediaId: String,
        mediaType: String,
        cacheTimeMs: Long = DEFAULT_CACHE_TIME_MS
    ): ApiResult<RatingsResponse> {
        val key = "$mediaId-$mediaType"
        val now = System.currentTimeMillis()

        // If cacheTimeMs is 0, only return cached data, don't fetch
        if (cacheTimeMs == 0L) {
            val cached = ratingsCache[key]
            if (cached != null) {
                return ApiResult.Success(cached.data)
            }
            return ApiResult.Error(Exception("No cached ratings available"))
        }

        // Check if we already know this media item has no ratings available
        if (noRatingsAvailable.contains(key)) {
            Log.d(TAG, "⏭️ Skipping ratings fetch for $key - previously returned 404 (no ratings available)")
            return ApiResult.Error(Exception("Ratings not available - previously returned 404"))
        }

        val cached = ratingsCache[key]
        if (cached != null && (now - cached.timestamp) < cacheTimeMs) {
            return ApiResult.Success(cached.data)
        }

        val existing = ratingsJobs[key]
        if (existing?.isActive == true) {
            existing.join()
            val after = ratingsCache[key]
            return if (after != null) ApiResult.Success(after.data) else ApiResult.Error(Exception("Ratings not available after join"))
        }

        val job = viewModelScope.launch {
            val result = apiService.getRatingsData(mediaId, mediaType)
            Log.d(TAG, "Ratings API response: $result")
            if (result is ApiResult.Success) {
                ratingsCache[key] = CachedRatings(result.data, System.currentTimeMillis())
            } else if (result is ApiResult.Error) {
                if (result.statusCode == 404) {
                    // Record that this media item has no ratings available
                    noRatingsAvailable.add(key)
                    Log.d(TAG, "📝 Recorded $key as having no ratings available (404 response)")
                } else {
                    Log.d(TAG, "❌ Ratings fetch failed for $key with status ${result.statusCode}: ${result.exception.message}")
                }
            }
        }
        ratingsJobs[key] = job
        job.join()
        ratingsJobs.remove(key)

        val finalCached = ratingsCache[key]
        return if (finalCached != null) ApiResult.Success(finalCached.data) else ApiResult.Error(Exception("Ratings fetch failed"))
    }

    /**
     * Clear the no-ratings tracking for a specific media item.
     * This allows us to try fetching ratings again when the media details screen is reopened.
     */
    fun clearNoRatingsTracking(mediaId: String, mediaType: String) {
        val key = "$mediaId-$mediaType"
        val removed = noRatingsAvailable.remove(key)
        if (removed) {
            Log.d(TAG, "🗑️ Cleared no-ratings tracking for $key - will try fetching again on next request")
        } else {
            Log.d(TAG, "ℹ️ No-ratings tracking not found for $key (either never set or already cleared)")
        }
    }

    // Add Radarr and Sonarr data states from RequestViewModel
    private val _radarrData = MutableStateFlow<RadarrServerInfo?>(null)
    val radarrData: StateFlow<RadarrServerInfo?> = _radarrData

    private val _sonarrData = MutableStateFlow<SonarrServerInfo?>(null)
    val sonarrData: StateFlow<SonarrServerInfo?> = _sonarrData

    // Similar media state management
    private val _similarMediaState = MutableStateFlow<ApiResult<List<SimilarMediaItem>>?>(null)
    val similarMediaState: StateFlow<ApiResult<List<SimilarMediaItem>>?> =
        _similarMediaState.asStateFlow()

    // Similar media pagination management
    private val similarMediaPages = mutableMapOf<Int, List<SimilarMediaItem>>()
    private val maxSimilarMediaPages = 5 // Limit to 100 items (5 pages of 20)

    // Track loading state per category to prevent concurrent loads
    private val categoryLoadingState = mutableStateMapOf<MediaCategory, Boolean>()

    // State to track which categories are currently loading more data
    private val loadingMoreCategories = mutableStateMapOf<MediaCategory, Boolean>()

    private val _categoryCardData =
        MutableStateFlow<Map<MediaCategory, ApiResult<List<CategoryCard>>>>(emptyMap())
    val categoryCardData: StateFlow<Map<MediaCategory, ApiResult<List<CategoryCard>>>> =
        _categoryCardData.asStateFlow()

    // Track category cards for pagination/caching
    private val categoryCardLists = mutableMapOf<MediaCategory, MutableList<CategoryCard>>()

    private val _currentSelectedMedia = MutableStateFlow<Media?>(null)
    val currentSelectedMedia: StateFlow<Media?> = _currentSelectedMedia
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _carouselUpdates = MutableStateFlow<CarouselUpdateEvent?>(null)
    val carouselUpdates: StateFlow<CarouselUpdateEvent?> = _carouselUpdates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _categoryStates = MutableStateFlow<Map<MediaCategory, CategoryState>>(emptyMap())
    val categoryStates: StateFlow<Map<MediaCategory, CategoryState>> = _categoryStates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    private val _showPlaceholders = MutableStateFlow<Map<MediaCategory, Boolean>>(emptyMap())
    val showPlaceholders: StateFlow<Map<MediaCategory, Boolean>> = _showPlaceholders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    private val _carouselResetEvents = MutableStateFlow<Pair<MediaCategory, Boolean>?>(null)
    val carouselResetEvents: StateFlow<Pair<MediaCategory, Boolean>?> = _carouselResetEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        // Initialize category states
        val initialStates = MediaCategory.entries.associateWith { category -> CategoryState(category) }
        _categoryStates.value = initialStates

        // Initialize placeholder visibility
        _showPlaceholders.value = MediaCategory.entries.associateWith { category -> false }

        MediaCategory.entries.forEach { category ->
            _categoryData.value = _categoryData.value.toMutableMap().apply {
                put(category, ApiResult.Loading())
            }
        }

        // Initialize category card data for new category types
        listOf(
            MediaCategory.MOVIE_GENRES,
            MediaCategory.SERIES_GENRES,
            MediaCategory.STUDIOS,
            MediaCategory.NETWORKS
        ).forEach { category ->
            _categoryCardData.value = _categoryCardData.value.toMutableMap().apply {
                put(category, ApiResult.Loading())
            }
        }

        // Initialize category media lists for non-card categories
        listOf(
            MediaCategory.RECENTLY_ADDED,
            MediaCategory.RECENT_REQUESTS,
            MediaCategory.TRENDING,
            MediaCategory.POPULAR_MOVIES,
            MediaCategory.UPCOMING_MOVIES,
            MediaCategory.POPULAR_SERIES,
            MediaCategory.UPCOMING_SERIES
        ).forEach { category ->
            categoryMediaLists[category] = mutableListOf()
        }
    }

    fun loadAllCategories() {
        // Cancel any existing load job
        activeJobs["loadAll"]?.cancel()

        setLastAction {
            if (!initialLoadDone || _needsRefresh.value) {
                activeJobs["loadAll"] = viewModelScope.launch {
                    try {
                        // Load categories in the specified order
                        listOf(
                            MediaCategory.RECENTLY_ADDED,
                            MediaCategory.RECENT_REQUESTS,
                            MediaCategory.TRENDING,
                            MediaCategory.POPULAR_MOVIES,
                            MediaCategory.MOVIE_GENRES,
                            MediaCategory.UPCOMING_MOVIES,
                            MediaCategory.STUDIOS,
                            MediaCategory.POPULAR_SERIES,
                            MediaCategory.SERIES_GENRES,
                            MediaCategory.UPCOMING_SERIES,
                            MediaCategory.NETWORKS
                        ).forEach { category ->
                            launch {
                                try {
                                    when (category) {
                                        // Load traditional media categories
                                        MediaCategory.RECENTLY_ADDED,
                                        MediaCategory.RECENT_REQUESTS,
                                        MediaCategory.TRENDING,
                                        MediaCategory.POPULAR_MOVIES,
                                        MediaCategory.UPCOMING_MOVIES,
                                        MediaCategory.POPULAR_SERIES,
                                        MediaCategory.UPCOMING_SERIES -> {
                                            loadCategory(category, forceRefresh = true)
                                        }

                                        // Load new category card categories
                                        MediaCategory.MOVIE_GENRES,
                                        MediaCategory.SERIES_GENRES,
                                        MediaCategory.STUDIOS,
                                        MediaCategory.NETWORKS -> {
                                            loadCategoryCards(category, forceRefresh = true)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(
                                        "SeerrTV",
                                        "Error loading category $category: ${e.message}"
                                    )
                                }
                            }
                        }
                        initialLoadDone = true
                        _isInitialLoad.value = false
                        _needsRefresh.value = false
                    } catch (e: Exception) {
                        Log.e("SeerrTV", "Error in loadAllCategories: ${e.message}")
                    }
                }
            } else {
                Log.d("SeerrTV", "Skipping loadAllCategories - data already loaded")
            }
        }
    }

    fun refreshAllCategories() {
        setLastAction {
            viewModelScope.launch {
                _isRefreshing.value = true
                try {
                    // Clear all loading states
                    categoryLoadingState.clear()

                    // Refresh categories in the specified order
                    listOf(
                        MediaCategory.RECENTLY_ADDED,
                        MediaCategory.RECENT_REQUESTS,
                        MediaCategory.TRENDING,
                        MediaCategory.POPULAR_MOVIES,
                        MediaCategory.MOVIE_GENRES,
                        MediaCategory.UPCOMING_MOVIES,
                        MediaCategory.STUDIOS,
                        MediaCategory.POPULAR_SERIES,
                        MediaCategory.SERIES_GENRES,
                        MediaCategory.UPCOMING_SERIES,
                        MediaCategory.NETWORKS
                    ).forEach { category ->
                        launch {
                            try {
                                categoryLoadingState[category] = true
                                when (category) {
                                    // Refresh traditional media categories
                                    MediaCategory.RECENTLY_ADDED,
                                    MediaCategory.RECENT_REQUESTS,
                                    MediaCategory.TRENDING,
                                    MediaCategory.POPULAR_MOVIES,
                                    MediaCategory.UPCOMING_MOVIES,
                                    MediaCategory.POPULAR_SERIES,
                                    MediaCategory.UPCOMING_SERIES -> {
                                        loadCategory(category, forceRefresh = true)
                                    }

                                    // Refresh new category card categories
                                    MediaCategory.MOVIE_GENRES,
                                    MediaCategory.SERIES_GENRES,
                                    MediaCategory.STUDIOS,
                                    MediaCategory.NETWORKS -> {
                                        loadCategoryCards(category, forceRefresh = true)
                                    }
                                }
                            } finally {
                                categoryLoadingState[category] = false
                            }
                        }
                    }
                } finally {
                    _isRefreshing.value = false
                }
            }
        }
    }

    /**
     * Refreshes a specific category
     * This could be called by UI actions or programmatically
     */
    fun refreshCategory(category: MediaCategory) {
        setLastAction {
            viewModelScope.launch {
                _isRefreshing.value = true
                try {
                    categoryLoadingState[category] = true
                    when (category) {
                        // Refresh traditional media category
                        MediaCategory.RECENTLY_ADDED,
                        MediaCategory.RECENT_REQUESTS,
                        MediaCategory.TRENDING,
                        MediaCategory.POPULAR_MOVIES,
                        MediaCategory.UPCOMING_MOVIES,
                        MediaCategory.POPULAR_SERIES,
                        MediaCategory.UPCOMING_SERIES -> {
                            loadCategory(category, forceRefresh = true)
                        }

                        // Refresh new category card category
                        MediaCategory.MOVIE_GENRES,
                        MediaCategory.SERIES_GENRES,
                        MediaCategory.STUDIOS,
                        MediaCategory.NETWORKS -> {
                            loadCategoryCards(category, forceRefresh = true)
                        }
                    }
                } finally {
                    categoryLoadingState[category] = false
                    _isRefreshing.value = false
                }
            }
        }
    }

    /**
     * Refreshes a category with stronger force options, including a complete data clear
     * This is used for critical refresh operations like after a request is submitted
     */
    fun refreshCategoryWithForce(category: MediaCategory?) {
        if (category == null) {
            // Handle null case - just clear any current reset event
            _carouselResetEvents.value = null
            return
        }

        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d("SeerrTV", "🔄 Performing forced refresh for ${category.name}")
            }

            try {
                // First notify UI that we're loading
                _categoryData.value = _categoryData.value.toMutableMap().apply {
                    put(category, ApiResult.Loading())
                }

                // Clear existing data completely and remove from cacheTimestamps
                clearCategoryData(category)
                cacheTimestamps.remove(category)

                // Reset API pagination
                resetApiPagination(category)

                // Force a carousel reset first to make sure the view is clean
                forceCarouselReset(category, animate = true)

                // Small delay to ensure reset is processed
                delay(200)

                // Now perform the standard refresh
                refreshCategory(category)

                // Add a delay to ensure data fetch is complete
                delay(500)

                // Check if data was loaded - if not, force another reload
                if (categoryMediaLists[category]?.isEmpty() == true) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "SeerrTV",
                            "⚠️ No data loaded after refresh for ${category.name}, retrying..."
                        )
                    }
                    refreshCategory(category)
                    delay(300)
                }

                // Final force carousel update and reset to ensure UI is updated
                _carouselUpdates.value = CarouselUpdateEvent(
                    category = category,
                    itemsBefore = 0,
                    itemsAfter = categoryMediaLists[category]?.size ?: 0
                )

                // Also force a final carousel reset
                forceCarouselReset(category, animate = true)

                if (BuildConfig.DEBUG) {
                    Log.d("SeerrTV", "✅ Forced refresh completed for ${category.name}")
                    Log.d(
                        "SeerrTV",
                        "📊 Category now has ${categoryMediaLists[category]?.size ?: 0} items"
                    )
                }
            } catch (e: Exception) {
                Log.e("SeerrTV", "Error during forced refresh of ${category.name}: ${e.message}", e)
            }
        }
    }

    /**
     * Resets the API pagination for a specific category
     * This ensures the next API call starts from page 1
     */
    fun resetApiPagination(category: MediaCategory) {
        viewModelScope.launch {
            try {
                if (BuildConfig.DEBUG) {
                    Log.d("SeerrTV", "🔄 Resetting API pagination for ${category.name}")
                }

                when (category) {
                    MediaCategory.RECENTLY_ADDED -> apiService.getRecentlyAdded(reset = true)
                    MediaCategory.RECENT_REQUESTS -> apiService.getRequests(reset = true)
                    MediaCategory.POPULAR_MOVIES -> apiService.discoverMovies(reset = true)
                    MediaCategory.TRENDING -> apiService.getTrending(reset = true)
                    MediaCategory.UPCOMING_MOVIES -> apiService.getUpcomingMovies(reset = true)
                    MediaCategory.POPULAR_SERIES -> apiService.getPopularSeries(reset = true)
                    MediaCategory.UPCOMING_SERIES -> apiService.getUpcomingSeries(reset = true)
                    MediaCategory.MOVIE_GENRES -> apiService.getMovieGenres(context, reset = true)
                    MediaCategory.SERIES_GENRES -> apiService.getTVGenres(context, reset = true)
                    MediaCategory.STUDIOS -> apiService.getStudios(reset = true)
                    MediaCategory.NETWORKS -> apiService.getNetworks(reset = true)
                }
            } catch (e: Exception) {
                Log.e("SeerrTV", "Error resetting pagination for ${category.name}: ${e.message}", e)
            }
        }
    }

    private suspend fun loadCategory(
        category: MediaCategory,
        forceRefresh: Boolean = false,
        loadMore: Boolean = false
    ) {
        val categoryTitle = getCategoryTitle(context, category)
        val currentTime = System.currentTimeMillis()
        val lastUpdateTime = cacheTimestamps[category] ?: 0L
        val cacheExpired = (currentTime - lastUpdateTime) > TimeUnit.MINUTES.toMillis(5)

        // Skip cache check if we're loading more data for pagination
        if (!loadMore) {
            if (forceRefresh) {
                // Clear existing data for this category
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "SeerrTV",
                        "🗑️ Force refresh: Clearing existing data for $categoryTitle (current size: ${categoryMediaLists[category]?.size ?: 0})"
                    )
                }
                categoryMediaLists[category]?.clear()

                // Reset pagination state in API service for this category
                when (category) {
                    MediaCategory.RECENTLY_ADDED -> apiService.getRecentlyAdded(reset = true)
                    MediaCategory.RECENT_REQUESTS -> apiService.getRequests(reset = true)
                    else -> {} // Other categories handle reset in their respective API calls
                }
            } else if (!cacheExpired && _categoryData.value[category] is ApiResult.Success) {
                Log.d("SeerrTV", "Category $categoryTitle cache is still valid, skipping")
                return
            }
        }

        Log.d(
            "SeerrTV",
            "Loading category: $categoryTitle (forceRefresh: $forceRefresh, loadMore: $loadMore)"
        )

        // Keep existing data while loading more
        if (loadMore && _categoryData.value[category] is ApiResult.Success) {
            val currentData = _categoryData.value[category] as ApiResult.Success
            _categoryData.value = _categoryData.value.toMutableMap().apply {
                put(category, ApiResult.Success(currentData.data, currentData.paginationInfo))
            }
        }

        try {
            val result = when (category) {
                MediaCategory.RECENTLY_ADDED -> fetchRecentlyAdded(forceRefresh)
                MediaCategory.RECENT_REQUESTS -> fetchRecentRequests(forceRefresh)
                MediaCategory.POPULAR_MOVIES -> apiService.discoverMovies(reset = forceRefresh)
                MediaCategory.TRENDING -> apiService.getTrending(reset = forceRefresh)
                MediaCategory.UPCOMING_MOVIES -> apiService.getUpcomingMovies(reset = forceRefresh)
                MediaCategory.POPULAR_SERIES -> apiService.getPopularSeries(reset = forceRefresh)
                MediaCategory.UPCOMING_SERIES -> apiService.getUpcomingSeries(reset = forceRefresh)
                else -> throw IllegalArgumentException("Category $category should not be processed by loadCategory. Use loadCategoryCards instead.")
            }

            when (result) {
                is ApiResult.Success<*> -> {
                    val mediaList = categoryMediaLists.getOrPut(category) { mutableListOf() }

                    when (val data = result.data) {
                        is List<*> -> {
                            if (data.all { it is Media }) {
                                if (!loadMore) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(
                                            "SeerrTV",
                                            "📋 INITIAL LOAD: Category $categoryTitle - clearing list and adding ${data.size} items"
                                        )
                                    }
                                    mediaList.clear()
                                    mediaList.addAll(data.filterIsInstance<Media>())
                                } else {
                                    // Filter out duplicates when loading more
                                    val existingIds = mediaList.map { it.id }.toSet()
                                    val newItems = data.filterIsInstance<Media>()
                                        .filter { it.id !in existingIds }

                                    // Debug logging for filtered duplicates
                                    if (BuildConfig.DEBUG) {
                                        if (newItems.size < data.size) {
                                            Log.d(
                                                "SeerrTV",
                                                "⚠️ Filtered out ${data.size - newItems.size} duplicate items in $categoryTitle"
                                            )
                                        }
                                        Log.d(
                                            "SeerrTV",
                                            "📋 LOAD MORE: Category $categoryTitle - adding ${newItems.size} items to existing ${mediaList.size}"
                                        )
                                    }

                                    mediaList.addAll(newItems)
                                }
                            } else {
                                throw IllegalStateException("List contains non-Media elements")
                            }
                        }

                        is PaginatedMediaResponse -> {
                            if (!loadMore) {
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "SeerrTV",
                                        "📋 INITIAL LOAD: Category $categoryTitle - clearing list and adding ${data.results.size} items from PaginatedResponse"
                                    )
                                }
                                mediaList.clear()
                                mediaList.addAll(data.results)
                            } else {
                                // Filter out duplicates when loading more
                                val existingIds = mediaList.map { it.id }.toSet()
                                val newItems = data.results.filter { it.id !in existingIds }

                                // Debug logging for filtered duplicates
                                if (BuildConfig.DEBUG) {
                                    if (newItems.size < data.results.size) {
                                        Log.d(
                                            "SeerrTV",
                                            "⚠️ Filtered out ${data.results.size - newItems.size} duplicate items in $categoryTitle"
                                        )
                                    }
                                    Log.d(
                                        "SeerrTV",
                                        "📋 LOAD MORE: Category $categoryTitle - adding ${newItems.size} items to existing ${mediaList.size}"
                                    )
                                }

                                mediaList.addAll(newItems)
                            }
                        }

                        else -> throw IllegalStateException("Unexpected data type: ${data!!::class.java}")
                    }

                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "SeerrTV",
                            "📦 FINAL: Category $categoryTitle now has ${mediaList.size} items"
                        )
                    }

                    _categoryData.value = _categoryData.value.toMutableMap().apply {
                        put(category, ApiResult.Success(mediaList.toList(), result.paginationInfo))
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "SeerrTV",
                            "🔄 Updated _categoryData for $categoryTitle, total categories: ${_categoryData.value.size}"
                        )
                        Log.d(
                            "SeerrTV",
                            "🔄 Current categoryData keys: ${_categoryData.value.keys.map { it.name }}"
                        )
                    }
                    cacheTimestamps[category] = System.currentTimeMillis()
                }

                is ApiResult.Error -> {
                    Log.e(
                        "SeerrTV",
                        "Error loading category $categoryTitle: ${result.exception.message}"
                    )
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                    if (!loadMore) {
                        _categoryData.value =
                            _categoryData.value.toMutableMap().apply { put(category, result) }
                    }
                }

                is ApiResult.Loading -> {
                    Log.d("SeerrTV", "Category $categoryTitle still loading")
                    if (!loadMore) {
                        _categoryData.value =
                            _categoryData.value.toMutableMap().apply { put(category, result) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SeerrTV", "Error loading category $categoryTitle: ${e.message}")
            if (handleApiError(e)) {
                authErrorState.showError()
            }
            if (!loadMore) {
                _categoryData.value =
                    _categoryData.value.toMutableMap().apply { put(category, ApiResult.Error(e)) }
            }
        }
    }

    private suspend fun loadCategoryCards(
        category: MediaCategory,
        forceRefresh: Boolean = false,
        loadMore: Boolean = false
    ) {
        val categoryTitle = getCategoryTitle(context, category)
        val currentTime = System.currentTimeMillis()
        val lastUpdateTime = cacheTimestamps[category] ?: 0L
        val cacheExpired = (currentTime - lastUpdateTime) > TimeUnit.MINUTES.toMillis(5)

        // Skip cache check if we're loading more data for pagination
        if (!loadMore) {
            if (forceRefresh) {
                // Clear existing data for this category
                categoryCardLists[category]?.clear()
            } else if (!cacheExpired && _categoryCardData.value[category] is ApiResult.Success) {
                Log.d("SeerrTV", "Category cards $categoryTitle cache is still valid, skipping")
                return
            }
        }

        Log.d(
            "SeerrTV",
            "Loading category cards: $categoryTitle (forceRefresh: $forceRefresh, loadMore: $loadMore)"
        )

        try {
            val result = when (category) {
                MediaCategory.MOVIE_GENRES -> apiService.getMovieGenres(
                    context,
                    reset = forceRefresh,
                    loadMore = loadMore
                )

                MediaCategory.SERIES_GENRES -> apiService.getTVGenres(
                    context,
                    reset = forceRefresh,
                    loadMore = loadMore
                )

                MediaCategory.STUDIOS -> apiService.getStudios(
                    reset = forceRefresh,
                    loadMore = loadMore
                )

                MediaCategory.NETWORKS -> apiService.getNetworks(
                    reset = forceRefresh,
                    loadMore = loadMore
                )

                else -> throw IllegalArgumentException("Category $category is not a category card type")
            }

            when (result) {
                is ApiResult.Success<*> -> {
                    val cardList = categoryCardLists.getOrPut(category) { mutableListOf() }

                    when (val data = result.data) {
                        is List<*> -> {
                            // Create a set to track used backdrops for this category
                            val usedBackdrops = mutableSetOf<String>()

                            val categoryCards = data.mapNotNull { item ->
                                when (category) {
                                    MediaCategory.MOVIE_GENRES -> {
                                        val genre = item as? SeerrApiService.GenreResponse
                                        genre?.let {
                                            // Get available backdrops that haven't been used yet
                                            val availableBackdrops =
                                                it.backdrops.filterNot { backdrop ->
                                                    backdrop in usedBackdrops || backdrop.isBlank()
                                                }

                                            // Pick a random backdrop or fall back to first if all are used
                                            val selectedBackdrop = when {
                                                availableBackdrops.isNotEmpty() -> {
                                                    // Pick random backdrop from available ones
                                                    availableBackdrops.random().also { backdrop ->
                                                        usedBackdrops.add(backdrop)
                                                    }
                                                }

                                                it.backdrops.isNotEmpty() -> {
                                                    // Fall back to first backdrop if all are used
                                                    it.backdrops.first()
                                                }

                                                else -> null
                                            }

                                            CategoryCard(
                                                id = it.id,
                                                name = it.name,
                                                type = category,
                                                imagePath = selectedBackdrop,
                                                discoveryType = DiscoveryType.MOVIE_GENRE
                                            )
                                        }
                                    }

                                    MediaCategory.SERIES_GENRES -> {
                                        val genre = item as? SeerrApiService.GenreResponse
                                        genre?.let {
                                            // Get available backdrops that haven't been used yet
                                            val availableBackdrops =
                                                it.backdrops.filterNot { backdrop ->
                                                    backdrop in usedBackdrops || backdrop.isBlank()
                                                }

                                            // Pick a random backdrop or fall back to first if all are used
                                            val selectedBackdrop = when {
                                                availableBackdrops.isNotEmpty() -> {
                                                    // Pick random backdrop from available ones
                                                    availableBackdrops.random().also { backdrop ->
                                                        usedBackdrops.add(backdrop)
                                                    }
                                                }

                                                it.backdrops.isNotEmpty() -> {
                                                    // Fall back to first backdrop if all are used
                                                    it.backdrops.first()
                                                }

                                                else -> null
                                            }

                                            CategoryCard(
                                                id = it.id,
                                                name = it.name,
                                                type = category,
                                                imagePath = selectedBackdrop,
                                                discoveryType = DiscoveryType.SERIES_GENRE
                                            )
                                        }
                                    }

                                    MediaCategory.STUDIOS -> {
                                        val studio = item as? SeerrApiService.StudioResponse
                                        studio?.let {
                                            CategoryCard(
                                                id = it.id,
                                                name = it.name,
                                                type = category,
                                                imagePath = it.logoPath,
                                                discoveryType = DiscoveryType.STUDIO
                                            )
                                        }
                                    }

                                    MediaCategory.NETWORKS -> {
                                        val network = item as? SeerrApiService.NetworkResponse
                                        network?.let {
                                            CategoryCard(
                                                id = it.id,
                                                name = it.name,
                                                type = category,
                                                imagePath = it.logoPath,
                                                discoveryType = DiscoveryType.NETWORK
                                            )
                                        }
                                    }

                                    else -> null
                                }
                            }

                            if (!loadMore) {
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "SeerrTV",
                                        "📋 INITIAL LOAD: Category $categoryTitle - clearing list and adding ${categoryCards.size} cards"
                                    )
                                }
                                cardList.clear()
                                cardList.addAll(categoryCards)
                            } else {
                                // Filter out duplicates when loading more
                                val existingIds = cardList.map { it.id }.toSet()
                                val newCards = categoryCards.filter { it.id !in existingIds }

                                if (BuildConfig.DEBUG) {
                                    if (newCards.size < categoryCards.size) {
                                        Log.d(
                                            "SeerrTV",
                                            "⚠️ Filtered out ${categoryCards.size - newCards.size} duplicate cards in $categoryTitle"
                                        )
                                    }
                                    Log.d(
                                        "SeerrTV",
                                        "📋 LOAD MORE: Category $categoryTitle - adding ${newCards.size} cards to existing ${cardList.size}"
                                    )
                                }

                                cardList.addAll(newCards)
                            }
                        }

                        else -> throw IllegalStateException("Unexpected data type: ${data!!::class.java}")
                    }

                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "SeerrTV",
                            "📦 FINAL: Category $categoryTitle now has ${cardList.size} cards"
                        )
                    }

                    _categoryCardData.value = _categoryCardData.value.toMutableMap().apply {
                        put(category, ApiResult.Success(cardList.toList(), result.paginationInfo))
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "SeerrTV",
                            "🔄 Updated _categoryCardData for $categoryTitle, total categories: ${_categoryCardData.value.size}"
                        )
                        Log.d(
                            "SeerrTV",
                            "🔄 Current categoryCardData keys: ${_categoryCardData.value.keys.map { it.name }}"
                        )
                    }
                    cacheTimestamps[category] = System.currentTimeMillis()
                }

                is ApiResult.Error -> {
                    Log.e(
                        "SeerrTV",
                        "Error loading category cards $categoryTitle: ${result.exception.message}"
                    )
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                    if (!loadMore) {
                        _categoryCardData.value = _categoryCardData.value.toMutableMap()
                            .apply { put(category, ApiResult.Error(result.exception)) }
                    }
                }

                is ApiResult.Loading -> {
                    Log.d("SeerrTV", "Category cards $categoryTitle still loading")
                    if (!loadMore) {
                        _categoryCardData.value =
                            _categoryCardData.value.toMutableMap().apply { put(category, result) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SeerrTV", "Error loading category cards $categoryTitle: ${e.message}")
            if (handleApiError(e)) {
                authErrorState.showError()
            }
            if (!loadMore) {
                _categoryCardData.value = _categoryCardData.value.toMutableMap()
                    .apply { put(category, ApiResult.Error(e)) }
            }
        }
    }

    fun loadMoreForCategory(context: Context, category: MediaCategory) {
        // Set the loading flag - will also update category state and show placeholder
        setLoadMoreFlag(category, true)

        viewModelScope.launch {
            // Check if category is already loading
            if (categoryLoadingState[category] == true) {
                Log.d(
                    "SeerrTV",
                    "🔄 Category ${getCategoryTitle(context, category)} is already loading, skipping"
                )
                return@launch
            }

            // Capture the initial item count for the category
            val initialCount = when (category) {
                MediaCategory.MOVIE_GENRES,
                MediaCategory.SERIES_GENRES,
                MediaCategory.STUDIOS,
                MediaCategory.NETWORKS -> getCategoryCardList(category).size

                else -> getCategoryMediaList(category).size
            }

            if (BuildConfig.DEBUG) {
                Log.d(
                    "SeerrTV",
                    "📊 Starting loadMore for ${category.name} with initial count: $initialCount"
                )
            }

            when (category) {
                // Handle loading more for traditional media categories
                MediaCategory.RECENTLY_ADDED,
                MediaCategory.RECENT_REQUESTS,
                MediaCategory.TRENDING,
                MediaCategory.POPULAR_MOVIES,
                MediaCategory.UPCOMING_MOVIES,
                MediaCategory.POPULAR_SERIES,
                MediaCategory.UPCOMING_SERIES -> {
                    val currentData = _categoryData.value[category]
                    val currentMediaList = categoryMediaLists[category]?.toList() ?: emptyList()

                    // Log pagination state before loading more
                    if (BuildConfig.DEBUG) {
                        val mediaCount = getCategoryMediaList(category).size
                        if (currentData is ApiResult.Success) {
                            val paginationInfo = currentData.paginationInfo
                            Log.d(
                                "SeerrTV", "📑 Category ${getCategoryTitle(context, category)}: " +
                                        "Current Items: $mediaCount, " +
                                        "Page: ${paginationInfo?.currentPage ?: "unknown"}/${paginationInfo?.totalPages ?: "unknown"}, " +
                                        "Has more: ${paginationInfo?.hasMorePages}"
                            )

                            // Update category state with current pagination info
                            updateCategoryState(
                                category,
                                mediaCount,
                                paginationInfo?.hasMorePages != false
                            )
                        } else {
                            Log.d(
                                "SeerrTV", "⚠️ Category ${getCategoryTitle(context, category)}: " +
                                        "Current Items: $mediaCount, No pagination info available"
                            )
                        }
                    }

                    // Explicitly handle null pagination info case - only don't load if hasMorePages is explicitly false
                    if (currentData is ApiResult.Success && currentData.paginationInfo?.hasMorePages != false) {
                        try {
                            categoryLoadingState[category] = true

                            // Keep showing existing data while loading more
                            if (currentMediaList.isNotEmpty()) {
                                _categoryData.value = _categoryData.value.toMutableMap().apply {
                                    put(
                                        category,
                                        ApiResult.Success(
                                            currentMediaList,
                                            currentData.paginationInfo
                                        )
                                    )
                                }
                            }

                            Log.d(
                                "SeerrTV",
                                "📥 Starting to load more data for ${
                                    getCategoryTitle(
                                        context,
                                        category
                                    )
                                }"
                            )

                            // Load new data in a separate coroutine to avoid blocking
                            launch {
                                loadCategory(category, forceRefresh = false, loadMore = true)

                                // After loading, track the carousel update
                                val finalCount = getCategoryMediaList(category).size
                                val hasMorePages =
                                    (_categoryData.value[category] as? ApiResult.Success)?.paginationInfo?.hasMorePages != false

                                // Update category state with new counts and pagination info
                                updateCategoryState(category, finalCount, hasMorePages)

                                val newItems = finalCount - initialCount

                                // Always emit an update event, even if no new items were added
                                // This helps the UI know that the loading process has completed
                                if (newItems > 0) {
                                    // High priority update for new items - critical for scrolling
                                    _carouselUpdates.value = CarouselUpdateEvent(
                                        category = category,
                                        itemsBefore = initialCount,
                                        itemsAfter = finalCount
                                    )

                                    // Ensure update is noticed by clearing and sending again after a tiny delay
                                    delay(50)
                                    _carouselUpdates.value = null
                                    delay(50)
                                    _carouselUpdates.value = CarouselUpdateEvent(
                                        category = category,
                                        itemsBefore = initialCount,
                                        itemsAfter = finalCount
                                    )
                                } else {
                                    // Still send at least one update
                                    _carouselUpdates.value = CarouselUpdateEvent(
                                        category = category,
                                        itemsBefore = initialCount,
                                        itemsAfter = finalCount
                                    )
                                }

                                if (BuildConfig.DEBUG) {
                                    if (newItems > 0) {
                                        Log.d(
                                            "SeerrTV",
                                            "✅ CAROUSEL UPDATE: Category ${
                                                getCategoryTitle(
                                                    context,
                                                    category
                                                )
                                            } added $newItems new items"
                                        )
                                    } else {
                                        Log.d(
                                            "SeerrTV",
                                            "ℹ️ CAROUSEL UPDATE: Category ${
                                                getCategoryTitle(
                                                    context,
                                                    category
                                                )
                                            } no new items added, finished loading"
                                        )
                                    }
                                }
                            }
                        } finally {
                            categoryLoadingState[category] = false

                            // Clear the loading flag after a delay to ensure UI has time to process the results
                            // This will also ensure the placeholder stays visible for the minimum time
                            delay(500)
                            setLoadMoreFlag(category, false)

                            // Make sure we always emit a final update event after the placeholder is removed
                            // This ensures the carousel recalculates its position even in edge cases
                            viewModelScope.launch {
                                delay(100)  // Very short delay after placeholder is removed
                                _carouselUpdates.value = CarouselUpdateEvent(
                                    category = category,
                                    itemsBefore = getCategoryMediaList(category).size,
                                    itemsAfter = getCategoryMediaList(category).size
                                )

                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "SeerrTV",
                                        "🔄 FINAL UPDATE: Category ${
                                            getCategoryTitle(
                                                context,
                                                category
                                            )
                                        } final position update, item count: ${
                                            getCategoryMediaList(
                                                category
                                            ).size
                                        }"
                                    )
                                }
                            }
                        }
                    } else {
                        // No more pages available, clear loading flag and update state
                        setLoadMoreFlag(category, false)
                        updateCategoryState(category, getCategoryMediaList(category).size, false)

                        // Still emit an update to inform the UI that loading is complete
                        _carouselUpdates.value = CarouselUpdateEvent(
                            category = category,
                            itemsBefore = initialCount,
                            itemsAfter = initialCount
                        )

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "SeerrTV",
                                "🛑 CAROUSEL UPDATE: Category ${
                                    getCategoryTitle(
                                        context,
                                        category
                                    )
                                } reached end of data"
                            )
                        }
                    }
                }

                // Handle loading more for category card categories
                MediaCategory.MOVIE_GENRES,
                MediaCategory.SERIES_GENRES,
                MediaCategory.STUDIOS,
                MediaCategory.NETWORKS -> {
                    val currentData = _categoryCardData.value[category]
                    val currentCardList = categoryCardLists[category]?.toList() ?: emptyList()

                    // Log pagination state before loading more
                    if (BuildConfig.DEBUG) {
                        val cardCount = getCategoryCardList(category).size
                        if (currentData is ApiResult.Success) {
                            val paginationInfo = currentData.paginationInfo
                            Log.d(
                                "SeerrTV",
                                "📑 Category Cards ${getCategoryTitle(context, category)}: " +
                                        "Current Items: $cardCount, " +
                                        "Page: ${paginationInfo?.currentPage ?: "unknown"}/${paginationInfo?.totalPages ?: "unknown"}, " +
                                        "Has more: ${paginationInfo?.hasMorePages}"
                            )

                            // Update category state with current pagination info
                            updateCategoryState(
                                category,
                                cardCount,
                                paginationInfo?.hasMorePages != false
                            )
                        } else {
                            Log.d(
                                "SeerrTV",
                                "⚠️ Category Cards ${getCategoryTitle(context, category)}: " +
                                        "Current Items: $cardCount, No pagination info available"
                            )
                        }
                    }

                    // Only load more if there are more pages available
                    if (currentData is ApiResult.Success && currentData.paginationInfo?.hasMorePages != false) {
                        try {
                            categoryLoadingState[category] = true

                            // Keep showing existing data while loading more
                            if (currentCardList.isNotEmpty()) {
                                _categoryCardData.value =
                                    _categoryCardData.value.toMutableMap().apply {
                                        put(
                                            category,
                                            ApiResult.Success(
                                                currentCardList,
                                                currentData.paginationInfo
                                            )
                                        )
                                    }
                            }

                            Log.d(
                                "SeerrTV",
                                "📥 Starting to load more category cards for ${
                                    getCategoryTitle(
                                        context,
                                        category
                                    )
                                }"
                            )

                            // Load new data in a separate coroutine to avoid blocking
                            launch {
                                loadCategoryCards(category, forceRefresh = false, loadMore = true)

                                // After loading, track the carousel update
                                val finalCount = getCategoryCardList(category).size
                                val hasMorePages =
                                    (_categoryCardData.value[category] as? ApiResult.Success)?.paginationInfo?.hasMorePages != false

                                // Update category state with new counts and pagination info
                                updateCategoryState(category, finalCount, hasMorePages)

                                val newItems = finalCount - initialCount

                                // Always emit an update event, even if no new items were added
                                // This helps the UI know that the loading process has completed
                                if (newItems > 0) {
                                    // High priority update for new items - critical for scrolling
                                    _carouselUpdates.value = CarouselUpdateEvent(
                                        category = category,
                                        itemsBefore = initialCount,
                                        itemsAfter = finalCount
                                    )

                                    // Ensure update is noticed by clearing and sending again after a tiny delay
                                    delay(50)
                                    _carouselUpdates.value = null
                                    delay(50)
                                    _carouselUpdates.value = CarouselUpdateEvent(
                                        category = category,
                                        itemsBefore = initialCount,
                                        itemsAfter = finalCount
                                    )
                                } else {
                                    // Still send at least one update
                                    _carouselUpdates.value = CarouselUpdateEvent(
                                        category = category,
                                        itemsBefore = initialCount,
                                        itemsAfter = finalCount
                                    )
                                }

                                if (BuildConfig.DEBUG) {
                                    if (newItems > 0) {
                                        Log.d(
                                            "SeerrTV",
                                            "✅ CAROUSEL UPDATE: Category ${
                                                getCategoryTitle(
                                                    context,
                                                    category
                                                )
                                            } added $newItems new category cards"
                                        )
                                    } else {
                                        Log.d(
                                            "SeerrTV",
                                            "ℹ️ CAROUSEL UPDATE: Category ${
                                                getCategoryTitle(
                                                    context,
                                                    category
                                                )
                                            } no new category cards added, finished loading"
                                        )
                                    }
                                }
                            }
                        } finally {
                            categoryLoadingState[category] = false

                            // Clear the loading flag after a delay to ensure UI has time to process the results
                            // This will also ensure the placeholder stays visible for the minimum time
                            delay(500)
                            setLoadMoreFlag(category, false)

                            // Make sure we always emit a final update event after the placeholder is removed
                            // This ensures the carousel recalculates its position even in edge cases
                            viewModelScope.launch {
                                delay(100)  // Very short delay after placeholder is removed
                                _carouselUpdates.value = CarouselUpdateEvent(
                                    category = category,
                                    itemsBefore = getCategoryCardList(category).size,
                                    itemsAfter = getCategoryCardList(category).size
                                )

                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "SeerrTV",
                                        "🔄 FINAL UPDATE: Category ${
                                            getCategoryTitle(
                                                context,
                                                category
                                            )
                                        } final position update, item count: ${
                                            getCategoryCardList(
                                                category
                                            ).size
                                        }"
                                    )
                                }
                            }
                        }
                    } else {
                        // No more pages available, clear loading flag and update state
                        setLoadMoreFlag(category, false)
                        updateCategoryState(category, getCategoryCardList(category).size, false)

                        // Still emit an update to inform the UI that loading is complete
                        _carouselUpdates.value = CarouselUpdateEvent(
                            category = category,
                            itemsBefore = initialCount,
                            itemsAfter = initialCount
                        )

                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "SeerrTV",
                                "🛑 CAROUSEL UPDATE: Category ${
                                    getCategoryTitle(
                                        context,
                                        category
                                    )
                                } reached end of data"
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the current media list for a specific category
     * @param category The category to get the media list for
     * @return The list of media items for the category, or an empty list if none are loaded
     */
    fun getCategoryMediaList(category: MediaCategory): List<Media> {
        val mediaList = categoryMediaLists[category] ?: emptyList()

        // Log the media list size to help with debugging
        if (BuildConfig.DEBUG) {
            Log.d("SeerrTV", "📋 getCategoryMediaList for ${category.name}: ${mediaList.size} items")
        }

        return mediaList
    }

    /**
     * Get the current category card list for a specific category
     * @param category The category to get the card list for
     * @return The list of category cards for the category, or an empty list if none are loaded
     */
    fun getCategoryCardList(category: MediaCategory): List<CategoryCard> {
        val cardList = categoryCardLists[category] ?: emptyList()

        // Log the card list size to help with debugging
        if (BuildConfig.DEBUG) {
            Log.d("SeerrTV", "📋 getCategoryCardList for ${category.name}: ${cardList.size} items")
        }

        return cardList
    }

    /**
     * Update the size of a category's media list
     * This method is used to ensure all components have the latest list size
     * @param category The category to update
     * @param size The new size to set
     */
    fun updateCategorySize(category: MediaCategory, size: Int) {
        // Only update if this is a meaningful size change
        val currentData = _categoryData.value[category]
        if (currentData is ApiResult.Success && currentData.data.size != size) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    "SeerrTV",
                    "📊 Updating size for ${category.name}: ${currentData.data.size} → $size"
                )
            }

            // Create a new media list with the updated size
            val mediaList = getCategoryMediaList(category)

            // Update the category data with the new size
            _categoryData.value = _categoryData.value.toMutableMap().apply {
                put(category, ApiResult.Success(mediaList, currentData.paginationInfo))
            }
        }
    }

    private fun cleanupMediaDetailsCache() {
        val currentTime = System.currentTimeMillis()
        _mediaDetailsCache.update { cache ->
            // Remove expired entries
            val cleanedCache = cache.filterValues { cached ->
                (currentTime - cached.timestamp) < cached.cacheTimeMs
            }

            // If still too large, remove oldest entries
            if (cleanedCache.size > MAX_CACHE_SIZE) {
                cleanedCache.toList()
                    .sortedBy { it.second.timestamp }
                    .drop(cleanedCache.size - MAX_CACHE_SIZE)
                    .toMap()
            } else {
                cleanedCache
            }
        }
    }

    /**
     * Update the media details cache with new data from polling
     * This ensures that status updates are reflected in the main UI
     */
    fun updateMediaDetailsCache(mediaId: String, mediaType: String, mediaDetails: MediaDetails) {
        val cacheKey = "$mediaId-$mediaType"
        val currentTime = System.currentTimeMillis()
        
        Log.d(TAG, "Updating media details cache for $cacheKey with status: ${mediaDetails.mediaInfo?.status}, status4k: ${mediaDetails.mediaInfo?.status4k}")
        
        // Update the main cache
        _mediaDetailsCache.update { cache ->
            cache + (cacheKey to CachedMediaDetails(
                details = ApiResult.Success(mediaDetails),
                timestamp = currentTime,
                cacheTimeMs = DEFAULT_CACHE_TIME_MS
            ))
        }
        
        // Also update the current media details if this is the currently displayed media
        if (currentMediaId == mediaId && currentMediaType == mediaType) {
            _currentMediaDetails.value = ApiResult.Success(mediaDetails)
            Log.d(TAG, "Updated current media details for $mediaId ($mediaType)")
        }
    }

    fun getMediaDetails(
        mediaId: String,
        mediaType: String,
        cacheTimeMinutes: Long =  (DEFAULT_CACHE_TIME_MS / 60_000L).coerceAtLeast(1),
        forceRefresh: Boolean = false
    ): StateFlow<ApiResult<MediaDetails>> {
        val cacheKey = "$mediaId-$mediaType"
        
        // Check if we're already fetching this media
        val cache = _mediaDetailsCache.value[cacheKey]
        val now = System.currentTimeMillis()
        val cacheFresh = cache != null && (now - cache.timestamp) < cache.cacheTimeMs
        val shouldRefresh = when {
            forceRefresh && !cacheFresh -> true // honor refresh only if stale
            mediaId != currentMediaId || mediaType != currentMediaType -> true
            else -> false
        }

        if (shouldRefresh) {
            // Atomically ensure only one job is created per key
            synchronized(mediaDetailsJobs) {
                val active = mediaDetailsJobs[cacheKey]?.isActive == true
                val now = System.currentTimeMillis()
                val cooldownUntil = inCooldownUntilMs[cacheKey] ?: 0L
                if (active || now < cooldownUntil) {
                    return _currentMediaDetails
                }
                // Begin cooldown window so immediate followers bail out
                inCooldownUntilMs[cacheKey] = now + 1000L
            }

            // Double-check not to recreate after leaving synchronized block
            currentMediaId = mediaId
            currentMediaType = mediaType
            
            // Register the job BEFORE launching to prevent race conditions
            val job = viewModelScope.launch {
                try {
                    val lock = synchronized(fetchLocks) { fetchLocks.getOrPut(cacheKey) { Mutex() } }
                    lock.withLock {
                        val currentTime = System.currentTimeMillis()

                        // Clean up cache before checking
                        cleanupMediaDetailsCache()

                        val cachedResult = _mediaDetailsCache.value[cacheKey]

                        // Check if polling is already active for this media - if so, don't make duplicate API calls
                        // BUT only skip if we already have data in cache or current state
                        val isPollingActive = _isPollingActive.value && pollingTmdbId == mediaId && pollingMediaType == mediaType

                        if (!forceRefresh && cachedResult != null) {
                            val timeSinceCache = currentTime - cachedResult.timestamp
                            val effectiveCacheTimeMs = minOf(TimeUnit.MINUTES.toMillis(cacheTimeMinutes), cachedResult.cacheTimeMs)
                            val cacheExpirationTime = effectiveCacheTimeMs

                            if (timeSinceCache < cacheExpirationTime) {
                                if (effectiveCacheTimeMs < cachedResult.cacheTimeMs) {
                                    _mediaDetailsCache.update { cache ->
                                        cache + (cacheKey to CachedMediaDetails(
                                            details = cachedResult.details,
                                            timestamp = cachedResult.timestamp,
                                            cacheTimeMs = effectiveCacheTimeMs
                                        ))
                                    }
                                }
                                _currentMediaDetails.value = cachedResult.details
                            } else if (!isPollingActive) {
                                // Fetch if polling is not active OR if we don't have data yet
                                val lastStart = lastFetchStartMs[cacheKey] ?: 0L
                                if (currentTime - lastStart >= 1000L) {
                                    lastFetchStartMs[cacheKey] = currentTime
                                    fetchMediaDetails(
                                    mediaId,
                                    mediaType,
                                    cacheKey,
                                    currentTime,
                                    effectiveCacheTimeMs
                                    )
                                }
                            }
                        } else {
                            // Always fetch on initial load, even if polling is active
                            // The polling will handle subsequent updates
                            val ttlMs = TimeUnit.MINUTES.toMillis(cacheTimeMinutes)
                            val lastStart = lastFetchStartMs[cacheKey] ?: 0L
                            if (currentTime - lastStart >= 1000L) {
                                lastFetchStartMs[cacheKey] = currentTime
                                fetchMediaDetails(mediaId, mediaType, cacheKey, currentTime, ttlMs)
                            }
                        }
                    }
                } finally {
                    // Clean up the job when done
                    mediaDetailsJobs.remove(cacheKey)
                }
            }
            // Register job immediately to close race window
            mediaDetailsJobs[cacheKey] = job
        }
        return _currentMediaDetails
    }

    private suspend fun fetchMediaDetails(
        mediaId: String,
        mediaType: String,
        cacheKey: String,
        currentTime: Long,
        cacheTimeMs: Long
    ) {
        _currentMediaDetails.value = ApiResult.Loading()
        logDebug("Fetching media details for mediaId: $mediaId, mediaType: $mediaType")
        Log.d("SeerrViewModel", "fetchMediaDetails: mediaId=$mediaId, mediaType=$mediaType")

        // Check if there's already an active job for this media
        val existingJob = mediaDetailsJobs[cacheKey]
        if (existingJob?.isActive == true) {
            Log.d("SeerrViewModel", "Media details fetch already in progress for $cacheKey, waiting for completion")
            // Wait for the existing job to complete and return the result
            existingJob.join()
            return
        }

        // Start new job
        mediaDetailsJobs[cacheKey] = viewModelScope.launch {
            try {
                val result = when (mediaType.lowercase()) {
                    "movie" -> {
                        val movieDetails = fetchMovieDetails(mediaId, TimeUnit.MILLISECONDS.toMinutes(cacheTimeMs))
                        if (movieDetails is ApiResult.Success) {
                            val ratings = getRatingsCached(mediaId, "movie", DEFAULT_CACHE_TIME_MS)
                            when (ratings) {
                                is ApiResult.Success -> ApiResult.Success(
                                    movieDetails.data.copy(ratings = ratings.data)
                                )
                                else -> movieDetails
                            }
                        } else movieDetails
                    }
                    "tv" -> {
                        Log.d("SeerrViewModel", "Calling fetchTVDetails for mediaId: $mediaId")
                        val tvDetails = fetchTVDetails(mediaId)
                        if (tvDetails is ApiResult.Success) {
                            val ratings = getRatingsCached(mediaId, "tv", DEFAULT_CACHE_TIME_MS)
                            when (ratings) {
                                is ApiResult.Success -> ApiResult.Success(
                                    tvDetails.data.copy(ratings = ratings.data)
                                )
                                else -> tvDetails
                            }
                        } else tvDetails
                    }
                    else -> ApiResult.Error(Exception("Invalid media type: $mediaType"))
                }
                Log.d("SeerrViewModel", "fetchMediaDetails result: $result")

                if (result is ApiResult.Success) {
                    _mediaDetailsCache.update {
                        it + (cacheKey to CachedMediaDetails(
                            result,
                            currentTime,
                            cacheTimeMs
                        ))
                    }
                }
                _currentMediaDetails.value = result
                logDebug("Media details fetched for mediaId: $mediaId, mediaType: $mediaType")
            } catch (e: Exception) {
                logError("Error fetching media details: ${e.message}", e)
                _currentMediaDetails.value = ApiResult.Error(e)
            } finally {
                // Remove the job when it's done
                mediaDetailsJobs.remove(cacheKey)
            }
        }
    }

    private suspend fun fetchMovieDetails(
        mediaId: String,
        cacheTimeMinutes: Long
    ): ApiResult<MediaDetails> {
        return try {
            when (val movieDetails = apiService.getMovieDetails(mediaId)) {
                is ApiResult.Success -> {
                    val ratingsResult = apiService.getRatingsData(mediaId, "movie")
                    when (ratingsResult) {
                        is ApiResult.Success -> {
                            val result = ApiResult.Success(
                                movieDetails.data.copy(
                                    mediaType = MediaType.MOVIE,
                                    ratings = ratingsResult.data
                                )
                            )
                            _mediaDetailsCache.update {
                                it + ("$mediaId-movie" to CachedMediaDetails(
                                    result,
                                    System.currentTimeMillis(),
                                    cacheTimeMinutes
                                ))
                            }
                            result
                        }

                        is ApiResult.Error -> {
                            logError("Error fetching ratings: ${ratingsResult.exception}")
                            val result =
                                ApiResult.Success(movieDetails.data.copy(mediaType = MediaType.MOVIE))
                            _mediaDetailsCache.update {
                                it + ("$mediaId-movie" to CachedMediaDetails(
                                    result,
                                    System.currentTimeMillis(),
                                    cacheTimeMinutes
                                ))
                            }
                            result
                        }

                        is ApiResult.Loading -> ApiResult.Loading()
                    }
                }

                is ApiResult.Error -> {
                    logError("Error fetching movie details: ${movieDetails.exception}")
                    movieDetails
                }

                is ApiResult.Loading -> ApiResult.Loading()
            }
        } catch (e: Exception) {
            logError("Exception in fetchMovieDetails: ${e.message}", e)
            ApiResult.Error(e)
        }
    }

    private suspend fun fetchTVDetails(mediaId: String): ApiResult<MediaDetails> {
        return try {
            val tvDetails = apiService.getTVDetails(mediaId)
            when (tvDetails) {
                is ApiResult.Success -> {
                    Log.d("SeerrViewModel", "TV Details API response: ${tvDetails.data}")
                    Log.d("SeerrViewModel", "TV Details mediaInfo: ${tvDetails.data.mediaInfo}")
                    Log.d("SeerrViewModel", "TV Details mediaInfo.id: ${tvDetails.data.mediaInfo?.id}")
                    // Legacy TV ratings fetch removed; handled by getRatingsCached path above
                    ApiResult.Success(tvDetails.data.copy(mediaType = MediaType.TV))
                }

                is ApiResult.Error -> {
                    logError("Error fetching TV details: ${tvDetails.exception}")
                    tvDetails
                }

                is ApiResult.Loading -> ApiResult.Loading()
            }
        } catch (e: Exception) {
            logError("Exception in fetchTVDetails: ${e.message}", e)
            ApiResult.Error(e)
        }
    }

    fun forceRefreshMediaDetails(mediaId: String, mediaType: String) {
        val cacheKey = "$mediaId-$mediaType"
        val existingJob = mediaDetailsJobs[cacheKey]
        
        if (existingJob?.isActive == true) {
            // Job is already running, don't start another one
            Log.d(TAG, "Media details fetch already in progress for $mediaId ($mediaType)")
            return
        }

        // Launch a new job
        mediaDetailsJobs[cacheKey] = viewModelScope.launch {
            try {
                // Clear the cache for this media
                _mediaDetailsCache.update { cache ->
                    cache - cacheKey
                }

                // Fetch fresh data directly without re-entering getMediaDetails
                val currentTime = System.currentTimeMillis()
                fetchMediaDetails(mediaId, mediaType, cacheKey, currentTime, DEFAULT_CACHE_TIME_MS)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Log cancellation but don't treat it as an error
                    Log.d(TAG, "Media details fetch cancelled for $mediaId ($mediaType)")
                } else {
                    Log.e(TAG, "Error refreshing media details for $mediaId ($mediaType): ${e.message}", e)
                    // Don't clear the cache on error, keep existing data if available
                    val cachedResult = _mediaDetailsCache.value[cacheKey]
                    if (cachedResult != null) {
                        _currentMediaDetails.value = cachedResult.details
                    } else {
                        _currentMediaDetails.value = ApiResult.Error(e)
                    }
                }
            } finally {
                // Always remove the job when it's done
                mediaDetailsJobs.remove(cacheKey)
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        // Cancel all active jobs
        activeJobs.values.forEach { it.cancel() }
        mediaDetailsJobs.values.forEach { it.cancel() }
        pollingJob?.cancel()
        activeJobs.clear()
        mediaDetailsJobs.clear()

        // Clean up ScrollPositionManager when the ViewModel is cleared
        ScrollPositionManager.cleanup()
    }

    fun setNeedsRefresh(value: Boolean) {
        _needsRefresh.value = value
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

    fun setMediaToRefresh(mediaDetails: MediaDetails) {
        Log.d(
            "SeerrTV",
            "Setting media to refresh - ID: ${mediaDetails.id}, Status: ${mediaDetails.mediaInfo?.status}"
        )
        // Update the categories immediately with the new media details
        viewModelScope.launch {
            updateMediaInCategories(mediaDetails)
        }
        // Store the media details for any components that need it
        _mediaToRefresh.value = mediaDetails
    }

    fun updateMediaInCategories(mediaDetails: MediaDetails) {
        Log.d("SeerrTV", "Updating media ${mediaDetails.id} in categories")
        // Create a new map instance to force recomposition
        val newMap = mutableMapOf<MediaCategory, ApiResult<List<Media>>>()
        // Update the media item in each category if found
        MediaCategory.entries.forEach { category ->
            val categoryResult = _categoryData.value[category]
            if (categoryResult is ApiResult.Success) {
                val updatedList = categoryResult.data.map { media ->
                    if (media.id == mediaDetails.id) {
                        // Create a new MediaInfo instance with updated fields
                        val updatedMediaInfo = mediaDetails.mediaInfo?.let { info ->
                            MediaInfo(
                                createdAt = info.createdAt,
                                downloadStatus = info.downloadStatus,
                                downloadStatus4k = info.downloadStatus4k,
                                externalServiceId = info.externalServiceId,
                                externalServiceId4k = info.externalServiceId4k,
                                externalServiceSlug = info.externalServiceSlug,
                                externalServiceSlug4k = info.externalServiceSlug4k,
                                id = info.id,
                                imdbId = info.imdbId,
                                iOSPlexUrl = info.iOSPlexUrl,
                                issues = info.issues,
                                lastSeasonChange = info.lastSeasonChange,
                                mediaAddedAt = info.mediaAddedAt,
                                mediaType = info.mediaType,
                                plexUrl = info.plexUrl,
                                ratingKey = info.ratingKey,
                                ratingKey4k = info.ratingKey4k,
                                requests = info.requests,
                                seasons = info.seasons,
                                serviceId = info.serviceId,
                                serviceId4k = info.serviceId4k,
                                serviceUrl = info.serviceUrl,
                                status = info.status,
                                status4k = info.status4k,
                                tmdbId = info.tmdbId,
                                tvdbId = info.tvdbId,
                                updatedAt = info.updatedAt
                            )
                        }

                        // Update the media item with new details
                        media.copy(
                            mediaInfo = updatedMediaInfo,
                            status = updatedMediaInfo?.status,
                            status4k = updatedMediaInfo?.status4k,
                            plexUrl = updatedMediaInfo?.plexUrl,
                            iOSPlexUrl = updatedMediaInfo?.iOSPlexUrl
                        )
                    } else {
                        media
                    }
                }
                newMap[category] = ApiResult.Success(updatedList)
            } else if (categoryResult != null) {
                newMap[category] = categoryResult
            }
        }
        // Update the state with the new map instance
        _categoryData.value = newMap
    }

    private suspend fun fetchRecentlyAdded(forceRefresh: Boolean): ApiResult<List<Media>> {
        return try {
            when (val response = apiService.getRecentlyAdded(reset = forceRefresh)) {
                is ApiResult.Success -> {
                    val newMedia = response.data.results.mapNotNull { result ->
                        try {
                            val mediaInfo = MediaInfo(
                                createdAt = result.createdAt ?: "",
                                downloadStatus = emptyList(),
                                downloadStatus4k = emptyList(),
                                externalServiceId = null,
                                externalServiceId4k = null,
                                externalServiceSlug = null,
                                externalServiceSlug4k = null,
                                id = result.id,
                                imdbId = result.imdbId,
                                iOSPlexUrl = null,
                                issues = null,
                                lastSeasonChange = result.lastSeasonChange ?: "",
                                mediaAddedAt = null,
                                mediaType = result.mediaType,
                                plexUrl = null,
                                ratingKey = null,
                                ratingKey4k = null,
                                requests = null,
                                seasons = null,
                                serviceId = null,
                                serviceId4k = null,
                                serviceUrl = null,
                                status = result.status ?: 0,
                                status4k = result.status4k ?: 0,
                                tmdbId = result.tmdbId ?: 0,
                                tvdbId = result.tvdbId ?: 0,
                                updatedAt = result.updatedAt ?: ""
                            )
                            Media(
                                adult = null,
                                backdropPath = "",
                                createdAt = "",
                                externalServiceId = result.externalServiceId,
                                externalServiceId4k = result.externalServiceId4k,
                                externalServiceSlug = result.externalServiceSlug,
                                externalServiceSlug4k = result.externalServiceSlug4k,
                                genreIds = emptyList(),
                                id = result.tmdbId ?: 0,
                                imdbId = "",
                                iOSPlexUrl = result.iOSPlexUrl,
                                lastSeasonChange = "",
                                mediaAddedAt = "",
                                mediaInfo = mediaInfo,
                                mediaType = result.mediaType,
                                name = "",
                                originalLanguage = "",
                                originalTitle = "",
                                overview = "",
                                plexUrl = result.plexUrl,
                                popularity = 0.0,
                                posterPath = "",
                                ratingKey = result.ratingKey,
                                ratingKey4k = result.ratingKey4k,
                                releaseDate = "",
                                seasons = result.seasons,
                                serviceId = result.serviceId,
                                serviceId4k = result.serviceId4k,
                                serviceUrl = result.serviceUrl,
                                status = 0,
                                status4k = 0,
                                title = "",
                                tmdbId = 0,
                                tvdbId = 0,
                                updatedAt = "",
                                video = null,
                                voteAverage = 0.0,
                                voteCount = 0
                            )
                        } catch (e: Exception) {
                            Log.e("SeerrTV", "Error parsing MediaResult: ${e.message}")
                            null
                        }
                    }

                    val updatedMedia = newMedia.map { media ->
                        val details = when (media.mediaType) {
                            "movie" -> apiService.getMovieDetails(media.id.toString())
                            "tv" -> apiService.getTVDetails(media.id.toString())
                            else -> null
                        }
                        if (details is ApiResult.Success) {
                            media.copy(
                                title = details.data.title ?: details.data.name ?: "",
                                name = details.data.name ?: "",
                                posterPath = details.data.posterPath ?: "",
                                backdropPath = details.data.backdropPath ?: "",
                                overview = details.data.overview
                            )
                        } else {
                            media
                        }
                    }
                    ApiResult.Success(updatedMedia, response.paginationInfo)
                }

                is ApiResult.Error -> {
                    Log.e("SeerrTV", "Error fetching recently added: ${response.exception.message}")
                    ApiResult.Error(response.exception)
                }

                is ApiResult.Loading -> ApiResult.Loading()
            }
        } catch (e: Exception) {
            Log.e("SeerrTV", "Exception in fetchRecentlyAdded: ${e.message}")
            ApiResult.Error(e)
        }
    }

    private suspend fun fetchRecentRequests(forceRefresh: Boolean): ApiResult<List<Media>> {
        return try {
            // Get requests with pagination
            val response = apiService.getRequests(reset = forceRefresh)

            when (response) {
                is ApiResult.Success<RequestResponse> -> {
                    val mediaList = mutableListOf<Media>()

                    // Process each request and fetch media details
                    val deferredMedia = response.data.results.map { request ->
                        coroutineScope {
                            async {
                                val mediaType = request.media.mediaType
                                val mediaId = request.media.tmdbId.toString()
                                // Get detailed media information
                                when (val details = if (mediaType == "movie") {
                                    apiService.getMovieDetails(mediaId)
                                } else {
                                    apiService.getTVDetails(mediaId)
                                }) {
                                    is ApiResult.Success -> {
                                        // Convert to Media object with all required details
                                        Media(
                                            id = details.data.id,
                                            mediaType = request.media.mediaType,
                                            tmdbId = request.media.tmdbId,
                                            tvdbId = request.media.tvdbId,
                                            imdbId = request.media.imdbId,
                                            status = request.media.status,
                                            status4k = request.media.status4k,
                                            createdAt = request.createdAt,
                                            updatedAt = request.updatedAt,
                                            lastSeasonChange = request.media.lastSeasonChange,
                                            mediaAddedAt = request.media.mediaAddedAt,
                                            serviceId = request.media.serviceId,
                                            serviceId4k = request.media.serviceId4k,
                                            externalServiceId = request.media.externalServiceId,
                                            externalServiceId4k = request.media.externalServiceId4k,
                                            externalServiceSlug = request.media.externalServiceSlug,
                                            externalServiceSlug4k = request.media.externalServiceSlug4k,
                                            ratingKey = request.media.ratingKey,
                                            ratingKey4k = request.media.ratingKey4k,
                                            serviceUrl = request.media.serviceUrl,
                                            title = details.data.title ?: details.data.name ?: "",
                                            originalTitle = details.data.originalTitle ?: "",
                                            name = details.data.name ?: "",
                                            overview = details.data.overview,
                                            posterPath = details.data.posterPath ?: "",
                                            backdropPath = details.data.backdropPath ?: "",
                                            mediaInfo = request.media.copy(
                                                seasons = request.seasons.map { requestSeason ->
                                                    Season(
                                                        id = requestSeason.id,
                                                        seasonNumber = requestSeason.seasonNumber,
                                                        status = requestSeason.status,
                                                        airDate = null,
                                                        episodeCount = null,
                                                        name = null,
                                                        overview = null,
                                                        posterPath = null,
                                                        status4k = null,
                                                        createdAt = null,
                                                        updatedAt = null
                                                    )
                                                },
                                                requests = null  // Keep requests as null since we'll use the request field
                                            ),
                                            request = request
                                        )
                                    }

                                    else -> {
                                        Log.e(
                                            "SeerrTV",
                                            "Failed to get details for media $mediaId: $details"
                                        )
                                        null
                                    }
                                }
                            }
                        }
                    }

                    // Wait for all media details to be fetched and filter out nulls
                    mediaList.addAll(deferredMedia.mapNotNull { it.await() })

                    ApiResult.Success(mediaList, response.paginationInfo)
                }

                is ApiResult.Error -> {
                    Log.e(
                        "SeerrTV",
                        "Error fetching recent requests: ${response.exception.message}"
                    )
                    ApiResult.Error(response.exception)
                }

                is ApiResult.Loading -> ApiResult.Loading()
            }
        } catch (e: Exception) {
            Log.e("SeerrTV", "Error in fetchRecentRequests: ${e.message}")
            ApiResult.Error(e)
        }
    }

    suspend fun deleteRequest(requestId: Int) {
        Log.d("SeerrTV", "Deleting request $requestId")
        try {
            when (val result = apiService.deleteRequest(requestId)) {
                is ApiResult.Success -> {
                    // Refresh the categories after deleting the request
                    refreshAllCategories()
                }

                is ApiResult.Error -> {
                    Log.e(
                        "SeerrTV",
                        "Failed to delete request $requestId: ${result.exception.message}"
                    )
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                }

                is ApiResult.Loading -> {}
            }
        } catch (e: Exception) {
            Log.e("SeerrTV", "Error deleting request: ${e.message}")
        }
    }

    fun getCurrentUserId(): Int? {
        return apiService.getCurrentUserInfo()?.id
    }

    fun getCurrentUserPermissions(): Int? {
        val permissions = apiService.getCurrentUserInfo()?.permissions
        return permissions
    }

    fun getDataForMediaType(mediaType: String) {
        viewModelScope.launch {
            when (mediaType.uppercase()) {
                "MOVIE" -> {
                    _radarrData.value = apiService.getCachedRadarrData()
                }

                "TV" -> {
                    _sonarrData.value = apiService.getCachedSonarrData()
                }

                else -> Log.e("SeerrTV", "Invalid media type: $mediaType")
            }
        }
    }

    fun setRefreshRequired(made: Boolean) {
        _requestMade.value = made
    }

    fun refreshRequired(): StateFlow<Boolean> {
        return _requestMade.asStateFlow()
    }

    fun clearRefreshRequired() {
        _requestMade.value = false
    }

    fun getServerType(): SeerrApiService.ServerType {
        return apiService.getServerType()
    }

    fun getRequestForMedia(mediaId: Int, is4k: Boolean? = null): Request? {
        // First check the media info's requests
        _currentMediaDetails.value.let { result ->
            if (result is ApiResult.Success) {
                val requests = result.data.mediaInfo?.requests
                if (requests != null) {
                    // If is4k is specified, filter by that; otherwise return first request
                    val filteredRequest = if (is4k != null) {
                        requests.find { it.is4k == is4k }
                    } else {
                        requests.firstOrNull()
                    }
                    filteredRequest?.let { return it }
                }
            }
        }

        // Then check recent requests category
        _categoryData.value[MediaCategory.RECENT_REQUESTS]?.let { result ->
            if (result is ApiResult.Success) {
                val mediaItem = result.data.find { it.mediaInfo?.tmdbId == mediaId }
                val request = mediaItem?.request
                if (request != null) {
                    // If is4k is specified, check if it matches; otherwise return the request
                    return if (is4k == null || request.is4k == is4k) {
                        request
                    } else {
                        null
                    }
                }
            }
        }

        return null
    }

    /**
     * Get both regular and 4K requests for a specific media item
     * @param mediaId The TMDB ID of the media
     * @return Pair of (regularRequest, fourKRequest) - either can be null
     */
    fun getRequestsForMedia(mediaId: Int): Pair<Request?, Request?> {
        // First check the media info's requests
        _currentMediaDetails.value.let { result ->
            if (result is ApiResult.Success) {
                val requests = result.data.mediaInfo?.requests
                if (requests != null) {
                    val regularRequest = requests.find { !it.is4k }
                    val fourKRequest = requests.find { it.is4k }
                    return Pair(regularRequest, fourKRequest)
                }
            }
        }

        // Then check recent requests category
        _categoryData.value[MediaCategory.RECENT_REQUESTS]?.let { result ->
            if (result is ApiResult.Success) {
                val mediaItem = result.data.find { it.mediaInfo?.tmdbId == mediaId }
                val request = mediaItem?.request
                if (request != null) {
                    return if (request.is4k) {
                        Pair(null, request)
                    } else {
                        Pair(request, null)
                    }
                }
            }
        }

        return Pair(null, null)
    }

    fun deleteMediaFile(mediaId: String) {
        setLastAction {
            viewModelScope.launch {
                try {
                    Log.d("SeerrTV", "🗑️ Deleting media file: $mediaId")

                    // Store the current items in the list before deletion
                    val beforeSize = categoryMediaLists[MediaCategory.RECENT_REQUESTS]?.size ?: 0

                    // Delete the media file
                    apiService.deleteMediaFile(mediaId)

                    // We'll need to refresh the category, but we can't do it without a context
                    // So we'll set a flag that MainScreen can check
                    setRefreshRequired(true)

                    // 1. Clear the existing data
                    categoryMediaLists[MediaCategory.RECENT_REQUESTS]?.clear()

                    // 2. Reset the API pagination
                    apiService.getRequests(reset = true)

                    // 3. Force a carousel reset
                    forceCarouselReset(MediaCategory.RECENT_REQUESTS, animate = true)

                    // 4. Check the current size and log for debugging
                    val afterSize = categoryMediaLists[MediaCategory.RECENT_REQUESTS]?.size ?: 0
                    Log.d(
                        "SeerrTV",
                        "📊 RECENT_REQUESTS size change after media file deletion: $beforeSize -> $afterSize"
                    )

                    // 5. Emit a carousel update event to force recomposition
                    _carouselUpdates.value = CarouselUpdateEvent(
                        category = MediaCategory.RECENT_REQUESTS,
                        itemsBefore = beforeSize,
                        itemsAfter = afterSize
                    )

                } catch (e: Exception) {
                    Log.e("SeerrTV", "Error deleting media file: ${e.message}")
                    if (handleApiError(e)) {
                        authErrorState.showError()
                    }
                }
            }
        }
    }

    fun deleteMedia(mediaId: String) {
        setLastAction {
            viewModelScope.launch {
                try {
                    Log.d("SeerrTV", "🗑️ Deleting media: $mediaId")

                    // Store the current items in the list before deletion
                    val beforeSize = categoryMediaLists[MediaCategory.RECENT_REQUESTS]?.size ?: 0

                    // Delete the media
                    apiService.deleteMedia(mediaId)

                    // We'll need to refresh the category, but we can't do it without a context
                    // So we'll set a flag that MainScreen can check
                    setRefreshRequired(true)

                    // 1. Clear the existing data
                    categoryMediaLists[MediaCategory.RECENT_REQUESTS]?.clear()

                    // 2. Reset the API pagination
                    apiService.getRequests(reset = true)

                    // 3. Force a carousel reset
                    forceCarouselReset(MediaCategory.RECENT_REQUESTS, animate = true)

                    // 4. Check the current size and log for debugging
                    val afterSize = categoryMediaLists[MediaCategory.RECENT_REQUESTS]?.size ?: 0
                    Log.d(
                        "SeerrTV",
                        "📊 RECENT_REQUESTS size change after media deletion: $beforeSize -> $afterSize"
                    )

                    // 5. Emit a carousel update event to force recomposition
                    _carouselUpdates.value = CarouselUpdateEvent(
                        category = MediaCategory.RECENT_REQUESTS,
                        itemsBefore = beforeSize,
                        itemsAfter = afterSize
                    )

                } catch (e: Exception) {
                    Log.e("SeerrTV", "Error deleting media: ${e.message}")
                    if (handleApiError(e)) {
                        authErrorState.showError()
                    }
                }
            }
        }
    }

    suspend fun updateRequestStatus(requestId: Int, status: Int) {
        try {
            when (val result = apiService.updateRequestStatus(requestId, status)) {
                is ApiResult.Success -> {
                    // Refresh the categories after updating the request
                    refreshAllCategories()
                }

                is ApiResult.Error -> {
                    Log.e(
                        "SeerrTV",
                        "Failed to update request $requestId status: ${result.exception.message}"
                    )
                    if (handleApiError(result.exception, result.statusCode)) {
                        authErrorState.showError()
                    }
                }

                is ApiResult.Loading -> {}
            }
        } catch (e: Exception) {
            Log.e("SeerrTV", "Error updating request status: ${e.message}")
        }
    }

    // Update the loading flag setting to maintain a minimum loading time
    fun setLoadMoreFlag(category: MediaCategory, isLoading: Boolean) {
        // Don't set loading flag during initial app load
        if (_isInitialLoad.value && isLoading) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    "SeerrTV",
                    "🚫 Ignoring load more request during initial load for ${category.name}"
                )
            }
            return
        }

        // Update the loading flag
        loadingMoreCategories[category] = isLoading

        // Update the category state
        if (isLoading) {
            // Start loading - capture current time
            val currentState = _categoryStates.value[category] ?: CategoryState(category)
            _categoryStates.value = _categoryStates.value.toMutableMap().apply {
                put(
                    category, currentState.copy(
                        isLoading = true,
                        loadingStartTime = System.currentTimeMillis()
                    )
                )
            }

            // Show the placeholder
            _showPlaceholders.value = _showPlaceholders.value.toMutableMap().apply {
                put(category, true)
            }
        } else {
            // Finished loading - ensure we've shown the placeholder for at least the minimum time
            val currentState = _categoryStates.value[category] ?: CategoryState(category)
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - currentState.loadingStartTime

            // Update the state to not loading
            _categoryStates.value = _categoryStates.value.toMutableMap().apply {
                put(category, currentState.copy(isLoading = false))
            }

            // If we haven't shown the placeholder for long enough, delay hiding it
            if (elapsedTime < currentState.minimumLoadingTimeMs) {
                viewModelScope.launch {
                    delay(currentState.minimumLoadingTimeMs - elapsedTime)
                    _showPlaceholders.value = _showPlaceholders.value.toMutableMap().apply {
                        put(category, false)
                    }
                }
            } else {
                // We've shown it long enough, hide it immediately
                _showPlaceholders.value = _showPlaceholders.value.toMutableMap().apply {
                    put(category, false)
                }
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d("SeerrTV", "🚩 Setting load more flag for ${category.name} to $isLoading")
        }
    }

    // Enhanced version that includes more data state information
    fun isLoadingMoreCategory(category: MediaCategory): Boolean {
        return loadingMoreCategories[category] == true
    }

    // Update category state with counts and pagination info
    private fun updateCategoryState(
        category: MediaCategory,
        itemCount: Int,
        hasMorePages: Boolean
    ) {
        val currentState = _categoryStates.value[category] ?: CategoryState(category)
        _categoryStates.value = _categoryStates.value.toMutableMap().apply {
            put(
                category, currentState.copy(
                    itemCount = itemCount,
                    hasMorePages = hasMorePages
                )
            )
        }
    }

    // Method to update the currently selected media (for category cards and regular media)
    fun updateCurrentMedia(media: Media?) {
        // Only update if it's actually different to avoid unnecessary recompositions
        if (media != _currentSelectedMedia.value) {
            _currentSelectedMedia.value = media
            if (BuildConfig.DEBUG) {
                val mediaType = when {
                    media?.genreName != null -> "Genre"
                    media?.studioName != null -> "Studio"
                    media?.networkName != null -> "Network"
                    else -> "Media"
                }
                val mediaName =
                    media?.genreName ?: media?.studioName ?: media?.networkName ?: media?.title
                    ?: media?.name ?: "null"
                Log.d("SeerrViewModel", "Updated current $mediaType: $mediaName")
            }
        }
    }

    // Convenient method to convert a CategoryCard to a Media object for backdrop purposes
    fun categoryCardToMedia(card: CategoryCard): Media {
        return Media(
            id = card.id,
            mediaType = when (card.discoveryType) {
                DiscoveryType.MOVIE_GENRE -> "movie"
                DiscoveryType.SERIES_GENRE -> "tv"
                DiscoveryType.STUDIO -> "movie"
                DiscoveryType.NETWORK -> "tv"
                else -> "unknown"
            },
            // Set the appropriate category name field
            genreName = if (card.discoveryType in listOf(
                    DiscoveryType.MOVIE_GENRE,
                    DiscoveryType.SERIES_GENRE
                )
            ) card.name else null,
            studioName = if (card.discoveryType == DiscoveryType.STUDIO) card.name else null,
            networkName = if (card.discoveryType == DiscoveryType.NETWORK) card.name else null,
            // For genres, use backdrop path. For studios/networks, use logo
            backdropPath = if (card.discoveryType in listOf(
                    DiscoveryType.MOVIE_GENRE,
                    DiscoveryType.SERIES_GENRE
                )
            ) card.imagePath else null,
            logoPath = if (card.discoveryType in listOf(
                    DiscoveryType.STUDIO,
                    DiscoveryType.NETWORK
                )
            ) card.imagePath else null
        )
    }

    // Add a function to clear carousel updates
    fun clearCarouselUpdate() {
        _carouselUpdates.value = null
    }

    /**
     * Forces a reset of carousel display state for a category
     * This is used to ensure the carousel is properly refreshed after data changes
     */
    fun forceCarouselReset(category: MediaCategory?, animate: Boolean = false) {
        if (category == null) {
            // Handle null case - just clear any current reset event
            _carouselResetEvents.value = null
            return
        }

        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d(
                    "SeerrViewModel",
                    "🔄 Forcing carousel reset for ${category.name}, animate=$animate"
                )
            }

            // Reset the scroll position manager state for this category
            ScrollPositionManager.resetPositionFor("${category.name}_index")
            ScrollPositionManager.resetPositionFor("${category.name}_scroll")

            // Notify of size change for the carousel to trigger a recomposition
            val currentSize = categoryMediaLists[category]?.size ?: 0
            Log.d(
                "SeerrTV",
                "📊 ${category.name} size change after media deletion: $currentSize -> 0"
            )

            // Force data state update by emitting a carousel update event
            emitCarouselUpdateEvent(category, itemsBefore = currentSize, itemsAfter = 0)

            // Set the carousel reset event
            _carouselResetEvents.value = Pair(category, animate)

            // Small delay to allow UI to catch up
            delay(100)

            // Reset to null to allow future events to be triggered
            _carouselResetEvents.value = null

            // Now trigger a final update with the correct size
            delay(50)
            emitCarouselUpdateEvent(category, itemsBefore = 0, itemsAfter = currentSize)
        }
    }

    /**
     * Emits a carousel update event to notify UI of data changes
     */
    private fun emitCarouselUpdateEvent(
        category: MediaCategory,
        itemsBefore: Int = 0,
        itemsAfter: Int = 0
    ) {
        _carouselUpdates.value = CarouselUpdateEvent(
            category = category,
            itemsBefore = itemsBefore,
            itemsAfter = itemsAfter
        )
    }

    /**
     * Clears all data for a specific category, forcing a complete refresh on next load
     */
    fun clearCategoryData(category: MediaCategory) {
        if (BuildConfig.DEBUG) {
            val existingSize = categoryMediaLists[category]?.size ?: 0
            Log.d(
                "SeerrViewModel",
                "🗑️ Explicitly clearing all data for $category (size: $existingSize)"
            )
        }

        // Clear the data in the backing storage
        categoryMediaLists[category]?.clear()

        // Remove the cache timestamp to force refresh
        cacheTimestamps.remove(category)

        // Reset the API pagination for this category
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (category == MediaCategory.RECENT_REQUESTS) {
                    Log.d("SeerrViewModel", "♻️ Resetting API pagination for $category")
                    apiService.getRequests(reset = true)
                }
            } catch (e: Exception) {
                Log.e("SeerrViewModel", "Error resetting pagination for $category: ${e.message}")
            }
        }

        // Update the state flow to show loading
        _categoryData.value = _categoryData.value.toMutableMap().apply {
            put(category, ApiResult.Loading())
        }

        // Emit explicit carousel update event
        _carouselUpdates.value = CarouselUpdateEvent(
            category = category,
            itemsBefore = categoryMediaLists[category]?.size ?: 0,
            itemsAfter = 0
        )

        // Force a carousel reset
        forceCarouselReset(category, animate = true)

        Log.d("SeerrViewModel", "🔄 Category $category fully reset")
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    private fun handleApiError(exception: Exception, statusCode: Int? = null): Boolean {
        val isAuthError = when {
            statusCode == 401 -> true
            statusCode == 403 -> true
            exception.message?.contains("Unauthorized", ignoreCase = true) == true -> true
            exception.message?.contains("Forbidden", ignoreCase = true) == true -> true
            else -> false
        }

        if (isAuthError) {
            logError("Authentication error occurred: ${exception.message}")
            authErrorState.showError()
        } else {
            logError("API error occurred: ${exception.message}", exception)
        }

        return isAuthError
    }

    // Similar media methods
    suspend fun getSimilarMedia(mediaId: Int, mediaType: String, page: Int = 1) {
        try {
            // Check if we already have this page loaded
            if (similarMediaPages.containsKey(page)) return

            // Check if we've reached the limit
            if (page > maxSimilarMediaPages) return

            val result = when (mediaType.lowercase()) {
                "movie" -> apiService.getSimilarMovies(mediaId, page)
                "tv" -> apiService.getSimilarTVShows(mediaId, page)
                else -> throw IllegalArgumentException("Unsupported media type: $mediaType")
            }

            when (result) {
                is ApiResult.Success -> {
                    similarMediaPages[page] = result.data.results
                    // Combine all loaded pages
                    val allItems = similarMediaPages.values.flatten()
                    _similarMediaState.value = ApiResult.Success(allItems)
                }

                is ApiResult.Error -> {
                    _similarMediaState.value = ApiResult.Error(result.exception)
                }

                is ApiResult.Loading -> {
                    _similarMediaState.value = ApiResult.Loading()
                }
            }
        } catch (e: Exception) {
            _similarMediaState.value = ApiResult.Error(e)
        }
    }

    // Load next page when user approaches end of carousel
    suspend fun loadNextSimilarMediaPage(mediaId: Int, mediaType: String, currentIndex: Int) {
        val currentPage = (currentIndex / 20) + 1
        val nextPage = currentPage + 1

        // Load next page if we haven't reached the limit and are near the end of current page
        if (nextPage <= maxSimilarMediaPages && currentIndex % 20 >= 15) {
            getSimilarMedia(mediaId, mediaType, nextPage)
        }
    }

    // Clear similar media pages when navigating to new media
    fun clearSimilarMediaPages() {
        similarMediaPages.clear()
        _similarMediaState.value = null
    }
    
    // ============================================================================
    // POLLING FUNCTIONALITY FOR AUTOMATIC MEDIA DETAILS UPDATES
    // ============================================================================
    
    /**
     * Start tracking media details with automatic updates
     */
    fun startPollingMediaDetails(tmdbId: String, mediaType: String, initialDetails: MediaDetails? = null) {
        if (_isPollingActive.value && tmdbId == pollingTmdbId && mediaType == pollingMediaType) {
            Log.d(TAG, "⚠️ Already polling media $tmdbId, skipping duplicate start")
            return
        }
        
        // Only stop existing polling if it's for a different media
        if (_isPollingActive.value && (tmdbId != pollingTmdbId || mediaType != pollingMediaType)) {
            Log.d(TAG, "🔄 Switching polling from $pollingTmdbId ($pollingMediaType) to $tmdbId ($mediaType)")
            stopPollingMediaDetails()
        }
        
        // Set current polling parameters
        pollingTmdbId = tmdbId
        pollingMediaType = mediaType
        _isPollingActive.value = true
        pollingCycleCount = 0
        pollingFailureCount = 0
        lastPollingUpdateTimeMs = 0L
        isCollectingPollingFlow = false
        
        // Set initial details if provided
        if (initialDetails != null) {
            updateMediaDetailsCache(tmdbId, mediaType, initialDetails)
        }
        
        _pollingLoadingState.value = LoadingState.LOADING
        
        // Start polling job
        pollingJob = viewModelScope.launch {
            try {
                Log.d(TAG, "🚀 Starting polling for media $tmdbId ($mediaType)")
                
                // Initial load
                val initialSuccess = fetchMediaDetailsForPolling(isInitialLoad = true)
                
                // Wait for data to settle
                delay(1000)
                
                // Continue polling if successful
                if (initialSuccess && _isPollingActive.value) {
                    startPollingLoop()
                } else {
                    Log.d(TAG, "⚠️ Skipping continuous polling: initialSuccess=$initialSuccess, isActive=${_isPollingActive.value}")
                }
            } catch (_: CancellationException) {
                Log.d(TAG, "🚫 Polling main loop cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Fatal error in polling main loop: ${e.message}", e)
            }
        }
    }
    
    /**
     * Stop polling and cancel any pending updates
     */
    fun stopPollingMediaDetails() {
        if (!_isPollingActive.value) {
            return
        }
        
        Log.d(TAG, "🛑 Stopping polling for media $pollingTmdbId (ratings optimization: ${noRatingsAvailable.size} items tracked as no-ratings)")
        _isPollingActive.value = false
        
        // Cancel polling job
        pollingJob?.cancel()
        pollingJob = null
        
        // Reset state
        _pollingLoadingState.value = LoadingState.IDLE
        pollingTmdbId = null
        pollingMediaType = null
    }
    
    /**
     * Start the polling loop
     */
    private suspend fun startPollingLoop() {
        try {
            Log.d(TAG, "🔄 Starting continuous polling loop")
            
            while (_isPollingActive.value) {
                // Determine the appropriate update interval
                val updateInterval = determinePollingInterval()
                
                // Log waiting time
                Log.d(TAG, "⏱️ Waiting ${updateInterval}ms until next polling update (next will be cycle #${pollingCycleCount + 1})")
                
                // Wait before next update
                delay(updateInterval)
                
                // Only update if still active
                if (_isPollingActive.value) {
                    Log.d(TAG, "🔔 Time's up! Running polling cycle #${pollingCycleCount + 1}")
                    fetchMediaDetailsForPolling(isInitialLoad = false)
                } else {
                    Log.d(TAG, "⚠️ Polling deactivated during wait, stopping loop")
                    break
                }
            }
            
            Log.d(TAG, "🏁 Polling loop finished after $pollingCycleCount cycles")
        } catch (_: CancellationException) {
            Log.d(TAG, "🚫 Polling loop cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in polling loop: ${e.message}", e)
        }
    }
    
    /**
     * Determine the appropriate polling interval based on current state
     */
    private fun determinePollingInterval(): Long {
        val currentDetails = (_currentMediaDetails.value as? ApiResult.Success<MediaDetails>)?.data
        
        // Check if media has download status
        val hasDownloadStatus = currentDetails?.mediaInfo?.downloadStatus != null && 
                currentDetails.mediaInfo.downloadStatus.isNotEmpty()
        
        val interval = if (hasDownloadStatus) {
            // Faster updates for content being downloaded
            Log.d(TAG, "⏱️ Using fast polling interval (${FAST_UPDATE_INTERVAL_MS}ms) for downloading content")
            FAST_UPDATE_INTERVAL_MS
        } else {
            // Slower updates for non-downloading content
            Log.d(TAG, "⏱️ Using slow polling interval (${SLOW_UPDATE_INTERVAL_MS}ms) for non-downloading content")
            SLOW_UPDATE_INTERVAL_MS
        }
        
        // Always return a positive interval to prevent immediate re-fetching
        return maxOf(MIN_UPDATE_INTERVAL_MS, interval)
    }
    
    /**
     * Fetch media details for polling
     * @return true if fetch was successful, false otherwise
     */
    private suspend fun fetchMediaDetailsForPolling(isInitialLoad: Boolean): Boolean {
        val tmdbId = pollingTmdbId ?: return false
        val mediaType = pollingMediaType ?: return false
        
        // Skip if too soon after last update and not initial load
        val now = System.currentTimeMillis()
        if (!isInitialLoad && now - lastPollingUpdateTimeMs < MIN_UPDATE_INTERVAL_MS && pollingCycleCount > 0) {
            Log.d(TAG, "⏭️ Skipping polling update: too soon after last update")
            return true // Considering this a "success" for continued polling
        }
        
        // Prevent concurrent collection of the same flow
        if (isCollectingPollingFlow) {
            Log.d(TAG, "⏭️ Skipping polling update: already collecting flow")
            return true // Considering this a "success" for continued polling
        }
        
        // Increment cycle count and update timestamp
        pollingCycleCount++
        lastPollingUpdateTimeMs = now
        
        Log.d(TAG, "📝 Starting polling cycle #$pollingCycleCount for media $tmdbId (ratings optimization: ${noRatingsAvailable.size} items tracked as no-ratings)")
        
        var success = false
        isCollectingPollingFlow = true
        val startTime = System.currentTimeMillis()
        
        try {
            _pollingLoadingState.value = if (isInitialLoad) LoadingState.LOADING else LoadingState.REFRESHING
            
            // Use withTimeout to ensure the API call doesn't hang indefinitely
            withTimeout(15000) {
                // Use the shared fetchMediaDetails method to avoid duplicate API calls
                val cacheKey = "$tmdbId-$mediaType"
                val currentTime = System.currentTimeMillis()
                
                // Check for an existing job and enforce cooldown atomically
                val decision = synchronized(mediaDetailsJobs) {
                    val active = mediaDetailsJobs[cacheKey]?.isActive == true
                    if (active) {
                        // Another fetch is in progress; we'll wait below
                        Triple(true, false, mediaDetailsJobs[cacheKey])
                    } else {
                        val cooldownUntil = inCooldownUntilMs[cacheKey] ?: 0L
                        if (currentTime < cooldownUntil) {
                            // Respect cooldown: skip fetching
                            Triple(false, true, null)
                        } else {
                            // Begin cooldown window so immediate followers bail out
                            inCooldownUntilMs[cacheKey] = currentTime + 1000L
                            Triple(false, false, null)
                        }
                    }
                }
                val existingJob = decision.third
                if (decision.second) {
                    Log.d(TAG, "⏭️ Skipping polling fetch for $cacheKey due to cooldown")
                    // Treat as success to keep polling schedule without spamming network
                    success = true
                    return@withTimeout
                }
                val result = if (existingJob?.isActive == true) {
                    Log.d(TAG, "⏳ Waiting for existing fetchMediaDetails job to complete for $cacheKey")
                    // Wait for the existing job to complete instead of skipping
                    existingJob.join()
                    // Check if we now have data
                    val currentResult = _currentMediaDetails.value
                    Log.d(TAG, if (currentResult is ApiResult.Success) "✅ Existing job completed successfully" else "⚠️ Existing job completed but no data")
                    currentResult
                } else {
                    // Use the shared fetchMediaDetails logic
                    when (mediaType.lowercase()) {
                        "movie" -> {
                            // Don't fetch ratings during polling - only during initial load
                            // Ratings are static data that doesn't change frequently
                            val md = fetchMovieDetails(tmdbId, TimeUnit.MILLISECONDS.toMinutes(DEFAULT_CACHE_TIME_MS))
                            if (md is ApiResult.Success) {
                                // Check if we already have cached ratings from initial load
                                val cachedRatings = getRatingsCached(tmdbId, "movie", 0L) // 0L = check cache only, no fetching
                                when (cachedRatings) {
                                    is ApiResult.Success -> ApiResult.Success(md.data.copy(ratings = cachedRatings.data))
                                    else -> md // Return without ratings during polling - they weren't available initially
                                }
                            } else md
                        }
                        "tv" -> {
                            // Don't fetch ratings during polling - only during initial load
                            // Ratings are static data that doesn't change frequently
                            val md = fetchTVDetails(tmdbId)
                            if (md is ApiResult.Success) {
                                // Check if we already have cached ratings from initial load
                                val cachedRatings = getRatingsCached(tmdbId, "tv", 0L) // 0L = check cache only, no fetching
                                when (cachedRatings) {
                                    is ApiResult.Success -> ApiResult.Success(md.data.copy(ratings = cachedRatings.data))
                                    else -> md // Return without ratings during polling - they weren't available initially
                                }
                            } else md
                        }
                        else -> ApiResult.Error(Exception("Invalid media type: $mediaType"))
                    }
                }
                
                // Process result
                when (result) {
                    is ApiResult.Success -> {
                        // Check if the media has download status
                        val hasDownloadStatus = result.data.mediaInfo?.downloadStatus?.isNotEmpty() == true
                        val duration = System.currentTimeMillis() - startTime
                        Log.d(TAG, "📝 Polling cycle #$pollingCycleCount: Updated data (hasDownloadStatus=$hasDownloadStatus, ratings cached only) in ${duration}ms")
                        
                        // Update the cache with the new data
                        updateMediaDetailsCache(tmdbId, mediaType, result.data)
                        
                        pollingFailureCount = 0  // Reset failure count on success
                        _pollingLoadingState.value = LoadingState.IDLE
                        success = true // Mark as successful
                    }
                    is ApiResult.Error -> {
                        pollingFailureCount++
                        Log.e(TAG, "❌ Polling cycle #$pollingCycleCount: Exception during update (failure #$pollingFailureCount): ${result.exception.message}", result.exception)
                        _pollingLoadingState.value = LoadingState.ERROR
                        
                        // Stop polling after too many failures
                        if (pollingFailureCount >= MAX_FAILURE_COUNT) {
                            Log.e(TAG, "🛑 Stopping polling after $pollingFailureCount consecutive failures")
                            _isPollingActive.value = false
                        }
                    }
                    is ApiResult.Loading -> {
                        // If we're still loading on initial load, consider it a success to continue polling
                        if (isInitialLoad) {
                            Log.d(TAG, "⏳ Initial load still in progress, will continue polling")
                            success = true
                        } else {
                            Log.w(TAG, "⚠️ Polling cycle #$pollingCycleCount: Unexpected Loading result")
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            pollingFailureCount++
            Log.e(TAG, "⏰ Polling cycle #$pollingCycleCount: Timeout after 15 seconds (failure #$pollingFailureCount)")
            _pollingLoadingState.value = LoadingState.ERROR
            
            // Stop polling after too many failures
            if (pollingFailureCount >= MAX_FAILURE_COUNT) {
                Log.e(TAG, "🛑 Stopping polling after $pollingFailureCount consecutive failures")
                _isPollingActive.value = false
            }
        } catch (e: Exception) {
            pollingFailureCount++
            Log.e(TAG, "❌ Polling cycle #$pollingCycleCount: Exception during update (failure #$pollingFailureCount): ${e.message}", e)
            _pollingLoadingState.value = LoadingState.ERROR
            
            // Stop polling after too many failures
            if (pollingFailureCount >= MAX_FAILURE_COUNT) {
                Log.e(TAG, "🛑 Stopping polling after $pollingFailureCount consecutive failures")
                _isPollingActive.value = false
            }
        } finally {
            isCollectingPollingFlow = false
        }
        
        return success
    }
}

/**
 * Loading state enum for polling operations
 */
enum class LoadingState {
    IDLE,
    LOADING,
    REFRESHING,
    ERROR
}