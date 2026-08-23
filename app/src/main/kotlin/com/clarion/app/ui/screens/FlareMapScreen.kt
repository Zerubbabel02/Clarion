package com.clarion.app.ui.screens

import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

// Bauchi, Nigeria — a placeholder center point until real device/incident GPS is wired up.
private val PLACEHOLDER_LOCATION = LatLng(10.3158, 9.8442)
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

@Composable
fun FlareMapScreen(onBack: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).also { view ->
                    mapView = view
                    view.onCreate(Bundle())
                    view.getMapAsync { map ->
                        map.setStyle(STYLE_URL)
                        map.cameraPosition = CameraPosition.Builder()
                            .target(PLACEHOLDER_LOCATION)
                            .zoom(15.0)
                            .build()
                    }
                }
            },
        )

        DisposableEffect(lifecycleOwner, mapView) {
            val view = mapView
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> view?.onStart()
                    Lifecycle.Event.ON_RESUME -> view?.onResume()
                    Lifecycle.Event.ON_PAUSE -> view?.onPause()
                    Lifecycle.Event.ON_STOP -> view?.onStop()
                    Lifecycle.Event.ON_DESTROY -> view?.onDestroy()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                view?.onDestroy()
            }
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
                Text(
                    "Demo Flare · Bauchi",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
