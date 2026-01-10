package ca.devmesh.seerrtv.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.Issue
import ca.devmesh.seerrtv.model.videoIssues
import ca.devmesh.seerrtv.model.audioIssues
import ca.devmesh.seerrtv.model.subtitleIssues
import ca.devmesh.seerrtv.model.otherIssues
import ca.devmesh.seerrtv.viewmodel.IssueViewModel

@Composable
fun AddCommentModal(
    isVisible: Boolean,
    issue: Issue,
    onBackKeyDown: (() -> Unit)? = null,
    onClose: () -> Unit,
    onSuccess: (() -> Unit)? = null,
    viewModel: IssueViewModel = hiltViewModel()
) {
    Log.d("AddCommentModal", "🎯 AddCommentModal created for issueId: ${issue.id}, isVisible: $isVisible")

    val submitState by viewModel.submitState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Trigger to request keyboard focus in the text field on DPAD Enter
    var keyboardTrigger by remember { mutableIntStateOf(0) }

    // Debounce Enter immediately after modal opens to avoid submitting from prior screen's Enter
    var modalOpenedAtTs by remember { mutableLongStateOf(0L) }
    // Track whether we've already handled a success for this modal open
    var handledSuccess by remember { mutableStateOf(false) }
    // Track whether the user pressed submit during this modal open
    var userSubmitted by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val controller = remember(issue, ctx) {
        AddCommentModalController(
            onDismiss = onClose,
            onSubmit = { message ->
                userSubmitted = true
                viewModel.addComment(issue.id, message)
            },
            issueType = issue.issueType,
            listState = listState,
            coroutineScope = coroutineScope,
            onRequestKeyboard = { keyboardTrigger += 1 },
            resolveString = { resId -> ctx.getString(resId) }
        )
    }

    // Back button debouncing
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val backPressDebounceMs = 500L

    // Reset submit state immediately when modal becomes visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            Log.d("AddCommentModal", "🎯 Modal is visible, resetting submit state immediately")
            // Reset the submit state to prevent stale success from immediately closing the modal
            viewModel.resetSubmitState()
            modalOpenedAtTs = System.currentTimeMillis()
            handledSuccess = false
            userSubmitted = false
        }
    }

    // Request focus when modal becomes visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            Log.d("AddCommentModal", "🎯 Modal is visible, requesting focus")
            delay(100)
            focusRequester.requestFocus()
        }
    }

    // Monitor focus state and request focus if modal is open but doesn't have focus
    // Only request focus when text field is NOT focused (to avoid interfering with keyboard)
    LaunchedEffect(isVisible) {
        if (isVisible) {
            while (true) {
                // Check if modal is visible but doesn't have focus
                // This will help regain focus if it's stolen by other components
                delay(100) // Check every 100ms
                // Only request focus if we're not in the text field (to avoid interfering with keyboard)
                val isTextFieldFocused = controller.currentFocus is AddCommentFocusState.CustomComment
                if (!isTextFieldFocused) {
                    // Request focus to ensure modal stays focused while open
                    focusRequester.requestFocus()
                }
            }
        }
    }

    // Close modal on successful submission and notify parent (only once per modal open)
    LaunchedEffect(submitState.success, isVisible, userSubmitted) {
        if (isVisible && userSubmitted && submitState.success && !handledSuccess) {
            handledSuccess = true
            onClose()
            onSuccess?.invoke()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background overlay
        if (isVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            )
        }

        // Modal content - slide out from left like IssueDetailsModal
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(300)
            ),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .zIndex(2000f) // Higher z-index than IssueDetailsModal
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(400.dp) // Same width as IssueDetailsModal
                    .background(Color(0xFF1F2937)) // Same background as IssueDetailsModal
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        Log.d("AddCommentModal", "🎯 Modal key event: ${event.key} type: ${event.type}, currentFocus: ${controller.currentFocus}")
                        
                        // Always handle back key events to prevent them from reaching the underlying screen
                        if (event.key == androidx.compose.ui.input.key.Key.Back) {
                            if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                onBackKeyDown?.invoke()
                                Log.d("AddCommentModal", "🎯 Back key event intercepted - handling with controller")
                                controller.handleBack()
                            }
                            return@onKeyEvent true // Consume the event
                        }
                        
                        // If we're focused on CustomComment and Enter is pressed, consume it here
                        // and trigger the keyboard directly instead of letting it bubble up
                        if (controller.currentFocus is AddCommentFocusState.CustomComment && 
                            KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) && 
                            event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                            Log.d("AddCommentModal", "🎯 Enter key on CustomComment - consuming event and triggering keyboard")
                            controller.handleEnterKey()
                            return@onKeyEvent true
                        }
                        // Guard: ignore Enter keys within 1s of opening the modal to prevent accidental submit from prior screen
                        if (KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) &&
                            event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                            val sinceOpen = System.currentTimeMillis() - modalOpenedAtTs
                            if (sinceOpen < 1000) {
                                Log.d("AddCommentModal", "⏸️ Enter ignored (${sinceOpen}ms since open) to prevent accidental submit")
                                return@onKeyEvent true
                            }
                        }

                        val result = controller.handleKeyEvent(event)
                        Log.d("AddCommentModal", "🎯 Modal key event result: $result")
                        result
                    }
            ) {
                // Back handler
                BackHandler {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime > backPressDebounceMs) {
                        lastBackPressTime = currentTime
                        onBackKeyDown?.invoke()
                        controller.handleBack()
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Header
                    item {
                        Text(
                            text = stringResource(R.string.issue_add_comment_title),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Common Issues
                    item {
                        Text(
                            text = stringResource(R.string.issue_common_issues_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    val currentPrecanned = getPrecannedIssuesForType(issue.issueType)

                    // None option (always first)
                    item {
                        val isFocused = when (val focus = controller.currentFocus) {
                            is AddCommentFocusState.PrecannedOption -> focus.index == -1
                            else -> false
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(if (isFocused) Color(0xFF374151) else Color.Transparent)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = controller.selectedPrecanned == -1,
                                onClick = { controller.selectedPrecanned = -1 }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.issue_none_option),
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Precanned options
                    itemsIndexed(currentPrecanned) { index, precannedIssue ->
                        val isFocused = when (val focus = controller.currentFocus) {
                            is AddCommentFocusState.PrecannedOption -> focus.index == index
                            else -> false
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(if (isFocused) Color(0xFF374151) else Color.Transparent)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = controller.selectedPrecanned == index,
                                onClick = { controller.selectedPrecanned = index }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(id = precannedIssue.descriptionResId),
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(12.dp)) }

                    // Custom Comment
                    item {
                        Text(
                            text = stringResource(R.string.issue_additional_comment_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    item {
                        val isFocused = controller.currentFocus is AddCommentFocusState.CustomComment
                        val textFieldFocusRequester = remember { FocusRequester() }
                        val focusManager = LocalFocusManager.current
                        
                        // Request focus when keyboard trigger is activated - same pattern as MediaDiscoveryScreen
                        LaunchedEffect(keyboardTrigger) {
                            if (isFocused && keyboardTrigger > 0) {
                                Log.d("AddCommentModal", "🎯 Keyboard trigger activated (trigger: $keyboardTrigger) - requesting focus on text field")
                                delay(100)
                                textFieldFocusRequester.requestFocus()
                            }
                        }
                        
                        OutlinedTextField(
                            value = controller.customComment,
                            onValueChange = { controller.customComment = it },
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = Color.White,
                                focusedBorderColor = if (isFocused) Color.White else Color(0xFFBB86FC),
                                unfocusedBorderColor = if (isFocused) Color.White else Color.Gray
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 16.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(
                                    if (isFocused) Color(0xFF374151) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(if (isFocused) 4.dp else 0.dp)
                                .focusRequester(textFieldFocusRequester)
                                .onFocusChanged { focusState ->
                                    Log.d("AddCommentModal", "🎯 Text field focus changed: ${focusState.isFocused}")
                                    if (focusState.isFocused) {
                                        Log.d("AddCommentModal", "🎯 Text field is now focused - keyboard should be visible")
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    Log.d("AddCommentModal", "🎯 Text field key event: ${event.key} type: ${event.type}, keyCode: ${event.nativeKeyEvent.keyCode}")
                                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                        when (event.nativeKeyEvent.keyCode) {
                                            android.view.KeyEvent.KEYCODE_BACK -> {
                                                Log.d("AddCommentModal", "🎯 Back key in text field - clearing focus and returning to modal")
                                                focusManager.clearFocus(force = true)
                                                // Immediately return focus to modal to prevent focus loss
                                                focusRequester.requestFocus()
                                                // Keep modal controller focus on CustomComment after closing IME
                                                controller.currentFocus = AddCommentFocusState.CustomComment
                                                return@onPreviewKeyEvent true
                                            }
                                            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                            android.view.KeyEvent.KEYCODE_DPAD_UP,
                                            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                Log.d("AddCommentModal", "🎯 D-pad key in text field - clearing focus and returning to modal")
                                                focusManager.clearFocus(force = true)
                                                // Immediately return focus to modal to prevent focus loss
                                                focusRequester.requestFocus()
                                                // Preserve current modal section focus
                                                controller.currentFocus = AddCommentFocusState.CustomComment
                                                // Let the modal handle the navigation by returning false
                                                return@onPreviewKeyEvent false
                                            }
                                            android.view.KeyEvent.KEYCODE_ENTER -> {
                                                Log.d("AddCommentModal", "🎯 Enter key in text field - allowing default behavior")
                                                return@onPreviewKeyEvent false
                                            }
                                        }
                                    }
                                    false
                                },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.issue_add_comment_placeholder),
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    Log.d("AddCommentModal", "🎯 Keyboard Done action triggered")
                                    // Close IME and explicitly restore focus to modal
                                    focusManager.clearFocus()
                                    focusRequester.requestFocus()
                                    controller.currentFocus = AddCommentFocusState.CustomComment
                                }
                            ),
                            minLines = 3
                        )
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }

                    // Action buttons - matching IssueReportModal style
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val cancelFocused = controller.currentFocus is AddCommentFocusState.CancelButton
                            val submitFocused = controller.currentFocus is AddCommentFocusState.SubmitButton

                            // Cancel - tight focus border like IssueReportModal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (cancelFocused) 2.dp else 0.dp,
                                        color = if (cancelFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Button(
                                    onClick = { controller.handleBack() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(stringResource(R.string.common_cancel), color = Color.White)
                                }
                            }

                            // Submit - tight focus border
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (submitFocused) 2.dp else 0.dp,
                                        color = if (submitFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Button(
                                    onClick = { controller.submitComment() },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (submitState.isSubmitting) Color.Gray else Color(0xFFBB86FC)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    enabled = !submitState.isSubmitting
                                ) {
                                    if (submitState.isSubmitting) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Text(stringResource(R.string.issue_add_comment_button), color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getPrecannedIssuesForType(issueType: Int): List<ca.devmesh.seerrtv.model.PrecannedIssue> {
    return when (issueType) {
        1 -> videoIssues // Video issues
        2 -> audioIssues // Audio issues
        3 -> subtitleIssues // Subtitle issues
        4 -> otherIssues // Other issues
        else -> emptyList()
    }
}

// Focus states
sealed class AddCommentFocusState {
    data class PrecannedOption(val index: Int) : AddCommentFocusState()
    object CustomComment : AddCommentFocusState()
    object CancelButton : AddCommentFocusState()
    object SubmitButton : AddCommentFocusState()
}

// Controller
class AddCommentModalController(
    private val onDismiss: () -> Unit,
    private val onSubmit: (String) -> Unit,
    private val issueType: Int,
    private val listState: androidx.compose.foundation.lazy.LazyListState,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
    private val onRequestKeyboard: () -> Unit,
    private val resolveString: (Int) -> String
) {
    var customComment by mutableStateOf("")
    var selectedPrecanned by mutableIntStateOf(-1) // Start with "None" selected
    var currentFocus by mutableStateOf<AddCommentFocusState>(AddCommentFocusState.PrecannedOption(-1))

    fun handleKeyEvent(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val result = when {
            event.key == androidx.compose.ui.input.key.Key.DirectionUp && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                handleUpKey()
                true
            }
            event.key == androidx.compose.ui.input.key.Key.DirectionDown && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                handleDownKey()
                true
            }
            event.key == androidx.compose.ui.input.key.Key.DirectionRight && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                handleRightKey()
                true
            }
            event.key == androidx.compose.ui.input.key.Key.DirectionLeft && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                handleLeftKey()
                true
            }
            KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                handleEnterKey()
                true
            }
            event.key == androidx.compose.ui.input.key.Key.Back -> {
                // Let BackHandler handle this, but consume the event to prevent bubbling
                true
            }
            else -> false
        }
        
        // Auto-scroll to keep focused item within viewport
        if (result) {
            coroutineScope.launch {
                scrollToFocusedItem()
            }
        }
        
        return result
    }
    
    private suspend fun scrollToFocusedItem() {
        when (val focus = currentFocus) {
            is AddCommentFocusState.PrecannedOption -> {
                // Scroll to the correct position for radio items
                // List structure:
                // 0: Header, 1: "Common Issues" title, 2: "None", 3..(3+n-1): precanned options
                if (focus.index == -1) {
                    // When on the top-most radio ("None"), scroll to top of the modal
                    Log.d("AddCommentModal", "🎯 Scrolling to top for 'None' option focus")
                    listState.animateScrollToItem(0)
                } else {
                    val target = 3 + focus.index
                    Log.d("AddCommentModal", "🎯 Scrolling to precanned option at index $target (precannedIndex=${focus.index})")
                    listState.animateScrollToItem(target)
                }
            }
            is AddCommentFocusState.CustomComment -> {
                // Scroll to custom comment section
                val precannedCount = getPrecannedIssuesForType(issueType).size
                val target = 5 + precannedCount // Header + "Common Issues" + "None" + precanned + spacer + "Additional Comment" + custom comment
                Log.d("AddCommentModal", "🎯 Scrolling to custom comment at index $target (precannedCount: $precannedCount)")
                listState.animateScrollToItem(target)
            }
            is AddCommentFocusState.CancelButton, is AddCommentFocusState.SubmitButton -> {
                // Scroll to bottom for buttons
                val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                Log.d("AddCommentModal", "🎯 Scrolling to buttons at index $lastIndex")
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    private fun handleUpKey() {
        when (val focus = currentFocus) {
            is AddCommentFocusState.PrecannedOption -> {
                if (focus.index == -1) {
                    // Already at "None" option, stay here
                } else if (focus.index > 0) {
                    currentFocus = AddCommentFocusState.PrecannedOption(focus.index - 1)
                } else {
                    // Move from first precanned option to "None"
                    currentFocus = AddCommentFocusState.PrecannedOption(-1)
                }
            }
            is AddCommentFocusState.CustomComment -> {
                val precannedCount = getPrecannedIssuesForType(issueType).size
                currentFocus = if (precannedCount > 0) {
                    AddCommentFocusState.PrecannedOption(precannedCount - 1)
                } else {
                    AddCommentFocusState.PrecannedOption(-1)
                }
            }
            is AddCommentFocusState.CancelButton -> {
                currentFocus = AddCommentFocusState.CustomComment
            }
            is AddCommentFocusState.SubmitButton -> {
                currentFocus = AddCommentFocusState.CancelButton
            }
        }
        Log.d("AddCommentModal", "🎯 handleUpKey: focus changed to $currentFocus")
    }

    private fun handleDownKey() {
        when (val focus = currentFocus) {
            is AddCommentFocusState.PrecannedOption -> {
                if (focus.index == -1) {
                    // Move from "None" to first precanned option (if any)
                    val precannedCount = getPrecannedIssuesForType(issueType).size
                    currentFocus = if (precannedCount > 0) {
                        AddCommentFocusState.PrecannedOption(0)
                    } else {
                        AddCommentFocusState.CustomComment
                    }
                } else {
                    val precannedCount = getPrecannedIssuesForType(issueType).size
                    currentFocus = if (focus.index < precannedCount - 1) {
                        AddCommentFocusState.PrecannedOption(focus.index + 1)
                    } else {
                        AddCommentFocusState.CustomComment
                    }
                }
            }
            is AddCommentFocusState.CustomComment -> {
                currentFocus = AddCommentFocusState.CancelButton
            }
            is AddCommentFocusState.CancelButton -> {
                currentFocus = AddCommentFocusState.SubmitButton
            }
            is AddCommentFocusState.SubmitButton -> {
                // Stay on submit button
            }
        }
        Log.d("AddCommentModal", "🎯 handleDownKey: focus changed to $currentFocus")
    }

    fun handleEnterKey() {
        when (val focus = currentFocus) {
            is AddCommentFocusState.PrecannedOption -> {
                selectedPrecanned = focus.index
            }
            is AddCommentFocusState.CustomComment -> {
                // Request keyboard focus for text input
                Log.d("AddCommentModal", "🎯 Enter key pressed on CustomComment - requesting keyboard")
                onRequestKeyboard()
            }
            is AddCommentFocusState.CancelButton -> {
                handleBack()
            }
            is AddCommentFocusState.SubmitButton -> {
                submitComment()
            }
        }
    }

    private fun handleRightKey() {
        when (currentFocus) {
            is AddCommentFocusState.CancelButton -> {
                // Move from Cancel to Submit button
                currentFocus = AddCommentFocusState.SubmitButton
                Log.d("AddCommentModal", "🎯 Right key: focus changed to SubmitButton")
            }
            is AddCommentFocusState.SubmitButton -> {
                // Already at Submit button, stay here
            }
            else -> {
                // For other focus states, don't handle Right key
            }
        }
    }

    private fun handleLeftKey() {
        when (currentFocus) {
            is AddCommentFocusState.SubmitButton -> {
                // Move from Submit to Cancel button
                currentFocus = AddCommentFocusState.CancelButton
                Log.d("AddCommentModal", "🎯 Left key: focus changed to CancelButton")
            }
            is AddCommentFocusState.CancelButton -> {
                // Already at Cancel button, stay here
            }
            else -> {
                // For other focus states, don't handle Left key
            }
        }
    }

    fun handleBack() {
        onDismiss()
    }

    fun submitComment() {
        val customText = customComment.trim()
        val precannedList = getPrecannedIssuesForType(issueType)
        val precannedText = if (selectedPrecanned in precannedList.indices) {
            resolveString(precannedList[selectedPrecanned].descriptionResId)
        } else null

        val message = when {
            !precannedText.isNullOrBlank() && customText.isNotBlank() -> "$precannedText\n$customText"
            !precannedText.isNullOrBlank() -> precannedText
            customText.isNotBlank() -> customText
            else -> ""
        }

        if (message.isNotBlank()) {
            onSubmit(message)
        }
    }
}