package com.lyricprompter.data.remote.bpm

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * GetSongBPM API for fetching song tempo and key information.
 * https://getsongbpm.com/api
 *
 * Requires API key registration with backlink attribution.
 */
interface GetSongBpmApi {

    /**
     * Search for songs by title and optionally artist.
     */
    @GET("search/")
    suspend fun searchSongs(
        @Query("api_key") apiKey: String,
        @Query("type") type: String = "song",
        @Query("lookup") query: String
    ): SongSearchResponse

    /**
     * Get detailed song information including BPM.
     */
    @GET("song/")
    suspend fun getSong(
        @Query("api_key") apiKey: String,
        @Query("id") songId: String
    ): SongDetailResponse
}

/**
 * Response wrapper for song search.
 */
data class SongSearchResponse(
    val search: List<SongSearchResult>?
)

/**
 * Individual song search result.
 */
data class SongSearchResult(
    val id: String,
    val title: String,
    val artist: ArtistInfo?
)

/**
 * Response wrapper for song detail.
 */
data class SongDetailResponse(
    val song: SongDetail?
)

/**
 * Detailed song information including tempo.
 */
data class SongDetail(
    val id: String,
    val title: String,
    val tempo: Int?,
    val time_sig: String?,
    val key_of: String?,
    val open_key: String?,
    val artist: ArtistInfo?
)

/**
 * Artist information.
 */
data class ArtistInfo(
    val id: String?,
    val name: String?,
    val genres: List<String>?
)
