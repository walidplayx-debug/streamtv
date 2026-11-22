# STV — working rules for AI coding agents

STV is an Android TV IPTV player (Kotlin, Jetpack Compose, Media3), one module: `app/`.
Every push to `main` is built by GitHub Actions and published to **every user** as the new
`stv.apk`. Treat a push to `main` as a release.

## The four rules

1. **State your assumptions first; ask when something is unclear.** If a request can be read two
   ways, ask one short question before writing code. A wrong guess costs a whole release round.
2. **Write the least code that does the job.** No new abstractions, files or options nobody asked for.
3. **Touch only the lines the change needs.** No drive-by refactors, renames or reformatting.
4. **Logic bug → a failing test first, then the fix.** Add a unit test under `app/src/test/` that
   shows the bug, then fix it. Screen-only issues (layout, focus, clipping) cannot be unit-tested:
   say how to reproduce them on the TV instead.

## How the owner works

- Speaks Levantine Arabic: answer in the same, short, phone-friendly.
- Often sends notes **in batches**: collect them, change nothing, until they write «خلص» / «تم».
- Tests everything on a real TV and sends photos; trust them over your assumptions.

## Building and releasing

- There is usually no Android SDK where the agent runs: **CI is the compiler**. After a push,
  wait for the run on that commit and read its result; on failure read the job log
  (`grep " e: "`) and fix before saying anything is done. A failed build publishes nothing.
- One topic per commit, a message that says what changed for the viewer and why.
- See `.claude/skills/release/SKILL.md` for the exact steps.

## Conventions in this code

- **Text:** every visible string goes through `t("English")`; add its translation for all nine
  languages in `ui/ExtraStrings.kt` (fr, ar, es, ru, zh, tr, de, pt, nl).
- **Colours:** read them from `ActivePalette.current` (themes in `ui/Palette.kt`), never fixed colours in screens.
- **Database:** Room is only a cache of the playlist. A schema change bumps the version in
  `data/StreamDatabase.kt` (destructive fallback: the playlist is downloaded again).
- **Xtream:** API first (`data/XtreamApi.kt`): live channels at once, films and series in the
  background, episodes and title details only when a page opens. `get.php` is a fallback.
- **Light on the TV:** no endless animations, no polling without need, nothing loaded before it
  is shown; TVs have little memory (big playlists ran out of it before).
- **Remote first:** every screen must work with the D-pad, OK and Back; mind focus and RTL (Arabic).
