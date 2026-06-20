package com.lyricprompter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyricprompter.data.repository.SettingsRepository
import com.lyricprompter.domain.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT)

    fun setDefaultTriggerPercent(value: Int) =
        viewModelScope.launch { repository.setDefaultTriggerPercent(value) }

    fun setDefaultPromptWords(value: Int) =
        viewModelScope.launch { repository.setDefaultPromptWords(value) }

    fun setDefaultCountInEnabled(value: Boolean) =
        viewModelScope.launch { repository.setDefaultCountInEnabled(value) }

    fun setDefaultCooldownBeats(value: Int) =
        viewModelScope.launch { repository.setDefaultCooldownBeats(value) }

    fun setTtsSpeed(value: Float) =
        viewModelScope.launch { repository.setTtsSpeed(value) }

    fun setKeepScreenOn(value: Boolean) =
        viewModelScope.launch { repository.setKeepScreenOn(value) }
}
