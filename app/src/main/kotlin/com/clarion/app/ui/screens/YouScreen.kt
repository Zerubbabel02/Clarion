package com.clarion.app.ui.screens

import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.clarion.app.core.AlertSoundPrefs
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.HoldPrefs
import com.clarion.app.core.NotificationHelper
import com.clarion.app.core.Profile
import com.clarion.app.core.ProfileCache
import com.clarion.app.core.ThemeMode
import com.clarion.app.core.supabase
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The You tab (mockup): photo and @username on top, then grouped settings rows. */
@Composable
fun YouScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSignedOut: () -> Unit,
    holdSeconds: Float,
    onHoldSecondsChange: (Float) -> Unit,
    sub: String?,
    onSub: (String?) -> Unit,
    dockBottom: Dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = tk()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var lodge by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var alertSoundUri by remember { mutableStateOf(AlertSoundPrefs.get(context)) }

    LaunchedEffect(Unit) {
        try { profile = ClarionRepository.getMyProfile(); lodge = ClarionRepository.getMyLodgeName() } catch (_: Exception) {}
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2600); toast = null } }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            try {
                // Downscale to 512 px JPEG before upload so avatars load fast on slow connections.
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val original = android.graphics.BitmapFactory.decodeStream(input)
                        val scale = minOf(1f, 512f / maxOf(original.width, original.height))
                        val resized = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1), (original.height * scale).toInt().coerceAtLeast(1), true) else original
                        java.io.ByteArrayOutputStream().use { out -> resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
                    }
                }
                val userId = ClarionRepository.currentUserId()
                if (bytes != null && userId != null) {
                    val path = "$userId/avatar.jpg"
                    supabase.storage.from("avatars").upload(path, bytes) { upsert = true }
                    val publicUrl = supabase.storage.from("avatars").publicUrl(path) + "?v=" + System.currentTimeMillis()
                    ClarionRepository.updateAvatarUrl(publicUrl)
                    profile = profile?.copy(avatarUrl = publicUrl) ?: ClarionRepository.getMyProfile()
                    toast = "Photo updated"
                }
            } catch (_: Exception) {
                toast = "That photo could not be uploaded. Try another one."
            } finally { uploading = false }
        }
    }

    if (sub != null) {
        Column(Modifier.fillMaxSize().background(t.bg).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton("back", small = true, glassy = false) { onSub(null) }
                Spacer(Modifier.width(12.dp))
                T(when (sub) { "people" -> "Who gets my Flare"; "hold" -> "Hold to send"; "tone" -> "Flare ringtone"; else -> "Night quiet" }, 20.sp, t.ink, FontWeight.ExtraBold)
            }
            key(sub) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(top = 12.dp, bottom = dockBottom + 30.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (sub) {
                        "people" -> PeoplePage()
                        "hold" -> {
                            T("How long you hold the Flare button before a Flare sends. A longer hold makes it harder to send one by mistake.", 13.sp, t.muted, lineHeight = 19.sp)
                            Seg(HoldPrefs.CHOICES.map { fmtSec(it) }, HoldPrefs.CHOICES.indexOf(holdSeconds).coerceAtLeast(0)) { onHoldSecondsChange(HoldPrefs.CHOICES[it]) }
                            HoldPill(holdSeconds, onComplete = { toast = "That is the full hold. Nothing was sent." }, test = true)
                        }
                        "tone" -> RingtonePage(alertSoundUri) { alertSoundUri = it }
                        else -> QuietPage(profile) { on -> profile = profile?.copy(nightMuteEnabled = on) }
                    }
                }
            }
            Toast(toast, Modifier.align(Alignment.CenterHorizontally).offset(y = (-60).dp))
        }
        return
    }

    Box(Modifier.fillMaxSize().background(t.bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(top = 24.dp, bottom = dockBottom + 30.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            T("You", 28.sp, t.ink, FontWeight.ExtraBold)
            val p = profile
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(92.dp).clickable { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(t.primarySoft), contentAlignment = Alignment.Center) {
                        when {
                            uploading -> CircularProgressIndicator(Modifier.size(28.dp), color = t.primary, strokeWidth = 2.dp)
                            p?.avatarUrl != null -> AsyncImage(p.avatarUrl, "Your photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else -> T((p?.displayName?.firstOrNull() ?: '?').uppercase(), 34.sp, t.primary, FontWeight.Bold)
                        }
                    }
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(34.dp).background(t.bg, CircleShape).padding(3.dp).background(t.primary, CircleShape), contentAlignment = Alignment.Center) {
                        CIcon("camera", 16.dp, t.onPrimary, 2.2f)
                    }
                }
                Spacer(Modifier.height(4.dp))
                T(p?.displayName ?: "…", 22.sp, t.ink, FontWeight.ExtraBold)
                T(p?.username?.let { "@$it" } ?: "No username yet", 14.5.sp, t.primary, mono = true)
                T(p?.username?.let { "Friends can search @$it to add you." } ?: "Pick a username so friends can find you.", 13.sp, t.muted)
                T(if (p?.avatarUrl != null) "Change photo" else "Upload a photo", 13.sp, t.primary, FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(horizontal = 12.dp, vertical = 10.dp))
            }

            Group("Account") {
                SRow("user", "Name", p?.displayName ?: "Add") { editing = "name" }
                LineDivider()
                SRow("at", "Username", p?.username?.let { "@$it" } ?: "Add") { editing = "username" }
                LineDivider()
                SRow("phone", "Phone number", p?.phoneNumber?.takeIf { it.isNotBlank() } ?: "Add") { editing = "phone" }
                LineDivider()
                SRow("home", "Lodge", lodge ?: "Add") { editing = "lodge" }
            }
            Group("Flare") {
                SRow("people", "Who gets my Flare", if (p?.broadcastMode == "circle") "Trusted circle" else "Within ${formatKm(p?.radiusKm ?: 1.2)}") { onSub("people") }
                LineDivider()
                SRow("timer", "Hold to send", fmtSec(holdSeconds)) { onSub("hold") }
            }
            Group("Alerts") {
                SRow("music", "Flare ringtone", soundName(context, alertSoundUri)) { onSub("tone") }
                LineDivider()
                SRow("moon", "Night quiet", if (p?.nightMuteEnabled != false) "On" else "Off") { onSub("quiet") }
            }
            Group("Appearance") {
                Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBox("sun"); Spacer(Modifier.width(12.dp))
                    T("Theme", 15.sp, t.ink, FontWeight.Bold, modifier = Modifier.weight(1f))
                    Seg(listOf("Light", "Dark"), if (themeMode == ThemeMode.LIGHT) 0 else 1, Modifier.width(156.dp), small = true) {
                        onThemeModeChange(if (it == 0) ThemeMode.LIGHT else ThemeMode.DARK)
                    }
                }
            }
            GhostButton("Sign out") {
                scope.launch { try { ClarionRepository.signOut() } catch (_: Exception) {}; ProfileCache.clear(context); onSignedOut() }
            }
        }
        Toast(toast, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp))
    }

    editing?.let { field ->
        EditFieldModal(field, profile, lodge, onDismiss = { editing = null }) { msg, updated, newLodge ->
            editing = null
            if (updated != null) profile = updated
            if (newLodge != null) lodge = newLodge
            toast = msg
        }
    }
}

