package com.clarion.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.Profile
import com.clarion.app.core.ThemeMode
import com.clarion.app.core.ThemePrefs
import com.clarion.app.core.supabase
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var uploading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        profile = ClarionRepository.getMyProfile()
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val userId = ClarionRepository.currentUserId()
                if (bytes != null && userId != null) {
                    val path = "$userId/avatar.jpg"
                    supabase.storage.from("avatars").upload(path, bytes) { upsert = true }
                    val publicUrl = supabase.storage.from("avatars").publicUrl(path)
                    ClarionRepository.updateAvatarUrl(publicUrl)
                    profile = profile?.copy(avatarUrl = publicUrl) ?: ClarionRepository.getMyProfile()
                }
            } catch (_: Exception) {
                // Avatar upload failing shouldn't block the rest of the profile screen.
            } finally {
                uploading = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
        ) {
            Spacer(modifier = Modifier.height(58.dp))
            Text("Profile", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(24.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .clickable { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) {
                    val avatarUrl = profile?.avatarUrl
                    when {
                        uploading -> CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        avatarUrl != null -> AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Your photo",
                            modifier = Modifier.size(96.dp).background(Color.Transparent, CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                        else -> Text(
                            (profile?.displayName?.firstOrNull() ?: '?').uppercase(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Tap to change photo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    profile?.displayName ?: "…",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Shown to others when you send a Flare",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
            Text("APPEARANCE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(4.dp),
            ) {
                ThemeOption("System", ThemeMode.SYSTEM, themeMode, Modifier.weight(1f), onThemeModeChange)
                ThemeOption("Light", ThemeMode.LIGHT, themeMode, Modifier.weight(1f), onThemeModeChange)
                ThemeOption("Dark", ThemeMode.DARK, themeMode, Modifier.weight(1f), onThemeModeChange)
            }

            Spacer(modifier = Modifier.height(30.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .clickable {
                        scope.launch {
                            ClarionRepository.signOut()
                            onSignedOut()
                        }
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Sign Out", color = Color(0xFFE8562E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ThemeOption(label: String, value: ThemeMode, current: ThemeMode, modifier: Modifier, onSelect: (ThemeMode) -> Unit) {
    val selected = value == current
    Box(
        modifier = modifier
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable { onSelect(value) }
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
