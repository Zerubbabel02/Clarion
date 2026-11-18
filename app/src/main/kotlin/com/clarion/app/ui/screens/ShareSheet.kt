package com.clarion.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.Profile
import com.clarion.app.core.SharedLocation
import com.clarion.app.ui.theme.Manrope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun scrimColor(): Color = if (tk().dark) Color(0x9E04060C) else Color(0x6B131A2B)

/** The Share tab: a pop-up over the current screen; the tab bar stays on top of it. */
@Composable
fun ShareSheet(
    myLoc: com.clarion.app.core.LatLng?,
    sharers: List<SharedLocation>,
    dockBottom: Dp,
    onDismiss: () -> Unit,
    onSeeFriend: (String) -> Unit,
) {
    val t = tk()
    val scope = rememberCoroutineScope()
    var sharingOut by remember { mutableStateOf<List<Profile>?>(null) }
    var shareFor by remember { mutableIntStateOf(1) }
    var adding by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    suspend fun reload() { sharingOut = try { ClarionRepository.listActiveShares() } catch (_: Exception) { emptyList() } }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(toast) { if (toast != null) { delay(2600); toast = null } }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(scrimColor()).clickable(remember { MutableInteractionSource() }, null) { onDismiss() })
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 110.dp).background(t.bg, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 18.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Share my location", 20.sp, t.ink, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                RoundButton("x", small = true, glassy = false) { onDismiss() }
            }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(top = 6.dp, bottom = dockBottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Sharing with you")
                    if (sharers.isEmpty()) EmptyBox("Nobody is sharing their location with you right now.")
                    else {
                        ListCard {
                            sharers.forEachIndexed { i, s ->
                                if (i > 0) LineDivider()
                                val dist = if (myLoc != null && s.lat != null && s.lng != null) formatKm(distanceKm(myLoc.lat, myLoc.lng, s.lat, s.lng)) + " away · " else ""
                                Row(Modifier.fillMaxWidth().clickable { onSeeFriend(s.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(s.displayName, s.avatarUrl, 36.dp, live = true)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        T(s.displayName, 15.sp, t.ink, FontWeight.Bold, maxLines = 1)
                                        T("Live · $dist${timeLeftLabel(s.expiresAt)}${if (s.expiresAt != null) " left" else ""}", 12.5.sp, t.muted, maxLines = 1)
                                    }
                                    CIcon("target", 20.dp, t.primary)
                                }
                            }
                        }
                        T("Tap someone to see the way from you to them on the map.", 13.sp, t.muted)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("You’re sharing with")
                    val out = sharingOut
                    when {
                        out == null -> T("Loading…", 13.sp, t.muted)
                        out.isEmpty() -> EmptyBox("Not sharing with anyone right now")
                        else -> ListCard {
                            out.forEachIndexed { i, p ->
                                if (i > 0) LineDivider()
                                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(p.displayName, p.avatarUrl, 36.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        T(p.displayName, 15.sp, t.ink, FontWeight.Bold, maxLines = 1)
                                        T("Live", 12.5.sp, t.muted)
                                    }
                                    MiniButton("Stop") {
                                        scope.launch {
                                            try { ClarionRepository.stopSharingWith(p.id); toast = "Stopped sharing with ${firstName(p.displayName)}"; reload() } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Share for")
                    Seg(listOf("1 hour", "2 hours", "Until I stop"), shareFor) { shareFor = it }
                    PrimaryButton("Share with someone new", icon = "share") { adding = true }
                }
                T("Separate from Flare alerts. Sharing here never rings anyone.", 13.sp, t.muted)
            }
        }
        Toast(toast, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp))
    }

    if (adding) {
        AddPersonModal(
            title = "Share location with", verb = "Share", already = sharingOut.orEmpty().map { it.id },
            onDismiss = { adding = false },
        ) { p ->
            adding = false
            scope.launch {
                try {
                    ClarionRepository.shareLocationWith(p.id, when (shareFor) { 0 -> 1; 1 -> 2; else -> null })
                    toast = "Sharing your location with ${firstName(p.displayName)}"
                    reload()
                } catch (_: Exception) { toast = "Couldn't share right now. Try again." }
            }
        }
    }
}

@Composable
fun EmptyBox(text: String) {
    val t = tk()
    T(text, 13.5.sp, t.muted, modifier = Modifier.fillMaxWidth().border(1.dp, t.line, RoundedCornerShape(22.dp)).padding(14.dp), lineHeight = 19.sp)
}

/** Bottom panel over a dim scrim, drawn in its own window so it covers the tab bar too. */
@Composable
fun BottomModal(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val t = tk()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(scrimColor()).clickable(remember { MutableInteractionSource() }, null) { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().imePadding().background(t.bg, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) { content() }
        }
    }
}

/** The mockup's text field: 54 dp tall, rounded, with an optional leading icon or "@". */
@Composable
fun ClarionField(value: String, onValue: (String) -> Unit, placeholder: String, lead: String? = null, at: Boolean = false) {
    val t = tk()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).background(t.surface, RoundedCornerShape(16.dp)).border(1.5.dp, t.line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lead != null) { CIcon(lead, 18.dp, t.muted); Spacer(Modifier.width(10.dp)) }
        if (at) { T("@", 16.sp, t.muted, FontWeight.SemiBold); Spacer(Modifier.width(6.dp)) }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) T(placeholder, 16.sp, t.muted.copy(alpha = 0.75f))
            BasicTextField(
                value = value, onValueChange = onValue, singleLine = true, cursorBrush = SolidColor(t.primary),
                textStyle = TextStyle(fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Search people by username, phone number or email (same search as the app had). */
@Composable
fun AddPersonModal(title: String, verb: String, already: List<String>, onDismiss: () -> Unit, onPick: (Profile) -> Unit) {
    val t = tk()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Profile>?>(null) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = null; return@LaunchedEffect }
        delay(350)
        results = try { ClarionRepository.searchProfiles(q.trim().removePrefix("@")) } catch (_: Exception) { emptyList() }
    }
    BottomModal(onDismiss) {
        T(title, 20.sp, t.ink, FontWeight.ExtraBold)
        T("Search by username, phone number, or email. They need a Clarion account already.", 13.sp, t.muted, lineHeight = 18.sp)
        T("Username, phone or email", 13.sp, t.muted, FontWeight.SemiBold)
        ClarionField(q, { q = it }, "@username", lead = "search")
        val r = results
        when {
            q.trim().length < 2 -> {}
            r == null -> T("Searching…", 13.sp, t.muted)
            r.isEmpty() -> EmptyBox("No one found. Check the spelling, or ask them to double-check their username.")
            else -> ListCard {
                r.take(6).forEachIndexed { i, p ->
                    if (i > 0) LineDivider()
                    val has = p.id in already
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.displayName, p.avatarUrl, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(p.displayName, 15.sp, t.ink, FontWeight.Bold, maxLines = 1)
                            if (p.username != null) T("@${p.username}", 12.5.sp, t.muted, maxLines = 1)
                        }
                        if (has) T("Added", 12.5.sp, t.safe, FontWeight.Bold) else MiniButton(verb, filled = true) { onPick(p) }
                    }
                }
            }
        }
        T("Cancel", 14.sp, t.muted, FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onDismiss() }.padding(12.dp))
        Spacer(Modifier.height(2.dp))
    }
}
