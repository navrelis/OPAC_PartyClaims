# Project — OPAC Team Claims (fork of Open Parties and Claims)

Fork of thexaero/open-parties-and-claims (remote `upstream`, base `upstream/1.21`) on github.com/navrelis/OPAC_PartyClaims.
Adds team claims (party pool budgets, roles, convert, FTB Teams sync), a hub with sidebar that is the only OPAC menu
(key P, OPAC menu key, World Map button; pages Overview/Party/Claims/Team/Settings/Admin), an admin menu, a grouped
searchable player config screen and Xaero's World Map right-click entries + map button (Fabric only). Current: 1.5.0.
Mod id `openpartiesandclaims`, jar names `opac-team-claims-<loader>-1.21.1-v<version>.jar`.

## Versions
- Minecraft 1.21.1, Java 21. Fabric loader 0.19.5, Fabric API 0.116.17+1.21.1, loom 1.11.8, Gradle 8.14.5.
- NeoForge 21.1.168 (NeoGradle 7.0.181). FTB Teams 2101.1.11 (optional). Mod version in `gradle.properties`.
- Xaero's World Map 1.47.0 (Fabric 1.21.1, newest on 2026-11-11, Modrinth version id `XIR7GzC3`); it bundles xaerolib 1.7.3.
  compile-only + dev client runs (`clientWorldMap`, `twoplayertestClient` source sets); never shipped. Minimap 26.6.0 for linkcheck.

## Structure
- `Common/src/main/java/xaero/pac/` — upstream OPAC code plus fork code. Fork code: `teamclaims/` (server logic, config
  `TeamClaimsServerConfig`, client toml `TeamClaimsClientConfig`), `client/teamclaims/` (client data, request sender,
  `WorldMapClaimingLogic`), `client/gui/hub/` (hub pages, `HubSidebar`, `HubTabs`; `hub/map/` claims map terrain),
  `client/gui/configview/` (config screen grouping/search/reset), `client/gui/party/` (party screen). Upstream edits are
  marked `[Team Claims]`. Upstream `MainMenu`/`ConfigMenu` stay unreferenced.
- `Fabric/src/main/java/xaero/pac/teamclaims/fabric/worldmap/` — World Map entries (mixin + plugin), map button.
- `Common/src/gametest/` — gametests (shared), wrapped by Fabric/NeoForge gametest entry points.
- `Fabric/`, `NeoForge/` — loader adapters, mixins, packet handlers, config registration.
- `Fabric/src/twoplayertest/**`, `tools/twoplayertest/` — dev-only automated two-player client test (`-PtwoPlayerTest`).
- `tools/linkcheck/` — JDK single-file tool: checks that World Map/Minimap jars link against our jar.
- Lang: `Common/src/main/resources/assets/openpartiesandclaims/lang/en_us.json` (full), `de_de.json` (fork keys only).
- Entry points: `ClientData.openMainMenu` (API, used by World Map) and `ClientTickHandler` (keys); hub = `HubTabs`.

## Build and test (one Gradle invocation per checkout at a time; Windows `gradlew.bat`)
- Build: `gradlew.bat :Fabric:build :NeoForge:build`
- Gametests: `:Fabric:runBootTest`, `:NeoForge:runTeamClaimsGameTest` (156 each in 1.5.0); with FTB Teams
  `:Fabric:runBootTestFtb`, `:NeoForge:runTeamClaimsGameTestFtb` (190); save compat `:Fabric:runSaveCompatTest`,
  `:NeoForge:runSaveCompatGameTest` (4). Pass = "All N required tests passed".
- Two-player test: `pwsh -File tools/twoplayertest/Run-TwoPlayerTest.ps1` (server + 2 Fabric clients with World Map, 79 checks,
  needs game.lock; needs `run-server/` eula.txt, server.properties, ops.json — gitignored, copy into a fresh worktree);
  self-check `:Fabric:runTwoPlayerTestSelfCheck -PtwoPlayerTest`.
- Linkcheck: `java tools/linkcheck/LinkCheck.java --provider <our jar> --consumer <WorldMap jar> --consumer <Minimap jar>`.

## Conventions
- Commits as Navrelis <111079703+navrelis@users.noreply.github.com>, no Co-Authored-By, no real names or local paths.
- Tabs for indentation, upstream style; minimal upstream delta; new packets ids 104+ (max 127).
- Release: jars to `ExportedJars/v<version>/` (gitignored), Nytheria dev instance
  `C:\Users\nikol\curseforge\minecraft\Instances\NytheriaDevelopment\mods` (old jar to `F:\Coding\NytheriaDevelopment\mods_backup\`),
  CurseForge project 1704256 via the curseforge-upload skill. Game lock `F:\Coding\.knowledge\game.lock`; never kill javaw.
