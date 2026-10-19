package com.kissedcode.chargingtile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

object SettingsNavigator {
    const val CHARGING_ACTION =
        "com.google.android.settings.intelligence.action.CHARGING_OPTIMIZATION"

    private fun chargingIntent(context: Context): Intent? {
        val intent = Intent(CHARGING_ACTION)
        val matches = context.packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_DEFAULT_ONLY
        )
        val activity = matches.firstOrNull {
            it.activityInfo.exported &&
                it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        }?.activityInfo ?: return null
        return intent.setClassName(activity.packageName, activity.name)
    }

    fun hasChargingSettings(context: Context) = try {
        chargingIntent(context) != null
    } catch (_: RuntimeException) {
        false
    }

    fun open(context: Context) {
        val direct = try { chargingIntent(context) } catch (_: RuntimeException) { null }
        val candidates = listOfNotNull(
            direct,
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            try {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                if (intent !== direct) {
                    Toast.makeText(context, R.string.settings_fallback, Toast.LENGTH_LONG).show()
                }
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next public system destination, not Battery Saver.
            } catch (_: SecurityException) {
                // Some firmware exposes an activity but does not let other apps open it.
            }
        }
        Toast.makeText(context, R.string.settings_failed, Toast.LENGTH_LONG).show()
    }
}

class ChargingSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsNavigator.open(this)
        finish()
    }
}
