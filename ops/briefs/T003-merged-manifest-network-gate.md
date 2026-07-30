# BRIEF T003 — Enforce the no-INTERNET invariant in CI

**Task ID:** `T003`
**Type:** invariant enforcement. The foundation the whole programme rests on.
**Branch:** `feature/manifest-network-gate` off `c385a17` (`main`)
**Worktree:** `/mnt/sdcard/Coding/wt/T003`

---

## Why this outranks everything else queued

The app declares no `android.permission.INTERNET`. CLAUDE.md calls that "a stronger zero-cost
guarantee than any denylist" — a paid API, a metered endpoint, a converting free tier, or an account
wall is structurally impossible in a process that cannot open a socket.

**Nothing automated enforces it.**

`.claude/hooks/guard-cost.py` inspects *edits*. Manifest merging pulls `<uses-permission>` elements
out of AAR dependencies that nobody edited. So the single threat the invariant exists to stop — a
transitive dependency quietly contributing INTERNET — is the exact threat the only existing check
cannot see. CI runs `assembleRelease` and nothing else.

Every feature in Phases 4–6 is supposed to be verified against this invariant. Today that
verification does not exist.

---

## 🔴 Read this before writing a line

**Three guards have been written in this programme. All three initially failed open.**

1. The adaptive-icon safe-zone checker passed artwork that a circular launcher mask would clip,
   because it measured anchor points and never the curve between them.
2. `check_destructive_calls.py` v1 exited 0 on a tree it never read (`rglob` on a missing directory
   returns empty and raises nothing), and was blind to the exact one-character typo its own brief
   warned about.
3. Both were caught only because someone deliberately tried to make them fail.

**Assume this one is broken until you have watched it fail.** A gate that has never been observed
rejecting something is not a gate — it is a green tick with no evidence behind it. Design every
branch to **fail closed**: if the artifact is missing, the tool is absent, the path is wrong, or the
output is unparseable, **the build fails**. Never pass because a check could not run.

---

## What to build

### The check

Add a step to `.github/workflows/build.yml`, after `Build release APK` and before the artifact
uploads, that fails the build if `android.permission.INTERNET` or
`android.permission.ACCESS_NETWORK_STATE` is present in what actually ships.

**Check the built APK, not just a source file.** The APK is ground truth: it is what users install
and it reflects the completed manifest merge. `aapt2 dump permissions <apk>` is the authoritative
reader and GitHub's `ubuntu-latest` image ships the Android SDK, so `aapt2` is available under
`$ANDROID_HOME/build-tools/*/`. Locate it robustly rather than hardcoding a version directory.

**Do not hardcode the merged-manifest intermediate path.** It has changed across AGP versions
(`merged_manifest/` vs `merged_manifests/<variant>/process<Variant>Manifest/`), and a stale path
that silently matches nothing is precisely failure mode #2 above. If you use the intermediate at
all, glob for it and **fail when the glob matches nothing**.

### Mandatory fail-closed conditions

The step must exit non-zero, with a clear message, if **any** of these hold:

- the APK is not found;
- `aapt2` cannot be located;
- `aapt2` exits non-zero;
- its output is empty (a successful run always prints at least the app's declared permissions —
  this repo declares three, so empty output means the check did not work);
- either banned permission appears.

### On success, print the evidence

Print every permission actually found. The three expected are `READ_MEDIA_VIDEO`,
`READ_MEDIA_IMAGES`, and `READ_EXTERNAL_STORAGE`. Printing them makes the gate auditable at a glance
and makes an unexpected *addition* visible even when it is not on the banned list — a new permission
nobody asked for is a signal regardless of which one it is.

### Failure output must be actionable

On failure, print the offending permission and the full permission list, and state that a transitive
dependency is the likely source. Suggest `gradle :app:dependencies` as the next step. Someone hitting
this at 2am should not have to reverse-engineer the check.

---

## File ownership — you own EXACTLY these

- `.github/workflows/build.yml` (add one step; change nothing else)
- optionally `tools/check_manifest_permissions.sh` if you prefer the logic in a script the step calls

If you add a script, it must be POSIX `sh` or `bash`, dependency-free, executable, and runnable
locally so it can be tested outside CI.

