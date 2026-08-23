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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.Profile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CirclesSettingsScreen(onOpenNightMode: () -> Unit) {
    val scope = rememberCoroutineScope()

    var radiusKm by remember { mutableFloatStateOf(1.2f) }
    var isRadiusMode by remember { mutableStateOf(true) }
    var trustedMembers by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var excludedMembers by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<AddTarget?>(null) }

    suspend fun refresh() {
        val profile = ClarionRepository.getMyProfile()
        if (profile != null) {
            radiusKm = profile.radiusKm.toFloat()
            isRadiusMode = profile.broadcastMode != "circle"
        }
        trustedMembers = ClarionRepository.listTrustedMembers()
        excludedMembers = ClarionRepository.listExcluded()
        loaded = true
    }

    LaunchedEffect(Unit) { refresh() }

    // Debounced save — write to Supabase 500ms after the user stops dragging, not on every pixel.
    LaunchedEffect(radiusKm, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(500)
        ClarionRepository.updateRadiusKm(radiusKm.toDouble())
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(modifier = Modifier.height(58.dp))
            Text("Circles & Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(4.dp),
            ) {
                SegmentTab(
                    label = "By Radius",
                    selected = isRadiusMode,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        isRadiusMode = true
                        scope.launch { ClarionRepository.updateBroadcastMode("radius") }
                    },
                )
                SegmentTab(
                    label = "Trusted Circle",
                    selected = !isRadiusMode,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        isRadiusMode = false
                        scope.launch { ClarionRepository.updateBroadcastMode("circle") }
                    },
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (isRadiusMode) {
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
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                        .padding(6.dp),
                ) {
                    trustedMembers.forEach { p -> ContactRow(p) }
                    AddRow(label = "Add someone to your circle", onClick = { dialog = AddTarget.TRUSTED })
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "EXCLUDE LIST",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .padding(6.dp),
            ) {
                excludedMembers.forEach { p -> ContactRow(p, subtitleOverride = "Never receives your Flare") }
                AddRow(label = "Add someone to exclude", onClick = { dialog = AddTarget.EXCLUDED })
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .clickable { onOpenNightMode() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Night circle muting", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text("Same-building alerts stay quiet after 9pm", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
                Text("→", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
            }

            Spacer(modifier = Modifier.height(90.dp))
        }
    }

    dialog?.let { target ->
        AddByEmailDialog(
            title = if (target == AddTarget.TRUSTED) "Add to trusted circle" else "Add to exclude list",
            onDismiss = { dialog = null },
            onConfirm = { email ->
                scope.launch {
                    val found = ClarionRepository.findProfileByEmail(email)
                    if (found != null) {
                        if (target == AddTarget.TRUSTED) ClarionRepository.addTrustedMember(found.id) else ClarionRepository.addExcluded(found.id)
                        refresh()
                    }
                    dialog = null
                }
            },
        )
    }
}

private enum class AddTarget { TRUSTED, EXCLUDED }

@Composable
private fun SegmentTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                RoundedCornerShape(9.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun ContactRow(profile: Profile, subtitleOverride: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Text((profile.displayName.firstOrNull() ?: '?').uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(profile.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitleOverride ?: (profile.phoneNumber ?: ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 11.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}
