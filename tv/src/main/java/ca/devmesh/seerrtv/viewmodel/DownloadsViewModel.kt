package ca.devmesh.seerrtv.viewmodel

import androidx.lifecycle.ViewModel
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.QueueDownload
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DownloadEntry(val row: QueueDownload, val media: MediaDetails? = null)

data class DownloadsState(
    val entries: List<DownloadEntry> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: Boolean = false,
    val checkedAt: Long = System.currentTimeMillis(),
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(private val api: SeerrApiService) : ViewModel() {
    private val mutableState = MutableStateFlow(DownloadsState())
    val state = mutableState.asStateFlow()
    private val refreshLock = Mutex()
    private val metadata = mutableMapOf<String, Pair<Long, MediaDetails?>>()

    suspend fun refresh() = refreshLock.withLock {
        mutableState.value = mutableState.value.copy(refreshing = true)
        when (val result = api.getDownloads()) {
            is ApiResult.Success -> {
                // Fetch metadata once per title, bounded to four concurrent requests.
                // Reuse the existing authenticated client, localization, and poster paths.
                val rows = result.data.results
                val now = System.currentTimeMillis()
                val unique = rows.distinctBy { "${it.mediaType}:${it.tmdbId}" }
                unique
                    .filter { row ->
                        val old = metadata["${row.mediaType}:${row.tmdbId}"]
                        old == null || old.second == null && now - old.first >= 60_000
                    }
                    .chunked(4)
                    .forEach { batch ->
                        coroutineScope {
                            batch
                                .map { row ->
                                    async {
                                        val media =
                                            if (row.mediaType == "movie")
                                                api.getMovieDetails(row.tmdbId.toString())
                                            else api.getTVDetails(row.tmdbId.toString())
                                        "${row.mediaType}:${row.tmdbId}" to
                                            (media as? ApiResult.Success)?.data
                                    }
                                }
                                .awaitAll()
                                .forEach { (key, media) -> metadata[key] = now to media }
                        }
                    }
                val activeKeys = unique.map { "${it.mediaType}:${it.tmdbId}" }.toSet()
                metadata.keys.retainAll(activeKeys)
                mutableState.value =
                    DownloadsState(
                        entries =
                            rows.map { row ->
                                DownloadEntry(
                                    row,
                                    metadata["${row.mediaType}:${row.tmdbId}"]?.second,
                                )
                            },
                        loading = false,
                        checkedAt = System.currentTimeMillis(),
                    )
            }
            is ApiResult.Error ->
                mutableState.value =
                    mutableState.value.copy(
                        loading = false,
                        refreshing = false,
                        error = true,
                        checkedAt = System.currentTimeMillis(),
                    )
            ApiResult.Loading -> Unit
        }
    }
}
