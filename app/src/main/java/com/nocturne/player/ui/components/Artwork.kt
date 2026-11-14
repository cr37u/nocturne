package com.nocturne.player.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.theme.NocturneBrushes
import com.nocturne.player.ui.theme.NocturneColors
import java.io.File

/**
 * Cover image. Without artwork: gradient box (one of 7 variants, by [seed]) with a white
 * 75%-opacity music note. [model] may be a local path, File, Uri or URL.
 */
@Composable
fun Artwork(
    model: Any?,
    seed: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
    placeholderIcon: @Composable (() -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(NocturneBrushes.cover(seed)),
        contentAlignment = Alignment.Center,
    ) {
        val iconSize: Dp = (maxWidth * 0.4f).coerceIn(16.dp, 96.dp)
        if (placeholderIcon != null) placeholderIcon() else Icon(
            Lucide.Music,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(iconSize),
        )
        val data = when (model) {
            is String -> if (model.startsWith("/")) File(model) else model
            else -> model
        }
        if (data != null) {
            AsyncImage(
                model = data,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Animated 3-bar amber equalizer (animates only while [playing]). */
@Composable
fun Equalizer(playing: Boolean, modifier: Modifier = Modifier, color: Color = NocturneColors.Amber) {
    val durations = listOf(520, 680, 440)
    val idle = listOf(0.45f, 0.75f, 0.35f)
    Row(
        modifier = modifier.size(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val heights: List<Float> = if (playing) {
            // The infinite transition only exists (and only ticks) while playing.
            val transition = rememberInfiniteTransition(label = "eq")
            durations.mapIndexed { i, d ->
                val animated by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(d), RepeatMode.Reverse),
                    label = "bar$i",
                )
                animated
            }
        } else idle
        heights.forEachIndexed { i, target ->
            val height by animateFloatAsState(target, label = "barH$i")
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
