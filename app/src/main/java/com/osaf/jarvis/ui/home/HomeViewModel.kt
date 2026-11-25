package com.osaf.jarvis.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osaf.jarvis.JarvisApplication
import com.osaf.jarvis.ReactorState
import com.osaf.jarvis.commands.PendingConfirmation
import com.osaf.jarvis.database.CommandStatEntity
import com.osaf.jarvis.database.JarvisSettings
import com.osaf.jarvis.device.BatteryStatus
import com.osaf.jarvis.services.JarvisVoiceService
import com.osaf.jarvis.utils.PerformanceMetrics
import com.osaf.jarvis.utils.PreparedAttachment
import com.osaf.jarvis.utils.RuntimePerformanceSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as JarvisApplication).container
    private val coordinator = container.coordinator
    private val deviceController = container.deviceController
    private val settingsRepo = container.settingsRepository
    private val memoryRepo = container.memoryRepository
    private val voiceEngine = container.voiceEngine

    val settings: StateFlow<JarvisSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JarvisSettings())

    val reactorState: StateFlow<ReactorState> = coordinator.reactorState
    val isOnlineActive: StateFlow<Boolean> = coordinator.isOnlineActive
    val rmsLevel: StateFlow<Float> = voiceEngine.rmsLevel
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript
    val lastTranscript: StateFlow<String> = coordinator.lastTranscript
    val lastResponse: StateFlow<String> = coordinator.lastResponse
    val errorBanner: StateFlow<String?> = coordinator.errorBanner
    val pendingConfirmation: StateFlow<PendingConfirmation?> = coordinator.pendingConfirmation
    val isListening: StateFlow<Boolean> = voiceEngine.isListening
    val isSpeaking: StateFlow<Boolean> = voiceEngine.isSpeaking
    val activeVoiceSource: StateFlow<String> = voiceEngine.activeVoiceSource
    val perfSnapshot: StateFlow<RuntimePerformanceSnapshot> = PerformanceMetrics.snapshot

    val topCommands: StateFlow<List<CommandStatEntity>> = memoryRepo.topCommands
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _batteryStatus = MutableStateFlow(deviceController.getBatteryStatus())
    val batteryStatus: StateFlow<BatteryStatus> = _batteryStatus.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _batteryStatus.value = deviceController.getBatteryStatus()
                PerformanceMetrics.refreshMemory()
                delay(20_000L)
            }
        }
    }

    fun onOrbOrMicTapped() {
        coordinator.onOrbOrMicTapped()
    }

    fun onStopSpeaking() {
        coordinator.stopSpeaking()
    }

    fun submitCommand(text: String, attachment: PreparedAttachment? = null) {
        coordinator.submitUserInput(text, fromVoice = false, attachment = attachment)
    }

    fun toggleOnlineOffline() {
        viewModelScope.launch {
            settingsRepo.setPreferOnlineMode(!settings.value.preferOnlineMode)
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setWakeWordEnabled(enabled)
            val app = getApplication<Application>()
            if (enabled) {
                JarvisVoiceService.start(app)
            } else {
                JarvisVoiceService.stop(app)
            }
        }
    }

    fun confirmPendingAction() = coordinator.confirmPendingAction()
    fun cancelPendingAction() = coordinator.cancelPendingAction()
    fun dismissError() = coordinator.clearError()
}
