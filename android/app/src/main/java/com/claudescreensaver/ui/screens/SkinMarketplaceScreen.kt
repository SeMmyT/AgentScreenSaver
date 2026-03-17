package com.claudescreensaver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.data.models.Skin
import com.claudescreensaver.data.network.SkinListItem
import com.claudescreensaver.data.skins.SkinEngine
import com.claudescreensaver.ui.components.GhostMascot
import com.claudescreensaver.ui.theme.*

/**
 * Marketplace screen: browse, install, and activate community skins.
 * Free users can browse but install is gated behind pro.
 */
@Composable
fun SkinMarketplaceScreen(
    skinEngine: SkinEngine,
    remoteSkins: List<SkinListItem>,
    activeSkinId: String,
    onFetchSkin: (String, (String?) -> Unit) -> Unit,
    onUploadSkin: (String, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = FontFamily.Monospace
    val installedSkins by skinEngine.availableSkins.collectAsState()
    val installedIds = installedSkins.map { it.id }.toSet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClaudeBgDark)
            .padding(16.dp),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "< Back",
                fontFamily = mono,
                fontSize = 12.sp,
                color = ClaudeGray,
                modifier = Modifier.clickable { onBack() },
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Skin Marketplace",
                fontFamily = mono,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ClaudeAccent,
                modifier = Modifier.weight(1f),
            )
            if (activeSkinId != "ghost") {
                var uploading by remember { mutableStateOf(false) }
                val activeSkin = installedSkins.find { it.id == activeSkinId }
                if (activeSkin != null) {
                    Button(
                        onClick = {
                            uploading = true
                            onUploadSkin(activeSkin.toJson()) { uploading = false }
                        },
                        enabled = !uploading,
                        colors = ButtonDefaults.buttonColors(containerColor = ClaudeAccentDeep),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (uploading) "..." else "Share",
                            fontFamily = mono,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Installed skins section
        Text(
            text = "INSTALLED",
            fontFamily = mono,
            fontSize = 11.sp,
            color = ClaudeGray,
        )
        Spacer(Modifier.height(8.dp))

        installedSkins.forEach { skin ->
            SkinCard(
                skinId = skin.id,
                name = skin.name,
                description = skin.description,
                author = skin.author,
                isActive = skin.id == activeSkinId,
                isInstalled = true,
                previewSkin = skin,
                onActivate = { skinEngine.setActiveSkin(skin.id) },
                onInstall = {},
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Community skins section
        if (remoteSkins.isNotEmpty()) {
            Text(
                text = "COMMUNITY",
                fontFamily = mono,
                fontSize = 11.sp,
                color = ClaudeGray,
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(remoteSkins.filter { it.id !in installedIds }) { item ->
                    var installing by remember { mutableStateOf(false) }

                    SkinCard(
                        skinId = item.id,
                        name = item.name,
                        description = item.description,
                        author = item.author,
                        isActive = false,
                        isInstalled = false,
                        previewSkin = null,
                        onActivate = {},
                        onInstall = {
                            installing = true
                            onFetchSkin(item.id) { json ->
                                if (json != null) {
                                    try {
                                        val skin = Skin.fromJson(json)
                                        skinEngine.installSkin(skin)
                                        skinEngine.setActiveSkin(skin.id)
                                    } catch (_: Exception) {}
                                }
                                installing = false
                            }
                        },
                        installing = installing,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
            Text(
                text = "No community skins available.\nUpload skins via the bridge API.",
                fontFamily = mono,
                fontSize = 12.sp,
                color = ClaudeGray,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SkinCard(
    skinId: String,
    name: String,
    description: String,
    author: String,
    isActive: Boolean,
    isInstalled: Boolean,
    previewSkin: Skin?,
    onActivate: () -> Unit,
    onInstall: () -> Unit,
    installing: Boolean = false,
) {
    val mono = FontFamily.Monospace
    val borderColor = if (isActive) ClaudeAccent else Color(0xFF2A2A2A)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .padding(12.dp),
    ) {
        // Preview mascot (if skin data available)
        if (previewSkin != null) {
            GhostMascot(
                state = AgentState.IDLE,
                skin = previewSkin,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(12.dp))
        }

        // Skin info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    fontFamily = mono,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClaudeTextLight,
                )
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "ACTIVE",
                        fontFamily = mono,
                        fontSize = 9.sp,
                        color = ClaudeAccent,
                    )
                }
            }
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    color = ClaudeGray,
                    maxLines = 1,
                )
            }
            Text(
                text = "by $author",
                fontFamily = mono,
                fontSize = 10.sp,
                color = ClaudeGray.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.width(8.dp))

        // Action button
        when {
            isActive -> {
                // Already active — no action needed
            }
            isInstalled -> {
                Button(
                    onClick = onActivate,
                    colors = ButtonDefaults.buttonColors(containerColor = ClaudeAccentDeep),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("USE", fontFamily = mono, fontSize = 11.sp)
                }
            }
            installing -> {
                Text(
                    text = "...",
                    fontFamily = mono,
                    fontSize = 12.sp,
                    color = ClaudeGray,
                )
            }
            else -> {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = ClaudeAccent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "INSTALL",
                        fontFamily = mono,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
