package com.claudescreensaver.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.data.models.Skin
import com.claudescreensaver.ui.components.GhostMascot
import com.claudescreensaver.ui.theme.*

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onTryDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Breathing animation for Clawd
    val infiniteTransition = rememberInfiniteTransition(label = "onboarding")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .background(ClaudeBgDark)
            .padding(32.dp),
    ) {
        Spacer(Modifier.weight(1f))

        // Ghost mascot - large, centered, breathing
        GhostMascot(
            state = AgentState.IDLE,
            skin = Skin.DEFAULT,
            modifier = Modifier
                .size(120.dp)
                .scale(breatheScale),
        )

        Spacer(Modifier.height(24.dp))

        // Title
        Text(
            text = "Agent ScreenSaver",
            style = MaterialTheme.typography.headlineMedium,
            color = ClaudeAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
        )

        Spacer(Modifier.height(12.dp))

        // Description
        Text(
            text = "Monitor your AI coding agents\nfrom your charging stand",
            style = MaterialTheme.typography.bodyLarge,
            color = ClaudeGray,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )

        Spacer(Modifier.height(36.dp))

        // Setup steps
        val steps = listOf(
            "Start the bridge server on your dev machine",
            "Enter the bridge URL or discover via network",
            "Set as your screen saver in Display settings",
        )

        steps.forEachIndexed { index, step ->
            SetupStep(
                number = index + 1,
                text = step,
            )
            if (index < steps.lastIndex) {
                Spacer(Modifier.height(16.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        // Get Started button
        Button(
            onClick = onGetStarted,
            colors = ButtonDefaults.buttonColors(containerColor = ClaudeAccent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = "Get Started",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Try Demo Mode button (outlined, secondary)
        OutlinedButton(
            onClick = onTryDemo,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ClaudeAccent,
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(ClaudeAccent.copy(alpha = 0.5f)),
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = "Try Demo Mode",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SetupStep(
    number: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Numbered circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(1.5.dp, ClaudeAccent, CircleShape),
        ) {
            Text(
                text = number.toString(),
                color = ClaudeAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(16.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = ClaudeTextLight,
            fontSize = 15.sp,
        )
    }
}
