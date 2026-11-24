package com.osaf.jarvis.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.osaf.jarvis.ReactorState
import com.osaf.jarvis.commands.PendingConfirmation
import com.osaf.jarvis.ui.components.ArcReactorView
import com.osaf.jarvis.ui.components.HudGlassCard
import com.osaf.jarvis.ui.theme.LocalJarvisHudColors

@Composable
fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val hud = LocalJarvisHudColors.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val reactorState by viewModel.reactorState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnlineActive.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val partial by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val lastTranscript by viewModel.lastTranscript.collectAsStateWithLifecycle()
    val lastResponse by viewModel.lastResponse.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val dateTime by viewModel.dateTime.collectAsStateWithLifecycle()
    val battery by viewModel.batteryStatus.collectAsStateWithLifecycle()

    var commandInput by rememberSaveable { mutableStateOf("") }
    var pendingWakeEnable by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            if (pendingWakeEnable) {
                viewModel.setWakeWordEnabled(true)
            } else {
                viewModel.onMicPressed()
            }
        }
        pendingWakeEnable = false
    }

    fun requestMicAndListen() {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.onMicPressed()
        } else {
            pendingWakeEnable = false
            micPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    fun toggleWakeWord() {
        if (settings.wakeWordEnabled) {
            viewModel.setWakeWordEnabled(false)
            return
        }
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
            viewModel.setWakeWordEnabled(true)
        } else {
            pendingWakeEnable = true
            micPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF071B30), hud.spaceBlack),
                    radius = 1200f
                )
            )
            .imePadding()
    ) {
        val isCompactHeight = maxHeight < 660.dp
        val reactorSize = when {
            maxWidth > 600.dp -> 260.dp
            isCompactHeight -> 175.dp
            else -> 225.dp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP HUD HEADER
            HudGlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 12.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(hud.primaryGlow.copy(alpha = 0.16f))
                                    .border(1.5.dp, hud.primaryGlow, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "OSAF JARVIS Logo",
                                    tint = hud.primaryGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "OSAF ${settings.assistantName.uppercase()}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = hud.primaryGlow
                                )
                                Text(
                                    text = "Hello ${settings.userDisplayName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = hud.textPrimary
                                )
                            }
                        }

                        // Online/Offline clickable telemetry pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isOnline) hud.onlineGreen.copy(alpha = 0.14f) else hud.offlineSteel.copy(alpha = 0.22f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isOnline) hud.onlineGreen else hud.offlineSteel
                            ),
                            modifier = Modifier
                                .testTag("online_status_pill")
                                .minimumInteractiveComponentSize()
                                .clickable { viewModel.toggleOnlineOffline() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                                    contentDescription = if (isOnline) "Online mode" else "Offline mode",
                                    tint = if (isOnline) hud.onlineGreen else hud.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isOnline) "ONLINE" else "OFFLINE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOnline) hud.onlineGreen else hud.textSecondary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${dateTime.first}  •  ${dateTime.second}",
                            style = MaterialTheme.typography.labelMedium,
                            color = hud.textSecondary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                                contentDescription = "Battery percentage",
                                tint = if (battery.percentage in 0..20) hud.warningAmber else hud.primaryGlow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (battery.percentage >= 0) "${battery.percentage}%" else "N/A",
                                style = MaterialTheme.typography.labelMedium,
                                color = hud.textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 18.dp))

            // CENTER ANIMATED ARC REACTOR
            ArcReactorView(
                state = reactorState,
                rmsLevel = rmsLevel,
                assistantName = settings.assistantName,
                size = reactorSize,
                onClick = { requestMicAndListen() },
                modifier = Modifier.testTag("arc_reactor_button")
            )

            Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 14.dp))

            // Error Banner (if any)
            AnimatedVisibility(visible = errorBanner != null) {
                HudGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    borderColor = hud.errorCrimson,
                    contentPadding = 10.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = hud.errorCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorBanner.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = hud.textPrimary
                            )
                        }
                        IconButton(onClick = { viewModel.dismissError() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = hud.textSecondary)
                        }
                    }
                }
            }

            // Confirmation Gate Card (Call / SMS)
            AnimatedVisibility(visible = pendingConfirmation != null) {
                pendingConfirmation?.let { pending ->
                    HudGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        borderColor = hud.warningAmber,
                        contentPadding = 12.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val title = when (pending) {
                                is PendingConfirmation.Call -> "Confirm Phone Dialer: ${pending.displayLabel}"
                                is PendingConfirmation.Sms -> "Confirm SMS Composer to ${pending.recipient}"
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = hud.warningAmber
                            )
                            if (pending is PendingConfirmation.Sms && pending.body.isNotBlank()) {
                                Text(
                                    text = "Message: \"${pending.body}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = hud.textSecondary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.confirmPendingAction() },
                                    colors = ButtonDefaults.buttonColors(containerColor = hud.primaryGlow),
                                    modifier = Modifier.testTag("confirm_action_button")
                                ) {
                                    Text("Confirm", color = Color.Black)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.cancelPendingAction() },
                                    modifier = Modifier.testTag("cancel_action_button")
                                ) {
                                    Text("Cancel", color = hud.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            // Live Transcript / Assistant Response HUD Panel
            HudGlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 14.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val activeTranscript = if (isListening && partial.isNotBlank()) partial else lastTranscript
                    if (activeTranscript.isNotBlank()) {
                        Text(
                            text = if (isListening) "HEARING: \"$activeTranscript\"" else "COMMAND: \"$activeTranscript\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = hud.primaryGlow,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = lastResponse,
                            style = MaterialTheme.typography.bodyLarge,
                            color = hud.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSpeaking) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.onStopSpeaking() },
                                modifier = Modifier.testTag("stop_speaking_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeOff,
                                    contentDescription = "Stop speaking",
                                    tint = hud.warningAmber
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // BOTTOM CONTROLS: Large Mic + Wake-Word + Shortcuts + Text Command Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Chat Shortcut
                OutlinedButton(
                    onClick = onOpenChat,
                    modifier = Modifier
                        .testTag("home_chat_shortcut")
                        .minimumInteractiveComponentSize(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Open AI Chat",
                        tint = hud.primaryGlow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chat", color = hud.textPrimary)
                }

                // Large Glowing Microphone / Stop Button
                FilledIconButton(
                    onClick = { requestMicAndListen() },
                    modifier = Modifier
                        .size(70.dp)
                        .testTag("home_mic_button")
                        .border(
                            width = 2.dp,
                            color = if (isListening) hud.errorCrimson else hud.primaryGlow,
                            shape = CircleShape
                        ),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening) hud.errorCrimson.copy(alpha = 0.28f) else hud.primaryGlow.copy(alpha = 0.20f)
                    )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop listening" else "Activate voice input",
                        tint = if (isListening) hud.errorCrimson else hud.primaryGlow,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Settings Shortcut
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .testTag("home_settings_shortcut")
                        .minimumInteractiveComponentSize(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings",
                        tint = hud.primaryGlow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Setup", color = hud.textPrimary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Voice Activation (Wake-Word) Toggle Pill
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (settings.wakeWordEnabled) hud.primaryGlow.copy(alpha = 0.16f) else hud.deepNavy,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (settings.wakeWordEnabled) hud.primaryGlow else hud.borderCyan
                ),
                modifier = Modifier
                    .testTag("wake_word_toggle_pill")
                    .minimumInteractiveComponentSize()
                    .clickable { toggleWakeWord() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = "Wake word activation",
                        tint = if (settings.wakeWordEnabled) hud.primaryGlow else hud.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (settings.wakeWordEnabled) {
                            "Wake Word Active (\"Hey ${settings.assistantName}\")"
                        } else {
                            "Enable Wake Word (\"Hey ${settings.assistantName}\")"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (settings.wakeWordEnabled) hud.primaryGlow else hud.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Text Command Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = {
                        Text(
                            "Command JARVIS (English / Urdu / Roman Urdu)...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = hud.textSecondary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (commandInput.isNotBlank()) {
                                viewModel.submitCommand(commandInput)
                                commandInput = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = hud.primaryGlow,
                        unfocusedBorderColor = hud.borderCyan,
                        focusedContainerColor = hud.deepNavy.copy(alpha = 0.85f),
                        unfocusedContainerColor = hud.deepNavy.copy(alpha = 0.65f),
                        focusedTextColor = hud.textPrimary,
                        unfocusedTextColor = hud.textPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_command_input")
                )

                FilledIconButton(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            viewModel.submitCommand(commandInput)
                            commandInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("home_send_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = hud.primaryGlow)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}
