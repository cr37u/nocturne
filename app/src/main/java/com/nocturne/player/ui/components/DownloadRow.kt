package com.nocturne.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nocturne.player.R
import com.nocturne.player.data.db.DownloadEntity
import com.nocturne.player.data.db.DownloadStatus
import com.nocturne.player.download.DownloadRepository
import com.nocturne.player.download.LiveProgress
import com.nocturne.player.youtube.ErrorKind
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.theme.NocturneBrushes
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType

/** Thin rounded progress bar with gradient (or solid) fill. */
@Composable
fun ProgressLine(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    track: Color = NocturneColors.ProgressTrack,
    fill: Brush = NocturneBrushes.BrightHorizontal,
    rounded: Boolean = true,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "progress")
    val shape = if (rounded) RoundedCornerShape(height / 2) else RoundedCornerShape(0.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(shape)
                .background(fill)
        )
    }
}

/**
 * 44×44 thumb · title 14 bold · status:
 * active (percent + 4dp bar + "2,8 / 3,6 MB · 1,3 MB/s"), done ("Gata" + check),
 * failed ("Eșuat" + reason + "Reîncearcă"), queued/paused.
 */
@Composable
fun DownloadRow(
    item: DownloadEntity,
    progress: LiveProgress?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.thumbnailUrl, item.videoId, NocturneShapes.Thumb12, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = NocturneType.Title14,
                    color = NocturneColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusLabel(item, progress)
            }
            when (item.status) {
                DownloadStatus.ACTIVE -> {
                    val total = progress?.totalBytes?.takeIf { it > 0 } ?: item.totalBytes
                    val done = progress?.downloadedBytes ?: 0L
                    ProgressLine(if (total > 0) done.toFloat() / total else 0f)
                    Text(
                        if (progress == null) stringResource(R.string.status_preparing)
                        else "${formatMbNumber(done)} / ${formatMb(total)} · ${formatSpeed(progress.bytesPerSecond)}",
                        style = NocturneType.Meta,
                        color = NocturneColors.TextSecondary,
                        maxLines = 1,
                    )
                }
                DownloadStatus.DONE -> Text(
                    "${formatMb(item.totalBytes)} · ${item.format ?: "m4a"}",
                    style = NocturneType.Meta, color = NocturneColors.TextSecondary, maxLines = 1,
                )
                DownloadStatus.FAILED -> Text(
                    stringResource(ErrorKind.fromKey(item.errorKey).message).let { reason ->
                        "$reason · ${stringResource(R.string.attempt, item.attempts, DownloadRepository.MAX_ATTEMPTS)}"
                    },
                    style = NocturneType.Meta, color = NocturneColors.TextSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                else -> Text(
                    item.artist,
                    style = NocturneType.Meta, color = NocturneColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (item.status) {
            DownloadStatus.DONE -> {
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(NocturneColors.Amber),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Check, stringResource(R.string.cd_done), tint = NocturneColors.Background, modifier = Modifier.size(16.dp))
                }
            }
            DownloadStatus.FAILED -> {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(NocturneShapes.IconButton)
                        .background(NocturneColors.SurfaceElevated)
                        .pressable(onClick = onRetry)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.RotateCw, null, tint = NocturneColors.TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.retry), style = NocturneType.Button14, color = NocturneColors.TextPrimary)
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun StatusLabel(item: DownloadEntity, progress: LiveProgress?) {
    when (item.status) {
        DownloadStatus.ACTIVE -> {
            val total = progress?.totalBytes?.takeIf { it > 0 } ?: item.totalBytes
            val pct = if (total > 0 && progress != null) (progress.downloadedBytes * 100 / total).toInt() else 0
            Text("$pct%", style = NocturneType.Title14, color = NocturneColors.TextPrimary)
        }
        DownloadStatus.DONE -> Text(stringResource(R.string.status_done), style = NocturneType.Title14, color = NocturneColors.Amber)
        DownloadStatus.FAILED -> Text(stringResource(R.string.status_failed), style = NocturneType.Title14, color = NocturneColors.Error)
        DownloadStatus.QUEUED -> Text(stringResource(R.string.status_queued), style = NocturneType.Meta, color = NocturneColors.TextSecondary)
        DownloadStatus.PAUSED -> Text(stringResource(R.string.status_paused), style = NocturneType.Meta, color = NocturneColors.TextSecondary)
    }
}
