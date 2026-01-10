package ca.devmesh.seerrtv

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.Alignment
import android.content.Context
import androidx.navigation.compose.composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.data.SeerrApiService.ApiValidationResult
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.data.TrustAllCerts
import ca.devmesh.seerrtv.navigation.LocalNavController
import ca.devmesh.seerrtv.navigation.rememberNavigationManager
import ca.devmesh.seerrtv.navigation.rememberNavigationCoordinator
import ca.devmesh.seerrtv.theme.SeerrTVTheme
import ca.devmesh.seerrtv.ui.ConfigScreen
import ca.devmesh.seerrtv.ui.MainScreen
import ca.devmesh.seerrtv.ui.MediaDetails
import ca.devmesh.seerrtv.ui.MediaDiscoveryScreen
import ca.devmesh.seerrtv.ui.MediaBrowseScreen
import ca.devmesh.seerrtv.ui.PersonScreen
import ca.devmesh.seerrtv.ui.SplashScreen
import ca.devmesh.seerrtv.ui.components.AuthenticationErrorHandler
import ca.devmesh.seerrtv.ui.components.MainTopBar
import ca.devmesh.seerrtv.ui.focus.rememberAppFocusManager
import ca.devmesh.seerrtv.ui.focus.AppFocusState
import ca.devmesh.seerrtv.ui.focus.TopBarFocus
import ca.devmesh.seerrtv.ui.focus.DpadController
import ca.devmesh.seerrtv.ui.focus.rememberDpadController
import ca.devmesh.seerrtv.ui.SettingsMenu
import ca.devmesh.seerrtv.ui.focus.AppFocusManager
import ca.devmesh.seerrtv.ui.components.TopBarController
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import ca.devmesh.seerrtv.util.UpdateInfo
import ca.devmesh.seerrtv.util.checkForUpdateIfAvailable
import ca.devmesh.seerrtv.model.MediaType
import ca.devmesh.seerrtv.viewmodel.DiscoveryType
import ca.devmesh.seerrtv.viewmodel.MediaCategory
import ca.devmesh.seerrtv.viewmodel.MediaDiscoveryViewModel
import ca.devmesh.seerrtv.viewmodel.PersonViewModel
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel
import ca.devmesh.seerrtv.util.LocaleContextWrapper
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

// Define loading step data class
data class LoadingStep(val message: String, val type: LoadingStepType)
enum class LoadingStepType { INFO, WARNING, SUCCESS, ERROR }

@HiltAndroidApp
class SeerrTV : Application() {
    companion object {
        lateinit var imageLoader: ImageLoader
    }

    override fun onCreate() {
        super.onCreate()

        // Create OkHttpClient with SSL bypass
        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(
                TrustAllCerts.createSSLSocketFactory()!!,
                TrustAllCerts.trustAllCerts[0]
            )
            .hostnameVerifier { _, _ -> true }
            .build()

        // Create optimized OkHttp client with concurrency controls
        val optimizedOkHttpClient = okHttpClient.newBuilder()
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    // Limit concurrent requests - crucial for TV devices
                    maxRequests = 8          // Max 8 total concurrent requests
                    maxRequestsPerHost = 4   // Max 4 per host (TMDB)
                }
            )
            .build()

        imageLoader = ImageLoader.Builder(this)
            .components {
                add(OkHttpNetworkFetcherFactory(optimizedOkHttpClient))
            }
            .build()
    }
}

