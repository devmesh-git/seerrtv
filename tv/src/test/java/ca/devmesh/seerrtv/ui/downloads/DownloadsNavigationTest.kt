package ca.devmesh.seerrtv.ui.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsNavigationTest {
    private val rows = listOf("a", "b")

    @Test
    fun `remote moves from header through filters to rows and back`() {
        val filter = moveDownloadsFocus(DownloadsFocus(), DownloadDirection.Down, rows, 2)
        assertEquals(DownloadsFocus(DownloadsFocus.Section.Filters, 2), filter)
        val first = moveDownloadsFocus(filter, DownloadDirection.Down, rows, 2)
        assertEquals("a", first.rowId)
        assertEquals("b", moveDownloadsFocus(first, DownloadDirection.Down, rows, 2).rowId)
        assertEquals(filter, moveDownloadsFocus(first, DownloadDirection.Up, rows, 2))
    }

    @Test
    fun `empty queue and filter boundaries keep valid focus`() {
        val filter = DownloadsFocus(DownloadsFocus.Section.Filters)
        assertEquals(filter, moveDownloadsFocus(filter, DownloadDirection.Down, emptyList(), 0))
        assertEquals(filter, moveDownloadsFocus(filter, DownloadDirection.Left, rows, 0))
        val last = filter.copy(index = 3)
        assertEquals(last, moveDownloadsFocus(last, DownloadDirection.Right, rows, 0))
    }

    @Test
    fun `polling preserves identity across reorder and recovers after removal`() {
        val focus = DownloadsFocus(DownloadsFocus.Section.Rows, rowId = "b")
        assertEquals(focus, reconcileDownloadsFocus(focus, rows.reversed(), 1))
        assertEquals(
            DownloadsFocus(DownloadsFocus.Section.Filters, 1),
            reconcileDownloadsFocus(focus, listOf("a"), 1),
        )
    }
}
