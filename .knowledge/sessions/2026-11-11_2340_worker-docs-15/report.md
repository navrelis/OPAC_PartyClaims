# Report worker docs-15

## Task T7: docs for 1.5.0
Files: `gradle.properties` (version `1.5.0+opac.0.32.7`), `README.md`, new `ExportedJars/v1.5.0/CURSEFORGE.md`. No lang change needed, no code change.
- README: new "Updating from 1.4.0" (no world/protocol change, client toml, hub replaces old menu, back to `v1.4.0`); "Hub (P)" rewritten (entry points, sidebar, pages Overview/Party/Claims/Team/Settings/Admin, claims map terrain/zoom/borders/ally); new "Config screens" (groups, search, reset), "Xaero's World Map" (entries, one-shot, button, known limitation), "Client config" (file, 3 options, defaults, Settings page); changelog 1.5.0 on top; test table counts 156/156/190/190/4/4 (all run, see below).
- CURSEFORGE.md copied from v1.4.0 (ExportedJars/**/*.jar is gitignored, CURSEFORGE.md is committed like 1.4.0): title, jar names, summary (245 chars), changelog, Updating from 1.4.0, hub/World Map/client config sections, feature list.
- All statements checked against code (HubTab/HubTabs, OverviewTabScreen, SettingsTabScreen, ClaimsTabScreen, HubGridFit, WorldMap* classes, TeamClaimsClientConfig after T5 merge 9a96eaf4, XPACKeyBindings, ClientTickHandler).
## de_de.json
Check (py, scratch): fork-added keys = en_us keys not in upstream/1.21 en_us (371). Missing in de before: 0, after: 0 (nothing changed). Upstream has no de file, so only fork keys count. Placeholder mismatches 0, de-only keys 0. 29 fork keys equal en are loanwords/labels (Party, Claims, Team, Admin, Server, Forceload, Radius, online...), left as is.
## Build/tests
- `gradlew.bat :Fabric:build :NeoForge:build` BUILD SUCCESSFUL; jars `open-parties-and-claims-{fabric,neoforge}-1.21.1-1.5.0+opac.0.32.7.jar`.
- `:Fabric:runBootTest` + `:NeoForge:runTeamClaimsGameTest` 156/156, `...Ftb` 190/190, save compat 4/4 on both (BUILD SUCCESSFUL).
## Open points
- `tools/twoplayertest/Run-TwoPlayerTest.ps1:262` still prints "1.4.0" in its summary title; README "Automated two-player test" paragraph (tabs wording, 55 assertions) is T6's to update.
- README says the Claims map is checkerboard where chunks are not loaded and ally detection is name based (from code/report); looks not verified in a running client.
- Code comments still say 1.4.0 for protocol revision 1 (correct: protocol unchanged).
- Merged claude/opac-1.5-unified-menu twice (last at 42789eb2 base); reset fix 6b (reset button appearing without relayout) not in; README wording stays valid.
