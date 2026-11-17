package com.nocturne.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nocturne.player.R
import com.nocturne.player.data.TrackRepository
import com.nocturne.player.data.db.TrackEntity
import com.nocturne.player.lyrics.Lyrics
import com.nocturne.player.lyrics.LyricsRepository
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LyricsUi {
    data object Loading : LyricsUi
    data class Ready(val lyrics: Lyrics) : LyricsUi
    data object Offline : LyricsUi
}

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repo: LyricsRepository,
    private val tracks: TrackRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow<LyricsUi>(LyricsUi.Loading)
    val ui: StateFlow<LyricsUi> = _ui
    private var loadedFor: String? = null
    private var job: Job? = null

    fun load(trackId: String, force: Boolean = false) {
        if (!force && loadedFor == trackId) return
        loadedFor = trackId
        job?.cancel()
        _ui.value = LyricsUi.Loading
        job = viewModelScope.launch {
            val track: TrackEntity = tracks.get(trackId) ?: run { _ui.value = LyricsUi.Ready(Lyrics.NotFound); return@launch }
            _ui.value = try {
                LyricsUi.Ready(repo.load(track, forceRefresh = force))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                repo.cached(track)?.let { LyricsUi.Ready(it) } ?: LyricsUi.Offline
            }
        }
    }
}

/**
 * "Versuri" sheet: synced lyrics follow the song (current line highlighted, tap a line to seek);
 * plain lyrics scroll. Fetched from LRCLIB once, then cached for offline use.
 */
@Composable
fun LyricsSheet(
    trackId: String,
    title: String,
    artist: String,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    vm: LyricsViewModel = hiltViewModel(),
) {
    LaunchedEffect(trackId) { vm.load(trackId) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    NocturneSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.lyrics).uppercase(), style = NocturneType.Overline12, color = NocturneColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(title, style = NocturneType.Section, color = NocturneColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, style = NocturneType.Meta, color = NocturneColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 560.dp)) {
            when (val s = ui) {
                LyricsUi.Loading -> Message(stringResource(R.string.lyrics_loading), loading = true)
                LyricsUi.Offline -> Message(stringResource(R.string.lyrics_offline), retry = { vm.load(trackId, force = true) })
                is LyricsUi.Ready -> when (val l = s.lyrics) {
                    is Lyrics.Synced -> SyncedLyrics(l, positionMs, onSeek)
                    is Lyrics.Plain -> Text(
                        l.text,
                        style = NocturneType.Body15.copy(lineHeight = 26.sp),
                        color = NocturneColors.TextPrimary,
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Lyrics.Instrumental -> Message(stringResource(R.string.lyrics_instrumental))
                    Lyrics.NotFound -> Message(stringResource(R.string.lyrics_not_found), retry = { vm.load(trackId, force = true) })
                }
            }
        }
        Text(
            stringResource(R.string.lyrics_source),
            style = NocturneType.Meta.copy(fontSize = 11.sp),
            color = NocturneColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics.Synced, positionMs: Long, onSeek: (Long) -> Unit) {
    val lines = lyrics.lines
    val current by remember(lines, positionMs) {
        derivedStateOf { lines.indexOfLast { it.timeMs <= positionMs + 250 }.coerceAtLeast(0) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        // Keep the current line in the upper third.
        listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(lines) { index, line ->
            val active = index == current
            val color by animateColorAsState(
                when {
                    active -> NocturneColors.TextPrimary
                    index < current -> NocturneColors.TextSecondary.copy(alpha = 0.55f)
                    else -> NocturneColors.TextSecondary
                },
                label = "lyricColor",
            )
            Text(
                line.text.ifBlank { "♪" },
                style = NocturneType.Section.copy(
                    fontSize = if (active) 22.sp else 19.sp,
                    lineHeight = 30.sp,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                ),
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timeMs) }
                    .padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
        item { Spacer(Modifier.height(160.dp)) }
    }
}

@Composable
private fun Message(text: String, loading: Boolean = false, retry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(color = NocturneColors.BrightStart, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        } else {
            Icon(Lucide.MicVocal, null, tint = NocturneColors.TextSecondary, modifier = Modifier.size(32.dp))
        }
        Text(text, style = NocturneType.Body15, color = NocturneColors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (retry != null) {
            TextButton(onClick = retry) {
                Text(stringResource(R.string.retry), style = NocturneType.Button14, color = NocturneColors.Amber)
            }
        }
    }
}
