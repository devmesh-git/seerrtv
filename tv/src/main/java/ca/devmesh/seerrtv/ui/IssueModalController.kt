package ca.devmesh.seerrtv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import ca.devmesh.seerrtv.model.IssueType
import ca.devmesh.seerrtv.model.CreateIssueRequest
import ca.devmesh.seerrtv.model.videoIssues
import ca.devmesh.seerrtv.model.audioIssues
import ca.devmesh.seerrtv.model.subtitleIssues
import ca.devmesh.seerrtv.model.otherIssues
import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class IssueReportModalController(
    private val onDismiss: () -> Unit,
    private val onSubmit: (CreateIssueRequest) -> Unit,
    val isSeries: Boolean,
    private val resolveString: (Int) -> String
) {
    var selectedIssueType by mutableIntStateOf(1) // Default to Video
    var selectedSeason by mutableIntStateOf(1)
    var selectedEpisode by mutableStateOf("1")
    var customDescription by mutableStateOf("")
    var selectedPrecanned by mutableIntStateOf(-1) // Start with "None" selected
    
    var currentFocus by mutableStateOf(if (isSeries) ModalFocusState.Season else ModalFocusState.IssueType(0))
    
    fun handleBack() {
        onDismiss()
    }
    
    fun handleKeyEvent(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        return when {
            event.key == androidx.compose.ui.input.key.Key.DirectionUp && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                handleUpKey()
                true
            }
            event.key == androidx.compose.ui.input.key.Key.DirectionDown && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                handleDownKey()
                true
            }
            event.key == androidx.compose.ui.input.key.Key.DirectionLeft && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                handleLeftKey()
                true
            }
            event.key == androidx.compose.ui.input.key.Key.DirectionRight && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                handleRightKey()
                true
            }
            KeyUtils.isEnterKey(event.nativeKeyEvent.keyCode) && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                handleEnterKey()
                true
            }
            else -> false
        }
    }
    
    fun handleUpKey() {
        val current = currentFocus
        currentFocus = when (current) {
            is ModalFocusState.IssueType -> {
                if (current.index > 0) {
                    ModalFocusState.IssueType(current.index - 1)
                } else {
                    // At first issue type; if series, go back to Episode row
                    if (isSeries) ModalFocusState.Episode else current
                }
            }
            is ModalFocusState.Season -> ModalFocusState.Season // top boundary
            is ModalFocusState.Episode -> ModalFocusState.Season
            is ModalFocusState.PrecannedOption -> {
                if (current.index == -1) {
                    // Move up from "None" back into Issue Type list (last item)
                    ModalFocusState.IssueType(IssueType.entries.size - 1)
                } else if (current.index == 0) {
                    // Move from first precanned option to "None"
                    ModalFocusState.PrecannedOption(-1)
                } else {
                    ModalFocusState.PrecannedOption(current.index - 1)
                }
            }
            is ModalFocusState.CustomDescription -> {
                val currentPrecanned = getCurrentPrecannedIssues()
                if (currentPrecanned.isNotEmpty()) ModalFocusState.PrecannedOption(currentPrecanned.size - 1)
                else if (isSeries) ModalFocusState.Episode else ModalFocusState.IssueType(0)
            }
            is ModalFocusState.CancelButton -> ModalFocusState.CustomDescription
            is ModalFocusState.SubmitButton -> ModalFocusState.CustomDescription
        }
    }
    
    fun handleDownKey() {
        val currentPrecanned = getCurrentPrecannedIssues()
        
        val current = currentFocus
        currentFocus = when (current) {
            is ModalFocusState.IssueType -> {
                val max = IssueType.entries.size - 1
                if (current.index < max) {
                    ModalFocusState.IssueType(current.index + 1)
                } else {
                    // Always start with "None" option (index -1)
                    ModalFocusState.PrecannedOption(-1)
                }
            }
            is ModalFocusState.Season -> ModalFocusState.Episode
            is ModalFocusState.Episode -> ModalFocusState.IssueType(0)
            is ModalFocusState.PrecannedOption -> {
                if (current.index == -1) {
                    // Move from "None" to first precanned option (if any)
                    if (currentPrecanned.isNotEmpty()) {
                        ModalFocusState.PrecannedOption(0)
                    } else {
                        ModalFocusState.CustomDescription
                    }
                } else if (current.index < currentPrecanned.size - 1) {
                    ModalFocusState.PrecannedOption(current.index + 1)
                } else {
                    ModalFocusState.CustomDescription
                }
            }
            is ModalFocusState.CustomDescription -> ModalFocusState.CancelButton
            is ModalFocusState.CancelButton -> ModalFocusState.SubmitButton
            is ModalFocusState.SubmitButton -> ModalFocusState.SubmitButton
        }
    }
    
    fun handleLeftKey() {
        val current = currentFocus
        currentFocus = when (current) {
            is ModalFocusState.Season -> {
                selectedSeason = (selectedSeason - 1).coerceAtLeast(1)
                current
            }
            is ModalFocusState.Episode -> {
                val currentEpisodeInt = selectedEpisode.toIntOrNull() ?: 1
                selectedEpisode = (currentEpisodeInt - 1).coerceAtLeast(1).toString()
                current
            }
            is ModalFocusState.CancelButton -> ModalFocusState.SubmitButton
            is ModalFocusState.SubmitButton -> ModalFocusState.CancelButton
            else -> current
        }
    }
    
    fun handleRightKey() {
        val current = currentFocus
        currentFocus = when (current) {
            is ModalFocusState.Season -> {
                selectedSeason += 1
                current
            }
            is ModalFocusState.Episode -> {
                val currentEpisodeInt = selectedEpisode.toIntOrNull() ?: 1
                selectedEpisode = (currentEpisodeInt + 1).toString()
                current
            }
            is ModalFocusState.CancelButton -> ModalFocusState.SubmitButton
            is ModalFocusState.SubmitButton -> ModalFocusState.CancelButton
            else -> current
        }
    }
    
    private fun handleEnterKey() {
        val current = currentFocus
        when (current) {
            is ModalFocusState.IssueType -> {
                val types = IssueType.entries.toTypedArray()
                val idx = current.index.coerceIn(0, types.size - 1)
                // When changing section, default-select top common issue
                selectedIssueType = types[idx].value
                selectedPrecanned = 0
            }
            is ModalFocusState.Season -> {
                // No-op on Enter; use Left/Right to adjust for TV UX
            }
            is ModalFocusState.Episode -> {
                // No-op on Enter; use Left/Right to adjust for TV UX
            }
            is ModalFocusState.PrecannedOption -> {
                selectedPrecanned = current.index
            }
            is ModalFocusState.CustomDescription -> {
                // Focus is already on the text field
            }
            is ModalFocusState.CancelButton -> {
                onDismiss()
            }
            is ModalFocusState.SubmitButton -> {
                submitIssue()
            }
        }
    }

    // Public setter used by UI when selecting type via radio click
    fun setIssueTypeIndex(index: Int) {
        val types = IssueType.entries.toTypedArray()
        val idx = index.coerceIn(0, types.size - 1)
        selectedIssueType = types[idx].value
        selectedPrecanned = -1 // Reset to "None" when issue type changes
    }
    
    private fun getCurrentPrecannedIssues() = when (IssueType.entries.find { it.value == selectedIssueType }) {
        IssueType.VIDEO -> videoIssues
        IssueType.AUDIO -> audioIssues
        IssueType.SUBTITLE -> subtitleIssues
        IssueType.OTHER -> otherIssues
        null -> emptyList()
    }
    
    fun submitIssue() {
        val currentPrecanned = getCurrentPrecannedIssues()
        val precannedText = if (selectedPrecanned >= 0 && selectedPrecanned < currentPrecanned.size) {
            // Resolve string resource to actual text
            resolveString(currentPrecanned[selectedPrecanned].descriptionResId)
        } else ""

        val customText = customDescription.trim()

        val description = when {
            precannedText.isNotBlank() && customText.isNotBlank() -> "$precannedText - $customText"
            precannedText.isNotBlank() -> precannedText
            else -> customText
        }
        
        if (description.isNotBlank()) {
            val request = CreateIssueRequest(
                issueType = selectedIssueType,
                message = description,
                mediaId = 0, // This will be overridden by the caller with the correct mediaId
                problemSeason = selectedSeason,
                problemEpisode = if (selectedEpisode == "All Episodes") 
                    kotlinx.serialization.json.JsonPrimitive("All Episodes") 
                else 
                    kotlinx.serialization.json.JsonPrimitive(selectedEpisode)
            )
            onSubmit(request)
        }
    }
}

