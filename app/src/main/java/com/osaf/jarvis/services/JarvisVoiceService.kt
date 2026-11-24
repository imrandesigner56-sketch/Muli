package com.osaf.jarvis.services

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.osaf.jarvis.JarvisApplication
import com.osaf.jarvis.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class JarvisVoiceService : Service() {

    companion object {
        const val ACTION_START = "com.osaf.jarvis.action.START_WAKE"
        const val ACTION_STOP = "com.osaf.jarvis.action.STOP_WAKE"

        fun start(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            handleStopRequest()
            return START_NOT_STICKY
        }

        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            stopSelf()
            return START_NOT_STICKY
        }

        val stopIntent = PendingIntent.getService(
            this,
            99,
            Intent(this, JarvisVoiceService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val app = application as JarvisApplication
        val notification = NotificationHelper.buildVoiceForegroundNotification(
            context = this,
            wakePhrase = "Hey JARVIS",
            stopIntent = stopIntent
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.FGS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NotificationHelper.FGS_NOTIFICATION_ID, notification)
        }

        startWakeLoop(app)
        return START_STICKY
    }

    private fun startWakeLoop(app: JarvisApplication) {
        val coordinator = app.container.coordinator
        val wakeEngine = app.container.wakeWordEngine
        val settingsRepo = app.container.settingsRepository

        var latestSensitivity = 0.65f
        var latestAssistantName = "JARVIS"
        serviceScope.launch {
            settingsRepo.settingsFlow.collect { s ->
                latestSensitivity = s.wakeWordSensitivity
                latestAssistantName = s.assistantName
                if (!s.wakeWordEnabled) {
                    wakeEngine.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        wakeEngine.startEnergyMonitoring(
            scope = serviceScope,
            getSensitivity = { latestSensitivity },
            onSpeechTrigger = {
                serviceScope.launch {
                    val currentSettings = settingsRepo.settingsFlow.first()
                    if (!currentSettings.wakeWordEnabled) return@launch
                    wakeEngine.setPaused(true)
                    coordinator.triggerWakePhraseVerification(
                        assistantName = latestAssistantName,
                        onFinished = { wakeEngine.setPaused(false) }
                    )
                }
            },
            onError = { msg ->
                coordinator.reportWakeError(msg)
                handleStopRequest()
            }
        )
    }

    private fun handleStopRequest() {
        val app = application as JarvisApplication
        app.container.wakeWordEngine.stop()
        serviceScope.launch {
            app.container.settingsRepository.setWakeWordEnabled(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        val app = application as JarvisApplication
        app.container.wakeWordEngine.stop()
        serviceScope.cancel()
        super.onDestroy()
    }
}
