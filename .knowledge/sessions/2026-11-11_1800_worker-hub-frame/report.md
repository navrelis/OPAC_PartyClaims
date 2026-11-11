# Worker hub-frame — report

## Task 1 (hub frame: sidebar, Overview/Settings, entry points) — UNFINISHED, stopped at context limit
State: WIP commit on branch `claude/jovial-babbage-54736b`. **Does not compile yet**: `HubTabs.create` references
`OverviewTabScreen` and `SettingsTabScreen`, which are not written. Nothing built or tested.

Done (files):
- `hub/HubTab.java`: pages OVERVIEW, PARTY, CLAIMS, TEAM, SETTINGS, ADMIN with item icons (compass, white banner, map,
  shield, comparator, command block). Label keys `gui.xaero_pac_hub_tab_overview` / `_settings` NOT yet in lang files.
- `hub/HubNavigation.java` (new, no client classes): visibility, visible list, opening tab (null/hidden last -> OVERVIEW),
  sidebar widths. Rule: collapse to icons when `screenWidth - expandedWidth < 360`; expanded = 39 + widest label, max 120;
  collapsed = 30. (426 wide = scale 3 @ 1280x720 -> collapsed; 960 = scale 2 @ 1080p -> expanded.)
- `hub/HubSidebar.java` (new): panel (vanilla list background texture + separator), entries = `Button` subclass with
  message = page label (harness `Ui.button(label)` still finds them; current page inactive + highlighted; collapsed ->
  label tooltip). Content area: left = sidebar + 8, right margin 8, top `CONTENT_TOP` (26, page title at y 10), bottom
  height - 6. `renderHeader(...)` = label + thin line (page titles and section headers).
- `hub/HubTabScreen.java`: sidebar instead of tab bar, `contentLeft/Width/Top/Bottom()`, `contentCenterX()`, page title,
  hidden page -> OVERVIEW, closes with hub key OR OPAC menu key (`HubTabs.isCloseKey/isCloseMouseButton`, new).
- `hub/HubTabs.java`: lastTab nullable (first open = OVERVIEW), delegates to HubNavigation, create() has the new pages.
  Public `open`, `openLast`, `isHubKey`, `isHubMouseButton` unchanged.
- `hub/ClaimsTabScreen.java`: laid out in content area; claiming-mode dropdown (MainMenu logic copied) at top of the
  panel when `ClaimsHubLogic.isClaimingModeChoiceShown(usableModes)` (> 1); budget lines moved to
  `HubBudgetLines.allLines()`.
- `hub/TeamTabScreen.java`, `hub/AdminTabScreen.java`, `party/PartyScreen.java`: laid out in content area (Team headings
  use `renderHeader`); PartyScreen has the sidebar + content* methods; `HubTabBar.java` deleted (unused).

Left to do:
1. `OverviewTabScreen` (brief item 4: party info / "no party" + button to Party page, `HubBudgetLines.allLines()`,
   `ClaimAsToggle` button with `ClaimsHubLogic.findTeamSubId(...)`, current chunk info, claim/forceload/anchor buttons +
   about party copied from `MainMenu.updateWidgets/onClaimButton/onForceloadButton/onAnchorButton/onAboutPartyButton`).
2. `SettingsTabScreen` (item 6: copy `ConfigMenu` buttons/rules into sections "My settings" / "Server" with
   `HubSidebar.renderHeader`, parent = this; room at the bottom for "Client options").
3. Entry points (item 7): `ClientData.openMainMenuScreen` and `ClientTickHandler` openModMenu -> `HubTabs.openLast`,
   mark `//[Team Claims]`.
4. Lang keys at end of en_us/de_de: `gui.xaero_pac_hub_tab_overview`, `gui.xaero_pac_hub_tab_settings`, section labels.
5. Gametests in `TeamClaimsHubTestCases` + Fabric/NeoForge wrappers for HubNavigation (order/visibility, opening tab,
   collapse at 426/480/960) and `isClaimingModeChoiceShown`.
6. Build both loaders, `:Fabric:runBootTest`, `:NeoForge:runTeamClaimsGameTest`, harness compile (`-PtwoPlayerTest`).

Harness notes: tab buttons are still `Button`s with the page label, so `clickTab` works; the hub now opens on OVERVIEW
the first time, so steps that expect the Party page after P / `openLast` need `openTab(PARTY)`; Claims grid lookups use
fields `gridLeft/gridTop/fit` (unchanged names). If the claim-mode dropdown shows (op player), the Claims panel buttons
move down by 14 px.
Open points: the Back buttons stay on every page (inside the content area); OPAC menu key (') also closes the hub.
