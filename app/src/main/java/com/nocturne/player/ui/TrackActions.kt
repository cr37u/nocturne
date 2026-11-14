package com.nocturne.player.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nocturne.player.R
import com.nocturne.player.data.PlaylistRepository
import com.nocturne.player.data.TrackRepository
import com.nocturne.player.data.db.PlaylistEntity
import com.nocturne.player.data.db.TrackEntity
import com.nocturne.player.playback.PlayerConnection
import com.nocturne.player.ui.components.AddToPlaylistSheet
import com.nocturne.player.ui.components.ConfirmDialog
import com.nocturne.player.ui.components.LocalSnackbar
import com.nocturne.player.ui.components.NameDialog
import com.nocturne.player.ui.components.TrackActionsSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shared track operations: favorite, delete, playlists, queue. */
@HiltViewModel
class TrackActionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tracks: TrackRepository,
    private val playlistRepo: PlaylistRepository,
    private val player: PlayerConnection,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistEntity>> =
        playlistRepo.observePlain().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(id: String) = viewModelScope.launch { tracks.toggleFavorite(id) }

    suspend fun isFavorite(id: String) = tracks.get(id)?.isFavorite == true

    fun delete(track: TrackEntity, onDone: () -> Unit = {}) = viewModelScope.launch {
        player.removeTrack(track.id)
        tracks.delete(track.id)
        onDone()
    }

    fun addToPlaylist(trackId: String, playlist: PlaylistEntity, onDone: (String) -> Unit) = viewModelScope.launch {
        playlistRepo.addTrack(playlist.id, trackId)
        onDone(context.getString(R.string.added_to_playlist, playlist.name))
    }

    fun createAndAdd(name: String, trackId: String, onDone: (String) -> Unit) = viewModelScope.launch {
        val id = playlistRepo.create(name)
        playlistRepo.addTrack(id, trackId)
        onDone(context.getString(R.string.added_to_playlist, name))
    }

    fun removeFromPlaylist(playlistId: Long, trackId: String) = viewModelScope.launch {
        playlistRepo.removeTrack(playlistId, trackId)
    }

    fun playNext(track: TrackEntity) = player.playNext(track)
    fun addToQueue(track: TrackEntity) = player.addToQueue(track)

    suspend fun track(id: String) = tracks.get(id)
}

/** Which overlay of the track-action flow is visible. */
sealed interface TrackMenuStep {
    data object None : TrackMenuStep
    data class Menu(val track: TrackEntity) : TrackMenuStep
    data class PickPlaylist(val trackId: String) : TrackMenuStep
    data class NewPlaylist(val trackId: String) : TrackMenuStep
    data class ConfirmDelete(val track: TrackEntity) : TrackMenuStep
}

class TrackMenuController {
    var step: TrackMenuStep by mutableStateOf(TrackMenuStep.None)
    fun open(track: TrackEntity) { step = TrackMenuStep.Menu(track) }
    fun pickPlaylist(trackId: String) { step = TrackMenuStep.PickPlaylist(trackId) }
    fun confirmDelete(track: TrackEntity) { step = TrackMenuStep.ConfirmDelete(track) }
    fun close() { step = TrackMenuStep.None }
}

@Composable
fun rememberTrackMenu() = remember { TrackMenuController() }

/** Hosts the track menu sheet + follow-up sheets/dialogs. */
@Composable
fun TrackMenuHost(
    controller: TrackMenuController,
    playlistId: Long? = null,
    vm: TrackActionsViewModel = hiltViewModel(),
) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    when (val step = controller.step) {
        TrackMenuStep.None -> Unit
        is TrackMenuStep.Menu -> TrackActionsSheet(
            track = step.track,
            onDismiss = controller::close,
            onPlayNext = { vm.playNext(step.track); controller.close(); notify(context.getString(R.string.playing_next)) },
            onAddToQueue = { vm.addToQueue(step.track); controller.close(); notify(context.getString(R.string.added_to_queue)) },
            onAddToPlaylist = { controller.pickPlaylist(step.track.id) },
            onToggleFavorite = { vm.toggleFavorite(step.track.id); controller.close() },
            onDelete = { controller.confirmDelete(step.track) },
            onRemoveFromPlaylist = playlistId?.let { pid -> { vm.removeFromPlaylist(pid, step.track.id); controller.close() } },
        )
        is TrackMenuStep.PickPlaylist -> AddToPlaylistSheet(
            playlists = playlists.filter { it.id != playlistId },
            onDismiss = controller::close,
            onPick = { p -> vm.addToPlaylist(step.trackId, p, notify); controller.close() },
            onCreateNew = { controller.step = TrackMenuStep.NewPlaylist(step.trackId) },
        )
        is TrackMenuStep.NewPlaylist -> NameDialog(
            title = stringResource(R.string.new_playlist_title),
            confirmLabel = stringResource(R.string.create),
            onDismiss = controller::close,
            onConfirm = { name -> vm.createAndAdd(name, step.trackId, notify); controller.close() },
        )
        is TrackMenuStep.ConfirmDelete -> ConfirmDialog(
            title = stringResource(R.string.delete_track_title),
            body = stringResource(R.string.delete_track_body, step.track.title),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = controller::close,
            onConfirm = {
                vm.delete(step.track) { notify(context.getString(R.string.track_deleted)) }
                controller.close()
            },
        )
    }
}
