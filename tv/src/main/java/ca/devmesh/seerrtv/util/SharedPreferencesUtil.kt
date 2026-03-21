package ca.devmesh.seerrtv.util

import android.content.Context
import android.util.Log
import ca.devmesh.seerrtv.data.SeerrApiService.SeerrConfig
import ca.devmesh.seerrtv.model.AuthType
import ca.devmesh.seerrtv.model.AvatarColor
import ca.devmesh.seerrtv.model.MediaServerType
import ca.devmesh.seerrtv.model.UserProfile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_DISPLAY_NAME = "user_display_name"
    private const val KEY_PLEX_CLIENT_ID = "plex_client_id"
    private const val KEY_PLEX_AUTH_TOKEN = "plex_auth_token"
    private const val KEY_DISCOVERY_LANGUAGE = "discovery_language"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val KEY_DEFAULT_STREAMING_REGION = "default_streaming_region"
    private const val KEY_USE_TRAILER_WEBVIEW = "use_trailer_webview"
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
    // Supported app languages
    val SUPPORTED_APP_LANGUAGES = listOf("en", "de", "es", "fr", "ja", "nl", "pt", "zh")

    fun getAppLanguage(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_APP_LANGUAGE, null)
    }

    fun setAppLanguage(context: Context, language: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString(KEY_APP_LANGUAGE, language)
            commit()
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
        val systemLocale = context.resources.configuration.locales[0]
        val systemLanguage = systemLocale.language.lowercase()
        return if (systemLanguage == "zh" || SUPPORTED_APP_LANGUAGES.contains(systemLanguage)) {
            systemLanguage
        } else {
            "en"
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
        with(sharedPrefs.edit()) {
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
            commit()
        }

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
                val nameCandidate = when {
                    usernameCandidate.isNullOrBlank().not() && usernameCandidate.contains('@') ->
                        usernameCandidate.substringBefore('@')
                    usernameCandidate.isNullOrBlank().not() ->
                        usernameCandidate
                    !emailCandidate.isNullOrBlank() -> emailCandidate.substringBefore('@')
                    else -> activeProfile?.name ?: normalizedHostname
                }

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
            val displayName =
                getUserDisplayName(context)
                    ?: config.username.takeIf { it.isNotBlank() }
                    ?: config.jellyfinEmail.takeIf { it.isNotBlank() }
                    ?: normalizedHostname

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
                config = embeddedConfig
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
            with(sharedPrefs.edit()) {
                putString(KEY_HOSTNAME, normalizedHostname)
                commit()
            }
        }
        
        // Normalize Jellyfin hostname to clean up any bad data (strip port since it's in a separate field)
        var jellyfinHostname = sharedPrefs.getString(KEY_JELLYFIN_HOSTNAME, "") ?: ""
        val normalizedJellyfinHostname = normalizeHostname(jellyfinHostname, stripPort = true)
        if (normalizedJellyfinHostname != jellyfinHostname && jellyfinHostname.isNotEmpty()) {
            Log.d("SharedPreferencesUtil", "Cleaning Jellyfin hostname: '$jellyfinHostname' -> '$normalizedJellyfinHostname'")
            // Save cleaned Jellyfin hostname back to storage
            with(sharedPrefs.edit()) {
                putString(KEY_JELLYFIN_HOSTNAME, normalizedJellyfinHostname)
                commit()
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
        with(sharedPrefs.edit()) {
            clear()
            commit()
        }
    }

    // -------------------------------------------------------------------------
    // Profile storage
    // -------------------------------------------------------------------------

    fun getProfiles(context: Context): List<UserProfile> {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sharedPrefs.getString(KEY_PROFILES_JSON, null) ?: return emptyList()
        return runCatching { this.json.decodeFromString<List<UserProfile>>(json) }
            .getOrElse { error ->
                Log.e("SharedPreferencesUtil", "Failed to decode profiles JSON", error)
                emptyList()
            }
    }

    fun saveProfiles(context: Context, profiles: List<UserProfile>) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = this.json.encodeToString(profiles)
        with(sharedPrefs.edit()) {
            putString(KEY_PROFILES_JSON, json)
            commit()
        }
    }

    fun getActiveProfileId(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_ACTIVE_PROFILE_ID, null)
    }

    fun setActiveProfileId(context: Context, profileId: String?) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            if (profileId == null) {
                remove(KEY_ACTIVE_PROFILE_ID)
            } else {
                putString(KEY_ACTIVE_PROFILE_ID, profileId)
            }
            commit()
        }
    }

    fun getActiveProfile(context: Context): UserProfile? {
        val profiles = getProfiles(context)
        if (profiles.isEmpty()) return null
        val activeId = getActiveProfileId(context)
        return profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    }

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
        with(sharedPrefs.edit()) {
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
            commit()
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
            with(sharedPrefs.edit()) {
                remove(KEY_PROFILES_JSON)
                remove(KEY_ACTIVE_PROFILE_ID)
                commit()
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
                config = legacyConfig
            )
            saveProfiles(context, listOf(profile))
            setActiveProfileId(context, profile.id)
            return
        }

        // Profiles exist but active id missing/invalid
        val profiles = getProfiles(context)
        if (profiles.isEmpty()) {
            setActiveProfileId(context, null)
            with(sharedPrefs.edit()) {
                remove(KEY_PROFILES_JSON)
                commit()
            }
            return
        }

        val isActiveValid = activeProfileId != null && profiles.any { it.id == activeProfileId }
        if (!isActiveValid) {
            setActiveProfileId(context, profiles.first().id)
        }
    }

    fun consumeSkipProfileSelectionOnce(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = sharedPrefs.getBoolean(KEY_SKIP_PROFILE_SELECTION_ON_NEXT_MAIN, false)
        if (current) {
            with(sharedPrefs.edit()) {
                remove(KEY_SKIP_PROFILE_SELECTION_ON_NEXT_MAIN)
                commit()
            }
        }
        return current
    }

    fun setSkipProfileSelectionOnce(context: Context, skip: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            if (skip) putBoolean(KEY_SKIP_PROFILE_SELECTION_ON_NEXT_MAIN, true) else remove(KEY_SKIP_PROFILE_SELECTION_ON_NEXT_MAIN)
            commit()
        }
    }

    fun setProfileSelectionTargetProfileId(context: Context, profileId: String?) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            if (profileId == null) {
                remove(KEY_PROFILE_SELECTION_TARGET_PROFILE_ID)
            } else {
                putString(KEY_PROFILE_SELECTION_TARGET_PROFILE_ID, profileId)
            }
            commit()
        }
    }

    fun consumeProfileSelectionTargetProfileId(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = sharedPrefs.getString(KEY_PROFILE_SELECTION_TARGET_PROFILE_ID, null)
        if (!current.isNullOrBlank()) {
            with(sharedPrefs.edit()) {
                remove(KEY_PROFILE_SELECTION_TARGET_PROFILE_ID)
                commit()
            }
        }
        return current
    }

    fun setProfileSelectionTargetPostActivationRoute(context: Context, route: String?) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            if (route == null) {
                remove(KEY_PROFILE_SELECTION_TARGET_POST_ACTIVATION_ROUTE)
            } else {
                putString(KEY_PROFILE_SELECTION_TARGET_POST_ACTIVATION_ROUTE, route)
            }
            commit()
        }
    }

    fun consumeProfileSelectionTargetPostActivationRoute(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = sharedPrefs.getString(KEY_PROFILE_SELECTION_TARGET_POST_ACTIVATION_ROUTE, null)
        if (!current.isNullOrBlank()) {
            with(sharedPrefs.edit()) {
                remove(KEY_PROFILE_SELECTION_TARGET_POST_ACTIVATION_ROUTE)
                commit()
            }
        }
        return current
    }

    fun setProfileSelectionCompleted(context: Context, completed: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean(KEY_PROFILE_SELECTION_COMPLETED, completed)
            commit()
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
            with(sharedPrefs.edit()) {
                remove(KEY_FORCE_SPLASH_RESET_ON_NEXT)
                commit()
            }
        }
        return current
    }

    fun setForceSplashResetOnNext(context: Context, reset: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            if (reset) putBoolean(KEY_FORCE_SPLASH_RESET_ON_NEXT, true) else remove(KEY_FORCE_SPLASH_RESET_ON_NEXT)
            commit()
        }
    }

    fun getApiUrl(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_API_URL, null)
    }

    fun isFolderSelectionEnabled(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_FOLDER_SELECTION_ENABLED, false)
    }

    fun setFolderSelectionEnabled(context: Context, enabled: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean(KEY_FOLDER_SELECTION_ENABLED, enabled)
            commit()
        }
    }

    fun use24HourClock(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_USE_24_HOUR_CLOCK, true) // Default to 24-hour clock
    }

    fun setUse24HourClock(context: Context, enabled: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean(KEY_USE_24_HOUR_CLOCK, enabled)
            commit()
        }
    }

    /** Default = false (use YouTube app for trailers). When true, use in-app WebView overlay. */
    fun useTrailerWebView(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_USE_TRAILER_WEBVIEW, false)
    }

    fun setUseTrailerWebView(context: Context, useWebView: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean(KEY_USE_TRAILER_WEBVIEW, useWebView)
            commit()
        }
    }

    fun setServerType(context: Context, serverType: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString(KEY_SERVER_TYPE, serverType)
            commit()
        }
    }

    fun getServerType(context: Context): String {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(KEY_SERVER_TYPE, "UNKNOWN") ?: "UNKNOWN"
    }

    fun saveUserInfo(context: Context, userId: Int, displayName: String, permissions: Int) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putInt(KEY_USER_ID, userId)
            putString(KEY_USER_DISPLAY_NAME, displayName)
            putInt(KEY_USER_PERMISSIONS, permissions)
            commit()
        }
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
            with(sharedPrefs.edit()) {
                putString(KEY_PLEX_CLIENT_ID, clientId)
                commit()
            }
        }
        
        return clientId
    }

    fun saveMediaServerType(context: Context, mediaServerType: MediaServerType) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString(KEY_MEDIA_SERVER_TYPE, mediaServerType.name)
            commit()
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
        with(sharedPrefs.edit()) {
            putString(KEY_DETECTED_MEDIA_SERVER_TYPE, mediaServerType.name)
            commit()
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
     * Clear the detected media server type (called during reconfiguration)
     */
    fun clearDetectedMediaServerType(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            remove(KEY_DETECTED_MEDIA_SERVER_TYPE)
            commit()
        }
        Log.d("SharedPreferencesUtil", "Cleared detected media server type")
    }

    /**
     * Get the selected discovery language. Defaults to "en" if not set.
     * Always returns lowercase language code for consistency.
     */
    fun getDiscoveryLanguage(context: Context): String {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (sharedPrefs.getString(KEY_DISCOVERY_LANGUAGE, "en") ?: "en").lowercase()
    }

    /**
     * Set the selected discovery language.
     * @param value One of: en, de, es, fr, ja, nl, pt, zh (stored as lowercase)
     */
    fun setDiscoveryLanguage(context: Context, value: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString(KEY_DISCOVERY_LANGUAGE, value.lowercase())
            commit()
        }
    }

    /**
     * Get the default streaming region. Defaults to "US" if not set.
     * Returns uppercase region code (ISO 3166-1) for consistency.
     */
    fun getDefaultStreamingRegion(context: Context): String {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (sharedPrefs.getString(KEY_DEFAULT_STREAMING_REGION, "US") ?: "US").uppercase()
    }

    /**
     * Set the default streaming region.
     * @param value ISO 3166-1 region code (e.g., "US", "CA", "GB")
     */
    fun setDefaultStreamingRegion(context: Context, value: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString(KEY_DEFAULT_STREAMING_REGION, value.uppercase())
            commit()
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
        with(sharedPrefs.edit()) {
            putString(KEY_CUSTOM_SLIDER_META_PREFIX + categoryId, encoded)
            commit()
        }
    }

    fun getCustomSliderTitle(context: Context, categoryId: String): String? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = sharedPrefs.getString(KEY_CUSTOM_SLIDER_META_PREFIX + categoryId, null)
            ?: return null
        return stored.substringBefore('|').takeIf { it.isNotBlank() }
    }

    fun getCustomSliderMeta(context: Context, categoryId: String): Triple<String, Int, String?>? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = sharedPrefs.getString(KEY_CUSTOM_SLIDER_META_PREFIX + categoryId, null)
            ?: return null
        val parts = stored.split('|')
        if (parts.size < 3) return null
        val title = parts[0]
        val typeValue = parts[1].toIntOrNull() ?: return null
        val data = parts[2].takeIf { it.isNotBlank() }
        return Triple(title, typeValue, data)
    }

    private fun removeCustomSliderMeta(context: Context, categoryId: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            remove(KEY_CUSTOM_SLIDER_META_PREFIX + categoryId)
            commit()
        }
    }

}