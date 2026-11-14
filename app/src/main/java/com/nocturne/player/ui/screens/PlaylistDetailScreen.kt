package com.nocturne.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nocturne.player.R
import com.nocturne.player.ui.PlaylistDetailViewModel
import com.nocturne.player.ui.TrackMenuHost
import com.nocturne.player.ui.components.Artwork
import com.nocturne.player.ui.components.CircleButton
import com.nocturne.player.ui.components.ConfirmDialog
import com.nocturne.player.ui.components.EmptyState
import com.nocturne.player.ui.components.IconButton44
import com.nocturne.player.ui.components.NameDialog
import com.nocturne.player.ui.components.NocturneSheet
import com.nocturne.player.ui.components.SheetAction
import com.nocturne.player.ui.components.TrackRow
import com.nocturne.player.ui.components.formatTotalDuration
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.rememberTrackMenu
import com.nocturne.player.ui.theme.NocturneBrushes
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    bottomSpace: Dp,
    vm: PlaylistDetailViewModel = hiltViewModel(),
) {
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()
    val menu = rememberTrackMenu()
    var options by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val name = if (vm.isFavorites) stringResource(R.string.favorites) else playlist?.name.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(NocturneColors.Background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = screenTopPadding(), bottom = bottomSpace),
    ) {
        item(key = "top") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton44(Lucide.ArrowLeft, stringResource(R.string.cd_back), onClick = onBack)
                Spacer(Modifier.weight(1f))
                if (!vm.isFavorites) IconButton44(Lucide.MoreVertical, stringResource(R.string.cd_more), onClick = { options = true })
            }
        }
        item(key = "cover") {
            Column(Modifier.padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(
                    model = if (vm.isFavorites) null else (tracks.firstOrNull()?.thumbnailPath ?: playlist?.coverUrl),
                    seed = if (vm.isFavorites) "favorites" else "pl${vm.playlistId}",
                    shape = NocturneShapes.CoverGrid,
                    modifier = Modifier.size(200.dp),
                    placeholderIcon = if (vm.isFavorites) {
                        { Icon(Lucide.HeartFilled, null, tint = NocturneColors.Amber, modifier = Modifier.size(72.dp)) }
                    } else null,
                )
                Spacer(Modifier.height(18.dp))
                if (playlist?.fromYoutube == true) {
                    Box(
                        Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NocturneColors.Surface)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.from_youtube), style = NocturneType.Badge11, color = NocturneColors.TextSecondary) }
                    Spacer(Modifier.height(8.dp))
                }
                Text(name, style = NocturneType.ScreenTitle, color = NocturneColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val total = tracks.sumOf { it.durationMs }
                Text(
                    pluralStringResource(R.plurals.songs_count, tracks.size, tracks.size) +
                        if (total > 0) " · ${formatTotalDuration(total)}" else "",
                    style = NocturneType.Meta,
                    color = NocturneColors.TextSecondary,
                )
            }
        }
        item(key = "actions") {
            Row(
                Modifier.padding(top = 18.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                CircleButton(
                    Lucide.Shuffle, stringResource(R.string.shuffle_all), onClick = { vm.play(shuffle = true) },
                    size = 44.dp, background = NocturneColors.Surface, tint = NocturneColors.Amber, haptic = true,
                )
                CircleButton(
                    Lucide.Play, stringResource(R.string.play_all), onClick = { vm.play() },
                    size = 48.dp, background = null, brush = NocturneBrushes.Primary, tint = NocturneColors.TextPrimary, haptic = true,
                    modifier = Modifier.shadow(14.dp, CircleShape, ambientColor = NocturneColors.Shadow, spotColor = NocturneColors.Shadow),
                )
                Spacer(Modifier.weight(1f))
            }
        }
        if (tracks.isEmpty()) item(key = "empty") { EmptyState(null, stringResource(R.string.playlist_empty), icon = Lucide.ListMusic) }
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = player.mediaId == track.id,
                isPlaying = player.isPlaying,
                onClick = { if (player.mediaId == track.id) vm.togglePlay() else vm.play(index) },
                onLongClick = { menu.open(track) },
                onMore = { menu.open(track) },
                modifier = Modifier.animateItem().padding(top = 4.dp),
            )
        }
    }

    TrackMenuHost(menu, playlistId = if (vm.isFavorites) null else vm.playlistId)

    if (options) {
        NocturneSheet(onDismiss = { options = false }) {
            SheetAction(Lucide.Pencil, stringResource(R.string.rename), { renaming = true; options = false })
            SheetAction(Lucide.Trash, stringResource(R.string.delete_playlist), { deleting = true; options = false }, tint = NocturneColors.Error)
        }
    }
    if (renaming) {
        NameDialog(
            title = stringResource(R.string.rename_playlist_title),
            confirmLabel = stringResource(R.string.save),
            initial = name,
            onDismiss = { renaming = false },
            onConfirm = { vm.rename(it); renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.delete_playlist_title),
            body = stringResource(R.string.delete_playlist_body),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { deleting = false },
            onConfirm = { vm.delete(); deleting = false; onBack() },
        )
    }
}
