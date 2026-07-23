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

/**
 * JSON-persistable snapshot of [RadarrServerInfo] ([RadarrServerInfo] itself can't be
 * `@Serializable` — it carries an [Exception]). Persisted after each successful
 * `loadRadarrConfiguration` so a process restart (e.g. after the external trailer player got the
 * app killed) restores 4K capability and the request modal's server/profile/folder options
 * without waiting for a full splash-time reload.
 */
@Serializable
data class PersistedRadarrCache(
    val allServers: List<Radarr>,
    val defaultServerId: Int? = null
)