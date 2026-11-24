package com.nocturne.player.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nocturne.player.R
import com.nocturne.player.data.AppPrefs
import com.nocturne.player.data.PlaylistRepository
import com.nocturne.player.data.SortOrder
import com.nocturne.player.data.TrackRepository
import com.nocturne.player.data.db.DownloadEntity
import com.nocturne.player.data.db.PlaylistEntity
import com.nocturne.player.data.db.PlaylistWithCount
import com.nocturne.player.data.db.TrackEntity
import com.nocturne.player.download.DownloadRepository
import com.nocturne.player.download.DownloadStats
import com.nocturne.player.download.LiveProgress
import com.nocturne.player.playback.PlayContext
import com.nocturne.player.playback.PlayerConnection
import com.nocturne.player.playback.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

private val started = SharingStarted.WhileSubscribed(5_000)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackRepo: TrackRepository,
    private val playlistRepo: PlaylistRepository,
    private val prefs: AppPrefs,
    private val player: PlayerConnection,
) : ViewModel() {

    val sort = MutableStateFlow(prefs.sortOrder)
    val query = MutableStateFlow("")
    private val collator = Collator.getInstance(Locale("ro", "RO")).apply { strength = Collator.PRIMARY }

    private val allTracks = trackRepo.observeAll().stateIn(viewModelScope, started, emptyList())

    val tracks: StateFlow<List<TrackEntity>> = combine(allTracks, sort, query) { list, s, q ->
        val filtered = if (q.isBlank()) list else {
            // Case- and diacritics-insensitive: "oras" finds "oraș", "горы" finds "Горы".
            val needle = com.nocturne.player.data.SearchKey.normalize(q)
            list.filter { t ->
                (t.searchKey.ifEmpty { com.nocturne.player.data.SearchKey.of(t.title, t.artist) }).contains(needle)
            }
        }
        when (s) {
            SortOrder.RECENT -> filtered.sortedByDescending { it.addedAt }
            SortOrder.TITLE -> filtered.sortedWith { a, b -> collator.compare(a.title, b.title) }
            SortOrder.DURATION -> filtered.sortedBy { it.durationMs }
        }
    }.stateIn(viewModelScope, started, emptyList())

    val libraryLoaded: StateFlow<Boolean> = trackRepo.observeCount().map { true }.stateIn(viewModelScope, started, false)

    val favorites: StateFlow<List<TrackEntity>> = trackRepo.observeFavorites().stateIn(viewModelScope, started, emptyList())
    val playlists: StateFlow<List<PlaylistWithCount>> = playlistRepo.observeAll().stateIn(viewModelScope, started, emptyList())
    val playerState: StateFlow<PlayerUiState> = player.state

    fun setSort(order: SortOrder) {
        sort.value = order; prefs.sortOrder = order
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun playAll(shuffle: Boolean) {
        player.playTracks(tracks.value, 0, shuffle, PlayContext(com.nocturne.player.i18n.AppLocale.localized(context).getString(R.string.all_songs), false))
    }

    fun playTrack(index: Int) {
        player.playTracks(tracks.value, index, false, PlayContext(com.nocturne.player.i18n.AppLocale.localized(context).getString(R.string.all_songs), false))
    }

    fun togglePlay() = player.togglePlayPause()

    fun playFavorites() {
        player.playTracks(favorites.value, 0, false, PlayContext(com.nocturne.player.i18n.AppLocale.localized(context).getString(R.string.favorites), true))
    }

    fun playPlaylist(id: Long) = viewModelScope.launch {
        val p = playlistRepo.get(id) ?: return@launch
        player.playTracks(playlistRepo.tracks(id), 0, false, PlayContext(p.name, true))
    }

    fun createPlaylist(name: String) = viewModelScope.launch { playlistRepo.create(name) }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { playlistRepo.rename(id, name) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { playlistRepo.delete(id) }

    suspend fun firstOfLibrary() = allTracks.first()
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: DownloadRepository,
) : ViewModel() {
    val items: StateFlow<List<DownloadEntity>> = repo.observeAll()
        .map { list -> list.sortedWith(compareByDescending<DownloadEntity> { it.batchId }.thenBy { it.createdAt }) }
        .stateIn(viewModelScope, started, emptyList())
    val progress: StateFlow<Map<Long, LiveProgress>> = repo.progress
    val stats: StateFlow<DownloadStats> = combine(repo.observeAll(), repo.progress) { all, p -> DownloadStats.compute(all, p) }
        .stateIn(viewModelScope, started, DownloadStats.EMPTY)

    fun pauseAll() = viewModelScope.launch { repo.pauseAll() }
    fun resumeAll() = viewModelScope.launch { repo.resumeAll() }
    fun retry(id: Long) = viewModelScope.launch { repo.retry(id) }
    fun clearFinished() = viewModelScope.launch { repo.clearFinished() }
    fun clearAll() = viewModelScope.launch { repo.clearAll() }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: PlayerConnection,
    private val tracks: TrackRepository,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = player.state
    val volume: StateFlow<Float> = player.volume
    val playContext: StateFlow<PlayContext> = player.playContext

    val currentTrack: StateFlow<TrackEntity?> = player.state
        .map { it.mediaId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else tracks.observe(id) }
        .stateIn(viewModelScope, started, null)

    fun togglePlay() = player.togglePlayPause()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(fraction: Float) {
        val d = state.value.durationMs
        if (d > 0) player.seekTo((d * fraction).toLong())
    }
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()
    fun setVolume(v: Float) = player.setVolume(v)
    fun refreshVolume() = player.refreshVolume()
    fun playQueueIndex(i: Int) = player.playQueueIndex(i)
    fun moveQueue(from: Int, to: Int) = player.moveQueueItem(from, to)
    fun removeQueue(i: Int) = player.removeQueueIndex(i)
    fun toggleFavorite() = viewModelScope.launch {
        state.value.mediaId?.let { tracks.toggleFavorite(it) }
    }
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val playlistRepo: PlaylistRepository,
    trackRepo: TrackRepository,
    private val player: PlayerConnection,
) : ViewModel() {
    /** -1 = the virtual "Favorite" playlist. */
    val playlistId: Long = savedStateHandle.get<Long>("id") ?: -1L
    val isFavorites = playlistId == FAVORITES_ID

    val playlist: StateFlow<PlaylistEntity?> =
        (if (isFavorites) flowOf(null) else playlistRepo.observe(playlistId)).stateIn(viewModelScope, started, null)

    val tracks: StateFlow<List<TrackEntity>> =
        (if (isFavorites) trackRepo.observeFavorites() else playlistRepo.observeTracks(playlistId))
            .stateIn(viewModelScope, started, emptyList())

    val playerState = player.state

    private fun name() = if (isFavorites) com.nocturne.player.i18n.AppLocale.localized(context).getString(R.string.favorites) else playlist.value?.name.orEmpty()

    fun play(index: Int = 0, shuffle: Boolean = false) =
        player.playTracks(tracks.value, index, shuffle, PlayContext(name(), true))

    fun togglePlay() = player.togglePlayPause()

    fun rename(name: String) = viewModelScope.launch { playlistRepo.rename(playlistId, name) }
    fun delete() = viewModelScope.launch { playlistRepo.delete(playlistId) }

    companion object {
        const val FAVORITES_ID = -1L
    }
}
