package com.nocturne.player.data

import android.content.Context
import com.nocturne.player.data.db.PlaylistDao
import com.nocturne.player.data.db.PlaylistEntity
import com.nocturne.player.data.db.PlaylistWithCount
import com.nocturne.player.data.db.TrackDao
import com.nocturne.player.data.db.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** App-specific storage: /Android/data/<pkg>/files/music and /files/thumbs. */
@Singleton
class StoragePaths @Inject constructor(@ApplicationContext private val context: Context) {
    val musicDir: File
        get() = (context.getExternalFilesDir("music") ?: File(context.filesDir, "music")).also { it.mkdirs() }
    val thumbsDir: File
        get() = (context.getExternalFilesDir("thumbs") ?: File(context.filesDir, "thumbs")).also { it.mkdirs() }

    fun audioFile(videoId: String, ext: String) = File(musicDir, "$videoId.$ext")
    fun partFile(videoId: String, ext: String) = File(musicDir, "$videoId.$ext.part")
    fun thumbFile(videoId: String) = File(thumbsDir, "$videoId.jpg")
}

enum class SortOrder { RECENT, TITLE, DURATION }

@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {
    companion object {
        const val MIN_PARALLEL = 1
        const val MAX_PARALLEL = 10
        const val DEFAULT_PARALLEL = 4
    }

    private val prefs = context.getSharedPreferences("nocturne_prefs", Context.MODE_PRIVATE)

    var batteryPromptShown: Boolean
        get() = prefs.getBoolean("battery_prompt_shown", false)
        set(v) = prefs.edit().putBoolean("battery_prompt_shown", v).apply()

    var sortOrder: SortOrder
        get() = runCatching { SortOrder.valueOf(prefs.getString("sort", SortOrder.RECENT.name)!!) }.getOrDefault(SortOrder.RECENT)
        set(v) = prefs.edit().putString("sort", v.name).apply()

    /** Keep playing after the app is swiped away from recents (off = stop playback). */
    var keepPlayingWhenClosed: Boolean
        get() = prefs.getBoolean("keep_playing_closed", false)
        set(v) = prefs.edit().putBoolean("keep_playing_closed", v).apply()

    /** How many tracks download at the same time (1–10). */
    var parallelDownloads: Int
        get() = prefs.getInt("parallel_downloads", DEFAULT_PARALLEL).coerceIn(MIN_PARALLEL, MAX_PARALLEL)
        set(v) = prefs.edit().putInt("parallel_downloads", v.coerceIn(MIN_PARALLEL, MAX_PARALLEL)).apply()

    var lastOfferedClipboard: String?
        get() = prefs.getString("last_clip", null)
        set(v) = prefs.edit().putString("last_clip", v).apply()
}

@Singleton
class TrackRepository @Inject constructor(
    private val trackDao: TrackDao,
) {
    fun observeAll(): Flow<List<TrackEntity>> = trackDao.observeAll()
    fun observeFavorites(): Flow<List<TrackEntity>> = trackDao.observeFavorites()
    fun observeCount(): Flow<Int> = trackDao.observeCount()
    fun observeTotalSize(): Flow<Long> = trackDao.observeTotalSize()

    suspend fun get(id: String) = trackDao.get(id)
    fun observe(id: String): Flow<TrackEntity?> = trackDao.observe(id)
    suspend fun getMany(ids: List<String>): List<TrackEntity> {
        if (ids.isEmpty()) return emptyList()
        val byId = ids.chunked(500).flatMap { trackDao.getMany(it) }.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun allIds(): Set<String> = trackDao.allIds().toSet()

    suspend fun setFavorite(id: String, favorite: Boolean) = trackDao.setFavorite(id, favorite)

    /** "Editează detaliile": user-owned title/artist, never overwritten by later downloads. */
    suspend fun editMetadata(id: String, title: String, artist: String): TrackEntity? {
        val t = title.trim().ifEmpty { return null }
        val a = artist.trim()
        trackDao.updateMetadata(id, t, a, SearchKey.of(t, a))
        return trackDao.get(id)
    }

    suspend fun toggleFavorite(id: String): Boolean {
        val t = trackDao.get(id) ?: return false
        trackDao.setFavorite(id, !t.isFavorite)
        return !t.isFavorite
    }

    /** Removes the track from the library and deletes its audio + thumbnail files. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val t = trackDao.get(id) ?: return@withContext
        runCatching { File(t.filePath).delete() }
        t.thumbnailPath?.let { runCatching { File(it).delete() } }
        trackDao.delete(id)
    }
}

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
) {
    fun observeAll(): Flow<List<PlaylistWithCount>> = playlistDao.observeAllWithCount()
    fun observePlain(): Flow<List<PlaylistEntity>> = playlistDao.observeAll()
    fun observe(id: Long): Flow<PlaylistEntity?> = playlistDao.observe(id)
    fun observeTracks(id: Long): Flow<List<TrackEntity>> = playlistDao.observeTracks(id)

    suspend fun get(id: Long) = playlistDao.get(id)
    suspend fun tracks(id: Long) = playlistDao.tracks(id)

    suspend fun create(name: String): Long = playlistDao.insert(PlaylistEntity(name = name.trim()))

    /** Returns the local playlist for a YouTube playlist, creating it on first import. */
    suspend fun getOrCreateImported(youtubeId: String, name: String, coverUrl: String?): Long {
        playlistDao.findByYoutubeId(youtubeId)?.let { return it.id }
        return playlistDao.insert(
            PlaylistEntity(name = name, fromYoutube = true, youtubeId = youtubeId, coverUrl = coverUrl)
        )
    }

    suspend fun rename(id: Long, name: String) {
        val p = playlistDao.get(id) ?: return
        playlistDao.update(p.copy(name = name.trim()))
    }

    suspend fun delete(id: Long) = playlistDao.delete(id)

    suspend fun addTrack(playlistId: Long, trackId: String) = playlistDao.appendTrack(playlistId, trackId)

    suspend fun addTrackAt(playlistId: Long, trackId: String, position: Int) =
        playlistDao.addTrack(com.nocturne.player.data.db.PlaylistTrackCrossRef(playlistId, trackId, position))

    suspend fun removeTrack(playlistId: Long, trackId: String) = playlistDao.removeTrack(playlistId, trackId)
}
