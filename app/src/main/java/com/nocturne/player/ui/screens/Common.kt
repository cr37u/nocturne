package com.nocturne.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nocturne.player.R
import com.nocturne.player.data.db.DownloadEntity
import com.nocturne.player.download.DownloadStats
import com.nocturne.player.download.LiveProgress
import com.nocturne.player.ui.components.DownloadRow
import com.nocturne.player.ui.components.EmptyState
import com.nocturne.player.ui.components.ProgressLine
import com.nocturne.player.ui.components.formatSpeed
import com.nocturne.player.ui.components.pressable
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.theme.NocturneBrushes
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType

/** Design top padding 56, never less than status bar + 16. */
@Composable
fun screenTopPadding(): Dp {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return maxOf(56.dp, status + 16.dp)
}

@Composable
fun navBarPadding(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Space reserved under lists for the floating mini-player. */
val MiniPlayerSpace = 132.dp

/** Logo: 38×38, radius 12, primary gradient, white crescent moon. */
@Composable
fun Logo(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(38.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(NocturneBrushes.Primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Moon, stringResource(R.string.cd_logo), tint = NocturneColors.TextPrimary, modifier = Modifier.size(20.dp))
    }
}

/** "Descărcări" header + stats + "Pauză tot", global progress, and the rows. */
fun LazyListScope.downloadsSection(
    items: List<DownloadEntity>,
    progress: Map<Long, LiveProgress>,
    stats: DownloadStats,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetry: (Long) -> Unit,
    onClearFinished: () -> Unit,
    onClearAll: (() -> Unit)? = null,
    showTitle: Boolean = true,
    horizontalPadding: Dp = 20.dp,
) {
    item(key = "dl-header") {
        Column(Modifier.padding(horizontal = horizontalPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (showTitle) Text(stringResource(R.string.downloads), style = NocturneType.Section, color = NocturneColors.TextPrimary)
                    if (stats.total > 0) {
                        Text(
                            if (stats.active > 0) {
                                stringResource(R.string.downloads_stats, stats.done, stats.total, stats.active, formatSpeed(stats.bytesPerSecond))
                            } else {
                                stringResource(R.string.downloads_stats_idle, stats.done, stats.total)
                            },
                            style = NocturneType.Meta,
                            color = NocturneColors.TextSecondary,
                        )
                    }
                }
                when {
                    stats.hasRunnable -> SmallButton(stringResource(R.string.pause_all), onPauseAll)
                    stats.hasPaused -> SmallButton(stringResource(R.string.resume_all), onResumeAll)
                    items.isNotEmpty() -> SmallButton(stringResource(R.string.clear_finished), onClearFinished)
                }
                if (onClearAll != null && items.isNotEmpty()) {
                    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(NocturneShapes.IconButton)
                            .background(NocturneColors.Surface)
                            .pressable(onClick = { confirm = true }, label = stringResource(R.string.clear_all)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Lucide.Trash, stringResource(R.string.clear_all), tint = NocturneColors.Error, modifier = Modifier.size(18.dp))
                    }
                    if (confirm) {
                        com.nocturne.player.ui.components.ConfirmDialog(
                            title = stringResource(R.string.clear_all_title),
                            body = stringResource(R.string.clear_all_body),
                            confirmLabel = stringResource(R.string.clear_all),
                            onDismiss = { confirm = false },
                            onConfirm = { confirm = false; onClearAll() },
                        )
                    }
                }
            }
            if (stats.total > 0) {
                Spacer(Modifier.height(12.dp))
                ProgressLine(stats.fraction, height = 6.dp, track = NocturneColors.SurfaceElevated)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (items.isEmpty()) {
        item(key = "dl-empty") { EmptyState(null, stringResource(R.string.downloads_empty), icon = Lucide.Download) }
    }
    items(items, key = { "dl-${it.id}" }) { d ->
        DownloadRow(
            item = d,
            progress = progress[d.id],
            onRetry = { onRetry(d.id) },
            modifier = Modifier
                .animateItem()
                .padding(horizontal = horizontalPadding),
        )
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(NocturneShapes.IconButton)
            .background(NocturneColors.Surface)
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NocturneType.Button14, color = NocturneColors.TextPrimary)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    big: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        if (leading != null) {
            leading(); Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = if (big) NocturneType.ScreenTitle else NocturneType.Header22,
            color = NocturneColors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        trailing?.invoke()
    }
}
