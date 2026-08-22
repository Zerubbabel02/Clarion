package com.clarion.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarion.app.ui.theme.Flare
import com.clarion.app.ui.theme.FlareGlow
import com.clarion.app.ui.theme.Safe

@Composable
fun HomeScreen(
    onSendFlare: () -> Unit = {},
    onShareLocation: () -> Unit = {},
    onCirclesSettings: () -> Unit = {},
    onSendTestAlert: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(56.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Clarion",
                    fontWeight = FontWeight.Medium,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable { onCirclesSettings() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚙", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Good evening",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Box(modifier = Modifier.size(7.dp).background(Safe, CircleShape))
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "All quiet in your circle right now",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(212.dp)
                        .background(Brush.radialGradient(listOf(FlareGlow, Flare)), CircleShape)
                        .clickable { onSendFlare() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Send a Flare",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "One tap alerts everyone nearby who can help. Nothing else needed.",
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Send test alert (demo)",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onSendTestAlert() },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickAccessCard(
                    title = "Share Location",
                    subtitle = "With loved ones, any time",
                    modifier = Modifier.weight(1f),
                    onClick = onShareLocation,
                )
                QuickAccessCard(
                    title = "Circles & Settings",
                    subtitle = "1.2km radius · 14 nearby",
                    modifier = Modifier.weight(1f),
                    onClick = onCirclesSettings,
                )
            }
        }
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
    }
}
