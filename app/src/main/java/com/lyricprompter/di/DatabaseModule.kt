package com.lyricprompter.di

import android.content.Context
import androidx.room.Room
import com.lyricprompter.data.local.db.AppDatabase
import com.lyricprompter.data.local.db.SongDao
import com.lyricprompter.data.local.db.SetlistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "lyricprompter.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideSongDao(database: AppDatabase): SongDao = database.songDao()

    @Provides
    @Singleton
    fun provideSetlistDao(database: AppDatabase): SetlistDao = database.setlistDao()
}
