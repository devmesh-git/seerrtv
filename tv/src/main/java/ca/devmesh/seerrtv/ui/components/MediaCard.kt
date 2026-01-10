package ca.devmesh.seerrtv.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import ca.devmesh.seerrtv.model.Collection
import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.model.MediaInfo
import ca.devmesh.seerrtv.model.Movie
import ca.devmesh.seerrtv.model.Person
import ca.devmesh.seerrtv.model.SearchResult
import ca.devmesh.seerrtv.model.TV
import ca.devmesh.seerrtv.R
import coil3.compose.AsyncImage
import coil3.ImageLoader
import coil3.request.ImageRequest

/**
 * Universal media card component that can display any media type:
 * - Movies and TV shows (standard poster layout)
 * - People (circular profile images)
 * - Collections (collection poster layout)
 *
 * @param mediaContent The content to display, can be Media, SearchResult, or specific types
 * @param context Android context for image loading
 * @param imageLoader Coil3 image loader
 * @param isSelected Whether this card is currently selected/focused
 * @param cardHeight Height of the card
 * @param cardWidth Width of the card
 * @param showLabel Whether to show a text label below the card (for discovery screens)
 * @param modifier Optional modifier for the component
 */
@Composable
fun MediaCard(
    modifier: Modifier = Modifier,
    mediaContent: Any,
    context: Context,
    imageLoader: ImageLoader,
    isSelected: Boolean,
    cardHeight: Dp = 185.dp,
    cardWidth: Dp = 134.dp,
    showLabel: Boolean = false
) {
    val scale by animateFloatAsState(if (isSelected) 1.15f else 1.0f, label = "")
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.scale(scale)
    ) {
        when (mediaContent) {
            // Handle Person type specifically
            is Person -> {
                PersonCard(
                    person = mediaContent,
                    context = context,
                    imageLoader = imageLoader,
                    isSelected = isSelected,
                    cardHeight = cardHeight,
                    cardWidth = cardWidth
                )
                
                if (showLabel) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = mediaContent.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 12.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(cardWidth)
                    )
                }
            }
            
            // Handle SearchResult types (Movie, TV, Collection)
            is SearchResult -> {
                when (mediaContent) {
                    is Movie -> {
                        StandardMediaCard(
                            posterPath = mediaContent.posterPath,
                            title = mediaContent.title,
                            mediaType = "movie",
                            mediaInfo = mediaContent.mediaInfo,
                            context = context,
                            imageLoader = imageLoader,
                            isSelected = isSelected,
                            cardHeight = cardHeight,
                            cardWidth = cardWidth
                        )
                        
                        if (showLabel) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mediaContent.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 12.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(cardWidth)
                            )
                        }
                    }
                    
                    is TV -> {
                        StandardMediaCard(
                            posterPath = mediaContent.posterPath,
                            title = mediaContent.name,
                            mediaType = "tv",
                            mediaInfo = mediaContent.mediaInfo,
                            context = context,
                            imageLoader = imageLoader,
                            isSelected = isSelected,
                            cardHeight = cardHeight,
                            cardWidth = cardWidth
                        )
                        
                        if (showLabel) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mediaContent.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 12.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(cardWidth)
                            )
                        }
                    }
                    
                    is Collection -> {
                        StandardMediaCard(
                            posterPath = mediaContent.posterPath,
                            title = mediaContent.name,
                            mediaType = "collection",
                            mediaInfo = mediaContent.mediaInfo,
                            context = context,
                            imageLoader = imageLoader,
                            isSelected = isSelected,
                            cardHeight = cardHeight,
                            cardWidth = cardWidth
                        )
                        
                        if (showLabel) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mediaContent.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 12.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(cardWidth)
                            )
                        }
                    }
                    
                    else -> {
                        // Fallback for unknown search result types
                        EmptyCard(
                            isSelected = isSelected,
                            cardHeight = cardHeight,
                            cardWidth = cardWidth
                        )
                    }
                }
            }
            
            // Handle Media model (backward compatibility)
            is Media -> {
                StandardMediaCard(
                    posterPath = mediaContent.posterPath,
                    title = if (mediaContent.mediaType == "movie") mediaContent.title else mediaContent.name,
                    mediaType = mediaContent.mediaType,
                    mediaInfo = mediaContent.mediaInfo,
                    context = context,
                    imageLoader = imageLoader,
                    isSelected = isSelected,
                    cardHeight = cardHeight,
                    cardWidth = cardWidth
                )
                
                if (showLabel) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (mediaContent.mediaType == "movie") mediaContent.title.orEmpty() else mediaContent.name.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 12.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(cardWidth)
                    )
                }
            }
            
            // Fallback for unknown types
            else -> {
                EmptyCard(
                    isSelected = isSelected,
                    cardHeight = cardHeight,
                    cardWidth = cardWidth
                )
            }
        }
    }
}

