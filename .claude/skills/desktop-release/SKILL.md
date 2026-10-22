---
name: desktop-release
description: Use when the owner wants a change shipped to the installed ScreenSort desktop app (publish a new version, update channel, release notes). A merged PR alone is not "done".
---

# Shipping a desktop release

The owner considers work finished only once it reaches the installed app through the updater.

1. Confirm `main` contains the change and CI is green.
2. Bump the version in `frontend/src-tauri/tauri.conf.json` and `frontend/src-tauri/Cargo.toml`
   (these two are the release authority; keep them identical).
3. Update the release notes in `packaging/distribution/core-release.md`. Write them in English for the
   updater UI: what is new, plus any behavior change the user will notice (for example "a `.png` file
   that is really a GIF is no longer added to the catalog").
4. Commit, then create and push an annotated tag `desktop-v<version>`. Ask the owner before pushing.
5. The `windows-release.yml` workflow builds the NSIS installer, signs it with minisign, and publishes
   installer, signature, `latest.json` and checksums to the `updates-preview` channel of
   `Pum2A/screensort-releases`. The launcher checks that channel on every fresh start.
6. After it is published, rewrite `docs/current-state.md` and the affected `docs/review-*.md` from
   "proposal / awaiting approval" to "published on <date> in channel `updates-preview`". Also update
   the version mentioned in `README.md`. Stale wording counts as a defect.
