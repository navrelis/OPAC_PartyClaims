# OPAC: Team Claims — CurseForge listing (v1.1.0)

Project name suggestion: **Open Parties and Claims: Team Claims**
Game version: **Minecraft 1.21.1** · Mod loaders: **Fabric** and **NeoForge** (one file per loader) · Environment: **client + server (required on both)** · License: **LGPL-3.0-only**

Files for this release (upload each with the matching mod loader selected):

| File | Mod loader | Needs |
|---|---|---|
| `opac-team-claims-fabric-1.21.1-v1.1.0.jar` | **Fabric** | Fabric Loader 0.14 or newer, **Fabric API**, **Forge Config API Port** (>= 21.0.5), Java 21 |
| `opac-team-claims-neoforge-1.21.1-v1.1.0.jar` | **NeoForge** | NeoForge 21.1.x, Java 21 (nothing else) |

## Summary

Paste this into the CurseForge "Summary" field (max 256 characters, this one has 243):

```
Open Parties and Claims with real team bases: claim land together as a party, share one fair claim budget, set team roles, convert claims, forceload for the whole team and keep colors, names and permissions in sync. Fabric and NeoForge 1.21.1.
```

## Categories

| Slot | Category | Why |
|---|---|---|
| **Main** | **Server Utility** | Chunk claiming, protection and party management are server administration / multiplayer infrastructure first. |
| Additional | **Utility & QoL** | Removes the daily friction of running a shared base: no more "who owns this chunk?", no manual permission juggling. |
| Additional | **Map and Information** | Team claims show up with one shared color and name on Xaero's Minimap and World Map, exactly like normal OPAC claims. |
| Additional | **Adventure and RPG** | Parties, shared territory and team identity are the backbone of faction-, town- and guild-style servers. |
| Additional | **API and Library** | Ships the complete, unchanged Open Parties and Claims API, so addons and integrations written for OPAC keep working. |

If CurseForge limits the number of additional categories, keep them in the order listed above.

Suggested release type: **Beta** for both files. Fabric 1.1.0 is feature complete and covered by 44 automated game tests, but the NeoForge file is the first public NeoForge build, so both stay Beta until the NeoForge port has had some time on real servers.

## Changelog for this file

Paste this into the CurseForge "Changelog" field of each file:

```
Open Parties and Claims 0.31.6 for Minecraft 1.21.1, with Team Claims 1.1.0

New since 1.0.0:
- NeoForge 1.21.1 support (NeoForge 21.1.x). One jar per loader, the Team Claims logic is shared.
- Team roles: /teamclaims roles shows and (party owner and admins) sets the rank needed to make team claims, to unclaim them and to forceload them.
- /teamclaims convert toteam|topersonal [radius]: turn your own claims in an area into team claims and back, forceloads included.
- /teamclaims info [player] and /teamclaims list [page]: team overview (members, budget, forceload state) and the list of team claims.
- Territory messages: moving between claims of the same team shows no claim welcome message. /teamclaims territorymessages [on|off] switches the welcome messages per player.
- New server config openpartiesandclaims-teamclaims-server.toml: enabled, maxTeamNameLength, forceloadGraceMinutes, territoryMessagesDefault, convertMaxRadius.
- Forceload grace period: team forceloads can stay loaded for a while after the last member went offline.
- Membership changes are event-driven and apply in the same tick (they were polled every 3 seconds, so a quick join and leave could be missed).
- Team forceloads behave like OPAC forceloads (random ticks and natural spawning).
- Late joiners get all admin-set team options. Team names with non-ASCII characters get unique sub-config ids.
- /teamclaims create and /oparties create <teamname> share one validated create path.
- Faster and more robust: indexed claim tracking, batched claim limit syncs, team config files are saved off the main thread, corrupt team files are preserved instead of overwritten.
- 44 automated game tests run on both loaders.
- The issue tracker and source links of the mod point to the fork.

Upgrading from 1.0.0 (Fabric): world data, claims, parties and team configs load unchanged. Update the server AND every client together: a 1.0.0 client with a 1.1.0 server is not supported.
```

