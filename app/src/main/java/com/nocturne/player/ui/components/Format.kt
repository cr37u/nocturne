package com.nocturne.player.ui.components

import java.util.Locale

private val RO = Locale("ro", "RO")

/** 238000 → "3:58", 3_725_000 → "1:02:05". */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(RO, "%d:%02d:%02d", h, m, s) else String.format(RO, "%d:%02d", m, s)
}

/** 5_520_000 → "1 h 32 min" ; 2_700_000 → "45 min". */
fun formatTotalDuration(ms: Long): String {
    val minutes = ms / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        else -> "${m.coerceAtLeast(1)} min"
    }
}

/** Bytes → "3,6" (MB with Romanian decimal comma). */
fun formatMbNumber(bytes: Long): String = String.format(RO, "%.1f", bytes / 1_048_576.0)

fun formatMb(bytes: Long): String = "${formatMbNumber(bytes)} MB"

fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(RO, "%.1f GB", bytes / 1_073_741_824.0)
    else -> formatMb(bytes)
}

fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1_048_576 -> String.format(RO, "%.1f MB/s", bytesPerSecond / 1_048_576.0)
    else -> String.format(RO, "%d KB/s", bytesPerSecond / 1024)
}
