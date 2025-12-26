package com.lyricprompter.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing setlists in the database.
 * Song IDs are stored as a JSON array to preserve order.
 */
@Entity(tableName = "setlists")
data class SetlistEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val songIdsJson: String,
    val createdAt: Long,
    val updatedAt: Long
)
