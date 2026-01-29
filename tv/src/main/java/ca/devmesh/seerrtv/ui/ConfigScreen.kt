package ca.devmesh.seerrtv.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.data.SeerrApiService.ApiValidationResult
import ca.devmesh.seerrtv.data.SeerrApiService.SeerrConfig
import ca.devmesh.seerrtv.data.SeerrApiService.ServerType
import ca.devmesh.seerrtv.data.SeerrApiService.SetupIdResponse
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.model.AuthType
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.ui.components.AppLogo
import ca.devmesh.seerrtv.ui.components.VersionNumber
import ca.devmesh.seerrtv.util.CommonUtil
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import ca.devmesh.seerrtv.viewmodel.ConfigViewModel
import io.ktor.client.plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class ConfigWizardStep {
    SERVER_CONNECTION,  // Protocol and hostname
    CLOUDFLARE_CONFIG, // Only shown if Cloudflare is detected
    AUTH_METHOD,       // Choose authentication method
    API_KEY_CONFIG,     // If API key is chosen
    LOCAL_USER_CONFIG,  // If LocalUser is chosen
    JELLYFIN_CONFIG,   // If Jellyfin or Emby is chosen
    PLEX              // If Plex is chosen
}

@Composable
fun ConfigScreen(
    navController: NavController,
    context: Context,
    onConfigComplete: () -> Unit,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    var configMethod by remember { mutableStateOf<ConfigMethod?>(null) }
    var showLanguageSelection by remember {
        mutableStateOf(SharedPreferencesUtil.getAppLanguage(context) == null)
    }
    var showConfigSelection by remember {
        mutableStateOf(SharedPreferencesUtil.getAppLanguage(context) != null)
    }
    var currentWizardStep by remember { mutableStateOf(ConfigWizardStep.SERVER_CONNECTION) }
    var showLoginSuccessModal by remember { mutableStateOf(false) }
    var configCompleted by remember { mutableStateOf(false) }

    // Backup current config when entering the config screen (only once per entry)
    LaunchedEffect(Unit) {
        viewModel.backupCurrentConfig()
    }

    // Consolidated BackHandler for all navigation scenarios
    BackHandler(enabled = true) {
        when {
            // If login success modal is showing, don't allow back
            showLoginSuccessModal -> {
                // Do nothing
            }
            // If we're showing language selection, exit config
            showLanguageSelection -> {
                CoroutineScope(Dispatchers.Main).launch {
                    val restored = viewModel.restoreBackupConfig()
                    // If configuration wasn't completed and no backup was restored, clear it completely
                    if (!configCompleted && !restored) {
                        Log.d("ConfigScreen", "Configuration not completed, clearing partial config (no backup to restore)")
                        SharedPreferencesUtil.clearConfig(context)
                    }
                }
                navController.popBackStack()
            }
            // If we're in manual config wizard, handle wizard step navigation
            configMethod == ConfigMethod.MANUAL -> {
                when (currentWizardStep) {
                    ConfigWizardStep.SERVER_CONNECTION -> {
                        // At first step of wizard, go back to selection
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.restoreBackupConfig()
                        }
                        showConfigSelection = true
                        configMethod = null
                    }
                    ConfigWizardStep.CLOUDFLARE_CONFIG -> {
                        currentWizardStep = ConfigWizardStep.SERVER_CONNECTION
                    }
                    ConfigWizardStep.AUTH_METHOD -> {
                        // Always go back to server connection from auth method
                        currentWizardStep = ConfigWizardStep.SERVER_CONNECTION
                    }
                    ConfigWizardStep.API_KEY_CONFIG, ConfigWizardStep.LOCAL_USER_CONFIG,
                    ConfigWizardStep.JELLYFIN_CONFIG, ConfigWizardStep.PLEX -> {
                        currentWizardStep = ConfigWizardStep.AUTH_METHOD
                    }
                }
            }
            // If we're in browser config, go back to selection
            configMethod == ConfigMethod.BROWSER -> {
                showConfigSelection = true
                configMethod = null
            }
            // If we're at config selection screen, go back to language selection
            showConfigSelection -> {
                showLanguageSelection = true
                showConfigSelection = false
            }
        }
    }

    // Update configCompleted state when login success is shown
    LaunchedEffect(showLoginSuccessModal) {
        if (showLoginSuccessModal) {
            configCompleted = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827))
    ) {
        // Content area
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                showLanguageSelection -> {
                    LanguageSelectionStep(
                        context = context,
                        languageOptions = listOf(
                            "en" to R.string.settingsMenu_discoverLanguageEN,
                            "de" to R.string.settingsMenu_discoverLanguageDE,
                            "es" to R.string.settingsMenu_discoverLanguageES,
                            "fr" to R.string.settingsMenu_discoverLanguageFR,
                            "ja" to R.string.settingsMenu_discoverLanguageJA,
                            "nl" to R.string.settingsMenu_discoverLanguageNL,
                            "pt" to R.string.settingsMenu_discoverLanguagePT,
                            "zh" to R.string.settingsMenu_discoverLanguageZH,
                        ),
                        onNext = {
                            showLanguageSelection = false
                            showConfigSelection = true
                        }
                    )
                }
                showConfigSelection -> {
                    ConfigSelectionScreen(
                        onMethodSelected = { method ->
                            configMethod = method
                            showConfigSelection = false
                            if (method == ConfigMethod.MANUAL) {
                                currentWizardStep = ConfigWizardStep.SERVER_CONNECTION
                            }
                        }
                    )
                }
                configMethod == ConfigMethod.BROWSER -> {
                    BrowserConfigScreen(
                        context = context,
                        onConfigComplete = {
                            showLoginSuccessModal = true
                        },
                        viewModel = viewModel,
                        onBackToSelection = {
                            showConfigSelection = true
                            configMethod = null
                        },
                    )
                }
                configMethod == ConfigMethod.MANUAL -> {
                    WizardConfigScreen(
                        context = context,
                        viewModel = viewModel,
                        currentStep = currentWizardStep,
                        onStepChange = { step -> currentWizardStep = step },
                        onShowLoginSuccessModal = { showLoginSuccessModal = true }
                    )
                }
            }
        }

        // Bottom elements
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
        ) {
            VersionNumber(modifier = Modifier.align(Alignment.BottomStart))
        }
    }

    // Show login success modal if needed
    if (showLoginSuccessModal) {
        Log.d("ConfigScreen", "Attempt to show login success modal")
        var userInfo by remember { mutableStateOf<SeerrApiService.UserInfo?>(null) }

        LaunchedEffect(Unit) {
            try {
                // Single attempt to get user info since we just logged in successfully
                delay(300)
                userInfo = viewModel.apiService.getCurrentUserInfo()
                Log.d("ConfigScreen", "User info retrieved: ${userInfo != null}")
            } catch (e: Exception) {
                Log.e("ConfigScreen", "Error getting user info", e)
                showLoginSuccessModal = false
            }
        }

        if (userInfo != null) {
            Log.d("ConfigScreen", "We have a user info object, Showing login success modal")
            LoginSuccessModal(
                context = context,
                userInfo = userInfo!!,
                onDismiss = {
                    showLoginSuccessModal = false
                    onConfigComplete()
                    // Navigation is now handled by MainActivity
                }
            )
        }
    }
}

