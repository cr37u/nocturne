package com.nocturne.player

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.nocturne.player.download.DownloadNotifications
import com.nocturne.player.youtube.NewPipeDownloader
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import javax.inject.Inject

@HiltAndroidApp
class NocturneApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var okHttpClient: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        com.nocturne.player.i18n.AppLocale.init(this)
        // Track metadata must NOT depend on the UI language: NewPipe always uses this fixed
        // localization, so the title YouTube returns is stable whatever language the app shows.
        // (Changing it would only affect new downloads; saved titles are never re-fetched.)
        NewPipe.init(NewPipeDownloader(okHttpClient), METADATA_LOCALIZATION, METADATA_COUNTRY)
        DownloadNotifications.ensureChannel(this)
    }

    /** Android 13+: the language can also change from system Settings → App languages. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val before = com.nocturne.player.i18n.AppLocale.locale
        com.nocturne.player.i18n.AppLocale.init(this)
        if (before != com.nocturne.player.i18n.AppLocale.locale) {
            com.nocturne.player.i18n.AppLocale.refreshSystemSurfaces(this)
        }
    }

    companion object {
        /** Fixed, UI-independent YouTube metadata localization (see onCreate). */
        val METADATA_LOCALIZATION = Localization("ro", "RO")
        val METADATA_COUNTRY = ContentCountry("RO")
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(okHttpClient)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(64L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}
