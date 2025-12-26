package com.lyricprompter

import android.app.Application
import com.lyricprompter.audio.vosk.VoskEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LyricPrompterApp : Application() {

    @Inject
    lateinit var voskEngine: VoskEngine

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Initialize Vosk in background to speed up first performance
        applicationScope.launch {
            voskEngine.initialize()
        }
    }
}
