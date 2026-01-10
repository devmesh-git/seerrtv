package ca.devmesh.seerrtv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.ui.components.CustomButton

@Composable
fun AuthenticationErrorDialog(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onReconfigure: () -> Unit,
    selectedOption: Int = 0,
    isVisible: Boolean
) {
    // Only render the dialog when visible
    if (!isVisible) return
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(400.dp)
                .padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E293B),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.configScreen_authenticationFailed),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(id = R.string.configScreen_authFailedMessage),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CustomButton(
                        onClick = onRetry,
                        isFocused = selectedOption == 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(id = R.string.configScreen_retry),
                            color = Color.White
                        )
                    }

                    CustomButton(
                        onClick = onReconfigure,
                        isFocused = selectedOption == 1,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(id = R.string.configScreen_reconfigure),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
} 