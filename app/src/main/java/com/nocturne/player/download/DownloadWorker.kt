package com.nocturne.player.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.nocturne.player.data.PlaylistRepository
import com.nocturne.player.data.StoragePaths
import com.nocturne.player.data.db.DownloadDao
import com.nocturne.player.data.db.DownloadEntity
import com.nocturne.player.data.db.DownloadStatus
import com.nocturne.player.data.db.TrackDao
import com.nocturne.player.data.db.TrackEntity
import com.nocturne.player.youtube.ErrorKind
import com.nocturne.player.youtube.FriendlyException
import com.nocturne.player.youtube.LinkResolver
import com.nocturne.player.youtube.NewPipeDownloader
import com.nocturne.player.youtube.toErrorKind
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Runs the whole download queue: up to [DownloadRepository.MAX_PARALLEL] tracks at once,
 * each fetched with sequential HTTP range requests (resumable), 3 attempts per track.
 * Runs as a foreground (dataSync) worker so it survives leaving the app.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val trackDao: TrackDao,
    private val playlists: PlaylistRepository,
    private val repo: DownloadRepository,
    private val resolver: LinkResolver,
    private val paths: StoragePaths,
    private val client: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    private val running = ConcurrentHashMap.newKeySet<Long>()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(DownloadNotifications.build(applicationContext, DownloadStats.EMPTY))

    override suspend fun doWork(): Result {
        DownloadNotifications.ensureChannel(applicationContext)
        runCatching { setForeground(getForegroundInfo()) }
        dao.resetStaleActive()

        coroutineScope {
            val notifier = launch { notificationLoop() }
            val semaphore = Semaphore(DownloadRepository.MAX_PARALLEL)
            while (isActive) {
                val next = dao.queued().filter { it.id !in running }
                if (next.isEmpty()) {
                    if (running.isEmpty()) break
                    delay(400)
                    continue
                }
                for (d in next) {
                    if (!semaphore.tryAcquire()) break
                    running += d.id
                    launch(Dispatchers.IO) {
                        try {
                            process(d)
                        } finally {
                            running -= d.id
                            repo.clearProgress(d.id)
                            semaphore.release()
                        }
                    }
                }
                delay(300)
            }
            notifier.cancel()
        }
        return Result.success()
    }

    private suspend fun notificationLoop() {
        while (coroutineContext.isActive) {
            val stats = DownloadStats.compute(dao.observeAll().first(), repo.progress.value)
            runCatching { setForeground(foregroundInfo(DownloadNotifications.build(applicationContext, stats))) }
            delay(1000)
        }
    }

    private fun foregroundInfo(notification: android.app.Notification) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(DownloadNotifications.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(DownloadNotifications.NOTIFICATION_ID, notification)
        }

    private suspend fun process(initial: DownloadEntity) {
        var current = dao.get(initial.id) ?: return
        if (current.status != DownloadStatus.QUEUED) return

        // Skip duplicates that finished in another batch meanwhile.
        trackDao.get(current.videoId)?.let { existing ->
            current.playlistId?.let { playlists.addTrackAt(it, existing.id, current.playlistPosition) }
            dao.update(current.copy(status = DownloadStatus.DONE, totalBytes = existing.sizeBytes, format = existing.format))
            return
        }

        var attempt = current.attempts
        while (attempt < DownloadRepository.MAX_ATTEMPTS) {
            attempt++
            current = current.copy(status = DownloadStatus.ACTIVE, attempts = attempt, errorKey = null)
            dao.update(current)
            try {
                current = downloadOne(current)
                dao.update(current.copy(status = DownloadStatus.DONE, errorKey = null))
                return
            } catch (e: CancellationException) {
                throw e // paused / cancelled: status already handled by the repository
            } catch (e: Throwable) {
                val kind = e.toErrorKind()
                val latest = dao.get(current.id) ?: return
                if (latest.status != DownloadStatus.ACTIVE) return
                if (attempt >= DownloadRepository.MAX_ATTEMPTS || !kind.retryable) {
                    dao.update(latest.copy(status = DownloadStatus.FAILED, attempts = attempt, errorKey = kind.name))
                    return
                }
                current = latest.copy(errorKey = kind.name)
                dao.update(current)
                delay(1500L * attempt)
            }
        }
    }

    private suspend fun downloadOne(d: DownloadEntity): DownloadEntity = withContext(Dispatchers.IO) {
        val stream = resolver.resolveStream(d.url)
        val part = paths.partFile(d.videoId, stream.extension)
        val target = paths.audioFile(d.videoId, stream.extension)

        val total = contentLength(stream.url)
        if (total > 0 && paths.musicDir.usableSpace in 1 until total + 5_000_000) {
            throw FriendlyException(ErrorKind.STORAGE)
        }
        // Resume only when it's the very same stream as the previous attempt.
        if (part.exists() && !(d.totalBytes == total && d.format == stream.formatName && total > 0)) part.delete()
        val updated = d.copy(totalBytes = total, format = stream.formatName)
        dao.update(updated)

        if (total > 0) downloadRanges(d.id, stream.url, part, total) else downloadPlain(d.id, stream.url, part)

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true); part.delete()
        }

        val thumb = downloadThumbnail(d.videoId, stream.thumbnailUrl ?: d.thumbnailUrl)
        val track = TrackEntity(
            id = d.videoId,
            title = d.title.ifBlank { stream.title },
            artist = d.artist.ifBlank { stream.artist },
            durationMs = if (stream.durationMs > 0) stream.durationMs else d.durationMs,
            filePath = target.absolutePath,
            thumbnailPath = thumb?.absolutePath,
            sizeBytes = target.length(),
            format = stream.formatName,
            addedAt = System.currentTimeMillis(),
            sourceUrl = d.url,
        )
        trackDao.upsert(track)
        d.playlistId?.let { playlists.addTrackAt(it, track.id, d.playlistPosition) }
        updated.copy(totalBytes = target.length())
    }

    /** Uses the googlevideo `clen` parameter, falling back to a 1-byte range probe. */
    private fun contentLength(url: String): Long {
        Uri.parse(url).getQueryParameter("clen")?.toLongOrNull()?.let { if (it > 0) return it }
        val probe = Request.Builder().url(url).header("Range", "bytes=0-0")
            .header("User-Agent", NewPipeDownloader.USER_AGENT).build()
        return runCatching {
            client.newCall(probe).execute().use { r ->
                r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
            }
        }.getOrDefault(-1L)
    }

    private suspend fun downloadRanges(id: Long, url: String, part: File, total: Long) {
        val speed = SpeedMeter()
        RandomAccessFile(part, "rw").use { raf ->
            var position = raf.length().coerceAtMost(total)
            raf.seek(position)
            val buffer = ByteArray(64 * 1024)
            while (position < total) {
                coroutineContext.ensureActive()
                val end = minOf(position + CHUNK_SIZE, total) - 1
                val request = Request.Builder().url(url)
                    .header("Range", "bytes=$position-$end")
                    .header("User-Agent", NewPipeDownloader.USER_AGENT)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty body")
                    // Server ignored the range: restart from zero with the full body.
                    if (response.code == 200 && position > 0) {
                        raf.setLength(0); raf.seek(0); position = 0
                    }
                    body.byteStream().use { input ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            raf.write(buffer, 0, n)
                            position += n
                            speed.add(n)
                            repo.reportProgress(id, LiveProgress(position, total, speed.bytesPerSecond()))
                        }
                    }
                }
            }
        }
        if (part.length() < total) throw IOException("Incomplete download")
    }

    private suspend fun downloadPlain(id: Long, url: String, part: File) {
        val speed = SpeedMeter()
        part.delete()
        val request = Request.Builder().url(url).header("User-Agent", NewPipeDownloader.USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            val total = body.contentLength()
            var written = 0L
            part.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        written += n
                        speed.add(n)
                        repo.reportProgress(id, LiveProgress(written, total, speed.bytesPerSecond()))
                    }
                }
            }
        }
    }

    private fun downloadThumbnail(videoId: String, url: String?): File? {
        val file = paths.thumbFile(videoId)
        val candidates = listOfNotNull(url, "https://i.ytimg.com/vi/$videoId/hqdefault.jpg").distinct()
        for (u in candidates) {
            val ok = runCatching {
                client.newCall(Request.Builder().url(u).build()).execute().use { r ->
                    if (!r.isSuccessful) return@use false
                    r.body?.byteStream()?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    file.length() > 0
                }
            }.getOrDefault(false)
            if (ok) return file
        }
        return null
    }

    /** Sliding 2-second window speed estimate. */
    private class SpeedMeter {
        private val samples = ArrayDeque<Pair<Long, Int>>()
        private var sum = 0L
        fun add(bytes: Int) {
            val now = System.nanoTime()
            samples.addLast(now to bytes); sum += bytes
            while (samples.isNotEmpty() && now - samples.first().first > 2_000_000_000L) {
                sum -= samples.removeFirst().second
            }
        }
        fun bytesPerSecond(): Long {
            if (samples.size < 2) return 0
            val span = (samples.last().first - samples.first().first).coerceAtLeast(1)
            return (sum * 1_000_000_000L / span)
        }
    }

    companion object {
        private const val CHUNK_SIZE = 4L * 1024 * 1024
    }
}
