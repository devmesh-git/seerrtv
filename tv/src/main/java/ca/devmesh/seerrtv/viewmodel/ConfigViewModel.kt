package ca.devmesh.seerrtv.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.data.SeerrApiService.ApiValidationResult
import ca.devmesh.seerrtv.data.SeerrApiService.SeerrConfig
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    val apiService: SeerrApiService
) : ViewModel() {
    private var backupConfig: SeerrConfig? = null

    fun backupCurrentConfig() {
        Log.d("ConfigViewModel", "Backing up current configuration")
        backupConfig = apiService.getConfig()
    }

    suspend fun restoreBackupConfig(): Boolean {
        Log.d("ConfigViewModel", "Restoring backup configuration")
        return backupConfig?.let { config ->
            // First restore the config in SharedPreferences
            SharedPreferencesUtil.saveConfig(context, config, true)
            // Then update the API service with the backup config
            apiService.updateConfig(config)
            // Re-validate to ensure auth state is restored
            when (val result = apiService.validateApi()) {
                is ApiValidationResult.Success -> {
                    Log.d("ConfigViewModel", "Backup configuration restored and validated successfully")
                }
                is ApiValidationResult.Error -> {
                    Log.e("ConfigViewModel", "Failed to validate restored config: ${result.message}")
                }
                is ApiValidationResult.CloudflareRequired -> {
                    Log.e("ConfigViewModel", "Cloudflare required after restore: ${result.message}")
                }
            }
            true
        } ?: false
    }

    fun clearBackupConfig() {
        Log.d("ConfigViewModel", "Clearing backup configuration")
        backupConfig = null
    }

    suspend fun testBaseConnection(config: SeerrConfig): ApiValidationResult {
        // Update our current configuration
        apiService.updateConfig(config)
        // Test the connection
        val result = apiService.testBaseConnection()
        // Return the ApiValidationResult
        return result
    }

    suspend fun validateAndSaveConfig(config: SeerrConfig, useBrowserValidation: Boolean = false): ApiValidationResult {
        Log.d("ConfigViewModel", "Validating and saving new configuration")
        apiService.updateConfig(config)
        return when (val result = if (useBrowserValidation) apiService.validateApi() else apiService.testAuth()) {
            is ApiValidationResult.Success -> {
                try {
                    // Only update the main API service and save config if validation succeeds
                    SharedPreferencesUtil.saveConfig(context, config, true)
                    Log.d("ConfigViewModel", "Configuration saved and validated successfully")
                    // Load Radarr and Sonarr data for the new configuration
                    apiService.loadRadarrConfiguration()
                    apiService.loadSonarrConfiguration()
                    // Clear backup since we've successfully saved a new config
                    clearBackupConfig()
                    result
                } catch (e: Exception) {
                    Log.e("ConfigViewModel", "Failed to update configuration", e)
                    ApiValidationResult.Error("Failed to update configuration: ${e.message}")
                }
            }
            is ApiValidationResult.Error -> {
                Log.e("ConfigViewModel", "Configuration validation failed: ${result.message}")
                result
            }
            is ApiValidationResult.CloudflareRequired -> {
                Log.e("ConfigViewModel", "Cloudflare required: ${result.message}")
                result
            }
        }
    }

    suspend fun requestPlexPin(clientId: String): SeerrApiService.PlexPinResponse {
        when (val result = apiService.requestPlexPin(clientId)) {
            is ApiResult.Success -> return result.data
            is ApiResult.Error -> throw result.exception
            is ApiResult.Loading -> throw IllegalStateException("Unexpected loading state")
        }
    }

    suspend fun checkPlexPinAuth(pinId: String, clientId: String): SeerrApiService.PlexAuthResponse {
        when (val result = apiService.checkPlexPinAuth(pinId, clientId)) {
            is ApiResult.Success -> return result.data
            is ApiResult.Error -> throw result.exception
            is ApiResult.Loading -> throw IllegalStateException("Unexpected loading state")
        }
    }
} 