package com.clarion.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF0F1420)
private val NightCard = Color(0xFF1B2233)
private val NightTextSoft = Color(0xFFB7C0D6)
private val Flare = Color(0xFFE8562E)
private val FlareGlow = Color(0xFFF08B5A)

@Composable
fun IncomingFlareScreen(
    senderName: String,
    distance: String,
    location: String,
    onOpenMap: () -> Unit,
    onDismiss: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "ring")
    val ringScale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "ringScale",
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "ringAlpha",
    )

    Surface(
        modifier = Modifier.fillMaxSize().clickable { onOpenMap() },
        color = Night,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "FLARE ALERT",
                color = FlareGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.6.sp,
            )
            Spacer(modifier = Modifier.height(28.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(ringScale)
                        .alpha(ringAlpha)
                        .background(Flare, shape = CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Brush.radialGradient(listOf(FlareGlow, Flare)), CircleShape),
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "$senderName sent a Flare",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$distance away · $location",
                color = NightTextSoft,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))
            Box(
                modifier = Modifier
                    .background(Flare, shape = RoundedCornerShape(16.dp))
                    .clickable { onOpenMap() }
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                Text("Open Map", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Dismiss",
                color = NightTextSoft,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onDismiss() },
            )
        }
    }
}
