package ca.devmesh.seerrtv.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.devmesh.seerrtv.model.AvatarColor
import ca.devmesh.seerrtv.model.UserProfile
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.ui.focus.AppFocusManager
import ca.devmesh.seerrtv.ui.focus.DpadController
import ca.devmesh.seerrtv.ui.focus.DpadSection
import ca.devmesh.seerrtv.ui.focus.DpadTransitions
import ca.devmesh.seerrtv.ui.focus.ScreenDpadConfig
import ca.devmesh.seerrtv.ui.focus.AppFocusState
import ca.devmesh.seerrtv.ui.components.AppLogo
import ca.devmesh.seerrtv.ui.components.VersionNumber
import ca.devmesh.seerrtv.util.PinUtils
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import ca.devmesh.seerrtv.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ProfileGateMode {
    SELECT_LIST,
    PIN_ENTRY
}

@Composable
fun UserProfileSelectionScreen(
    navController: NavController,
    context: Context,
    apiService: SeerrApiService,
    appFocusManager: AppFocusManager,
    dpadController: DpadController,
    route: String = "profile_select"
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(SharedPreferencesUtil.getProfiles(context)) }
    val activeProfileId = SharedPreferencesUtil.getActiveProfileId(context)
    val targetProfileId = remember { SharedPreferencesUtil.consumeProfileSelectionTargetProfileId(context) }
    val initialSelectionId = targetProfileId ?: activeProfileId

    // Controller state (kept inside composable so it can re-render reliably).
    var mode by remember { mutableStateOf(ProfileGateMode.SELECT_LIST) }

    var selectedProfileIndex by remember {
        mutableStateOf(
            profiles.indexOfFirst { it.id == initialSelectionId }.let { idx ->
                if (idx >= 0) idx else 0
            }
        )
    }

    var pinBuffer by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var keypadSelectedIndex by remember { mutableStateOf(0) }
    // Some remotes dispatch Back twice (down/up); swallow one after closing PIN modal.
    var consumeNextBackOnSelectList by remember { mutableStateOf(false) }

    // Ensure d-pad works with a registered focus state.
    LaunchedEffect(Unit) {
        if (profiles.isNotEmpty()) {
            selectedProfileIndex = profiles.indexOfFirst { it.id == initialSelectionId }.let { idx ->
                if (idx >= 0) idx else 0
            }
        }
        // Re-use SettingsScreen as a generic "non-topbar list" focus state.
        appFocusManager.setFocus(AppFocusState.SettingsScreen)
    }

    fun activateProfile(profile: UserProfile) {
        SharedPreferencesUtil.setActiveProfileId(context, profile.id)
        // SeerrApiService is a singleton, so switching profiles must update its in-memory config.
        // We navigate directly to `main` to avoid relying on the splash startup state machine.
        apiService.updateConfig(profile.config)

        val postActivationRoute =
            SharedPreferencesUtil.consumeProfileSelectionTargetPostActivationRoute(context)
                ?: "main"

        // A successful profile switch should not bounce back to selector during startup validation.
        SharedPreferencesUtil.setSkipProfileSelectionOnce(context, true)

        // We may be navigating to `splash` only to allow `MainActivity` to run its startup validation,
        // but we already handled profile PIN entry here. Prevent `MainActivity` from pushing us
        // back to `profile_select` after auth succeeds.
        if (postActivationRoute == "splash") {
            // Ensure MainActivity re-runs splash validation with the newly selected profile.
            SharedPreferencesUtil.setForceSplashResetOnNext(context, true)
        }
        // Gate splash authentication/validation until the user has chosen/unlocked a profile.
        SharedPreferencesUtil.setProfileSelectionCompleted(context, completed = true)

        navController.navigate(postActivationRoute) {
            popUpTo(route) { inclusive = true }
        }

        // Recreate activity after profile switch so all per-profile settings
        // (app language, time format, etc.) are immediately reloaded.
        val activity = context as? Activity
        if (activity != null) {
            scope.launch {
                delay(100)
                activity.recreate()
            }
        }
    }

    fun onUp() {
        when (mode) {
            ProfileGateMode.SELECT_LIST -> {
                // Carousel is horizontal; use left/right for profile switching.
            }
            ProfileGateMode.PIN_ENTRY -> {
                val row = keypadSelectedIndex / 3
                if (row > 0) keypadSelectedIndex -= 3
            }
        }
    }

    fun onDown() {
        when (mode) {
            ProfileGateMode.SELECT_LIST -> {
                // Carousel is horizontal; use left/right for profile switching.
            }
            ProfileGateMode.PIN_ENTRY -> {
                val row = keypadSelectedIndex / 3
                if (row < 3) keypadSelectedIndex += 3
            }
        }
    }

    fun onLeft() {
        when (mode) {
            ProfileGateMode.SELECT_LIST -> {
                selectedProfileIndex =
                    (selectedProfileIndex - 1).coerceAtLeast(0)
            }
            ProfileGateMode.PIN_ENTRY -> {
                val col = keypadSelectedIndex % 3
                if (col > 0) keypadSelectedIndex -= 1
            }
        }
    }

    fun onRight() {
        when (mode) {
            ProfileGateMode.SELECT_LIST -> {
                selectedProfileIndex =
                    (selectedProfileIndex + 1).coerceAtMost((profiles.size - 1).coerceAtLeast(0))
            }
            ProfileGateMode.PIN_ENTRY -> {
                val col = keypadSelectedIndex % 3
                if (col < 2) keypadSelectedIndex += 1
            }
        }
    }

    fun onEnter() {
        when (mode) {
            ProfileGateMode.SELECT_LIST -> {
                if (profiles.isEmpty()) return
                val selected = profiles[selectedProfileIndex]
                if (selected.pinHash.isBlank()) {
                    pinError = null
                    activateProfile(selected)
                } else {
                    mode = ProfileGateMode.PIN_ENTRY
                    pinBuffer = ""
                    pinError = null
                    keypadSelectedIndex = 0
                }
            }
            ProfileGateMode.PIN_ENTRY -> {
                val key = when (keypadSelectedIndex) {
                    0, 1, 2 -> (keypadSelectedIndex + 1).toString() // 1,2,3
                    3, 4, 5 -> (keypadSelectedIndex + 1).toString() // 4,5,6
                    6, 7, 8 -> (keypadSelectedIndex + 1).toString() // 7,8,9
                    10 -> "0"
                    9 -> ""
                    11 -> ""
                    else -> ""
                }

                when (keypadSelectedIndex) {
                    9 -> {
                        pinBuffer = pinBuffer.dropLast(1)
                    }
                    11 -> {
                        val selectedProfile = profiles.getOrNull(selectedProfileIndex) ?: return
                        val ok = PinUtils.verifyPin(pinBuffer, selectedProfile.pinHash)
                        if (ok) {
                            pinError = null
                            activateProfile(selectedProfile)
                        } else {
                            pinError = context.getString(R.string.userProfiles_incorrectPin)
                            pinBuffer = ""
                        }
                    }
                    else -> {
                        if (pinBuffer.length < 6) {
                            // Only digits
                            if (key.isNotBlank() && key.all { it.isDigit() }) {
                                pinBuffer += key
                            }
                        }
                    }
                }
            }
        }
    }

    fun onBack() {
        when (mode) {
            ProfileGateMode.SELECT_LIST -> {
                if (consumeNextBackOnSelectList) {
                    consumeNextBackOnSelectList = false
                    return
                }
                // If this screen was opened from the top bar, return to the prior screen.
                // If there's no previous entry (app startup), fall back to splash.
                val popped = navController.popBackStack()
                if (!popped) {
                    navController.navigate("splash") {
                        popUpTo(route) { inclusive = true }
                    }
                }
            }
            ProfileGateMode.PIN_ENTRY -> {
                mode = ProfileGateMode.SELECT_LIST
                pinBuffer = ""
                pinError = null
                keypadSelectedIndex = 0
                consumeNextBackOnSelectList = true
            }
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
                rightTransitions = mapOf(DpadSection.List to DpadSection.List)
            ),
            onUp = { onUp() },
            onDown = { onDown() },
            onLeft = { onLeft() },
            onRight = { onRight() },
            onEnter = { onEnter() },
            onBack = { onBack() }
        )
    }

    LaunchedEffect(dpadConfig) {
        dpadController.registerScreen(dpadConfig)
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Keep the same logo positioning as `SplashScreen`.
        AppLogo(
            imageHeight = 150,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 82.dp)
        )

        VersionNumber(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Bottom anchored content so the carousel/keypad always sits at the bottom.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (mode == ProfileGateMode.SELECT_LIST) {
                        stringResource(R.string.userProfiles_selectProfile)
                    } else {
                        stringResource(R.string.userProfiles_enterPin)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (mode == ProfileGateMode.SELECT_LIST) {
                    if (profiles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.userProfiles_noProfilesConfigureServer),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                24.dp,
                                Alignment.CenterHorizontally
                            )
                        ) {
                            itemsIndexed(profiles) { index, profile ->
                                val selected = index == selectedProfileIndex
                                ProfileRow(profile = profile, isSelected = selected)
                            }
                        }
                    }
                }
            }
        }

        if (mode == ProfileGateMode.PIN_ENTRY) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                val selectedProfile = profiles.getOrNull(selectedProfileIndex)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .shadow(16.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF111827))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = selectedProfile?.name ?: "",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = stringResource(R.string.userProfiles_enterPin),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val maxLen = 6
                            repeat(maxLen) { i ->
                                val filled = i < pinBuffer.length
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (filled) Color(0xFFBB86FC)
                                            else Color.White.copy(alpha = 0.25f)
                                        )
                                )
                                if (i < maxLen - 1) Spacer(modifier = Modifier.width(10.dp))
                            }
                        }

                        if (!pinError.isNullOrBlank()) {
                            Text(
                                text = pinError ?: "",
                                color = Color(0xFFFF6B6B),
                                modifier = Modifier.padding(bottom = 12.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        Text(
                            text = "${stringResource(R.string.common_cancel)}: Back",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )

                        PinKeypad(keypadSelectedIndex = keypadSelectedIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: UserProfile, isSelected: Boolean) {
    val avatarColor = AvatarColor.fromKey(profile.avatarColor).toColor()
    val avatarBorderWidth = if (isSelected) 3.dp else 2.dp
    val avatarBorderAlpha = if (isSelected) 0.95f else 0.25f

    Column(
        modifier = Modifier.width(240.dp).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(avatarColor)
                .border(
                    width = avatarBorderWidth,
                    color = Color.White.copy(alpha = avatarBorderAlpha),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profile.avatarInitials.take(2),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = profile.name,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (profile.pinHash.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.userProfiles_pinProtected),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PinKeypad(
    keypadSelectedIndex: Int,
    // Kept as a plain visual component; Enter is handled at the screen level via DPAD.
) {
    val keyLabels = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        stringResource(R.string.userProfiles_keyDelete),
        "0",
        stringResource(R.string.userProfiles_keyOk)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (row in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (col in 0 until 3) {
                    val idx = row * 3 + col
                    val label = keyLabels.getOrNull(idx) ?: ""
                    val selected = idx == keypadSelectedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clip(RectangleShape)
                            .background(if (selected) Color(0xFFBB86FC) else Color(0xFF111827))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

