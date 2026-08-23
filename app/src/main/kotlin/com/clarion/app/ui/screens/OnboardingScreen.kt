package com.clarion.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarion.app.core.ClarionRepository
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onDone: () -> Unit, onSignOut: () -> Unit = {}) {
    var step by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf("") }
    var circleName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp)) {
            Spacer(modifier = Modifier.height(58.dp))

            Text(
                "Not you? Sign out",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.clickable {
                    scope.launch {
                        ClarionRepository.signOut()
                        onSignOut()
                    }
                },
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(2) { i ->
                    Spacer(modifier = Modifier.width(6.dp))
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            if (step == 0) {
                Text("Tell us who you are", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Your circle is the people who share your building or block.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(30.dp))
                Text("Your name", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text("Lodge, building, or shop", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = circleName,
                    onValueChange = { circleName = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Ridgeway Lodge, Block C") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Everyone who enters the same name shares your night-time close circle automatically.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                error?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(it, color = Color(0xFFE8562E), fontSize = 12.5.sp)
                }
            } else {
                Text("Two permissions we need", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(10.dp))
                Text("Nothing works without these — here's exactly why.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(24.dp))
                PermissionCard(
                    title = "Location",
                    body = "So a Flare carries your exact spot, and others can be routed straight to you.",
                    onGrant = {
                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                )
                Spacer(modifier = Modifier.height(14.dp))
                PermissionCard(
                    title = "Notifications",
                    body = "So a Flare can ring your phone even when Clarion isn't open.",
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (saving) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = !saving) {
                        if (step == 0) {
                            if (displayName.isBlank()) {
                                error = "Enter your name to continue."
                                return@clickable
                            }
                            error = null
                            step = 1
                        } else {
                            saving = true
                            scope.launch {
                                try {
                                    ClarionRepository.saveProfile(displayName.trim(), circleName.trim())
                                    onDone()
                                } catch (e: Exception) {
                                    error = e.message ?: "Couldn't save your profile. Try again."
                                    step = 0
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                    .padding(vertical = 16.dp)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        if (step == 0) "Continue" else "Finish Setup",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .clickable { onGrant() }
            .padding(18.dp),
    ) {
        Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(4.dp))
        Text(body, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Tap to allow", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
