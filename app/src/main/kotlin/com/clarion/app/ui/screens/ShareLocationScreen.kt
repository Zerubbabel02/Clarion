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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.launch

private val Warm = Color(0xFFE87A46)
private val WarmSoft = Color(0xFFFBEDE3)

@Composable
fun ShareLocationScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sharedWith by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var selectedHours by remember { mutableIntStateOf(2) } // null-equivalent handled via -1 = until stopped
    var showAddDialog by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        sharedWith = ClarionRepository.listActiveShares()
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
            Spacer(modifier = Modifier.height(58.dp))
            ScreenHeader(title = "Share My Location", onBack = onBack)
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarmSoft, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(44.dp).background(Warm, CircleShape))
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    "Separate from Flare alerts — sharing here never rings anyone.",
                    fontSize = 12.5.sp,
                    color = Color(0xFF7A3F1C),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "SHARING WITH",
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
                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Warm, strokeWidth = 2.dp)
                    }
                } else if (sharedWith.isEmpty()) {
                    Text(
                        "Not sharing with anyone right now",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(14.dp),
                    )
                } else {
                    sharedWith.forEach { p ->
                        ShareRow(p) {
                            scope.launch {
                                ClarionRepository.stopSharingWith(p.id)
                                refresh()
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "SHARE FOR",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DurationChip("1 hour", selected = selectedHours == 1, modifier = Modifier.weight(1f)) { selectedHours = 1 }
                DurationChip("2 hours", selected = selectedHours == 2, modifier = Modifier.weight(1f)) { selectedHours = 2 }
                DurationChip("Until I stop", selected = selectedHours == -1, modifier = Modifier.weight(1f)) { selectedHours = -1 }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Warm, RoundedCornerShape(16.dp))
                    .clickable { showAddDialog = true }
                    .padding(vertical = 16.dp)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Share With Someone New", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            }
        }
    }

    if (showAddDialog) {
        AddByEmailDialog(
            title = "Share your location",
            onDismiss = { showAddDialog = false },
            onConfirm = { email ->
                scope.launch {
                    val found = ClarionRepository.findProfileByEmail(email)
                    if (found != null) {
                        ClarionRepository.shareLocationWith(found.id, if (selectedHours == -1) null else selectedHours)
                        refresh()
                    }
                    showAddDialog = false
                }
            },
        )
    }
}

@Composable
private fun DurationChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (selected) WarmSoft else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color(0xFF7A3F1C) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ShareRow(profile: Profile, onStop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(42.dp).background(Warm, CircleShape))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text("Live sharing", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
        Text(
            "Stop",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE8562E),
            modifier = Modifier.clickable { onStop() },
        )
    }
}
