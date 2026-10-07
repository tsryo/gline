package com.gline

import kotlin.math.pow

/** Google's encoded polyline format, used by both Google (precision 5) and OSRM (precision 6). */
object Polyline {

    fun decode(encoded: String, precision: Int = 5): List<LatLng> {
        val factor = 10.0.pow(precision)
        val points = ArrayList<LatLng>()
        var index = 0
        var lat = 0L
        var lng = 0L

        fun nextValue(): Long {
            var result = 0L
            var shift = 0
            var b: Int
            do {
                require(index < encoded.length) { "Truncated polyline" }
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }

        while (index < encoded.length) {
            lat += nextValue()
            lng += nextValue()
            points += LatLng(lat / factor, lng / factor)
        }
        return points
    }
}
