# CLAUDE.md — GlassGallery

Native Android media gallery. Kotlin + Jetpack Compose. Package `com.darsma.glassgallery`.

**The Darsma workspace `CLAUDE.md` and `.claude/rules/frontend.md` do not apply here.** They were
written for single-file HTML/CSS/JS deliverables — inline CSS, `backdrop-filter`, CSS `linear()`
curves, verify at 412×915 in a browser. None of that exists in this repo. `frontend.md` is
path-scoped to `**/*.html`, `**/*.css`, `**/*.js`, so it never loads for a `.kt` file: it is
silently inert here rather than loudly wrong. Compose and motion conventions live in
`.claude/rules/android.md`, scoped to `**/*.kt`, `**/*.kts`, `**/*.agsl`, `**/AndroidManifest.xml`.

---

## The invariant: no INTERNET permission

This app declares no `android.permission.INTERNET`. Keep it that way. That single fact is a stronger
zero-cost guarantee than any denylist — a paid API, a metered endpoint, a converting free tier, a
keyed SDK, or an account wall is **structurally impossible in a process that cannot open a socket**.

Every feature must work with zero network permission and zero runtime downloads. If an
implementation drifts toward either, stop and report rather than weakening the invariant.

**Banned regardless of convenience:** Firebase, Crashlytics, Google Analytics, Play Integrity, any
ads SDK, Google Maps/Places, any raster map tile provider, cloud inference, Sentry/Bugsnag or any
hosted error reporting, any asset without an open licence.

**Banned specifically: unbundled ML Kit.** `com.google.android.gms:play-services-mlkit-*` is free
but pulls models through Play Services *at runtime*. That breaks the offline invariant. Only the
bundled `com.google.mlkit:*` artifacts already here are permitted.

**Preferred:** AndroidX/Jetpack, Media3, Coil 3, bundled ML Kit, MediaPipe or TFLite with models
bundled in the APK, Glance, `androidx.biometric`, `androidx.exifinterface`, `androidx.palette`,
`androidx.graphics.shapes`, `androidx.window`, Compose test, Macrobenchmark, OSS Gradle plugins.

`.claude/hooks/guard-cost.py` blocks edits that introduce any of the above, including a manifest
edit adding INTERNET. It has a test suite: `python3 .claude/hooks/test_guard_cost.py`.

## Build facts

`minSdk 31` · `compileSdk 35` · `targetSdk 35` · JDK 17 · Kotlin 2.1.0 (strong skipping on) ·
AGP 8.7.0 · Gradle 8.10.2 · single module `:app`.

Compose BOM `2024.12.01` → **Compose UI 1.7.6**, but `compose.animation` is pinned **separately at
1.7.8**. Check the right artifact before assuming a version. Consequences that bite:

- `HapticFeedbackType` at 1.7.6 has only `LongPress` and `TextHandleMove`. `Confirm`, `Reject`,
  `ToggleOn/Off` need Compose UI **1.8.0**. Use the platform `VibrationEffect.Composition`
  primitives (`PRIMITIVE_LOW_TICK`/`SPIN`/`THUD`, API 31) instead of upgrading for haptics alone.
- Shared elements need `compose.animation` ≥1.7.0 — available. Still `@ExperimentalSharedTransitionApi`.
- Overscroll customisation at Foundation 1.7.6 is `LocalOverscrollConfiguration` +
  `OverscrollEffect.effectModifier`, both deprecated in 1.8.0. `FlingBehavior` is stable — use it.

**Kotlin and Compose only. No XML views.** Layout, styling and animation are Compose.

## Glass and shaders — what is actually true here

- **The blur is real.** `GlassBackdrop.kt` captures the content beneath chrome into a
  `GraphicsLayer` and re-blurs it in a *separate* effect layer with `BlurEffect`. This is genuine
  backdrop sampling, not stacked gradients. Blur is capped at 60 px; the fallback ladder degrades to
  tint and **never to transparent** (`MINIMUM_FALLBACK_ALPHA = 0.22f`).
- **The source slot must contain only background and content.** Any chrome recorded into it makes
  the capture recursive and renders blank or black.
- **AGSL is decoration, not the blur.** `RuntimeShader` appears only in `OpticalGlass.kt`, gated
  `SDK_INT >= TIRAMISU` (**API 33**, not 31 — `RenderEffect` is API 31 but
  `createRuntimeShaderEffect` is API 33). It computes a light field from UV and time and never
  samples backdrop pixels.
- **The AGSL fallback is currently a divergent second implementation** (`drawOpticalFallback`), not
  the same timeline with a stage omitted. Treat that as known debt: new shader work must use one
  timeline, one token set, one state machine.
- `PlayerView` draws through a `SurfaceView` that a layer capture physically cannot see. Player
  chrome can never be blurred, and video can never be a shared-element participant — official docs
  state there is no View/Compose interop for shared elements.

## Destructive paths — the standing hazard

Deletion of user media **defaults to recoverable**: `MediaStore.createTrashRequest(…, true)`, which
lands in the OS 30-day bin. `createDeleteRequest` is permanent and belongs only to an explicit
"delete forever" action inside Trash.

🔴 **Known bug: `PlayerScreen.kt:412` uses `createDeleteRequest`** while the visually identical
photo button (`PhotoViewerScreen.kt:457`) uses `createTrashRequest`. Same control, same size, same
launcher, opposite consequence. Fix before any feature work touches deletion.

Identical-looking affordances must have identical consequences. Any change to a write, move, trash
or delete path gets its test written **before** the change.

