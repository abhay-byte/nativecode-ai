package com.ivarna.nativecode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Colors extracted from your config ---
val CardBackground = Color(0xFF1C1B1B) // surface-container-low
val BorderDim = Color(0xFF3E3E42) // border-dim
val TextOnBackground = Color(0xFFE5E2E1) // on-background
val TextOnSurfaceVariant = Color(0xFFD3C0D8) // on-surface-variant
val ErrorRed = Color(0xFFFFB4AB) // error
val SurfaceBright = Color(0xFF3A3939) // surface-bright
val PrimaryPurple = Color(0xFFE7B4FF) // primary
val ShadowGreen = Color(0xFF2D5942) // cyber brutalist shadow green

// --- Typography Styles ---
val SectionHeaderStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.2.sp
)

val CardTitleStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.8.sp
)

val ValueTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight.Bold
)

val SubValueStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    fontWeight = FontWeight.Normal
)

@Composable
fun SystemTelemetryCards(
    cpuPercentage: Int = 34,
    memPercentage: Int = 82,
    ramUsedMb: Long = 0,
    ramTotalMb: Long = 0,
    swapUsedMb: Long = 0,
    swapTotalMb: Long = 0,
    diskPercentage: Int = 0
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SYSTEM TELEMETRY",
                color = TextOnBackground,
                style = SectionHeaderStyle
            )
            // Pulse indicator dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(PrimaryPurple, CircleShape)
            )
        }

        // Edge-to-Edge Horizontal Scroll Cards Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // CPU USAGE Card
            CompactTelemetryCard(
                title = "CPU USAGE",
                mainValue = "$cpuPercentage%",
                subValue = if (cpuPercentage > 80) "[HIGH LOAD]" else "[NORMAL]",
                progress = cpuPercentage / 100f,
                progressColor = if (cpuPercentage > 80) ErrorRed else PrimaryPurple
            )

            // RAM ALLOCATED Card
            val ramUsedGb = if (ramTotalMb > 0) String.format(java.util.Locale.US, "%.1fGB", ramUsedMb / 1024.0) else "$memPercentage%"
            val ramSub = if (ramTotalMb > 0) "${ramUsedMb}M / ${ramTotalMb}M" else "RAM Allocated"
            CompactTelemetryCard(
                title = "RAM ALLOCATED",
                mainValue = ramUsedGb,
                subValue = ramSub,
                progress = memPercentage / 100f,
                progressColor = if (memPercentage > 80) ErrorRed else PrimaryPurple
            )

            // SWAP USAGE Card
            val swapPercentage = if (swapTotalMb > 0) ((swapUsedMb * 100) / swapTotalMb).toInt().coerceIn(0, 100) else 0
            val swapUsedGb = if (swapTotalMb > 0) String.format(java.util.Locale.US, "%.1fGB", swapUsedMb / 1024.0) else "0GB"
            val swapSub = if (swapTotalMb > 0) "${swapUsedMb}M / ${swapTotalMb}M" else "No Swap"
            CompactTelemetryCard(
                title = "SWAP USAGE",
                mainValue = swapUsedGb,
                subValue = swapSub,
                progress = swapPercentage / 100f,
                progressColor = PrimaryPurple
            )

            // TOTAL STORAGE Card
            CompactTelemetryCard(
                title = "TOTAL STORAGE",
                mainValue = "$diskPercentage%",
                subValue = "Disk Allocated",
                progress = diskPercentage / 100f,
                progressColor = PrimaryPurple
            )
        }
    }
}

@Composable
fun CompactTelemetryCard(
    title: String,
    mainValue: String,
    subValue: String,
    progress: Float,
    progressColor: Color
) {
    Box(
        modifier = Modifier
            .width(165.dp)
            .padding(end = 6.dp, bottom = 6.dp)
    ) {
        // Cyber Brutalist Shadow Layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = 6.dp.toPx()
                    translationY = 6.dp.toPx()
                }
                .background(ShadowGreen, RoundedCornerShape(8.dp))
        )

        // Main Card Container
        Column(
            modifier = Modifier
                .width(165.dp)
                .background(CardBackground, RoundedCornerShape(8.dp))
                .border(1.dp, BorderDim, RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = TextOnSurfaceVariant,
                style = CardTitleStyle,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = mainValue,
                color = TextOnBackground,
                style = ValueTextStyle
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subValue,
                color = TextOnSurfaceVariant.copy(alpha = 0.7f),
                style = SubValueStyle,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Linear Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(SurfaceBright, RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(progressColor, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
