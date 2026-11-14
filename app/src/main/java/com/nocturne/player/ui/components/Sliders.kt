package com.nocturne.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nocturne.player.ui.theme.NocturneColors

/**
 * Draggable slider drawn to the design. Seek bar: 6dp track rgba(241,241,247,0.2), white fill,
 * 16dp white thumb with a 5dp halo rgba(241,241,247,0.18). [onChange] fires while dragging,
 * [onChangeFinished] with the final value on release.
 */
@Composable
fun NocturneSlider(
    value: Float,
    onChange: (Float) -> Unit,
    onChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 6.dp,
    thumbSize: Dp = 16.dp,
    halo: Dp = 5.dp,
    trackColor: Color = NocturneColors.TextPrimary.copy(alpha = 0.2f),
    fill: Brush = SolidColor(NocturneColors.TextPrimary),
    thumbColor: Color = NocturneColors.TextPrimary,
    haloColor: Color = NocturneColors.TextPrimary.copy(alpha = 0.18f),
    description: String? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) dragValue else value.coerceIn(0f, 1f)
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    val touchHeight = maxOf(44.dp, thumbSize + halo * 2)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(touchHeight)
            .semantics {
                if (description != null) contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                setProgress { v -> finished(v.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val pad = (thumbSize.toPx() / 2 + halo.toPx()).coerceAtMost(size.width / 4f)
                    val v = ((offset.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                    change(v); finished(v)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val pad = (thumbSize.toPx() / 2 + halo.toPx()).coerceAtMost(size.width / 4f)
                        dragging = true
                        dragValue = ((offset.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                        change(dragValue)
                    },
                    onDragEnd = { dragging = false; finished(dragValue) },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change2, amount ->
                        change2.consume()
                        val pad = (thumbSize.toPx() / 2 + halo.toPx()).coerceAtMost(size.width / 4f)
                        dragValue = (dragValue + amount / (size.width - 2 * pad)).coerceIn(0f, 1f)
                        change(dragValue)
                    },
                )
            },
    ) {
        val th = trackHeight.toPx()
        val thumbR = thumbSize.toPx() / 2
        val haloR = thumbR + halo.toPx()
        val left = haloR.coerceAtMost(size.width / 4)
        val width = size.width - left * 2
        val cy = size.height / 2
        val radius = CornerRadius(th / 2, th / 2)
        drawRoundRect(trackColor, Offset(left, cy - th / 2), Size(width, th), radius)
        val fx = left + width * shown
        if (shown > 0f) drawRoundRect(fill, Offset(left, cy - th / 2), Size(fx - left, th), radius)
        if (thumbSize > 0.dp) {
            if (halo > 0.dp) drawCircle(haloColor, haloR, Offset(fx, cy))
            drawCircle(thumbColor, thumbR, Offset(fx, cy))
        }
    }
}
