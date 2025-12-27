package com.lyricprompter.data.remote.bpm

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import retrofit2.http.GET
import retrofit2.http.Query
import java.lang.reflect.Type

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
 * Note: The API returns 'search' as either an array OR a single object depending on results.
 */
data class SongSearchResponse(
    @JsonAdapter(SearchResultDeserializer::class)
    val search: List<SongSearchResult>?
)

/**
 * Custom deserializer to handle 'search' being either an array, single object, or error object.
 */
class SearchResultDeserializer : JsonDeserializer<List<SongSearchResult>?> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext
    ): List<SongSearchResult>? {
        if (json == null || json.isJsonNull) return null

        return when {
            json.isJsonArray -> {
                json.asJsonArray.map { element ->
                    context.deserialize<SongSearchResult>(element, SongSearchResult::class.java)
                }
            }
            json.isJsonObject -> {
                val obj = json.asJsonObject
                // Check if this is an error response: {"error":"no result"}
                if (obj.has("error")) {
                    return null // No results found
                }
                // Single result returned as object instead of array
                listOf(context.deserialize(json, SongSearchResult::class.java))
            }
            else -> null
        }
    }
}

/**
 * Individual song search result.
 * Note: API returns tempo as string, not int. Fields can be null.
 */
data class SongSearchResult(
    val id: String?,
    val title: String?,
    val tempo: String?,
    val time_sig: String?,
    val key_of: String?,
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
 * Note: API returns tempo as string, not int.
 */
data class SongDetail(
    val id: String,
    val title: String,
    val tempo: String?,
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
