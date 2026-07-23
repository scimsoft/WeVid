package com.scimsoft.wevid.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class LocationUnavailableException :
    Exception("Couldn't get your location. Check that location is enabled.")

class LocationProvider(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Returns the best available location: prefers a fresh current fix, falls
     * back to last known. Throws [LocationUnavailableException] when none.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location {
        if (!hasPermission()) throw LocationUnavailableException()

        val last = runCatching { client.lastLocation.await() }.getOrNull()
        if (last != null && System.currentTimeMillis() - last.time < FRESH_MS) {
            return last
        }

        val current = runCatching {
            val token = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                .await()
        }.getOrNull()

        return current ?: last ?: throw LocationUnavailableException()
    }

    companion object {
        private const val FRESH_MS = 60_000L
    }
}
