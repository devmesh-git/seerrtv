package ca.devmesh.seerrtv.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.SeerrTV
import ca.devmesh.seerrtv.ui.focus.*
import ca.devmesh.seerrtv.viewmodel.DownloadEntry
import ca.devmesh.seerrtv.viewmodel.DownloadsViewModel
import coil3.compose.AsyncImage
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val downloadsFocusSaver =
    listSaver<DownloadsFocus, Any>(
        save = { listOf(it.section.ordinal, it.index, it.rowId.orEmpty()) },
        restore = {
            DownloadsFocus(
                DownloadsFocus.Section.entries[it[0] as Int],
                it[1] as Int,
                (it[2] as String).ifEmpty { null },
            )
        },
    )

@Composable
fun DownloadsScreen(
    appFocusManager: AppFocusManager,
    dpadController: DpadController,
    onBack: () -> Unit,
    onOpenMedia: (Int, String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf(DownloadFilter.All) }
    val listState = rememberLazyListState()
    // Details clears the global focus history on Back. Keep this entry's selection separately.
    var rememberedFocus by
        rememberSaveable(stateSaver = downloadsFocusSaver) {
            mutableStateOf(DownloadsFocus())
        }
    fun setFocus(value: DownloadsFocus) {
        rememberedFocus = value
        appFocusManager.setFocus(AppFocusState.DownloadsScreen(value))
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(lifecycleOwner, viewModel) {
        // The back-stack entry is RESUMED only while this screen is visible.
        // Navigation away, backgrounding, and disposal all cancel the loop/request.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refresh()
                delay(if (viewModel.state.value.entries.isEmpty()) 15_000 else 5_000)
            }
        }
    }
    val now = state.checkedAt
    val rows = state.entries
    val downloading = rows.count { it.row.download.state(now) == "downloading" }
    val attention = rows.count { it.row.download.needsAttention(now) }
    val reportedSpeeds = rows.mapNotNull { it.row.download.speed(now) }
    val visible = rows.filter { entry ->
        when (filter) {
            DownloadFilter.All -> true
            DownloadFilter.Movies -> entry.row.mediaType == "movie"
            DownloadFilter.Series -> entry.row.mediaType == "tv"
            DownloadFilter.Attention -> entry.row.download.needsAttention(now)
        }
    }
    val focus =
        (appFocusManager.currentFocus as? AppFocusState.DownloadsScreen)?.focus ?: rememberedFocus
    val rowIds = visible.map { it.row.id }
    fun move(direction: DownloadDirection) {
        setFocus(moveDownloadsFocus(focus, direction, rowIds, filter.ordinal))
    }
    val config =
        rememberStableDpadConfig(
            ScreenDpadConfig(
                route = "downloads",
                focusManager = appFocusManager,
                sections = listOf(DpadSection.Actions, DpadSection.List),
                transitions = DpadTransitions(emptyMap(), emptyMap(), emptyMap(), emptyMap()),
                onUp = { move(DownloadDirection.Up) },
                onDown = { move(DownloadDirection.Down) },
                onLeft = { move(DownloadDirection.Left) },
                onRight = { move(DownloadDirection.Right) },
                onEnter = {
                    when (focus.section) {
                        DownloadsFocus.Section.Header ->
                            if (focus.index == 0) onBack()
                            else if (!state.refreshing) {
                                scope.launch { viewModel.refresh() }
                            }
                        DownloadsFocus.Section.Filters -> {
                            filter = DownloadFilter.entries[focus.index]
                        }
                        DownloadsFocus.Section.Rows ->
                            visible
                                .firstOrNull { it.row.id == focus.rowId }
                                ?.let {
                                    onOpenMedia(it.row.tmdbId, it.row.mediaType)
                                }
                    }
                },
                onBack = onBack,
            )
        )
    DisposableEffect(config, dpadController) {
        dpadController.registerScreen(config)
        onDispose { dpadController.unregisterScreen("downloads") }
    }
    LaunchedEffect(Unit) { setFocus(rememberedFocus) }
    LaunchedEffect(rowIds, filter, focus, state.loading) {
        if (state.loading) return@LaunchedEffect
        val valid = reconcileDownloadsFocus(focus, rowIds, filter.ordinal)
        if (valid != focus) setFocus(valid)
        if (valid.section == DownloadsFocus.Section.Rows) {
            val index = rowIds.indexOf(valid.rowId)
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val fullyVisible = visibleItems.any {
                it.index == index &&
                    it.offset >= listState.layoutInfo.viewportStartOffset &&
                    it.offset + it.size <= listState.layoutInfo.viewportEndOffset
            }
            if (index >= 0 && !fullyVisible) listState.animateScrollToItem(index)
        }
    }
    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFF111827))
            .padding(horizontal = 40.dp, vertical = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DownloadAction(stringResource(R.string.downloads_back), focus == DownloadsFocus())
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.downloads_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.downloads_subtitle),
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            DownloadAction(
                stringResource(R.string.downloads_refresh),
                focus == DownloadsFocus(index = 1),
                enabled = !state.refreshing,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Summary(downloading.toString(), stringResource(R.string.downloads_downloading))
            Summary(
                if (reportedSpeeds.isEmpty()) "—"
                else
                    String.format(
                        locale,
                        "%.1f MB/s",
                        reportedSpeeds.sum() / 1_000_000,
                    ),
                stringResource(R.string.downloads_speed),
            )
            Summary(attention.toString(), stringResource(R.string.downloads_attention))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DownloadFilter.entries.forEach { option ->
                val label =
                    when (option) {
                        DownloadFilter.All -> stringResource(R.string.downloads_all)
                        DownloadFilter.Movies -> stringResource(R.string.downloads_movies)
                        DownloadFilter.Series -> stringResource(R.string.downloads_series)
                        DownloadFilter.Attention -> stringResource(R.string.downloads_attention)
                    }
                DownloadAction(
                    label,
                    focus == DownloadsFocus(DownloadsFocus.Section.Filters, option.ordinal),
                    selected = filter == option,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.error) {
            Text(stringResource(R.string.downloads_error), color = Color(0xFFFCA5A5))
            Spacer(Modifier.height(8.dp))
        }
        when {
            state.loading ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            rows.isEmpty() ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(
                            if (state.error) R.string.downloads_unavailable
                            else R.string.downloads_empty
                        ),
                        color = Color(0xFF9CA3AF),
                    )
                }
            visible.isEmpty() ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.downloads_empty_filter), color = Color(0xFF9CA3AF))
                }
            else ->
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(visible, key = { it.row.id }) { entry ->
                        DownloadRow(
                            entry,
                            now,
                            focus.section == DownloadsFocus.Section.Rows &&
                                focus.rowId == entry.row.id,
                        )
                    }
                }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.downloads_episode_note),
            color = Color(0xFF9CA3AF),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun Summary(value: String, label: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF9CA3AF))
    }
}

