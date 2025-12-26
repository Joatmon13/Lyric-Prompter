package com.lyricprompter.data.remote.lyrics

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that orchestrates lyrics search across multiple sources.
 */
@Singleton
class LyricsSearchService @Inject constructor(
    private val lrcLibApi: LrcLibApi
) {
    companion object {
        private const val TAG = "LyricsSearchService"
    }

    /**
     * Search for lyrics across available sources.
     */
    suspend fun search(query: String): Result<List<LyricsSearchResult>> {
        return try {
            // Try LRCLIB first (primary source)
            val results = lrcLibApi.search(query)

            val mappedResults = results.map { result ->
                LyricsSearchResult(
                    source = LyricsSource.LRCLIB,
                    id = result.id.toString(),
                    title = result.trackName,
                    artist = result.artistName,
                    album = result.albumName,
                    duration = result.duration
                )
            }

            Log.i(TAG, "Found ${mappedResults.size} results for: $query")
            Result.success(mappedResults)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for: $query", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch full lyrics for a search result.
     */
    suspend fun fetchLyrics(result: LyricsSearchResult): Result<String> {
        return try {
            when (result.source) {
                LyricsSource.LRCLIB -> {
                    val lyrics = lrcLibApi.getLyrics(
                        artist = result.artist,
                        track = result.title
                    )

                    // Prefer plain lyrics over synced (we don't use timing info)
                    val lyricsText = lyrics.plainLyrics ?: lyrics.syncedLyrics

                    if (lyricsText.isNullOrBlank()) {
                        Result.failure(Exception("No lyrics found"))
                    } else {
                        // Clean synced lyrics if needed (remove timestamps)
                        val cleanedLyrics = if (lyricsText.contains("[")) {
                            cleanSyncedLyrics(lyricsText)
                        } else {
                            lyricsText
                        }
                        Result.success(cleanedLyrics)
                    }
                }
                LyricsSource.GENIUS -> {
                    // Genius doesn't provide lyrics directly - would need scraping
                    Result.failure(Exception("Genius lyrics not implemented"))
                }
                LyricsSource.MANUAL -> {
                    Result.failure(Exception("Manual entry - no fetch needed"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch lyrics for: ${result.title}", e)
            Result.failure(e)
        }
    }

    /**
     * Clean synced/LRC format lyrics by removing timestamps.
     */
    private fun cleanSyncedLyrics(syncedLyrics: String): String {
        // LRC format: [00:12.34]Lyrics text here
        return syncedLyrics
            .lines()
            .map { line ->
                // Remove timestamp patterns like [00:12.34] or [00:12]
                line.replace(Regex("\\[\\d{2}:\\d{2}(\\.\\d{2})?]"), "").trim()
            }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}

data class LyricsSearchResult(
    val source: LyricsSource,
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Int? = null,
    val previewText: String? = null
)

enum class LyricsSource {
    LRCLIB,
    GENIUS,
    MANUAL
}
