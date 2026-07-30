# GlassGallery — Phase 0 inventory

HEAD `b3b8f96` on `main`. Read-only reconnaissance. **Inventory only — no proposals.**

Environment: no JDK, no Android SDK, no Gradle, no emulator. `adb` exists at
`/data/data/com.termux/files/usr/bin/adb` but reports **no devices attached**. Anything that
requires a build or a device is marked **unverifiable here**.

---

## 1. Module and package layout

Single module `:app`, package `com.darsma.glassgallery`, **39 `.kt` files, ~13,942 lines**.

```
app/src/main/java/com/darsma/glassgallery/
├── GlassGalleryApp.kt          MainActivity.kt (SharedTransitionLayout host + nav graph)
├── data/          8 files   FavoritesStore, ImageSearchIndexStore, ImageSearchIndexer,
│                            MediaStoreVideoSource, PlaybackStore, SortOrder, Timeline, Video
├── ui/components/ 11 files  GlassBackdrop, GlassShader, Haptics, LiquidGlassModifier,
│                            LiquidGlassUi, MediaDetailsSheet, MiniPlayerNowBar, MorphShapes,
│                            Motion, OpticalGlass, SmoothSeekBar
├── ui/editor/     2   ui/gallery/ 4   ui/photo/ 2   ui/player/ 2
├── ui/theme/      4   GlassTokens, Shape, Theme, Type
├── ui/trash/      1   ui/trim/ 1
└── widget/        2   FavoritesWidget, FavoritesWidgetReceiver (Glance, read-only)
```

Six screen composables: `GalleryScreen` (gallery/GalleryScreen.kt:152), `PhotoViewerScreen`
(photo/PhotoViewerScreen.kt:118), `PlayerScreen` (player/PlayerScreen.kt:119), `PhotoEditorScreen`
(editor/PhotoEditorScreen.kt:125), `VideoTrimScreen` (trim/VideoTrimScreen.kt:117), `TrashScreen`
(trash/TrashScreen.kt:77). Everything else is a sheet or overlay embedded in those six.

## 2. Animations and transitions

**`Motion.kt:45-82` is the sole spring token source** — eight `fun <T> …(): FiniteAnimationSpec<T>`
specs, all `spring(...)`, no `tween`: `snappy` (0.9/High), `standard` (0.85/380), `expressive`
(0.72/300), `spatial` (0.80/235), `morph` (0.62/340), `elastic` (0.56/470), `settle` (1.0/260),
`bouncy` (0.42/520), `gentle` (1.0/VeryLow). Also owns `pressBounce` (:89), `BouncyIconButton`
(:115), `shimmer` (:152).

**Motion is the most consistently tokenised category in the repo.** No raw
`spring(dampingRatio=…, stiffness=…)` exists outside `Motion.kt:49-81`; roughly 45
`animateFloatAsState` / `Animatable` sites all pass a `Motion.*()` token.

**14 remaining `tween(` sites, none driving a live touch value.** All are ambient loops, one-shot
entrances, or autoplay: `LiquidGlassUi.kt:134,160,282`, `GlassShader.kt:45`, `SmoothSeekBar.kt:108`,
`Motion.kt:159`, `OpticalGlass.kt:51`, `MorphShapes.kt:155`, `GalleryScreen.kt:1091`,
`PhotoViewerScreen.kt:664,665,675`. Pinch, pan and seek-drag all resolve through springs
(`SmoothSeekBar.kt:66-70`, `PhotoViewerScreen.kt:181-183`, `PhotoEditorScreen.kt:164-166`).

**4 `infiniteRepeatable` sites remain:** `GlassShader.kt:39-49` (`glassSheen`, ungated — runs
forever while attached), `SmoothSeekBar.kt:98-114` (gated `!dragging && isPlaying`),
`Motion.kt:152-163` (`shimmer`, loading placeholders), `OpticalGlass.kt:45-58` (gated behind
`animated=false` default). `AuroraBackground` (`LiquidGlassUi.kt:71-116`) is explicitly *not*
infinite — a comment records that ambient drift was removed to stop per-frame invalidation.

**Literal durations at call sites** (outside `Motion.kt`): the 14 `tween` sites above carry
hardcoded `durationMillis` values — 90, 260, 560, 720, 1100, 1200, 1200, 5200, 6200, 9600 ms.

