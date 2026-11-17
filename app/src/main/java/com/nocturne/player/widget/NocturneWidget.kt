package com.nocturne.player.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.Player
import com.nocturne.player.MainActivity
import com.nocturne.player.R
import com.nocturne.player.i18n.AppLocale
import com.nocturne.player.ui.components.formatDuration

private val White = Color(0xFFF1F1F7)
private val OnGradient = Color(0xFFD4D4EA)
private val Purple = Color(0xFF3B0764)
private val Amber = Color(0xFFF5B942)
private val TrackBg = Color(0x38F1F1F7)

/** Home-screen widget: 2×2, 4×2 and 4×4 layouts from one responsive Glance widget. */
class NocturneWidget : GlanceAppWidget() {

    // Exact: layouts get the real widget size (Responsive only reports the nearest bucket,
    // so a tall 4×3 widget was drawn with the 110dp-high 4×2 layout).
    override val sizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
        /** Bumped by NocturneWidgetUpdater so every update recomposes with fresh state. */
        val REVISION = longPreferencesKey("revision")
    }
}

class NocturneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NocturneWidget()
}

@Composable
private fun WidgetContent() {
    val context = LocalContext.current
    currentState<Preferences>()[NocturneWidget.REVISION] // subscribe to updates
    val state = WidgetState.read(context)
    val l = AppLocale.localized(context)
    val size = LocalSize.current
    when {
        size.width >= 230.dp && size.height >= 220.dp -> LargeWidget(state, l)
        size.width >= 200.dp -> MediumWidget(state, l)
        else -> SmallWidget(state, l)
    }
}

// ------------------------------------------------------------------------------- actions

private fun command(name: String): Action =
    actionRunCallback<WidgetPlayerAction>(actionParametersOf(WidgetPlayerAction.COMMAND to name))

private fun openNowPlaying(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_NOW_PLAYING)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

private fun openAddFromClipboard(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_ADD_FROM_CLIPBOARD)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

// ------------------------------------------------------------------------------- pieces

private fun titleOrPlaceholder(s: WidgetState, l: Context) = if (s.hasMedia) s.title else l.getString(R.string.widget_nothing_playing)
private fun artistOrPlaceholder(s: WidgetState, l: Context) = if (s.hasMedia) s.artist else l.getString(R.string.widget_pick_song)

@Composable
private fun Artwork(s: WidgetState, sizeDp: Int, radiusDp: Int, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).toInt().coerceIn(64, 420)
    val bmp = WidgetArt.load(s.artworkPath, px, radiusDp * density)
    Box(
        modifier.size(sizeDp.dp).background(ImageProvider(R.drawable.widget_bg_small)).cornerRadius(radiusDp.dp)
            .clickable(openNowPlaying(context)),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(ImageProvider(bmp), contentDescription = s.title, modifier = GlanceModifier.size(sizeDp.dp), contentScale = ContentScale.Crop)
        } else {
            Image(ImageProvider(R.drawable.ic_stat_moon), contentDescription = null, modifier = GlanceModifier.size((sizeDp * 0.4f).dp))
        }
    }
}

@Composable
private fun PlayButton(s: WidgetState, l: Context, sizeDp: Int) {
    Box(
        GlanceModifier.size(sizeDp.dp).background(ImageProvider(R.drawable.widget_circle_white)).clickable(command(WidgetPlayerAction.PLAY_PAUSE)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(if (s.isPlaying) R.drawable.ic_n_pause else R.drawable.ic_n_play),
            contentDescription = l.getString(if (s.isPlaying) R.string.cd_pause else R.string.cd_play),
            modifier = GlanceModifier.size((sizeDp * 0.42f).dp),
            colorFilter = ColorFilter.tint(ColorProvider(Purple)),
        )
    }
}

@Composable
private fun IconAction(icon: Int, description: String, cmd: String, sizeDp: Int = 40, active: Boolean = false) {
    Box(
        GlanceModifier.size(sizeDp.dp)
            .then(if (active) GlanceModifier.background(ImageProvider(R.drawable.widget_amber_bg)) else GlanceModifier)
            .clickable(command(cmd)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(icon),
            contentDescription = description,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(ColorProvider(if (active) Amber else White)),
        )
    }
}

@Composable
private fun Progress(s: WidgetState) {
    LinearProgressIndicator(
        progress = s.progress,
        modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
        color = ColorProvider(White),
        backgroundColor = ColorProvider(TrackBg),
    )
}

private fun title(size: Int) = TextStyle(color = ColorProvider(White), fontSize = size.sp, fontWeight = FontWeight.Bold)
private fun meta(size: Int, color: Color = OnGradient) = TextStyle(color = ColorProvider(color), fontSize = size.sp)

// ------------------------------------------------------------------------------- 2×2

@Composable
private fun SmallWidget(s: WidgetState, l: Context) {
    val context = LocalContext.current
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(26.dp).background(ImageProvider(R.drawable.widget_bg_small))
            .clickable(openNowPlaying(context)),
    ) {
        val density = context.resources.displayMetrics.density
        val bmp = WidgetArt.load(s.artworkPath, (180 * density).toInt().coerceAtMost(420), 0f)
        if (bmp != null) {
            Image(ImageProvider(bmp), contentDescription = s.title, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_stat_moon), contentDescription = null, modifier = GlanceModifier.size(48.dp))
            }
        }
        Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_fade))) {}
        Row(
            GlanceModifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(titleOrPlaceholder(s, l), style = title(14), maxLines = 1)
                Text(artistOrPlaceholder(s, l), style = meta(12), maxLines = 1)
            }
            Spacer(GlanceModifier.width(6.dp))
            PlayButton(s, l, 44)
        }
    }
}

