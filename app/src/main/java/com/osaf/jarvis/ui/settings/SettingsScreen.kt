package com.osaf.jarvis.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.osaf.jarvis.database.HudThemeMode
import com.osaf.jarvis.ui.components.HudGlassCard
import com.osaf.jarvis.ui.theme.LocalJarvisHudColors
import com.osaf.jarvis.utils.LanguageMode
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val hud = LocalJarvisHudColors.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val testResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    // Local editable form state synced when settings load
    var endpoint by remember(settings.apiEndpoint) { mutableStateOf(settings.apiEndpoint) }
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKeyText by remember { mutableStateOf(false) }
    var modelName by remember(settings.modelName) { mutableStateOf(settings.modelName) }
    var temperature by remember(settings.temperature) { mutableFloatStateOf(settings.temperature) }
    var maxTokens by remember(settings.maxTokens) { mutableIntStateOf(settings.maxTokens) }
    var timeoutSec by remember(settings.timeoutSeconds) { mutableIntStateOf(settings.timeoutSeconds) }
    var streamingEnabled by remember(settings.streamingEnabled) { mutableStateOf(settings.streamingEnabled) }

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
                ttsAutoSpeak = settings.ttsAutoSpeak,
                wakeWordEnabled = true,
                wakeWordSensitivity = settings.wakeWordSensitivity
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshVoices()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(hud.spaceBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = hud.textPrimary
                )
                Text(
                    text = "Voice, Theme, AI Brain & Privacy",
                    style = MaterialTheme.typography.labelMedium,
                    color = hud.textSecondary
                )
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

        // 1. ONLINE AI BRAIN CONFIGURATION
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
                        label = { Text("Model Name") },
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

        // 2. VOICE ENGINE & WAKE WORD
        item {
            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = hud.primaryGlow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VOICE ENGINE & WAKE WORD", style = MaterialTheme.typography.labelMedium, color = hud.primaryGlow)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Speak Responses (TTS)", style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                            Text("Supports English & installed Urdu TTS", style = MaterialTheme.typography.labelSmall, color = hud.textSecondary)
                        }
                        Switch(
                            checked = settings.ttsAutoSpeak,
                            onCheckedChange = {
                                viewModel.updateVoiceSettings(
                                    settings.selectedVoiceName, settings.speechSpeed, settings.speechPitch,
                                    it, settings.wakeWordEnabled, settings.wakeWordSensitivity
                                )
                            }
                        )
                    }

                    Text("Speech Speed: ${((settings.speechSpeed * 100).roundToInt() / 100f)}x", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                    Slider(
                        value = settings.speechSpeed,
                        onValueChange = {
                            viewModel.updateVoiceSettings(
                                settings.selectedVoiceName, it, settings.speechPitch,
                                settings.ttsAutoSpeak, settings.wakeWordEnabled, settings.wakeWordSensitivity
                            )
                        },
                        valueRange = 0.5f..2.0f
                    )

                    Text("Speech Pitch: ${((settings.speechPitch * 100).roundToInt() / 100f)}x", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                    Slider(
                        value = settings.speechPitch,
                        onValueChange = {
                            viewModel.updateVoiceSettings(
                                settings.selectedVoiceName, settings.speechSpeed, it,
                                settings.ttsAutoSpeak, settings.wakeWordEnabled, settings.wakeWordSensitivity
                            )
                        },
                        valueRange = 0.5f..2.0f
                    )

                    if (voices.isNotEmpty()) {
                        Text("TTS Voice Selection", style = MaterialTheme.typography.labelMedium, color = hud.textSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            voices.take(6).forEach { v ->
                                FilterChip(
                                    selected = settings.selectedVoiceName == v.name,
                                    onClick = {
                                        viewModel.updateVoiceSettings(
                                            v.name, settings.speechSpeed, settings.speechPitch,
                                            settings.ttsAutoSpeak, settings.wakeWordEnabled, settings.wakeWordSensitivity
                                        )
                                    },
                                    label = { Text("${v.localeLabel}${if (v.isOfflineCapable) " (Offline)" else ""}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.testVoice(settings.speechSpeed, settings.speechPitch, settings.selectedVoiceName, settings.userDisplayName)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
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
                                "Runs a visible foreground microphone service with a Stop notification. Uses extra battery; Push-to-Talk is always available.",
                                style = MaterialTheme.typography.labelSmall,
                                color = hud.textSecondary
                            )
                        }
                        Switch(
                            checked = settings.wakeWordEnabled,
                            onCheckedChange = { enable ->
                                if (!enable) {
                                    viewModel.updateVoiceSettings(
                                        settings.selectedVoiceName, settings.speechSpeed, settings.speechPitch,
                                        settings.ttsAutoSpeak, false, settings.wakeWordSensitivity
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
                                            settings.selectedVoiceName, settings.speechSpeed, settings.speechPitch,
                                            settings.ttsAutoSpeak, true, settings.wakeWordSensitivity
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
                                settings.selectedVoiceName, settings.speechSpeed, settings.speechPitch,
                                settings.ttsAutoSpeak, settings.wakeWordEnabled, it
                            )
                        },
                        valueRange = 0.2f..0.95f
                    )
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
                        label = { Text("User Address / Display Name") },
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

        // 4. PRIVACY, NOTIFICATIONS & DIAGNOSTICS
        item {
            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = hud.onlineGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PRIVACY & SYSTEM DIAGNOSTICS", style = MaterialTheme.typography.labelMedium, color = hud.onlineGreen)
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
                                viewModel.updatePrivacyAndNotifications(settings.notificationsEnabled, it, settings.saveChatHistory)
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
                                viewModel.updatePrivacyAndNotifications(settings.notificationsEnabled, settings.shareNotesWithOnlineAi, it)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reminder Notifications", style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                        Switch(
                            checked = settings.notificationsEnabled,
                            onCheckedChange = {
                                viewModel.updatePrivacyAndNotifications(it, settings.shareNotesWithOnlineAi, settings.saveChatHistory)
                            }
                        )
                    }

                    Text(
                        text = "ABI Telemetry: ${diagnostics.supportedAbis.joinToString(", ")} • RAM: ${diagnostics.totalRamMb} MB • Android ${diagnostics.androidVersion} (Min API 26)",
                        style = MaterialTheme.typography.labelSmall,
                        color = hud.primaryGlow
                    )
                    Text(
                        text = "Zero Play Services required for offline core. API keys encrypted via Android Keystore AES-GCM.",
                        style = MaterialTheme.typography.labelSmall,
                        color = hud.textSecondary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}