**Do not touch** any `.kt`, any Gradle file, the manifest, or the other `tools/` scripts.
Do **not** wire in `check_destructive_calls.py` or the icon checker — that is a separate task.

---

## Constraints

- Commit author **must** be `DarsmaOfficial <darsmaofficial@gmail.com>`. **No `Co-Authored-By`, no
  `Signed-off-by`, no AI/model/vendor name** anywhere.
- Commit locally. **DO NOT PUSH.**
- No new action, no third-party action, no new dependency, no new permission in the workflow.
- Preserve everything T002 established: `branches: [ '**' ]`, the `concurrency` block with
  `cancel-in-progress: ${{ github.ref != 'refs/heads/main' }}`, and the publish step's
  `if: github.ref == 'refs/heads/main'` gate. Quote all three back in your report as unchanged.
- Do not add `timeout-minutes` or pin action SHAs here — both are real and both are filed as
  separate tasks. Keep this commit to one concern.

---

## Verification

You cannot run GitHub Actions locally and must not pretend otherwise. Do this instead:

1. If you wrote a script, **run it locally** against a fabricated `aapt2`-style output containing
   INTERNET and confirm non-zero exit; then against a clean one and confirm zero. Paste both.
2. **Test every fail-closed branch you can reach locally** — missing APK, missing `aapt2`, empty
   output. Each must exit non-zero. Paste the outputs. This is the part that matters most.
3. Print the workflow diff and confirm it adds exactly one step and preserves the three T002
   invariants above.

## Definition of done

1. CI fails when either banned permission is present in the built APK.
2. CI fails when the check itself cannot run, for every reason listed above.
3. On success the full permission list is printed.
4. The three T002 invariants are untouched.
5. One commit, correct author, no trailers, not pushed.

---

## REVISION 2 — two pre-push fixes from adversarial review

The script passed review on control flow. These are workflow-level, and both were defects in **this
brief** rather than in the work.

### Fix 1 — invoke via `bash`, not the exec bit

`build.yml` currently runs `tools/check_manifest_permissions.sh …` directly, which depends on the
file's exec bit. Git records mode `100755` today, but this repo has `core.filemode = false` on a
`/sdcard` mount that cannot represent the bit — git can only *preserve* what is already recorded, not
detect it. Any future worker that recreates rather than edits the file silently drops it to `100644`,
and the step then fails with exit 126 and "Permission denied" on **every branch, every build**.

Change the step's `run:` to invoke it explicitly:

```yaml
run: bash tools/check_manifest_permissions.sh app/build/outputs/apk/release/app-release.apk
```

Mode becomes irrelevant. One word, removes the whole class.

### Fix 2 — `if: always()` on both artifact uploads

The gate sits before the uploads, so when it fires the APK and mapping are never uploaded. In this
project that is unusually painful: there is **no JDK, Android SDK or Gradle in the authoring
environment**, so the failure message's suggestion to run `gradle :app:dependencies` names a command
that cannot be run anywhere we have access to, and the APK that would let us inspect the offending
entry dies with the runner. The only remaining move would be to disable the gate and re-push —
exactly the wrong pressure on a freshly-installed invariant check.

Add `if: always()` to **both** `Upload release APK` and `Upload R8 mapping files`.

Leave the gate where it is. Do **not** move it after the uploads — that would read as "ship first,
check later". The publish step needs no change: `if: github.ref == 'refs/heads/main'` contains no
status function, so GitHub implicitly ANDs it with `success()` and a failed gate still skips it.

### Explicitly NOT in this revision

- Do not switch the oracle to `dump xmltree`. That is the deeper fix for an unverified assumption
  and it waits until a real CI run has shown us what `dump permissions` actually prints.
- Do not add an expected-permissions allowlist yet. Same reason — settle reality first.
- Do not touch `timeout-minutes` or pin action SHAs. Separate filed tasks.

## What to report

- Commit SHA and full diff.
- Verbatim local runs for the positive case, the clean case, and **each** fail-closed branch.
- The three T002 invariants quoted back as unchanged.
- Explicit statement that the workflow was not executed and cannot be until pushed.
- Anything you found that this brief did not anticipate. The previous three tasks each surfaced a
  real defect that way, and two of them were in the guard rather than the code.
