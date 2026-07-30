# BRIEF T001 — Make video deletion recoverable

**Task ID:** `T001`
**Type:** data-loss bugfix. Highest severity in the repo.
**Branch:** `fix/player-delete-recoverable` off `b3b8f96` (`main`)
**Worktree:** `/mnt/sdcard/Coding/wt/T001`
**Priority:** ahead of the council. This is live on `main` right now.

---

## The bug

Two visually identical delete buttons have opposite consequences.

| Screen | Line | Call | Result |
|---|---|---|---|
| Photo viewer | `PhotoViewerScreen.kt:457` | `MediaStore.createTrashRequest(resolver, listOf(photoUri), true)` | 30-day OS bin — **recoverable** |
| **Video player** | **`PlayerScreen.kt:412`** | **`createDeleteRequest(resolver, listOf(videoUri))`** | **permanent — no undo** |

Both are a `BouncyIconButton` with `size = 46.dp`, in the same control-row position, launching the
same `deleteLauncher`. A user who learns "delete goes to the bin" from photos will permanently
destroy a video. The system confirmation dialog is the only safety net, and it does not distinguish
the two cases in a way the user can act on.

The comment at `PlayerScreen.kt:226-227` calls it "System-confirmed delete" and does not mention
that this path is irreversible while its siblings are not.

**Corroborating tell:** `MediaStore` is already imported at `PlayerScreen.kt:7`, yet line 412 uses
the fully-qualified `android.provider.MediaStore.createDeleteRequest`. That line was written in
isolation from the rest of the file — consistent with an inconsistency introduced by a single
narrow edit rather than a deliberate design decision.

## Required change

### 1. `PlayerScreen.kt:412` — the fix

Replace:
```kotlin
val pi = android.provider.MediaStore.createDeleteRequest(
    context.contentResolver, listOf(videoUri)
)
```
with:
```kotlin
// Soft delete into the 30-day OS recycle bin, matching the photo viewer
// and the gallery multi-select path.
val pi = MediaStore.createTrashRequest(
    context.contentResolver, listOf(videoUri), true
)
```

Use the already-imported `MediaStore` (line 7). Do not add an import. Do not use the FQN.

### 2. `PlayerScreen.kt:226-227` — correct the stale comment

It currently reads "System-confirmed delete: MediaStore shows the OS dialog, and on OK we prune the
item from the gallery and leave the player." Amend it to state that the item is **trashed, not
destroyed**, and is recoverable from the Trash screen for 30 days.

### 3. Do NOT change the launcher

`deleteLauncher` (`PlayerScreen.kt:229-237`) calls `galleryViewModel.removeFromList(setOf(videoId))`
then `onBack()`. That remains correct: a trashed item drops out of the normal MediaStore query, so
pruning the list is still the right response. `PhotoViewerScreen.kt:153-160` does exactly the same
thing after a trash request. **Verified — do not "fix" this.**

### 4. Add the regression guard

Create `tools/check_destructive_calls.py` — dependency-free Python 3, standard library only.

It must scan `app/src/main/java/**/*.kt` for `createDeleteRequest` and **fail (exit 1)** if it
appears anywhere outside an explicit allowlist. The only legitimate site is the permanent-purge
action inside the Trash screen, where the user has already opted into "delete forever":

```
ALLOWED = { "ui/trash/TrashScreen.kt" }
```

On failure it must print each offending `file:line` with the source line, and explain that user
media deletion defaults to `createTrashRequest(..., true)`.

It must also print a positive inventory on success: every `createTrashRequest` and
`createDeleteRequest` site found, so the destructive surface is visible at a glance rather than
having to be re-derived.

**Run it yourself before committing and paste the verbatim output in your report.** Also verify it
FAILS on the pre-fix code — check out `PlayerScreen.kt` from `b3b8f96`, run the script, confirm
exit 1, then restore. Report both outputs. A guard that has never been seen to fail is not a guard.

Do **not** wire it into CI or Gradle — that is a separate change and the workflow file is outside
your ownership.

---

## REVISION 2 — the Kotlin fix is correct; the guard is not

Adversarial review passed the Kotlin change ("a strict improvement to a data-loss path") and found
the guard to be the weak half of the commit. Both failures below were **reproduced by the conductor
with actual commands**, not accepted on argument.

### Blocker 1 — the guard exits 0 when it scans nothing

`SOURCE_ROOT` is hardcoded to `app/src/main/java`. `Path.rglob` on a directory that does not exist
returns an empty iterator and raises nothing — verified:

```
dir exists: False
rglob result count: 0
```

So if the source set is renamed, a second one is added (`app/src/main/kotlin` is the standard
alternative), or the module moves, the guard prints an empty inventory, prints
`OK: permanent deletion is limited to the explicit allowlist.`, and returns 0. **It reports a clean
bill of health on a tree it never read.** For a guard protecting a data-loss path, a silent false
PASS is the worst possible failure mode.

Required:
- If `SOURCE_ROOT` is not a directory → print the resolved path and **exit 2**.
- If the scan finds **zero** `create*Request` sites → **exit 2**. This repo can never legitimately
  have zero; zero means the scan is broken, not that the code is clean.
