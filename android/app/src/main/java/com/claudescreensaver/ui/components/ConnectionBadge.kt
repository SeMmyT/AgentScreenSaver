package com.claudescreensaver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.claudescreensaver.data.network.ConnectionState
import com.claudescreensaver.ui.theme.*

@Composable
fun ConnectionBadge(
    state: ConnectionState,
    instanceName: String,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when (state) {
        ConnectionState.CONNECTED -> StatusRunning to "Connected"
        ConnectionState.CONNECTING -> StatusCaution to "Connecting..."
        ConnectionState.ERROR -> StatusCritical to "Error"
        ConnectionState.DISCONNECTED -> StatusDisabled to "Disconnected"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (instanceName.isNotEmpty()) "$instanceName — $label" else label,
            style = MaterialTheme.typography.labelSmall,
            color = ClaudeGray,
        )
    }
}
