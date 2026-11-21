# Route sharing between apps: research

Researched 2026-11-21 for turning GLine from a Google-Maps-to-Beeline converter into a
general route-sharing tool on Google Play.

Every claim below was checked on 2026-11-21. Each is marked:

- **Verified**: the page was opened and read, at the URL given.
- **Unverified**: seen only in a search-result excerpt, a forum or a third-party page, or
  inferred. The source is given where there is one.

## The short version

- **GPX is the common language.** Every app below can import a GPX file (Strava only on its
  website, not its phone app), and every app that exports routes offers GPX. TCX and FIT are second-tier (Komoot, Wahoo and Garmin take them
  too). GLine needs no partner deal to work with any of them through files.
- **No app here offers an open, free route-write API that a Play app can use for everyone.**
  Strava's API cannot create routes, and its terms bar passing data on. Komoot has no
  public API, and its terms bar exporting tours except through its own export button.
  Garmin's Courses API is for approved businesses only, and new approvals look paused.
  Ride with GPS is the exception: its API is open, and since July 2026 it can create routes
  from uploaded files.
- **So the product is a file hub on the phone.** It takes a route in (a Google Maps link, or
  a GPX/TCX/KML file shared or opened from any app), cleans it up, and hands a GPX to the
  app the user picks through Android's normal "open with" and "share" intents. No accounts,
  no OAuth, nothing stored off the phone.
- **Google Maps is the one source with no export**, which is why the link converter is
  GLine's real value. Re-planning the stops on OpenStreetMap routing is fine. Writing
  Google Routes API output into a GPX for Beeline or Komoot is not: Google's Routes API
  terms bar using its content with a non-Google map. The Google-key option should leave
  the store build.
- **The free OSM services are fine for a small app, not for a big one.** Nominatim and the
  FOSSGIS OSRM server both cap at 1 request per second for the whole app across all users,
  and either can switch you off. Cache geocodes, send a proper User-Agent, show attribution,
  and plan a switch if installs grow.
- **The Play Store gate for a first app is the testing rule, not the fee.** It's US$25 once,
  but a new personal account must run a closed test with at least 12 testers opted in for
  14 days in a row before it can publish to production.

## Google Maps

**Route in (to GLine):**
- Google Maps has no built-in GPX export. Every guide found points to third-party converters
  that take a shared directions link. *Unverified* (search excerpts of
  https://www.groovypost.com/howto/export-google-maps-route-data/ and
  https://docs.kurviger.com/route_transfer/exchange_routes_with_google_maps).
- The Android app's **Share directions** sends the route as a text link (`maps.app.goo.gl`
  short link), which GLine already expands and parses. *Verified* in this repo
  (`app/src/main/AndroidManifest.xml`, `MapsLinkParser.kt`).

**Route out of GLine back into Google Maps:** Google Maps does not import GPX on Android.
Google My Maps on the web imports KML/GPX, but that's a web tool. *Unverified* (general
knowledge, not checked today).

**Terms that matter:**
- Google Maps consumer terms (last modified 2026-01-27) say users may not "redistribute or
  sell any part of Google Maps", "mass download or create bulk feeds of the content", or use
  Google Maps to build a navigation dataset for a substitute service. They also bar using
  "any part of Google Maps with other people's products or services" for real-time
  navigation. *Verified*: https://www.google.com/help/terms_maps/ (sections 2.1–2.5).
  - *Inference, not legal advice:* GLine reads only the stops the user chose (names and
    coordinates in the link) and re-plans the line on OpenStreetMap. It copies no Google
    route geometry, so this looks low-risk. The listing shouldn't claim to "export Google
    Maps routes". It should say it re-plans the stops from a shared Maps link.
- Google Maps Platform **Service Specific Terms**, section 19 Routes API (last modified
  2026-10-28): "Customer must not use Google Maps Content from the Routes API in conjunction
  with a non-Google map", and lat/lng may be cached "for up to 30 consecutive calendar days".
  *Verified* by downloading
  https://cloud.google.com/maps-platform/terms/maps-service-terms?hl=en and searching it.
  - *Inference:* a GPX built from Routes API output and opened in Beeline, Komoot or Garmin
    is shown on a non-Google map and kept longer than 30 days. That conflicts with 19.2 and
    19.3. The current "Google routing with your own key" option should not ship in the store
    build.
