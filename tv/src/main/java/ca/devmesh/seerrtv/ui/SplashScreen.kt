package ca.devmesh.seerrtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.ui.components.AppLogo
import ca.devmesh.seerrtv.ui.components.VersionNumber
import ca.devmesh.seerrtv.util.UpdateInfo
import ca.devmesh.seerrtv.LoadingStep
import ca.devmesh.seerrtv.LoadingStepType
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf

@Composable
fun SplashScreen(
    errorMessage: String? = null,
    updateJsonUrl: String = "https://release.devmesh.ca/u/update.json",
    onContinue: () -> Unit = {},
    isAuthenticationComplete: Boolean = false,
    isConfigured: Boolean = false,
    loadingSteps: List<LoadingStep> = emptyList(),
    apiValidationError: String? = null,
    showUpdateDialog: Boolean = false,
    updateInfoForDialog: UpdateInfo? = null,
    onUpdateDialogClose: () -> Unit = {}
) {
    
    var allStatusMessages by remember { mutableStateOf<List<LoadingStep>>(emptyList()) }
    var lastLoggedLoadingStepsSize by remember { mutableIntStateOf(0) }
    
    // Log new loading steps as they're added
    LaunchedEffect(loadingSteps.size) {
        if (loadingSteps.size > lastLoggedLoadingStepsSize) {
            val newSteps = loadingSteps.takeLast(loadingSteps.size - lastLoggedLoadingStepsSize)
            newSteps.forEach { step ->
                Log.d("SplashScreen", "New loading step: [${step.type}] ${step.message}")
            }
            lastLoggedLoadingStepsSize = loadingSteps.size
        }
    }
    
    // Update the message list when errorMessage changes
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            Log.d("SplashScreen", "Adding error message: $errorMessage")
            allStatusMessages = allStatusMessages + LoadingStep(errorMessage, LoadingStepType.ERROR)
        }
    }
    
    // Update the message list when apiValidationError changes
    LaunchedEffect(apiValidationError) {
        if (apiValidationError != null) {
            Log.d("SplashScreen", "Adding API validation error: $apiValidationError")
            allStatusMessages = allStatusMessages + LoadingStep(apiValidationError, LoadingStepType.ERROR)
        }
    }
    val context = LocalContext.current

    // For direct flavor with no update: call onContinue when authentication completes OR when no config exists
    LaunchedEffect(isAuthenticationComplete, showUpdateDialog) {
        if (BuildConfig.IS_DIRECT_FLAVOR && !showUpdateDialog) {
            // Call onContinue if authentication is complete OR if we're not configured (no auth needed)
            val shouldContinue = isAuthenticationComplete || !isConfigured
            if (shouldContinue) {
                onContinue()
            }
        }
    }


    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AppLogo(
            imageHeight = 150,
            modifier = Modifier.align(Alignment.Center)
        )
        
        // Version number positioned at bottom left
        VersionNumber(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Activity log centered at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Scrolling component centered at the bottom
            val scrollState = rememberScrollState()
            val coroutineScope = rememberCoroutineScope()
            
            // Auto-scroll to bottom when content changes
            LaunchedEffect(allStatusMessages.size, loadingSteps.size, apiValidationError) {
                coroutineScope.launch {
                    // Small delay to ensure content is rendered before scrolling
                    kotlinx.coroutines.delay(50)
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Collect all messages to show only the last 5
                val allMessages = mutableListOf<LoadingStep>()
                
                // Add completed loading steps with their specified types
                loadingSteps.forEach { step ->
                    allMessages.add(step)
                }
                
                // Add accumulated status messages
                allMessages.addAll(allStatusMessages)
                
                // Add current error messages if any
                // Note: Update errors are now handled in MainActivity
                
                // Show only the last 5 messages (newest at bottom)
                val messagesToShow = allMessages.takeLast(5)
                
                // Reverse the order so newest appears at bottom (position 5)
                messagesToShow.forEach { message ->
                    val color = when (message.type) {
                        LoadingStepType.SUCCESS -> Color.Green
                        LoadingStepType.ERROR -> Color.Red
                        LoadingStepType.WARNING -> Color.Yellow
                        LoadingStepType.INFO -> Color.White
                    }
                    
                    Text(
                        text = message.message,
                        color = color,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }

        // Show update dialog if update is available
        if (BuildConfig.IS_DIRECT_FLAVOR && showUpdateDialog && updateInfoForDialog != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                UpdateAvailableDialog(
                    context = context,
                    updateInfo = updateInfoForDialog,
                    updateJsonUrl = updateJsonUrl,
                    onClose = onUpdateDialogClose
                )
            }
        }
    }
}