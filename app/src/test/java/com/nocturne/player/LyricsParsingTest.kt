package com.nocturne.player

import com.nocturne.player.lyrics.Lrc
import com.nocturne.player.lyrics.SongQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsParsingTest {
    @Test fun titleWithArtistDash() {
        assertEquals("Kavinsky" to "Nightcall", SongQuery.from("Kavinsky - Nightcall (Official Audio)", "Kavinsky"))
    }

    @Test fun cyrillicAndFeat() {
        assertEquals("Макс Корж" to "Горы по колено", SongQuery.from("Макс Корж - Горы по колено (Official video)", "Макс Корж"))
        assertEquals("Jah Khalib" to "Аромат твоих волос", SongQuery.from("Jah Khalib - Аромат твоих волос ft. Someone [Премьера клипа]", "music channel"))
    }

    @Test fun channelAsArtistWhenNoDash() {
        assertEquals("Timecop1983" to "On the Run", SongQuery.from("On the Run", "Timecop1983 - Topic"))
    }

    @Test fun collabKeepsFirstArtistButNotWordsWithX() {
        assertEquals("HammAli" to "А если это любовь?", SongQuery.from("HammAli & Navai - А если это любовь?", "HammAli & Navai"))
        assertEquals("Felix Jaehn" to "Ain't Nobody", SongQuery.from("Felix Jaehn - Ain't Nobody", "Felix Jaehn"))
    }

    @Test fun lrcLines() {
        val lines = Lrc.parse("[00:12.50]Prima linie\n[01:02.3][01:30.00]Refren\nnot a line")
        assertEquals(listOf(12_500L, 62_300L, 90_000L), lines.map { it.timeMs })
        assertEquals("Refren", lines[1].text)
    }
}
