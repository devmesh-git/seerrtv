package ca.devmesh.seerrtv.ui.downloads

internal enum class DownloadFilter {
    All,
    Movies,
    Series,
    Attention,
}

data class DownloadsFocus(
    val section: Section = Section.Header,
    val index: Int = 0,
    val rowId: String? = null,
) {
    enum class Section {
        Header,
        Filters,
        Rows,
    }
}

internal enum class DownloadDirection {
    Up,
    Down,
    Left,
    Right,
}

/**
 * Pure remote navigation, keyed by download ID so polling never shifts focus onto another title.
 */
internal fun moveDownloadsFocus(
    focus: DownloadsFocus,
    direction: DownloadDirection,
    rows: List<String>,
    selectedFilter: Int,
): DownloadsFocus {
    val current = reconcileDownloadsFocus(focus, rows, selectedFilter)
    return when (current.section) {
        DownloadsFocus.Section.Header ->
            when (direction) {
                DownloadDirection.Down ->
                    DownloadsFocus(DownloadsFocus.Section.Filters, selectedFilter)
                DownloadDirection.Left -> current.copy(index = 0)
                DownloadDirection.Right -> current.copy(index = 1)
                else -> current
            }
        DownloadsFocus.Section.Filters ->
            when (direction) {
                DownloadDirection.Up -> DownloadsFocus()
                DownloadDirection.Down ->
                    rows.firstOrNull()?.let {
                        DownloadsFocus(DownloadsFocus.Section.Rows, rowId = it)
                    } ?: current
                DownloadDirection.Left -> current.copy(index = (current.index - 1).coerceAtLeast(0))
                DownloadDirection.Right -> current.copy(index = (current.index + 1).coerceAtMost(3))
            }
        DownloadsFocus.Section.Rows -> {
            val index = rows.indexOf(current.rowId)
            when (direction) {
                DownloadDirection.Up ->
                    if (index == 0) {
                        DownloadsFocus(DownloadsFocus.Section.Filters, selectedFilter)
                    } else current.copy(rowId = rows[index - 1])
                DownloadDirection.Down ->
                    current.copy(rowId = rows[(index + 1).coerceAtMost(rows.lastIndex)])
                else -> current
            }
        }
    }
}

internal fun reconcileDownloadsFocus(
    focus: DownloadsFocus,
    rows: List<String>,
    selectedFilter: Int,
): DownloadsFocus =
    if (focus.section == DownloadsFocus.Section.Rows && focus.rowId !in rows) {
        DownloadsFocus(DownloadsFocus.Section.Filters, selectedFilter)
    } else focus
