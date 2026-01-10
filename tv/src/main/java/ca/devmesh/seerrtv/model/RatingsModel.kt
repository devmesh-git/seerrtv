package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable

@Serializable
data class RatingsResponse(
    val rt: RottenTomatoesRating? = null,
    val imdb: ImdbRating? = null
)

@Serializable
data class RatingsCombinedResponse(
    val rt: RottenTomatoesRating? = null,
    val imdb: ImdbRating? = null
)

@Serializable
data class RatingsFlatResponse(
    val title: String? = null,
    val url: String? = null,
    val criticsRating: String? = null,
    val criticsScore: Int? = null,
    val audienceRating: String? = null,
    val audienceScore: Int? = null,
    val year: Int? = null
)

@Serializable
data class RottenTomatoesRating(
    val title: String? = null,
    val url: String? = null,
    val criticsRating: String? = null,
    val criticsScore: Int? = null,
    val audienceRating: String? = null,
    val audienceScore: Int? = null,
    val year: Int? = null
)

@Serializable
data class ImdbRating(
    val title: String? = null,
    val url: String? = null,
    val criticsScore: Double? = null
)
