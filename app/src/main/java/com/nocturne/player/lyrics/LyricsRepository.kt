package com.nocturne.player.lyrics

import android.content.Context
import com.nocturne.player.data.db.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** One timed line of synced lyrics. */
data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
    data object Instrumental : Lyrics
    data object NotFound : Lyrics
}

/**
 * Lyrics from LRCLIB (https://lrclib.net — free, no account, no API key), fetched on demand
 * the first time and cached in app storage, so they are available offline afterwards.
 */
@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val dir: File get() = File(context.filesDir, "lyrics").also { it.mkdirs() }
    private fun cacheFile(trackId: String) = File(dir, "$trackId.json")

    /** Cached result only (no network). */
    suspend fun cached(track: TrackEntity): Lyrics? = withContext(Dispatchers.IO) {
        val f = cacheFile(track.id)
        if (!f.exists()) return@withContext null
        val json = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return@withContext null
        // "Not found" is re-checked after a week (LRCLIB grows every day).
        if (json.optString("kind") == "notfound" && System.currentTimeMillis() - f.lastModified() > NOT_FOUND_TTL) {
            return@withContext null
        }
        fromJson(json)
    }

    /** Cache first, then LRCLIB. Throws IOException when offline and nothing is cached. */
    suspend fun load(track: TrackEntity, forceRefresh: Boolean = false): Lyrics = withContext(Dispatchers.IO) {
        if (!forceRefresh) cached(track)?.let { return@withContext it }
        val result = fetch(track)
        cacheFile(track.id).writeText(toJson(result).toString())
        result
    }

    private fun fetch(track: TrackEntity): Lyrics {
        val (artist, title) = SongQuery.from(track.title, track.artist)
        val durationSec = track.durationMs / 1000

        // 1. Exact signature: artist + title + duration (±2 s).
        if (artist.isNotBlank()) {
            val url = "$BASE/get".toHttpUrl().newBuilder()
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("track_name", title)
                .apply { if (durationSec > 0) addQueryParameter("duration", durationSec.toString()) }
                .build()
            request(url.toString())?.let { body -> parse(JSONObject(body))?.let { return it } }
        }

        // 2. Free search, best duration match.
        val searchUrl = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", listOf(artist, title).filter { it.isNotBlank() }.joinToString(" "))
            .build()
        val body = request(searchUrl.toString()) ?: return Lyrics.NotFound
        val results = JSONArray(body)
        val candidates = (0 until results.length()).map { results.getJSONObject(it) }
        val best = candidates
            .filter { durationSec <= 0 || abs(it.optDouble("duration", 0.0) - durationSec) <= 8 }
            .sortedWith(compareByDescending<JSONObject> { !it.isNull("syncedLyrics") }.thenBy { abs(it.optDouble("duration", 0.0) - durationSec) })
            .firstOrNull()
        return best?.let { parse(it) } ?: Lyrics.NotFound
    }

    /** Returns the body for 200, null for 404; throws IOException for network/server errors. */
    private fun request(url: String): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Nocturne Android (https://github.com/cr37u/nocturne)")
            .build()
        client.newCall(req).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw IOException("LRCLIB HTTP ${r.code}")
            return r.body?.string()
        }
    }

    private fun parse(o: JSONObject): Lyrics? {
        if (o.optBoolean("instrumental", false)) return Lyrics.Instrumental
        val synced = o.optString("syncedLyrics").takeIf { !o.isNull("syncedLyrics") && it.isNotBlank() }
        if (synced != null) {
            val lines = Lrc.parse(synced)
            if (lines.isNotEmpty()) return Lyrics.Synced(lines)
        }
        val plain = o.optString("plainLyrics").takeIf { !o.isNull("plainLyrics") && it.isNotBlank() }
        return plain?.let { Lyrics.Plain(it.trim()) }
    }

    private fun toJson(l: Lyrics): JSONObject = JSONObject().apply {
        when (l) {
            is Lyrics.Synced -> {
                put("kind", "synced")
                put("lines", JSONArray().apply { l.lines.forEach { put(JSONObject().put("t", it.timeMs).put("x", it.text)) } })
            }
            is Lyrics.Plain -> { put("kind", "plain"); put("text", l.text) }
            Lyrics.Instrumental -> put("kind", "instrumental")
            Lyrics.NotFound -> put("kind", "notfound")
        }
    }

    private fun fromJson(o: JSONObject): Lyrics? = when (o.optString("kind")) {
        "synced" -> {
            val arr = o.optJSONArray("lines") ?: JSONArray()
            Lyrics.Synced((0 until arr.length()).map { arr.getJSONObject(it).let { j -> LyricLine(j.getLong("t"), j.getString("x")) } })
        }
        "plain" -> Lyrics.Plain(o.optString("text"))
        "instrumental" -> Lyrics.Instrumental
        "notfound" -> Lyrics.NotFound
        else -> null
    }

    private companion object {
        const val BASE = "https://lrclib.net/api"
        const val NOT_FOUND_TTL = 7L * 24 * 60 * 60 * 1000
    }
}

/** LRC "[mm:ss.xx] text" parser (multiple timestamps per line supported). */
object Lrc {
    private val stamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun parse(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        lrc.lineSequence().forEach { raw ->
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) return@forEach
            val text = raw.substring(stamps.last().range.last + 1).trim()
            stamps.forEach { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                out += LyricLine(min * 60_000 + sec * 1000 + ms, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }
}

/**
 * Turns a YouTube title into an artist / song query:
 * "Kavinsky - Nightcall (Official Audio)" → ("Kavinsky", "Nightcall").
 */
object SongQuery {
    private val noise = Regex(
        "\\s*[(\\[][^)\\]]*(official|video|audio|lyric|lyrics|visualizer|clip|премьера|клип|hd|4k|remaster|live|explicit)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE,
    )
    private val feat = Regex("\\s+(ft\\.?|feat\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE)
    private val topic = Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE)

    fun from(title: String, channel: String): Pair<String, String> {
        var t = noise.replace(title, "").replace(Regex("\\s{2,}"), " ").trim()
        var artist = topic.replace(channel, "").trim()
        val dash = Regex("\\s[-–—]\\s").find(t)
        if (dash != null) {
            artist = t.substring(0, dash.range.first).trim()
            t = t.substring(dash.range.last + 1).trim()
        }
        t = feat.replace(t, "").trim().trim('"', '“', '”', '«', '»')
        // First artist of a collab: "A, B", "A & B", "A x B", "A × B" (x only as a separate word).
        artist = feat.replace(artist, "").split(Regex("\\s*,\\s*|\\s+(?:&|×|x|X)\\s+")).first().trim()
        return artist to t
    }
}
