---
description: Compose and motion conventions for the GlassGallery native Android app
paths:
  - "**/*.kt"
  - "**/*.kts"
  - "**/*.agsl"
  - "**/AndroidManifest.xml"
---

# Android / Compose conventions

These apply to native Kotlin and Jetpack Compose. The Darsma frontend standards do **not** apply
here — they are scoped to `**/*.html`, `**/*.css`, `**/*.js` and never load for a `.kt` file.
There is no browser, no CSS, no bundler, and no single-file deliverable in this project.

## The invariant

The app declares **no INTERNET permission**. Never add `android.permission.INTERNET` or
`ACCESS_NETWORK_STATE` to any manifest, and never add a dependency that contributes one through
manifest merging. A feature that cannot work offline with nothing downloaded at runtime does not
ship. Only **bundled** ML Kit (`com.google.mlkit:*`) is permitted; the unbundled
`play-services-mlkit-*` artifacts fetch models at runtime and are banned.

## Motion

- **Spring-first.** Every animation references a named token from `ui/components/Motion.kt`. No
  literal `stiffness` or `dampingRatio` at a call site.
- **Never `tween` anything a finger drives.** `tween` cannot preserve velocity across an
  interruption; `spring` can. `tween` is acceptable only for non-interactive, fixed-duration
  effects, and each use carries a comment saying why.
- **Every gesture-driven animation is interruptible.** Catching a moving animation redirects from
  its *current value and current velocity*. It must never snap, restart, or jump to the target.
- **Velocity handoff is mandatory.** When a drag ends, feed release velocity into the spring via
  `Animatable.animateTo(initialVelocity = …)` or `animateDecay`. Discarding it and starting from
  zero is the most common way motion stops feeling physical.
- **No perpetual animation for decoration.** Motion responds to what the user just did. Animation
  on a timer costs frames forever and invalidates every frame it touches.
- **Duration discipline.** ~100 ms for simple feedback, 200–300 ms for a screen change, 400 ms only
  for large movement. Additive per-item entrance delay is latency, not motion.
- **Dragging is far stricter than tapping** — direct manipulation tolerates roughly an order of
  magnitude less latency, so anything the finger tracks gets the tightest budget.

## Reduced motion

A first-class path, not an afterthought. Check `ValueAnimator.areAnimatorsEnabled()` and respect the
system duration scale. With animator duration scale at 0, **every** animation resolves instantly to
the correct final state, with no crash and no stuck intermediate state. Prefer swapping the animated
property (translate → opacity) over deleting a transition outright.

## Compose correctness

- **Stable keys in every lazy list** — `items(list, key = { it.id })`. Without them `animateItem()`
  cannot track a moved element and reuse breaks.
- **No side effects in composition.** No I/O, no external mutation, no dispatcher work in a
  composable body. Use `LaunchedEffect`, `DisposableEffect`, or `rememberCoroutineScope`.
- **Never block the main thread** — no file, MediaStore, decode, or ML work on it.
- **Defer state reads.** Prefer lambda-taking modifiers (`Modifier.offset { }`, `graphicsLayer { }`,
  `drawBehind { }`) so an animating value is read at layout or draw instead of recomposing the tree
  every frame. Recomposition during scroll is almost always unnecessary.
- **Hoist state.** Composables take state and emit events; they do not own business logic.
- **Prefer stable parameters.** Strong skipping is on, but an unstable parameter still defeats it.

## Touch and accessibility

- **48 dp minimum touch target**, regardless of the visual size of the thing being touched.
- Every interactive element has a TalkBack label; every media item has a content description.
- State changes are announced. Focus order follows visual order. Contrast is checked, not assumed.
- Accessibility is a merge gate, not a nicety.

## Destructive operations

- **Deletion of user media defaults to recoverable.** Use `MediaStore.createTrashRequest(…, true)`
  so the item lands in the OS 30-day bin. `createDeleteRequest` is permanent and is reserved for an
  explicit "delete forever" action inside the Trash screen, where the user has already opted in.
- Identical-looking affordances must have identical consequences. A delete button that destroys
  permanently on one screen and recoverably on another is a data-loss bug regardless of the dialog.
- Any change to a write, move, trash, or delete path gets a test before the change, not after.

## Glass and shaders

- Glass is built from real backdrop sampling where available, plus gradient, sheen, edge treatment
  and layered translucency. The fallback ladder must degrade to tint, never to transparent.
- Any `RuntimeShader`/AGSL path is **API 33+** and requires a fallback. Prefer the fallback be the
  same timeline with the shader stage omitted. Where a genuinely separate algorithm is unavoidable,
  say so in a comment at both sites — two independently authored paths drift.
- Every translucent layer multiplies GPU fill cost, and overdraw hurts far more on a weak GPU than
  on a flagship. Measure overdraw before stacking another translucent surface.

## Manifest

- No new permission without written justification in the task brief.
- Keep `android:enableOnBackInvokedCallback="true"` — predictive back depends on it.
- Do not add `exported="true"` to any component that does not need it.
