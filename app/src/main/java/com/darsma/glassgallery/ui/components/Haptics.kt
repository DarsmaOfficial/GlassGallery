package com.darsma.glassgallery.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Physical feedback layer. A whisper of vibration on every meaningful touch is
 * what separates a "nice" interface from one that feels genuinely premium —
 * the motion on screen gets confirmed in the user's hand.
 *
 * All of this is built into Android: zero dependencies, zero cost.
 */

/** Fires a light "tick" the instant an interaction source is pressed. */
@Composable
fun MutableInteractionSource.hapticPress() {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(this) {
        interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}
