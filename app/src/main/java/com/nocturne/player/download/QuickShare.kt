package com.nocturne.player.download

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nocturne.player.R
import com.nocturne.player.i18n.AppLocale
import com.nocturne.player.youtube.LinkResolver
import com.nocturne.player.youtube.YouTubeLinks
import com.nocturne.player.youtube.toErrorKind
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * "Descarcă în Nocturne" share target: Share from the YouTube app / browser starts the download
 * right away in the background, without opening the app. (The plain "Nocturne" target still opens
 * "Adaugă muzică".) Invisible activity: it only hands the link to [QuickAddWorker] and closes.
 */
class QuickShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString
        val url = YouTubeLinks.extractUrl(text)
        val ctx = AppLocale.localized(this)
        if (url == null || YouTubeLinks.parse(url) == null) {
            Toast.makeText(this, ctx.getString(R.string.error_invalid_link), Toast.LENGTH_LONG).show()
        } else {
            QuickAddWorker.start(this, url)
            Toast.makeText(this, ctx.getString(R.string.quick_download_started), Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        private const val SHORTCUT_ID = "quick_download"
        const val SHARE_CATEGORY = "com.nocturne.player.category.QUICK_DOWNLOAD"

        /** Puts "Descarcă în Nocturne" in the top (Direct Share) row of the share sheet. */
        fun publishShareShortcut(context: Context) {
            runCatching {
                val label = AppLocale.localized(context).getString(R.string.share_download_label)
                val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(Intent(context, QuickShareActivity::class.java).setAction(Intent.ACTION_SEND))
                    .setCategories(setOf(SHARE_CATEGORY))
                    .setLongLived(true)
                    .build()
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            }
        }
    }
}

/** Resolves the shared link (video or whole playlist) and queues the downloads. */
@HiltWorker
class QuickAddWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val resolver: LinkResolver,
    private val downloads: DownloadRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val link = YouTubeLinks.parse(url) ?: return Result.failure()
        val ctx = AppLocale.localized(applicationContext)
        return try {
            val resolved = resolver.resolve(link)
            val added = downloads.enqueue(resolved)
            if (added == 0) {
                notify(ctx, ctx.getString(if (resolved.isPlaylist) R.string.playlist_imported else R.string.all_in_library))
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (runAttemptCount < 2) return Result.retry()
            notify(ctx, ctx.getString(e.toErrorKind().message))
            Result.failure()
        }
    }

    private fun notify(ctx: Context, text: String) {
        DownloadNotifications.ensureChannel(applicationContext)
        val n = NotificationCompat.Builder(applicationContext, DownloadNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_moon)
            .setContentTitle(ctx.getString(R.string.share_download_label))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, n) }
    }

    companion object {
        private const val KEY_URL = "url"
        private const val NOTIFICATION_ID = 2002

        fun start(context: Context, url: String) {
            val request = OneTimeWorkRequestBuilder<QuickAddWorker>()
                .setInputData(workDataOf(KEY_URL to url))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
