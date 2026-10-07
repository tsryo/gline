package com.gline

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val code: Int, val body: String) : IOException("HTTP $code")

/** Blocking HTTP; call from Dispatchers.IO. */
object Http {
    const val USER_AGENT = "GLine/1.0 (Android; Google Maps route to GPX converter)"

    fun get(url: String, headers: Map<String, String> = emptyMap()): String = request("GET", url, headers, null)

    fun post(url: String, body: String, headers: Map<String, String>): String = request("POST", url, headers, body)

    private fun request(method: String, url: String, headers: Map<String, String>, body: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach(conn::setRequestProperty)
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpException(code, text)
            return text
        } finally {
            conn.disconnect()
        }
    }
}

/** Expands maps.app.goo.gl style links to the full google.com/maps URL they stand for. */
object LinkResolver {
    private const val MAX_HOPS = 8

    // The short-link service answers browsers with a plain redirect.
    private const val BROWSER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    private val MAPS_URL_IN_PAGE = Regex("""https://(?:www\.|maps\.)?google\.[a-z.]+/maps[^"'\s<>\\]*""")

    fun resolve(url: String): String {
        var current = url
        repeat(MAX_HOPS) {
            current = when {
                // EU visitors get bounced through a cookie-consent page that carries the target along.
                MapsLinkParser.host(current).startsWith("consent.") ->
                    MapsLinkParser.queryParams(current)["continue"] ?: return current
                MapsLinkParser.isShortLink(current) -> follow(current)
                else -> return current
            }
        }
        return current
    }

    private fun follow(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", BROWSER_AGENT)
            val code = conn.responseCode
            conn.getHeaderField("Location")?.let { return URL(URL(url), it).toString() }
            if (code in 200..299) {
                // Some answers are a page that redirects in script; the target is still in it.
                val page = conn.inputStream.bufferedReader().use { it.readText() }
                    .replace("\\u003d", "=").replace("\\u0026", "&").replace("&amp;", "&").replace("\\/", "/")
                MAPS_URL_IN_PAGE.find(page)?.let { return it.value }
            }
            throw IOException("The short link didn't lead anywhere (HTTP $code)")
        } finally {
            conn.disconnect()
        }
    }
}
