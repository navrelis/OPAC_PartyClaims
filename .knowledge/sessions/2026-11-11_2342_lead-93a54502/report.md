# Report — OPAC Team Claims 1.5.0 (lead 93a54502, successor of lead 9238a1f9)

Branch `claude/opac-1.5-unified-menu` (pushed). 1.4.0 kept as tag `v1.4.0`. Released 2026-11-12.

## Implemented, per requirement
- **Hub as the single menu** (T1): sidebar pages Overview / Party / Claims / Team / Settings / Admin; P, OPAC's menu key,
  World Map's OPAC button and the `openMainMenuScreen` API open it; old main/config menus unreferenced.
- **Xaero's World Map 1.47.0, Fabric** (T2, T9d): right-click "Claim as Team" / "Claim as Private" (one-shot),
  "Switch to Team/Private claiming", "Open claims menu"; switch + menu entry also on claimed selections; map button
  "Claiming: <mode> used/limit" with position option. World Map is compile-only, never shipped.
- **More appealing, easier config** (T3, 6b, T9a/b): grouped, collapsible, searchable player config screen; per-option
  reset button that follows the value at once, drawn icon; long text labels trimmed with full name in the tooltip.
- **Claims map** (T4): terrain of loaded chunks, borders, 3 zoom steps, ally category (name based).
- **Options and defaults** (T5): client toml (World Map button on/off + position, map start zoom) editable on the
  Settings page; convert radius up to 16 (server `convertMaxRadius` still limits); server defaults unchanged.
- **Docs** (T7, T9): README (hub, config screens, World Map, client config, updating from 1.4.0, changelog),
  `ExportedJars/v1.5.0/CURSEFORGE.md`, de_de complete. Version `1.5.0+opac.0.32.7`.
- **Two-player test** (T6): covers the new pages, grouped config (search/toggle/reset), claims zoom, World Map button
  and right-click entries for both players; test clients run with World Map.

## This session's work (lead 93a54502)
Merged: T6 investigation notes, T7 docs (docs-15), 6b reset fix (reset-15), T6 test (twoplayer-2), task 9 polish +
harness lookup follow-up (polish-15). Main changed files of this session: `ConfigOptionRowListElement.java`,
`TextWidgetListElement.java`, `WorldMapRightClickEntries.java`, `WorldMapClaimingLogic.java`, lang en/de, README,
CURSEFORGE.md, `Fabric/build.gradle` (twoplayertestClient source set), `Fabric/src/twoplayertest/**`, runner title.

## Key decisions
See `decisions.md` (2026-11-11/12 v1.5 lines): mixin approach, Modrinth compile-only dep, one-shot semantics, hub replaces
menus, config screen improved in place, client toml, no protocol change, T6 to Opus, polish before release, slug override.

## Checks run (lead, on the merged branch)
- `:Fabric:build :NeoForge:build` green; jars contain no `twoplayertest` or `xaero/map` classes.
- Gametests: `runBootTest` / `runTeamClaimsGameTest` 156/156 each (after task 9); `...Ftb` 190/190 each; save compat 4/4 each.
- Linkcheck of the 1.5.0 Fabric jar vs World Map 1.47.0, Minimap 26.6.0, Unified Xaero World Map 2.0.4: OK, 0 warnings.
- Two-player test: lead run 1 = 77/79 (harness looked for the old "↺" text; product correct in screenshots), after the
  harness fix lead run 2 = **79/79 PASS** (`build/twoPlayerTest/20261112-005132`). Worker runs 3+4 also 79/79.
- Screenshots checked by the lead: hub/Overview/Settings at GUI 2 and 4, claims map zoom, grouped config, reset icon,
  World Map menu and button.
- No new TODO/FIXME/debug output in the diff since `v1.4.0`.

## Release
- Nytheria instance: `opac-team-claims-fabric-1.21.1-v1.5.0.jar` installed, 1.4.0 jar moved to
  `F:\Coding\NytheriaDevelopment\mods_backup\`. The owner asked for the upload after the jar was in the instance; no
  separate instance start check (perf harness) was run by the lead.
- CurseForge project 1704256, Release, both loaders: NeoForge file 9566847, Fabric file 9566889
  (https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims-team-claims/files/9566847, .../files/9566889).

## Manual test checklist (not automated)
- Real mouse right-click on the World Map (the test sets World Map's selection state by reflection) on unclaimed and on
  claimed selections; button positions other than top centre.
- World Map together with Unified Xaero World Map (in Nytheria): entries and button present.
- Claims map in the Nether and with a large render distance; ally colours with a real allied party.
- Sidebar icon-only mode on a narrow window / GUI scale 4+; Settings client options on a very small screen.
- NeoForge client without World Map: hub and Settings page (no World Map options shown).
- One-shot claim while over a claim limit (documented limitation: uses the old mode).

## Model usage
Whole 1.5 effort (long = 2, short = 1): Opus 6 (T1a hub-frame, T6 investigation, T6 implementation), Sonnet 13
(T2, T2 fix, T3, T1b, T4, T5, T7, 6b, T9). Opus ≈ 32 %. Override: T6 to Opus (recorded in decisions.md).

## Known limitations and recommendations
- Ally detection in the Claims map is name based (the client has no ally member list).
- One-shot claim uses the old mode when the server refuses the sub-config change (over limit, not OP).
- World Map entries depend on World Map's `SupportOpenPartiesAndClaims.addRightClickOptions` (mixin `require = 0`): a
  World Map update can silently drop them; rerun linkcheck + the two-player test after World Map updates.
- The uploader's cfwidget slug lookup keeps returning 404 for two Fabric relations; worth fixing in the tool
  (look up by project id).
