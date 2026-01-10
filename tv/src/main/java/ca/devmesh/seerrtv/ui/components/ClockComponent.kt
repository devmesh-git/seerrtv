package ca.devmesh.seerrtv.ui.components

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import kotlinx.coroutines.delay

/**
 * A simple clock component that shows the current time
 * Uses 24-hour or 12-hour format based on user preferences
 */
@Composable
fun Clock(context: Context) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    val use24HourClock by rememberUpdatedState(SharedPreferencesUtil.use24HourClock(context))
    val formatter =
            remember(use24HourClock) {
                if (use24HourClock) {
                    DateTimeFormatter.ofPattern("HH:mm")
                } else {
                    DateTimeFormatter.ofPattern("h:mm")
                }
            }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            // Update every second but only show hours and minutes
            delay(1000)
        }
    }

    Text(
            text = currentTime.format(formatter),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 16.dp)
    )
} 