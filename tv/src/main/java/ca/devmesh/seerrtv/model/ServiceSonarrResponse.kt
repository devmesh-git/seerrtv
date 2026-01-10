package ca.devmesh.seerrtv.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SonarrResult(
    val id: Int,
    val name: String,
    val is4k: Boolean,
    val isDefault: Boolean,
    val activeDirectory: String,
    val activeProfileId: Int,
    val activeAnimeProfileId: Int? = null,
    val activeAnimeDirectory: String? = null,
    val activeLanguageProfileId: Int? = null,
    val activeTags: List<Int>? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Tag (
    val id: Int,
    val label: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Sonarr(
    val server: SonarrServer,
    val profiles: List<Profile>,
    val rootFolders: List<SonarrRootFolder>,
    val languageProfiles: List<SonarrLanguageProfile>? = null,
    val tags: List<Tag>
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SonarrServer(
    val id: Int,
    val name: String,
    val is4k: Boolean,
    val isDefault: Boolean,
    val activeDirectory: String,
    val activeProfileId: Int,
    val activeAnimeProfileId: Int? = null,
    val activeAnimeDirectory: String? = null,
    val activeLanguageProfileId: Int? = null,
    val activeTags: List<Int> = emptyList(),
    val activeAnimeTags: List<Int>? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SonarrRootFolder(
    val id: Int,
    val freeSpace: Long,
    val path: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SonarrLanguageProfile(
    val name: String,
    val upgradeAllowed: Boolean,
    val cutoff: SonarrLanguage,
    val languages: List<SonarrLanguageAllowed>,
    val id: Int
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SonarrLanguage(
    val id: Int,
    val name: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SonarrLanguageAllowed(
    val language: SonarrLanguage,
    val allowed: Boolean
)

@SuppressLint("UnsafeOptInUsageError")
data class SonarrServerInfo(
    val allServers: List<Sonarr>,
    val defaultServer: Sonarr?,
    val error: Exception? = null
)