# BRIEF T004 — Make the no-network invariant actually true

**Task ID:** `T004`
**Type:** invariant repair. Highest-consequence change in the programme so far.
**Branch:** `feature/manifest-network-gate` — **same branch as T003**, new commit on `6e8dc1e`
**Worktree:** `/mnt/sdcard/Coding/wt/T003` (already exists)

---

## Why this shares T003's branch

T003 added the CI gate. CI run **#73 proved the gate works by failing**: the built APK contains two
banned permissions merged in from dependencies. Merging the gate on its own would turn `main`
permanently red and block every later task. The gate and the repair therefore land together, and one
green CI run proves both.

## What run #73 found

Source manifest declares three permissions. The built APK contains **eight**:

```
android.permission.READ_MEDIA_VIDEO          declared, keep
android.permission.READ_MEDIA_IMAGES         declared, keep
android.permission.READ_EXTERNAL_STORAGE     declared, keep
android.permission.ACCESS_NETWORK_STATE      ← REMOVE (this task)
android.permission.INTERNET                  ← REMOVE (this task)
android.permission.WAKE_LOCK                 ← leave alone, see scope note
android.permission.RECEIVE_BOOT_COMPLETED    ← leave alone, see scope note
android.permission.FOREGROUND_SERVICE        ← leave alone, see scope note
```

Likely contributed by bundled ML Kit → `com.google.mlkit:common` →
`com.google.android.gms:play-services-basement`. Unconfirmed.

## Scope — remove exactly two, and no more

**Remove only `INTERNET` and `ACCESS_NETWORK_STATE`.** Those are the invariant. The other three are a
separate question with a different risk profile: `FOREGROUND_SERVICE`, `WAKE_LOCK` and
`RECEIVE_BOOT_COMPLETED` together look like a real scheduler or worker component, and stripping them
could break background behaviour in a way no build can detect. They are filed for their own decision.
Conflating them here would add runtime risk to the change that repairs the invariant.

## Required change — `app/src/main/AndroidManifest.xml` only

1. Add the tools namespace to the `<manifest>` root, alongside the existing `xmlns:android`:

   ```
   xmlns:tools="http://schemas.android.com/tools"
   ```

2. Add **two separate** removal directives beside the existing `<uses-permission>` elements:

   ```xml
   <uses-permission
       android:name="android.permission.INTERNET"
       tools:node="remove" />
   <uses-permission
       android:name="android.permission.ACCESS_NETWORK_STATE"
       tools:node="remove" />
   ```

   **Keep them as two independent elements.** They carry different runtime risk and must be
   revertible one at a time — see the rollback note below.

3. Add a short comment above them explaining what they are and why, so the next reader does not
   "tidy up" what looks like two permissions being requested. State that they are merged in by a
   dependency and removed to preserve the no-network invariant, and reference CI run #73.

**Change nothing else.** Do not touch the three declared permissions, the `<application>` element,
the activity, the receiver, or any Gradle file, Kotlin file, workflow, or `tools/` script.

## The risk you must not paper over

`tools:node="remove"` deletes the permission from the merged manifest. It does **not** stop library
code from trying to use it. Two distinct failure modes, which is exactly why the two directives stay
separate:

- **Removing `INTERNET`** — a socket attempt fails. Bundled ML Kit runs on-device, so this should be
  inert, but it is an expectation, not a measurement.
- **Removing `ACCESS_NETWORK_STATE`** — this is the riskier of the two. A `ConnectivityManager`
  query without it throws `SecurityException`, and library code that checks connectivity
  *defensively* — including for on-device work — would crash rather than degrade. If the app starts
  crashing on ML Kit features after this change, **`ACCESS_NETWORK_STATE` is the prime suspect and
  can be reverted alone** while keeping the `INTERNET` removal.

Neither failure mode is detectable by compilation. CI going green proves the merged manifest is
clean; it proves nothing about runtime.

## Constraints

- Commit author **must** be `DarsmaOfficial <darsmaofficial@gmail.com>`. **No `Co-Authored-By`, no
  `Signed-off-by`, no AI/model/vendor name** anywhere.
- New commit on `feature/manifest-network-gate`. **DO NOT PUSH.**
- No new dependency, no Gradle change, no workflow change. T003's gate stays exactly as it is.
- Do not add any other permission, and do not remove any of the three declared ones.

## Verification

You cannot build, run, or merge a manifest here — no JDK, no Android SDK, no Gradle. Do not pretend
otherwise. Instead:

1. Confirm the XML is well-formed using Python's stdlib `xml.etree.ElementTree`. Paste the result.
2. Confirm the `tools` namespace is declared on the root element and that both directives carry
   `tools:node="remove"`.
3. Print the full file and the diff.
4. State explicitly that whether the merged manifest is actually clean is **unknown until CI run
   #74**, and that runtime behaviour is unknown until it runs on hardware.

## Definition of done

1. Tools namespace declared on `<manifest>`.
2. Exactly two `tools:node="remove"` directives, as separate elements, with an explanatory comment.
3. The three declared permissions untouched.
4. `git diff` shows exactly one file changed.
5. One commit, correct author, no trailers, not pushed.

## What to report

- Commit SHA, full diff, full resulting file.
- XML well-formedness check output.
- Explicit statement that the merged result is unverified until CI, and runtime unverified until
  hardware.
- Anything you noticed that this brief did not anticipate.
