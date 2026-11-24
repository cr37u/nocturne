package com.nocturne.player.ui.screens

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nocturne.player.BuildConfig
import com.nocturne.player.R
import com.nocturne.player.data.TrackRepository
import com.nocturne.player.ui.components.IconButton44
import com.nocturne.player.ui.components.formatSize
import com.nocturne.player.ui.icons.Lucide
import com.nocturne.player.ui.theme.NocturneColors
import com.nocturne.player.ui.theme.NocturneShapes
import com.nocturne.player.ui.theme.NocturneType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Battery-optimization / OEM autostart helpers. */
object BackgroundHelper {
    fun isIgnoringOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    @SuppressLint("BatteryLife")
    fun requestIgnore(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (!start(context, direct)) openSystemBatteryPage(context)
    }

    fun openSystemBatteryPage(context: Context) {
        if (!start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) openAppDetails(context)
    }

    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    fun openXiaomiAutostart(context: Context) {
        val intents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
        )
        if (intents.none { start(context, it) }) openAppDetails(context)
    }

    fun openNotificationSettings(context: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (!start(context, i)) openAppDetails(context)
    }

    private fun openAppDetails(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    tracks: TrackRepository,
    private val prefs: com.nocturne.player.data.AppPrefs,
) : ViewModel() {
    val keepPlaying = kotlinx.coroutines.flow.MutableStateFlow(prefs.keepPlayingWhenClosed)
    fun setKeepPlaying(v: Boolean) {
        prefs.keepPlayingWhenClosed = v; keepPlaying.value = v
    }

    val parallel = kotlinx.coroutines.flow.MutableStateFlow(prefs.parallelDownloads)
    fun setParallel(v: Int) {
        prefs.parallelDownloads = v; parallel.value = prefs.parallelDownloads
    }

    val count = tracks.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val size = tracks.observeTotalSize().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
}

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val count by vm.count.collectAsStateWithLifecycle()
    val size by vm.size.collectAsStateWithLifecycle()
    val keepPlaying by vm.keepPlaying.collectAsStateWithLifecycle()
    val parallel by vm.parallel.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val ignoring = remember(tick) { BackgroundHelper.isIgnoringOptimizations(context) }
    val notificationsOn = remember(tick) { NotificationManagerCompat.from(context).areNotificationsEnabled() }

    Column(
        Modifier
            .fillMaxSize()
            .background(NocturneColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = screenTopPadding(), bottom = 32.dp + navBarPadding()),
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings),
            leading = { IconButton44(Lucide.ArrowLeft, stringResource(R.string.cd_back), onClick = onBack) },
        )

        // ---- Limbă: applies immediately (the Activity is recreated; scroll position, playback
        // in PlaybackService and downloads in WorkManager are not interrupted).
        val selected = remember { com.nocturne.player.i18n.AppLocale.selectedTag(context) }
        Section(stringResource(R.string.settings_language_section))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LanguageCard(
                name = stringResource(R.string.language_system),
                subtitle = stringResource(R.string.language_system_body),
                code = null,
                selected = selected == com.nocturne.player.i18n.AppLocale.SYSTEM,
                onClick = { com.nocturne.player.i18n.AppLocale.setLanguage(context, com.nocturne.player.i18n.AppLocale.SYSTEM) },
            )
            LanguageCard("Română", null, "RO", selected == "ro") { com.nocturne.player.i18n.AppLocale.setLanguage(context, "ro") }
            LanguageCard("English", null, "EN", selected == "en") { com.nocturne.player.i18n.AppLocale.setLanguage(context, "en") }
            LanguageCard("Русский", null, "RU", selected == "ru") { com.nocturne.player.i18n.AppLocale.setLanguage(context, "ru") }
        }

        Section(stringResource(R.string.settings_playback_section))
        SettingsCard {
            SettingsRow(
                icon = Lucide.Music,
                title = stringResource(R.string.keep_playing_title),
                subtitle = stringResource(if (keepPlaying) R.string.keep_playing_on else R.string.keep_playing_off),
                accent = keepPlaying,
                onClick = { vm.setKeepPlaying(!keepPlaying) },
                trailing = {
                    androidx.compose.material3.Switch(
                        checked = keepPlaying,
                        onCheckedChange = vm::setKeepPlaying,
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = NocturneColors.Background,
                            checkedTrackColor = NocturneColors.Amber,
                            uncheckedThumbColor = NocturneColors.TextSecondary,
                            uncheckedTrackColor = NocturneColors.SurfaceElevated,
                            uncheckedBorderColor = NocturneColors.ProgressTrack,
                        ),
                    )
                },
            )
            SettingsRow(
                icon = Lucide.Battery,
                title = stringResource(R.string.battery_title),
                subtitle = stringResource(if (ignoring) R.string.battery_on else R.string.battery_off),
                accent = ignoring,
                onClick = { if (!ignoring) BackgroundHelper.requestIgnore(context) else BackgroundHelper.openSystemBatteryPage(context) },
            )
            SettingsRow(
                icon = Lucide.Sliders,
                title = stringResource(R.string.battery_system_page),
                subtitle = stringResource(R.string.battery_system_page_body),
                onClick = { BackgroundHelper.openSystemBatteryPage(context) },
            )
            if (BackgroundHelper.isXiaomi) {
                SettingsRow(
                    icon = Lucide.Power,
                    title = stringResource(R.string.xiaomi_autostart),
                    subtitle = stringResource(R.string.xiaomi_autostart_body),
                    onClick = { BackgroundHelper.openXiaomiAutostart(context) },
                )
            }
            SettingsRow(
                icon = Lucide.Bell,
                title = stringResource(R.string.notifications_title),
                subtitle = stringResource(if (notificationsOn) R.string.notifications_on else R.string.notifications_off),
                accent = notificationsOn,
                onClick = { BackgroundHelper.openNotificationSettings(context) },
            )
        }

        Section(stringResource(R.string.downloads))
        SettingsCard {
            SettingsRow(
                icon = Lucide.Download,
                title = stringResource(R.string.parallel_downloads_title),
                subtitle = stringResource(R.string.parallel_downloads_body),
                onClick = null,
                accent = parallel > com.nocturne.player.data.AppPrefs.DEFAULT_PARALLEL,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton44(
                            Lucide.Minus, stringResource(R.string.cd_parallel_less),
                            onClick = { vm.setParallel(parallel - 1) },
                            enabled = parallel > com.nocturne.player.data.AppPrefs.MIN_PARALLEL,
                        )
                        Text(
                            parallel.toString(),
                            style = NocturneType.Section,
                            color = NocturneColors.TextPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.width(28.dp),
                        )
                        IconButton44(
                            Lucide.Plus, stringResource(R.string.cd_parallel_more),
                            onClick = { vm.setParallel(parallel + 1) },
                            enabled = parallel < com.nocturne.player.data.AppPrefs.MAX_PARALLEL,
                        )
                    }
                },
            )
        }

        Section(stringResource(R.string.storage_title))
        SettingsCard {
            SettingsRow(
                icon = Lucide.HardDrive,
                title = stringResource(R.string.storage_title),
                subtitle = stringResource(R.string.storage_body, pluralStringResource(R.plurals.songs_count, count, count), formatSize(size)),
                onClick = null,
            )
        }

        Section(stringResource(R.string.about_section))
        SettingsCard {
            SettingsRow(Lucide.Info, stringResource(R.string.app_version), BuildConfig.VERSION_NAME, onClick = null)
            SettingsRow(Lucide.Download, stringResource(R.string.extractor_version), BuildConfig.NEWPIPE_EXTRACTOR_VERSION, onClick = null)
        }
    }
}

