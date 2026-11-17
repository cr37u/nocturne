package com.nocturne.player.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A downloaded, locally playable track. [id] IS the YouTube video id (primary key), so every
 * track has its video id and duplicates are detected by it, never by title.
 *
 * Title / artist are library data: saved once at download time, never re-fetched or translated.
 * [userEdited] marks a manual edit ("Editează detaliile") that must never be overwritten.
 * [searchKey] is the accent/case-insensitive form of title + artist (see SearchKey).
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val filePath: String,
    val thumbnailPath: String?,
    val sizeBytes: Long,
    val format: String,
    val addedAt: Long,
    val isFavorite: Boolean = false,
    val sourceUrl: String,
    @ColumnInfo(defaultValue = "0") val userEdited: Boolean = false,
    @ColumnInfo(defaultValue = "") val searchKey: String = "",
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["youtubeId"], unique = true)],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fromYoutube: Boolean = false,
    /** YouTube playlist id for imported lists (used to merge re-imports). */
    val youtubeId: String? = null,
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("trackId")],
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
)

enum class DownloadStatus { QUEUED, ACTIVE, PAUSED, DONE, FAILED }

@Entity(
    tableName = "downloads",
    indices = [Index(value = ["videoId"]), Index(value = ["batchId"])],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val url: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val batchId: Long,
    val batchName: String?,
    /** Local playlist that should receive the track when done. */
    val playlistId: Long?,
    val playlistPosition: Int,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val attempts: Int = 0,
    val errorKey: String? = null,
    val totalBytes: Long = 0,
    val format: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "trackCount") val trackCount: Int,
    @ColumnInfo(name = "firstThumb") val firstThumb: String?,
)
