package com.nocturne.player

import com.nocturne.player.data.db.DownloadEntity
import com.nocturne.player.data.db.DownloadStatus
import com.nocturne.player.download.DownloadStats
import com.nocturne.player.download.LiveProgress
import com.nocturne.player.ui.components.formatDuration
import com.nocturne.player.ui.components.formatMb
import com.nocturne.player.ui.components.formatSpeed
import com.nocturne.player.ui.components.formatTotalDuration
import com.nocturne.player.youtube.ErrorKind
import com.nocturne.player.youtube.toErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import java.net.UnknownHostException

class LogicTest {

    @Test fun durations() {
        assertEquals("3:58", formatDuration(238_000))
        assertEquals("1:42", formatDuration(102_000))
        assertEquals("1:02:05", formatDuration(3_725_000))
        assertEquals("1 h 32 min", formatTotalDuration(92 * 60_000L))
        assertEquals("45 min", formatTotalDuration(45 * 60_000L))
    }

    @Test fun romanianNumbers() {
        assertEquals("3,6 MB", formatMb((3.6 * 1_048_576).toLong()))
        assertEquals("1,3 MB/s", formatSpeed((1.3 * 1_048_576).toLong()))
    }

    @Test fun errorMapping() {
        assertEquals(ErrorKind.NO_INTERNET, UnknownHostException("x").toErrorKind())
        assertEquals(ErrorKind.AGE_RESTRICTED, AgeRestrictedContentException("x").toErrorKind())
        assertEquals(ErrorKind.UNAVAILABLE, ContentNotAvailableException("x").toErrorKind())
        assertEquals(ErrorKind.GENERIC, IllegalStateException().toErrorKind())
    }

    private fun d(id: Long, batch: Long, status: DownloadStatus) = DownloadEntity(
        id = id, videoId = "v$id", url = "", title = "t", artist = "a", thumbnailUrl = null, durationMs = 0,
        batchId = batch, batchName = "Mix", playlistId = null, playlistPosition = id.toInt(), status = status,
    )

    @Test fun statsOnlyCountOpenBatches() {
        val all = listOf(
            d(1, 1, DownloadStatus.DONE), d(2, 1, DownloadStatus.DONE), // finished old batch
            d(3, 2, DownloadStatus.DONE), d(4, 2, DownloadStatus.ACTIVE), d(5, 2, DownloadStatus.QUEUED), d(6, 2, DownloadStatus.FAILED),
        )
        val s = DownloadStats.compute(all, mapOf(4L to LiveProgress(50, 100, 2048)))
        assertEquals(1, s.done)
        assertEquals(4, s.total)
        assertEquals(1, s.active)
        assertEquals(2048, s.bytesPerSecond)
        assertEquals((1 + 0.5f) / 4, s.fraction, 0.001f)
        assertEquals("Mix", s.batchName)
        assertTrue(s.hasRunnable)
        assertFalse(s.hasPaused)
    }

    @Test fun statsFallBackToLatestBatch() {
        val all = listOf(d(1, 1, DownloadStatus.DONE), d(2, 5, DownloadStatus.DONE), d(3, 5, DownloadStatus.DONE))
        val s = DownloadStats.compute(all, emptyMap())
        assertEquals(2, s.total)
        assertEquals(2, s.done)
        assertEquals(1f, s.fraction, 0.001f)
    }
}
