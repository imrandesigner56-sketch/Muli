package com.osaf.jarvis.ui.overlay

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.osaf.jarvis.JarvisApplication
import com.osaf.jarvis.database.JarvisSettings
import com.osaf.jarvis.ui.components.GeminiBottomPopup
import com.osaf.jarvis.ui.theme.JarvisTheme

/**
 * Translucent bottom-anchored overlay activity that appears like Google Gemini
 * when "Hey JARVIS" is spoken while the main app is closed or in the background.
 */
class JarvisOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val app = application as JarvisApplication
        val coordinator = app.container.coordinator
        val voiceEngine = app.container.voiceEngine
        val settingsRepo = app.container.settingsRepository

        setContent {
            val settings by settingsRepo.settingsFlow.collectAsStateWithLifecycle(initialValue = JarvisSettings())
            val reactorState by coordinator.reactorState.collectAsStateWithLifecycle()
            val rmsLevel by voiceEngine.rmsLevel.collectAsStateWithLifecycle()
            val partial by voiceEngine.partialTranscript.collectAsStateWithLifecycle()
            val lastTranscript by coordinator.lastTranscript.collectAsStateWithLifecycle()
            val lastResponse by coordinator.lastResponse.collectAsStateWithLifecycle()
            val errorBanner by coordinator.errorBanner.collectAsStateWithLifecycle()
            val pendingConfirmation by coordinator.pendingConfirmation.collectAsStateWithLifecycle()
            val isListening by voiceEngine.isListening.collectAsStateWithLifecycle()
            val isSpeaking by voiceEngine.isSpeaking.collectAsStateWithLifecycle()

            JarvisTheme(themeMode = settings.themeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            coordinator.dismissBottomPopup()
                            finish()
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    GeminiBottomPopup(
                        visible = true,
                        assistantName = settings.assistantName,
                        reactorState = reactorState,
                        rmsLevel = rmsLevel,
                        partialTranscript = partial,
                        lastTranscript = lastTranscript,
                        lastResponse = lastResponse,
                        errorMessage = errorBanner,
                        pendingConfirmation = pendingConfirmation,
                        isListening = isListening,
                        isSpeaking = isSpeaking,
                        onMicClick = { coordinator.onOrbOrMicTapped() },
                        onStopSpeaking = { coordinator.stopSpeaking() },
                        onConfirmAction = {
                            coordinator.confirmPendingAction()
                            finish()
                        },
                        onCancelAction = { coordinator.cancelPendingAction() },
                        onDismiss = {
                            coordinator.dismissBottomPopup()
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
