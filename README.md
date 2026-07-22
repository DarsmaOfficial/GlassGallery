# Glass Gallery

![Build](https://github.com/DarsmaOfficial/GlassGallery/actions/workflows/build.yml/badge.svg)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-31-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)

A native Android media gallery built entirely with Jetpack Compose, with a distinctive
**"liquid glass"** aesthetic inspired by Apple's Photos app — translucent gradient
surfaces, specular light sheens, and physics-based spring motion throughout, without
a single backdrop blur.

## Features

- **Unified media grid** — photos and videos together, with a timeline view grouped
  by date behind a sticky frosted chip.
- **Dynamic Island search** — tapping search dissolves the whole gallery into a
  floating glass capsule that morphs into a live, ranked results chamber as you type.
- **Albums** — auto-grouped folder shelf with a 30-day recycle bin (system trash,
  not a permanent delete).
- **Photo editor** — non-destructive crop/rotate/flip and color adjustment, saved as
  a new `MediaStore` copy with full undo/redo history.
- **Video player** — custom seek bar, double-tap-to-seek ripple, predictive-back
  gesture, and a MiniPlayer that morphs into the full player via a shared element
  transition.
- **Favorites, multi-select, and sort** — bulk share/favorite/delete backed by
  system-confirmed `MediaStore` operations.
- **Pinch-to-resize grid density**, remembered per tab.

Everything runs fully offline against local `MediaStore` content — no backend, no
accounts, no analytics, no ads.

## Tech stack

- 100% [Jetpack Compose](https://developer.android.com/jetpack/compose), no XML views
- [Media3 ExoPlayer](https://developer.android.com/media/media3) for video playback
- [Coil 3](https://coil-kt.github.io/coil/) for image/video thumbnail loading
- AGSL runtime shaders for glass sheen effects, gated behind API 33 with a graceful
  gradient-only fallback on older devices
- Compose's `SharedTransitionLayout` for grid ↔ fullscreen and MiniPlayer ↔ player
  morphs

## Building

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
git clone https://github.com/DarsmaOfficial/GlassGallery.git
cd GlassGallery
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. CI builds and
uploads it as an artifact on every push to `main` — see the badge above.

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_VIDEO` / `READ_MEDIA_IMAGES` (API 33+) | Read local videos and photos |
| `READ_EXTERNAL_STORAGE` (API ≤32) | Same, for pre-scoped-storage devices |

No network permission, no ads SDK, no analytics.