@Composable
private fun Group(title: String, rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { SectionLabel(title); ListCard { rows() } }
}

@Composable
private fun IconBox(icon: String) {
    val t = tk()
    Box(Modifier.size(34.dp).background(t.primarySoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { CIcon(icon, 18.dp, t.primary) }
}

@Composable
private fun SRow(icon: String, label: String, value: String, onClick: () -> Unit) {
    val t = tk()
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable { onClick() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(icon)
        Spacer(Modifier.width(12.dp))
        T(label, 15.sp, t.ink, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
        T(value, 13.5.sp, t.muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp).fillMaxWidth(0.45f), )
        CIcon("chevR", 18.dp, t.muted, 2.2f)
    }
}

private fun soundName(context: android.content.Context, uri: String?): String = try {
    val u = uri?.let { Uri.parse(it) } ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
    if (uri == null) "Phone default" else u?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) } ?: "Custom sound"
} catch (_: Exception) { "Phone default" }

/* ---------------- Who gets my Flare ---------------- */

@Composable
private fun PeoplePage() {
    val t = tk()
    val scope = rememberCoroutineScope()
    var trusted by remember { mutableStateOf(false) }
    var km by remember { mutableStateOf(1.2f) }
    var trustedList by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var excluded by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var adding by remember { mutableStateOf<String?>(null) }
    suspend fun reload() {
        try { trustedList = ClarionRepository.listTrustedMembers(); excluded = ClarionRepository.listExcluded() } catch (_: Exception) {}
    }
    LaunchedEffect(Unit) {
        try { ClarionRepository.getMyProfile()?.let { trusted = it.broadcastMode == "circle"; km = it.radiusKm.toFloat().coerceIn(MIN_KM, MAX_KM) } } catch (_: Exception) {}
        reload()
    }
    Seg(listOf("By distance", "Trusted circle"), if (trusted) 1 else 0) { i ->
        trusted = i == 1
        scope.launch { try { ClarionRepository.updateBroadcastMode(if (trusted) "circle" else "radius") } catch (_: Exception) {} }
    }
    if (!trusted) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Broadcast radius")
            val parts = formatKm(km.toDouble()).split(" ")
            Row(verticalAlignment = Alignment.Bottom) {
                T(parts[0], 44.sp, t.ink, FontWeight.Medium, mono = true)
                Spacer(Modifier.width(8.dp))
                T(parts.getOrElse(1) { "" }, 17.sp, t.muted, modifier = Modifier.padding(bottom = 6.dp))
            }
            RadiusBar(km, onChange = { km = it }, onDone = { scope.launch { try { ClarionRepository.updateRadiusKm(km.toDouble()) } catch (_: Exception) {} } })
            T("Everyone with Clarion inside this range gets your Flare.", 13.sp, t.muted)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Trusted circle · ${trustedList.size}")
            PeopleRows(trustedList, "Nobody in your trusted circle yet.") { p ->
                scope.launch { try { ClarionRepository.removeTrustedMember(p.id); reload() } catch (_: Exception) {} }
            }
            GhostButton("Add someone", icon = "plus") { adding = "trusted" }
            T("Only these people get your Flare, wherever they are.", 13.sp, t.muted)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Exclude list")
        PeopleRows(excluded, "Nobody is excluded.") { p ->
            scope.launch { try { ClarionRepository.removeExcluded(p.id); reload() } catch (_: Exception) {} }
        }
        GhostButton("Exclude someone", icon = "plus") { adding = "excluded" }
    }
    adding?.let { kind ->
        AddPersonModal(
            title = if (kind == "trusted") "Add to trusted circle" else "Exclude someone",
            verb = if (kind == "trusted") "Add" else "Exclude",
            already = (if (kind == "trusted") trustedList else excluded).map { it.id },
            onDismiss = { adding = null },
        ) { p ->
            adding = null
            scope.launch {
                try { if (kind == "trusted") ClarionRepository.addTrustedMember(p.id) else ClarionRepository.addExcluded(p.id); reload() } catch (_: Exception) {}
            }
        }
    }
}

