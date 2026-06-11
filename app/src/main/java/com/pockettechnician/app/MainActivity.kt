package com.pockettechnician.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pockettechnician.app.ui.PocketTechnicianApp
import com.pockettechnician.app.ui.theme.PocketTechnicianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketTechnicianTheme {
                PocketTechnicianApp()
            }
        }
    }
}
