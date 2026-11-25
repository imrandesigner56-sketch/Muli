package com.osaf.jarvis.utils

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimePerformanceSnapshot(
    val startupMs: Long = 0L,
    val lastWakeToReadyMs: Long = 0L,
    val lastAiFirstTokenMs: Long = 0L,
    val lastTtsFirstAudioMs: Long = 0L,
    val usedRamMb: Long = 0L,
    val maxRamMb: Long = 0L,
    val cacheHits: Int = 0
)

object PerformanceMetrics {
    private val processStartMs = SystemClock.elapsedRealtime()
    private val _snapshot = MutableStateFlow(captureMemory(RuntimePerformanceSnapshot()))
    val snapshot: StateFlow<RuntimePerformanceSnapshot> = _snapshot.asStateFlow()

    fun markAppInitialized() {
        val elapsed = (SystemClock.elapsedRealtime() - processStartMs).coerceAtLeast(1L)
        update { it.copy(startupMs = elapsed) }
    }

    fun recordWakeToReady(durationMs: Long) {
        update { it.copy(lastWakeToReadyMs = durationMs.coerceAtLeast(0L)) }
    }

    fun recordAiFirstToken(durationMs: Long) {
        update { it.copy(lastAiFirstTokenMs = durationMs.coerceAtLeast(0L)) }
    }

    fun recordTtsFirstAudio(durationMs: Long) {
        update { it.copy(lastTtsFirstAudioMs = durationMs.coerceAtLeast(0L)) }
    }

    fun recordCacheHit() {
        update { it.copy(cacheHits = it.cacheHits + 1) }
    }

    fun refreshMemory() {
        update { it }
    }

    private inline fun update(transform: (RuntimePerformanceSnapshot) -> RuntimePerformanceSnapshot) {
        _snapshot.value = captureMemory(transform(_snapshot.value))
    }

    private fun captureMemory(base: RuntimePerformanceSnapshot): RuntimePerformanceSnapshot {
        val rt = Runtime.getRuntime()
        val usedMb = ((rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)).coerceAtLeast(1L)
        val maxMb = (rt.maxMemory() / (1024L * 1024L)).coerceAtLeast(usedMb)
        return base.copy(usedRamMb = usedMb, maxRamMb = maxMb)
    }
}