@Composable
private fun PeopleRows(people: List<Profile>, empty: String, onRemove: (Profile) -> Unit) {
    val t = tk()
    if (people.isEmpty()) { EmptyBox(empty); return }
    ListCard {
        people.forEachIndexed { i, p ->
            if (i > 0) LineDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.displayName, p.avatarUrl, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T(p.displayName, 15.sp, t.ink, FontWeight.Bold, maxLines = 1)
                    if (p.username != null) T("@${p.username}", 12.5.sp, t.muted, maxLines = 1)
                }
                MiniButton("Remove") { onRemove(p) }
            }
        }
    }
}

/* ---------------- Flare ringtone ---------------- */

@Composable
private fun RingtonePage(current: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val t = tk()
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { mutableStateOf<Ringtone?>(null) }
    DisposableEffect(Unit) { onDispose { player.value?.stop() } }
    fun apply(uri: String?) {
        AlertSoundPrefs.set(context, uri); onChange(uri)
        // The channel's sound is frozen once created — rebuilding it is the only way the pick takes effect.
        NotificationHelper.ensureChannel(context)
    }
    fun play(id: String, uri: Uri?) {
        player.value?.stop()
        if (playing == id) { playing = null; return }
        val u = uri ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE) ?: return
        player.value = try { RingtoneManager.getRingtone(context, u)?.also { it.play() } } catch (_: Exception) { null }
        playing = if (player.value != null) id else null
    }
    LaunchedEffect(playing) { if (playing != null) { delay(4000); player.value?.stop(); playing = null } }

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        @Suppress("DEPRECATION") val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (picked != null) apply(picked.toString())
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        try { context.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        apply(uri.toString())
    }
    val isCustom = current != null && !current.contains("media/") && !current.startsWith("content://settings")
    val isPhone = current != null && !isCustom

    T("This sound plays when a Flare reaches you, and keeps ringing until you respond.", 13.sp, t.muted, lineHeight = 19.sp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Clarion SOS tones")
        T("Coming soon. Your own Clarion SOS tones will appear here.", 12.5.sp, t.muted,
            modifier = Modifier.fillMaxWidth().border(1.dp, t.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("From your phone")
        ListCard {
            ToneRow("Phone default", current == null, playing == "default", onSelect = { apply(null) }) { play("default", null) }
            if (isPhone) { LineDivider(); ToneRow(soundName(context, current), true, playing == "phone", onSelect = {}) { play("phone", Uri.parse(current)) } }
        }
        GhostButton("Choose from phone ringtones", icon = "phone") {
            systemPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Flare ringtone")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current?.let { Uri.parse(it) })
            })
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Custom")
        if (isCustom) ListCard { ToneRow(soundName(context, current), true, playing == "custom", sub = "Your sound file", onSelect = {}) { play("custom", Uri.parse(current)) } }
        else EmptyBox("No custom ringtone yet.")
        GhostButton("Add a custom ringtone", icon = "plus") { filePicker.launch(arrayOf("audio/*")) }
        T("Pick any sound file on your phone, like an MP3 you recorded.", 13.sp, t.muted)
    }
}

