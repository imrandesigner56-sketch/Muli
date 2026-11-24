package com.osaf.jarvis.ui.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.osaf.jarvis.ui.components.HudGlassCard
import com.osaf.jarvis.ui.theme.LocalJarvisHudColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlsScreen(viewModel: ControlsViewModel = viewModel()) {
    val hud = LocalJarvisHudColors.current
    val flashOn by viewModel.isFlashlightOn.collectAsStateWithLifecycle()
    val hasFlash by viewModel.hasFlashlight.collectAsStateWithLifecycle()
    val volume by viewModel.mediaVolume.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()

    var appSearch by remember { mutableStateOf("") }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showCallDialog by remember { mutableStateOf(false) }
    var showSmsDialog by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }

    val filteredApps = remember(apps, appSearch) {
        if (appSearch.isBlank()) apps
        else apps.filter {
            it.label.contains(appSearch, ignoreCase = true) ||
                it.packageName.contains(appSearch, ignoreCase = true)
        }
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Device Controls",
                        style = MaterialTheme.typography.headlineSmall,
                        color = hud.textPrimary
                    )
                    Text(
                        text = "Quick Hardware, Apps & Diagnostics",
                        style = MaterialTheme.typography.labelMedium,
                        color = hud.textSecondary
                    )
                }
                IconButton(onClick = { viewModel.refreshTelemetry() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh telemetry", tint = hud.primaryGlow)
                }
            }
        }

        if (statusMsg != null) {
            item {
                HudGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = hud.primaryGlow,
                    contentPadding = 10.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusMsg.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = hud.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearStatus() }) {
                            Text("OK", color = hud.primaryGlow)
                        }
                    }
                }
            }
        }

        // Hardware Controls: Flashlight + Media Volume
        item {
            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (flashOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = "Flashlight",
                                tint = if (flashOn) hud.warningAmber else hud.primaryGlow
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Flashlight", style = MaterialTheme.typography.titleMedium, color = hud.textPrimary)
                                Text(
                                    text = if (!hasFlash) "No flash hardware detected" else if (flashOn) "Torch Active" else "Standby",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = hud.textSecondary
                                )
                            }
                        }
                        Switch(
                            checked = flashOn,
                            onCheckedChange = { viewModel.toggleFlashlight() },
                            enabled = hasFlash,
                            modifier = Modifier.testTag("flashlight_switch")
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Media Volume", tint = hud.primaryGlow)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Media Volume", style = MaterialTheme.typography.titleMedium, color = hud.textPrimary)
                            }
                            Text("$volume%", style = MaterialTheme.typography.labelMedium, color = hud.primaryGlow)
                        }
                        Slider(
                            value = volume.toFloat(),
                            onValueChange = { viewModel.setVolume(it.toInt()) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = hud.primaryGlow,
                                activeTrackColor = hud.primaryGlow
                            )
                        )
                    }
                }
            }
        }

        // Quick System Actions
        item {
            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SYSTEM & COMMUNICATION", style = MaterialTheme.typography.labelMedium, color = hud.primaryGlow)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ControlChip(Icons.Default.CameraAlt, "Camera") { viewModel.openCamera() }
                        ControlChip(Icons.Default.Wifi, "Wi-Fi Settings") { viewModel.openWifiSettings() }
                        ControlChip(Icons.Default.Bluetooth, "Bluetooth") { viewModel.openBluetoothSettings() }
                        ControlChip(Icons.Default.Settings, "Settings") { viewModel.openSystemSettings() }
                        ControlChip(Icons.Default.Timer, "5m Timer") { viewModel.startQuickTimer(300) }
                        ControlChip(Icons.Default.Alarm, "Set Alarm") { showAlarmDialog = true }
                        ControlChip(Icons.Default.Search, "Google Search") { showSearchDialog = true }
                        ControlChip(Icons.Default.Call, "Prepare Call") { showCallDialog = true }
                        ControlChip(Icons.Default.Sms, "Prepare SMS") { showSmsDialog = true }
                    }
                }
            }
        }

        // System Hardware & ABI Telemetry
        item {
            HudGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = "Diagnostics", tint = hud.primaryGlow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("HARDWARE TELEMETRY", style = MaterialTheme.typography.labelMedium, color = hud.primaryGlow)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = hud.onlineGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Battery: ${battery.percentage}% (${battery.statusLabel})  •  RAM: ${diagnostics.availableRamMb}MB free / ${diagnostics.totalRamMb}MB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = hud.textPrimary
                        )
                    }
                    Text(
                        text = "Device: ${diagnostics.deviceModel} (Android ${diagnostics.androidVersion}, API ${diagnostics.apiLevel})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = hud.textSecondary
                    )
                    Text(
                        text = "Supported ABIs: ${diagnostics.supportedAbis.joinToString(", ")} " +
                            "(ARM64=${diagnostics.supportsArm64}, ARM32=${diagnostics.supportsArm32})",
                        style = MaterialTheme.typography.labelSmall,
                        color = hud.primaryGlow
                    )
                }
            }
        }

        // Installed Applications Launcher
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Apps, contentDescription = "Installed Apps", tint = hud.primaryGlow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INSTALLED APPLICATIONS (${filteredApps.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = hud.primaryGlow
                    )
                }
                OutlinedTextField(
                    value = appSearch,
                    onValueChange = { appSearch = it },
                    placeholder = { Text("Search installed apps (YouTube, WhatsApp, Chrome...)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(filteredApps.take(40), key = { it.packageName }) { app ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = hud.deepNavy,
                border = BorderStroke(1.dp, hud.borderCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.launchApp(app) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = hud.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = hud.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text("LAUNCH", style = MaterialTheme.typography.labelSmall, color = hud.primaryGlow)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showSearchDialog) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Google Search") },
            text = {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("Search query") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (q.isNotBlank()) viewModel.searchGoogle(q)
                    showSearchDialog = false
                }) { Text("Search") }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCallDialog) {
        var number by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCallDialog = false },
            title = { Text("Confirm Phone Dialer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("JARVIS never places silent calls. Confirming opens the system Phone Dialer.")
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text("Phone number") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.prepareCall(number)
                    showCallDialog = false
                }) { Text("Open Dialer") }
            },
            dismissButton = {
                TextButton(onClick = { showCallDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSmsDialog) {
        var number by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSmsDialog = false },
            title = { Text("Confirm SMS Composer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("JARVIS never sends silent SMS. Confirming opens your SMS app.")
                    OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("Recipient number") }, singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message text") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.prepareSms(number, body)
                    showSmsDialog = false
                }) { Text("Open SMS App") }
            },
            dismissButton = {
                TextButton(onClick = { showSmsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAlarmDialog) {
        var hour by remember { mutableIntStateOf(7) }
        var minute by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showAlarmDialog = false },
            title = { Text("Set System Alarm") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(String.format("Alarm Time: %02d:%02d", hour, minute))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { hour = (hour + 1) % 24 }) { Text("Hour +1") }
                        OutlinedButton(onClick = { minute = (minute + 5) % 60 }) { Text("Min +5") }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setAlarm(hour, minute)
                        showAlarmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = hud.primaryGlow)
                ) { Text("Set Alarm", color = hud.onPrimaryAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showAlarmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ControlChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val hud = LocalJarvisHudColors.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, hud.borderCyan)
    ) {
        Icon(icon, contentDescription = label, tint = hud.primaryGlow, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = hud.textPrimary)
    }
}
