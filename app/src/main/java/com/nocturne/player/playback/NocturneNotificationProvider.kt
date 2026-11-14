package com.nocturne.player.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
import com.nocturne.player.R

/**
 * MediaStyle "mini-player" notification: artwork, title, artist, shuffle · previous ·
 * play/pause · next · repeat, colorized with the Nocturne gradient tone. The seek bar on
 * Android 10+ and the lock-screen / Quick Settings media card come from the MediaSession.
 */
class NocturneNotificationProvider(private val context: Context) : MediaNotification.Provider {

    private var cachedArtKey: String? = null
    private var cachedArt: Bitmap? = null

    init {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_playback_desc)
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val player = mediaSession.player
        val metadata = player.mediaMetadata
        val playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED

        val shuffleButton = customLayout.firstOrNull { it.sessionCommand?.customAction == PlaybackService.CMD_SHUFFLE }
        val repeatButton = customLayout.firstOrNull { it.sessionCommand?.customAction == PlaybackService.CMD_REPEAT }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        val compact = ArrayList<Int>()
        var index = 0

        shuffleButton?.let {
            builder.addAction(actionFactory.createCustomActionFromCustomCommandButton(mediaSession, it)); index++
        }
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession, IconCompat.createWithResource(context, R.drawable.ic_n_prev),
                context.getString(R.string.cd_previous), Player.COMMAND_SEEK_TO_PREVIOUS,
            )
        ); compact += index++
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, if (playing) R.drawable.ic_n_pause else R.drawable.ic_n_play),
                context.getString(if (playing) R.string.cd_pause else R.string.cd_play),
                Player.COMMAND_PLAY_PAUSE,
            )
        ); compact += index++
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession, IconCompat.createWithResource(context, R.drawable.ic_n_next),
                context.getString(R.string.cd_next), Player.COMMAND_SEEK_TO_NEXT,
            )
        ); compact += index++
        repeatButton?.let {
            builder.addAction(actionFactory.createCustomActionFromCustomCommandButton(mediaSession, it)); index++
        }

        val style = MediaStyleNotificationHelper.MediaStyle(mediaSession)
            .setShowActionsInCompactView(*compact.toIntArray())

        builder
            .setStyle(style)
            .setSmallIcon(R.drawable.ic_stat_moon)
            .setContentTitle(metadata.title ?: metadata.displayTitle)
            .setContentText(metadata.artist)
            .setLargeIcon(artwork(metadata.artworkUri?.path))
            .setColorized(true)
            .setColor(NOTIFICATION_COLOR)
            .setContentIntent(mediaSession.sessionActivity)
            .setDeleteIntent(actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_STOP.toLong()))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(playing)

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean = false

    /** Decodes the local (already downloaded) thumbnail, center-cropped to a square. */
    private fun artwork(path: String?): Bitmap? {
        if (path == null) return null
        if (path == cachedArtKey) return cachedArt
        val bmp = runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= 512 && opts.outHeight / (sample * 2) >= 512) sample *= 2
            val src = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val side = minOf(src.width, src.height)
            Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        }.getOrNull()
        cachedArtKey = path
        cachedArt = bmp
        return bmp
    }

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1001
        /** Mid-tone of the #1E40AF → #3B0764 gradient. */
        val NOTIFICATION_COLOR = 0xFF2C2489.toInt()
    }
}
