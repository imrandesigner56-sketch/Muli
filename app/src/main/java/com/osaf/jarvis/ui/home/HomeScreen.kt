package com.osaf.jarvis.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import com.example.R
import com.osaf.jarvis.ReactorState
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
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val lastTranscript by viewModel.lastTranscript.collectAsStateWithLifecycle()
    val lastResponse by viewModel.lastResponse.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val activeVoiceSource by viewModel.activeVoiceSource.collectAsStateWithLifecycle()

    var commandInput by rememberSaveable { mutableStateOf("") }
    var showTypeBar by rememberSaveable { mutableStateOf(false) }
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
                viewModel.onOrbOrMicTapped()
            }
        }
        pendingWakeEnable = false
    }

    fun requestMicAndInteract() {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.onOrbOrMicTapped()
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
                    Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                hud.spaceBlack,
                                hud.primaryGlow.copy(alpha = 0.045f),
                                hud.spaceBlack
                            )
                        )
                    )
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
        val orbSize = when {
            maxWidth > 600.dp -> 244.dp
            isCompactHeight -> 178.dp
            else -> 216.dp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. MINIMAL HEADER: Small Official Logo + Online/Offline Indicator + Single Settings Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.osaf_jarvis_logo),
                        contentDescription = "OSAF JARVIS Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(36.dp)
                    )
                    Column {
                        Text(
                            text = "OSAF ${settings.assistantName.uppercase()}",
                            style = MaterialTheme.typography.titleMedium,
                            color = hud.textPrimary
                        )
                        Text(
                            text = "Hey ${settings.userDisplayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = hud.textSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = hud.deepNavy,
                        border = BorderStroke(1.dp, hud.borderCyan),
                        modifier = Modifier
                            .testTag("online_status_pill")
                            .minimumInteractiveComponentSize()
                            .clickable { viewModel.toggleOnlineOffline() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) hud.onlineGreen else hud.offlineSteel)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOnline) "Online" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = hud.textPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = hud.textSecondary
                        )
                    }
                }
            }

            // 2. CENTRAL ANIMATED JARVIS ORB + MINIMAL CONVERSATION SNIPPET
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                ArcReactorView(
                    state = reactorState,
                    rmsLevel = rmsLevel,
                    assistantName = settings.assistantName,
                    size = orbSize,
                    onClick = { requestMicAndInteract() },
                    modifier = Modifier.testTag("arc_reactor_button")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Subtle active voice badge when speaking
                if (isSpeaking) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = hud.primaryGlow.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, hud.primaryGlow.copy(alpha = 0.35f)),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = activeVoiceSource,
                            style = MaterialTheme.typography.labelSmall,
                            color = hud.primaryGlow,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Minimal previous user message snippet
                val shownUserSnippet = if (isListening && partialTranscript.isNotBlank()) {
                    "\"$partialTranscript\""
                } else if (lastTranscript.isNotBlank()) {
                    lastTranscript
                } else {
                    ""
                }
                if (shownUserSnippet.isNotBlank()) {
                    Text(
                        text = shownUserSnippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = hud.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = errorBanner ?: lastResponse,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (errorBanner != null) hud.warningAmber else hud.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clickable { onOpenChat() }
                )
            }

            // 3. CLEAN VOICE-FIRST BOTTOM CONTROLS: Wake Pill + 1 Central Mic Button + Compact Type Toggle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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

                    if (!canDrawOverlays && settings.wakeWordEnabled) {
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
                                    contentDescription = "Allow overlay popup",
                                    tint = hud.warningAmber,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Overlay Off",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = hud.textPrimary
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showTypeBar) {
                    OutlinedTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        placeholder = {
                            Text(
                                "Type to ${settings.assistantName}...",
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
                            focusedContainerColor = hud.deepNavy,
                            unfocusedContainerColor = hud.deepNavy,
                            focusedTextColor = hud.textPrimary,
                            unfocusedTextColor = hud.textPrimary
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_command_input")
                    )
                }

                // Prominent Centered Mic Button flanked by subtle Keyboard & Chat buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showTypeBar = !showTypeBar },
                        modifier = Modifier.testTag("home_toggle_keyboard")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Toggle text input",
                            tint = if (showTypeBar) hud.primaryGlow else hud.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    FilledIconButton(
                        onClick = { requestMicAndInteract() },
                        modifier = Modifier
                            .size(70.dp)
                            .testTag("home_mic_button")
                            .border(
                                width = 2.dp,
                                color = when {
                                    isListening -> hud.errorCrimson
                                    isSpeaking -> hud.onlineGreen
                                    else -> hud.primaryGlow
                                },
                                shape = CircleShape
                            ),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = when {
                                isListening -> hud.errorCrimson.copy(alpha = 0.24f)
                                isSpeaking -> hud.onlineGreen.copy(alpha = 0.20f)
                                else -> hud.primaryGlow.copy(alpha = 0.20f)
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (isListening || isSpeaking) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = when {
                                isListening -> "Stop listening"
                                isSpeaking -> "Interrupt and speak"
                                else -> "Talk to JARVIS"
                            },
                            tint = when {
                                isListening -> hud.errorCrimson
                                isSpeaking -> hud.onlineGreen
                                else -> hud.primaryGlow
                            },
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    IconButton(
                        onClick = onOpenChat,
                        modifier = Modifier.testTag("home_open_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Open full chat history",
                            tint = hud.textSecondary
                        )
                    }
                }
            }
        }
    }
}
