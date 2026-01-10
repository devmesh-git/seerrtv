package ca.devmesh.seerrtv.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.ui.KeyUtils

/**
 * Shared CustomSearchBar component for consistent search bar styling across the app.
 * Provides D-pad navigation support and proper focus handling for Android TV.
 */
@Composable
fun CustomSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    hasSearchResults: Boolean,
    onNavigateToResults: () -> Unit,
    requestKeyboardTrigger: Int,
    onFocusLost: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val textFieldFocusRequester = remember { FocusRequester() }

    // Track if this is the initial composition
    val isInitialComposition = remember { mutableStateOf(true) }
    
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF2A2E3B))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(24.dp)
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(id = R.string.common_search),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(textFieldFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onFocus()
                        } else {
                            onFocusLost()
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_BACK -> {
                                    focusManager.clearFocus(force = true)
                                    onFocusLost()
                                    return@onPreviewKeyEvent true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    // Leave the TextField and drop into grid with one press when results exist
                                    if (hasSearchResults) {
                                        // Navigate first, then clear focus
                                        onNavigateToResults()
                                        focusManager.clearFocus(force = true)
                                        // Don't call onFocusLost here as it resets focus to Search
                                    } else {
                                        // If no results, just clear focus and let controller handle navigation
                                        focusManager.clearFocus(force = true)
                                        onFocusLost()
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_UP,
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    // Release focus so controller handles subsequent navigation
                                    focusManager.clearFocus(force = true)
                                    onFocusLost()
                                    return@onPreviewKeyEvent true
                                }
                                else -> if (KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode)) {
                                    // Open keyboard when Enter is pressed on search field
                                    if (BuildConfig.DEBUG) {
                                        Log.d("CustomSearchBar", "🎮 Enter pressed on search field - requesting focus for keyboard")
                                    }
                                    textFieldFocusRequester.requestFocus()
                                    return@onPreviewKeyEvent true
                                } else {
                                    false
                                }
                            }
                        }
                        false
                    },
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.mediaDiscovery_searchPlaceholder),
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        // Only navigate to grid if there are results
                        if (hasSearchResults) {
                            onNavigateToResults()
                            Log.d(
                                "CustomSearchBar",
                                "🎮 Navigation: Done pressed, focusing first result"
                            )
                        }
                    }
                ),
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }

    // Request focus when DPAD Enter is pressed on the search bar
    LaunchedEffect(requestKeyboardTrigger) {
        if (isSelected) {
            textFieldFocusRequester.requestFocus()
        }
    }
    
    // Clear the initial composition flag after first composition
    LaunchedEffect(Unit) {
        isInitialComposition.value = false
    }
}
