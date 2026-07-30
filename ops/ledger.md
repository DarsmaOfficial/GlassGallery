# GlassGallery — task ledger

Read this first, every session. Finish in-flight work before starting new work.

---

## Programme state

Phase 0 complete. Phase 1 (council) **not started** — deliberately deferred so the delete bug could
land first. Three prompt premises were contradicted by the inventory and must be corrected in the
council briefs before C1–C3 are dispatched. See "Open decisions" below.

---

## T001 — Make video deletion recoverable ✅ MERGED

**`origin/main` = `d077345`** (was `b3b8f96`), clean fast-forward. Contributors still **1** (52).
CI **#65 green** (feature branch); **#66** triggered on main. Worktree removed, branch deleted.

- Brief: `ops/briefs/T001-player-delete-recoverable.md`
- Verdict: `ops/returns/T001.verdict.md`

`PlayerScreen.kt:416` now calls `createTrashRequest(…, true)` instead of `createDeleteRequest`, so
deleting a video is recoverable rather than permanent. `player.pause()` added so audio stops before
the system sheet appears. Ships `tools/check_destructive_calls.py`, proven four ways.

**Green gate: 2 of 9 verifiable.** Gates 1–3 and 5 have never executed in this project's history;
gate 7 needs hardware. Do not read the merge as a full pass.

🔴 **Device check outstanding, highest value:** trash a camera-shot video, open Trash, confirm it is
listed and Restore works. The app holds only `READ_MEDIA_VIDEO`/`READ_MEDIA_IMAGES` — no
`MANAGE_MEDIA` — so MediaProvider may hide trashed rows the app does not own, which would make the
new comment's "recoverable from the Trash screen" false for the common case.

---

## T002 — CI unbypassable by branch name ✅ MERGED

**`origin/main` = `1feff98`** (was `d077345`), clean fast-forward. One file, +5/−1.
Verdict: `ops/returns/T002.verdict.md` · Brief: `ops/briefs/T002-ci-trigger-all-branches.md`

`branches: [ '**' ]` plus a concurrency block that cancels superseded runs on feature branches but
**never on `main`** (a main run publishes the rolling release; cancelling mid-upload can truncate
the asset). Proven with three real runs rather than argued:

1. `chore/ci-trigger-proof` — a name matching neither old pattern — **triggered run #67**. Before the
   fix: zero runs.
2. A second push **cancelled #67**, proving the expression is evaluated, not merely accepted.
3. `workflow_dispatch` on `main` during run #69: **#69 and #70 both `success`**, nothing cancelled —
   proving it evaluates `false` on `main`. Proof 2 alone cannot distinguish this from
   "everything is truthy", which would silently kill publishing runs.

Worktree removed, proof branch deleted locally and remotely, throwaway probe commit never merged.

---

## 🔴 CORRECTION — CLAUDE.md was misleading and has been fixed

I wrote "Gates 1–4 are CI-reachable". **CI runs `assembleRelease` and nothing else** — no lint, no
tests, no merged-manifest check. "Reachable" meant "could be wired up"; it reads as "does run".

T002 makes that misreading *more* likely: every branch now shows a green tick, which is a far
stronger invitation to treat green CI as a passed gate. CLAUDE.md now carries a per-gate table
stating exactly what is and is not checked. **A green tick means the code compiles and packages.
Nothing more.**

---

## Next task, and why it outranks Phase 1

**Gate 4 — the merged-manifest INTERNET check — has no automated enforcement at all.**
`guard-cost.py` inspects *edits*; manifest merging pulls permissions from AARs nobody edited. That
is precisely the threat gate 4 exists to close, and it is open. The no-INTERNET invariant is the
foundation the whole programme rests on, and it is currently protected only by inspection.

Sketch (verify by deliberately adding INTERNET on a throwaway branch and watching CI go red — a
permission check never observed failing is not a check):

```yaml
- name: Assert no network permissions in merged manifest
  run: |
    M=app/build/intermediates/merged_manifest/release/AndroidManifest.xml
    test -f "$M" || { echo "merged manifest not found at $M"; exit 1; }
    grep -Eq 'android\.permission\.(INTERNET|ACCESS_NETWORK_STATE)' "$M" && {
      echo "::error::Network permission in MERGED manifest"; grep -n uses-permission "$M"; exit 1; }
```

The `test -f` guard matters more than the grep: a path typo would otherwise pass forever — the same
false-PASS class as the two already found in this programme's own tooling.

