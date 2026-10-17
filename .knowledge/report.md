# Final report — OPAC Team Claims 1.4.0 (Fabric + NeoForge 1.21.1), sessions 2026-10-17 / 2026-10-18

Branch `claude/opac-admin-rework-d73a06` on github.com/navrelis/OPAC_PartyClaims (based on `v1.3-dev`, = 1.3.0); `main`
and `fabric-port` untouched. Version `1.4.0+opac.0.32.7`. (1.3.0 report: git history of this file.)

## Release
| | Fabric | NeoForge |
|---|---|---|
| Jar (ExportedJars/v1.4.0, gitignored) | `opac-team-claims-fabric-1.21.1-v1.4.0.jar` | `opac-team-claims-neoforge-1.21.1-v1.4.0.jar` |
| SHA-256 | `af61fbad…ccbe1c` | `e1accc83…4d4` |
| CurseForge (project 1704256, Release) | file **9233932** | file **9233929** |
| Relations | fabric-api (req.), forge-config-api-port (req.), ftb-teams-fabric (opt.) | none (as 1.1.0–1.3.0) |

Links: https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims-team-claims/files/9233932 (Fabric) and
https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims-team-claims/files/9233929 (NeoForge); new files
may show as processing until CurseForge has reviewed them.
Relation-slug override (cfwidget slug lookup 404 for forge-config-api-port and ftb-teams-fabric, both confirmed by project
id 547434 / 438497): approved by the owner in chat, 2026-10-18.

