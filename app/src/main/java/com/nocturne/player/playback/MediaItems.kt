package com.nocturne.player.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.nocturne.player.data.db.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

const val EXTRA_DURATION = "nocturne.duration"

fun TrackEntity.toMediaItem(): MediaItem {
    val artwork = thumbnailPath?.let { Uri.fromFile(File(it)) }
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.fromFile(File(filePath)))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumArtist(artist)
                .setDisplayTitle(title)
                .setArtworkUri(artwork)
                .setDurationMs(durationMs.takeIf { it > 0 })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { putLong(EXTRA_DURATION, durationMs) })
                .build()
        )
        .build()
}

/** Where the current queue was started from ("SE REDĂ DIN PLAYLIST" + name). */
data class PlayContext(val label: String, val isPlaylist: Boolean)

/**
 * Persists queue, index, position, shuffle/repeat and play context so that playback can be
 * restored after the process is killed (and for media resumption from the system UI).
 */
@Singleton
class PlaybackStateStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("playback_state", Context.MODE_PRIVATE)

    data class Saved(
        val queue: List<String>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int,
    )

    private val _context = MutableStateFlow(
        PlayContext(prefs.getString("ctx_label", "") ?: "", prefs.getBoolean("ctx_playlist", false))
    )
    val playContext: StateFlow<PlayContext> = _context.asStateFlow()

    fun setContext(ctx: PlayContext) {
        _context.value = ctx
        prefs.edit().putString("ctx_label", ctx.label).putBoolean("ctx_playlist", ctx.isPlaylist).apply()
    }

    fun save(queue: List<String>, index: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        prefs.edit()
            .putString("queue", queue.joinToString("\n"))
            .putInt("index", index)
            .putLong("position", positionMs)
            .putBoolean("shuffle", shuffle)
            .putInt("repeat", repeatMode)
            .apply()
    }

    fun load(): Saved? {
        val raw = prefs.getString("queue", null)?.takeIf { it.isNotBlank() } ?: return null
        return Saved(
            queue = raw.split("\n").filter { it.isNotBlank() },
            index = prefs.getInt("index", 0),
            positionMs = prefs.getLong("position", 0L),
            shuffle = prefs.getBoolean("shuffle", false),
            repeatMode = prefs.getInt("repeat", 0),
        )
    }
}
