# Clarion

A community anti-robbery SOS app for areas where local police response is slow. Tap **Send a Flare** and everyone nearby who has the app gets a ringing, lock-screen alert that opens straight to a live map of the incident.

## Status

Early scaffold — native Android, Kotlin + Jetpack Compose. Building out screens against the design at [Clarion App Design](https://claude.ai/code/artifact/77b8f526-c2c1-4847-90fd-2d7428578e41).

## Building

Requires JDK 17 and the Android SDK (compileSdk 36).

```
./gradlew assembleDebug
```

Produces a debug APK at `app/build/outputs/apk/debug/`. Pushes to `main` also build automatically via GitHub Actions and publish the APK to the repo's [Releases](../../releases) page as `latest`.
