package com.clarion.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.clarion.app.core.ClarionRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Flare = Color(0xFFE8562E)

@Composable
fun NightModeScreen(onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ClarionRepository.getMyProfile()?.let { enabled = it.nightMuteEnabled }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
            Spacer(modifier = Modifier.height(58.dp))
            ScreenHeader(title = "Night Circle Muting", onBack = onBack)
            Spacer(modifier = Modifier.height(6.dp))

            NightCircleDiagram(modifier = Modifier.fillMaxWidth().height(210.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                LegendDot(color = MaterialTheme.colorScheme.primaryContainer, label = "Your building — muted")
                Spacer(modifier = Modifier.width(20.dp))
                LegendDot(color = Flare, label = "Everyone else — full alert")
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mute my lodge after dark", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text("On by default for every close circle", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        enabled = checked
                        scope.launch { ClarionRepository.updateNightMuteEnabled(checked) }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp))
                    .padding(18.dp),
            ) {
                Text(
                    "Why this exists",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "During quiet hours (9pm–6am), a Flare from inside your own lodge won't ring your " +
                        "lodge-mates' phones out loud — a room full of ringing phones can tip off an intruder " +
                        "to more people, or more targets. People outside your building still get the full alert " +
                        "immediately. You can always open Clarion yourself to check the map.",
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun NightCircleDiagram(modifier: Modifier = Modifier) {
    val indigo = MaterialTheme.colorScheme.primary
    val indigoSoft = MaterialTheme.colorScheme.primaryContainer
    val card = MaterialTheme.colorScheme.surface
    val background = MaterialTheme.colorScheme.background

    val transition = rememberInfiniteTransition(label = "nightPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "pulseScale",
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "glowAlpha",
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.height * 0.44f
        val innerR = outerR * 0.55f

        // Gentle breathing glow behind the inner circle — the "quiet" of the muted circle.
        drawCircle(
            color = indigo.copy(alpha = glowAlpha),
            radius = innerR * pulse * 1.25f,
            center = Offset(cx, cy),
        )

        // Outer dashed ring — the wider community, full alert.
        drawCircle(
            color = Flare,
            radius = outerR,
            center = Offset(cx, cy),
            style = Stroke(
                width = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 12f)),
            ),
        )

        // Inner filled circle — your building, muted.
        drawCircle(color = indigoSoft, radius = innerR, center = Offset(cx, cy))
        drawCircle(
            color = indigo,
            radius = innerR,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5f),
        )

        // A small building glyph in the center.
        val bW = innerR * 0.85f
        val bH = bW * 0.9f
        drawRect(
            color = card,
            topLeft = Offset(cx - bW / 2f, cy - bH / 2f),
            size = androidx.compose.ui.geometry.Size(bW, bH),
        )
        drawRect(
            color = indigo,
            topLeft = Offset(cx - bW / 2f, cy - bH / 2f),
            size = androidx.compose.ui.geometry.Size(bW, bH),
            style = Stroke(width = 2.5f),
        )
        val winSize = bW * 0.22f
        val winGap = bW * 0.12f
        drawRect(color = indigo, topLeft = Offset(cx - winGap - winSize, cy - bH * 0.28f), size = androidx.compose.ui.geometry.Size(winSize, winSize))
        drawRect(color = indigo, topLeft = Offset(cx + winGap, cy - bH * 0.28f), size = androidx.compose.ui.geometry.Size(winSize, winSize))
        drawRect(color = indigo, topLeft = Offset(cx - winSize / 2f, cy + bH * 0.02f), size = androidx.compose.ui.geometry.Size(winSize, bH * 0.28f))

        // Crescent moon, top right of the ring.
        val moonR = outerR * 0.16f
        val moonCenter = Offset(cx + outerR * 0.62f, cy - outerR * 0.72f)
        drawCircle(color = indigo, radius = moonR, center = moonCenter)
        drawCircle(color = background, radius = moonR * 0.85f, center = moonCenter + Offset(moonR * 0.45f, -moonR * 0.25f))

        // A muted "sound off" mark at the bottom — a small crossed speaker hint.
        val soundY = cy + outerR * 0.78f
        drawCircle(color = Flare, radius = 3f, center = Offset(cx - outerR * 0.18f, soundY))
        drawCircle(color = Flare, radius = 3f, center = Offset(cx + outerR * 0.18f, soundY))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(9.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}
