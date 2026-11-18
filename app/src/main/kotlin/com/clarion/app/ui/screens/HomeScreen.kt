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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.ProfileCache
import com.clarion.app.ui.theme.Amber
import com.clarion.app.ui.theme.Flare
import com.clarion.app.ui.theme.FlareGlow
import com.clarion.app.ui.theme.Safe
import com.clarion.app.ui.theme.Terracotta

private fun timeOfDayGreeting(): String {
    // java.time works natively at minSdk 26, no desugaring needed. LocalTime.now() reads the
    // device's current timezone, so this is correct wherever the phone travels — no need to
    // capture a timezone at registration and have it go stale.
    return when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
}

@Composable
fun HomeScreen(
    onSendFlare: () -> Unit = {},
    onShareLocation: () -> Unit = {},
    onFlareTrustedCircle: () -> Unit = {},
) {
    val context = LocalContext.current
    // Seeded from the local cache so the greeting shows the real name on the very first
    // frame — no flash of the name-less greeting while the network fetch below is in flight.
    var displayName by remember { mutableStateOf(ProfileCache.getDisplayName(context)) }
    val greeting = remember { timeOfDayGreeting() }

    LaunchedEffect(Unit) {
        try {
            val fresh = ClarionRepository.getMyProfile()?.displayName
            if (fresh != null) {
                displayName = fresh
                ProfileCache.setDisplayName(context, fresh)
            }
        } catch (_: Exception) {
            // A network hiccup here just means the cached/last-known name stays on screen —
            // it must never crash the app (a real, confirmed crash on this exact pattern).
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(56.dp))

            Text(
                text = "Clarion",
                fontWeight = FontWeight.Medium,
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (displayName != null) "$greeting, $displayName" else greeting,
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
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickAccessCard(
                    title = "Share Location",
                    subtitle = "With loved ones, any time",
                    accentColor = Amber,
                    modifier = Modifier.weight(1f),
                    onClick = onShareLocation,
                )
                QuickAccessCard(
                    title = "Alert Circle",
                    subtitle = "Just your trusted contacts",
                    accentColor = Terracotta,
                    modifier = Modifier.weight(1f),
                    onClick = onFlareTrustedCircle,
                )
            }
        }
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    subtitle: String,
    accentColor: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(
                accentColor?.copy(alpha = 0.14f) ?: MaterialTheme.colorScheme.surface,
                RoundedCornerShape(18.dp),
            )
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = accentColor ?: MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
    }
}
