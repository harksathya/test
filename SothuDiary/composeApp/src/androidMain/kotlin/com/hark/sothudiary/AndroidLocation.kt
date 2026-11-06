package com.hark.sothudiary

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** "Use my current location" via the platform LocationManager (no Google Play services needed). */
class AndroidLocation(private val context: Context, private val bridge: ActivityBridge) : LocationActions {
    override val supported: Boolean = context.getSystemService(Context.LOCATION_SERVICE) != null

    private fun granted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun currentLocation(onResult: (LatLng?) -> Unit) {
        if (granted()) fetch(onResult)
        else bridge.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { ok ->
            if (ok) fetch(onResult) else onResult(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetch(onResult: (LatLng?) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            onResult(null)
            return
        }
        val enabled = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }
        val last: Location? = (enabled + LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 2 * 60_000L) {
            onResult(LatLng(last.latitude, last.longitude))
            return
        }
        if (enabled.isEmpty()) {
            onResult(last?.let { LatLng(it.latitude, it.longitude) })
            return
        }
        val main = Handler(Looper.getMainLooper())
        var done = false
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (done) return
                done = true
                runCatching { lm.removeUpdates(this) }
                onResult(LatLng(location.latitude, location.longitude))
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        enabled.forEach { p ->
            runCatching { lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper()) }
        }
        main.postDelayed({
            if (!done) {
                done = true
                runCatching { lm.removeUpdates(listener) }
                onResult(last?.let { LatLng(it.latitude, it.longitude) })
            }
        }, 20_000L)
    }
}
