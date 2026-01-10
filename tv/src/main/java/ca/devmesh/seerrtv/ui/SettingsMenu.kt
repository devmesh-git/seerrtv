package ca.devmesh.seerrtv.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.model.AuthType
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.ui.components.AppLogo
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import ca.devmesh.seerrtv.util.checkForUpdateIfAvailable
import ca.devmesh.seerrtv.util.UpdateInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import android.util.Log
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.model.Region

@Composable
fun SettingsMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onOpenConfigScreen: () -> Unit,
    updateJsonUrl: String = "https://release.devmesh.ca/u/update.json",
    viewModel: ca.devmesh.seerrtv.viewmodel.MediaDiscoveryViewModel? = null
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val controller = remember {
        SettingsMenuController(
            onDismiss = onDismiss,
            onOpenConfigScreen = onOpenConfigScreen,
            hostname = SharedPreferencesUtil.getApiUrl(context) ?: "",
            context = context
        )
    }
    
    // Build menu items directly here, not in the controller
    val menuItems = run {
        val items = mutableListOf<MenuItem>()
        items.add(MenuItem(context.getString(R.string.settingsMenu_configMenu), ""))
        items.add(MenuItem(context.getString(R.string.settingsMenu_appLanguage), context.getString(controller.getAppLanguageLabelRes(controller.appLanguage))))
        items.add(MenuItem(context.getString(R.string.settingsMenu_discoveryMenu), context.getString(controller.getDiscoveryLanguageLabelRes(controller.discoveryLanguage))))
        items.add(MenuItem(context.getString(R.string.settingsMenu_defaultStreamingRegion), controller.getDefaultStreamingRegionDisplayName()))
        items.add(MenuItem(context.getString(R.string.settingsMenu_folderSelection), if (controller.folderSelectionEnabled) context.getString(R.string.settingsMenu_enabled) else context.getString(R.string.settingsMenu_disabled)))
        items.add(MenuItem(context.getString(R.string.settingsMenu_clockFormat), if (controller.use24HourClock) context.getString(R.string.settingsMenu_24Hour) else context.getString(R.string.settingsMenu_12Hour)))
        if (BuildConfig.IS_DIRECT_FLAVOR) {
            items.add(MenuItem("Check for Update", ""))
        }
        items.add(MenuItem(context.getString(R.string.settingsMenu_aboutMenu), ""))
        items
    }
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfoForDialog by remember { mutableStateOf<UpdateInfo?>(null) }
    var showToast by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val contextForToast = LocalContext.current
    
    // Back button debouncing
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val backPressDebounceMs = 500L // 500ms debounce

    // Toast effect
    LaunchedEffect(showToast) {
        if (showToast) {
            Toast.makeText(contextForToast, "No update available", Toast.LENGTH_LONG).show()
            delay(5000)
        }
    }

    // Request focus when menu becomes visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(100)
            focusRequester.requestFocus()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 300)
            ),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .zIndex(10f) // Ensure settings menu appears above the top bar
        ) {
            // Handle back press with debouncing - placed inside AnimatedVisibility for proper interception
            BackHandler {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime > backPressDebounceMs) {
                    lastBackPressTime = currentTime
                    if (BuildConfig.DEBUG) {
                        Log.d("SettingsMenu", "Back button pressed - handling with debouncing")
                    }
                    // Use the controller's back handling logic
                    controller.handleBack()
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("SettingsMenu", "Back button debounced - ignoring rapid back press")
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(Color(0xFF1F2937))
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        // Handle Enter/Right key for Check for Update
                        if (BuildConfig.IS_DIRECT_FLAVOR &&
                            controller.selectedIndex == 6 && // Check for Update is at index 6 in direct flavor (was 5, now shifted)
                            (event.key == Key.DirectionRight || KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode)) &&
                            event.type == KeyEventType.KeyDown
                        ) {
                            coroutineScope.launch {
                                val updateInfo = checkForUpdateIfAvailable(context, updateJsonUrl)
                                if (updateInfo == null) {
                                    showToast = true
                                } else {
                                    updateInfoForDialog = updateInfo
                                    showUpdateDialog = true
                                }
                            }
                            true
                        } else {
                            controller.handleKeyEvent(event)
                        }
                    }
            ) {
                when {
                    controller.isSubMenuOpen -> SubMenu(context, controller, viewModel)
                    else -> MainMenu(menuItems, controller)
                }

                // Bottom info
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color(0xFF111827))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.common_versionPrefix) + BuildConfig.VERSION_NAME,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    // Show update dialog only in direct flavor
    if (BuildConfig.IS_DIRECT_FLAVOR && showUpdateDialog && updateInfoForDialog != null) {
        Dialog(onDismissRequest = { }) {
            UpdateAvailableDialog(
                context = context,
                updateInfo = updateInfoForDialog!!,
                updateJsonUrl = updateJsonUrl,
                onClose = { }
            )
        }
    }
}

