package ca.devmesh.seerrtv.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import ca.devmesh.seerrtv.data.SeerrApiService.SeerrConfig
import ca.devmesh.seerrtv.model.AuthType
import ca.devmesh.seerrtv.model.AvatarColor
import ca.devmesh.seerrtv.model.MediaServerType
import ca.devmesh.seerrtv.model.ProfileSettings
import ca.devmesh.seerrtv.model.UserProfile
import kotlinx.serialization.json.Json
import ca.devmesh.seerrtv.util.AvatarUtils.generateInitialsFromNameOrEmail
import ca.devmesh.seerrtv.util.AvatarUtils.resolveUniqueInitials

object SharedPreferencesUtil {
    private const val PREFS_NAME = "SeerrTVPrefs"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Normalizes a hostname by removing:
     * - Leading/trailing whitespace
     * - Protocol prefixes (http://, https://)
     * - Trailing slashes
     * 
     * When stripPort is true, also removes:
     * - Port numbers (e.g., :8096)
     * - Paths (e.g., /jellyfin)
     * - Query parameters and fragments
     * 
     * Examples:
     * - "https://example.com/" -> "example.com"
     * - "http://hostname/" -> "hostname"
     * - "  example.com  " -> "example.com"
     * - "example.com:8096" with stripPort=true -> "example.com"
     * - "https://example.com/jellyfin" with stripPort=true -> "example.com"
     */
    fun normalizeHostname(hostname: String, stripPort: Boolean = false): String {
        var normalized = hostname
            .trim()
            .replace(Regex("^(https?://)", RegexOption.IGNORE_CASE), "")
            .trimEnd('/')
            .trim()
        
        if (stripPort) {
            // Remove port number (everything after the last colon that's followed by digits)
            normalized = normalized.split(':').firstOrNull() ?: normalized
            // Remove paths, query params, and fragments
            normalized = normalized.split('/').firstOrNull() ?: normalized
            normalized = normalized.split('?').firstOrNull() ?: normalized
            normalized = normalized.split('#').firstOrNull() ?: normalized
            normalized = normalized.trim()
        }
        
        return normalized
    }

    /**
     * Aggressively sanitizes user- or server-supplied host input to produce a best-effort
     * host string for API base URL building. Use before validation so we can accept
     * pasted URLs and fix common mistakes instead of only failing.
     *
     * - Trims and strips protocol (http/https)
     * - Removes path, query, and fragment
     * - Collapses internal whitespace (e.g. "exa mple.com" -> "example.com")
     * - When stripPort is true, removes port; otherwise keeps host:port for main Seerr URL
     */
    fun sanitizeHostnameForApi(hostname: String, stripPort: Boolean = false): String {
        var s = hostname
            .trim()
            .replace(Regex("^(https?://)", RegexOption.IGNORE_CASE), "")
            .trim()
        s = s.split('/').firstOrNull() ?: s
        s = s.split('?').firstOrNull() ?: s
        s = s.split('#').firstOrNull() ?: s
        s = s.replace(Regex("\\s+"), "")
        s = s.trim()
        if (stripPort) {
            s = s.split(':').firstOrNull() ?: s
            s = s.trim()
        }
        return s
    }

    /**
     * Sanitizes protocol to a value safe for building API URLs.
     * Returns "https" only when input is explicitly "https" (case-insensitive); otherwise "http".
     */
    fun sanitizeProtocolForApi(protocol: String): String {
        return when (protocol.trim().lowercase()) {
            "https" -> "https"
            else -> "http"
        }
    }

    private const val KEY_PROTOCOL = "protocol"
    private const val KEY_HOSTNAME = "hostname"
    private const val KEY_CLOUDFLARE_ENABLED = "cloudflare_enabled"
    private const val KEY_CF_CLIENT_ID = "cf_client_id"
    private const val KEY_CF_CLIENT_SECRET = "cf_client_secret"
    private const val KEY_AUTH_TYPE = "auth_type"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_JELLYFIN_HOSTNAME = "jellyfin_hostname"
    private const val KEY_JELLYFIN_PORT = "jellyfin_port"
    private const val KEY_JELLYFIN_USE_SSL = "jellyfin_use_ssl"
    private const val KEY_JELLYFIN_URL_BASE = "jellyfin_url_base"
    private const val KEY_JELLYFIN_EMAIL = "jellyfin_email"
    private const val KEY_CONFIG_VALID = "config_valid"
    private const val KEY_API_URL = "api_url"
    private const val KEY_FOLDER_SELECTION_ENABLED = "folder_selection_enabled"
    private const val KEY_USE_24_HOUR_CLOCK = "use_24_hour_clock"
    private const val KEY_SERVER_TYPE = "server_type"
    private const val KEY_MEDIA_SERVER_TYPE = "media_server_type"
    private const val KEY_DETECTED_MEDIA_SERVER_TYPE = "detected_media_server_type"
    private const val KEY_USER_PERMISSIONS = "user_permissions"
    private const val KEY_HIDE_AVAILABLE = "server_hide_available"
    private const val KEY_HIDE_BLOCKLISTED = "server_hide_blocklisted"
    private const val KEY_CACHED_RADARR_SERVERS = "cached_radarr_servers"
    private const val KEY_CACHED_SONARR_SERVERS = "cached_sonarr_servers"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_DISPLAY_NAME = "user_display_name"
    private const val KEY_USER_REMOTE_AVATAR = "user_remote_avatar_url"
    private const val KEY_PLEX_CLIENT_ID = "plex_client_id"
    private const val KEY_PLEX_AUTH_TOKEN = "plex_auth_token"
    private const val KEY_DISCOVERY_LANGUAGE = "discovery_language"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val KEY_DEFAULT_STREAMING_REGION = "default_streaming_region"
    private const val KEY_USE_TRAILER_WEBVIEW = "use_trailer_webview"
    private const val KEY_PENDING_NEW_PROFILE_APP_LANGUAGE = "pending_new_profile_app_language"
    private const val KEY_PROFILES_JSON = "profiles_json"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val KEY_SKIP_PROFILE_SELECTION_ON_NEXT_MAIN =
        "skip_profile_selection_once"
    private const val KEY_PROFILE_SELECTION_TARGET_PROFILE_ID =
        "profile_selection_target_profile_id"
    private const val KEY_PROFILE_SELECTION_TARGET_POST_ACTIVATION_ROUTE =
        "profile_selection_target_post_activation_route"
    private const val KEY_PROFILE_SELECTION_COMPLETED = "profile_selection_completed"
    private const val KEY_FORCE_SPLASH_RESET_ON_NEXT = "force_splash_reset_on_next"
    private const val KEY_PENDING_NEW_PROFILE_CREATION = "pending_new_profile_creation"
    // Supported app languages — the languages the app is translated into. Single source of
    // truth is LanguageCatalog (which must mirror the res/values-* folders).
    val SUPPORTED_APP_LANGUAGES = LanguageCatalog.codes

    fun getAppLanguage(context: Context): String? {
        val activeProfile = getActiveProfile(context)
        if (activeProfile != null) return activeProfile.settings.appLanguage
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_APP_LANGUAGE, null)
    }

