package com.osaf.jarvis.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osaf.jarvis.JarvisApplication
import com.osaf.jarvis.ReactorState
import com.osaf.jarvis.database.ChatMessageEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as JarvisApplication).container
    private val chatRepo = container.chatRepository
    private val coordinator = container.coordinator
    private val voiceEngine = container.voiceEngine

    val messages: StateFlow<List<ChatMessageEntity>> = chatRepo.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streamingResponse: StateFlow<String> = coordinator.streamingResponse
    val reactorState: StateFlow<ReactorState> = coordinator.reactorState
    val isListening: StateFlow<Boolean> = voiceEngine.isListening
    val isOnlineActive: StateFlow<Boolean> = coordinator.isOnlineActive

    fun sendMessage(text: String) {
        coordinator.submitUserInput(text, fromVoice = false)
    }

    fun toggleVoiceInput() {
        if (isListening.value) coordinator.cancelVoiceCommand() else coordinator.startVoiceCommand()
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch { chatRepo.deleteMessage(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { chatRepo.clearHistory() }
    }
}
