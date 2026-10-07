package com.gline

import java.net.URLDecoder

/**
 * Reads a Google Maps directions link into its stops and travel mode.
 *
 * Maps produces three shapes of directions link:
 *  - app/web:  /maps/dir/<stop>/<stop>/@<camera>/data=!4m..!3e1  (stop names in the path, coordinates in `data`)
 *  - API:      /maps/dir/?api=1&origin=..&destination=..&waypoints=a|b&travelmode=..
 *  - legacy:   /maps?saddr=..&daddr=b+to:c&dirflg=b
 */
object MapsLinkParser {

    private val URL_IN_TEXT = Regex("""https?://[^\s<>"]+""")
    private val LAT_LNG = Regex("""^\s*(-?\d{1,3}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")
    private val PATH_PARAM = Regex("""^[a-z]+=.*""")
    private val DATA_TOKEN = Regex("""^(\d+)([a-zA-Z])(.*)$""")
    private val SHORT_LINK_HOSTS = setOf("maps.app.goo.gl", "goo.gl", "g.co")

    /** Pulls the link out of shared text such as "Directions to X: https://maps.app.goo.gl/abc". */
    fun extractUrl(text: String): String? =
        URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ')', ']', '\'')

    fun isShortLink(url: String): Boolean =
        host(url).let { it in SHORT_LINK_HOSTS || it.endsWith(".app.goo.gl") }

    fun host(url: String): String =
        url.substringAfter("://", "")
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').substringBefore(':')
            .lowercase()

    fun queryParams(url: String): Map<String, String> {
        val query = url.substringBefore('#').substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()
        return query.split('&').filter { it.isNotEmpty() }.associate { part ->
            decode(part.substringBefore('=')) to decode(part.substringAfter('=', ""))
        }
    }

    fun parseLatLng(text: String): LatLng? {
        val match = LAT_LNG.matchEntire(text) ?: return null
        val (lat, lng) = match.destructured
        return LatLng(lat.toDouble(), lng.toDouble()).takeIf { it.isValid }
    }

    /** @throws NotDirectionsException when the link is a place, a search, or not Maps at all. */
    fun parse(url: String): MapsRoute {
        val path = url.substringBefore('#').substringBefore('?').substringAfter("://", url).substringAfter('/', "")
        val params = queryParams(url)
        val segments = path.split('/')
        val dirIndex = segments.indexOf("dir")

        return when {
            dirIndex >= 0 && params["api"] == "1" -> fromApiParams(params)
            dirIndex >= 0 -> fromDirPath(segments.drop(dirIndex + 1), params)
            params.containsKey("daddr") -> fromLegacyParams(params)
            else -> throw NotDirectionsException()
        }
    }

    private fun fromDirPath(afterDir: List<String>, params: Map<String, String>): MapsRoute {
        val texts = afterDir
            .takeWhile { !it.startsWith("@") && !PATH_PARAM.matches(it) }
            .map(::decode)
            .toMutableList()
        // A trailing slash leaves an empty last segment that isn't a stop.
        if (texts.size > 1 && texts.last().isBlank()) texts.removeAt(texts.lastIndex)

        val data = afterDir.firstOrNull { it.startsWith("data=") }?.removePrefix("data=") ?: params["data"]
        val directions = data?.let(::readDirectionsData)
        val dataStops = directions?.stops.orEmpty()

        val count = maxOf(texts.size, dataStops.size)
        if (count == 0) throw NotDirectionsException()

        val waypoints = buildList {
            for (i in 0 until count) {
                val text = texts.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
                val dataStop = dataStops.getOrNull(i)
                add(Waypoint(text, dataStop?.position ?: text?.let(::parseLatLng)))
                dataStop?.vias?.forEach { add(Waypoint(null, it, isVia = true)) }
            }
        }
        val mode = directions?.modeCode?.let(::modeFromDataCode)
            ?: params["travelmode"]?.let(::modeFromName)
            ?: params["dirflg"]?.let(::modeFromDirflg)
        return MapsRoute(waypoints, mode)
    }

    private fun fromApiParams(params: Map<String, String>): MapsRoute {
        val destination = params["destination"] ?: throw NotDirectionsException()
        // No origin means "from where I am", same as a blank stop in a path link.
        val texts = listOf(params["origin"].orEmpty()) +
            params["waypoints"].orEmpty().split('|').filter { it.isNotBlank() } +
            destination
        return MapsRoute(texts.map(::waypointFromText), params["travelmode"]?.let(::modeFromName))
    }

    private fun fromLegacyParams(params: Map<String, String>): MapsRoute {
        val destinations = params.getValue("daddr").split(" to:").filter { it.isNotBlank() }
        if (destinations.isEmpty()) throw NotDirectionsException()
        val texts = listOf(params["saddr"].orEmpty()) + destinations
        return MapsRoute(texts.map(::waypointFromText), params["dirflg"]?.let(::modeFromDirflg))
    }

    private fun waypointFromText(raw: String): Waypoint {
        val text = raw.trim().takeIf { it.isNotEmpty() }
        return Waypoint(text, text?.let(::parseLatLng))
    }

    private fun modeFromDataCode(code: String): TravelMode? = when (code) {
        "0" -> TravelMode.DRIVE
        "1" -> TravelMode.BICYCLE
        "2" -> TravelMode.WALK
        else -> null // 3 is transit, which has no road route to export
    }

    private fun modeFromName(name: String): TravelMode? = when (name.lowercase()) {
        "driving", "two-wheeler" -> TravelMode.DRIVE
        "bicycling" -> TravelMode.BICYCLE
        "walking" -> TravelMode.WALK
        else -> null
    }

    private fun modeFromDirflg(flags: String): TravelMode? = when {
        'r' in flags -> null
        'b' in flags -> TravelMode.BICYCLE
        'w' in flags -> TravelMode.WALK
        else -> TravelMode.DRIVE
    }

    // --- The `data=` parameter -------------------------------------------------------------
    //
    // `data` is a flattened protobuf: "!<field><type><value>" tokens, where an "m" token's value
    // is how many of the following tokens nest inside it. Directions look like
    //   !4m22!4m21!1m5!1m4!1s<feature>!8m2!3d<lat>!4d<lng>!1m5...!2m1!11b1!3e1     (current app links)
    //   !4m14!4m13!1m5!1m1!1s<feature>!2m2!1d<lng>!2d<lat>!1m5...!3e1             (older web links)
    // with one "1m" per stop, holding its coordinates in either of those two places. Each "3m"
    // inside a stop is a point the route was dragged through on the way to the next stop.

    internal class DataNode(val field: Int, val type: Char, val value: String, val children: List<DataNode>) {
        fun child(field: Int, type: Char): DataNode? = children.firstOrNull { it.field == field && it.type == type }
        fun all(field: Int, type: Char): List<DataNode> = children.filter { it.field == field && it.type == type }
    }

    internal class DataStop(val position: LatLng?, val vias: List<LatLng>)
    internal class Directions(val stops: List<DataStop>, val modeCode: String?)

    internal fun parseData(data: String): List<DataNode> {
        val raw = if ('!' !in data) decode(data) else data
        val tokens = raw.split('!').mapNotNull { DATA_TOKEN.matchEntire(it) }
        var next = 0
        fun read(count: Int): List<DataNode> {
            val end = minOf(next + count, tokens.size)
            val nodes = mutableListOf<DataNode>()
            while (next < end) {
                val (field, type, value) = tokens[next++].destructured
                val children = if (type == "m") read(minOf(value.toIntOrNull() ?: 0, end - next)) else emptyList()
                nodes += DataNode(field.toIntOrNull() ?: -1, type[0], value, children)
            }
            return nodes
        }
        return read(tokens.size)
    }

    internal fun readDirectionsData(data: String): Directions? {
        val node = findDirections(parseData(data)) ?: return null
        val stops = node.all(1, 'm').map { stop ->
            DataStop(
                position = lngLatOf(stop.child(2, 'm')) ?: latLngOf(stop.child(1, 'm')?.child(8, 'm')),
                vias = stop.all(3, 'm').mapNotNull { lngLatOf(it.child(1, 'm')) },
            )
        }
        return Directions(stops, node.child(3, 'e')?.value)
    }

    private fun findDirections(nodes: List<DataNode>): DataNode? {
        for (node in nodes) {
            if (node.type != 'm') continue
            if (node.field == 4 && node.children.any { it.field == 1 && it.type == 'm' }) return node
            findDirections(node.children)?.let { return it }
        }
        return null
    }

    /** A "!1d<lng>!2d<lat>" message. */
    private fun lngLatOf(node: DataNode?): LatLng? {
        val lng = node?.child(1, 'd')?.value?.toDoubleOrNull() ?: return null
        val lat = node.child(2, 'd')?.value?.toDoubleOrNull() ?: return null
        return LatLng(lat, lng).takeIf { it.isValid }
    }

    /** A "!3d<lat>!4d<lng>" message, as places store their position. */
    private fun latLngOf(node: DataNode?): LatLng? {
        val lat = node?.child(3, 'd')?.value?.toDoubleOrNull() ?: return null
        val lng = node.child(4, 'd')?.value?.toDoubleOrNull() ?: return null
        return LatLng(lat, lng).takeIf { it.isValid }
    }

    private fun decode(text: String): String =
        try {
            URLDecoder.decode(text, "UTF-8")
        } catch (_: IllegalArgumentException) {
            text
        }
}
