package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable

@Serializable
data class RequestResponse(
    val pageInfo: PageInfo,
    val results: List<Request>
)

@Serializable
data class Request(
    val id: Int,
    val createdAt: String,
    val is4k: Boolean,
    val isAutoRequest: Boolean,
    val languageProfileId: Int? = null,
    val media: MediaInfo,
    val modifiedBy: User? = null,
    val profileId: Int? = null,
    val profileName: String? = null,
    val requestedBy: User,
    val rootFolder: String? = null,
    val seasonCount: Int,
    val seasons: List<Season> = emptyList(),
    val serverId: Int? = null,
    val status: Int,
    val tags: List<String>? = null,
    val type: String,
    val updatedAt: String,
)

@Serializable
data class MediaRequestBody(
    val mediaType: String,
    val mediaId: Int,
    val userId: Int? = null,
    val is4k: Boolean = false,
    val tvdbId: Int? = null,
    val seasons: List<Int>? = null,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val languageProfileId: Int? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class Profile(
    val id: Int,
    val name: String
)