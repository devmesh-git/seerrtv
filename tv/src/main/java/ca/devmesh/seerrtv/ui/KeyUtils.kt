package ca.devmesh.seerrtv.ui

object KeyUtils {
    private val enterKeyCodes = setOf(
        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
        android.view.KeyEvent.KEYCODE_ENTER,
        98784247808 // Your specific device's code
    )
    fun isEnterKey(keyCode: Int): Boolean = enterKeyCodes.contains(keyCode)
}