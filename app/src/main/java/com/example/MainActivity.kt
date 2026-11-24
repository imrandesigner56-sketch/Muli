package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.osaf.jarvis.JarvisApplication
import com.osaf.jarvis.ui.JarvisAppShell

class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        (application as JarvisApplication).container.coordinator.setAppForeground(true)
    }

    override fun onStop() {
        (application as JarvisApplication).container.coordinator.setAppForeground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            JarvisAppShell()
        }
    }
}
