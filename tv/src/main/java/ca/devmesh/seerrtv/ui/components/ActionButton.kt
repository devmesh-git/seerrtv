package ca.devmesh.seerrtv.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Standardized ActionButton component for TV navigation
 * 
 * This component provides a consistent button style across the entire application.
 * All buttons use the same dimensions, styling, and behavior for a unified experience.
 * 
 * @param text The text to display on the button
 * @param isFocused Whether the button is currently focused
 * @param backgroundColor The background color of the button
 * @param enabled Whether the button is enabled (affects text opacity)
 * @param focusable Whether the button should be focusable for D-pad navigation (default: false)
 */
@Composable
fun ActionButton(
    text: String,
    isFocused: Boolean,
    backgroundColor: Color,
    enabled: Boolean = true,
    focusable: Boolean = false,
    fillMaxWidth: Boolean = true,
    contentColor: Color = Color.White
) {
    val buttonShape = RoundedCornerShape(16.dp)
    
    Box(
        modifier = Modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .clip(buttonShape)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = { /* No onClick for TV application - uses keyboard navigation only */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = backgroundColor
            ),
            contentPadding = PaddingValues(horizontal = 24.dp),
            modifier = Modifier
                .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                .height(44.dp)
                .focusable(focusable),
            enabled = enabled
        ) {
            Box(
                modifier = Modifier.then(if (fillMaxWidth) Modifier.fillMaxSize() else Modifier.wrapContentWidth()),
                contentAlignment = Alignment.Center
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodyLarge.copy(
                        textAlign = TextAlign.Center
                    )
                ) {
                    // One line, shrinking to fit: a long single-word label (e.g. Russian
                    // "Запросить") would otherwise be broken mid-word in a half-width button.
                    Text(
                        text = text,
                        color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 10.sp,
                            maxFontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            stepSize = 0.5.sp
                        ),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

