package ca.devmesh.seerrtv.ui.position

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for the studio/network grid lock-up (0.28.14).
 *
 * `GridPositionManager` is a process-wide object, so a selection saved against one list is still
 * there when the same screen comes back to a shorter one — the shared discovery ViewModel refetches
 * from page 1 whenever another target uses it. MediaDiscoveryScreen's grid has no individually
 * focusable cells, so it navigates entirely off these values: anything they refuse or misreport
 * leaves the screen unnavigable until the process is killed.
 */
class GridPositionManagerTest {

    private val screenKey = "studio_420"
    private val columns = 6

    @Before
    fun clearState() {
        GridPositionManager.clearScreenState(screenKey)
    }

    @Test
    fun positionPastEndIsClampedRatherThanRefused() {
        // The "load more" spinner is a real grid item, so firstVisibleItemIndex can sit one past
        // the last result while a page is in flight. This used to return false and save nothing.
        val accepted = GridPositionManager.requestPositionChange(
            screenKey = screenKey,
            position = 20,
            offset = 40,
            row = 3,
            column = 2,
            totalItems = 20
        )

        assertTrue("An out-of-range position must still produce a usable selection", accepted)
        assertEquals(Pair(19, 0), GridPositionManager.getSavedPosition(screenKey))
        assertEquals(Pair(3, 2), GridPositionManager.getSavedSelection(screenKey))
    }

    @Test
    fun inRangePositionKeepsItsScrollOffset() {
        GridPositionManager.requestPositionChange(
            screenKey = screenKey,
            position = 12,
            offset = 40,
            row = 2,
            column = 0,
            totalItems = 100
        )

        assertEquals(Pair(12, 40), GridPositionManager.getSavedPosition(screenKey))
    }

    @Test
    fun emptyListIsStillRefused() {
        val accepted = GridPositionManager.requestPositionChange(
            screenKey = screenKey,
            position = 0,
            offset = 0,
            row = 0,
            column = 0,
            totalItems = 0
        )

        assertEquals(false, accepted)
        assertNull(GridPositionManager.getSavedSelection(screenKey))
    }

    @Test
    fun selectionSavedAgainstLongerListIsRepairedOnRead() {
        // Deep in a 100-item studio list...
        GridPositionManager.requestPositionChange(
            screenKey = screenKey,
            position = 72,
            offset = 0,
            row = 12,
            column = 3,
            totalItems = 100
        )

        // ...then the list is refetched from page 1 while the screen was on details.
        val repaired = GridPositionManager.getValidSelection(screenKey, totalItems = 20, numberOfColumns = columns)

        assertNotNull(repaired)
        val (row, column) = repaired!!
        // Row 12 no longer exists; row 3 is the last addressable row of 20 items.
        assertEquals(3, row)
        // Every direction must now be able to compute a move that passes its own bounds check.
        assertTrue("Row start must address a real item", row * columns < 20)
        assertEquals(Pair(row, column), GridPositionManager.getSavedSelection(screenKey))
        assertEquals(Pair(row * columns, 0), GridPositionManager.getSavedPosition(screenKey))
    }

    @Test
    fun columnIsClampedToTheShortLastRow() {
        GridPositionManager.requestPositionChange(
            screenKey = screenKey,
            position = 0,
            offset = 0,
            row = 0,
            column = 5,
            totalItems = 100
        )

        // 21 items over 6 columns: the last row (index 3) holds only items 18..20, so a column
        // past 2 addresses nothing even though the row itself is valid.
        val repaired = GridPositionManager.getValidSelection(screenKey, totalItems = 21, numberOfColumns = columns)

        assertEquals(Pair(0, 5), repaired) // row 0 is full, nothing to repair

        GridPositionManager.saveSelection(screenKey, row = 3, column = 5)
        assertEquals(Pair(3, 2), GridPositionManager.getValidSelection(screenKey, 21, columns))
    }

    @Test
    fun validSelectionIsReturnedUnchanged() {
        GridPositionManager.saveSelection(screenKey, row = 2, column = 4)

        assertEquals(Pair(2, 4), GridPositionManager.getValidSelection(screenKey, 100, columns))
        assertEquals(Pair(2, 4), GridPositionManager.getSavedSelection(screenKey))
    }

    @Test
    fun nothingSavedYieldsNoSelection() {
        assertNull(GridPositionManager.getValidSelection(screenKey, 100, columns))
    }

    @Test
    fun emptyListYieldsNoSelection() {
        GridPositionManager.saveSelection(screenKey, row = 2, column = 4)

        assertNull(GridPositionManager.getValidSelection(screenKey, totalItems = 0, numberOfColumns = columns))
    }
}
