# T6 hand-over: two-player test for 1.5.0 (findings + implementation plan)

Harness: `Fabric/src/twoplayertest/java/xaero/pac/teamclaims/twoplayertest/` (`client/Scenario.java` 1243 lines,
`client/Ui.java`, `client/ClientDriver.java`, `ScenarioPlan.java`, `server/ServerSnapshot.java`), manifest
`tools/twoplayertest/expected-assertions.txt` (must list every ScenarioPlan id in order; self-check compares),
runner `tools/twoplayertest/Run-TwoPlayerTest.ps1`, Gradle block `Fabric/build.gradle` ~l.395-500.

## 1. Build: World Map on the test clients only
- `twoPlayerTestClient1/2` use `source(sourceSets.twoplayertest)` -> no World Map. Add, inside the `-PtwoPlayerTest` block:
  `twoplayertestClient { runtimeClasspath = configurations.clientWorldMapRuntimeClasspath + sourceSets.twoplayertest.runtimeClasspath + sourceSets.twoplayertest.output }`
  (same pattern as `clientWorldMap`, l.149) and `source(sourceSets.twoplayertestClient)` in the two client runs. Server and
  self-check stay without World Map. Compile: World Map + xaerolib are `modCompileOnly` on main, so twoplayertest
  (compileClasspath += main.compileClasspath) can reference `xaero.map.*` directly (GuiMap's superclass is in xaerolib, also compileOnly).
- Jar check after build: `jar tf` of the Fabric/NeoForge jars must show no `twoplayertest` classes.

## 2. Existing scenario fixes
- Hub first open = `OverviewTabScreen` (HubTabScreen). `isHub` (PartyScreen || HubTabScreen) still right. keyOpens may
  additionally expect OverviewTabScreen on the first P.
- Sidebar: `HubSidebar` field `sidebar` (in HubTabScreen, and in PartyScreen: own field `sidebar`), `getEntries()` =
  Buttons with message = `tab.getLabel()`; entry of the open page is inactive. Replace `clickTab`/`Ui.button(screen,label)`
  (ambiguous) by a click on the sidebar entry found in `sidebar.getEntries()`. Navigate: if no hub open ->
  `HubTabs.openLast`, then click entry. Keep direct `HubTabs.open(ADMIN)` in `adminHidden` (tests the fallback).
- ClaimsTabScreen fields unchanged: `fit` (HubGridFit.Fit radius/cell), `gridLeft`, `gridTop`, `centerX`, `centerZ`,
  `selection`, `claimAsButton`, `state` (READY). Fit now `HubGridFit.fit(side, zoom)`; static `zoom` (0..2, default 1),
  buttons "-"/"+" (fields `zoomOutButton`/`zoomInButton`); `changeZoom` calls rebuildWidgets.
- Terrain filled: `terrain` field (TerrainCache, private map `entries`, `get(x,z)`): wait until every grid chunk with
  `mc.level.getChunkSource().getChunkNow(x,z) != null` has `terrain.get(x,z) != null` (then a few frames), per zoom step.

## 3. New steps (ids to add to ScenarioPlan + manifest; add "overview","settings" to SHOT_TABS of both players)
- Overview (P1, after "ranked", usedSub still main): `OverviewTabScreen` fields `claimButton` (label CLAIM/UNCLAIM,
  sends `/openpac-claims claim|unclaim` for the player's chunk and CLOSES the hub), `claimAsButton`, `partyPageButton`,
  `forceloadButton`, `aboutPartyButton`. Claim -> snapshot claim at own chunk (private, owner P1); reopen, Unclaim -> none.
- Settings (P1, before the Claims team toggle): `SettingsTabScreen` button `gui.xaero_pac_ui_my_player_config` ->
  `PlayerConfigScreen` (may first wait for sync). Fields: `optionRows` (List<OptionRow(entry, element)>), `elements`
  (displayed: pinned sub-config controls, headers, `ConfigOptionRowListElement`s), `searchBox` (EditBox message
  `gui.xaero_pac_config_search`), `viewState`. Group header = Button with message "▾ <title> (n)" (expanded) / "▸ ..."
  (collapsed); click toggles + relayout. Default: >12 options -> only first group expanded. 12 elements per page.
  Search: entry.searchText = lowercase name+id+tooltip; type `shareLocationWithParty` -> only
  `playerConfig.parties.shareLocationWithParty` ("Share Position With Party", default true, in the default
  playerConfigurablePlayerConfigOptions list) matches. Value widget = CycleButton at the row's x,y (WidgetListElement
  fields `x`,`y`); click -> false, synced to server. Reset button "↺" (18 px at x+w-18) exists only while value !=
  target AND only after a re-init (see report: product UX issue) -> record whether it appears at once; otherwise relayout
  (reopen screen / edit search) then click it -> server value back to true.
  ServerSnapshot: add to playerData per player `options: {"parties.shareLocationWithParty": config.getEffective(v2 PlayerConfigOptions.SHARE_LOCATION_WITH_PARTY)}`.
- Claims zoom: screenshots at zoom 0,1,2 after terrain filled (restore zoom 1).
- World Map P1 (after admin steps): open map by pressing the bound key of `xaero.map.controls.ControlsRegister.keyOpenMap`
  (default M) via `d.pressKey`; await `mc.screen instanceof xaero.map.gui.GuiMap`. Our button: Button in screen children
  with message starting `Claiming: ` = `Ui.text("gui.xaero_pac_wm_button", Ui.text("gui.xaero_pac_wm_mode_team"|"..._private"))`
  (+ " used/limit" when budget known). Click -> server `playerData.<p>.usedSub` toggles (after Claims steps P1 is on the
  team sub). Right-click menu: GuiMap private fields `mapTileSelection` (new `MapTileSelection(x0,z0)` + `setEnd(x1,z1)`,
  chunk coords), `rightClickDim` (ResourceKey<Level> = mc.level.dimension()), `rightClickX/Y/Z` (block coords); then
  invoke private `handleRightClick(IRightClickableElement target=screen, int x, int y)` -> real `GuiRightClickMenu` in
  field `rightClickMenu` (screenshot it). Its private `actionOptions` holds the options; ours: names
  `gui.xaero_pac_wm_claim_as_team`, `..._claim_as_private`, `gui.xaero_pac_wm_toggle_mode`, `gui.xaero_pac_wm_open_menu`
  (RightClickOption `getDisplayName()`, `isActive()`); they are only added if World Map's "Claim Selected"
  (`gui.xaero_pac_claim_chunks`) is there. Run one: `menu.selectId(index, true)` (real path: onSelected -> onAction if active).
  "Claim as Team" restores usedSub (ClaimAsToggle.set back) -> assert 4 chunks teamClaim=true AND usedSub back.
  "Claim as Private" on another 2x2 -> 4 private claims. "Open claims menu" -> `HubTabs.openLast(.., parent=GuiMap)`.
  Selections (relative to own chunk cx,cz; ServerSnapshot covers radius 6): team (cx+2..cx+3, cz+2..cz+3), private
  (cx-3..cx-2, cz+2..cz+3); P2 (its chunk +2..+3 x, -3..-2 z) cannot overlap even with a 1-chunk spawn offset.
  Requests are clamped to `getMaxClaimDistance()`.
- Player2 (after crafted): open map, button present with label; "Claim as Team" active on the client (needs only a team
  sub); server rule: allowed iff `TeamRole.valueOf(P2 rank).isAtLeast(TeamRole.valueOf(team roles.claim))` (rank names =
  role names; owner->OWNER). In the run the owner sets claim role MEMBER->CLAIMER and P2's rank MEMBER->next. Expect 4 team
  claims if allowed, else none.

## 4. Run
- run-server: copy eula.txt, server.properties, ops.json (Player1 op 4) from
  `.claude/worktrees/opac-admin-rework-d73a06/run-server` into this worktree's `run-server/`.
- Lock: create `F:\Coding\.knowledge\game.lock` (free at 23:45) with name/time, run
  `pwsh -File tools/twoplayertest/Run-TwoPlayerTest.ps1`, delete the lock. Never kill javaw not started by the run.
- `gradlew.bat :Fabric:runTwoPlayerTestSelfCheck -PtwoPlayerTest` (6 gametests).
