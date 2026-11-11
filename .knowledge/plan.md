# Plan — v1.5.0: one menu + Xaero's World Map integration

Status: open / in progress / in review / done. Size S/L, difficulty E/H. One Gradle invocation per checkout at a time.
(v1.4.0 plan: git history of this file.) Working branch `claude/opac-1.5-unified-menu`.

| # | Task | Size/Diff | Model | Depends | Status | Definition of done |
|---|------|-----------|-------|---------|--------|--------------------|
| 0 | Push 1.4.0 state (tag `v1.4.0`), questions, plan | – | lead | – | done | tag + branch pushed |
| 1 | Hub frame: sidebar navigation, pages Overview + Settings, all tabs laid out in the content area, OPAC key + `openMainMenu` API open the hub, old main-menu features moved in | L/H | Opus (hub-frame, 1a) + Sonnet (hub-finish, 1b) | 0 | done (147/147 per loader, harness compiles, merged c85a6513; looks unverified in client -> T6) | builds both loaders, all gametests green, old menu unreachable, harness compiles |
| 2 | World Map (Fabric): compile-only + dev runtime dep, conditional mixin adding right-click entries (claim as team / private, mode switch, open menu), map button with mode + budget, client config toml (button on/off, position) | L/E | Sonnet (worldmap) | 0 | in review: implemented on claude/suspicious-lichterman-76e72b (3f371582, NeoForge 139 green, code reviewed OK); blocker: Fabric dev runtime lacks nested xaerolib -> fix = Sonnet worldmap-fix (local_aef1bca9, running) | Fabric + NeoForge build; gametests green both loaders; logic tests; dev client starts with World Map |
| 3 | Detailed settings screen (PlayerConfigScreen) grouped by category, collapsible, search box, reset to default; public API unchanged | L/E | Sonnet (settings-view) | 0 | done (143/143 per loader, linkcheck 0 missing, merged b9b4a033; looks unverified in client -> T6 screenshots) | builds; gametests green; linkcheck vs World Map clean |
| 4 | Claims map: terrain from loaded chunks, zoom, ally category (legend exists); default-zoom client option moves to T5 | L/E | Sonnet (claims-map, local_e9839a1e) | 1a | in progress (temp stubs commit to drop on merge) | builds; tests green; screenshot in two-player test |
| 5 | Settings page: client options section (World Map button on/off + position, map default zoom), client convert-radius cap = server convertMaxRadius; server defaults unchanged (decisions) | S/E | Sonnet (reuse worldmap-fix) | 1, 2, 3 | open | builds; tests green |
| 6 | Two-player test: new pages + World Map entries + map button, screenshots | L/H | Sonnet or Opus | 1–5 | open | runner green, screenshots checked by lead |
| 7 | Docs: version 1.5.0, README, changelog, CURSEFORGE.md, de lang complete | S/E | Sonnet | 1–5 | open | docs match behaviour |
| 8 | Acceptance + release (instance, CurseForge, push, report) | – | lead | 1–7 | open | report.md in session folder |

Parallel now: 1, 2, 3 (disjoint files; lang keys at fixed anchors). Usage tally (long = 2, short = 1): Opus T1a (2);
Sonnet T2 (2), T3 (2), T1b (2), T2 fix (1), T4 (2).
