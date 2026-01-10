package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable

@Serializable
data class PersonDetails(
    val id: Int,
    val name: String,
    val birthday: String?,
    val deathday: String?,
    val knownForDepartment: String,
    val alsoKnownAs: List<String>,
    val gender: Int,
    val biography: String,
    val popularity: Double,
    val placeOfBirth: String?,
    val profilePath: String?,
    val adult: Boolean,
    val imdbId: String?,
    val homepage: String?
)