@Composable
fun WizardConfigScreen(
    context: Context,
    viewModel: ConfigViewModel,
    currentStep: ConfigWizardStep,
    onStepChange: (ConfigWizardStep) -> Unit,
    onShowLoginSuccessModal: () -> Unit
) {
    var protocol by remember { mutableStateOf("http") }
    var hostname by remember { mutableStateOf("") }
    var enableCloudflare by remember { mutableStateOf(false) }
    var cloudflareClientId by remember { mutableStateOf("") }
    var cloudflareClientSecret by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf<AuthType?>(null) }
    var apiKey by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Initialize Jellyfin-specific state with default values
    var jellyfinHostname by remember { mutableStateOf("") }
    var jellyfinPort by remember { mutableIntStateOf(8096) }
    var jellyfinUseSsl by remember { mutableStateOf(false) }
    var jellyfinUsername by remember { mutableStateOf("") }
    var jellyfinPassword by remember { mutableStateOf("") }

    // Get string resources at composition time
    val successString = stringResource(R.string.configScreen_configSuccess)
    val authFailedString = stringResource(R.string.splashScreen_errorAuthFailed)
    val overseerrDetectedString = stringResource(R.string.configScreen_overseerrDetected)
    val jellyseerrDetectedString = stringResource(R.string.configScreen_jellyseerrDetected)
    val seerrDetectedString = stringResource(R.string.configScreen_seerrDetected)

    // Reset Jellyfin state when switching to Jellyfin config step
    LaunchedEffect(currentStep) {
        if (currentStep == ConfigWizardStep.JELLYFIN_CONFIG) {
            jellyfinHostname = ""
            jellyfinPort = 8096
            jellyfinUseSsl = false
            jellyfinUsername = ""
            jellyfinPassword = ""
            errorMessage = null
            successMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827))
    ) {
        // Content area
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentStep) {
                ConfigWizardStep.SERVER_CONNECTION -> {
                    ServerConnectionStep(
                        protocol = protocol,
                        hostname = hostname,
                        errorMessage = errorMessage,
                        successMessage = successMessage,
                        onProtocolChange = { protocol = it },
                        onHostnameChange = { hostname = it },
                        onNext = {
                            errorMessage = null
                            successMessage = null
                            // Create temporary config for testing
                            val tempConfig = SeerrConfig(
                                protocol = protocol,
                                hostname = hostname,
                                cloudflareEnabled = false,
                                cfClientId = "",
                                cfClientSecret = "",
                                authType = AuthType.ApiKey.type,
                                apiKey = "",
                                username = "",
                                password = "",
                                isSubmitted = false,
                                createdAt = ""
                            )
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                val result = viewModel.testBaseConnection(tempConfig)
                                handleApiValidationResult(
                                    result = result,
                                    onSuccess = { serverType ->
                                        successMessage = when (serverType) {
                                            ServerType.OVERSEERR -> overseerrDetectedString
                                            ServerType.JELLYSEERR -> jellyseerrDetectedString
                                            ServerType.SEERR -> seerrDetectedString
                                            else -> successString
                                        }
                                        CoroutineScope(Dispatchers.Main).launch {
                                            delay(2000)
                                            successMessage = null
                                            errorMessage = null
                                            onStepChange(ConfigWizardStep.AUTH_METHOD)
                                        }
                                    },
                                    onError = { message ->
                                        errorMessage = message
                                    },
                                    onCloudflareRequired = {
                                        enableCloudflare = true
                                        successMessage = successString
                                        CoroutineScope(Dispatchers.Main).launch {
                                            delay(2000)
                                            successMessage = null
                                            errorMessage = null
                                            onStepChange(ConfigWizardStep.CLOUDFLARE_CONFIG)
                                        }
                                    },
                                    authFailedString = authFailedString
                                )
                            }
                        }
                    )
                }
                ConfigWizardStep.CLOUDFLARE_CONFIG -> {
                    CloudflareConfigStep(
                        clientId = cloudflareClientId,
                        clientSecret = cloudflareClientSecret,
                        onClientIdChange = { cloudflareClientId = it },
                        onClientSecretChange = { cloudflareClientSecret = it },
                        errorMessage = errorMessage,
                        successMessage = successMessage,
                        onNext = {
                            errorMessage = null
                            successMessage = null
                            // Test connection with Cloudflare credentials
                            val tempConfig = SeerrConfig(
                                protocol = protocol,
                                hostname = hostname,
                                cloudflareEnabled = true,
                                cfClientId = cloudflareClientId,
                                cfClientSecret = cloudflareClientSecret,
                                authType = AuthType.ApiKey.type,  // Use a valid default auth type
                                apiKey = "",
                                username = "",
                                password = "",
                                isSubmitted = false,
                                createdAt = ""
                            )
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                val result = viewModel.testBaseConnection(tempConfig)
                                handleApiValidationResult(
                                    result = result,
                                    onSuccess = { serverType ->
                                        successMessage = when (serverType) {
                                            ServerType.OVERSEERR -> overseerrDetectedString
                                            ServerType.JELLYSEERR -> jellyseerrDetectedString
                                            ServerType.SEERR -> seerrDetectedString
                                            else -> successString
                                        }
                                        CoroutineScope(Dispatchers.Main).launch {
                                            delay(2000)
                                            successMessage = null
                                            errorMessage = null
                                            onStepChange(ConfigWizardStep.AUTH_METHOD)
                                        }
                                    },
                                    onError = { message ->
                                        errorMessage = message
                                    },
                                    onCloudflareRequired = {
                                        errorMessage = "Cloudflare authentication required"
                                    },
                                    authFailedString = authFailedString
                                )
                            }
                        }
                    )
                }
                ConfigWizardStep.AUTH_METHOD -> {
                    errorMessage = null
                    AuthMethodStep(
                        onMethodSelected = { method ->
                            authType = method
                            onStepChange(when (method) {
                                AuthType.ApiKey -> ConfigWizardStep.API_KEY_CONFIG
                                AuthType.LocalUser -> ConfigWizardStep.LOCAL_USER_CONFIG
                                AuthType.Jellyfin -> ConfigWizardStep.JELLYFIN_CONFIG
                                AuthType.Emby -> ConfigWizardStep.JELLYFIN_CONFIG
                                AuthType.Plex -> ConfigWizardStep.PLEX
                            })
                        },
                        viewModel = viewModel
                    )
                }
                ConfigWizardStep.API_KEY_CONFIG -> {
                    ApiKeyConfigStep(
                        apiKey = apiKey,
                        onApiKeyChange = { apiKey = it },
                        errorMessage = errorMessage,
                        successMessage = successMessage,
                        onNext = {
                            errorMessage = null
                            val config = SeerrConfig(
                                protocol = protocol,
                                hostname = hostname,
                                cloudflareEnabled = enableCloudflare,
                                cfClientId = cloudflareClientId,
                                cfClientSecret = cloudflareClientSecret,
                                authType = AuthType.ApiKey.type,
                                apiKey = apiKey,
                                username = "",
                                password = "",
                                isSubmitted = true,
                                createdAt = ""
                            )
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                val result = viewModel.validateAndSaveConfig(config, useBrowserValidation = false)
                                handleApiValidationResult(
                                    result = result,
                                    onSuccess = { _ ->
                                        successMessage = successString
                                        CoroutineScope(Dispatchers.Main).launch {
                                            delay(2000)
                                            onShowLoginSuccessModal()
                                        }
                                    },
                                    onError = { message ->
                                        errorMessage = message
                                    },
                                    onCloudflareRequired = {
                                        errorMessage = "Cloudflare authentication required"
                                    },
                                    authFailedString = authFailedString
                                )
                            }
                        }
                    )
                }
                ConfigWizardStep.LOCAL_USER_CONFIG -> {
                    LocalUserStep(
                        username = username,
                        password = password,
                        onUsernameChange = { username = it },
                        onPasswordChange = { password = it },
                        errorMessage = errorMessage,
                        successMessage = successMessage,
                        onNext = {
                            errorMessage = null
                            val config = SeerrConfig(
                                protocol = protocol,
                                hostname = hostname,
                                cloudflareEnabled = enableCloudflare,
                                cfClientId = cloudflareClientId,
                                cfClientSecret = cloudflareClientSecret,
                                authType = AuthType.LocalUser.type,
                                apiKey = "",
                                username = username,
                                password = password,
                                isSubmitted = true,
                                createdAt = ""
                            )
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                when (val result = viewModel.validateAndSaveConfig(config, useBrowserValidation = false)) {
                                    is ApiValidationResult.Success -> {
                                        Log.d("ConfigScreen", "Configuration validation successful")
                                        // Show success message immediately
                                        successMessage = successString
                                        Log.d("ConfigScreen", "Set success message, waiting 2 seconds before showing modal")
                                        // Then wait 2 seconds before showing modal
                                        delay(2000)
                                        onShowLoginSuccessModal()
                                    }
                                    is ApiValidationResult.Error -> {
                                        // Check if the error message indicates a 403 error
                                        errorMessage = if (result.message.contains("403") || result.message.contains("permission")) {
                                            authFailedString
                                        } else {
                                            result.message
                                        }
                                    }
                                    is ApiValidationResult.CloudflareRequired -> {
                                        errorMessage = "Cloudflare authentication required"
                                    }
                                }
                            }
                        }
                    )
                }
                ConfigWizardStep.JELLYFIN_CONFIG -> {
                    JellyfinConfigStep(
                        jellyfinHostname = jellyfinHostname,
                        jellyfinPort = jellyfinPort,
                        jellyfinUseSsl = jellyfinUseSsl,
                        username = jellyfinUsername,
                        password = jellyfinPassword,
                        onHostnameChange = { jellyfinHostname = it },
                        onPortChange = { jellyfinPort = it },
                        onUseSslChange = { jellyfinUseSsl = it },
                        onUsernameChange = { jellyfinUsername = it },
                        onPasswordChange = { jellyfinPassword = it },
                        errorMessage = errorMessage,
                        successMessage = successMessage,
                        onNext = {
                            errorMessage = null
                            val config = SeerrConfig(
                                protocol = protocol,
                                hostname = hostname,
                                cloudflareEnabled = enableCloudflare,
                                cfClientId = cloudflareClientId,
                                cfClientSecret = cloudflareClientSecret,
                                authType = authType!!.type,
                                apiKey = "",
                                username = jellyfinUsername,
                                password = jellyfinPassword,
                                // Only include Jellyfin-specific fields for Jellyfin auth
                                jellyfinHostname = if (authType == AuthType.Jellyfin) jellyfinHostname else "",
                                jellyfinPort = if (authType == AuthType.Jellyfin) jellyfinPort else 8096,
                                jellyfinUseSsl = if (authType == AuthType.Jellyfin) jellyfinUseSsl else false,
                                jellyfinUrlBase = if (authType == AuthType.Jellyfin) "/" else "",
                                jellyfinEmail = if (authType == AuthType.Jellyfin) "" else "",
                                isSubmitted = true,
                                createdAt = ""
                            )
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                val result = viewModel.validateAndSaveConfig(config, useBrowserValidation = false)
                                handleApiValidationResult(
                                    result = result,
                                    onSuccess = { _ ->
                                        successMessage = successString
                                        CoroutineScope(Dispatchers.Main).launch {
                                            delay(2000)
                                            onShowLoginSuccessModal()
                                        }
                                    },
                                    onError = { message ->
                                        errorMessage = if (message.contains("403") || message.contains("permission")) {
                                            authFailedString
                                        } else {
                                            message
                                        }
                                    },
                                    onCloudflareRequired = {
                                        errorMessage = "Cloudflare authentication required"
                                    },
                                    authFailedString = authFailedString
                                )
                            }
                        },
                        authType = authType!!
                    )
                }
                ConfigWizardStep.PLEX -> {
                    PlexConfigStep(
                        context = context,
                        protocol = protocol,
                        hostname = hostname,
                        enableCloudflare = enableCloudflare,
                        cloudflareClientId = cloudflareClientId,
                        cloudflareClientSecret = cloudflareClientSecret,
                        errorMessage = errorMessage,
                        successMessage = successMessage,
                        onError = { message -> errorMessage = message },
                        onSuccess = {
                            successMessage = successString
                            CoroutineScope(Dispatchers.Main).launch {
                                onShowLoginSuccessModal()
                            }
                        },
                        onCancel = {
                            errorMessage = null
                            onStepChange(ConfigWizardStep.AUTH_METHOD)
                        },
                        viewModel = viewModel
                    )
                }
            }
        }

        // Bottom elements
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
        ) {
            VersionNumber(modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun RadioOption(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (isFocused) Color(0xFF2C3E50) else Color.Transparent,
                    shape = CircleShape
                )
                .padding(4.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFFBB86FC),
                    unselectedColor = Color.White
                ),
                modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
            )
        }
        Text(label, color = Color.White)
    }
}

