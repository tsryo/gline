package com.gline

data class LatLng(val lat: Double, val lng: Double) {
    val isValid: Boolean get() = lat in -90.0..90.0 && lng in -180.0..180.0
}

enum class TravelMode(val label: String) {
    DRIVE("Driving"),
    BICYCLE("Cycling"),
    WALK("Walking"),
}

/** One point read from a Maps link, before it has necessarily been located. */
data class Waypoint(
    /** The place name, address or "lat,lng" text from the link; null when the link left it blank. */
    val label: String?,
    /** Known coordinates, or null when the label still has to be geocoded. */
    val position: LatLng?,
    /** A point the route was dragged through in Maps, rather than a stop. */
    val isVia: Boolean = false,
) {
    /** Maps leaves "Your location" blank in links, or names it outright. */
    val meansDeviceLocation: Boolean
        get() = !isVia && position == null &&
            (label.isNullOrBlank() || label.trim().lowercase() in DEVICE_LOCATION_NAMES)

    private companion object {
        val DEVICE_LOCATION_NAMES = setOf("your location", "my location", "current location")
    }
}

/** What a Maps directions link describes; [mode] is null when the link doesn't say. */
data class MapsRoute(val waypoints: List<Waypoint>, val mode: TravelMode?)

/** A located point on the final route. */
data class Stop(val name: String, val position: LatLng, val isVia: Boolean)

/** A road-following line between stops, from a routing service. */
data class RoutedPath(
    val points: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val engine: String,
)

class NotDirectionsException : Exception("Not a directions link")

/** A failure whose message is written for the person using the app. */
class ConversionException(message: String) : Exception(message)
