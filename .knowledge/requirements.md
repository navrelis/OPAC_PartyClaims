# Requirements (session 2026-11-11, v1.5.0 "one menu + World Map")

Earlier state: 1.4.0 (`2a6e34d7`, tag `v1.4.0`, branch `claude/opac-admin-rework-d73a06`). Requirements of 1.4.0 and
older: git history of this file.

## Goals (owner, 2026-11-11)
1. Xaero's World Map integration (Fabric 1.21.1, newest World Map = 1.47.0 from the Nytheria instance): right-click
   entries "Claim as Team", "Claim as Private", a switch for the claim mode, "Open claims menu"; a button on the map
   showing mode + budget ("Claiming: Team 34/120"), click switches.
2. One menu: the hub (P) is the default menu and replaces OPAC's old main menu (OPAC key and API open the hub too).
   Pages Overview, Party, Claims, Team, Settings, Admin; Settings holds all of OPAC's detailed config screens.
3. Overhaul: more appealing, easier to navigate (sidebar with icons, sections, grouped + searchable detailed settings
   with reset to default), vanilla-style widgets, no new library.
4. Claims map in the menu: terrain from loaded chunks behind the claim colours, zoom, legend.
5. Defaults and options: lead reviews Team Claims + client defaults (OPAC server defaults unchanged), adds client options
   (World Map button on/off + position, map default zoom). Every change documented.
6. Everything documented; owner can go back to 1.4.0 (tag `v1.4.0`, pushed before any change).

## Done means
- Version 1.5.0, both loaders build; World Map features Fabric only (NeoForge builds and works without them).
- All gametests green (both loaders, with/without FTB Teams, save compat); two-player test extended and green with
  World Map in the dev client; linkcheck vs World Map 1.47.0 + Minimap 26.6.0 clean.
- Fabric jar into the Nytheria dev instance (old to mods_backup), CurseForge Release both loaders, pushed to
  `claude/opac-1.5-unified-menu` (never main, never force); report with manual checklist.

## Standing constraints
- As in 1.4.0 (`project.md` conventions): no new shipped dependencies (World Map compile-only + dev runtime only),
  upstream delta minimal and marked `[Team Claims]`, existing world data and configs keep loading.
- Owner said: "do whatever you think is good", no further questions needed.
- Owner's own `unified-xaero-worldmap` 2.0.4 only mixes into World Map's MapProcessor/MapSaveLoad (no conflict).
