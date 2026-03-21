package ca.devmesh.seerrtv.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import ca.devmesh.seerrtv.model.AvatarColor
import ca.devmesh.seerrtv.model.UserProfile
import ca.devmesh.seerrtv.ui.focus.AppFocusManager
import ca.devmesh.seerrtv.ui.focus.AppFocusState
import ca.devmesh.seerrtv.ui.focus.DpadController
import ca.devmesh.seerrtv.ui.focus.DpadSection
import ca.devmesh.seerrtv.ui.focus.DpadTransitions
import ca.devmesh.seerrtv.ui.focus.ScreenDpadConfig
import ca.devmesh.seerrtv.util.PinUtils
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.delay
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.model.User
import androidx.compose.ui.res.stringResource
import ca.devmesh.seerrtv.R

enum class ManagementMode {
    LIST,
    ACTIONS,
    SET_PIN,
    DELETE_CONFIRM
}

private enum class ProfileActionType {
    CONFIGURE_API,
    AVATAR_COLOR,
    SET_PIN,
    CLEAR_PIN,
    DELETE_PROFILE
}

private data class ProfileAction(
    val type: ProfileActionType,
    val label: String
)

@Composable
fun UserProfilesManagementScreen(
    navController: NavController,
    context: Context,
    apiService: SeerrApiService,
    appFocusManager: AppFocusManager,
    dpadController: DpadController,
    route: String = "user_profiles"
) {
    val actionConfigureApi = stringResource(R.string.userProfiles_actionConfigureApi)
    val actionAvatarColor = stringResource(R.string.userProfiles_actionAvatarColor)
    val actionClearPin = stringResource(R.string.userProfiles_actionClearPin)
    val actionSetPin = stringResource(R.string.userProfiles_actionSetPin)
    val actionDeleteProfile = stringResource(R.string.userProfiles_actionDeleteProfile)

    var profiles by remember { mutableStateOf(SharedPreferencesUtil.getProfiles(context)) }
    val activeProfileId = SharedPreferencesUtil.getActiveProfileId(context)
    var selectedListIndex by remember {
        mutableStateOf(
            profiles.indexOfFirst { it.id == activeProfileId }.takeIf { it >= 0 } ?: 0
        )
    }

    var mode by remember { mutableStateOf(ManagementMode.LIST) }
    var selectedActionIndex by remember { mutableStateOf(0) }

    var pinBuffer by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var confirmDeleteIndex by remember { mutableStateOf(0) } // 0=Cancel, 1=Delete

    var pinKeypadIndex by remember { mutableStateOf(0) }

    val activeProfile = profiles.firstOrNull { it.id == activeProfileId }

    var authUserResult by remember { mutableStateOf<ApiResult<User>?>(null) }
    var isLoadingAuthUser by remember { mutableStateOf(false) }

    LaunchedEffect(mode, activeProfileId) {
        if (mode != ManagementMode.ACTIONS) return@LaunchedEffect
        val active = activeProfile
        if (active == null) return@LaunchedEffect

        isLoadingAuthUser = true
        authUserResult = null
        try {
            authUserResult = apiService.getAuthenticatedUser()
        } catch (_: Exception) {
            // We'll just fall back to unknown/unlimited defaults.
            authUserResult = null
        } finally {
            isLoadingAuthUser = false
        }
    }

    val actions = run {
        val hasPin = activeProfile?.pinHash?.isNotBlank() == true
        listOf(
            ProfileAction(ProfileActionType.CONFIGURE_API, actionConfigureApi),
            ProfileAction(ProfileActionType.AVATAR_COLOR, actionAvatarColor),
            if (hasPin) {
                ProfileAction(ProfileActionType.CLEAR_PIN, actionClearPin)
            } else {
                ProfileAction(ProfileActionType.SET_PIN, actionSetPin)
            },
            ProfileAction(ProfileActionType.DELETE_PROFILE, actionDeleteProfile)
        )
    }

    // LIST mode pseudo-row at the end that creates a new profile.
    val addProfileRowIndex = profiles.size

    fun refreshProfilesSnapshot() {
        profiles = SharedPreferencesUtil.getProfiles(context)
    }

    fun cycleAvatarColor(active: UserProfile?) {
        if (active == null) return
        val current = AvatarColor.fromKey(active.avatarColor)
        val idx = AvatarColor.entries.indexOf(current)
        val next = AvatarColor.entries[(idx + 1) % AvatarColor.entries.size]
        SharedPreferencesUtil.updateActiveProfileAvatarColor(context, next.key)
        refreshProfilesSnapshot()
    }

    fun activateNewProfileAndConfigure() {
        // Domain rule: new profiles start unprotected unless user configures PIN later.
        val existingInitials = profiles.map { it.avatarInitials }.toSet()
        val seed = "Profile${profiles.size + 1}"
        val initials = ca.devmesh.seerrtv.util.AvatarUtils.resolveUniqueInitials(
            desiredInitials = seed.take(2).uppercase().padEnd(2, 'Z'),
            existingInitials = existingInitials,
            seed = seed
        )
        val newProfile = UserProfile(
            name = seed,
            email = null,
            avatarInitials = initials,
            avatarColor = AvatarColor.PURPLE.key,
            pinHash = "",
            config = SharedPreferencesUtil.getConfig(context)
                ?: ca.devmesh.seerrtv.data.SeerrApiService.SeerrConfig(
                    protocol = "",
                    hostname = "",
                    authType = ca.devmesh.seerrtv.model.AuthType.ApiKey.type,
                    apiKey = "",
                    username = "",
                    password = "",
                    cloudflareEnabled = false,
                    cfClientId = "",
                    cfClientSecret = "",
                    isSubmitted = false,
                    createdAt = System.currentTimeMillis().toString()
                )
        )

        val all = SharedPreferencesUtil.getProfiles(context) + newProfile
        SharedPreferencesUtil.saveProfiles(context, all)
        SharedPreferencesUtil.setActiveProfileId(context, newProfile.id)
        SharedPreferencesUtil.setSkipProfileSelectionOnce(context, true)

        navController.navigate("config") {
            popUpTo(route) { inclusive = true }
        }
    }

    fun onUp() {
        when (mode) {
            ManagementMode.LIST -> selectedListIndex = (selectedListIndex - 1).coerceAtLeast(0)
            ManagementMode.ACTIONS -> selectedActionIndex = (selectedActionIndex - 1).coerceAtLeast(0)
            ManagementMode.SET_PIN -> {
                val row = pinKeypadIndex / 3
                if (row > 0) pinKeypadIndex = pinKeypadIndex - 3
            }
            ManagementMode.DELETE_CONFIRM -> confirmDeleteIndex = (confirmDeleteIndex - 1).coerceAtLeast(0)
        }
    }

    fun onDown() {
        when (mode) {
            ManagementMode.LIST -> selectedListIndex =
                (selectedListIndex + 1).coerceAtMost(addProfileRowIndex.coerceAtLeast(0))
            ManagementMode.ACTIONS -> selectedActionIndex = (selectedActionIndex + 1).coerceAtMost(actions.size - 1)
            ManagementMode.SET_PIN -> {
                val row = pinKeypadIndex / 3
                if (row < 3) pinKeypadIndex = pinKeypadIndex + 3
            }
            ManagementMode.DELETE_CONFIRM -> confirmDeleteIndex = (confirmDeleteIndex + 1).coerceAtMost(1)
        }
    }

    fun onLeft() {
        when (mode) {
            ManagementMode.LIST -> Unit
            ManagementMode.ACTIONS -> Unit
            ManagementMode.SET_PIN -> {
                val col = pinKeypadIndex % 3
                if (col > 0) pinKeypadIndex -= 1
            }
            ManagementMode.DELETE_CONFIRM -> Unit
        }
    }

    fun onRight() {
        when (mode) {
            ManagementMode.LIST -> Unit
            ManagementMode.ACTIONS -> Unit
            ManagementMode.SET_PIN -> {
                val col = pinKeypadIndex % 3
                if (col < 2) pinKeypadIndex += 1
            }
            ManagementMode.DELETE_CONFIRM -> Unit
        }
    }

    fun onEnter() {
        when (mode) {
            ManagementMode.LIST -> {
                val isAddRow = selectedListIndex == addProfileRowIndex
                if (isAddRow) {
                    activateNewProfileAndConfigure()
                    return
                }

                if (profiles.isEmpty()) return
                if (selectedListIndex !in 0 until profiles.size) return

                val selected = profiles[selectedListIndex]
                val isActive = selected.id == activeProfileId
                if (isActive) {
                    mode = ManagementMode.ACTIONS
                    selectedActionIndex = 0
                } else {
                    // Switch to this profile first (PIN gate happens there).
                    SharedPreferencesUtil.setProfileSelectionTargetProfileId(context, selected.id)
                    navController.navigate("profile_select") {
                        popUpTo(route) { inclusive = false }
                    }
                }
            }
            ManagementMode.ACTIONS -> {
                val action = actions[selectedActionIndex]
                when (action.type) {
                    ProfileActionType.CONFIGURE_API -> {
                        SharedPreferencesUtil.setSkipProfileSelectionOnce(context, true)
                        navController.navigate("config") {
                            popUpTo(route) { inclusive = false }
                        }
                    }
                    ProfileActionType.AVATAR_COLOR -> {
                        val latestActive = SharedPreferencesUtil.getActiveProfile(context)
                        cycleAvatarColor(latestActive)
                    }
                    ProfileActionType.SET_PIN -> {
                        mode = ManagementMode.SET_PIN
                        pinBuffer = ""
                        pinError = null
                        pinKeypadIndex = 0
                    }
                    ProfileActionType.CLEAR_PIN -> {
                        SharedPreferencesUtil.setActiveProfilePinHash(context, "")
                        refreshProfilesSnapshot()
                    }
                    ProfileActionType.DELETE_PROFILE -> {
                        mode = ManagementMode.DELETE_CONFIRM
                        confirmDeleteIndex = 0
                    }
                }
            }
            ManagementMode.SET_PIN -> {
                val key = when (pinKeypadIndex) {
                    0, 1, 2 -> (pinKeypadIndex + 1).toString()
                    3, 4, 5 -> (pinKeypadIndex + 1).toString()
                    6, 7, 8 -> (pinKeypadIndex + 1).toString()
                    9 -> "0"
                    10 -> ""
                    11 -> ""
                    else -> ""
                }
                when (pinKeypadIndex) {
                    10 -> pinBuffer = pinBuffer.dropLast(1)
                    11 -> {
                        if (pinBuffer.length < 4) {
                            pinError = context.getString(R.string.userProfiles_pinMinimumLengthError)
                        } else {
                            val hash = PinUtils.hashPin(pinBuffer)
                            SharedPreferencesUtil.setActiveProfilePinHash(context, hash)
                            refreshProfilesSnapshot()
                            mode = ManagementMode.ACTIONS
                            pinBuffer = ""
                            pinError = null
                            pinKeypadIndex = 0
                        }
                    }
                    else -> {
                        if (pinBuffer.length < 6 && key.all { it.isDigit() }) {
                            pinBuffer += key
                        }
                    }
                }
            }
            ManagementMode.DELETE_CONFIRM -> {
                if (confirmDeleteIndex == 1) {
                    val deleted = SharedPreferencesUtil.deleteActiveProfile(context)
                    if (deleted) {
                        // If we deleted the last profile, app will go to config on next splash.
                        SharedPreferencesUtil.setSkipProfileSelectionOnce(context, false)
                        navController.navigate("splash") {
                            popUpTo(route) { inclusive = true }
                        }
                    }
                } else {
                    mode = ManagementMode.ACTIONS
                }
            }
        }
    }

    fun onBack() {
        when (mode) {
            ManagementMode.LIST -> navController.popBackStack()
            ManagementMode.ACTIONS -> mode = ManagementMode.LIST
            ManagementMode.SET_PIN -> mode = ManagementMode.ACTIONS
            ManagementMode.DELETE_CONFIRM -> mode = ManagementMode.ACTIONS
        }
    }

    val dpadConfig = remember(dpadController, appFocusManager) {
        ScreenDpadConfig(
            route = route,
            focusManager = appFocusManager,
            sections = listOf(DpadSection.List),
            transitions = DpadTransitions(
                upTransitions = mapOf(DpadSection.List to DpadSection.List),
                downTransitions = mapOf(DpadSection.List to DpadSection.List),
                leftTransitions = mapOf(DpadSection.List to DpadSection.List),
                rightTransitions = mapOf(DpadSection.List to DpadSection.List),
            ),
            onUp = { onUp() },
            onDown = { onDown() },
            onLeft = { onLeft() },
            onRight = { onRight() },
            onEnter = { onEnter() },
            onBack = { onBack() },
        )
    }

    LaunchedEffect(Unit) {
        appFocusManager.setFocus(AppFocusState.SettingsScreen)
        dpadController.registerScreen(dpadConfig)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1F2937))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = when (mode) {
                    ManagementMode.LIST -> stringResource(R.string.userProfiles_titleList)
                    ManagementMode.ACTIONS -> stringResource(R.string.userProfiles_titleManageProfile)
                    ManagementMode.SET_PIN -> stringResource(R.string.userProfiles_titleSetProfilePin)
                    ManagementMode.DELETE_CONFIRM -> stringResource(R.string.userProfiles_titleDeleteProfile)
                },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            when (mode) {
                ManagementMode.LIST -> {
                    if (profiles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.userProfiles_noProfilesYet),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(profiles) { index, profile ->
                                val selected = index == selectedListIndex
                                val isActive = profile.id == activeProfileId
                                ProfileMgmtRow(profile = profile, isActive = isActive, isSelected = selected)
                            }
                            item {
                                AddProfileRow(
                                    selected = selectedListIndex == addProfileRowIndex,
                                    onAdd = { activateNewProfileAndConfigure() }
                                )
                            }
                        }
                    }
                }
                ManagementMode.ACTIONS -> {
                    if (activeProfile == null) {
                        Text(stringResource(R.string.userProfiles_noActiveProfileFound), color = Color.White)
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            val result = authUserResult
                            val authUser = (result as? ApiResult.Success)?.data
                            ProfileMgmtActiveCard(
                                activeProfile = activeProfile,
                                authUser = authUser,
                                isLoadingAuthUser = isLoadingAuthUser
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(actions) { index, label ->
                                    val isSelected = index == selectedActionIndex
                                    ActionRow(label = label.label, isSelected = isSelected)
                                }
                            }
                        }
                    }
                }
                ManagementMode.SET_PIN -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val maxLen = 6
                            repeat(maxLen) { i ->
                                val filled = i < pinBuffer.length
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(if (filled) Color(0xFFBB86FC) else Color.White.copy(alpha = 0.25f))
                                )
                                if (i < maxLen - 1) Spacer(modifier = Modifier.width(10.dp))
                            }
                        }
                        pinError?.let { err ->
                            Text(
                                text = err,
                                color = Color(0xFFFF6B6B),
                                modifier = Modifier.padding(top = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        PinEditorKeypad(
                            keypadSelectedIndex = pinKeypadIndex
                        )
                    }
                }
                ManagementMode.DELETE_CONFIRM -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.userProfiles_deleteConfirmMessage),
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        ConfirmRow(
                            index = 0,
                            label = stringResource(R.string.common_cancel),
                            selectedIndex = confirmDeleteIndex
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ConfirmRow(
                            index = 1,
                            label = stringResource(R.string.requestAction_delete),
                            selectedIndex = confirmDeleteIndex,
                            danger = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMgmtRow(
    profile: UserProfile,
    isActive: Boolean,
    isSelected: Boolean
) {
    val bg = when {
        isSelected -> Color(0xFF374151)
        isActive -> Color(0xFF23293A)
        else -> Color.Transparent
    }

    val avatarColor = AvatarColor.fromKey(profile.avatarColor).toColor()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(bg)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(avatarColor)
                .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = profile.avatarInitials.take(2), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (profile.pinHash.isNotBlank()) {
                Text(
                    text = stringResource(R.string.userProfiles_pinLabel),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (isActive) {
            Text(
                text = stringResource(R.string.userProfiles_active),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AddProfileRow(
    selected: Boolean,
    onAdd: () -> Unit
) {
    // This is a selectable row; Enter handling is done in the screen's DPAD logic.
    // We still render the action affordance to make it clear what will happen.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(if (selected) Color(0xFF374151) else Color.Transparent)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = stringResource(R.string.userProfiles_addProfile),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProfileMgmtActiveCard(
    activeProfile: UserProfile,
    authUser: User?,
    isLoadingAuthUser: Boolean
) {
    val avatarColor = AvatarColor.fromKey(activeProfile.avatarColor).toColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111827), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(avatarColor)
                .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = activeProfile.avatarInitials.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = activeProfile.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (activeProfile.pinHash.isNotBlank()) {
                Text(
                    text = stringResource(R.string.userProfiles_pinProtected),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoadingAuthUser) {
                Text(
                    text = stringResource(R.string.userProfiles_loadingProfileInfo),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            } else {
                val requestCount = authUser?.requestCount ?: 0
                Text(
                    text = stringResource(R.string.userProfiles_totalRequests, requestCount),
                    color = Color.White,
                    fontSize = 13.sp
                )

                Text(
                    text = stringResource(
                        R.string.userProfiles_movieRequests,
                        formatQuota(limit = authUser?.movieQuotaLimit, days = authUser?.movieQuotaDays)
                    ),
                    color = Color.White,
                    fontSize = 13.sp
                )

                Text(
                    text = stringResource(
                        R.string.userProfiles_seriesRequests,
                        formatQuota(limit = authUser?.tvQuotaLimit, days = authUser?.tvQuotaDays)
                    ),
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun formatQuota(limit: Int?, days: Int?): String {
    val l = limit ?: return stringResource(R.string.userProfiles_quotaUnlimited)
    val d = days ?: return stringResource(R.string.userProfiles_quotaUnlimited)
    if (l <= 0 || d <= 0) return stringResource(R.string.userProfiles_quotaUnlimited)
    return stringResource(R.string.userProfiles_quotaPerDays, l, d)
}

@Composable
private fun ActionRow(label: String, isSelected: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (isSelected) Color(0xFF374151) else Color.Transparent)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text = label, color = Color.White, fontSize = 18.sp)
    }
}

@Composable
private fun ConfirmRow(
    index: Int,
    label: String,
    selectedIndex: Int,
    danger: Boolean = false
) {
    val selected = index == selectedIndex
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (selected) Color(0xFF374151) else Color.Transparent)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            color = if (danger) (if (selected) Color(0xFFFF6B6B) else Color(0xFFCC4444)) else Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PinEditorKeypad(keypadSelectedIndex: Int) {
    val labels = listOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        stringResource(R.string.userProfiles_keyDelete),
        stringResource(R.string.userProfiles_keyOk)
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        for (row in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (col in 0 until 3) {
                    val idx = row * 3 + col
                    val label = labels[idx]
                    val selected = idx == keypadSelectedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color(0xFFBB86FC) else Color(0xFF111827))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

