package com.osaf.jarvis.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.osaf.jarvis.ui.components.ArcReactorView
import com.osaf.jarvis.ui.theme.LocalJarvisHudColors

@Composable
fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hud = LocalJarvisHudColors.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val reactorState by viewModel.reactorState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnlineActive.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val lastResponse by viewModel.lastResponse.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val battery by viewModel.batteryStatus.collectAsStateWithLifecycle()

    var commandInput by rememberSaveable { mutableStateOf("") }
    var pendingWakeEnable by remember { mutableStateOf(false) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (hud.isSimpleTheme) {
                    Modifier.background(hud.spaceBlack)
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(hud.spaceBlack, Color(0xFF061528), hud.spaceBlack)
                        )
                    )
                }
            )
            .imePadding()
    ) {
        val isCompactHeight = maxHeight < 650.dp
        val reactorSize = when {
            maxWidth > 600.dp -> 240.dp
            isCompactHeight -> 175.dp
            else -> 215.dp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. MINIMAL CLEAN HEADER (No top popup banners)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OSAF ${settings.assistantName.uppercase()}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = hud.primaryGlow
                    )
                    Text(
                        text = "Hey ${settings.userDisplayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = hud.textSecondary
                    )
                }

                // Single clean telemetry chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = hud.deepNavy.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, hud.borderCyan),
                    modifier = Modifier
                        .testTag("online_status_pill")
                        .minimumInteractiveComponentSize()
                        .clickable { viewModel.toggleOnlineOffline() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) hud.onlineGreen else hud.offlineSteel)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = hud.textPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                            contentDescription = "Battery",
                            tint = hud.primaryGlow,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (battery.percentage >= 0) "${battery.percentage}%" else "--",
                            style = MaterialTheme.typography.labelSmall,
                            color = hud.textSecondary
                        )
                    }
                }
            }

            // 2. SPACIOUS CENTER ORB & WARM MESSAGE
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                ArcReactorView(
                    state = reactorState,
                    rmsLevel = rmsLevel,
                    assistantName = settings.assistantName,
                    size = reactorSize,
                    onClick = { requestMicAndListen() },
                    modifier = Modifier.testTag("arc_reactor_button")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = errorBanner ?: lastResponse,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (errorBanner != null) hud.warningAmber else hud.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
            }

            // 3. SIMPLE CLEAN BOTTOM BAR
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Quick Wake-Word & Background Gemini Popup toggles
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (settings.wakeWordEnabled) hud.primaryGlow.copy(alpha = 0.16f) else hud.deepNavy,
                        border = BorderStroke(
                            1.dp,
                            if (settings.wakeWordEnabled) hud.primaryGlow else hud.borderCyan
                        ),
                        modifier = Modifier
                            .testTag("wake_word_toggle_pill")
                            .minimumInteractiveComponentSize()
                            .clickable { toggleWakeWord() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = "Hey JARVIS wake word",
                                tint = if (settings.wakeWordEnabled) hud.primaryGlow else hud.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (settings.wakeWordEnabled) "Hey ${settings.assistantName}: ON" else "Hey ${settings.assistantName}: OFF",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (settings.wakeWordEnabled) hud.primaryGlow else hud.textSecondary
                            )
                        }
                    }

                    if (!canDrawOverlays) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = hud.deepNavy,
                            border = BorderStroke(1.dp, hud.borderCyan),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable { openOverlayPermissionSettings() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = "Allow bottom popup outside app",
                                    tint = hud.warningAmber,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Popup Outside App",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = hud.textPrimary
                                )
                            }
                        }
                    }
                }

                // Clean Pill Input + Glowing Mic Button
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
                                "Ask anything...",
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
                        trailingIcon = {
                            if (commandInput.isNotBlank()) {
                                FilledIconButton(
                                    onClick = {
                                        viewModel.submitCommand(commandInput)
                                        commandInput = ""
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("home_send_button"),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = hud.primaryGlow)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = hud.onPrimaryAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = hud.primaryGlow,
                            unfocusedBorderColor = hud.borderCyan,
                            focusedContainerColor = hud.deepNavy.copy(alpha = 0.85f),
                            unfocusedContainerColor = hud.deepNavy.copy(alpha = 0.72f),
                            focusedTextColor = hud.textPrimary,
                            unfocusedTextColor = hud.textPrimary
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_command_input")
                    )

                    FilledIconButton(
                        onClick = { requestMicAndListen() },
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("home_mic_button")
                            .border(
                                width = 1.5.dp,
                                color = if (isListening) hud.errorCrimson else hud.primaryGlow,
                                shape = CircleShape
                            ),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isListening) hud.errorCrimson.copy(alpha = 0.25f) else hud.primaryGlow.copy(alpha = 0.2f)
                        )
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop listening" else "Talk to JARVIS",
                            tint = if (isListening) hud.errorCrimson else hud.primaryGlow,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
