package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val pageInfo: PageInfo,
    val results: List<User>
)

@Serializable
data class AuthMeResponse(
    val id: Int,
    val email: String? = null,
    val permissions: Int? = null,
    val username: String? = null,
    val displayName: String
)

@Serializable
data class User(
    val id: Int,
    val email: String? = null,
    val permissions: Int? = null,
    val warnings: List<String> = emptyList(),
    val jellyfinUsername: String? = null,
    val username: String? = null,
    val recoveryLinkExpirationDate: String? = null,
    val userType: Int? = null,
    val plexId: Int? = null,
    val jellyfinUserId: String? = null,
    val plexToken: String? = null,
    val avatar: String? = null,
    val movieQuotaLimit: Int? = null,
    val movieQuotaDays: Int? = null,
    val tvQuotaLimit: Int? = null,
    val tvQuotaDays: Int? = null,
    val createdAt: String,
    val updatedAt: String,
    val requestCount: Int? = null,
    val displayName: String
)
