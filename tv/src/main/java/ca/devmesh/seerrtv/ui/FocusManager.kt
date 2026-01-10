package ca.devmesh.seerrtv.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class FocusManager {
    var buttonFocusOrder by mutableStateOf<List<Int>>(emptyList())

    fun setButtonOrder(order: List<Int>) {
        buttonFocusOrder = order
    }

    fun getPrevInOrder(current: Int): Int? {
        val idx = buttonFocusOrder.indexOf(current)
        if (idx == -1) return buttonFocusOrder.firstOrNull()
        return if (idx > 0) buttonFocusOrder[idx - 1] else null
    }
}


