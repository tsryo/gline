package com.gline

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Turns located stops into a road-following line. Blocking; call from Dispatchers.IO. */
interface Router {
    fun route(stops: List<Stop>, mode: TravelMode): RoutedPath
}

/**
 * OSRM on the OpenStreetMap community server (FOSSGIS), which runs separate car, bike and foot
 * profiles. Free and keyless; the line goes through the same stops as the Google route, but
 * between them it takes OSRM's choice of road.
 */
object OsrmRouter : Router {
    override fun route(stops: List<Stop>, mode: TravelMode): RoutedPath {
        val profile = when (mode) {
            TravelMode.DRIVE -> "routed-car"
            TravelMode.BICYCLE -> "routed-bike"
            TravelMode.WALK -> "routed-foot"
        }
        val coords = stops.joinToString(";") { "%.6f,%.6f".format(java.util.Locale.ROOT, it.position.lng, it.position.lat) }
        val url = "https://routing.openstreetmap.de/$profile/route/v1/driving/$coords" +
            "?overview=full&geometries=polyline6&steps=false"

        val json = try {
            JSONObject(Http.get(url))
        } catch (e: HttpException) {
            throw ConversionException("OpenStreetMap routing failed: ${messageFrom(e.body) ?: "HTTP ${e.code}"}")
        }
        if (json.optString("code") != "Ok") {
            throw ConversionException("OpenStreetMap routing failed: ${json.optString("message", json.optString("code"))}")
        }
        val route = json.getJSONArray("routes").getJSONObject(0)
        return RoutedPath(
            points = Polyline.decode(route.getString("geometry"), precision = 6),
            distanceMeters = route.getDouble("distance"),
            durationSeconds = route.getDouble("duration"),
            engine = "OpenStreetMap",
        )
    }

    private fun messageFrom(body: String): String? =
        try {
            JSONObject(body).optString("message").takeIf { it.isNotBlank() }
        } catch (_: JSONException) {
            null
        }
}

/**
 * Google's Routes API, which follows the same roads as the Maps app. Needs the user's own API
 * key with the Routes API enabled.
 */
class GoogleRouter(private val apiKey: String) : Router {
    override fun route(stops: List<Stop>, mode: TravelMode): RoutedPath {
        val body = JSONObject()
            .put("origin", waypoint(stops.first()))
            .put("destination", waypoint(stops.last()))
            .put("intermediates", JSONArray(stops.subList(1, stops.size - 1).map(::waypoint)))
            .put(
                "travelMode",
                when (mode) {
                    TravelMode.DRIVE -> "DRIVE"
                    TravelMode.BICYCLE -> "BICYCLE"
                    TravelMode.WALK -> "WALK"
                },
            )
            .put("polylineQuality", "HIGH_QUALITY")

        val text = try {
            Http.post(
                "https://routes.googleapis.com/directions/v2:computeRoutes",
                body.toString(),
                mapOf(
                    "Content-Type" to "application/json",
                    "X-Goog-Api-Key" to apiKey,
                    "X-Goog-FieldMask" to "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline",
                ),
            )
        } catch (e: HttpException) {
            throw ConversionException("Google routing failed: ${messageFrom(e.body) ?: "HTTP ${e.code}"}")
        }
        val routes = JSONObject(text).optJSONArray("routes")
        if (routes == null || routes.length() == 0) {
            throw ConversionException("Google found no ${mode.label.lowercase()} route between these stops.")
        }
        val route = routes.getJSONObject(0)
        return RoutedPath(
            points = Polyline.decode(route.getJSONObject("polyline").getString("encodedPolyline"), precision = 5),
            distanceMeters = route.optDouble("distanceMeters", 0.0),
            durationSeconds = route.optString("duration").removeSuffix("s").toDoubleOrNull() ?: 0.0,
            engine = "Google",
        )
    }

    private fun waypoint(stop: Stop): JSONObject {
        val latLng = JSONObject().put("latitude", stop.position.lat).put("longitude", stop.position.lng)
        return JSONObject()
            .put("location", JSONObject().put("latLng", latLng))
            .apply { if (stop.isVia) put("via", true) }
    }

    private fun messageFrom(body: String): String? =
        try {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (_: JSONException) {
            null
        }
}
