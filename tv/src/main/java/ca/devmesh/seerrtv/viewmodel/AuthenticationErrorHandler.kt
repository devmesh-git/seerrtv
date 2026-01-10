package ca.devmesh.seerrtv.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun ViewModel.handleApiError(error: Exception, statusCode: Int? = null): Boolean {
    // First check if it's a serialization error - not an auth error
    if (error is kotlinx.serialization.SerializationException ||
        error.message?.contains("Unexpected JSON token") == true) {
        return false
    }
    
    // Check for connection errors - not auth errors
    if (error is java.net.ConnectException || 
        error is java.net.SocketTimeoutException ||
        error is java.net.UnknownHostException ||
        error.message?.contains("Failed to connect") == true ||
        error.message?.contains("Connection refused") == true ||
        error.message?.contains("timed out") == true ||
        error.message?.contains("Unable to resolve host") == true) {
        return false
    }
    
    return when {
        statusCode == 403 || 
        statusCode == 401 ||
        error.message?.contains("403") == true || 
        error.message?.contains("401") == true ||
        error.message?.contains("Access denied") == true -> {
            true // Authentication error
        }
        else -> false
    }
}

class AuthenticationErrorState {
    private val _showAuthenticationError = MutableStateFlow(false)
    val showAuthenticationError: StateFlow<Boolean> = _showAuthenticationError

    fun showError() {
        _showAuthenticationError.value = true
    }

    fun hideError() {
        _showAuthenticationError.value = false
    }
} 