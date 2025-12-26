package com.lyricprompter.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lyricprompter.data.local.db.entities.SetlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SetlistDao {

    @Query("SELECT * FROM setlists ORDER BY updatedAt DESC")
    fun getAllSetlists(): Flow<List<SetlistEntity>>

    @Query("SELECT * FROM setlists WHERE id = :id")
    suspend fun getSetlistById(id: String): SetlistEntity?

    @Query("SELECT * FROM setlists WHERE id = :id")
    fun getSetlistByIdFlow(id: String): Flow<SetlistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetlist(setlist: SetlistEntity)

    @Update
    suspend fun updateSetlist(setlist: SetlistEntity)

    @Delete
    suspend fun deleteSetlist(setlist: SetlistEntity)

    @Query("DELETE FROM setlists WHERE id = :id")
    suspend fun deleteSetlistById(id: String)

    @Query("SELECT COUNT(*) FROM setlists")
    suspend fun getSetlistCount(): Int
}
