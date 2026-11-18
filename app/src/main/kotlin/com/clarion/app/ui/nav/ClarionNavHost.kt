package com.clarion.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.NotificationHelper
import com.clarion.app.core.ThemeMode
import com.clarion.app.core.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.clarion.app.ui.screens.AuthScreen
import com.clarion.app.ui.screens.FlareMapScreen
import com.clarion.app.ui.screens.NightModeScreen
import com.clarion.app.ui.screens.OnboardingScreen
import com.clarion.app.ui.screens.SendFlareScreen
import com.clarion.app.ui.screens.ShareLocationScreen

private suspend fun routeAfterAuth(): String {
    // Defense in depth: sign-up can succeed without producing a session (e.g. a Supabase
    // project with "Confirm email" still required) — never route into Onboarding without a
    // real signed-in user, or saving the profile there fails with a confusing dead end.
    if (ClarionRepository.currentUserId() == null) return ClarionDestinations.AUTH
    return if (ClarionRepository.hasProfile()) ClarionDestinations.MAIN else ClarionDestinations.ONBOARDING
}

@Composable
fun ClarionNavHost(
    navController: NavHostController = rememberNavController(),
    pendingNav: PendingNav? = null,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
) {
    // Navigation-Compose routes are plain strings with no argument-passing built in here, so
    // the target Flare's location/sender rides alongside in this remembered slot instead —
    // updated whenever a new pendingNav arrives, read by the FLARE_MAP destination below.
    var targetFlare by remember { mutableStateOf<PendingNav?>(null) }

    LaunchedEffect(pendingNav) {
        if (pendingNav != null) {
            targetFlare = pendingNav
            navController.navigate(pendingNav.route)
        }
    }

    // Pairs with the fast open: if the saved session turns out to be signed out, leave Home for sign-in.
    LaunchedEffect(Unit) {
        supabase.auth.sessionStatus.collect { status ->
            if (status is SessionStatus.NotAuthenticated && navController.currentDestination?.route == ClarionDestinations.MAIN) {
                navController.navigate(ClarionDestinations.AUTH) { popUpTo(ClarionDestinations.MAIN) { inclusive = true } }
            }
        }
    }

    NavHost(navController = navController, startDestination = ClarionDestinations.SPLASH) {
        composable(ClarionDestinations.SPLASH) {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                // Fast open, for emergencies: a session saved on this phone plus a profile seen
                // before (cached name) means go straight to Home — no waiting on the network.
                // The token refresh and profile check carry on in the background; if the account
                // turns out to be signed out, the watcher below sends the user to sign-in.
                try {
                    supabase.auth.awaitInitialization()
                    if (supabase.auth.currentSessionOrNull() != null && com.clarion.app.core.ProfileCache.getDisplayName(context) != null) {
                        navController.navigate(ClarionDestinations.MAIN) { popUpTo(ClarionDestinations.SPLASH) { inclusive = true } }
                        return@LaunchedEffect
                    }
                } catch (_: Exception) {
                    // Fall through to the careful path below.
                }
                // The real logout bug, round 2: awaitInitialization() only guarantees the
                // disk-restore step finished — it does NOT wait for the follow-up network
                // refresh Auth performs when the restored access token has already expired,
                // which it always has after the app's been closed more than an hour (the
                // default token lifetime). Checking currentUserOrNull() right after
                // awaitInitialization() raced that refresh: it could still read the stale,
                // expired-looking session and route to the sign-in screen even though a
                // valid refresh was about to succeed a moment later (confirmed live via
                // Supabase's own Auth logs — a token refresh completing successfully within
                // a second of the app showing "Create your account"). sessionStatus is the
                // real signal: it only leaves Initializing once Auth has settled into
                // Authenticated, NotAuthenticated, or RefreshFailure — i.e. restore AND any
                // needed refresh have both actually finished.
                // A real, confirmed crash: routeAfterAuth() hitting Postgrest to check for a
                // profile can throw HttpRequestTimeoutException on this app's actual target
                // network conditions (rural, intermittent), and this runs on every single
                // cold start — an uncaught exception here crashed the whole app ("suddenly
                // closes, drops to home screen"), not just this screen. One retry after a
                // short pause handles a transient blip; a second failure falls back to AUTH
                // rather than crash — worst case is a real re-login, never a crash.
                val target = try {
                    val status = supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
                    if (status !is SessionStatus.Authenticated) ClarionDestinations.AUTH else routeAfterAuth()
                } catch (e: Exception) {
                    try {
                        delay(2000)
                        val status = supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
                        if (status !is SessionStatus.Authenticated) ClarionDestinations.AUTH else routeAfterAuth()
                    } catch (e2: Exception) {
                        ClarionDestinations.AUTH
                    }
                }
                navController.navigate(target) {
                    popUpTo(ClarionDestinations.SPLASH) { inclusive = true }
                }
            }
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        composable(ClarionDestinations.AUTH) {
            AuthScreen(
                onSignedIn = {
                    // Called from inside AuthScreen's own coroutine (after sign-in/sign-up
                    // succeeds), so this suspend call runs in that same scope.
                    val target = routeAfterAuth()
                    navController.navigate(target) {
                        popUpTo(ClarionDestinations.AUTH) { inclusive = true }
                    }
                },
            )
        }

        composable(ClarionDestinations.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(ClarionDestinations.MAIN) {
                        popUpTo(ClarionDestinations.AUTH) { inclusive = true }
                    }
                },
                onSignOut = {
                    navController.navigate(ClarionDestinations.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(ClarionDestinations.MAIN) {
            val context = LocalContext.current
            ClarionMainShell(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onSendFlare = { navController.navigate(ClarionDestinations.SEND_FLARE) },
                onShareLocation = { navController.navigate(ClarionDestinations.SHARE_LOCATION) },
                onFlareTrustedCircle = { navController.navigate(ClarionDestinations.SEND_FLARE_TRUSTED) },
                onOpenNightMode = { navController.navigate(ClarionDestinations.NIGHT_MODE) },
                onSignedOut = {
                    navController.navigate(ClarionDestinations.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
            LaunchedEffect(context) { NotificationHelper.ensureChannel(context) }
        }

        composable(ClarionDestinations.SEND_FLARE) {
            SendFlareScreen(onCancelFlare = { navController.popBackStack() })
        }
        composable(ClarionDestinations.SEND_FLARE_TRUSTED) {
            SendFlareScreen(onCancelFlare = { navController.popBackStack() }, trustedOnly = true)
        }
        composable(ClarionDestinations.NIGHT_MODE) {
            NightModeScreen(onBack = { navController.popBackStack() })
        }
        composable(ClarionDestinations.SHARE_LOCATION) {
            ShareLocationScreen(onBack = { navController.popBackStack() })
        }
        composable(ClarionDestinations.FLARE_MAP) {
            val target = targetFlare
            FlareMapScreen(
                targetLat = target?.flareLat,
                targetLng = target?.flareLng,
                targetSenderName = target?.flareSender,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
