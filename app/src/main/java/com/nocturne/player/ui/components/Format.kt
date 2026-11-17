package com.nocturne.player.ui.components

import com.nocturne.player.R
import com.nocturne.player.i18n.AppLocale
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

// All formatters follow the selected app language (AppLocale), not the phone language:
// "4,2 MB/s" in RO/RU, "4.2 MB/s" in EN, "1 ч 32 мин" in RU, "12 nov." / "Nov 12" / "12 нояб.".

private val locale get() = AppLocale.locale
private val res get() = AppLocale.res

/** 238000 → "3:58", 3_725_000 → "1:02:05" (digits are the same in every supported language). */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s) else String.format(locale, "%d:%02d", m, s)
}

/** 5_520_000 → "1 h 32 min" (RO/EN) / "1 ч 32 мин" (RU); 2_700_000 → "45 min". */
fun formatTotalDuration(ms: Long): String {
    val minutes = ms / 60_000
    val h = (minutes / 60).toInt()
    val m = (minutes % 60).toInt()
    return when {
        h > 0 && m > 0 -> res.getString(R.string.fmt_hours_minutes, h, m)
        h > 0 -> res.getString(R.string.fmt_hours, h)
        else -> res.getString(R.string.fmt_minutes, m.coerceAtLeast(1))
    }
}

/** Bytes → "3,6" (RO/RU) / "3.6" (EN). */
fun formatMbNumber(bytes: Long): String = String.format(locale, "%.1f", bytes / 1_048_576.0)

fun formatMb(bytes: Long): String = res.getString(R.string.fmt_mb, formatMbNumber(bytes))

fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> res.getString(R.string.fmt_gb, String.format(locale, "%.1f", bytes / 1_073_741_824.0))
    else -> formatMb(bytes)
}

fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1_048_576 -> res.getString(R.string.fmt_speed_mb, String.format(locale, "%.1f", bytesPerSecond / 1_048_576.0))
    else -> res.getString(R.string.fmt_speed_kb, String.format(locale, "%d", bytesPerSecond / 1024))
}

/** Short date in the app language: "12 nov." (RO), "Nov 12" (EN), "12 нояб." (RU); adds the year if not this year. */
fun formatShortDate(epochMs: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = epochMs }
    val sameYear = then.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, if (sameYear) "dMMM" else "dMMMyyyy")
    return runCatching { SimpleDateFormat(pattern, locale).format(Date(epochMs)) }
        .getOrElse { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(epochMs)) }
}
