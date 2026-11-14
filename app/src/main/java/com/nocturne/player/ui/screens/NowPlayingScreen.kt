package com.nocturne.player.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.nocturne.player.R
import com.nocturne.player.playback.QueueEntry
import com.nocturne.player.ui.PlayerViewModel
import com.nocturne.player.ui.TrackMenuHost
import com.nocturne.player.ui.TrackMenuStep
import com.nocturne.player.ui.components.Artwork
import com.nocturne.player.ui.components.IconButton44
import com.nocturne.player.ui.components.NocturneSheet
import com.nocturne.player.ui.components.NocturneSlider
import com.nocturne.player.ui.components.formatDuration
import com.nocturne.player.ui.components.pressable
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.rememberTrackMenu
import com.nocturne.player.ui.theme.NocturneBrushes
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

/**
 * Screen 2 — "Se redă acum". [artworkModifier] carries the shared-element transition from the
 * mini-player. Swipe down (or "Închide") collapses back to the mini-player.
 */
@Composable
fun NowPlayingScreen(
    onClose: () -> Unit,
    artworkModifier: Modifier = Modifier,
    vm: PlayerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val ctx by vm.playContext.collectAsStateWithLifecycle()
    val track by vm.currentTrack.collectAsStateWithLifecycle()
    val menu = rememberTrackMenu()
    var showQueue by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) { vm.refreshVolume() }
    LaunchedEffect(state.hasMedia) { if (!state.hasMedia && state.connected) onClose() }

    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val closeThreshold = with(LocalDensity.current) { 140.dp.toPx() }
    val artScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.9f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "artScale",
    )
    val topPad = maxOf(52.dp, screenTopPadding() - 4.dp)
    val bottomPad = 36.dp + navBarPadding()

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .background(NocturneBrushes.NowPlaying)
            .draggable(
                state = rememberDraggableState { delta ->
                    scope.launch { offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f)) }
                },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (offsetY.value > closeThreshold || velocity > 2500f) onClose()
                    else offsetY.animateTo(0f)
                },
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = topPad, bottom = bottomPad + 44.dp + 20.dp),
        ) {
            // 1. Top bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton44(Lucide.ChevronDown, stringResource(R.string.cd_close), onClick = onClose, onGradient = true, iconSize = 22.dp)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(if (ctx.isPlaylist) R.string.playing_from_playlist else R.string.playing_from_library),
                        style = NocturneType.Overline11,
                        color = NocturneColors.OnGradientSecondary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        ctx.label.ifBlank { stringResource(R.string.all_songs) },
                        style = NocturneType.Title14,
                        color = NocturneColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Box {
                    IconButton44(Lucide.MoreHorizontal, stringResource(R.string.cd_more), onClick = { moreOpen = true }, onGradient = true)
                    DropdownMenu(
                        expanded = moreOpen,
                        onDismissRequest = { moreOpen = false },
                        containerColor = NocturneColors.SurfaceElevated,
                        shape = NocturneShapes.Card,
                    ) {
                        MenuRow(Lucide.ListPlus, stringResource(R.string.menu_add_playlist)) {
                            moreOpen = false; track?.let { menu.pickPlaylist(it.id) }
                        }
                        MenuRow(
                            if (track?.isFavorite == true) Lucide.HeartFilled else Lucide.Heart,
                            stringResource(if (track?.isFavorite == true) R.string.menu_favorite_remove else R.string.menu_favorite_add),
                        ) { moreOpen = false; vm.toggleFavorite() }
                        MenuRow(Lucide.ListMusic, stringResource(R.string.queue)) { moreOpen = false; showQueue = true }
                        MenuRow(Lucide.Trash, stringResource(R.string.menu_delete), NocturneColors.Error) {
                            moreOpen = false; track?.let { menu.step = TrackMenuStep.ConfirmDelete(it) }
                        }
                    }
                }
            }

            // 2. Artwork (square, radius 28, scales 1.0 ↔ 0.9)
            Spacer(Modifier.height(34.dp))
            Box(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .graphicsLayer { scaleX = artScale; scaleY = artScale }
                ) {
                    Artwork(
                        model = state.artworkUri,
                        seed = state.mediaId,
                        shape = NocturneShapes.CoverNowPlaying,
                        modifier = artworkModifier
                            .fillMaxSize()
                            .shadow(30.dp, NocturneShapes.CoverNowPlaying, ambientColor = Color(0x8C0A0A14), spotColor = Color(0x8C0A0A14)),
                    )
                }
            }

            // 3. Title row
            Spacer(Modifier.height(34.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = NocturneType.NowPlayingTitle,
                        color = NocturneColors.TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.artist,
                        style = NocturneType.NowPlayingArtist,
                        color = NocturneColors.OnGradientSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconButton44(
                    Lucide.ListPlus, stringResource(R.string.cd_add_to_playlist),
                    onClick = { track?.let { menu.pickPlaylist(it.id) } }, onGradient = true,
                )
                Spacer(Modifier.width(8.dp))
                val fav = track?.isFavorite == true
                IconButton44(
                    if (fav) Lucide.HeartFilled else Lucide.Heart,
                    stringResource(R.string.cd_favorite),
                    onClick = vm::toggleFavorite,
                    onGradient = !fav,
                    background = NocturneColors.AmberBackground,
                    tint = if (fav) NocturneColors.Amber else NocturneColors.TextPrimary,
                    haptic = true,
                )
            }

            // 4. Seek bar + times
            Spacer(Modifier.height(26.dp))
            val shownFraction = dragFraction ?: state.progress
            NocturneSlider(
                value = shownFraction,
                onChange = { dragFraction = it },
                onChangeFinished = { vm.seekTo(it); dragFraction = null },
                modifier = Modifier.padding(horizontal = 0.dp),
                description = stringResource(R.string.now_playing),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    formatDuration(((dragFraction ?: state.progress) * state.durationMs).toLong()),
                    style = NocturneType.MetaSemi, color = NocturneColors.OnGradientSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(formatDuration(state.durationMs), style = NocturneType.MetaSemi, color = NocturneColors.OnGradientSecondary)
            }

            // 5. Controls
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleButton(Lucide.Shuffle, stringResource(R.string.cd_shuffle), on = state.shuffle, onClick = vm::toggleShuffle)
                IconButton44(
                    Lucide.SkipBack, stringResource(R.string.cd_previous), onClick = vm::previous,
                    size = 56.dp, iconSize = 28.dp, shape = CircleShape, background = Color.Transparent, haptic = true,
                )
                PlayPauseBig(state.isPlaying, vm::togglePlay)
                IconButton44(
                    Lucide.SkipForward, stringResource(R.string.cd_next), onClick = vm::next,
                    size = 56.dp, iconSize = 28.dp, shape = CircleShape, background = Color.Transparent, haptic = true,
                )
                Box {
                    ToggleButton(
                        Lucide.Repeat,
                        stringResource(
                            when (state.repeatMode) {
                                Player.REPEAT_MODE_ALL -> R.string.repeat_all
                                Player.REPEAT_MODE_ONE -> R.string.repeat_one
                                else -> R.string.repeat_off
                            }
                        ),
                        on = state.repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = vm::cycleRepeat,
                    )
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(NocturneColors.Amber),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("1", style = NocturneType.Badge11.copy(fontSize = 10.sp, lineHeight = 10.sp), color = NocturneColors.Background)
                        }
                    }
                }
            }
        }

        // 6. Bottom (pinned): volume + queue
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = bottomPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Volume, stringResource(R.string.cd_volume), tint = NocturneColors.OnGradientSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            NocturneSlider(
                value = volume,
                onChange = vm::setVolume,
                onChangeFinished = vm::setVolume,
                modifier = Modifier.weight(1f),
                trackHeight = 4.dp,
                thumbSize = 12.dp,
                halo = 0.dp,
                fill = SolidColor(NocturneColors.OnGradientSecondary),
                thumbColor = NocturneColors.OnGradientSecondary,
                description = stringResource(R.string.cd_volume),
            )
            Spacer(Modifier.width(8.dp))
            IconButton44(Lucide.ListMusic, stringResource(R.string.cd_queue), onClick = { showQueue = true }, onGradient = true)
        }
    }

    if (showQueue) {
        QueueSheet(
            queue = state.queue,
            currentIndex = state.currentIndex,
            isPlaying = state.isPlaying,
            onDismiss = { showQueue = false },
            onPlay = vm::playQueueIndex,
            onMove = vm::moveQueue,
            onRemove = vm::removeQueue,
        )
    }
    TrackMenuHost(menu)
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, tint: Color = NocturneColors.TextPrimary, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = NocturneType.Body15, color = tint) },
        leadingIcon = { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}

