package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun WifiSignalIcon(
    level: Int, // 0 to 4
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animating: Boolean = false
) {
    if (level == 0) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = "No Signal",
            tint = MaterialTheme.colorScheme.error,
            modifier = modifier.size(size)
        )
    } else {
        val transition = rememberInfiniteTransition(label = "wifi_anim")
        val alphaAnim by if (animating) {
            transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wifi_alpha"
            )
        } else {
            androidx.compose.runtime.rememberUpdatedState(1.0f)
        }

        val tint = when (level) {
            1 -> MaterialTheme.colorScheme.error.copy(alpha = alphaAnim)
            2 -> MaterialTheme.colorScheme.tertiary.copy(alpha = alphaAnim)
            3 -> MaterialTheme.colorScheme.secondary.copy(alpha = alphaAnim)
            4 -> MaterialTheme.colorScheme.primary.copy(alpha = alphaAnim)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = "WiFi Signal level $level",
            tint = tint,
            modifier = modifier.size(size)
        )
    }
}
