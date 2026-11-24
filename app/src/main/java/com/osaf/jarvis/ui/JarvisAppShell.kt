package com.osaf.jarvis.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.osaf.jarvis.JarvisApplication
import com.osaf.jarvis.database.JarvisSettings
import com.osaf.jarvis.ui.chat.ChatScreen
import com.osaf.jarvis.ui.components.GeminiBottomPopup
import com.osaf.jarvis.ui.controls.ControlsScreen
import com.osaf.jarvis.ui.home.HomeScreen
import com.osaf.jarvis.ui.memory.MemoryScreen
import com.osaf.jarvis.ui.settings.SettingsScreen
import com.osaf.jarvis.ui.theme.JarvisTheme
import com.osaf.jarvis.ui.theme.LocalJarvisHudColors

enum class JarvisNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    CHAT("chat", "AI Chat", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    CONTROLS("controls", "Controls", Icons.Filled.Tune, Icons.Outlined.Tune),
    MEMORY("memory", "Memory", Icons.Filled.Memory, Icons.Outlined.Memory),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun JarvisAppShell() {
    val context = LocalContext.current
    val app = context.applicationContext as JarvisApplication
    val coordinator = app.container.coordinator
    val voiceEngine = app.container.voiceEngine

    val settings by app.container.settingsRepository.settingsFlow.collectAsStateWithLifecycle(
        initialValue = JarvisSettings()
    )
    val isBottomPopupVisible by coordinator.isBottomPopupVisible.collectAsStateWithLifecycle()
    val reactorState by coordinator.reactorState.collectAsStateWithLifecycle()
    val rmsLevel by voiceEngine.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by voiceEngine.partialTranscript.collectAsStateWithLifecycle()
    val lastTranscript by coordinator.lastTranscript.collectAsStateWithLifecycle()
    val lastResponse by coordinator.lastResponse.collectAsStateWithLifecycle()
    val errorBanner by coordinator.errorBanner.collectAsStateWithLifecycle()
    val pendingConfirmation by coordinator.pendingConfirmation.collectAsStateWithLifecycle()
    val isListening by voiceEngine.isListening.collectAsStateWithLifecycle()
    val isSpeaking by voiceEngine.isSpeaking.collectAsStateWithLifecycle()

    JarvisTheme(themeMode = settings.themeMode) {
        val hud = LocalJarvisHudColors.current
        LaunchedEffect(hud.isLight) {
            (context as? ComponentActivity)?.enableEdgeToEdge(
                statusBarStyle = if (hud.isLight) {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                },
                navigationBarStyle = if (hud.isLight) {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                }
            )
        }
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        fun navigateTo(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 700.dp

            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = hud.spaceBlack,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                containerColor = hud.deepNavy,
                                tonalElevation = 0.dp
                            ) {
                                JarvisNavItem.entries.forEach { item ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTo(item.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = hud.primaryGlow,
                                            selectedTextColor = hud.primaryGlow,
                                            unselectedIconColor = hud.textSecondary,
                                            unselectedTextColor = hud.textSecondary,
                                            indicatorColor = hud.primaryGlow.copy(alpha = 0.16f)
                                        ),
                                        modifier = Modifier.testTag("nav_${item.route}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail(containerColor = hud.deepNavy) {
                                JarvisNavItem.entries.forEach { item ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTo(item.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = hud.primaryGlow,
                                            selectedTextColor = hud.primaryGlow,
                                            unselectedIconColor = hud.textSecondary,
                                            unselectedTextColor = hud.textSecondary,
                                            indicatorColor = hud.primaryGlow.copy(alpha = 0.16f)
                                        )
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = JarvisNavItem.HOME.route,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(JarvisNavItem.HOME.route) {
                                HomeScreen(
                                    onOpenChat = { navigateTo(JarvisNavItem.CHAT.route) },
                                    onOpenSettings = { navigateTo(JarvisNavItem.SETTINGS.route) }
                                )
                            }
                            composable(JarvisNavItem.CHAT.route) { ChatScreen() }
                            composable(JarvisNavItem.CONTROLS.route) { ControlsScreen() }
                            composable(JarvisNavItem.MEMORY.route) { MemoryScreen() }
                            composable(JarvisNavItem.SETTINGS.route) { SettingsScreen() }
                        }
                    }
                }

                // Dim backdrop when Gemini-style bottom popup is active
                if (isBottomPopupVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { coordinator.dismissBottomPopup() }
                    )
                }

                // Gemini-style Bottom Sheet Popup anchored at bottom
                GeminiBottomPopup(
                    visible = isBottomPopupVisible,
                    assistantName = settings.assistantName,
                    reactorState = reactorState,
                    rmsLevel = rmsLevel,
                    partialTranscript = partialTranscript,
                    lastTranscript = lastTranscript,
                    lastResponse = lastResponse,
                    errorMessage = errorBanner,
                    pendingConfirmation = pendingConfirmation,
                    isListening = isListening,
                    isSpeaking = isSpeaking,
                    onMicClick = {
                        if (isListening) coordinator.cancelVoiceCommand()
                        else coordinator.startVoiceCommand(openBottomSheet = true)
                    },
                    onStopSpeaking = { coordinator.stopSpeaking() },
                    onConfirmAction = { coordinator.confirmPendingAction() },
                    onCancelAction = { coordinator.cancelPendingAction() },
                    onDismiss = { coordinator.dismissBottomPopup() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
