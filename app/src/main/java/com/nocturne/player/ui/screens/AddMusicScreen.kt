package com.nocturne.player.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nocturne.player.R
import com.nocturne.player.download.DownloadRepository
import com.nocturne.player.ui.DownloadsViewModel
import com.nocturne.player.ui.components.Artwork
import com.nocturne.player.ui.components.GradientButton
import com.nocturne.player.ui.components.IconButton44
import com.nocturne.player.ui.components.LocalSnackbar
import com.nocturne.player.ui.components.formatTotalDuration
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType
import com.nocturne.player.youtube.ErrorKind
import com.nocturne.player.youtube.LinkResolver
import com.nocturne.player.youtube.ResolvedLink
import com.nocturne.player.youtube.YouTubeLinks
import com.nocturne.player.youtube.toErrorKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface Detection {
    data object Idle : Detection
    data object Loading : Detection
    data class Error(val kind: ErrorKind) : Detection
    data class Found(
        val resolved: ResolvedLink,
        val totalDurationMs: Long,
        val alreadyInLibrary: Int,
        val newCount: Int,
    ) : Detection
}

@OptIn(FlowPreview::class)
@HiltViewModel
class AddMusicViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val resolver: LinkResolver,
    private val downloads: DownloadRepository,
) : ViewModel() {

    private val _text = MutableStateFlow(savedStateHandle.get<String>("link").orEmpty())
    val text: StateFlow<String> = _text.asStateFlow()

    private val _detection = MutableStateFlow<Detection>(Detection.Idle)
    val detection: StateFlow<Detection> = _detection.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private var job: Job? = null
    private var lastResolvedUrl: String? = null

    init {
        if (_text.value.isNotBlank()) parse(immediate = true)
    }

    fun onTextChange(value: String) {
        _text.value = value
        parse(immediate = false)
    }

    fun clear() {
        _text.value = ""
        job?.cancel()
        lastResolvedUrl = null
        _detection.value = Detection.Idle
    }

    fun retry() {
        lastResolvedUrl = null
        parse(immediate = true)
    }

    private fun parse(immediate: Boolean) {
        val raw = _text.value
        job?.cancel()
        if (raw.isBlank()) {
            _detection.value = Detection.Idle; lastResolvedUrl = null; return
        }
        val url = YouTubeLinks.extractUrl(raw)
        val link = url?.let { YouTubeLinks.parse(it) }
        if (link == null) {
            lastResolvedUrl = null
            _detection.value = if (raw.length > 8) Detection.Error(ErrorKind.INVALID_LINK) else Detection.Idle
            return
        }
        if (link.canonicalUrl == lastResolvedUrl && _detection.value is Detection.Found) return
        job = viewModelScope.launch {
            if (!immediate) delay(400)
            _detection.value = Detection.Loading
            try {
                val resolved = resolver.resolve(link)
                lastResolvedUrl = link.canonicalUrl
                _detection.value = summarize(resolved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _detection.value = Detection.Error(e.toErrorKind())
            }
        }
    }

    private suspend fun summarize(resolved: ResolvedLink): Detection.Found {
        val known = downloads.knownVideoIds()
        val already = resolved.items.count { it.videoId in known }
        return Detection.Found(
            resolved = resolved,
            totalDurationMs = resolved.items.sumOf { it.durationMs },
            alreadyInLibrary = already,
            newCount = resolved.items.size - already,
        )
    }

    fun download() {
        val found = _detection.value as? Detection.Found ?: return
        viewModelScope.launch {
            val added = downloads.enqueue(found.resolved)
            _messages.tryEmit(
                if (added == 0 && found.resolved.isPlaylist) com.nocturne.player.i18n.AppLocale.localized(context).getString(R.string.playlist_imported)
                else com.nocturne.player.i18n.AppLocale.localized(context).resources.getQuantityString(R.plurals.added_to_downloads, added, added)
            )
            _detection.value = summarize(found.resolved)
        }
    }
}

/** Screen 3 — "Adaugă muzică". */
@Composable
fun AddMusicScreen(
    onBack: () -> Unit,
    vm: AddMusicViewModel = hiltViewModel(),
    downloadsVm: DownloadsViewModel = hiltViewModel(),
) {
    val text by vm.text.collectAsStateWithLifecycle()
    val detection by vm.detection.collectAsStateWithLifecycle()
    val items by downloadsVm.items.collectAsStateWithLifecycle()
    val progress by downloadsVm.progress.collectAsStateWithLifecycle()
    val stats by downloadsVm.stats.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(NocturneColors.Background).imePadding(),
        contentPadding = PaddingValues(top = screenTopPadding(), bottom = 32.dp + navBarPadding()),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = stringResource(R.string.add_music),
                leading = { IconButton44(Lucide.ArrowLeft, stringResource(R.string.cd_back), onClick = onBack) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item(key = "input") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                Text(stringResource(R.string.link_label), style = NocturneType.MetaSemi, color = NocturneColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                LinkInput(text, vm::onTextChange, onClear = vm::clear, onDone = { focusManager.clearFocus() })
            }
        }
        item(key = "detection") {
            AnimatedVisibility(
                visible = detection !is Detection.Idle,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    DetectionCard(detection, onDownload = vm::download, onRetry = vm::retry)
                }
            }
        }
        item(key = "dl-gap") { Spacer(Modifier.height(22.dp)) }
        downloadsSection(
            items = items,
            progress = progress,
            stats = stats,
            onPauseAll = downloadsVm::pauseAll,
            onResumeAll = downloadsVm::resumeAll,
            onRetry = downloadsVm::retry,
            onClearFinished = downloadsVm::clearFinished,
        )
    }
}