    fun getPendingNewProfileAppLanguage(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_PENDING_NEW_PROFILE_APP_LANGUAGE, null)
    }

    fun setPendingNewProfileAppLanguage(context: Context, language: String?) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            if (language.isNullOrBlank()) remove(KEY_PENDING_NEW_PROFILE_APP_LANGUAGE)
            else putString(KEY_PENDING_NEW_PROFILE_APP_LANGUAGE, language.lowercase())
        }
    }

    fun setAppLanguage(context: Context, language: String) {
        val normalized = language.lowercase()
        val profiles = getProfiles(context)
        val targetId = resolveSettingsTargetProfileId(profiles, getActiveProfileId(context))
        if (targetId != null) {
            val updated = profiles.map { profile ->
                if (profile.id == targetId) {
                    profile.copy(
                        settings = profile.settings.copy(appLanguage = normalized),
                        updatedAt = System.currentTimeMillis()
                    )
                } else profile
            }
            saveProfiles(context, updated)
            return
        }
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            putString(KEY_APP_LANGUAGE, normalized)
        }
    }

    fun resolveAppLanguage(context: Context): String {
        // 1. Check if user already has a preference (set via language screen or Settings)
        val storedLanguage = getAppLanguage(context)
        if (storedLanguage != null) {
            return storedLanguage
        }

        // 2. No preference yet - use system language for this session only (do not persist).
        // Language is only persisted when the user selects one on the language screen.
        val systemLocale = CommonUtil.primarySystemLocale(context)
        val systemLanguage = systemLocale.language.lowercase()
        return if (systemLanguage == "zh" || SUPPORTED_APP_LANGUAGES.contains(systemLanguage)) {
            systemLanguage
        } else {
            "en"
        }
    }

    fun setPendingNewProfileCreation(context: Context, pending: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            if (pending) putBoolean(KEY_PENDING_NEW_PROFILE_CREATION, true)
            else remove(KEY_PENDING_NEW_PROFILE_CREATION)
        }
    }

    fun isPendingNewProfileCreation(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_PENDING_NEW_PROFILE_CREATION, false)
    }

    fun clearPendingNewProfileCreation(context: Context) {
        setPendingNewProfileCreation(context, false)
        setPendingNewProfileAppLanguage(context, null)
    }

    /**
     * Profile label + avatar initials: prefer Jellyseerr/Overseerr [auth/me] display name when known,
     * then username / email local-part / [fallbackName] (e.g. hostname or "Profile N").
     */
    private fun resolveProfileDisplayName(context: Context, config: SeerrConfig, fallbackName: String): String {
        val apiDisplay = getUserDisplayName(context)?.trim().orEmpty()
        if (apiDisplay.isNotEmpty()) return apiDisplay
        val usernameCandidate = config.username.takeIf { it.isNotBlank() }
        val emailCandidate =
            config.jellyfinEmail.takeIf { it.isNotBlank() } ?: usernameCandidate?.takeIf { it.contains('@') }
        return when {
            usernameCandidate.isNullOrBlank().not() && usernameCandidate.contains('@') ->
                usernameCandidate.substringBefore('@')
            usernameCandidate.isNullOrBlank().not() ->
                usernameCandidate
            !emailCandidate.isNullOrBlank() -> emailCandidate.substringBefore('@')
            else -> fallbackName
        }
    }

    /**
     * After API validation succeeds, append a new profile with [config] and make it active.
     * Updates legacy global prefs to match (same as [saveConfig]).
     * Clears the pending-new-profile flag when done.
     */
    fun appendNewProfileWithValidatedConfig(context: Context, config: SeerrConfig) {
        val profiles = getProfiles(context)
        if (profiles.isEmpty()) {
            clearPendingNewProfileCreation(context)
            saveConfig(context, config, true)
            setSkipProfileSelectionOnce(context, true)
            return
        }

        val normalizedHostname = normalizeHostname(config.hostname, stripPort = false)
        val normalizedJellyfinHostname = normalizeHostname(config.jellyfinHostname, stripPort = true)
        persistGlobalApiConfigSnapshot(
            context,
            config,
            normalizedHostname,
            normalizedJellyfinHostname,
            isValid = true
        )

        val embeddedConfig = config.copy(
            hostname = normalizedHostname,
            jellyfinHostname = normalizedJellyfinHostname,
            isSubmitted = true,
            createdAt = config.createdAt.takeIf { it.isNotBlank() } ?: System.currentTimeMillis().toString()
        )

        val usernameCandidate = config.username.takeIf { it.isNotBlank() }
        val emailCandidate =
            config.jellyfinEmail.takeIf { it.isNotBlank() } ?: usernameCandidate?.takeIf { it.contains('@') }
        val nameCandidate = resolveProfileDisplayName(
            context,
            config,
            fallbackName = "Profile${profiles.size + 1}"
        )

        val existingInitials = profiles.map { it.avatarInitials }.toSet()
        val resolvedInitials = resolveUniqueInitials(
            desiredInitials = generateInitialsFromNameOrEmail(nameCandidate, emailCandidate),
            existingInitials = existingInitials,
            seed = nameCandidate
        )

        val profileSettings = ProfileSettings(
            appLanguage = getPendingNewProfileAppLanguage(context)
                ?: getAppLanguage(context)
                ?: "en",
            discoveryLanguage = getDiscoveryLanguage(context),
            defaultStreamingRegion = getDefaultStreamingRegion(context),
            folderSelectionEnabled = isFolderSelectionEnabled(context),
            use24HourClock = use24HourClock(context),
            useTrailerWebView = useTrailerWebView(context)
        )

        val newProfile = UserProfile(
            name = nameCandidate,
            email = emailCandidate,
            avatarInitials = resolvedInitials,
            avatarColor = AvatarColor.PURPLE.key,
            // Carried explicitly: the identity sync deliberately skips this window (see
            // [resolveIdentitySyncTargetProfileId]), so the new profile takes the avatar from the
            // auth/me values saved moments ago rather than inheriting the global fallback.
            remoteAvatarUrl = getRemoteAvatarUrl(context),
            pinHash = "",
            config = embeddedConfig,
            settings = profileSettings
        )
        saveProfiles(context, profiles + newProfile)
        setActiveProfileId(context, newProfile.id)
        setSkipProfileSelectionOnce(context, true)
        clearPendingNewProfileCreation(context)
    }

    private fun persistGlobalApiConfigSnapshot(
        context: Context,
        config: SeerrConfig,
        normalizedHostname: String,
        normalizedJellyfinHostname: String,
        isValid: Boolean
    ) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            putString(KEY_PROTOCOL, config.protocol)
            putString(KEY_HOSTNAME, normalizedHostname)
            putBoolean(KEY_CLOUDFLARE_ENABLED, config.cloudflareEnabled)
            putString(KEY_CF_CLIENT_ID, config.cfClientId)
            putString(KEY_CF_CLIENT_SECRET, config.cfClientSecret)
            putString(KEY_AUTH_TYPE, config.getAuthType().type)
            putString(KEY_API_KEY, config.apiKey)
            putString(KEY_USERNAME, config.username)
            putString(KEY_PASSWORD, config.password)
            putString(KEY_JELLYFIN_HOSTNAME, normalizedJellyfinHostname)
            putInt(KEY_JELLYFIN_PORT, config.jellyfinPort)
            putBoolean(KEY_JELLYFIN_USE_SSL, config.jellyfinUseSsl)
            putString(KEY_JELLYFIN_URL_BASE, config.jellyfinUrlBase)
            putString(KEY_JELLYFIN_EMAIL, config.jellyfinEmail)
            putString(KEY_PLEX_CLIENT_ID, config.plexClientId)
            putString(KEY_PLEX_AUTH_TOKEN, config.plexAuthToken)
            putBoolean(KEY_CONFIG_VALID, isValid)
            putString(KEY_API_URL, "${config.protocol}://${normalizedHostname}")
            putBoolean(KEY_FOLDER_SELECTION_ENABLED, false)
        }
    }

    fun hasApiConfig(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // If we already have profiles, treat configuration as present when an active profile exists
        val profilesJson = sharedPrefs.getString(KEY_PROFILES_JSON, null)
        if (!profilesJson.isNullOrBlank()) {
            val profiles = runCatching { json.decodeFromString<List<UserProfile>>(profilesJson) }
                .getOrElse { emptyList() }
            val activeId = sharedPrefs.getString(KEY_ACTIVE_PROFILE_ID, null)
            return profiles.isNotEmpty() && activeId != null && profiles.any { it.id == activeId }
        }
        return sharedPrefs.getBoolean(KEY_CONFIG_VALID, false)
    }

    fun saveConfig(context: Context, config: SeerrConfig, isValid: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Normalize hostname before saving (don't strip port for main Seerr hostname)
        val normalizedHostname = normalizeHostname(config.hostname, stripPort = false)
        // Normalize Jellyfin hostname (strip port since it's in a separate field)
        val normalizedJellyfinHostname = normalizeHostname(config.jellyfinHostname, stripPort = true)
        persistGlobalApiConfigSnapshot(
            context,
            config,
            normalizedHostname,
            normalizedJellyfinHostname,
            isValid
        )

        // Profile-aware config persistence:
        // - If profiles already exist, update the active profile's embedded config.
        // - If this is the first time saving config, create a default local profile snapshot.
        val profilesJson = sharedPrefs.getString(KEY_PROFILES_JSON, null)
        val embeddedConfig = config.copy(
            hostname = normalizedHostname,
            jellyfinHostname = normalizedJellyfinHostname,
            isSubmitted = isValid,
            createdAt = config.createdAt.takeIf { it.isNotBlank() } ?: System.currentTimeMillis().toString()
        )

        if (!profilesJson.isNullOrBlank()) {
            val profiles = json.decodeFromString<List<UserProfile>>(profilesJson)
            val activeId = sharedPrefs.getString(KEY_ACTIVE_PROFILE_ID, null)
            val actualActiveId = activeId ?: profiles.firstOrNull()?.id
            if (actualActiveId != null) {
                val activeProfile = profiles.firstOrNull { it.id == actualActiveId }
                val usernameCandidate = config.username.takeIf { it.isNotBlank() }
                val emailCandidate =
                    config.jellyfinEmail.takeIf { it.isNotBlank() } ?: usernameCandidate?.takeIf { it.contains('@') }
                val nameCandidate = resolveProfileDisplayName(
                    context,
                    config,
                    fallbackName = activeProfile?.name ?: normalizedHostname
                )

                val otherInitials = profiles
                    .filter { it.id != actualActiveId }
                    .map { it.avatarInitials }
                    .toSet()

                val desiredInitials = generateInitialsFromNameOrEmail(nameCandidate, emailCandidate)
                val resolvedInitials = resolveUniqueInitials(
                    desiredInitials = desiredInitials,
                    existingInitials = otherInitials,
                    seed = nameCandidate
                )

                val updated = profiles.map { profile ->
                    if (profile.id == actualActiveId) {
                        profile.copy(
                            name = nameCandidate,
                            email = emailCandidate,
                            avatarInitials = resolvedInitials,
                            config = embeddedConfig,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else profile
                }
                saveProfiles(context, updated)
                setActiveProfileId(context, actualActiveId)
            }
        } else {
            val displayName = resolveProfileDisplayName(context, config, fallbackName = normalizedHostname)

            val emailCandidate =
                config.jellyfinEmail.takeIf { it.isNotBlank() }
                    ?: config.username.takeIf { it.isNotBlank() && it.contains('@') }

            val initials = resolveUniqueInitials(
                desiredInitials = generateInitialsFromNameOrEmail(displayName, emailCandidate),
                existingInitials = emptySet(),
                seed = displayName
            )

            val profile = UserProfile(
                name = displayName,
                email = emailCandidate,
                avatarInitials = initials,
                avatarColor = AvatarColor.PURPLE.key,
                pinHash = "",
                config = embeddedConfig,
                settings = ProfileSettings(
                    appLanguage = getAppLanguage(context) ?: "en",
                    discoveryLanguage = getDiscoveryLanguage(context),
                    defaultStreamingRegion = getDefaultStreamingRegion(context),
                    folderSelectionEnabled = isFolderSelectionEnabled(context),
                    use24HourClock = use24HourClock(context),
                    useTrailerWebView = useTrailerWebView(context)
                )
            )
            saveProfiles(context, listOf(profile))
            setActiveProfileId(context, profile.id)
        }
    }

    fun getConfig(context: Context): SeerrConfig? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // If profiles exist and an active profile is set, prefer its embedded config
        val profilesJson = sharedPrefs.getString(KEY_PROFILES_JSON, null)
        val activeProfileId = sharedPrefs.getString(KEY_ACTIVE_PROFILE_ID, null)
        if (!profilesJson.isNullOrBlank() && !activeProfileId.isNullOrBlank()) {
            val profiles = runCatching { json.decodeFromString<List<UserProfile>>(profilesJson) }
                .getOrElse { emptyList() }
            val activeProfile = profiles.firstOrNull { it.id == activeProfileId }
            if (activeProfile != null) {
                return activeProfile.config
            }
        }

        val protocol = sharedPrefs.getString(KEY_PROTOCOL, null) ?: return null
        var hostname = sharedPrefs.getString(KEY_HOSTNAME, null) ?: return null
        var authType = sharedPrefs.getString(KEY_AUTH_TYPE, null) ?: return null

        // Normalize hostname to clean up any bad data (don't strip port for main Seerr hostname)
        val normalizedHostname = normalizeHostname(hostname, stripPort = false)
        if (normalizedHostname != hostname) {
            Log.d("SharedPreferencesUtil", "Cleaning hostname: '$hostname' -> '$normalizedHostname'")
            hostname = normalizedHostname
            // Save cleaned hostname back to storage
            sharedPrefs.edit(commit = true) {
                putString(KEY_HOSTNAME, normalizedHostname)
            }
        }
        
        // Normalize Jellyfin hostname to clean up any bad data (strip port since it's in a separate field)
        var jellyfinHostname = sharedPrefs.getString(KEY_JELLYFIN_HOSTNAME, "") ?: ""
        val normalizedJellyfinHostname = normalizeHostname(jellyfinHostname, stripPort = true)
        if (normalizedJellyfinHostname != jellyfinHostname && jellyfinHostname.isNotEmpty()) {
            Log.d("SharedPreferencesUtil", "Cleaning Jellyfin hostname: '$jellyfinHostname' -> '$normalizedJellyfinHostname'")
            // Save cleaned Jellyfin hostname back to storage
            sharedPrefs.edit(commit = true) {
                putString(KEY_JELLYFIN_HOSTNAME, normalizedJellyfinHostname)
            }
        }
        // Use the normalized value
        jellyfinHostname = normalizedJellyfinHostname

        // Update existing auth_type of "username" to "localUser"
        authType = if (authType == "username") "localUser" else authType
        
        // Validate authType and fallback to ApiKey if invalid
        val validAuthTypes = AuthType.entries.map { it.type }
        if (authType !in validAuthTypes) {
            Log.w("SharedPreferencesUtil", "Invalid authType '$authType' found, falling back to 'apiKey'")
            authType = AuthType.ApiKey.type
        }

        return SeerrConfig(
            protocol = protocol,
            hostname = hostname,
            cloudflareEnabled = sharedPrefs.getBoolean(KEY_CLOUDFLARE_ENABLED, false),
            cfClientId = sharedPrefs.getString(KEY_CF_CLIENT_ID, "") ?: "",
            cfClientSecret = sharedPrefs.getString(KEY_CF_CLIENT_SECRET, "") ?: "",
            authType = authType,
            apiKey = sharedPrefs.getString(KEY_API_KEY, "") ?: "",
            username = sharedPrefs.getString(KEY_USERNAME, "") ?: "",
            password = sharedPrefs.getString(KEY_PASSWORD, "") ?: "",
            jellyfinHostname = jellyfinHostname,
            jellyfinPort = sharedPrefs.getInt(KEY_JELLYFIN_PORT, 8096),
            jellyfinUseSsl = sharedPrefs.getBoolean(KEY_JELLYFIN_USE_SSL, false),
            jellyfinUrlBase = sharedPrefs.getString(KEY_JELLYFIN_URL_BASE, "/") ?: "/",
            jellyfinEmail = sharedPrefs.getString(KEY_JELLYFIN_EMAIL, "") ?: "",
            plexClientId = sharedPrefs.getString(KEY_PLEX_CLIENT_ID, "") ?: "",
            plexAuthToken = sharedPrefs.getString(KEY_PLEX_AUTH_TOKEN, "") ?: "",
            isSubmitted = true,
            createdAt = ""
        )
    }

    fun clearConfig(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            clear()
        }
    }

    // -------------------------------------------------------------------------
    // Profile storage
    // -------------------------------------------------------------------------

    /**
     * Decodes stored profiles JSON, or null if it cannot be read.
     *
     * The null is the point: callers that only need the profiles can treat it as "none", but
     * [ensureProfilesInitialized] *deletes* the stored JSON when it sees no profiles, and must
     * never do that to a blob that merely failed to parse. `UserProfile` and its embedded
     * `SeerrConfig` have required fields with no defaults (`name`, `avatarInitials`,
     * `avatarColor`, `config`; `protocol`, `hostname`, `authType`, `isSubmitted`, `createdAt`),
     * so a partial write, or any field added later without a default, turns every saved profile
     * and its settings into an unreadable blob — and deleting it would make that permanent
     * instead of recoverable by a later build that can read it.
     */
    internal fun decodeProfiles(rawJson: String): List<UserProfile>? =
        runCatching { this.json.decodeFromString<List<UserProfile>>(rawJson) }
            .getOrElse { error ->
                Log.e("SharedPreferencesUtil", "Failed to decode profiles JSON", error)
                null
            }

    fun getProfiles(context: Context): List<UserProfile> {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sharedPrefs.getString(KEY_PROFILES_JSON, null) ?: return emptyList()
        return decodeProfiles(json) ?: emptyList()
    }

    fun saveProfiles(context: Context, profiles: List<UserProfile>) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = this.json.encodeToString(profiles)
        sharedPrefs.edit(commit = true) {
            putString(KEY_PROFILES_JSON, json)
        }
    }

    fun getActiveProfileId(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_ACTIVE_PROFILE_ID, null)
    }

    fun setActiveProfileId(context: Context, profileId: String?) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            if (profileId == null) {
                remove(KEY_ACTIVE_PROFILE_ID)
            } else {
                putString(KEY_ACTIVE_PROFILE_ID, profileId)
            }
        }
    }

    fun getActiveProfile(context: Context): UserProfile? {
        val profiles = getProfiles(context)
        if (profiles.isEmpty()) return null
        val activeId = getActiveProfileId(context)
        return profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    }

    /**
     * Id of the profile a settings write must target, resolved exactly as [getActiveProfile]
     * resolves reads — including its fall back to the first profile when `active_profile_id` is
     * missing or names a profile that no longer exists.
     *
     * The two sides have to agree. When the setters instead required an exact id match, a write
     * in that fallback state fell through to the legacy global key, which the getters ignore
     * whenever any profile exists. The setting appeared to save, every read returned the old
     * profile value, and the change only surfaced on the next cold start, when
     * [ensureProfilesInitialized] migrated the stale global key into the first profile.
     *
     * Returns null only when there are no profiles at all — the one case where the legacy global
     * keys are still the correct destination.
     */
    internal fun resolveSettingsTargetProfileId(
        profiles: List<UserProfile>,
        activeId: String?
    ): String? = profiles.firstOrNull { it.id == activeId }?.id ?: profiles.firstOrNull()?.id

    /**
     * Which profile an `auth/me` identity (display name, initials, remote avatar) may be written to.
     *
     * Returns null while a new profile is being created, because at that moment the credentials
     * just validated belong to the profile that does not exist yet — [appendNewProfileWithValidatedConfig]
     * runs *after* [ca.devmesh.seerrtv.viewmodel.ConfigViewModel.validateAndSaveConfig] authenticates,
     * and it names the new profile from the same freshly-saved display name. Writing during that
     * window stamped the still-active *previous* profile with the new account's name and avatar,
     * leaving two identically-labelled profiles and no way to tell them apart in the picker.
     *
     * Otherwise resolves the same way every other profile-scoped write does, via
     * [resolveSettingsTargetProfileId], so a stale or missing active id falls back to the first
     * profile instead of silently skipping the sync.
     */
    internal fun resolveIdentitySyncTargetProfileId(
        profiles: List<UserProfile>,
        activeId: String?,
        pendingNewProfileCreation: Boolean
    ): String? = if (pendingNewProfileCreation) null else resolveSettingsTargetProfileId(profiles, activeId)

    fun updateActiveProfileAvatarColor(context: Context, colorKey: String): Boolean {
        val profiles = getProfiles(context)
        val activeId = getActiveProfileId(context) ?: return false
        if (profiles.isEmpty()) return false
        val updated = profiles.map { profile ->
            if (profile.id == activeId) {
                profile.copy(avatarColor = AvatarColor.fromKey(colorKey).key, updatedAt = System.currentTimeMillis())
            } else profile
        }
        saveProfiles(context, updated)
        return true
    }

    fun setActiveProfilePinHash(context: Context, pinHash: String): Boolean {
        val profiles = getProfiles(context)
        val activeId = getActiveProfileId(context) ?: return false
        if (profiles.isEmpty()) return false
        val normalized = pinHash.trim()
        val updated = profiles.map { profile ->
            if (profile.id == activeId) {
                profile.copy(pinHash = normalized, updatedAt = System.currentTimeMillis())
            } else profile
        }
        saveProfiles(context, updated)
        return true
    }

    private fun clearLegacyApiConfig(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            remove(KEY_PROTOCOL)
            remove(KEY_HOSTNAME)
            remove(KEY_CLOUDFLARE_ENABLED)
            remove(KEY_CF_CLIENT_ID)
            remove(KEY_CF_CLIENT_SECRET)
            remove(KEY_AUTH_TYPE)
            remove(KEY_API_KEY)
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
            remove(KEY_JELLYFIN_HOSTNAME)
            remove(KEY_JELLYFIN_PORT)
            remove(KEY_JELLYFIN_USE_SSL)
            remove(KEY_JELLYFIN_URL_BASE)
            remove(KEY_JELLYFIN_EMAIL)
            remove(KEY_PLEX_CLIENT_ID)
            remove(KEY_PLEX_AUTH_TOKEN)
            putBoolean(KEY_CONFIG_VALID, false)
            remove(KEY_API_URL)
        }
    }

    /**
     * Deletes the currently active profile.
     * Domain rule: only "your" profile (active) is deletable.
     */
    fun deleteActiveProfile(context: Context): Boolean {
        val profiles = getProfiles(context)
        if (profiles.isEmpty()) return false
        val activeId = getActiveProfileId(context) ?: return false

        val updated = profiles.filterNot { it.id == activeId }
        return if (updated.isEmpty()) {
            // Clear profiles + API config so the app routes to initial setup.
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs.edit(commit = true) {
                remove(KEY_PROFILES_JSON)
                remove(KEY_ACTIVE_PROFILE_ID)
            }
            clearLegacyApiConfig(context)
            true
        } else {
            saveProfiles(context, updated)
            setActiveProfileId(context, updated.first().id)
            true
        }
    }

    /**
     * Ensures we have a valid local profile list and an active profile id.
     * If only legacy global config exists (no profiles JSON), we migrate it into a single default profile.
     */
    fun ensureProfilesInitialized(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val profilesJson = sharedPrefs.getString(KEY_PROFILES_JSON, null)
        val activeProfileId = sharedPrefs.getString(KEY_ACTIVE_PROFILE_ID, null)

        // Fresh install with legacy config
        if (profilesJson.isNullOrBlank()) {
            val legacyConfig = getConfig(context)
            val hasLegacyConfig = legacyConfig != null && legacyConfig.isSubmitted
            if (!hasLegacyConfig) return

            val displayName =
                getUserDisplayName(context)
                    ?: legacyConfig.username.takeIf { it.isNotBlank() }
                    ?: legacyConfig.jellyfinEmail.takeIf { it.isNotBlank() }
                    ?: legacyConfig.hostname

            val initials = resolveUniqueInitials(
                desiredInitials = generateInitialsFromNameOrEmail(displayName, legacyConfig.jellyfinEmail),
                existingInitials = emptySet(),
                seed = displayName
            )

            val profile = UserProfile(
                name = displayName,
                email = legacyConfig.jellyfinEmail.takeIf { it.isNotBlank() },
                avatarInitials = initials,
                avatarColor = AvatarColor.PURPLE.key,
                pinHash = "",
                config = legacyConfig,
                settings = ProfileSettings(
                    appLanguage = sharedPrefs.getString(KEY_APP_LANGUAGE, "en") ?: "en",
                    discoveryLanguage = (sharedPrefs.getString(KEY_DISCOVERY_LANGUAGE, "en") ?: "en").lowercase(),
                    defaultStreamingRegion = (sharedPrefs.getString(KEY_DEFAULT_STREAMING_REGION, "US")
                        ?: "US").uppercase(),
                    folderSelectionEnabled = sharedPrefs.getBoolean(KEY_FOLDER_SELECTION_ENABLED, false),
                    use24HourClock = sharedPrefs.getBoolean(KEY_USE_24_HOUR_CLOCK, true),
                    useTrailerWebView = sharedPrefs.getBoolean(KEY_USE_TRAILER_WEBVIEW, false)
                )
            )
            saveProfiles(context, listOf(profile))
            setActiveProfileId(context, profile.id)
            sharedPrefs.edit(commit = true) {
                // Cleanup legacy globals after seeding the first profile settings.
                remove(KEY_APP_LANGUAGE)
                remove(KEY_DISCOVERY_LANGUAGE)
                remove(KEY_DEFAULT_STREAMING_REGION)
                remove(KEY_FOLDER_SELECTION_ENABLED)
                remove(KEY_USE_24_HOUR_CLOCK)
                remove(KEY_USE_TRAILER_WEBVIEW)
            }
            return
        }

        // Profiles exist but active id missing/invalid
        val profiles = getProfiles(context)
        if (profiles.isEmpty()) {
            // Distinguish "the stored list is genuinely empty" from "the stored list could not be
            // read". Only the first is safe to clear. Deleting a blob that merely failed to decode
            // would permanently destroy every profile and every setting in it over what may be a
            // partial write or a schema a later build could read. Leave it and bail out instead:
            // the app falls back to the legacy global keys for this run, and the data survives.
            if (decodeProfiles(profilesJson) == null) {
                Log.e(
                    "SharedPreferencesUtil",
                    "Profiles JSON present but unreadable; leaving it untouched rather than " +
                        "discarding saved profiles"
                )
                return
            }
            setActiveProfileId(context, null)
            sharedPrefs.edit(commit = true) {
                remove(KEY_PROFILES_JSON)
            }
            return
        }

        val isActiveValid = activeProfileId != null && profiles.any { it.id == activeProfileId }
        if (!isActiveValid) {
            setActiveProfileId(context, profiles.first().id)
        }

        // Upgrade migration: if legacy global setting keys are still present, move them into profile storage.
        if (hasLegacyGlobalProfileSettingKeys(sharedPrefs) && profiles.isNotEmpty()) {
            // If multiple profiles exist, legacy globals are applied to the first profile by design.
            // For a single-profile install, applying to the only profile is equivalent and preserves data.
            val firstProfileId = profiles.first().id
            val migrated = profiles.map { profile ->
                if (profile.id == firstProfileId) {
                    profile.copy(
                        settings = profile.settings.copy(
                            appLanguage = sharedPrefs.getString(KEY_APP_LANGUAGE, profile.settings.appLanguage)
                                ?: profile.settings.appLanguage,
                            discoveryLanguage = (sharedPrefs.getString(
                                KEY_DISCOVERY_LANGUAGE,
                                profile.settings.discoveryLanguage
                            ) ?: profile.settings.discoveryLanguage).lowercase(),
                            defaultStreamingRegion = (sharedPrefs.getString(
                                KEY_DEFAULT_STREAMING_REGION,
                                profile.settings.defaultStreamingRegion
                            ) ?: profile.settings.defaultStreamingRegion).uppercase(),
                            folderSelectionEnabled = sharedPrefs.getBoolean(
                                KEY_FOLDER_SELECTION_ENABLED,
                                profile.settings.folderSelectionEnabled
                            ),
                            use24HourClock = sharedPrefs.getBoolean(
                                KEY_USE_24_HOUR_CLOCK,
                                profile.settings.use24HourClock
                            ),
                            useTrailerWebView = sharedPrefs.getBoolean(
                                KEY_USE_TRAILER_WEBVIEW,
                                profile.settings.useTrailerWebView
                            )
                        ),
                        updatedAt = System.currentTimeMillis()
                    )
                } else profile
            }
            saveProfiles(context, migrated)
            sharedPrefs.edit(commit = true) {
                // Cleanup: once migrated, remove legacy global setting keys so profile storage is canonical.
                remove(KEY_APP_LANGUAGE)
                remove(KEY_DISCOVERY_LANGUAGE)
                remove(KEY_DEFAULT_STREAMING_REGION)
                remove(KEY_FOLDER_SELECTION_ENABLED)
                remove(KEY_USE_24_HOUR_CLOCK)
                remove(KEY_USE_TRAILER_WEBVIEW)
            }
        }
    }

    private fun hasLegacyGlobalProfileSettingKeys(sharedPrefs: android.content.SharedPreferences): Boolean {
        return sharedPrefs.contains(KEY_APP_LANGUAGE) ||
            sharedPrefs.contains(KEY_DISCOVERY_LANGUAGE) ||
            sharedPrefs.contains(KEY_DEFAULT_STREAMING_REGION) ||
            sharedPrefs.contains(KEY_FOLDER_SELECTION_ENABLED) ||
            sharedPrefs.contains(KEY_USE_24_HOUR_CLOCK) ||
            sharedPrefs.contains(KEY_USE_TRAILER_WEBVIEW)
    }

    fun consumeSkipProfileSelectionOnce(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = sharedPrefs.getBoolean(KEY_SKIP_PROFILE_SELECTION_ON_NEXT_MAIN, false)
        if (current) {
            sharedPrefs.edit(commit = true) {
                remove(KEY_SKIP_PROFILE_SELECTION_ON_NEXT_MAIN)
            }
        }
        return current
    }

    fun shouldSkipProfileSelectionOnce(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_SKIP_PROFILE_SELECTION_ON_NEXT_MAIN, false)
    }

    fun setSkipProfileSelectionOnce(context: Context, skip: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            if (skip) putBoolean(KEY_SKIP_PROFILE_SELECTION_ON_NEXT_MAIN, true) else remove(KEY_SKIP_PROFILE_SELECTION_ON_NEXT_MAIN)
        }
    }

    fun setProfileSelectionTargetProfileId(context: Context, profileId: String?) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            if (profileId == null) {
                remove(KEY_PROFILE_SELECTION_TARGET_PROFILE_ID)
            } else {
                putString(KEY_PROFILE_SELECTION_TARGET_PROFILE_ID, profileId)
            }
        }
    }

    fun consumeProfileSelectionTargetProfileId(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = sharedPrefs.getString(KEY_PROFILE_SELECTION_TARGET_PROFILE_ID, null)
        if (!current.isNullOrBlank()) {
            sharedPrefs.edit(commit = true) {
                remove(KEY_PROFILE_SELECTION_TARGET_PROFILE_ID)
            }
        }
        return current
    }

    fun setProfileSelectionTargetPostActivationRoute(context: Context, route: String?) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // MainActivity.onCreate calls this on every launch, before setContent. `commit()` rewrites
        // and fsyncs the whole prefs file on the main thread while the window is being added, so
        // skip it when the stored value already matches — the steady state after the first run.
        if (sharedPrefs.getString(KEY_PROFILE_SELECTION_TARGET_POST_ACTIVATION_ROUTE, null) == route) return
        sharedPrefs.edit(commit = true) {
            if (route == null) {
                remove(KEY_PROFILE_SELECTION_TARGET_POST_ACTIVATION_ROUTE)
            } else {
                putString(KEY_PROFILE_SELECTION_TARGET_POST_ACTIVATION_ROUTE, route)
            }
        }
    }

    fun consumeProfileSelectionTargetPostActivationRoute(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = sharedPrefs.getString(KEY_PROFILE_SELECTION_TARGET_POST_ACTIVATION_ROUTE, null)
        if (!current.isNullOrBlank()) {
            sharedPrefs.edit(commit = true) {
                remove(KEY_PROFILE_SELECTION_TARGET_POST_ACTIVATION_ROUTE)
            }
        }
        return current
    }

    fun setProfileSelectionCompleted(context: Context, completed: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Same as above: called unconditionally from MainActivity.onCreate, so avoid a main-thread
        // fsync when the value is unchanged.
        val unchanged = sharedPrefs.contains(KEY_PROFILE_SELECTION_COMPLETED) &&
            sharedPrefs.getBoolean(KEY_PROFILE_SELECTION_COMPLETED, false) == completed
        if (unchanged) return
        sharedPrefs.edit(commit = true) {
            putBoolean(KEY_PROFILE_SELECTION_COMPLETED, completed)
        }
    }

    fun isProfileSelectionCompleted(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_PROFILE_SELECTION_COMPLETED, true)
    }

    fun consumeForceSplashResetOnNext(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = sharedPrefs.getBoolean(KEY_FORCE_SPLASH_RESET_ON_NEXT, false)
        if (current) {
            sharedPrefs.edit(commit = true) {
                remove(KEY_FORCE_SPLASH_RESET_ON_NEXT)
            }
        }
        return current
    }

    fun setForceSplashResetOnNext(context: Context, reset: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            if (reset) putBoolean(KEY_FORCE_SPLASH_RESET_ON_NEXT, true) else remove(KEY_FORCE_SPLASH_RESET_ON_NEXT)
        }
    }

    fun getApiUrl(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_API_URL, null)
    }

    fun isFolderSelectionEnabled(context: Context): Boolean {
        val activeProfile = getActiveProfile(context)
        if (activeProfile != null) return activeProfile.settings.folderSelectionEnabled
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_FOLDER_SELECTION_ENABLED, false)
    }

    fun setFolderSelectionEnabled(context: Context, enabled: Boolean) {
        val profiles = getProfiles(context)
        val targetId = resolveSettingsTargetProfileId(profiles, getActiveProfileId(context))
        if (targetId != null) {
            val updated = profiles.map { profile ->
                if (profile.id == targetId) {
                    profile.copy(
                        settings = profile.settings.copy(folderSelectionEnabled = enabled),
                        updatedAt = System.currentTimeMillis()
                    )
                } else profile
            }
            saveProfiles(context, updated)
            return
        }
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            putBoolean(KEY_FOLDER_SELECTION_ENABLED, enabled)
        }
    }

    fun use24HourClock(context: Context): Boolean {
        val activeProfile = getActiveProfile(context)
        if (activeProfile != null) return activeProfile.settings.use24HourClock
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_USE_24_HOUR_CLOCK, true) // Default to 24-hour clock
    }

    fun setUse24HourClock(context: Context, enabled: Boolean) {
        val profiles = getProfiles(context)
        val targetId = resolveSettingsTargetProfileId(profiles, getActiveProfileId(context))
        if (targetId != null) {
            val updated = profiles.map { profile ->
                if (profile.id == targetId) {
                    profile.copy(
                        settings = profile.settings.copy(use24HourClock = enabled),
                        updatedAt = System.currentTimeMillis()
                    )
                } else profile
            }
            saveProfiles(context, updated)
            return
        }
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            putBoolean(KEY_USE_24_HOUR_CLOCK, enabled)
        }
    }

    /** Default = false (use YouTube app for trailers). When true, use in-app WebView overlay. */
    fun useTrailerWebView(context: Context): Boolean {
        val activeProfile = getActiveProfile(context)
        if (activeProfile != null) return activeProfile.settings.useTrailerWebView
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_USE_TRAILER_WEBVIEW, false)
    }

    fun setUseTrailerWebView(context: Context, useWebView: Boolean) {
        val profiles = getProfiles(context)
        val targetId = resolveSettingsTargetProfileId(profiles, getActiveProfileId(context))
        if (targetId != null) {
            val updated = profiles.map { profile ->
                if (profile.id == targetId) {
                    profile.copy(
                        settings = profile.settings.copy(useTrailerWebView = useWebView),
                        updatedAt = System.currentTimeMillis()
                    )
                } else profile
            }
            saveProfiles(context, updated)
            return
        }
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            putBoolean(KEY_USE_TRAILER_WEBVIEW, useWebView)
        }
    }

    fun setServerType(context: Context, serverType: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            putString(KEY_SERVER_TYPE, serverType)
        }
    }

    fun getServerType(context: Context): String {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_SERVER_TYPE, "UNKNOWN") ?: "UNKNOWN"
    }

    fun saveUserInfo(
        context: Context,
        userId: Int,
        displayName: String,
        permissions: Int,
        remoteAvatarUrl: String? = null
    ) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            putInt(KEY_USER_ID, userId)
            putString(KEY_USER_DISPLAY_NAME, displayName)
            putInt(KEY_USER_PERMISSIONS, permissions)
            putString(KEY_USER_REMOTE_AVATAR, remoteAvatarUrl.orEmpty())
        }
        syncActiveProfileWithServerUser(context, displayName, remoteAvatarUrl)
    }

    fun getRemoteAvatarUrl(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_USER_REMOTE_AVATAR, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Last permissions value saved by [saveUserInfo], or null when nothing is saved for [userId].
     * Used so a refreshed `auth/me` payload without a readable permissions value never degrades
     * the user to "no permissions" (which silently hides the Request button).
     */
    fun getSavedUserPermissions(context: Context, userId: Int): Int? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!sharedPrefs.contains(KEY_USER_PERMISSIONS)) return null
        if (sharedPrefs.getInt(KEY_USER_ID, Int.MIN_VALUE) != userId) return null
        return sharedPrefs.getInt(KEY_USER_PERMISSIONS, 0)
    }

    /** Last user id saved by [saveUserInfo], or null when none is saved. */
    fun getSavedUserId(context: Context): Int? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!sharedPrefs.contains(KEY_USER_ID)) return null
        return sharedPrefs.getInt(KEY_USER_ID, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
    }

    /**
     * Removes the user info saved by [saveUserInfo]. Called on connection/profile change so a
     * process restart can never seed the in-memory user from a different server's account.
     */
    fun clearUserInfo(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            remove(KEY_USER_ID)
            remove(KEY_USER_DISPLAY_NAME)
            remove(KEY_USER_PERMISSIONS)
            remove(KEY_USER_REMOTE_AVATAR)
        }
    }

    // --- Startup service caches (Radarr/Sonarr configuration) -----------------------------------
    // Persisted JSON snapshots written after each successful splash-time load, read back at
    // process start so a restore-after-process-death (e.g. returning from the external trailer
    // player) has working 4K capability and request-modal options without a full reload.

    fun saveRadarrCacheJson(context: Context, jsonValue: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_CACHED_RADARR_SERVERS, jsonValue) }
    }

    fun getRadarrCacheJson(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_RADARR_SERVERS, null)
    }

    fun saveSonarrCacheJson(context: Context, jsonValue: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_CACHED_SONARR_SERVERS, jsonValue) }
    }

    fun getSonarrCacheJson(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_SONARR_SERVERS, null)
    }

    /** Removes the persisted Radarr/Sonarr snapshots. Called alongside [clearUserInfo] on connection change. */
    fun clearServiceCaches(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            remove(KEY_CACHED_RADARR_SERVERS)
            remove(KEY_CACHED_SONARR_SERVERS)
            remove(KEY_HIDE_AVAILABLE)
            remove(KEY_HIDE_BLOCKLISTED)
        }
    }

    // --- Seerr public settings ------------------------------------------------------------------
    // "Hide Available Items" / "Hide Blocklisted Items" from GET /api/v1/settings/public. Seerr
    // applies both in its web client only, so SeerrTV has to filter for itself; cached here so
    // the filter stays synchronous and survives a restore-after-process-death. Cleared with the
    // service caches on connection/profile change so one user's visibility never leaks to another.

    fun saveServerContentVisibility(context: Context, hideAvailable: Boolean, hideBlocklisted: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_HIDE_AVAILABLE, hideAvailable)
            putBoolean(KEY_HIDE_BLOCKLISTED, hideBlocklisted)
        }
    }

    fun getServerHideAvailable(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_AVAILABLE, false)

    fun getServerHideBlocklisted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_BLOCKLISTED, false)

    /**
     * Keeps the active local profile name, initials, and remote avatar aligned with [auth/me] after login.
     */
    private fun syncActiveProfileWithServerUser(
        context: Context,
        displayName: String,
        remoteAvatarUrl: String?
    ) {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return
        val profilesJson =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PROFILES_JSON, null)
                ?: return
        val profiles = runCatching { json.decodeFromString<List<UserProfile>>(profilesJson) }
            .getOrElse { return }
        if (profiles.isEmpty()) return
        val activeId = resolveIdentitySyncTargetProfileId(
            profiles = profiles,
            activeId = getActiveProfileId(context),
            pendingNewProfileCreation = isPendingNewProfileCreation(context)
        ) ?: return

        val emailFromProfile: (UserProfile) -> String? = { p ->
            p.config.jellyfinEmail.takeIf { it.isNotBlank() }
                ?: p.config.username.takeIf { it.isNotBlank() && it.contains('@') }
        }
        val activeProfile = profiles.firstOrNull { it.id == activeId } ?: return
        val emailCandidate = emailFromProfile(activeProfile)

        val otherInitials = profiles.filter { it.id != activeId }.map { it.avatarInitials }.toSet()
        val resolvedInitials = resolveUniqueInitials(
            desiredInitials = generateInitialsFromNameOrEmail(trimmed, emailCandidate),
            existingInitials = otherInitials,
            seed = trimmed
        )

        val updated = profiles.map { p ->
            if (p.id == activeId) {
                p.copy(
                    name = trimmed,
                    avatarInitials = resolvedInitials,
                    remoteAvatarUrl = remoteAvatarUrl,
                    updatedAt = System.currentTimeMillis()
                )
            } else p
        }
        saveProfiles(context, updated)
    }

    fun getUserDisplayName(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_USER_DISPLAY_NAME, null)
    }

    fun getOrGeneratePlexClientId(context: Context): String {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var clientId = sharedPrefs.getString(KEY_PLEX_CLIENT_ID, null)
        
        if (clientId.isNullOrBlank()) {
            // Generate a new UUID if we don't have one
            clientId = java.util.UUID.randomUUID().toString()
            // Store it for future use
            sharedPrefs.edit(commit = true) {
                putString(KEY_PLEX_CLIENT_ID, clientId)
            }
        }
        
        return clientId
    }

    fun saveMediaServerType(context: Context, mediaServerType: MediaServerType) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previous = sharedPrefs.getString(KEY_MEDIA_SERVER_TYPE, null)
        sharedPrefs.edit(commit = true) {
            putString(KEY_MEDIA_SERVER_TYPE, mediaServerType.name)
            // The detected type (learned from playback fallbacks) is only meaningful for the
            // server it was learned against; drop it when the configured type changes.
            if (previous != mediaServerType.name) {
                remove(KEY_DETECTED_MEDIA_SERVER_TYPE)
            }
        }
    }

    fun getMediaServerType(context: Context): MediaServerType {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val typeString = sharedPrefs.getString(KEY_MEDIA_SERVER_TYPE, MediaServerType.NOT_CONFIGURED.name)
        return try {
            MediaServerType.valueOf(typeString ?: MediaServerType.NOT_CONFIGURED.name)
        } catch (e: IllegalArgumentException) {
            MediaServerType.NOT_CONFIGURED
        }
    }

    /**
     * Save the detected media server type (learned from successful playback attempts)
     */
    fun saveDetectedMediaServerType(context: Context, mediaServerType: MediaServerType) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            putString(KEY_DETECTED_MEDIA_SERVER_TYPE, mediaServerType.name)
        }
        Log.d("SharedPreferencesUtil", "Saved detected media server type: $mediaServerType")
    }

    /**
     * Get the detected media server type (learned from successful playback attempts)
     * Returns null if not yet detected
     */
    fun getDetectedMediaServerType(context: Context): MediaServerType? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val typeString = sharedPrefs.getString(KEY_DETECTED_MEDIA_SERVER_TYPE, null)
        return try {
            typeString?.let { MediaServerType.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Get the selected discovery language. Defaults to "en" if not set.
     * Always returns lowercase language code for consistency.
     */
    fun getDiscoveryLanguage(context: Context): String {
        val activeProfile = getActiveProfile(context)
        if (activeProfile != null) return activeProfile.settings.discoveryLanguage.lowercase()
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (sharedPrefs.getString(KEY_DISCOVERY_LANGUAGE, "en") ?: "en").lowercase()
    }

    /**
     * Set the selected discovery language.
     * @param value One of: en, de, es, fr, ja, nl, pt, zh (stored as lowercase)
     */
    fun setDiscoveryLanguage(context: Context, value: String) {
        val normalized = value.lowercase()
        val profiles = getProfiles(context)
        val targetId = resolveSettingsTargetProfileId(profiles, getActiveProfileId(context))
        if (targetId != null) {
            val updated = profiles.map { profile ->
                if (profile.id == targetId) {
                    profile.copy(
                        settings = profile.settings.copy(discoveryLanguage = normalized),
                        updatedAt = System.currentTimeMillis()
                    )
                } else profile
            }
            saveProfiles(context, updated)
            return
        }
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            putString(KEY_DISCOVERY_LANGUAGE, normalized)
        }
    }

    /**
     * Get the default streaming region. Defaults to "US" if not set.
     * Returns uppercase region code (ISO 3166-1) for consistency.
     */
    fun getDefaultStreamingRegion(context: Context): String {
        val activeProfile = getActiveProfile(context)
        if (activeProfile != null) return activeProfile.settings.defaultStreamingRegion.uppercase()
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (sharedPrefs.getString(KEY_DEFAULT_STREAMING_REGION, "US") ?: "US").uppercase()
    }

    /**
     * Set the default streaming region.
     * @param value ISO 3166-1 region code (e.g., "US", "CA", "GB")
     */
    fun setDefaultStreamingRegion(context: Context, value: String) {
        val normalized = value.uppercase()
        val profiles = getProfiles(context)
        val targetId = resolveSettingsTargetProfileId(profiles, getActiveProfileId(context))
        if (targetId != null) {
            val updated = profiles.map { profile ->
                if (profile.id == targetId) {
                    profile.copy(
                        settings = profile.settings.copy(defaultStreamingRegion = normalized),
                        updatedAt = System.currentTimeMillis()
                    )
                } else profile
            }
            saveProfiles(context, updated)
            return
        }
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit(commit = true) {
            putString(KEY_DEFAULT_STREAMING_REGION, normalized)
        }
    }

    // -------------------------------------------------------------------------
    // Custom slider metadata cache
    //
    // Custom sliders fetched from GET /api/v1/settings/discover are cached here
    // so their titles can be resolved without an additional network call.
    //
    // Each slider is stored under "custom_slider_meta_{custom_id}" where
    // custom_id is "custom_{server_slider_id}" (e.g. "custom_7").
    // The value is a pipe-delimited string: "{title}|{type_int}|{data}"
    // -------------------------------------------------------------------------

    private const val KEY_CUSTOM_SLIDER_META_PREFIX = "custom_slider_meta_"

    fun saveCustomSliderMeta(context: Context, categoryId: String, title: String, typeValue: Int, data: String?) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = "${title}|${typeValue}|${data ?: ""}"
        sharedPrefs.edit(commit = true) {
            putString(KEY_CUSTOM_SLIDER_META_PREFIX + categoryId, encoded)
        }
    }

    fun getCustomSliderTitle(context: Context, categoryId: String): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = sharedPrefs.getString(KEY_CUSTOM_SLIDER_META_PREFIX + categoryId, null)
            ?: return null
        return stored.substringBefore('|').takeIf { it.isNotBlank() }
    }

}