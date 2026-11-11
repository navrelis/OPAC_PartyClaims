# Worker hub-finish — report

## Task 1 (finish the hub frame: Overview, Settings, entry points, lang, tests)
Merged `claude/opac-1.5-unified-menu` and `claude/jovial-babbage-54736b` (no conflicts).

Files:
- new `hub/OverviewTabScreen.java`: two columns. Left: Party (name + members, or "Not in a party", button to the Party page), Claims
  (ClaimAsToggle with `findTeamSubId`, `HubBudgetLines.allLines()`). Right: "This chunk" (chunk, owner, claim name) with
  Claim/Unclaim, Forceload/Unforceload, Anchor/Unanchor (only anchor-based claiming) and About party; logic, commands, active rules,
  tooltips copied from `MainMenu`; refresh in `tick()`; team info requested like the Claims tab. Unanchor confirm returns to the page.
- new `hub/SettingsTabScreen.java`: left "My settings" (my player config, party claims config), right "Server" (default, server claims,
  expired, wilderness, other player: label + EditBox + button, Enter key as in ConfigMenu). Same active rules. Opened screens get this page
  as parent. Space below "My settings" is free for "Client options".
- `ClientData.openMainMenuScreen` and `ClientTickHandler` openModMenu -> `HubTabs.openLast`, marked `//[Team Claims]`.
  `MainMenu.java`/`ConfigMenu.java` unchanged (now unused by entry points).
- lang en_us/de_de (end of file): `gui.xaero_pac_hub_tab_overview/_settings`, `_overview_no_party/_open_party/_chunk`, `_settings_my/_server`.
  Other keys reuse existing ones (`hub_tab_party/claims`, `hub_tt_chunk/owner/name/unclaimed`, `ui_*`). All keys used by hub/party code exist
  (only dynamic prefixes `hub_team_role_`, `party_screen_rank_` show up in a grep).
- gametests `TeamClaimsHubTestCases` + Fabric/NeoForge wrappers: page order/visibility, opening tab (first = OVERVIEW, hidden last tab ->
  OVERVIEW), sidebar collapse at 426/480/960 (+ limit 456/457, 120 cap), `isClaimingModeChoiceShown`. +4 tests.

Results: `:Fabric:build :NeoForge:build` green; `:Fabric:runBootTest` and `:NeoForge:runTeamClaimsGameTest` "All 147 required tests passed"
(143 + 4); `:Fabric:compileTwoplayertestJava -PtwoPlayerTest` compiles with no change. Not run in a real client: the screens are only compiled
and laid out by calculation (no visual check).

Open points:
- Overview/Settings have a Back button (like the other pages), centred at the bottom of the content area; Back goes to `parent` (null = closes).
- Claim/Forceload/Anchor/About close the hub as MainMenu did; the Overview does not show the claiming-mode dropdown (it is on the Claims page).
- Layout was not seen on screen; at GUI scale 3 (240 high) the left column ends about 175 px below the top, should fit.
- The twoplayertest scenario still assumes Party after `openLast`; the hub now opens on OVERVIEW the first time (scenario change later).
