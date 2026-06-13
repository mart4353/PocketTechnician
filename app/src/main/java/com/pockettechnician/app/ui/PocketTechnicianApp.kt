package com.pockettechnician.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.pockettechnician.app.ui.screens.ChatScreen
import com.pockettechnician.app.ui.screens.ConversationsScreen
import com.pockettechnician.app.ui.screens.DashboardScreen
import com.pockettechnician.app.ui.screens.GalleryScreen
import com.pockettechnician.app.ui.screens.TakePhotoScreen
import com.pockettechnician.app.ui.screens.VoiceScreen

enum class TechnicianTab(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Filled.SpaceDashboard),
    Conversations("Conversations", Icons.Filled.Forum),
    Chat("Chat", Icons.AutoMirrored.Filled.Chat),
    TakePhoto("Take Photo", Icons.Filled.PhotoCamera),
    Gallery("Gallery", Icons.Filled.PhotoLibrary),
    Voice("Voice", Icons.Filled.Mic),
}

/**
 * App shell: NavigationSuiteScaffold renders the 6 tabs as a bottom bar on
 * compact (portrait phone) windows and as a navigation rail on expanded
 * (landscape / tablet) windows, giving the responsive layout for free.
 */
@Composable
fun PocketTechnicianApp() {
    var currentTab by rememberSaveable { mutableStateOf(TechnicianTab.Dashboard) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TechnicianTab.entries.forEach { tab ->
                item(
                    selected = tab == currentTab,
                    onClick = { currentTab = tab },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                )
            }
        },
    ) {
        when (currentTab) {
            TechnicianTab.Dashboard -> DashboardScreen()
            TechnicianTab.Conversations -> ConversationsScreen(
                onOpenChat = { currentTab = TechnicianTab.Chat },
            )
            TechnicianTab.Chat -> ChatScreen()
            TechnicianTab.TakePhoto -> TakePhotoScreen(onPhotoAttached = { currentTab = TechnicianTab.Chat })
            TechnicianTab.Gallery -> GalleryScreen(onPhotoAttached = { currentTab = TechnicianTab.Chat })
            TechnicianTab.Voice -> VoiceScreen(onSubmitted = { currentTab = TechnicianTab.Chat })
        }
    }
}
