package com.example.hershield.location

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
import android.util.Log
import androidx.core.content.ContextCompat

object LocationHelper {

    private const val TAG = "HER_SHIELD"

    @SuppressLint("MissingPermission")
    fun getLocation(context: Context, onResult: (Location?) -> Unit): () -> Unit {
        Log.d(TAG, "Location request started")

        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) {
            Log.w(TAG, "Location failure: missing location permissions")
            onResult(null)
            return {}
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = try {
            lm.getAllProviders()
        } catch (e: Exception) {
            Log.e(TAG, "Location failure: error getting providers: ${e.message}")
            emptyList<String>()
        }

        if (providers.isEmpty()) {
            Log.w(TAG, "Location failure: no providers available")
            onResult(null)
            return {}
        }

        var bestLocation: Location? = null
        for (p in providers) {
            try {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null) {
                    if (bestLocation == null || loc.time > bestLocation.time) {
                        bestLocation = loc
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting last known location for provider $p: ${e.message}")
            }
        }

        Log.d(TAG, "Location last known = ${bestLocation?.let { "${it.latitude}, ${it.longitude}" } ?: "null"}")

        val handler = Handler(Looper.getMainLooper())
        var finished = false

        val finishWithLocation: (Location?) -> Unit = { loc ->
            if (!finished) {
                finished = true
                handler.removeCallbacksAndMessages(null)
                val finalLoc = loc ?: bestLocation
                if (finalLoc != null) {
                    Log.d(TAG, "Location received: ${finalLoc.latitude}, ${finalLoc.longitude}")
                } else {
                    Log.w(TAG, "Location timeout / unavailable, using fallback")
                }
                onResult(finalLoc)
            }
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "Location received from listener: ${location.latitude}, ${location.longitude}")
                try {
                    lm.removeUpdates(this)
                } catch (_: Exception) {}
                finishWithLocation(location)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // 10-second timeout handler scheduled first
        handler.postDelayed({
            if (!finished) {
                Log.w(TAG, "Location timeout reached (10s)")
                try {
                    lm.removeUpdates(listener)
                } catch (_: Exception) {}
                finishWithLocation(bestLocation)
            }
        }, 10_000L)

        var requestedAny = false
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                if (lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(
                        p,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                    requestedAny = true
                    Log.d(TAG, "Requested location updates from provider: $p")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request location updates for provider $p: ${e.message}")
            }
        }

        if (!requestedAny) {
            Log.w(TAG, "No enabled providers found for location updates.")
            try {
                lm.removeUpdates(listener)
            } catch (_: Exception) {}
            finishWithLocation(bestLocation)
        }

        return {
            if (!finished) {
                finished = true
                try {
                    lm.removeUpdates(listener)
                } catch (_: Exception) {}
                handler.removeCallbacksAndMessages(null)
                Log.d(TAG, "Location request cancelled")
            }
        }
    }
}
