# Report worker settings-view

## Task 1: PlayerConfigScreen categories, search, reset (1.5.0)
Changed (all `[Team Claims]`-marked where upstream):
- `PlayerConfigScreen.java`: option rows collected separately, shown through group headers / collapse / search; Builder + public API unchanged (only private ctor changed).
- `WidgetListScreen.java`: page count computed live, page clamped, `setPage` (protected), background hook call, title "(1/max(1,n))". `WidgetListElement.java`: `renderRowBackground` hook. `TextWidgetListElement.java`: `setValueAndConfirm`.
- New: `ConfigOptionRowListElement` (value widget + reset button + alternating row background), package `client/gui/configview/` (`ConfigOptionGrouping`, `ConfigViewState`, `ConfigViewLayout`, `ConfigResetRule`; pure logic).
- Lang en_us + de_de: 15 keys each, directly before `gui.xaero_pac_hub_claim_as_private` (no `hub_admin_menu` key existed).
- Tests: `TeamClaimsConfigViewTestCases` (8) + Fabric/NeoForge wrappers; Fabric `fabric.mod.json` entrypoint line added after `TeamClaimsPartyScreenTest`.

Grouping (first id segment; `claims.protection.*` split by option category, GENERAL* categories stay in their first segment; single group = no headers). Counts by reading PlayerConfigOptions (test asserts 3..30, general excepted):
claims 11, parties 5, general 2, protection: blocks 10, block triggers 8, entities 7, players 4, portals/teleport 4, item use 3, item pickup 5, spawning 7, other 7 (73 total). Unknown groups from other mods: titled from the key.
Defaults: list of <=12 options: all expanded; else only the first group; collapse state kept per config type for the session (static). Search: terms (all words), name/id/tooltip, lower-case; matching groups forced open, headers inactive while searching, state untouched; text not remembered between opens. Header never in last row of a column (filler).
Reset: sub-config -> null (inherit); PLAYER/PARTY_CLAIMS -> server default player config value; else option default. Button shown only if editable and value differs; uses the normal change path (setCastValue + syncToServer, text via confirm path). Value widgets are 20 px narrower to make room.

Tests: `:Fabric:build :NeoForge:build` green; `:Fabric:runBootTest` and `:NeoForge:runTeamClaimsGameTest` 143/143 (135 + 8); linkcheck vs World Map 1.47.0: 0 missing, 0 errors.

Open points:
- Not looked at in a running client (no GUI run): layout/looks unverified.
- Search box sits under the list between the prev/next buttons (no room at the top without moving the title/buttons); focus only on click.
- fabric.mod.json line may conflict with hub-frame's gametest registration (trivial merge).
- Reset on a main config whose default player config equals the option default is the same; "general" group has only 2 options.
