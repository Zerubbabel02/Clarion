package com.clarion.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    startRoute: String? = null,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
) {
    LaunchedEffect(startRoute) {
        if (startRoute != null) {
            navController.navigate(startRoute)
        }
    }

    NavHost(navController = navController, startDestination = ClarionDestinations.SPLASH) {
        composable(ClarionDestinations.SPLASH) {
            LaunchedEffect(Unit) {
                val hasSession = ClarionRepository.currentUserId() != null
                val target = if (!hasSession) ClarionDestinations.AUTH else routeAfterAuth()
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
        composable(ClarionDestinations.NIGHT_MODE) {
            NightModeScreen(onBack = { navController.popBackStack() })
        }
        composable(ClarionDestinations.SHARE_LOCATION) {
            ShareLocationScreen(onBack = { navController.popBackStack() })
        }
        composable(ClarionDestinations.FLARE_MAP) {
            FlareMapScreen(onBack = { navController.popBackStack() })
        }
    }
}