## Description

Everything below this line is the project description.

---

# Open Parties and Claims: Team Claims

**Your party built the base together. Now your party can own it together.**

This is a drop-in replacement build of **[Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims)** (OPAC) by Xaero96 for **Fabric and NeoForge 1.21.1**. It contains everything OPAC does — chunk claims, claim protection, forceloading, parties, allies, the full config UI, Xaero's Minimap / World Map integration, the complete API — and adds one thing OPAC players keep asking for: **Team Claims**.

## The problem it solves

In stock OPAC every claim belongs to exactly one player. For a group that builds together this gets awkward fast:

- The base is claimed by whoever got there first. If that player is offline, nobody else can expand, unclaim or fix the borders.
- Forceloaded farms stop working when "the wrong person" logs off.
- Everyone has to configure their own claim color, name and permissions, so one base looks like a patchwork on the map.
- Giving the whole party access means fiddling with per-player protection settings, and one wrong toggle locks your friends out of their own storage room.

Team Claims makes the party the unit that matters, while keeping every personal claim exactly as it was.

## How it works

### 1. Create a party — get a team
Create a party with a name in a single command:

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

### 4. One fair, shared budget
Team claims are not free extra land. Every team claim counts against the claim limit of **every** party member, and a new team claim (or team forceload) is only accepted if **all** members still have room for it. In practice:

- Your claim counter shows *your personal claims + all team claims of your party* — in chat, in the claim UI and on the map.
- A party cannot out-claim solo players by stacking members: the member with the smallest limit caps the team.
- Server owners keep full control with the normal OPAC limits, bonus claims and permission-based limits.
- When a teammate is at their limit you get a clear message naming who is blocking the claim.

### 5. One look, one set of rules
A team claim should look and behave the same no matter who claimed it:

- The team gets a **shared claim color** (generated from the party, changeable) and a **shared claim name**.
- All protection options of the team sub-config are **synchronized to every member automatically**, including members who join later.
- Only the **party owner and party admins** can change team settings. For everyone else the options are shown read-only, and the server enforces it.
- The team sub-config **cannot be deleted** by hand — it disappears by itself when you leave the party.

### 6. Forceloading that follows the team
Forceloaded team chunks stay loaded **while any party member is online**, not just the player who flipped the switch. They behave like OPAC's own forceloads: random ticks and natural spawning work, and a dimension holding them keeps ticking without players. Your shared farms keep running when the claimer goes to bed and someone else is still playing.

When the last member logs off, the tickets are released — right away by default, or after a **grace period** the server owner can set (`forceloadGraceMinutes`). A member who comes back within that time cancels the release, so a short disconnect does not stop your farms.

### 7. Members come and go, the base stays
When a member leaves or is kicked, their team claims are **transferred to the party owner automatically** — including the forceload state — instead of turning into private land in the middle of your base. Both sides get a chat message. This also works if it happened while the server was offline. If the whole party is disbanded, the team data is cleaned up.

Membership changes are handled as events and take effect in the same server tick, so even a quick join and leave is not missed.

## Commands

| Command | Who | What it does |
|---|---|---|
| `/teamclaims create <name>` | A player in no party | Creates a party with that team name and sets up the team config and every member's team sub-config. |
| `/oparties create [teamname]` | A player in no party | OPAC's own command. With the team name it does the same as `/teamclaims create`. |
| `/teamclaims info` | Everybody in a team | Overview of your team: name, owner, team claims and forceloads, the forceload state (active, inactive, or active during the grace period with the minutes left), every member with their claims and forceloads as `personal + team = count / limit` (red at a limit), the team roles, and how many more team claims and forceloads the team can make right now and who limits that. |
| `/teamclaims info <player>` | Permission level 2 | The same overview for the team of another player, also offline. |
| `/teamclaims list [page]` | Everybody in a team | The team claims, 10 per page: dimension, chunk, block coordinates, who made it and whether it is forceloaded. The page arrows are clickable. |
| `/teamclaims roles` | Everybody in a team | Shows which rank your team requires to claim, unclaim and forceload. |
| `/teamclaims roles <claim\|unclaim\|forceload> <member\|claimer\|moderator\|admin\|owner>` | Party owner and admins | Sets the rank an action requires. The other online members are told. |
| `/teamclaims convert toteam [radius]` | Everybody in a team | Turns your own claims around you into team claims, keeping their forceload. |
| `/teamclaims convert topersonal [radius]` | Everybody in a team | Turns your own team claims around you back into personal claims of your selected sub-config. |
| `/teamclaims territorymessages [on\|off]` | Everybody | Turns the claim welcome messages off or on for yourself, or shows the current state. |

