# Worker twoplayer-2 — T6 two-player test for 1.5.0

## T6 (done): runner green twice (79/79), screenshots in `screenshots.md` here
- Changed: `Fabric/build.gradle` (`-PtwoPlayerTest` block: source set `twoplayertestClient` = twoplayertest + World Map/xaerolib
  runtime for the two client runs; server and self-check unchanged), `ScenarioPlan.java` (+24 ids, shots of overview/settings),
  `client/Scenario.java` (sidebar navigation, Overview claim/unclaim, config groups/search/toggle/reset-at-once/reset, claims
  zoom 0/1/2, World Map button + right-click entries P1 and P2), `client/Ui.java` (set/invoke), `server/ServerSnapshot.java`
  (playerData.options.parties.shareLocationWithParty), `expected-assertions.txt` (73 ids), runner title 1.5.0, README paragraph.
- Merged `claude/opac-1.5-unified-menu` (reset fix + docs) before runs 2-4.
- Tests: `:Fabric:runTwoPlayerTestSelfCheck -PtwoPlayerTest` 6/6; `Run-TwoPlayerTest.ps1` run 1 FAIL 5 (reset fix not merged;
  my right-click helper), run 2 FAIL 1 (see open point 1), runs 3+4 PASS 79/0/0 (20261112-000701, 20261112-001031);
  `:Fabric:build :NeoForge:build` OK, `jar tf` of both jars: no twoplayertest and no xaero/map entries.
- Screenshot folder: `build/twoPlayerTest/20261112-001031/Player{1,2}/screenshots` in this worktree (list in screenshots.md).

## Open points
1. Product behaviour: our four World Map entries are added only next to World Map's "Claim Selected"; on a selection that is
   already claimed World Map shows "Unclaim Selected" instead, so "Open claims menu" and the mode switch disappear there too.
   The test uses unclaimed selections. Decide whether that is wanted.
2. Looks (for the screenshot review): with render distance 4 (test options) zoom 0 of the Claims map is mostly empty grid;
   the "↺" reset glyph renders very small; the config search box sits below the option rows.
3. Right-click menu in the test: GuiMap private state is set by reflection (mapTileSelection, rightClickDim/X/Y/Z), then
   the real `handleRightClick` and `GuiRightClickMenu.selectId` run; a real mouse right-click is not simulated.
4. P2 Claim as Team: the expected outcome is computed from the snapshot (rank vs claim role); in the run P2 was allowed
   and got 4 team claims.
