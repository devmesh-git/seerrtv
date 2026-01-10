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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import ca.devmesh.seerrtv.ui.components.ActionButton
import ca.devmesh.seerrtv.ui.components.AutoUpdatingHumanizedDate
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.Issue

@Composable
fun IssueDetailsModal(
    isVisible: Boolean,
    issues: List<Issue>?,
    onBackKeyDown: (() -> Unit)? = null,
    onClose: () -> Unit,
    onCreateNewIssue: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    canCreateIssues: Boolean = true
) {
    Log.d("IssueDetailsModal", "🎯 IssueDetailsModal created with ${issues?.size ?: 0} issues, isVisible: $isVisible, canCreateIssues: $canCreateIssues")
    val focusRequester = remember { FocusRequester() }
    
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // State for AddCommentModal
    var showAddComment by remember { mutableStateOf(false) }
    var selectedIssueForComment by remember { mutableStateOf<Issue?>(null) }
    var lastChildBackKeyTime by remember { mutableLongStateOf(0L) }
    
    val controller = remember(canCreateIssues) {
        IssueDetailsModalController(
            onDismiss = onClose,
            onCreateNewIssue = onCreateNewIssue,
            onOpenAddComment = { issue ->
                Log.d("IssueDetailsModal", "🎯 onOpenAddComment called for issue: ${issue.id}")
                selectedIssueForComment = issue
                showAddComment = true
                Log.d("IssueDetailsModal", "🎯 showAddComment set to: ${true}, selectedIssueForComment: $selectedIssueForComment")
            },
            lazyListState = lazyListState,
            coroutineScope = coroutineScope,
            canCreateIssues = canCreateIssues
        )
    }
    
    // Back button debouncing
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val backPressDebounceMs = 500L // 500ms debounce
    
    // Sort issues: Open first, then by newest first
    val sortedIssues = issues?.sortedWith(compareBy<Issue> { issue ->
        // Status priority: Open (1) = 0, Resolved (3) = 1
        when (issue.status) {
            1 -> 0 // Open - highest priority
            2 -> 1 // Resolved
            else -> 2 // Unknown status - lowest priority
        }
    }.thenByDescending { issue ->
        // Then by newest first (most recent createdAt)
        issue.createdAt
    }) ?: emptyList()
    
    // Update issue count when issues change
    LaunchedEffect(issues) {
        controller.issueCount = issues?.size ?: 0
        // Reset selection if we have fewer issues than the current selection
        if (controller.selectedIssueIndex >= (issues?.size ?: 0)) {
            controller.selectedIssueIndex = 0
        }
    }
    
    // Request focus when modal becomes visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            Log.d("IssueDetailsModal", "🎯 Modal is visible, requesting focus")
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
            Log.d("IssueDetailsModal", "🎯 Focus requested")
        }
    }

    // Monitor focus state and request focus if modal is open but doesn't have focus
    // Only do this when AddCommentModal is NOT open
    LaunchedEffect(isVisible, showAddComment) {
        if (isVisible && !showAddComment) {
            while (!showAddComment) {
                // Check if modal is visible but doesn't have focus
                // This will help regain focus after data refreshes or other operations
                kotlinx.coroutines.delay(100) // Check every 100ms
                if (!showAddComment) {
                    // Request focus to ensure modal stays focused while open
                    focusRequester.requestFocus()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                .zIndex(10f) // Ensure modal appears above the top bar
        ) {
            // Handle back press with debouncing
            BackHandler {
                val currentTime = System.currentTimeMillis()
                // If child just handled Back, ignore this Back to avoid cascading close
                if (currentTime - lastChildBackKeyTime < 600L) {
                    return@BackHandler
                }
                if (currentTime - lastBackPressTime > backPressDebounceMs) {
                    lastBackPressTime = currentTime
                    if (showAddComment) {
                        Log.d("IssueDetailsModal", "Back pressed - closing AddCommentModal only")
                        showAddComment = false
                    } else {
                        Log.d("IssueDetailsModal", "Back button pressed - handling with debouncing (closing IssueDetailsModal)")
                        controller.handleBack()
                    }
                } else {
                    Log.d("IssueDetailsModal", "Back button debounced - ignoring rapid back press")
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(400.dp) // Narrower width like RequestModal
                    .background(Color(0xFF1F2937)) // Same background as settings menu
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        Log.d("IssueDetailsModal", "🎯 Key event received: ${event.key} type: ${event.type}")
                        
                        // Always handle back key events to prevent them from reaching the underlying screen
                        if (event.key == Key.Back && event.type == KeyEventType.KeyDown) {
                            onBackKeyDown?.invoke()
                            lastChildBackKeyTime = System.currentTimeMillis()
                            if (showAddComment) {
                                Log.d("IssueDetailsModal", "🎯 Back key - closing AddCommentModal only")
                                showAddComment = false
                            } else {
                                Log.d("IssueDetailsModal", "🎯 Back key event intercepted - handling with controller (closing IssueDetailsModal)")
                                controller.handleBack()
                            }
                            return@onKeyEvent true // Consume the event
                        }
                        
                        // Only handle navigation keys, let IssueRow components handle Enter
                        val result = when {
                            event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                                Log.d("IssueDetailsModal", "🎯 Handling Up key in modal")
                                controller.handleUpKey()
                                true
                            }
                            event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                                Log.d("IssueDetailsModal", "🎯 Handling Down key in modal")
                                controller.handleDownKey()
                                true
                            }
                            event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown -> {
                                Log.d("IssueDetailsModal", "🎯 Handling Left key in modal")
                                controller.handleLeftKey()
                                true
                            }
                            event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown -> {
                                Log.d("IssueDetailsModal", "🎯 Handling Right key in modal")
                                controller.handleRightKey()
                                true
                            }
                            KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) && event.type == KeyEventType.KeyDown -> {
                                Log.d("IssueDetailsModal", "🎯 Handling Enter key in modal")
                                // Handle Enter key based on current focus
                                when (controller.currentFocus) {
                                    is IssueDetailsFocusState.IssueList -> {
                                        // Get the currently selected issue and trigger onIssueEnter
                                        val selectedIssue = sortedIssues.getOrNull(controller.selectedIssueIndex)
                                        if (selectedIssue != null) {
                                            Log.d("IssueDetailsModal", "🎯 Enter key on issue: ${selectedIssue.id}")
                                            controller.handleIssueSelection(selectedIssue)
                                        }
                                    }
                                    is IssueDetailsFocusState.CancelButton -> {
                                        Log.d("IssueDetailsModal", "🎯 Enter key on Cancel button")
                                        controller.handleBack()
                                    }
                                    is IssueDetailsFocusState.NewIssueButton -> {
                                        Log.d("IssueDetailsModal", "🎯 Enter key on New Issue button")
                                        controller.triggerCreateNewIssue()
                                    }
                                }
                                true
                            }
                            else -> {
                                Log.d("IssueDetailsModal", "🎯 Key not handled by modal: ${event.key}")
                                false
                            }
                        }
                        Log.d("IssueDetailsModal", "🎯 Key event handled: $result")
                        result
                    }
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = stringResource(R.string.issue_details_title),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    when {
                        issues == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                        issues.isEmpty() -> Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = stringResource(R.string.issue_no_issues_message), color = Color.White, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.issue_press_back_to_close), color = Color.White.copy(alpha = 0.8f))
                        }
                        else -> IssueList(
                            selectedIndex = controller.selectedIssueIndex,
                            currentFocus = controller.currentFocus,
                            lazyListState = lazyListState,
                            onUpdateIssueCount = { count -> controller.updateIssueCount(count) },
                            sortedIssues = sortedIssues,
                            canCreateIssues = canCreateIssues
                        )
                    }
                }
            }
        }
        
        // AddCommentModal overlay
        if (showAddComment && selectedIssueForComment != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2000f)
            ) {
                AddCommentModal(
                    isVisible = showAddComment,
                    issue = selectedIssueForComment!!,
                    onBackKeyDown = {
                        // Mark child handled Back so our BackHandler will ignore this cycle
                        lastChildBackKeyTime = System.currentTimeMillis()
                        onBackKeyDown?.invoke()
                    },
                    onClose = { 
                        showAddComment = false
                        selectedIssueForComment = null
                        // Request focus back to IssueDetailsModal
                        focusRequester.requestFocus()
                    },
                    onSuccess = {
                        // Refresh the issue list after successful comment submission
                        Log.d("IssueDetailsModal", "🎯 Comment added successfully - requesting data refresh")
                        onRefresh?.invoke()
                        // The focus monitoring LaunchedEffect will handle regaining focus
                        Log.d("IssueDetailsModal", "🎯 Data refresh triggered - focus monitoring will handle focus")
                    }
                )
            }
        }
    }
}