@Composable
fun ServerConnectionStep(
    protocol: String,
    hostname: String,
    errorMessage: String?,
    successMessage: String?,
    onProtocolChange: (String) -> Unit,
    onHostnameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    val httpFocusRequester = remember { FocusRequester() }
    val hostnameFocusRequester = remember { FocusRequester() }
    val nextButtonFocusRequester = remember { FocusRequester() }
    val titleFocusRequester = remember { FocusRequester() }
    val view = LocalView.current
    var hasInteracted by remember { mutableStateOf(false) }
    var shouldTriggerNextButton by remember { mutableStateOf(false) }
    var shouldDismissKeyboard by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(true) }
    
    // Disable focus requests when component is unmounting
    DisposableEffect(Unit) {
        onDispose {
            isActive = false
        }
    }

    ConfigStepLayout(
        title = stringResource(R.string.configScreen_serverConnection),
        helpText = stringResource(R.string.configScreen_hostnameHelp),
        errorMessage = errorMessage,
        successMessage = successMessage,
        onNext = { 
            isActive = false
            onNext() 
        },
        nextButtonText = stringResource(R.string.configScreen_next),
        nextButtonFocusRequester = nextButtonFocusRequester,
        isTitleFocusable = true,
        titleFocusRequester = titleFocusRequester
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(stringResource(R.string.configScreen_protocol) + ":", color = Color.White)
            
            RadioOption(
                selected = protocol == "http",
                onClick = { onProtocolChange("http") },
                label = stringResource(R.string.configScreen_protocolHttp),
                modifier = Modifier.focusRequester(httpFocusRequester)
            )
            
            RadioOption(
                selected = protocol == "https",
                onClick = { onProtocolChange("https") },
                label = stringResource(R.string.configScreen_protocolHttps)
            )
        }

        CommonTextField(
            value = hostname,
            onValueChange = { onHostnameChange(cleanInput(it, "hostname")) },
            label = stringResource(R.string.configScreen_hostname),
            modifier = Modifier.focusRequester(hostnameFocusRequester),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (hostname.isNotEmpty() && isActive) {
                        Log.d("ServerConnectionStep", "Keyboard done pressed, dismissing keyboard first")
                        shouldDismissKeyboard = true
                    }
                }
            )
        )
    }

    // Initial focus - only set when we're in the initial state
    LaunchedEffect(Unit) {
        delay(100) // Small delay to ensure composition is complete
        if (isActive && !hasInteracted && errorMessage == null && successMessage == null) {
            try {
                Log.d("ServerConnectionStep", "Requesting initial focus on protocol")
                httpFocusRequester.requestFocus()
                hasInteracted = true
            } catch (e: Exception) {
                Log.e("ServerConnectionStep", "Error requesting focus: ${e.message}")
            }
        }
    }

    // Handle keyboard dismissal first
    LaunchedEffect(shouldDismissKeyboard) {
        if (isActive && shouldDismissKeyboard) {
            try {
                Log.d("ServerConnectionStep", "Dismissing keyboard")
                shouldDismissKeyboard = false
                
                // Request focus to title to dismiss keyboard
                titleFocusRequester.requestFocus()
                
                // Hide keyboard explicitly
                val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                
                // Then trigger the next action
                shouldTriggerNextButton = true
            } catch (e: Exception) {
                Log.e("ServerConnectionStep", "Error dismissing keyboard: ${e.message}")
                shouldDismissKeyboard = false
                // Still try to proceed with the action
                shouldTriggerNextButton = true
            }
        }
    }

    // Handle next action after keyboard is dismissed
    LaunchedEffect(shouldTriggerNextButton) {
        if (isActive && shouldTriggerNextButton) {
            try {
                Log.d("ServerConnectionStep", "Triggering next action after hostname")
                shouldTriggerNextButton = false
                onNext()
            } catch (e: Exception) {
                Log.e("ServerConnectionStep", "Error triggering next action: ${e.message}")
                shouldTriggerNextButton = false
            }
        }
    }
}

@Composable
fun CloudflareConfigStep(
    clientId: String,
    clientSecret: String,
    onClientIdChange: (String) -> Unit,
    onClientSecretChange: (String) -> Unit,
    errorMessage: String? = null,
    successMessage: String? = null,
    onNext: () -> Unit
) {
    val clientIdFocusRequester = remember { FocusRequester() }
    val clientSecretFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var hasInteracted by remember { mutableStateOf(false) }

    ConfigStepLayout(
        title = stringResource(R.string.configScreen_cloudflareConfig),
        helpText = stringResource(R.string.configScreen_cloudflareHelp),
        errorMessage = errorMessage,
        successMessage = successMessage,
        onNext = onNext,
        nextButtonText = stringResource(R.string.configScreen_next)
    ) {
        CommonTextField(
            value = clientId,
            onValueChange = onClientIdChange,
            label = stringResource(R.string.configScreen_cloudflareClientId),
            modifier = Modifier.focusRequester(clientIdFocusRequester),
            keyboardActions = KeyboardActions(
                onDone = {
                    clientSecretFocusRequester.requestFocus()
                }
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        CommonTextField(
            value = clientSecret,
            onValueChange = onClientSecretChange,
            label = stringResource(R.string.configScreen_cloudflareClientSecret),
            isPassword = true,
            modifier = Modifier.focusRequester(clientSecretFocusRequester),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (clientId.isNotEmpty() && clientSecret.isNotEmpty()) {
                        onNext()
                    }
                }
            )
        )
    }

    // Only request initial focus if we haven't interacted with the form yet
    LaunchedEffect(hasInteracted) {
        if (!hasInteracted && errorMessage == null && successMessage == null) {
            Log.d("CloudflareConfigStep", "Requesting focus client ID")
            clientIdFocusRequester.requestFocus()
            hasInteracted = true
        }
    }
}