/**
 * Card specifically designed for Person display
 */
@Composable
private fun PersonCard(
    person: Person,
    context: Context,
    imageLoader: ImageLoader,
    isSelected: Boolean,
    cardHeight: Dp,
    cardWidth: Dp
) {
    Box(
        modifier = Modifier
            .padding(5.dp)
            .height(cardHeight)
            .width(cardWidth)
    ) {
        Surface(
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1F2937), Color(0xFF111827))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Image fills the entire card with rounded corners
                if (!person.profilePath.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("https://image.tmdb.org/t/p/w300_and_h450_face${person.profilePath}")
                            .build(),
                        contentDescription = stringResource(R.string.mediaDiscovery_personImage),
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback for when no image is available
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Calculate the avatar size as 70% of the smaller card dimension
                        val avatarSize = cardWidth.coerceAtMost(cardHeight) * 0.7f
                        // Ensure the avatar is always perfectly circular
                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(CircleShape)
                                .background(Color.Transparent), // Prevents any parent background bleed
                            contentAlignment = Alignment.Center
                        ) {
                            LetterAvatar(
                                name = person.name,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                
                // Add a subtle gradient overlay at the bottom for better visibility of badges
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x00000000),
                                    Color(0x99000000)
                                ),
                                startY = 0f,
                                endY = 300f
                            )
                        )
                )
            }
        }
        
        // Add PERSON badge
        Box(
            modifier = Modifier
                .padding(8.dp)
                .background(Color(0xFF4A6572).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .align(Alignment.TopStart)
        ) {
            Text(
                text = stringResource(R.string.common_personLabel),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, Color.White, RoundedCornerShape(15.dp))
            )
            
            // Add an additional focus indicator for TV compatibility
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(17.dp)
                    )
            )
        }
    }
}

/**
 * Standard card for movies, TV shows, and collections
 */
@Composable
private fun StandardMediaCard(
    posterPath: String?,
    title: String?,
    mediaType: String,
    mediaInfo: MediaInfo?,
    context: Context,
    imageLoader: ImageLoader,
    isSelected: Boolean,
    cardHeight: Dp,
    cardWidth: Dp
) {
    var hasError by remember { mutableStateOf(false) }
    val hasImagePath = !posterPath.isNullOrBlank()
    
    Box(
        modifier = Modifier
            .padding(5.dp)
            .height(cardHeight)
            .width(cardWidth)
    ) {
        Surface(
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1F2937), Color(0xFF111827))
                        )
                    )
            ) {
                if (hasImagePath) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("https://image.tmdb.org/t/p/w300_and_h450_face$posterPath")
                            .memoryCacheKey("poster_$posterPath")
                            .diskCacheKey("poster_$posterPath")
                            .build(),
                        contentDescription = stringResource(R.string.common_mediaPoster),
                        imageLoader = imageLoader,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { state ->
                            Log.e("MediaCard", "Error loading image for ${title ?: "Unknown"}: ${state.result}")
                            hasError = true
                        },
                        onLoading = { state ->
                            hasError = false
                        },
                        onSuccess = { state ->
                            hasError = false
                        }
                    )
                }
                
                // Only show text if there was no image path to begin with or if image failed to load
                if (!hasImagePath || hasError) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title ?: stringResource(R.string.mediaDiscovery_noImage),
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        
        // Type badge
        val typeBadgeColor = when (mediaType) {
            "movie" -> Color(0xFF2e5ac1).copy(alpha = 0.8f)
            "tv" -> Color(0xFF9d29bc).copy(alpha = 0.8f)
            "collection" -> Color(0xFFe07a5f).copy(alpha = 0.8f)
            else -> Color.Gray.copy(alpha = 0.8f)
        }
        
        val typeBadgeText = when (mediaType) {
            "movie" -> stringResource(R.string.common_movieLabel)
            "tv" -> stringResource(R.string.common_seriesLabel)
            "collection" -> stringResource(R.string.common_collectionLabel)
            else -> mediaType.uppercase()
        }
        
        Box(
            modifier = Modifier
                .padding(8.dp)
                .background(typeBadgeColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .align(Alignment.TopStart)
        ) {
            Text(
                text = typeBadgeText,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
        
        // Status icon
        mediaInfo?.let { info ->
            val iconComposable = getMediaInfoIcon(info)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(18.dp)
                    .graphicsLayer(alpha = 0.8f)
            ) {
                iconComposable(Modifier.fillMaxSize())
            }
        }
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, Color.White, RoundedCornerShape(15.dp))
            )
            
            // Add an additional focus indicator for TV compatibility
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(17.dp)
                    )
            )
        }
    }
}

