package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable

@Serializable
data class Discover(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val keywords: List<Keyword> = emptyList(),
    val results: List<Media>
)

@Serializable
data class Media(
    val adult: Boolean? = null,
    val backdropPath: String? = null,
    val createdAt: String? = null,
    val downloadStatus: List<DownloadStatus> = emptyList(),
    val downloadStatus4k: List<DownloadStatus> = emptyList(),
    val externalServiceId: Int? = null,
    val externalServiceId4k: Int? = null,
    val externalServiceSlug: String? = null,
    val externalServiceSlug4k: String? = null,
    val genreIds: List<Int>? = null,
    val id: Int,
    val imdbId: String? = null,
    val iOSPlexUrl: String? = null,
    val lastSeasonChange: String? = null,
    val mediaAddedAt: String? = null,
    val mediaInfo: MediaInfo? = null,
    val mediaType: String,
    val name: String? = null,
    val originalLanguage: String? = null,
    val originalTitle: String? = null,
    val overview: String? = null,
    val plexUrl: String? = null,
    val popularity: Double? = null,
    val posterPath: String? = null,
    val ratingKey: String? = null,
    val ratingKey4k: String? = null,
    val releaseDate: String? = null,
    val seasons: List<Season>? = null,
    val serviceId: Int? = null,
    val serviceId4k: Int? = null,
    val serviceUrl: String? = null,
    val status: Int? = null,
    val status4k: Int? = null,
    val title: String? = null,
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val updatedAt: String? = null,
    val video: Boolean? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    val request: Request? = null,
    val genreName: String? = null,
    val studioName: String? = null,
    val networkName: String? = null,
    val logoPath: String? = null
)

@Serializable
data class DownloadStatus(
    val estimatedCompletionTime: String? = null,
    val externalId: Int,
    val mediaType: String,
    val size: Long,
    val sizeLeft: Long? = null,
    val status: String,
    val timeLeft: String? = null,
    val title: String
)
