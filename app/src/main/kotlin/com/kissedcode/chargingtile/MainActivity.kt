package com.kissedcode.chargingtile

import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var snapshot by mutableStateOf<ChargingSnapshot?>(null)
    private var message by mutableStateOf<Int?>(null)
    private var unsubscribe: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeFailure(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) darkColorScheme(primary = Color(0xFF40C9A2))
                else lightColorScheme(primary = Color(0xFF006B54))
            ) {
                Scaffold { insets ->
                    Column(
                        Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.intro), style = MaterialTheme.typography.bodyLarge)
                        val current = snapshot
                        val mode = if (current?.supported == true) current.mode else ChargingMode.UNKNOWN
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.current_mode), style = MaterialTheme.typography.labelLarge)
                                Row {
                                    Icon(painterResource(mode.iconResource()), contentDescription = null, Modifier.size(36.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        if (current == null) stringResource(R.string.loading) else stringResource(mode.labelResource()),
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                }
                                Text(stringResource(R.string.tile_behavior))
                            }
                        }
                        if (current != null && !current.supported) {
                            Text(stringResource(R.string.unsupported), color = MaterialTheme.colorScheme.error)
                        }
                        message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                        current?.readError?.let { detail ->
                            Text(stringResource(R.string.read_blocked), color = MaterialTheme.colorScheme.error)
                            Card(Modifier.fillMaxWidth()) {
                                SelectionContainer {
                                    Text(
                                        detail,
                                        Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    )
                                }
                            }
                        }
                        Button(onClick = ::addTile, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.add_tile))
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(if (current?.permission == true) R.string.permission_granted else R.string.permission_missing))
                        Text(stringResource(R.string.install_note), style = MaterialTheme.typography.bodySmall)
                        Card(Modifier.fillMaxWidth()) {
                            SelectionContainer {
                                Text(
                                    stringResource(R.string.install_command),
                                    Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                )
                            }
                        }
                        if (current?.permission != true) {
                            Text(stringResource(R.string.adb_instructions))
                            Card(Modifier.fillMaxWidth()) {
                                SelectionContainer {
                                    Text(
                                        getString(R.string.adb_command, packageName),
                                        Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    )
                                }
                            }
                            OutlinedButton(onClick = ::copyCommand, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.copy_command))
                            }
                        }
                        TextButton(onClick = {
                            message = null
                            refresh()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.check_permission))
                        }
                        OutlinedButton(
                            onClick = { SettingsNavigator.open(this@MainActivity) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.open_settings))
                        }
                        Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        unsubscribe?.invoke()
        unsubscribe = chargingRepository.observe { refresh() }
        refresh()
    }

    override fun onPause() {
        unsubscribe?.invoke()
        unsubscribe = null
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeFailure(intent)
    }

    private fun refresh() {
        lifecycleScope.launch { snapshot = chargingRepository.snapshot() }
    }

    private fun consumeFailure(intent: Intent) {
        message = when (intent.getStringExtra(EXTRA_FAILURE)) {
            ToggleFailure.PERMISSION.name -> R.string.error_permission
            ToggleFailure.UNKNOWN.name -> R.string.error_unknown
            ToggleFailure.UNSUPPORTED.name -> R.string.unsupported
            ToggleFailure.WRITE_FAILED.name -> R.string.error_write
            else -> null
        }
        intent.removeExtra(EXTRA_FAILURE)
    }

    private fun copyCommand() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(getString(R.string.permission_title), getString(R.string.adb_command, packageName))
        )
    }

    private fun addTile() {
        try {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, ChargingTileService::class.java),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_limit),
                mainExecutor,
            ) { result ->
                val text = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_already_added
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> R.string.tile_not_added
                    else -> R.string.tile_add_failed
                }
                Toast.makeText(this, text, Toast.LENGTH_LONG).show()
            }
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.tile_add_failed, Toast.LENGTH_LONG).show()
        }
    }

    companion object { const val EXTRA_FAILURE = "charging_failure" }
}
