package com.lyricprompter.data.local.db

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lyricprompter.data.local.db.entities.SetlistEntity
import com.lyricprompter.data.local.db.entities.SongEntity
import com.lyricprompter.domain.model.LyricLine
import com.lyricprompter.domain.model.Setlist
import com.lyricprompter.domain.model.Song

/**
 * Extension functions to map between Room entities and domain models.
 */

private val gson = Gson()

// Song mappings

fun SongEntity.toDomain(): Song {
    val linesType = object : TypeToken<List<LyricLine>>() {}.type
    val vocabularyType = object : TypeToken<Set<String>>() {}.type
    val stringListType = object : TypeToken<List<String>>() {}.type

    return Song(
        id = id,
        title = title,
        artist = artist,
        bpm = bpm,
        originalKey = originalKey,
        performKey = performKey,
        timeSignature = timeSignature,
        countInEnabled = countInEnabled,
        countInBeats = countInBeats,
        triggerPercent = triggerPercent,
        promptWordCount = promptWordCount,
        lines = gson.fromJson(lyricsJson, linesType) ?: emptyList(),
        vocabulary = gson.fromJson(vocabularyJson, vocabularyType) ?: emptySet(),
        setlistIds = gson.fromJson(setlistIdsJson, stringListType) ?: emptyList(),
        tags = gson.fromJson(tagsJson, stringListType) ?: emptyList(),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Song.toEntity(): SongEntity {
    return SongEntity(
        id = id,
        title = title,
        artist = artist,
        bpm = bpm,
        originalKey = originalKey,
        performKey = performKey,
        timeSignature = timeSignature,
        countInEnabled = countInEnabled,
        countInBeats = countInBeats,
        triggerPercent = triggerPercent,
        promptWordCount = promptWordCount,
        lyricsJson = gson.toJson(lines),
        vocabularyJson = gson.toJson(vocabulary),
        setlistIdsJson = gson.toJson(setlistIds),
        tagsJson = gson.toJson(tags),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// Setlist mappings

fun SetlistEntity.toDomain(): Setlist {
    val stringListType = object : TypeToken<List<String>>() {}.type

    return Setlist(
        id = id,
        name = name,
        songIds = gson.fromJson(songIdsJson, stringListType) ?: emptyList(),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Setlist.toEntity(): SetlistEntity {
    return SetlistEntity(
        id = id,
        name = name,
        songIdsJson = gson.toJson(songIds),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