**Shared elements:** one `SharedTransitionLayout` at `MainActivity.kt:53`, scope passed to Gallery,
PhotoViewer and Player (`:81,127,162`). Four `.sharedBounds(` sites — `MiniPlayerNowBar.kt:90`,
`GalleryScreen.kt:1007`, `PhotoViewerScreen.kt:329`, `PlayerScreen.kt:275`. **Zero
`sharedElement(`** call sites. **Zero `updateTransition`** anywhere.

`AnimatedContent` ×15. `animateItem` ×7, all with explicit `Motion.*` specs except
`GalleryScreen.kt:1571` (`TrashCard`), which uses the bare default.

**`VideoTrimScreen.kt` contains zero Compose animation API calls** — no `tween`, `spring`,
`Animatable`, `animateFloatAsState`, or `Motion.*`, in a codebase otherwise saturated with springs.

## 3. Glass and shader surface

**Real backdrop sampling: CONFIRMED, not decorative.** `GlassBackdrop.kt` implements a two-stage
RenderNode graph:

- `rememberGlassBackdropState()` (:84-88) owns a `GraphicsLayer` via `rememberGraphicsLayer()` (:86).
- `GlassBackdropHost` (:101-122) records **only** its `source` slot into that layer
  (`recordBackdropSource`, :362-386). The source layer is deliberately never given a `renderEffect`
  — comment at :359 says so explicitly.
- `GlassSurface` (:137-354) builds `BlurEffect(radiusX, radiusY, TileMode.Clamp)` (:225-229),
  assigns it to a **separate** local `effectLayer.renderEffect` (:313), replays the shared source
  layer translated into it (:312-327), then composites (:328).
- Hardware gate: `canvas.nativeCanvas.isHardwareAccelerated` checked at draw time (:285-304).

**Caps and fallback ladder:** `MAX_BLUR_RADIUS_PX = 60f` (:389), `EFFECT_PADDING_MULTIPLIER = 2f`
(:390), `MINIMUM_FALLBACK_ALPHA = 0.22f` (:391). Fallback triggers cover inspection mode,
non-hardware canvas, non-finite radius, `BlurEffect` construction failure, missing
`LayoutCoordinates`, unavailable capture, zero-size source, and any thrown exception — all degrade
to a tinted rect (:335), **never to transparent**.

**AGSL is used only for decoration, not for the blur.** `OpticalGlass.kt` only. Gate:
`SDK_INT >= TIRAMISU` (API 33) at :59 and :67; `OpticalProgram` is `@RequiresApi(TIRAMISU)` (:164).
The shader (`OPTICAL_GLASS_AGSL`, :186-216) computes a caustic light field from UV, time and a light
position uniform — it never samples backdrop pixels and never blurs.

**The AGSL fallback is a divergent second implementation, not the same timeline minus a stage.**
`drawOpticalFallback` (:132-162) is two `Brush.radialGradient`/`linearGradient` draws with hardcoded
stops and a Kotlin-side `sin()` wobble, while the shader path computes procedural caustics and a
hue-shifting spectrum mix (:198-213). They approximate the same idea via independently authored
code and can drift.

`GlassShader.kt:82-105` (`frostedBlur`) is a `@Deprecated` **self**-blur retained for source
compatibility — distinct from the real backdrop path.

## 4. ML Kit call sites — all bundled

| Call site | Artifact | Variant | Failure handling |
|---|---|---|---|
| `ImageSearchIndexer.kt:32` `TextRecognition` | `com.google.mlkit:text-recognition:16.0.1` | **Bundled** | Silently swallowed — both pipelines failing returns `null` (:100-102), caller skips the photo (:62). Documented at :24-25. |
| `ImageSearchIndexer.kt:36` `ImageLabeling` | `com.google.mlkit:image-labeling:17.0.9` | **Bundled** | Partial failure → empty labels, no error (:104-105). |
| `BackgroundBlurProcessor.kt:33-40` `Segmentation` | `com.google.mlkit:segmentation-selfie:16.0.0-beta6` | **Bundled** | Surfaced — `runCatching` + Toast at `PhotoEditorScreen.kt:394-398`. |
| `BarcodeScanSheet.kt:78` `BarcodeScanning` | `com.google.mlkit:barcode-scanning:17.3.0` | **Bundled** | Surfaced — try/catch + Toast at `PhotoViewerScreen.kt:498-517`. |

**No `play-services-mlkit-*` anywhere.** The invariant holds today.

## 5. Tests, CI, lint