@Composable
private fun IssueList(
    selectedIndex: Int,
    currentFocus: IssueDetailsFocusState,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onUpdateIssueCount: (Int) -> Unit,
    sortedIssues: List<Issue>,
    canCreateIssues: Boolean
) {
    // Update issue count when sorted issues change
    LaunchedEffect(sortedIssues.size) {
        onUpdateIssueCount(sortedIssues.size)
    }
    
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
                itemsIndexed(sortedIssues) { index, issue ->
                    val isCurrentlyFocused = currentFocus is IssueDetailsFocusState.IssueList && index == selectedIndex
                    IssueRow(
                        issue = issue,
                        isFocused = isCurrentlyFocused,
                    )
                }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons - matching RequestModal style (Cancel first, then New Issue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (canCreateIssues) Arrangement.SpaceBetween else Arrangement.Center
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ActionButton(
                        text = stringResource(R.string.common_cancel),
                        isFocused = currentFocus is IssueDetailsFocusState.CancelButton,
                        backgroundColor = Color.Gray
                    )
                }
                if (canCreateIssues) {
                    Box(modifier = Modifier.weight(1f)) {
                        ActionButton(
                            text = stringResource(R.string.issue_new_issue_button),
                            isFocused = currentFocus is IssueDetailsFocusState.NewIssueButton,
                            backgroundColor = Color(0xFF6200EE)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueRow(
    issue: Issue,
    isFocused: Boolean,
) {
    val (statusLabel, statusColor) = when (issue.status) {
        1 -> stringResource(R.string.issue_status_open) to Color(0xFFc2992a) // Yellow for open
        2 -> stringResource(R.string.issue_status_resolved) to Color(0xFF2ca65b) // Green for resolved
        else -> stringResource(R.string.common_unknown) to Color(0xFF9CA3AF) // Light gray for unknown
    }
    val typeLabel = when (issue.issueType) {
        1 -> stringResource(R.string.issue_type_video)
        2 -> stringResource(R.string.issue_type_audio)
        3 -> stringResource(R.string.issue_type_subtitle)
        4 -> stringResource(R.string.issue_type_other)
        else -> stringResource(R.string.common_unknown)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isFocused) Color(0xFF374151) else Color(0xFF2B303A),
                RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF424B5A),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        // First row: Type and Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
                // Type badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.issue_type_label),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2AA55B), // Green color for type
                    modifier = Modifier
                ) {
                    Text(
                        text = typeLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            // Status badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.issue_status_label),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = statusColor,
                    modifier = Modifier
                ) {
                    Text(
                        text = statusLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Second row: Season and Episode
        SeasonEpisodeDisplay(
            season = issue.problemSeason,
            episode = issue.problemEpisode
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Third row: Opened info
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.issue_opened_label),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
            AutoUpdatingHumanizedDate(
                date = issue.createdAt,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                ),
                isVisible = true
            )
            Text(
                text = stringResource(R.string.issue_by_label),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = issue.createdBy?.displayName ?: issue.createdBy?.username ?: issue.createdBy?.email ?: stringResource(R.string.common_unknown),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (issue.comments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Show last 3 comments (newest first)
                issue.comments.takeLast(3).reversed().forEach { comment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left column: Timestamp (styled as label)
                        Column(
                            modifier = Modifier.width(80.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AutoUpdatingHumanizedDate(
                                date = comment.createdAt,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 18.sp
                                ),
                                isVisible = true
                            )
                        }
                        
                        // Right column: Comment (word wrapped, prominent)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = comment.message,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                
                if (issue.comments.size > 3) {
                    Text(
                        text = stringResource(R.string.issue_more_comments, issue.comments.size - 3),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonEpisodeDisplay(
    season: Int,
    episode: Any // Can be String for TV series or Int for movies
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Season badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.issue_season),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        Color(0xFF6A5ACD), // Purple color like in the image
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = season.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Episode badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.issue_episode),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        Color(0xFF6A5ACD), // Purple color like in the image
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (episode) {
                        is String -> episode
                        is Int -> episode.toString()
                        else -> "0"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
