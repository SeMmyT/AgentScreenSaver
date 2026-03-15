package com.claudescreensaver.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudescreensaver.ui.theme.ClaudeGray

@Composable
fun SubAgentList(
    agents: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    if (agents.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = "Sub-agents",
            style = MaterialTheme.typography.labelSmall,
            color = ClaudeGray,
        )
        Spacer(Modifier.height(4.dp))
        agents.forEach { (type, status) ->
            Text(
                text = "$type: $status",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
