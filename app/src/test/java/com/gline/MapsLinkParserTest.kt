package com.gline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapsLinkParserTest {

    @Test
    fun `extracts the link from shared text`() {
        val text = "Directions to Tower Bridge: https://maps.app.goo.gl/AbCd123XyZ?g_st=ac"
        val url = MapsLinkParser.extractUrl(text)
        assertEquals("https://maps.app.goo.gl/AbCd123XyZ?g_st=ac", url)
        assertTrue(MapsLinkParser.isShortLink(url!!))
        assertFalse(MapsLinkParser.isShortLink("https://www.google.com/maps/dir/A/B"))
        assertNull(MapsLinkParser.extractUrl("no link here"))
    }

    @Test
    fun `reads stops and mode from an app directions link`() {
        val url = "https://www.google.com/maps/dir/Big+Ben,+London/Tower+Bridge,+Tower+Bridge+Rd,+London+SE1+2UP/" +
            "@51.5036,-0.1,14z/data=!3m1!4b1!4m14!4m13!1m5!1m1!1s0x487604c38c8cd1d9:0xb78f2474b9a45aa9" +
            "!2m2!1d-0.1246254!2d51.5007292!1m5!1m1!1s0x48760349331f38dd:0xa8bf49dde1d56467" +
            "!2m2!1d-0.0753565!2d51.5054564!3e1?entry=ttu"
        val route = MapsLinkParser.parse(url)
        assertEquals(TravelMode.BICYCLE, route.mode)
        assertEquals(2, route.waypoints.size)
        assertEquals("Big Ben, London", route.waypoints[0].label)
        assertEquals(LatLng(51.5007292, -0.1246254), route.waypoints[0].position)
        assertEquals("Tower Bridge, Tower Bridge Rd, London SE1 2UP", route.waypoints[1].label)
        assertEquals(LatLng(51.5054564, -0.0753565), route.waypoints[1].position)
    }

    @Test
    fun `reads a link expanded from the Google Maps app's Share directions`() {
        // What https://maps.app.goo.gl/uKxQwShDUDtTNXRX8 redirected to on 2026-10-07.
        val url = "https://www.google.com/maps/dir/Den+Haag+Centraal/Madurodam/De+Pier/data=!4m22!4m21" +
            "!1m5!1m4!1s0x47c5b71762b8a2bf:0xf78ed942c45107c6!8m2!3d52.08108!4d4.32412" +
            "!1m5!1m4!1s0x47c5b74d1c0dd2c7:0xccfe81afc891bc40!8m2!3d52.099076!4d4.2990392" +
            "!1m5!1m4!1s0x47c5ba097854f957:0x83e6ec574cc11686!8m2!3d52.115776999999994!4d4.282714299999999" +
            "!2m1!11b1!3e1?utm_source=mstt_0&g_ep=CAESBzI2LjM5LjQYACCRQSrHASw5NDI2NzcyNyw5NDI5MjE5NSw5NDI5OTUz" +
            "&skid=3d6e9ff1-40cc-44f7-9a2b-71d0ba1c8e11"
        val route = MapsLinkParser.parse(url)
        assertEquals(TravelMode.BICYCLE, route.mode)
        assertEquals(listOf("Den Haag Centraal", "Madurodam", "De Pier"), route.waypoints.map { it.label })
        assertEquals(
            listOf(LatLng(52.08108, 4.32412), LatLng(52.099076, 4.2990392), LatLng(52.115776999999994, 4.282714299999999)),
            route.waypoints.map { it.position },
        )
        assertTrue(route.waypoints.none { it.isVia })
    }

    @Test
    fun `keeps dragged via points in order`() {
        val url = "https://www.google.com/maps/dir/A/B/@51.5,-0.1,13z/data=!4m19!4m18!1m10!1m1!1s0x1:0x2" +
            "!2m2!1d-0.12!2d51.50!3m4!1m2!1d-0.10!2d51.51!3s0x3:0x4!1m5!1m1!1s0x5:0x6!2m2!1d-0.07!2d51.50!3e0"
        val route = MapsLinkParser.parse(url)
        assertEquals(TravelMode.DRIVE, route.mode)
        assertEquals(listOf(false, true, false), route.waypoints.map { it.isVia })
        assertEquals(LatLng(51.51, -0.10), route.waypoints[1].position)
        assertEquals("B", route.waypoints[2].label)
    }

    @Test
    fun `a blank first stop means your location`() {
        val url = "https://www.google.com/maps/dir//Tower+Bridge,+London/@51.5,-0.1,14z/data=!4m9!4m8!1m0" +
            "!1m5!1m1!1s0x48760349331f38dd:0xa8bf49dde1d56467!2m2!1d-0.0753565!2d51.5054564!3e2"
        val route = MapsLinkParser.parse(url)
        assertEquals(TravelMode.WALK, route.mode)
        assertEquals(2, route.waypoints.size)
        assertTrue(route.waypoints[0].meansDeviceLocation)
        assertFalse(route.waypoints[1].meansDeviceLocation)
    }

    @Test
    fun `reads coordinates from the path when there is no data`() {
        val route = MapsLinkParser.parse("https://www.google.com/maps/dir/51.5007,-0.1246/51.5054,-0.0753/")
        assertNull(route.mode)
        assertEquals(listOf(LatLng(51.5007, -0.1246), LatLng(51.5054, -0.0753)), route.waypoints.map { it.position })
    }

    @Test
    fun `reads an api=1 link`() {
        val url = "https://www.google.com/maps/dir/?api=1&origin=Paris&destination=48.8584,2.2945" +
            "&waypoints=Louvre%7CNotre+Dame&travelmode=bicycling"
        val route = MapsLinkParser.parse(url)
        assertEquals(TravelMode.BICYCLE, route.mode)
        assertEquals(listOf("Paris", "Louvre", "Notre Dame", "48.8584,2.2945"), route.waypoints.map { it.label })
        assertEquals(LatLng(48.8584, 2.2945), route.waypoints.last().position)
    }

    @Test
    fun `an api=1 link without origin starts from your location`() {
        val route = MapsLinkParser.parse("https://www.google.com/maps/dir/?api=1&destination=Oxford")
        assertTrue(route.waypoints.first().meansDeviceLocation)
        assertEquals("Oxford", route.waypoints.last().label)
    }

    @Test
    fun `reads a legacy saddr daddr link`() {
        val route = MapsLinkParser.parse("https://maps.google.com/maps?saddr=London&daddr=Oxford+to:Bath&dirflg=b")
        assertEquals(TravelMode.BICYCLE, route.mode)
        assertEquals(listOf("London", "Oxford", "Bath"), route.waypoints.map { it.label })
    }

    @Test(expected = NotDirectionsException::class)
    fun `rejects a place link`() {
        MapsLinkParser.parse(
            "https://www.google.com/maps/place/Tower+Bridge/@51.5055,-0.0754,17z/data=!3m1!4b1!4m6!3m5" +
                "!1s0x48760349331f38dd:0xa8bf49dde1d56467!8m2!3d51.5054564!4d-0.0753565",
        )
    }

    @Test
    fun `survives a truncated data parameter`() {
        val route = MapsLinkParser.parse("https://www.google.com/maps/dir/A/B/data=!4m14!4m13!1m5!1m1!1s0x1:0x2!2m2!1d-0.12")
        assertEquals(listOf("A", "B"), route.waypoints.map { it.label })
        assertNull(route.waypoints[0].position)
    }

    @Test
    fun `follows the continue parameter of a consent page`() {
        val consent = "https://consent.google.com/m?continue=https://www.google.com/maps/dir/A/B&gl=DE"
        assertEquals("https://www.google.com/maps/dir/A/B", LinkResolver.resolve(consent))
    }
}