/**
 * Fallback empty card when no valid content is provided
 */
@Composable
private fun EmptyCard(
    isSelected: Boolean,
    cardHeight: Dp,
    cardWidth: Dp
) {
    Box(
        modifier = Modifier
            .padding(5.dp)
            .height(cardHeight)
            .width(cardWidth)
    ) {
        Surface(
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1F2937), Color(0xFF111827))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.mediaDiscovery_noImage),
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, Color.White, RoundedCornerShape(15.dp))
            )
        }
    }
}

@Composable
fun LetterAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 36.sp
) {
    // Defensive: handle null, non-string, or malformed input
    val safeName = when (name) {
        null -> "?"
        else -> name
    }
    val initials = try {
        if (safeName.isBlank()) {
            "?"
        } else {
            val parts = safeName.trim().split(" ").filter { it.isNotBlank() }
            when {
                parts.size >= 2 && parts[0].isNotEmpty() && parts.last().isNotEmpty() ->
                    "${parts[0].first()}${parts.last().first()}".uppercase()
                parts.size == 1 && parts[0].length >= 2 ->
                    parts[0].take(2).uppercase()
                parts.size == 1 && parts[0].isNotEmpty() ->
                    parts[0].first().uppercase()
                else ->
                    "?"
            }
        }
    } catch (e: Exception) {
        Log.e("LetterAvatar", "Invalid name input: $name", e)
        "?"
    }

    // Use a more centered and balanced gradient for the background
    val background = Brush.linearGradient(
        colors = listOf(
            Color(0xFF4A6572), // blue-gray
            Color(0xFF3370FF), // vibrant blue
            Color(0xFF9D29BC)  // purple accent
        ),
        start = Offset(0.2f, 0.2f),
        end = Offset(200f, 200f)
    )

    // Render a circular avatar with shadow and border
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background, CircleShape)
            .semantics { contentDescription = safeName },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    offset = Offset(2f, 2f),
                    blurRadius = 6f
                )
            )
        )
    }
}