- **Zero tests.** No `src/test/`, no `src/androidTest/`, no test source files.
- **Zero test dependencies.** No `testImplementation` / `androidTestImplementation` lines at all.
  `testInstrumentationRunner` at `app/build.gradle.kts:13` is inert boilerplate.
- **CI runs `gradle :app:assembleRelease` only** — no `test`, no `lint`, no `check`.
- **No lint configuration**, no `lint.xml`, no baseline, no detekt/ktlint.
- `gradle/wrapper/gradle-wrapper.jar` present (43,583 B). `gradlew` is **971 B**, a hand-written
  minimal launcher, not the official ~8 KB script. Not executable (`-rw-rw----`).

## 6. Design tokens — partial single source of truth

| Category | Token file | Literals outside `ui/theme/` |
|---|---|---|
| Colour | `Theme.kt:9-25`, `GlassTokens.kt` | **62** `Color(0x…)` across 13 files |
| Shape | `Shape.kt`, `GlassStyle.defaultRadius` | **13** literal `RoundedCornerShape(N.dp)` + 3 `CornerRadius(...)` |
| Elevation | `GlassTokens.kt:39,51` | none competing |
| Typography | `Type.kt` (8 named styles) | **34** literal `N.sp` across 8 files |
| Motion | `Motion.kt` | 14 literal `tween` durations (see §2) |

## 7. APK composition and ABI

`app/build.gradle.kts` contains **no `splits`, no `abiFilters`, no `packaging`, no `bundle` block**.

Native-`.so`-bearing dependencies: `media3-exoplayer`/`-ui`/`-transformer` (1.5.1), `coil-video`
(3.0.4), and all four bundled ML Kit artifacts (TFLite-derived inference libs).
`androidx.graphics:graphics-shapes` (1.0.1) is pure Kotlin.

**Per-ABI sizes are unverifiable here.** Establishing them needs `assembleRelease` or
`bundleRelease` + `bundletool` with a real SDK. The only current measurement point is the CI
artifact.

## 8. Destructive / MediaStore write paths

| Action | Site | API | Reversible |
|---|---|---|---|
| Gallery multi-select delete | `GalleryScreen.kt:733` | `createTrashRequest(…, true)` | **Yes** — 30-day bin |
| Photo viewer delete | `PhotoViewerScreen.kt:457` | `createTrashRequest(…, true)` | **Yes** |
| **Player video delete** | **`PlayerScreen.kt:412`** | **`createDeleteRequest`** | **NO — permanent** |
| Trash restore | `TrashScreen.kt:164` | `createTrashRequest(…, false)` | Reverses a trash |
| Trash purge | `TrashScreen.kt:170` | `createDeleteRequest` | No — intended |
| Editor save | `PhotoEditorScreen.kt:860-872` | `resolver.insert` + `IS_PENDING` | Non-destructive, new file |
| Trim save | `VideoTrimScreen.kt:804-830` | `resolver.insert` + `IS_PENDING` | Non-destructive, new file |

No direct `contentResolver.delete()` and no `File.delete()` on user media anywhere. All destructive
paths go through `IntentSender` system confirmation.

---

## Contradictions and surprises

1. **🔴 The player's delete button permanently destroys video while the visually identical photo
   button is recoverable.** `PlayerScreen.kt:412` calls `createDeleteRequest`; `PhotoViewerScreen.kt:457`
   calls `createTrashRequest(…, true)`. Same `BouncyIconButton`, same `size = 46.dp`, same
   `deleteLauncher`, same icon position — opposite consequence, no undo window, and the comment at
   `PlayerScreen.kt:226-227` does not mention the asymmetry. **Verified directly by the conductor,
   not accepted from the scout.**
2. **The blur is real but the shader is decoration** — the inverse of what the programme prompt
   assumes on both counts. Backdrop sampling genuinely captures and re-blurs a `GraphicsLayer`;
   AGSL never touches the backdrop and is a pure overlay.
3. **The AGSL fallback is a second implementation**, not a degraded single timeline.
4. **ML Kit failure handling is deliberately inconsistent** — the background indexer swallows
   silently by design (documented), the interactive paths Toast. Inspecting only the interactive
   sites gives the wrong impression.
5. **CI has never run a test or a lint check** — `assembleRelease` only. R8/shrink correctness is
   verified by successful compilation alone.
6. **`VideoTrimScreen` uses no Compose animation at all**, uniquely among the six screens.
7. **`gradlew` is 971 B, hand-written, and not executable.** CI never exercises it (it uses
   `gradle` from `setup-gradle`), so `./gradlew` has likely never run in this project.
