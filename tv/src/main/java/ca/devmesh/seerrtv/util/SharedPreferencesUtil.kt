package ca.devmesh.seerrtv.util

import android.content.Context
import android.util.Log
import ca.devmesh.seerrtv.data.SeerrApiService.SeerrConfig
import ca.devmesh.seerrtv.model.AuthType
import ca.devmesh.seerrtv.model.MediaServerType

object SharedPreferencesUtil {
    private const val PREFS_NAME = "SeerrTVPrefs"
    
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
    }

    fun getConfig(context: Context): SeerrConfig? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
}