package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable

@Serializable
data class Region(
    val iso_3166_1: String,
    val english_name: String,
    val native_name: String? = null
)

@Serializable
data class FilterLanguage(
    val iso_639_1: String,
    val english_name: String,
    val name: String? = null
)

@Serializable
data class ContentRating(
    val certification: String,
    val meaning: String = "",
    val order: Int = 0
)
