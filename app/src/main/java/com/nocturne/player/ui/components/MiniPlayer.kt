package com.nocturne.player.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nocturne.player.R
import com.nocturne.player.playback.PlayerUiState
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.theme.NocturneBrushes
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType

/**
 * Floating mini-player: 1dp bright-gradient border (radius 24), inner #161630 (radius 23),
 * 46dp artwork · title/artist · play/pause (44 circle) · next, 3dp progress line below.
 * [artworkModifier] carries the shared-element transition to Now Playing.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NocturneShapes.MiniPlayer)
            .border(1.dp, NocturneBrushes.BrightDiagonal, NocturneShapes.MiniPlayer)
            .padding(1.dp)
            .clip(NocturneShapes.MiniPlayerInner)
            .background(NocturneColors.MiniPlayerInner)
            .pressable(onClick = onExpand, label = stringResource(R.string.now_playing))
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                model = state.artworkUri,
                seed = state.mediaId,
                shape = NocturneShapes.CoverSmall,
                modifier = artworkModifier.size(46.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    state.title,
                    style = NocturneType.TrackTitle,
                    color = NocturneColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.artist,
                    style = NocturneType.Meta,
                    color = NocturneColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Crossfade(state.isPlaying, label = "miniPlayPause") { playing ->
                CircleButton(
                    icon = if (playing) Lucide.Pause else Lucide.Play,
                    contentDescription = stringResource(if (playing) R.string.cd_pause else R.string.cd_play),
                    onClick = onTogglePlay,
                    size = 44.dp,
                    iconSize = 18.dp,
                    background = NocturneColors.TextPrimary,
                    tint = NocturneColors.PlayIcon,
                    haptic = true,
                )
            }
            IconButton44(
                icon = Lucide.SkipForward,
                contentDescription = stringResource(R.string.cd_next),
                onClick = onNext,
                background = Color.Transparent,
                iconSize = 20.dp,
                haptic = true,
            )
        }
        Spacer(Modifier.height(10.dp))
        ProgressLine(state.progress, height = 3.dp)
    }
}