@Composable
fun MainMenu(menuItems: List<MenuItem>, controller: SettingsMenuController) {
    val listState = rememberLazyListState()

    // Auto-scroll when selected index changes
    LaunchedEffect(controller.selectedIndex) {
        // Map controller index to list index (add 1 for the header)
        val listIndex = controller.selectedIndex + 1
        
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
            val visibleItems = layoutInfo.visibleItemsInfo
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val lastVisibleIndex = visibleItems.last().index
            
            when {
                // If near the top (first 2 menu items), always scroll to the very top to ensure "Settings" title is visible
                listIndex <= 2 -> {
                     listState.animateScrollToItem(0)
                }
                // If selected is 2nd last or last visible, scroll down (shift view down by 1)
                listIndex >= lastVisibleIndex - 1 -> {
                    val targetIndex = (firstVisibleIndex + 1).coerceAtMost(listState.layoutInfo.totalItemsCount - 1)
                    if (targetIndex != firstVisibleIndex) {
                        listState.animateScrollToItem(targetIndex)
                    }
                }
                // If selected is 2nd or first visible (and > 2), scroll up (shift view up by 1)
                listIndex <= firstVisibleIndex + 1 -> {
                    val targetIndex = (firstVisibleIndex - 1).coerceAtLeast(0)
                    if (targetIndex != firstVisibleIndex) {
                        listState.animateScrollToItem(targetIndex)
                    }
                }
                // Fallback: if completely off-screen, snap to it
                listIndex < firstVisibleIndex || listIndex > lastVisibleIndex -> {
                    listState.animateScrollToItem(listIndex)
                }
            }
        } else {
            listState.animateScrollToItem(listIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(Color(0xFF1F2937))
            .padding(bottom = 60.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settingsMenu_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 24.dp)
            )
        }
        itemsIndexed(menuItems) { index, item ->
            MenuItem(item, isSelected = index == controller.selectedIndex)
        }
    }
}

@Composable
fun SubMenu(context: Context, controller: SettingsMenuController, viewModel: ca.devmesh.seerrtv.viewmodel.MediaDiscoveryViewModel? = null) {
    when (controller.currentSubMenu?.title) {
        context.getString(R.string.settingsMenu_configMenu) -> ConfigApiSubMenu(controller)
        context.getString(R.string.settingsMenu_appLanguage) -> AppLanguageSubMenu(controller)
        context.getString(R.string.settingsMenu_discoveryMenu) -> DiscoveryLanguageSubMenu(controller)
        context.getString(R.string.settingsMenu_defaultStreamingRegion) -> DefaultStreamingRegionSubMenu(controller, viewModel)
        context.getString(R.string.settingsMenu_aboutMenu) -> AboutSubMenu()
        else -> {} // Handle other sub-menus if needed
    }
}

