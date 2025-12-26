package com.lyricprompter.domain.model

/**
 * Domain model representing a setlist (ordered collection of songs for a gig).
 */
data class Setlist(
    val id: String,
    val name: String,
    val songIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns the number of songs in the setlist.
     */
    val songCount: Int
        get() = songIds.size
}