@Composable
private fun LinkInput(text: String, onChange: (String) -> Unit, onClear: () -> Unit, onDone: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) NocturneColors.BrightStart else Color.Transparent, label = "inputBorder")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(NocturneShapes.Input)
            .background(NocturneColors.Surface)
            .border(1.5.dp, border, NocturneShapes.Input)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Link, null, tint = NocturneColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(stringResource(R.string.link_placeholder), style = NocturneType.Body15, color = NocturneColors.TextSecondary)
            BasicTextField(
                value = text,
                onValueChange = onChange,
                singleLine = true,
                interactionSource = interaction,
                textStyle = NocturneType.Body15.copy(color = NocturneColors.TextPrimary),
                cursorBrush = SolidColor(NocturneColors.BrightStart),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            IconButton44(Lucide.X, stringResource(R.string.cd_clear), onClick = onClear, background = NocturneColors.SurfaceElevated, iconSize = 18.dp)
        }
    }
}

@Composable
private fun DetectionCard(detection: Detection, onDownload: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NocturneShapes.Card)
            .background(NocturneColors.Surface)
            .padding(16.dp),
    ) {
        when (detection) {
            Detection.Idle -> Unit
            Detection.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = NocturneColors.BrightStart, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.resolving), style = NocturneType.Body15, color = NocturneColors.TextSecondary)
            }
            is Detection.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.AlertCircle, null, tint = NocturneColors.Error, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(detection.kind.message), style = NocturneType.Body15, color = NocturneColors.Error, modifier = Modifier.weight(1f))
                if (detection.kind.retryable) {
                    Spacer(Modifier.width(8.dp))
                    IconButton44(Lucide.RotateCw, stringResource(R.string.retry), onClick = onRetry, background = NocturneColors.SurfaceElevated, iconSize = 18.dp)
                }
            }
            is Detection.Found -> {
                val r = detection.resolved
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(r.coverUrl, r.youtubePlaylistId ?: r.items.firstOrNull()?.videoId, NocturneShapes.Cover, Modifier.size(64.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            stringResource(if (r.isPlaylist) R.string.playlist_detected else R.string.video_detected),
                            style = NocturneType.Overline12,
                            color = NocturneColors.Amber,
                        )
                        Text(r.title, style = NocturneType.Body16Bold, color = NocturneColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val parts = buildList {
                            add(androidx.compose.ui.res.pluralStringResource(R.plurals.songs_count, r.items.size, r.items.size))
                            if (detection.totalDurationMs > 0) add(formatTotalDuration(detection.totalDurationMs))
                            if (detection.alreadyInLibrary > 0) add(androidx.compose.ui.res.pluralStringResource(R.plurals.already_in_library, detection.alreadyInLibrary, detection.alreadyInLibrary))
                        }
                        Text(parts.joinToString(" · "), style = NocturneType.Meta, color = NocturneColors.TextSecondary, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(16.dp))
                GradientButton(
                    text = when {
                        detection.newCount == 0 && r.isPlaylist -> stringResource(R.string.import_playlist)
                        detection.newCount == 0 -> stringResource(R.string.all_in_library)
                        !r.isPlaylist -> stringResource(R.string.download_one)
                        else -> androidx.compose.ui.res.pluralStringResource(R.plurals.download_new, detection.newCount, detection.newCount)
                    },
                    onClick = onDownload,
                    icon = Lucide.Download,
                    height = 50.dp,
                    enabled = detection.newCount > 0 || r.isPlaylist,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
