package ca.devmesh.seerrtv.ui

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

private const val TAG = "TrailerOverlay"
private const val SCRUB_SECONDS = 10f

@Composable
fun TrailerOverlay(
    videoId: String,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentVideoId by rememberUpdatedState(videoId)
    val playerViewRef = remember { mutableStateOf<YouTubePlayerView?>(null) }
    val youTubePlayerRef = remember { mutableStateOf<YouTubePlayer?>(null) }
    var currentSecond by remember { mutableStateOf(0f) }
    var videoDuration by remember { mutableStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var loadState by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var hasInitialized by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose {
            youTubePlayerRef.value = null
            playerViewRef.value?.release()
            playerViewRef.value = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (event.key == Key.Back) {
                    onClose()
                    return@onKeyEvent true
                }
                val player = youTubePlayerRef.value
                when (event.key) {
                    Key.DirectionLeft -> {
                        player?.seekTo((currentSecond - SCRUB_SECONDS).coerceAtLeast(0f))
                        true
                    }
                    Key.DirectionRight -> {
                        val target = currentSecond + SCRUB_SECONDS
                        player?.seekTo(if (videoDuration > 0f) target.coerceIn(0f, videoDuration) else target)
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        if (player != null) {
                            if (isPlaying) player.pause() else player.play()
                            true
                        } else {
                            @Suppress("DEPRECATION")
                            event.nativeKeyEvent?.let { playerViewRef.value?.dispatchKeyEvent(it) } ?: false
                        }
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        event.nativeKeyEvent?.let { playerViewRef.value?.dispatchKeyEvent(it) } ?: false
                    }
                }
            }
    ) {
        AndroidView(
            factory = { context ->
                YouTubePlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    enableAutomaticInitialization = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            update = { youTubePlayerView ->
                playerViewRef.value = youTubePlayerView
                if (!hasInitialized) {
                    hasInitialized = true
                    lifecycleOwner.lifecycle.addObserver(youTubePlayerView)
                    youTubePlayerView.initialize(
                        object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                youTubePlayerRef.value = youTubePlayer
                                youTubePlayer.loadVideo(currentVideoId, 0f)
                                loadState = LoadState.Loaded
                                Log.d(TAG, "YouTube player ready, loaded video $currentVideoId")
                            }
                            override fun onStateChange(
                                youTubePlayer: YouTubePlayer,
                                state: PlayerConstants.PlayerState
                            ) {
                                isPlaying = state == PlayerConstants.PlayerState.PLAYING
                            }
                            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                                currentSecond = second
                            }
                            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                                videoDuration = duration
                            }
                            override fun onError(
                                youTubePlayer: YouTubePlayer,
                                error: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError
                            ) {
                                loadState = LoadState.Error
                                Log.w(TAG, "YouTube player error: $error")
                            }
                        },
                        true
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        when (loadState) {
            LoadState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White
                    )
                }
            }
            LoadState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Red, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(32.dp)) {
                            val w = size.width
                            val h = size.height
                            val stroke = 4.dp.toPx()
                            drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = stroke)
                            drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = stroke)
                        }
                    }
                }
            }
            LoadState.Loaded -> { /* player visible */ }
        }
    }
}

private enum class LoadState { Loading, Loaded, Error }