class IssueDetailsModalController(
    private val onDismiss: () -> Unit,
    private val onCreateNewIssue: () -> Unit,
    private val onOpenAddComment: (ca.devmesh.seerrtv.model.Issue) -> Unit,
    private val lazyListState: LazyListState,
    private val coroutineScope: CoroutineScope,
    private val canCreateIssues: Boolean
) {
    var selectedIssueIndex by mutableIntStateOf(0)
    var currentFocus by mutableStateOf<IssueDetailsFocusState>(IssueDetailsFocusState.IssueList)
    var issueCount by mutableIntStateOf(0)
    
    fun updateIssueCount(count: Int) {
        issueCount = count
        // Reset selected index if it's out of bounds
        if (selectedIssueIndex >= count) {
            selectedIssueIndex = maxOf(0, count - 1)
        }
    }
    
    fun handleBack() {
        onDismiss()
    }

    fun handleUpKey() {
        when (currentFocus) {
            is IssueDetailsFocusState.IssueList -> {
                if (selectedIssueIndex > 0) {
                    selectedIssueIndex--
                    scrollToIssue(selectedIssueIndex)
                }
            }
            is IssueDetailsFocusState.CancelButton -> {
                currentFocus = IssueDetailsFocusState.IssueList
                scrollToIssue(selectedIssueIndex)
            }
            is IssueDetailsFocusState.NewIssueButton -> {
                if (canCreateIssues) {
                    currentFocus = IssueDetailsFocusState.CancelButton
                }
            }
        }
    }
    
    fun handleDownKey() {
        when (currentFocus) {
            is IssueDetailsFocusState.IssueList -> {
                // Check if we can move down within the issue list
                if (selectedIssueIndex < issueCount - 1) {
                    selectedIssueIndex++
                    scrollToIssue(selectedIssueIndex)
                } else {
                    // Move to Cancel button when at the end of the list
                    currentFocus = IssueDetailsFocusState.CancelButton
                    scrollToBottom()
                }
            }
            is IssueDetailsFocusState.CancelButton -> {
                // Move to New Issue button only if user has permission to create issues
                if (canCreateIssues) {
                    currentFocus = IssueDetailsFocusState.NewIssueButton
                }
                // Otherwise stay on Cancel button
            }
            is IssueDetailsFocusState.NewIssueButton -> {
                // Stay on New Issue button
            }
        }
    }
    
    fun handleLeftKey() {
        when (currentFocus) {
            is IssueDetailsFocusState.CancelButton -> {
                if (canCreateIssues) {
                    currentFocus = IssueDetailsFocusState.NewIssueButton
                }
            }
            is IssueDetailsFocusState.NewIssueButton -> {
                currentFocus = IssueDetailsFocusState.CancelButton
            }
            else -> {
                // No left/right navigation for issue list
            }
        }
    }
    
    fun handleRightKey() {
        when (currentFocus) {
            is IssueDetailsFocusState.CancelButton -> {
                if (canCreateIssues) {
                    currentFocus = IssueDetailsFocusState.NewIssueButton
                }
            }
            is IssueDetailsFocusState.NewIssueButton -> {
                currentFocus = IssueDetailsFocusState.CancelButton
            }
            else -> {
                // No left/right navigation for issue list
            }
        }
    }

    fun handleIssueSelection(issue: ca.devmesh.seerrtv.model.Issue) {
        Log.d("IssueDetailsModalController", "🎯 Issue selection triggered for issue: ${issue.id}")
        onOpenAddComment(issue)
    }

    // Public helper to trigger New Issue from UI without exposing private handlers
    fun triggerCreateNewIssue() {
        onCreateNewIssue()
    }
    
    private fun scrollToIssue(index: Int) {
        coroutineScope.launch {
            lazyListState.animateScrollToItem(index)
        }
    }
    
    private fun scrollToBottom() {
        coroutineScope.launch {
            lazyListState.animateScrollToItem(lazyListState.layoutInfo.totalItemsCount - 1)
        }
    }

}

sealed class IssueDetailsFocusState {
    object IssueList : IssueDetailsFocusState()
    object NewIssueButton : IssueDetailsFocusState()
    object CancelButton : IssueDetailsFocusState()
}

// Modal focus state management
sealed class ModalFocusState {
    data class IssueType(val index: Int) : ModalFocusState()
    object Season : ModalFocusState()
    object Episode : ModalFocusState()
    data class PrecannedOption(val index: Int) : ModalFocusState()
    object CustomDescription : ModalFocusState()
    object CancelButton : ModalFocusState()
    object SubmitButton : ModalFocusState()
}
