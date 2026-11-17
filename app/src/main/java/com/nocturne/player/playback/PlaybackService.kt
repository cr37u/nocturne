package com.nocturne.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nocturne.player.MainActivity
import com.nocturne.player.R
import com.nocturne.player.data.TrackRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Owns the ExoPlayer and the MediaSession. Lives independently of the Activity as a
 * mediaPlayback foreground service, so playback continues with the app in background,
 * the screen off, after swiping the app from recents, and in Doze.
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var tracks: TrackRepository
    @Inject lateinit var stateStore: PlaybackStateStore
    @Inject lateinit var prefs: com.nocturne.player.data.AppPrefs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true, // pause on calls, duck for notifications
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL) // WakeLock only while playing
            .setSkipSilenceEnabled(false)
            .build()
        player.addListener(playerListener)

        val openNowPlaying = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_NOW_PLAYING)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openNowPlaying)
            .setCustomLayout(customLayout())
            .build()

        setMediaNotificationProvider(NocturneNotificationProvider(this))
        restoreQueue()
        startPositionSaver()
        startWidgetProgress()
        com.nocturne.player.i18n.AppLocale.prefs(this).registerOnSharedPreferenceChangeListener(languageListener)
        pushWidget()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    // ---------------------------------------------------------------- language & widgets

    /** In-app language change: re-label shuffle/repeat (rebuilds the notification) and widgets. */
    private val languageListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == com.nocturne.player.i18n.AppLocale.PREF_KEY) onLanguageChanged()
    }

    /** Android 13+ system "App languages" change. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        onLanguageChanged()
    }

    private fun onLanguageChanged() {
        com.nocturne.player.i18n.AppLocale.init(this)
        session?.setCustomLayout(customLayout())
        pushWidget()
    }

    /** Snapshot for the home-screen widgets (track, play state, shuffle/repeat, up next). */
    private fun pushWidget() {
        if (!::player.isInitialized) return
        val item = player.currentMediaItem
        val meta = player.mediaMetadata
        val nextIndex = player.nextMediaItemIndex
        val next = if (nextIndex != C.INDEX_UNSET) player.getMediaItemAt(nextIndex).mediaMetadata else null
        val duration = player.duration.takeIf { it > 0 } ?: (meta.durationMs ?: 0L)
        com.nocturne.player.widget.NocturneWidgetUpdater.push(
            this,
            com.nocturne.player.widget.WidgetState(
                hasMedia = item != null,
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                artworkPath = meta.artworkUri?.path,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = duration,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                nextTitle = next?.title?.toString(),
                nextArtist = next?.artist?.toString(),
            ),
        )
    }

    /** Progress only while playing AND the screen is on — no polling when paused (battery). */
    private fun startWidgetProgress() {
        val power = getSystemService(android.os.PowerManager::class.java)
        scope.launch {
            while (isActive) {
                delay(3_000)
                if (player.isPlaying && power.isInteractive) pushWidget()
            }
        }
    }

    /**
     * App swiped away from recents: stop playback (default), or keep playing in the background
     * when "Redare după închiderea aplicației" is enabled in Settings. Queue/position are saved
     * either way so the next launch resumes where it stopped.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        saveState()
        val idle = !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED
        if (idle || !prefs.keepPlayingWhenClosed) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        saveState()
        com.nocturne.player.i18n.AppLocale.prefs(this).unregisterOnSharedPreferenceChangeListener(languageListener)
        if (::player.isInitialized) {
            // Leave the widgets showing the last track, paused.
            pushWidget()
        }
        scope.cancel()
        session?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- state persistence

    private fun restoreQueue() {
        val saved = stateStore.load() ?: return
        scope.launch {
            val items = tracks.getMany(saved.queue).map { it.toMediaItem() }
            if (items.isEmpty() || player.mediaItemCount > 0) return@launch
            val index = saved.index.coerceIn(0, items.size - 1)
            player.shuffleModeEnabled = saved.shuffle
            player.repeatMode = saved.repeatMode
            player.setMediaItems(items, index, saved.positionMs.coerceAtLeast(0))
            // Not prepared/played: the user (or a media button) decides when to resume.
        }
    }

    private fun startPositionSaver() {
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) saveState()
            }
        }
    }

    private fun saveState() {
        if (!::player.isInitialized) return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (ids.isEmpty()) return
        stateStore.save(ids, player.currentMediaItemIndex, player.currentPosition, player.shuffleModeEnabled, player.repeatMode)
    }

    private val playerListener = object : Player.Listener {
        /** Widgets refresh on real changes only: track, play/pause, shuffle/repeat, metadata, queue. */
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) pushWidget()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = saveState()
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = saveState()
        override fun onIsPlayingChanged(isPlaying: Boolean) = saveState()

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            session?.setCustomLayout(customLayout()); saveState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            session?.setCustomLayout(customLayout()); saveState()
        }

        /** A missing/corrupt file must not stop auto-play: skip to the next track. */
        override fun onPlayerError(error: PlaybackException) {
            val hasNext = player.hasNextMediaItem()
            val index = player.currentMediaItemIndex
            if (player.mediaItemCount > 1) {
                player.removeMediaItem(index)
                player.prepare()
                if (hasNext || player.repeatMode != Player.REPEAT_MODE_OFF) player.play()
            }
        }
    }

    // ---------------------------------------------------------------- custom buttons

    private fun customLayout(): ImmutableList<CommandButton> {
        val shuffleOn = ::player.isInitialized && player.shuffleModeEnabled
        val repeat = if (::player.isInitialized) player.repeatMode else Player.REPEAT_MODE_OFF
        @Suppress("DEPRECATION")
        val shuffle = CommandButton.Builder()
            .setDisplayName(com.nocturne.player.i18n.AppLocale.localized(this).getString(if (shuffleOn) R.string.shuffle_on else R.string.shuffle_off))
            .setIconResId(if (shuffleOn) R.drawable.ic_n_shuffle else R.drawable.ic_n_shuffle_off)
            .setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            .build()
        @Suppress("DEPRECATION")
        val repeatButton = CommandButton.Builder()
            .setDisplayName(
                com.nocturne.player.i18n.AppLocale.localized(this).getString(
                    when (repeat) {
                        Player.REPEAT_MODE_ALL -> R.string.repeat_all
                        Player.REPEAT_MODE_ONE -> R.string.repeat_one
                        else -> R.string.repeat_off
                    }
                )
            )
            .setIconResId(
                when (repeat) {
                    Player.REPEAT_MODE_ALL -> R.drawable.ic_n_repeat
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_n_repeat_one
                    else -> R.drawable.ic_n_repeat_off
                }
            )
            .setSessionCommand(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
            .build()
        return ImmutableList.of(shuffle, repeatButton)
    }

    private suspend fun resolveItems(items: List<MediaItem>): List<MediaItem> {
        val resolved = tracks.getMany(items.map { it.mediaId }).associateBy { it.id }
        return items.mapNotNull { item ->
            resolved[item.mediaId]?.toMediaItem()
                ?: item.takeIf { it.localConfiguration != null && File(it.localConfiguration!!.uri.path ?: "").exists() }
        }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(customLayout())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                CMD_REPEAT -> player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                else -> return Futures.immediateFuture(SessionResult(androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** Controllers only send media ids; resolve the local file URIs from Room. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future { resolveItems(mediaItems).toMutableList() }

        /** Bluetooth play button / system media card after the process was killed. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val saved = stateStore.load()
            val items = saved?.let { tracks.getMany(it.queue).map { t -> t.toMediaItem() } }.orEmpty()
            if (saved != null) {
                player.shuffleModeEnabled = saved.shuffle
                player.repeatMode = saved.repeatMode
            }
            if (items.isEmpty()) {
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
            } else {
                MediaSession.MediaItemsWithStartPosition(
                    items, saved!!.index.coerceIn(0, items.size - 1), saved.positionMs.coerceAtLeast(0),
                )
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootId = if (params?.isRecent == true) RECENT_ROOT else ROOT
            val root = MediaItem.Builder()
                .setMediaId(rootId)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(com.nocturne.player.i18n.AppLocale.localized(this@PlaybackService).getString(R.string.app_name))
                        .setIsBrowsable(true).setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val children = if (parentId == RECENT_ROOT) {
                val saved = stateStore.load()
                val id = saved?.queue?.getOrNull(saved.index)
                listOfNotNull(id?.let { tracks.get(it) }?.toMediaItem())
            } else {
                tracks.getMany(tracks.allIds().toList()).map { it.toMediaItem() }
            }
            val from = (page * pageSize).coerceAtMost(children.size)
            val to = (from + pageSize).coerceAtMost(children.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(children.subList(from, to)), params)
        }
    }

    companion object {
        const val CMD_SHUFFLE = "nocturne.TOGGLE_SHUFFLE"
        const val CMD_REPEAT = "nocturne.CYCLE_REPEAT"
        private const val ROOT = "nocturne_root"
        private const val RECENT_ROOT = "nocturne_recent"
    }
}
