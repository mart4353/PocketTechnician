package com.pockettechnician.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun TakePhotoScreen() {
    ScreenContainer(title = "Take Photo") {
        // CameraX preview will replace this placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Camera preview",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = { /* capture via CameraX and attach to the active conversation */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Text("  Take Picture", style = MaterialTheme.typography.titleMedium)
        }

        Text(
            "Photos are attached to the current conversation at full resolution " +
                "(Photo Resolution: MAX).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