---

## Workflow hazards — filed, none caused by T002

1. **No `timeout-minutes`.** Default 360. With `cancel-in-progress: false` on `main` (added by T002)
   a hung build holds the main group for six hours and queued pushes silently replace each other.
   T002 removed the self-healing here. Set `timeout-minutes: 25`.
2. **`softprops/action-gh-release@v2` is a mutable tag** holding a `contents: write` token. Pin to a
   full commit SHA; same for the three GitHub-owned actions.
3. **`contents: write` is workflow-level**, so `gradle assembleRelease` runs with it on every branch.
   Fork PRs are forced read-only so severity is low today, but it is structural. Split publish into
   a second job with `needs:`.
4. Publish gate could also check `github.event_name` — `workflow_dispatch` on `main` republishes.
5. **Do not add `paths-ignore` later.** It reintroduces exactly the T002 defect with a worse
   signature. Actions is free on public repos; the build noise costs nothing.
6. `refs/heads/feature/player-delete-recoverable` is **still on the remote** — merged, safe to delete.

---

## 🔴🔴 T003 — THE INVARIANT IS AND ALWAYS HAS BEEN FALSE IN THE SHIPPED APK

**NOT MERGED. Blocked on a decision only the user can make.**
Branch `feature/manifest-network-gate` (`6e8dc1e`), CI run **#73 FAILED at the gate step** — which is
the gate working, on its first contact with a real APK.

The app's source manifest declares three permissions. **The built APK contains eight:**

```
android.permission.READ_MEDIA_VIDEO          declared
android.permission.READ_MEDIA_IMAGES         declared
android.permission.READ_EXTERNAL_STORAGE     declared
android.permission.ACCESS_NETWORK_STATE      ← BANNED, merged in
android.permission.INTERNET                  ← BANNED, merged in
android.permission.WAKE_LOCK                 ← undeclared, unaccounted for
android.permission.RECEIVE_BOOT_COMPLETED    ← undeclared, unaccounted for
android.permission.FOREGROUND_SERVICE        ← undeclared, unaccounted for
```

**Consequences, stated plainly:**

1. CLAUDE.md's claim that a paid API is "structurally impossible in a process that cannot open a
   socket" **is false of the artifact** and has been false in every APK ever published. It was only
   ever true of the *source* manifest. `declares` vs `merged` is exactly the distinction gate 4
   exists to police, and nothing had ever looked at the merged set.
2. The programme prompt's central premise — the invariant that "replaces every cost rule" — rests on
   a fact that does not hold.
3. Three further permissions arrived that nobody asked for. Red-team predicted precisely this class
   ("a new permission nobody asked for is a signal regardless of which one it is") and argued the
   two-item denylist would not surface it. It was right, and the evidence print caught them anyway.

**Likely source:** bundled ML Kit → `com.google.mlkit:common` → `com.google.android.gms:play-services-basement`,
whose AAR manifest declares the network pair. `WAKE_LOCK` / `RECEIVE_BOOT_COMPLETED` /
`FOREGROUND_SERVICE` point at a scheduling/worker component in the same chain. **Unconfirmed** — no
dependency resolution is possible in this environment; `gradle :app:dependencies` in CI would settle
it.

**Do NOT merge the gate until this is decided.** Merging turns `main` permanently red and blocks
every subsequent task.

### Options for the user

| | Approach | Risk |
|---|---|---|
| A | Strip via `tools:node="remove"` in the source manifest, then merge the gate | Removing a permission a dependency genuinely uses at runtime causes a failure the build cannot catch. **Requires device testing.** Bundled ML Kit should not need network, but that is an expectation, not a measurement. |
| B | Merge the gate with the five extras as an explicit, documented baseline; drive to zero as separate tasks | Honest and unblocking, but the invariant stays false meanwhile and the doc must say so. |
| C | Drop the network permissions from the invariant and rewrite the guarantee around "no code we wrote opens a socket" | Weakest. Abandons the structural claim. |
| D | Remove the dependency chain that contributes them | Largest change; costs ML Kit features. |

**Recommendation: A, gated on device verification** — it is the only option that makes the stated
invariant true. Verify barcode scan, OCR search, image labelling and portrait blur on hardware after
stripping, because that is exactly what a build cannot prove.

### Verified as working, regardless of the decision

