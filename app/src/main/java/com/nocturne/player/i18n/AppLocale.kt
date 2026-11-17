package com.nocturne.player.i18n

import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.nocturne.player.R
import com.nocturne.player.download.DownloadNotifications
import com.nocturne.player.playback.NocturneNotificationProvider
import com.nocturne.player.widget.NocturneWidgetUpdater
import java.util.Locale

/**
 * Per-app language (RO default, EN, RU, or "system").
 *
 * The UI follows AppCompatDelegate.setApplicationLocales (Android 13+ LocaleManager, older
 * versions through autoStoreLocales). Surfaces that are not Activities — the playback
 * service, notifications, home-screen widgets — use [localized], because on Android < 13 the
 * application context keeps the system language.
 */
object AppLocale {
    const val SYSTEM = ""
    val SUPPORTED = listOf("ro", "en", "ru")
    private const val PREFS = "nocturne_prefs"
    private const val KEY = "language"
    const val FALLBACK = "ro"
    /** Stored value for "Limba sistemului" (distinguishes it from "never chosen" → Română). */
    private const val STORED_SYSTEM = "system"

    @Volatile var locale: Locale = Locale(FALLBACK)
        private set

    @Volatile private var resources: Resources? = null

    /** Localized resources for code that has no Context (formatters). */
    val res: Resources get() = resources ?: Resources.getSystem()

    fun init(context: Context) {
        val app = context.applicationContext
        // Română is the default: until the user picks something, apply "ro" explicitly
        // (otherwise a phone set to another supported language would show that one).
        if (!prefs(app).contains(KEY)) {
            prefs(app).edit().putString(KEY, FALLBACK).commit()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(FALLBACK))
        }
        locale = Locale.forLanguageTag(effectiveTag(app))
        resources = localized(app).resources
    }

    /** "" (system), "ro", "en" or "ru" — what the user picked. */
    fun selectedTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+: the system "App languages" page may have changed it.
            val tags = AppCompatDelegate.getApplicationLocales()
            if (!tags.isEmpty) return tags[0]?.language?.takeIf { it in SUPPORTED } ?: SYSTEM
            return SYSTEM
        }
        val stored = prefs(context).getString(KEY, FALLBACK) ?: FALLBACK
        return if (stored == STORED_SYSTEM) SYSTEM else stored
    }

    /** Language actually shown: the selection, or the phone language if supported, else Română. */
    fun effectiveTag(context: Context): String {
        val selected = selectedTag(context)
        if (selected in SUPPORTED) return selected
        val system = Resources.getSystem().configuration.locales[0].language
        return if (system in SUPPORTED) system else FALLBACK
    }

    fun setLanguage(context: Context, tag: String) {
        val app = context.applicationContext
        prefs(app).edit().putString(KEY, tag.ifEmpty { STORED_SYSTEM }).commit()
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
        init(app)
        refreshSystemSurfaces(app)
    }

    /** A Context whose resources use the app language (works on every Android version). */
    fun localized(context: Context): Context {
        val loc = Locale.forLanguageTag(effectiveTag(context))
        val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList(loc)) }
        return context.createConfigurationContext(config)
    }

    /**
     * Re-labels everything outside the Activity: notification channel names, the playback
     * notification (the service listens for the language key), and the home-screen widgets.
     */
    fun refreshSystemSurfaces(context: Context) {
        val app = context.applicationContext
        val l = localized(app)
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.getNotificationChannel(DownloadNotifications.CHANNEL_ID)?.let {
            it.name = l.getString(R.string.channel_downloads)
            it.description = l.getString(R.string.channel_downloads_desc)
            nm.createNotificationChannel(it)
        }
        nm.getNotificationChannel(NocturneNotificationProvider.CHANNEL_ID)?.let {
            it.name = l.getString(R.string.channel_playback)
            it.description = l.getString(R.string.channel_playback_desc)
            nm.createNotificationChannel(it)
        }
        NocturneWidgetUpdater.requestUpdate(app)
    }

    fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    const val PREF_KEY = KEY
}
