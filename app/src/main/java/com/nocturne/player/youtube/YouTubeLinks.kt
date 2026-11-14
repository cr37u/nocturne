package com.nocturne.player.youtube

import java.net.URI
import java.net.URLDecoder

sealed interface ParsedLink {
    val canonicalUrl: String

    data class Video(val videoId: String) : ParsedLink {
        override val canonicalUrl get() = YouTubeLinks.videoUrl(videoId)
    }

    data class Playlist(val listId: String) : ParsedLink {
        /** Auto-generated YouTube Mix ("RD…"): endless, so it's capped when importing. */
        val isMix get() = listId.startsWith("RD")

        override val canonicalUrl: String
            get() {
                val seed = listId.takeLast(11)
                return if (isMix && seed.length == 11) "https://www.youtube.com/watch?v=$seed&list=$listId"
                else "https://www.youtube.com/playlist?list=$listId"
            }
    }
}

/** Pure-Kotlin YouTube link parsing (unit-testable on the JVM). */
object YouTubeLinks {
    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val idRegex = Regex("^[A-Za-z0-9_-]{11}$")
    private val listRegex = Regex("^[A-Za-z0-9_-]{10,}$")
    private val hosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be")

    fun videoUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    /** Finds the first YouTube URL inside arbitrary text (e.g. "Title – https://youtu.be/…"). */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val candidates = urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ')', ']') }.toList()
        return candidates.firstOrNull { parse(it) != null }
            ?: text.trim().takeIf { !it.contains(' ') && parse("https://$it") != null }?.let { "https://$it" }
    }

    fun parse(raw: String?): ParsedLink? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
        if (!s.startsWith("http", ignoreCase = true)) s = "https://$s"
        val uri = runCatching { URI(s.replace(" ", "%20")) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in hosts) return null
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
        val query = parseQuery(uri.rawQuery)
        val list = query["list"]
        val v = query["v"]

        // Playlists and YouTube Mixes (list=RD…).
        if (list != null && listRegex.matches(list)) {
            val first = segments.firstOrNull()
            if (first == null || first == "playlist" || first == "watch" || host.endsWith("youtu.be")) {
                return ParsedLink.Playlist(list)
            }
        }

        val id: String? = when {
            host.endsWith("youtu.be") -> segments.firstOrNull()
            segments.firstOrNull() == "watch" -> v
            segments.firstOrNull() in setOf("shorts", "live", "embed", "v") -> segments.getOrNull(1)
            else -> v
        }
        return id?.takeIf { idRegex.matches(it) }?.let { ParsedLink.Video(it) }
    }

    fun videoIdFromUrl(url: String): String? = when (val p = parse(url)) {
        is ParsedLink.Video -> p.videoId
        else -> runCatching { parseQuery(URI(url).rawQuery)["v"] }.getOrNull()?.takeIf { idRegex.matches(it) }
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else {
                val key = URLDecoder.decode(pair.substring(0, i), "UTF-8")
                val value = URLDecoder.decode(pair.substring(i + 1), "UTF-8")
                key to value
            }
        }.toMap()
    }
}