/** Radio card: language name in its own language + code chip; selected = 1.5dp #3B6CF6 border + amber check. */
@Composable
private fun LanguageCard(name: String, subtitle: String?, code: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(NocturneShapes.Card)
            .background(NocturneColors.Surface)
            .then(
                if (selected) Modifier.border(1.5.dp, NocturneColors.BrightStart, NocturneShapes.Card)
                else Modifier.border(1.dp, NocturneColors.SurfaceElevated, NocturneShapes.Card)
            )
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(NocturneShapes.Thumb12)
                .background(if (selected) NocturneColors.BrightStart.copy(alpha = 0.2f) else NocturneColors.SurfaceElevated)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (code != null) {
                Text(code, style = NocturneType.Badge11, color = if (selected) NocturneColors.TextPrimary else NocturneColors.TextSecondary)
            } else {
                Icon(Lucide.Globe, null, tint = if (selected) NocturneColors.TextPrimary else NocturneColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = NocturneType.TrackTitle, color = NocturneColors.TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = NocturneType.Meta, color = NocturneColors.TextSecondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (selected) NocturneColors.Amber else Color.Transparent)
                .then(if (selected) Modifier else Modifier.border(1.5.dp, NocturneColors.ProgressTrack, androidx.compose.foundation.shape.CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Lucide.Check, null, tint = NocturneColors.Background, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = NocturneType.Overline12,
        color = NocturneColors.TextSecondary,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp, start = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NocturneShapes.Card)
            .background(NocturneColors.Surface),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) { content() }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    accent: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(NocturneShapes.Thumb12)
                .background(if (accent) NocturneColors.AmberBackground else NocturneColors.SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (accent) NocturneColors.Amber else NocturneColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = NocturneType.TrackTitle, color = NocturneColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = NocturneType.Meta, color = NocturneColors.TextSecondary)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp)); trailing()
        } else if (onClick != null) {
            Icon(Lucide.ChevronRight, null, tint = NocturneColors.TextSecondary, modifier = Modifier.size(18.dp))
        } else Spacer(Modifier.width(0.dp).background(Color.Transparent))
    }
}
