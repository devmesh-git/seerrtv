package ca.devmesh.seerrtv.viewmodel

import ca.devmesh.seerrtv.data.seerrApiJson
import ca.devmesh.seerrtv.model.DownloadStatus
import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.model.MediaInfo
import ca.devmesh.seerrtv.model.Request
import ca.devmesh.seerrtv.model.RequestResponse
import ca.devmesh.seerrtv.model.User
import ca.devmesh.seerrtv.model.activeDownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestDownloadsTest {
    private fun info(tmdbId: Int, type: String = "movie", status: Int = 3, downloads: List<DownloadStatus> = emptyList(), downloads4k: List<DownloadStatus> = emptyList()) =
        MediaInfo(
            id = tmdbId, mediaType = type, tmdbId = tmdbId, status = status, status4k = 1,
            createdAt = "", updatedAt = "", lastSeasonChange = "",
            downloadStatus = downloads, downloadStatus4k = downloads4k
        )

    private fun download(size: Long, left: Long?, id: String? = null) = DownloadStatus(
        externalId = 1, mediaType = "movie", size = size, sizeLeft = left, status = "downloading",
        title = "file", downloadId = id
    )

    private fun request(info: MediaInfo, status: Int = 2) = Request(
        id = info.id, createdAt = "", is4k = false, isAutoRequest = false, media = info,
        requestedBy = User(id = 1), seasonCount = 0, status = status, type = info.mediaType, updatedAt = ""
    )

    private fun media(info: MediaInfo, requestStatus: Int = 2) =
        Media(id = info.tmdbId, mediaType = info.mediaType, mediaInfo = info, request = request(info, requestStatus))

    @Test
    fun decodesStockSeerrDownloadStatus() {
        // Shape of a stock Seerr /request?filter=processing response, trimmed.
        val json = """
            {"pageInfo":{"pages":1,"pageSize":100,"results":1,"page":1},"results":[{
              "id":7,"createdAt":"x","updatedAt":"x","is4k":false,"isAutoRequest":false,"status":2,
              "type":"tv","seasonCount":1,"requestedBy":{"id":1},
              "media":{"id":3,"mediaType":"tv","tmdbId":1399,"status":3,"status4k":1,
                "createdAt":"x","updatedAt":"x","lastSeasonChange":"x",
                "downloadStatus":[
                  {"externalId":5,"mediaType":"tv","size":1000,"sizeLeft":250,"status":"downloading",
                   "timeLeft":"00:10:00","estimatedCompletionTime":"2026-11-06T20:00:00Z",
                   "title":"Pack","downloadId":"ABC","episode":{"seasonNumber":1,"episodeNumber":1,"absoluteEpisodeNumber":1}},
                  {"externalId":5,"mediaType":"tv","size":1000,"sizeLeft":250,"status":"downloading",
                   "title":"Pack","downloadId":"ABC","episode":{"seasonNumber":1,"episodeNumber":2}}
                ]}}]}
        """.trimIndent()
        val response = seerrApiJson.decodeFromString(RequestResponse.serializer(), json)
        val media = response.results.single().media
        assertEquals(2, media.downloadStatus[1].episode?.episodeNumber)
        // One season pack listed per episode: its bytes count once, so 75% not a doubled total.
        assertEquals(0.75f, media.activeDownloadProgress()!!, 0.001f)
    }

    @Test
    fun progressIsNullWithoutDownloadsAndIgnoresMissingSizeLeft() {
        assertNull(info(1).activeDownloadProgress())
        val unknown = info(1, downloads = listOf(download(1000, null)))
        assertEquals(0f, unknown.activeDownloadProgress()!!, 0.001f)
    }

    @Test
    fun progressCombinesTiersAndSeparateDownloads() {
        val i = info(
            1,
            downloads = listOf(download(1000, 0, "a"), download(1000, 1000, "b")),
            downloads4k = listOf(download(2000, 1000, "a"))
        )
        assertEquals(2000f / 4000f, i.activeDownloadProgress()!!, 0.001f)
    }

    @Test
    fun downloadingTitlesComeFirstOthersKeepOrder() {
        val items = listOf(media(info(1)), media(info(2, downloads = listOf(download(10, 5)))), media(info(3)))
        assertEquals(listOf(2, 1, 3), RequestDownloads.downloadingFirst(items).map { it.id })
    }

    @Test
    fun pollIntervalFollowsWhatTheRowHolds() {
        assertEquals(RequestDownloads.ACTIVE_POLL_MS, RequestDownloads.pollIntervalForRow(listOf(media(info(1, downloads = listOf(download(10, 5)))))))
        assertEquals(RequestDownloads.WAITING_POLL_MS, RequestDownloads.pollIntervalForRow(listOf(media(info(1)))))
        // Never stops: the row lists every request, and new ones can arrive at any time.
        assertEquals(RequestDownloads.WAITING_POLL_MS, RequestDownloads.pollIntervalForRow(listOf(media(info(1, status = 5), requestStatus = 5))))
        assertEquals(RequestDownloads.WAITING_POLL_MS, RequestDownloads.pollIntervalForProcessing(emptyList()))
    }

    @Test
    fun patchUpdatesProgressInPlaceAndReportsFinished() {
        val downloading = info(1, downloads = listOf(download(100, 80)))
        val finishing = info(2, downloads = listOf(download(100, 1)))
        val idle = info(3)
        val items = listOf(media(downloading), media(finishing), media(idle))
        val processing = listOf(request(info(1, downloads = listOf(download(100, 40)))), request(info(3)))

        val patch = RequestDownloads.patch(items, processing)

        assertTrue(patch.changed)
        assertEquals(listOf(1, 2, 3), patch.items.map { it.id })
        assertEquals(0.6f, patch.items[0].mediaInfo!!.activeDownloadProgress()!!, 0.001f)
        assertNull(patch.items[1].mediaInfo!!.activeDownloadProgress())
        assertEquals(listOf(2), patch.finished.map { it.tmdbId })
        // Request-row seasons and other fields survive the patch.
        assertEquals(items[2], patch.items[2])

        val done = RequestDownloads.applyStatus(patch.items, info(2, status = 5))
        assertEquals(5, done[1].mediaInfo!!.status)
        assertEquals(5, done[1].status)
    }

    @Test
    fun patchWithNothingNewChangesNothing() {
        val items = listOf(media(info(1, downloads = listOf(download(100, 80)))))
        val patch = RequestDownloads.patch(items, listOf(request(items[0].mediaInfo!!)))
        assertFalse(patch.changed)
        assertTrue(patch.finished.isEmpty())
    }

    @Test
    fun reportsDownloadsTheRowDoesNotHold() {
        val items = listOf(media(info(1)))
        val processing = listOf(
            request(info(1)),
            request(info(9, downloads = listOf(download(100, 50)))),
            request(info(8))
        )
        assertEquals(setOf("movie:9"), RequestDownloads.patch(items, processing).newDownloads)
    }

    @Test
    fun titleLeavingTheListAfterItsDownloadEndedIsCheckedOnce() {
        // Download already gone, Seerr marks it available a minute later: the request drops out.
        val items = listOf(media(info(1)))
        val first = RequestDownloads.patch(items, emptyList())
        assertEquals(listOf(1), first.finished.map { it.tmdbId })
        assertTrue(first.changed)
        val second = RequestDownloads.patch(first.items, emptyList())
        assertTrue(second.finished.isEmpty())
        assertFalse(second.changed)
    }
}
