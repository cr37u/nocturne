package com.nocturne.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nocturne.player.R
import com.nocturne.player.data.db.PlaylistEntity
import com.nocturne.player.data.db.TrackEntity
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun NocturneSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = NocturneShapes.Sheet,
        containerColor = NocturneColors.SurfaceElevated,
        contentColor = NocturneColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = NocturneColors.TextSecondary.copy(alpha = 0.5f)) },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) { content() }
    }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = NocturneColors.TextPrimary) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = NocturneType.Body15, color = tint)
    }
}

/** Long-press / "more" menu for a track. */
@Composable
fun TrackActionsSheet(
    track: TrackEntity,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onEditDetails: (() -> Unit)? = null,
) {
    NocturneSheet(onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.thumbnailPath, track.id, NocturneShapes.CoverSmall, Modifier.size(52.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = NocturneType.TrackTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = NocturneType.Meta, color = NocturneColors.TextSecondary, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        SheetAction(Lucide.ListStart, stringResource(R.string.menu_play_next), onPlayNext)
        SheetAction(Lucide.ListEnd, stringResource(R.string.menu_add_queue), onAddToQueue)
        SheetAction(Lucide.ListPlus, stringResource(R.string.menu_add_playlist), onAddToPlaylist)
        SheetAction(
            if (track.isFavorite) Lucide.HeartFilled else Lucide.Heart,
            stringResource(if (track.isFavorite) R.string.menu_favorite_remove else R.string.menu_favorite_add),
            onToggleFavorite,
            tint = if (track.isFavorite) NocturneColors.Amber else NocturneColors.TextPrimary,
        )
        if (onEditDetails != null) {
            SheetAction(Lucide.Pencil, stringResource(R.string.menu_edit_details), onEditDetails)
        }
        if (onRemoveFromPlaylist != null) {
            SheetAction(Lucide.X, stringResource(R.string.menu_remove_from_playlist), onRemoveFromPlaylist)
        }
        SheetAction(Lucide.Trash, stringResource(R.string.menu_delete), onDelete, tint = NocturneColors.Error)
    }
}

/**
 * "Editează detaliile": edit title and artist (bottom sheet, radius 28). The values are library
 * data owned by the user; they are never translated or overwritten by later downloads.
 */
@Composable
fun EditTrackSheet(
    track: TrackEntity,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String) -> Unit,
) {
    var title by rememberSaveable(track.id) { mutableStateOf(track.title) }
    var artist by rememberSaveable(track.id) { mutableStateOf(track.artist) }
    val canSave = title.isNotBlank()
    NocturneSheet(onDismiss) {
        Text(
            stringResource(R.string.edit_details_title),
            style = NocturneType.Section,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        EditField(stringResource(R.string.edit_title_label), title, { title = it.take(200) }, ImeAction.Next)
        EditField(stringResource(R.string.edit_artist_label), artist, { artist = it.take(120) }, ImeAction.Done) {
            if (canSave) onSave(title.trim(), artist.trim())
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), style = NocturneType.Button14, color = NocturneColors.TextSecondary)
            }
            TextButton(onClick = { onSave(title.trim(), artist.trim()) }, enabled = canSave) {
                Text(
                    stringResource(R.string.save),
                    style = NocturneType.Button14,
                    color = if (canSave) NocturneColors.Amber else NocturneColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    ime: ImeAction,
    onDone: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
        Text(label, style = NocturneType.Meta, color = NocturneColors.TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(NocturneShapes.Input)
                .background(NocturneColors.Surface)
                .border(1.5.dp, NocturneColors.BrightStart, NocturneShapes.Input)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = NocturneType.Body15.copy(color = NocturneColors.TextPrimary),
                cursorBrush = SolidColor(NocturneColors.BrightStart),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ime),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AddToPlaylistSheet(
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onPick: (PlaylistEntity) -> Unit,
    onCreateNew: () -> Unit,
) {
    NocturneSheet(onDismiss) {
        Text(
            stringResource(R.string.choose_playlist),
            style = NocturneType.Section,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SheetAction(Lucide.Plus, stringResource(R.string.new_playlist_title), onCreateNew)
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(playlists, key = { it.id }) { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onPick(p) }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(p.coverUrl, "pl${p.id}", NocturneShapes.Thumb12, Modifier.size(40.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(p.name, style = NocturneType.TrackTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (p.fromYoutube) {
                        Text(stringResource(R.string.from_youtube), style = NocturneType.Badge11, color = NocturneColors.TextSecondary)
                    }
                }
            }
        }
    }
}

/** Create / rename playlist dialog. */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NocturneColors.SurfaceElevated,
        shape = NocturneShapes.CardLarge,
        title = { Text(title, style = NocturneType.Section, color = NocturneColors.TextPrimary) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(NocturneShapes.Input)
                    .background(NocturneColors.Surface)
                    .border(1.5.dp, NocturneColors.BrightStart, NocturneShapes.Input)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) Text(stringResource(R.string.playlist_name_hint), style = NocturneType.Body15, color = NocturneColors.TextSecondary)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    singleLine = true,
                    textStyle = NocturneType.Body15.copy(color = NocturneColors.TextPrimary),
                    cursorBrush = SolidColor(NocturneColors.BrightStart),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text.trim()) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(confirmLabel, style = NocturneType.Button14, color = if (text.isNotBlank()) NocturneColors.Amber else NocturneColors.TextSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), style = NocturneType.Button14, color = NocturneColors.TextSecondary)
            }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NocturneColors.SurfaceElevated,
        shape = NocturneShapes.CardLarge,
        title = { Text(title, style = NocturneType.Section, color = NocturneColors.TextPrimary) },
        text = { Text(body, style = NocturneType.Body15, color = NocturneColors.TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = NocturneType.Button14, color = if (destructive) NocturneColors.Error else NocturneColors.Amber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), style = NocturneType.Button14, color = NocturneColors.TextSecondary)
            }
        },
    )
}

/** Centered empty-state message. */
@Composable
fun EmptyState(title: String?, body: String, modifier: Modifier = Modifier, icon: ImageVector = Lucide.Music) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(NocturneShapes.Cover).background(NocturneColors.Surface),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = NocturneColors.TextSecondary, modifier = Modifier.size(28.dp)) }
        if (title != null) Text(title, style = NocturneType.Section, color = NocturneColors.TextPrimary)
        Text(body, style = NocturneType.Meta, color = NocturneColors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
