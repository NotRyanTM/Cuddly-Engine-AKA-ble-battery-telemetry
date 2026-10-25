package com.example.batterytelemetry

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.batterytelemetry.ui.theme.BatteryTelemetryTheme

class MainActivity : ComponentActivity() {

    // NetworkTelemetrySource retries every few seconds, so once the user allows this,
    // the next attempt connects. Nothing else needs to happen in the callback.
    private val requestLocalNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askForLocalNetworkAccess()
        enableEdgeToEdge()
        setContent {
            BatteryTelemetryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TelemetryRoute(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    /** Android 17 (API 37) needs this runtime permission to reach the laptop at 10.0.2.2. */
    private fun askForLocalNetworkAccess() {
        if (Build.VERSION.SDK_INT < 37) return
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestLocalNetwork.launch(permission)
        }
    }
}
