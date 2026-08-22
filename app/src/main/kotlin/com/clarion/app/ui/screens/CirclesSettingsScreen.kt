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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CirclesSettingsScreen(onBack: () -> Unit, onOpenNightMode: () -> Unit) {
    var radiusKm by remember { mutableFloatStateOf(1.2f) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
            Spacer(modifier = Modifier.height(58.dp))
            ScreenHeader(title = "Circles & Settings", onBack = onBack)
            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .padding(20.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Broadcast radius", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "${"%.1f".format(radiusKm)} km",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = radiusKm,
                    onValueChange = { radiusKm = it },
                    valueRange = 0.3f..5f,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("300m", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
                    Text("5km", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This will be fine-tuned once we've walked the real distances in your area.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            SettingsRow(
                title = "Night circle muting",
                subtitle = "Same-building alerts stay quiet after 9pm",
                trailing = "ON",
                onClick = onOpenNightMode,
            )

            Spacer(modifier = Modifier.height(26.dp))
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Text("←", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun SettingsRow(title: String, subtitle: String, trailing: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(trailing, color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