@Composable
private fun ToneRow(name: String, on: Boolean, playing: Boolean, sub: String? = null, onSelect: () -> Unit, onPlay: () -> Unit) {
    val t = tk()
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 44.dp).clickable { onSelect() }, verticalAlignment = Alignment.CenterVertically) {
            Radio(on); Spacer(Modifier.width(12.dp))
            Column { T(name, 15.sp, t.ink, FontWeight.Bold, maxLines = 1); if (sub != null) T(sub, 12.5.sp, t.muted) }
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).then(if (playing) Modifier.background(t.primary) else Modifier.border(1.5.dp, t.line, RoundedCornerShape(50)))
                .clickable { onPlay() }.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CIcon(if (playing) "stop" else "play", 14.dp, if (playing) t.onPrimary else t.ink, 2.4f)
            Spacer(Modifier.width(6.dp))
            T(if (playing) "Stop" else "Play", 12.5.sp, if (playing) t.onPrimary else t.ink, FontWeight.Bold)
        }
    }
}

/* ---------------- Night quiet ---------------- */

@Composable
private fun QuietPage(profile: Profile?, onChange: (Boolean) -> Unit) {
    val t = tk()
    val scope = rememberCoroutineScope()
    var on by remember(profile) { mutableStateOf(profile?.nightMuteEnabled ?: true) }
    ListCard {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Mute my lodge after dark", 15.sp, t.ink, FontWeight.Bold)
                T("Same-building alerts stay quiet at night", 12.5.sp, t.muted)
            }
            Toggle(on) {
                on = it; onChange(it)
                scope.launch { try { ClarionRepository.updateNightMuteEnabled(it) } catch (_: Exception) {} }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Why this exists")
        T("At night, people in your own building usually already know when something happens there. Muting stops your phone ringing for those, while Flares from further away and from your trusted circle still come through.", 13.sp, t.muted, lineHeight = 19.sp)
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val t = tk()
    Box(
        Modifier.width(52.dp).height(32.dp).clip(RoundedCornerShape(50)).background(if (on) t.primary else t.line).clickable { onChange(!on) }.padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(26.dp).background(Color.White, CircleShape)) }
}

