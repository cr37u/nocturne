package com.nocturne.player.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.nocturne.player.data.db.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class QueueEntry(
    /** Stable key: media id + occurrence number (the same track may be queued twice). */
    val key: String,
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val durationMs: Long,
)

data class PlayerUiState(
    val connected: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artworkUri: Uri? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
) {
    val hasMedia get() = mediaId != null
    val progress get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * UI-side bridge to [PlaybackService] through a Media3 [MediaController].
 * The controller lives as long as the process; playback itself lives in the service.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateStore: PlaybackStateStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val playContext: StateFlow<PlayContext> get() = stateStore.playContext

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val _volume = MutableStateFlow(readVolume())
    val volume: StateFlow<Float> = _volume.asStateFlow()

    fun connect() {
        if (controller != null || future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    this@PlayerConnection.controller = null
                    future = null
                    _state.update { it.copy(connected = false) }
                }
            })
            .buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) {
                future = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            refresh()
            pending.toList().forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) block(c) else {
            pending += block
            connect()
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val meta = c.mediaMetadata
        val seen = HashMap<String, Int>()
        val queue = (0 until c.mediaItemCount).map { i ->
            val mi = c.getMediaItemAt(i)
            val occurrence = seen.merge(mi.mediaId, 1, Int::plus)!!
            QueueEntry(
                key = "${mi.mediaId}#$occurrence",
                mediaId = mi.mediaId,
                title = mi.mediaMetadata.title?.toString().orEmpty(),
                artist = mi.mediaMetadata.artist?.toString().orEmpty(),
                artworkUri = mi.mediaMetadata.artworkUri,
                durationMs = mi.mediaMetadata.durationMs ?: 0L,
            )
        }
        val duration = c.duration.takeIf { it > 0 } ?: (meta.durationMs ?: 0L)
        _state.value = PlayerUiState(
            connected = true,
            mediaId = item?.mediaId,
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            artworkUri = meta.artworkUri,
            durationMs = duration,
            positionMs = c.currentPosition.coerceAtLeast(0),
            isPlaying = c.isPlaying,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue,
            currentIndex = c.currentMediaItemIndex,
        )
        if (c.isPlaying) startTicker() else stopTicker()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    _state.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0),
                            durationMs = c.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                        )
                    }
                }
                _volume.value = readVolume()
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel(); ticker = null
    }

    // ------------------------------------------------------------------ commands

    fun playTracks(tracks: List<TrackEntity>, startIndex: Int = 0, shuffle: Boolean = false, context: PlayContext) {
        if (tracks.isEmpty()) return
        stateStore.setContext(context)
        val items = tracks.map { it.toMediaItem() }
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle) items.indices.random() else startIndex.coerceIn(0, items.size - 1)
            c.setMediaItems(items, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlayPause() = withController { c ->
        when {
            c.isPlaying -> c.pause()
            else -> {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
                c.play()
            }
        }
    }

    fun play() = withController { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun next() = withController { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.seekToNext()
    }

    /** Restarts the track when more than 3 s in, otherwise goes to the previous one. */
    fun previous() = withController { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.currentPosition > 3_000) c.seekTo(0) else c.seekToPrevious()
    }

    fun seekTo(positionMs: Long) = withController { c ->
        c.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun toggleShuffle() = withController { c -> c.shuffleModeEnabled = !c.shuffleModeEnabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playQueueIndex(index: Int) = withController { c ->
        if (index !in 0 until c.mediaItemCount) return@withController
        c.seekToDefaultPosition(index)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun removeQueueIndex(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun playNext(track: TrackEntity) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItem(track.toMediaItem()); c.prepare(); c.play()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
        }
    }

    fun addToQueue(track: TrackEntity) = withController { c ->
        c.addMediaItem(track.toMediaItem())
        if (c.mediaItemCount == 1) c.prepare()
    }

    /**
     * Edited title/artist → every queued occurrence. replaceMediaItem keeps the same file URI, so
     * ExoPlayer updates the metadata in place without interrupting playback; the session then
     * refreshes the notification, lock screen (MediaMetadata) and widgets.
     */
    fun updateMetadata(track: TrackEntity) = withController { c ->
        for (i in 0 until c.mediaItemCount) {
            if (c.getMediaItemAt(i).mediaId == track.id) c.replaceMediaItem(i, track.toMediaItem())
        }
    }

    /** Removes every occurrence of a deleted track from the queue. */
    fun removeTrack(mediaId: String) = withController { c ->
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId == mediaId) c.removeMediaItem(i)
        }
    }

    // ------------------------------------------------------------------ volume

    private fun readVolume(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun refreshVolume() {
        _volume.value = readVolume()
    }

    fun setVolume(fraction: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (fraction.coerceIn(0f, 1f) * max).let { kotlin.math.round(it).toInt() }
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        _volume.value = fraction.coerceIn(0f, 1f)
    }
}
