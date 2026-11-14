package com.nocturne.player.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nocturne.player.R
import com.nocturne.player.data.AppPrefs
import com.nocturne.player.ui.components.HomeTab
import com.nocturne.player.ui.components.LocalSnackbar
import com.nocturne.player.ui.components.MiniPlayer
import com.nocturne.player.ui.screens.AddMusicScreen
import com.nocturne.player.ui.screens.BackgroundHelper
import com.nocturne.player.ui.screens.HomeScreen
import com.nocturne.player.ui.screens.MiniPlayerSpace
import com.nocturne.player.ui.screens.NowPlayingScreen
import com.nocturne.player.ui.screens.PlaylistDetailScreen
import com.nocturne.player.ui.screens.SettingsScreen
import com.nocturne.player.ui.screens.navBarPadding
import com.nocturne.player.ui.theme.NocturneBrushes
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType
import com.nocturne.player.youtube.YouTubeLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import javax.inject.Inject

sealed interface UiEvent {
    data object OpenNowPlaying : UiEvent
    data object OpenDownloads : UiEvent
    data class OpenLink(val text: String) : UiEvent
}

@HiltViewModel
class RootViewModel @Inject constructor(val prefs: AppPrefs) : ViewModel()

private const val ROUTE_HOME = "home"
private const val ROUTE_ADD = "add?link={link}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PLAYLIST = "playlist/{id}"

private fun NavHostController.openAdd(link: String?) {
    navigate(if (link == null) "add" else "add?link=${Uri.encode(link)}") { launchSingleTop = true }
}