Dev instance `NytheriaDevelopment\mods`: `opac-team-claims-fabric-1.21.1-v1.4.0.jar` added (same SHA-256 as above);
`opac-team-claims-fabric-1.21.1-v1.3.0.jar` moved to `F:\Coding\NytheriaDevelopment\mods_backup\`. Nothing committed there.

## What was implemented, per requirement
- **Rank-button bug** (party screen changed a different row): root cause: member rows were sorted by rank, so after a
  rank change the clicked member moved to another row and the next click hit someone else. Fix: rows ordered owner, name,
  uuid (393e7956); regression gametests; the two-player test proves it in a real client (a third member sits between
  owner and Player2, rows unchanged after the click, server shows only Player2 changed).
- **Tabbed hub on P** (Party | Claims | Team | Admin): 3e868c55, 8153a389, 9ca8a7a6. New key binding id so P reaches
  players whose options.txt already saved O.
- **P-key finding** (T8, f332ab36): vanilla Social Interactions is also P and vanilla keeps one KeyMapping per key, so
  the hub key never fired (P opened Social Interactions). Fix: while the hub key and Social Interactions share key and
  modifier, the hub takes the clicks of both (drained at client-tick start, before vanilla's handleKeybinds). Rebinding
  Social Interactions restores vanilla behaviour. NeoForge's `KeyMapping.same()` was not used (it treats P and Ctrl+P as
  the same); the modifier is compared through the platform key-binding helper. Docs updated (README, CURSEFORGE.md).
- **GUI for team claims**: Claims tab (chunk grid, select, claim/unclaim/forceload/unforceload, Private/Team toggle,
  budgets, oversize selection disables actions) and Team tab (roles, territory messages, convert, team settings;
  read-only for members).
- **Permission-gated Admin menu** (op level 2 or `xaero.pac_admin_menu`): server options of OPAC and Team Claims (live
  vs restart-only with pending value), player bonuses with lookup, team bonuses; every request checked server side
  (5ec9c0dc backend, 9ca8a7a6 UI).
- **Automated two-player test** (owner requirement, no computer-use; T9, 04e77a06 + 60606a99): dev-only, gated by
  `-PtwoPlayerTest`, one command `pwsh -File tools/twoplayertest/Run-TwoPlayerTest.ps1` (server + two Fabric clients,
  scripted driver, server snapshots, screenshots, results.json / summary.md, exit code). Not in any jar (listing checked).
- **Docs**: README (hub, admin menu, team bonus, updating from 1.3.0, two-player test), changelog, CURSEFORGE.md.

## Changed files (main areas)
- `Common/src/main/java/xaero/pac/client/gui/hub/` (HubTabs, Claims/Team/Admin tab screens, AdminConfigView,
  AdminValueRules): the hub UI.
- `Common/src/main/java/xaero/pac/client/ClientTickHandler.java`, `client/controls/keybinding/IKeyBindingHelper.java` +
  Fabric/NeoForge helpers: hub key and the P conflict.
- Party screen (member order), `client/teamclaims/TeamClaimsClientData.java` / `TeamClaimsClientRequests`: client data
  and the only send path for hub requests.
- Server side Team Claims: admin access, config registry, bonus storage (optional JSON fields), packets 104–113, command
  `/teamclaims bonus`; network version 7001 / NeoForge protocol 1.11.0-teamclaims.1.
- Lang en + de; gametests (Common/src/gametest + Fabric/NeoForge wrappers).
- `Fabric/build.gradle` (dev-only block), `Fabric/src/twoplayertest/**`, `tools/twoplayertest/**`: the two-player test.
- README.md, ExportedJars/v1.4.0/CURSEFORGE.md.
102 files, +14.9k / −65 lines against 1.3.0 (most of it tests and the new UI).

## Key decisions (full list in decisions.md)
- Member rows ordered owner, name, uuid (no rank).
- Hub tabs are separate screens with a shared tab bar; the Party tab is the 1.3 party screen.
- All OPAC server options carry upstream's `worldRestart` flag, so admin edits to them are saved to the toml and apply
  after a restart (Team Claims options apply live); upstream flags unchanged.
- P conflict: route Social Interactions clicks to the hub while both share the key; no change to vanilla bindings.
- In-game acceptance = automated scripted two-player run (owner); T9 to Opus (new architecture, false greens costly).

## Checks and tests run (lead, on the final code)
| Run | Fabric | NeoForge |
|---|---|---|
| Default gametests (`runBootTest` / `runTeamClaimsGameTest`) | 135 / 135 | 135 / 135 |
| With FTB Teams (`runBootTestFtb` / `runTeamClaimsGameTestFtb`) | 169 / 169 | 169 / 169 |
| Save compatibility (1.2.0 world data) | 4 / 4 | 4 / 4 |
| Build | green | green |
- **Two-player test** (real dedicated server + 2 Fabric dev clients, default key bindings): **55 / 55 PASS, 0 FAIL,
  0 SKIPPED**, first run; a second run identical. Results, screenshots (GUI scale 2 and 4) and logs:
  `F:\Coding\OPAC_PartyClaims\.claude\worktrees\opac-clienttest\build\twoPlayerTest\20261017-233735\`
  (`summary.md`, `results.json`, `Player1\screenshots\` 26 PNGs, `Player2\screenshots\` 13 PNGs). The lead checked the
  counts and the rank-click and read-only screenshots. Covers: P opens/closes the hub and not while typing; party
  create/invite/accept; rank click; Claims claim/unclaim/Team toggle/oversize; Team tab owner edit + member read-only;
  Admin tab op only, live option, restart-only pending, team bonus, player bonus via lookup, the suggestion-button click
  (no crash), a non-admin's crafted admin requests rejected with a log warning.
- Harness self-check `:Fabric:runTwoPlayerTestSelfCheck -PtwoPlayerTest`: 6 / 6; dry run OK.
- Jar listings without `-PtwoPlayerTest`: same entries as before, 0 harness entries.
- Linkcheck vs Xaero's World Map 1.47.0 + Minimap 26.6.0 (on 9ca8a7a6; unchanged API since): 0 missing.
- Nytheria dev instance start with the 1.4.0 jar and all 647 mods (perf harness `startup`, player profile because the
  owner was playing at the time): title screen after 139.6 s, OPAC 1.4.0+opac.0.32.7 loaded, no OPAC WARN/ERROR lines.

## Manual test checklist (not automated)
- Claim map colours, markers and hover tooltip; Forceload / Unforceload buttons; Xaero's World Map "Claim Selected".
- Convert with its confirmation; team settings screen; the territory-messages toggle.
- A small window / GUI scale 3 on a real monitor resolution.
- A 1.3.0 client joining a 1.4.0 server is refused.
- Admin path via the permission node (only op level was tested).
- NeoForge: hub on Ctrl+P with Social Interactions on P (both mappings may fire; untested).

## Model usage
Long tasks count 2, short 1. Opus: T2 (2), T9 (2) = 4. Sonnet: T1 1, T3 2, T4a 2, T5 1, T4b 2, docs recheck 1, T8 1,
README test section 1 = 11. Opus ≈ 27 %. No precision overrides.

## Known limitations and recommendations
- The Team tab button "Team protection & appearance settings..." does not fit at GUI scale 2 / 1280×960 and scrolls
  (vanilla scrolling label); consider a shorter label in 1.4.1.
- Screenshots of the two-player test can contain a hover tooltip where the real mouse cursor sits over a client window.
- Allies show as "others" in the Claims grid; the client caps the convert radius at 0..4 (from earlier notes).
- `renderCount` in the Admin Players page rebuilds the filtered rows every frame (minor).
- The `run-*` line in `.gitignore` also matches `Run-*.ps1` on Windows (case-insensitive); the runner script is
  force-added. A rename or an exception line would avoid the trap for new files.
- Graphify: rebuilt code-only (AST, 11,873 nodes, 276 communities) in this worktree's `graphify-out/` (untracked, by
  the earlier decision not to commit it); the old graph in the main checkout was built from another path and the
  fabric-port code and is superseded. Docs and images were not extracted (no LLM pass).
