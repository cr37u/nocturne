package com.nocturne.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nocturne.player.R

/** Manrope (bundled variable font) at weights 200/400/600/700/800. */
@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(
    Font(R.font.manrope_variable, FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
    Font(R.font.manrope_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

private fun style(size: Int, weight: FontWeight, letterSpacing: TextUnit = 0.em, lineHeightMul: Float = 1.3f) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeightMul).sp,
    letterSpacing = letterSpacing,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Nocturne text styles. */
object NocturneType {
    val Brand = style(22, FontWeight.ExtraBold, (-0.02).em)
    val ScreenTitle = style(26, FontWeight.ExtraBold, (-0.02).em)
    val Header22 = style(22, FontWeight.ExtraBold, (-0.02).em)
    val NowPlayingTitle = style(24, FontWeight.ExtraBold, (-0.01).em)
    val NowPlayingArtist = style(16, FontWeight.Normal)
    val Section = style(17, FontWeight.Bold)
    val TrackTitle = style(15, FontWeight.Bold)
    val Body15 = style(15, FontWeight.Normal)
    val Body16Bold = style(16, FontWeight.Bold)
    val Meta = style(13, FontWeight.Normal)
    val MetaSemi = style(13, FontWeight.SemiBold)
    val Button14 = style(14, FontWeight.Bold)
    val Title14 = style(14, FontWeight.Bold)
    val Overline11 = style(11, FontWeight.Bold, 0.12.em)
    val Overline12 = style(12, FontWeight.Bold, 0.06.em)
    val Badge11 = style(11, FontWeight.Bold)
    val Pill = style(14, FontWeight.SemiBold)
    val PillActive = style(14, FontWeight.Bold)
    val Light = style(15, FontWeight.ExtraLight)
}

val NocturneTypography = Typography(
    displayLarge = NocturneType.ScreenTitle,
    headlineMedium = NocturneType.ScreenTitle,
    headlineSmall = NocturneType.Header22,
    titleLarge = NocturneType.Header22,
    titleMedium = NocturneType.Section,
    titleSmall = NocturneType.TrackTitle,
    bodyLarge = NocturneType.Body15,
    bodyMedium = NocturneType.Body15,
    bodySmall = NocturneType.Meta,
    labelLarge = NocturneType.Button14,
    labelMedium = NocturneType.MetaSemi,
    labelSmall = NocturneType.Badge11,
)