/** Shuffle / repeat: 48dp, radius 16; ON = amber icon on amber 16% background. */
@Composable
private fun ToggleButton(icon: ImageVector, description: String, on: Boolean, onClick: () -> Unit) {
    IconButton44(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        size = 48.dp,
        iconSize = 22.dp,
        shape = RoundedCornerShape(16.dp),
        background = if (on) NocturneColors.AmberBackground else Color.Transparent,
        tint = if (on) NocturneColors.Amber else NocturneColors.OnGradientSecondary,
    )
}

/** 78dp white circle, #3B0764 icon, violet glow (0 0 44 rgba(124,58,237,0.7)). */
@Composable
private fun PlayPauseBig(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(78.dp)
            .drawBehind {
                val r = size.minDimension / 2
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NocturneColors.Glow, NocturneColors.Glow.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = r + 44.dp.toPx(),
                    ),
                    radius = r + 44.dp.toPx(),
                )
            }
            .clip(CircleShape)
            .background(NocturneColors.TextPrimary)
            .pressable(onClick = onClick, haptic = true, label = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(isPlaying, label = "playPause") { playing ->
            Icon(
                if (playing) Lucide.Pause else Lucide.Play,
                contentDescription = stringResource(if (playing) R.string.cd_pause else R.string.cd_play),
                tint = NocturneColors.PlayIcon,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** Bottom sheet (radius 28) with the reorderable play queue. */
@Composable
private fun QueueSheet(
    queue: List<QueueEntry>,
    currentIndex: Int,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onPlay: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    var local by remember(queue) { mutableStateOf(queue) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        local = local.toMutableList().apply { add(to.index, removeAt(from.index)) }
        onMove(from.index, to.index)
    }
    NocturneSheet(onDismiss) {
        Text(
            stringResource(R.string.queue),
            style = NocturneType.Section,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (local.isEmpty()) {
            Text(
                stringResource(R.string.queue_empty),
                style = NocturneType.Meta, color = NocturneColors.TextSecondary,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(520.dp)) {
            itemsIndexed(local, key = { _, e -> e.key }) { index, entry ->
                ReorderableItem(reorderState, key = entry.key) { dragging ->
                    val current = index == currentIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (dragging) NocturneColors.Surface else Color.Transparent)
                            .pressable(onClick = { onPlay(index) })
                            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(entry.artworkUri, entry.mediaId, NocturneShapes.Thumb12, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title, style = NocturneType.Title14,
                                color = if (current) NocturneColors.Amber else NocturneColors.TextPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${entry.artist} · ${formatDuration(entry.durationMs)}",
                                style = NocturneType.Meta, color = NocturneColors.TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (current) com.nocturne.player.ui.components.Equalizer(isPlaying, Modifier.padding(horizontal = 6.dp))
                        IconButton44(
                            Lucide.X, stringResource(R.string.cd_remove), onClick = { onRemove(index) },
                            background = Color.Transparent, tint = NocturneColors.TextSecondary, iconSize = 18.dp,
                        )
                        Box(
                            Modifier.size(44.dp).draggableHandle(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Lucide.Grip, stringResource(R.string.cd_drag), tint = NocturneColors.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
