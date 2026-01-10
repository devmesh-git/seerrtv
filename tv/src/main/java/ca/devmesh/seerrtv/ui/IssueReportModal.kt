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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.IssueType
import ca.devmesh.seerrtv.model.videoIssues
import ca.devmesh.seerrtv.model.audioIssues
import ca.devmesh.seerrtv.model.subtitleIssues
import ca.devmesh.seerrtv.model.otherIssues
import ca.devmesh.seerrtv.viewmodel.IssueViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import android.widget.Toast

@Composable
fun IssueReportModal(
    isVisible: Boolean,
    mediaId: Int,
    isSeries: Boolean,
    onBackKeyDown: (() -> Unit)? = null,
    onClose: () -> Unit,
    onSuccess: (() -> Unit)? = null,
    viewModel: IssueViewModel = hiltViewModel()
) {
    Log.d(
        "IssueReportModal",
        "🎯 IssueReportModal created for mediaId: $mediaId, isVisible: $isVisible"
    )

    // Log when isVisible changes
    LaunchedEffect(isVisible) {
        Log.d("IssueReportModal", "🎯 isVisible changed to: $isVisible")
    }
    val submitState by viewModel.submitState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var keyboardTrigger by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val controller = remember(isSeries, ctx) {
        IssueReportModalController(
            onDismiss = onClose,
            onSubmit = { request ->
                val requestWithMediaId = request.copy(mediaId = mediaId)
                viewModel.submitIssue(requestWithMediaId)
            },
            isSeries = isSeries,
            resolveString = { resId -> ctx.getString(resId) }
        )
    }

    // Back button debouncing
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val backPressDebounceMs = 500L // 500ms debounce

    // Request focus when modal becomes visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            Log.d("IssueReportModal", "🎯 Modal is visible, requesting focus")
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
            Log.d("IssueReportModal", "🎯 Focus requested")
        }
    }

    // Close modal on successful submission and notify parent
    LaunchedEffect(submitState.success) {
        if (submitState.success) {
            Log.d("IssueReportModal", "✅ Issue created successfully - closing modal and notifying parent")
            // Close modal first
            onClose()
            // Notify parent to show success message
            onSuccess?.invoke()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Log.d("IssueReportModal", "🎯 Rendering modal with isVisible: $isVisible")

        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(durationMillis = 300)
            ),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .zIndex(10f)
        ) {
            // Handle back press with debouncing - placed inside AnimatedVisibility for proper interception
            BackHandler {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime > backPressDebounceMs) {
                    lastBackPressTime = currentTime
                    Log.d("IssueReportModal", "Back button pressed - handling with debouncing")
                    controller.handleBack()
                } else {
                    Log.d("IssueReportModal", "Back button debounced - ignoring rapid back press")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(400.dp)
                    .background(Color(0xFF1F2937))
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        // Always handle back key events to prevent them from reaching the underlying screen
                        if (event.key == Key.Back) {
                            // Consume both KeyDown and KeyUp to avoid bubbling into underlying screen
                            if (event.type == KeyEventType.KeyDown) {
                                onBackKeyDown?.invoke()
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastBackPressTime > backPressDebounceMs) {
                                    lastBackPressTime = currentTime
                                    Log.d("IssueReportModal", "🎯 Back key DOWN intercepted - handling with controller")
                                    controller.handleBack()
                                } else {
                                    Log.d("IssueReportModal", "🎯 Back key DOWN debounced - ignoring rapid back press")
                                }
                            } else if (event.type == KeyEventType.KeyUp) {
                                Log.d("IssueReportModal", "🎯 Back key UP consumed - preventing bubble to MediaDetails")
                            }
                            return@onKeyEvent true
                        }
                        // If focused on CustomDescription and Enter is pressed, trigger keyboard focus
                        if (controller.currentFocus is ModalFocusState.CustomDescription &&
                            KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) &&
                            event.type == KeyEventType.KeyDown) {
                            keyboardTrigger += 1
                            return@onKeyEvent true
                        }
                        // If Enter on Submit while invalid, show feedback toast instead of doing nothing
                        if (controller.currentFocus is ModalFocusState.SubmitButton &&
                            event.type == KeyEventType.KeyDown &&
                            KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode)) {
                            val currentPrecanned = when (IssueType.entries.find { it.value == controller.selectedIssueType }) {
                                IssueType.VIDEO -> videoIssues
                                IssueType.AUDIO -> audioIssues
                                IssueType.SUBTITLE -> subtitleIssues
                                IssueType.OTHER -> otherIssues
                                null -> emptyList()
                            }
                            val hasPrecanned = controller.selectedPrecanned >= 0 && controller.selectedPrecanned < currentPrecanned.size
                            val canSubmit = !submitState.isSubmitting && (controller.customDescription.isNotBlank() || hasPrecanned)
                            if (!canSubmit) {
                                // When None is selected and no comment, prompt the user
                                Toast.makeText(
                                    ctx,
                                    ctx.getString(R.string.issue_describe_placeholder),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@onKeyEvent true
                            }
                        }
                        controller.handleKeyEvent(event)
                    }
            ) {
                Log.d("IssueReportModal", "🎯 Modal Box content is being rendered")
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val listState = rememberLazyListState()

                    // Auto-scroll to keep focused item within viewport in a fluid way
                    fun computeFocusIndex(): Int {
                        val afterHeader = if (isSeries) 3 else 1
                        val issueTypeTitleIndex = afterHeader
                        val issueTypeStart = issueTypeTitleIndex + 1
                        val commonIssuesTitle = issueTypeStart + IssueType.entries.size
                        val precannedStart = commonIssuesTitle + 1
                        return when (val f = controller.currentFocus) {
                            // Keep section titles pinned at the top for radio lists
                            is ModalFocusState.IssueType -> if (f.index == 0) 0 else issueTypeTitleIndex
                            ModalFocusState.Season -> 0 // ensure title is visible
                            ModalFocusState.Episode -> 0 // keep header visible while on first row block
                            is ModalFocusState.PrecannedOption -> commonIssuesTitle
                            // When editing description, scroll near the bottom so buttons are visible
                            ModalFocusState.CustomDescription -> precannedStart + (
                                    when (IssueType.entries.find { it.value == controller.selectedIssueType }) {
                                        IssueType.VIDEO -> videoIssues.size
                                        IssueType.AUDIO -> audioIssues.size
                                        IssueType.SUBTITLE -> subtitleIssues.size
                                        IssueType.OTHER -> otherIssues.size
                                        null -> 0
                                    }
                                    ) + 4 // spacer + title + field + spacer -> ensures buttons show
                            ModalFocusState.CancelButton -> precannedStart + (
                                    when (IssueType.entries.find { it.value == controller.selectedIssueType }) {
                                        IssueType.VIDEO -> videoIssues.size
                                        IssueType.AUDIO -> audioIssues.size
                                        IssueType.SUBTITLE -> subtitleIssues.size
                                        IssueType.OTHER -> otherIssues.size
                                        null -> 0
                                    }
                                    ) + 4 // jump to buttons row
                            ModalFocusState.SubmitButton -> precannedStart + (
                                    when (IssueType.entries.find { it.value == controller.selectedIssueType }) {
                                        IssueType.VIDEO -> videoIssues.size
                                        IssueType.AUDIO -> audioIssues.size
                                        IssueType.SUBTITLE -> subtitleIssues.size
                                        IssueType.OTHER -> otherIssues.size
                                        null -> 0
                                    }
                                    ) + 4
                        }.coerceAtLeast(0)
                    }

                    LaunchedEffect(
                        controller.currentFocus,
                        controller.selectedIssueType,
                        isSeries
                    ) {
                        val target = computeFocusIndex()
                        when (controller.currentFocus) {
                            is ModalFocusState.IssueType,
                            is ModalFocusState.PrecannedOption -> {
                                // Always scroll to the section title for radio lists
                                listState.animateScrollToItem(target)
                            }

                            ModalFocusState.CustomDescription,
                            ModalFocusState.CancelButton,
                            ModalFocusState.SubmitButton -> {
                                // Force scroll to bottom so buttons are fully visible
                                val lastIndex =
                                    (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                listState.animateScrollToItem(lastIndex)
                            }

                            else -> {
                                // Edge-based scroll for other components
                                val first =
                                    listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                                val last =
                                    listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                val needsScroll = target <= first + 1 || target >= last - 1
                                if (needsScroll) {
                                    listState.animateScrollToItem(target)
                                }
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        // Header
                        item {
                            Text(
                                text = stringResource(R.string.issue_report_title),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        // Season/Episode Selection (only for TV series) - FIRST SECTION
                        if (isSeries) {
                            item {
                                Text(
                                    text = stringResource(R.string.issue_season_episode_title),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stringResource(R.string.issue_season), fontSize = 14.sp, color = Color.White)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp)
                                                .background(
                                                    if (controller.currentFocus is ModalFocusState.Season) Color(
                                                        0xFF374151
                                                    ) else Color.Transparent
                                                )
                                                .border(
                                                    width = if (controller.currentFocus is ModalFocusState.Season) 1.dp else 0.dp,
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "-", color = Color.White, fontSize = 20.sp)
                                            Spacer(modifier = Modifier.weight(.5f))
                                            Text(
                                                text = controller.selectedSeason.toString(),
                                                color = Color.White,
                                                fontSize = 18.sp
                                            )
                                            Spacer(modifier = Modifier.weight(.5f))
                                            Text(text = "+", color = Color.White, fontSize = 20.sp)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.issue_episode),
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp)
                                                .background(
                                                    if (controller.currentFocus is ModalFocusState.Episode) Color(
                                                        0xFF374151
                                                    ) else Color.Transparent
                                                )
                                                .border(
                                                    width = if (controller.currentFocus is ModalFocusState.Episode) 1.dp else 0.dp,
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "-", color = Color.White, fontSize = 20.sp)
                                            Spacer(modifier = Modifier.weight(.5f))
                                            Text(
                                                text = controller.selectedEpisode,
                                                color = Color.White,
                                                fontSize = 18.sp
                                            )
                                            Spacer(modifier = Modifier.weight(.5f))
                                            Text(text = "+", color = Color.White, fontSize = 20.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Issue Type Selection - SECOND SECTION
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                        item {
                            Text(
                                text = stringResource(R.string.issue_type_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        itemsIndexed(IssueType.entries.toTypedArray()) { index, type ->
                            val isFocused = controller.currentFocus is ModalFocusState.IssueType &&
                                    (controller.currentFocus as ModalFocusState.IssueType).index == index
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(if (isFocused) Color(0xFF374151) else Color.Transparent)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = controller.selectedIssueType == type.value,
                                    onClick = { controller.setIssueTypeIndex(index) }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = when (type) {
                                        IssueType.VIDEO -> stringResource(R.string.issue_type_video)
                                        IssueType.AUDIO -> stringResource(R.string.issue_type_audio)
                                        IssueType.SUBTITLE -> stringResource(R.string.issue_type_subtitle)
                                        IssueType.OTHER -> stringResource(R.string.issue_type_other)
                                    },
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(12.dp)) }

                        // Precanned Descriptions
                        item {
                            Text(
                                text = stringResource(R.string.issue_common_issues_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        val currentPrecanned =
                            when (IssueType.entries.find { it.value == controller.selectedIssueType }) {
                                IssueType.VIDEO -> videoIssues
                                IssueType.AUDIO -> audioIssues
                                IssueType.SUBTITLE -> subtitleIssues
                                IssueType.OTHER -> otherIssues
                                null -> emptyList()
                            }

                        // None option (always first)
                        item {
                            val isFocused = controller.currentFocus is ModalFocusState.PrecannedOption &&
                                    (controller.currentFocus as ModalFocusState.PrecannedOption).index == -1
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

                        itemsIndexed(currentPrecanned) { index, issue ->
                            val isFocused =
                                controller.currentFocus is ModalFocusState.PrecannedOption &&
                                        (controller.currentFocus as ModalFocusState.PrecannedOption).index == index
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
                                    text = stringResource(id = issue.descriptionResId),
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(12.dp)) }

                        // Additional Comment
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
                            val isFocused = controller.currentFocus is ModalFocusState.CustomDescription
                            val textFieldFocusRequester = remember { FocusRequester() }
                            LaunchedEffect(key1 = keyboardTrigger) {
                                if (isFocused && keyboardTrigger > 0) {
                                    kotlinx.coroutines.delay(100)
                                    textFieldFocusRequester.requestFocus()
                                }
                            }
                            OutlinedTextField(
                                value = controller.customDescription,
                                onValueChange = { controller.customDescription = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (controller.currentFocus is ModalFocusState.CustomDescription) 2.dp else 0.dp,
                                        color = if (controller.currentFocus is ModalFocusState.CustomDescription) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .focusRequester(textFieldFocusRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.nativeKeyEvent.keyCode) {
                                                android.view.KeyEvent.KEYCODE_BACK -> {
                                                    // Close IME and restore focus to modal content
                                                    focusManager.clearFocus(force = true)
                                                    focusRequester.requestFocus()
                                                    controller.currentFocus = ModalFocusState.CustomDescription
                                                    return@onPreviewKeyEvent true
                                                }
                                                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                                android.view.KeyEvent.KEYCODE_DPAD_UP,
                                                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                    focusManager.clearFocus(force = true)
                                                    focusRequester.requestFocus()
                                                    controller.currentFocus = ModalFocusState.CustomDescription
                                                    // Allow modal onKeyEvent to handle navigation
                                                    return@onPreviewKeyEvent false
                                                }
                                                android.view.KeyEvent.KEYCODE_ENTER -> {
                                                    // Let default handling (newline) or IME action occur
                                                    return@onPreviewKeyEvent false
                                                }
                                            }
                                        }
                                        false
                                    },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        focusRequester.requestFocus()
                                        controller.currentFocus = ModalFocusState.CustomDescription
                                    }
                                ),
                                minLines = 3,
                                placeholder = { Text(stringResource(R.string.issue_describe_placeholder)) }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }

                        // Action Buttons
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val cancelFocused =
                                    controller.currentFocus is ModalFocusState.CancelButton
                                val submitFocused =
                                    controller.currentFocus is ModalFocusState.SubmitButton

                                // Cancel - tight focus border like RequestModal.ActionButton
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
                                        onClick = onClose,
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
                                        onClick = { controller.submitIssue() },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .height(40.dp)
                                            .fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(
                                                0xFF6200EE
                                            )
                                        ),
                                        enabled = run {
                                            if (submitState.isSubmitting) return@run false
                                            val currentPrecanned =
                                                when (IssueType.entries.find { it.value == controller.selectedIssueType }) {
                                                    IssueType.VIDEO -> videoIssues
                                                    IssueType.AUDIO -> audioIssues
                                                    IssueType.SUBTITLE -> subtitleIssues
                                                    IssueType.OTHER -> otherIssues
                                                    null -> emptyList()
                                                }
                                            val hasPrecanned = controller.selectedPrecanned >= 0 && controller.selectedPrecanned < currentPrecanned.size
                                            controller.customDescription.isNotBlank() || hasPrecanned
                                        }
                                    ) {
                                        if (submitState.isSubmitting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.width(16.dp),
                                                color = Color.White
                                            )
                                        } else {
                                            Text(stringResource(R.string.issue_submit_button), color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // Status Messages
                        if (submitState.errorMessage != null) {
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                            item {
                                Text(
                                    text = submitState.errorMessage ?: stringResource(R.string.common_error),
                                    color = Color.Red,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
