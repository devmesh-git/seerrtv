package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable

/**
 * Container object for browse-related data models
 */
object BrowseModels {
    
    /**
     * Filter state for media browsing.
     * All filters are optional; null/empty values mean "no filter applied"
     */
    @Serializable
    data class MediaFilters(
        val mediaType: MediaType,
        val releaseFrom: String? = null,  // YYYY-MM-DD format
        val releaseTo: String? = null,    // YYYY-MM-DD format
        val genres: List<Int> = emptyList(),
        val keywords: List<Int> = emptyList(),
        val originalLanguage: String? = null,  // ISO 639-1 code (e.g., "en")
        val contentRatings: List<String> = emptyList(),  // e.g., ["PG", "PG-13"]
        val runtimeMin: Int? = null,  // minutes
        val runtimeMax: Int? = null,  // minutes
        val userScoreMin: Float? = null,  // 1.0-10.0
        val userScoreMax: Float? = null,  // 1.0-10.0
        val voteCountMin: Int? = null,
        val voteCountMax: Int? = null,
        val studio: Int? = null,  // Movies only - single selection
        val networks: List<Int> = emptyList(), // TV only
        val region: String? = null,  // ISO 3166-1 code (e.g., "US") - for content ratings
        val watchProviders: List<Int> = emptyList(),  // Streaming provider IDs
        val watchRegion: String? = null  // ISO 3166-1 code (e.g., "US") - for watch providers
    ) {
        companion object {
            /**
             * Create default filters for a given media type
             */
            fun default(mediaType: MediaType): MediaFilters {
                return MediaFilters(mediaType = mediaType)
            }
        }
        
        /**
         * Check if this filter is "empty" (all default values)
         */
        fun isEmpty(): Boolean {
            return releaseFrom == null &&
                    releaseTo == null &&
                    genres.isEmpty() &&
                    keywords.isEmpty() &&
                    originalLanguage == null &&
                    contentRatings.isEmpty() &&
                    runtimeMin == null &&
                    runtimeMax == null &&
                    userScoreMin == null &&
                    userScoreMax == null &&
                    voteCountMin == null &&
                    voteCountMax == null &&
                    studio == null &&
                    networks.isEmpty() &&
                    region == null &&
                    watchProviders.isEmpty() &&
                    watchRegion == null
        }
        
        /**
         * Count the number of active (non-default) filters
         */
        fun activeCount(): Int {
            var count = 0
            if (releaseFrom != null || releaseTo != null) count++
            if (genres.isNotEmpty()) count++
            if (keywords.isNotEmpty()) count++
            if (originalLanguage != null) count++
            if (contentRatings.isNotEmpty()) count++
            if (runtimeMin != null || runtimeMax != null) count++
            if (userScoreMin != null || userScoreMax != null) count++
            if (voteCountMin != null || voteCountMax != null) count++
            if (studio != null) count++
            if (networks.isNotEmpty()) count++
            if (region != null) count++
            if (watchProviders.isNotEmpty()) count++
            return count
        }
    }

    /**
     * Sort options for media browsing
     */
    @Serializable
    sealed class SortOption {
        abstract val displayName: String
        abstract val apiParameter: String
        
        // Common sort options (both Movies and Series)
        @Serializable
        object PopularityDesc : SortOption() {
            override val displayName = "Popularity ↓"
            override val apiParameter = "popularity.desc"
        }
        
        @Serializable
        object PopularityAsc : SortOption() {
            override val displayName = "Popularity ↑"
            override val apiParameter = "popularity.asc"
        }
        
        @Serializable
        object TitleAsc : SortOption() {
            override val displayName = "Title A→Z"
            override val apiParameter = "original_title.asc"
        }
        
        @Serializable
        object TitleDesc : SortOption() {
            override val displayName = "Title Z→A"
            override val apiParameter = "original_title.desc"
        }
        
        @Serializable
        object TMDBRatingDesc : SortOption() {
            override val displayName = "TMDB Rating ↓"
            override val apiParameter = "vote_average.desc"
        }
        
        @Serializable
        object TMDBRatingAsc : SortOption() {
            override val displayName = "TMDB Rating ↑"
            override val apiParameter = "vote_average.asc"
        }
        
        // Movie-specific sort options
        @Serializable
        object ReleaseDateDesc : SortOption() {
            override val displayName = "Release Date ↓"
            override val apiParameter = "release_date.desc"
        }
        
        @Serializable
        object ReleaseDateAsc : SortOption() {
            override val displayName = "Release Date ↑"
            override val apiParameter = "release_date.asc"
        }
        
        // Series-specific sort options
        @Serializable
        object FirstAirDateDesc : SortOption() {
            override val displayName = "First Air Date ↓"
            override val apiParameter = "first_air_date.desc"
        }
        
        @Serializable
        object FirstAirDateAsc : SortOption() {
            override val displayName = "First Air Date ↑"
            override val apiParameter = "first_air_date.asc"
        }
        
        companion object {
            /**
             * Default sort option (matches Jellyseerr default)
             */
            fun default(): SortOption = PopularityDesc
            
            /**
             * Get sort options for Movies
             */
            fun forMovies(): List<SortOption> = listOf(
                PopularityDesc,
                PopularityAsc,
                ReleaseDateDesc,
                ReleaseDateAsc,
                TMDBRatingDesc,
                TMDBRatingAsc,
                TitleAsc,
                TitleDesc
            )
            
            /**
             * Get sort options for Series
             */
            fun forSeries(): List<SortOption> = listOf(
                PopularityDesc,
                PopularityAsc,
                FirstAirDateDesc,
                FirstAirDateAsc,
                TMDBRatingDesc,
                TMDBRatingAsc,
                TitleAsc,
                TitleDesc
            )
            
            /**
             * Get sort options based on media type
             */
            fun forMediaType(mediaType: MediaType): List<SortOption> = when (mediaType) {
                MediaType.MOVIE -> forMovies()
                MediaType.TV -> forSeries()
            }
            
            /**
             * All available sort options (legacy - use forMediaType instead)
             */
            fun all(): List<SortOption> = listOf(
                PopularityDesc,
                PopularityAsc,
                ReleaseDateDesc,
                ReleaseDateAsc,
                FirstAirDateDesc,
                FirstAirDateAsc,
                TMDBRatingDesc,
                TMDBRatingAsc,
                TitleAsc,
                TitleDesc
            )
        }
    }
}