@Composable
private fun DownloadAction(
    label: String,
    isFocused: Boolean,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.semantics {
                focused = isFocused
                role = Role.Button
                if (!enabled) disabled()
            }
            .border(2.dp, if (isFocused) Color.White else Color.Transparent, shape)
            .background(if (selected) Color(0xFF4F46E5) else Color(0xFF374151), shape)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (enabled) Color.White else Color(0xFF9CA3AF))
    }
}

@Composable
private fun DownloadRow(entry: DownloadEntry, now: Long, isFocused: Boolean) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val row = entry.row
    val progress = row.download
    val percent = progress.percent()
    val state = progress.state(now)
    val title =
        entry.media?.title
            ?: entry.media?.name
            ?: stringResource(R.string.downloads_unknown_title, row.tmdbId)
    val stateLabel =
        when (state) {
            "downloading" -> R.string.downloads_downloading
            "paused" -> R.string.downloads_paused
            "stalled" -> R.string.downloads_stalled
            "queued" -> R.string.downloads_queued
            "completed" -> R.string.downloads_importing
            "failed",
            "error",
            "warning" -> R.string.downloads_failed
            "checking" -> R.string.downloads_checking
            "metadata" -> R.string.downloads_metadata
            else -> R.string.downloads_unknown
        }
    val statusColor =
        when {
            state == "failed" -> Color(0xFFFCA5A5)
            progress.needsAttention(now) -> Color(0xFFFCD34D)
            state == "completed" -> Color(0xFF6EE7B7)
            else -> Color(0xFFD1D5DB)
        }
    Row(
        Modifier.fillMaxWidth()
            .semantics {
                focused = isFocused
                role = Role.Button
            }
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) Color(0xFFA5B4FC) else Color(0xFF374151),
                RoundedCornerShape(12.dp),
            )
            .background(
                if (isFocused) Color(0xFF293548) else Color(0xFF1F2937),
                RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AsyncImage(
            model =
                entry.media?.posterPath?.let { "https://image.tmdb.org/t/p/w300_and_h450_face$it" },
            imageLoader = SeerrTV.imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.width(62.dp)
                    .height(93.dp)
                    .background(Color(0xFF374151), RoundedCornerShape(6.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    stringResource(stateLabel),
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            val episodeText =
                row.episodes.joinToString(", ") {
                    String.format(locale, "S%02dE%02d", it.seasonNumber, it.episodeNumber)
                }
            val typeLabel =
                if (row.mediaType == "movie") stringResource(R.string.downloads_movie)
                else stringResource(R.string.downloads_series)
            val scopeLabel =
                if (row.episodes.size > 1) stringResource(R.string.downloads_shared) else typeLabel
            Text(
                listOfNotNull(
                        if (episodeText.isNotBlank()) "$episodeText · $scopeLabel" else typeLabel,
                        if (row.is4k) "4K" else null,
                    )
                    .joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF9CA3AF),
                style = MaterialTheme.typography.labelMedium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LinearProgressIndicator(
                    progress = { (percent / 100).toFloat() },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = Color(0xFF818CF8),
                    trackColor = Color(0xFF374151),
                )
                Text(
                    String.format(locale, "%.1f%%", percent),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val speed = progress.speed(now)
            val eta = progress.remainingSeconds(now)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(
                    if (speed != null) String.format(locale, "%.1f MB/s", speed / 1_000_000)
                    else stringResource(R.string.downloads_no_speed),
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    if (eta == null) stringResource(R.string.downloads_eta_unknown)
                    else
                        stringResource(
                            R.string.downloads_eta,
                            if (eta >= 3600) "${eta / 3600}h ${(eta % 3600) / 60}m"
                            else if (eta >= 60) "${(eta + 59) / 60}m" else "${eta}s",
                        ),
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (row.requestedBy.isNotEmpty())
                Text(
                    stringResource(
                        R.string.downloads_requested_by,
                        row.requestedBy.joinToString(", "),
                    ),
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.labelSmall,
                )
        }
    }
}