`info` and `list` only read, they change nothing.

### Team roles
By default every member may make team claims, unclaim any team claim and toggle any team forceload. The party owner and admins can restrict each of these three actions to a minimum rank: `member` (the default), `claimer`, `moderator`, `admin` (OPAC's party ranks) or `owner`. A player below the rank is told which rank is needed, in chat for the claim commands and in the claim result on the map; nothing is changed. Claiming over a team claim of your own team with a personal sub-config takes it away from the team and so needs the unclaim rank. Personal claims are never restricted, and OPAC's admin mode and server claims ignore the roles. The roles are stored in the team config, and a team from 1.0.0 simply uses the defaults.

### Converting claims
`/teamclaims convert toteam [radius]` is for land claimed before the team existed or with the wrong sub-config: every claim in the area that you own and that is not a team claim becomes a team claim, and `topersonal` does the opposite. The area is the square of chunks around the chunk you stand in (`radius` 0, the default, is only that chunk; at most `convertMaxRadius`, 4 by default). Only your own claims are touched, never a teammate's or another player's. Nothing is unclaimed in between and forceloads are kept. Every chunk goes through OPAC's normal claim rules: the team roles apply and `toteam` has to fit into the shared team budget, and the conversion stops at the first claim that does not fit. You get one summary: how many claims were converted, how many were skipped and why.

### Territory messages
OPAC shows a line with the claim's name when you walk into a different claim. With Team Claims, moving from one claim of a team to another claim of the **same team** shows nothing, it is one territory. Entering or leaving a team's land, another team's land, a personal claim or the wilderness works as in OPAC. `/teamclaims territorymessages off` silences **all** claim welcome messages for you, not only the team ones; server owners choose what players who never used it get. This only applies while OPAC's own `claimWelcomeMessages` option is on.

## Feature overview

- Automatic `team_<party name>` sub-config for every party member (long and non-ASCII party names supported)
- Team claims and personal claims side by side, switchable at any time
- Shared claim budget with per-member limit checks for claims **and** forceloads
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
- Corrupt team files are kept as `.corrupt-<timestamp>` instead of being overwritten
- All player-facing messages are translatable; players without the language file still get readable text
- Team data is stored per world, so singleplayer worlds never mix
- Fully compatible with Xaero's Minimap and Xaero's World Map claim display and claim controls
- Covered by 44 automated server-side game tests that run on both loaders

## Installation

1. **Remove the original Open Parties and Claims jar.** This mod *is* Open Parties and Claims (same mod id `openpartiesandclaims`) with Team Claims built in — the two cannot be installed together.
2. Download the file for your mod loader and put it into the `mods` folder of the **server and every client**.
3. Requirements:
   - **Fabric:** Minecraft 1.21.1, Fabric Loader 0.14 or newer, **Fabric API**, **Forge Config API Port**.
   - **NeoForge:** Minecraft 1.21.1, NeoForge 21.1.x. Nothing else is needed.
   - Recommended on both: Xaero's Minimap and Xaero's World Map.

Existing OPAC worlds, claims, parties and configs are used as they are.

**Going back to stock OPAC:** stock OPAC only accepts sub-config ids of up to 16 characters, and a team sub-config is called `team_` plus the team name, so it fits only when the name takes at most 11 characters (`team_iron_wolves` is exactly 16; ids that had to be made unique with a suffix are longer). Those team sub-configs keep working in stock OPAC as ordinary sub-configs of the players. Stock OPAC does not load a longer team sub-config (its file is silently left unused), and the claims that used it become ordinary claims of the claimer's main config when stock loads them, with their forceload kept. Team sharing, roles and the shared budget are gone in stock OPAC either way. Back up the world first, and keep team names short if you plan to go back.

### Upgrading from 1.0.0
- Your world data loads unchanged: claims, parties and team configs from 1.0.0 are used as they are.
- **Update the server and every client together.** A 1.0.0 client with a 1.1.0 server is not a supported mix.
- On the first start the new server config is created. Its defaults keep the 1.0.0 behaviour (no grace period, every member may claim, unclaim and forceload), except that the claim welcome messages are now team-aware.

## For server owners

- Based on Open Parties and Claims **0.31.6**; every OPAC server option, permission node and command works as documented upstream.
- Team Claims has its own config, `openpartiesandclaims-teamclaims-server.toml`, next to OPAC's own (generated in the `config` folder at server start on both loaders; a copy in `<world>/serverconfig/` overrides it for that world). Limits still come from your existing `maxPlayerClaims` / `maxPlayerClaimForceloads`, bonus options and permission systems.

| Option | Default | Meaning |
|---|---|---|
| `enabled` | `true` | When `false`, Team Claims does not start: OPAC behaves exactly like the original mod and `/teamclaims` is unavailable. Existing Team Claims data stays untouched. Needs a server restart. |
| `maxTeamNameLength` | `24` (1..100) | Longest team name accepted by `/teamclaims create` and `/oparties create <teamname>`. |
| `forceloadGraceMinutes` | `0` (0..1440) | Minutes the forceloaded chunks of a team stay loaded after its last online member left. `0` releases them right away. |
| `territoryMessagesDefault` | `true` | Whether players who never used `/teamclaims territorymessages` see the claim welcome messages. |
| `convertMaxRadius` | `4` (0..16) | The largest `radius` of `/teamclaims convert`. 16 is a square of 33x33 chunks. |

- OPAC's own, separate `partyOwnedClaims` option can stay off. If you enable it as well, both systems share one claim pool per party owner; the server log explains the details at startup.
- Team data lives in `<world>/data/opacteamclaims/` and in the world's saved data.
- Mods built against the OPAC API (parties, claims, config API, addon events) keep working.

## FAQ

**Do clients need it?** Yes. Like OPAC itself, it has to be installed on both sides for the UI, map integration and config screens.

**Can I still have private claims?** Yes. Only claims made while the team sub-config is selected are team claims, and personal claims are never restricted by team roles.

**Who technically owns a team claim?** The member who claimed it (so OPAC's data stays fully compatible), but the whole party can manage it and it counts for everyone's budget.

**What happens to team land when I leave the party?** Your team claims move to the party owner. Your personal claims stay yours.

**Can I restrict who may claim or forceload for the team?** Yes, the party owner or an admin sets a minimum rank per action with `/teamclaims roles`.

**Does it work with other party systems (FTB Teams, Argonauts)?** Team Claims is built on OPAC's default party system.

**Which versions and loaders are supported?** Minecraft 1.21.1 on Fabric and on NeoForge 21.1.x. Forge and other Minecraft versions are not supported.

## Credits & license

Open Parties and Claims is created by **Xaero96** and contributors — all credit for the claim, party and protection systems goes to them. Please support the original project: https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims

Team Claims fork by **Navrelis**. Licensed under the **GNU LGPL v3.0** (LGPL-3.0-only), the same license as the original. Source code: https://github.com/navrelis/OPAC_PartyClaims

Please report issues with this build to the fork's issue tracker (https://github.com/navrelis/OPAC_PartyClaims/issues), not to Xaero.
