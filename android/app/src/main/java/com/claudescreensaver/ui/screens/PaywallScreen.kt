package com.claudescreensaver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import com.claudescreensaver.data.ProStatus
import com.claudescreensaver.ui.theme.*

@Composable
fun PaywallScreen(
    proStatus: ProStatus,
    trialDaysRemaining: Int,
    products: List<ProductDetails>,
    onPurchase: (ProductDetails) -> Unit,
    onContinueFree: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .background(ClaudeBgDark)
            .padding(24.dp),
    ) {
        Text(
            text = "Agent ScreenSaver Pro",
            style = MaterialTheme.typography.headlineMedium,
            color = ClaudeAccent,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(12.dp))

        if (proStatus == ProStatus.TRIAL) {
            Text(
                text = "$trialDaysRemaining days left in trial",
                style = MaterialTheme.typography.titleMedium,
                color = StatusCaution,
            )
        } else {
            Text(
                text = "Trial expired",
                style = MaterialTheme.typography.titleMedium,
                color = ClaudeGray,
            )
        }

        Spacer(Modifier.height(24.dp))

        // Pro features
        val features = listOf(
            "4-pane session grid",
            "Reply to sessions from your phone",
            "Ghost skin marketplace",
            "Community skin packs",
            "Premium exclusive skins",
        )
        features.forEach { feature ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                Text("  +  ", color = StatusRunning, fontSize = 14.sp)
                Text(feature, color = ClaudeTextLight, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Purchase options
        products.sortedBy {
            when {
                it.productId == "monthly_pro" -> 0
                it.productId == "annual_pro" -> 1
                it.productId == "lifetime_pro" -> 2
                else -> 3
            }
        }.forEach { product ->
            val (label, price, badge) = when (product.productId) {
                "monthly_pro" -> Triple(
                    "Monthly",
                    product.subscriptionOfferDetails?.firstOrNull()
                        ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                        ?.formattedPrice ?: "$1.00",
                    null,
                )
                "annual_pro" -> Triple(
                    "Annual",
                    product.subscriptionOfferDetails?.firstOrNull()
                        ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                        ?.formattedPrice ?: "$5.75",
                    "Save 52%",
                )
                "lifetime_pro" -> Triple(
                    "Lifetime",
                    product.oneTimePurchaseOfferDetails?.formattedPrice ?: "$9.85",
                    "Best value",
                )
                else -> Triple(product.productId, "", null)
            }

            val isHighlighted = product.productId == "annual_pro"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isHighlighted) 2.dp else 1.dp,
                        color = if (isHighlighted) ClaudeAccent else ClaudeGray.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onPurchase(product) }
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(label, color = ClaudeTextLight, fontWeight = FontWeight.Medium)
                        if (badge != null) {
                            Text(badge, color = ClaudeAccent, fontSize = 12.sp)
                        }
                    }
                    Text(
                        text = price,
                        color = ClaudeTextLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
            }
        }

        // If products haven't loaded yet (no Play Store connection), show fallback prices
        if (products.isEmpty()) {
            listOf(
                Triple("Monthly", "$1.00/mo", false),
                Triple("Annual", "$5.75/yr -- Save 52%", true),
                Triple("Lifetime", "$9.85 -- Best value", false),
            ).forEach { (label, price, highlighted) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (highlighted) 2.dp else 1.dp,
                            color = if (highlighted) ClaudeAccent else ClaudeGray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, color = ClaudeTextLight)
                        Text(price, color = ClaudeGray)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Purchase options will be available when connected to Play Store",
                color = ClaudeGray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Continue with free tier
        TextButton(onClick = onContinueFree) {
            Text("Continue with free tier", color = ClaudeGray)
        }
    }
}
