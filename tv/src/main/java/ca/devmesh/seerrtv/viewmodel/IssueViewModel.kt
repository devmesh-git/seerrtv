package ca.devmesh.seerrtv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.data.IssueRepository
import ca.devmesh.seerrtv.model.CreateIssueRequest
import ca.devmesh.seerrtv.model.Issue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class IssueViewModel @Inject constructor(
    private val issueRepository: IssueRepository
) : ViewModel() {

    data class SubmitState(
        val isSubmitting: Boolean = false,
        val success: Boolean = false,
        val errorMessage: String? = null,
        val createdIssue: Issue? = null
    )

    private val _submitState = MutableStateFlow(SubmitState())
    val submitState: StateFlow<SubmitState> = _submitState
    private val _commentSubmission = MutableStateFlow<ApiResult<Issue>>(ApiResult.Loading())

    fun submitIssue(request: CreateIssueRequest) {
        _submitState.update { it.copy(isSubmitting = true, success = false, errorMessage = null, createdIssue = null) }
        viewModelScope.launch {
            when (val result = issueRepository.createIssue(request)) {
                is ApiResult.Success -> {
                    _submitState.update { it.copy(isSubmitting = false, success = true, createdIssue = result.data) }
                }
                is ApiResult.Error -> {
                    _submitState.update { it.copy(isSubmitting = false, success = false, errorMessage = result.exception.message) }
                }
                is ApiResult.Loading -> {
                    // no-op
                }
            }
        }
    }

    fun addComment(issueId: Int, message: String) {
        _submitState.update { it.copy(isSubmitting = true, success = false, errorMessage = null, createdIssue = null) }
        _commentSubmission.value = ApiResult.Loading()
        viewModelScope.launch {
            when (val result = issueRepository.addComment(issueId, message)) {
                is ApiResult.Success -> {
                    _submitState.update { it.copy(isSubmitting = false, success = true) }
                    _commentSubmission.value = result
                }
                is ApiResult.Error -> {
                    _submitState.update { it.copy(isSubmitting = false, success = false, errorMessage = result.exception.message) }
                    _commentSubmission.value = result
                }
                is ApiResult.Loading -> {
                    // no-op
                }
            }
        }
    }

    fun resetSubmitState() {
        Log.d("IssueViewModel", "🔄 Resetting submit state")
        _submitState.update { it.copy(isSubmitting = false, success = false, errorMessage = null, createdIssue = null) }
        _commentSubmission.value = ApiResult.Loading()
        Log.d("IssueViewModel", "🔄 Submit state reset complete: ${_submitState.value}")
    }
}


