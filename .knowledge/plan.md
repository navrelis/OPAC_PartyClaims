# Plan — v1.4.0: claims hub and admin menu

Status: open / in progress / in review / done. Size S/L, difficulty E/H. One Gradle invocation per checkout at a time.
(v1.3.0 plan: git history of this file.) Design: `design-1.4.md`.

| # | Task | Size/Diff | Model | Depends | Status | Definition of done |
|---|------|-----------|-------|---------|--------|--------------------|
| 0 | Mapping, design, owner question round | – | lead | – | done | design-1.4.md, requirements.md |
| 1 | Rank-button bug: stable member order (owner, name, uuid) + gametest | S/E | Sonnet | 0 | done (102 / 136 per loader) | test fails with old order, passes with new; both loaders green |
| 2 | Server backend: admin permission (op 2 or node), config registry of both specs (scalar options, ranges, restart flag, pending values), get/set packets, per-team bonus (JSON, budget, command), player bonus read/write, team info packet for the Team tab, network version bump, gametests | L/H | Opus (own worktree) | 0 | open | all new packets with codec + handler tests; non-admin rejected; restart-only values pending; old team JSON loads; both loaders green |
| 3 | Hub frame + Claims tab: P key, tab bar, Party tab = party screen, chunk grid, selection + area requests, Private/Team toggle, budget bars | L/E | Sonnet (own worktree) | 1 | open | builds both loaders; screen works in dev client |
| 4 | Team tab + Admin tab UI on top of 2 and 3 | L/E | Sonnet | 2, 3 | open | builds; every admin action goes through task 2's packets |
| 5 | Docs/release prep: version 1.4.0+opac.0.32.7, README, changelog, CURSEFORGE.md, lang en + de complete | S/E | Sonnet | 4 | open | docs match behaviour |
| 6 | Acceptance: all test runs, dedicated server with two players, linkcheck vs World Map/Minimap | – | lead | 1–5 | open | checklist in report.md |
| 7 | Release: jars to ExportedJars/v1.4.0, dev instance mods/, push, CurseForge upload both loaders, report | – | lead | 6 | open | file ids in report.md |
