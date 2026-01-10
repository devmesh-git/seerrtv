package ca.devmesh.seerrtv.util

import android.util.Log
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

/**
 * Utility class to handle key events safely, catching SecurityExceptions
 * that can occur on certain Android TV devices (like SKYWORTH-HY4002)
 * when the system tries to close system dialogs.
 */
object SafeKeyEventHandler {
    
    private const val TAG = "SafeKeyEventHandler"

    /**
     * Safely handles a key event with additional context information for better debugging.
     * 
     * @param keyEvent The key event to handle
     * @param context Additional context string for logging
     * @param handler The actual key event handler function
     * @return true if the event was handled, false otherwise
     */
    fun handleKeyEventWithContext(
        keyEvent: KeyEvent,
        context: String,
        handler: (KeyEvent) -> Boolean
    ): Boolean {
        return try {
            handler(keyEvent)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException in $context: ${e.message}")
            
            // For certain key types, we can still return true to consume the event
            try {
                when (keyEvent.key) {
                    androidx.compose.ui.input.key.Key.Back -> {
                        Log.d(TAG, "Consuming Back key event in $context despite SecurityException")
                        true
                    }
                    else -> {
                        Log.d(TAG, "Not consuming key event in $context due to SecurityException")
                        false
                    }
                }
            } catch (keyAccessException: Exception) {
                Log.w(TAG, "Could not access key property in $context: ${keyAccessException.message}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception in $context: ${e.message}", e)
            false
        }
    }
}
