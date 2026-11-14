package com.nocturne.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nocturne.player.R
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType

/**
 * Square cover (radius 22) with optional "Din YouTube" badge and a 44dp play button,
 * then name (15 bold) + "24 melodii" (13).
 */
@Composable
fun PlaylistCard(
    name: String,
    trackCount: Int,
    coverModel: Any?,
    seed: String,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    fromYoutube: Boolean = false,
    isFavorites: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Artwork(
                model = if (isFavorites) null else coverModel,
                seed = seed,
                shape = NocturneShapes.CoverGrid,
                modifier = Modifier.matchParentSize(),
                placeholderIcon = if (isFavorites) {
                    {
                        Icon(Lucide.HeartFilled, null, tint = NocturneColors.Amber, modifier = Modifier.size(56.dp))
                    }
                } else {
                    {
                        Icon(Lucide.ListMusic, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(48.dp))
                    }
                },
            )
            if (fromYoutube) {
                Box(
                    Modifier
                        .padding(10.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NocturneColors.Scrim)
                        .padding(horizontal = 10.dp)
                        .align(Alignment.TopStart),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.from_youtube), style = NocturneType.Badge11, color = NocturneColors.TextPrimary)
                }
            }
            if (trackCount > 0) {
                CircleButton(
                    icon = Lucide.Play,
                    contentDescription = stringResource(R.string.cd_play_playlist),
                    onClick = onPlay,
                    size = 44.dp,
                    iconSize = 18.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    haptic = true,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(name, style = NocturneType.TrackTitle, color = NocturneColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(
            pluralStringResource(R.plurals.songs_count, trackCount, trackCount),
            style = NocturneType.Meta,
            color = NocturneColors.TextSecondary,
        )
    }
}
