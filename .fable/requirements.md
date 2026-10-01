# Requirements (user answers 2026-09-20)

- Goal: remove NeoForge, make OPAC PartyClaims a **Fabric** mod for MC **1.21.1**, Fabric Loader **0.19.5** (Nytheria pack: fabric-api 0.116.17, FCAP 21.1.6, Xaero minimap 26.5.0 / worldmap 1.46.0).
- Vision confirmed 1:1: team sub-config `team_<name>` per member; team claims count against every member's personal limit (overhead, no bonuses); any member can unclaim / toggle forceload on team claims; settings (color/name/etc.) synced, admin/owner-only edits, PROTECT_FROM_PARTY locked false, team sub-config not manually deletable; forceloads active while any member online; `/openpac-parties create <name>` + `/teamclaims create <name>`.
- Base: **re-apply onto upstream v0.31.6** (branch `1.21` HEAD `c0d97b37`); native `partyOwnedClaims` stays off / untouched.
- Delivery: **patched OPAC jar**, mod id `openpartiesandclaims`, replaces stock jar on client + server.
- Allowed: delete NeoForge leftovers (NeoForge/, neoforge_docs/, other_mods/, run dirs, template README); Fabric 2-client + server test setup (run dirs, start-all/stop-all); fix bugs found (e.g. team JSON in global config dir → per-world; hardcoded English strings → lang keys).
- NOT requested: deploying into the Nytheria instance — deliver the jar only.
- Workflow: commander mode, no further questions unless hard blocker; final report in `.fable/report.md`.

## Phase 2 answers (2026-09-20)
- Do: automated fake-player logic tests; fix partyOwnedClaims leak; transfer leaver's team claims to party owner.
- User explicitly authorised setting `eula=true` in dev `run-server/eula.txt`. Also set `online-mode=false` there.
- NOT selected: deploying into Nytheria.
- HARD BOUNDARY (user, mid-phase-2): do not interact with the Nytheria modpack instance in any way; all work stays inside `C:\Users\nikol\Desktop\Coding\OPAC_PartyClaims`. Deployment is off the table unless the user raises it.
