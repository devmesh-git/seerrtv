package ca.devmesh.seerrtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.Keyword
import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.model.SimilarMediaItem
import coil3.compose.AsyncImage
import coil3.ImageLoader
import coil3.request.ImageRequest

@Composable
fun FlexibleTagLayout(
    keywords: List<Keyword>?,
    selectedIndex: Int,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    onLeftmostTagsUpdated: (List<Int>) -> Unit,
    onTagPositionsUpdated: (List<Pair<Int, Float>>) -> Unit
) {
    keywords?.takeIf { it.isNotEmpty() }?.let { nonNullKeywords ->
        androidx.compose.ui.layout.Layout(
            content = {
                nonNullKeywords.forEachIndexed { index, keyword ->
                    TagItem(
                        keyword = keyword,
                        isSelected = isFocused && index == selectedIndex
                    )
                }
            },
            modifier = modifier.fillMaxWidth()
        ) { measurables, constraints ->
            val tagPositions = mutableListOf<Pair<Int, Float>>()
            val placeables = measurables.map { measurable ->
                measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            }

            val maxWidth = constraints.maxWidth
            var totalHeight = 0
            var rowHeight = 0
            val leftmostTags = mutableListOf(0)
            var x = 0

            placeables.forEachIndexed { index, placeable ->
                if (x + placeable.width > maxWidth) {
                    x = 0
                    totalHeight += rowHeight + 8 // 8.dp vertical spacing
                    rowHeight = 0
                    leftmostTags.add(index)
                }

                x += placeable.width + 8 // 8.dp horizontal spacing
                rowHeight = maxOf(rowHeight, placeable.height)
            }

            totalHeight += rowHeight // Add the height of the last row

            layout(maxWidth, totalHeight) {
                x = 0
                var y = 0
                rowHeight = 0

                placeables.forEachIndexed { index, placeable ->
                    if (x + placeable.width > maxWidth) {
                        x = 0
                        y += rowHeight + 8
                        rowHeight = 0
                        leftmostTags.add(index)
                    }

                    placeable.place(x, y)
                    tagPositions.add(Pair(index, y.toFloat()))
                    x += placeable.width + 8
                    rowHeight = maxOf(rowHeight, placeable.height)
                }

                onLeftmostTagsUpdated(leftmostTags)
                onTagPositionsUpdated(tagPositions)
            }
        }
    }
}

@Composable
fun TagItem(
    keyword: Keyword,
    isSelected: Boolean,
) {
    Box(
        modifier = Modifier
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                color = Color(0xFF2A2F3A),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TagIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = keyword.name,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun PersonCarousel(
    people: List<Any>,
    imageLoader: ImageLoader,
    selectedIndex: Int,
    isFocused: Boolean,
    getPersonName: (Any) -> String,
    getPersonRole: (Any) -> String,
    getPersonProfilePath: (Any) -> String?
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(end = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(people) { index, person ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(100.dp)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isFocused && index == selectedIndex) 2.dp else 0.dp,
                                color = if (isFocused && index == selectedIndex) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                    ) {
                        val profilePath = getPersonProfilePath(person)
                        if (!profilePath.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("https://image.tmdb.org/t/p/w185$profilePath")
                                    .build(),
                                contentDescription = stringResource(R.string.mediaDiscovery_personImage),
                                imageLoader = imageLoader,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LetterAvatar(
                                name = getPersonName(person),
                                modifier = Modifier.fillMaxSize(),
                                fontSize = 32.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = getPersonName(person),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = getPersonRole(person),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SimilarMediaCarousel(
    similarMedia: List<SimilarMediaItem>,
    imageLoader: ImageLoader,
    selectedIndex: Int,
    isFocused: Boolean,
    onMediaClick: (Int, String) -> Unit,
    onLoadNextPage: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    // Load next page when approaching end of carousel
    LaunchedEffect(selectedIndex, similarMedia.size) {
        if (selectedIndex >= similarMedia.size - 5) {
            onLoadNextPage()
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(similarMedia) { index, media ->
            SimilarMediaCard(
                media = media,
                imageLoader = imageLoader,
                isSelected = isFocused && index == selectedIndex,
                onClick = { onMediaClick(media.id, media.mediaType) }
            )
        }
    }
}

@Composable
fun SimilarMediaCard(
    media: SimilarMediaItem,
    imageLoader: ImageLoader,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Convert SimilarMediaItem to Media for MediaCard compatibility
    val mediaForCard = remember(media) {
        Media(
            id = media.id,
            mediaType = media.mediaType,
            title = media.title ?: media.name ?: "", // Use title for movies, name for TV series
            name = media.name ?: media.title ?: "", // Use name for TV series, title for movies
            posterPath = media.posterPath ?: "",
            backdropPath = media.backdropPath ?: "",
            overview = media.overview,
            mediaInfo = media.mediaInfo
        )
    }

    MediaCard(
        mediaContent = mediaForCard,
        context = LocalContext.current,
        imageLoader = imageLoader,
        isSelected = isSelected,
        cardWidth = 120.dp,
        cardHeight = 180.dp,
        showLabel = true,
        modifier = Modifier
            .padding(end = 8.dp)
            .clickable { onClick() }
    )
}

// Tag icon definition
val TagIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "tag",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            ) {
                moveTo(9.568f, 3f)
                horizontalLineTo(5.25f)
                arcTo(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3f, 5.25f)
                verticalLineTo(9.568f)
                curveToRelative(0f, 0.597f, 0.237f, 1.17f, 0.659f, 1.591f)
                // Equivalent to SVG relative line "l9.581 9.581" from current point (5.909, 11.159)
                // -> absolute end point (15.49, 20.74)
                lineTo(15.49f, 20.74f)
                curveToRelative(0.699f, 0.699f, 1.78f, 0.872f, 2.607f, 0.33f)
                arcToRelative(
                    18.095f,
                    18.095f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    5.223f,
                    -5.223f
                )
                curveToRelative(0.542f, -0.827f, 0.369f, -1.908f, -0.33f, -2.607f)
                lineTo(11.16f, 3.66f)
                arcTo(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9.568f, 3f)
                close()
            }
            // Tiny square dot at (6,6) sized 0.008 as per source SVG
            path(
                fill = null,
                stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            ) {
                moveTo(6f, 6f)
                horizontalLineTo(6.008f)
                verticalLineTo(6.008f)
                horizontalLineTo(6f)
                verticalLineTo(6f)
                close()
            }
        }.build()
    }
