package ca.devmesh.seerrtv.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ca.devmesh.seerrtv.util.UpdateDownloadCallback
import ca.devmesh.seerrtv.util.UpdateInfo
import ca.devmesh.seerrtv.util.UpdateManager
import ca.devmesh.seerrtv.ui.components.CustomButton
import ca.devmesh.seerrtv.ui.KeyUtils
import java.io.File
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*

@Composable
fun UpdateAvailableDialog(
    context: Context,
    updateInfo: UpdateInfo,
    updateJsonUrl: String,
    onClose: () -> Unit
) {
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val updateManager = remember { UpdateManager(context, updateJsonUrl) }

    // Focus management for TV remote
    var focusedButton by remember { mutableStateOf(0) } // 0 = Update Now, 1 = Close
    val updateNowFocusRequester = remember { FocusRequester() }
    val closeFocusRequester = remember { FocusRequester() }
    var isHandlingEnter by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .padding(32.dp)
            .widthIn(max = 500.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .onKeyEvent { keyEvent ->
                    when {
                        keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                            focusedButton = if (focusedButton == 0) 1 else 0 // cycle
                            true
                        }
                        keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                            focusedButton = if (focusedButton == 1) 0 else 1 // cycle
                            true
                        }
                        KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyDown -> {
                            if (!isHandlingEnter) {
                                isHandlingEnter = true
                                if (focusedButton == 0) {
                                    if (!downloading) {
                                        downloading = true
                                        errorMessage = null
                                        updateManager.downloadUpdate(updateInfo, object : UpdateDownloadCallback {
                                            override fun onProgress(percent: Int) {
                                                progress = percent
                                            }
                                            override fun onDownloaded(apkFile: File) {
                                                downloading = false
                                                updateManager.promptInstall(apkFile)
                                            }
                                            override fun onError(error: String) {
                                                errorMessage = error
                                                downloading = false
                                            }
                                        })
                                    }
                                } else {
                                    onClose()
                                }
                            }
                            true
                        }
                        KeyUtils.isEnterKey(keyEvent.nativeKeyEvent.keyCode) && keyEvent.type == KeyEventType.KeyUp -> {
                            isHandlingEnter = false
                            true
                        }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LaunchedEffect(focusedButton) {
                if (focusedButton == 0) {
                    updateNowFocusRequester.requestFocus()
                } else {
                    closeFocusRequester.requestFocus()
                }
                isHandlingEnter = false
            }
            Text(
                text = "Update Available",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Version: v${updateInfo.latestVersionName}",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (downloading) {
                LinearProgressIndicator(progress = { progress / 100f })
                Text("Downloading: $progress%", color = Color.White)
            }
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CustomButton(
                    onClick = {
                        if (!downloading) {
                            downloading = true
                            errorMessage = null
                            updateManager.downloadUpdate(updateInfo, object : UpdateDownloadCallback {
                                override fun onProgress(percent: Int) {
                                    progress = percent
                                }
                                override fun onDownloaded(apkFile: File) {
                                    downloading = false
                                    updateManager.promptInstall(apkFile)
                                }
                                override fun onError(error: String) {
                                    errorMessage = error
                                    downloading = false
                                }
                            })
                        }
                    },
                    isFocused = focusedButton == 0,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(updateNowFocusRequester)
                        .onFocusChanged { if (it.isFocused) focusedButton = 0 }
                ) {
                    Text("Update Now", color = Color.White)
                }
                CustomButton(
                    onClick = onClose,
                    isFocused = focusedButton == 1,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(closeFocusRequester)
                        .onFocusChanged { if (it.isFocused) focusedButton = 1 }
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
} 