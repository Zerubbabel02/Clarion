package com.clarion.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

// Filled in once the Google Cloud OAuth clients exist: a WEB client id (not the Android one —
// the Android client only exists so its SHA-1 can be registered; the code always uses the web
// id, both here as setServerClientId and in Supabase's Google provider config, which is how
// Supabase verifies the token came from this app's own request). Debug keystore SHA-1 for
// registering the Android client: 7A:B9:D6:00:0F:78:8E:D2:9C:EA:FC:B1:95:62:B7:0E:4B:25:3E:BA
private const val GOOGLE_WEB_CLIENT_ID = "PASTE_YOUR_WEB_CLIENT_ID_HERE.apps.googleusercontent.com"

private fun randomNonce(): String = UUID.randomUUID().toString()

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

@Composable
fun AuthScreen(onSignedIn: suspend () -> Unit) {
    var isSignUp by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun signInWithGoogle() {
        if (googleLoading) return
        googleLoading = true
        error = null
        scope.launch {
            try {
                val rawNonce = randomNonce()
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                    .setNonce(sha256Hex(rawNonce))
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

                val credentialManager = CredentialManager.create(context)
                val result = credentialManager.getCredential(
                    request = request,
                    context = context as Activity,
                )
                val googleIdToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken

                ClarionRepository.signInWithGoogleIdToken(googleIdToken, rawNonce)
                onSignedIn()
            } catch (e: GetCredentialException) {
                // The most common case by far is the user backing out of the account picker —
                // that's not an error worth alarming them with.
                if (e.type != "androidx.credentials.TYPE_USER_CANCELED") {
                    error = "Couldn't sign in with Google. Try again."
                }
            } catch (e: Exception) {
                error = e.message ?: "Couldn't sign in with Google. Try again."
            } finally {
                googleLoading = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp)) {
            Spacer(modifier = Modifier.height(80.dp))

            Text(
                "Clarion",
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                if (isSignUp) "Create your account" else "Welcome back",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Your account keeps Flares trustworthy: no fake alerts, no spam.",
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(30.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
            )

            error?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(it, color = Color(0xFFE8562E), fontSize = 12.5.sp)
            }

            Spacer(modifier = Modifier.height(22.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (loading) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = !loading) {
                        if (email.isBlank() || password.length < 6) {
                            error = "Enter a valid email and a password of at least 6 characters."
                            return@clickable
                        }
                        loading = true
                        scope.launch {
                            try {
                                if (isSignUp) ClarionRepository.signUp(email.trim(), password) else ClarionRepository.signIn(email.trim(), password)
                                onSignedIn()
                            } catch (e: Exception) {
                                error = e.message ?: "Something went wrong. Try again."
                            } finally {
                                loading = false
                            }
                        }
                    }
                    .padding(vertical = 16.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        if (isSignUp) "Create Account" else "Log In",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)))
                Text(
                    "or",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)))
            }
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .clickable(enabled = !googleLoading) { signInWithGoogle() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (googleLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "Continue with Google",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                if (isSignUp) "Already have an account? Log in" else "New here? Create an account",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { isSignUp = !isSignUp; error = null },
            )
        }
    }
}
