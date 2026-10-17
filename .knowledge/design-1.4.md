# Design — v1.4.0 "Claims hub and admin menu" (2026-10-17)

## Why
The owner (Nytheria pack, Fabric 1.21.1) finds claim settings and claiming hard to reach. A team claim today only happens
when a player picks the `team_<name>` sub-config in a buried config screen (then World Map's "Claim Selected" claims for
the team). Team claims cannot be configured in a GUI and their limits cannot be raised. Server settings live in two toml
files and many `/opac` commands. On top, the party screen's rank buttons seem to change the wrong row.

## Owner decisions (question round 2026-10-17)
- UI: one tabbed hub, tabs **Party | Claims | Team | Admin**, vanilla look like the 1.2 party screen, opened with **P**
  (the party key moves from O to P and opens the hub). Claims tab has a chunk grid (claim colours, no terrain) with
  click/drag claiming, unclaiming and forceloading, budget bars and a Private/Team toggle that also decides what World
  Map's "Claim Selected" does.
- Admin scope: every number / on-off / choice / text option of OPAC's server toml (`openpartiesandclaims-server.toml`)
  and of the Team Claims toml; per-player bonus claims, forceloads, anchors (and anchor range); a **new per-team bonus**
  for team claims and team forceloads. Restart-only options are saved to the toml and marked "after restart". The
  protection block/entity/item lists (and other list options) stay toml-only.
- Permission: operator level 2 **or** permission node `xaero.pac_admin_menu` through OPAC's configured permission system.
  The server checks every request, the client only hides the tab.
- Loaders: Fabric and NeoForge, version `1.4.0+opac.0.32.7`.

## Goals
1. Rank-button bug fixed and proven by a test (done first, own commit).
2. Hub screen on P, tabs as above; the Party tab is the existing party screen.
3. Claims tab: grid around the player (radius chosen by the builder, at least 8 chunks), owner colours from the synced
   claims, own private / own team / ally / others / server distinguishable, forceloaded chunks marked, player marker,
   hover tooltip (coords, owner, claim name, forceloaded). Click or drag selects a rectangle; buttons Claim / Unclaim /
   Forceload / Unforceload send OPAC's existing area requests (`IClientClaimsManagerAPI.requestArea*`). Private/Team
   toggle sets `claims.usedSub` (main or the team sub-config) through the existing option packet. Budget bars: private
   claims and forceloads, team claims and forceloads (existing limits packet 17 and budget packet 103).
4. Team tab: team name, members count, budget with over-limit deadline, claim colour and claims name (team sub-config
   settings), roles for claim / unclaim / forceload, own territory-message toggle, convert to team / to personal with a
   radius, button to the full team protection settings (existing PlayerConfigScreen of the team sub-config). Editable
   for party owner and party admins, read-only for others, server enforces as today.
5. Admin tab (only with permission): sections "OPAC server", "Team Claims", "Players", "Teams".
   - Config sections list every scalar option of the two specs with current value, range / allowed values, comment as
     tooltip, a "restart" badge; edit and save per option; the server validates against the spec, writes the toml and
     answers with the new value or an error.
   - Players: pick an online player or type a name (offline players with known config too); show limits and usage;
     edit bonus claims, bonus forceloads, bonus anchors, bonus anchor range.
   - Teams: list of teams (name, members, used / limit); edit per-team bonus claims and bonus forceloads.
6. Per-team bonus: new fields in the team JSON (missing = 0, old files load), limit = (formula as today) + bonus; also
   a command `/teamclaims bonus <team> claims|forceloads <n>` (op 2 or the node) for scripts and tests. Lowering a bonus
   can put a team over its limit; the existing over-limit rule then applies as for a member leaving.
7. Tests green on both loaders with and without FTB Teams plus new gametests; dedicated server check with two players.
8. 1.4.0 release: README, changelog, CURSEFORGE.md, en + de lang, jars, dev instance, push, CurseForge upload.

## Non-goals
- No terrain rendering in the grid (World Map already shows terrain); no new World Map menu entries (closed source).
- No editing of list options (protection lists, dimension lists, configurable-option lists) in the GUI.
- No change to OPAC claim rules, budgets formula (except the additive team bonus), or command behaviour.

## Anti-goals (would make it a failure)
- A non-admin able to change server settings through a crafted packet.
- A restart-only option silently half-applied at runtime.
- Breaking existing world data or the 1.3.0 behaviour when nobody uses the new screens.

## Constraints
- Same as 1.3.0 (`requirements.md`): mod id, MC 1.21.1, Fabric + NeoForge, upstream delta minimal and marked
  `[Team Claims]`, no new shipped dependencies, existing world data keeps loading.
- New packets take ids from 104 up (index byte, max 127). A 1.3.0 client and a 1.4.0 server (or the reverse) must be
  refused at login (network version), not crash.

## Lead calls (not asked)
- The O key becomes P by default (owner's choice); note: vanilla binds P to "Social Interactions", the controls screen
  shows the conflict and either can be rebound. Stated in the report.
- Per-team bonus applies even below `teamClaimsMinMembers` (an admin can grant a single-player team land).
- Restart-only options: the new value goes to the toml file only; the running server keeps the old value until restart,
  and the admin tab shows "pending: <value>" next to the live one.
- The OPAC claiming-mode choice (player / party / server) for ops stays in OPAC's main menu, the Claims tab claims in
  the player's current mode.
