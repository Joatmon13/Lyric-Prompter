package com.lyricprompter.data.remote.lyrics

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Genius API for searching songs.
 * Note: Genius doesn't return lyrics directly - would need to scrape from URL.
 * This is primarily used as a fallback for metadata.
 */
interface GeniusApi {

    @GET("search")
    suspend fun search(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): GeniusSearchResponse
}

data class GeniusSearchResponse(
    val response: GeniusResponse
)

data class GeniusResponse(
    val hits: List<GeniusHit>
)

data class GeniusHit(
    val type: String,
    val result: GeniusResult
)

data class GeniusResult(
    val id: Int,
    val title: String,
    val url: String,
    val primary_artist: GeniusArtist
)

data class GeniusArtist(
    val id: Int,
    val name: String
)
