package com.gline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

enum class RoutingEngine(val label: String) {
    OSM("OpenStreetMap"),
    GOOGLE("Google"),
}

class Conversion(
    val name: String,
    val stops: List<Stop>,
    val path: RoutedPath,
    val mode: TravelMode,
    val gpx: String,
    val fileName: String,
    val sourceUrl: String,
    val notes: List<String>,
)

/** Shared Maps text in, GPX out. */
class Converter(
    private val locator: Locator,
    /** Asks the user for location access; true if granted. */
    private val requestLocationPermission: suspend () -> Boolean,
) {

    suspend fun convert(
        sharedText: String,
        modeChoice: TravelMode?,
        engine: RoutingEngine,
        googleKey: String,
        progress: (String) -> Unit,
    ): Conversion {
        val link = MapsLinkParser.extractUrl(sharedText)
            ?: throw ConversionException("There's no link in that text. In Google Maps, open the route and use Share directions.")

        progress("Expanding link…")
        val url = try {
            withContext(Dispatchers.IO) { LinkResolver.resolve(link) }
        } catch (e: IOException) {
            throw ConversionException("Couldn't open the Maps link: ${e.message}. Check your internet connection.")
        }

        val route = try {
            MapsLinkParser.parse(url)
        } catch (_: NotDirectionsException) {
            throw ConversionException(
                "That link is a place or a search, not a route. In Google Maps, tap Directions, " +
                    "plan the route, then use Share directions.",
            )
        }

        progress("Finding stops…")
        val notes = mutableListOf<String>()
        val stops = locate(route.waypoints, notes)
            .dropWhile { it.isVia }
            .dropLastWhile { it.isVia }
        if (stops.size < 2) {
            throw ConversionException("A route needs a start and a destination, and this link has only one place that could be found.")
        }

        val mode = modeChoice ?: route.mode ?: TravelMode.DRIVE.also {
            notes += "The link didn't say how you're travelling, so this is a driving route. Pick a mode above to change it."
        }

        progress("Planning the ${mode.label.lowercase()} route…")
        val router = when (engine) {
            RoutingEngine.OSM -> OsrmRouter
            RoutingEngine.GOOGLE -> GoogleRouter(googleKey)
        }
        val path = try {
            withContext(Dispatchers.IO) { router.route(stops, mode) }
        } catch (e: IOException) {
            throw ConversionException("Couldn't reach the ${engine.label} routing service: ${e.message}")
        }

        if (path.distanceMeters > BEELINE_COMFORT_LIMIT_M) {
            notes += "Beeline recommends imported routes under about 240 km (150 mi); longer ones can time out while importing."
        }

        val realStops = stops.filterNot { it.isVia }
        val name = "${realStops.first().name} to ${realStops.last().name}"
        return Conversion(
            name = name,
            stops = stops,
            path = path,
            mode = mode,
            gpx = GpxWriter.write(name, stops, path.points),
            fileName = fileNameFor(name),
            sourceUrl = url,
            notes = notes,
        )
    }

    private suspend fun locate(waypoints: List<Waypoint>, notes: MutableList<String>): List<Stop> {
        val stopCount = waypoints.count { !it.isVia }
        var stopIndex = 0
        var here: LatLng? = null
        val stops = mutableListOf<Stop>()

        for (waypoint in waypoints) {
            if (waypoint.isVia) {
                stops += Stop("Via point", waypoint.position!!, isVia = true)
                continue
            }
            val index = stopIndex++
            val position = when {
                waypoint.position != null -> waypoint.position
                waypoint.meansDeviceLocation -> {
                    here = here ?: deviceLocation()
                    if (here == null) {
                        if (index == 0) {
                            notes += "The route started at \"Your location\", which wasn't available, so the GPX starts at the next stop."
                            continue
                        }
                        throw ConversionException("One stop is \"Your location\". Allow location access so it can be filled in.")
                    }
                    here
                }
                else -> locator.geocode(waypoint.label!!)
                    ?: throw ConversionException("Couldn't find \"${waypoint.label}\" on the map.")
            }
            stops += Stop(nameFor(waypoint, index, stopCount), position, isVia = false)
        }
        return stops
    }

    private suspend fun deviceLocation(): LatLng? {
        if (!locator.hasLocationPermission() && !requestLocationPermission()) return null
        return locator.deviceLocation()
    }

    private fun nameFor(waypoint: Waypoint, index: Int, count: Int): String {
        if (waypoint.meansDeviceLocation) return "My location"
        val label = waypoint.label
        if (label != null && MapsLinkParser.parseLatLng(label) == null) {
            // Maps labels are full addresses; the first part is the place's own name.
            return label.substringBefore(',').trim().ifEmpty { label }
        }
        return when (index) {
            0 -> "Start"
            count - 1 -> "Destination"
            else -> "Stop $index"
        }
    }

    private fun fileNameFor(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").take(80).trim() + ".gpx"

    private companion object {
        const val BEELINE_COMFORT_LIMIT_M = 240_000.0
    }
}
