package com.lyricprompter.di

import android.content.Context
import com.lyricprompter.audio.routing.AudioRouter
import com.lyricprompter.audio.tts.CountInPlayer
import com.lyricprompter.audio.tts.PromptSpeaker
import com.lyricprompter.audio.vosk.VoskEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides
    @Singleton
    fun provideVoskEngine(@ApplicationContext context: Context): VoskEngine {
        return VoskEngine(context)
    }

    @Provides
    @Singleton
    fun provideAudioRouter(@ApplicationContext context: Context): AudioRouter {
        return AudioRouter(context)
    }

    @Provides
    @Singleton
    fun providePromptSpeaker(
        @ApplicationContext context: Context
    ): PromptSpeaker {
        return PromptSpeaker(context)
    }

    @Provides
    @Singleton
    fun provideCountInPlayer(
        promptSpeaker: PromptSpeaker,
        audioRouter: AudioRouter
    ): CountInPlayer {
        return CountInPlayer(promptSpeaker, audioRouter)
    }
}