- The gate fires on a real APK and names the offending entries.
- `if: always()` (added pre-push) meant both artifacts uploaded despite the failure — the APK is
  retrievable for diagnosis, which is what made this diagnosable at all.
- Publish correctly **skipped** on the failed gate.
- The evidence print surfaced three violations that were not on the banned list.

### Still unverified

- My 10-case local harness fed the script a **hand-written imitation** of `aapt2` output. It tested
  the regex against my model of the format, not against aapt2 — the same "validated against itself"
  defect as the two earlier guards. CI has now proven the parse works on real output for the
  `uses-permission:` form only.
- Whether `dump permissions` can miss `uses-permission-sdk-23` or implied-permission forms remains
  open. Switching the oracle to `dump xmltree` is the deeper fix and is filed.

---

## Open decisions blocking Phase 1

The programme prompt is wrong on three points. Council briefs must carry the corrections:

1. **Glass premise inverted.** Prompt says "AGSL shaders and gradient surfaces with explicitly NO
   backdrop blur." Reality: backdrop blur is **real** (`GlassBackdrop.kt` captures a `GraphicsLayer`
   and re-blurs it in a separate effect layer, 60 px cap, tint fallback), and AGSL is **decoration
   only** (`OpticalGlass.kt`, never samples backdrop pixels). C1 option (c) would be building a new
   capability, not extending an existing one.
2. **AGSL is API 33, not 31.** `RenderEffect` is API 31 but `RuntimeShader` /
   `createRuntimeShaderEffect` are API 33. At `minSdk 31` the shader path needs a runtime gate plus a
   permanently maintained fallback — and the existing fallback is already a divergent second
   implementation, the exact failure C1 asks about.
3. **APK premise stale.** "~264 MB universal debug APK" is out of date: R8 + resource shrinking
   already shipped and `app-release.apk` is **184.9 MiB**. The debug asset was deleted. C3's live
   question is ABI splits vs bundle vs accept, against a 184.9 MiB baseline.

---

## Backlog — found during Phase 0, not yet scheduled

**Verification infrastructure (Phase 2 scope, all currently absent):**
- Zero tests, zero test dependencies, no `androidTest` source set.
- CI runs `assembleRelease` only — never `test`, `lint`, or `check`.
- No lint config, no baseline, no static analysis.
- No macrobenchmark module, no baseline profile.

**Traps:**
- 🔴 **CI trigger is `[main, 'feature/**']`.** A branch named `fix/`, `chore/`, `perf/` or `task/`
  pushes with **no CI at all** and looks successful. Caught during T001 — the branch was renamed
  from `fix/…` to `feature/…` before pushing. A green gate bypassable by a branch name is not a
  gate. Widen the trigger or standardise the prefix.
- `gradlew` is a 971-byte hand-written script, **not executable**, whose `DEFAULT_JVM_OPTS` expands
  unquoted so java receives a literal `"-Xmx64m"`. Green gate items 1–3 are written as
  `./gradlew …` and that command has almost certainly never run. CI sidesteps it via `setup-gradle`.
- `refs/tags/latest` points at `a596325` (Jul 22), now many commits behind. Release *assets* update
  correctly but the tag ref does not, so cloning the tag yields stale source.
- `adb` exists in Termux but no device is attached. Pairing one makes gates 5–7 reachable from here.

**Deferred from T001 (pre-existing, recorded not fixed):**
- `PlayerScreen.kt:197` has no `onPlayerError` listener; `VideoTrimScreen.kt:250` does.
- `onDispose` saves a resume position even for a trashed item.
- "30 days" is OS-determined via `DATE_EXPIRES`, not guaranteed; same wording at
  `TrashScreen.kt:132,147`. Fix all four sites together or none.
- `tools/check_destructive_calls.py` is **not wired into CI** — nothing runs it automatically.

**Other:**
- `glassSheen` (`GlassShader.kt:39-49`) is still an ungated `infiniteRepeatable`.
- `VideoTrimScreen.kt` uses no Compose animation at all, uniquely among the six screens.
- 62 `Color(0x…)`, 34 `N.sp`, 13 `RoundedCornerShape(N.dp)` literals outside `ui/theme/`.

---

## Standing user actions

- 🔴 **Revoke the GitHub token** and the three before it. Replace with one fine-grained token scoped
  to `GlassGallery`, `Contents: write` only.
- Install and check the adaptive launcher icon (shipped `b3b8f96`, never seen rendering).
- Trash/restore device check for T001, above.
