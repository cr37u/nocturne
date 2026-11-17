package com.nocturne.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nocturne.player.R
import com.nocturne.player.data.SortOrder
import com.nocturne.player.data.db.PlaylistEntity
import com.nocturne.player.ui.DownloadsViewModel
import com.nocturne.player.ui.HomeViewModel
import com.nocturne.player.ui.TrackMenuHost
import com.nocturne.player.ui.components.CircleButton
import com.nocturne.player.ui.components.ConfirmDialog
import com.nocturne.player.ui.components.EmptyState
import com.nocturne.player.ui.components.GradientButton
import com.nocturne.player.ui.components.HomeTab
import com.nocturne.player.ui.components.IconButton44
import com.nocturne.player.ui.components.LocalSnackbar
import com.nocturne.player.ui.components.NameDialog
import com.nocturne.player.ui.components.NocturneSheet
import com.nocturne.player.ui.components.PillTabs
import com.nocturne.player.ui.components.PlaylistCard
import com.nocturne.player.ui.components.SheetAction
import com.nocturne.player.ui.components.TrackRow
import com.nocturne.player.ui.components.pressable
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.rememberTrackMenu
import com.nocturne.player.ui.theme.NocturneBrushes
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType
import com.nocturne.player.youtube.YouTubeLinks
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    onOpenAdd: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    bottomSpace: Dp,
) {
    AnimatedContent(
        targetState = tab,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "homeTab",
        modifier = Modifier.fillMaxSize().background(NocturneColors.Background),
    ) { t ->
        when (t) {
            HomeTab.SONGS -> LibraryTab(onTabChange, onOpenAdd, onOpenSettings, bottomSpace)
            HomeTab.PLAYLISTS -> PlaylistsTab(onTabChange, onOpenPlaylist, bottomSpace)
            HomeTab.DOWNLOADS -> DownloadsTab(onTabChange, onOpenAdd, bottomSpace)
        }
    }
}

// =====================================================================  Screen 1 — Library

@Composable
private fun LibraryTab(
    onTabChange: (HomeTab) -> Unit,
    onOpenAdd: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    bottomSpace: Dp,
    vm: HomeViewModel = hiltViewModel(),
) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()
    var searchActive by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    val menu = rememberTrackMenu()

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = screenTopPadding(), bottom = bottomSpace),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo()
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.app_name), style = NocturneType.Brand, color = NocturneColors.TextPrimary, modifier = Modifier.weight(1f))
                IconButton44(
                    Lucide.Search, stringResource(R.string.cd_search),
                    onClick = {
                        searchActive = !searchActive
                        if (!searchActive) vm.setQuery("")
                    },
                    tint = if (searchActive) NocturneColors.Amber else NocturneColors.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                IconButton44(Lucide.Sliders, stringResource(R.string.cd_settings), onClick = onOpenSettings)
            }
        }
        item(key = "linkbar") {
            Box(Modifier.padding(top = 22.dp)) {
                if (searchActive) {
                    SearchBar(query, vm::setQuery, onClose = { searchActive = false; vm.setQuery("") })
                } else {
                    LinkBar(onSubmit = onOpenAdd)
                }
            }
        }
        item(key = "tabs") { PillTabs(HomeTab.SONGS, onTabChange, Modifier.padding(top = 20.dp)) }
        item(key = "sort") {
            SortRow(
                count = tracks.size,
                sort = sort,
                onSort = vm::setSort,
                onShuffle = { vm.playAll(shuffle = true) },
                onPlayAll = { vm.playAll(shuffle = false) },
                modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
            )
        }
        if (tracks.isEmpty()) {
            item(key = "empty") {
                if (query.isNotBlank()) EmptyState(null, stringResource(R.string.search_empty), icon = Lucide.Search)
                else EmptyState(stringResource(R.string.library_empty_title), stringResource(R.string.library_empty_body))
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) menu.confirmDelete(track)
                    false
                },
            )
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(NocturneShapes.CurrentRow)
                            .background(NocturneColors.Error.copy(alpha = 0.18f))
                            .padding(end = 24.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) { Icon(Lucide.Trash, stringResource(R.string.delete), tint = NocturneColors.Error) }
                },
                modifier = Modifier
                    .animateItem()
                    .padding(top = 4.dp),
            ) {
                TrackRow(
                    track = track,
                    isCurrent = player.mediaId == track.id,
                    isPlaying = player.isPlaying,
                    onClick = { if (player.mediaId == track.id) vm.togglePlay() else vm.playTrack(index) },
                    onLongClick = { menu.open(track) },
                    onMore = { menu.open(track) },
                    modifier = Modifier.background(NocturneColors.Background, NocturneShapes.CurrentRow),
                )
            }
        }
    }
    TrackMenuHost(menu)
}

