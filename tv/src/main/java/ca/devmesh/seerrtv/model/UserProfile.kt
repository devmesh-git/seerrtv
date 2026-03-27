package ca.devmesh.seerrtv.model

import ca.devmesh.seerrtv.data.SeerrApiService.SeerrConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ProfileSettings(
    @SerialName("app_language") val appLanguage: String = "en",
    @SerialName("discovery_language") val discoveryLanguage: String = "en",
    @SerialName("default_streaming_region") val defaultStreamingRegion: String = "US",
    @SerialName("folder_selection_enabled") val folderSelectionEnabled: Boolean = false,
    @SerialName("use_24_hour_clock") val use24HourClock: Boolean = true,
    @SerialName("use_trailer_webview") val useTrailerWebView: Boolean = false
)

@Serializable
data class UserProfile(
    @SerialName("id") val id: String = UUID.randomUUID().toString(),
    @SerialName("name") val name: String,
    @SerialName("email") val email: String? = null,
    @SerialName("avatar_initials") val avatarInitials: String,
    @SerialName("avatar_color") val avatarColor: String,
    /**
     * Optional PIN hash used for lightweight profile protection.
     * Empty string means no PIN configured.
     */
    @SerialName("pin_hash") val pinHash: String = "",
    /**
     * Configuration associated with this profile. For now we embed SeerrConfig
     * so each profile has its own full API configuration snapshot.
     */
    @SerialName("config") val config: SeerrConfig,
    @SerialName("settings") val settings: ProfileSettings = ProfileSettings(),
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

