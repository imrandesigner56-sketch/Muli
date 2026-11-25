package com.osaf.jarvis.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

data class BatteryStatus(
    val percentage: Int,
    val isCharging: Boolean,
    val statusLabel: String
)

sealed class DeviceActionResult {
    data class Success(val message: String) : DeviceActionResult()
    data class Error(val message: String) : DeviceActionResult()
}

class DeviceController(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _hasFlashlight = MutableStateFlow(false)
    val hasFlashlight: StateFlow<Boolean> = _hasFlashlight.asStateFlow()

    private val _isNetworkConnected = MutableStateFlow(checkCurrentConnectivity())
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private var torchCameraId: String? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                _isFlashlightOn.value = enabled
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isNetworkConnected.value = checkCurrentConnectivity()
        }

        override fun onLost(network: Network) {
            _isNetworkConnected.value = checkCurrentConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _isNetworkConnected.value =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    init {
        initTorch()
        registerNetworkMonitor()
    }

    private fun initTorch() {
        val cm = cameraManager ?: return
        try {
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facingBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                if (hasFlash && (facingBack || torchCameraId == null)) {
                    torchCameraId = id
                    _hasFlashlight.value = true
                    if (facingBack) break
                }
            }
            cm.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
            _hasFlashlight.value = false
        }
    }

    private fun registerNetworkMonitor() {
        val cm = connectivityManager ?: return
        try {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, networkCallback)
        } catch (_: Exception) {
            _isNetworkConnected.value = checkCurrentConnectivity()
        }
    }

    fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun setFlashlight(enabled: Boolean): DeviceActionResult {
        val cm = cameraManager
        val camId = torchCameraId
        if (cm == null || camId == null) {
            return DeviceActionResult.Error("Flashlight hardware is unavailable on this device.")
        }
        return try {
            cm.setTorchMode(camId, enabled)
            _isFlashlightOn.value = enabled
            DeviceActionResult.Success(if (enabled) "Flashlight enabled." else "Flashlight disabled.")
        } catch (e: Exception) {
            DeviceActionResult.Error("Unable to control flashlight: ${e.localizedMessage ?: "hardware busy"}")
        }
    }

    fun getBatteryStatus(): BatteryStatus {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: -1
        }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val label = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            else -> "Active"
        }
        return BatteryStatus(pct, isCharging, label)
    }

    fun getCurrentDateTimeFormatted(): Pair<String, String> {
        val now = Date()
        val date = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(now)
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        return date to time
    }

    fun getMediaVolumePercent(): Int {
        val am = audioManager ?: return 50
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current * 100f) / max).toInt().coerceIn(0, 100)
    }

    fun setMediaVolumePercent(percent: Int): DeviceActionResult {
        val am = audioManager ?: return DeviceActionResult.Error("Audio service unavailable.")
        val clamped = percent.coerceIn(0, 100)
        return try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val target = ((clamped / 100f) * max).toInt().coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            DeviceActionResult.Success("Media volume set to $clamped%.")
        } catch (e: SecurityException) {
            DeviceActionResult.Error("Volume change blocked by Do Not Disturb settings.")
        } catch (e: Exception) {
            DeviceActionResult.Error("Failed to adjust volume: ${e.localizedMessage}")
        }
    }

    fun adjustMediaVolume(raise: Boolean): DeviceActionResult {
        val am = audioManager ?: return DeviceActionResult.Error("Audio service unavailable.")
        return try {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            DeviceActionResult.Success(
                if (raise) "Media volume increased to ${getMediaVolumePercent()}%."
                else "Media volume decreased to ${getMediaVolumePercent()}%."
            )
        } catch (e: Exception) {
            DeviceActionResult.Error("Unable to adjust media volume.")
        }
    }

    fun refreshInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        val apps = resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                InstalledAppInfo(label = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
        _installedApps.value = apps
        return apps
    }

    fun findBestMatchingApp(query: String): InstalledAppInfo? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val apps = _installedApps.value.ifEmpty { refreshInstalledApps() }

        // Common alias package hints
        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "calculator" to "com.google.android.calculator"
        )
        knownPackages[q]?.let { pkgHint ->
            apps.firstOrNull { it.packageName == pkgHint }?.let { return it }
        }

        // Exact label match
        apps.firstOrNull { it.label.equals(q, ignoreCase = true) }?.let { return it }
        // Starts with
        apps.firstOrNull { it.label.lowercase().startsWith(q) }?.let { return it }
        // Contains
        return apps.firstOrNull { it.label.lowercase().contains(q) }
    }

    fun launchApp(app: InstalledAppInfo): DeviceActionResult {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return DeviceActionResult.Error("${app.label} cannot be launched.")
        return startIntentSafely(intent, "Opening ${app.label}.")
    }

    fun launchAppByQuery(query: String): DeviceActionResult {
        val clean = query.trim().lowercase()
        if (clean == "settings" || clean == "setting") {
            return openSystemSettings()
        }
        if (clean == "camera") {
            return openCamera()
        }
        val matched = findBestMatchingApp(query)
            ?: return DeviceActionResult.Error("No installed app matching \"$query\" was found.")
        return launchApp(matched)
    }

    fun openSystemSettings(): DeviceActionResult {
        return startIntentSafely(Intent(Settings.ACTION_SETTINGS), "Opening System Settings.")
    }

    fun openWifiSettings(): DeviceActionResult {
        return startIntentSafely(Intent(Settings.ACTION_WIFI_SETTINGS), "Opening Wi-Fi Settings.")
    }

    fun openBluetoothSettings(): DeviceActionResult {
        return startIntentSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Opening Bluetooth Settings.")
    }

    fun openCamera(): DeviceActionResult {
        return startIntentSafely(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            "Opening Camera."
        )
    }

    fun setSystemAlarm(hour24: Int, minute: Int, message: String = "OSAF JARVIS Alarm"): DeviceActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour24.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return startIntentSafely(
            intent,
            String.format(Locale.getDefault(), "Preparing alarm for %02d:%02d.", hour24, minute)
        )
    }

    fun setSystemTimer(seconds: Int, message: String = "OSAF JARVIS Timer"): DeviceActionResult {
        val clamped = seconds.coerceAtLeast(1)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, clamped)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        val mins = clamped / 60
        val remSecs = clamped % 60
        val label = if (mins > 0) "$mins min ${if (remSecs > 0) "$remSecs sec" else ""}".trim() else "$remSecs sec"
        return startIntentSafely(intent, "Setting timer for $label.")
    }

    fun openGoogleSearch(query: String): DeviceActionResult {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val uri = Uri.parse("https://www.google.com/search?q=$encoded")
        return startIntentSafely(Intent(Intent.ACTION_VIEW, uri), "Searching Google for \"$query\".")
    }

    fun openWebsite(rawUrl: String): DeviceActionResult {
        val cleaned = rawUrl.trim()
        val httpsUrl = when {
            cleaned.startsWith("https://", ignoreCase = true) -> cleaned
            cleaned.startsWith("http://", ignoreCase = true) -> "https://" + cleaned.removePrefix("http://")
            else -> "https://$cleaned"
        }
        val uri = Uri.parse(httpsUrl)
        return startIntentSafely(Intent(Intent.ACTION_VIEW, uri), "Opening $httpsUrl.")
    }

    fun prepareDialer(phoneNumber: String): DeviceActionResult {
        val sanitized = phoneNumber.filter { it.isDigit() || it in charArrayOf('+', '-', ' ') }.trim()
        if (sanitized.isEmpty()) {
            return DeviceActionResult.Error("No valid phone number provided.")
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(sanitized)}"))
        return startIntentSafely(intent, "Opening Phone Dialer for $sanitized.")
    }

    fun prepareSms(recipient: String, body: String): DeviceActionResult {
        val sanitized = recipient.filter { it.isDigit() || it == '+' }.trim()
        val uri = if (sanitized.isNotEmpty()) Uri.parse("smsto:${Uri.encode(sanitized)}") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (body.isNotBlank()) putExtra("sms_body", body)
        }
        return startIntentSafely(intent, "Opening SMS composer.")
    }

    private fun startIntentSafely(intent: Intent, successMessage: String): DeviceActionResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            DeviceActionResult.Success(successMessage)
        } catch (e: Exception) {
            DeviceActionResult.Error("No compatible system app found to handle this action.")
        }
    }

    fun release() {
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) { }
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) { }
    }
}
