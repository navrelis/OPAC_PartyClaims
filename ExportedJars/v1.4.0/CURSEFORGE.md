# OPAC: Team Claims — CurseForge listing (v1.4.0)

Project name suggestion: **Open Parties and Claims: Team Claims**
Game version: **Minecraft 1.21.1** · Mod loaders: **Fabric** and **NeoForge** (one file per loader) · Environment: **client + server (required on both)** · License: **LGPL-3.0-only**

Files for this release (upload each with the matching mod loader selected):

| File | Mod loader | Needs |
|---|---|---|
| `opac-team-claims-fabric-1.21.1-v1.4.0.jar` | **Fabric** | Fabric Loader 0.14 or newer, **Fabric API**, **Forge Config API Port** (>= 21.0.5), Java 21. Optional: FTB Teams |
| `opac-team-claims-neoforge-1.21.1-v1.4.0.jar` | **NeoForge** | NeoForge 21.1.x, Java 21 (nothing else). Optional: FTB Teams |

FTB Teams is an **optional dependency** (relationship type "Optional Dependency" on CurseForge), not a required one. Nothing of FTB Teams, FTB Library or Architectury is inside the jars.

## Summary

Paste this into the CurseForge "Summary" field (max 256 characters, this one has 233):

```
Open Parties and Claims 0.32.7 with real team bases: a team claim pool per party, a hub screen on the P key with a claim grid, team tab and admin menu, team roles, team forceloads, optional FTB Teams sync. Fabric and NeoForge 1.21.1.
```

## Categories

| Slot | Category | Why |
|---|---|---|
| **Main** | **Server Utility** | Chunk claiming, protection and party management are server administration / multiplayer infrastructure first. |
| Additional | **Utility & QoL** | Removes the daily friction of running a shared base: no more "who owns this chunk?", no typing party commands, no manual permission juggling. |
| Additional | **Map and Information** | Team claims show up with one shared color and name on Xaero's Minimap and World Map, exactly like normal OPAC claims. |
| Additional | **Adventure and RPG** | Parties, shared territory and team identity are the backbone of faction-, town- and guild-style servers. |
| Additional | **API and Library** | Ships the complete, unchanged Open Parties and Claims API, so addons and integrations written for OPAC keep working. |

If CurseForge limits the number of additional categories, keep them in the order listed above.

Suggested release type: **Beta** for both files. 1.4.0 is covered by automated game tests on each loader (with and without FTB Teams installed, plus the tests that load the world data of a 1.2.0 server), but the new hub screens, the admin menu and the new network version have not had a long run on real servers yet, so both stay Beta until they have.

## Changelog for this file

Paste this into the CurseForge "Changelog" field of each file:

```
Open Parties and Claims 0.32.7 for Minecraft 1.21.1, with Team Claims 1.4.0

New since 1.3.0:
- Hub screen on the P key (new key binding "Open Parties and Claims Hub"; P is also vanilla's "Social Interactions" key, rebind either one in Controls). Tabs: Party (the party screen of 1.3.0), Claims, Team and Admin.
- Claims tab: a chunk grid around you in the claim colours. Click or drag to select, then claim, unclaim, forceload or unforceload; hover for owner, claim name and forceload state; budget lines. The "Claim as: Private / Team" button decides who owns what you claim and also what Xaero's World Map "Claim Selected" claims.
- Team tab: team name, team budget (bonus included) and the over-limit countdown, the roles for claim, unclaim and forceload, your own territory-message switch, convert to team and to personal with a radius and a confirmation, the Private / Team switch, and a button to the team's protection settings. Editable for the party owner and admins, read-only for everybody else; the server enforces it as before.
- Admin menu (permission level 2 or the permission node xaero.pac_admin_menu): every number, on/off, choice and text option of OPAC's server config and of the Team Claims server config, with search, a restart badge and the "after restart" value; the bonus claims, bonus forceloads, bonus anchors and bonus anchor range of players; the bonus of teams. List options (protection lists etc.) stay toml-only. Restart-only options are written to the toml and take effect after a restart, all others apply at once. Every change is logged in the server log. The server checks every request.
- Team bonus: a per-team bonus of team claims and team forceloads on top of the formula, also below teamClaimsMinMembers. Stored as optional fields bonusClaims and bonusForceloads in the team JSON. New command /teamclaims bonus <team> [claims|forceloads <n>]. Lowering a bonus can start the over-limit deadline.
- Fix: the member rows of the party screen keep their place when a rank changes (they were sorted by rank, so the clicked member moved to another row).
- New automated game tests on both loaders for the hub logic, the admin permission, the config registry (live and restart-only writes), the team and player bonus and the new packets.

IMPORTANT when updating from 1.3.0: update the server AND every client together. The network version changed (7 to 7001, NeoForge protocol 1.11.0-teamclaims.1) and the hub and the admin menu use ten new packets (ids 104-113), so a 1.3.0 client cannot join a 1.4.0 server and a 1.4.0 client cannot join a 1.3.0 server. Existing worlds load as they are: nothing is unclaimed, and the team files only get the bonus fields for teams that have a bonus. The party key is now P (a new key binding, the old O binding is gone). Make a backup first, as always.
```