@Composable
fun AuthMethodStep(
    onMethodSelected: (AuthType) -> Unit,
    viewModel: ConfigViewModel
) {
    var apiKeyButtonFocused by remember { mutableStateOf(false) }
    var localUserButtonFocused by remember { mutableStateOf(false) }
    var jellyfinButtonFocused by remember { mutableStateOf(false) }
    var embyButtonFocused by remember { mutableStateOf(false) }
    var plexButtonFocused by remember { mutableStateOf(false) }
    val userCredFocusRequester = remember { FocusRequester() }
    var serverType by remember { mutableStateOf(ServerType.UNKNOWN) }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // Track composition readiness using derivedStateOf
    val isCompositionReady by remember {
        derivedStateOf {
            // We consider the composition ready when:
            // 1. Server type is known (not UNKNOWN)
            // 2. We haven't requested focus yet
            // 3. The focus requester is attached to the composition
            serverType != ServerType.UNKNOWN && !hasRequestedInitialFocus
        }
    }

    // Update server type when the composable is first created
    LaunchedEffect(Unit) {
        serverType = viewModel.apiService.getServerType()
    }

    // Request focus when composition is ready
    LaunchedEffect(isCompositionReady) {
        if (isCompositionReady) {
            try {
                Log.d("AuthMethodStep", "Requesting focus local user button")
                userCredFocusRequester.requestFocus()
                hasRequestedInitialFocus = true
            } catch (e: Exception) {
                Log.e("AuthMethodStep", "Failed to request focus: ${e.message}")
            }
        }
    }

    ConfigStepLayout(
        title = stringResource(R.string.configScreen_authMethod),
        helpText = stringResource(R.string.configScreen_authMethodHelp),
        centerHelpText = true
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Local User button (first)
                CustomButton(
                    onClick = { onMethodSelected(AuthType.LocalUser) },
                    isFocused = localUserButtonFocused,
                    modifier = Modifier
                        .focusRequester(userCredFocusRequester)
                        .onFocusChanged { localUserButtonFocused = it.isFocused }
                ) {
                    Text(
                        text = stringResource(R.string.configScreen_authTypeLocalUser),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Plex button (available for both Overseerr and Jellyseerr)
                CustomButton(
                    onClick = { onMethodSelected(AuthType.Plex) },
                    isFocused = plexButtonFocused,
                    modifier = Modifier.onFocusChanged { plexButtonFocused = it.isFocused }
                ) {
                    Text(
                        text = stringResource(R.string.configScreen_authTypePlex),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Media server buttons (only show for Jellyseerr and Seerr)
                if (serverType == ServerType.JELLYSEERR || serverType == ServerType.SEERR) {
                    CustomButton(
                        onClick = { onMethodSelected(AuthType.Jellyfin) },
                        isFocused = jellyfinButtonFocused,
                        modifier = Modifier.onFocusChanged { jellyfinButtonFocused = it.isFocused }
                    ) {
                        Text(
                            text = stringResource(R.string.configScreen_authTypeJellyfin),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    CustomButton(
                        onClick = { onMethodSelected(AuthType.Emby) },
                        isFocused = embyButtonFocused,
                        modifier = Modifier.onFocusChanged { embyButtonFocused = it.isFocused }
                    ) {
                        Text(
                            text = stringResource(R.string.configScreen_authTypeEmby),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // API Key button (last)
                CustomButton(
                    onClick = { onMethodSelected(AuthType.ApiKey) },
                    isFocused = apiKeyButtonFocused,
                    modifier = Modifier.onFocusChanged { apiKeyButtonFocused = it.isFocused }
                ) {
                    Text(
                        text = stringResource(R.string.common_apiKey),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ApiKeyConfigStep(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    errorMessage: String? = null,
    successMessage: String? = null,
    onNext: () -> Unit
) {
    val apiKeyFocusRequester = remember { FocusRequester() }
    val nextButtonFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var hasInteracted by remember { mutableStateOf(false) }

    ConfigStepLayout(
        title = stringResource(R.string.common_apiKey),
        helpText = stringResource(R.string.configScreen_apiKeyHelp),
        errorMessage = errorMessage,
        successMessage = successMessage,
        onNext = onNext,
        nextButtonText = stringResource(R.string.configScreen_testAndSave),
        nextButtonFocusRequester = nextButtonFocusRequester
    ) {
        CommonTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = stringResource(R.string.common_apiKey),
            isPassword = true,
            modifier = Modifier.focusRequester(apiKeyFocusRequester),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (apiKey.isNotEmpty()) {
                        onNext()
                    }
                }
            )
        )
    }

    // Only request initial focus if we haven't interacted with the form yet
    LaunchedEffect(hasInteracted) {
        if (!hasInteracted && errorMessage == null && successMessage == null) {
            Log.d("ApiKeyConfigStep", "Requesting focus api key")
            apiKeyFocusRequester.requestFocus()
            hasInteracted = true
        }
    }

    // Request focus back to next button when error occurs
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            Log.d("ApiKeyConfigStep", "Error occurred, focusing next button")
            delay(100) // Small delay to ensure error message is shown
            nextButtonFocusRequester.requestFocus()
        }
    }
}

@Composable
fun LocalUserStep(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    errorMessage: String? = null,
    successMessage: String? = null,
    onNext: () -> Unit
) {
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val nextButtonFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var hasInteracted by remember { mutableStateOf(false) }

    ConfigStepLayout(
        title = stringResource(R.string.configScreen_localUser),
        helpText = stringResource(R.string.configScreen_localUserHelp),
        errorMessage = errorMessage,
        successMessage = successMessage,
        onNext = onNext,
        nextButtonText = stringResource(R.string.configScreen_testAndSave),
        nextButtonFocusRequester = nextButtonFocusRequester
    ) {
        CommonTextField(
            value = username,
            onValueChange = { onUsernameChange(cleanInput(it, "username")) },
            label = stringResource(R.string.common_email),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocusRequester),
            keyboardActions = KeyboardActions(
                onDone = {
                    passwordFocusRequester.requestFocus()
                }
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        CommonTextField(
            value = password,
            onValueChange = { onPasswordChange(cleanInput(it, "password")) },
            label = stringResource(R.string.configScreen_password),
            isPassword = true,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .focusRequester(passwordFocusRequester),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        onNext()
                    }
                }
            )
        )
    }

    // Only request initial focus if we haven't interacted with the form yet
    LaunchedEffect(hasInteracted) {
        if (!hasInteracted && errorMessage == null && successMessage == null) {
            Log.d("LocalUserStep", "Requesting focus email")
            emailFocusRequester.requestFocus()
            hasInteracted = true
        }
    }

    // Request focus back to next button when error occurs
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            Log.d("LocalUserStep", "Error occurred, focusing next button")
            delay(100) // Small delay to ensure error message is shown
            nextButtonFocusRequester.requestFocus()
        }
    }
}

@Composable
fun JellyfinConfigStep(
    jellyfinHostname: String,
    jellyfinPort: Int,
    jellyfinUseSsl: Boolean,
    username: String,
    password: String,
    onHostnameChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onUseSslChange: (Boolean) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    errorMessage: String? = null,
    successMessage: String? = null,
    onNext: () -> Unit,
    authType: AuthType = AuthType.Jellyfin
) {
    val scrollState = rememberScrollState()
    val sslFocusRequester = remember { FocusRequester() }
    val hostnameFocusRequester = remember { FocusRequester() }
    val portFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var hasInteracted by remember { mutableStateOf(false) }
    var sslSwitchFocused by remember { mutableStateOf(false) }
    var portText by remember(jellyfinPort) { mutableStateOf(jellyfinPort.toString()) }
    var isValidPort by remember { mutableStateOf(true) }

    ConfigStepLayout(
        title = if (authType == AuthType.Emby) 
            stringResource(R.string.configScreen_embyConfig)
        else 
            stringResource(R.string.configScreen_jellyfinConfig),
        helpText = if (authType == AuthType.Emby) 
            stringResource(R.string.configScreen_embyHelp)
        else 
            stringResource(R.string.configScreen_jellyfinHelp),
        errorMessage = errorMessage,
        successMessage = successMessage,
        onNext = onNext,
        nextButtonText = stringResource(R.string.configScreen_testAndSave),
        nextButtonEnabled = (authType == AuthType.Emby || isValidPort) && portText.isNotEmpty(),
        modifier = Modifier.verticalScroll(scrollState)
    ) {
        // Only show these fields for Jellyfin
        if (authType == AuthType.Jellyfin) {
            // SSL Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.configScreen_jellyfinUseSsl),
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .focusRequester(sslFocusRequester)
                        .onFocusChanged { sslSwitchFocused = it.isFocused }
                        .border(
                            width = if (sslSwitchFocused) 2.dp else 0.dp,
                            color = if (sslSwitchFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(2.dp)
                        .size(width = 52.dp, height = 32.dp)
                ) {
                    Switch(
                        checked = jellyfinUseSsl,
                        onCheckedChange = onUseSslChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFBB86FC),
                            checkedTrackColor = Color(0xFF6200EE),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Hostname and Port in a row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommonTextField(
                    value = jellyfinHostname,
                    onValueChange = { onHostnameChange(cleanInput(it, "jellyfinHostname")) },
                    label = stringResource(R.string.configScreen_jellyfinHostname),
                    modifier = Modifier
                        .weight(0.7f)
                        .focusRequester(hostnameFocusRequester),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            portFocusRequester.requestFocus()
                        }
                    )
                )

                CommonTextField(
                    value = portText,
                    onValueChange = { newValue ->
                        val cleaned = cleanInput(newValue, "port")
                        portText = cleaned
                        if (cleaned.isEmpty()) {
                            isValidPort = false
                        } else {
                            cleaned.toIntOrNull()?.let { port ->
                                if (port in 1..65535) {
                                    isValidPort = true
                                    onPortChange(port)
                                } else {
                                    isValidPort = false
                                }
                            } ?: run {
                                isValidPort = false
                            }
                        }
                    },
                    label = stringResource(R.string.configScreen_jellyfinPort),
                    isError = !isValidPort,
                    modifier = Modifier
                        .weight(0.3f)
                        .focusRequester(portFocusRequester),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            usernameFocusRequester.requestFocus()
                        }
                    )
                )
            }
        }

        // Username field
        CommonTextField(
            value = username,
            onValueChange = { onUsernameChange(cleanInput(it, "username")) },
            label = stringResource(R.string.configScreen_username),
            modifier = Modifier
                .padding(bottom = 16.dp)
                .focusRequester(usernameFocusRequester),
            keyboardActions = KeyboardActions(
                onDone = {
                    passwordFocusRequester.requestFocus()
                }
            )
        )

        // Password field
        CommonTextField(
            value = password,
            onValueChange = { onPasswordChange(cleanInput(it, "password")) },
            label = stringResource(R.string.configScreen_password),
            isPassword = true,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .focusRequester(passwordFocusRequester),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (username.isNotEmpty() && password.isNotEmpty() &&
                        (authType != AuthType.Jellyfin || (jellyfinHostname.isNotEmpty() && isValidPort))) {
                        onNext()
                    }
                }
            )
        )
    }

    // Set up initial focus
    LaunchedEffect(hasInteracted) {
        if (!hasInteracted && errorMessage == null && successMessage == null) {
            if (authType == AuthType.Jellyfin) {
                Log.d("JellyfinConfigStep", "Requesting focus ssl")
                sslFocusRequester.requestFocus()
            } else {
                usernameFocusRequester.requestFocus()
            }
            hasInteracted = true
        }
    }
}

