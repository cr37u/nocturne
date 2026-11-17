package com.nocturne.player.widget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.media3.common.Player

/**
 * Snapshot of the player shown by the home-screen widgets. Written by PlaybackService on every
 * relevant change (track, play/pause, shuffle/repeat, metadata edit, language) and read by
 * [NocturneWidget] when it renders. Survives process death, so the widget never goes blank.
 */
data class WidgetState(
    val hasMedia: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artworkPath: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val nextTitle: String? = null,
    val nextArtist: String? = null,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    companion object {
        private const val PREFS = "widget_state"

        private fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun read(context: Context): WidgetState {
            val p = prefs(context)
            return WidgetState(
                hasMedia = p.getBoolean("has", false),
                title = p.getString("title", "") ?: "",
                artist = p.getString("artist", "") ?: "",
                artworkPath = p.getString("art", null),
                isPlaying = p.getBoolean("playing", false),
                positionMs = p.getLong("pos", 0),
                durationMs = p.getLong("dur", 0),
                shuffle = p.getBoolean("shuffle", false),
                repeatMode = p.getInt("repeat", Player.REPEAT_MODE_OFF),
                nextTitle = p.getString("next_title", null),
                nextArtist = p.getString("next_artist", null),
            )
        }

        /** Returns true when something visible changed (position-only changes count separately). */
        fun write(context: Context, s: WidgetState) {
            prefs(context).edit()
                .putBoolean("has", s.hasMedia)
                .putString("title", s.title)
                .putString("artist", s.artist)
                .putString("art", s.artworkPath)
                .putBoolean("playing", s.isPlaying)
                .putLong("pos", s.positionMs)
                .putLong("dur", s.durationMs)
                .putBoolean("shuffle", s.shuffle)
                .putInt("repeat", s.repeatMode)
                .putString("next_title", s.nextTitle)
                .putString("next_artist", s.nextArtist)
                .apply()
        }
    }
}

/** Decodes the local thumbnail, center-cropped to a square with rounded corners (all API levels). */
internal object WidgetArt {
    private var cacheKey: String? = null
    private var cache: Bitmap? = null

    fun load(path: String?, sizePx: Int, radiusPx: Float): Bitmap? {
        if (path.isNullOrEmpty()) return null
        val key = "$path|$sizePx|$radiusPx"
        if (key == cacheKey) return cache
        val bmp = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
            val src = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
            if (radiusPx <= 0f) return@runCatching scaled
            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            Canvas(out).drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radiusPx, radiusPx, paint)
            out
        }.getOrNull()
        cacheKey = key
        cache = bmp
        return bmp
    }
}