## Description

Everything below this line is the project description.

---

# Open Parties and Claims: Team Claims

**Your party built the base together. Now your party can own it together.**

This is a drop-in replacement build of **[Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims)** (OPAC) by Xaero96 for **Fabric and NeoForge 1.21.1**. It contains everything OPAC does — chunk claims, claim protection, forceloading, claiming anchors, parties, allies, the full config UI, Xaero's Minimap / World Map integration, the complete API — and adds one thing OPAC players keep asking for: **Team Claims**.

## The problem it solves

In stock OPAC every claim belongs to exactly one player. For a group that builds together this gets awkward fast:

- The base is claimed by whoever got there first. If that player is offline, nobody else can expand, unclaim or fix the borders.
- Forceloaded farms stop working when "the wrong person" logs off.
- Everyone has to configure their own claim color, name and permissions, so one base looks like a patchwork on the map.
- Giving the whole party access means fiddling with per-player protection settings, and one wrong toggle locks your friends out of their own storage room.
- Managing the party means typing `/oparties ...` commands, and if you also run FTB Teams, creating the same team twice.

Team Claims makes the party the unit that matters, while keeping every personal claim exactly as it was.

## Updating from 1.3.0

**Read this before you update a server.** 1.4.0 adds the hub screen on the **P** key, an admin menu for server owners and a per-team bonus. It is still based on Open Parties and Claims 0.32.7.

- **Update the server and every client together.** The network version changed (7 to 7001, NeoForge protocol 1.11.0-teamclaims.1) and ten new packets (ids 104-113) belong to the hub and the admin menu. A 1.3.0 client cannot join a 1.4.0 server and a 1.4.0 client cannot join a 1.3.0 server.
- **Existing worlds load as they are.** Nothing is unclaimed by the update. The team JSON files get the optional fields `bonusClaims` and `bonusForceloads` only for teams that have a bonus.
- **The hub key is P.** The party screen (key O) is now the Party tab of the hub. It is a new key binding, so everybody gets P by default, also players who had rebound O (that rebinding is lost). P is also vanilla's "Social Interactions" key: if both open, rebind either one in Controls.
- **Fixed:** in the party screen the member rows no longer move when a rank changes.
- Servers that do not use the new features see no change: the Admin tab and `/teamclaims bonus` are only for permission level 2 and the node `xaero.pac_admin_menu`, and a team without a bonus behaves as in 1.3.0.

## Updating from 1.2.0

**Read this before you update a server.** 1.3.0 is based on Open Parties and Claims 0.32.7 (1.2.0 had 0.31.6). Xaero's World Map 1.47.0 and Xaero's Minimap 26.6.0 call OPAC API that was added in 0.32.x; with 1.2.0 installed, every right-click on the world map crashed the client with a `NoSuchMethodError` for `IClientClaimsManagerAPI.usingAnchorBasedClaiming()`.

- **Update the server and every client together.** The network version changed (6 to 7, NeoForge protocol 1.10.0 to 1.11.0) and the party screen packets moved to new ids. A 1.2.0 client cannot join a 1.3.0 server and a 1.3.0 client cannot join a 1.2.0 server.
- **Existing worlds load as they are.** Claims, team claims, parties, player and team configs, `data/opacteamclaims/**` and the saved data `opacteamclaims_data` are kept, and nothing is unclaimed by the update. Upstream's claim files get an empty list of anchors the next time they are saved.
- **Make a backup first.** Going back to 1.2.0 after 1.3.0 has run on a world is not supported.
- The new options of OPAC's anchor-based claiming appear in `openpartiesandclaims-server.toml` with upstream's defaults. **`anchorBasedClaiming` is off**, and while it is off (and the new Team Claims option `overLimitEnforcement` stays `true`), Team Claims behaves exactly as in 1.2.0: budgets, limits, over-limit unclaim, FTB Teams sync, party screen and key.
- If you turn anchors on, read "Anchor-based claiming and team claims" below first.

## Updating from 1.1.0

**Read this before you update a server.** 1.2.0 splits the claim budget (see "Two separate budgets"). Your world data, claims, parties and team configs load unchanged, but:

