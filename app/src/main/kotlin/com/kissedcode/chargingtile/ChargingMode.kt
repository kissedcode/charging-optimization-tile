package com.kissedcode.chargingtile

enum class ChargingMode { LIMIT_80, OFF, ADAPTIVE, UNKNOWN }

object ChargingModeMapper {
    fun read(limit: String?, adaptive: String?): ChargingMode = when (limit?.toIntOrNull()) {
        1 -> ChargingMode.LIMIT_80
        0 -> when (adaptive?.toIntOrNull()) {
            0 -> ChargingMode.OFF
            1 -> ChargingMode.ADAPTIVE
            else -> ChargingMode.UNKNOWN
        }
        else -> ChargingMode.UNKNOWN
    }

    fun next(current: ChargingMode): ChargingMode? = when (current) {
        ChargingMode.LIMIT_80 -> ChargingMode.OFF
        ChargingMode.OFF, ChargingMode.ADAPTIVE -> ChargingMode.LIMIT_80
        ChargingMode.UNKNOWN -> null
    }
}

interface SettingsStore {
    fun read(key: String): String?
    fun write(key: String, value: Int): Boolean
}

enum class ToggleFailure { PERMISSION, UNSUPPORTED, UNKNOWN, WRITE_FAILED }

sealed interface ToggleResult {
    data class Success(val mode: ChargingMode) : ToggleResult
    data class Failure(val reason: ToggleFailure, val mode: ChargingMode) : ToggleResult
}

/** Pure, synchronous engine. The repository serializes calls and runs I/O off the UI thread. */
class ChargingEngine(private val store: SettingsStore) {
    fun current(): ChargingMode = try {
        ChargingModeMapper.read(store.read(LIMIT_KEY), store.read(ADAPTIVE_KEY))
    } catch (_: RuntimeException) {
        ChargingMode.UNKNOWN
    }

    fun toggle(hasPermission: Boolean, supported: Boolean): ToggleResult {
        val current = current()
        if (!supported) return ToggleResult.Failure(ToggleFailure.UNSUPPORTED, current)
        if (!hasPermission) return ToggleResult.Failure(ToggleFailure.PERMISSION, current)
        val next = ChargingModeMapper.next(current)
            ?: return ToggleResult.Failure(ToggleFailure.UNKNOWN, current)
        return try {
            // Disable Adaptive first, so removing the limit never accidentally enables it.
            val adaptiveWritten = store.write(ADAPTIVE_KEY, 0)
            val limitWritten = adaptiveWritten &&
                store.write(LIMIT_KEY, if (next == ChargingMode.LIMIT_80) 1 else 0)
            val actual = current()
            if (adaptiveWritten && limitWritten && actual == next &&
                store.read(ADAPTIVE_KEY)?.toIntOrNull() == 0
            ) {
                ToggleResult.Success(actual)
            } else {
                ToggleResult.Failure(ToggleFailure.WRITE_FAILED, actual)
            }
        } catch (_: SecurityException) {
            ToggleResult.Failure(ToggleFailure.PERMISSION, current())
        } catch (_: RuntimeException) {
            ToggleResult.Failure(ToggleFailure.WRITE_FAILED, current())
        }
    }

    companion object {
        const val LIMIT_KEY = "charge_optimization_mode"
        const val ADAPTIVE_KEY = "adaptive_charging_enabled"
    }
}