// ------------------------------------------------------------------------------- 4×2

@Composable
private fun MediumWidget(s: WidgetState, l: Context) {
    val height = LocalSize.current.height
    val width = LocalSize.current.width
    val art = minOf(height.value - 28f, width.value * 0.42f, 142f).toInt().coerceAtLeast(64)
    Row(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(26.dp)
            .background(ImageProvider(R.drawable.widget_bg_medium)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(s, art, 18)
        Spacer(GlanceModifier.width(14.dp))
        Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            Text(titleOrPlaceholder(s, l), style = title(15), maxLines = 1)
            Text(artistOrPlaceholder(s, l), style = meta(12), maxLines = 1)
            Spacer(GlanceModifier.height(10.dp))
            Progress(s)
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                IconAction(R.drawable.ic_n_prev, l.getString(R.string.cd_previous), WidgetPlayerAction.PREVIOUS)
                Spacer(GlanceModifier.defaultWeight())
                PlayButton(s, l, 48)
                Spacer(GlanceModifier.defaultWeight())
                IconAction(R.drawable.ic_n_next, l.getString(R.string.cd_next), WidgetPlayerAction.NEXT)
            }
        }
    }
}

// ------------------------------------------------------------------------------- 4×4

@Composable
private fun LargeWidget(s: WidgetState, l: Context) {
    val context = LocalContext.current
    val size = LocalSize.current
    // Fixed parts: padding 32 + header 26 + gaps 38 + progress 4 + controls 58 ≈ 160dp.
    // "URMEAZĂ" (~70dp) only when it fits; the artwork takes what is left (88–140dp).
    val showUpNext = size.height.value >= 340f
    val artByHeight = size.height.value - 160f - (if (showUpNext) 84f else 0f)
    val art = minOf((size.width.value - 32f) * 0.34f, artByHeight, 140f).toInt().coerceAtLeast(72)
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(26.dp)
            .background(ImageProvider(R.drawable.widget_bg_large)).padding(16.dp),
    ) {
        // Header: logo + Nocturne · "Adaugă link"
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(26.dp).background(ImageProvider(R.drawable.widget_logo_bg)), contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_stat_moon), contentDescription = null, modifier = GlanceModifier.size(16.dp))
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(l.getString(R.string.app_name), style = title(15), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
            Box(
                GlanceModifier.background(ImageProvider(R.drawable.widget_pill)).padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable(openAddFromClipboard(context)),
            ) {
                Text("+ " + l.getString(R.string.widget_add_link), style = TextStyle(color = ColorProvider(White), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(14.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Artwork(s, art, 18)
            Spacer(GlanceModifier.width(14.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(titleOrPlaceholder(s, l), style = title(16), maxLines = 2)
                Text(artistOrPlaceholder(s, l), style = meta(13), maxLines = 1)
                Spacer(GlanceModifier.height(4.dp))
                Text("${formatDuration(s.positionMs)} / ${formatDuration(s.durationMs)}", style = meta(12), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(14.dp))
        Progress(s)
        Spacer(GlanceModifier.height(10.dp))
        Row(GlanceModifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(
                if (s.shuffle) R.drawable.ic_n_shuffle else R.drawable.ic_n_shuffle_off,
                l.getString(if (s.shuffle) R.string.shuffle_on else R.string.shuffle_off),
                WidgetPlayerAction.SHUFFLE, active = s.shuffle,
            )
            Spacer(GlanceModifier.defaultWeight())
            IconAction(R.drawable.ic_n_prev, l.getString(R.string.cd_previous), WidgetPlayerAction.PREVIOUS)
            Spacer(GlanceModifier.defaultWeight())
            PlayButton(s, l, 54)
            Spacer(GlanceModifier.defaultWeight())
            IconAction(R.drawable.ic_n_next, l.getString(R.string.cd_next), WidgetPlayerAction.NEXT)
            Spacer(GlanceModifier.defaultWeight())
            IconAction(
                when (s.repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_n_repeat_one
                    Player.REPEAT_MODE_ALL -> R.drawable.ic_n_repeat
                    else -> R.drawable.ic_n_repeat_off
                },
                l.getString(
                    when (s.repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.string.repeat_one
                        Player.REPEAT_MODE_ALL -> R.string.repeat_all
                        else -> R.string.repeat_off
                    }
                ),
                WidgetPlayerAction.REPEAT, active = s.repeatMode != Player.REPEAT_MODE_OFF,
            )
        }
        if (showUpNext) {
            Spacer(GlanceModifier.defaultWeight())
            // "URMEAZĂ" card
            Column(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_card)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    l.getString(R.string.widget_up_next),
                    style = TextStyle(color = ColorProvider(OnGradient), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
                Text(s.nextTitle ?: l.getString(R.string.queue_empty), style = title(13), maxLines = 1)
                if (!s.nextArtist.isNullOrBlank()) Text(s.nextArtist, style = meta(12), maxLines = 1)
            }
        }
    }
}
