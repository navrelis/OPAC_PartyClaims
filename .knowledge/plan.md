# Plan — v1.5.0: one menu + Xaero's World Map integration

Status: open / in progress / in review / done. Size S/L, difficulty E/H. One Gradle invocation per checkout at a time.
(v1.4.0 plan: git history of this file.) Working branch `claude/opac-1.5-unified-menu`.

| # | Task | Size/Diff | Model | Depends | Status | Definition of done |
|---|------|-----------|-------|---------|--------|--------------------|
| 0 | Push 1.4.0 state (tag `v1.4.0`), questions, plan | – | lead | – | done | tag + branch pushed |
| 1 | Hub frame: sidebar navigation, pages Overview + Settings, all tabs laid out in the content area, OPAC key + `openMainMenu` API open the hub, old main-menu features (claim/forceload/anchor here, claiming mode for ops) moved in | L/H | Opus (hub-frame) | 0 | in progress | builds both loaders, all gametests green, old menu unreachable, harness compiles |
| 2 | World Map (Fabric): compile-only + dev runtime dep, conditional mixin adding right-click entries (claim as team / private, mode switch, open menu), map button with mode + budget, client config toml (button on/off, position) | L/E | Sonnet (worldmap) | 0 | in progress | Fabric + NeoForge build; gametests green both loaders; logic tests; dev client starts with World Map |
| 3 | Detailed settings screen (PlayerConfigScreen) grouped by category, collapsible, search box, reset to default; public API unchanged | L/E | Sonnet (settings-view) | 0 | in progress | builds; gametests green; linkcheck vs World Map clean |
| 4 | Claims map: terrain from loaded chunks, zoom, legend, default zoom client option | L/E | Sonnet | 1, 2 | open | builds; tests green; screenshot in two-player test |
| 5 | Settings page: client options section; default changes (lead list) | S/E | Sonnet | 1, 2, 3 | open | builds; tests green |
| 6 | Two-player test: new pages + World Map entries + map button, screenshots | L/H | Sonnet or Opus | 1–5 | open | runner green, screenshots checked by lead |
| 7 | Docs: version 1.5.0, README, changelog, CURSEFORGE.md, de lang complete | S/E | Sonnet | 1–5 | open | docs match behaviour |
| 8 | Acceptance + release (instance, CurseForge, push, report) | – | lead | 1–7 | open | report.md in session folder |

Parallel now: 1, 2, 3 (disjoint files; lang keys at fixed anchors). Usage tally (long = 2, short = 1): Opus T1 (2);
Sonnet T2 (2), T3 (2).
