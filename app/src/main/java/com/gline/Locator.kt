package com.gline

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume

/** Finds coordinates for place names, and for the phone itself. */
class Locator(private val context: Context) {

    /** The phone's geocoder (Google's, on most phones), then OpenStreetMap's Nominatim. */
    suspend fun geocode(query: String): LatLng? =
        platformGeocode(query) ?: withContext(Dispatchers.IO) { nominatim(query) }

    private suspend fun platformGeocode(query: String): LatLng? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context)
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses.firstOrNull()?.let { LatLng(it.latitude, it.longitude) })
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resume(null)
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 1)?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
                }
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun nominatim(query: String): LatLng? =
        try {
            val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=" +
                URLEncoder.encode(query, "UTF-8")
            val results = JSONArray(Http.get(url))
            if (results.length() == 0) {
                null
            } else {
                val first = results.getJSONObject(0)
                LatLng(first.getString("lat").toDouble(), first.getString("lon").toDouble())
            }
        } catch (_: Exception) {
            null
        }

    fun hasLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    /** A recent fix if the phone has one, otherwise a fresh one; null without permission or signal. */
    @SuppressLint("MissingPermission")
    suspend fun deviceLocation(): LatLng? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = manager.getProviders(true)
        val last = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < FRESH_MS) return last.toLatLng()

        if (Build.VERSION.SDK_INT >= 30) {
            val provider = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .firstOrNull { it in providers }
            if (provider != null) {
                val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        manager.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                    }
                }
                if (fresh != null) return fresh.toLatLng()
            }
        }
        return last?.toLatLng()
    }

    private fun Location.toLatLng() = LatLng(latitude, longitude)

    private companion object {
        const val FRESH_MS = 10 * 60_000L
        const val FIX_TIMEOUT_MS = 15_000L
    }
}
