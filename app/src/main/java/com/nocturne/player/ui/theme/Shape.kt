package com.nocturne.player.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object NocturneShapes {
    val IconButton = RoundedCornerShape(14.dp)
    val Input = RoundedCornerShape(20.dp)
    val Card = RoundedCornerShape(20.dp)
    val CardLarge = RoundedCornerShape(22.dp)
    val CoverSmall = RoundedCornerShape(14.dp)
    val Cover = RoundedCornerShape(16.dp)
    val CoverGrid = RoundedCornerShape(22.dp)
    val CoverNowPlaying = RoundedCornerShape(28.dp)
    val MiniPlayer = RoundedCornerShape(24.dp)
    val MiniPlayerInner = RoundedCornerShape(23.dp)
    val Button = RoundedCornerShape(16.dp)
    val CurrentRow = RoundedCornerShape(18.dp)
    val Thumb12 = RoundedCornerShape(12.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Pill = CircleShape
}

val NocturneMaterialShapes = Shapes(
    extraSmall = NocturneShapes.IconButton,
    small = NocturneShapes.IconButton,
    medium = NocturneShapes.Card,
    large = NocturneShapes.CardLarge,
    extraLarge = RoundedCornerShape(28.dp),
)
