package com.osaf.jarvis.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class SystemDiagnostics(
    val supportedAbis: List<String>,
    val primaryAbi: String,
    val supportsArm32: Boolean,
    val supportsArm64: Boolean,
    val androidVersion: String,
    val apiLevel: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowRamDevice: Boolean,
    val deviceModel: String
)

object DeviceDiagnostics {
    fun collect(context: Context): SystemDiagnostics {
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024L * 1024L)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val isLowRam = activityManager?.isLowRamDevice ?: (totalMb in 1..3072)

        return SystemDiagnostics(
            supportedAbis = abis,
            primaryAbi = abis.firstOrNull() ?: "unknown",
            supportsArm32 = abis.any { it.contains("armeabi-v7a") },
            supportsArm64 = abis.any { it.contains("arm64-v8a") },
            androidVersion = Build.VERSION.RELEASE ?: "8.0+",
            apiLevel = Build.VERSION.SDK_INT,
            totalRamMb = totalMb,
            availableRamMb = availMb,
            isLowRamDevice = isLowRam,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        )
    }
}
