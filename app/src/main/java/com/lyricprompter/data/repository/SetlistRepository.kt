package com.lyricprompter.data.repository

import com.lyricprompter.data.local.db.SetlistDao
import com.lyricprompter.data.local.db.toDomain
import com.lyricprompter.data.local.db.toEntity
import com.lyricprompter.domain.model.Setlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetlistRepository @Inject constructor(
    private val setlistDao: SetlistDao
) {
    /**
     * Get all setlists as a Flow, sorted by last updated.
     */
    fun getAllSetlists(): Flow<List<Setlist>> {
        return setlistDao.getAllSetlists().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get a single setlist by ID.
     */
    suspend fun getSetlistById(id: String): Setlist? {
        return setlistDao.getSetlistById(id)?.toDomain()
    }

    /**
     * Get a single setlist by ID as a Flow.
     */
    fun getSetlistByIdFlow(id: String): Flow<Setlist?> {
        return setlistDao.getSetlistByIdFlow(id).map { it?.toDomain() }
    }

    /**
     * Insert or update a setlist.
     */
    suspend fun saveSetlist(setlist: Setlist) {
        val updatedSetlist = setlist.copy(updatedAt = System.currentTimeMillis())
        setlistDao.insertSetlist(updatedSetlist.toEntity())
    }

    /**
     * Delete a setlist by ID.
     */
    suspend fun deleteSetlist(id: String) {
        setlistDao.deleteSetlistById(id)
    }

    /**
     * Add a song to a setlist.
     */
    suspend fun addSongToSetlist(setlistId: String, songId: String) {
        val setlist = getSetlistById(setlistId) ?: return
        if (songId !in setlist.songIds) {
            val updatedSetlist = setlist.copy(
                songIds = setlist.songIds + songId
            )
            saveSetlist(updatedSetlist)
        }
    }

    /**
     * Remove a song from a setlist.
     */
    suspend fun removeSongFromSetlist(setlistId: String, songId: String) {
        val setlist = getSetlistById(setlistId) ?: return
        val updatedSetlist = setlist.copy(
            songIds = setlist.songIds - songId
        )
        saveSetlist(updatedSetlist)
    }

    /**
     * Reorder songs in a setlist.
     */
    suspend fun reorderSongs(setlistId: String, songIds: List<String>) {
        val setlist = getSetlistById(setlistId) ?: return
        val updatedSetlist = setlist.copy(songIds = songIds)
        saveSetlist(updatedSetlist)
    }

    /**
     * Get total setlist count.
     */
    suspend fun getSetlistCount(): Int {
        return setlistDao.getSetlistCount()
    }
}