@Composable
private fun getMediaInfoIcon(mediaInfo: MediaInfo): @Composable (Modifier) -> Unit {
    // Check both regular and 4K statuses to determine the appropriate icon
    val regularStatus = mediaInfo.status
    val fourKStatus = mediaInfo.status4k
    
    // Determine the highest priority status between regular and 4K
    // Priority: Deleted (7) > Blacklisted (6) > Available (5) > Partially Available (4) > Pending (2,3) > Not Requested (0,1)
    val effectiveStatus = when {
        regularStatus == 7 || fourKStatus == 7 -> 7 // Deleted
        regularStatus == 6 || fourKStatus == 6 -> 6 // Blacklisted
        regularStatus == 5 || fourKStatus == 5 -> 5 // Available
        regularStatus == 4 || fourKStatus == 4 -> 4 // Partially Available
        regularStatus in listOf(2, 3) || fourKStatus in listOf(2, 3) -> {
            // Return the actual pending status (2 or 3) instead of always 3
            when {
                regularStatus == 2 || fourKStatus == 2 -> 2 // Pending Approval (Jellyseerr)
                else -> 3 // Pending
            }
        }
        else -> 0 // Not requested
    }
    
    return when (effectiveStatus) {
        2 -> { modifier -> CustomClockIcon(modifier) } // Pending Approval (Jellyseerr)
        3 -> { modifier -> CustomClockIcon(modifier) } // Pending
        4 -> { modifier -> CustomDashIcon(modifier) } // Partially Available
        5 -> { modifier -> CustomCheckmarkIcon(modifier) } // Available
        6 -> { modifier -> CustomBlockedIcon(modifier) } // Blacklisted
        7 -> { modifier -> CustomTrashIcon(modifier) } // Deleted
        else -> { {} }
    }
}

@Composable
internal fun CustomCheckmarkIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outerCircleColor = Color(0xFF4ADE80)
        val innerCircleColor = Color(0xFF22A555)
        val innerFillColor = Color(0xFFDCFCE7)
        val checkMarkColor = Color(0xFF22A657)
        val outerRadius = size.minDimension / 2
        val innerRadius = outerRadius * 0.85f
        val center = Offset(size.width / 2, size.height / 2)
        // Draw outer circle
        drawCircle(outerCircleColor, radius = outerRadius, center = center)
        // Draw inner circle
        drawCircle(innerCircleColor, radius = innerRadius, center = center)
        // Draw innermost fill
        drawCircle(innerFillColor, radius = innerRadius * 0.75f, center = center)
        // Draw checkMark
        val checkPath = Path().apply {
            moveTo(center.x - innerRadius * 0.4f, center.y)
            lineTo(center.x - innerRadius * 0.1f, center.y + innerRadius * 0.3f)
            lineTo(center.x + innerRadius * 0.4f, center.y - innerRadius * 0.3f)
        }
        drawPath(checkPath, checkMarkColor, style = Stroke(width = innerRadius * 0.15f))
    }
}

@Composable
internal fun CustomDashIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outerCircleColor = Color(0xFF4ADE80)
        val innerFillColor = Color(0xFF50C261)
        val dashColor = Color.White
        val outerRadius = size.minDimension / 2
        val innerRadius = outerRadius * 0.9f
        val center = Offset(size.width / 2, size.height / 2)
        // Draw outer circle
        drawCircle(outerCircleColor, radius = outerRadius, center = center)
        // Draw innermost fill
        drawCircle(innerFillColor, radius = innerRadius * 0.9f, center = center)
        // Draw dashed line
        val dashLength = innerRadius * 0.75f
        val dashWidth = size.minDimension * 0.05f
        val dashStart = Offset(center.x - dashLength / 2, center.y)
        val dashEnd = Offset(center.x + dashLength / 2, center.y)
        drawLine(
            color = dashColor,
            start = dashStart,
            end = dashEnd,
            strokeWidth = dashWidth
        )
    }
}

@Composable
internal fun CustomClockIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outerCircleColor = Color(0xFF858BF5)
        val innerCircleColor = Color(0xFF6A64CC)
        val innerFillColor = Color(0xFFE1E7FE)
        val clockHandColor = Color(0xFF6A64CC)
        
        val outerRadius = size.minDimension / 2
        val innerRadius = outerRadius * 0.8f
        val center = Offset(size.width / 2, size.height / 2)
        
        // Draw outer circle
        drawCircle(outerCircleColor, radius = outerRadius, center = center)
        
        // Draw inner circle
        drawCircle(innerCircleColor, radius = innerRadius, center = center)
        
        // Draw innermost fill
        drawCircle(innerFillColor, radius = innerRadius * 0.9f, center = center)
        
        // Draw clock hands
        val handLength = innerRadius * 0.6f
        drawLine(
            color = clockHandColor,
            start = center,
            end = Offset(center.x, center.y - handLength),
            strokeWidth = size.minDimension * 0.05f
        )
        drawLine(
            color = clockHandColor,
            start = center,
            end = Offset(center.x + handLength * 0.6f, center.y),
            strokeWidth = size.minDimension * 0.05f
        )
    }
}

