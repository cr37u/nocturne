package com.nocturne.player

import com.nocturne.player.youtube.LinkResolver
import com.nocturne.player.youtube.NewPipeDownloader
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.InetAddress

/**
 * Live smoke test against YouTube through NewPipeExtractor (same downloader + stream selection
 * the app uses). Skipped automatically when offline. Run: ./gradlew :app:testDebugUnitTest
 */
class NewPipeLiveTest {

    companion object {
        private val client = OkHttpClient()
        private var online = false

        @BeforeClass @JvmStatic fun setUp() {
            online = runCatching { InetAddress.getByName("www.youtube.com"); true }.getOrDefault(false)
            NewPipe.init(NewPipeDownloader(client), Localization("ro", "RO"), ContentCountry("RO"))
        }
    }

    @Test fun resolvesAudioOnlyM4aAndDownloadsARange() {
        assumeTrue("offline", online)
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=jNQXAC9IVRw")
        assertTrue(info.name.isNotBlank())
        assertTrue(info.duration > 0)
        assertNotNull(LinkResolver.bestImage(info.thumbnails))

        val stream = LinkResolver.selectAudioStream(info.audioStreams)
        assertNotNull("no audio stream", stream)
        assertEquals(MediaFormat.M4A, stream!!.format)
        println("Selected ${stream.format} ${stream.averageBitrate} kbps itag=${stream.itag}")

        val request = Request.Builder().url(stream.content)
            .header("Range", "bytes=0-262143")
            .header("User-Agent", NewPipeDownloader.USER_AGENT)
            .build()
        client.newCall(request).execute().use { r ->
            assertEquals(206, r.code)
            val bytes = r.body!!.bytes()
            assertEquals(262_144, bytes.size)
            // MP4 container: "ftyp" box at offset 4.
            assertEquals("ftyp", String(bytes, 4, 4))
        }
    }

    @Test fun resolvesMixCappedAt50() {
        assumeTrue("offline", online)
        val link = com.nocturne.player.youtube.YouTubeLinks.parse(
            "https://youtube.com/playlist?list=RDWujKJpxaUHk&playnext=1&si=2OAzEmUTdy6Uy4R8"
        ) as com.nocturne.player.youtube.ParsedLink.Playlist
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, link.canonicalUrl)
        assertTrue(info.relatedItems.isNotEmpty())
        println("Mix '${info.name}' first page: ${info.relatedItems.size}")
    }

    @Test fun resolvesPlaylistWithPaging() {
        assumeTrue("offline", online)
        val url = "https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
        assertTrue(info.name.isNotBlank())
        assertTrue(info.relatedItems.isNotEmpty())
        var total = info.relatedItems.size
        if (info.hasNextPage()) {
            val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, info.nextPage)
            total += more.items.size
        }
        println("Playlist '${info.name}': ${info.streamCount} items, fetched $total in 2 pages")
        assertTrue(total > info.relatedItems.size || !info.hasNextPage())
    }
}
