package com.osaf.jarvis

import android.app.Application
import com.osaf.jarvis.ai.OfflineAiEngine
import com.osaf.jarvis.ai.OpenAiClient
import com.osaf.jarvis.commands.CommandInterpreter
import com.osaf.jarvis.database.BackupManager
import com.osaf.jarvis.database.ChatRepository
import com.osaf.jarvis.database.JarvisDatabase
import com.osaf.jarvis.database.MemoryRepository
import com.osaf.jarvis.database.SettingsRepository
import com.osaf.jarvis.device.DeviceController
import com.osaf.jarvis.notifications.NotificationHelper
import com.osaf.jarvis.security.KeystoreCryptoManager
import com.osaf.jarvis.voice.VoiceEngine
import com.osaf.jarvis.wakeword.WakeWordEngine

class AppContainer(application: Application) {
    val cryptoManager = KeystoreCryptoManager()
    val database = JarvisDatabase.getInstance(application)
    val settingsRepository = SettingsRepository(application, cryptoManager)
    val memoryRepository = MemoryRepository(database.noteDao(), database.reminderDao())
    val chatRepository = ChatRepository(database.chatDao())
    val backupManager = BackupManager(memoryRepository, settingsRepository)
    val deviceController = DeviceController(application)
    val offlineAiEngine = OfflineAiEngine()
    val openAiClient = OpenAiClient()
    val voiceEngine = VoiceEngine(application)
    val wakeWordEngine = WakeWordEngine()
    val commandInterpreter = CommandInterpreter(
        context = application,
        deviceController = deviceController,
        memoryRepository = memoryRepository,
        settingsRepository = settingsRepository,
        offlineAiEngine = offlineAiEngine
    )
    val coordinator = JarvisCoordinator(
        deviceController = deviceController,
        settingsRepository = settingsRepository,
        memoryRepository = memoryRepository,
        chatRepository = chatRepository,
        commandInterpreter = commandInterpreter,
        openAiClient = openAiClient,
        offlineAiEngine = offlineAiEngine,
        voiceEngine = voiceEngine,
        wakeWordEngine = wakeWordEngine
    )
}

class JarvisApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        container = AppContainer(this)
    }

    override fun onTerminate() {
        container.voiceEngine.release()
        container.deviceController.release()
        container.wakeWordEngine.stop()
        super.onTerminate()
    }
}
