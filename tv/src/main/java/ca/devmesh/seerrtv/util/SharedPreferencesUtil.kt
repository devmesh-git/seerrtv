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
    private const val KEY_HOME_CATEGORY_ORDER = "home_category_order"
    private const val KEY_HOME_CATEGORY_ENABLED_PREFIX = "home_category_enabled_"

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

    /**
     * Home screen category configuration
     *
     * Category IDs are stable string identifiers such as:
     * - "recently_added", "recent_requests", "trending", "popular_movies",
     *   "upcoming_movies", "popular_series", "upcoming_series",
     *   "movie_genres", "series_genres", "studios", "networks",
     *   "watchlist", "apps".
     *
     * Ordering is stored as a comma-separated list of IDs.
     * Enabled flags are stored as per-category booleans, with Apps always enabled.
     */

    private fun getDefaultHomeCategoryOrderInternal(isLauncherBuild: Boolean): List<String> {
        val base = listOf(
            "recently_added",   // Recently Added
            "recent_requests",  // Recent Requests
            "watchlist",        // Your Wishlist
            "trending",         // Trending
            "popular_movies",   // Popular Movies
            "movie_genres",     // Movie Genres
            "upcoming_movies",  // Upcoming Movies
            "studios",          // Studios
            "popular_series",   // Popular Series
            "series_genres",    // Series Genres
            "upcoming_series",  // Upcoming Series
            "networks"          // Networks
        )

        return if (isLauncherBuild) {
            // Default: show Apps at the top for launcher builds
            listOf("apps") + base
        } else {
            base
        }
    }

    fun getDefaultHomeCategoryOrder(isLauncherBuild: Boolean): List<String> {
        return getDefaultHomeCategoryOrderInternal(isLauncherBuild)
    }

    fun getHomeCategoryOrder(context: Context, isLauncherBuild: Boolean): List<String> {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = sharedPrefs.getString(KEY_HOME_CATEGORY_ORDER, null)
        val defaultOrder = getDefaultHomeCategoryOrderInternal(isLauncherBuild)

        if (stored.isNullOrBlank()) {
            return defaultOrder
        }

        val knownIds = defaultOrder.toSet()
        val fromPrefs = stored.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Keep known built-in IDs AND any "custom_*" IDs (server-defined sliders), preserving order
        val cleaned = fromPrefs.filter { it in knownIds || it.startsWith("custom_") }.toMutableList()

        // Append any missing built-in IDs that were added in a later version
        for (id in defaultOrder) {
            if (!cleaned.contains(id)) {
                cleaned.add(id)
            }
        }

        // Ensure Apps is present for launcher builds and absent otherwise
        if (isLauncherBuild) {
            if (!cleaned.contains("apps")) {
                cleaned.add(0, "apps")
            }
        } else {
            cleaned.removeAll { it == "apps" }
        }

        return cleaned
    }

    fun setHomeCategoryOrder(context: Context, order: List<String>) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = order.joinToString(",")
        with(sharedPrefs.edit()) {
            putString(KEY_HOME_CATEGORY_ORDER, value)
            commit()
        }
    }

    fun isHomeCategoryEnabled(context: Context, id: String, isLauncherBuild: Boolean): Boolean {
        // Apps row is always enabled in launcher builds and does not exist otherwise
        if (id == "apps") {
            return isLauncherBuild
        }

        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Default to enabled when not explicitly disabled
        return sharedPrefs.getBoolean(KEY_HOME_CATEGORY_ENABLED_PREFIX + id, true)
    }

    fun setHomeCategoryEnabled(context: Context, id: String, enabled: Boolean, isLauncherBuild: Boolean) {
        // Prevent disabling Apps row
        if (id == "apps" && isLauncherBuild) {
            return
        }

        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean(KEY_HOME_CATEGORY_ENABLED_PREFIX + id, enabled)
            commit()
        }
    }

    fun resetHomeCategoryConfigToDefaults(context: Context, isLauncherBuild: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultOrder = getDefaultHomeCategoryOrderInternal(isLauncherBuild)

        with(sharedPrefs.edit()) {
            // Reset order
            putString(KEY_HOME_CATEGORY_ORDER, defaultOrder.joinToString(","))

            // Clear explicit enabled flags so defaults (all true, Apps forced true) apply
            defaultOrder.forEach { id ->
                remove(KEY_HOME_CATEGORY_ENABLED_PREFIX + id)
            }

            commit()
        }
    }

    // -------------------------------------------------------------------------
    // Custom slider metadata cache
    //
    // Custom sliders fetched from GET /api/v1/settings/discover are cached here
    // so their titles can be resolved without an additional network call (e.g. in
    // the Settings > Home Categories screen).
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

    /**
     * Merges server-returned discover sliders into the locally stored category order.
     *
     * - Caches title/type/data for every custom slider so labels resolve offline.
     * - Inserts new custom sliders at their server-specified position.
     * - Prunes custom IDs that no longer appear in the server response.
     * - Respects any reordering the user has already done locally.
     *
     * Built-in sliders (isBuiltIn=true) are NOT inserted here because their
     * category IDs ("recently_added", "trending", etc.) are already hardcoded in
     * getDefaultHomeCategoryOrderInternal() and always present in the stored order.
     */
    fun mergeServerSlidersIntoOrder(
        context: Context,
        sliders: List<ca.devmesh.seerrtv.viewmodel.DiscoverSlider>,
        isLauncherBuild: Boolean
    ) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Cache metadata for all custom sliders and collect their IDs
        val serverCustomIds = mutableSetOf<String>()
        val sortedSliders = sliders.sortedBy { it.order }

        for (slider in sortedSliders) {
            if (!slider.isBuiltIn) {
                val categoryId = slider.categoryId
                serverCustomIds.add(categoryId)
                saveCustomSliderMeta(
                    context,
                    categoryId,
                    slider.title ?: categoryId,
                    slider.type.value,
                    slider.data
                )
            }
        }

        // Load existing order from prefs (may already contain some custom IDs from a prior run)
        val existingOrder = getHomeCategoryOrder(context, isLauncherBuild).toMutableList()

        // Find custom IDs already in the stored order
        val existingCustomIds = existingOrder.filter { it.startsWith("custom_") }.toSet()

        // Determine new custom IDs not yet in the stored order
        val newCustomIds = serverCustomIds - existingCustomIds

        if (newCustomIds.isNotEmpty()) {
            // Build a position map from the server order so we can insert at the right spot.
            // We interleave new custom sliders relative to the built-in slider they follow.
            val serverOrderMap = sortedSliders.mapIndexed { idx, s -> s.categoryId to idx }.toMap()

            for (newId in newCustomIds.sortedBy { serverOrderMap[it] ?: Int.MAX_VALUE }) {
                // Find the server slider for this ID
                val newSlider = sliders.firstOrNull { it.categoryId == newId } ?: continue

                // Find the server slider that comes just before newSlider in server order
                val predecessorId = sortedSliders
                    .filter { it.order < newSlider.order }
                    .maxByOrNull { it.order }
                    ?.categoryId

                val insertIndex = if (predecessorId != null) {
                    val predIndex = existingOrder.indexOf(predecessorId)
                    if (predIndex >= 0) predIndex + 1 else existingOrder.size
                } else {
                    existingOrder.size
                }

                existingOrder.add(insertIndex.coerceAtMost(existingOrder.size), newId)
            }
        }

        // Prune custom IDs that the server no longer returns
        val pruned = existingOrder.filter { id ->
            !id.startsWith("custom_") || id in serverCustomIds
        }

        // Remove cached meta for pruned sliders
        (existingCustomIds - serverCustomIds).forEach { removeCustomSliderMeta(context, it) }

        // Persist the merged order
        with(sharedPrefs.edit()) {
            putString(KEY_HOME_CATEGORY_ORDER, pruned.joinToString(","))
            commit()
        }
    }
}