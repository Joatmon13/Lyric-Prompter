package com.lyricprompter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lyricprompter.data.local.db.entities.LogEventEntity
import com.lyricprompter.data.local.db.entities.SessionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionLogDao {
    // Session operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionLogEntity)

    @Update
    suspend fun updateSession(session: SessionLogEntity)

    @Query("SELECT * FROM session_logs ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionLogEntity>>

    @Query("SELECT * FROM session_logs WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): SessionLogEntity?

    @Query("SELECT * FROM session_logs WHERE songId = :songId ORDER BY startTime DESC")
    fun getSessionsForSong(songId: String): Flow<List<SessionLogEntity>>

    @Query("DELETE FROM session_logs WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM session_logs")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM session_logs WHERE startTime < :beforeTime")
    suspend fun deleteSessionsBefore(beforeTime: Long)

    // Event operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: LogEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<LogEventEntity>)

    @Query("SELECT * FROM log_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getEventsForSession(sessionId: String): Flow<List<LogEventEntity>>

    @Query("SELECT * FROM log_events WHERE sessionId = :sessionId AND eventType = :eventType ORDER BY timestamp ASC")
    fun getEventsOfType(sessionId: String, eventType: String): Flow<List<LogEventEntity>>

    @Query("SELECT * FROM log_events WHERE sessionId = :sessionId AND eventType IN (:eventTypes) ORDER BY timestamp ASC")
    fun getEventsOfTypes(sessionId: String, eventTypes: List<String>): Flow<List<LogEventEntity>>

    @Query("DELETE FROM log_events WHERE sessionId = :sessionId")
    suspend fun deleteEventsForSession(sessionId: String)

    // Analytics queries
    @Query("""
        SELECT AVG(matchScore) FROM log_events
        WHERE sessionId = :sessionId AND eventType = 'MATCH_RESULT' AND matchScore IS NOT NULL
    """)
    suspend fun getAverageMatchScore(sessionId: String): Float?

    @Query("""
        SELECT COUNT(*) FROM log_events
        WHERE sessionId = :sessionId AND eventType = 'PROMPT_FIRED'
    """)
    suspend fun getPromptCount(sessionId: String): Int

    @Query("""
        SELECT COUNT(*) FROM log_events
        WHERE sessionId = :sessionId AND eventType = 'LINE_SKIP'
    """)
    suspend fun getSkipCount(sessionId: String): Int

    @Query("""
        SELECT COUNT(*) FROM log_events
        WHERE sessionId = :sessionId AND eventType = 'VOSK_FINAL'
    """)
    suspend fun getRecognitionCount(sessionId: String): Int

    // Summary query: get sessions with computed stats
    @Query("""
        SELECT s.*,
            (SELECT COUNT(*) FROM log_events WHERE sessionId = s.id AND eventType = 'PROMPT_FIRED') as computedPrompts,
            (SELECT AVG(matchScore) FROM log_events WHERE sessionId = s.id AND eventType = 'MATCH_RESULT') as computedAvgScore
        FROM session_logs s
        ORDER BY s.startTime DESC
        LIMIT :limit
    """)
    suspend fun getRecentSessionsWithStats(limit: Int = 50): List<SessionLogEntity>
}
