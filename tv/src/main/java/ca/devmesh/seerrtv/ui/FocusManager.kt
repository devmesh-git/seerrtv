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
}


