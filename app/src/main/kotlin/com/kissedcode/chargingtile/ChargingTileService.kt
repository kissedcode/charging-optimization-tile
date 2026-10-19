package com.kissedcode.chargingtile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ChargingTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var unsubscribe: (() -> Unit)? = null
    private var listening = false
    private var switching = false
    private var revision = 0

    override fun onStartListening() {
        super.onStartListening()
        listening = true
        unsubscribe?.invoke()
        unsubscribe = chargingRepository.observe { refresh() }
        refresh()
    }

    override fun onStopListening() {
        listening = false
        revision++
        unsubscribe?.invoke()
        unsubscribe = null
        super.onStopListening()
    }

    private fun refresh() {
        val requested = ++revision
        scope.launch {
            val snapshot = chargingRepository.snapshot()
            if (listening && requested == revision) {
                render(if (snapshot.supported) snapshot.mode else ChargingMode.UNKNOWN)
            }
        }
    }

    private fun render(mode: ChargingMode) {
        qsTile?.apply {
            label = getString(R.string.tile_label)
            subtitle = getString(mode.labelResource())
            stateDescription = subtitle
            contentDescription = "$label: $subtitle"
            icon = Icon.createWithResource(this@ChargingTileService, mode.iconResource())
            state = when (mode) {
                ChargingMode.LIMIT_80, ChargingMode.ADAPTIVE -> Tile.STATE_ACTIVE
                ChargingMode.OFF, ChargingMode.UNKNOWN -> Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (switching) return
        if (isLocked) {
            unlockAndRun { switchMode() }
        } else {
            switchMode()
        }
    }

    private fun switchMode() {
        if (switching) return
        switching = true
        scope.launch {
            try {
                val result = chargingRepository.toggle()
                refresh()
                if (result is ToggleResult.Failure) openSetup(result.reason)
            } finally {
                switching = false
            }
        }
    }

    private fun openSetup(reason: ToggleFailure) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_FAILURE, reason.name)
        val pending = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pending)
    }

    override fun onDestroy() {
        unsubscribe?.invoke()
        scope.cancel()
        super.onDestroy()
    }
}

fun ChargingMode.labelResource() = when (this) {
    ChargingMode.LIMIT_80 -> R.string.mode_limit
    ChargingMode.OFF -> R.string.mode_off
    ChargingMode.ADAPTIVE -> R.string.mode_adaptive
    ChargingMode.UNKNOWN -> R.string.mode_unknown
}

fun ChargingMode.iconResource() = when (this) {
    ChargingMode.LIMIT_80 -> R.drawable.ic_limit
    ChargingMode.OFF -> R.drawable.ic_off
    ChargingMode.ADAPTIVE -> R.drawable.ic_adaptive
    ChargingMode.UNKNOWN -> R.drawable.ic_unknown
}
