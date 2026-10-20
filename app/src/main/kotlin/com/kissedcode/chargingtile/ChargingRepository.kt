package com.kissedcode.chargingtile

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ChargingSnapshot(
    val mode: ChargingMode,
    val permission: Boolean,
    val supported: Boolean,
    val readError: String? = null,
)

class ChargingRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val mutex = Mutex()
    private val engine = ChargingEngine(object : SettingsStore {
        override fun read(key: String): String? = Settings.Secure.getString(resolver, key)
        override fun write(key: String, value: Int) = Settings.Secure.putInt(resolver, key, value)
    })

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
        PackageManager.PERMISSION_GRANTED

    fun isSupported(): Boolean =
        Build.MANUFACTURER.equals("Google", ignoreCase = true) &&
            Build.MODEL.startsWith("Pixel") && SettingsNavigator.hasChargingSettings(context)

    suspend fun snapshot(): ChargingSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock { ChargingSnapshot(engine.current(), hasPermission(), isSupported(), engine.readError()) }
    }

    suspend fun toggle(): ToggleResult = withContext(Dispatchers.IO) {
        mutex.withLock { engine.toggle(hasPermission(), isSupported()) }
    }

    /** Only while the tile/activity is visible. Returns a matching unsubscribe function. */
    fun observe(onChange: () -> Unit): () -> Unit {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        try {
            resolver.registerContentObserver(Settings.Secure.getUriFor(ChargingEngine.LIMIT_KEY), false, observer)
            resolver.registerContentObserver(Settings.Secure.getUriFor(ChargingEngine.ADAPTIVE_KEY), false, observer)
        } catch (_: RuntimeException) {
            resolver.unregisterContentObserver(observer)
        }
        return { resolver.unregisterContentObserver(observer) }
    }
}

class ChargingApplication : Application() {
    val repository: ChargingRepository by lazy { ChargingRepository(this) }
}

val Context.chargingRepository: ChargingRepository
    get() = (applicationContext as ChargingApplication).repository
