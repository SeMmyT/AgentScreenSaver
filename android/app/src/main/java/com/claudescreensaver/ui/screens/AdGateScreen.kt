package com.claudescreensaver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudescreensaver.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Ad gate shown to free users before entering the screensaver.
 * Currently a placeholder countdown — will integrate AdMob interstitial later.
 */
@Composable
fun AdGateScreen(
    onAdComplete: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var countdown by remember { mutableIntStateOf(5) }

    // Auto-proceed after countdown
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        onAdComplete()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .background(ClaudeBgDark)
            .padding(32.dp),
    ) {
        Spacer(Modifier.weight(1f))

        // Placeholder for ad content
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(
                    color = ClaudeGray.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Ad Placeholder",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = ClaudeGray.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "AdMob interstitial goes here",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = ClaudeGray.copy(alpha = 0.3f),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Countdown
        Text(
            text = if (countdown > 0) "Entering screensaver in ${countdown}s..." else "Loading...",
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = ClaudeGray,
        )

        Spacer(Modifier.weight(1f))

        // Upgrade CTA
        TextButton(onClick = onUpgrade) {
            Text(
                text = "Remove ads — Go Pro",
                color = ClaudeAccent,
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (countdown > 0) {
            Text(
                text = "Skip in ${countdown}s",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = ClaudeGray.copy(alpha = 0.4f),
            )
        } else {
            TextButton(onClick = onAdComplete) {
                Text("Skip", color = ClaudeGray, fontSize = 12.sp)
            }
        }
    }
}
