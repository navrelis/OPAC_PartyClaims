# Plan — v1.5.0: one menu + Xaero's World Map integration

Status: open / in progress / in review / done. Size S/L, difficulty E/H. One Gradle invocation per checkout at a time.
(v1.4.0 plan: git history of this file.) Working branch `claude/opac-1.5-unified-menu`.

| # | Task | Size/Diff | Model | Depends | Status | Definition of done |
|---|------|-----------|-------|---------|--------|--------------------|
| 0 | Push 1.4.0 state (tag `v1.4.0`), questions, plan | – | lead | – | done | tag + branch pushed |
| 1 | Hub frame: sidebar navigation, pages Overview + Settings, all tabs laid out in the content area, OPAC key + `openMainMenu` API open the hub, old main-menu features moved in | L/H | Opus (hub-frame, 1a) + Sonnet (hub-finish, 1b) | 0 | done (147/147 per loader, harness compiles, merged c85a6513; looks unverified in client -> T6) | builds both loaders, all gametests green, old menu unreachable, harness compiles |
| 2 | World Map (Fabric): right-click entries (claim as team / private one-shot, mode switch, open menu), map button with mode + budget, client config toml; World Map + extracted xaerolib on the dev client runs only | L/E | Sonnet (worldmap + worldmap-fix) | 0 | done (151/151 per loader on the combined branch, jars free of World Map, runClient OK; in-world behaviour unverified -> T6) | Fabric + NeoForge build; gametests green both loaders; dev client starts with World Map |
| 3 | Detailed settings screen (PlayerConfigScreen) grouped by category, collapsible, search box, reset to default; public API unchanged | L/E | Sonnet (settings-view) | 0 | done (143/143 per loader, linkcheck 0 missing, merged b9b4a033; looks unverified in client -> T6 screenshots) | builds; gametests green; linkcheck vs World Map clean |
| 4 | Claims map: terrain from loaded chunks, overlay + borders, 3-step zoom, ally category (name-based: client has no ally member list) | L/E | Sonnet (claims-map) | 1a | done (merged fa52b270; its 5 map tests are not registered in Fabric's gametest fabric.mod.json -> fixed in T5; looks untuned -> T6) | builds; tests green |
| 5 | Settings page: client options section (World Map button on/off + position, map default zoom), convert radius cycles up to 16 (server enforces convertMaxRadius, no protocol change); register TeamClaimsMapTest in Fabric gametests; server defaults unchanged (decisions) | S/E | Sonnet (reuse worldmap-fix) | 1, 2, 3 | done (156/156 per loader, merged) | builds; tests green |
| 6 | Two-player test: new pages + World Map entries + map button, screenshots | L/H | Opus (twoplayer-15, local_61febd7e) | 1–5 | in progress: investigation only, worker at limit; plan in handover-T6.md on branch claude/elastic-kalam-03b411 (24136153): test clients need a World Map source set, no run-server/ in fresh worktrees; reported UX bug: config reset button appears only after relayout -> needs a new worker (Opus) from that branch + a fix task | runner green, screenshots checked by lead |
| 7 | Docs: version 1.5.0, README, changelog, CURSEFORGE.md, de lang complete | S/E | Sonnet (docs-15, local_3cbb40de) | 1–5 | in progress | docs match behaviour |
| 8 | Acceptance + release (instance, CurseForge, push, report) | – | lead | 1–7 | open | report.md in session folder |

Parallel now: 1, 2, 3 (disjoint files; lang keys at fixed anchors). Usage tally (long = 2, short = 1): Opus T1a (2);
Sonnet T2 (2), T3 (2), T1b (2), T2 fix (1), T4 (2).
