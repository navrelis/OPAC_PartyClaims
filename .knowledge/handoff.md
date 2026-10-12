# Handoff — OPAC Team Claims v1.3.0 (in progress, 2026-10-12)

Branch `v1.3-dev` (worktree `.claude/worktrees/dazzling-noether-a1675c`), based on `50d1919`. Remote `upstream` = thexaero/open-parties-and-claims.
A merge of `upstream/1.21` is in progress in this checkout (not committed): see `plan.md` for the task state and `log.md` for what ran.
To redo the merge base: `git replace --graft e901c190 c0d97b37`, `git merge --no-commit --no-ff upstream/1.21`, `git replace -d e901c190`; then `git rm` everything under `Forge/` and `docs/`.

Commands and "things that cost time" (full list: git history of this file, commit `50d1919`):
- Without FTB Teams: `./gradlew :Fabric:build :NeoForge:build :Fabric:runBootTest :NeoForge:runTeamClaimsGameTest` -> "All 77 required tests passed" twice.
- With FTB Teams: `./gradlew :Fabric:runBootTestFtb :NeoForge:runTeamClaimsGameTestFtb` -> "All 111 required tests passed" twice.
- Never two Gradle invocations in the same checkout; only stop Java processes whose command line contains this repo's path.
- `python` is not on the Git Bash PATH (use `py`); a subagent with `isolation: "worktree"` starts on the old `main` root commit.
- CurseForge: project 1704256; 1.2.0 files NeoForge 9106819, Fabric 9108825; uploader in `F:\Coding\CurseForgeUpload`.
