package com.nocturne.player.youtube

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class RemoteItem(
    val videoId: String,
    val url: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
)

data class ResolvedLink(
    val isPlaylist: Boolean,
    val title: String,
    val coverUrl: String?,
    val youtubePlaylistId: String?,
    val items: List<RemoteItem>,
)

data class ResolvedStream(
    val url: String,
    val extension: String,
    val formatName: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
)

@Singleton
class LinkResolver @Inject constructor(@ApplicationContext private val context: Context) {

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Resolves a video or a playlist (ALL pages) into downloadable items. */
    suspend fun resolve(link: ParsedLink): ResolvedLink = withContext(Dispatchers.IO) {
        if (!isOnline()) throw FriendlyException(ErrorKind.NO_INTERNET)
        when (link) {
            is ParsedLink.Video -> {
                val info = StreamInfo.getInfo(ServiceList.YouTube, link.canonicalUrl)
                val item = RemoteItem(
                    videoId = link.videoId,
                    url = link.canonicalUrl,
                    title = info.name,
                    artist = cleanArtist(info.uploaderName),
                    durationMs = info.duration.coerceAtLeast(0) * 1000,
                    thumbnailUrl = bestImage(info.thumbnails),
                )
                ResolvedLink(false, info.name, item.thumbnailUrl, null, listOf(item))
            }

            is ParsedLink.Playlist -> {
                val service = ServiceList.YouTube
                val info = PlaylistInfo.getInfo(service, link.canonicalUrl)
                val limit = if (link.isMix) MIX_LIMIT else Int.MAX_VALUE
                val all = LinkedHashMap<String, RemoteItem>()
                fun addAll(list: List<StreamInfoItem>) = list.forEach { item ->
                    if (all.size < limit) item.toRemote()?.let { all.putIfAbsent(it.videoId, it) }
                }
                addAll(info.relatedItems)
                var page = info.nextPage
                var guard = 0
                while (page != null && all.size < limit && guard++ < 200) {
                    coroutineContext.ensureActive()
                    val more = PlaylistInfo.getMoreItems(service, link.canonicalUrl, page)
                    val before = all.size
                    addAll(more.items)
                    page = if (more.hasNextPage()) more.nextPage else null
                    if (link.isMix && all.size == before) break // mix started repeating
                }
                val items = all.values.toList()
                if (items.isEmpty()) throw FriendlyException(ErrorKind.UNAVAILABLE)
                ResolvedLink(
                    isPlaylist = true,
                    title = info.name,
                    coverUrl = bestImage(info.thumbnails) ?: items.first().thumbnailUrl,
                    youtubePlaylistId = link.listId,
                    items = items,
                )
            }
        }
    }

    /** Picks the best audio-only progressive stream: m4a/AAC first, then webm/opus. */
    suspend fun resolveStream(url: String): ResolvedStream = withContext(Dispatchers.IO) {
        if (!isOnline()) throw FriendlyException(ErrorKind.NO_INTERNET)
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        val stream = selectAudioStream(info.audioStreams) ?: throw NoAudioStreamException()
        val format = stream.format
        val ext = when (format) {
            MediaFormat.M4A -> "m4a"
            MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS -> "webm"
            MediaFormat.OPUS -> "opus"
            MediaFormat.MP3 -> "mp3"
            else -> format?.suffix ?: "m4a"
        }
        ResolvedStream(
            url = stream.content,
            extension = ext,
            formatName = if (ext == "webm") "opus" else ext,
            title = info.name,
            artist = cleanArtist(info.uploaderName),
            durationMs = info.duration.coerceAtLeast(0) * 1000,
            thumbnailUrl = bestImage(info.thumbnails),
        )
    }

    private fun StreamInfoItem.toRemote(): RemoteItem? {
        val id = YouTubeLinks.videoIdFromUrl(url) ?: return null
        return RemoteItem(
            videoId = id,
            url = YouTubeLinks.videoUrl(id),
            title = name ?: return null,
            artist = cleanArtist(uploaderName),
            durationMs = duration.coerceAtLeast(0) * 1000,
            thumbnailUrl = bestImage(thumbnails),
        )
    }

    companion object {
        /** Same size the YouTube app shows for a Mix. */
        const val MIX_LIMIT = 50

        fun selectAudioStream(streams: List<AudioStream>): AudioStream? {
            val usable = streams.filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    // Original-language track when multiple dubbed tracks exist.
                    (it.audioTrackType == null || it.audioTrackType?.name == "ORIGINAL")
            }.ifEmpty { streams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP } }
            return usable.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
                ?: usable.filter { it.format == MediaFormat.WEBMA_OPUS || it.format == MediaFormat.OPUS }.maxByOrNull { it.averageBitrate }
                ?: usable.filter { it.format == MediaFormat.WEBMA }.maxByOrNull { it.averageBitrate }
                ?: usable.maxByOrNull { it.averageBitrate }
        }

        fun bestImage(images: List<Image>?): String? =
            images?.maxByOrNull { (it.width.coerceAtLeast(1)) * (it.height.coerceAtLeast(1)) }?.url
                ?: images?.lastOrNull()?.url

        /** "Artist - Topic" channels → "Artist". */
        fun cleanArtist(raw: String?): String =
            raw.orEmpty().removeSuffix(" - Topic").removeSuffix("VEVO").trim().ifEmpty { "YouTube" }
    }
}
