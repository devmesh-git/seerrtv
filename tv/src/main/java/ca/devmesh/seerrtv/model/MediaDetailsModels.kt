package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MediaDetails(
    val id: Int,
    val type: String? = null, // This is the type returned by the API (e.g., "SERIES")
    var mediaType: MediaType? = null, // This will be injected by the ViewModel
    val adult: Boolean? = null,
    val relatedVideos: List<RelatedVideo>? = null,
    val title: String? = null, // For movies
    val name: String? = null, // For TV shows
    val overview: String,
    val backdropPath: String?,
    val posterPath: String?,
    val genres: List<Genre>,
    val voteAverage: Double,
    val voteCount: Int,
    val status: String,
    val tagline: String?,
    val releaseDate: String? = null, // For movies
    val releases: Results? = null,
    val firstAirDate: String? = null, // For TV shows
    val runtime: Int? = null, // For movies
    val episodeRunTime: List<Int>? = null, // For TV shows
    val credits: Credits,
    val externalIds: ExternalIds,
    val mediaInfo: MediaInfo? = null,
    val watchProviders: List<WatchProvider>,
    val productionCompanies: List<ProductionCompany>,
    val productionCountries: List<ProductionCountry>,
    val spokenLanguages: List<SpokenLanguage>? = null,
    // TV-specific fields
    val createdBy: List<CreatedBy>? = null,
    val numberOfSeasons: Int? = null,
    val numberOfEpisodes: Int? = null,
    val seasons: List<Season>? = null,
    val lastAirDate: String? = null,
    val inProduction: Boolean? = null,
    val networks: List<Network>? = null,
    // Movie-specific fields
    val budget: Long? = null,
    val revenue: Long? = null,
    val imdbId: String? = null,
    val originalTitle: String? = null,
    val collection: Collection? = null,
    val keywords: List<Keyword>? = null,
    val ratings: RatingsResponse? = null
)

@Serializable
data class RelatedVideo(
    val site: String? = null,
    val key: String? = null,
    val name: String? = null,
    val size: Int? = null,
    val type: String? = null,
    val url: String? = null
)

@Serializable
data class CreatedBy(
    val id: Int,
    @SerialName("credit_id")
    val creditId: String,
    val name: String,
    @SerialName("original_name")
    val originalName: String? = null,
    val gender: Int? = null,
    val profilePath: String? = null
)

@Serializable
data class Results(
    val results: List<ReleaseResults>? = null,
)

@Serializable
data class ReleaseResults(
    val iso_3166_1: String,
    @SerialName("release_dates")
    val releaseDates: List<ReleaseDate>? = null,
)

@Serializable
data class ReleaseDate(
    val certification: String,
    val descriptors: List<String>? = null,
    val iso_639_1: String,
    val note: String,
    val releaseDate: String? = null,
    val type: Int
)
