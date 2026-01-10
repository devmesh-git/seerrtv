package ca.devmesh.seerrtv.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

typealias RadarrsResponse = List<RadarrResult>

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class RadarrResult(
    val id: Int,
    val name: String,
    val is4k: Boolean,
    val isDefault: Boolean,
    val activeDirectory: String,
    val activeProfileId: Int,
    val activeTags: List<Int>? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Radarr(
    val server: RadarrServer,
    val profiles: List<Profile>,
    val rootFolders: List<RadarrRootFolder>,
    val tags: List<Tag>? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class RadarrServer(
    val id: Int,
    val name: String,
    val is4k: Boolean,
    val isDefault: Boolean,
    val activeDirectory: String,
    val activeProfileId: Int,
    val activeTags: List<Int>? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class RadarrRootFolder(
    val id: Int,
    val freeSpace: Long,
    val path: String
)

@SuppressLint("UnsafeOptInUsageError")
data class RadarrServerInfo(
    val allServers: List<Radarr>,
    val defaultServer: Radarr?,
    val error: Exception? = null
)