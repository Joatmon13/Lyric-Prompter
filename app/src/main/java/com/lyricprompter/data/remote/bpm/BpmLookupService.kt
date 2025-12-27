package com.lyricprompter.data.remote.bpm

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for looking up song BPM from GetSongBPM API.
 */
@Singleton
class BpmLookupService @Inject constructor(
    private val api: GetSongBpmApi
) {
    companion object {
        private const val TAG = "BpmLookupService"
    }

    /**
     * Look up BPM for a song by title and artist.
     *
     * @param title Song title
     * @param artist Artist name
     * @param apiKey GetSongBPM API key
     * @return BPM value if found, null otherwise
     */
    suspend fun lookupBpm(title: String, artist: String, apiKey: String): BpmResult {
        if (apiKey.isBlank()) {
            Log.w(TAG, "No API key configured for GetSongBPM")
            return BpmResult.NoApiKey
        }

        return try {
            // Use "both" type search with song: and artist: prefixes for better matching
            val query = "song:$title artist:$artist"
            Log.i(TAG, "Searching BPM for: $query")

            val searchResponse = api.searchSongs(
                apiKey = apiKey,
                type = "both",
                query = query
            )

            Log.i(TAG, "Search response: ${searchResponse.search?.size ?: 0} results")

            val results = searchResponse.search
            if (results.isNullOrEmpty()) {
                Log.w(TAG, "No BPM results found for: $query")
                return BpmResult.NotFound
            }

            // Find best matching result (search results already contain tempo)
            // Note: API can return null for title/artist fields
            val bestMatch = results.find { result ->
                result.title?.contains(title, ignoreCase = true) == true &&
                result.artist?.name?.contains(artist, ignoreCase = true) == true
            } ?: results.firstOrNull()

            if (bestMatch == null) {
                Log.w(TAG, "No matching result found")
                return BpmResult.NotFound
            }

            Log.i(TAG, "Best match: '${bestMatch.title}' by ${bestMatch.artist?.name}, tempo=${bestMatch.tempo}, time_sig=${bestMatch.time_sig}, key=${bestMatch.key_of}")

            // Parse tempo string to int
            val tempoInt = bestMatch.tempo?.toIntOrNull()
            if (tempoInt != null && tempoInt > 0) {
                Log.i(TAG, "Found BPM for '$title': $tempoInt")
                BpmResult.Success(
                    bpm = tempoInt,
                    key = bestMatch.key_of,
                    timeSignature = bestMatch.time_sig
                )
            } else {
                Log.w(TAG, "Song found but no valid BPM: tempo='${bestMatch.tempo}'")
                BpmResult.NotFound
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up BPM: ${e.message}", e)
            BpmResult.Error(e.message ?: "Unknown error")
        }
    }
}

/**
 * Result of a BPM lookup.
 */
sealed interface BpmResult {
    data class Success(
        val bpm: Int,
        val key: String? = null,
        val timeSignature: String? = null
    ) : BpmResult

    data object NotFound : BpmResult
    data object NoApiKey : BpmResult
    data class Error(val message: String) : BpmResult
}