@Composable
fun NocturneRoot(events: StateFlow<UiEvent?>, onEventConsumed: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val nav = rememberNavController()
    val playerVm: PlayerViewModel = hiltViewModel()
    val rootVm: RootViewModel = hiltViewModel()
    val player by playerVm.state.collectAsStateWithLifecycle()
    val event by events.collectAsStateWithLifecycle()

    var expanded by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(HomeTab.SONGS) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val miniVisible = player.hasMedia && (route == ROUTE_HOME || route == ROUTE_PLAYLIST)
    val bottomSpace = (if (miniVisible) MiniPlayerSpace else 24.dp) + navBarPadding()

    // ---- external events (share intent, notification taps)
    LaunchedEffect(event) {
        when (val e = event) {
            null -> return@LaunchedEffect
            UiEvent.OpenNowPlaying -> expanded = true
            UiEvent.OpenDownloads -> {
                expanded = false
                nav.popBackStack(ROUTE_HOME, inclusive = false)
                tab = HomeTab.DOWNLOADS
            }
            is UiEvent.OpenLink -> {
                expanded = false
                val link = YouTubeLinks.extractUrl(e.text)
                if (link != null) nav.openAdd(link)
                else snackbar.showSnackbar(context.getString(R.string.error_invalid_link))
            }
        }
        onEventConsumed()
    }

    // ---- clipboard YouTube link detection when the app gains focus
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.filter { it }.collect {
            val link = clipboardYouTubeLink(context, rootVm.prefs) ?: return@collect
            if (nav.currentDestination?.route == ROUTE_ADD) return@collect
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.clipboard_found),
                actionLabel = context.getString(R.string.clipboard_add),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                expanded = false
                nav.openAdd(link)
            }
        }
    }

    // ---- first launch: notifications permission, then battery optimization explanation
    var showBatteryDialog by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!rootVm.prefs.batteryPromptShown && !BackgroundHelper.isIgnoringOptimizations(context)) showBatteryDialog = true
    }
    LaunchedEffect(Unit) {
        val needsNotif = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotif) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (!rootVm.prefs.batteryPromptShown && !BackgroundHelper.isIgnoringOptimizations(context)) showBatteryDialog = true
    }

    BackHandler(enabled = expanded) { expanded = false }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        SharedTransitionLayout(Modifier.fillMaxSize().background(NocturneColors.Background)) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = nav,
                    startDestination = ROUTE_HOME,
                    enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 } },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 10 } },
                ) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            tab = tab,
                            onTabChange = { tab = it },
                            onOpenAdd = { nav.openAdd(it) },
                            onOpenSettings = { nav.navigate(ROUTE_SETTINGS) { launchSingleTop = true } },
                            onOpenPlaylist = { nav.navigate("playlist/$it") },
                            bottomSpace = bottomSpace,
                        )
                    }
                    composable(
                        ROUTE_ADD,
                        arguments = listOf(navArgument("link") { type = NavType.StringType; nullable = true; defaultValue = null }),
                    ) { AddMusicScreen(onBack = { nav.popBackStack() }) }
                    composable(ROUTE_SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
                    composable(ROUTE_PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        PlaylistDetailScreen(onBack = { nav.popBackStack() }, bottomSpace = bottomSpace)
                    }
                }

                // Status-bar scrim so scrolled content never sits under the clock/icons.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to NocturneColors.Background,
                                0.7f to NocturneColors.Background.copy(alpha = 0.92f),
                                1f to NocturneColors.Background.copy(alpha = 0f),
                            )
                        )
                )

                // Mini-player ↔ Now Playing with a shared-element artwork transition (300 ms).
                AnimatedContent(
                    targetState = expanded && player.hasMedia,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "nowPlaying",
                    modifier = Modifier.fillMaxSize(),
                ) { isExpanded ->
                    val artworkShared = Modifier.sharedElement(
                        state = rememberSharedContentState(key = "artwork"),
                        animatedVisibilityScope = this@AnimatedContent,
                        boundsTransform = { _, _ -> tween(300) },
                    )
                    if (isExpanded) {
                        NowPlayingScreen(onClose = { expanded = false }, artworkModifier = artworkShared)
                    } else if (miniVisible) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(170.dp + navBarPadding())
                                    .background(NocturneBrushes.BottomFade)
                            )
                            MiniPlayer(
                                state = player,
                                onExpand = { expanded = true },
                                onTogglePlay = playerVm::togglePlay,
                                onNext = playerVm::next,
                                artworkModifier = artworkShared,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 22.dp + navBarPadding()),
                            )
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = if (miniVisible && !expanded) bottomSpace - 8.dp else 24.dp + navBarPadding()),
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = NocturneShapes.Card,
                        containerColor = NocturneColors.SurfaceElevated,
                        contentColor = NocturneColors.TextPrimary,
                        actionColor = NocturneColors.Amber,
                        dismissActionContentColor = NocturneColors.TextSecondary,
                    )
                }
            }
        }
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false; rootVm.prefs.batteryPromptShown = true },
            containerColor = NocturneColors.SurfaceElevated,
            shape = NocturneShapes.CardLarge,
            title = { Text(stringResource(R.string.battery_dialog_title), style = NocturneType.Section, color = NocturneColors.TextPrimary) },
            text = { Text(stringResource(R.string.battery_dialog_body), style = NocturneType.Body15, color = NocturneColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryDialog = false
                    rootVm.prefs.batteryPromptShown = true
                    BackgroundHelper.requestIgnore(context)
                }) { Text(stringResource(R.string.battery_dialog_allow), style = NocturneType.Button14, color = NocturneColors.Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false; rootVm.prefs.batteryPromptShown = true }) {
                    Text(stringResource(R.string.battery_dialog_later), style = NocturneType.Button14, color = NocturneColors.TextSecondary)
                }
            },
        )
    }
}

/**
 * Returns a new YouTube link from the clipboard, or null. Uses the clip timestamp so the
 * clipboard content (and Android's "pasted" toast) is only read once per new clip.
 */
private fun clipboardYouTubeLink(context: Context, prefs: AppPrefs): String? {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
    val desc = runCatching { cm.primaryClipDescription }.getOrNull() ?: return null
    if (!desc.hasMimeType("text/*")) return null
    val stamp = desc.timestamp.toString()
    if (prefs.lastOfferedClipboard?.startsWith("$stamp|") == true) return null
    val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
    val link = YouTubeLinks.extractUrl(text)
    prefs.lastOfferedClipboard = "$stamp|${link.orEmpty()}"
    return link
}
