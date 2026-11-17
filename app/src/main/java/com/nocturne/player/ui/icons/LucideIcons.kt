package com.nocturne.player.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Lucide-style stroke icons (24 viewport, 2px stroke, round caps/joins). */
object Lucide {
    private fun icon(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (filled) SolidColor(Color.White) else null,
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun dot(cx: Float, cy: Float) = "M${cx - 1} ${cy}a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z"

    val Search by lazy { icon("search", "M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", "M21 21l-4.3-4.3") }
    val Sliders by lazy {
        icon("sliders", "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4")
    }
    val Link by lazy {
        icon(
            "link",
            "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
            "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
        )
    }
    val Play by lazy { icon("play", "M7 4.5l12 7.5-12 7.5V4.5z", filled = true) }
    val Pause by lazy { icon("pause", "M6.5 4.5h3v15h-3z", "M14.5 4.5h3v15h-3z", filled = true) }
    val SkipForward by lazy { icon("skip-forward", "M5 4.5l10 7.5-10 7.5V4.5z", "M19 5v14", filled = true) }
    val SkipBack by lazy { icon("skip-back", "M19 19.5L9 12l10-7.5v15z", "M5 19V5", filled = true) }
    val Shuffle by lazy {
        icon(
            "shuffle",
            "M2 18h1.4c1.3 0 2.5-.6 3.3-1.7l6.1-8.6c.7-1.1 2-1.7 3.3-1.7H22",
            "M18 2l4 4-4 4",
            "M2 6h1.9c1.5 0 2.9.9 3.6 2.2",
            "M22 18h-5.9c-1.3 0-2.6-.7-3.3-1.8l-.5-.8",
            "M18 14l4 4-4 4",
        )
    }
    val Repeat by lazy {
        icon("repeat", "M17 2l4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "M7 22l-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3")
    }
    val Heart by lazy {
        icon(
            "heart",
            "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7z",
        )
    }
    val HeartFilled by lazy {
        icon(
            "heart-filled",
            "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7z",
            filled = true,
        )
    }
    val ListPlus by lazy { icon("list-plus", "M11 12H3", "M16 6H3", "M16 18H3", "M18 9v6", "M21 12h-6") }
    val ListMusic by lazy {
        icon("list-music", "M21 15V6", "M18.5 18a2.5 2.5 0 1 0 0-5a2.5 2.5 0 0 0 0 5z", "M12 12H3", "M16 6H3", "M12 18H3")
    }
    val ChevronDown by lazy { icon("chevron-down", "M6 9l6 6 6-6") }
    val ChevronRight by lazy { icon("chevron-right", "M9 18l6-6-6-6") }
    val ArrowLeft by lazy { icon("arrow-left", "M12 19l-7-7 7-7", "M19 12H5") }
    val MoreVertical by lazy { icon("more-vertical", dot(12f, 5f), dot(12f, 12f), dot(12f, 19f)) }
    val MoreHorizontal by lazy { icon("more-horizontal", dot(5f, 12f), dot(12f, 12f), dot(19f, 12f)) }
    val Music by lazy {
        icon("music", "M9 18V5l12-2v13", "M6 15a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M18 13a3 3 0 1 0 0 6a3 3 0 1 0 0-6z")
    }
    val Moon by lazy { icon("moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z") }
    val Download by lazy { icon("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3") }
    val X by lazy { icon("x", "M18 6L6 18", "M6 6l12 12") }
    val Check by lazy { icon("check", "M20 6L9 17l-5-5") }
    val MicVocal by lazy {
        icon(
            "mic-vocal",
            "M11 7.6l-6 8.19a1 1 0 0 0 .1 1.3l.82.82a1 1 0 0 0 1.31.09L15.09 12",
            "M16.5 21.17C15.5 20.5 14.37 20 13 20c-2.06 0-3.93 2.36-6 2-2.07-.36-2.78-3.37-1.5-4.5",
            "M16 2a5 5 0 1 0 0 10a5 5 0 1 0 0-10z",
        )
    }
    val Globe by lazy {
        icon("globe", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z", "M2 12h20", "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z")
    }
    val Plus by lazy { icon("plus", "M5 12h14", "M12 5v14") }
    val Volume by lazy {
        icon("volume-2", "M11 5L6 9H2v6h4l5 4V5z", "M15.54 8.46a5 5 0 0 1 0 7.07", "M19.07 4.93a10 10 0 0 1 0 14.14")
    }
    val Trash by lazy {
        icon(
            "trash-2", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
            "M10 11v6", "M14 11v6",
        )
    }
    val Pencil by lazy { icon("pencil", "M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5z", "M15 5l4 4") }
    val RotateCw by lazy { icon("rotate-cw", "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5") }
    val Grip by lazy {
        icon("grip", dot(9f, 5f), dot(9f, 12f), dot(9f, 19f), dot(15f, 5f), dot(15f, 12f), dot(15f, 19f))
    }
    val Battery by lazy {
        icon(
            "battery-charging",
            "M15 7h1a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2h-2",
            "M6 7H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h1",
            "M22 11v2",
            "M11 7l-3 5h4l-3 5",
        )
    }
    val Power by lazy { icon("power", "M12 2v10", "M18.4 6.6a9 9 0 1 1-12.77.04") }
    val Bell by lazy { icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0") }
    val HardDrive by lazy {
        icon(
            "hard-drive", "M22 12H2",
            "M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
            "M6 16h.01", "M10 16h.01",
        )
    }
    val Info by lazy { icon("info", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z", "M12 16v-4", "M12 8h.01") }
    val Clipboard by lazy {
        icon(
            "clipboard", "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z",
            "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
        )
    }
    val ListEnd by lazy { icon("list-end", "M16 12H3", "M16 6H3", "M10 18H3", "M21 6v10a2 2 0 0 1-2 2h-5", "M16 16l-2 2 2 2") }
    val ListStart by lazy { icon("list-start", "M21 6H3", "M7 12H3", "M7 18H3", "M12 18a5 5 0 0 0 9-3 4.5 4.5 0 0 0-4.5-4.5c-1.33 0-2.54.54-3.41 1.41L11 14", "M11 10v4h4") }
    val AlertCircle by lazy { icon("alert-circle", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z", "M12 8v4", "M12 16h.01") }
}