@Composable
private fun LinkBar(onSubmit: (String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun submit(raw: String) {
        val link = YouTubeLinks.extractUrl(raw)
        if (link != null) {
            text = ""
            onSubmit(link)
        } else {
            scope.launch { snackbar.showSnackbar(com.nocturne.player.i18n.AppLocale.localized(context).getString(R.string.error_invalid_link)) }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(NocturneShapes.Input)
            .background(NocturneColors.Surface)
            .padding(start = 16.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Link, null, tint = NocturneColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) {
                Text(stringResource(R.string.link_placeholder), style = NocturneType.Body15, color = NocturneColors.TextSecondary, maxLines = 1)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = NocturneType.Body15.copy(color = NocturneColors.TextPrimary),
                cursorBrush = SolidColor(NocturneColors.BrightStart),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit(text) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        GradientButton(
            text = stringResource(R.string.paste),
            onClick = {
                if (text.isNotBlank()) submit(text) else {
                    val clip = clipboard.getText()?.text
                    if (YouTubeLinks.extractUrl(clip) != null) submit(clip!!)
                    else scope.launch { snackbar.showSnackbar(com.nocturne.player.i18n.AppLocale.localized(context).getString(R.string.clipboard_empty)) }
                }
            },
            height = 44.dp,
            shape = NocturneShapes.Button,
        )
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(NocturneShapes.Input)
            .background(NocturneColors.Surface)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Search, null, tint = NocturneColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(stringResource(R.string.search_placeholder), style = NocturneType.Body15, color = NocturneColors.TextSecondary)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = NocturneType.Body15.copy(color = NocturneColors.TextPrimary),
                cursorBrush = SolidColor(NocturneColors.BrightStart),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        IconButton44(Lucide.X, stringResource(R.string.cd_close_search), onClick = onClose, background = NocturneColors.SurfaceElevated, iconSize = 18.dp)
    }
}

@Composable
private fun SortRow(
    count: Int,
    sort: SortOrder,
    onSort: (SortOrder) -> Unit,
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pluralStringResource(R.plurals.songs_count, count, count), style = NocturneType.Section, color = NocturneColors.TextPrimary)
            Box {
                Text(
                    "${stringResource(sort.label())} ▾",
                    style = NocturneType.Meta,
                    color = NocturneColors.TextSecondary,
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .pressable(onClick = { open = true })
                        .padding(vertical = 6.dp),
                )
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    containerColor = NocturneColors.SurfaceElevated,
                    shape = NocturneShapes.Card,
                ) {
                    SortOrder.entries.forEach { o ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(o.label()),
                                    style = if (o == sort) NocturneType.Title14 else NocturneType.Body15,
                                    color = if (o == sort) NocturneColors.Amber else NocturneColors.TextPrimary,
                                )
                            },
                            onClick = { onSort(o); open = false },
                            trailingIcon = if (o == sort) {
                                { Icon(Lucide.Check, null, tint = NocturneColors.Amber, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }
            }
        }
        CircleButton(
            icon = Lucide.Shuffle,
            contentDescription = stringResource(R.string.shuffle_all),
            onClick = onShuffle,
            size = 44.dp,
            background = NocturneColors.Surface,
            tint = NocturneColors.Amber,
            haptic = true,
        )
        Spacer(Modifier.width(10.dp))
        CircleButton(
            icon = Lucide.Play,
            contentDescription = stringResource(R.string.play_all),
            onClick = onPlayAll,
            size = 48.dp,
            iconSize = 20.dp,
            background = null,
            brush = NocturneBrushes.Primary,
            tint = NocturneColors.TextPrimary,
            haptic = true,
            modifier = Modifier.shadow(14.dp, CircleShape, ambientColor = NocturneColors.Shadow, spotColor = NocturneColors.Shadow),
        )
    }
}

private fun SortOrder.label() = when (this) {
    SortOrder.RECENT -> R.string.sort_recent
    SortOrder.TITLE -> R.string.sort_title
    SortOrder.DURATION -> R.string.sort_duration
}

// =====================================================================  Screen 4 — Playlists

@Composable
private fun PlaylistsTab(
    onTabChange: (HomeTab) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    bottomSpace: Dp,
    vm: HomeViewModel = hiltViewModel(),
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renaming by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deleting by remember { mutableStateOf<PlaylistEntity?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = screenTopPadding(), bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.playlists_title), style = NocturneType.ScreenTitle, color = NocturneColors.TextPrimary, modifier = Modifier.weight(1f))
                GradientButton(stringResource(R.string.new_playlist), onClick = { creating = true }, icon = Lucide.Plus, height = 44.dp)
            }
        }
        item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) { PillTabs(HomeTab.PLAYLISTS, onTabChange, Modifier.padding(top = 2.dp)) }
        item(key = "favorites") {
            PlaylistCard(
                name = stringResource(R.string.favorites),
                trackCount = favorites.size,
                coverModel = null,
                seed = "favorites",
                isFavorites = true,
                onClick = { onOpenPlaylist(-1L) },
                onPlay = vm::playFavorites,
            )
        }
        items(playlists, key = { it.playlist.id }) { p ->
            PlaylistCard(
                name = p.playlist.name,
                trackCount = p.trackCount,
                coverModel = p.firstThumb ?: p.playlist.coverUrl,
                seed = "pl${p.playlist.id}",
                fromYoutube = p.playlist.fromYoutube,
                onClick = { onOpenPlaylist(p.playlist.id) },
                onPlay = { vm.playPlaylist(p.playlist.id) },
                onLongClick = { menuFor = p.playlist },
                modifier = Modifier.animateItem(),
            )
        }
        if (playlists.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(null, stringResource(R.string.playlists_empty), icon = Lucide.ListMusic)
            }
        }
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.new_playlist_title),
            confirmLabel = stringResource(R.string.create),
            onDismiss = { creating = false },
            onConfirm = { vm.createPlaylist(it); creating = false },
        )
    }
    menuFor?.let { p ->
        NocturneSheet(onDismiss = { menuFor = null }) {
            Text(p.name, style = NocturneType.Section, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SheetAction(Lucide.Play, stringResource(R.string.cd_play_playlist), { vm.playPlaylist(p.id); menuFor = null })
            SheetAction(Lucide.Pencil, stringResource(R.string.rename), { renaming = p; menuFor = null })
            SheetAction(Lucide.Trash, stringResource(R.string.delete_playlist), { deleting = p; menuFor = null }, tint = NocturneColors.Error)
        }
    }
    renaming?.let { p ->
        NameDialog(
            title = stringResource(R.string.rename_playlist_title),
            confirmLabel = stringResource(R.string.save),
            initial = p.name,
            onDismiss = { renaming = null },
            onConfirm = { vm.renamePlaylist(p.id, it); renaming = null },
        )
    }
    deleting?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.delete_playlist_title),
            body = stringResource(R.string.delete_playlist_body),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = { vm.deletePlaylist(p.id); deleting = null },
        )
    }
}

// =====================================================================  Downloads tab

@Composable
private fun DownloadsTab(
    onTabChange: (HomeTab) -> Unit,
    onOpenAdd: (String?) -> Unit,
    bottomSpace: Dp,
    vm: DownloadsViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(top = screenTopPadding(), bottom = bottomSpace),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.downloads), style = NocturneType.ScreenTitle, color = NocturneColors.TextPrimary, modifier = Modifier.weight(1f))
                GradientButton(stringResource(R.string.add), onClick = { onOpenAdd(null) }, icon = Lucide.Plus, height = 44.dp)
            }
        }
        item(key = "tabs") { PillTabs(HomeTab.DOWNLOADS, onTabChange, Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 22.dp)) }
        downloadsSection(
            items = items,
            progress = progress,
            stats = stats,
            onPauseAll = vm::pauseAll,
            onResumeAll = vm::resumeAll,
            onRetry = vm::retry,
            onClearFinished = vm::clearFinished,
            showTitle = false,
        )
    }
}