// Define top bar focus state data class
data class TopBarFocusState(
    val isInTopBar: Boolean,
    val isSearchFocused: Boolean,
    val isMoviesFocused: Boolean,
    val isSeriesFocused: Boolean,
    val isSettingsFocused: Boolean,
    val showRefreshHint: Boolean,
    val isRefreshRowVisible: Boolean
)

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        // Enforce the selected app language (resolving migration if needed)
        super.attachBaseContext(LocaleContextWrapper.wrap(newBase))
    }

    @Inject
    lateinit var apiService: SeerrApiService
    private var isConfigured by mutableStateOf(false)
    private var showSplash by mutableStateOf(true)
    private var apiValidationError by mutableStateOf<String?>(null)
    private var loadingSteps by mutableStateOf<List<LoadingStep>>(emptyList())
    private var showConnectionErrorDialog by mutableStateOf(false)
    private var isAuthError by mutableStateOf(false)
    private var isAuthenticationComplete by mutableStateOf(false)
    private var showUpdateDialog by mutableStateOf(false)
    private var updateInfoForDialog by mutableStateOf<UpdateInfo?>(null)
    private val activityScope = MainScope()
    private val sharedViewModel: SeerrViewModel by viewModels()
    private val sharedDiscoveryViewModel: MediaDiscoveryViewModel by viewModels()
    private val sharedPersonViewModel: PersonViewModel by viewModels()

    // Lifecycle observer for token refresh
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                Log.d("MainActivity", "App resumed, checking token refresh...")
                activityScope.launch {
                    try {
                        // Avoid triggering token refresh while splash/update check is active
                        if (isConfigured && !showSplash) {
                            when (val result = apiService.checkAndRefreshTokenIfNeeded()) {
                                is ApiResult.Success<*> -> {
                                    Log.d(
                                        "MainActivity",
                                        "Token check/refresh completed successfully"
                                    )
                                }

                                is ApiResult.Error -> {
                                    Log.w(
                                        "MainActivity",
                                        "Token refresh failed: ${result.exception.message}"
                                    )
                                    // Check if it's an authentication error (401/403)
                                    if (result.exception.message?.contains("403") == true ||
                                        result.exception.message?.contains("401") == true ||
                                        result.exception.message?.contains("Unable to authenticate") == true
                                    ) {
                                        Log.e(
                                            "MainActivity",
                                            "Authentication error during token refresh, showing auth modal"
                                        )
                                        handleConnectionError(isAuthenticationError = true)
                                    }
                                    // Don't show error to user unless it's a critical failure
                                    // The automatic retry logic in executeApiCall will handle most cases
                                }

                                is ApiResult.Loading -> {
                                    Log.d("MainActivity", "Token refresh in progress...")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error during token refresh on resume", e)
                    }
                }
            }

            else -> { /* Handle other lifecycle events if needed */
            }
        }
    }

    private fun updateConfigurationState() {
        isConfigured = SharedPreferencesUtil.hasApiConfig(this)
        Log.d("MainActivity", "Configuration state updated: isConfigured=$isConfigured")

        // Double-check that the configuration is valid and complete
        val config = SharedPreferencesUtil.getConfig(this)
        if (config != null) {
            if (!config.isSubmitted || config.hostname.isEmpty() || config.authType.isEmpty()) {
                Log.d("MainActivity", "Found incomplete configuration, clearing it")
                SharedPreferencesUtil.clearConfig(this)
                isConfigured = false
            }
        }
    }

    private fun handleConnectionError(isAuthenticationError: Boolean = false) {
        isAuthError = isAuthenticationError
        showConnectionErrorDialog = true
    }

    private suspend fun addLoadingStep(
        message: String,
        type: LoadingStepType = LoadingStepType.INFO
    ) {
        loadingSteps = loadingSteps + LoadingStep(message, type)
        Log.d("MainActivity", "Loading step: $message (${type.name})")

        // Custom delay based on message type
        val delayMs = when (type) {
            LoadingStepType.ERROR -> 5000L    // Errors stay longer for visibility
            LoadingStepType.WARNING -> 2000L // Warning messages get a moderate delay
            LoadingStepType.SUCCESS -> 800L   // Success messages get moderate delay
            LoadingStepType.INFO -> 300L    // Info messages get short delay
        }
        delay(delayMs)
    }

    private suspend fun validateConfiguration() {
        Log.d("MainActivity", "🔍 Starting validateConfiguration()")
        // Don't reset loading steps here - preserve any existing messages like "Checking for Updates..."

        addLoadingStep(getString(R.string.splashScreen_loadingConfiguration))
        val config = SharedPreferencesUtil.getConfig(this)
        if (config != null) {
            Log.d("MainActivity", "📋 Config found, updating API service")
            try {
                apiService.updateConfig(config)
                // Small delay to ensure HTTP client is fully ready after refresh
                kotlinx.coroutines.delay(100)
                addLoadingStep(getString(R.string.splashScreen_checkingConnection))

                Log.d("MainActivity", "🔗 Testing base connection...")
                // Test base connection first
                when (val connectionResult = apiService.testBaseConnection()) {
                    is ApiValidationResult.Success -> {
                        Log.d(
                            "MainActivity",
                            "✅ Base connection successful, testing authentication..."
                        )
                        addLoadingStep(getString(R.string.splashScreen_authenticating))

                        // Now test authentication
                        when (val authResult = apiService.testAuthentication()) {
                            is ApiValidationResult.Success -> {
                                Log.d(
                                    "MainActivity",
                                    "✅ Authentication test completed successfully"
                                )
                                apiValidationError = null
                                isAuthError = false
                                // Load server configurations with detailed feedback
                                addLoadingStep(getString(R.string.splashScreen_loadingSonarrConfig))
                                apiService.loadSonarrConfiguration()

                                // Get Sonarr server counts and add to loading steps
                                val sonarrData = apiService.getCachedSonarrData()
                                when {
                                    sonarrData?.error != null -> {
                                        val errMsg =
                                            "Sonarr configuration error: ${sonarrData.error.message ?: "Unknown error"}"
                                        addLoadingStep(errMsg, LoadingStepType.ERROR)
                                    }

                                    sonarrData?.allServers?.isNotEmpty() == true -> {
                                        val hdCount =
                                            sonarrData.allServers.count { !it.server.is4k }
                                        val fourKCount =
                                            sonarrData.allServers.count { it.server.is4k }
                                        val totalServers = sonarrData.allServers.size
                                        if (fourKCount > 0) {
                                            addLoadingStep(
                                                getString(
                                                    R.string.splashScreen_sonarrConfigComplete,
                                                    totalServers,
                                                    hdCount,
                                                    fourKCount
                                                ),
                                                LoadingStepType.SUCCESS
                                            )
                                        } else {
                                            addLoadingStep(
                                                resources.getQuantityString(
                                                    R.plurals.splashScreen_sonarrConfigComplete_basic,
                                                    totalServers,
                                                    totalServers
                                                ),
                                                LoadingStepType.SUCCESS
                                            )
                                        }
                                    }

                                    else -> {
                                        // Successful call but no servers returned
                                        addLoadingStep(
                                            getString(R.string.splashScreen_noSonarrServersInfo),
                                            LoadingStepType.INFO
                                        )
                                    }
                                }

                                addLoadingStep(getString(R.string.splashScreen_loadingRadarrConfig))
                                apiService.loadRadarrConfiguration()

                                // Get Radarr server counts and add to loading steps
                                val radarrData = apiService.getCachedRadarrData()
                                when {
                                    radarrData?.error != null -> {
                                        val errMsg =
                                            "Radarr configuration error: ${radarrData.error.message ?: "Unknown error"}"
                                        addLoadingStep(errMsg, LoadingStepType.ERROR)
                                    }

                                    radarrData?.allServers?.isNotEmpty() == true -> {
                                        val hdCount =
                                            radarrData.allServers.count { !it.server.is4k }
                                        val fourKCount =
                                            radarrData.allServers.count { it.server.is4k }
                                        val totalServers = radarrData.allServers.size
                                        if (fourKCount > 0) {
                                            addLoadingStep(
                                                getString(
                                                    R.string.splashScreen_radarrConfigComplete,
                                                    totalServers,
                                                    hdCount,
                                                    fourKCount
                                                ),
                                                LoadingStepType.SUCCESS
                                            )
                                        } else {
                                            addLoadingStep(
                                                resources.getQuantityString(
                                                    R.plurals.splashScreen_radarrConfigComplete_basic,
                                                    totalServers,
                                                    totalServers
                                                ),
                                                LoadingStepType.SUCCESS
                                            )
                                        }
                                    }

                                    else -> {
                                        // Successful call but no servers returned
                                        addLoadingStep(
                                            getString(R.string.splashScreen_noRadarrServersInfo),
                                            LoadingStepType.INFO
                                        )
                                    }
                                }

                                // Detect media server type and display appropriate message
                                val mediaServerType = apiService.detectMediaServerType()
                                val mediaServerMessage = when (mediaServerType) {
                                    ca.devmesh.seerrtv.model.MediaServerType.PLEX ->
                                        "Plex media server detected"

                                    ca.devmesh.seerrtv.model.MediaServerType.JELLYFIN ->
                                        "Jellyfin media server detected"

                                    ca.devmesh.seerrtv.model.MediaServerType.EMBY ->
                                        "Emby media server detected"

                                    ca.devmesh.seerrtv.model.MediaServerType.NOT_CONFIGURED ->
                                        "No media server configured"
                                }
                                val mediaServerMessageType = when (mediaServerType) {
                                    ca.devmesh.seerrtv.model.MediaServerType.PLEX,
                                    ca.devmesh.seerrtv.model.MediaServerType.JELLYFIN,
                                    ca.devmesh.seerrtv.model.MediaServerType.EMBY ->
                                        LoadingStepType.SUCCESS

                                    ca.devmesh.seerrtv.model.MediaServerType.NOT_CONFIGURED ->
                                        LoadingStepType.WARNING
                                }
                                addLoadingStep(mediaServerMessage, mediaServerMessageType)

                                // Check if we have any servers configured at all
                                val hasAnyServers =
                                    (sonarrData?.allServers?.isNotEmpty() == true) ||
                                            (radarrData?.allServers?.isNotEmpty() == true)

                                if (!hasAnyServers) {
                                    addLoadingStep(
                                        getString(R.string.splashScreen_noServersInfo),
                                        LoadingStepType.WARNING
                                    )
                                }

                                addLoadingStep(
                                    getString(R.string.splashScreen_ready),
                                    LoadingStepType.SUCCESS
                                )
                                // Mark authentication as complete
                                isAuthenticationComplete = true
                                // Authentication complete - for direct flavor, let SplashScreen control navigation
                                // For play flavor, we can safely hide splash and navigate
                                if (!BuildConfig.IS_DIRECT_FLAVOR) {
                                    showSplash = false
                                }
                            }

                            is ApiValidationResult.Error -> {
                                Log.e(
                                    "MainActivity",
                                    "❌ Authentication test failed: ${authResult.message}"
                                )
                                if (authResult.message.contains("403") || authResult.message.contains(
                                        "Access denied"
                                    )
                                ) {
                                    apiValidationError =
                                        getString(R.string.splashScreen_errorAuthFailed)
                                    handleConnectionError(isAuthenticationError = true)
                                } else {
                                    apiValidationError = authResult.message
                                    handleConnectionError(isAuthenticationError = true)
                                }
                            }

                            is ApiValidationResult.CloudflareRequired -> {
                                apiValidationError =
                                    getString(R.string.splashScreen_errorCloudflareRequired)
                                handleConnectionError(isAuthenticationError = true)
                            }
                        }
                    }

                    is ApiValidationResult.Error -> {
                        Log.e(
                            "MainActivity",
                            "❌ Base connection failed: ${connectionResult.message}"
                        )
                        apiValidationError = connectionResult.message
                        handleConnectionError(isAuthenticationError = true)
                    }

                    is ApiValidationResult.CloudflareRequired -> {
                        apiValidationError =
                            getString(R.string.splashScreen_errorCloudflareRequired)
                        handleConnectionError(isAuthenticationError = true)
                    }
                }
            } catch (e: CancellationException) {
                throw e // Propagate cancellation
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during configuration validation", e)
                val errorMessage = e.message ?: ""
                if (errorMessage.contains("403") || errorMessage.contains("Access denied")) {
                    apiValidationError = getString(R.string.splashScreen_errorAuthFailed)
                    handleConnectionError(isAuthenticationError = true)
                } else {
                    apiValidationError = getString(R.string.splashScreen_errorConnectionFailed)
                    handleConnectionError(isAuthenticationError = true)
                }
            }
        }
    }

    private val selectedCategory = mutableStateOf(MediaCategory.RECENTLY_ADDED)
    private val previousCategory = mutableStateOf(MediaCategory.RECENTLY_ADDED)
    private val selectedIndex = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register lifecycle observer for token refresh
        lifecycle.addObserver(lifecycleObserver)

        // Add system-level back button handler to consume predictive/system back so the Activity doesn't auto-finish
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Consume system back; actual behavior handled in Compose/DPAD
                Log.d(
                    "MainActivity",
                    "System back button pressed - consumed; delegating to Compose/DPAD"
                )
            }
        })

        updateConfigurationState()
        Log.d("MainActivity", "Current Locale: ${Locale.current}")

        // State to trigger navigation after validation
        var shouldNavigateAfterValidation by mutableStateOf<String?>(null)
        // Add a reference to the onContinue callback
        var splashOnContinue: (() -> Unit)? = null

        setContent {
            val navController = rememberNavController()
            val appFocusManager = rememberAppFocusManager()
            val dpadController = rememberDpadController()
            val coroutineScope = rememberCoroutineScope()

            // Create NavigationCoordinator as single source of truth
            val navigationCoordinator = rememberNavigationCoordinator(
                navController = navController,
                appFocusManager = appFocusManager,
                dpadController = dpadController,
                scope = coroutineScope
            )

            // Settings menu state - moved from MainScreen to MainActivity for proper z-order
            var isSettingsMenuVisible by remember { mutableStateOf(false) }

            // Top bar UI state lifted to MainActivity so MainTopBar can render hints/refresh row
            var topBarShowRefreshHint by remember { mutableStateOf(false) }
            var topBarIsRefreshRowVisible by remember { mutableStateOf(false) }

            CompositionLocalProvider(LocalNavController provides navController) {
                SeerrTVTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Update check - runs once on app startup
                        LaunchedEffect(Unit) {
                            // For direct flavor, check for updates first (before any configuration logic)
                            if (BuildConfig.IS_DIRECT_FLAVOR && showSplash) {
                                loadingSteps = emptyList() // Reset loading steps
                                addLoadingStep(getString(R.string.splashScreen_checkingForUpdates))

                                // Actually check for updates
                                val updateInfo = checkForUpdateIfAvailable(
                                    this@MainActivity,
                                    "https://api.github.com/repos/devmesh-git/seerrtv/releases/latest"
                                )
                                if (updateInfo != null) {
                                    updateInfoForDialog = updateInfo
                                    showUpdateDialog = true
                                    return@LaunchedEffect // Exit early if update is available
                                }
                            }
                        }

                        // Configuration validation - runs when showSplash changes
                        LaunchedEffect(showSplash) {
                            if (isConfigured && showSplash) {
                                isAuthenticationComplete = false
                                delay(300) // Show initial loading message
                                validateConfiguration()
                                // For play flavor, call onContinue after validation
                                // For direct flavor, let SplashScreen call onContinue after update dialog is handled
                                if (!BuildConfig.IS_DIRECT_FLAVOR) {
                                    splashOnContinue?.invoke()
                                }
                            } else if (!isConfigured && showSplash) {
                                // If not configured, show loading steps then move to config
                                loadingSteps = emptyList() // Reset loading steps
                                addLoadingStep(getString(R.string.splashScreen_loadingConfiguration))

                                // For play flavor, navigate immediately
                                if (!BuildConfig.IS_DIRECT_FLAVOR) {
                                    showSplash = false
                                    splashOnContinue?.invoke()
                                }
                                // For direct flavor, let SplashScreen control navigation after update check
                                // Don't set showSplash = false here as it would interfere with update dialog
                            }
                        }

                        // Create derived state for top bar focus from AppFocusManager
                        val (isInTopBarNow, isSearchFocusedNow, isSettingsFocusedNow) = when (val focus =
                            appFocusManager.currentFocus) {
                            is AppFocusState.TopBar -> Triple(
                                true,
                                focus.focus == TopBarFocus.Search,
                                focus.focus == TopBarFocus.Settings
                            )

                            else -> Triple(false, false, false)
                        }
                        val currentRouteLocal =
                            navController.currentDestination?.route?.split("/")?.firstOrNull()
                        val topBarFocusState = TopBarFocusState(
                            isInTopBar = isInTopBarNow,
                            isSearchFocused = isSearchFocusedNow,
                            isMoviesFocused = appFocusManager.currentFocus is AppFocusState.TopBar && 
                                             (appFocusManager.currentFocus as AppFocusState.TopBar).focus == TopBarFocus.Movies,
                            isSeriesFocused = appFocusManager.currentFocus is AppFocusState.TopBar && 
                                             (appFocusManager.currentFocus as AppFocusState.TopBar).focus == TopBarFocus.Series,
                            isSettingsFocused = isSettingsFocusedNow,
                            showRefreshHint = if (currentRouteLocal == "main") topBarShowRefreshHint else false,
                            isRefreshRowVisible = if (currentRouteLocal == "main") topBarIsRefreshRowVisible else false
                        )

                        SeerrTVApp(
                            navController = navController,
                            context = this@MainActivity,
                            topBarFocusState = topBarFocusState,
                            dpadController = dpadController,
                            appFocusManager = appFocusManager,
                            isSettingsMenuVisible = isSettingsMenuVisible,
                            onSettingsMenuDismiss = { isSettingsMenuVisible = false },
                            onOpenSettingsMenu = { isSettingsMenuVisible = true },
                            onOpenConfigScreen = {
                                navController.navigate("config")
                            },
                            discoveryViewModel = sharedDiscoveryViewModel
                        ) {
                            // Add authentication error handler for splash screen validation errors
                            AuthenticationErrorHandler(
                                isVisible = showConnectionErrorDialog && isAuthError,
                                onRetry = {
                                    showConnectionErrorDialog = false
                                    isAuthError = false
                                    // Retry authentication by re-running validation
                                    activityScope.launch {
                                        validateConfiguration()
                                    }
                                },
                                onReconfigure = {
                                    showConnectionErrorDialog = false
                                    isAuthError = false
                                    // Clear config and navigate to config screen
                                    SharedPreferencesUtil.clearConfig(this@MainActivity)
                                    updateConfigurationState()
                                    navController.navigate("config") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )

                            NavHost(
                                navController = navController,
                                startDestination = "splash",
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable("splash") {
                                    SplashScreen(
                                        errorMessage = null,
                                        isAuthenticationComplete = isAuthenticationComplete,
                                        isConfigured = isConfigured,
                                        loadingSteps = loadingSteps,
                                        apiValidationError = apiValidationError,
                                        showUpdateDialog = showUpdateDialog,
                                        updateInfoForDialog = updateInfoForDialog,
                                        onUpdateDialogClose = {
                                            showUpdateDialog = false
                                            showSplash = false
                                        },
                                        onContinue = {
                                            // For direct flavor, this callback controls navigation after update dialog is handled
                                            // For play flavor, automatic navigation is handled by the LaunchedEffect below
                                            if (BuildConfig.IS_DIRECT_FLAVOR) {
                                                showSplash = false
                                            }
                                        }.also { splashOnContinue = it }
                                    )
                                }
                                composable(
                                    route = "config",
                                    exitTransition = { fadeOut(animationSpec = tween(300)) },
                                    popEnterTransition = { fadeIn(animationSpec = tween(300)) }
                                ) {
                                    ConfigScreen(
                                        navController = navController,
                                        context = this@MainActivity,
                                        onConfigComplete = {
                                            updateConfigurationState()
                                            // Clear navigation state and restart the app
                                            navController.navigate("splash") {
                                                popUpTo(0) { inclusive = true }
                                            }
                                            // Small delay to ensure navigation completes, then recreate
                                            CoroutineScope(Dispatchers.Main).launch {
                                                delay(100)
                                                recreate()
                                            }
                                        }
                                    )
                                }
                                composable(
                                    route = "main",
                                    exitTransition = { fadeOut(animationSpec = tween(1000)) },
                                    popEnterTransition = { fadeIn(animationSpec = tween(1000)) }
                                ) {
                                    // Use NavigationCoordinator's state for currentBackStackEntry
                                    val currentBackStackEntry by navigationCoordinator.currentBackStackEntry.collectAsState()
                                    MainScreen(
                                        context = this@MainActivity,
                                        selectedCategory = selectedCategory,
                                        previousCategory = previousCategory,
                                        selectedMediaIndex = selectedIndex,
                                        viewModel = sharedViewModel,
                                        imageLoader = SeerrTV.imageLoader,
                                        navController = navController,
                                        appFocusManager = appFocusManager,
                                        dpadController = dpadController,
                                        currentBackStackEntry = currentBackStackEntry,
                                        onFocusStateChange = { isInTopBar, isSearchFocused, isSettingsFocused, showRefreshHint, isRefreshRowVisible ->
                                            topBarShowRefreshHint = showRefreshHint
                                            topBarIsRefreshRowVisible = isRefreshRowVisible
                                        }
                                    )
                                }
                                composable(
                                    route = "details/{mediaId}/{mediaType}?showRequestModal={showRequestModal}",
                                    arguments = listOf(
                                        navArgument("mediaId") { type = NavType.StringType },
                                        navArgument("mediaType") { type = NavType.StringType },
                                        navArgument("showRequestModal") {
                                            type = NavType.BoolType
                                            defaultValue = false
                                        }
                                    ),
                                    exitTransition = { fadeOut(animationSpec = tween(300)) },
                                    popEnterTransition = { fadeIn(animationSpec = tween(300)) }
                                ) { backStackEntry ->
                                    Log.d("MainActivity", "Media details route triggered")
                                    val mediaId =
                                        backStackEntry.arguments?.getString("mediaId") ?: ""
                                    val mediaType =
                                        backStackEntry.arguments?.getString("mediaType") ?: ""
                                    val showRequestModal =
                                        backStackEntry.arguments?.getBoolean("showRequestModal") == true

                                    if (showRequestModal) {
                                        Log.d(
                                            "MainActivity",
                                            "Media details should show request modal automatically"
                                        )
                                    }

                                    val scope = rememberCoroutineScope()
                                    val navigationManager =
                                        rememberNavigationManager(scope, navController)

                                    MediaDetails(
                                        context = this@MainActivity,
                                        imageLoader = SeerrTV.imageLoader,
                                        mediaId = mediaId,
                                        mediaType = mediaType,
                                        navController = navController,
                                        viewModel = sharedViewModel,
                                        dpadController = dpadController,
                                        appFocusManager = appFocusManager,
                                        navigationManager = navigationManager,
                                        initialShowRequestModal = showRequestModal
                                    )
                                }
                                composable(
                                    route = "search",
                                    exitTransition = { fadeOut(animationSpec = tween(1000)) },
                                    popEnterTransition = { fadeIn(animationSpec = tween(1000)) }
                                ) {
                                    Log.d(
                                        "MainActivity",
                                        "Composing MediaDiscoveryScreen for Search"
                                    )
                                    val scope = rememberCoroutineScope()
                                    val navigationManager =
                                        rememberNavigationManager(scope, navController)

                                    MediaDiscoveryScreen(
                                        viewModel = sharedDiscoveryViewModel,
                                        appFocusManager = appFocusManager,
                                        dpadController = dpadController,
                                        imageLoader = SeerrTV.imageLoader,
                                        context = this@MainActivity,
                                        discoveryType = DiscoveryType.SEARCH,
                                        initialKeyword = "",
                                        keywordText = "",
                                        timestamp = 0L,
                                        navigationManager = navigationManager,
                                    )
                                }
                                composable(
                                    route = "mediaDiscovery/{type}/{keywordId}/{keywordText}/{timestamp}",
                                    arguments = listOf(
                                        navArgument("type") { type = NavType.StringType },
                                        navArgument("keywordId") { type = NavType.StringType },
                                        navArgument("keywordText") { type = NavType.StringType },
                                        navArgument("timestamp") { type = NavType.LongType }
                                    )
                                ) { backStackEntry ->
                                    val type = backStackEntry.arguments?.getString("type") ?: ""
                                    val keywordId =
                                        backStackEntry.arguments?.getString("keywordId") ?: ""
                                    val keywordText = URLDecoder.decode(
                                        backStackEntry.arguments?.getString("keywordText") ?: "",
                                        "UTF-8"
                                    )
                                    val timestamp =
                                        backStackEntry.arguments?.getLong("timestamp") ?: 0L

                                    val discoveryType = when (type) {
                                        "MOVIE_KEYWORDS" -> DiscoveryType.MOVIE_KEYWORDS
                                        "TV_KEYWORDS" -> DiscoveryType.TV_KEYWORDS
                                        else -> DiscoveryType.SEARCH
                                    }

                                    MediaDiscoveryScreen(
                                        viewModel = sharedDiscoveryViewModel,
                                        appFocusManager = appFocusManager,
                                        dpadController = dpadController,
                                        discoveryType = discoveryType,
                                        initialKeyword = keywordId,
                                        keywordText = keywordText,
                                        imageLoader = SeerrTV.imageLoader,
                                        context = this@MainActivity,
                                        timestamp = timestamp
                                    )
                                }
                                composable(
                                    route = "mediaDiscovery/{mediaType}/{categoryId}/{discoveryType}/{categoryName}/{timestamp}?image={image}",
                                    arguments = listOf(
                                        navArgument("mediaType") { type = NavType.StringType },
                                        navArgument("categoryId") { type = NavType.StringType },
                                        navArgument("discoveryType") { type = NavType.StringType },
                                        navArgument("categoryName") { type = NavType.StringType },
                                        navArgument("timestamp") { type = NavType.LongType },
                                        navArgument("image") {
                                            type = NavType.StringType
                                            nullable = true
                                            defaultValue = null
                                        }
                                    ),
                                    exitTransition = { fadeOut(animationSpec = tween(300)) },
                                    popExitTransition = { fadeOut(animationSpec = tween(300)) }
                                ) { backStackEntry ->
                                    backStackEntry.arguments?.getString("mediaType") ?: ""
                                    val categoryId =
                                        backStackEntry.arguments?.getString("categoryId") ?: ""
                                    val discoveryTypeStr =
                                        backStackEntry.arguments?.getString("discoveryType") ?: ""
                                    val categoryName = URLDecoder.decode(
                                        backStackEntry.arguments?.getString("categoryName") ?: "",
                                        "UTF-8"
                                    )
                                    val timestamp =
                                        backStackEntry.arguments?.getLong("timestamp") ?: 0L
                                    val image = backStackEntry.arguments?.getString("image")?.let {
                                        URLDecoder.decode(it, "UTF-8")
                                    }

                                    val discoveryType = try {
                                        DiscoveryType.valueOf(discoveryTypeStr)
                                    } catch (e: Exception) {
                                        Log.e(
                                            "MainActivity",
                                            "Invalid discovery type: $discoveryTypeStr",
                                            e
                                        )
                                        DiscoveryType.SEARCH  // Default
                                    }

                                    MediaDiscoveryScreen(
                                        viewModel = sharedDiscoveryViewModel,
                                        appFocusManager = appFocusManager,
                                        dpadController = dpadController,
                                        discoveryType = discoveryType,
                                        initialKeyword = categoryId,
                                        keywordText = categoryName,
                                        imageLoader = SeerrTV.imageLoader,
                                        context = this@MainActivity,
                                        timestamp = timestamp,
                                        image = image
                                    )
                                }
                                composable(
                                    route = "person/{personId}",
                                    arguments = listOf(
                                        navArgument("personId") { type = NavType.StringType }
                                    ),
                                    exitTransition = { fadeOut(animationSpec = tween(300)) },
                                    popEnterTransition = { fadeIn(animationSpec = tween(300)) }
                                ) { backStackEntry ->
                                    val personId =
                                        backStackEntry.arguments?.getString("personId") ?: ""
                                    Log.d(
                                        "MainActivity",
                                        "Composing PersonScreen for ID: $personId"
                                    )

                                    PersonScreen(
                                        personId = personId,
                                        imageLoader = SeerrTV.imageLoader,
                                        appFocusManager = appFocusManager,
                                        dpadController = dpadController,
                                        viewModel = sharedPersonViewModel
                                    )
                                }

                                // Browse Movies route
                                composable(
                                    route = "browse/movies",
                                    exitTransition = { fadeOut(animationSpec = tween(1000)) },
                                    popEnterTransition = { fadeIn(animationSpec = tween(1000)) }
                                ) {
                                    MediaBrowseScreen(
                                        mediaType = MediaType.MOVIE,
                                        viewModel = sharedDiscoveryViewModel,
                                        appFocusManager = appFocusManager,
                                        dpadController = dpadController,
                                        imageLoader = SeerrTV.imageLoader,
                                        context = this@MainActivity
                                    )
                                }

                                // Browse Series route
                                composable(
                                    route = "browse/series",
                                    exitTransition = { fadeOut(animationSpec = tween(1000)) },
                                    popEnterTransition = { fadeIn(animationSpec = tween(1000)) }
                                ) {
                                    MediaBrowseScreen(
                                        mediaType = MediaType.TV,
                                        viewModel = sharedDiscoveryViewModel,
                                        appFocusManager = appFocusManager,
                                        dpadController = dpadController,
                                        imageLoader = SeerrTV.imageLoader,
                                        context = this@MainActivity
                                    )
                                }
                            }
                        }

                        // Handle navigation after validation
                        LaunchedEffect(shouldNavigateAfterValidation) {
                            shouldNavigateAfterValidation?.let { dest ->
                                navController.navigate(dest) {
                                    popUpTo("splash") { inclusive = true }
                                }
                                shouldNavigateAfterValidation = null
                            }
                        }

                        // Trigger navigation when splash completes
                        LaunchedEffect(
                            showSplash,
                            isConfigured,
                            apiValidationError,
                            isAuthenticationComplete
                        ) {
                            if (!showSplash) {
                                when {
                                    // First run: No configuration exists
                                    !isConfigured -> {
                                        Log.d("MainActivity", "🆕 First run - navigating to config")
                                        shouldNavigateAfterValidation = "config"
                                    }
                                    // Configured but authentication failed
                                    apiValidationError != null -> {
                                        Log.d(
                                            "MainActivity",
                                            "❌ Authentication failed - staying on splash"
                                        )
                                        // Don't navigate, let user handle the error
                                    }
                                    // Configured and authentication succeeded
                                    isAuthenticationComplete -> {
                                        Log.d(
                                            "MainActivity",
                                            "✅ Authentication succeeded - navigating to main"
                                        )
                                        shouldNavigateAfterValidation = "main"
                                    }
                                    // Still authenticating
                                    else -> {
                                        Log.d("MainActivity", "⏳ Still authenticating...")
                                        // Don't navigate yet, wait for authentication to complete
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(lifecycleObserver)
        activityScope.cancel()
    }
}

@Composable
fun SeerrTVApp(
    navController: NavController,
    context: Context,
    topBarFocusState: TopBarFocusState,
    dpadController: DpadController,
    appFocusManager: AppFocusManager,
    isSettingsMenuVisible: Boolean,
    onSettingsMenuDismiss: () -> Unit,
    onOpenSettingsMenu: () -> Unit,
    onOpenConfigScreen: () -> Unit,
    discoveryViewModel: MediaDiscoveryViewModel,
    content: @Composable () -> Unit
) {

    // Track current route to determine if top bar should be visible
    var currentRoute by remember { mutableStateOf<String?>(null) }

    // Track current route for top bar visibility (NavigationCoordinator handles focus state management)
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val newRoute = destination.route?.split("/")?.firstOrNull() ?: ""
            currentRoute = newRoute
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    // Determine if top bar should be visible (not on splash or config screens)
    val shouldShowTopBar =
        currentRoute in listOf("main", "search", "mediaDiscovery", "details", "person")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827)) // Default SeerrTV background color
    ) {
        // Main content - no padding, let it fill the entire screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusable(interactionSource = remember { MutableInteractionSource() })
                .onKeyEvent { keyEvent ->
                    // Skip DpadController for screens that use native Compose focus (config, splash)
                    if (currentRoute in listOf("config", "splash")) {
                        return@onKeyEvent false // Let native Compose focus handle it
                    }

                    // Handle key events through the centralized DpadController
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "MainActivity",
                            "🎯 MainActivity key event: ${keyEvent.key} (${keyEvent.type})"
                        )
                    }
                    val result = dpadController.onKeyEvent(keyEvent)
                    if (BuildConfig.DEBUG) {
                        Log.d("MainActivity", "🎯 MainActivity key event result: $result")
                    }

                    // TopBar now handles its own key events through TopBarController
                    result
                }
        ) {
            content()
        }

        // TopBar Controller - handles its own DPAD navigation and business logic
        if (shouldShowTopBar) {
            TopBarController(
                navController = navController,
                appFocusManager = appFocusManager,
                dpadController = dpadController,
                onOpenSettingsMenu = onOpenSettingsMenu,
                onRefresh = {
                    // Only refresh on MainScreen; invoke MainScreen's onRefresh even if TopBar is focused
                    if (currentRoute == "main") {
                        dpadController.getScreenConfig("main")?.onRefresh?.invoke()
                    }
                }
            )
        }

        // Persistent top bar - render AFTER content to ensure it's on top
        if (shouldShowTopBar) {
            MainTopBar(
                context = context,
                isSearchFocused = topBarFocusState.isSearchFocused,
                isMoviesFocused = topBarFocusState.isMoviesFocused,
                isSeriesFocused = topBarFocusState.isSeriesFocused,
                isSettingsFocused = topBarFocusState.isSettingsFocused,
                showRefreshHint = topBarFocusState.showRefreshHint,
                isInTopBar = topBarFocusState.isInTopBar,
                isRefreshRowVisible = topBarFocusState.isRefreshRowVisible,
                showSearchIcon = navController.currentDestination?.route?.startsWith("search") != true,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Settings menu - render AFTER top bar to ensure it's on top of everything
        SettingsMenu(
            isVisible = isSettingsMenuVisible,
            onDismiss = onSettingsMenuDismiss,
            onOpenConfigScreen = onOpenConfigScreen,
            viewModel = discoveryViewModel
        )
    }
}