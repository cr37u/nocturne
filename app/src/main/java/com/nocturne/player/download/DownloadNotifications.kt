package com.nocturne.player.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nocturne.player.MainActivity
import com.nocturne.player.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.nocturne.player.di.AppScope
import kotlinx.coroutines.CoroutineScope
import android.content.BroadcastReceiver

object DownloadNotifications {
    const val CHANNEL_ID = "downloads"
    const val NOTIFICATION_ID = 2001
    private const val AMBER = 0xFFF5B942.toInt()
    private var largeIcon: Bitmap? = null

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_downloads_desc)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun gradientIcon(context: Context): Bitmap? {
        largeIcon?.let { return it }
        val d = ContextCompat.getDrawable(context, R.drawable.bg_gradient_icon) ?: return null
        val size = (64 * context.resources.displayMetrics.density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        largeIcon = bmp
        return bmp
    }

    fun build(context: Context, stats: DownloadStats): Notification {
        ensureChannel(context)
        // "Se descarcă 4 melodii" = tracks downloading in parallel right now.
        val downloading = stats.active
        val title = when {
            stats.total == 0 || downloading == 0 -> context.getString(R.string.notif_preparing)
            downloading == 1 -> context.getString(R.string.notif_downloading_one)
            else -> context.getString(R.string.notif_downloading, downloading)
        }
        val subtitle = if (stats.batchName != null) {
            context.getString(R.string.notif_subtitle, stats.done, stats.total, stats.batchName)
        } else context.getString(R.string.notif_subtitle_plain, stats.done, stats.total)
        val percent = (stats.fraction * 100).toInt()
        val percentText = SpannableString("$percent%").apply {
            setSpan(ForegroundColorSpan(AMBER), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val openIntent = PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_DOWNLOADS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_n_download)
            .setLargeIcon(gradientIcon(context))
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText(percentText)
            .setColor(AMBER)
            .setProgress(100, percent, stats.total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_n_pause_outline, context.getString(R.string.notif_pause), action(context, DownloadActionReceiver.ACTION_PAUSE))
            .addAction(R.drawable.ic_n_close, context.getString(R.string.notif_cancel), action(context, DownloadActionReceiver.ACTION_CANCEL))
            .build()
    }

    private fun action(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, DownloadActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: DownloadRepository
    @Inject @AppScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> repository.pauseAll()
                    ACTION_CANCEL -> repository.cancelAll()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.nocturne.player.download.PAUSE"
        const val ACTION_CANCEL = "com.nocturne.player.download.CANCEL"
    }
}
