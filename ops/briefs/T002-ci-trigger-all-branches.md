# BRIEF T002 — Make CI unbypassable by branch name

**Task ID:** `T002`
**Type:** gate integrity. Everything downstream depends on it.
**Branch:** `feature/ci-trigger-all-branches` off `d077345` (`main`)
**Worktree:** `/mnt/sdcard/Coding/wt/T002`

---

## The problem

`.github/workflows/build.yml:5` reads:

```yaml
on:
  push:
    branches: [ main, 'feature/**' ]
```

**A push to any branch not named `main` or `feature/**` produces no CI run at all**, and nothing
about the push looks wrong — no error, no warning, no skipped run in the UI. It simply does not
appear.

This was hit for real during T001: the branch was `fix/player-delete-recoverable`, which matches
neither pattern. It was renamed to `feature/…` immediately before pushing, purely because the
mismatch was noticed by reading the workflow. Had it not been, the branch would have pushed
"successfully", CI would have been waited on indefinitely, and an unverified commit could have
reached `main`.

The programme ahead runs every task on its own branch. Any branch named `fix/`, `chore/`, `perf/`,
`task/`, `docs/`, `wip/` or anything else silently skips verification. **A green gate that can be
bypassed by choosing a branch name is not a gate.**

## Required change — `.github/workflows/build.yml` only

### 1. Trigger on every branch

```yaml
on:
  push:
    branches: [ '**' ]
  pull_request:
  workflow_dispatch:
```

`'**'` matches every branch including nested names like `feature/a/b`. GitHub Actions is free and
unlimited on public repositories, so the extra runs cost nothing.

Do **not** try to enumerate more prefixes. An allowlist of prefixes has exactly the same defect as
today's — it just moves the hole. The point is that no name can escape.

### 2. Add concurrency control

Widening the trigger means more runs, and pushes in quick succession would otherwise pile up and
race. Add at top level, after the `permissions:` block:

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.ref != 'refs/heads/main' }}
```

**The conditional matters and is not decoration.** On a feature branch, superseding an in-flight run
is desirable — only the latest commit is interesting. On `main` it is not: the run publishes
`app-release.apk` to the rolling `latest` release, and cancelling a publishing run mid-upload could
leave the release asset missing or truncated. `main` therefore never cancels.

### 3. Leave everything else exactly as it is

Do **not** touch: the job, `runs-on`, any step, the JDK or Gradle setup, `assembleRelease`, either
artifact upload, retention days, or the publish step.

In particular **keep the publish step gated exactly as it is**:

```yaml
if: github.ref == 'refs/heads/main'
```

This gate becomes *more* load-bearing once every branch builds — without it, every branch would
publish over the rolling release. Verify it is unchanged and say so in your report.

## File ownership — you own EXACTLY one file

- `.github/workflows/build.yml`

Nothing else. No Kotlin, no Gradle, no manifest, no tooling.

## Constraints

- Commit author **must** be `DarsmaOfficial <darsmaofficial@gmail.com>`. **No `Co-Authored-By`, no
  `Signed-off-by`, no AI/model/vendor name** in the commit message, file, or comments.
- Commit locally. **DO NOT PUSH.**
- No new action, no third-party action, no new permission. `permissions: contents: write` stays as-is.
- The app declares **no INTERNET permission** — irrelevant to this file, but do not add anything
  that would change the built artifact.

## Verification you must perform

You cannot run GitHub Actions locally, and you must not pretend otherwise. Do these instead:

1. **Validate the YAML parses.** Use Python's stdlib only — no `pip install`. If `yaml` is not
   available, do not install it; instead confirm structure by inspection and say clearly that
   parsing was not machine-verified. Report which you did.
2. **Print the diff** and confirm it touches only the `on:` block and adds the `concurrency:` block.
3. **Confirm by grep** that `if: github.ref == 'refs/heads/main'` is still present on the publish
   step and that the step count is unchanged.

## Definition of done

1. `on.push.branches` is `[ '**' ]`.
2. A top-level `concurrency` block exists with the `main`-excluding `cancel-in-progress` expression.
3. The publish step is still gated on `refs/heads/main`.
4. `git diff` shows exactly one file changed, and no step was added, removed, or reordered.
5. One commit, correct author, no trailers, not pushed.

## What to report

- Commit SHA and the full diff.
- Whether YAML parsing was machine-verified or inspected by eye.
- Confirmation the publish gate is untouched, quoted.
- Explicit statement that the workflow was not executed and cannot be until it is pushed.
- Anything you noticed about this workflow that this brief did not ask about — the previous two
  tasks each surfaced a real defect that way.
