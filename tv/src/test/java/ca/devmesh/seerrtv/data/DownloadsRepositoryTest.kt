package ca.devmesh.seerrtv.data

import ca.devmesh.seerrtv.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DownloadsRepositoryTest {
    private val progress = QueueProgress(downloadId = "stable-id", size = 100.0, sizeLeft = 25.0)

    private fun request(id: Int = 1, type: String = "movie", is4k: Boolean = false) =
        DownloadRequest(
            id = id,
            type = type,
            is4k = is4k,
            media =
                DownloadMedia(
                    id = 10,
                    tmdbId = 20,
                    serviceId = 0,
                    downloadStatus = listOf(progress),
                ),
            seasons = listOf(DownloadSeason(1)),
        )

    @Test
    fun `decodes stock queue with optional live fields absent`() {
        val page =
            seerrApiJson.decodeFromString<DownloadRequestPage>(
                """
            {"pageInfo":{"results":1},"results":[{"id":1,"type":"movie","media":{
            "id":10,"tmdbId":20,"downloadStatus":[{"size":100,"sizeLeft":25,"status":"downloading",
            "downloadId":"example","title":"ignored","externalId":5}]}}]}
        """
            )
        val row = mergeDownloads(page.results).single()
        assertEquals(75.0, row.download.percent(), 0.001)
        assertNull(row.download.speed(System.currentTimeMillis()))
        assertNull(row.download.downloadId)
    }

    @Test
    fun `duplicate requests count a download only once`() {
        assertEquals(1, mergeDownloads(listOf(request(), request(2))).size)
    }

    @Test
    fun `quality and service instances never merge`() {
        val first = request()
        val second = first.copy(media = first.media.copy(serviceId = 1))
        val fourK =
            first.copy(is4k = true, media = first.media.copy(downloadStatus4k = listOf(progress)))
        assertEquals(3, mergeDownloads(listOf(first, second, fourK)).size)
    }

    @Test
    fun `series pack counts bytes once and labels requested episodes only`() {
        val episodes = listOf(QueueEpisode(1, 1), QueueEpisode(1, 2), QueueEpisode(2, 1))
        val tv =
            request(type = "tv").let {
                it.copy(
                    media =
                        it.media.copy(
                            downloadStatus = episodes.map { ep -> progress.copy(episode = ep) }
                        )
                )
            }
        val row = mergeDownloads(listOf(tv)).single()
        assertEquals(episodes.take(2), row.episodes)
        assertEquals(100.0, row.download.size, 0.001)
        assertTrue(mergeDownloads(listOf(tv.copy(seasons = emptyList()))).isEmpty())
    }

    @Test
    fun `series with unknown episode scope is omitted`() {
        assertTrue(mergeDownloads(listOf(request(type = "tv"))).isEmpty())
    }

    @Test
    fun `polling walks independent pages including pages without queue data`() = runBlocking {
        val offsets = mutableListOf<Int>()
        val result = loadDownloads { skip, take ->
            offsets += skip
            assertEquals(100, take)
            val rows =
                if (skip == 0)
                    List(100) {
                        request(it).let { r ->
                            r.copy(media = r.media.copy(downloadStatus = emptyList()))
                        }
                    }
                else listOf(request(101))
            ApiResult.Success(DownloadRequestPage(rows, DownloadPageInfo(101)))
        }
        assertEquals(listOf(0, 100), offsets)
        assertEquals(1, (result as ApiResult.Success).data.results.size)
    }

    @Test
    fun `failed page does not publish a partial queue as complete`() = runBlocking {
        val failure = ApiResult.Error(IllegalStateException("offline"), 503)
        val result = loadDownloads { skip, _ ->
            if (skip == 0)
                ApiResult.Success(
                    DownloadRequestPage(List(100) { request(it) }, DownloadPageInfo(101))
                )
            else failure
        }
        assertSame(failure, result)
    }

    @Test
    fun `removed queue entry disappears at next successful snapshot`() {
        assertEquals(1, mergeDownloads(listOf(request())).size)
        assertTrue(
            mergeDownloads(
                    listOf(
                        request().let {
                            it.copy(media = it.media.copy(downloadStatus = emptyList()))
                        }
                    )
                )
                .isEmpty()
        )
    }
}