## Commands

```bash
./gradlew assembleDebug          # NOTE: gradlew here is a 971-byte hand-written script,
./gradlew lint                   # not executable, and never exercised by CI. Fix or
./gradlew test                   # regenerate it before relying on these.
./gradlew connectedAndroidTest   # CI uses `gradle` from setup-gradle, not ./gradlew.
python3 .claude/hooks/test_guard_cost.py
```

There is no JDK, Android SDK, Gradle, emulator or attached device in the authoring environment.
**GitHub Actions CI is the only automated verifier and it proves compilation and packaging only** —
never rendering, never runtime, never feel. `adb` exists in Termux, so a physically connected or
wirelessly paired device makes the device-side gates reachable.

## THE GREEN GATE

Nothing merges unless **all nine** pass. State explicitly which were run and which were not.

1. `./gradlew assembleDebug` succeeds with **zero new warnings**.
2. `./gradlew lint` reports **no new errors** against the baseline.
3. Unit and Compose UI tests pass. **New behaviour has new tests.**
4. The **merged** manifest contains no `INTERNET` and no `ACCESS_NETWORK_STATE` — merged, not just
   source, so a transitive dependency cannot smuggle one in. CI fails the build and prints the
   permission diff.
5. Macrobenchmark shows **no regression** in frame timing against the committed baseline.
6. **Reduced motion verified:** with animator duration scale 0, every animation resolves instantly
   to the correct final state, with no crash and no stuck intermediate.
7. **Verified on real hardware:** OnePlus 13 phone **and** OnePlus Pad 3 tablet, both orientations,
   gesture navigation, dark and light.
8. No new dependency violating the invariant. **APK size delta reported**, per ABI where relevant.
9. Ledger updated, worktree removed, verdict written.

### What CI actually checks today — read this before trusting a green tick

CI runs **`gradle :app:assembleRelease` and nothing else.** It does not run `lint`, does not run any
test, and does not inspect the merged manifest. So of the nine gates:

| Gate | Enforced by CI today? |
|---|---|
| 1 compile | **partly** — it builds, but `--warning-mode` is not set, so "zero new warnings" is unchecked |
| 2 lint | ❌ no lint step exists, and there is no config or baseline |
| 3 tests | ❌ no test step, no test source set, no test dependencies |
| 4 merged manifest | ✅ **enforced** — `tools/check_manifest_permissions.sh` runs `aapt2 dump permissions` on the built APK and fails the build if either network permission is present. Fails closed on missing APK, missing/failing `aapt2`, empty output, or unparseable entries. Prints the full permission list on success. **Boundary below.** |
| 5 macrobenchmark | ❌ no module, no baseline |
| 6 reduced motion | ❌ needs an instrumented test that does not exist |
| 7 device | ❌ needs hardware |
| 8 dependency/APK delta | manual review |
| 9 ledger/verdict | manual |

Since CI now builds **every** branch, a green tick appears on everything — which makes it far easier
to read "CI passed" as "the gate passed". It does not mean that. **A green tick currently means the
code compiles and packages. Nothing more.**

**Never report a gate as passed because it was expected to pass.** State which gates ran and which
did not, every time. An unverified claim of success is worse than an admitted gap.

### Gate 4's exact boundary — do not overstate it

CI fails the release build if the **packaged APK** requests `INTERNET` or `ACCESS_NETWORK_STATE`.
That is all it proves. It does **not** prove the app causes no network traffic:

- **Delegated network needs no permission in this process.** `startActivity(ACTION_VIEW, https://…)`,
  a custom tab, a share intent, or binding a service in another app can all move data off-device.
  Unbundled ML Kit's model download happens in the Play Services process — the very threat this
  project bans it for is **outside** what this gate can see.
- **Debug and test variants are unchecked.** CI builds release only.
- **Other permissions are unchecked.** The gate is a two-item denylist, not an allowlist. A
  dependency adding `AD_ID`, `QUERY_ALL_PACKAGES` or a location permission passes green — and so
  does the *removal* of an expected permission. The permission list is printed on success so a human
  can spot it; nobody reads passing logs. An expected-permissions allowlist is filed as a task.
- Raw sockets and NDK network genuinely fail without `INTERNET`, so the socket claim itself is sound.

**A history worth remembering:** the invariant was false for the entire life of this project. The
source manifest declared three permissions; the shipped APK carried `INTERNET` and
`ACCESS_NETWORK_STATE` merged in from a dependency, plus three more nobody requested. CI run **#73**
found it on the gate's first contact with a real APK; run **#74** confirmed the repair.
"Declared in source" silently stood in for "present in the artifact" for months.

## Working agreement

- One task, one brief in `ops/briefs/`, one worktree, one green gate. Do not batch features.
- Read `ops/ledger.md` first; finish in-flight work before starting new work.
- Never let an agent judge its own work — verification goes to `red-team` with fresh context.
- Never merge a diff you have not read line by line.
- On failure: rewrite the brief. Second failure: change agent. Third: stop and ask.
- Every commit is authored `DarsmaOfficial <darsmaofficial@gmail.com>`. **No `Co-Authored-By`, no
  `Signed-off-by`, no AI or vendor name** in any commit, file, comment, or CI config.
- Ask before anything irreversible — force-push, history rewrite, branch/release/file deletion.
- Never echo a token. Use an ephemeral `git -c credential.helper=…`, never `remote set-url`, never a
  stored helper. Shred afterwards and verify nothing persisted in `.git/config`.
- **Say what you did not test.** That admission is worth more than a confident claim.
