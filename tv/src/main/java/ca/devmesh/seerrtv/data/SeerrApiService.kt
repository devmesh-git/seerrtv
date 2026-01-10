package ca.devmesh.seerrtv.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.ui.text.intl.Locale
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.model.AuthMeResponse
import ca.devmesh.seerrtv.model.AuthType
import ca.devmesh.seerrtv.model.BrowseModels
import ca.devmesh.seerrtv.model.CombinedCredits
import ca.devmesh.seerrtv.model.MediaType
import ca.devmesh.seerrtv.model.SonarrLookupResult
import ca.devmesh.seerrtv.model.Discover
import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.MediaRequestBody
import ca.devmesh.seerrtv.model.MediaServerType
import ca.devmesh.seerrtv.model.Movie
import ca.devmesh.seerrtv.model.PaginatedMediaResponse
import ca.devmesh.seerrtv.model.Person
import ca.devmesh.seerrtv.model.PersonDetails
import ca.devmesh.seerrtv.model.Radarr
import ca.devmesh.seerrtv.model.RadarrResult
import ca.devmesh.seerrtv.model.RadarrServerInfo
import ca.devmesh.seerrtv.model.RadarrsResponse
import ca.devmesh.seerrtv.model.RatingsCombinedResponse
import ca.devmesh.seerrtv.model.RatingsFlatResponse
import ca.devmesh.seerrtv.model.RatingsResponse
import ca.devmesh.seerrtv.model.Request
import ca.devmesh.seerrtv.model.RequestResponse
import ca.devmesh.seerrtv.model.RottenTomatoesRating
import ca.devmesh.seerrtv.model.SearchResponse
import ca.devmesh.seerrtv.model.SearchResult
import ca.devmesh.seerrtv.model.SimilarMediaResponse
import ca.devmesh.seerrtv.model.Sonarr
import ca.devmesh.seerrtv.model.SonarrResult
import ca.devmesh.seerrtv.model.SonarrServerInfo
import ca.devmesh.seerrtv.model.TV
import ca.devmesh.seerrtv.model.Region
import ca.devmesh.seerrtv.model.Language
import ca.devmesh.seerrtv.model.Keyword
import ca.devmesh.seerrtv.model.ContentRating
import ca.devmesh.seerrtv.model.Provider
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.Serializable
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient

object TrustAllCerts {
    val trustAllCerts = arrayOf<X509TrustManager>(@SuppressLint("CustomX509TrustManager")
    object : X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    fun createSSLSocketFactory(): SSLSocketFactory? = try {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        sslContext.socketFactory
    } catch (e: Exception) {
        throw RuntimeException("Failed to create SSL socket factory", e)
    }
}

sealed class ApiResult<out T> {
    data class Success<out T>(
        val data: T,
        val paginationInfo: PaginationInfo? = null
    ) : ApiResult<T>()
    data class Error(
        val exception: Exception, 
        val statusCode: Int? = null
    ) : ApiResult<Nothing>()
    class Loading : ApiResult<Nothing>()
}

data class PaginationInfo(
    val currentPage: Int,
    val totalPages: Int,
    val totalResults: Int,
    val hasMorePages: Boolean = currentPage < totalPages,
    val isPaginated: Boolean = true
)

internal data class MutablePaginationInfo(
    var currentPage: Int = 1,
    var totalPages: Int = 1,
    var totalResults: Int = 0,
    var hasMorePages: Boolean = currentPage < totalPages,
    var isPaginated: Boolean = true
) {
    internal fun toImmutable() = PaginationInfo(
        currentPage = currentPage,
        totalPages = totalPages,
        totalResults = totalResults,
        hasMorePages = hasMorePages,
        isPaginated = isPaginated
    )
}

class SeerrApiService @Inject constructor(
    private var config: SeerrConfig,
    private val context: Context
) {
    private data class AuthToken(
        val token: String,
        val expiresAt: Instant
    )

    data class UserInfo(
        val id: Int,
        val displayName: String,
        val permissions: Int
    )

    private var currentAuthToken: AuthToken? = null
    private var currentUserInfo: UserInfo? = null
    private var cachedRadarrData: RadarrServerInfo? = null
    private var cachedSonarrData: SonarrServerInfo? = null

    private var apiUrl = buildApiUrl(config)

    private val paginationStates = mutableMapOf<String, MutablePaginationInfo>()
    
    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }

    internal fun getOrCreatePaginationState(endpoint: String): MutablePaginationInfo {
        return paginationStates.getOrPut(endpoint) { MutablePaginationInfo() }
    }

    fun resetPaginationState(endpoint: String) {
        paginationStates[endpoint] = MutablePaginationInfo()
    }

    private fun buildApiUrl(config: SeerrConfig): String {
        // Normalize hostname defensively to handle any edge cases (don't strip port for main Seerr hostname)
        val normalizedHostname = SharedPreferencesUtil.normalizeHostname(config.hostname, stripPort = false)
        // Construct URL and normalize any double slashes
        val url = "${config.protocol}://${normalizedHostname}/api/v1"
            .replace(Regex("([^:])//+"), "$1/") // Replace multiple slashes with single slash, but not after :
        
        return url.also {
            require(it.isNotBlank()) { "API URL must not be blank" }
        }
    }

    fun getCurrentUserInfo(): UserInfo? = currentUserInfo

    fun getAuthType(): AuthType = config.getAuthType()

    @Serializable
    data class SetupIdResponse(val id: String)

    @Serializable
    data class GenreResponse(
        val id: Int,
        val name: String,
        val backdrops: List<String>
    )

    /**
     * Simplified company/studio model for search results.
     * The /search/company endpoint returns minimal fields.
     */
    @Serializable
    data class CompanySearchResult(
        val id: Int,
        val name: String,
        @SerialName("logo_path") val logoPath: String? = null,
        @SerialName("origin_country") val originCountry: String? = null
    )

    @Serializable
    data class StudioResponse(
        val id: Int,
        val name: String,
        val originCountry: String,
        val description: String,
        val headquarters: String,
        val homepage: String,
        val logoPath: String
    )

    @Serializable
    data class NetworkResponse(
        val id: Int,
        val name: String,
        val originCountry: String,
        val headquarters: String,
        val homepage: String,
        val logoPath: String
    )

    @Serializable
    data class SeerrConfig(
        val protocol: String,
        val hostname: String,
        @SerialName("cloudflare_enabled") val cloudflareEnabled: Boolean = false,
        @SerialName("cf_client_id") val cfClientId: String = "",
        @SerialName("cf_client_secret") val cfClientSecret: String = "",
        @SerialName("auth_type") val authType: String,
        @SerialName("api_key") val apiKey: String = "",
        val username: String = "",
        val password: String = "",
        @SerialName("jellyfin_hostname") val jellyfinHostname: String = "",
        @SerialName("jellyfin_port") val jellyfinPort: Int = 8096,
        @SerialName("jellyfin_use_ssl") val jellyfinUseSsl: Boolean = false,
        @SerialName("jellyfin_url_base") val jellyfinUrlBase: String = "/",
        @SerialName("jellyfin_email") val jellyfinEmail: String = "",
        @SerialName("plex_client_id") val plexClientId: String = "",
        @SerialName("plex_auth_token") val plexAuthToken: String = "",
        @SerialName("is_submitted") val isSubmitted: Boolean,
        @SerialName("created_at") val createdAt: String
    ) {
        fun getAuthType(): AuthType = AuthType.entries.find { it.type == authType }
            ?: AuthType.ApiKey // Fallback to ApiKey if invalid authType is found to prevent crashes
    }

    @Serializable
    data class OverseerrApiResponse(
        val api: String,
        val version: String
    )

    @Serializable
    data class AuthRequest(
        val email: String,
        val password: String
    )

    sealed class ApiValidationResult {
        data class Success(val serverType: ServerType) : ApiValidationResult()
        data class Error(val message: String, val code: Int? = null) : ApiValidationResult()
        data class CloudflareRequired(val message: String) : ApiValidationResult()
    }

    enum class ServerType {
        OVERSEERR,
        JELLYSEERR,
        SEERR,
        UNKNOWN
    }

    private var serverType: ServerType = ServerType.UNKNOWN

    @Serializable
    data class JellyfinTestRequest(
        val username: String = "test",
        val password: String = "test",
        val hostname: String = "test"
    )

    @Serializable
    data class JellyfinAuthRequest(
        val username: String,
        val password: String,
        val hostname: String,
        val port: Int,
        val useSsl: Boolean,
        @SerialName("urlBase")
        val urlBase: String,
        @SerialName("email")
        val email: String
    )

    @Serializable
    data class EmbyAuthRequest(
        val username: String,
        val password: String
    )

    @Serializable
    data class PlexPinResponse(
        val id: String,
        val code: String,
        val expiresAt: String,
        val clientIdentifier: String
    )

    @Serializable
    data class PlexAuthResponse(
        val id: String,
        val code: String,
        val authToken: String = "",
        val clientIdentifier: String
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        serializersModule = SerializersModule {
            polymorphic(SearchResult::class) {
                subclass(Movie::class)
                subclass(TV::class)
                subclass(Person::class)
            }
        }
    }

    private var client = createHttpClient()

    // Cache for all genres data
    private var cachedMovieGenres: List<GenreResponse>? = null
    private var cachedTVGenres: List<GenreResponse>? = null
    
    // Predefined lists of studio and network IDs from requirements
    private val studioIds = listOf(2, 127928, 34, 174, 33, 4, 3, 521, 420, 9993, 41077)
    private val networkIds = listOf(213, 2739, 1024, 2552, 453, 49, 4353, 2, 19, 359, 174, 67, 318, 71, 6, 16, 4330, 4, 56, 80, 13, 3353)
    
    // Cache for studios and networks data
    private val cachedStudios = mutableMapOf<Int, StudioResponse>()
    private val cachedNetworks = mutableMapOf<Int, NetworkResponse>()

    private var mediaServerType: MediaServerType = MediaServerType.NOT_CONFIGURED

    init {
        Log.d("SeerrApiService", "Initializing API Service:")
        Log.d("SeerrApiService", "API URL: $apiUrl")
        Log.d("SeerrApiService", "Config Details:")
        Log.d("SeerrApiService", "- Protocol: ${config.protocol}")
        Log.d("SeerrApiService", "- Hostname: ${config.hostname}")
        Log.d("SeerrApiService", "- Auth Type: ${config.authType}")
        Log.d("SeerrApiService", "- Cloudflare Enabled: ${config.cloudflareEnabled}")
        if (config.cloudflareEnabled) {
            Log.d("SeerrApiService", "- CF Client ID: ${if (config.cfClientId.isNotBlank()) "Present" else "Not Present"}")
            Log.d("SeerrApiService", "- CF Client Secret: ${if (config.cfClientSecret.isNotBlank()) "Present" else "Not Present"}")
        }
        if (config.authType == AuthType.ApiKey.type) {
            Log.d("SeerrApiService", "- API Key: ${if (config.apiKey.isNotBlank()) "Present" else "Not Present"}")
        } else if (config.authType == AuthType.LocalUser.type) {
            Log.d("SeerrApiService", "- Username: ${if (config.username.isNotBlank()) "Present" else "Not Present"}")
        }
    }

