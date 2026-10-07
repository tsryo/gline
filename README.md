# GLine

An Android app that turns a shared Google Maps directions link into a GPX
route file, ready to import into [Beeline](https://beeline.co) or any other
app that accepts GPX routes.

## How it works

1. In Google Maps, plan a route and use **Share directions**, sending it to
   GLine (or paste the shared text/link directly into the app).
2. GLine expands the link, works out the stops and travel mode, and geocodes
   any place names.
3. The route between stops is fetched from a routing engine — either the free
   [OSRM](https://project-osrm.org/) demo server, or Google's Routes API if
   you supply your own API key.
4. The resulting route is written out as a `.gpx` file and handed straight to
   an installed GPX-capable app (e.g. Beeline), or shared/saved otherwise.

Geocoding of place names uses [Nominatim](https://nominatim.openstreetmap.org/).

## Building

Standard Gradle/Android Studio project.

```
./gradlew assembleDebug
```

Requires a local Android SDK (set `sdk.dir` in `local.properties`, which
Android Studio generates automatically and is not checked into the repo).

## Google routing (optional)

The app defaults to OSRM, which needs no setup. To use Google's routing
instead, enter your own Google Maps Routes API key in the app's settings —
the key is stored only in local app preferences on your device and is never
committed to or read from this repository.

## License

MIT — see [LICENSE](LICENSE).
