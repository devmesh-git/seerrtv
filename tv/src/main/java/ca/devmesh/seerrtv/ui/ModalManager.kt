package ca.devmesh.seerrtv.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Centralized modal manager for MediaDetails. Phase 2 introduces this behind a feature flag.
 * Mirrors existing modal flags and preserves tier context and focus restoration data.
 */
@Stable
class ModalManager {
    // Visibility flags mirroring existing behavior
    var showRequestModal by mutableStateOf(false)
    var showRequestActionModal by mutableStateOf(false)
    var showIssueReport by mutableStateOf(false)
    var showIssueDetails by mutableStateOf(false)

    // Context needed across modals
    var is4kRequest by mutableStateOf(false)
    var tierTypeLabel by mutableStateOf<String?>(null) // Placeholder for tier context until unified types are introduced

    // Focus restoration
    var lastFocusedArea by mutableIntStateOf(-1)

    fun openIssueReport(previousFocusArea: Int) {
        lastFocusedArea = previousFocusArea
        showIssueReport = true
    }

    fun openIssueDetails(previousFocusArea: Int) {
        lastFocusedArea = previousFocusArea
        showIssueDetails = true
    }

    fun closeIssueReport() {
        showIssueReport = false
    }

    fun closeIssueDetails() {
        showIssueDetails = false
    }

    fun openRequest(previousFocusArea: Int, is4k: Boolean, tierLabel: String?) {
        lastFocusedArea = previousFocusArea
        is4kRequest = is4k
        tierTypeLabel = tierLabel
        showRequestModal = true
    }

    fun openRequestAction(previousFocusArea: Int, is4k: Boolean, tierLabel: String?) {
        lastFocusedArea = previousFocusArea
        is4kRequest = is4k
        tierTypeLabel = tierLabel
        showRequestActionModal = true
    }

    fun closeRequest() {
        showRequestModal = false
    }

    fun closeRequestAction() {
        showRequestActionModal = false
    }

}


