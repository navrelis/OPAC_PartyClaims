# Open Parties and Claims - Team Claims fork

A fork of [Open Parties and Claims](https://github.com/thexaero/open-parties-and-claims)
v0.31.6 by Xaero96, licensed under LGPL-3.0-only, adding a "Team Claims" layer on top
of the original party/claims system.

This fork targets **Fabric** for Minecraft 1.21.1 only (the upstream Forge/NeoForge
modules are not part of this repository).

## Team Claims behaviour notes

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

## Building

```
gradlew.bat :Fabric:build
```

The built jar is written to `Fabric/build/libs/`.

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

The same run also executes `TeamClaimsLogicTest`, a set of `@GameTest` methods that exercise the
actual Team Claims game logic (team sub-config creation/admin-only editing, claim/forceload
budget sharing, teammate unclaim/forceload, membership-poll-driven claim transfer on leave, long
team names) against offline UUID players, independently of each other and cleaning up after
themselves so reruns stay green.
