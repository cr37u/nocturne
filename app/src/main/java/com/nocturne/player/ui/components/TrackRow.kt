package com.nocturne.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nocturne.player.R
import com.nocturne.player.data.db.TrackEntity
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType

/**
 * 52×52 thumb · title (15 bold) + "Artist · 3:58" · more button.
 * Current track: bg #141426, radius 18, amber title, animated equalizer before the dots.
 */
@Composable
fun TrackRow(
    track: TrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(if (isCurrent) NocturneColors.Surface else Color.Transparent, label = "rowBg")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(NocturneShapes.CurrentRow)
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.thumbnailPath, track.id, NocturneShapes.CoverSmall, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                track.title,
                style = NocturneType.TrackTitle,
                color = if (isCurrent) NocturneColors.Amber else NocturneColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${track.artist} · ${formatDuration(track.durationMs)}",
                style = NocturneType.Meta,
                color = NocturneColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Spacer(Modifier.width(8.dp))
            Equalizer(playing = isPlaying)
        }
        IconButton44(
            icon = Lucide.MoreVertical,
            contentDescription = stringResource(R.string.cd_more),
            onClick = onMore,
            background = Color.Transparent,
            tint = NocturneColors.TextSecondary,
        )
    }
}
