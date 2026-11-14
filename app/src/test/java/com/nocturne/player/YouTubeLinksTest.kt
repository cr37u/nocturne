package com.nocturne.player

import com.nocturne.player.youtube.ParsedLink
import com.nocturne.player.youtube.YouTubeLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLinksTest {

    private val id = "dQw4w9WgXcQ"
    private val list = "PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"

    @Test fun watchUrl() = assertEquals(ParsedLink.Video(id), YouTubeLinks.parse("https://www.youtube.com/watch?v=$id"))
    @Test fun watchUrlExtraParams() = assertEquals(ParsedLink.Video(id), YouTubeLinks.parse("https://youtube.com/watch?feature=share&v=$id&t=42s"))
    @Test fun mobileUrl() = assertEquals(ParsedLink.Video(id), YouTubeLinks.parse("https://m.youtube.com/watch?v=$id"))
    @Test fun shortUrl() = assertEquals(ParsedLink.Video(id), YouTubeLinks.parse("https://youtu.be/$id?si=abcdef"))
    @Test fun musicUrl() = assertEquals(ParsedLink.Video(id), YouTubeLinks.parse("https://music.youtube.com/watch?v=$id&feature=share"))
    @Test fun shorts() = assertEquals(ParsedLink.Video(id), YouTubeLinks.parse("https://www.youtube.com/shorts/$id"))
    @Test fun noScheme() = assertEquals(ParsedLink.Video(id), YouTubeLinks.parse("youtu.be/$id"))

    @Test fun playlistUrl() = assertEquals(ParsedLink.Playlist(list), YouTubeLinks.parse("https://www.youtube.com/playlist?list=$list"))
    @Test fun musicPlaylist() = assertEquals(ParsedLink.Playlist(list), YouTubeLinks.parse("https://music.youtube.com/playlist?list=$list"))
    @Test fun watchInPlaylist() = assertEquals(ParsedLink.Playlist(list), YouTubeLinks.parse("https://www.youtube.com/watch?v=$id&list=$list&index=3"))

    @Test fun mixFromWatchUrl() =
        assertEquals(ParsedLink.Playlist("RD$id"), YouTubeLinks.parse("https://www.youtube.com/watch?v=$id&list=RD$id&start_radio=1"))

    @Test fun mixFromPlaylistUrl() {
        val p = YouTubeLinks.parse("https://youtube.com/playlist?list=RDWujKJpxaUHk&playnext=1&si=2OAzEmUTdy6Uy4R8") as ParsedLink.Playlist
        assertEquals(true, p.isMix)
        assertEquals("https://www.youtube.com/watch?v=WujKJpxaUHk&list=RDWujKJpxaUHk", p.canonicalUrl)
    }

    @Test fun rejectsOtherHosts() = assertNull(YouTubeLinks.parse("https://vimeo.com/123456"))
    @Test fun rejectsBadId() = assertNull(YouTubeLinks.parse("https://www.youtube.com/watch?v=abc"))
    @Test fun rejectsGarbage() = assertNull(YouTubeLinks.parse("nu e link"))

    @Test fun extractsFromSharedText() = assertEquals(
        "https://youtu.be/$id",
        YouTubeLinks.extractUrl("Ascultă asta: https://youtu.be/$id."),
    )

    @Test fun extractNoneWhenNoYoutube() = assertNull(YouTubeLinks.extractUrl("vezi https://example.com/x"))

    @Test fun videoIdFromPlaylistItem() =
        assertEquals(id, YouTubeLinks.videoIdFromUrl("https://www.youtube.com/watch?v=$id&list=$list"))
}
