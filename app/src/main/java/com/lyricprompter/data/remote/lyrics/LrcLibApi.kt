package com.lyricprompter.data.remote.lyrics

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * LRCLIB API for fetching lyrics.
 * https://lrclib.net/docs
 */
interface LrcLibApi {

    @GET("api/search")
    suspend fun search(@Query("q") query: String): List<LrcLibSearchResult>

    @GET("api/get")
    suspend fun getLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") track: String
    ): LrcLibLyrics
}

data class LrcLibSearchResult(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Int?
)

data class LrcLibLyrics(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val plainLyrics: String?,
    val syncedLyrics: String?
)
