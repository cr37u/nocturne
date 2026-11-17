package com.nocturne.player.widget

import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.nocturne.player.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Widget buttons → the existing PlaybackService, through a short-lived MediaController.
 * Connecting binds (and if needed creates) the service, which restores the saved queue, so the
 * buttons work with the app UI closed or after the process was killed.
 */
class WidgetPlayerAction : ActionCallback {

    override suspend fun onAction(receiverContext: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Glance hands us the BroadcastReceiver's restricted context, which may not bind to services
        // (ReceiverCallNotAllowedException) — the MediaController must use the application context.
        val context = receiverContext.applicationContext
        val command = parameters[COMMAND] ?: return
        // MediaController must be used on the thread whose Looper it was built with (main).
        withContext(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controller = withTimeoutOrNull(5_000) { MediaController.Builder(context, token).buildAsync().await() }
                ?: return@withContext
            try {
                // A freshly created service restores the queue asynchronously: give it a moment.
                withTimeoutOrNull(3_000) { while (controller.mediaItemCount == 0) delay(100) }
                run(controller, command)
                delay(400) // let the command reach the session before disconnecting
            } finally {
                controller.release()
            }
        }
    }

    private fun run(c: MediaController, command: String) {
        when (command) {
            PLAY_PAUSE -> if (c.isPlaying) c.pause() else {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
                c.play()
            }
            NEXT -> {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                c.seekToNext()
            }
            PREVIOUS -> {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                if (c.currentPosition > 3_000) c.seekTo(0) else c.seekToPrevious()
            }
            SHUFFLE -> c.sendCustomCommand(SessionCommand(PlaybackService.CMD_SHUFFLE, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY)
            REPEAT -> c.sendCustomCommand(SessionCommand(PlaybackService.CMD_REPEAT, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY)
        }
    }

    companion object {
        val COMMAND = ActionParameters.Key<String>("command")
        const val PLAY_PAUSE = "play_pause"
        const val NEXT = "next"
        const val PREVIOUS = "previous"
        const val SHUFFLE = "shuffle"
        const val REPEAT = "repeat"
    }
}
