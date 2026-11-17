package com.nocturne.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swipe left → next track, swipe right → previous. The content follows the finger; past the
 * threshold (or on a quick fling) it slides out, the track changes and the new one slides in
 * from the opposite side. Taps and vertical gestures are left to the parent.
 */
@Composable
fun Modifier.swipeToSkip(enabled: Boolean, onNext: () -> Unit, onPrevious: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    var width by remember { mutableIntStateOf(1) }

    return this
        .onSizeChanged { width = it.width.coerceAtLeast(1) }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            val tracker = VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = { tracker.resetTracking() },
                onDragCancel = { scope.launch { offset.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) } },
                onHorizontalDrag = { change, delta ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offset.snapTo(offset.value + delta) }
                },
                onDragEnd = {
                    val velocity = tracker.calculateVelocity().x
                    val o = offset.value
                    val next = o < -threshold || (velocity < -1_400f && o < 0f)
                    val previous = o > threshold || (velocity > 1_400f && o > 0f)
                    scope.launch {
                        if (next || previous) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val out = if (next) -width.toFloat() else width.toFloat()
                            offset.animateTo(out, tween(150))
                            if (next) onNext() else onPrevious()
                            offset.snapTo(-out * 0.6f)
                            offset.animateTo(0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                        } else {
                            offset.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                    }
                },
            )
        }
        .graphicsLayer {
            val f = offset.value / width
            translationX = offset.value
            rotationZ = f * 6f
            alpha = 1f - abs(f).coerceIn(0f, 0.6f)
        }
}
