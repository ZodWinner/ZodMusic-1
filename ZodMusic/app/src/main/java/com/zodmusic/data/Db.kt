package com.zodmusic.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverPath: String? = null,
)

/** Uma música dentro de uma playlist, com a posição escolhida pelo usuário. */
@Entity(primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

/** Capa personalizada de uma música. */
@Entity
data class TrackCover(
    @PrimaryKey val trackId: Long,
    val coverPath: String,
)

@Dao
abstract class ZodDao {
    @Query("SELECT * FROM PlaylistEntity ORDER BY name COLLATE NOCASE")
    abstract fun playlists(): Flow<List<PlaylistEntity>>

    @Insert
    abstract suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Query("UPDATE PlaylistEntity SET name = :name WHERE id = :id")
    abstract suspend fun renamePlaylist(id: Long, name: String)

    @Query("UPDATE PlaylistEntity SET coverPath = :path WHERE id = :id")
    abstract suspend fun setPlaylistCover(id: Long, path: String?)

    @Query("DELETE FROM PlaylistEntity WHERE id = :id")
    abstract suspend fun deletePlaylistRow(id: Long)

    @Query("DELETE FROM PlaylistTrack WHERE playlistId = :id")
    abstract suspend fun clearPlaylist(id: Long)

    @Transaction
    open suspend fun deletePlaylist(id: Long) {
        clearPlaylist(id)
        deletePlaylistRow(id)
    }

    @Query("SELECT * FROM PlaylistTrack WHERE playlistId = :id ORDER BY position")
    abstract fun playlistTracks(id: Long): Flow<List<PlaylistTrack>>

    @Query("SELECT * FROM PlaylistTrack WHERE playlistId = :id ORDER BY position")
    abstract suspend fun playlistTracksOnce(id: Long): List<PlaylistTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTracks(list: List<PlaylistTrack>)

    @Query("DELETE FROM PlaylistTrack WHERE playlistId = :pid AND trackId = :tid")
    abstract suspend fun removeTrack(pid: Long, tid: Long)

    /** Grava a ordem completa da playlist de uma vez. */
    @Transaction
    open suspend fun saveOrder(pid: Long, orderedTrackIds: List<Long>) {
        clearPlaylist(pid)
        insertTracks(orderedTrackIds.mapIndexed { i, id -> PlaylistTrack(pid, id, i) })
    }

    @Query("SELECT * FROM TrackCover")
    abstract fun covers(): Flow<List<TrackCover>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun setCover(c: TrackCover)

    @Query("DELETE FROM TrackCover WHERE trackId = :id")
    abstract suspend fun clearCover(id: Long)
}

@Database(
    entities = [PlaylistEntity::class, PlaylistTrack::class, TrackCover::class],
    version = 1,
    exportSchema = false,
)
abstract class ZodDb : RoomDatabase() {
    abstract fun dao(): ZodDao

    companion object {
        @Volatile private var instance: ZodDb? = null

        fun get(context: Context): ZodDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ZodDb::class.java,
                "zodmusic.db",
            ).build().also { instance = it }
        }
    }
}
