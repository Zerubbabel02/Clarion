package com.clarion.app.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LatLng(val lat: Double, val lng: Double)

object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    suspend fun getCurrentLocation(context: Context): LatLng? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()

        return suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(LatLng(location.latitude, location.longitude))
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private suspend fun getLastKnownLocation(context: Context): LatLng? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { location -> cont.resume(location?.let { LatLng(it.latitude, it.longitude) }) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    /**
     * The real reason sending a Flare felt slow: it always waited for a brand-new GPS fix,
     * which can take several seconds even on PRIORITY_BALANCED_POWER_ACCURACY. For an
     * emergency send, getting the alert out immediately matters far more than a few meters of
     * precision — so try the OS's cached last-known position first (returns almost instantly
     * on any phone that's had a location fix recently) and only fall back to a fresh fix if
     * there genuinely isn't one yet (e.g. right after a reboot or first-ever use).
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    suspend fun getFastestLocation(context: Context): LatLng? =
        getLastKnownLocation(context) ?: getCurrentLocation(context)

    /** A position plus how precise it is (metres) and how old it is (ms). */
    data class Fix(val lat: Double, val lng: Double, val accuracyM: Float, val ageMs: Long)

    private fun android.location.Location.toFix() = Fix(
        latitude, longitude, if (hasAccuracy()) accuracy else 999f,
        (android.os.SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000,
    )

    /** The phone's cached position — instant, but may be old or rough. */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    suspend fun lastFix(context: Context): Fix? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { cont.resume(it?.toFix()) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    /** A brand-new fix. [precise] uses GPS (slower, best accuracy); otherwise the fastest decent one. */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    suspend fun freshFix(context: Context, precise: Boolean): Fix? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(if (precise) 0 else 30_000)
            .setDurationMillis(if (precise) 15_000 else 5_000)
            .build()
        return suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { cont.resume(it?.toFix()) }
                .addOnFailureListener { cont.resume(null) }
        }
    }
}
