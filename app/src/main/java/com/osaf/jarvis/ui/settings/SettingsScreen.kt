package com.osaf.jarvis.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BuildConfig
import com.example.R
import com.osaf.jarvis.database.HudThemeMode
import com.osaf.jarvis.database.NeuralTtsProvider
import com.osaf.jarvis.ui.components.HudGlassCard
import com.osaf.jarvis.ui.theme.LocalJarvisHudColors
import com.osaf.jarvis.utils.LanguageMode
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val hud = LocalJarvisHudColors.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val activeVoiceSource by viewModel.activeVoiceSource.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val perf by viewModel.perfSnapshot.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val testResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    // LLM API state
    var endpoint by remember(settings.apiEndpoint) { mutableStateOf(settings.apiEndpoint) }
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKeyText by remember { mutableStateOf(false) }
    var modelName by remember(settings.modelName) { mutableStateOf(settings.modelName) }
    var temperature by remember(settings.temperature) { mutableFloatStateOf(settings.temperature) }
    var maxTokens by remember(settings.maxTokens) { mutableIntStateOf(settings.maxTokens) }
    var timeoutSec by remember(settings.timeoutSeconds) { mutableIntStateOf(settings.timeoutSeconds) }
    var streamingEnabled by remember(settings.streamingEnabled) { mutableStateOf(settings.streamingEnabled) }

    // Neural TTS state
    var selectedTtsProvider by remember(settings.neuralTtsProvider) { mutableStateOf(settings.neuralTtsProvider) }
    var ttsEndpoint by remember(settings.neuralTtsEndpoint) { mutableStateOf(settings.neuralTtsEndpoint) }
    var ttsModel by remember(settings.neuralTtsModel) { mutableStateOf(settings.neuralTtsModel) }
    var ttsVoiceId by remember(settings.neuralTtsVoiceId) { mutableStateOf(settings.neuralTtsVoiceId) }
    var ttsKeyInput by remember { mutableStateOf("") }
    var showTtsKeyText by remember { mutableStateOf(false) }

    // Identity state
    var assistantName by remember(settings.assistantName) { mutableStateOf(settings.assistantName) }
    var userDisplayName by remember(settings.userDisplayName) { mutableStateOf(settings.userDisplayName) }

    val wakePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val hasMic = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.updateVoiceSettings(
                selectedVoiceName = settings.selectedVoiceName,
                speechSpeed = settings.speechSpeed,
                speechPitch = settings.speechPitch,
                voiceVolume = settings.voiceVolume,
                ttsAutoSpeak = settings.ttsAutoSpeak,
                wakeWordEnabled = true,
                wakeWordSensitivity = settings.wakeWordSensitivity,
                continuousConversation = settings.continuousConversation,
                bargeInEnabled = settings.bargeInEnabled
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshVoices()
        viewModel.refreshPerformance()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(hud.spaceBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = hud.textPrimary
                        )
                    }
                }
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = hud.textPrimary
                    )
                    Text(
                        text = "Neural Voice, AI Brain, Privacy & About",
                        style = MaterialTheme.typography.labelMedium,
                        color = hud.textSecondary
                    )
                }
            }
        }

        if (feedback != null) {
            item {
                HudGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = hud.onlineGreen, contentPadding = 10.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(feedback.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = hud.textPrimary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.dismissFeedback() }) { Text("OK", color = hud.primaryGlow) }
                    }
                }
            }
        }

        // 1. HUMAN-LIKE NEURAL VOICE (TTS) & REAL-TIME CONVERSATION
        item {
            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = hud.primaryGlow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("HUMAN VOICE & NEURAL TTS", style = MaterialTheme.typography.labelMedium, color = hud.primaryGlow)
                    }

                    Text(
                        text = "Built-in Android TTS is kept as an offline fallback only. For genuine human-like voice in Urdu and English, select OpenAI Neural Voice or ElevenLabs below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = hud.textSecondary
                    )

                    Text("Active Voice Output: $activeVoiceSource", style = MaterialTheme.typography.labelMedium, color = hud.onlineGreen)

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NeuralTtsProvider.entries.forEach { provider ->
                            FilterChip(
                                selected = selectedTtsProvider == provider,
                                onClick = {
                                    selectedTtsProvider = provider
                                    ttsEndpoint = provider.defaultEndpoint
                                    ttsModel = provider.defaultModel
                                    ttsVoiceId = provider.defaultVoice
                                    if (provider == NeuralTtsProvider.DEVICE_BEST) {
                                        viewModel.saveNeuralTtsConfig(provider, "", "local", "", "")
                                    }
                                },
                                label = { Text(provider.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (selectedTtsProvider != NeuralTtsProvider.DEVICE_BEST) {
                        OutlinedTextField(
                            value = ttsEndpoint,
                            onValueChange = { ttsEndpoint = it },
                            label = { Text("Neural TTS HTTPS URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ttsModel,
                                onValueChange = { ttsModel = it },
                                label = { Text("TTS Model") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = ttsVoiceId,
                                onValueChange = { ttsVoiceId = it },
                                label = { Text("Voice ID (e.g. onyx)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = ttsKeyInput,
                            onValueChange = { ttsKeyInput = it },
                            label = { Text("TTS API Key (${settings.maskedTtsApiKey})") },
                            placeholder = { Text("Leave blank to reuse main OpenAI key") },
                            singleLine = true,
                            visualTransformation = if (showTtsKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showTtsKeyText = !showTtsKeyText }) {
                                    Icon(
                                        imageVector = if (showTtsKeyText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle TTS key visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.saveNeuralTtsConfig(selectedTtsProvider, ttsEndpoint, ttsModel, ttsVoiceId, ttsKeyInput)
                                    ttsKeyInput = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = hud.primaryGlow),
                                modifier = Modifier.weight(1f).testTag("save_neural_tts_button")
                            ) {
                                Text("Save Voice Provider", color = hud.onPrimaryAccent)
                            }
                            if (settings.hasTtsApiKey) {
                                TextButton(onClick = { viewModel.clearTtsApiKey() }) {
                                    Text("Clear TTS Key", color = hud.errorCrimson)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Speak Responses", style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                            Text("Streams sentence-by-sentence for low delay", style = MaterialTheme.typography.labelSmall, color = hud.textSecondary)
                        }
                        Switch(
                            checked = settings.ttsAutoSpeak,
                            onCheckedChange = {
                                viewModel.updateVoiceSettings(
                                    settings.selectedVoiceName, settings.speechSpeed, settings.speechPitch, settings.voiceVolume,
                                    it, settings.wakeWordEnabled, settings.wakeWordSensitivity,
                                    settings.continuousConversation, settings.bargeInEnabled
                                )
                            }
                        )
                    }

                    Text("Voice Volume: ${(settings.voiceVolume * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                    Slider(
                        value = settings.voiceVolume,
                        onValueChange = {
                            viewModel.updateVoiceSettings(
                                settings.selectedVoiceName, settings.speechSpeed, settings.speechPitch, it,
                                settings.ttsAutoSpeak, settings.wakeWordEnabled, settings.wakeWordSensitivity,
                                settings.continuousConversation, settings.bargeInEnabled
                            )
                        },
                        valueRange = 0.1f..1.0f
                    )

                    Text("Speech Speed: ${((settings.speechSpeed * 100).roundToInt() / 100f)}x", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                    Slider(
                        value = settings.speechSpeed,
                        onValueChange = {
                            viewModel.updateVoiceSettings(
                                settings.selectedVoiceName, it, settings.speechPitch, settings.voiceVolume,
                                settings.ttsAutoSpeak, settings.wakeWordEnabled, settings.wakeWordSensitivity,
                                settings.continuousConversation, settings.bargeInEnabled
                            )
                        },
                        valueRange = 0.5f..2.0f
                    )

                    Text("Offline Voice Pitch: ${((settings.speechPitch * 100).roundToInt() / 100f)}x", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                    Slider(
                        value = settings.speechPitch,
                        onValueChange = {
                            viewModel.updateVoiceSettings(
                                settings.selectedVoiceName, settings.speechSpeed, it, settings.voiceVolume,
                                settings.ttsAutoSpeak, settings.wakeWordEnabled, settings.wakeWordSensitivity,
                                settings.continuousConversation, settings.bargeInEnabled
                            )
                        },
                        valueRange = 0.5f..2.0f
                    )

                    if (voices.isNotEmpty()) {
                        Text("Offline Device Fallback Voice", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            voices.take(6).forEach { v ->
                                FilterChip(
                                    selected = settings.selectedVoiceName == v.name,
                                    onClick = {
                                        viewModel.updateVoiceSettings(
                                            v.name, settings.speechSpeed, settings.speechPitch, settings.voiceVolume,
                                            settings.ttsAutoSpeak, settings.wakeWordEnabled, settings.wakeWordSensitivity,
                                            settings.continuousConversation, settings.bargeInEnabled
                                        )
                                    },
                                    label = { Text("${v.localeLabel}${if (v.isOfflineCapable) " (Offline)" else ""}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Conversation", style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                            Text("Auto-listens for follow-up turns after speaking", style = MaterialTheme.typography.labelSmall, color = hud.textSecondary)
                        }
                        Switch(
                            checked = settings.continuousConversation,
                            onCheckedChange = {
                                viewModel.updateVoiceSettings(
                                    settings.selectedVoiceName, settings.speechSpeed, settings.speechPitch, settings.voiceVolume,
                                    settings.ttsAutoSpeak, settings.wakeWordEnabled, settings.wakeWordSensitivity,
                                    it, settings.bargeInEnabled
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Barge-In Interruption", style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                            Text("Interrupt JARVIS mid-sentence by speaking or tapping orb", style = MaterialTheme.typography.labelSmall, color = hud.textSecondary)
                        }
                        Switch(
                            checked = settings.bargeInEnabled,
                            onCheckedChange = {
                                viewModel.updateVoiceSettings(
                                    settings.selectedVoiceName, settings.speechSpeed, settings.speechPitch, settings.voiceVolume,
                                    settings.ttsAutoSpeak, settings.wakeWordEnabled, settings.wakeWordSensitivity,
                                    settings.continuousConversation, it
                                )
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.testVoice(settings) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("test_voice_button")
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = hud.primaryGlow, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Voice Output", color = hud.primaryGlow)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wake-Word Listener (\"Hey ${settings.assistantName}\")", style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                            Text(
                                "Noise-suppressed mic service with Stop notification.",
                                style = MaterialTheme.typography.labelSmall,
                                color = hud.textSecondary
                            )
                        }
                        Switch(
                            checked = settings.wakeWordEnabled,
                            onCheckedChange = { enable ->
                                if (!enable) {
                                    viewModel.updateVoiceSettings(
                                        settings.selectedVoiceName, settings.speechSpeed, settings.speechPitch, settings.voiceVolume,
                                        settings.ttsAutoSpeak, false, settings.wakeWordSensitivity,
                                        settings.continuousConversation, settings.bargeInEnabled
                                    )
                                } else {
                                    val needed = buildList {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                            add(Manifest.permission.RECORD_AUDIO)
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                    if (needed.isEmpty()) {
                                        viewModel.updateVoiceSettings(
                                            settings.selectedVoiceName, settings.speechSpeed, settings.speechPitch, settings.voiceVolume,
                                            settings.ttsAutoSpeak, true, settings.wakeWordSensitivity,
                                            settings.continuousConversation, settings.bargeInEnabled
                                        )
                                    } else {
                                        wakePermLauncher.launch(needed.toTypedArray())
                                    }
                                }
                            }
                        )
                    }

                    Text("Wake-Word Sensitivity: ${(settings.wakeWordSensitivity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                    Slider(
                        value = settings.wakeWordSensitivity,
                        onValueChange = {
                            viewModel.updateVoiceSettings(
                                settings.selectedVoiceName, settings.speechSpeed, settings.speechPitch, settings.voiceVolume,
                                settings.ttsAutoSpeak, settings.wakeWordEnabled, it,
                                settings.continuousConversation, settings.bargeInEnabled
                            )
                        },
                        valueRange = 0.2f..0.95f
                    )
                }
            }
        }

        // 2. ONLINE AI BRAIN CONFIGURATION
        item {
            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = hud.primaryGlow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ONLINE AI BRAIN (OPENAI-COMPATIBLE)", style = MaterialTheme.typography.labelMedium, color = hud.primaryGlow)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Prefer Online AI Mode", style = MaterialTheme.typography.titleMedium, color = hud.textPrimary)
                            Text("Disable to run strictly offline", style = MaterialTheme.typography.labelSmall, color = hud.textSecondary)
                        }
                        Switch(
                            checked = settings.preferOnlineMode,
                            onCheckedChange = { viewModel.setPreferOnline(it) }
                        )
                    }

                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("HTTPS Chat Completions Endpoint") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_endpoint_input")
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key (${settings.maskedApiKey})") },
                        placeholder = { Text("Enter new key to update (Keystore encrypted)") },
                        singleLine = true,
                        visualTransformation = if (showApiKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKeyText = !showApiKeyText }) {
                                Icon(
                                    imageVector = if (showApiKeyText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle key visibility"
                                )
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = hud.onlineGreen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input")
                    )

                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Model Name (e.g. gpt-4o-mini)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Temperature: ${((temperature * 100).roundToInt() / 100f)}", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                    Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0.0f..1.5f)

                    Text("Max Response Tokens: $maxTokens", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                    Slider(value = maxTokens.toFloat(), onValueChange = { maxTokens = it.roundToInt() }, valueRange = 64f..4096f)

                    Text("Connection Timeout: ${timeoutSec}s", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                    Slider(value = timeoutSec.toFloat(), onValueChange = { timeoutSec = it.roundToInt() }, valueRange = 5f..120f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Streaming Responses (SSE)", style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                        Switch(checked = streamingEnabled, onCheckedChange = { streamingEnabled = it })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.saveApiConfig(endpoint, apiKeyInput, modelName, temperature, maxTokens, timeoutSec, streamingEnabled)
                                apiKeyInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = hud.primaryGlow),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_api_button")
                        ) {
                            Text("Save Config", color = hud.onPrimaryAccent)
                        }
                        OutlinedButton(
                            onClick = { viewModel.testApiConnection(endpoint, apiKeyInput, modelName, timeoutSec) },
                            enabled = !isTesting,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_connection_button")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Link", color = hud.textPrimary)
                        }
                    }

                    if (settings.hasApiKey) {
                        TextButton(onClick = { viewModel.clearApiKey() }) {
                            Text("Remove Stored API Key", color = hud.errorCrimson)
                        }
                    }

                    testResult?.let { res ->
                        Text(
                            text = res.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (res.success) hud.onlineGreen else hud.errorCrimson
                        )
                    }
                }
            }
        }

        // 3. PERSONALIZATION & LANGUAGE
        item {
            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = hud.primaryGlow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PERSONALIZATION & THEME", style = MaterialTheme.typography.labelMedium, color = hud.primaryGlow)
                    }

                    OutlinedTextField(
                        value = assistantName,
                        onValueChange = { assistantName = it },
                        label = { Text("Assistant Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = userDisplayName,
                        onValueChange = { userDisplayName = it },
                        label = { Text("Your Name (Default: Osaf)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Preferred Language", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageMode.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.preferredLanguage == lang,
                                onClick = {
                                    viewModel.updatePersonalization(assistantName, userDisplayName, lang, settings.themeMode)
                                },
                                label = { Text(lang.displayName) }
                            )
                        }
                    }

                    Text("App Theme", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HudThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    viewModel.updatePersonalization(assistantName, userDisplayName, settings.preferredLanguage, mode)
                                },
                                label = { Text(mode.displayName) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.updatePersonalization(assistantName, userDisplayName, settings.preferredLanguage, settings.themeMode)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = hud.primaryGlow)
                    ) {
                        Text("Apply Identity", color = hud.onPrimaryAccent)
                    }
                }
            }
        }

        // 4. PRIVACY & PERFORMANCE TELEMETRY
        item {
            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = hud.onlineGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PRIVACY & NOTIFICATIONS", style = MaterialTheme.typography.labelMedium, color = hud.onlineGreen)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Share Saved Memory Facts with Online AI", style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                            Text("Personalizes replies using your preferences", style = MaterialTheme.typography.labelSmall, color = hud.textSecondary)
                        }
                        Switch(
                            checked = settings.shareMemoryWithOnlineAi,
                            onCheckedChange = {
                                viewModel.updatePrivacyAndNotifications(
                                    settings.notificationsEnabled,
                                    settings.shareNotesWithOnlineAi,
                                    it,
                                    settings.saveChatHistory
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Share Local Notes with Online AI", style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                            Text("Disabled by default for privacy", style = MaterialTheme.typography.labelSmall, color = hud.textSecondary)
                        }
                        Switch(
                            checked = settings.shareNotesWithOnlineAi,
                            onCheckedChange = {
                                viewModel.updatePrivacyAndNotifications(
                                    settings.notificationsEnabled,
                                    it,
                                    settings.shareMemoryWithOnlineAi,
                                    settings.saveChatHistory
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Save Conversation History Locally", style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                        Switch(
                            checked = settings.saveChatHistory,
                            onCheckedChange = {
                                viewModel.updatePrivacyAndNotifications(
                                    settings.notificationsEnabled,
                                    settings.shareNotesWithOnlineAi,
                                    settings.shareMemoryWithOnlineAi,
                                    it
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scheduled Reminder Notifications", style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                            Text("Zero spam — only alerts you explicitly schedule", style = MaterialTheme.typography.labelSmall, color = hud.textSecondary)
                        }
                        Switch(
                            checked = settings.notificationsEnabled,
                            onCheckedChange = {
                                viewModel.updatePrivacyAndNotifications(
                                    it,
                                    settings.shareNotesWithOnlineAi,
                                    settings.shareMemoryWithOnlineAi,
                                    settings.saveChatHistory
                                )
                            }
                        )
                    }
                }
            }
        }

        // 5. PERFORMANCE TELEMETRY & ABOUT OSAF JARVIS 2.0 (OFFICIAL LOGO)
        item {
            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = hud.primaryGlow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PERFORMANCE & ABOUT", style = MaterialTheme.typography.labelMedium, color = hud.primaryGlow)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.osaf_jarvis_logo),
                            contentDescription = "OSAF JARVIS Official Brand Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(72.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "OSAF JARVIS ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleMedium,
                                color = hud.textPrimary
                            )
                            Text(
                                text = "Next-Generation Personal AI Assistant",
                                style = MaterialTheme.typography.bodyMedium,
                                color = hud.textSecondary
                            )
                            Text(
                                text = "ABIs: ${diagnostics.supportedAbis.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = hud.primaryGlow
                            )
                        }
                    }

                    Text(
                        text = "Startup: ${perf.startupMs}ms • Wake Handoff: ${perf.lastWakeToReadyMs}ms • AI First Token: ${perf.lastAiFirstTokenMs}ms • TTS First Audio: ${perf.lastTtsFirstAudioMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = hud.textSecondary
                    )
                    Text(
                        text = "App Heap: ${perf.usedRamMb} MB / ${perf.maxRamMb} MB • System RAM: ${diagnostics.availableRamMb} MB free / ${diagnostics.totalRamMb} MB • Cache Hits: ${perf.cacheHits}",
                        style = MaterialTheme.typography.labelSmall,
                        color = hud.textSecondary
                    )
                    Text(
                        text = "Android ${diagnostics.androidVersion} (API ${diagnostics.apiLevel}, Min API 26). API keys encrypted via Android Keystore AES-GCM.",
                        style = MaterialTheme.typography.labelSmall,
                        color = hud.textSecondary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
