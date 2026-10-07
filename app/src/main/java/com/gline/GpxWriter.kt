package com.gline

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Writes plain GPX 1.1 shaped for Beeline's importer: the stops as `<wpt>` (its waypoints-only
 * mode reads only those) and the whole road-following line as one `<trk>` (its turn-by-turn and
 * breadcrumb modes use the first track). No `<rte>` and no extensions, which Beeline rejects or
 * would read in preference to the track.
 */
object GpxWriter {

    fun write(name: String, stops: List<Stop>, track: List<LatLng>, time: Instant = Instant.now()): String =
        buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<gpx version="1.1" creator="GLine" xmlns="http://www.topografix.com/GPX/1/1" """ +
                    """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                    """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""",
            )
            appendLine("  <metadata>")
            appendLine("    <name>${escape(name)}</name>")
            appendLine("    <time>${DateTimeFormatter.ISO_INSTANT.format(time.truncatedTo(ChronoUnit.SECONDS))}</time>")
            appendLine("  </metadata>")
            for (stop in stops.filterNot { it.isVia }) {
                appendLine("""  <wpt ${coords(stop.position)}><name>${escape(stop.name)}</name></wpt>""")
            }
            appendLine("  <trk>")
            appendLine("    <name>${escape(name)}</name>")
            appendLine("    <trkseg>")
            for (point in track) {
                appendLine("""      <trkpt ${coords(point)}/>""")
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }

    private fun coords(p: LatLng) =
        """lat="${String.format(Locale.ROOT, "%.6f", p.lat)}" lon="${String.format(Locale.ROOT, "%.6f", p.lng)}""""

    internal fun escape(text: String): String = buildString {
        for (c in text) {
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c == '\'' -> append("&apos;")
                c < ' ' && c != '\t' && c != '\n' && c != '\r' -> Unit // not allowed in XML 1.0
                else -> append(c)
            }
        }
    }
}