@Composable
fun PlexConfigStep(
    context: Context,
    protocol: String,
    hostname: String,
    enableCloudflare: Boolean,
    cloudflareClientId: String,
    cloudflareClientSecret: String,
    errorMessage: String? = null,
    successMessage: String? = null,
    onError: (String) -> Unit,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ConfigViewModel
) {
    var cancelButtonFocused by remember { mutableStateOf(false) }
    var isPolling by remember { mutableStateOf(false) }
    var localPlexPin by remember { mutableStateOf("") }
    var showWaitingForAuth by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val cancelButtonFocusRequester = remember { FocusRequester() }

    // Reset state when entering the screen
    LaunchedEffect(Unit) {
        Log.d("ConfigScreen", "Starting Plex config setup")
        // Reset all state variables
        isActive = true
        isPolling = false
        localPlexPin = ""
        showWaitingForAuth = false
        errorMessage?.let { onError("") } // Clear any previous error
        
        try {
            isPolling = true
            val plexClientId = SharedPreferencesUtil.getOrGeneratePlexClientId(context)
            
            Log.d("ConfigScreen", "Requesting Plex PIN...")
            try {
                val setupResponse = viewModel.requestPlexPin(plexClientId)
                if (!isActive) {
                    Log.d("ConfigScreen", "Setup cancelled, stopping PIN setup")
                    return@LaunchedEffect
                }
                localPlexPin = setupResponse.code
                val plexPinId = setupResponse.id
                Log.d("ConfigScreen", "Received Plex PIN: $localPlexPin")

                if (!isActive) {
                    Log.d("ConfigScreen", "Setup cancelled, stopping before polling")
                    return@LaunchedEffect
                }

                startPollingForToken(
                    plexPinId = plexPinId,
                    plexClientId = plexClientId,
                    viewModel = viewModel,
                    isActive = { isActive },
                    onSuccess = { token ->
                        if (!isActive) return@startPollingForToken
                        scope.launch {
                            Log.d("ConfigScreen", "Got Plex token, authenticating with server")
                            authenticateWithServer(
                                token = token,
                                protocol = protocol,
                                hostname = hostname,
                                enableCloudflare = enableCloudflare,
                                cloudflareClientId = cloudflareClientId,
                                cloudflareClientSecret = cloudflareClientSecret,
                                plexClientId = plexClientId,
                                viewModel = viewModel,
                                onSuccess = {
                                    if (!isActive) return@authenticateWithServer
                                    Log.d("ConfigScreen", "Server authentication successful")
                                    isPolling = false
                                    showWaitingForAuth = false
                                    scope.launch {
                                        onSuccess()
                                    }
                                },
                                onError = { message ->
                                    if (!isActive) return@authenticateWithServer
                                    Log.e("ConfigScreen", "Server authentication failed: $message")
                                    isPolling = false
                                    showWaitingForAuth = false
                                    onError(message)
                                }
                            )
                        }
                    },
                    onError = { message ->
                        if (!isActive) return@startPollingForToken
                        Log.e("ConfigScreen", "Plex token polling failed: $message")
                        isPolling = false
                        showWaitingForAuth = false
                        onError(message)
                    }
                )
            } catch (e: Exception) {
                if (!isActive) return@LaunchedEffect
                Log.e("ConfigScreen", "Failed to get Plex PIN", e)
                isPolling = false
                showWaitingForAuth = false
                onError("Failed to get Plex PIN: ${e.message}")
            }
        } catch (e: Exception) {
            if (!isActive) return@LaunchedEffect
            Log.e("ConfigScreen", "Error starting Plex auth", e)
            isPolling = false
            showWaitingForAuth = false
            onError("Error starting Plex auth: ${e.message}")
        }
    }

    // Update showWaitingForAuth based on PIN and polling state
    LaunchedEffect(localPlexPin, isPolling, isActive) {
        showWaitingForAuth = localPlexPin.isNotEmpty() && isPolling && isActive
        Log.d("ConfigScreen", "Updated waiting state: showWaitingForAuth=$showWaitingForAuth (pin=${localPlexPin.isNotEmpty()}, polling=$isPolling, active=$isActive)")
    }

    // Clean up polling when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            Log.d("ConfigScreen", "Cleaning up Plex config step")
            isActive = false
            isPolling = false
            showWaitingForAuth = false
            localPlexPin = ""
            errorMessage?.let { onError("") } // Clear any error messages
        }
    }

    ConfigStepLayout(
        title = stringResource(R.string.configScreen_plexTitle),
        helpText = stringResource(R.string.configScreen_plexHelp),
        errorMessage = errorMessage,
        successMessage = successMessage
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Instructions are always shown
            Text(
                text = stringResource(R.string.configScreen_plexInstructions),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // Show either "Getting PIN" state or PIN with instructions
            if (localPlexPin.isEmpty()) {
                // Still waiting for PIN
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.configScreen_plexGettingPin),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            } else {
                // PIN received - show PIN and link
                Text(
                    text = stringResource(R.string.configScreen_plexPinCode),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                
                Text(
                    text = localPlexPin,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color(0xFFBB86FC),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                
                Text(
                    text = stringResource(R.string.configScreen_plexVisitLink),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                
                Text(
                    text = "https://plex.tv/link",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = Color(0xFF3370FF)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Only show waiting message if we're still polling and haven't received success/error
                if (showWaitingForAuth && errorMessage == null && successMessage == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.configScreen_waitingAuth),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Always show cancel button
            CustomButton(
                onClick = {
                    Log.d("ConfigScreen", "Cancel button clicked")
                    isActive = false
                    isPolling = false
                    showWaitingForAuth = false
                    localPlexPin = ""
                    errorMessage?.let { onError("") }
                    onCancel()
                },
                isFocused = cancelButtonFocused,
                modifier = Modifier
                    .focusRequester(cancelButtonFocusRequester)
                    .onFocusChanged { cancelButtonFocused = it.isFocused }
                    .focusable()
            ) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = Color.White
                )
            }
        }
    }

    // Set initial focus to cancel button
    LaunchedEffect(Unit) {
        delay(100) // Small delay to ensure button is ready
        cancelButtonFocusRequester.requestFocus()
    }
}

private suspend fun startPollingForToken(
    plexPinId: String,
    plexClientId: String,
    viewModel: ConfigViewModel,
    isActive: () -> Boolean,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val startTime = System.currentTimeMillis()
    val timeoutMillis = 5 * 60 * 1000 // 5 minutes timeout
    var attempts = 0
    var isPolling = true
    
    while (isPolling && System.currentTimeMillis() - startTime < timeoutMillis && isActive()) {
        attempts++
        try {
            Log.d("ConfigScreen", "Checking Plex auth status (attempt $attempts)...")
            val plexAuthResponse = viewModel.checkPlexPinAuth(plexPinId, plexClientId)
            val token = plexAuthResponse.authToken

            // Check to see if we have a valid token
            // The token must be non-null and non-empty.. it has to be at more than 1 character
            Log.d("ConfigScreen", "Valid auth token received on attempt $attempts")
            isPolling = false
            onSuccess(token)
        } catch (e: Exception) {
            Log.w("ConfigScreen", "Error during polling (attempt $attempts): ${e.message}")
            if (e.message?.contains("Waiting for user authentication") == true) {
                Log.d("ConfigScreen", "Still waiting for user authentication...")
                delay(5000) // Wait 5 seconds before retrying
            } else {
                isPolling = false
                onError("Failed to check auth status: ${e.message}")
            }
        }
    }
    
    // If we're still polling when we exit the loop, we hit the timeout
    if (isPolling && isActive()) {
        onError("Plex authentication timed out after $attempts attempts. Please try again.")
    }
}

private suspend fun authenticateWithServer(
    token: String,
    protocol: String,
    hostname: String,
    enableCloudflare: Boolean,
    cloudflareClientId: String,
    cloudflareClientSecret: String,
    plexClientId: String,
    viewModel: ConfigViewModel,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        // Create config with Plex auth
        val config = SeerrConfig(
            protocol = protocol,
            hostname = hostname,
            cloudflareEnabled = enableCloudflare,
            cfClientId = cloudflareClientId,
            cfClientSecret = cloudflareClientSecret,
            authType = AuthType.Plex.type,
            apiKey = "",
            username = "",
            password = "",
            plexClientId = plexClientId,
            plexAuthToken = token,
            isSubmitted = true,
            createdAt = ""
        )

        // Validate and save config with Plex auth token
        Log.d("ConfigScreen", "Authenticating with server using Plex token...")
        when (val validationResult = viewModel.validateAndSaveConfig(config, useBrowserValidation = false)) {
            is ApiValidationResult.Success -> {
                onSuccess()
            }
            is ApiValidationResult.Error -> {
                onError(validationResult.message)
            }
            is ApiValidationResult.CloudflareRequired -> {
                onError("Cloudflare authentication required")
            }
        }
    } catch (e: Exception) {
        Log.e("ConfigScreen", "Error authenticating with server", e)
        onError("Error authenticating with server: ${e.message}")
    }
}