@Composable
internal fun CustomTrashIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outerCircleColor = Color(0xFFEF4444)
        val innerCircleColor = Color(0xFFDC2626)
        val innerFillColor = Color(0xFFFEF2F2)
        val trashColor = Color(0xFFDC2626)
        
        val outerRadius = size.minDimension / 2
        val innerRadius = outerRadius * 0.85f
        val center = Offset(size.width / 2, size.height / 2)
        
        // Draw outer circle
        drawCircle(outerCircleColor, radius = outerRadius, center = center)
        
        // Draw inner circle
        drawCircle(innerCircleColor, radius = innerRadius, center = center)
        
        // Draw innermost fill
        drawCircle(innerFillColor, radius = innerRadius * 0.75f, center = center)
        
        // Draw trash can lid
        val lidWidth = innerRadius * 0.6f
        val lidHeight = innerRadius * 0.15f
        val lidTop = center.y - innerRadius * 0.4f
        
        val lidPath = Path().apply {
            moveTo(center.x - lidWidth / 2, lidTop)
            lineTo(center.x + lidWidth / 2, lidTop)
            lineTo(center.x + lidWidth / 2, lidTop + lidHeight)
            lineTo(center.x - lidWidth / 2, lidTop + lidHeight)
            close()
        }
        drawPath(lidPath, trashColor)
        
        // Draw trash can body
        val bodyWidth = innerRadius * 0.5f
        val bodyHeight = innerRadius * 0.5f
        val bodyTop = lidTop + lidHeight
        
        val bodyPath = Path().apply {
            moveTo(center.x - bodyWidth / 2, bodyTop)
            lineTo(center.x + bodyWidth / 2, bodyTop)
            lineTo(center.x + bodyWidth / 2, bodyTop + bodyHeight)
            lineTo(center.x - bodyWidth / 2, bodyTop + bodyHeight)
            close()
        }
        drawPath(bodyPath, trashColor, style = Stroke(width = innerRadius * 0.08f))
        
        // Draw handle
        val handlePath = Path().apply {
            moveTo(center.x - lidWidth / 2 - innerRadius * 0.1f, lidTop + lidHeight / 2)
            lineTo(center.x - lidWidth / 2, lidTop + lidHeight / 2)
        }
        drawPath(handlePath, trashColor, style = Stroke(width = innerRadius * 0.08f))
    }
}

@Composable
internal fun CustomBlockedIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outerCircleColor = Color(0xFF000000)
        val innerCircleColor = Color(0xFF1F1F1F)
        val innerFillColor = Color(0xFFF3F4F6)
        val blockedColor = Color(0xFF000000)
        
        val outerRadius = size.minDimension / 2
        val innerRadius = outerRadius * 0.85f
        val center = Offset(size.width / 2, size.height / 2)
        
        // Draw outer circle
        drawCircle(outerCircleColor, radius = outerRadius, center = center)
        
        // Draw inner circle
        drawCircle(innerCircleColor, radius = innerRadius, center = center)
        
        // Draw innermost fill
        drawCircle(innerFillColor, radius = innerRadius * 0.75f, center = center)
        
        // Draw prohibition symbol (circle with diagonal line)
        val prohibitionRadius = innerRadius * 0.4f
        val lineWidth = innerRadius * 0.12f
        
        // Draw the diagonal prohibition line
        val lineLength = prohibitionRadius * 1.8f
        val lineStart = Offset(
            center.x - lineLength / 2,
            center.y - lineLength / 2
        )
        val lineEnd = Offset(
            center.x + lineLength / 2,
            center.y + lineLength / 2
        )
        
        drawLine(
            color = blockedColor,
            start = lineStart,
            end = lineEnd,
            strokeWidth = lineWidth
        )
        
        // Draw small circles at the ends of the line for better visibility
        drawCircle(blockedColor, radius = lineWidth / 2, center = lineStart)
        drawCircle(blockedColor, radius = lineWidth / 2, center = lineEnd)
    }
} 