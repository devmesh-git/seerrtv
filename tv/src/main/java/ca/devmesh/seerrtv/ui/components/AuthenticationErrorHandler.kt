package ca.devmesh.seerrtv.ui.components

import android.util.Log
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import ca.devmesh.seerrtv.ui.KeyUtils
import ca.devmesh.seerrtv.util.SafeKeyEventHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthenticationErrorHandler(
    isVisible: Boolean,
    onRetry: () -> Unit,
    onReconfigure: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        var selectedOption by remember { mutableIntStateOf(0) }
        val dialogFocusRequester = remember { FocusRequester() }
        var isHandlingEnter by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        var hasRequestedInitialFocus by remember { mutableStateOf(false) }

        // Request focus when the dialog appears
        LaunchedEffect(Unit) {
            if (!hasRequestedInitialFocus) {
                delay(150) // Slightly longer delay to ensure all components are rendered
                try {
                    dialogFocusRequester.requestFocus()
                    hasRequestedInitialFocus = true
                    Log.d("AuthenticationErrorHandler", "Initial focus requested")
                } catch (e: Exception) {
                    Log.e("AuthenticationErrorHandler", "Error requesting initial focus: ${e.message}")
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(1000f)
                .focusRequester(dialogFocusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    return@onKeyEvent SafeKeyEventHandler.handleKeyEventWithContext(
                        keyEvent = keyEvent,
                        context = "AuthenticationErrorHandler"
                    ) { keyEvent ->
                        when {
                            keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft -> {
                                selectedOption = 0
                                true
                            }
                            keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight -> {
                                selectedOption = 1
                                true
                            }
                            KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) -> {
                                if (isHandlingEnter) {
                                    true
                                } else {
                                    when (keyEvent.type) {
                                        KeyEventType.KeyDown -> {
                                            isHandlingEnter = true
                                            scope.launch {
                                                if (selectedOption == 0) {
                                                    onRetry()
                                                } else {
                                                    delay(300) // Delay before reconfigure to ensure key consumption
                                                    onReconfigure()
                                                }
                                                delay(200) // Additional delay after action
                                                isHandlingEnter = false
                                            }
                                            true
                                        }
                                        KeyEventType.KeyUp -> true
                                        else -> true
                                    }
                                }
                            }
                            else -> false
                        }
                    }
                }
        ) {
            AuthenticationErrorDialog(
                onRetry = { /* Do nothing, handled by key events */ },
                onReconfigure = { /* Do nothing, handled by key events */ },
                selectedOption = selectedOption,
                isVisible = true
            )
        }
    }
} 