@Composable
fun ConfigApiSubMenu(controller: SettingsMenuController) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(Color(0xFF1F2937))
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.settingsMenu_configMenu),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        ConfigInfoItem(
            label = stringResource(R.string.settingsMenu_hostname),
            value = controller.hostname
        )

        Spacer(modifier = Modifier.height(16.dp))

        ConfigInfoItem(
            label = stringResource(R.string.settingsMenu_serverType),
            value = controller.serverType
        )

        Spacer(modifier = Modifier.height(16.dp))

        ConfigInfoItem(
            label = stringResource(R.string.settingsMenu_authType),
            value = when (controller.authType) {
                AuthType.ApiKey.type -> stringResource(R.string.common_apiKey)
                AuthType.LocalUser.type -> stringResource(R.string.configScreen_authTypeLocalUser)
                AuthType.Jellyfin.type -> stringResource(R.string.configScreen_authTypeJellyfin)
                AuthType.Emby.type -> stringResource(R.string.configScreen_authTypeEmby)
                AuthType.Plex.type -> stringResource(R.string.configScreen_authTypePlex)
                else -> controller.authType
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ConfigInfoItem(
            label = stringResource(R.string.settingsMenu_mediaServerType),
            value = when (controller.mediaServerType) {
                "PLEX" -> stringResource(R.string.settingsMenu_mediaServerTypePlex)
                "JELLYFIN" -> stringResource(R.string.settingsMenu_mediaServerTypeJellyfin)
                "EMBY" -> stringResource(R.string.settingsMenu_mediaServerTypeEmby)
                else -> stringResource(R.string.settingsMenu_mediaServerTypeNotConfigured)
            }
        )

        if ((controller.authType == AuthType.LocalUser.type || 
            controller.authType == AuthType.Jellyfin.type || 
            controller.authType == AuthType.Emby.type || 
            controller.authType == AuthType.Plex.type) && 
            controller.displayName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            ConfigInfoItem(
                label = stringResource(R.string.settingsMenu_username),
                value = controller.displayName
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(if (controller.subMenuSelectedIndex == 0) Color(0xFF374151) else Color.Transparent)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settingsMenu_edit),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}

@Composable
fun ConfigInfoItem(label: String, value: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = Color(0xFFBB86FC)
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun AboutSubMenu() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1F2937))
            .padding(24.dp)
    ) {
        AppLogo(
            imageHeight = 150,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.settingsMenu_thankYou),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Original Author:",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFBB86FC),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.devmesh_logo),
                contentDescription = stringResource(R.string.common_devmeshLogo),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "devmesh.ca",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Seerr Community:",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFBB86FC),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "https://seerr.dev/",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF60A5FA)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "This project is now community-driven.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun MenuItem(item: MenuItem, isSelected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (isSelected) Color(0xFF374151) else Color.Transparent)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
        Spacer(modifier = Modifier.weight(1f))
        if (item.value.isNotEmpty()) {
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.title == stringResource(R.string.settingsMenu_debugMode)) {
                    if (item.value == stringResource(R.string.settingsMenu_enabled)) Color(0xFF4CAF50) else Color.LightGray
                } else {
                    Color.LightGray
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AppLanguageSubMenu(controller: SettingsMenuController) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(Color(0xFF1F2937))
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.settingsMenu_appLanguage),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        // Re-use discovery language options as they are sufficient standard codes
        controller.appLanguageOptions.forEachIndexed { index, (code, res) ->
            val isFocused = index == controller.subMenuSelectedIndex
            val isSelected = controller.appLanguage == code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        when {
                            isFocused -> Color(0xFF374151) // focused
                            isSelected -> Color(0xFF23293A) // selected but not focused
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(
                    selected = isSelected,
                    onClick = {
                        controller.selectAppLanguage(index)
                    },
                    colors = androidx.compose.material3.RadioButtonDefaults.colors(
                        selectedColor = Color(0xFFBB86FC),
                        unselectedColor = Color.White
                    )
                )
                Text(
                    text = stringResource(res),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun DiscoveryLanguageSubMenu(controller: SettingsMenuController) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(Color(0xFF1F2937))
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.settingsMenu_discoveryMenu),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        controller.discoveryLanguageOptions.forEachIndexed { index, (code, res) ->
            val isFocused = index == controller.subMenuSelectedIndex
            val isSelected = controller.discoveryLanguage == code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        when {
                            isFocused -> Color(0xFF374151) // focused
                            isSelected -> Color(0xFF23293A) // selected but not focused
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(
                    selected = isSelected,
                    onClick = {
                        controller.selectDiscoveryLanguage(index)
                    },
                    colors = androidx.compose.material3.RadioButtonDefaults.colors(
                        selectedColor = Color(0xFFBB86FC),
                        unselectedColor = Color.White
                    )
                )
                Text(
                    text = stringResource(res),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun DefaultStreamingRegionSubMenu(
    controller: SettingsMenuController,
    viewModel: ca.devmesh.seerrtv.viewmodel.MediaDiscoveryViewModel? = null
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    
    // Use viewModel if available, otherwise fall back to loading directly
    val regionsFromViewModel by viewModel?.availableRegions?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    var regions by remember { mutableStateOf<List<Region>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isInitialized by remember { mutableStateOf(false) }
    
    // If viewModel is available, use its regions and ensure they're loaded
    LaunchedEffect(viewModel, regionsFromViewModel) {
        if (viewModel != null) {
            if (regionsFromViewModel.isEmpty()) {
                // Load regions if not already loaded
                viewModel.loadFilterOptions()
            } else {
                regions = regionsFromViewModel
                isLoading = false
                // Initialize selected index to current region
                if (!isInitialized) {
                    val currentIndex = regions.indexOfFirst { it.iso_3166_1 == controller.defaultStreamingRegion }
                    if (currentIndex >= 0) {
                        controller.subMenuSelectedIndex = currentIndex
                        // Scroll to show the selected item
                        listState.animateScrollToItem(currentIndex)
                    }
                    isInitialized = true
                }
            }
        } else {
            // Fallback: load regions directly if no viewModel
            isLoading = true
            val config = SharedPreferencesUtil.getConfig(context)
            if (config != null) {
                val apiService = SeerrApiService(config, context)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    when (val result = apiService.getRegions()) {
                        is ApiResult.Success -> {
                            regions = result.data
                            isLoading = false
                            // Initialize selected index to current region
                            if (!isInitialized) {
                                val currentIndex = regions.indexOfFirst { it.iso_3166_1 == controller.defaultStreamingRegion }
                                if (currentIndex >= 0) {
                                    controller.subMenuSelectedIndex = currentIndex
                                    // Scroll to show the selected item
                                    listState.animateScrollToItem(currentIndex)
                                }
                                isInitialized = true
                            }
                        }
                        is ApiResult.Error -> {
                            Log.e("DefaultStreamingRegionSubMenu", "Failed to load regions", result.exception)
                            isLoading = false
                        }
                        else -> {
                            isLoading = false
                        }
                    }
                }
            } else {
                isLoading = false
            }
        }
    }
    
    // Update regions when viewModel regions change
    LaunchedEffect(regionsFromViewModel) {
        if (viewModel != null && regionsFromViewModel.isNotEmpty()) {
            regions = regionsFromViewModel
            isLoading = false
            // Initialize selected index to current region
            if (!isInitialized) {
                val currentIndex = regions.indexOfFirst { it.iso_3166_1 == controller.defaultStreamingRegion }
                if (currentIndex >= 0) {
                    controller.subMenuSelectedIndex = currentIndex
                    // Scroll to show the selected item
                    listState.animateScrollToItem(currentIndex)
                }
                isInitialized = true
            }
        }
    }
    
    // Auto-scroll when selected index changes - only if item is not visible
    LaunchedEffect(controller.subMenuSelectedIndex) {
        if (controller.subMenuSelectedIndex >= 0 && controller.subMenuSelectedIndex < regions.size && !isLoading) {
            // Small delay to ensure UI has updated
            delay(50)
            
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val firstVisible = listState.firstVisibleItemIndex
                val lastVisible = firstVisible + layoutInfo.visibleItemsInfo.size - 1
                
                // Only scroll if the selected item is outside the visible area
                when {
                    controller.subMenuSelectedIndex < firstVisible -> {
                        listState.animateScrollToItem(controller.subMenuSelectedIndex)
                    }
                    controller.subMenuSelectedIndex > lastVisible -> {
                        listState.animateScrollToItem(controller.subMenuSelectedIndex)
                    }
                    // Item is already visible, no need to scroll
                }
            } else {
                // Fallback: scroll if layout info not available
                listState.animateScrollToItem(controller.subMenuSelectedIndex)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(Color(0xFF1F2937))
            .padding(24.dp)
            .onPreviewKeyEvent { keyEvent ->
                when {
                    KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyDown -> {
                        if (controller.subMenuSelectedIndex >= 0 && controller.subMenuSelectedIndex < regions.size) {
                            val selectedRegion = regions[controller.subMenuSelectedIndex]
                            controller.selectDefaultStreamingRegion(selectedRegion.iso_3166_1)
                        }
                        true
                    }
                    keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                        if (controller.subMenuSelectedIndex > 0) {
                            controller.subMenuSelectedIndex--
                        }
                        true
                    }
                    keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                        if (controller.subMenuSelectedIndex < regions.size - 1) {
                            controller.subMenuSelectedIndex++
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Text(
            text = stringResource(R.string.settingsMenu_defaultStreamingRegion),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (isLoading) {
            Text(
                text = "Loading regions...",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(regions) { index, region ->
                    val isFocused = index == controller.subMenuSelectedIndex
                    val isSelected = controller.defaultStreamingRegion == region.iso_3166_1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(
                                when {
                                    isFocused -> Color(0xFF374151) // focused
                                    isSelected -> Color(0xFF23293A) // selected but not focused
                                    else -> Color.Transparent
                                }
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = isSelected,
                            onClick = {
                                controller.selectDefaultStreamingRegion(region.iso_3166_1)
                            },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFBB86FC),
                                unselectedColor = Color.White
                            )
                        )
                        Text(
                            text = region.english_name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

class SettingsMenuController(
    private val onDismiss: () -> Unit,
    private val onOpenConfigScreen: () -> Unit,
    val hostname: String,
    private val context: Context
) {
    var selectedIndex by mutableIntStateOf(0)
    var isSubMenuOpen by mutableStateOf(false)
    var subMenuSelectedIndex by mutableIntStateOf(0)
    var currentSubMenu: SubMenu? by mutableStateOf(null)
    var folderSelectionEnabled by mutableStateOf(SharedPreferencesUtil.isFolderSelectionEnabled(context))
    var use24HourClock by mutableStateOf(SharedPreferencesUtil.use24HourClock(context))
    val authType = SharedPreferencesUtil.getConfig(context)?.authType ?: "unknown"
    val displayName = SharedPreferencesUtil.getUserDisplayName(context) ?: ""
    val mediaServerType = SharedPreferencesUtil.getMediaServerType(context).name
    
    // Format the server type for display
    val serverType = formatServerType(SharedPreferencesUtil.getServerType(context))

    // App Language state
    var appLanguage by mutableStateOf(SharedPreferencesUtil.getAppLanguage(context) ?: "en")

    // Discovery Language state
    var discoveryLanguage by mutableStateOf(SharedPreferencesUtil.getDiscoveryLanguage(context))
    
    // Default Streaming Region state
    var defaultStreamingRegion by mutableStateOf(SharedPreferencesUtil.getDefaultStreamingRegion(context))

    private fun formatServerType(serverType: String): String {
        return when (serverType.uppercase()) {
            "OVERSEERR" -> "Overseerr"
            "JELLYSEERR" -> "Jellyseerr"
            "SEERR" -> "Seerr"
            "UNKNOWN" -> "Unknown"
            else -> serverType // Fallback to the original value
        }
    }

    // Language options and their string resource keys
    val discoveryLanguageOptions = listOf(
        "en" to R.string.settingsMenu_discoverLanguageEN,
        "de" to R.string.settingsMenu_discoverLanguageDE,
        "es" to R.string.settingsMenu_discoverLanguageES,
        "fr" to R.string.settingsMenu_discoverLanguageFR,
        "ja" to R.string.settingsMenu_discoverLanguageJA,
        "nl" to R.string.settingsMenu_discoverLanguageNL,
        "pt" to R.string.settingsMenu_discoverLanguagePT,
        "zh" to R.string.settingsMenu_discoverLanguageZH,
    )

    // App Language options (same as discovery for now)
    val appLanguageOptions = discoveryLanguageOptions

    fun getAppLanguageLabelRes(code: String): Int {
        return appLanguageOptions.find { it.first == code }?.second ?: R.string.settingsMenu_discoverLanguageEN
    }

    fun getDiscoveryLanguageLabelRes(code: String): Int {
        return discoveryLanguageOptions.find { it.first == code }?.second ?: R.string.settingsMenu_discoverLanguageEN
    }

    fun handleBack() {
        if (BuildConfig.DEBUG) {
            Log.d("SettingsMenuController", "handleBack called - isSubMenuOpen: $isSubMenuOpen")
        }
        if (isSubMenuOpen) {
            isSubMenuOpen = false
            currentSubMenu = null
        } else {
            onDismiss()
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        return when {
            event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                if (isSubMenuOpen) {
                    subMenuSelectedIndex = (subMenuSelectedIndex - 1).coerceAtLeast(0)
                } else {
                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                }
                true
            }

            event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                if (isSubMenuOpen) {
                    subMenuSelectedIndex = (subMenuSelectedIndex + 1).coerceAtMost(
                        (currentSubMenu?.items?.size ?: 1) - 1
                    )
                } else {
                    // menuItems is not available in the controller, so use a constant for max index
                    // When IS_DIRECT_FLAVOR is true, we have: Config(0), AppLanguage(1), Folder(2), Discovery(3), Streaming Region(4), Clock(5), Check for Update(6), About(7)
                    // When IS_DIRECT_FLAVOR is false, we have: Config(0), AppLanguage(1), Folder(2), Discovery(3), Streaming Region(4), Clock(5), About(6)
                    val maxIndex = if (BuildConfig.IS_DIRECT_FLAVOR) 7 else 6
                    selectedIndex = (selectedIndex + 1).coerceAtMost(maxIndex)
                }
                true
            }

            event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown -> {
                handleRightOrEnterKey()
            }

            KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) && event.type == KeyEventType.KeyDown -> {
                when {
                    isSubMenuOpen && currentSubMenu?.title == context.getString(R.string.settingsMenu_configMenu) && subMenuSelectedIndex == 0 -> {
                        // First close the submenu and settings menu
                        isSubMenuOpen = false
                        currentSubMenu = null
                        onDismiss()
                        // Navigate immediately - ConfigScreen will handle its own focus management
                        onOpenConfigScreen()
                        true
                    }
                    isSubMenuOpen && currentSubMenu?.title == context.getString(R.string.settingsMenu_appLanguage) -> {
                        // Update app language
                        val selected = appLanguageOptions[subMenuSelectedIndex].first
                        updateAppLanguage(selected)
                        isSubMenuOpen = false
                        currentSubMenu = null
                        true
                    }
                    isSubMenuOpen && currentSubMenu?.title == context.getString(R.string.settingsMenu_discoveryMenu) -> {
                        // Update discovery language
                        val selected = discoveryLanguageOptions[subMenuSelectedIndex].first
                        updateDiscoveryLanguage(selected)
                        isSubMenuOpen = false
                        currentSubMenu = null
                        true
                    }
                    !isSubMenuOpen && selectedIndex == 4 -> { // Folder Selection toggle
                        toggleFolderSelection()
                        true
                    }
                    !isSubMenuOpen && selectedIndex == 5 -> { // Clock Format toggle
                        toggleClockFormat()
                        true
                    }
                    else -> handleRightOrEnterKey()
                }
            }

            event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown -> {
                handleBack()
                true
            }

            event.key == Key.Back && event.type == KeyEventType.KeyDown -> {
                handleBack()
                true
            }

            else -> false
        }
    }

    private fun handleRightOrEnterKey(): Boolean {
        if (!isSubMenuOpen) {
            isSubMenuOpen = true
            currentSubMenu = when (selectedIndex) {
                0 -> SubMenu(context.getString(R.string.settingsMenu_configMenu), listOf(MenuItem(context.getString(R.string.settingsMenu_edit), "")))
                1 -> SubMenu(
                    context.getString(R.string.settingsMenu_appLanguage),
                    appLanguageOptions.map { (code, res) -> MenuItem(context.getString(res), code) }
                )
                2 -> SubMenu(
                    context.getString(R.string.settingsMenu_discoveryMenu),
                    discoveryLanguageOptions.map { (code, res) -> MenuItem(context.getString(res), code) }
                )
                3 -> SubMenu(context.getString(R.string.settingsMenu_defaultStreamingRegion), emptyList())
                // About menu index depends on whether Check for Update is present
                // Update Check (6) -> No sub menu
                if (BuildConfig.IS_DIRECT_FLAVOR) 7 else 6 -> SubMenu(context.getString(R.string.settingsMenu_aboutMenu), emptyList())
                // Don't handle index corresponding to "Check for Update" in direct flavor
                else -> null
            }
            subMenuSelectedIndex = when (selectedIndex) {
                1 -> appLanguageOptions.indexOfFirst { it.first == appLanguage }.coerceAtLeast(0)
                2 -> discoveryLanguageOptions.indexOfFirst { it.first == discoveryLanguage }.coerceAtLeast(0)
                else -> 0
            }
            // If currentSubMenu is null (e.g. for toggle items or check update), don't open sub menu state
            if (currentSubMenu == null) {
                isSubMenuOpen = false
                return false
            }
            return true // Consume the event
        }
        return false // Don't consume the event if we didn't handle it
    }

    fun toggleFolderSelection() {
        folderSelectionEnabled = !folderSelectionEnabled
        SharedPreferencesUtil.setFolderSelectionEnabled(context, folderSelectionEnabled)
    }

    fun toggleClockFormat() {
        use24HourClock = !use24HourClock
        SharedPreferencesUtil.setUse24HourClock(context, use24HourClock)
    }

    private fun updateDiscoveryLanguage(newValue: String) {
        discoveryLanguage = newValue
        SharedPreferencesUtil.setDiscoveryLanguage(context, newValue)
    }


    fun selectDiscoveryLanguage(index: Int) {
        val selected = discoveryLanguageOptions[index].first
        discoveryLanguage = selected
        SharedPreferencesUtil.setDiscoveryLanguage(context, selected)
        isSubMenuOpen = false
        currentSubMenu = null
    }
    
    fun getDefaultStreamingRegionDisplayName(): String {
        // For now, return the region code. In a real implementation, you might want to look up the display name
        // from a list of regions, but since we don't have that cached, we'll just return the code
        return defaultStreamingRegion
    }
    
    fun selectDefaultStreamingRegion(regionCode: String) {
        defaultStreamingRegion = regionCode
        SharedPreferencesUtil.setDefaultStreamingRegion(context, regionCode)
        isSubMenuOpen = false
        currentSubMenu = null
    }

    private fun updateAppLanguage(newValue: String) {
        appLanguage = newValue
        SharedPreferencesUtil.setAppLanguage(context, newValue)
        // Restart the activity to apply the language change
        (context as? android.app.Activity)?.recreate()
    }

    fun selectAppLanguage(index: Int) {
        val selected = appLanguageOptions[index].first
        updateAppLanguage(selected)
        isSubMenuOpen = false
        currentSubMenu = null
    }
}

data class MenuItem(val title: String, val value: String)
data class SubMenu(val title: String, val items: List<MenuItem>)