- Exit 2 (not 1) so "guard is broken" is distinguishable from "code is bad".

### Blocker 2 — the guard cannot detect the exact typo this brief warns about

The offender test is `site[2] == "createDeleteRequest"`. It never looks at the third argument of
`createTrashRequest`. Worse, the call spans two lines, so line-by-line iteration never sees the
boolean at all:

```
415:  val pi = MediaStore.createTrashRequest(
416:      context.contentResolver, listOf(videoUri), true
```

Conductor reproduced the consequence: injecting `true` → `false` on line 416 leaves the guard
printing `OK ... exit=0`. At runtime that is **worse than the original bug** — the OS shows an
untrash confirmation, returns `RESULT_OK`, the launcher prunes the item and navigates back, so the
video disappears from the gallery **while remaining on disk**. The user believes it is deleted.

Required:
- Join each file's text before matching so a call spanning lines is seen whole. Capture the argument
  list, e.g. `create(Trash|Delete)Request\s*\(([^)]*)\)`.
- **Fail if any `createTrashRequest` outside the allowlist has a last argument that is not `true`.**
- Report `file:line` correctly for multi-line calls (compute the line from the match offset).

### Blocker 3 — the guard sees only one of several ways to destroy media

`contentResolver.delete(...)`, `DocumentsContract.deleteDocument`, and `File.delete()` all sail
through. Required, with a deliberate split to avoid false positives:

- **FAIL** on `contentResolver.delete(` / `resolver.delete(` / `deleteDocument`, allowlisting the one
  legitimate site: `ui/trim/VideoTrimScreen.kt` cleans up its own just-inserted `IS_PENDING` row
  inside a `catch`. Verified as correct — do not flag it.
- **REPORT ONLY, do not fail**, on `File.delete()`. Every current site is `context.cacheDir` temp
  output (`VideoTrimScreen.kt:261, 541-545, 580, 769`) and failing on those would be noise. Listing
  them keeps the destructive surface visible without crying wolf.

### Also fix — pause playback before the dialog

`PlayerScreen.kt` Trim button (`:380-383`) calls `player.pause()` before launching its intent; the
delete button does not. Video keeps playing **with audio** underneath the system confirmation sheet,
and the 5 Hz playback-state loop keeps driving the MiniPlayer for an item the user is being asked to
delete. Add `player.pause()` as the first statement of the delete `onClick`, matching the Trim
button. This is pre-existing, but it is on the lines this diff rewrites.

### Deliberately NOT in scope — record, do not fix

Do not touch these. They are pre-existing, unrelated to deletion semantics, and widening this task
risks the data-loss fix:
- `PlayerScreen.kt:197` has no `onPlayerError` listener (`VideoTrimScreen.kt:250` does).
- `onDispose` saves a resume position even for a trashed item.
- "30 days" is OS-determined, not guaranteed, and the same wording already appears at
  `TrashScreen.kt:132,147` — changing one of four sites would be worse than leaving all four.

---

## File ownership — you own EXACTLY these

- `app/src/main/java/com/darsma/glassgallery/ui/player/PlayerScreen.kt` (two edits above, nothing else)
- `tools/check_destructive_calls.py` (new)

Touch nothing else. Do not refactor. Do not tidy neighbouring code. Do not touch
`PhotoViewerScreen.kt`, `GalleryScreen.kt` or `TrashScreen.kt` — they are already correct.

## Constraints

- Commit author **must** be `DarsmaOfficial <darsmaofficial@gmail.com>`. **No `Co-Authored-By`, no
  `Signed-off-by`, no AI/model/vendor name** in the commit message, file contents, comments, or
  identifiers.
- Commit locally on the branch. **DO NOT PUSH.**
- No new dependency. No manifest change. The app declares **no INTERNET permission** — nothing you
  add may require one.
- There is **no JDK, Android SDK, Gradle, emulator or device** here. You cannot build, run, or test
  this. Do not claim you did. Report it explicitly as an unbuilt, unrendered change.

## Definition of done

1. `PlayerScreen.kt:412` uses `createTrashRequest(..., true)` via the already-imported `MediaStore`.
2. The comment at :226-227 no longer misdescribes the behaviour.
3. `tools/check_destructive_calls.py` exists, passes on the fixed tree, and is demonstrated to fail
   on the pre-fix tree.
4. `git diff` shows exactly two files changed and no incidental edits.
5. One commit, correct author, no trailers, imperative message, not pushed.

## What to report

- Commit SHA and the full `git diff`.
- Verbatim guard output on the fixed tree **and** on the pre-fix tree.
- Confirmation that the launcher was left alone and why.
- Explicit statement that this is unbuilt and unverified on a device.
- Anything you found that this brief did not anticipate.

## Known trap

Resource and import mistakes are the top cause of CI failure in this project. `MediaStore` is
imported at `PlayerScreen.kt:7` — use the short name and add no import. `createTrashRequest` takes a
third `Boolean` argument (`true` to trash, `false` to restore); omitting it will not compile.