- **A team with a single member has a team claim limit of 0** with the default `teamClaimsMinMembers = 2`. When the 1.2.0 server starts, such a team is over its limit: its members get a warning (at their next login if they are offline), and after the grace period (`overLimitGraceHours`, 7 days of real time by default, also counted while the server is off) the newest team claims are unclaimed, until none are left. Team forceloads work the same way. A team of two or more members is only affected if it has more team claims or team forceloads than the new limits allow.
- **The ways out:** a second member joins the party, the members turn the team claims into private claims with `/teamclaims convert topersonal` (they have to fit into the players' own claim limit), they unclaim what they do not need, or you lower `teamClaimsMinMembers` (for example to `1`) in `openpartiesandclaims-teamclaims-server.toml`.
- **Update the server and every client together.** The party screen and the budget numbers use new packets, so a 1.1.0 client with a 1.2.0 server (or the other way round) is not supported.
- The new options of the Team Claims server config are added with their defaults on the first start.

## How it works

### 1. Create a party — get a team
Create a party with a name in a single command, or in the party screen:

```
/oparties create Iron Wolves
```

(or `/teamclaims create Iron Wolves`). The name is checked first (it must not be empty and may be at most 24 characters by default, see the server config below), then it becomes the party name, and every member — now and in the future — automatically receives a **team sub-config** called `team_iron_wolves` in their OPAC player config. Nobody has to set anything up. Existing parties are picked up automatically as well.

### 2. Claim for the team
Open the OPAC config (or use the usual OPAC sub-config option) and select the team sub-config as your **active sub-config for new claims**. From that moment, every chunk you claim — by command, keybind or by dragging on Xaero's World Map — is a **team claim**. Switch back to your main config whenever you want to claim something private. Personal and team claims live side by side.

Already claimed land with the wrong config? `/teamclaims convert` fixes it, see "Converting claims" below.

### 3. Everyone can manage team land
By default any party member can:

- **unclaim** a team claim, no matter which member originally claimed it,
- **enable or disable forceloading** on a team claim,
- build, break and interact everywhere on team land — party access on team claims is **always on and cannot be switched off by accident**.

No admin mode, no asking the original claimer to come online. If you want it stricter, see "Team roles".

### 4. Two separate budgets
Private claims and team claims do not compete:

- **Private claims** count against OPAC's own per-player limits (`maxPlayerClaims`, `maxPlayerClaimForceloads`, bonus options and permission-based limits) and nothing else. A team claim never uses one up.
- **Team claims** count against the team's pool only, whoever of the members made them. The pool depends on the number of members (the owner included, invited players not): a team below `teamClaimsMinMembers` (2) members has none, a team with exactly that many has `teamClaimsBase` (500) team claims and `teamForceloadsBase` (10) team forceloads, and every further member adds `teamClaimsPerExtraMember` (25) and `teamForceloadsPerExtraMember` (2). With the defaults: 2 members = 500 / 10, 3 = 525 / 12, 4 = 550 / 14.
- The claim counter in the claim UI and on the map shows the team's numbers while your team sub-config is selected, and your private numbers otherwise. `/teamclaims info` and the party screen show both.
- Server owners keep full control: the private limits stay OPAC's own, and the team pool is a handful of config options. An admin can also give one team a **bonus** of team claims and team forceloads (admin menu or `/teamclaims bonus`), which is added to the pool, also below `teamClaimsMinMembers`.

### 5. When a team is over its limit
A member leaves, you lower the limits, the update from 1.1.0 — a team can end up with more team claims (or team forceloads) than its limit allows. It is not punished at once:

- Every online member gets a chat message with the number over the limit, the **time left** (7 days by default, real time, also while the server is off) and the ways out; offline members get it at their next login, and everybody is reminded once a day. `/teamclaims info` and the party screen show the countdown. The team can make no new team claims meanwhile.
- If the team is back within its limit before the deadline — unclaim some, get more members, turn team claims into private claims with `/teamclaims convert topersonal` — the deadline is cancelled and nothing is removed.
- Otherwise the **most recently made team claims are unclaimed**, newest first, until the team is within its limit. For team forceloads the newest forceloads are turned off and the claims stay.
- **Prefer to never remove anything?** Set `overLimitEnforcement = false` in the Team Claims server config. A team above its limit then keeps all its team claims and team forceloads: no deadline, no warnings, no countdown in chat, `/teamclaims info` or the party screen. The limits still apply: the team can make no new team claims or team forceloads until it is back within them. A deadline that is already running is dropped at the next check; switching the option back on starts a fresh one for a team that is still above its limit.

### 6. One look, one set of rules
A team claim should look and behave the same no matter who claimed it:

- The team gets a **shared claim color** (generated from the party, changeable) and a **shared claim name**.
- All protection options of the team sub-config are **synchronized to every member automatically**, including members who join later.
- Only the **party owner and party admins** can change team settings. For everyone else the options are shown read-only, and the server enforces it.
- The team sub-config **cannot be deleted** by hand — it disappears by itself when you leave the party.

### 7. Forceloading that follows the team
Forceloaded team chunks stay loaded **while any party member is online**, not just the player who flipped the switch. They behave like OPAC's own forceloads: random ticks and natural spawning work, and a dimension holding them keeps ticking without players. Your shared farms keep running when the claimer goes to bed and someone else is still playing.

When the last member logs off, the tickets are released — right away by default, or after a **grace period** the server owner can set (`forceloadGraceMinutes`). A member who comes back within that time cancels the release, so a short disconnect does not stop your farms.

### 8. Members come and go, the base stays
When a member leaves or is kicked, their team claims are **transferred to the party owner automatically** — including the forceload state — instead of turning into private land in the middle of your base. Both sides get a chat message. This also works if it happened while the server was offline. The team limit shrinks with the member count, so a team that is then over its limit gets the warning from section 5.

If the whole party is disbanded, its team claims become **private claims of the members who made them, as far as each member's own claim limit allows**; the rest is unclaimed at once, newest first, and forceloads are kept as far as the private forceload limit allows. Disbanding therefore never turns a team pool into extra private land. The disband confirmation says so.

Membership changes are handled as events and take effect in the same server tick, so even a quick join and leave is not missed.

## Anchor-based claiming and team claims

Anchor-based claiming is a feature of upstream OPAC (since 0.32), **off by default**. With the OPAC server option `anchorBasedClaiming` on, a player can only claim chunks within the range of one of their own **claiming anchors** (per player and per dimension). Team Claims keeps it that way: there is no team pool of anchors, and a team claim is a claim of the member who makes it, so it obeys that member's anchors.

| Option (OPAC server config) | Default | Meaning |
|---|---|---|
| `anchorBasedClaiming` | `false` | Turns anchor-based claiming on. |
| `maxPlayerClaimingAnchors` | `3` | Anchors a player can have in a dimension. |
| `claimingAnchorRange` | `16` | Chunks in every direction from an anchor (a square) in which the player can claim. |
| `claimingAnchorBonusPerPartyMember` / `claimingAnchorBonusForPartyOwner` | `0.1` / `0` | Extra anchors for party members; they apply with `partyOwnedClaims`. |
| `maxPlayerClaimingAnchorsPermission` / `claimingAnchorRangePermission` | permission nodes | Permission nodes that override the two values above. |

The player config has the options `claims.bonusAnchors` and `claims.bonusAnchorRange`. Anchors are placed and removed with `/oclaims anchor [<x> <z>]` and `/oclaims unanchor [<x> <z>] confirm`, or with the anchor buttons of the OPAC menu. An anchor on a chunk that is not claimed yet makes it a **private** claim first, which needs a free private claim.

With anchors on, for team claims:

- **Making a team claim** needs an anchor of the member who makes it in range. Another member's anchor does not count.
- **Removing an anchor** unclaims the claims of the anchor's owner in its range that no other anchor of theirs covers. That includes their team claims, whatever rank the team requires to unclaim. Claims of other members are never touched.
- **A chunk with an anchor cannot be unclaimed** by anybody, teammates included; the anchor has to be removed first. Other team claims are unclaimed as before.
- **When a member leaves**, their team claims go to the party owner only where the owner has an anchor in range or receives one with the claim (the owner needs room for another anchor in that dimension and an anchor range at least as large as the leaver's, and none of the claims the leaver keeps may depend on that anchor). Every other team claim stays a private claim of the leaver.
- **Automatic unclaims skip chunks with an anchor:** a team over its limit after the grace period, and a removed party whose former team claims do not fit into a member's private limit (the next older claim goes instead). A team can stay above its limit with claims that carry an anchor.

Budgets, limits and the rest of Team Claims are the same as with anchors off.

## The hub (P)

Press **P** (the default; rebindable in Controls under "Open Parties and Claims" as "Open Parties and Claims Hub") or click **Party** in the OPAC menu. P is also vanilla's "Social Interactions" key, so Controls shows a conflict until one of the two is rebound. The hub has the tabs **Party**, **Claims**, **Team** and, only for admins, **Admin**, and opens on the tab you used last.

### Claims tab
A grid of the chunks around you in the claim colours (no terrain, Xaero's World Map shows that). Click a chunk or drag a rectangle to select it, then press **Claim**, **Unclaim**, **Forceload** or **Unforceload**. Every OPAC rule still applies (limits, anchors, team roles) and the server answers as usual.

- Your private claims, your team's, your party's, other players' and server claims have their own colours; forceloaded chunks carry a dot and your own chunk a marker. Hover a chunk for coordinates, owner, claim name and forceload state.
- **Claim as: Private / Team** decides who owns what you claim: only you, or your whole team. It also decides what Xaero's World Map **"Claim Selected"** claims. Without a team only Private is possible.
- The budget lines show your private claims and forceloads and, in a team, the team claims and forceloads.

### Team tab
The team name, the team budget (bonus included) with the over-limit countdown, the roles required to claim, unclaim and forceload, your own switch for the claim welcome messages, **Convert to team** and **Convert to personal** with a radius (with a confirmation), the Private / Team switch and a button to the protection settings of the team. The party owner and admins can change team settings; everybody else sees them read-only, and the server enforces it.

### Party tab
The party screen of 1.3.0, see "Party screen".

### Admin tab
Only for permission level 2 or the permission node `xaero.pac_admin_menu` (through OPAC's permission system, for example LuckPerms). See "Admin menu" below.

## Party screen

The Party tab of the hub (key **P**) or the **Party** button in the OPAC menu. No more typing `/oparties ...`:

- Without a party: create one (the name is optional) and accept or decline the invitations sent to you.
- In a party: see the members and their ranks, invite players (online players are suggested), cancel invitations, kick, change ranks with one click, make someone the owner, rename the party, leave it, or disband it. Anything that needs a confirmation asks for it.
- The screen only offers what your rank allows, and every action runs the normal party command, so the server's checks and messages are the same.
- It shows your **private** claim and forceload numbers, and for a team the **team** numbers, a hint when the team is too small for team claims, and the countdown while the team is over its limit.

## FTB Teams sync (optional)

Install FTB Teams next to this mod and your players only ever create **one** team. Parties and FTB Teams party teams are kept in sync in both directions; nothing of FTB Teams is bundled, and without it the mod works exactly as before. Tested with FTB Teams 2101.1.11.

- **Synced:** creating and disbanding a party, members joining, leaving and being kicked, the owner, the name, ranks (OPAC owner = FTB owner, OPAC admin and moderator = FTB officer, OPAC claimer and member = FTB member, an FTB officer becomes an OPAC moderator; an OPAC admin or claimer is not degraded) and invitations (an invitation in either mod exists in both, accepting it in either joins both).
- **Not synced:** allies, the FTB team colour, description and other properties, and party chat.
- **OPAC wins** on the first sync and wherever the mods disagree: a join, an invitation or a rename that OPAC cannot take is undone in FTB Teams and the player is told; an FTB party that is deleted while the OPAC party still has members FTB Teams does not know yet is created again.
- **Disbanding** in either mod disbands in both, and the team claims then follow the disband rule above.
- What cannot be synced yet (a player who has not logged in since FTB Teams was installed, a full FTB party (`max_party_size`), a change FTB Teams refuses, ...) stays pending and is retried; the affected players are told, the log gets a warning, and `/teamclaims ftbsync status` lists it. FTB Teams has no events for invitations, declines and rank changes, so those are picked up within about a second.
- `/teamclaims ftbsync status` and `/teamclaims ftbsync resync` need permission level 2. The sync is controlled by the server option `ftbTeamsSync` (read at server start).

## Commands

| Command | Who | What it does |
|---|---|---|
| `/teamclaims create <name>` | A player in no party | Creates a party with that team name and sets up the team config and every member's team sub-config. |
| `/oparties create [teamname]` | A player in no party | OPAC's own command. With the team name it does the same as `/teamclaims create`. |
| `/teamclaims info` | Everybody in a team | Overview of your team: name, owner, team claims and team forceloads against the team limits, what one more member adds, running over-limit countdowns, the forceload state (active, inactive, or active during the grace period with the minutes left), the team roles, and every member with their private claims and forceloads against their own limits (red at a limit) and how many team claims they own. |
| `/teamclaims info <player>` | Permission level 2 | The same overview for the team of another player, also offline. |
| `/teamclaims list [page]` | Everybody in a team | The team claims, 10 per page: dimension, chunk, block coordinates, who made it and whether it is forceloaded. The page arrows are clickable. |
| `/teamclaims roles` | Everybody in a team | Shows which rank your team requires to claim, unclaim and forceload. |
| `/teamclaims roles <claim\|unclaim\|forceload> <member\|claimer\|moderator\|admin\|owner>` | Party owner and admins | Sets the rank an action requires. The other online members are told. |
| `/teamclaims convert toteam [radius]` | Everybody in a team | Turns your own claims around you into team claims, keeping their forceload. Needs room in the team pool. |
| `/teamclaims convert topersonal [radius]` | Everybody in a team | Turns your own team claims around you back into personal claims of your selected sub-config. Needs room in your own limits. |
| `/teamclaims territorymessages [on\|off]` | Everybody | Turns the claim welcome messages off or on for yourself, or shows the current state. |
| `/teamclaims bonus <team>` | Permission level 2 or the node `xaero.pac_admin_menu` | Shows the bonus team claims and team forceloads of a team and its limits. |
| `/teamclaims bonus <team> <claims|forceloads> <n>` | Permission level 2 or the node `xaero.pac_admin_menu` | Sets the bonus team claims or team forceloads of a team (the limit is the formula plus the bonus, also below `teamClaimsMinMembers`). |
| `/teamclaims ftbsync status` | Permission level 2 | Whether the FTB Teams sync runs (and if not, why), how many parties are linked and what is waiting to be synced. |
| `/teamclaims ftbsync resync` | Permission level 2 | Runs the full reconciliation with FTB Teams now and reports what it changed. |

`info` and `list` only read, they change nothing.

### Team roles
By default every member may make team claims, unclaim any team claim and toggle any team forceload. The party owner and admins can restrict each of these three actions to a minimum rank: `member` (the default), `claimer`, `moderator`, `admin` (OPAC's party ranks) or `owner`. A player below the rank is told which rank is needed, in chat for the claim commands and in the claim result on the map; nothing is changed. Claiming over a team claim of your own team with a personal sub-config takes it away from the team and so needs the unclaim rank. Personal claims are never restricted, and OPAC's admin mode and server claims ignore the roles. The roles are stored in the team config, and a team from an older version simply uses the defaults.

### Converting claims
`/teamclaims convert toteam [radius]` is for land claimed before the team existed or with the wrong sub-config: every claim in the area that you own and that is not a team claim becomes a team claim, and `topersonal` does the opposite. The area is the square of chunks around the chunk you stand in (`radius` 0, the default, is only that chunk; at most `convertMaxRadius`, 4 by default). Only your own claims are touched, never a teammate's or another player's. Nothing is unclaimed in between and forceloads are kept. Every chunk goes through OPAC's normal claim rules: the team roles apply, `toteam` has to fit into the team pool and `topersonal` into your own claim limit, and the conversion stops at the first claim that does not fit. You get one summary: how many claims were converted, how many were skipped and why.

### Territory messages
OPAC shows a line with the claim's name when you walk into a different claim. With Team Claims, moving from one claim of a team to another claim of the **same team** shows nothing, it is one territory. Entering or leaving a team's land, another team's land, a personal claim or the wilderness works as in OPAC. `/teamclaims territorymessages off` silences **all** claim welcome messages for you, not only the team ones; server owners choose what players who never used it get. This only applies while OPAC's own `claimWelcomeMessages` option is on.

## Admin menu

The **Admin** tab of the hub lets server owners change settings without editing toml files or typing commands.

- **Who sees it:** players with permission level 2 or the permission node `xaero.pac_admin_menu`, checked through the permission system OPAC is configured to use (LuckPerms etc.). The server checks every admin request itself; a modified client gets nothing, and the attempt is logged.
- **OPAC server** and **Team Claims:** every number, on/off, choice and text option of `openpartiesandclaims-server.toml` and `openpartiesandclaims-teamclaims-server.toml`, with a search, the range or allowed values, the comment as a tooltip and a restart badge.
- **Players:** pick an online player or type a name (offline players with a config work too); see their limits and usage and edit their bonus claims, bonus forceloads, bonus anchors and bonus anchor range.
- **Teams:** the teams with their members and usage; edit a team's bonus team claims and bonus team forceloads.
- **List options stay toml-only:** the protection block, entity and item lists and every other list option are not shown.
- **Live and restart-only:** most options apply at once. Options that are only read at server start are written to the toml file only; the running server keeps its value until the next restart and the tab shows the saved value as "after restart".
- **Every change is logged** in the server log with the name of the admin, the option and the new value.

### Team bonus
A server admin can give one team extra team claims and team forceloads on top of the formula. The team limit is the formula plus the bonus, **also below `teamClaimsMinMembers`**, so a team of one player can be given land. Lowering a bonus can put a team over its limit; the normal over-limit deadline then applies, as when a member leaves. The command: `/teamclaims bonus <team>` shows the bonus and the limits, `/teamclaims bonus <team> claims <n>` and `/teamclaims bonus <team> forceloads <n>` set them (permission level 2 or the node `xaero.pac_admin_menu`).

## Feature overview

- Automatic `team_<party name>` sub-config for every party member (long and non-ASCII party names supported)
- Team claims and personal claims side by side, switchable at any time
- Separate budgets: private claims against OPAC's own limits, team claims against a team pool that grows with the member count, for claims **and** forceloads
- Over-limit warning with a configurable grace period before the newest team claims are removed, or an option to never remove them
- Hub screen on the `P` key with the tabs Party, Claims, Team and Admin
- Claims tab: a chunk grid with click-and-drag claiming, unclaiming and forceloading, and a Private / Team switch that also decides what Xaero's World Map "Claim Selected" claims
- Team tab: budget, roles, territory messages, convert and the team's protection settings in one place
- Admin menu for operators: server and Team Claims options with search, player bonuses, team bonuses, every change logged
- Per-team bonus of team claims and team forceloads, `/teamclaims bonus`
- Upstream's optional anchor-based claiming (off by default) works together with team claims
- Optional two-way sync with FTB Teams
- Team roles: choose the rank needed to claim, unclaim and forceload
- `/teamclaims convert` between personal and team claims in an area
- `/teamclaims info` and `/teamclaims list` for a team overview
- Teammates can unclaim and toggle forceload on team claims (by default)
- Party access permanently guaranteed on team claims
- Shared, synchronized claim color, claim name and protection settings, also for late joiners
- Owner/admin-only editing, enforced server-side, read-only UI for members
- Team forceloads active while any member is online, with an optional grace period, random ticks and natural spawning
- Team-aware claim welcome messages and a per-player switch
- Automatic claim transfer to the party owner on leave/kick (also after server downtime)
- Disbanding keeps team claims as private claims only within the owners' private limits
- Corrupt team files are kept as `.corrupt-<timestamp>` instead of being overwritten
- All player-facing messages are translatable (English and German included); players without the language file still get readable text
- Team data is stored per world, so singleplayer worlds never mix
- Fully compatible with Xaero's Minimap and Xaero's World Map claim display and claim controls
- Covered by automated server-side game tests (with and without FTB Teams installed) that run on both loaders

## Installation

1. **Remove the original Open Parties and Claims jar.** This mod *is* Open Parties and Claims (same mod id `openpartiesandclaims`) with Team Claims built in — the two cannot be installed together.
2. Download the file for your mod loader and put it into the `mods` folder of the **server and every client**.
3. Requirements:
   - **Fabric:** Minecraft 1.21.1, Fabric Loader 0.14 or newer, **Fabric API**, **Forge Config API Port**.
   - **NeoForge:** Minecraft 1.21.1, NeoForge 21.1.x. Nothing else is needed.
   - Optional: FTB Teams (with the FTB Library and Architectury it needs itself) for the party sync.
   - Recommended on both: Xaero's Minimap and Xaero's World Map.

Existing OPAC worlds, claims, parties and configs are used as they are.

**Going back to stock OPAC:** stock OPAC only accepts sub-config ids of up to 16 characters, and a team sub-config is called `team_` plus the team name, so it fits only when the name takes at most 11 characters (`team_iron_wolves` is exactly 16; ids that had to be made unique with a suffix are longer). Those team sub-configs keep working in stock OPAC as ordinary sub-configs of the players. Stock OPAC does not load a longer team sub-config (its file is silently left unused), and the claims that used it become ordinary claims of the claimer's main config when stock loads them, with their forceload kept. Team sharing, roles and the team budget are gone in stock OPAC either way, and there all of a player's claims count against that player's own limit. Back up the world first, and keep team names short if you plan to go back.

### Upgrading from 1.3.0
See "Updating from 1.3.0" at the top: update the server and every client together, existing worlds load as they are, the party key is now P.

### Upgrading from 1.2.0
See "Updating from 1.2.0" at the top: update the server and every client together, existing worlds load as they are, make a backup first.

### Upgrading from 1.1.0
See "Updating from 1.1.0" at the top: single-member teams lose their team claims after the grace period unless a second member joins, they convert the claims, or you lower `teamClaimsMinMembers`. Update the server and every client together.

### Upgrading from 1.0.0
- Your world data loads unchanged: claims, parties and team configs from 1.0.0 are used as they are. Read "Updating from 1.1.0" as well, the budget change applies to you too.
- **Update the server and every client together.**
- On the first start the new server config is created. The forceload grace period is off, every member may claim, unclaim and forceload, and the claim welcome messages are team-aware.

## For server owners

- Based on Open Parties and Claims **0.32.7**; every OPAC server option, permission node and command works as documented upstream.
- Team Claims has its own config, `openpartiesandclaims-teamclaims-server.toml`, next to OPAC's own (generated in the `config` folder at server start on both loaders; a copy in `<world>/serverconfig/` overrides it for that world). The private limits still come from your existing `maxPlayerClaims` / `maxPlayerClaimForceloads`, bonus options and permission systems.

| Option | Default | Meaning |
|---|---|---|
| `enabled` | `true` | When `false`, Team Claims does not start: OPAC behaves exactly like the original mod and `/teamclaims` is unavailable. Existing Team Claims data stays untouched. Needs a server restart. |
| `maxTeamNameLength` | `24` (1..100) | Longest team name accepted by `/teamclaims create` and `/oparties create <teamname>`. |
| `forceloadGraceMinutes` | `0` (0..1440) | Minutes the forceloaded chunks of a team stay loaded after its last online member left. `0` releases them right away. |
| `territoryMessagesDefault` | `true` | Whether players who never used `/teamclaims territorymessages` see the claim welcome messages. |
| `convertMaxRadius` | `4` (0..16) | The largest `radius` of `/teamclaims convert`. 16 is a square of 33x33 chunks. |
| `teamClaimsMinMembers` | `2` (1..100) | Members (owner included, invited players not) a team needs before it can have team claims and team forceloads. Fewer members: both limits are 0. |
| `teamClaimsBase` | `500` (0..1000000) | Team claim limit of a team with exactly `teamClaimsMinMembers` members. |
| `teamClaimsPerExtraMember` | `25` (0..1000000) | Team claims added for every member beyond `teamClaimsMinMembers`. |
| `teamForceloadsBase` | `10` (0..1000000) | Team forceload limit of a team with exactly `teamClaimsMinMembers` members. |
| `teamForceloadsPerExtraMember` | `2` (0..1000000) | Team forceloads added for every member beyond `teamClaimsMinMembers`. |
| `overLimitGraceHours` | `168` (0..8760) | Real hours a team may stay above its team claim or team forceload limit before the newest ones are removed. `0` removes the excess at the next check. A running countdown keeps its end time when this is changed. |
| `overLimitEnforcement` | `true` | Whether a team above its limit loses the excess when `overLimitGraceHours` are up. `false`: the team keeps everything, with no countdown and no warnings; the limits still refuse new team claims and team forceloads. A running countdown is dropped at the next check; switching back to `true` starts a fresh one for a team that is still above its limit. |
| `ftbTeamsSync` | `true` | Whether parties are kept in sync with FTB Teams when that mod is installed. Does nothing without FTB Teams. Needs a server restart. |

- Changes to the Team Claims config apply right away, except `enabled` and `ftbTeamsSync` (read at server start) and `forceloadGraceMinutes` (from the next logout). Teams are re-checked against their limits on every membership change and at least once a minute.
- OPAC's own, separate `partyOwnedClaims` option can stay off. If you enable it as well, native party claims count as private claims of the party owner (against the owner's own limit, which `claimBonusPerPartyMember` and `claimBonusForPartyOwner` raise) and never against the team's pool; the server log explains the details at startup.
- Team data lives in `<world>/data/opacteamclaims/` and in the world's saved data.
- Most options of both configs can be changed in the Admin tab of the hub (key P) by operators and players with the node `xaero.pac_admin_menu`; see "Admin menu". Options that need a restart are written to the toml and apply after the next start. List options stay in the toml.
- Mods built against the OPAC API (parties, claims, config API, addon events) keep working. `getClaimCount()` of a player still includes the team claims the player technically owns.

## FAQ

**Do clients need it?** Yes. Like OPAC itself, it has to be installed on both sides for the UI, map integration and config screens.

**Is FTB Teams required?** No. It is an optional dependency: install it and the sync starts, leave it out and nothing changes. The mod is tested both ways.

**Can I still have private claims?** Yes. Only claims made while the team sub-config is selected are team claims. Private claims have their own limit and are never restricted by team roles; team claims do not use it up.

**Who technically owns a team claim?** The member who claimed it (so OPAC's data stays fully compatible), but the whole party can manage it and it counts for the team's pool.

**What happens to team land when I leave the party?** Your team claims move to the party owner. Your personal claims stay yours.

**What happens to team land when the party is disbanded?** Each team claim becomes a private claim of the member who made it, as far as that member's own claim limit allows; the rest is unclaimed.

**Can I restrict who may claim or forceload for the team?** Yes, the party owner or an admin sets a minimum rank per action with `/teamclaims roles`.

**Does it work with other party systems (FTB Teams, Argonauts)?** Team Claims is built on OPAC's default party system. FTB Teams parties are kept in sync with it (optional, see above); Argonauts is not synced.

**What does World Map's "Claim Selected" claim?** What the "Claim as" button of the Claims tab says: private claims, or team claims. It is the same switch as the selected sub-claim.

**Which versions and loaders are supported?** Minecraft 1.21.1 on Fabric and on NeoForge 21.1.x. Forge and other Minecraft versions are not supported.

## Credits & license

Open Parties and Claims is created by **Xaero96** and contributors — all credit for the claim, party and protection systems goes to them. Please support the original project: https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims

Team Claims fork by **Navrelis**. Licensed under the **GNU LGPL v3.0** (LGPL-3.0-only), the same license as the original. Source code: https://github.com/navrelis/OPAC_PartyClaims

Please report issues with this build to the fork's issue tracker (https://github.com/navrelis/OPAC_PartyClaims/issues), not to Xaero.
