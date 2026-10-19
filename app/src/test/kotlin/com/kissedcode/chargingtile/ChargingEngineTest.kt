package com.kissedcode.chargingtile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingEngineTest {
    private class FakeStore(limit: String? = "0", adaptive: String? = "0") : SettingsStore {
        val values = mutableMapOf(ChargingEngine.LIMIT_KEY to limit, ChargingEngine.ADAPTIVE_KEY to adaptive)
        val writes = mutableListOf<Pair<String, Int>>()
        var rejectKey: String? = null
        var throwOnWrite = false
        var ignoreWrites = false
        var failReads = false
        override fun read(key: String): String? {
            if (failReads) throw SecurityException()
            return values[key]
        }
        override fun write(key: String, value: Int): Boolean {
            writes += key to value
            if (throwOnWrite) throw SecurityException()
            if (key == rejectKey) return false
            if (!ignoreWrites) values[key] = value.toString()
            return true
        }
    }

    @Test fun readsAllModes() {
        assertEquals(ChargingMode.OFF, ChargingModeMapper.read("0", "0"))
        assertEquals(ChargingMode.ADAPTIVE, ChargingModeMapper.read("0", "1"))
        assertEquals(ChargingMode.LIMIT_80, ChargingModeMapper.read("1", "0"))
        assertEquals(ChargingMode.LIMIT_80, ChargingModeMapper.read("1", "1"))
        assertEquals(ChargingMode.LIMIT_80, ChargingModeMapper.read("1", null))
    }

    @Test fun missingAndMalformedAreNeverOff() {
        for (limit in listOf(null, "", "x", "2", "-1")) {
            assertEquals(ChargingMode.UNKNOWN, ChargingModeMapper.read(limit, "0"))
        }
        for (adaptive in listOf(null, "", "x", "2", "-1")) {
            assertEquals(ChargingMode.UNKNOWN, ChargingModeMapper.read("0", adaptive))
        }
    }

    @Test fun nextNeverSelectsAdaptive() {
        assertEquals(ChargingMode.OFF, ChargingModeMapper.next(ChargingMode.LIMIT_80))
        assertEquals(ChargingMode.LIMIT_80, ChargingModeMapper.next(ChargingMode.OFF))
        assertEquals(ChargingMode.LIMIT_80, ChargingModeMapper.next(ChargingMode.ADAPTIVE))
        assertEquals(null, ChargingModeMapper.next(ChargingMode.UNKNOWN))
    }

    @Test fun offToLimit() {
        val store = FakeStore()
        assertEquals(ToggleResult.Success(ChargingMode.LIMIT_80), ChargingEngine(store).toggle(true, true))
        assertEquals(listOf(ChargingEngine.ADAPTIVE_KEY to 0, ChargingEngine.LIMIT_KEY to 1), store.writes)
    }

    @Test fun limitToOffDisablesAdaptiveFirst() {
        val store = FakeStore("1", "1")
        assertEquals(ToggleResult.Success(ChargingMode.OFF), ChargingEngine(store).toggle(true, true))
        assertEquals(listOf(ChargingEngine.ADAPTIVE_KEY to 0, ChargingEngine.LIMIT_KEY to 0), store.writes)
    }

    @Test fun adaptiveToLimit() {
        val store = FakeStore("0", "1")
        assertEquals(ToggleResult.Success(ChargingMode.LIMIT_80), ChargingEngine(store).toggle(true, true))
    }

    @Test fun missingPermissionDoesNotWrite() {
        val store = FakeStore()
        assertEquals(ToggleFailure.PERMISSION, (ChargingEngine(store).toggle(false, true) as ToggleResult.Failure).reason)
        assertTrue(store.writes.isEmpty())
    }

    @Test fun unsupportedDoesNotWrite() {
        val store = FakeStore()
        assertEquals(ToggleFailure.UNSUPPORTED, (ChargingEngine(store).toggle(true, false) as ToggleResult.Failure).reason)
        assertTrue(store.writes.isEmpty())
    }

    @Test fun unknownDoesNotWrite() {
        val store = FakeStore(null, null)
        assertEquals(ToggleFailure.UNKNOWN, (ChargingEngine(store).toggle(true, true) as ToggleResult.Failure).reason)
        assertTrue(store.writes.isEmpty())
    }

    @Test fun firstWriteRejectedDoesNotWriteSecondKey() {
        val store = FakeStore("1", "1").apply { rejectKey = ChargingEngine.ADAPTIVE_KEY }
        assertEquals(ToggleResult.Failure(ToggleFailure.WRITE_FAILED, ChargingMode.LIMIT_80), ChargingEngine(store).toggle(true, true))
        assertEquals(1, store.writes.size)
    }

    @Test fun partialWriteReportsActualState() {
        val store = FakeStore("0", "1").apply { rejectKey = ChargingEngine.LIMIT_KEY }
        assertEquals(ToggleResult.Failure(ToggleFailure.WRITE_FAILED, ChargingMode.OFF), ChargingEngine(store).toggle(true, true))
        assertEquals(2, store.writes.size)
    }

    @Test fun silentlyIgnoredWriteIsNotSuccess() {
        val store = FakeStore().apply { ignoreWrites = true }
        assertEquals(ToggleResult.Failure(ToggleFailure.WRITE_FAILED, ChargingMode.OFF), ChargingEngine(store).toggle(true, true))
    }

    @Test fun permissionRevokedMidOperation() {
        val store = FakeStore().apply { throwOnWrite = true }
        assertEquals(ToggleFailure.PERMISSION, (ChargingEngine(store).toggle(true, true) as ToggleResult.Failure).reason)
    }

    @Test fun readFailureIsUnknown() {
        val store = FakeStore().apply { failReads = true }
        assertEquals(ChargingMode.UNKNOWN, ChargingEngine(store).current())
        assertTrue(ChargingEngine(store).toggle(true, true) is ToggleResult.Failure)
        assertTrue(store.writes.isEmpty())
    }
}
