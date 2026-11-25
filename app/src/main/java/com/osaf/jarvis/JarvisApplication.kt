package com.osaf.jarvis

import android.app.Application
import com.osaf.jarvis.ai.OfflineAiEngine
import com.osaf.jarvis.ai.OpenAiClient
import com.osaf.jarvis.ai.PersonalityEngine
import com.osaf.jarvis.ai.WebSearchTool
import com.osaf.jarvis.commands.CommandInterpreter
import com.osaf.jarvis.database.BackupManager
import com.osaf.jarvis.database.ChatRepository
import com.osaf.jarvis.database.JarvisDatabase
import com.osaf.jarvis.database.MemoryRepository
import com.osaf.jarvis.database.SettingsRepository
import com.osaf.jarvis.device.DeviceController
import com.osaf.jarvis.notifications.NotificationHelper
import com.osaf.jarvis.security.KeystoreCryptoManager
import com.osaf.jarvis.utils.PerformanceMetrics
import com.osaf.jarvis.voice.VoiceEngine
import com.osaf.jarvis.wakeword.WakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(application: Application) {
    private val warmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val cryptoManager = KeystoreCryptoManager()
    val database = JarvisDatabase.getInstance(application)
    val settingsRepository = SettingsRepository(application, cryptoManager)
    val memoryRepository = MemoryRepository(
        noteDao = database.noteDao(),
        reminderDao = database.reminderDao(),
        memoryFactDao = database.memoryFactDao(),
        commandStatDao = database.commandStatDao()
    )
    val chatRepository = ChatRepository(database.chatDao())
    val backupManager = BackupManager(memoryRepository, settingsRepository)
    val deviceController = DeviceController(application)
    val offlineAiEngine = OfflineAiEngine()
    val personalityEngine = PersonalityEngine()
    val webSearchTool = WebSearchTool(sharedHttpClient)
    val openAiClient = OpenAiClient(sharedHttpClient)
    val voiceEngine = VoiceEngine(application, sharedHttpClient)
    val wakeWordEngine = WakeWordEngine()
    val commandInterpreter = CommandInterpreter(
        context = application,
        deviceController = deviceController,
        memoryRepository = memoryRepository,
        settingsRepository = settingsRepository,
        offlineAiEngine = offlineAiEngine,
        personalityEngine = personalityEngine
    )
    val coordinator = JarvisCoordinator(
        deviceController = deviceController,
        settingsRepository = settingsRepository,
        memoryRepository = memoryRepository,
        chatRepository = chatRepository,
        commandInterpreter = commandInterpreter,
        openAiClient = openAiClient,
        offlineAiEngine = offlineAiEngine,
        personalityEngine = personalityEngine,
        webSearchTool = webSearchTool,
        voiceEngine = voiceEngine,
        wakeWordEngine = wakeWordEngine
    )

    init {
        warmupScope.launch {
            deviceController.refreshInstalledApps()
            PerformanceMetrics.refreshMemory()
        }
    }
}

class JarvisApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        container = AppContainer(this)
        PerformanceMetrics.markAppInitialized()
    }

    override fun onTerminate() {
        container.voiceEngine.release()
        container.deviceController.release()
        container.wakeWordEngine.stop()
        super.onTerminate()
    }
}
