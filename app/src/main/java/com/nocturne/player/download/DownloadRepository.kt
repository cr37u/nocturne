package com.nocturne.player.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nocturne.player.data.PlaylistRepository
import com.nocturne.player.data.StoragePaths
import com.nocturne.player.data.TrackRepository
import com.nocturne.player.data.db.DownloadDao
import com.nocturne.player.data.db.DownloadEntity
import com.nocturne.player.data.db.DownloadStatus
import com.nocturne.player.youtube.ResolvedLink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Live (non-persisted) progress of an active download. */
data class LiveProgress(val downloadedBytes: Long, val totalBytes: Long, val bytesPerSecond: Long)

/** Aggregated numbers for the "current run" of downloads (UI header + notification). */
data class DownloadStats(
    val done: Int,
    val total: Int,
    val active: Int,
    val bytesPerSecond: Long,
    val fraction: Float,
    val batchName: String?,
    val hasPaused: Boolean,
    val hasRunnable: Boolean,
) {
    companion object {
        val EMPTY = DownloadStats(0, 0, 0, 0, 0f, null, false, false)

        fun compute(all: List<DownloadEntity>, progress: Map<Long, LiveProgress>): DownloadStats {
            if (all.isEmpty()) return EMPTY
            val openBatches = all.filter { it.status != DownloadStatus.DONE }.map { it.batchId }.toSet()
            val set = if (openBatches.isNotEmpty()) all.filter { it.batchId in openBatches }
            else all.maxBy { it.batchId }.batchId.let { last -> all.filter { it.batchId == last } }
            val done = set.count { it.status == DownloadStatus.DONE }
            val activeItems = set.filter { it.status == DownloadStatus.ACTIVE }
            val partial = activeItems.sumOf { d ->
                val p = progress[d.id]
                if (p != null && p.totalBytes > 0) (p.downloadedBytes.toDouble() / p.totalBytes) else 0.0
            }
            val names = set.mapNotNull { it.batchName }.distinct()
            return DownloadStats(
                done = done,
                total = set.size,
                active = activeItems.size,
                bytesPerSecond = activeItems.sumOf { progress[it.id]?.bytesPerSecond ?: 0L },
                fraction = if (set.isEmpty()) 0f else ((done + partial) / set.size).toFloat().coerceIn(0f, 1f),
                batchName = names.singleOrNull(),
                hasPaused = set.any { it.status == DownloadStatus.PAUSED },
                hasRunnable = set.any { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.ACTIVE },
            )
        }
    }
}

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val tracks: TrackRepository,
    private val playlists: PlaylistRepository,
    private val paths: StoragePaths,
) {
    private val _progress = MutableStateFlow<Map<Long, LiveProgress>>(emptyMap())
    val progress: StateFlow<Map<Long, LiveProgress>> = _progress.asStateFlow()

    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun reportProgress(id: Long, p: LiveProgress) = _progress.update { it + (id to p) }
    fun clearProgress(id: Long) = _progress.update { it - id }

    /** Library ids + ids already waiting in the download queue. */
    suspend fun knownVideoIds(): Set<String> = tracks.allIds() + dao.pendingVideoIds()

    /**
     * Queues every item of [resolved] that is not already in the library or queue.
     * Imported YouTube playlists become local playlists ("Din YouTube"); tracks that are
     * already downloaded are linked into that playlist immediately.
     * @return number of newly queued downloads.
     */
    suspend fun enqueue(resolved: ResolvedLink): Int = withContext(Dispatchers.IO) {
        val inLibrary = tracks.allIds()
        val pending = dao.pendingVideoIds().toSet()
        val playlistId = if (resolved.isPlaylist && resolved.youtubePlaylistId != null) {
            playlists.getOrCreateImported(resolved.youtubePlaylistId, resolved.title, resolved.coverUrl)
        } else null

        val batchId = System.currentTimeMillis()
        val now = System.currentTimeMillis()
        val newItems = ArrayList<DownloadEntity>()
        resolved.items.forEachIndexed { index, item ->
            when {
                item.videoId in inLibrary -> if (playlistId != null) playlists.addTrackAt(playlistId, item.videoId, index)
                item.videoId in pending -> Unit
                newItems.any { it.videoId == item.videoId } -> Unit
                else -> newItems += DownloadEntity(
                    videoId = item.videoId,
                    url = item.url,
                    title = item.title,
                    artist = item.artist,
                    thumbnailUrl = item.thumbnailUrl,
                    durationMs = item.durationMs,
                    batchId = batchId,
                    batchName = if (resolved.isPlaylist) resolved.title else null,
                    playlistId = playlistId,
                    playlistPosition = index,
                    createdAt = now + index,
                )
            }
        }
        if (newItems.isNotEmpty()) {
            dao.insertAll(newItems)
            startWorker()
        }
        newItems.size
    }

    fun startWorker() {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    suspend fun pauseAll() {
        dao.pauseAll()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        _progress.value = emptyMap()
    }

    suspend fun resumeAll() {
        dao.resumeAll()
        startWorker()
    }

    /** Cancels every unfinished download and removes partial files. */
    suspend fun cancelAll() = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        dao.unfinished().forEach { d ->
            paths.musicDir.listFiles { f -> f.name.startsWith(d.videoId) && f.name.endsWith(".part") }
                ?.forEach { it.delete() }
        }
        dao.deleteUnfinished()
        _progress.value = emptyMap()
    }

    suspend fun retry(id: Long) {
        dao.retry(id)
        startWorker()
    }

    suspend fun clearFinished() = dao.clearFinished()

    /** "Șterge tot": cancels running / queued / failed downloads and empties the list. Songs already
     *  downloaded stay in the library. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        cancelAll()
        dao.clearFinished()
    }

    companion object {
        const val WORK_NAME = "nocturne-downloads"
        const val MAX_PARALLEL = 4
        const val MAX_ATTEMPTS = 3
    }
}
