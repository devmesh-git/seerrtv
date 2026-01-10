package ca.devmesh.seerrtv.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.devmesh.seerrtv.BuildConfig
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.model.DownloadStatus
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.MediaInfo
import ca.devmesh.seerrtv.R
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    imageHeight: Int = 200
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher),
            contentDescription = stringResource(id = R.string.configScreen_seerrIcon),
            modifier = Modifier
                .height(imageHeight.dp)
                .fillMaxWidth(),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color.White)) {
                    append("Seerr")
                }
                withStyle(style = SpanStyle(color = Color(0xFF3B4152))) {
                    append("TV")
                }
            },
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun VersionNumber(
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(R.string.common_versionPrefix) + BuildConfig.VERSION_NAME,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 12.sp,
        modifier = modifier
    )
}

@Composable
fun DevMeshBranding(
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Image(
            painter = painterResource(id = R.drawable.devmesh_logo),
            contentDescription = stringResource(R.string.common_devmeshLogo),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "devmesh.ca",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
fun AutoUpdatingHumanizedDate(
    date: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.White,
    isVisible: Boolean = true
) {
    var tick by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    
    LaunchedEffect(date, isVisible) {
        // Only start updating if the component is visible
        if (!isVisible) return@LaunchedEffect
        
        while (true) {
            val now = Instant.now()
            val instant = date?.let { Instant.parse(it) }
            
            if (instant != null) {
                val diffSeconds = now.epochSecond - instant.epochSecond
                val delay = when {
                    diffSeconds < 60 -> 1000L // Update every second if less than a minute
                    diffSeconds < 3600 -> 60000L // Update every minute if less than an hour
                    diffSeconds < 86400 -> 300000L // Update every 5 minutes if less than a day
                    else -> 3600000L // Update every hour for older dates
                }
                delay(delay)
                tick = System.currentTimeMillis()
            } else {
                break // Exit the loop if date is null
            }
        }
    }
    
    // Use derivedStateOf to efficiently compute the humanized date
    val humanizedDate by remember(date, tick, context) {
        derivedStateOf { 
            date?.let { dateString ->
                try {
                    val instant = Instant.parse(dateString)
                    val now = Instant.now()
                    val diffSeconds = now.epochSecond - instant.epochSecond
                    
                    when {
                        diffSeconds < 0 -> context.getString(R.string.time_justNow)
                        diffSeconds == 0L -> context.getString(R.string.time_justNow)
                        diffSeconds == 1L -> context.getString(R.string.time_secondAgo)
                        diffSeconds < 60 -> context.getString(R.string.time_secondsAgo, diffSeconds.toInt())
                        diffSeconds < 120 -> context.getString(R.string.time_minuteAgo)
                        diffSeconds < 3600 -> context.getString(R.string.time_minutesAgo, (diffSeconds / 60).toInt())
                        diffSeconds < 7200 -> context.getString(R.string.time_hourAgo)
                        diffSeconds < 86400 -> context.getString(R.string.time_hoursAgo, (diffSeconds / 3600).toInt())
                        diffSeconds < 172800 -> context.getString(R.string.time_dayAgo)
                        diffSeconds < 2592000 -> context.getString(R.string.time_daysAgo, (diffSeconds / 86400).toInt())
                        diffSeconds < 5184000 -> context.getString(R.string.time_monthAgo)
                        diffSeconds < 31536000 -> context.getString(R.string.time_monthsAgo, (diffSeconds / 2592000).toInt())
                        diffSeconds < 63072000 -> context.getString(R.string.time_yearAgo)
                        else -> context.getString(R.string.time_yearsAgo, (diffSeconds / 31536000).toInt())
                    }
                } catch (_: Exception) {
                    dateString
                }
            } ?: ""
        }
    }
    
    Text(
        text = humanizedDate,
        modifier = modifier,
        style = style,
        color = color
    )
}

// Add companion object with humanizeTime method
object TimeHelper {
    @Composable
    fun humanizeTime(seconds: Long): String {
        // Use the existing time strings from strings.xml
        return when {
            seconds < 60 -> stringResource(id = R.string.time_seconds, seconds.toInt())
            seconds < 3600 -> stringResource(id = R.string.time_minutes, (seconds / 60).toInt())
            seconds < 86400 -> stringResource(id = R.string.time_hours, (seconds / 3600).toInt())
            else -> stringResource(id = R.string.time_days, (seconds / 86400).toInt())
        }
    }
}

/**
 * Creates initial debug data for download status display
 */
private fun createDebugData(result: ApiResult.Success<MediaDetails>, mediaId: String): ApiResult.Success<MediaDetails> {
    val originalData = result.data
    val modifiedMediaInfo = originalData.mediaInfo?.copy(
        status = 3, // Processing status
        downloadStatus = listOf(
            DownloadStatus(
                externalId = 6,
                estimatedCompletionTime = "2025-03-21T18:13:42.000Z",
                mediaType = "movie",
                size = 18595329901,
                sizeLeft = 5792495468,
                status = "downloading",
                timeLeft = "01:19:31",
                title = "Kung Fu Panda 4 2024 2160p Blu ray x265 10bit SDR Org Hindi DDP5 1 English DDP7 1 Atmos NmCT"
            )
        )
    ) ?: MediaInfo(
        // Create new MediaInfo if it was null
        id = 1,
        mediaType = "movie",
        tmdbId = mediaId.toIntOrNull() ?: 0,
        status = 3, // Processing status
        status4k = 0, // Default value for status4k
        createdAt = "", // Empty string for createdAt
        updatedAt = "", // Empty string for updatedAt
        lastSeasonChange = "", // Empty string for lastSeasonChange
        downloadStatus = listOf(
            DownloadStatus(
                externalId = 6,
                estimatedCompletionTime = "2025-03-21T18:13:42.000Z",
                mediaType = "movie",
                size = 18595329901,
                sizeLeft = 5792495468,
                status = "downloading",
                timeLeft = "01:19:31",
                title = "Kung Fu Panda 4 2024 2160p Blu ray x265 10bit SDR Org Hindi DDP5 1 English DDP7 1 Atmos NmCT"
            )
        )
    )
    val modifiedData = originalData.copy(mediaInfo = modifiedMediaInfo)
    return ApiResult.Success(modifiedData)
}

/**
 * Updates existing debug data to simulate download progress
 */
private fun updateDebugProgress(
    newResult: ApiResult.Success<MediaDetails>, 
    currentDetails: ApiResult<MediaDetails>
): ApiResult.Success<MediaDetails> {
    val originalData = newResult.data
    val currentMediaInfo = currentDetails.let {
        if (it is ApiResult.Success) it.data.mediaInfo else null
    }
    
    // If we already have debug media info, update it to simulate progress
    if (currentMediaInfo?.downloadStatus?.isNotEmpty() == true) {
        val currentDownload = currentMediaInfo.downloadStatus.first()
        val newSizeLeft = (currentDownload.sizeLeft ?: 0L) - 200000000 // Decrease by ~200MB
        val updatedDownload = currentDownload.copy(
            sizeLeft = if (newSizeLeft > 0) newSizeLeft else 0,
            timeLeft = if (newSizeLeft > 0) "00:59:31" else "00:00:00"
        )
        
        Log.d("updateDebugProgress", "📊 Progress update: ${currentDownload.sizeLeft} -> $newSizeLeft bytes left (${(newSizeLeft * 100 / currentDownload.size).toInt()}%)")
        
        val updatedMediaInfo = currentMediaInfo.copy(
            downloadStatus = listOf(updatedDownload)
        )
        return ApiResult.Success(originalData.copy(mediaInfo = updatedMediaInfo))
    } else {
        // If we don't have debug media info, create it
        return createDebugData(newResult, originalData.id.toString())
    }
}

@Composable
fun DownloadStatusItem(
    downloadStatus: DownloadStatus,
    modifier: Modifier = Modifier,
    is4K: Boolean = false
) {
    // Force recomposition for any updates to ensure progress changes are visible
    val recomposeKey = remember(downloadStatus) { Object() }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (is4K) Color(0xFF1E3A8A) else Color(0xFF111827))
            .then(
                if (is4K) {
                    Modifier.border(
                        width = 1.dp,
                        color = Color(0xFF60A5FA),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .padding(8.dp)
    ) {
        // Download title with 4K indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = downloadStatus.title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            
            // Show 4K badge if this is a 4K download
            if (is4K) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3B82F6), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "4K",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        
        // Progress bar
        val progress = if (downloadStatus.size > 0) {
            val remaining = downloadStatus.sizeLeft ?: 0L
            val downloaded = downloadStatus.size - remaining
            (downloaded.toFloat() / downloadStatus.size.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val progressPercent = (progress * 100).toInt()
        
        // Log the progress value to verify it's changing
        Log.d("DownloadStatusItem", "Displaying progress: $progressPercent% for download ${downloadStatus.externalId}")
        
        // Progress and percentage row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Progress bar
            val progressBarHeight = 8.dp
            
            // Background for progress
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(progressBarHeight)
                    .background(Color(0xFF374151), RoundedCornerShape(4.dp))
            ) {
                // Foreground progress indicator
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(
                            if (is4K) Color(0xFF60A5FA) else Color(0xFF6964EE),
                            RoundedCornerShape(4.dp)
                        )
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Percentage text
            Text(
                text = "$progressPercent%",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.common_processing),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            
            // Parse timeLeft in format HH:MM:SS
            downloadStatus.timeLeft?.let { timeLeftStr ->
                // Parse the HH:MM:SS format to seconds
                val timeLeftSeconds = try {
                    val parts = timeLeftStr.split(":")
                    if (parts.size == 3) {
                        val hours = parts[0].toLong()
                        val minutes = parts[1].toLong()
                        val seconds = parts[2].toLong()
                        hours * 3600 + minutes * 60 + seconds
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
                
                if (timeLeftSeconds != null && timeLeftSeconds > 0) {
                    // Use resource reference for the string
                    val estimatedText = stringResource(R.string.time_estimatedIn)
                    val timeText = TimeHelper.humanizeTime(timeLeftSeconds)

                    Text(
                        text = "$estimatedText $timeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                } else {
                    // Just show the raw value if we can't parse it
                    Text(
                        text = timeLeftStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        }
    }
} 