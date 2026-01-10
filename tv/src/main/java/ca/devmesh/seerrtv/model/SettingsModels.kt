package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable

@Serializable
data class PlexSettingsResponse(
    val name: String = "",
    val ip: String = "",
    val port: Int = 0,
    val useSsl: Boolean = false,
    val libraries: List<PlexLibrary> = emptyList(),
    val machineId: String = ""
)

@Serializable
data class PlexLibrary(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = false,
    val type: String = "",
    val lastScan: Long? = null
)

@Serializable
data class JellyfinSettingsResponse(
    val name: String = "",
    val ip: String = "",
    val port: Int = 0,
    val useSsl: Boolean = false,
    val urlBase: String = "",
    val externalHostname: String = "",
    val jellyfinForgotPasswordUrl: String = "",
    val libraries: List<JellyfinLibrary> = emptyList(),
    val serverId: String = "",
    val apiKey: String = ""
)

@Serializable
data class JellyfinLibrary(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = false,
    val type: String = ""
) 