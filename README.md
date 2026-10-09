# Open Parties and Claims - Team Claims fork

A fork of [Open Parties and Claims](https://github.com/thexaero/open-parties-and-claims)
v0.31.6 by Xaero96, licensed under LGPL-3.0-only, adding a "Team Claims" layer on top
of the original party/claims system.

Supported: Minecraft 1.21.1 on **Fabric** (Fabric Loader 0.14 or newer, Fabric API and Forge Config API Port) and on
**NeoForge** (21.1.x). The mod id is still `openpartiesandclaims`, so it replaces the original mod; it cannot be
installed next to it. Install it on the server and on every client. FTB Teams is optional (see "FTB Teams sync").

## Updating from 1.1.0

Version 1.2.0 splits the claim budget (see "Claim budgets"). World data, claims, parties and team configs load
unchanged, but one thing needs your attention before you update a server:

* **A team with a single member has a team claim limit of 0** with the default `teamClaimsMinMembers = 2`. When the
  1.2.0 server starts, every such team that has team claims is over its limit: its members get the warning (at their
  next login if they are offline) and, after the grace period (`overLimitGraceHours`, 7 days by default, counted in
  real time, also while the server is off), the newest team claims are unclaimed, until none are left. Same for team
  forceloads. A team of two or more members is checked the same way against the new limits (the 1.1.0 budget was
  measured differently), so it is only affected if it has more team claims or team forceloads than they allow.
* The ways out, before the deadline: a second member joins the party, the members turn the team claims into private
  claims with `/teamclaims convert topersonal` (they have to fit into the players' own claim limit), they unclaim what
  they do not need, or you lower `teamClaimsMinMembers` (for example to `1`) in the Team Claims server config.
* **Update the server and every client together.** The party screen and the budget numbers use new packets, so a 1.1.0
  client with a 1.2.0 server (or the other way round) is not a supported mix.
* On the first start the new options of the Team Claims server config are added with their defaults (see "Server
  config"). The FTB Teams sync does nothing unless FTB Teams is installed.

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
(`PlayerClaimInfoManagerIO.onObjectLoad`), with their forceload kept. Team sharing, roles and the team budget are gone
in stock either way, and in stock all of a player's claims count against that player's own limit. Back up the world
before going back.

**Team config files.** The team configs are saved off the server thread. A team config file that cannot be read is
not deleted: it is renamed to `<party>.json.corrupt-<timestamp>` in the same folder and the team gets a fresh config.

**Leaving a party.** When a member leaves or is kicked from a party that still exists, every team claim technically
owned by them (the claims they made) is re-assigned to the current party owner (keeping the forceload flag, in every
dimension), so the team keeps its territory. The claims stay team claims, so the team's totals and every player's
private claims are unchanged. What changes is the team limit, which shrinks with the member count: a team that ends up
above it gets the warning described in "Over the limit". It works while the leaver and/or the owner are offline, and
members that left while the server was down are caught up at the next start. The leaver's own private claims stay
theirs.

**`partyOwnedClaims` (upstream's native party claims).** Team Claims works with the server option enabled. Native
party claims are owned by the *party owner's* UUID, so OPAC counts them as the owner's private claims, against the
owner's own limit, in both `player` and `party` claiming mode (those two modes show the same number for the owner).
`claimBonusPerPartyMember` / `claimBonusForPartyOwner` raise that private limit of the owner. Native party claims
never count against the team's budget, and the owner's team claims are not part of the number the party mode shows. A
native party claim made with the owner's `team_*` sub-config is indistinguishable from a team claim of the owner and is
treated as one (it counts against the team budget instead). The server logs one WARN line stating this at start-up
while the option is enabled.

## Claim budgets

Private claims and team claims are two separate budgets. Neither counts against the other.

* **Private claims** are all claims of a player that are not team claims. They count against OPAC's own per-player
  limits only: `maxPlayerClaims` and `maxPlayerClaimForceloads` of the OPAC server config (defaults 500 and 10), plus
  the usual OPAC bonus options and permission-based limits. A team claim never uses up a private claim.
* **Team claims** are the claims made while the team sub-config (`team_<name>`) is the selected sub-claim. They count
  against the team's pool only, whoever of the members made them, and a team claim never uses up anybody's private
  limit. Team forceloads (the forceload of a team claim) count against the team's forceload pool, private forceloads
  against the player's own forceload limit.

The limits of the team pool depend only on the number of members (the owner included, invited players not):

| Option (Team Claims server config) | Default | Meaning |
| --- | --- | --- |
| `teamClaimsMinMembers` | 2 | A team with fewer members has a team claim limit **and** a team forceload limit of 0. |
| `teamClaimsBase` / `teamForceloadsBase` | 500 / 10 | The limit of a team with exactly `teamClaimsMinMembers` members. |
| `teamClaimsPerExtraMember` / `teamForceloadsPerExtraMember` | 25 / 2 | Added for every member beyond `teamClaimsMinMembers`. |

So the team claim limit is `teamClaimsBase + (members - teamClaimsMinMembers) * teamClaimsPerExtraMember`, and the
team forceload limit is `teamForceloadsBase + (members - teamClaimsMinMembers) * teamForceloadsPerExtraMember`. With
the defaults a team of 1 member has 0 team claims and 0 team forceloads, 2 members 500 and 10, 3 members 525 and 12,
4 members 550 and 14. A team at or above a limit can make no new team claims (or team forceloads); the message says
which limit it is and, for a team that is too small, how many members it needs.

The claim counter of OPAC's claim UI and map shows the team's numbers while the team sub-config is the selected
sub-claim, and the player's private numbers otherwise. `/teamclaims info` shows both, `/oclaims about` shows the private
numbers. A team claim is still technically owned by the member who made it (OPAC's data stays compatible); mods that
use the OPAC API still see a player's own team claims in `getClaimCount()`.

## Over the limit

A team is over its limit when it has more team claims than its team claim limit, or more team forceloads than its team
forceload limit (each is tracked separately). That happens when a member leaves or is kicked (the limits shrink with the
member count), when the limits in the Team Claims server config are lowered, and after the update from 1.1.0. Teams
are checked on every membership change, at server start, when a team with a running deadline loses a team claim or
forceload, and once a minute (which is how a changed config is noticed).

* **Warning.** When a team is found over its limit, the deadline starts, `overLimitGraceHours` of real time from now (7
  days by default), and every online member gets a chat message with the number over the limit, the time left and the
  ways out. A member who is offline then gets the message with the time left at every login while the deadline runs.
  While it runs, the online members are reminded once every 24 hours. `/teamclaims info` and the party screen show the
  countdown. During that time the team can make no new team claims.
* **After the deadline** the most recently made team claims are unclaimed until the team is within its limit again,
  newest first (the order in which the chunks became team claims of that team; a hand-over to the owner keeps a claim's
  place). The online members are told how many. The team forceloads work the same way: the forceload of the most
  recently forceloaded team claims is turned off, the claims stay. The claims are handled first, because unclaiming a
  forceloaded team claim also takes a team forceload away. `overLimitGraceHours = 0` removes the excess at the next
  check instead (at the latest a minute later, at once on a membership change). If a claim cannot be changed right
  then (a claim transfer of its owner is running), it is tried again at the next check.
* **A running deadline keeps its end time** when `overLimitGraceHours` is changed.
* **Back within the limit** at any time before: the deadline is cancelled, nothing is removed, and the online members
  are told. (Inviting a member who left back therefore ends the countdown as soon as the larger team is within the
  limit; if the team is over its limit again later, a new full grace period starts.)
* **Ways out:** unclaim team claims (or turn off team forceloads), get more members, turn team claims into private claims
  with `/teamclaims convert topersonal` (they have to fit into the converting player's own limits), or, as the
  admin, raise the limits.

## Disbanding a party

When a party is removed (the owner disbands it with `/oparties destroy confirm` or the party screen, it expires, an
admin removes it, or it is deleted by the FTB Teams sync), its team claims stop being team claims, because the team
sub-configs go with it, and become private claims of whoever technically owns them (the member who made them, or the
party owner for claims handed over from a member who left). They only stay as far as that player has room in their own
private limit: the oldest team claims are kept, the rest is **unclaimed at once, newest first**. Of the kept claims, the
forceloads that were made first stay forceloaded as far as the player has private forceload room, the others lose their
forceload and stay claimed. Disbanding therefore never turns a team pool into extra private land. It also works for
players who are offline; online players get a chat line with how many claims they kept and how many were unclaimed. The
confirmation of the party screen says: the party will be deleted for all of its members, team claims become private
claims of the members who made them as far as their own claim limit allows, the rest is unclaimed, and this cannot be
undone.

## Party screen

A screen for what used to be typed as `/oparties ...` commands. Open it with the key **O** (the default; the vanilla key
for the social interactions screen is P) or with the **Party** button in the OPAC menu (the menu of the apostrophe key).
The key is rebindable in Minecraft's Controls under the category "Open Parties and Claims" as "Open Party Menu", and
pressing it again closes the screen while no text field has the focus. On a server without the mod, or with parties
disabled, the screen only says so (and the button is greyed out or missing). Every action sends the same `/oparties` command a player would type, so the server's checks and
messages are the same; the screen only offers the actions the player's rank allows.

* **Without a party:** create a party (the name is optional; characters OPAC does not allow in a party name turn the
  box red), and a list of the invitations sent to you with **Accept** and **Decline**. The list is refreshed every couple
  of seconds while the screen is open.
* **In a party:** the owner, the member and invitation counts, the rename box (owner), the members (owner first, then by
  rank, then alphabetical) with a rank button that cycles through the ranks you may give, **Kick**, and **Make owner**
  (owner only, with a confirmation), the pending invitations with **Cancel**, an invite box with buttons for online
  players who are not in the party yet (inviting and kicking need the rank moderator or higher, changing ranks admin or
  higher), and **Leave party** or, for the owner, **Disband party** (both with a confirmation).
* **Budget lines:** "Private claims / Forceloads" with your numbers, and for a team "Team claims / Forceloads" (the tooltip
  says what one more member adds to the limits). A team with fewer members than `teamClaimsMinMembers` gets a yellow
  hint instead, and a team over its limit a red line with the countdown to the removal.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/teamclaims create <name>` | A player in no party | Creates a party with that team name, sets up the team config and every member's team sub-config (`team_<name>`; a name without any letter or digit, or one already taken by another team, gets a short suffix from the party id, so the id is always unique). The name is validated first (not empty, at most `maxTeamNameLength` characters), and nothing is created when it is rejected. Needs the server option `partiesEnabled`. |
| `/oparties create [teamname]` | A player in no party | OPAC's own party create command. With the optional team name argument it does the same as `/teamclaims create`, through the same validated code path. |
| `/teamclaims info` | Everybody in a team | Overview of your team: the team name and owner; the team claims and team forceloads against the team limits (red when one is at its limit); the member count and what one more member would add to the limits, and a yellow line when the team has fewer members than `teamClaimsMinMembers`; the running over-limit countdowns; the forceload state (only when the team has forceloads: active, inactive because no member is online, or active during the grace period with the minutes left); the team roles; and one line per member (owner first, then by rank, then alphabetical) with the online marker, the member's **private** claims and private forceloads against their own limits (in red when at or over a limit) and how many team claims they technically own. |
| `/teamclaims info <player>` | Permission level 2 | The same overview for the team of another player (also offline). |
| `/teamclaims list [page]` | Everybody in a team | The team claims, 10 per page, sorted by dimension, then x, then z: dimension, chunk, block coordinates of the chunk's centre, the member who made it and whether it is forceloaded. The `[<]` and `[>]` in the footer are clickable. A team without team claims gets a hint on how to make one. |
| `/teamclaims territorymessages [on\|off]` | Everybody | Turns the claim welcome messages off or on for yourself, or shows the current state. See "Territory messages" below. |
| `/teamclaims roles` | Everybody in a team | Shows which rank your team requires to make, unclaim and forceload team claims. See "Team roles" below. |
| `/teamclaims roles <claim\|unclaim\|forceload> <member\|claimer\|moderator\|admin\|owner>` | The party owner and admins | Sets the rank one of those actions requires. The other online members are told about the change. |
| `/teamclaims convert toteam [radius]` | Everybody in a team | Turns your own claims in the square of chunks around the chunk you stand in (`radius` 0, the default, is only that chunk; at most `convertMaxRadius`) into team claims, keeping their forceload. See "Converting claims" below. |
| `/teamclaims convert topersonal [radius]` | Everybody in a team | Turns your own team claims in that area back into personal claims of your selected sub-claim (the main config if that is the team sub-config), keeping their forceload. |
| `/teamclaims ftbsync status` | Permission level 2 | Whether the FTB Teams sync runs (and if not, why), how many parties are linked to an FTB Teams party and what is waiting to be synced (up to 10 entries). See "FTB Teams sync" below. Also works from the console. |
| `/teamclaims ftbsync resync` | Permission level 2 | Runs the full reconciliation with FTB Teams right away and reports how many changes it made, how many parties are linked and how many entries are waiting. |

`info` and `list` only read, they change nothing. All of `/teamclaims` is unavailable while `enabled` is `false` in the
Team Claims server config.

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
between, and a forceloaded claim stays forceloaded (it becomes a team forceload, or a private one again). The area is
the square of chunks around the chunk you stand in, `radius` chunks in each direction (0 = only that chunk), at most
`convertMaxRadius` from the server config.

Only your own claims are touched, never a teammate's, another player's or a server claim. While impersonating another
player with OPAC's claims commands, their claims are converted. Every chunk goes through OPAC's normal (not forced) claim
path, so the same rules apply as for claiming it by hand, and a conversion moves a claim from one budget into the other,
so it needs room in the one it moves into:

* `toteam` needs the team's `claim` rank (and the `forceload` rank for a forceloaded claim) and room in the **team**
  pool: the team claim limit and, for forceloaded claims, the team forceload limit. Your private numbers do not matter.
* `topersonal` needs the `unclaim` rank and room in **your own** limits: your claim limit and, for forceloaded claims,
  your forceload limit. The team's numbers do not matter.

OPAC's admin mode does not bypass any of that here. The claims nearest to you are converted first, and the conversion
stops at the first claim that doesn't fit into the claim limit (the summary names the limit that was reached); when it is
the forceload limit, only the forceloaded claims are skipped. At the end you get one summary: how many claims were
converted (and how many of them are forceloaded), how many were skipped and why (not yours, already in that state, not
allowed for your team rank, over the limit, another reason).

## FTB Teams sync

Optional. When [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams) is installed next to this mod (with
the FTB Library and Architectury it needs itself), OPAC parties and FTB Teams party teams are kept in sync in both
directions, so a player only ever creates one team. Tested with FTB Teams 2101.1.11. Nothing of FTB Teams is bundled in
the jars, and without FTB Teams not a single FTB class is loaded.

It runs only while Team Claims is enabled, FTB Teams is installed, `ftbTeamsSync` is `true` in the Team Claims server
config (the default; read once when the server starts, so a change needs a restart) and the OPAC server option
`partiesEnabled` is on. Without FTB Teams the option does nothing. If an FTB Teams version does not have the classes
and methods the sync uses, the sync stops for that session, `/teamclaims ftbsync status` says so and the server log has
the reason.

**What is synced, in both directions:**

* A party existing at all: creating a party in one mod creates it in the other (an FTB party gets an OPAC party owned
  by the FTB owner, set up like `/oparties create <name>`), and disbanding it in one mod disbands it in the other.
* Members: joining, leaving, being kicked. FTB Teams does not let its owner leave while there are other members, so the
  ownership is handed over first (to the OPAC owner if possible).
* The owner.
* The name: the OPAC party name and the FTB display name. For FTB Teams, line breaks become spaces and names shorter
  than 3 characters are padded with `_`; for OPAC, formatting codes are removed, whitespace is collapsed, characters OPAC
  does not allow in a party name become `_` and the name is cut to the shorter of `maxTeamNameLength` and 100.
* Ranks: OPAC owner is FTB owner; OPAC admin and moderator are FTB officer; OPAC claimer and member are FTB member; an
  FTB officer becomes an OPAC moderator. Only the two classes (officer or not) are compared: while they agree
  nothing is touched, and the OPAC rank only follows FTB Teams when the FTB class changed and the OPAC rank did not,
  so an OPAC admin or claimer is not degraded by the sync.
* Invitations: an invitation made in either mod exists in both (without a second prompt), declining or withdrawing it
  in one removes it in the other, and accepting it in either joins both.

**Not synced:** allies, the FTB team colour, description and every other FTB team property, and party chat.

**OPAC wins.** On the first sync and wherever the mods disagree, the OPAC party is the truth:

* A party that exists in one mod only is created in the other. A player who is in different parties in the two mods is
  moved to the FTB party of their OPAC party and told.
* An FTB join that OPAC cannot take (the player is in another party, or the party has `maxPartyMembers`) is undone and
  the player is told. An FTB invitation OPAC cannot take (`maxPartyInvites`, a full party, a player in a party) is
  withdrawn and the FTB officers are told. An FTB rename OPAC cannot take (the server does not let players set party
  names) is set back and the officers are told.
* An FTB party that is deleted while the OPAC party still has members who are not in FTB Teams yet does not take the
  party with it: the FTB party is created again and the owner is told. A member who leaves the FTB party is removed from
  the OPAC party, except its owner, who is put back.
* When the FTB party really is deleted (its last member left, or it was force-disbanded) and every member was in it, the
  OPAC party is removed too. Disbanding in either mod therefore disbands in both, and the team claims then follow the
  disband rule above: they stay private claims only within the owners' private limits and the rest is unclaimed.

**What can stay pending.** Something that cannot be synced yet is kept and tried again, never dropped and never a
crash: a player who has not logged in since FTB Teams was installed (FTB Teams does not know them; they join the FTB
party at their next login, the same for an owner whose FTB party is to be created), an FTB party that is full
(`max_party_size` in the FTB Teams server config), a change FTB Teams refuses (with FTB's message), an owner who is not
yet a member of the FTB party, an FTB party whose owner is in another OPAC party, an FTB party for which OPAC did not
create a party, an FTB party that could not be disbanded, and unexpected errors. The players concerned get one chat line when it first
appears, the server log one WARN line (and at most one reminder per hour), and `/teamclaims ftbsync status` lists it
until it is resolved.

**When it runs.** OPAC changes and FTB Teams' events (create, delete, join, leave, kick, owner, name) are taken note of
and handled at the end of the same server tick. FTB Teams has no events for invitations, declining one and rank
changes, so every linked pair is compared once a second (every 20 ticks), and FTB-side invitations, declines and rank
changes are picked up within about a second. A full reconciliation, which also finds parties that exist in one mod only,
runs when the server has started and both mods have loaded, whenever FTB Teams gets to know a player (their first
login), once a minute, and with `/teamclaims ftbsync resync`. The link between the parties and the last state both
sides agreed on are saved with the world (`opacteamclaims_ftbsync`).

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
| `teamClaimsMinMembers` | `2` (1..100) | How many members (the owner included, invited players not) a team needs before it can have team claims and team forceloads at all. A team with fewer members has both limits at 0. See "Claim budgets". Changes apply right away; teams are re-checked at the next membership change or within a minute. |
| `teamClaimsBase` | `500` (0..1000000) | The team claim limit of a team with exactly `teamClaimsMinMembers` members. Same when it applies. |
| `teamClaimsPerExtraMember` | `25` (0..1000000) | Team claims added to the limit for every member beyond `teamClaimsMinMembers`. Same when it applies. |
| `teamForceloadsBase` | `10` (0..1000000) | The team forceload limit of a team with exactly `teamClaimsMinMembers` members. Same when it applies. |
| `teamForceloadsPerExtraMember` | `2` (0..1000000) | Team forceloads added to the limit for every member beyond `teamClaimsMinMembers`. Same when it applies. |
| `overLimitGraceHours` | `168` (0..8760) | How many real hours (also while the server is off) a team may stay above its team claim or team forceload limit before the newest team claims are unclaimed (or the newest team forceloads turned off). `0` removes the excess at the next check. A running countdown keeps its end time when this is changed; a new one uses the new value. See "Over the limit". |
| `ftbTeamsSync` | `true` | Whether parties are kept in sync with FTB Teams when that mod is installed. Does nothing without FTB Teams. Read once at server start, so a change needs a restart. See "FTB Teams sync". |

The private limits are not set here: they stay OPAC's own `maxPlayerClaims` and `maxPlayerClaimForceloads`.

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
listing text for that release. FTB Teams, FTB Library and Architectury are compile-only dependencies; none of them is in
the jars.

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

**Automated tests.** Four Gradle runs start a headless server with the mod and run the Team Claims `@GameTest` tests.
None of them needs an EULA, each starts with a fresh world, and each task fails unless its log contains
`All N required tests passed`:

| Run | FTB Teams | Tests | Log |
| --- | --- | --- | --- |
| `gradlew.bat :Fabric:runBootTest --console=plain` | not installed | 77 | `run-boottest/logs/latest.log` |
| `gradlew.bat :NeoForge:runTeamClaimsGameTest --console=plain` | not installed | 77 | `NeoForge/runs/teamClaimsGameTest/logs/latest.log` |
| `gradlew.bat :Fabric:runBootTestFtb --console=plain` | installed | 111 | `run-boottest-ftb/logs/latest.log` |
| `gradlew.bat :NeoForge:runTeamClaimsGameTestFtb --console=plain` | installed | 111 | `NeoForge/runs/teamClaimsGameTestFtb/logs/latest.log` |

The runs without FTB Teams have no FTB Teams, FTB Library or Architectury on their classpath, so they prove that the
mod boots and that all of Team Claims works without FTB Teams (it is optional): the wiring smoke test (bridge handler,
managers, `/teamclaims` and `/oparties` commands, team config dir), team sub-config creation and admin-only editing,
the separate private and team budgets, the over-limit grace period and its removal, the disband rule, teammate
unclaim/forceload, event-driven claim transfer on leave (party hooks queue membership events that are processed at the
end of the tick), long and non-ASCII team names, hardening (quick join and leave, claim overhead after many changes,
name validation, late joiners, corrupt team files, forceload tickets), the server config and the forceload grace
period, territory messages, `info`/`list`, team roles, `convert`, and the packets of the party screen (invitations and
budget numbers). The runs with FTB Teams (FTB Teams 2101.1.11, FTB Library and Architectury as test-only runtime
mods) run the same tests plus the FTB Teams sync tests: every operation in both directions, the rank stability rule, no
feedback loops, the first sync including conflicts, offline members, the saved link, the size limits, and the interplay
with Team Claims. The tests run against offline UUID players, independently of each other, and clean up after themselves
so reruns stay green.

On Fabric the tests run with Fabric API's gametest server (`-Dfabric-api.gametest`); that run lives in its own
`gametest` source set/mod (never shipped in the release jar) and includes a test-only mixin working around vanilla's
`GameTestServer` having no player profile cache. On NeoForge the test bodies are shared from `Common/src/gametest` and
loaded through the test-only `gametest` source set/mod (`NeoForge/src/gametest`, never shipped in the release jar).
The FTB runs add the second test-only mod `gametestFtb` on each loader.

## Changelog

### 1.2.0

* **Separate budgets.** Private claims count against OPAC's own per-player limits only; team claims count against the
  team's pool only. The pool depends on the member count: no team claims below `teamClaimsMinMembers` (2) members, then
  `teamClaimsBase` (500) + `teamClaimsPerExtraMember` (25) per further member, and `teamForceloadsBase` (10) +
  `teamForceloadsPerExtraMember` (2) team forceloads. Five new options in the Team Claims server config.
* **Over-limit grace period.** A team above its limit (a member left, the config changed, the update from 1.1.0) is
  warned and has `overLimitGraceHours` (168) real hours; then the newest team claims are unclaimed and the newest team
  forceloads turned off, until the team is within its limit. The countdown is cancelled when the team is back within it.
  **Teams with a single member lose their team claims after the grace period with the default settings, see "Updating
  from 1.1.0".**
* **New disband rule.** When a party is removed its team claims become private claims of the members who made them as
  far as their own claim limit allows, the rest is unclaimed at once (newest first).
* **Party screen** (key `O`, rebindable, and a button in the OPAC menu): create, accept and decline invitations, members
  and ranks, invite, kick, make owner, rename, leave, disband, and the private and team claim numbers.
* **FTB Teams sync** (optional, `ftbTeamsSync`, FTB Teams 2101.1.11): parties, members, owner, name, ranks and
  invitations are kept in sync with FTB Teams in both directions, OPAC wins on conflicts. `/teamclaims ftbsync status`
  and `resync`.
* `/teamclaims info` shows the team's numbers and every member's private numbers; `/teamclaims convert` checks the budget
  it moves a claim into; claims transferred with OPAC's `/oclaims transfer` become private claims of the target.
* New server config options `teamClaimsMinMembers`, `teamClaimsBase`, `teamClaimsPerExtraMember`,
  `teamForceloadsBase`, `teamForceloadsPerExtraMember`, `overLimitGraceHours` and `ftbTeamsSync`.
* 77 automated gametests (111 with FTB Teams installed), run on both loaders. Two extra Gradle runs with FTB Teams.
* The server and every client have to run 1.2.0.

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