/* ---------------- edit name / username / phone / lodge ---------------- */

@Composable
private fun EditFieldModal(field: String, profile: Profile?, lodge: String?, onDismiss: () -> Unit, onSaved: (String, Profile?, String?) -> Unit) {
    val t = tk()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var value by remember {
        mutableStateOf(when (field) { "name" -> profile?.displayName; "username" -> profile?.username; "phone" -> profile?.phoneNumber; else -> lodge }.orEmpty())
    }
    var err by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val title = when (field) { "name" -> "Your name"; "username" -> "Your username"; "phone" -> "Phone number"; else -> "Lodge, building, or shop" }
    val help = when (field) {
        "name" -> "Shown to people when you send a Flare."
        "username" -> "Friends search this to add you. Use letters, numbers and underscores."
        "phone" -> "Friends can also find you by phone number. It is only used for search."
        else -> "Everyone who enters the same name shares your night-time close circle."
    }
    BottomModal(onDismiss) {
        T(title, 20.sp, t.ink, FontWeight.ExtraBold)
        T(title.substringBefore(","), 13.sp, t.muted, FontWeight.SemiBold)
        ClarionField(value, { value = it; err = null }, when (field) { "name" -> "Your full name"; "username" -> "yourname"; "phone" -> "0803 123 4567"; else -> "e.g. Ridgeway Lodge, Block C" }, at = field == "username")
        if (err != null) T(err!!, 13.5.sp, t.flareText, FontWeight.SemiBold) else T(help, 13.sp, t.muted, lineHeight = 18.sp)
        PrimaryButton(if (saving) "Saving…" else "Save") {
            if (saving) return@PrimaryButton
            val v = value.trim()
            saving = true
            scope.launch {
                try {
                    when (field) {
                        "name" -> {
                            if (v.isEmpty()) { err = "Type your name." } else {
                                ClarionRepository.updateDisplayName(v); ProfileCache.setDisplayName(context, v)
                                onSaved("Name saved", profile?.copy(displayName = v), null)
                            }
                        }
                        "username" -> {
                            val u = v.removePrefix("@").filter { it.isLetterOrDigit() || it == '_' }.lowercase()
                            if (u.length < 3) err = "Use at least 3 letters or numbers."
                            else { val msg = ClarionRepository.updateUsername(u); if (msg != null) err = msg else onSaved("Saved. Friends can now find you as @$u", profile?.copy(username = u), null) }
                        }
                        "phone" -> {
                            val msg = ClarionRepository.updatePhone(v)
                            if (msg != null) err = msg else onSaved(if (v.isEmpty()) "Phone number removed" else "Phone number saved", profile?.copy(phoneNumber = v.ifEmpty { null }), null)
                        }
                        else -> {
                            ClarionRepository.saveProfile(profile?.displayName ?: "", v.ifEmpty { null })
                            onSaved("Lodge saved", null, v.ifEmpty { null })
                        }
                    }
                } catch (_: Exception) { err = "Couldn't save. Check your connection and try again." } finally { saving = false }
            }
        }
        T("Cancel", 14.sp, t.muted, FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onDismiss() }.padding(12.dp))
    }
}
