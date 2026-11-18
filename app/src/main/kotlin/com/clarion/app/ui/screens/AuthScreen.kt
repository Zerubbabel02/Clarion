package com.clarion.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.clarion.app.core.ClarionRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

// Filled in once the Google Cloud OAuth clients exist: a WEB client id (not the Android one).
// Debug keystore SHA-1 for registering the Android client: 7A:B9:D6:00:0F:78:8E:D2:9C:EA:FC:B1:95:62:B7:0E:4B:25:3E:BA
private const val GOOGLE_WEB_CLIENT_ID = "PASTE_YOUR_WEB_CLIENT_ID_HERE.apps.googleusercontent.com"
private val googleReady = !GOOGLE_WEB_CLIENT_ID.startsWith("PASTE")

private fun randomNonce(): String = UUID.randomUUID().toString()

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

/** The Clarion mark from the mockup: an open ring with an orange dot. */
@Composable
fun ClarionLogo(size: Dp) {
    val t = tk()
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val r = 8.6f * u
        val sweep = 360f - 2 * 47.9f
        drawArc(t.primary, 47.9f, sweep, false, topLeft = Offset(12 * u - r, 12 * u - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = Stroke(2.6f * u, cap = StrokeCap.Round))
        drawCircle(t.flare, 2.5f * u, Offset(19.4f * u, 12 * u))
    }
}

@Composable
fun Brand() {
    val t = tk()
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClarionLogo(28.dp); Spacer(Modifier.width(8.dp)); T("Clarion", 21.sp, t.ink, FontWeight.ExtraBold, letter = (-0.4).sp)
    }
}

/** Sign up / log in (mockup). Email and password; Google appears once its client id is set. */
@Composable
fun AuthScreen(onSignedIn: suspend () -> Unit) {
    val t = tk()
    var isSignUp by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun submit() {
        if (loading) return
        if (!email.contains("@") || password.length < 6) { error = "Enter a valid email and a password of at least 6 characters."; return }
        loading = true; error = null
        scope.launch {
            try {
                if (isSignUp) ClarionRepository.signUp(email.trim(), password) else ClarionRepository.signIn(email.trim(), password)
                onSignedIn()
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong. Try again."
            } finally { loading = false }
        }
    }

    fun signInWithGoogle() {
        if (googleLoading) return
        googleLoading = true; error = null
        scope.launch {
            try {
                val rawNonce = randomNonce()
                val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(GOOGLE_WEB_CLIENT_ID).setNonce(sha256Hex(rawNonce)).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = CredentialManager.create(context).getCredential(request = request, context = context as Activity)
                val googleIdToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                ClarionRepository.signInWithGoogleIdToken(googleIdToken, rawNonce)
                onSignedIn()
            } catch (e: GetCredentialException) {
                // Backing out of the account picker is not an error worth alarming anyone with.
                if (e.type != "androidx.credentials.TYPE_USER_CANCELED") error = "Couldn't sign in with Google. Try again."
            } catch (e: Exception) {
                error = e.message ?: "Couldn't sign in with Google. Try again."
            } finally { googleLoading = false }
        }
    }

    Column(
        Modifier.fillMaxSize().background(t.bg).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 26.dp),
    ) {
        Brand()
        Spacer(Modifier.height(40.dp))
        T(if (isSignUp) "Create your account" else "Welcome back", 28.sp, t.ink, FontWeight.ExtraBold, lineHeight = 32.sp)
        Spacer(Modifier.height(10.dp))
        T(if (isSignUp) "Your account keeps Flares trustworthy: no fake alerts, no spam." else "Log in to reach your circle.", 14.5.sp, t.muted, lineHeight = 21.sp)
        Spacer(Modifier.height(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                T("Email", 13.sp, t.muted, FontWeight.SemiBold)
                ClarionField(email, { email = it; error = null }, "you@example.com", keyboard = KeyboardType.Email)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                T("Password", 13.sp, t.muted, FontWeight.SemiBold)
                ClarionField(password, { password = it; error = null }, if (isSignUp) "At least 6 characters" else "Your password", password = true)
            }
            error?.let { T(it, 13.5.sp, t.flareText, FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(30.dp))
        PrimaryButton(if (loading) "Please wait…" else if (isSignUp) "Create account" else "Log in") { submit() }
        if (googleReady) {
            Spacer(Modifier.height(10.dp))
            GhostButton(if (googleLoading) "Please wait…" else "Continue with Google") { signInWithGoogle() }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(50)).clickable { isSignUp = !isSignUp; error = null }, contentAlignment = Alignment.Center) {
            T(if (isSignUp) "Already have an account? Log in" else "New here? Create an account", 14.sp, t.muted, FontWeight.SemiBold)
        }
    }
}

@Suppress("unused") private val keepBorder: (Modifier) -> Modifier = { it.border(0.dp, androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(0.dp)) }
