package com.nocturne.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun get(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun observe(id: String): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<TrackEntity>

    @Query("SELECT id FROM tracks")
    suspend fun allIds(): List<String>

    @Query("SELECT COUNT(*) FROM tracks")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM tracks")
    fun observeTotalSize(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Query("UPDATE tracks SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.*,
          (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount,
          (SELECT t.thumbnailPath FROM playlist_tracks pt JOIN tracks t ON t.id = pt.trackId
             WHERE pt.playlistId = p.id ORDER BY pt.position LIMIT 1) AS firstThumb
        FROM playlists p ORDER BY p.createdAt DESC
        """
    )
    fun observeAllWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE youtubeId = :youtubeId LIMIT 1")
    suspend fun findByYoutubeId(youtubeId: String): PlaylistEntity?

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        SELECT t.* FROM tracks t JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC
        """
    )
    fun observeTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC
        """
    )
    suspend fun tracks(playlistId: Long): List<TrackEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrack(ref: PlaylistTrackCrossRef): Long

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: String)

    @Transaction
    suspend fun appendTrack(playlistId: Long, trackId: String) {
        addTrack(PlaylistTrackCrossRef(playlistId, trackId, maxPosition(playlistId) + 1))
    }
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC, id ASC")
    suspend fun queued(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT videoId FROM downloads WHERE status IN ('QUEUED','ACTIVE','PAUSED')")
    suspend fun pendingVideoIds(): List<String>

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED','ACTIVE')")
    suspend fun runnableCount(): Int

    @Insert
    suspend fun insertAll(items: List<DownloadEntity>)

    @Update
    suspend fun update(item: DownloadEntity)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET status = 'PAUSED' WHERE status IN ('QUEUED','ACTIVE')")
    suspend fun pauseAll()

    @Query("UPDATE downloads SET status = 'QUEUED' WHERE status = 'PAUSED'")
    suspend fun resumeAll()

    @Query("UPDATE downloads SET status = 'QUEUED' WHERE status = 'ACTIVE'")
    suspend fun resetStaleActive()

    @Query("UPDATE downloads SET status = 'QUEUED', attempts = 0, errorKey = NULL WHERE id = :id")
    suspend fun retry(id: Long)

    @Query("SELECT * FROM downloads WHERE status != 'DONE'")
    suspend fun unfinished(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE status != 'DONE'")
    suspend fun deleteUnfinished()

    @Query("DELETE FROM downloads WHERE status IN ('DONE','FAILED')")
    suspend fun clearFinished()
}
