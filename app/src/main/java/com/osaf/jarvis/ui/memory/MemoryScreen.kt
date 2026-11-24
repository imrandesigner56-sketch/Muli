package com.osaf.jarvis.ui.memory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.osaf.jarvis.ui.components.HudGlassCard
import com.osaf.jarvis.ui.theme.LocalJarvisHudColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryScreen(viewModel: MemoryViewModel = viewModel()) {
    val hud = LocalJarvisHudColors.current
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val banner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddNote by remember { mutableStateOf(false) }
    var showAddReminder by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportBackup(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBackup(it) }
    }

    Scaffold(
        containerColor = hud.spaceBlack,
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                FloatingActionButton(
                    onClick = { if (selectedTab == 0) showAddNote = true else showAddReminder = true },
                    containerColor = hud.primaryGlow,
                    contentColor = Color.Black,
                    modifier = Modifier.testTag("memory_add_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add memory item")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "JARVIS MEMORY CORE",
                style = MaterialTheme.typography.headlineSmall,
                color = hud.primaryGlow
            )
            Text(
                text = "Transparent Local Storage & User-Controlled Backups",
                style = MaterialTheme.typography.labelMedium,
                color = hud.textSecondary
            )

            if (banner != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HudGlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 10.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(banner.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = hud.textPrimary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.dismissBanner() }) { Text("OK", color = hud.primaryGlow) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = hud.deepNavy,
                contentColor = hud.primaryGlow
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Notes (${notes.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Reminders (${reminders.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Backup") })
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearch(it) },
                        placeholder = { Text("Search saved notes...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notes_search_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (notes.isEmpty()) {
                            item {
                                Text(
                                    text = "No notes saved yet. Tap + or say \"JARVIS, ek note save karo...\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = hud.textSecondary,
                                    modifier = Modifier.padding(top = 24.dp)
                                )
                            }
                        }
                        items(notes, key = { it.id }) { note ->
                            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(note.title, style = MaterialTheme.typography.titleMedium, color = hud.primaryGlow)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(note.content, style = MaterialTheme.typography.bodyLarge, color = hud.textPrimary)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        val dt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(note.updatedAt))
                                        Text("${note.category} • $dt", style = MaterialTheme.typography.labelSmall, color = hud.textSecondary)
                                    }
                                    IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete note", tint = hud.errorCrimson)
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }

                1 -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (reminders.isEmpty()) {
                            item {
                                Text(
                                    text = "No reminders scheduled. Tap + or say \"JARVIS, remind me in 10 minutes...\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = hud.textSecondary,
                                    modifier = Modifier.padding(top = 24.dp)
                                )
                            }
                        }
                        items(reminders, key = { it.id }) { rem ->
                            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = rem.isCompleted,
                                        onCheckedChange = { viewModel.toggleReminder(rem) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rem.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = hud.textPrimary,
                                            textDecoration = if (rem.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                        val timeStr = SimpleDateFormat("EEE, dd MMM hh:mm a", Locale.getDefault())
                                            .format(Date(rem.triggerAtMillis))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = hud.primaryGlow, modifier = Modifier.padding(end = 4.dp))
                                            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = hud.textSecondary)
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteReminder(rem.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete reminder", tint = hud.errorCrimson)
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }

                2 -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("DATA PORTABILITY", style = MaterialTheme.typography.labelMedium, color = hud.primaryGlow)
                                    Text(
                                        text = "Export your notes, reminders, and personalization preferences to a JSON file, or restore a previous backup. API keys are never exported.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = hud.textSecondary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { exportLauncher.launch("osaf_jarvis_backup.json") },
                                            colors = ButtonDefaults.buttonColors(containerColor = hud.primaryGlow),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Upload, contentDescription = null, tint = Color.Black)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Export", color = Color.Black)
                                        }
                                        OutlinedButton(
                                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = hud.primaryGlow)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Import", color = hud.textPrimary)
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            HudGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = hud.errorCrimson.copy(alpha = 0.6f)) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("PRIVACY & DATA WIPE", style = MaterialTheme.typography.labelMedium, color = hud.errorCrimson)
                                    OutlinedButton(
                                        onClick = { viewModel.clearAllHistory() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = hud.warningAmber)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Clear All Conversation History", color = hud.textPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = { showClearConfirm = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = hud.errorCrimson)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Delete All Notes & Reminders", color = hud.errorCrimson)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddNote) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("General") }
        AlertDialog(
            onDismissRequest = { showAddNote = false },
            title = { Text("New Local Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, minLines = 3)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addNote(title, content, category)
                    showAddNote = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddNote = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddReminder) {
        var title by remember { mutableStateOf("") }
        var minutesStr by remember { mutableStateOf("10") }
        AlertDialog(
            onDismissRequest = { showAddReminder = false },
            title = { Text("New Local Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Reminder message") }, singleLine = true)
                    OutlinedTextField(value = minutesStr, onValueChange = { minutesStr = it.filter { c -> c.isDigit() } }, label = { Text("Minutes from now") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val mins = minutesStr.toIntOrNull()?.coerceAtLeast(1) ?: 10
                    viewModel.addReminder(title, mins)
                    showAddReminder = false
                }) { Text("Schedule") }
            },
            dismissButton = {
                TextButton(onClick = { showAddReminder = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Delete All Memories?") },
            text = { Text("This permanently deletes all saved notes and reminders from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllMemories()
                    showClearConfirm = false
                }) { Text("Delete All", color = hud.errorCrimson) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
