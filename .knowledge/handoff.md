# Handoff — OPAC Team Claims v1.2.0 (finished 2026-10-09)

All tasks of v1.2.0 are done, verified and pushed on `v1.2-dev` (see `plan.md`, `log.md`, `report.md`). Nothing is
running. (The handoff of the stopped session of 2026-10-09 is in the git history of this file.)

## State
- `v1.2-dev` = origin: budgets, party screen, FTB Teams sync, lang en + de, tests, README, version `1.2.0+opac.0.31.6`.
- `v1.2-dev` is merged into `fabric-port` (GitHub default) and pushed; `main` is untouched.
- Release files: `ExportedJars/v1.2.0/` (two jars, not committed; `CURSEFORGE.md` committed).
- CurseForge project 1704256 (Beta): NeoForge 1.2.0 uploaded (file 9106819). Fabric 1.2.0 NOT uploaded yet: the upload tool's relation check (api.cfwidget.com) returns 404 for `forge-config-api-port` and `ftb-teams-fabric`; plan file `curseforge-plan-1704256-1.2.0-fabric.json` in the tool's `local/` folder, changelog text = section "Changelog for this file" of `ExportedJars/v1.2.0/CURSEFORGE.md`.
- Untracked and to stay untracked: `graphify-out/`, `ExportedJars/opacteamclaimslogo.png`.

## Open for the user
1. Manual test checklist in `report.md` (two clients; with and without FTB Teams).
2. Fabric upload to CurseForge (see State), then the project description / summary from `ExportedJars/v1.2.0/CURSEFORGE.md`.
3. Server admins: read "Updating from 1.1.0" in the README (solo teams have a team limit of 0 by default).

## Commands
- Without FTB Teams: `./gradlew :Fabric:build :NeoForge:build :Fabric:runBootTest :NeoForge:runTeamClaimsGameTest` -> "All 77 required tests passed" twice.
- With FTB Teams: `./gradlew :Fabric:runBootTestFtb :NeoForge:runTeamClaimsGameTestFtb` -> "All 111 required tests passed" twice.
- Never two Gradle invocations in the same checkout at once.

## Things that cost time (avoid them)
- A subagent started with `isolation: "worktree"` gets a worktree based on the old `main` root commit: put the wanted
  base commit in the instruction and allow `git reset --hard <commit>` on its own branch as the first step.
- `git worktree remove` fails on Windows with "Filename too long" after deregistering the worktree: delete the folder
  with `[System.IO.Directory]::Delete('\\?\<path>', $true)`.
- With staged work of an agent in the checkout, commit board files with `git commit -o -- <paths>`.
- `python` is not on the Git Bash PATH (use `py`); filter git's CRLF warnings with `2>/dev/null`.
- Other Claude sessions run Gradle daemons on this machine: only stop Java processes whose command line contains this repo's path.
