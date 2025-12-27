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
            // Search for the song
            val query = "$title $artist"
            Log.d(TAG, "Searching BPM for: $query")

            val searchResponse = api.searchSongs(
                apiKey = apiKey,
                query = query
            )

            val results = searchResponse.search
            if (results.isNullOrEmpty()) {
                Log.d(TAG, "No BPM results found for: $query")
                return BpmResult.NotFound
            }

            // Find best matching result
            val bestMatch = results.find { result ->
                result.title.contains(title, ignoreCase = true) &&
                result.artist?.name?.contains(artist, ignoreCase = true) == true
            } ?: results.firstOrNull()

            if (bestMatch == null) {
                return BpmResult.NotFound
            }

            // Get detailed song info with BPM
            Log.d(TAG, "Getting BPM details for song ID: ${bestMatch.id}")
            val detailResponse = api.getSong(
                apiKey = apiKey,
                songId = bestMatch.id
            )

            val song = detailResponse.song
            if (song?.tempo != null && song.tempo > 0) {
                Log.i(TAG, "Found BPM for '$title': ${song.tempo}")
                BpmResult.Success(
                    bpm = song.tempo,
                    key = song.key_of,
                    timeSignature = song.time_sig
                )
            } else {
                Log.d(TAG, "Song found but no BPM available")
                BpmResult.NotFound
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up BPM", e)
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
