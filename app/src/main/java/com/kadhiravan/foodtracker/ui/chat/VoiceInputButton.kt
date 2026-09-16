package com.kadhiravan.foodtracker.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/**
 * Mic/stop toggle whose waveform rings react to the live input level while listening
 * (driven by [amplitude], 0f..1f from [android.speech.SpeechRecognizer]'s RMS callback) , 
 * so it visibly breathes with your voice instead of animating on a fixed timer, and only
 * ever stops when [onClick] is tapped.
 */
@Composable
fun VoiceInputButton(
    isListening: Boolean,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val level by animateFloatAsState(
        targetValue = if (isListening) amplitude.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "mic-level"
    )

    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        if (isListening) {
            WaveRing(baseSize = 56.dp, level = level, maxGrowth = 0.7f, baseAlpha = 0.35f)
            WaveRing(baseSize = 48.dp, level = level, maxGrowth = 0.4f, baseAlpha = 0.55f)
        }
        FilledIconButton(
            onClick = onClick,
            colors = if (isListening) {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            } else {
                IconButtonDefaults.filledIconButtonColors()
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Voice input"
            )
        }
    }
}

@Composable
private fun WaveRing(baseSize: androidx.compose.ui.unit.Dp, level: Float, maxGrowth: Float, baseAlpha: Float) {
    Box(
        modifier = Modifier
            .size(baseSize)
            .scale(1f + level * maxGrowth)
            .alpha((baseAlpha * (1f - level * 0.5f)).coerceIn(0.12f, baseAlpha))
            .background(MaterialTheme.colorScheme.error, CircleShape)
    )
}
