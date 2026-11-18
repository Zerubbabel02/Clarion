package com.clarion.app.ui.screens

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.clarion.app.core.AlertSoundPrefs
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.NotificationHelper
import com.clarion.app.core.Profile
import com.clarion.app.core.ProfileCache
import com.clarion.app.core.ThemeMode
import com.clarion.app.core.ThemePrefs
import com.clarion.app.core.supabase
import com.clarion.app.ui.theme.Dusk
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenNightMode: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var alertSoundUri by remember { mutableStateOf(AlertSoundPrefs.get(context)) }

    LaunchedEffect(Unit) {
        try {
            profile = ClarionRepository.getMyProfile()
        } catch (_: Exception) {
            // A network hiccup must never crash the app — the screen just stays on its
            // loading placeholders and the user can back out and back in to retry.
        }
    }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        AlertSoundPrefs.set(context, picked?.toString())
        alertSoundUri = picked?.toString()
        // The channel's sound is frozen once created — this rebuild is the only way the
        // pick actually takes effect, rather than silently sticking with the old one.
        NotificationHelper.ensureChannel(context)
    }

    fun currentSoundName(): String {
        val uri = alertSoundUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
        return uri?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) } ?: "Phone default"
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            try {
                // The real reason avatars were slow to load everywhere they appear (Profile,
                // every incoming Flare alert): this was uploading the raw picked file
                // untouched — a modern phone photo straight from the camera roll is several
                // MB, and every screen that shows the avatar re-downloads that full file over
                // whatever connection the viewer has, which matters a lot for this app's
                // rural/low-bandwidth users. Downscale and re-compress before upload instead —
                // a 512px JPEG is plenty for an avatar shown at a few dozen dp.
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val original = android.graphics.BitmapFactory.decodeStream(input)
                        val maxDim = 512
                        val scale = minOf(1f, maxDim.toFloat() / maxOf(original.width, original.height))
                        val resized = if (scale < 1f) {
                            android.graphics.Bitmap.createScaledBitmap(
                                original,
                                (original.width * scale).toInt().coerceAtLeast(1),
                                (original.height * scale).toInt().coerceAtLeast(1),
                                true,
                            )
                        } else {
                            original
                        }
                        java.io.ByteArrayOutputStream().use { out ->
                            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                            out.toByteArray()
                        }
                    }
                }
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
                .verticalScroll(rememberScrollState())
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
                Spacer(modifier = Modifier.height(8.dp))

                var editingName by remember { mutableStateOf(false) }
                var nameField by remember { mutableStateOf("") }
                var nameSaving by remember { mutableStateOf(false) }

                if (editingName) {
                    OutlinedTextField(
                        value = nameField,
                        onValueChange = { nameField = it },
                        singleLine = true,
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                .clickable(enabled = !nameSaving) {
                                    val trimmed = nameField.trim()
                                    if (trimmed.isBlank()) return@clickable
                                    nameSaving = true
                                    scope.launch {
                                        try {
                                            ClarionRepository.updateDisplayName(trimmed)
                                            profile = profile?.copy(displayName = trimmed)
                                            ProfileCache.setDisplayName(context, trimmed)
                                            editingName = false
                                        } finally {
                                            nameSaving = false
                                        }
                                    }
                                }
                                .padding(vertical = 11.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (nameSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                                .clickable(enabled = !nameSaving) { editingName = false }
                                .padding(vertical = 11.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile?.displayName ?: "…",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit name",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    nameField = profile?.displayName.orEmpty()
                                    editingName = true
                                },
                        )
                    }
                    Text(
                        "Shown to others when you send a Flare",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
            Text("USERNAME", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .padding(16.dp),
            ) {
                EditableIdentityRow(
                    label = "Username",
                    displayValue = profile?.username.orEmpty(),
                    prefix = "@",
                    isPrimary = true,
                    helperText = "No two people can share one — this is how others find you to add.",
                    normalize = { it.filter { c -> c.isLetterOrDigit() || c == '_' }.lowercase() },
                    onSave = { value -> ClarionRepository.updateUsername(value).also { if (it == null) profile = ClarionRepository.getMyProfile() } },
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
            Text("SAFETY", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Dusk.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                    .clickable { onOpenNightMode() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Night circle muting", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Dusk)
                    Text(
                        "Same-building alerts stay quiet after 9pm",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
                Text("→", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Dusk)
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
            Text("ALERT SOUND", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .clickable {
                        val currentUri = alertSoundUri?.let { Uri.parse(it) }
                            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Flare alert sound")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
                        }
                        soundPicker.launch(intent)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Flare ringtone", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(currentSoundName(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
                }
                Text("Change", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(30.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .clickable {
                        scope.launch {
                            ClarionRepository.signOut()
                            ProfileCache.clear(context)
                            onSignedOut()
                        }
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Sign Out", color = Color(0xFFE8562E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(60.dp))
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

/**
 * Edit-in-place, not a persistent form: tap "Edit" to open a field + Save/Cancel, tap Save to
 * commit it — and the row visibly collapsing back to the new value IS the confirmation it
 * actually saved, rather than a Save button that just sits there with no feedback either way.
 */
@Composable
private fun EditableIdentityRow(
    label: String,
    displayValue: String,
    isPrimary: Boolean,
    helperText: String,
    prefix: String? = null,
    normalize: (String) -> String = { it },
    onSave: suspend (String) -> String?,
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(displayValue) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf(false) }

    if (editing) {
        Column {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { fieldValue = normalize(it); error = null },
                singleLine = true,
                label = { Text(label) },
                prefix = prefix?.let { p -> { Text(p) } },
                isError = error != null,
                supportingText = {
                    Text(
                        error ?: helperText,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        .clickable(enabled = !saving) {
                            val trimmed = fieldValue.trim()
                            if (trimmed.isBlank()) {
                                error = "Can't be empty"
                                return@clickable
                            }
                            saving = true
                            scope.launch {
                                val result = onSave(trimmed)
                                saving = false
                                error = result
                                if (result == null) {
                                    editing = false
                                    justSaved = true
                                }
                            }
                        }
                        .padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                        .clickable(enabled = !saving) {
                            editing = false
                            fieldValue = displayValue
                            error = null
                        }
                        .padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (displayValue.isBlank()) "Add a $label".lowercase().replaceFirstChar { it.uppercase() } else (prefix ?: "") + displayValue,
                    fontSize = if (isPrimary) 16.sp else 13.sp,
                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
                    color = if (displayValue.isBlank()) {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    } else if (isPrimary) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    },
                )
                Text(
                    if (justSaved) "Saved" else helperText,
                    fontSize = 11.sp,
                    color = if (justSaved) com.clarion.app.ui.theme.Safe else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    fieldValue = displayValue
                    error = null
                    justSaved = false
                    editing = true
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit $label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Edit",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
