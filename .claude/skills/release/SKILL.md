---
name: release
description: Publish a change of the STV app (every push to main goes to all users) - commit, push, wait for the GitHub Actions build, and fix it if it fails. Use whenever a change is ready to ship.
---

# Releasing a change of STV

A push to `main` is a release: GitHub Actions builds, lints, runs the unit tests and, only if all
pass, publishes `stv.apk` to every user. A failed run publishes nothing.

## Steps

1. **Check the change**
   - Only the lines the change needs are touched (`git diff --stat`, then read the diff).
   - New visible text goes through `t()` and has translations for all nine languages in
     `app/src/main/java/com/streamtv/ui/ExtraStrings.kt`.
   - A logic fix comes with a unit test under `app/src/test/`.

2. **Commit**: one topic per commit. First line: what changed for the viewer. Body: why.

3. **Push**
   ```sh
   git -c push.negotiate=true push origin main
   ```

4. **Wait for the build of that commit**
   ```sh
   sha=$(git rev-parse HEAD); sleep 45
   id=$(gh api "repos/walidplayx-debug/streamtv/actions/runs?head_sha=$sha" --jq '.workflow_runs[0].id')
   for i in $(seq 1 18); do
     r=$(gh api repos/walidplayx-debug/streamtv/actions/runs/$id --jq '.status+" "+(.conclusion//"")')
     case "$r" in completed*) echo "$r"; break;; esac; sleep 30
   done
   ```

5. **If it failed**, read the errors, fix them, and go back to step 2:
   ```sh
   job=$(gh api repos/walidplayx-debug/streamtv/actions/runs/$id/jobs --jq '.jobs[0].id')
   gh api repos/walidplayx-debug/streamtv/actions/jobs/$job/logs | grep -E " e: |Error: |\.kt:[0-9]+"
   ```
   Common causes: a `@Composable` call (such as `t()`) inside a non-composable lambda, a missing
   import, an `Int * Dp` (write `(n * 32).dp`).

6. **Tell the owner** (in Levantine Arabic, short): what changed on the TV, that the update is
   published, and anything they should try. Only after a successful run.
