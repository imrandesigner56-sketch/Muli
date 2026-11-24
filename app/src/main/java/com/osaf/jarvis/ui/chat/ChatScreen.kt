package com.osaf.jarvis.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.osaf.jarvis.ReactorState
import com.osaf.jarvis.database.ChatMessageEntity
import com.osaf.jarvis.ui.theme.LocalJarvisHudColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val hud = LocalJarvisHudColors.current

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streamingResponse.collectAsStateWithLifecycle()
    val reactorState by viewModel.reactorState.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnlineActive.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val quickPrompts = remember {
        listOf(
            "Battery kitni hai?",
            "250 multiply by 40",
            "Flashlight on karo",
            "Aaj ki date batao",
            "What can you do?"
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.toggleVoiceInput()
    }

    LaunchedEffect(messages.size, streaming) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(hud.spaceBlack)
            .imePadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AI Chat",
                    style = MaterialTheme.typography.headlineSmall,
                    color = hud.textPrimary
                )
                Text(
                    text = if (isOnline) "Online AI & Device Commands" else "Offline Mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOnline) hud.onlineGreen else hud.textSecondary
                )
            }
            if (messages.isNotEmpty()) {
                IconButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.testTag("clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear conversation history",
                        tint = hud.textSecondary
                    )
                }
            }
        }

        // Quick bilingual command chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(quickPrompts) { prompt ->
                AssistChip(
                    onClick = { viewModel.sendMessage(prompt) },
                    label = { Text(prompt, style = MaterialTheme.typography.bodyMedium) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = hud.deepNavy,
                        labelColor = hud.textPrimary
                    ),
                    border = BorderStroke(1.dp, hud.borderCyan)
                )
            }
        }

        // Messages list
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && streaming.isBlank()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = hud.primaryGlow,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Hello Sir, I am OSAF JARVIS.",
                        style = MaterialTheme.typography.titleMedium,
                        color = hud.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ask in English, Urdu, or Roman Urdu. Device commands work offline; configure an HTTPS API key in Settings for cloud AI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = hud.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(msg)
                    }
                    if (streaming.isNotBlank() || reactorState == ReactorState.THINKING) {
                        item(key = "streaming_bubble") {
                            StreamingBubble(
                                text = if (streaming.isNotBlank()) "$streaming ▌" else "Processing telemetry...",
                                isStreaming = streaming.isNotBlank()
                            )
                        }
                    }
                }
            }
        }

        // Bottom input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(hud.deepNavy)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.toggleVoiceInput() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.testTag("chat_mic_button")
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Voice input",
                    tint = if (isListening) hud.errorCrimson else hud.primaryGlow
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message JARVIS...", color = hud.textSecondary) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (input.isNotBlank()) {
                            viewModel.sendMessage(input)
                            input = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = hud.primaryGlow,
                    unfocusedBorderColor = hud.borderCyan,
                    focusedTextColor = hud.textPrimary,
                    unfocusedTextColor = hud.textPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )

            FilledIconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        viewModel.sendMessage(input)
                        input = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_send_button"),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = hud.primaryGlow)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = hud.onPrimaryAccent)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Chat History?") },
            text = { Text("This permanently deletes all saved conversation messages from local storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All", color = hud.errorCrimson)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessageEntity) {
    val hud = LocalJarvisHudColors.current
    val isUser = message.role == "user"
    val timeText = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) hud.primaryGlow.copy(alpha = 0.16f) else hud.deepNavy,
            border = BorderStroke(
                1.dp,
                when {
                    isUser -> hud.primaryGlow.copy(alpha = 0.55f)
                    message.source == "error" -> hud.errorCrimson.copy(alpha = 0.6f)
                    else -> hud.borderCyan
                }
            ),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = hud.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = hud.textSecondary
                    )
                    Text(
                        text = "${message.language.uppercase()} • ${message.source.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = hud.primaryGlow.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String, isStreaming: Boolean) {
    val hud = LocalJarvisHudColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = hud.deepNavy,
            border = BorderStroke(1.dp, hud.primaryGlow.copy(alpha = 0.7f)),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = hud.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isStreaming) "STREAMING" else "THINKING",
                    style = MaterialTheme.typography.labelSmall,
                    color = hud.onlineGreen
                )
            }
        }
    }
}
