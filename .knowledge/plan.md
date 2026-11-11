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
| 6 | Two-player test: new pages + World Map entries + map button, screenshots | L/H | Opus (twoplayer-15 investigation; twoplayer-2, local_0f8c3742, implementation) | 1–5 | in progress: twoplayer-15 plan (handover-T6.md) merged; twoplayer-2 implements it; Settings reset assertion needs 6b merged first | runner green twice, screenshots checked by lead |
| 6b | Config reset button follows the value without relayout (PlayerConfigScreen, configview) | S/E | Sonnet (reset-15, local_fb052f19) | 3 | done (merged; button always created, render() sets visible/active from the supplier; build green, worker 156/156; in-game check -> T6) | button shows right after change, hides after reset; builds; gametests green both loaders |
| 7 | Docs: version 1.5.0, README, changelog, CURSEFORGE.md, de lang complete | S/E | Sonnet (docs-15, local_3cbb40de) | 1–5 | done (merged; build green, worker ran all 6 gametest runs 156/156/190/190/4/4; README two-player paragraph + runner title "1.4.0" left to T6) | docs match behaviour |
| 8 | Acceptance + release (instance, CurseForge, push, report) | – | lead | 1–7 | open | report.md in session folder |

Parallel now: 6, 6b, 7 (disjoint files). Usage tally (long = 2, short = 1): Opus T1a (2), T6 investigation (2),
T6 implementation (2) = 6; Sonnet T2 (2), T3 (2), T1b (2), T2 fix (1), T4 (2), T5 (1), T7 (1), T6b (1) = 12. Opus ≈ 33 %.
