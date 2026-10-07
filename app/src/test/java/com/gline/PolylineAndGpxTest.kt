package com.gline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PolylineAndGpxTest {

    @Test
    fun `decodes Google's reference polyline`() {
        val points = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].lat, 1e-9)
        assertEquals(-120.2, points[0].lng, 1e-9)
        assertEquals(40.7, points[1].lat, 1e-9)
        assertEquals(-120.95, points[1].lng, 1e-9)
        assertEquals(43.252, points[2].lat, 1e-9)
        assertEquals(-126.453, points[2].lng, 1e-9)
    }

    @Test
    fun `decodes at precision 6`() {
        // The reference polyline's first point, with values scaled for OSRM's 6-digit precision.
        val points = Polyline.decode("_izlhA~rlgdF", precision = 6)
        assertEquals(38.5, points[0].lat, 1e-9)
        assertEquals(-120.2, points[0].lng, 1e-9)
    }

    @Test
    fun `writes stops as wpt and the line as one trk`() {
        val stops = listOf(
            Stop("Fish & Chips <Shop>", LatLng(51.5, -0.12), isVia = false),
            Stop("Via point", LatLng(51.505, -0.11), isVia = true),
            Stop("Tower Bridge", LatLng(51.5054564, -0.0753565), isVia = false),
        )
        val track = listOf(LatLng(51.5, -0.12), LatLng(51.505, -0.11), LatLng(51.5054564, -0.0753565))
        val gpx = GpxWriter.write("Fish to Bridge", stops, track, Instant.parse("2026-10-07T10:00:00.123Z"))

        assertTrue(gpx.contains("""<wpt lat="51.500000" lon="-0.120000"><name>Fish &amp; Chips &lt;Shop&gt;</name></wpt>"""))
        assertTrue(gpx.contains("""<wpt lat="51.505456" lon="-0.075357"><name>Tower Bridge</name></wpt>"""))
        assertFalse("via points aren't stops", gpx.contains("Via point"))
        assertEquals(2, Regex("<wpt ").findAll(gpx).count())
        assertEquals(1, Regex("<trk>").findAll(gpx).count())
        assertEquals(3, Regex("<trkpt ").findAll(gpx).count())
        assertFalse(gpx.contains("<rte"))
        assertTrue(gpx.contains("<time>2026-10-07T10:00:00Z</time>"))
    }
}
