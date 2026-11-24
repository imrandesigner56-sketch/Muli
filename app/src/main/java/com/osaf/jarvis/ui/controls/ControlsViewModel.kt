package com.osaf.jarvis.ui.controls

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.osaf.jarvis.JarvisApplication
import com.osaf.jarvis.device.BatteryStatus
import com.osaf.jarvis.device.DeviceActionResult
import com.osaf.jarvis.device.InstalledAppInfo
import com.osaf.jarvis.utils.DeviceDiagnostics
import com.osaf.jarvis.utils.SystemDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ControlsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as JarvisApplication).container
    private val deviceController = container.deviceController

    val isFlashlightOn: StateFlow<Boolean> = deviceController.isFlashlightOn
    val hasFlashlight: StateFlow<Boolean> = deviceController.hasFlashlight
    val isOnline: StateFlow<Boolean> = deviceController.isNetworkConnected
    val installedApps: StateFlow<List<InstalledAppInfo>> = deviceController.installedApps

    private val _mediaVolume = MutableStateFlow(deviceController.getMediaVolumePercent())
    val mediaVolume: StateFlow<Int> = _mediaVolume.asStateFlow()

    private val _battery = MutableStateFlow(deviceController.getBatteryStatus())
    val battery: StateFlow<BatteryStatus> = _battery.asStateFlow()

    private val _diagnostics = MutableStateFlow(DeviceDiagnostics.collect(application))
    val diagnostics: StateFlow<SystemDiagnostics> = _diagnostics.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun refreshTelemetry() {
        _battery.value = deviceController.getBatteryStatus()
        _mediaVolume.value = deviceController.getMediaVolumePercent()
        _diagnostics.value = DeviceDiagnostics.collect(getApplication())
        deviceController.refreshInstalledApps()
    }

    fun toggleFlashlight() {
        val next = !isFlashlightOn.value
        reportResult(deviceController.setFlashlight(next))
    }

    fun setVolume(percent: Int) {
        _mediaVolume.value = percent.coerceIn(0, 100)
        reportResult(deviceController.setMediaVolumePercent(percent))
    }

    fun openCamera() = reportResult(deviceController.openCamera())
    fun openWifiSettings() = reportResult(deviceController.openWifiSettings())
    fun openBluetoothSettings() = reportResult(deviceController.openBluetoothSettings())
    fun openSystemSettings() = reportResult(deviceController.openSystemSettings())
    fun startQuickTimer(seconds: Int) = reportResult(deviceController.setSystemTimer(seconds))
    fun setAlarm(hour24: Int, minute: Int) = reportResult(deviceController.setSystemAlarm(hour24, minute))
    fun searchGoogle(query: String) = reportResult(deviceController.openGoogleSearch(query))
    fun prepareCall(number: String) = reportResult(deviceController.prepareDialer(number))
    fun prepareSms(number: String, body: String) = reportResult(deviceController.prepareSms(number, body))
    fun launchApp(app: InstalledAppInfo) = reportResult(deviceController.launchApp(app))

    fun clearStatus() {
        _statusMessage.value = null
    }

    private fun reportResult(result: DeviceActionResult) {
        _statusMessage.value = when (result) {
            is DeviceActionResult.Success -> result.message
            is DeviceActionResult.Error -> result.message
        }
    }
}