- Google Maps Platform **Terms of Service** (last modified 2026-08-26), section 3.2.3: "(b)
  No Caching", "(c) No Creating Content From Google Maps Content", "(e) No Use With
  Non-Google Maps", plus "No Re-Creating Google Products or Features". *Verified*:
  https://cloud.google.com/maps-platform/terms?hl=en (downloaded and searched).
- Routes API free tier: "Routes: Compute Routes Essentials" has a free usage cap of 10,000
  per month, then from US$5.00 per 1,000. *Verified*:
  https://developers.google.com/maps/billing-and-pricing/pricing (no effective date on the
  page).

## Strava

**Route in:**
- The API has no endpoint that creates a route. The four Routes endpoints are all reads:
  Export Route GPX, Export Route TCX, Get Route, List Athlete Routes. The only uploads are
  activities. *Verified*: https://developers.strava.com/docs/reference/.
- On the Strava **website**, a GPX can become a route: Dashboard > My Routes > Create New
  Route, then upload. *Unverified* (search excerpt of
  https://support.strava.com/en-us/articles/15402061-how-do-i-create-a-route-from-a-gpx-file).
  Whether it needs a subscription is unclear: a blog says yes, an older forum post says no.
  *Unverified* (https://www.merinomuster.com/downoald-map-instructions,
  https://www.bikeforums.net/18613354-post5.html). No source shows a GPX route import in
  the Strava phone app.
- A July 2026 user report says route import thins out the points of a detailed GPX.
  *Unverified*:
  https://communityhub.strava.com/developers-api-7/how-to-create-a-route-with-a-gpx-file-that-doesn-t-lose-resolution-13717.

**Route out:**
- "You can export any public route from the Strava website" as GPX or TCX. *Verified*:
  https://support.strava.com/en-us/articles/15401749-how-do-i-follow-a-route-on-strava.
- Exporting activity data "is only available on the Strava website". *Verified*:
  https://support.strava.com/hc/en-us/articles/216918437.

**API terms (Agreement and Policy, both effective 2026-06-01):** *Verified*:
https://www.strava.com/legal/api and https://www.strava.com/legal/api_policy.
- Strava data "can only be displayed or disclosed in your Developer Application to that
  user".
- "You may not create applications that compete with or replicate Strava functionality."
- Policy 5.10: "You may not transfer or disclose Strava Data" to any third party except as
  the Agreement allows. Policy 6.2: no caching longer than seven days.
- Policy 5.16 bars running a "pass-through proxy, intermediary, or aggregator" that
  re-exposes the API.
- Policy 5.3 bars any AI/ML use of Strava data, including putting it in a model's context
  window.
- Policy 3.3: Standard Tier apps are subject to subscription requirements, including an
  active Strava subscription.
- *Inference:* pulling a Strava route through the API and handing it to Komoot or Beeline
  would be moving Strava data to another service, which 5.10 and 5.16 seem to bar. Strava
  should be a file source only: the user exports the GPX from the website and shares or
  opens it in GLine.

**Rate limits:** a new app gets 200 requests per 15 minutes and 2,000 per day, and an
athlete capacity of 1 ("Single Player Mode"). Going past 10 athletes needs Strava's review.
*Verified*: https://developers.strava.com/docs/rate-limits/.

**Recent changes:** the API base URL moves to `https://api-v3.strava.com` (available from
2027-01-04). Club endpoints were removed and Explore Segments restricted on 2026-09-01.
*Verified*: https://developers.strava.com/docs/changelog/.

## Komoot

**Route in:** saved routes import as GPX, TCX or FIT, not KML. In the app: Profile > Saved
routes > Import > pick a file. There is a file size limit (no number given), and the advice
is to split big files. *Unverified* (search excerpt of
https://support.komoot.com/hc/articles/10115477099674; the page returns 403 to direct
fetches).

**Route out:** Komoot's GPX export contains only the route geometry, without planning
waypoints, voice cues or annotations. On Android the file usually lands in Downloads.
*Unverified* (same help page, via search excerpt).

**API:** no public developer API was found. `komoot.com/api` is a 404. The B2B site is about
partnerships. A third-party project says Komoot has no official public API. *Verified*
(404 and business page read: https://komoot.business/en); third-party claim *unverified*
(https://glama.ai/mcp/servers/wpk8itzk8x).

**Terms:** Komoot's terms say it is "explicitly prohibited to export, distribute or publish
tours in other ways" than its own export function. Section 12 limits use to "the options
described". *Verified*: https://www.komoot.com/terms-of-service (English translation; the
German text governs).
- *Inference:* GLine must not fetch tours from Komoot links. It may accept a GPX the user
  exported with Komoot's own button, which is the allowed path.

**Komoot Premium:** Beeline's Komoot link needs saved routes to be unlocked, which needs
Komoot Premium for accounts without a map purchase before end of February 2025. *Verified*:
https://support.beeline.co/en/articles/11155841-linking-your-komoot-account-within-the-beeline-app.

## Garmin Connect

**Route in:**
- On Android, users report that opening a GPX with Garmin Connect (from the Files app, or
  shared from WhatsApp) adds it as a course that can sync to the watch. Some report an
  "error loading your course". *Unverified* (forum:
  https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-mobile-andriod/435183/how-to-import-course-on-mobile).
- The Garmin Connect website imports GPX/FIT/TCX courses. *Unverified* (forum reports; the
  official support search returns 403 to direct fetches).

**API:** the Courses API "allows you to publish courses to Garmin Connect" and is "free to
approved business developers", "only for business use". *Unverified* (search excerpts of
https://developer.garmin.com/gc-developer-program/courses-api/ and
https://developer.garmin.com/gc-developer-program/program-faq; both return 404 to direct
fetches today). Since mid-2026, developers report that new access requests are paused.
*Unverified*: https://the5krunner.com/2026/09/14/garmin-developer-api-access-paused/,
https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-mobile-andriod/441607/garmin-connect-api-access-paused-for-months-what-are-startups-supposed-to-do.
A personal API that returns only the user's own data, pull-only, appeared in October 2026.
*Unverified*: https://the5krunner.com/2026/10/22/garmin-personal-api/.

**Context:** Strava sued Garmin in October 2025 over Garmin's API branding rules, then
dropped the case 21 days later. *Unverified* (press:
https://www.androidcentral.com/wearables/garmin/strava-drops-patent-suit-against-garmin-after-a-contentious-21-days).
It shows how touchy these platforms are about data moving between them.

## Beeline

**Route in:** Plan ride > Options > "Import GPX route", files "up to 4MB". Turn-by-turn
imports "must be under 1500km". On Android, choose "always" to make Beeline the default GPX
app. *Verified*:
https://support.beeline.co/en/articles/10570038-importing-and-exporting-gpx-routes (updated
2026-07-30). The import modes are turn-by-turn, breadcrumb and waypoints-only, and imports
are map-matched. Multi-track files should be split into single tracks. *Unverified*
(search excerpts of
https://support.beeline.co/en/articles/11155912-gpx-import-modes-turn-by-turn-breadcrumb-and-waypoint
and https://support.beeline.co/en/articles/11787599-gpx-errors-and-issues).
- GLine today warns above 240 km, from an older Beeline limit. The current page says
  1500 km for turn-by-turn. *Verified* (same page; repo `Converter.kt`).

**Route out:** Options > "Share/ download", then the ridden or the planned route, as GPX.
*Verified* (same page).

**Komoot link:** Settings > Integrations > komoot syncs saved Komoot routes into Beeline
(Android 4.2.5277+). *Verified* (Komoot-link page above).

## Other popular route apps

| App | Route in | Route out | API | Status |
|---|---|---|---|---|
| **Ride with GPS** | GPX/TCX/FIT import | GPX/TCX/FIT export | Open API: anyone may use it without a paid plan or prior approval. OAuth. Since July 2026 it can upload files and create routes. | *Unverified* (search excerpts of https://ridewithgps.com/api/v1/doc/terms, dated 2026-09-11, and https://ridewithgps.com/api/v1/doc/getting_started/change_log) |
| **Wahoo (ELEMNT app)** | Open a FIT, GPX or TCX file on the phone with the Wahoo app, confirm, then Send to ELEMNT. Needs internet for cloud conversion. | — | Wahoo Cloud API exists (not checked) | *Unverified*: https://support.wahoofitness.com/hc/en-us/articles/25936827245970 (search excerpt) |
| **Hammerhead Karoo** | Through the Hammerhead companion app. Users say open-with doesn't list it, and some GPX files fail silently. | — | — | *Unverified* (user forum: https://support.hammerhead.io/hc/en-us/community/posts/4405994426011) |
| **OsmAnd** | Tap a GPX, KML or KMZ file to import; it lands in My Places > Import | Share sends tracks as GPX | Has an AIDL API (not checked) | *Unverified* (search excerpt of https://docs.osmand.net/docs/user/personal/tracks/manage-tracks) |
| **Organic Maps** | GPX/KML/KMZ open-with | KML/GPX share | none | *Unverified* (low-authority page only) |
| **Suunto** | Users share a GPX to the Suunto app on Android | — | partner API | *Unverified* (forum: https://forum.suunto.com/topic/1459/gpx-import-into-sa-how-to-guide-android/50) |
| **AllTrails** | File upload up to 20 MB | GPX export (paid tier, not checked) | none public | *Unverified* (search excerpt: https://support.alltrails.com/hc/en-us/articles/37228498475028-Uploading-files-to-AllTrails) |

## OpenStreetMap services GLine calls

**Nominatim (geocoding place names):** *Verified*:
https://operations.osmfoundation.org/policies/nominatim/.
- "an absolute maximum of 1 request per second", and the limits "apply per
  website/application: the sum of traffic by all your users should not exceed the limits".
- A User-Agent that identifies the app is required ("stock User-Agents ... will not do").
- Caching is expected, and attribution must be shown. The data is ODbL.
- "periodic requests from apps are considered bulk geocoding" and are strongly discouraged.
  This "may be okay if your app has very few users" with caching. Apps must be able to
  switch services at OSMF's request. Autocomplete is forbidden.
- *Inference:* GLine geocodes only on a user's tap, and only stops that have no coordinates
  in the link. That is fine at small scale. It needs an on-device cache and a way to switch
  geocoder (for example a user-supplied key for a commercial one) if installs grow.

**OSRM routing:** the OSRM wiki's old policy is now marked as applying to "the previous demo
server". It forbids selling access, requires ODbL and OSRM attribution and a real
User-Agent, and says access can be "withdrawn at any time". *Verified*:
https://github.com/Project-OSRM/osrm-backend/wiki/Api-usage-policy.
- The current server is run by FOSSGIS at routing.openstreetmap.de: "One request per second
  max", a valid User-Agent, "No scraping, no heavy usage", and attribution with a link to fix
  the map. *Verified*: https://routing.openstreetmap.de/about.html. The full German terms
  (https://www.fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen/) were not read, so
  their position on commercial use is *unverified*.
- *Inference:* a free app with no paywall around routing is the safest fit. Charging for
  the routing step would clash with "selling access".

**Fallbacks if the free servers say no:**
- GraphHopper's free plan gives 500 credits a day, "for non-commercial use only". Paid
  plans start at €69/month. *Verified*: https://www.graphhopper.com/pricing/.
- OpenRouteService's plans page didn't render (script-only). *Unverified*.
- Running our own OSRM server costs hosting money. That's for later, if ever.

## Google Play basics for a first app

*Verified* unless marked:
- **Fee:** "a US$25 one-time registration fee". Google may ask for a government ID and a
  credit card in the legal name. The account holder must be 18+. Personal accounts must
  verify an Android device with the Play Console app before publishing.
  https://support.google.com/googleplay/android-developer/answer/6112435.
- **Testing rule:** personal accounts created after 2023-11-13 need a closed test with "a
  minimum of 12 testers" opted in "continuously for at least 14 days" before applying for
  production. Review "usually takes seven days or less".
  https://support.google.com/googleplay/android-developer/answer/14151465.
  - Third-party guides report 2026 engagement checks that reject tests whose testers barely
    open the app. *Unverified*:
    https://www.testerscommunity.com/blog/google-play-closed-testing-requirements-2026.
  - Organization accounts are said to be exempt (they need a D-U-N-S number). *Unverified*.
- **Data safety form:** required for every app on closed, open or production tracks (only
  internal-test-only apps are exempt), even apps that collect nothing.
  https://support.google.com/googleplay/android-developer/answer/10787469.
  - "'Collect' means transmitting data from your app off a user's device." Data processed
    only on the device needs no disclosure.
  - Sending data to a third party "based on a specific user-initiated action" is not
    "sharing". Neither is sending it to a service provider.
  - Data "processed ephemerally" still goes in the form, but can be flagged as such.
- **Privacy policy:** every app needs one, even apps that touch no personal data. The link
  goes in Play Console and in the app, on a "publicly accessible and non-geofenced URL (no
  PDFs)". It must name a privacy contact and state retention and deletion.
  https://support.google.com/googleplay/android-developer/answer/10144311.
- **Target SDK:** since 2026-08-31, new apps and updates must target API 36 (Android 16).
  https://developer.android.com/google/play/requirements/target-sdk. GLine already targets
  36 (`app/build.gradle.kts`).
- **Developer verification:** from 2026-09-30 in Brazil, Indonesia, Singapore and Thailand,
  and globally in 2027. "Google Play automatically registers 99% of apps", so a Play-only
  app needs nothing extra. https://developer.android.com/developer-verification.
- **Listing rules:** "Your app title must be 30 characters or less". Avoid "repetitive or
  unrelated keywords" and word lists. https://support.google.com/googleplay/android-developer/answer/9898842.
  The impersonation policy bars titles or icons "so similar to those of existing products"
  that users may be misled, and bars falsely implying "an affiliation or relationship with
  another company". https://support.google.com/googleplay/android-developer/answer/9888374.
  - *Inference:* naming a few apps in the long description ("works with GPX files from and
    for apps such as …", plus "not affiliated with …") is common and seems allowed. Brand
    names in the title or icon, or a long list of them, are not.
  - Short description limit of 80 characters: *unverified* (not on the metadata page; widely
    cited).
- **Signing:** the current release build is signed with the debug key
  (`app/build.gradle.kts`). Play needs an upload key, with Play App Signing holding the app
  key. *Verified* in repo; the Play requirement is *unverified* today (well known).

## What this means for GLine

1. **Position it as a phone-side route hub, not an API integrator.** In: a Google Maps
   directions link, or a GPX/TCX/KML file opened or shared from any app. Out: a clean GPX
   handed to the app the user picks, or saved, or shared.
2. **Do the jobs the target apps don't:** re-plan Google Maps stops into a real route, merge
   multi-track files or split them, thin a file under a size limit (Beeline 4 MB, Komoot's
   unstated limit), and turn a TCX or KML into GPX.
3. **Drop the Google Routes key option from the store build** (Routes API terms 19.2/19.3).
   Keep OSRM on the FOSSGIS server and Nominatim, with attribution, a proper User-Agent and
   an on-device geocode cache.
4. **Skip Strava, Komoot and Garmin APIs in v1.** Their terms or access rules close the
   door. Ride with GPS's open API is the one worth a later look.
5. **Nothing leaves the phone except** the Maps link (to Google, to expand it), stop names
   (to Nominatim) and stop coordinates (to the routing server), all on the user's tap. That
   keeps the Data safety form short.
