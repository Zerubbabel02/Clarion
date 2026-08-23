package com.clarion.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.LocationHelper
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

// Fallback only if a real GPS fix can't be obtained (permission denied, no signal indoors, etc).
private val FALLBACK_LOCATION = LatLng(10.3158, 9.8442) // Bauchi, Nigeria
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private val Flare = Color(0xFFE8562E)
private val FlareGlow = Color(0xFFF08B5A)

/**
 * [targetLat]/[targetLng] (with [targetSenderName]) center the map on a SPECIFIC Flare — the
 * path from tapping an incoming alert. When null, this behaves as the general "where am I"
 * Map tab instead, centering on the viewer's own live location.
 */
@Composable
fun FlareMapScreen(
    targetLat: Double?,
    targetLng: Double?,
    targetSenderName: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val hasTarget = targetLat != null && targetLng != null

    var hasPermission by remember { mutableStateOf(LocationHelper.hasPermission(context)) }
    var statusLabel by remember {
        mutableStateOf(if (hasTarget) "${targetSenderName ?: "A neighbor"}'s Flare" else "Locating you…")
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted && !hasTarget) statusLabel = "Location permission needed"
    }

    // Created once via `remember`, not inside AndroidView's factory — writing to Compose
    // state from inside factory (as an earlier version of this screen did) caused a spurious
    // recomposition that re-registered lifecycle callbacks mid-render, which is a known
    // trigger for native SIGSEGV crashes in MapLibre's GL render thread on some GPUs
    // (confirmed reproducing on this Tecno device). textureMode(true) additionally switches
    // the MapView off the default GLSurfaceView renderer, which is the other half of the fix.
    val mapView = remember {
        val options = MapLibreMapOptions.createFromAttributes(context).apply {
            textureMode(true)
        }
        MapView(context, options).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                map.setStyle(STYLE_URL)
                val startPoint = if (hasTarget) LatLng(targetLat!!, targetLng!!) else FALLBACK_LOCATION
                map.cameraPosition = CameraPosition.Builder()
                    .target(startPoint)
                    .zoom(if (hasTarget) 16.5 else 15.0)
                    .build()
            }
        }
    }

    LaunchedEffect(hasPermission, hasTarget) {
        if (hasTarget) {
            // The camera's already centered on the Flare from the factory above — just try to
            // capture the viewer's own position in the background for a future distance/route
            // calculation, without moving the camera off the incident.
            if (LocationHelper.hasPermission(context)) {
                try {
                    @Suppress("MissingPermission")
                    val loc = LocationHelper.getCurrentLocation(context)
                    if (loc != null) scope.launch { ClarionRepository.updateLocation(loc.lat, loc.lng) }
                } catch (_: Exception) {
                    // Non-critical here — the Flare's own location is what matters on this screen.
                }
            }
            return@LaunchedEffect
        }

        if (!ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION).let { it == PackageManager.PERMISSION_GRANTED }) {
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                return@LaunchedEffect
            }
        }
        try {
            @Suppress("MissingPermission")
            val loc = LocationHelper.getCurrentLocation(context)
            if (loc != null) {
                statusLabel = "Your location"
                mapView.getMapAsync { map ->
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(loc.lat, loc.lng))
                        .zoom(16.0)
                        .build()
                }
                scope.launch { ClarionRepository.updateLocation(loc.lat, loc.lng) }
            } else {
                statusLabel = "Couldn't get a location fix"
            }
        } catch (_: Exception) {
            statusLabel = "Couldn't get a location fix"
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView })

        // The camera is centered exactly on the Flare's coordinates, so a marker fixed to the
        // middle of the screen lines up with it — simpler and dependency-free compared to
        // MapLibre's native annotation/marker plugin, which isn't wired in.
        if (hasTarget) {
            FlareMarker(modifier = Modifier.align(Alignment.Center))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 56.dp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Text("←", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(statusLabel, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
private fun FlareMarker(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "flareMarkerPulse")
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 2.1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "alpha",
    )
    Box(modifier = modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .scale(scale)
                .alpha(alpha)
                .background(Flare, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(FlareGlow, CircleShape),
        )
    }
}
