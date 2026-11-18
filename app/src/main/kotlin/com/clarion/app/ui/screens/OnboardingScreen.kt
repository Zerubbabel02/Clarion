package com.clarion.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.clarion.app.core.ClarionRepository
import kotlinx.coroutines.launch

/** Two setup steps after sign-up (mockup): who you are, then the two permissions. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, onSignOut: () -> Unit = {}) {
    val t = tk()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf("") }
    var circleName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var locOk by remember { mutableStateOf(granted(Manifest.permission.ACCESS_FINE_LOCATION)) }
    var notifOk by remember { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS)) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { locOk = it }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }

    Column(
        Modifier.fillMaxSize().background(t.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 26.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { i -> Box(Modifier.weight(1f).height(4.dp).background(if (i <= step) t.primary else t.line, RoundedCornerShape(4.dp))) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 28.dp)) {
            if (step == 0) {
                T("Tell us who you are", 28.sp, t.ink, FontWeight.ExtraBold, lineHeight = 32.sp)
                Spacer(Modifier.height(10.dp))
                T("Your circle is the people who share your building or block.", 14.5.sp, t.muted, lineHeight = 21.sp)
                Spacer(Modifier.height(26.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        T("Your name", 13.sp, t.muted, FontWeight.SemiBold)
                        ClarionField(displayName, { displayName = it; error = null }, "Your full name")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        T("Lodge, building, or shop", 13.sp, t.muted, FontWeight.SemiBold)
                        ClarionField(circleName, { circleName = it }, "e.g. Ridgeway Lodge, Block C")
                    }
                    T("Everyone who enters the same name shares your night-time close circle automatically.", 13.sp, t.muted, lineHeight = 19.sp)
                    error?.let { T(it, 13.5.sp, t.flareText, FontWeight.SemiBold) }
                }
            } else {
                T("Two permissions we need", 28.sp, t.ink, FontWeight.ExtraBold, lineHeight = 32.sp)
                Spacer(Modifier.height(10.dp))
                T("Nothing works without these. Here is exactly why.", 14.5.sp, t.muted, lineHeight = 21.sp)
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermCard("pin", "Location", "So a Flare carries your exact spot, and others can be routed straight to you. Nearby Clarion users see an unnamed dot about 200 m from you; you can turn that off in Profile.", locOk) {
                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    PermCard("bell", "Notifications", "So a Flare can ring your phone even when Clarion isn’t open.", notifOk) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    error?.let { T(it, 13.5.sp, t.flareText, FontWeight.SemiBold) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PrimaryButton(if (saving) "Saving…" else if (step == 0) "Continue" else "Finish setup") {
                if (saving) return@PrimaryButton
                if (step == 0) {
                    if (displayName.isBlank()) { error = "Enter your name to continue."; return@PrimaryButton }
                    error = null; step = 1
                } else {
                    saving = true
                    scope.launch {
                        try { ClarionRepository.saveProfile(displayName.trim(), circleName.trim()); onDone() }
                        catch (e: Exception) { error = e.message ?: "Couldn't save your profile. Try again." }
                        finally { saving = false }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(50)).clickable {
                if (step == 1) step = 0 else scope.launch { try { ClarionRepository.signOut() } catch (_: Exception) {}; onSignOut() }
            }, contentAlignment = Alignment.Center) {
                T(if (step == 1) "Back" else "Not you? Sign out", 14.sp, t.muted, FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PermCard(icon: String, title: String, body: String, ok: Boolean, onAllow: () -> Unit) {
    val t = tk()
    Row(
        Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(22.dp)).border(1.dp, t.line, RoundedCornerShape(22.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp).background(t.primarySoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { CIcon(icon, 20.dp, t.primary) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(title, 15.5.sp, t.ink, FontWeight.Bold)
            T(body, 13.sp, t.muted, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(if (ok) (if (t.dark) Color(0xFF12301F) else Color(0xFFE3F3EC)) else t.primary)
                .clickable(enabled = !ok) { onAllow() }.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ok) { CIcon("check", 14.dp, t.safe, 2.6f); Spacer(Modifier.width(6.dp)) }
            T(if (ok) "Allowed" else "Allow", 12.5.sp, if (ok) t.safe else t.onPrimary, FontWeight.Bold)
        }
    }
}
