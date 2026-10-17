# Requirements (session 2026-10-17, v1.4.0)

Earlier state: `1.3.0+opac.0.32.7` on `v1.3-dev` (`197a2225`), shipped in Nytheria as `opac-team-claims-fabric-1.21.1-v1.3.0.jar`.
Requirements of v1.3.0 and older: git history of this file. Design and owner decisions: `design-1.4.md`.

## Goals (owner, via the Nytheria lead session)
1. Easier in-game UI for party/claim settings and for claiming and unclaiming.
2. Same for team claims: configurable in a GUI, limits can be raised.
3. Admin menu: every server/admin setting that /opac exposes (claim, forceload, team claim limits and config, per-player
   and per-party overrides), editable in a GUI, permission-gated.
4. Bug: party screen rank button changes a different row. Fix first, own commit, proven by test or in game.

## Done means
- Features work on a dedicated server with two players in game; tests green (both loaders, with and without FTB Teams).
- Version 1.4.0; Fabric jar into the Nytheria dev instance `mods/` replacing 1.3.0 (do not commit there, list changed
  pack files in the report); pushed to `claude/opac-admin-rework-d73a06` (never main, never force).
- CurseForge upload (project 1704256, both loaders) after build, tests and in-game check pass.
- `.knowledge/report.md` with a manual in-game checklist.

## Standing constraints
- As in 1.3.0 (git history of this file) plus: commit as Navrelis <111079703+navrelis@users.noreply.github.com>, no
  Co-Authored-By, no real name or local paths in commits. Never kill javaw; game lock `F:\Coding\.knowledge\game.lock`.
- Never overwrite an existing handoff file; own handoff = `.knowledge/handoff-opac-admin-rework-2026-10-17.md`.
- 2026-10-17 owner: CurseForge 1.4.0 release type = Release (both loaders); relation-slug check override OK for this upload.
- 2026-10-17 lead local_14a71120 (23:28): game.lock free, OPAC last workstream; take the lock when T9 headless passes; old instance jar goes to F:\Coding\NytheriaDevelopment\mods_backup\ (not deleted); short instance client start with all jars, check OPAC WARN/ERROR; then release lock, CF upload, push.