enum class ConfigMethod {
    BROWSER,
    MANUAL
}

@Composable
private fun ConfigStepLayout(
    modifier: Modifier = Modifier,
    title: String,
    helpText: String? = null,
    centerHelpText: Boolean = false,
    errorMessage: String? = null,
    successMessage: String? = null,
    onNext: (() -> Unit)? = null,
    nextButtonText: String? = null,
    nextButtonEnabled: Boolean = true,
    nextButtonFocusRequester: FocusRequester? = null,
    nextButtonFocused: Boolean = false,
    nextButtonControlledExternally: Boolean = false,
    isTitleFocusable: Boolean = false,
    titleFocusRequester: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var internalNextButtonFocused by remember { mutableStateOf(false) }
    val nextButtonFocusedState = if (nextButtonFocused) nextButtonFocused else internalNextButtonFocused
    val defaultButtonFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = nextButtonFocusRequester ?: defaultButtonFocusRequester
    val defaultTitleFocusRequester = remember { FocusRequester() }
    val actualTitleFocusRequester = titleFocusRequester ?: defaultTitleFocusRequester
    val focusManager = LocalFocusManager.current

    // Handle focus changes when success message or error message appears
    LaunchedEffect(successMessage, errorMessage) {
        if (successMessage != null || errorMessage != null) {
            // Clear focus to dismiss keyboard
            focusManager.clearFocus()
            
            // If there's an error and we have a next button, focus it
            if (errorMessage != null && onNext != null) {
                try {
                    Log.d("ConfigScreen:ConfigStepLayout", "Error message shown, focusing button")
                    delay(100) // Small delay to ensure UI is ready
                    buttonFocusRequester.requestFocus()
                } catch (e: Exception) {
                    Log.e("ConfigScreen:ConfigStepLayout", "Error focusing button: ${e.message}")
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFBB86FC),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .run {
                        if (isTitleFocusable) {
                            this.focusRequester(actualTitleFocusRequester).focusable()
                        } else {
                            this
                        }
                    }
            )
            if (helpText != null) {
                Text(
                    text = helpText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = if (centerHelpText) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            }
            content()
            Spacer(modifier = Modifier.height(16.dp))
            ValidationMessages(errorMessage, successMessage)
            if (onNext != null && nextButtonText != null && successMessage == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CustomButton(
                        onClick = {
                            if (!nextButtonControlledExternally) {
                                Log.d("ConfigScreen:ConfigStepLayout", "Next button clicked")
                                onNext()
                            }
                        },
                        enabled = nextButtonEnabled,
                        isFocused = nextButtonFocusedState,
                        modifier = Modifier
                            .then(
                                if (nextButtonControlledExternally) {
                                    Modifier // No focus management when externally controlled
                                } else {
                                    Modifier
                                        .focusRequester(buttonFocusRequester)
                                        .onFocusChanged { 
                                            if (!nextButtonFocused) {
                                                // Only update internal state if not externally controlled
                                                internalNextButtonFocused = it.isFocused
                                            }
                                            if (!it.isFocused) {
                                                // Don't clear focus when losing focus, let the button handle it
                                                return@onFocusChanged
                                            }
                                        }
                                        .focusable()
                                }
                            )
                    ) {
                        Text(
                            text = nextButtonText,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

class LanguageSelectionController(
    private val context: Context,
    private val languageOptions: List<Pair<String, Int>>,
    private val onLanguageSelected: (String) -> Unit,
    private val onNext: () -> Unit
) {
    val currentLanguage = SharedPreferencesUtil.getAppLanguage(context) ?: "en"
    
    // Navigation state: 0..size-1 = list item focused
    var selectedIndex by mutableStateOf(
        languageOptions.indexOfFirst { it.first == currentLanguage }.coerceAtLeast(0)
    )
        private set
    
    var selectedLanguage by mutableStateOf(currentLanguage)
        private set
    
    private fun selectLanguageByIndex(index: Int) {
        selectedIndex = index
        val (code, _) = languageOptions[index]
        if (code != currentLanguage) {
            selectedLanguage = code
            SharedPreferencesUtil.setAppLanguage(context, code)
            onLanguageSelected(code)
        } else {
            selectedLanguage = code
        }
    }
    
    fun handleKeyEvent(event: KeyEvent): Boolean {
        return when {
            event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                if (selectedIndex < languageOptions.size - 1) {
                    selectedIndex++
                    true
                } else {
                    true
                }
            }
            
            event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                if (selectedIndex > 0) {
                    selectedIndex--
                    true
                } else {
                    false
                }
            }
            
            (event.key == Key.Enter || event.key == Key.DirectionCenter) && event.type == KeyEventType.KeyDown -> {
                if (selectedIndex >= 0 && selectedIndex < languageOptions.size) {
                    selectLanguageByIndex(selectedIndex)
                    onNext()
                    true
                } else {
                    false
                }
            }
            
            else -> false
        }
    }
}

@Composable
fun LanguageSelectionStep(
    context: Context,
    languageOptions: List<Pair<String, Int>>,
    onNext: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    
    val controller = remember {
        LanguageSelectionController(
            context = context,
            languageOptions = languageOptions,
            onLanguageSelected = {
                (context as? android.app.Activity)?.recreate()
            },
            onNext = onNext
        )
    }
    
    // Initial setup: scroll to selected item and request focus
    LaunchedEffect(Unit) {
        if (controller.selectedIndex >= 0 && controller.selectedIndex < languageOptions.size) {
            listState.animateScrollToItem(controller.selectedIndex)
            focusRequester.requestFocus()
        }
    }
    
    // Auto-scroll when selected index changes
    LaunchedEffect(controller.selectedIndex) {
        if (controller.selectedIndex >= 0 && controller.selectedIndex < languageOptions.size) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val firstVisibleIndex = listState.firstVisibleItemIndex
                val lastVisibleIndex = visibleItems.last().index
                
                when {
                    controller.selectedIndex >= lastVisibleIndex - 1 -> {
                        val targetIndex = (firstVisibleIndex + 1).coerceAtMost(layoutInfo.totalItemsCount - 1)
                        if (targetIndex != firstVisibleIndex && targetIndex >= 0) {
                            listState.animateScrollToItem(targetIndex)
                        }
                    }
                    controller.selectedIndex <= firstVisibleIndex + 1 -> {
                        val targetIndex = (firstVisibleIndex - 1).coerceAtLeast(0)
                        if (targetIndex != firstVisibleIndex && targetIndex >= 0) {
                            listState.animateScrollToItem(targetIndex)
                        }
                    }
                    controller.selectedIndex < firstVisibleIndex || controller.selectedIndex > lastVisibleIndex -> {
                        listState.animateScrollToItem(controller.selectedIndex)
                    }
                }
            } else {
                listState.animateScrollToItem(controller.selectedIndex)
            }
        }
    }
    
    // Navigation manager Box - holds focus all the time
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                // Re-request focus if lost to ensure controller always has focus
                if (!focusState.isFocused) {
                    scope.launch {
                        focusRequester.requestFocus()
                    }
                }
            }
            .onKeyEvent { event ->
                controller.handleKeyEvent(event)
            }
    ) {
        ConfigStepLayout(
            title = stringResource(R.string.settingsMenu_appLanguage),
            helpText = "Select your preferred language",
            onNext = null,
            nextButtonText = null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .border(1.dp, Color(0xFF374151), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
            items(
                count = languageOptions.size,
                key = { it }
            ) { index ->
                val (code, resId) = languageOptions[index]
                val isSelected = code == controller.selectedLanguage
                val isFocused = controller.selectedIndex == index

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(
                            width = if (isFocused) 2.dp else 0.dp,
                            color = if (isFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .background(
                            if (isFocused) Color(0xFF2C3E50) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                     RadioButton(
                        selected = isSelected,
                        onClick = { /* Handled by navigation controller */ },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color(0xFFBB86FC),
                            unselectedColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(resId),
                        color = if (isFocused) Color(0xFFBB86FC) else Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        }
        }
    }
}

@Composable
fun ConfigSelectionScreen(
    onMethodSelected: (ConfigMethod) -> Unit
) {
    var browserButtonFocused by remember { mutableStateOf(false) }
    var manualButtonFocused by remember { mutableStateOf(false) }
    val browserFocusRequester = remember { FocusRequester() }
    var isReadyForInteraction by remember { mutableStateOf(false) }
    var focusRequestAttempted by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ConfigStepLayout(
            title = stringResource(R.string.configScreen_selectConfigurationMethod),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                AppLogo(
                    imageHeight = 150,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CustomButton(
                        onClick = { 
                            if (isReadyForInteraction) {
                                onMethodSelected(ConfigMethod.BROWSER)
                            }
                        },
                        isFocused = browserButtonFocused,
                        modifier = Modifier
                            .focusRequester(browserFocusRequester)
                            .onFocusChanged { 
                                browserButtonFocused = it.isFocused
                                // Once we receive focus, enable interactions
                                if (it.isFocused && !isReadyForInteraction) {
                                    isReadyForInteraction = true
                                    Log.d("ConfigSelectionScreen", "Focus received, ready for interaction")
                                }
                            }
                    ) {
                        Text(
                            text = stringResource(R.string.configScreen_browserBasedConfiguration),
                            color = Color.White
                        )
                    }

                    CustomButton(
                        onClick = { 
                            if (isReadyForInteraction) {
                                onMethodSelected(ConfigMethod.MANUAL)
                            }
                        },
                        isFocused = manualButtonFocused,
                        modifier = Modifier.onFocusChanged { manualButtonFocused = it.isFocused }
                    ) {
                        Text(
                            text = stringResource(R.string.configScreen_manualConfiguration),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Request focus once the composable is fully composed and ready
    LaunchedEffect(Unit) {
        if (!focusRequestAttempted) {
            // Wait for the next frame to ensure composition and layout are complete
            withFrameMillis { }
            try {
                Log.d("ConfigSelectionScreen", "Composition and layout complete, requesting focus")
                browserFocusRequester.requestFocus()
                focusRequestAttempted = true
            } catch (e: IllegalStateException) {
                Log.w("ConfigSelectionScreen", "Focus request failed: ${e.message}")
                // If focus request fails, try again after a short delay
                delay(50)
                try {
                    browserFocusRequester.requestFocus()
                    focusRequestAttempted = true
                } catch (e2: Exception) {
                    Log.e("ConfigSelectionScreen", "Focus request retry failed: ${e2.message}")
                }
            }
        }
    }
}

@Composable
fun CustomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    // Use Box with explicit key handling for more reliable TV navigation
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .background(
                color = if (isFocused) Color(0xFFBB86FC) else Color(0xFF6200EE),
                shape = RoundedCornerShape(16.dp)
            )
            .width(300.dp)
            .height(48.dp)
            // Handle key events directly for more reliable TV remote input
            .onKeyEvent { keyEvent ->
                if ((keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter) && 
                    keyEvent.type == KeyEventType.KeyDown && 
                    enabled && 
                    isFocused) {
                    Log.d("CustomButton", "Button activated via key ${keyEvent.key}")
                    onClick()
                    true
                } else {
                    false
                }
            }
            // Also add clickable for touch support
            .clickable(
                enabled = enabled && isFocused,
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Material3 handles ripple automatically
            ) {
                Log.d("CustomButton", "Button clicked via clickable")
                onClick()
            }
            .focusable(enabled),
        contentAlignment = Alignment.Center
    ) {
        ProvideTextStyle(
            value = MaterialTheme.typography.bodyLarge.copy(
                textAlign = TextAlign.Center
            )
        ) {
            content()
        }
    }
}

@Composable
fun BrowserConfigScreen(
    context: Context,
    onConfigComplete: () -> Unit,
    viewModel: ConfigViewModel,
    onBackToSelection: () -> Unit,
) {
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var formUrl by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPolling by remember { mutableStateOf(false) }
    var showLoginSuccessModal by remember { mutableStateOf(false) }
    var hasCompletedConfig by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Get string resources at composition time
    val successString = stringResource(R.string.configScreen_configSuccess)

    // Function to start polling
    fun startPolling(setupId: String) {
        isPolling = true
        errorMessage = null
        scope.launch {
            while (isPolling) {
                delay(5000) // Poll every 5 seconds
                when (val configResult = viewModel.apiService.getBrowserConfig(setupId)) {
                    is ApiResult.Success<SeerrConfig> -> {
                        val config = configResult.data
                        if (config.isSubmitted) {
                            // create a new config object and override the auth_type of "username" to "localUser"
                            val newConfig = config.copy(authType = if (config.authType == "username") "localUser" else config.authType)
                            when (val validationResult = viewModel.validateAndSaveConfig(newConfig, useBrowserValidation = true)) {
                                is ApiValidationResult.Success -> {
                                    Log.d("BrowserConfigScreen", "Configuration saved successfully")
                                    successMessage = successString
                                    isPolling = false
                                    hasCompletedConfig = true
                                    break
                                }
                                is ApiValidationResult.Error -> {
                                    Log.e("BrowserConfigScreen", "Validation failed: ${validationResult.message}")
                                    errorMessage = validationResult.message
                                    isPolling = false
                                    break
                                }
                                is ApiValidationResult.CloudflareRequired -> {
                                    Log.e("BrowserConfigScreen", "Cloudflare authentication required: ${validationResult.message}")
                                    errorMessage = validationResult.message
                                    isPolling = false
                                    break
                                }
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        if (configResult.exception !is ClientRequestException) {
                            errorMessage = context.getString(R.string.configScreen_connectionFailed)
                            isPolling = false
                            break
                        }
                    }
                    is ApiResult.Loading -> { /* Continue polling */ }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            when (val result = viewModel.apiService.getSetupId()) {
                is ApiResult.Success<SetupIdResponse> -> {
                    formUrl = "${BuildConfig.BROWSER_CONFIG_BASE_URL}/${result.data.id}"
                    qrCodeBitmap = generateQRCode(formUrl, 500, 500)
                    isLoading = false
                    startPolling(result.data.id)
                }
                is ApiResult.Error -> {
                    Log.e("BrowserConfigScreen", "Setup ID Error: ${result.exception.message}")
                    errorMessage = when {
                        result.exception.message?.contains("timeout", ignoreCase = true) == true -> 
                            context.getString(R.string.configScreen_connectionTimeout)
                        result.exception is ClientRequestException -> 
                            context.getString(R.string.configScreen_serverError) + " ${result.exception.response.status}"
                        result.exception is ServerResponseException -> 
                            context.getString(R.string.configScreen_serverError) + " ${result.exception.response.status}"
                        else -> context.getString(R.string.configScreen_configServiceUnavailable)
                    }
                    isLoading = false
                }
                is ApiResult.Loading -> { /* Keep loading state */ }
            }
        } catch (e: Exception) {
            Log.e("BrowserConfigScreen", "Setup Error: ${e.message}")
            errorMessage = e.message ?: context.getString(R.string.configScreen_unknownError)
            isLoading = false
        }
    }

    // Show login success modal if config is complete and we haven't shown it yet
    LaunchedEffect(hasCompletedConfig) {
        if (hasCompletedConfig) {
            viewModel.apiService.getCurrentUserInfo()?.let { userInfo ->
                showLoginSuccessModal = true
            }
        }
    }

    // Clean up polling when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            isPolling = false
        }
    }

    ConfigStepLayout(
        title = stringResource(R.string.configScreen_browserBasedConfiguration),
        errorMessage = errorMessage,
        successMessage = successMessage,
        onNext = if (errorMessage != null) ({
            onBackToSelection()
        }) else null,
        nextButtonText = if (errorMessage != null) stringResource(R.string.common_cancel) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.configScreen_connecting),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (qrCodeBitmap != null && formUrl.isNotEmpty() && errorMessage == null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.configScreen_configInstructions),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Image(
                        bitmap = qrCodeBitmap!!.asImageBitmap(),
                        contentDescription = stringResource(R.string.configScreen_qrCode),
                        modifier = Modifier.size(200.dp)
                    )
                    
                    Text(
                        text = stringResource(R.string.configScreen_orVisit),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = formUrl,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = Color(0xFF3370FF),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isPolling && !showLoginSuccessModal) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = stringResource(R.string.configScreen_waitingAuth),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Show login success modal if needed
    if (showLoginSuccessModal && hasCompletedConfig) {
        viewModel.apiService.getCurrentUserInfo()?.let { userInfo ->
            LoginSuccessModal(
                context = context,
                userInfo = userInfo,
                onDismiss = {
                    showLoginSuccessModal = false
                    hasCompletedConfig = false
                    onConfigComplete()
                    // Navigation is now handled by MainActivity
                }
            )
        }
    }
}

// Common composables and utilities
@Composable
private fun CommonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions: KeyboardActions? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    
    // Create default keyboard actions that clear focus on Done if none provided
    val actualKeyboardActions = keyboardActions ?: KeyboardActions(
        onDone = {
            focusManager.clearFocus()
        }
    )
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = if (isError) Color.Red else Color(0xFFBB86FC),
            unfocusedBorderColor = if (isError) Color.Red.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f),
            focusedLabelColor = if (isError) Color.Red else Color(0xFFBB86FC),
            unfocusedLabelColor = if (isError) Color.Red.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = actualKeyboardActions,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
    )
}

@Composable
private fun ValidationMessages(
    errorMessage: String?,
    successMessage: String?,
    modifier: Modifier = Modifier
) {
    if (errorMessage != null || successMessage != null) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage ?: successMessage ?: "",
                color = if (errorMessage != null) Color(0xFFF44336) else Color(0xFF00C853),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun LoginSuccessModal(
    context: Context,
    userInfo: SeerrApiService.UserInfo,
    onDismiss: () -> Unit
) {
    var okButtonFocused by remember { mutableStateOf(true) }
    val permissions = remember(userInfo.permissions) {
        CommonUtil.getPermissionsList(userInfo.permissions)
    }
    val okButtonfocusRequester = remember { FocusRequester() }
    var isDismissing by remember { mutableStateOf(false) }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .background(Color(0x88000000))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1F2937)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.configScreen_loginSuccess),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFBB86FC),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InfoRow(
                        label = stringResource(R.string.configScreen_displayName),
                        value = userInfo.displayName
                    )

                    InfoRow(
                        label = stringResource(R.string.settingsMenu_hostname),
                        value = SharedPreferencesUtil.getApiUrl(context) ?: ""
                    )

                    InfoRow(
                        label = stringResource(R.string.settingsMenu_serverType),
                        value = SharedPreferencesUtil.getServerType(context)
                    )

                    InfoRow(
                        label = stringResource(R.string.settingsMenu_authType),
                        value = when (SharedPreferencesUtil.getConfig(context)?.authType) {
                            AuthType.ApiKey.type -> stringResource(R.string.common_apiKey)
                            AuthType.LocalUser.type -> stringResource(R.string.configScreen_authTypeLocalUser)
                            AuthType.Jellyfin.type -> stringResource(R.string.configScreen_authTypeJellyfin)
                            AuthType.Emby.type -> stringResource(R.string.configScreen_authTypeEmby)
                            AuthType.Plex.type -> stringResource(R.string.configScreen_authTypePlex)
                            else -> ""
                        }
                    )

                    if (permissions.isNotEmpty()) {
                        InfoRow(
                            label = stringResource(R.string.configScreen_permissions),
                            value = permissions.joinToString(", ")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CustomButton(
                        onClick = {
                            if (!isDismissing) {
                                Log.d("LoginSuccessModal", "Button clicked")
                                isDismissing = true
                                onDismiss()
                            }
                        },
                        isFocused = okButtonFocused && !isDismissing,
                        modifier = Modifier
                            .focusRequester(okButtonfocusRequester)
                            .onFocusChanged { state -> 
                                if (!isDismissing) {
                                    Log.d("LoginSuccessModal", "Focus changed: isFocused=${state.isFocused}")
                                    okButtonFocused = state.isFocused 
                                }
                            }
                    ) {
                        Text(
                            text = stringResource(R.string.mediaDetails_ok),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Request focus only once when the modal first appears
    LaunchedEffect(Unit) {
        if (!hasRequestedInitialFocus && !isDismissing) {
            Log.d("LoginSuccessModal", "Initial composition started")
            delay(100)
            Log.d("LoginSuccessModal", "Composition ready")
            Log.d("LoginSuccessModal", "Requesting focus")
            okButtonfocusRequester.requestFocus()
            hasRequestedInitialFocus = true
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Label column (left)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFBB86FC),
            modifier = Modifier.weight(0.4f)
        )
        
        // Value column (right)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(0.6f)
        )
    }
}

private fun cleanInput(input: String, type: String): String {
    return when (type) {
        "hostname" -> SharedPreferencesUtil.normalizeHostname(input, stripPort = false)
        "jellyfinHostname" -> SharedPreferencesUtil.normalizeHostname(input, stripPort = true)
        "port" -> input.filter { it.isDigit() }
        "username", "password" -> input.trim()
        else -> input.trim()
    }
}

private fun handleApiValidationResult(
    result: ApiValidationResult,
    onSuccess: (ServerType?) -> Unit,
    onError: (String) -> Unit,
    onCloudflareRequired: () -> Unit,
    authFailedString: String
) {
    when (result) {
        is ApiValidationResult.Success -> {
            onSuccess(result.serverType)
        }
        is ApiValidationResult.Error -> {
            val errorMessage = if (result.message.contains("403") || 
                                 result.message.contains("permission") ||
                                 result.message.contains("unauthorized", ignoreCase = true)) {
                authFailedString
            } else {
                result.message
            }
            onError(errorMessage)
        }
        is ApiValidationResult.CloudflareRequired -> {
            onCloudflareRequired()
        }
    }
}