package com.osaf.jarvis.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osaf.jarvis.JarvisApplication
import com.osaf.jarvis.ReactorState
import com.osaf.jarvis.database.ChatMessageEntity
import com.osaf.jarvis.utils.AttachmentHelper
import com.osaf.jarvis.utils.PreparedAttachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isSpeaking: StateFlow<Boolean> = voiceEngine.isSpeaking
    val activeVoiceSource: StateFlow<String> = voiceEngine.activeVoiceSource
    val isOnlineActive: StateFlow<Boolean> = coordinator.isOnlineActive

    private val _pendingAttachment = MutableStateFlow<PreparedAttachment?>(null)
    val pendingAttachment: StateFlow<PreparedAttachment?> = _pendingAttachment.asStateFlow()

    private val _attachmentStatus = MutableStateFlow<String?>(null)
    val attachmentStatus: StateFlow<String?> = _attachmentStatus.asStateFlow()

    fun sendMessage(text: String) {
        val attachment = _pendingAttachment.value
        _pendingAttachment.value = null
        coordinator.submitUserInput(text, fromVoice = false, attachment = attachment)
    }

    fun prepareImage(uri: Uri) {
        viewModelScope.launch {
            _attachmentStatus.value = "Preparing image..."
            val prepared = AttachmentHelper.prepareImageFromUri(getApplication(), uri)
            if (prepared != null) {
                _pendingAttachment.value = prepared
                _attachmentStatus.value = null
            } else {
                _attachmentStatus.value = "Could not load selected image."
            }
        }
    }

    fun prepareDocument(uri: Uri) {
        viewModelScope.launch {
            _attachmentStatus.value = "Reading document..."
            val prepared = AttachmentHelper.prepareDocumentFromUri(getApplication(), uri)
            if (prepared != null) {
                _pendingAttachment.value = prepared
                _attachmentStatus.value = null
            } else {
                _attachmentStatus.value = "Could not extract text from selected document."
            }
        }
    }

    fun clearPendingAttachment() {
        _pendingAttachment.value = null
    }

    fun dismissAttachmentStatus() {
        _attachmentStatus.value = null
    }

    fun toggleVoiceInput() {
        coordinator.onOrbOrMicTapped()
    }

    fun stopSpeaking() {
        coordinator.stopSpeaking()
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch { chatRepo.deleteMessage(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { chatRepo.clearHistory() }
    }
}
