package com.nocturne.player.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nocturne.player.ui.theme.NocturneBrushes
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType

/** Click with the design press feedback: scale 0.94 + ripple, optional haptic tick. */
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    haptic: Boolean = false,
    role: Role = Role.Button,
    label: String? = null,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(120), label = "press")
    val view = LocalView.current
    this
        .scale(scale)
        .combinedClickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            role = role,
            onClickLabel = label,
            onLongClick = onLongClick?.let { l ->
                {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    l()
                }
            },
            onClick = {
                if (haptic) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
        )
}

/** 44×44 icon button, radius 14, bg #141426 (or translucent on gradient screens). */
@Composable
fun IconButton44(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    shape: Shape = NocturneShapes.IconButton,
    background: Color = NocturneColors.Surface,
    tint: Color = NocturneColors.TextPrimary,
    enabled: Boolean = true,
    haptic: Boolean = false,
    onGradient: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (onGradient) NocturneColors.OnGradientButton else background)
            .pressable(onClick = onClick, enabled = enabled, haptic = haptic, label = contentDescription)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Primary-gradient button (#1E40AF → #3B0764, 135°). */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 44.dp,
    shape: Shape = NocturneShapes.Button,
    enabled: Boolean = true,
    brush: Brush = NocturneBrushes.Primary,
    horizontalPadding: Dp = 18.dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(brush)
            .pressable(onClick = onClick, enabled = enabled)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = NocturneColors.TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = NocturneType.Button14, color = NocturneColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Circular button (play/pause etc). */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    background: Color? = NocturneColors.TextPrimary,
    brush: Brush? = null,
    tint: Color = NocturneColors.PlayIcon,
    haptic: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                when {
                    brush != null -> Modifier.background(brush)
                    background != null -> Modifier.background(background)
                    else -> Modifier
                }
            )
            .pressable(onClick = onClick, haptic = haptic, label = contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}
