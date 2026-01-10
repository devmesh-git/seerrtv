package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable

@Serializable
data class CombinedCredits(
    val cast: List<CreditItem>,
    val crew: List<CreditItem>,
    val id: Int
)

@Serializable
data class CreditItem(
    val id: Int,
    val originalLanguage: String? = null,
    val overview: String? = null,
    val voteCount: Int? = null,
    val mediaType: String,
    val popularity: Double? = null,
    val creditId: String? = null,
    val backdropPath: String? = null,
    val voteAverage: Double? = null,
    val genreIds: List<Int>? = null,
    val posterPath: String? = null,
    val originalTitle: String? = null,
    val video: Boolean? = null,
    val title: String? = null,
    val adult: Boolean? = null,
    val releaseDate: String? = null,
    val character: String? = null,
    val episodeCount: Int? = null,
    val originCountry: List<String>? = null,
    val originalName: String? = null,
    val name: String? = null,
    val firstAirDate: String? = null,
    val department: String? = null,
    val job: String? = null,
    val mediaInfo: MediaInfo? = null
)
