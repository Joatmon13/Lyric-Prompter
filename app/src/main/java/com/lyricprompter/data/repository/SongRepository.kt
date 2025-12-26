package com.lyricprompter.data.repository

import com.lyricprompter.data.local.db.SongDao
import com.lyricprompter.data.local.db.toDomain
import com.lyricprompter.data.local.db.toEntity
import com.lyricprompter.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository @Inject constructor(
    private val songDao: SongDao
) {
    /**
     * Get all songs as a Flow, sorted by last updated.
     */
    fun getAllSongs(): Flow<List<Song>> {
        return songDao.getAllSongs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get a single song by ID.
     */
    suspend fun getSongById(id: String): Song? {
        return songDao.getSongById(id)?.toDomain()
    }

    /**
     * Get a single song by ID as a Flow.
     */
    fun getSongByIdFlow(id: String): Flow<Song?> {
        return songDao.getSongByIdFlow(id).map { it?.toDomain() }
    }

    /**
     * Search songs by title or artist.
     */
    fun searchSongs(query: String): Flow<List<Song>> {
        return songDao.searchSongs(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get recently updated songs.
     */
    fun getRecentSongs(limit: Int = 5): Flow<List<Song>> {
        return songDao.getRecentSongs(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Insert or update a song.
     */
    suspend fun saveSong(song: Song) {
        val updatedSong = song.copy(updatedAt = System.currentTimeMillis())
        songDao.insertSong(updatedSong.toEntity())
    }

    /**
     * Delete a song by ID.
     */
    suspend fun deleteSong(id: String) {
        songDao.deleteSongById(id)
    }

    /**
     * Get total song count.
     */
    suspend fun getSongCount(): Int {
        return songDao.getSongCount()
    }
}
