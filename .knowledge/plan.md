# Plan — v1.3.0: port to upstream OPAC 0.32.7

Status: open / in progress / in review / done. Size S/L, difficulty E/H. One Gradle invocation per checkout at a time.
(v1.2.0 plan: git history of this file.)

| # | Task | Size/Diff | Model | Depends | Status | Definition of done |
|---|------|-----------|-------|---------|--------|--------------------|
| 0 | Analysis, branch `v1.3-dev`, upstream fetched, merge strategy, trial merge | – | lead | – | done | requirements.md, plan.md, decisions.md |
| 1 | Merge upstream `1.21` (0.32.7): resolve the 9 conflicted files, adapt the Team Claims layer and hooks to the changed upstream code (limits packet, anchors, claim results, transfer, unclaim, spread-out tasks), move the fork's packet ids, anchors together with team claims | L/H | Opus (this checkout) | 0 | done | no conflict markers; both loaders build; 77/77 twice and 111/111 twice; behaviour of 1.2.0 kept; anchor behaviour documented; upstream delta marked |
| 2 | Linkage checker `tools/linkcheck/`: every `xaero.pac.*` class, method, field referenced by given mod jars exists in a given OPAC jar (incl. nested jars, inherited members, static / interface mismatches); self-test | S/E | Sonnet (same checkout, own folder, no Gradle) | 0 | done | self-test passes; exit code 1 and a list on missing members; JDK 21 only, no dependencies |
| 3 | New gametests: budgets and limits with the new claim limits packet, anchors with team claiming mode, loading a 1.2.0 world save | L/E | Sonnet | 1 | in progress | same new test count on both loaders, all green with and without FTB Teams |
| 4 | Release prep: version `1.3.0+opac.0.32.7`, README upgrade notes, changelog, `ExportedJars/v1.3.0/CURSEFORGE.md`, de_de for new fork strings, leftover scan | S/E | Sonnet (parallel to 3, no Gradle) | 1 | in progress | docs match behaviour; metadata shows 1.3.0 |
| 5 | Linkage check on the built jars with World Map 1.47.0 + Minimap 26.6.0 (both loaders); control: the 1.2.0 jar must fail; if possible dev-client run with World Map (open map, right-click) | S/E | lead (+ Sonnet for the client run) | 1, 2, owner OK for the jars | open | zero missing references on both loaders; control run reports `usingAnchorBasedClaiming` |
| 6 | Final acceptance: clean build, all four test runs, jars to `ExportedJars/v1.3.0/`, Graphify update, report.md, push, CurseForge upload (both loaders, Beta), shared log line | – | lead | 1–5 | open | report.md; pushed; two file ids |

Model tally (delegated work, long task counts double): Opus 2 (T1) / Sonnet 1 (T2) + 2 (T3, long) + 1 (T4) = Opus 2 of 6.