    private fun createHttpClient(): HttpClient {
        val okHttpClient = OkHttpClient.Builder()
            .dns(Dns.SYSTEM)
            .sslSocketFactory(TrustAllCerts.createSSLSocketFactory()!!, TrustAllCerts.trustAllCerts[0])
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            // Fix connection pool issues that cause "Max send count exceeded" errors
            .connectionPool(ConnectionPool(maxIdleConnections = 5, keepAliveDuration = 5, java.util.concurrent.TimeUnit.MINUTES))
            .retryOnConnectionFailure(false) // Disable automatic retries to prevent accumulation
            .build()
        return HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
            install(ContentNegotiation) {
                json(json)
            }
            install(io.ktor.client.plugins.HttpTimeout) {
                connectTimeoutMillis = 30000
                requestTimeoutMillis = 30000
                socketTimeoutMillis = 30000
            }
            defaultRequest {
                header("User-Agent", "SeerrTV v${BuildConfig.VERSION_NAME}")
                
                // Add Cloudflare headers if enabled
                if (config.cloudflareEnabled && config.cfClientId.isNotBlank() && config.cfClientSecret.isNotBlank()) {
                    header("CF-Access-Client-Id", config.cfClientId)
                    header("CF-Access-Client-Secret", config.cfClientSecret)
                }
            }
        }
    }

    private fun refreshClient() {
        try {
            client.close()
        } catch (e: Exception) {
            Log.w("SeerrApiService", "Error closing old client (may already be closed): ${e.message}")
        }
        client = createHttpClient()
        Log.d("SeerrApiService", "HTTP client refreshed")
    }

    // Issue endpoints
    suspend fun createIssue(request: ca.devmesh.seerrtv.model.CreateIssueRequest): ApiResult<ca.devmesh.seerrtv.model.Issue> {
        val body = json.encodeToString(request)
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "CreateIssue payload: $body")
        }
        return executeApiCall("issue", HttpMethod.Post, body)
    }

    suspend fun addIssueComment(issueId: Int, message: String): ApiResult<ca.devmesh.seerrtv.model.Issue> {
        val safe = message.replace("\"", "\\\"")
        val body = """{"message":"$safe"}"""
        return executeApiCall("issue/$issueId/comment", HttpMethod.Post, body)
    }

    private suspend inline fun <reified T> executeApiCall(
        endpoint: String,
        method: HttpMethod = HttpMethod.Get,
        body: String? = null,
        raw: Boolean = false,
        testResponse: String? = null,
        headers: Map<String, String>? = null,
        retryCount: Int = 0
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        if (testResponse != null) {
            try {
                // Parse the test response directly
                return@withContext when (T::class) {
                    Boolean::class -> ApiResult.Success(true as T)
                    Unit::class -> ApiResult.Success(Unit as T)
                    else -> ApiResult.Success(json.decodeFromString<T>(testResponse))
                }
            } catch (e: Exception) {
                Log.e("SeerrApiService", "Error parsing test response", e)
                return@withContext ApiResult.Error(e)
            }
        }

        try {
            // Check if we need to refresh the token for all session-based auth types
            if (isSessionBasedAuth(config.getAuthType()) && currentAuthToken != null) {
                val now = Instant.now()
                if (now.isAfter(currentAuthToken!!.expiresAt.minus(1, ChronoUnit.HOURS))) {
                    Log.d("SeerrApiService", "Auth token is about to expire, refreshing...")
                    when (val loginResult = login()) {
                        is ApiResult.Success -> {
                            Log.d("SeerrApiService", "Token refreshed successfully")
                        }
                        is ApiResult.Error -> {
                            Log.e("SeerrApiService", "Failed to refresh token: ${loginResult.exception.message}")
                            return@withContext loginResult
                        }
                        is ApiResult.Loading -> {
                            Log.e("SeerrApiService", "Unexpected loading state during token refresh")
                            return@withContext ApiResult.Error(Exception("Unexpected loading state"))
                        }
                    }
                }
            }

            val requestUrl = if (raw) endpoint else "$apiUrl/$endpoint"
            val response: HttpResponse = client.request {
                url {
                    if (raw) {
                        takeFrom(endpoint)
                    } else {
                        takeFrom("$apiUrl/$endpoint")
                    }
                }
                this.method = method
                
                // Add custom headers if provided
                headers?.forEach { (key, value) ->
                    header(key, value)
                }
                
                // Handle all authentication in one place
                when (config.getAuthType()) {
                    AuthType.ApiKey -> {
                        if (config.apiKey.isNotBlank()) {
                            header("X-Api-Key", config.apiKey)
                        }
                    }
                    AuthType.LocalUser -> {
                        // Skip sending auth cookie for authentication endpoints
                        if (!endpoint.startsWith("auth/local") && currentAuthToken != null) {
                            header("Cookie", "connect.sid=${currentAuthToken!!.token}")
                        }
                    }
                    AuthType.Jellyfin -> {
                        // Skip sending auth cookie for authentication endpoints
                        if (!endpoint.startsWith("auth/jellyfin") && currentAuthToken != null) {
                            header("Cookie", "connect.sid=${currentAuthToken!!.token}")
                        }
                    }
                    AuthType.Emby -> {
                        // Skip sending auth cookie for authentication endpoints
                        if (!endpoint.startsWith("auth/jellyfin") && currentAuthToken != null) {
                            header("Cookie", "connect.sid=${currentAuthToken!!.token}")
                        }
                    }
                    AuthType.Plex -> {
                        // Skip sending auth cookie for authentication endpoints
                        if (!endpoint.startsWith("auth/plex") && currentAuthToken != null) {
                            header("Cookie", "connect.sid=${currentAuthToken!!.token}")
                        }
                    }
                }
                
                if (method == HttpMethod.Post) {
                    header("Content-Type", "application/json")
                }
                body?.let { 
                    setBody(it)
                }
            }
            
            // Log request and response in a single line
            Log.d("SeerrApiService", "${method.value} $requestUrl - Status: ${response.status.value}")
            
            // Handle 404 errors before attempting to parse the response
            if (response.status.value == 404) {
                val errorBody = try {
                    response.bodyAsText()
                } catch (_: Exception) {
                    "Unable to read error response body"
                }
                Log.e("SeerrApiService", "Resource not found: $requestUrl")
                Log.e("SeerrApiService", "404 Response body: $errorBody")
                return@withContext ApiResult.Error(Exception("Resource not found: $errorBody"), 404)
            }

            when {
                raw -> {
                    // For raw calls, return the HttpResponse as is
                    ApiResult.Success(response as T)
                }
                response.status.isSuccess() -> {
                    when (T::class) {
                        Boolean::class -> ApiResult.Success(true as T)
                        Unit::class -> ApiResult.Success(Unit as T)
                        else -> ApiResult.Success(response.body())
                    }
                }
                else -> {
                    val errorBody = try {
                        response.bodyAsText()
                    } catch (_: Exception) {
                        "Unable to read error response body"
                    }
                    Log.e("SeerrApiService", "Request failed with status ${response.status.value}")
                    Log.e("SeerrApiService", "Error response body: $errorBody")
                    
                    // Check if this is an authentication error and we should retry
                    if (isAuthenticationError(response.status.value, errorBody) && 
                        isSessionBasedAuth(config.getAuthType()) && 
                        retryCount == 0) {
                        
                        Log.d("SeerrApiService", "Authentication error detected, attempting token refresh and retry...")
                        
                        // Clear the current token to force a fresh login
                        currentAuthToken = null
                        
                        // Attempt to refresh the token
                        when (val loginResult = login()) {
                            is ApiResult.Success -> {
                                Log.d("SeerrApiService", "Token refreshed successfully, retrying request...")
                                // Retry the request with the new token using the non-inline function
                                // Note: retryCount is 0 here, so we set it to 1 to prevent infinite retries
                                return@withContext executeApiCallWithRetry(
                                    endpoint = endpoint,
                                    method = method,
                                    body = body,
                                    raw = raw,
                                    testResponse = testResponse,
                                    headers = headers,
                                    retryCount = 1
                                )
                            }
                            is ApiResult.Error -> {
                                Log.e("SeerrApiService", "Failed to refresh token during retry: ${loginResult.exception.message}")
                                return@withContext loginResult
                            }
                            is ApiResult.Loading -> {
                                Log.e("SeerrApiService", "Unexpected loading state during token refresh retry")
                                return@withContext ApiResult.Error(Exception("Unexpected loading state"))
                            }
                        }
                    }
                    
                    ApiResult.Error(Exception("HTTP Error: ${response.status} - $errorBody"), response.status.value)
                }
            }
        } catch (e: Exception) {
            handleApiException(e, if (raw) endpoint else "$apiUrl/$endpoint", method)
        }
    }

    /**
     * Non-inline version of executeApiCall for retry logic to avoid recursive inline function calls
     */
    private suspend inline fun <reified T> executeApiCallWithRetry(
        endpoint: String,
        method: HttpMethod = HttpMethod.Get,
        body: String? = null,
        raw: Boolean = false,
        testResponse: String? = null,
        headers: Map<String, String>? = null,
        retryCount: Int = 0
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        if (testResponse != null) {
            try {
                // Parse the test response directly
                return@withContext when (T::class) {
                    Boolean::class -> ApiResult.Success(true as T)
                    Unit::class -> ApiResult.Success(Unit as T)
                    else -> ApiResult.Success(json.decodeFromString<T>(testResponse))
                }
            } catch (e: Exception) {
                Log.e("SeerrApiService", "Error parsing test response", e)
                return@withContext ApiResult.Error(e)
            }
        }

        try {
            val requestUrl = if (raw) endpoint else "$apiUrl/$endpoint"
            val response: HttpResponse = client.request {
                url {
                    if (raw) {
                        takeFrom(endpoint)
                    } else {
                        takeFrom("$apiUrl/$endpoint")
                    }
                }
                this.method = method
                
                // Add custom headers if provided
                headers?.forEach { (key, value) ->
                    header(key, value)
                }
                
                // Handle all authentication in one place
                when (config.getAuthType()) {
                    AuthType.ApiKey -> {
                        if (config.apiKey.isNotBlank()) {
                            header("X-Api-Key", config.apiKey)
                        }
                    }
                    AuthType.LocalUser -> {
                        // Skip sending auth cookie for authentication endpoints
                        if (!endpoint.startsWith("auth/local") && currentAuthToken != null) {
                            header("Cookie", "connect.sid=${currentAuthToken!!.token}")
                        }
                    }
                    AuthType.Jellyfin -> {
                        // Skip sending auth cookie for authentication endpoints
                        if (!endpoint.startsWith("auth/jellyfin") && currentAuthToken != null) {
                            header("Cookie", "connect.sid=${currentAuthToken!!.token}")
                        }
                    }
                    AuthType.Emby -> {
                        // Skip sending auth cookie for authentication endpoints
                        if (!endpoint.startsWith("auth/jellyfin") && currentAuthToken != null) {
                            header("Cookie", "connect.sid=${currentAuthToken!!.token}")
                        }
                    }
                    AuthType.Plex -> {
                        // Skip sending auth cookie for authentication endpoints
                        if (!endpoint.startsWith("auth/plex") && currentAuthToken != null) {
                            header("Cookie", "connect.sid=${currentAuthToken!!.token}")
                        }
                    }
                }
                
                if (method == HttpMethod.Post) {
                    header("Content-Type", "application/json")
                }
                body?.let { 
                    setBody(it)
                }
            }
            
            // Log request and response in a single line
            Log.d("SeerrApiService", "${method.value} $requestUrl - Status: ${response.status.value} (retry $retryCount)")
            
            // Handle 404 errors before attempting to parse the response
            if (response.status.value == 404) {
                val errorBody = try {
                    response.bodyAsText()
                } catch (_: Exception) {
                    "Unable to read error response body"
                }
                Log.e("SeerrApiService", "Resource not found: $requestUrl")
                Log.e("SeerrApiService", "404 Response body: $errorBody")
                return@withContext ApiResult.Error(Exception("Resource not found: $errorBody"), 404)
            }

            when {
                raw -> {
                    // For raw calls, return the HttpResponse as is
                    ApiResult.Success(response as T)
                }
                response.status.isSuccess() -> {
                    when (T::class) {
                        Boolean::class -> ApiResult.Success(true as T)
                        Unit::class -> ApiResult.Success(Unit as T)
                        else -> ApiResult.Success(response.body())
                    }
                }
                else -> {
                    val errorBody = try {
                        response.bodyAsText()
                    } catch (_: Exception) {
                        "Unable to read error response body"
                    }
                    Log.e("SeerrApiService", "Request failed with status ${response.status.value} (retry $retryCount)")
                    Log.e("SeerrApiService", "Error response body: $errorBody")
                    
                    ApiResult.Error(Exception("HTTP Error: ${response.status} - $errorBody"), response.status.value)
                }
            }
        } catch (e: Exception) {
            handleApiException(e, if (raw) endpoint else "$apiUrl/$endpoint", method)
        }
    }

    /**
     * Check if the authentication type uses session-based authentication (cookies)
     */
    private fun isSessionBasedAuth(authType: AuthType): Boolean {
        return authType in listOf(AuthType.LocalUser, AuthType.Jellyfin, AuthType.Emby, AuthType.Plex)
    }

    /**
     * Check if the response indicates an authentication error
     */
    private fun isAuthenticationError(statusCode: Int, errorBody: String): Boolean {
        return statusCode == 401 || 
               statusCode == 403 || 
               errorBody.contains("unauthorized", ignoreCase = true) ||
               errorBody.contains("forbidden", ignoreCase = true) ||
               errorBody.contains("authentication", ignoreCase = true) ||
               errorBody.contains("login", ignoreCase = true)
    }

    private suspend fun login(): ApiResult<Unit> {
        Log.d("SeerrApiService", "🔄 Starting login process...")
        
        try {
            when (config.getAuthType()) {
                AuthType.LocalUser -> {
                    Log.d("SeerrApiService", "🔐 Attempting local user login for user: ${config.username}")
                    Log.d("SeerrApiService", "🔗 Auth endpoint: $apiUrl/auth/local")
                    
                    val authRequest = AuthRequest(config.username, config.password)
                    Log.d("SeerrApiService", "📤 Sending auth request...")
                    val authResponse = client.request {
                        url {
                            takeFrom("$apiUrl/auth/local")
                        }
                        method = HttpMethod.Post
                        headers.append("Content-Type", "application/json; charset=utf-8")
                        setBody(json.encodeToString(authRequest))
                    }
                    Log.d("SeerrApiService", "📥 Auth response received, status: ${authResponse.status}")
                    return handleAuthResponse(authResponse)
                }
                AuthType.Jellyfin -> {
                    Log.d("SeerrApiService", "Attempting Jellyfin login for user: ${config.username}")
                    Log.d("SeerrApiService", "Auth endpoint: $apiUrl/auth/jellyfin")
                    
                    val jellyfinRequest = JellyfinAuthRequest(
                        username = config.username,
                        password = config.password,
                        hostname = config.jellyfinHostname,
                        port = config.jellyfinPort,
                        useSsl = config.jellyfinUseSsl,
                        urlBase = config.jellyfinUrlBase,
                        email = config.jellyfinEmail
                    )
                    
                    val authResponse = client.request {
                        url {
                            takeFrom("$apiUrl/auth/jellyfin")
                        }
                        method = HttpMethod.Post
                        headers.append("Content-Type", "application/json; charset=utf-8")
                        setBody(json.encodeToString(jellyfinRequest))
                    }
                    return handleAuthResponse(authResponse)
                }
                AuthType.Emby -> {
                    Log.d("SeerrApiService", "Attempting Emby login for user: ${config.username}")
                    Log.d("SeerrApiService", "Auth endpoint: $apiUrl/auth/jellyfin")
                    
                    val embyRequest = EmbyAuthRequest(
                        username = config.username,
                        password = config.password
                    )
                    
                    val authResponse = client.request {
                        url {
                            takeFrom("$apiUrl/auth/jellyfin")
                        }
                        method = HttpMethod.Post
                        headers.append("Content-Type", "application/json; charset=utf-8")
                        setBody(json.encodeToString(embyRequest))
                    }
                    return handleAuthResponse(authResponse)
                }
                AuthType.Plex -> {
                    Log.d("SeerrApiService", "Attempting Plex login with auth token")
                    Log.d("SeerrApiService", "Auth endpoint: $apiUrl/auth/plex")
                    
                    // Log token status (without exposing the actual token)
                    if (config.plexAuthToken.isBlank()) {
                        Log.e("SeerrApiService", "⚠️ Plex auth token is empty or blank!")
                        return ApiResult.Error(Exception("Plex auth token is not configured"))
                    }
                    Log.d("SeerrApiService", "Plex auth token present (length: ${config.plexAuthToken.length})")
                    
                    val authResponse = client.request {
                        url {
                            takeFrom("$apiUrl/auth/plex")
                        }
                        method = HttpMethod.Post
                        headers.append("Content-Type", "application/json")
                        setBody("""{"authToken": "${config.plexAuthToken}"}""")
                    }
                    return handleAuthResponse(authResponse)
                }
                AuthType.ApiKey -> {
                    // API key auth doesn't need login
                    return ApiResult.Success(Unit)
                }
            }
        } catch (e: Exception) {
            Log.e("SeerrApiService", "Login error", e)
            return ApiResult.Error(e)
        }
    }

    private suspend fun handleAuthResponse(authResponse: HttpResponse): ApiResult<Unit> {
        Log.d("SeerrApiService", "Auth request sent. Response status: ${authResponse.status.value}")
        
        if (!authResponse.status.isSuccess()) {
            // Log the error response for debugging but ensure no sensitive data is included
            val errorBody = try {
                authResponse.bodyAsText()
                    .replace(Regex("\"email\":\"[^\"]*\""), "\"email\":\"[REDACTED]\"")
                    .replace(Regex("\"password\":\"[^\"]*\""), "\"password\":\"[REDACTED]\"")
            } catch (_: Exception) {
                "Unable to read error response body"
            }
            Log.e("SeerrApiService", "Authentication failed with status ${authResponse.status.value}")
            Log.e("SeerrApiService", "Error details: $errorBody")
            return ApiResult.Error(Exception(errorBody), authResponse.status.value)
        }

        try {
            // Get the session ID from Set-Cookie header
            val setCookieHeaders = authResponse.headers.getAll("Set-Cookie")
            Log.d("SeerrApiService", "🍪 Set-Cookie headers found: ${setCookieHeaders?.size ?: 0}")
            if (setCookieHeaders.isNullOrEmpty()) {
                Log.e("SeerrApiService", "❌ No Set-Cookie headers found in response")
                return ApiResult.Error(Exception("No session cookie received"))
            }
            
            // Find the connect.sid cookie
            val connectSidCookie = setCookieHeaders.find { it.startsWith("connect.sid=") }
                ?: run {
                    Log.e("SeerrApiService", "❌ No connect.sid cookie found in headers: $setCookieHeaders")
                    return ApiResult.Error(Exception("No session cookie received"))
                }
            
            Log.d("SeerrApiService", "✅ Found connect.sid cookie")

            // Parse the cookie value
            val cookieValue = connectSidCookie
                .substringAfter("connect.sid=")
                .substringBefore(";")
                .trim()
            
            Log.d("SeerrApiService", "🔑 Cookie value: ${cookieValue.take(10)}...")
            
            // Store the session ID
            val expiryTime = Instant.now().plus(30, ChronoUnit.DAYS)
            currentAuthToken = AuthToken(
                token = cookieValue,
                expiresAt = expiryTime
            )

            Log.d("SeerrApiService", "💾 Session token stored successfully")
            // Return success - user info will be fetched by testAuthentication
            return ApiResult.Success(Unit)
        } catch (e: Exception) {
            Log.e("SeerrApiService", "❌ Failed to process auth response", e)
            return ApiResult.Error(e)
        }
    }

    private inline fun <reified T> handleApiException(
        e: Exception,
        fullUrl: String,
        method: HttpMethod
    ): ApiResult<T> {
        Log.e("SeerrApiService", "API Error for $method $fullUrl", e)
        return when (e) {
            is ConnectTimeoutException, is java.net.SocketTimeoutException -> {
                val timeoutMs = 30000 // Match the configured timeout value
                val timeoutMessage = "Connection timeout after ${timeoutMs}ms: ${e.message}. Please check your network connection and server availability."
                Log.e("SeerrApiService", timeoutMessage)
                ApiResult.Error(Exception(timeoutMessage, e))
            }
            is kotlinx.serialization.SerializationException -> {
                Log.e("SeerrApiService", "JSON parsing error: ${e.message}")
                ApiResult.Error(e)
            }
            is ResponseException -> {
                Log.e("SeerrApiService", "HTTP error: ${e.response.status}")
                ApiResult.Error(e, e.response.status.value)
            }
            else -> ApiResult.Error(e)
        }
    }

    fun getCurrentLanguage(): String {
        return Locale.current.language.lowercase()
    }

    suspend fun getTrending(reset: Boolean = false): ApiResult<List<Media>> {
        val endpoint = "discover/trending"
        if (reset) resetPaginationState(endpoint)
        return fetchSinglePage(
            endpoint = endpoint,
            filter = { media -> media.mediaType == "movie" || media.mediaType == "tv" }
        ).mapData { it.results }
    }

    suspend fun discoverMovies(reset: Boolean = false): ApiResult<List<Media>> {
        val endpoint = "discover/movies"
        if (reset) resetPaginationState(endpoint)
        return fetchSinglePage(endpoint).mapData { it.results }
    }

    suspend fun getUpcomingMovies(reset: Boolean = false): ApiResult<List<Media>> {
        val endpoint = "discover/movies"
        if (reset) resetPaginationState(endpoint)
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        return fetchSinglePage("$endpoint?primaryReleaseDateGte=$today").mapData { it.results }
    }

    suspend fun getPopularSeries(reset: Boolean = false): ApiResult<List<Media>> {
        val endpoint = "discover/tv"
        if (reset) resetPaginationState(endpoint)
        return fetchSinglePage(endpoint).mapData { it.results }
    }

    suspend fun getUpcomingSeries(reset: Boolean = false): ApiResult<List<Media>> {
        val endpoint = "discover/tv"
        if (reset) resetPaginationState(endpoint)
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        return fetchSinglePage("$endpoint?firstAirDateGte=$today").mapData { it.results }
    }

    private suspend fun fetchSinglePage(
        endpoint: String,
        filter: ((Media) -> Boolean)? = null
    ): ApiResult<Discover> {
        val state = getOrCreatePaginationState(endpoint)
        
        return when (val result = fetchMediaListPage(endpoint, state.currentPage)) {
            is ApiResult.Success -> {
                val data = result.data
                val filteredResults = if (filter != null) {
                    data.results.filter(filter)
                } else {
                    data.results
                }

                // Update pagination state
                state.totalPages = data.totalPages
                state.totalResults = data.totalResults
                state.hasMorePages = state.currentPage < state.totalPages
                
                // Only increment the page counter after a successful API call with results
                if (state.hasMorePages && filteredResults.isNotEmpty()) {
                    state.currentPage++
                }

                ApiResult.Success(
                    data = Discover(
                        page = state.currentPage - 1,
                        totalPages = state.totalPages,
                        totalResults = state.totalResults,
                        results = filteredResults
                    ),
                    paginationInfo = state.toImmutable()
                )
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }

    suspend fun getMovieDetails(id: String): ApiResult<MediaDetails> {
        val locale = getCurrentLanguage()
        val pageEndpoint = "movie/$id"
        // append locale if not english
        val localizedEndpoint = if (locale != "en") "$pageEndpoint?language=$locale" else pageEndpoint
        return executeApiCall(localizedEndpoint)
    }

    suspend fun getTVDetails(id: String): ApiResult<MediaDetails> {
        val locale = getCurrentLanguage()
        val pageEndpoint = "tv/$id"
        // append locale if not english
        val localizedEndpoint = if (locale != "en") "$pageEndpoint?language=$locale" else pageEndpoint
        return executeApiCall(localizedEndpoint)
    }

    suspend fun getSimilarMovies(movieId: Int, page: Int = 1): ApiResult<SimilarMediaResponse> {
        val locale = getCurrentLanguage()
        val pageEndpoint = "movie/$movieId/similar?page=$page"
        // append locale if not english
        val localizedEndpoint = if (locale != "en") "$pageEndpoint&language=$locale" else pageEndpoint
        return executeApiCall(localizedEndpoint)
    }

    suspend fun getSimilarTVShows(tvId: Int, page: Int = 1): ApiResult<SimilarMediaResponse> {
        val locale = getCurrentLanguage()
        val pageEndpoint = "tv/$tvId/similar?page=$page"
        // append locale if not english
        val localizedEndpoint = if (locale != "en") "$pageEndpoint&language=$locale" else pageEndpoint
        return executeApiCall(localizedEndpoint)
    }

    suspend fun getRecentlyAdded(reset: Boolean = false): ApiResult<PaginatedMediaResponse> {
        val endpoint = "media"
        if (reset) resetPaginationState(endpoint)
        
        val state = getOrCreatePaginationState(endpoint)
        val skip = (state.currentPage - 1) * DEFAULT_PAGE_SIZE
        
        return when (val result = executeApiCall<PaginatedMediaResponse>("$endpoint?filter=allavailable&take=$DEFAULT_PAGE_SIZE&skip=$skip&sort=mediaAdded")) {
            is ApiResult.Success -> {
                state.totalResults = result.data.pageInfo.results
                state.totalPages = result.data.pageInfo.pages
                
                // Calculate total items retrieved so far
                val itemsRetrievedSoFar = (state.currentPage - 1) * DEFAULT_PAGE_SIZE + result.data.results.size
                
                // More accurate check for hasMorePages: compare items retrieved so far with total results
                state.hasMorePages = itemsRetrievedSoFar < state.totalResults
                
                if (BuildConfig.DEBUG) {
                    Log.d("SeerrApiService", "RecentlyAdded pagination: page ${state.currentPage}/${state.totalPages}, " +
                        "items so far: $itemsRetrievedSoFar/${state.totalResults}, " +
                        "hasMorePages: ${state.hasMorePages}")
                }
                
                // Only increment the page counter after a successful API call with results
                if (state.hasMorePages && result.data.results.isNotEmpty()) {
                    state.currentPage++
                }

                ApiResult.Success(result.data, state.toImmutable())
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }

    suspend fun search(query: String, loadMore: Boolean = false): ApiResult<SearchResponse> {
        if (!loadMore) {
            resetPaginationState("search")
        }
        val state = getOrCreatePaginationState("search")
        
        val locale = getCurrentLanguage()
        val endpoint = "search?query=${query.encodeForOverseerr()}"
        val localizedEndpoint = if (locale != "en") "$endpoint&language=$locale" else endpoint
        
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "Searching for '$query', page ${state.currentPage}")
        }
        
        return when (val result = executeApiCall<SearchResponse>("$localizedEndpoint&page=${state.currentPage}")) {
            is ApiResult.Success -> {
                // Update pagination state
                state.totalPages = result.data.totalPages
                state.totalResults = result.data.totalResults
                state.hasMorePages = state.currentPage < state.totalPages
                
                // Only increment the page counter after a successful API call
                if (state.hasMorePages && result.data.results.isNotEmpty()) {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Successfully loaded search page ${state.currentPage}, incrementing to next page")
                    }
                    state.currentPage++
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Not incrementing search page: hasMore=${state.hasMorePages}, results=${result.data.results.size}")
                    }
                }

                ApiResult.Success(result.data, state.toImmutable())
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }
    
    suspend fun getPersonDetails(personId: String): ApiResult<PersonDetails> {
        val locale = getCurrentLanguage()
        val pageEndpoint = "person/$personId"
        // append locale if not english
        val localizedEndpoint = if (locale != "en") "$pageEndpoint?language=$locale" else pageEndpoint
        return executeApiCall(localizedEndpoint)
    }

    suspend fun getPersonCombinedCredits(personId: String): ApiResult<CombinedCredits> {
        val locale = getCurrentLanguage()
        val pageEndpoint = "person/$personId/combined_credits"
        // append locale if not english
        val localizedEndpoint = if (locale != "en") "$pageEndpoint?language=$locale" else pageEndpoint
        return executeApiCall(localizedEndpoint)
    }

    suspend fun getRadarrs(): ApiResult<List<RadarrResult>> {
        return executeApiCall<RadarrsResponse>("service/radarr")
    }

    suspend fun getRadarr(id: Int): ApiResult<Radarr> {
        return executeApiCall("service/radarr/$id")
    }

    suspend fun getSonarrs(): ApiResult<List<SonarrResult>> {
        // val testResponse = """ """
        // return executeApiCall("service/sonarr", testResponse = testResponse)
        return executeApiCall("service/sonarr")
    }

    suspend fun getSonarr(id: Int): ApiResult<Sonarr> {
        // var testResponse = ""
        // return executeApiCall("service/sonarr/$id", testResponse = testResponse)
        return executeApiCall("service/sonarr/$id")
    }

    suspend fun discoverMoviesByKeyword(keywordId: String, loadMore: Boolean = false): ApiResult<Discover> {
        if (!loadMore) {
            resetPaginationState("movie_keywords")
        }
        val state = getOrCreatePaginationState("movie_keywords")
        val locale = SharedPreferencesUtil.getDiscoveryLanguage(context)
        // Build the endpoint string with parameters
        val endpoint = "discover/movies"
        val pageEndpoint = "$endpoint?keywords=$keywordId&page=${state.currentPage}"
        val localizedEndpoint = if (locale != "en") "${pageEndpoint}&language=$locale" else pageEndpoint
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "Fetching movies for keyword $keywordId, page ${state.currentPage}")
        }
        return when (val result = executeApiCall<Discover>(localizedEndpoint)) {
            is ApiResult.Success -> {
                // Update pagination info
                state.totalPages = result.data.totalPages
                state.totalResults = result.data.totalResults
                state.hasMorePages = state.currentPage < state.totalPages
                
                // Only increment the page counter after a successful API call with results
                if (state.hasMorePages && result.data.results.isNotEmpty()) {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Successfully loaded keyword page ${state.currentPage}, incrementing to next page")
                    }
                    state.currentPage++
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Not incrementing keyword page: hasMore=${state.hasMorePages}, results=${result.data.results.size}")
                    }
                }
                
                ApiResult.Success(result.data, state.toImmutable())
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }

    suspend fun discoverTVByKeyword(keywordId: String, loadMore: Boolean = false): ApiResult<Discover> {
        if (!loadMore) {
            resetPaginationState("tv_keywords")
        }
        val state = getOrCreatePaginationState("tv_keywords")
        val locale = SharedPreferencesUtil.getDiscoveryLanguage(context)
        // Build the endpoint string with parameters
        val endpoint = "discover/tv"
        val pageEndpoint = "$endpoint?keywords=$keywordId&page=${state.currentPage}"
        val localizedEndpoint = if (locale != "en") "${pageEndpoint}&language=$locale" else pageEndpoint
        return when (val result = executeApiCall<Discover>(localizedEndpoint)) {
            is ApiResult.Success -> {
                // Update pagination info
                state.totalPages = result.data.totalPages
                state.totalResults = result.data.totalResults
                state.hasMorePages = state.currentPage < state.totalPages
                
                // Only increment the page counter after a successful API call with results
                if (state.hasMorePages && result.data.results.isNotEmpty()) {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Successfully loaded keyword page ${state.currentPage}, incrementing to next page")
                    }
                    state.currentPage++
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Not incrementing keyword page: hasMore=${state.hasMorePages}, results=${result.data.results.size}")
                    }
                }
                
                ApiResult.Success(result.data, state.toImmutable())
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }

    suspend fun getRatingsData(mediaId: String, mediaType: String): ApiResult<RatingsResponse> {
        val ratingsEndpoint = when (mediaType.lowercase()) {
            "movie" -> "ratingscombined"
            "tv" -> "ratings" // TV series use the /ratings endpoint
            else -> "ratings"
        }
        val endpoint = "$mediaType/$mediaId/$ratingsEndpoint"
        Log.d("SeerrApiService", "Fetching ratings from endpoint: $endpoint")
        
        val result = when (ratingsEndpoint) {
            "ratingscombined" -> {
                val combinedResult = executeApiCall<RatingsCombinedResponse>(endpoint)
                when (combinedResult) {
                    is ApiResult.Success -> ApiResult.Success(
                        RatingsResponse(
                            rt = combinedResult.data.rt,
                            imdb = combinedResult.data.imdb
                        )
                    )
                    is ApiResult.Error -> ApiResult.Error(combinedResult.exception, combinedResult.statusCode)
                    is ApiResult.Loading -> ApiResult.Loading()
                }
            }
            "ratings" -> {
                val flatResult = executeApiCall<RatingsFlatResponse>(endpoint)
                when (flatResult) {
                    is ApiResult.Success -> ApiResult.Success(
                        RatingsResponse(
                            rt = if (flatResult.data.criticsScore != null || flatResult.data.audienceScore != null) {
                                RottenTomatoesRating(
                                    title = flatResult.data.title,
                                    url = flatResult.data.url,
                                    criticsRating = flatResult.data.criticsRating,
                                    criticsScore = flatResult.data.criticsScore,
                                    audienceRating = flatResult.data.audienceRating,
                                    audienceScore = flatResult.data.audienceScore,
                                    year = flatResult.data.year
                                )
                            } else null,
                            imdb = null
                        )
                    )
                    is ApiResult.Error -> ApiResult.Error(flatResult.exception, flatResult.statusCode)
                    is ApiResult.Loading -> ApiResult.Loading()
                }
            }
            else -> ApiResult.Error(Exception("Unknown endpoint: $ratingsEndpoint"))
        }
        
        Log.d("SeerrApiService", "Ratings API response: $result")
        return result
    }

    suspend fun requestMedia(requestBody: MediaRequestBody): ApiResult<Boolean> {
        val jsonPayload = json.encodeToString(requestBody)
        Log.d("SeerrApiService", "Request Media Payload: $jsonPayload")
        return executeApiCall("request", HttpMethod.Post, jsonPayload)
    }

    suspend fun getSetupId(): ApiResult<SetupIdResponse> {
        return executeApiCall<HttpResponse>("${BuildConfig.BROWSER_CONFIG_BASE_URL}/api/setupid", HttpMethod.Get, raw = true).let { result ->
            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    if (response.status.isSuccess()) {
                        try {
                            val responseBody: String = response.bodyAsText()
                            val setupIdResponse = Json.decodeFromString<SetupIdResponse>(responseBody)
                            ApiResult.Success(setupIdResponse)
                        } catch (e: Exception) {
                            Log.e("SeerrApiService", "Error parsing setup ID response", e)
                            ApiResult.Error(e)
                        }
                    } else {
                        ApiResult.Error(Exception("Failed to get setup ID"), response.status.value)
                    }
                }
                is ApiResult.Error -> result
                is ApiResult.Loading -> result
            }
        }
    }

    suspend fun getBrowserConfig(setupId: String): ApiResult<SeerrConfig> {
        return executeApiCall<HttpResponse>("${BuildConfig.BROWSER_CONFIG_BASE_URL}/api/config/$setupId", HttpMethod.Get, raw = true).let { result ->
            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    if (response.status.isSuccess()) {
                        try {
                            val responseBody: String = response.bodyAsText()
                            val config = Json.decodeFromString<SeerrConfig>(responseBody)
                            ApiResult.Success(config)
                        } catch (e: Exception) {
                            ApiResult.Error(Exception("Failed to parse config response: ${e.message}"))
                        }
                    } else {
                        ApiResult.Error(Exception("Failed to get config. Status: ${response.status}"))
                    }
                }
                is ApiResult.Error -> result
                is ApiResult.Loading -> result
            }
        }
    }

    private suspend fun fetchMediaListPage(endpoint: String, page: Int): ApiResult<Discover> {
        val pageEndpoint = "$endpoint${if ('?' in endpoint) '&' else '?'}page=$page"
        // append locale if not english
        val locale = SharedPreferencesUtil.getDiscoveryLanguage(context)
        val localizedEndpoint = if (locale != "en") "$pageEndpoint&language=$locale" else pageEndpoint
        return executeApiCall(localizedEndpoint)
    }

    private fun <T, R> ApiResult<T>.mapData(transform: (T) -> R): ApiResult<R> {
        return when (this) {
            is ApiResult.Success -> ApiResult.Success(
                data = transform(data),
                paginationInfo = paginationInfo
            )
            is ApiResult.Error -> this
            is ApiResult.Loading -> this
        }
    }

    private fun String.encodeForOverseerr(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
            .replace("+", "%20") // Replace + with %20 for spaces
            .replace("*", "%2A") // Replace * with %2A for asterisks")
    }

    suspend fun testBaseConnection(): ApiValidationResult = withContext(Dispatchers.IO) {
        Log.d("SeerrApiService", "Testing base API endpoint: $apiUrl")
        try {
            val baseApiResponse = executeApiCall<HttpResponse>(apiUrl, HttpMethod.Get, raw = true)
            when (baseApiResponse) {
                is ApiResult.Success -> {
                    val response = baseApiResponse.data
                    // Check for Cloudflare first
                    if (response.status.value == 403 && response.headers["server"]?.contains("cloudflare", ignoreCase = true) == true) {
                        Log.d("SeerrApiService", "Cloudflare protection detected")
                        val config = SharedPreferencesUtil.getConfig(context)
                        if (config?.cloudflareEnabled == true && 
                            config.cfClientId.isNotBlank() && 
                            config.cfClientSecret.isNotBlank()) {
                            Log.d("SeerrApiService", "Retrying with Cloudflare credentials")
                            // Retry with Cloudflare headers
                            val cfResponse = client.request {
                                url {
                                    takeFrom(apiUrl)
                                }
                                method = HttpMethod.Get
                                header("CF-Access-Client-Id", config.cfClientId)
                                header("CF-Access-Client-Secret", config.cfClientSecret)
                            }
                            if (!cfResponse.status.isSuccess()) {
                                Log.e("SeerrApiService", "Cloudflare authentication failed: ${cfResponse.status.value}")
                                return@withContext ApiValidationResult.Error("Failed to connect with Cloudflare credentials", cfResponse.status.value)
                            }
                        } else {
                            Log.e("SeerrApiService", "Cloudflare protection detected but credentials not configured")
                            return@withContext ApiValidationResult.CloudflareRequired("Cloudflare protection detected but credentials not configured")
                        }
                    }

                    // Check Content-Type header
                    val contentType = response.headers["Content-Type"] ?: response.headers["content-type"]
                    if (contentType?.contains("application/json", ignoreCase = true) != true) {
                        Log.e("SeerrApiService", "Invalid content type received: $contentType")
                        return@withContext ApiValidationResult.Error("The provided address does not appear to be a valid Overseerr/Jellyseerr/Seerr server")
                    }
                    
                    try {
                        val apiResponse = json.decodeFromString<OverseerrApiResponse>(response.bodyAsText())
                        Log.d("SeerrApiService", "API response parsed successfully: ${apiResponse.api} v${apiResponse.version}")
                        if (apiResponse.api !in listOf("Overseerr API", "Jellyseerr API", "Seerr API")) {
                            Log.e("SeerrApiService", "Invalid API response: ${apiResponse.api}")
                            return@withContext ApiValidationResult.Error("Invalid API response", response.status.value)
                        }
                        
                        // Detect server type from API response
                        val detectedServerType = when (apiResponse.api) {
                            "Jellyseerr API" -> ServerType.JELLYSEERR
                            "Seerr API" -> ServerType.SEERR
                            else -> {
                                // For Overseerr or when we need additional verification
                                determineServerTypeFromEndpoints()
                            }
                        }
                        
                        // Set the server type
                        serverType = detectedServerType
                        
                        // Save it to SharedPreferences
                        SharedPreferencesUtil.setServerType(context, serverType.name)
                        
                        Log.d("SeerrApiService", "Server type detected and saved: $serverType")

                        // Return success if we have a valid server type
                        if (serverType != ServerType.UNKNOWN) {
                            return@withContext ApiValidationResult.Success(serverType)
                        } else {
                            Log.e("SeerrApiService", "Unknown server type detected")
                            return@withContext ApiValidationResult.Error("Unknown server type detected")
                        }
                    } catch (e: Exception) {
                        Log.e("SeerrApiService", "Failed to parse API response", e)
                        return@withContext ApiValidationResult.Error("Failed to parse API response: ${e.message}")
                    }
                }
                is ApiResult.Error -> {
                    if (baseApiResponse.statusCode == 404) {
                        Log.e("SeerrApiService", "API endpoint not found. Please check the hostname and ensure Overseerr/Jellyseerr/Seerr is running.")
                        return@withContext ApiValidationResult.Error("API endpoint not found. Please check the hostname and ensure Overseerr/Jellyseerr/Seerr is running.", 404)
                    }
                    Log.e("SeerrApiService", "Failed to connect to API: ${baseApiResponse.exception.message}")
                    return@withContext ApiValidationResult.Error("Failed to connect to API: ${baseApiResponse.exception.message}", baseApiResponse.statusCode)
                }
                is ApiResult.Loading -> {
                    Log.e("SeerrApiService", "Unexpected loading state during base API check")
                    return@withContext ApiValidationResult.Error("Unexpected loading state")
                }
            }
        } catch (e: Exception) {
            Log.e("SeerrApiService", "Connection test failed with exception", e)
            return@withContext ApiValidationResult.Error("Connection test failed: ${e.message}")
        }
    }

    /**
     * Helper method to determine the server type based on API endpoint availability
     * This is a more reliable method than just checking the API response
     */
    private suspend fun determineServerTypeFromEndpoints(): ServerType = withContext(Dispatchers.IO) {
        Log.d("SeerrApiService", "Determining server type from endpoints...")
        try {
            // Try to call the Jellyfin auth endpoint which only exists in Jellyseerr
            val testRequest = JellyfinTestRequest()
            val response = client.request {
                url {
                    takeFrom("$apiUrl/auth/jellyfin")
                }
                method = HttpMethod.Post
                headers.append("Content-Type", "application/json")
                setBody(json.encodeToString(testRequest))
            }
            
            // If we get here and the response is not 404, it's Jellyseerr
            Log.d("SeerrApiService", "Jellyfin endpoint response: ${response.status.value}")
            
            // We hit a redirection - this is an error
            if (response.status.value == 301) {
                Log.d("SeerrApiService", "Error: Redirection detected when checking server type")
                return@withContext ServerType.UNKNOWN
            }

            if (response.status.value != 404) {
                Log.d("SeerrApiService", "Detected Jellyseerr server (non-404 response from Jellyfin endpoint)")
                return@withContext ServerType.JELLYSEERR
            }
            
            Log.d("SeerrApiService", "Detected Overseerr server (404 response from Jellyfin endpoint)")
            return@withContext ServerType.OVERSEERR
        } catch (e: Exception) {
            Log.e("SeerrApiService", "Error determining server type from endpoints", e)
            return@withContext ServerType.UNKNOWN
        }
    }

    suspend fun testAuthentication(): ApiValidationResult = withContext(Dispatchers.IO) {
        Log.d("SeerrApiService", "Testing authentication with type: ${config.authType}")
        try {
            // Retry logic for transient 500 errors (server might need a moment after client refresh)
            var lastError: ApiResult.Error? = null
            val maxRetries = 2
            var attempt = 0
            
            while (attempt <= maxRetries) {
                if (attempt > 0) {
                    Log.d("SeerrApiService", "🔄 Retrying authentication (attempt ${attempt + 1}/${maxRetries + 1})...")
                    // Small delay before retry to allow server/client to stabilize
                    kotlinx.coroutines.delay(500)
                }
                
                // Attempt login to get initial token
                Log.d("SeerrApiService", "🔄 Starting login process...")
                when (val loginResult = login()) {
                    is ApiResult.Success -> {
                        Log.d("SeerrApiService", "✅ Login successful, testing token with /auth/me")
                        // Test if the token works by making a request to /auth/me
                        val tokenTestResponse = executeApiCall<AuthMeResponse>("auth/me")
                        when (tokenTestResponse) {
                            is ApiResult.Success -> {
                                Log.d("SeerrApiService", "✅ Authentication successful")
                                val user = tokenTestResponse.data
                                // Update user info with correct permissions
                                currentUserInfo = UserInfo(
                                    id = user.id,
                                    displayName = user.displayName,
                                    permissions = user.permissions ?: 0
                                )
                                Log.d("SeerrApiService", "ID: ${currentUserInfo?.id}")
                                Log.d("SeerrApiService", "DisplayName: ${currentUserInfo?.displayName}")
                                Log.d("SeerrApiService", "Permissions: ${currentUserInfo?.permissions}")
                                // Store in SharedPreferences
                                SharedPreferencesUtil.saveUserInfo(
                                    context,
                                    user.id,
                                    user.displayName,
                                    user.permissions ?: 0
                                )
                                
                                // Detect media server type after successful authentication
                                detectMediaServerType()
                                
                                return@withContext ApiValidationResult.Success(serverType)
                            }
                            is ApiResult.Error -> {
                                Log.e("SeerrApiService", "❌ Token validation failed: ${tokenTestResponse.exception.message}")
                                return@withContext ApiValidationResult.Error("Token validation failed: ${tokenTestResponse.exception.message}")
                            }
                            is ApiResult.Loading -> {
                                Log.e("SeerrApiService", "❌ Unexpected loading state during token validation")
                                return@withContext ApiValidationResult.Error("Unexpected loading state")
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        lastError = loginResult
                        val statusCode = loginResult.statusCode
                        // Retry on 500 errors (server errors that might be transient)
                        if (statusCode == 500 && attempt < maxRetries) {
                            Log.w("SeerrApiService", "⚠️ Login failed with 500 error, will retry: ${loginResult.exception.message}")
                            attempt++
                            continue
                        } else {
                            Log.e("SeerrApiService", "❌ Login failed: ${loginResult.exception.message}")
                            return@withContext ApiValidationResult.Error("Authentication failed: ${loginResult.exception.message}", statusCode)
                        }
                    }
                    is ApiResult.Loading -> {
                        Log.e("SeerrApiService", "❌ Unexpected loading state during auth")
                        return@withContext ApiValidationResult.Error("Unexpected loading state")
                    }
                }
            }
            
            // If we exhausted retries, return the last error
            Log.e("SeerrApiService", "❌ Login failed after ${maxRetries + 1} attempts: ${lastError?.exception?.message}")
            return@withContext ApiValidationResult.Error(
                "Authentication failed after retries: ${lastError?.exception?.message}",
                lastError?.statusCode
            )
        } catch (e: Exception) {
            Log.e("SeerrApiService", "❌ Authentication test failed with exception", e)
            return@withContext ApiValidationResult.Error("Authentication test failed: ${e.message}")
        }
    }

    suspend fun validateApi(): ApiValidationResult = withContext(Dispatchers.IO) {
        Log.d("SeerrApiService", "Starting API validation")
        // Step 1: Test base connection
        when (val connectionResult = testBaseConnection()) {
            is ApiValidationResult.Success -> {
                // Step 2: Test authentication
                return@withContext testAuthentication()
            }
            else -> return@withContext connectionResult
        }
    }

    suspend fun testAuth(): ApiValidationResult = withContext(Dispatchers.IO) {
        Log.d("SeerrApiService", "Starting authentication test")
        return@withContext testAuthentication()
    }

    fun updateConfig(newConfig: SeerrConfig) {
        Log.d("SeerrApiService", "Updating API Service configuration:")
        Log.d("SeerrApiService", "- Protocol: ${newConfig.protocol}")
        Log.d("SeerrApiService", "- Hostname: ${newConfig.hostname}")
        Log.d("SeerrApiService", "- Auth Type: ${newConfig.authType}")
        Log.d("SeerrApiService", "- Cloudflare Enabled: ${newConfig.cloudflareEnabled}")
        Log.d("SeerrApiService", "Updating config from ${config.getAuthType()} to ${newConfig.getAuthType()}")

        // Update the config first
        config = newConfig
        apiUrl = buildApiUrl(config)
        
        // Refresh the HTTP client with new config
        refreshClient()

        // Clear caches since we're effectively creating a new connection
        cachedRadarrData = null
        cachedSonarrData = null
    }

    fun getConfig(): SeerrConfig {
        return config
    }

    fun getServerType(): ServerType {
        return serverType
    }

    suspend fun loadRadarrConfiguration() = withContext(Dispatchers.IO) {
        try {
            // Load Radarr data
            when (val radarrResults = getRadarrs()) {
                is ApiResult.Success -> {
                    val allRadarrs = radarrResults.data
                    Log.d("SeerrApiService", "Found ${allRadarrs.size} Radarr servers")
                    val defaultRadarr = allRadarrs.find { it.isDefault }
                    val fullRadarrList = allRadarrs.mapNotNull { radarr ->
                        when (val radarrDetails = getRadarr(radarr.id)) {
                            is ApiResult.Success -> {
                                radarrDetails.data
                            }
                            is ApiResult.Error -> {
                                Log.e("SeerrApiService", "Failed to load Radarr details for server ${radarr.id}: ${radarrDetails.exception.message}")
                                null
                            }
                            is ApiResult.Loading -> null
                        }
                    }
                    val defaultRadarrDetails = fullRadarrList.find { it.server.id == defaultRadarr?.id }
                    cachedRadarrData = RadarrServerInfo(
                        allServers = fullRadarrList,
                        defaultServer = defaultRadarrDetails ?: fullRadarrList.firstOrNull()
                    )
                }
                is ApiResult.Error -> {
                    Log.e("SeerrApiService", "Failed to load Radarr servers", radarrResults.exception)
                    cachedRadarrData = RadarrServerInfo(
                        allServers = emptyList(),
                        defaultServer = null,
                        error = radarrResults.exception
                    )
                }
                is ApiResult.Loading -> { /* Handle loading state if needed */ }
            }
        } catch (e: Exception) {
            Log.e("SeerrApiService", "Error loading Radarr configuration", e)
            cachedRadarrData = RadarrServerInfo(
                allServers = emptyList(),
                defaultServer = null,
                error = e
            )
            throw e
        }
    }

    suspend fun loadSonarrConfiguration() = withContext(Dispatchers.IO) {
        try {
            // Load Sonarr data
            when (val sonarrResults = getSonarrs()) {
                is ApiResult.Success -> {
                    val allSonarrs = sonarrResults.data
                    Log.d("SeerrApiService", "Found ${allSonarrs.size} Sonarr servers")
                    val defaultSonarr = allSonarrs.find { it.isDefault }
                    val fullSonarrList = allSonarrs.mapNotNull { sonarr ->
                        when (val sonarrDetails = getSonarr(sonarr.id)) {
                            is ApiResult.Success -> {
                                sonarrDetails.data
                            }
                            is ApiResult.Error -> {
                                Log.e("SeerrApiService", "Failed to load Sonarr details for server ${sonarr.id}: ${sonarrDetails.exception.message}")
                                null
                            }
                            is ApiResult.Loading -> null
                        }
                    }
                    val defaultSonarrDetails = fullSonarrList.find { it.server.id == defaultSonarr?.id }
                    cachedSonarrData = SonarrServerInfo(
                        allServers = fullSonarrList,
                        defaultServer = defaultSonarrDetails ?: fullSonarrList.firstOrNull()
                    )
                }
                is ApiResult.Error -> {
                    Log.e("SeerrApiService", "Failed to load Sonarr servers", sonarrResults.exception)
                    cachedSonarrData = SonarrServerInfo(
                        allServers = emptyList(),
                        defaultServer = null,
                        error = sonarrResults.exception
                    )
                }
                is ApiResult.Loading -> { /* Handle loading state if needed */ }
            }
        } catch (e: Exception) {
            Log.e("SeerrApiService", "Error loading Sonarr configuration", e)
            cachedSonarrData = SonarrServerInfo(
                allServers = emptyList(),
                defaultServer = null,
                error = e
            )
            throw e
        }
    }

    fun getCachedRadarrData(): RadarrServerInfo? {
        return cachedRadarrData
    }
    
    fun getCachedSonarrData(): SonarrServerInfo? {
        return cachedSonarrData
    }

    suspend fun getRequests(
        reset: Boolean = false,
        sort: String = "modified"
    ): ApiResult<RequestResponse> {
        val endpoint = "request"
        if (reset) resetPaginationState(endpoint)
        
        val state = getOrCreatePaginationState(endpoint)
        val skip = (state.currentPage - 1) * DEFAULT_PAGE_SIZE
        
        return when (val result = executeApiCall<RequestResponse>("$endpoint?filter=all&take=$DEFAULT_PAGE_SIZE&sort=$sort&skip=$skip")) {
            is ApiResult.Success -> {
                state.totalResults = result.data.pageInfo.results
                state.totalPages = result.data.pageInfo.pages
                
                // Calculate total items retrieved so far
                val itemsRetrievedSoFar = (state.currentPage - 1) * DEFAULT_PAGE_SIZE + result.data.results.size
                
                // More accurate check for hasMorePages: compare items retrieved so far with total results
                state.hasMorePages = itemsRetrievedSoFar < state.totalResults
                
                if (BuildConfig.DEBUG) {
                    Log.d("SeerrApiService", "Request pagination: page ${state.currentPage}/${state.totalPages}, " +
                        "items so far: $itemsRetrievedSoFar/${state.totalResults}, " +
                        "hasMorePages: ${state.hasMorePages}")
                }
                
                // Only increment the page counter after a successful API call with results
                if (state.hasMorePages && result.data.results.isNotEmpty()) {
                    state.currentPage++
                }

                ApiResult.Success(result.data, state.toImmutable())
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }

    suspend fun deleteRequest(requestId: Int): ApiResult<Unit> {
        return executeApiCall("request/$requestId", HttpMethod.Delete)
    }

    /**
     * Update the status of a request.
     * Permission Requirements:
     * - MANAGE_REQUESTS (16) permission required
     * - ADMIN (2) permission also grants this ability
     *
     * @param requestId The ID of the request to update
     * @param status The new status: 2 (APPROVED) or 3 (DECLINED)
     */
    suspend fun updateRequestStatus(requestId: Int, status: Int): ApiResult<Request> {
        require(status in listOf(2, 3)) {
            "Status must be either 2 (APPROVED) or 3 (DECLINED)"
        }
        // Convert the integer status to the appropriate string value for the API
        val statusString = when (status) {
            2 -> "approve"
            3 -> "decline"
            else -> throw IllegalArgumentException("Invalid status value")
        }
        return executeApiCall("request/$requestId/$statusString", HttpMethod.Post)
    }

    suspend fun deleteMediaFile(mediaId: String): ApiResult<Unit> {
        return executeApiCall("media/$mediaId/file", HttpMethod.Delete)
    }

    suspend fun deleteMedia(mediaId: String): ApiResult<Unit> {
        return executeApiCall("media/$mediaId", HttpMethod.Delete)
    }

    suspend fun requestPlexPin(clientId: String): ApiResult<PlexPinResponse> {
        val headers = mapOf(
            "X-Plex-Client-Identifier" to clientId,
            "X-Plex-Product" to "SeerrTV",
            "X-Plex-Version" to BuildConfig.VERSION_NAME,
            "X-Plex-Device" to "Android TV",
            "X-Plex-Platform" to "Android",
            "X-Plex-Platform-Version" to "15"
        )
        
        // val requestBody = """{"strong": true}"""
        val requestBody = null

        Log.d("SeerrApiService", "Requesting Plex PIN with:")
        Log.d("SeerrApiService", "Headers: $headers")
        Log.d("SeerrApiService", "Body: $requestBody")
        
        return when (val response = executeApiCall<HttpResponse>(
            endpoint = "https://plex.tv/api/v2/pins",
            method = HttpMethod.Post,
            raw = true,
            body = requestBody,
            headers = headers
        )) {
            is ApiResult.Success -> {
                try {
                    val contentType = response.data.headers["Content-Type"] ?: response.data.headers["content-type"]
                    val responseText = response.data.bodyAsText()
                    Log.d("SeerrApiService", "Received Plex PIN response: $responseText")
                    Log.d("SeerrApiService", "Content-Type: $contentType")

                    // Parse JSON response
                    val jsonResponse = json.decodeFromString<Map<String, JsonElement>>(responseText)
                    
                    val pinResponse = PlexPinResponse(
                        id = jsonResponse["id"]?.jsonPrimitive?.content ?: "",
                        code = jsonResponse["code"]?.jsonPrimitive?.content ?: "",
                        expiresAt = jsonResponse["expiresAt"]?.jsonPrimitive?.content ?: "",
                        clientIdentifier = jsonResponse["clientIdentifier"]?.jsonPrimitive?.content ?: ""
                    )

                    if (pinResponse.id.isNotBlank() && 
                        pinResponse.code.isNotBlank() && 
                        pinResponse.expiresAt.isNotBlank() && 
                        pinResponse.clientIdentifier.isNotBlank()) {
                        Log.d("SeerrApiService", "Successfully parsed Plex PIN response: $pinResponse")
                        ApiResult.Success(pinResponse)
                    } else {
                        val error = "Failed to parse Plex PIN response. Missing required fields." +
                                "\nID: ${pinResponse.id}" +
                                "\nCode: ${pinResponse.code}" +
                                "\nExpires At: ${pinResponse.expiresAt}" +
                                "\nClient Identifier: ${pinResponse.clientIdentifier}"
                        Log.e("SeerrApiService", error)
                        ApiResult.Error(Exception(error))
                    }
                } catch (e: Exception) {
                    Log.e("SeerrApiService", "Error parsing Plex PIN response", e)
                    Log.e("SeerrApiService", "Raw response: ${response.data.bodyAsText()}")
                    ApiResult.Error(e)
                }
            }
            is ApiResult.Error -> {
                Log.e("SeerrApiService", "Error getting Plex PIN", response.exception)
                response
            }
            is ApiResult.Loading -> response
        }
    }

    suspend fun checkPlexPinAuth(pinId: String, clientId: String): ApiResult<PlexAuthResponse> {
        val headers = mapOf(
            "X-Plex-Client-Identifier" to clientId,
            "X-Plex-Product" to "SeerrTV",
            "X-Plex-Version" to BuildConfig.VERSION_NAME,
            "X-Plex-Device" to "Android TV",
            "X-Plex-Platform" to "Android",
            "X-Plex-Platform-Version" to "15"
        )
        
        Log.d("SeerrApiService", "Checking Plex PIN auth status for PIN $pinId with:")
        Log.d("SeerrApiService", "Headers: $headers")
        
        return when (val response = executeApiCall<HttpResponse>(
            endpoint = "https://plex.tv/api/v2/pins/$pinId",
            method = HttpMethod.Get,
            raw = true,
            headers = headers
        )) {
            is ApiResult.Success -> {
                try {
                    val responseText = response.data.bodyAsText()
                    Log.d("SeerrApiService", "Received Plex auth check response: $responseText")

                    // Parse JSON response
                    val jsonResponse = json.decodeFromString<Map<String, JsonElement>>(responseText)
                    
                    // Get required fields
                    val id = jsonResponse["id"]?.jsonPrimitive?.content
                    val code = jsonResponse["code"]?.jsonPrimitive?.content
                    val clientId = jsonResponse["clientIdentifier"]?.jsonPrimitive?.content
                    val authToken = jsonResponse["authToken"]?.jsonPrimitive?.content

                    // Check if we have a valid auth token
                    if (authToken == null || authToken.isBlank() || authToken == "null" || authToken.length < 2) {
                        return ApiResult.Error(Exception("Waiting for user authentication"))
                    }

                    // Validate required fields
                    if (id.isNullOrBlank() || code.isNullOrBlank() || clientId.isNullOrBlank()) {
                        val error = "Failed to parse Plex auth response. Missing required fields." +
                                "\nID: $id" +
                                "\nCode: $code" +
                                "\nClient Identifier: $clientId"
                        Log.e("SeerrApiService", error)
                        return ApiResult.Error(Exception(error))
                    }

                    // Create response with validated fields
                    return ApiResult.Success(PlexAuthResponse(
                        id = id,
                        code = code,
                        authToken = authToken,
                        clientIdentifier = clientId
                    ))
                } catch (e: Exception) {
                    Log.e("SeerrApiService", "Error parsing Plex auth response", e)
                    Log.e("SeerrApiService", "Raw response: ${response.data.bodyAsText()}")
                    ApiResult.Error(e)
                }
            }
            is ApiResult.Error -> {
                Log.e("SeerrApiService", "Error checking Plex PIN auth", response.exception)
                response
            }
            is ApiResult.Loading -> response
        }
    }

    /**
     * Get a list of movie genres
     * @param reset Whether to reset pagination state
     * @param loadMore Whether to load more items (next page)
     * @return ApiResult containing list of genre responses
     */
    suspend fun getMovieGenres(context: Context, loadMore: Boolean = false, reset: Boolean = false): ApiResult<List<GenreResponse>> {
        val endpoint = "discover/genreslider/movie"
        
        if (reset) {
            resetPaginationState(endpoint)
            // Clear cache if resetting
            cachedMovieGenres = null
        }
        
        if (!loadMore) {
            getOrCreatePaginationState(endpoint).currentPage = 1
        } else {
            // CRITICAL FIX: Increment the page counter BEFORE fetching more data
            // This ensures we don't get the same page twice
            getOrCreatePaginationState(endpoint).currentPage++
            
            if (BuildConfig.DEBUG) {
                Log.d("SeerrApiService", "🔢 Movie genres: Incrementing page to ${getOrCreatePaginationState(endpoint).currentPage} before fetching")
            }
        }
        
        val pageState = getOrCreatePaginationState(endpoint)
        val pageSize = 8
        
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "Fetching movie genres, page ${pageState.currentPage}")
        }
        
        // If cache is empty, fetch all genres
        if (cachedMovieGenres == null) {
            val locale = SharedPreferencesUtil.getDiscoveryLanguage(context)
            val apiEndpoint = "discover/genreslider/movie"
            val localizedEndpoint = if (locale != "en") "$apiEndpoint?language=$locale" else apiEndpoint
            
            try {
                // Use executeApiCall with HttpResponse to keep all auth headers and settings
                val httpResult = executeApiCall<HttpResponse>(localizedEndpoint)

                when (httpResult) {
                    is ApiResult.Success -> {
                        val responseBody = httpResult.data.bodyAsText()
                        try {
                            // Parse the raw JSON array response
                            val genresList = json.decodeFromString<List<GenreResponse>>(responseBody)
                            cachedMovieGenres = genresList
                            Log.d("SeerrApiService", "Cached ${cachedMovieGenres?.size} movie genres")
                        } catch (e: kotlinx.serialization.SerializationException) {
                            Log.e("SeerrApiService", "Error parsing genre array: ${e.message}")
                            return ApiResult.Error(e)
                        }
                    }

                    is ApiResult.Error -> {
                        return ApiResult.Error(httpResult.exception, httpResult.statusCode)
                    }

                    else -> {
                        return ApiResult.Loading()
                    }
                }
            } catch (e: Exception) {
                Log.e("SeerrApiService", "Error fetching movie genres: ${e.message}")
                return ApiResult.Error(e)
            }
        }
        
        // Calculate pagination based on our cached data
        val allGenres = cachedMovieGenres ?: emptyList()
        val startIndex = (pageState.currentPage - 1) * pageSize
        
        if (startIndex >= allGenres.size) {
            return ApiResult.Success(emptyList(), PaginationInfo(
                currentPage = pageState.currentPage,
                totalPages = (allGenres.size + pageSize - 1) / pageSize, // Ceiling division
                totalResults = allGenres.size,
                hasMorePages = false
            ))
        }
        
        val endIndex = min(startIndex + pageSize, allGenres.size)
        val pagedGenres = allGenres.subList(startIndex, endIndex)
        
        val hasMorePages = endIndex < allGenres.size
        
        // REMOVE INCORRECT PAGE INCREMENT: We already increment at the start of the method
        // if (loadMore && hasMorePages && pagedGenres.isNotEmpty()) {
        //     if (BuildConfig.DEBUG) {
        //         Log.d("SeerrApiService", "Successfully loaded movie genres page ${pageState.currentPage}, incrementing to next page")
        //     }
        //     pageState.currentPage++
        // } else if (loadMore) {
        //     if (BuildConfig.DEBUG) {
        //         Log.d("SeerrApiService", "Not incrementing movie genres page: hasMore=$hasMorePages, results=${pagedGenres.size}")
        //     }
        // }
        
        // Log the current page info
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "🔢 Returning movie genres: page=${pageState.currentPage}, " +
                "showing items ${startIndex+1}-${endIndex} of ${allGenres.size}, " +
                "hasMore=$hasMorePages")
        }
        
        return ApiResult.Success(pagedGenres, PaginationInfo(
            currentPage = pageState.currentPage,
            totalPages = (allGenres.size + pageSize - 1) / pageSize, // Ceiling division
            totalResults = allGenres.size,
            hasMorePages = hasMorePages
        ))
    }

    /**
     * Get a list of TV genres
     * @param reset Whether to reset pagination state
     * @param loadMore Whether to load more items (next page)
     * @return ApiResult containing list of genre responses
     */
    suspend fun getTVGenres(context: Context, loadMore: Boolean = false, reset: Boolean = false): ApiResult<List<GenreResponse>> {
        val endpoint = "discover/genreslider/tv"
        
        if (reset) {
            resetPaginationState(endpoint)
            // Clear cache if resetting
            cachedTVGenres = null
        }
        
        if (!loadMore) {
            getOrCreatePaginationState(endpoint).currentPage = 1
        } else {
            // CRITICAL FIX: Increment the page counter BEFORE fetching more data
            // This ensures we don't get the same page twice
            getOrCreatePaginationState(endpoint).currentPage++
            
            if (BuildConfig.DEBUG) {
                Log.d("SeerrApiService", "🔢 TV genres: Incrementing page to ${getOrCreatePaginationState(endpoint).currentPage} before fetching")
            }
        }
        
        val pageState = getOrCreatePaginationState(endpoint)
        val pageSize = 8
        
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "Fetching TV genres, page ${pageState.currentPage}")
        }
        
        // If cache is empty, fetch all genres
        if (cachedTVGenres == null) {
            val locale = SharedPreferencesUtil.getDiscoveryLanguage(context)
            val apiEndpoint = "discover/genreslider/tv"
            val localizedEndpoint = if (locale != "en") "$apiEndpoint?language=$locale" else apiEndpoint
            
            try {
                // Use executeApiCall with HttpResponse to keep all auth headers and settings
                val httpResult = executeApiCall<HttpResponse>(localizedEndpoint)

                when (httpResult) {
                    is ApiResult.Success -> {
                        val responseBody = httpResult.data.bodyAsText()
                        try {
                            // Parse the raw JSON array response
                            val genresList = json.decodeFromString<List<GenreResponse>>(responseBody)
                            cachedTVGenres = genresList
                            Log.d("SeerrApiService", "Cached ${cachedTVGenres?.size} TV genres")
                        } catch (e: kotlinx.serialization.SerializationException) {
                            Log.e("SeerrApiService", "Error parsing genre array: ${e.message}")
                            return ApiResult.Error(e)
                        }
                    }

                    is ApiResult.Error -> {
                        return ApiResult.Error(httpResult.exception, httpResult.statusCode)
                    }

                    else -> {
                        return ApiResult.Loading()
                    }
                }
            } catch (e: Exception) {
                Log.e("SeerrApiService", "Error fetching TV genres: ${e.message}")
                return ApiResult.Error(e)
            }
        }
        
        // Calculate pagination based on our cached data
        val allGenres = cachedTVGenres ?: emptyList()
        val startIndex = (pageState.currentPage - 1) * pageSize
        
        if (startIndex >= allGenres.size) {
            return ApiResult.Success(emptyList(), PaginationInfo(
                currentPage = pageState.currentPage,
                totalPages = (allGenres.size + pageSize - 1) / pageSize, // Ceiling division
                totalResults = allGenres.size,
                hasMorePages = false
            ))
        }
        
        val endIndex = min(startIndex + pageSize, allGenres.size)
        val pagedGenres = allGenres.subList(startIndex, endIndex)
        
        val hasMorePages = endIndex < allGenres.size
        
        // REMOVE INCORRECT PAGE INCREMENT: We already increment at the start of the method
        // if (loadMore && hasMorePages && pagedGenres.isNotEmpty()) {
        //     if (BuildConfig.DEBUG) {
        //         Log.d("SeerrApiService", "Successfully loaded TV genres page ${pageState.currentPage}, incrementing to next page")
        //     }
        //     pageState.currentPage++
        // } else if (loadMore) {
        //     if (BuildConfig.DEBUG) {
        //         Log.d("SeerrApiService", "Not incrementing TV genres page: hasMore=$hasMorePages, results=${pagedGenres.size}")
        //     }
        // }
        
        // Log the current page info
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "🔢 Returning TV genres: page=${pageState.currentPage}, " +
                "showing items ${startIndex+1}-${endIndex} of ${allGenres.size}, " +
                "hasMore=$hasMorePages")
        }
        
        return ApiResult.Success(pagedGenres, PaginationInfo(
            currentPage = pageState.currentPage,
            totalPages = (allGenres.size + pageSize - 1) / pageSize, // Ceiling division
            totalResults = allGenres.size,
            hasMorePages = hasMorePages
        ))
    }

    /**
     * Fetches studios with logo images.
     * Fetches individual studio details and creates a paginated response.
     * 
     * @param reset Whether to reset the pagination state.
     * @param loadMore Whether to load the next page of studios.
     * @return A paginated list of studios with logo images.
     */
    suspend fun getStudios(reset: Boolean = false, loadMore: Boolean = false): ApiResult<List<StudioResponse>> {
        val endpoint = "studios"
        
        if (reset) {
            resetPaginationState(endpoint)
        }
        
        if (!loadMore) {
            getOrCreatePaginationState(endpoint).currentPage = 1
        }
        
        val pageState = getOrCreatePaginationState(endpoint)
        val pageSize = 6 // Display 6 items per page as per requirements
        
        // Calculate which IDs to fetch for this page
        val startIndex = (pageState.currentPage - 1) * pageSize
        if (startIndex >= studioIds.size) {
            return ApiResult.Success(emptyList(), PaginationInfo(
                currentPage = pageState.currentPage,
                totalPages = (studioIds.size + pageSize - 1) / pageSize, // Ceiling division
                totalResults = studioIds.size,
                hasMorePages = false
            ))
        }
        
        val endIndex = min(startIndex + pageSize, studioIds.size)
        val pageIds = studioIds.subList(startIndex, endIndex)
        
        // Fetch each studio's details (using cache when available)
        val pageStudios = mutableListOf<StudioResponse>()
        for (id in pageIds) {
            val cachedStudio = cachedStudios[id]
            if (cachedStudio != null) {
                pageStudios.add(cachedStudio)
            } else {
                when (val result = getStudioDetails(id)) {
                    is ApiResult.Success -> {
                        cachedStudios[id] = result.data
                        pageStudios.add(result.data)
                    }
                    is ApiResult.Error -> {
                        Log.e("SeerrApiService", "Failed to fetch studio with ID $id: ${result.exception.message}")
                        // Continue with the next ID instead of failing the whole request
                        continue
                    }
                    is ApiResult.Loading -> continue
                }
            }
        }
        
        // Update pagination state
        val totalPages = (studioIds.size + pageSize - 1) / pageSize // Ceiling division
        pageState.totalPages = totalPages
        pageState.totalResults = studioIds.size
        pageState.hasMorePages = pageState.currentPage < totalPages
        
        if (pageState.hasMorePages) {
            pageState.currentPage++
        }
        
        return ApiResult.Success(
            data = pageStudios,
            paginationInfo = pageState.toImmutable()
        )
    }

    /**
     * Fetches details for a specific studio.
     * @param studioId The ID of the studio to fetch.
     * @return Details for the requested studio.
     */
    suspend fun getStudioDetails(studioId: Int): ApiResult<StudioResponse> {
        val endpoint = "studio/$studioId"
        return executeApiCall(endpoint)
    }

    /**
     * Fetches networks with logo images.
     * Fetches individual network details and creates a paginated response.
     * 
     * @param reset Whether to reset the pagination state.
     * @param loadMore Whether to load the next page of networks.
     * @return A paginated list of networks with logo images.
     */
    suspend fun getNetworks(reset: Boolean = false, loadMore: Boolean = false): ApiResult<List<NetworkResponse>> {
        val endpoint = "networks"
        
        if (reset) {
            resetPaginationState(endpoint)
        }
        
        if (!loadMore) {
            getOrCreatePaginationState(endpoint).currentPage = 1
        }
        
        val pageState = getOrCreatePaginationState(endpoint)
        val pageSize = 6 // Display 6 items per page as per requirements
        
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "Fetching networks, page ${pageState.currentPage}")
        }
        
        // Calculate which IDs to fetch for this page
        val startIndex = (pageState.currentPage - 1) * pageSize
        if (startIndex >= networkIds.size) {
            return ApiResult.Success(emptyList(), PaginationInfo(
                currentPage = pageState.currentPage,
                totalPages = (networkIds.size + pageSize - 1) / pageSize, // Ceiling division
                totalResults = networkIds.size,
                hasMorePages = false
            ))
        }
        
        val endIndex = min(startIndex + pageSize, networkIds.size)
        val pageIds = networkIds.subList(startIndex, endIndex)
        
        // Track active network fetch requests to prevent duplicate calls
        val activeRequests = mutableMapOf<Int, ApiResult<NetworkResponse>>()
        
        // Fetch each network's details (using cache when available)
        val pageNetworks = mutableListOf<NetworkResponse>()
        for (id in pageIds) {
            // Check if this network is already in cache
            val cachedNetwork = cachedNetworks[id]
            if (cachedNetwork != null) {
                pageNetworks.add(cachedNetwork)
                continue
            }
            
            // Prevent duplicate API calls for the same network ID
            val result = if (id in activeRequests) {
                if (BuildConfig.DEBUG) {
                    Log.d("SeerrApiService", "Reusing existing request for network ID $id")
                }
                activeRequests[id]!!
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d("SeerrApiService", "Fetching details for network ID $id")
                }
                val fetchResult = getNetworkDetails(id)
                activeRequests[id] = fetchResult
                fetchResult
            }
            
            when (result) {
                is ApiResult.Success -> {
                    cachedNetworks[id] = result.data
                    pageNetworks.add(result.data)
                }
                is ApiResult.Error -> {
                    Log.e("SeerrApiService", "Failed to fetch network with ID $id: ${result.exception.message}")
                    // Continue with the next ID instead of failing the whole request
                    continue
                }
                is ApiResult.Loading -> continue
            }
        }
        
        // Update pagination state
        val totalPages = (networkIds.size + pageSize - 1) / pageSize // Ceiling division
        pageState.totalPages = totalPages
        pageState.totalResults = networkIds.size
        pageState.hasMorePages = pageState.currentPage < totalPages
        
        // Only increment the page counter after a successful API call with results
        if (pageState.hasMorePages && pageNetworks.isNotEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.d("SeerrApiService", "Successfully loaded network page ${pageState.currentPage}, incrementing to next page")
            }
            pageState.currentPage++
        } else {
            if (BuildConfig.DEBUG) {
                Log.d("SeerrApiService", "Not incrementing network page: hasMore=${pageState.hasMorePages}, results=${pageNetworks.size}")
            }
        }
        
        return ApiResult.Success(
            data = pageNetworks,
            paginationInfo = pageState.toImmutable()
        )
    }

    /**
     * Fetches details for a specific network.
     * @param networkId The ID of the network to fetch.
     * @return Details for the requested network.
     */
    suspend fun getNetworkDetails(networkId: Int): ApiResult<NetworkResponse> {
        val endpoint = "network/$networkId"
        return executeApiCall(endpoint)
    }

    /**
     * Discovers movies by genre.
     * @param genreId The ID of the genre to search for.
     * @param loadMore Whether to load the next page of results.
     * @return A list of movies matching the specified genre.
     */
    suspend fun discoverMoviesByGenre(genreId: String, loadMore: Boolean = false): ApiResult<Discover> {
        if (!loadMore) {
            resetPaginationState("movie_genre")
        }
        val state = getOrCreatePaginationState("movie_genre")
        val locale = SharedPreferencesUtil.getDiscoveryLanguage(context)
        // Build the endpoint string with parameters
        val endpoint = "discover/movies"
        val pageEndpoint = "$endpoint?genre=$genreId&page=${state.currentPage}"
        val localizedEndpoint = if (locale != "en") "${pageEndpoint}&language=${locale}" else pageEndpoint
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "🔄 Fetching movies for genre $genreId, page ${state.currentPage}")
        }
        return when (val result = executeApiCall<Discover>(localizedEndpoint)) {
            is ApiResult.Success -> {
                // Update pagination info
                state.totalPages = result.data.totalPages
                state.totalResults = result.data.totalResults
                state.hasMorePages = state.currentPage < state.totalPages
                
                // Only increment the page counter after a successful API call with results
                if (state.hasMorePages && result.data.results.isNotEmpty()) {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "✅ Successfully loaded page ${state.currentPage}, incrementing to next page")
                    }
                    state.currentPage++  // Increment by 1
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "⚠️ Not incrementing page: hasMore=${state.hasMorePages}, results=${result.data.results.size}")
                    }
                }
                
                ApiResult.Success(result.data, state.toImmutable())
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }

    /**
     * Discovers TV series by genre.
     * @param genreId The ID of the genre to search for.
     * @param loadMore Whether to load the next page of results.
     * @return A list of TV series matching the specified genre.
     */
    suspend fun discoverTVByGenre(genreId: String, loadMore: Boolean = false): ApiResult<Discover> {
        if (!loadMore) {
            resetPaginationState("tv_genre")
        }
        val state = getOrCreatePaginationState("tv_genre")
        val locale = SharedPreferencesUtil.getDiscoveryLanguage(context)
        // Build the endpoint string with parameters
        val endpoint = "discover/tv"
        val pageEndpoint = "$endpoint?genre=$genreId&page=${state.currentPage}"
        val localizedEndpoint = if (locale != "en") "${pageEndpoint}&language=$locale" else pageEndpoint
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "Fetching TV shows for genre $genreId, page ${state.currentPage}")
        }
        return when (val result = executeApiCall<Discover>(localizedEndpoint)) {
            is ApiResult.Success -> {
                // Update pagination info
                state.totalPages = result.data.totalPages
                state.totalResults = result.data.totalResults
                state.hasMorePages = state.currentPage < state.totalPages
                
                // Only increment the page counter after a successful API call with results
                if (state.hasMorePages && result.data.results.isNotEmpty()) {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Successfully loaded TV genre page ${state.currentPage}, incrementing to next page")
                    }
                    state.currentPage++
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Not incrementing TV genre page: hasMore=${state.hasMorePages}, results=${result.data.results.size}")
                    }
                }
                
                ApiResult.Success(result.data, state.toImmutable())
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }

    /**
     * Discovers movies by studio.
     * @param studioId The ID of the studio to search for.
     * @param loadMore Whether to load the next page of results.
     * @return A list of movies from the specified studio.
     */
    suspend fun discoverMoviesByStudio(studioId: String, loadMore: Boolean = false): ApiResult<Discover> {
        if (!loadMore) {
            resetPaginationState("studio")
        }
        val state = getOrCreatePaginationState("studio")
        val locale = SharedPreferencesUtil.getDiscoveryLanguage(context)
        // Build the endpoint string with parameters
        val endpoint = "discover/movies/studio/$studioId"
        val pageEndpoint = "$endpoint?page=${state.currentPage}"
        val localizedEndpoint = if (locale != "en") "${pageEndpoint}&language=$locale" else pageEndpoint
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "Fetching movies for studio $studioId, page ${state.currentPage}")
        }
        return when (val result = executeApiCall<Discover>(localizedEndpoint)) {
            is ApiResult.Success -> {
                // Update pagination info
                state.totalPages = result.data.totalPages
                state.totalResults = result.data.totalResults
                state.hasMorePages = state.currentPage < state.totalPages
                
                // Only increment the page counter after a successful API call with results
                if (state.hasMorePages && result.data.results.isNotEmpty()) {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Successfully loaded studio page ${state.currentPage}, incrementing to next page")
                    }
                    state.currentPage++
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Not incrementing studio page: hasMore=${state.hasMorePages}, results=${result.data.results.size}")
                    }
                }
                
                ApiResult.Success(result.data, state.toImmutable())
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }

    /**
     * Discovers TV series by network.
     * @param networkId The ID of the network to search for.
     * @param loadMore Whether to load the next page of results.
     * @return A list of TV series from the specified network.
     */
    suspend fun discoverTVByNetwork(networkId: String, loadMore: Boolean = false): ApiResult<Discover> {
        if (!loadMore) {
            resetPaginationState("network")
        }
        val state = getOrCreatePaginationState("network")
        val locale = SharedPreferencesUtil.getDiscoveryLanguage(context)
        // Build the endpoint string with parameters
        val endpoint = "discover/tv/network/$networkId"
        val pageEndpoint = "$endpoint?page=${state.currentPage}"
        val localizedEndpoint = if (locale != "en") "${pageEndpoint}&language=$locale" else pageEndpoint
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "Fetching TV shows for network $networkId, page ${state.currentPage}")
        }
        return when (val result = executeApiCall<Discover>(localizedEndpoint)) {
            is ApiResult.Success -> {
                // Update pagination info
                state.totalPages = result.data.totalPages
                state.totalResults = result.data.totalResults
                state.hasMorePages = state.currentPage < state.totalPages
                
                // Only increment the page counter after a successful API call with results
                if (state.hasMorePages && result.data.results.isNotEmpty()) {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Successfully loaded network page ${state.currentPage}, incrementing to next page")
                    }
                    state.currentPage++
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("SeerrApiService", "Not incrementing network page: hasMore=${state.hasMorePages}, results=${result.data.results.size}")
                    }
                }
                
                ApiResult.Success(result.data, state.toImmutable())
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }

    @Serializable
    private data class PublicSettingsResponse(
        @SerialName("mediaServerType") val mediaServerType: Int? = null
    )

    @Serializable
    private data class OverseerrPlexSettingsResponse(
        val name: String? = null,
        val ip: String? = null,
        val port: Int? = null,
        val useSsl: Boolean? = null,
        val libraries: List<JsonElement>? = null,
        val machineId: String? = null
    )

    suspend fun detectMediaServerType(): MediaServerType = withContext(Dispatchers.IO) {
        Log.d("SeerrApiService", "Detecting media server type for server type: $serverType")
        
        when (serverType) {
            ServerType.JELLYSEERR -> {
                Log.d("SeerrApiService", "Jellyseerr detected, checking /settings/public...")
                when (val result = executeApiCall<PublicSettingsResponse>("settings/public")) {
                    is ApiResult.Success -> {
                        val type = when (result.data.mediaServerType) {
                            1 -> {
                                Log.d("SeerrApiService", "Media server type 1 detected → PLEX")
                                MediaServerType.PLEX
                            }
                            2 -> {
                                Log.d("SeerrApiService", "Media server type 2 detected → JELLYFIN")
                                MediaServerType.JELLYFIN
                            }
                            3 -> {
                                Log.d("SeerrApiService", "Media server type 3 detected → EMBY")
                                MediaServerType.EMBY
                            }
                            else -> {
                                Log.d("SeerrApiService", "Unknown media server type: ${result.data.mediaServerType} → NOT_CONFIGURED")
                                MediaServerType.NOT_CONFIGURED
                            }
                        }
                        mediaServerType = type
                        SharedPreferencesUtil.saveMediaServerType(context, type)
                        Log.d("SeerrApiService", "Final detected media server type: $type")
                        type
                    }
                    is ApiResult.Error -> {
                        Log.e("SeerrApiService", "Failed to get /settings/public: ${result.exception.message}")
                        mediaServerType = MediaServerType.NOT_CONFIGURED
                        SharedPreferencesUtil.saveMediaServerType(context, MediaServerType.NOT_CONFIGURED)
                        MediaServerType.NOT_CONFIGURED
                    }
                    is ApiResult.Loading -> {
                        mediaServerType = MediaServerType.NOT_CONFIGURED
                        SharedPreferencesUtil.saveMediaServerType(context, MediaServerType.NOT_CONFIGURED)
                        MediaServerType.NOT_CONFIGURED
                    }
                }
            }
            ServerType.SEERR -> {
                Log.d("SeerrApiService", "Seerr detected, checking /settings/public...")
                when (val result = executeApiCall<PublicSettingsResponse>("settings/public")) {
                    is ApiResult.Success -> {
                        val type = when (result.data.mediaServerType) {
                            1 -> {
                                Log.d("SeerrApiService", "Media server type 1 detected → PLEX")
                                MediaServerType.PLEX
                            }
                            2 -> {
                                Log.d("SeerrApiService", "Media server type 2 detected → JELLYFIN")
                                MediaServerType.JELLYFIN
                            }
                            3 -> {
                                Log.d("SeerrApiService", "Media server type 3 detected → EMBY")
                                MediaServerType.EMBY
                            }
                            else -> {
                                Log.d("SeerrApiService", "Unknown media server type: ${result.data.mediaServerType} → NOT_CONFIGURED")
                                MediaServerType.NOT_CONFIGURED
                            }
                        }
                        mediaServerType = type
                        SharedPreferencesUtil.saveMediaServerType(context, type)
                        Log.d("SeerrApiService", "Final detected media server type: $type")
                        type
                    }
                    is ApiResult.Error -> {
                        Log.e("SeerrApiService", "Failed to get /settings/public: ${result.exception.message}")
                        mediaServerType = MediaServerType.NOT_CONFIGURED
                        SharedPreferencesUtil.saveMediaServerType(context, MediaServerType.NOT_CONFIGURED)
                        MediaServerType.NOT_CONFIGURED
                    }
                    is ApiResult.Loading -> {
                        mediaServerType = MediaServerType.NOT_CONFIGURED
                        SharedPreferencesUtil.saveMediaServerType(context, MediaServerType.NOT_CONFIGURED)
                        MediaServerType.NOT_CONFIGURED
                    }
                }
            }
            ServerType.OVERSEERR -> {
                Log.d("SeerrApiService", "Overseerr detected, checking /settings/plex...")
                when (val result = executeApiCall<OverseerrPlexSettingsResponse>("settings/plex")) {
                    is ApiResult.Success -> {
                        // Check if Plex is configured by looking for required fields
                        val isPlexConfigured = !result.data.name.isNullOrBlank() && 
                                             !result.data.ip.isNullOrBlank() && 
                                             result.data.port != null && 
                                             !result.data.machineId.isNullOrBlank()
                        
                        val type = if (isPlexConfigured) {
                            MediaServerType.PLEX
                        } else {
                            MediaServerType.NOT_CONFIGURED
                        }
                        
                        mediaServerType = type
                        SharedPreferencesUtil.saveMediaServerType(context, type)
                        Log.d("SeerrApiService", "Detected media server type: $type (Plex configured: $isPlexConfigured)")
                        type
                    }
                    is ApiResult.Error -> {
                        Log.d("SeerrApiService", "Non-200 response from /settings/plex (${result.statusCode}): ${result.exception.message} - treating as Plex not configured")
                        mediaServerType = MediaServerType.NOT_CONFIGURED
                        SharedPreferencesUtil.saveMediaServerType(context, MediaServerType.NOT_CONFIGURED)
                        MediaServerType.NOT_CONFIGURED
                    }
                    is ApiResult.Loading -> {
                        mediaServerType = MediaServerType.NOT_CONFIGURED
                        SharedPreferencesUtil.saveMediaServerType(context, MediaServerType.NOT_CONFIGURED)
                        MediaServerType.NOT_CONFIGURED
                    }
                }
            }
            else -> {
                Log.w("SeerrApiService", "Unknown server type: $serverType, defaulting to NOT_CONFIGURED")
                mediaServerType = MediaServerType.NOT_CONFIGURED
                SharedPreferencesUtil.saveMediaServerType(context, MediaServerType.NOT_CONFIGURED)
                MediaServerType.NOT_CONFIGURED
            }
        }
    }

    /**
     * Proactively check and refresh the authentication token if needed.
     * This is useful when the app resumes after being idle for a long time.
     * @return ApiResult indicating whether the token is valid or was refreshed
     */
    suspend fun checkAndRefreshTokenIfNeeded(): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!isSessionBasedAuth(config.getAuthType())) {
            // API key auth doesn't need token refresh
            return@withContext ApiResult.Success(Unit)
        }

        try {
            // If we don't have a token, try to login
            if (currentAuthToken == null) {
                Log.d("SeerrApiService", "No auth token found, attempting login...")
                return@withContext login()
            }

            // Check if token is expired or about to expire
            val now = Instant.now()
            if (now.isAfter(currentAuthToken!!.expiresAt)) {
                Log.d("SeerrApiService", "Auth token has expired, refreshing...")
                currentAuthToken = null
                return@withContext login()
            } else if (now.isAfter(currentAuthToken!!.expiresAt.minus(1, ChronoUnit.HOURS))) {
                Log.d("SeerrApiService", "Auth token is about to expire, refreshing...")
                currentAuthToken = null
                return@withContext login()
            }

            // Test the current token to make sure it's still valid
            Log.d("SeerrApiService", "Testing current auth token...")
            when (val testResult = executeApiCall<AuthMeResponse>("auth/me")) {
                is ApiResult.Success -> {
                    Log.d("SeerrApiService", "Current auth token is valid")
                    // Update user info
                    val user = testResult.data
                    currentUserInfo = UserInfo(
                        id = user.id,
                        displayName = user.displayName,
                        permissions = user.permissions ?: 0
                    )
                    ApiResult.Success(Unit)
                }
                is ApiResult.Error -> {
                    Log.d("SeerrApiService", "Current auth token is invalid, refreshing...")
                    currentAuthToken = null
                    login()
                }
                is ApiResult.Loading -> {
                    Log.e("SeerrApiService", "Unexpected loading state during token validation")
                    ApiResult.Error(Exception("Unexpected loading state"))
                }
            }
        } catch (e: Exception) {
            Log.e("SeerrApiService", "Error checking/refreshing token", e)
            ApiResult.Error(e)
        }
    }

    /**
     * Lookup TV series in Sonarr by TMDB ID
     * Used when mediaDetails doesn't have a tvdbId and we need to find the correct match
     */
    suspend fun lookupSonarrSeries(tmdbId: Int): ApiResult<List<SonarrLookupResult>> {
        return executeApiCall("service/sonarr/lookup/$tmdbId")
    }

    /**
     * Browse media with filters and sorting
     * Maps MediaFilters and SortOption to Jellyseerr/TMDB discover endpoint parameters
     */
    suspend fun browseMedia(
        mediaType: MediaType,
        filters: BrowseModels.MediaFilters,
        sort: BrowseModels.SortOption,
        query: String = "",
        loadMore: Boolean = false
    ): ApiResult<Discover> {
        if (!loadMore) {
            val cacheKey = "browse_${mediaType.name.lowercase()}_${filters.hashCode()}_${sort.hashCode()}_${query.hashCode()}"
            resetPaginationState(cacheKey)
        }
        
        val state = getOrCreatePaginationState("browse_${mediaType.name.lowercase()}_${filters.hashCode()}_${sort.hashCode()}_${query.hashCode()}")
        val locale = SharedPreferencesUtil.getDiscoveryLanguage(context)
        
        // Build the base endpoint
        val baseEndpoint = when (mediaType) {
            MediaType.MOVIE -> "discover/movies"
            MediaType.TV -> "discover/tv"
        }
        
        // Build query parameters
        val params = mutableListOf<String>()
        
        // Add page parameter
        params.add("page=${state.currentPage}")
        
        // Add language if not English
        if (locale != "en") {
            params.add("language=$locale")
        }
        
        // Add text search if provided
        if (query.isNotEmpty()) {
            params.add("query=$query")
        }
        
        // Add date filters
        filters.releaseFrom?.let { params.add("${if (mediaType == MediaType.MOVIE) "primaryReleaseDateGte" else "firstAirDateGte"}=$it") }
        filters.releaseTo?.let { params.add("${if (mediaType == MediaType.MOVIE) "primaryReleaseDateLte" else "firstAirDateLte"}=$it") }
        
        // Add genre filters (using comma for multiple genres, URL encoded)
    if (filters.genres.isNotEmpty()) {
        val genreValue = filters.genres.joinToString(",")
        params.add("genre=${genreValue.encodeForOverseerr()}")
    }
    
    // Add keyword filters (using comma for multiple keywords, URL encoded)
    // Note: Jellyseerr uses 'keywords' not 'with_keywords' (TMDB uses with_keywords)
    if (filters.keywords.isNotEmpty()) {
        val keywordValue = filters.keywords.joinToString(",")
        params.add("keywords=${keywordValue.encodeForOverseerr()}")
    }
    
    // Add original language filter (format: language={code})
    filters.originalLanguage?.let { 
        val languageValue = "server|$it"
        params.add("language=${languageValue.encodeForOverseerr()}")
    }
    
    // Add content rating filters (certification)
    if (filters.contentRatings.isNotEmpty()) {
        // Always specify certification country, defaulting to US if not set
        val regionParam = filters.region ?: "US"
        params.add("certificationCountry=$regionParam")
        val certificationValue = filters.contentRatings.joinToString("|")
        params.add("certification=${certificationValue.encodeForOverseerr()}")
        params.add("certificationMode=exact")
    }
    
    // Add runtime filters
    filters.runtimeMin?.let { params.add("withRuntimeGte=$it") }
    filters.runtimeMax?.let { params.add("withRuntimeLte=$it") }
    
    // Add user score filters
    filters.userScoreMin?.let { params.add("voteAverageGte=$it") }
    filters.userScoreMax?.let { params.add("voteAverageLte=$it") }
    
    // Add vote count filters
    filters.voteCountMin?.let { params.add("voteCountGte=$it") }
    filters.voteCountMax?.let { params.add("voteCountLte=$it") }
    
    // Add studio/network filters
    if (mediaType == MediaType.MOVIE && filters.studio != null) {
        // Jellyseerr uses 'studio' parameter (singular) - only one studio allowed
        params.add("studio=${filters.studio}")
    } else if (mediaType == MediaType.TV && filters.networks.isNotEmpty()) {
        params.add("with_networks=${filters.networks.joinToString("|")}")
    }
    
    // Add watch provider filters
    if (filters.watchProviders.isNotEmpty()) {
        val providerValue = filters.watchProviders.joinToString("|")
        params.add("watchProviders=${providerValue.encodeForOverseerr()}")
        // Use watchRegion if set, otherwise default to US
        val regionValue = filters.watchRegion ?: "US"
        params.add("watchRegion=$regionValue")
    }
    
    // Add sort parameter
    // Note: Jellyseerr uses 'sortBy' (camelCase), TMDB uses 'sort_by' (snake_case)
    params.add("sortBy=${sort.apiParameter}")
        
        // Build final endpoint
        val endpoint = if (params.isNotEmpty()) {
            "$baseEndpoint?${params.joinToString("&")}"
        } else {
            baseEndpoint
        }
        
        if (BuildConfig.DEBUG) {
            Log.d("SeerrApiService", "Browse query: $endpoint")
        }
        
        return when (val result = executeApiCall<Discover>(endpoint)) {
            is ApiResult.Success -> {
                val data = result.data
                
                // Update pagination state
                state.totalPages = data.totalPages
                state.totalResults = data.totalResults
                state.hasMorePages = state.currentPage < state.totalPages
                
                // Only increment the page counter after a successful API call with results
                if (state.hasMorePages && data.results.isNotEmpty()) {
                    state.currentPage++
                }
                
                if (BuildConfig.DEBUG) {
                    Log.d("SeerrApiService", "Browse results: page ${state.currentPage}/${state.totalPages}, " +
                        "results=${data.results.size}, hasMore=${state.hasMorePages}")
                }
                
                ApiResult.Success(data, state.toImmutable())
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }

    /**
     * Get available regions for watch providers.
     */
    suspend fun getRegions(): ApiResult<List<Region>> {
        return executeApiCall<List<Region>>("watchproviders/regions").mapData { regions ->
            regions.sortedBy { it.english_name }
        }
    }

    /**
     * Get available watch providers for a specific media type and region.
     */
    suspend fun getWatchProviders(mediaType: MediaType, region: String): ApiResult<List<Provider>> {
        val endpoint = when (mediaType) {
            MediaType.MOVIE -> "watchproviders/movies?watchRegion=$region"
            MediaType.TV -> "watchproviders/tv?watchRegion=$region"
        }
        return executeApiCall<List<Provider>>(endpoint)
    }

    /**
     * Get available languages.
     * Currently returns a static list of common languages as there is no direct API endpoint.
     */
    fun getLanguages(): List<ca.devmesh.seerrtv.model.FilterLanguage> {
        return listOf(
            ca.devmesh.seerrtv.model.FilterLanguage("en", "English", "English"),
            ca.devmesh.seerrtv.model.FilterLanguage("fr", "French", "Français"),
            ca.devmesh.seerrtv.model.FilterLanguage("es", "Spanish", "Español"),
            ca.devmesh.seerrtv.model.FilterLanguage("de", "German", "Deutsch"),
            ca.devmesh.seerrtv.model.FilterLanguage("it", "Italian", "Italiano"),
            ca.devmesh.seerrtv.model.FilterLanguage("pt", "Portuguese", "Português"),
            ca.devmesh.seerrtv.model.FilterLanguage("ru", "Russian", "Pусский"),
            ca.devmesh.seerrtv.model.FilterLanguage("ja", "Japanese", "日本語"),
            ca.devmesh.seerrtv.model.FilterLanguage("ko", "Korean", "한국어"),
            ca.devmesh.seerrtv.model.FilterLanguage("zh", "Chinese", "中文"),
            ca.devmesh.seerrtv.model.FilterLanguage("hi", "Hindi", "हिन्दी"),
            ca.devmesh.seerrtv.model.FilterLanguage("ar", "Arabic", "العربية"),
            ca.devmesh.seerrtv.model.FilterLanguage("tr", "Turkish", "Türkçe"),
            ca.devmesh.seerrtv.model.FilterLanguage("nl", "Dutch", "Nederlands"),
            ca.devmesh.seerrtv.model.FilterLanguage("sv", "Swedish", "Svenska"),
            ca.devmesh.seerrtv.model.FilterLanguage("da", "Danish", "Dansk"),
            ca.devmesh.seerrtv.model.FilterLanguage("no", "Norwegian", "Norsk"),
            ca.devmesh.seerrtv.model.FilterLanguage("fi", "Finnish", "Suomi"),
            ca.devmesh.seerrtv.model.FilterLanguage("pl", "Polish", "Polski"),
            ca.devmesh.seerrtv.model.FilterLanguage("cs", "Czech", "Čeština"),
            ca.devmesh.seerrtv.model.FilterLanguage("hu", "Hungarian", "Magyar"),
            ca.devmesh.seerrtv.model.FilterLanguage("el", "Greek", "Ελληνικά"),
            ca.devmesh.seerrtv.model.FilterLanguage("he", "Hebrew", "עִבְרִית"),
            ca.devmesh.seerrtv.model.FilterLanguage("th", "Thai", "ภาษาไทย"),
            ca.devmesh.seerrtv.model.FilterLanguage("vi", "Vietnamese", "Tiếng Việt"),
            ca.devmesh.seerrtv.model.FilterLanguage("id", "Indonesian", "Bahasa Indonesia")
        ).sortedBy { it.english_name }
    }

    /**
     * Get content ratings.
     * Returns a static list of standard certifications.
     */
    fun getContentRatings(): List<ContentRating> {
        return listOf(
            ContentRating("G", "General Audiences", 1),
            ContentRating("PG", "Parental Guidance Suggested", 2),
            ContentRating("PG-13", "Parents Strongly Cautioned", 3),
            ContentRating("R", "Restricted", 4),
            ContentRating("NC-17", "Adults Only", 5),
            ContentRating("TV-Y", "All Children", 1),
            ContentRating("TV-Y7", "Directed to Older Children", 2),
            ContentRating("TV-G", "General Audience", 3),
            ContentRating("TV-PG", "Parental Guidance Suggested", 4),
            ContentRating("TV-14", "Parents Strongly Cautioned", 5),
            ContentRating("TV-MA", "Mature Audience Only", 6)
        )
    }

    /**
     * Search for keywords.
     * Uses the specific keyword search endpoint if available, or falls back to general search.
     * Note: Jellyseerr/Overseerr usually proxies TMDB, so we try to hit a keyword search endpoint.
     * If that fails, we might need to adjust.
     */
    suspend fun searchKeywords(query: String): ApiResult<List<Keyword>> {
        if (query.isBlank()) return ApiResult.Success(emptyList())
        
        // Try the standard TMDB-like endpoint which Overseerr often exposes
        // If this 404s, we might need to check if there's another way.
        // Based on research, Overseerr proxies TMDB search.
        val encodedQuery = query.encodeForOverseerr()
        return executeApiCall<PaginatedResponse<Keyword>>("search/keyword?query=$encodedQuery").mapData { it.results }
    }

    /**
     * Search for studios/companies.
     * Uses the company search endpoint to find studios by name.
     */
    suspend fun searchStudios(query: String): ApiResult<List<CompanySearchResult>> {
        if (query.isBlank()) return ApiResult.Success(emptyList())
        
        val encodedQuery = query.encodeForOverseerr()
        return executeApiCall<PaginatedResponse<CompanySearchResult>>("search/company?query=$encodedQuery").mapData { it.results }
    }

    @Serializable
    data class PaginatedResponse<T>(
        val page: Int,
        val results: List<T>,
        val total_pages: Int,
        val total_results: Int
    )

}