package com.pockettechnician.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.pockettechnician.app.ui.PocketTechnicianApp
import com.pockettechnician.app.ui.theme.PocketTechnicianTheme

class MainActivity : ComponentActivity() {

    private val requestBluetooth = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Whether granted now or already held, (re)start the HID service so the
        // SDP record is published and bonded hosts can be listed/connected.
        hidManager().start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestBluetooth.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            ),
        )
        setContent {
            PocketTechnicianTheme {
                PocketTechnicianApp()
            }
        }
    }

    private fun hidManager() = (application as PocketTechnicianApplication).hidManager
}
