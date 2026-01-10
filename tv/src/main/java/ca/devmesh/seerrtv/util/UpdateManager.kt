package ca.devmesh.seerrtv.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.data.TrustAllCerts
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.util.Log

/**
 * Data class representing update information from GitHub Releases API.
 */
data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
)

/**
 * Callback interface for update check results.
 */
interface UpdateCheckCallback {
    fun onUpdateAvailable(updateInfo: UpdateInfo)
    fun onNoUpdate()
    fun onError(error: String)
}

/**
 * Callback interface for update download progress and result.
 */
interface UpdateDownloadCallback {
    fun onProgress(percent: Int)
    fun onDownloaded(apkFile: File)
    fun onError(error: String)
}

/**
 * Manager for handling update checks, downloads, and installation for sideloaded APKs.
 * This class is only active in the 'direct' flavor (BuildConfig.IS_DIRECT_FLAVOR).
 */
class UpdateManager(
    private val context: Context,
    private val updateJsonUrl: String
) {
    private val client = OkHttpClient.Builder()
        .sslSocketFactory(TrustAllCerts.createSSLSocketFactory()!!, TrustAllCerts.trustAllCerts[0])
        .hostnameVerifier { _, _ -> true }
        .build()
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Checks for an available update by fetching from GitHub Releases API.
     * Calls the appropriate callback method based on the result.
     */
    fun checkForUpdate(callback: UpdateCheckCallback) {
        if (!BuildConfig.IS_DIRECT_FLAVOR) {
            Log.d("UpdateManager", "Not direct flavor, skipping update check.")
            callback.onNoUpdate()
            return
        }
        Log.d("UpdateManager", "Checking for update from $updateJsonUrl")
        executor.execute {
            try {
                val request = Request.Builder().url(updateJsonUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e("UpdateManager", "Failed to fetch update info: HTTP ${response.code}")
                    callback.onError("Failed to fetch update info: HTTP ${response.code}")
                    return@execute
                }
                val body = response.body.string()
                if (body.isEmpty()) {
                    Log.e("UpdateManager", "Empty response from GitHub API")
                    callback.onError("Empty response from GitHub API")
                    return@execute
                }
                Log.d("UpdateManager", "GitHub release JSON: $body")
                
                val updateInfo = parseGitHubReleaseResponse(body)
                
                if (updateInfo == null) {
                    Log.e("UpdateManager", "Failed to parse GitHub release response")
                    callback.onError("Failed to parse GitHub release response")
                    return@execute
                }
                
                // Compare versionName semantically
                val currentVersionName = getCurrentVersionName()
                val hasUpdate = compareVersions(updateInfo.latestVersionName, currentVersionName) > 0
                Log.d("UpdateManager", "Current version name: $currentVersionName, Latest: ${updateInfo.latestVersionName}, Update available: $hasUpdate")
                
                if (hasUpdate) {
                    Log.d("UpdateManager", "Update available: ${updateInfo.latestVersionName}")
                    callback.onUpdateAvailable(updateInfo)
                } else {
                    Log.d("UpdateManager", "No update available.")
                    callback.onNoUpdate()
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Update check failed: ${e.localizedMessage}", e)
                callback.onError("Update check failed: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * Parses GitHub Releases API response format.
     * Extracts version from tag_name, finds APK asset, and derives versionCode from versionName.
     */
    private fun parseGitHubReleaseResponse(body: String): UpdateInfo? {
        return try {
            val json = JSONObject(body)
            val tagName = json.getString("tag_name")
            // Remove 'v' prefix if present (e.g., "v0.26.3" -> "0.26.3")
            val versionName = tagName.removePrefix("v")
            
            // Find the APK asset
            val assetsArray = json.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.getJSONObject(i)
                val assetName = asset.getString("name")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.getString("browser_download_url")
                    Log.d("UpdateManager", "Found APK asset: $assetName -> $apkUrl")
                    break
                }
            }
            
            if (apkUrl == null) {
                Log.e("UpdateManager", "No APK asset found in GitHub release")
                return null
            }
            
            // Derive versionCode from versionName by parsing semantic version
            // For now, we'll use a simple conversion: 0.26.3 -> 26 * 1000 + 3 = 26003
            // This allows for versions up to 99.99.999
            val versionCode = parseVersionCodeFromVersionName(versionName)
            
            UpdateInfo(
                latestVersionCode = versionCode,
                latestVersionName = versionName,
                apkUrl = apkUrl
            )
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to parse GitHub release response: ${e.localizedMessage}", e)
            null
        }
    }
    
    /**
     * Converts semantic version string (e.g., "0.26.3") to a version code integer.
     * Format: major * 1000000 + minor * 1000 + patch
     * Examples: "0.26.3" -> 26003, "1.2.3" -> 1002003
     * Note: This versionCode is only used for the UpdateInfo data class and is not used for comparison.
     */
    private fun parseVersionCodeFromVersionName(versionName: String): Int {
        return try {
            val parts = versionName.split(".")
            val major = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
            val minor = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
            val patch = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
            major * 1000000 + minor * 1000 + patch
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to parse version code from version name: $versionName", e)
            0
        }
    }
    
    private fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (_: PackageManager.NameNotFoundException) {
            "0.0.0"
        }
    }
    
    /**
     * Compares two semantic version strings.
     * Returns: > 0 if v1 > v2, < 0 if v1 < v2, 0 if v1 == v2
     * Examples: compareVersions("0.26.3", "0.26.2") returns > 0
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLength) {
            val part1 = parts1.getOrElse(i) { 0 }
            val part2 = parts2.getOrElse(i) { 0 }
            
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        
        return 0
    }

    /**
     * Downloads the APK from the provided URL and reports progress.
     * Calls the appropriate callback method on completion or error.
     */
    fun downloadUpdate(updateInfo: UpdateInfo, callback: UpdateDownloadCallback) {
        if (!BuildConfig.IS_DIRECT_FLAVOR) {
            Log.d("UpdateManager", "Not direct flavor, skipping download.")
            callback.onError("Update functionality not available")
            return
        }
        Log.d("UpdateManager", "Starting APK download from ${updateInfo.apkUrl}")
        executor.execute {
            try {
                val request = Request.Builder().url(updateInfo.apkUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e("UpdateManager", "Failed to download APK: HTTP ${response.code}")
                    callback.onError("Failed to download APK: HTTP ${response.code}")
                    return@execute
                }
                val body = response.body
                val apkFile = File(context.cacheDir, "update_download.apk")
                Log.d("UpdateManager", "Writing APK to ${apkFile.absolutePath}")
                if (!writeResponseBodyToDisk(body, apkFile, callback)) {
                    Log.e("UpdateManager", "Failed to save APK file")
                    callback.onError("Failed to save APK file")
                    return@execute
                }
                Log.d("UpdateManager", "APK downloaded successfully: ${apkFile.absolutePath}")
                callback.onDownloaded(apkFile)
            } catch (e: Exception) {
                Log.e("UpdateManager", "APK download failed: ${e.localizedMessage}", e)
                callback.onError("APK download failed: ${e.localizedMessage}")
            }
        }
    }

    private fun writeResponseBodyToDisk(body: ResponseBody, file: File, callback: UpdateDownloadCallback): Boolean {
        Log.d("UpdateManager", "Writing response body to disk at ${file.absolutePath}")
        return try {
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(file)
            val fileSize = body.contentLength()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloaded: Long = 0
            var read: Int
            var lastProgress = 0
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                downloaded += read
                if (fileSize > 0) {
                    val progress = (downloaded * 100 / fileSize).toInt()
                    if (progress != lastProgress) {
                        Log.d("UpdateManager", "Download progress: $progress% ($downloaded/$fileSize bytes)")
                        callback.onProgress(progress)
                        lastProgress = progress
                    }
                }
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            Log.d("UpdateManager", "Finished writing APK to disk.")
            true
        } catch (e: IOException) {
            Log.e("UpdateManager", "IOException while writing APK: ${e.localizedMessage}", e)
            false
        }
    }

    /**
     * Prompts the user to install the downloaded APK using an intent.
     * Uses FileProvider for Android N+ and sets the correct intent flags.
     * Ensure the provider is declared in the manifest for the direct flavor.
     */
    fun promptInstall(apkFile: File) {
        if (!BuildConfig.IS_DIRECT_FLAVOR) {
            Log.d("UpdateManager", "Not direct flavor, skipping install prompt.")
            return
        }
        Log.d("UpdateManager", "Prompting user to install APK: ${apkFile.absolutePath}")
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}

// Shared suspend function to check for update
suspend fun checkForUpdateIfAvailable(context: Context, updateJsonUrl: String): UpdateInfo? = suspendCancellableCoroutine { cont ->
    val updateManager = UpdateManager(context, updateJsonUrl)
    updateManager.checkForUpdate(object : UpdateCheckCallback {
        override fun onUpdateAvailable(updateInfo: UpdateInfo) {
            cont.resume(updateInfo)
        }
        override fun onNoUpdate() {
            cont.resume(null)
        }
        override fun onError(error: String) {
            cont.resume(null)
        }
    })
} 