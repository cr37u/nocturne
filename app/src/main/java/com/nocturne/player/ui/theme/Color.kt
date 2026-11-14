package com.nocturne.player.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object NocturneColors {
    val Background = Color(0xFF0A0A14)
    val Surface = Color(0xFF141426)
    val SurfaceElevated = Color(0xFF1C1C33)
    val MiniPlayerInner = Color(0xFF161630)
    val ProgressTrack = Color(0xFF2A2A48)
    val TextPrimary = Color(0xFFF1F1F7)
    val TextSecondary = Color(0xFF9A9AB8)
    val OnGradientSecondary = Color(0xFFD4D4EA)
    val GradientStart = Color(0xFF1E40AF)
    val GradientEnd = Color(0xFF3B0764)
    val BrightStart = Color(0xFF3B6CF6)
    val BrightEnd = Color(0xFF7C3AED)
    val Amber = Color(0xFFF5B942)
    val AmberBackground = Color(0x29F5B942) // rgba(245,185,66,0.16)
    val Error = Color(0xFFF87171)
    val OnGradientButton = Color(0x1FF1F1F7) // rgba(241,241,247,0.12)
    val PlayIcon = Color(0xFF3B0764)
    val Shadow = Color(0x735B21B6) // rgba(91,33,182,0.45)
    val Glow = Color(0xB37C3AED) // rgba(124,58,237,0.7)
    val Scrim = Color(0x990A0A14) // rgba(10,10,20,0.6)
}

/**
 * CSS-style `linear-gradient(<angle>deg, …)`: 0° points up, 90° to the right; the gradient line
 * length matches CSS so corners get the exact first/last colors.
 */
class CssLinearGradient(
    private val angleDeg: Float,
    private val colors: List<Color>,
    private val stops: List<Float>? = null,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        return LinearGradientShader(
            from = Offset(cx - dx * half, cy - dy * half),
            to = Offset(cx + dx * half, cy + dy * half),
            colors = colors,
            colorStops = stops,
        )
    }

    override fun equals(other: Any?) =
        other is CssLinearGradient && other.angleDeg == angleDeg && other.colors == colors && other.stops == stops

    override fun hashCode() = (angleDeg.hashCode() * 31 + colors.hashCode()) * 31 + stops.hashCode()
}

object NocturneBrushes {
    /** Primary gradient (135°) for buttons, logo, covers. */
    val Primary: Brush = CssLinearGradient(135f, listOf(NocturneColors.GradientStart, NocturneColors.GradientEnd))

    /** Bright gradient for thin bars (90°). */
    val BrightHorizontal: Brush = CssLinearGradient(90f, listOf(NocturneColors.BrightStart, NocturneColors.BrightEnd))

    /** Bright gradient for borders (135°). */
    val BrightDiagonal: Brush = CssLinearGradient(135f, listOf(NocturneColors.BrightStart, NocturneColors.BrightEnd))

    /** Now Playing background: 170°, #1E40AF 0% → #3B0764 55% → #0A0A14 100%. */
    val NowPlaying: Brush = CssLinearGradient(
        170f,
        listOf(NocturneColors.GradientStart, NocturneColors.GradientEnd, NocturneColors.Background),
        listOf(0f, 0.55f, 1f),
    )

    val BottomFade: Brush = Brush.verticalGradient(listOf(Color.Transparent, NocturneColors.Background))

    private val coverPairs = listOf(
        Color(0xFF1E40AF) to Color(0xFF3B0764),
        Color(0xFF312E81) to Color(0xFF6D28D9),
        Color(0xFF1D4ED8) to Color(0xFF4C1D95),
        Color(0xFF0F172A) to Color(0xFF4338CA),
        Color(0xFF3730A3) to Color(0xFF581C87),
        Color(0xFF1E3A8A) to Color(0xFF7E22CE),
        Color(0xFF172554) to Color(0xFF5B21B6),
    )
    private val covers = coverPairs.map { CssLinearGradient(135f, listOf(it.first, it.second)) }

    /** Deterministic artwork-less cover gradient for a given seed (track/playlist id). */
    fun cover(seed: String?): Brush = covers[((seed?.hashCode() ?: 0) and Int.MAX_VALUE) % covers.size]
}
