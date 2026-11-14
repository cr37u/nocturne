package com.nocturne.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nocturne.player.R
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType

enum class HomeTab(val label: Int) {
    SONGS(R.string.tab_songs),
    PLAYLISTS(R.string.tab_playlists),
    DOWNLOADS(R.string.tab_downloads),
}

/** "Melodii / Playlisturi / Descărcări" pills: 40 tall, gap 8, fully rounded. */
@Composable
fun PillTabs(selected: HomeTab, onSelect: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeTab.entries.forEach { tab ->
            val active = tab == selected
            val bg by animateColorAsState(if (active) NocturneColors.TextPrimary else NocturneColors.Surface, label = "pillBg")
            val fg by animateColorAsState(if (active) NocturneColors.Background else NocturneColors.TextSecondary, label = "pillFg")
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(NocturneShapes.Pill)
                    .background(bg)
                    .semantics { this.selected = active }
                    .pressable(onClick = { onSelect(tab) }, role = Role.Tab)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(tab.label),
                    style = if (active) NocturneType.PillActive else NocturneType.Pill,
                    color = fg,
                )
            }
        }
    }
}
