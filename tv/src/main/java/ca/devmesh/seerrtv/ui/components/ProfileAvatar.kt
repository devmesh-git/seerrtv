package ca.devmesh.seerrtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import ca.devmesh.seerrtv.SeerrTV
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
fun ProfileAvatar(
    modifier: Modifier = Modifier,
    initials: String,
    backgroundColor: Color,
    remoteAvatarUrl: String?,
    contentDescription: String? = null,
    fontSize: TextUnit = MaterialTheme.typography.bodyMedium.fontSize,
    textColor: Color = Color.White
) {
    val context = LocalContext.current
    val letterFallback = initials.take(2).ifBlank { "??" }
    var imageFailed by remember(remoteAvatarUrl) { mutableStateOf(false) }
    val showRemote = !remoteAvatarUrl.isNullOrBlank() && !imageFailed

    Box(
        modifier = modifier.clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor, CircleShape)
        )
        if (showRemote) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(remoteAvatarUrl)
                    .build(),
                imageLoader = SeerrTV.imageLoader,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                onError = { imageFailed = true },
                onSuccess = { imageFailed = false }
            )
        } else {
            Text(
                text = letterFallback,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize
            )
        }
    }
}
