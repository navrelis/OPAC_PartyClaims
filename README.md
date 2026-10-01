# Open Parties and Claims - Team Claims fork

A fork of [Open Parties and Claims](https://github.com/thexaero/open-parties-and-claims)
v0.31.6 by Xaero96, licensed under LGPL-3.0-only, adding a "Team Claims" layer on top
of the original party/claims system.

Supported: Minecraft 1.21.1 on **Fabric** (Fabric Loader 0.14 or newer, Fabric API and Forge Config API Port) and on
**NeoForge** (21.1.x). The mod id is still `openpartiesandclaims`, so it replaces the original mod; it cannot be
installed next to it. Install it on the server and on every client.

## Team Claims behaviour notes

**Forceloads.** A team's forceloaded chunks stay loaded while any member of the team is online. After the last member
left they are released right away, or after `forceloadGraceMinutes` (see "Server config"). Team forceloads behave like
OPAC's own: forceloaded chunks get random ticks and natural spawning, and a dimension holding them keeps ticking
without players.

**Membership changes.** Joining, leaving, kicking, ownership changes and removing a party are handled by events and
take effect at the end of the same server tick, so even a member who joins and leaves again within a moment is not
missed.

**Going back to stock OPAC.** Team sub-configs are called `team_<name>`. Stock OPAC 0.31.6 only accepts sub-config ids
of up to 16 characters (`PlayerConfig.isValidSubId`; this fork lifts that limit for `team_*` ids, up to 100), so a team
sub-config fits only when the name takes at most 11 characters (an id made unique with a suffix is longer). Those keep working in stock as ordinary sub-configs.
Stock does not load a longer one (`PlayerConfig.createSubConfig` refuses it, its file is silently left unused), and the
claims that used it become ordinary claims of the claimer's main config when stock loads them
(`PlayerClaimInfoManagerIO.onObjectLoad`), with their forceload kept. Team sharing, roles and the shared budget are gone
in stock either way. Back up the world before going back.

**Team config files.** The team configs are saved off the server thread. A team config file that cannot be read is
not deleted: it is renamed to `<party>.json.corrupt-<timestamp>` in the same folder and the team gets a fresh config.

**Leaving a party.** When a member leaves or is kicked from a party that still exists, every
team claim technically owned by them is re-assigned to the current party owner (keeping the
forceload flag, in every dimension), so the team keeps its territory. This is budget-neutral:
the team's total is unchanged and every member's count stays `own personal claims + team
total`. It also works while the leaver and/or the owner are offline, and members that left
while the server was down are caught up at the next start. Destroying a party is unchanged:
the claims stay personal claims of whoever made them.

**`partyOwnedClaims` (upstream's native party claims).** Team Claims works with the server
option enabled and the displayed numbers stay correct, but both features share **one pool per
player**, which is worth knowing before you turn it on:

* Native party claims are owned by the *party owner's* UUID, so OPAC counts and limits them
  against the owner in both `player` and `party` claiming mode — those two modes show the same
  number for the owner. Team Claims adds the other members' team claims on top of that number,
  for the owner and for every member, which is exactly what it enforces.
* Because the owner is also a team member, native party claims made by *anyone* reduce the
  claim budget of the *whole* team.
* `claimBonusPerPartyMember` / `claimBonusForPartyOwner` only raise the *owner's* limit, while
  the team total is capped by every member's own limit — so that bonus does not enlarge the
  team's budget.
* A native party claim made with the owner's `team_*` sub-config is indistinguishable from an
  owner team claim and is treated as one (tracked, shared budget, any member may unclaim it).

The server logs one WARN line stating this at start-up while the option is enabled.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/teamclaims create <name>` | A player in no party | Creates a party with that team name, sets up the team config and every member's team sub-config (`team_<name>`; a name without any letter or digit, or one already taken by another team, gets a short suffix from the party id, so the id is always unique). The name is validated first (not empty, at most `maxTeamNameLength` characters), and nothing is created when it is rejected. Needs the server option `partiesEnabled`. |
| `/oparties create [teamname]` | A player in no party | OPAC's own party create command. With the optional team name argument it does the same as `/teamclaims create`, through the same validated code path. |
| `/teamclaims info` | Everybody in a team | Overview of your team: the team name and owner, the team claims and how many of them are forceloaded, the forceload state (active, inactive because no member is online, or active during the grace period with the minutes left), one line per member (owner first, then by rank, then alphabetical) and the budget. A member line shows the claims and forceloads as `personal + team total = count / limit`, in red when the member is at or over a limit. The budget is how many more team claims and team forceloads the team can make right now, which is what the most limited member has left, and who that is. |
| `/teamclaims info <player>` | Permission level 2 | The same overview for the team of another player (also offline). |
| `/teamclaims list [page]` | Everybody in a team | The team claims, 10 per page, sorted by dimension, then x, then z: dimension, chunk, block coordinates of the chunk's centre, the member who made it and whether it is forceloaded. The `[<]` and `[>]` in the footer are clickable. A team without team claims gets a hint on how to make one. |
| `/teamclaims territorymessages [on\|off]` | Everybody | Turns the claim welcome messages off or on for yourself, or shows the current state. See "Territory messages" below. |
| `/teamclaims roles` | Everybody in a team | Shows which rank your team requires to make, unclaim and forceload team claims. See "Team roles" below. |
| `/teamclaims roles <claim\|unclaim\|forceload> <member\|claimer\|moderator\|admin\|owner>` | The party owner and admins | Sets the rank one of those actions requires. The other online members are told about the change. |
| `/teamclaims convert toteam [radius]` | Everybody in a team | Turns your own claims in the square of chunks around the chunk you stand in (`radius` 0, the default, is only that chunk; at most `convertMaxRadius`) into team claims, keeping their forceload. See "Converting claims" below. |
| `/teamclaims convert topersonal [radius]` | Everybody in a team | Turns your own team claims in that area back into personal claims of your selected sub-claim (the main config if that is the team sub-config), keeping their forceload. |

`info` and `list` only read, they change nothing. The `info` overview also has a line with the team roles. All of
`/teamclaims` is unavailable while `enabled` is `false` in the Team Claims server config.

## Team roles

By default every member of a team may make team claims, unclaim any team claim of the team (their own or a
teammate's) and turn the forceload of any of them on or off. The party owner and admins can restrict each of those three
actions to a minimum rank with `/teamclaims roles <action> <rank>`:

| Action | What it covers |
| --- | --- |
| `claim` | Making a team claim, i.e. claiming with the team sub-config, also over an existing claim. |
| `unclaim` | Unclaiming a team claim of the team, your own or a teammate's. Claiming over a team claim of your own team with a personal sub-config takes it away from the team too, so it needs this rank as well. |
| `forceload` | Turning the forceload of a team claim of the team on or off, your own or a teammate's. Turning a forceloaded claim into a team claim (`/teamclaims convert toteam`) adds a team forceload, so it needs this rank as well as the `claim` rank. |

The ranks, from the lowest: `member`, `claimer`, `moderator`, `admin` (OPAC's party ranks) and `owner` (only the party
owner). The default for all three actions is `member`, which is the behaviour without roles. A player below the rank
gets "... needs the team rank X or higher" as the reason, in chat for the claim commands and in the claim result for the
map's claim UI; nothing is changed. Personal claims are never restricted. OPAC's admin mode and server claims (forced
actions) and Team Claims' own claim transfers ignore the roles.

The roles are stored in the team config, `<world>/data/opacteamclaims/teams/<party>.json`, as a `roles` object; a team
config without it (or with an unknown value) uses the default.

## Converting claims

`/teamclaims convert toteam [radius]` is for land claimed before the team existed or with the wrong sub-config:
every claim in the area that you own and that is not a team claim becomes a team claim (your team sub-config), and
`/teamclaims convert topersonal [radius]` does the opposite for your team claims there. Nothing is unclaimed in
between, and a forceloaded claim stays forceloaded (it becomes a team forceload, or a personal one again). The area is
the square of chunks around the chunk you stand in, `radius` chunks in each direction (0 = only that chunk), at most
`convertMaxRadius` from the server config.

Only your own claims are touched, never a teammate's, another player's or a server claim. While impersonating another
player with OPAC's claims commands, their claims are converted. Every chunk goes through OPAC's normal (not forced) claim
path, so the same rules apply as for claiming it by hand: `toteam` needs the team's `claim` rank (and the `forceload`
rank for a forceloaded claim) and has to fit into the shared team budget, including the forceload budget for forceloaded
claims, and `topersonal` needs the `unclaim` rank.
OPAC's admin mode does not bypass any of that here. The claims nearest to you are converted first, and the conversion
stops at the first claim that doesn't fit into the budget (the budget message names the member at the limit). At the
end you get one summary: how many claims were converted (and how many of them are forceloaded), how many were skipped
and why (not yours, already in that state, not allowed for your team rank, over the team budget, another reason).

## Server config

Team Claims has its own server config, `openpartiesandclaims-teamclaims-server.toml`, next to OPAC's
`openpartiesandclaims-server.toml`. It is generated in the `config/` folder when the server starts, on both
Fabric and NeoForge; a copy in `<world>/serverconfig/` overrides it for that world.

| Option | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | When `false`, Team Claims does not start: OPAC behaves exactly like the original mod and `/teamclaims` is unavailable. Existing Team Claims data stays untouched on disk. Read once at server start, so a change needs a restart. |
| `maxTeamNameLength` | `24` (1..100) | Maximum length of a team name (after removing formatting codes) given to `/teamclaims create` or to the team name argument of the party create command. Changes apply right away. |
| `forceloadGraceMinutes` | `0` (0..1440) | How many minutes the forceloaded chunks of a team stay loaded after its last online member left. A member coming back within that time cancels the release. `0` releases them right away. Changes apply from the next logout. |
| `territoryMessagesDefault` | `true` | Whether players who never used `/teamclaims territorymessages` see the claim welcome messages. See "Territory messages" below. Changes apply right away. |
| `convertMaxRadius` | `4` (0..16) | The largest `radius` of `/teamclaims convert`. 16 is a square of 33x33 chunks. Changes apply right away. |

## Territory messages

OPAC shows an action bar line with the claim's name and colour whenever you walk into a different claim (server
option `claimWelcomeMessages`). Team Claims makes that team-aware: every member's team claims share one name and
colour, so moving from one team claim to another claim of the **same team** in the same dimension shows nothing, it
is one territory. Entering or leaving a team's land, entering another team's land, a personal claim or the wilderness
behaves exactly like stock OPAC.

`/teamclaims territorymessages <on|off>` lets each player turn the claim welcome messages off (or back on) for
themselves, and `/teamclaims territorymessages` on its own shows the current state. All of this only matters while
OPAC's own `claimWelcomeMessages` server option is enabled. The setting silences **all** claim
welcome messages for that player, not only the team ones. Any player may use it, in a party or not. Only explicit
choices are stored (in the Team Claims data of the world); everybody else follows the server config option
`territoryMessagesDefault`.

## Building

```
gradlew.bat :Fabric:build :NeoForge:build
```

The Fabric jar is written to `Fabric/build/libs/` and the NeoForge jar to `NeoForge/build/libs/`
(ignore the `-sources` jars next to them). The version is set in `gradle.properties`. Team Claims works the same on both loaders: the logic
lives in `Common`, and each loader only has a thin adapter that forwards its events to it
(`TeamClaimsFabric` on Fabric, `TeamClaimsNeoForge` on NeoForge).

Released builds live in `ExportedJars/<version>/`, together with the CurseForge
listing text for that release.

## Development / testing

Three Gradle runs are set up for manual multiplayer testing of party/claims features:
`:Fabric:runClient` (Player1, `run-client1/`), `:Fabric:runClient2` (Player2,
`run-client2/`) and `:Fabric:runServer` (`run-server/`). Use `start-all.bat` /
`stop-all.bat` in the repo root to launch/kill all three at once.

Before the server actually boots you must, one time:
1. Accept the Minecraft EULA yourself by setting `eula=true` in `run-server/eula.txt`
   (generated after the first server start).
2. Set `online-mode=false` in `run-server/server.properties` so the two offline dev
   accounts (Player1/Player2) can join.

`start-all.bat` does not create or edit either file for you; it only prints a reminder.

`gradlew.bat :Fabric:runBootTest --console=plain` runs a headless smoke test (Fabric API's
gametest server, `-Dfabric-api.gametest`): it boots a real dedicated-server environment with
the mod, needs no EULA at all, runs one real test asserting Team Claims actually wired itself
in (bridge handler, managers, `/teamclaims`/`/oparties` commands, team config dir), then exits
on its own. That test lives in its own `gametest` source set/mod (never shipped in the release
jar) and includes a test-only mixin working around vanilla's `GameTestServer` having no player
profile cache. Check `run-boottest/logs/latest.log` for the result.

The same run also executes the Team Claims game logic tests: 44 automated `@GameTest` tests in total (smoke, team
sub-config creation and admin-only editing, claim/forceload budget sharing, teammate unclaim/forceload, event-driven
claim transfer on leave (party hooks queue membership events that are processed at the end of the tick), long and
non-ASCII team names, hardening (quick join and leave, claim overhead after many changes, name validation, late joiners, corrupt team files, forceload tickets), the server config and the
forceload grace period, territory messages, `info`/`list`, team roles and `convert`). They run against offline UUID
players, independently of each other, and clean up after themselves so reruns stay green.

The same 44 tests run on NeoForge with `gradlew.bat :NeoForge:runTeamClaimsGameTest --console=plain`
(NeoForge's gametest server, which also needs no EULA). The test bodies are shared from
`Common/src/gametest`; NeoForge loads them through its own test-only `gametest` source set/mod
(`NeoForge/src/gametest`, never shipped in the release jar). Each run starts with a fresh world,
and the task fails unless the log contains `All N required tests passed`. Check
`NeoForge/runs/teamClaimsGameTest/logs/latest.log` for the result.

## Changelog

### 1.1.0

* **NeoForge 1.21.1 support** (NeoForge 21.1.x), next to Fabric. The Team Claims logic is shared between both loaders;
  each loader only has a thin adapter. One jar per loader.
* **Team roles**: `/teamclaims roles` shows and (owner and admins) sets the rank required to make team claims, to
  unclaim them and to forceload them.
* **`/teamclaims convert toteam|topersonal [radius]`** turns your own claims in an area into team claims and back,
  keeping their forceload.
* **`/teamclaims info [player]`** (team overview with members, budget and forceload state) and
  **`/teamclaims list [page]`** (the team claims).
* **Territory messages**: moving between claims of the same team shows no claim welcome message;
  `/teamclaims territorymessages [on|off]` turns the claim welcome messages off or on per player.
* **New server config** `openpartiesandclaims-teamclaims-server.toml` with `enabled`, `maxTeamNameLength`,
  `forceloadGraceMinutes`, `territoryMessagesDefault` and `convertMaxRadius`.
* **Forceload grace period**: a team's forceloads can stay loaded for a while after the last member went offline.
* **Membership changes are event-driven** and apply in the same server tick (they were polled every 3 seconds, so a
  quick join and leave could be missed).
* Team forceloads behave like OPAC forceloads (random ticks and natural spawning); late joiners get all admin-set
  team options; sub-config ids of non-ASCII team names are unique; `/teamclaims create` and
  `/oparties create <teamname>` share one validated create path.
* Faster and more robust: indexed claim tracking, batched claim limit syncs, team config files saved off the main
  thread, and corrupt team config files are preserved instead of overwritten.
* 44 automated gametests, run on both loaders.
* The issue tracker and source links of the mod metadata point to this fork.

### 1.0.0

* First release, Fabric 1.21.1 only: Team Claims on top of Open Parties and Claims 0.31.6.
* Team sub-config `team_<name>` for every party member, shared claim color, name and protection settings (owner and
  admins edit them), shared claim and forceload budget, every member may unclaim and forceload team claims, team
  forceloads while any member is online, and automatic claim transfer to the party owner when a member leaves.
* `/teamclaims create <name>` and `/oparties create <teamname>`.
