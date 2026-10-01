package com.helmet.guard

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.helmet.guard.service.HelmetGuardService
import com.helmet.guard.ui.GuardApp
import com.helmet.guard.ui.theme.HelmetGuardTheme

class MainActivity : ComponentActivity() {
    private val permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val companionChooser = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HelmetGuardTheme {
                GuardApp(
                    graph = appGraph,
                    requestCorePermissions = ::requestCorePermissions,
                    startMonitoring = { startGuardService(HelmetGuardService.ACTION_START) },
                    stopMonitoring = { startGuardService(HelmetGuardService.ACTION_STOP) },
                    associateCompanion = {
                        appGraph.companionPairing.request(
                            { sender -> companionChooser.launch(IntentSenderRequest.Builder(sender).build()) },
                            { }
                        )
                    },
                    runServiceAction = { action, configure ->
                        val intent = Intent(this, HelmetGuardService::class.java).setAction(action).also(configure)
                        ContextCompat.startForegroundService(this, intent)
                    }
                )
            }
        }
    }

    private fun requestCorePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.distinct().toTypedArray()
        permissionsLauncher.launch(permissions)
    }

    private fun startGuardService(action: String) {
        val intent = Intent(this, HelmetGuardService::class.java).setAction(action)
        ContextCompat.startForegroundService(this, intent)
    }
}
