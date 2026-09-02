package com.rork.weatherloom.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember

/**
 * A single always-running clock every painter reads, so sway, drift and rainfall
 * stay in step across the whole app. Frozen when the player asks for reduced motion.
 */
@Composable
fun rememberLoomPhase(reducedMotion: Boolean = false): Float {
    if (reducedMotion) {
        val frozen: State<Float> = remember { mutableFloatStateOf(0.4f) }
        return frozen.value
    }
    val transition = rememberInfiniteTransition(label = "loom-phase")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat() * 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    return phase
}
