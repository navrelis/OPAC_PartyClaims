# Plan — v1.4.0: claims hub and admin menu

Status: open / in progress / in review / done. Size S/L, difficulty E/H. One Gradle invocation per checkout at a time.
(v1.3.0 plan: git history of this file.) Design: `design-1.4.md`.

| # | Task | Size/Diff | Model | Depends | Status | Definition of done |
|---|------|-----------|-------|---------|--------|--------------------|
| 0 | Mapping, design, owner question round | – | lead | – | done | design-1.4.md, requirements.md |
| 1 | Rank-button bug: stable member order (owner, name, uuid) + gametest | S/E | Sonnet | 0 | done (102 / 136 per loader) | test fails with old order, passes with new; both loaders green |
| 2 | Server backend: admin permission (op 2 or node), config registry of both specs (scalar options, ranges, restart flag, pending values), get/set packets, per-team bonus (JSON, budget, command), player bonus read/write, team info packet for the Team tab, network version bump, gametests | L/H | Opus (own worktree) | 0 | done (113 / 147 per loader, save-compat 4) | all new packets with codec + handler tests; non-admin rejected; restart-only values pending; old team JSON loads; both loaders green |
| 3 | Hub frame + Claims tab: P key, tab bar, Party tab = party screen, chunk grid, selection + area requests, Private/Team toggle, budget bars | L/E | Sonnet | 1 | done (110 per loader; screens not yet seen in game) | builds both loaders; screen works in dev client |
| 4a | Team tab + Claims tab uses team info (sub index, max action size) | L/E | Sonnet (this checkout) | 2, 3 | done (128 per loader) | builds; Team tab edits go through existing commands and option packets |
| 4b | Admin tab (configs, players, teams) | L/E | Sonnet (worktree ../opac-t4-admin) | 2, 3 | done (135 / 169 per loader, merged 9ca8a7a6) | builds; every admin action goes through task 2's packets |
| 5 | Docs/release prep: version 1.4.0+opac.0.32.7, README, changelog, CURSEFORGE.md, lang en + de complete | S/E | Sonnet | 4 | done (rechecked after 4b) | docs match behaviour |
| 8 | Hub key P vs vanilla Social Interactions (P): with both on P vanilla wins (one mapping per key); route the conflicting social click to the hub | S/E | Sonnet (this checkout) | 4b | done (135 / 135 both loaders; in-game proof via T9) | P opens the hub with default keys; build + tests green |
| 9 | Automated two-player client test (owner: no computer-use): dev-only scripted client driver for runServer + runClient + runClient2, opens hub/Claims/Team/Admin, clicks rank buttons, screenshots, asserts client and server state, report file | L/H | Opus (worktree ../opac-clienttest) | 4b | done (55 / 55 PASS, 2 real runs, no harness change; runner script added 60606a99) | one command runs server + 2 clients unattended, writes screenshots + pass/fail per assertion, exits; committed |
| 6 | Acceptance: all test runs, dedicated server with two players, linkcheck vs World Map/Minimap | – | lead | 1–5, 8, 9 | open | checklist in report.md |
| 7 | Release: jars to ExportedJars/v1.4.0, dev instance mods/, push, CurseForge upload both loaders, report | – | lead | 6 | open | file ids in report.md |

Usage tally (long = 2, short = 1): Opus T2 (2) + T9 (2) = 4; Sonnet T1 (1), T3 (2), T4a (2), T5 (1), T4b (2), docs recheck (1), T8 (1) = 10. Opus ≈ 29 %.
