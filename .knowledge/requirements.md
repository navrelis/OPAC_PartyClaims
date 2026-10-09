# Requirements (session 2026-10-08, v1.2.0)

Earlier state: v1.1.0+opac.0.31.6 on `fabric-port` — fork of OPAC v0.31.6 (`c0d97b37`) for MC 1.21.1, Fabric + NeoForge, Team Claims layer in `Common/src/main/java/xaero/pac/teamclaims/`. Session 2026-10-01 details: git history of this folder.

## Goals (user)
1. **FTB Teams <-> OPAC party sync**, both directions, so a player only ever creates one team: create, invite, join, leave, kick, disband, owner transfer, name, ranks. Optional: active only when FTB Teams is installed (+ config switch `ftbTeamsSync`, default on).
2. **Party screen** opened by a hotkey (new rebindable key, default `P`) and from the existing OPAC menu; replaces typing `/oparties ...`: create with name, members + ranks, invite, accept/decline invites sent to you, kick, promote/demote, transfer, rename, leave, disband (confirm), private + team claim numbers. English + German.
3. **Private and team claim budgets separated**: private 500 per player (OPAC `maxPlayerClaims`); team pool 0 with one member, 500 from 2 members, +25 per further member; team claims no longer count against private limits. Forceloads the same way: private = OPAC limit, team pool 10 from 2 members, +2 per further member. All values configurable.
4. **Shrinking teams ("bulletproof")**: when a team is over its limit (member left/kicked, config change, upgrade), members get a warning to unclaim; after 1 week real time (configurable) the most recently claimed team claims are unclaimed automatically until the team is within its limit.

## Confirmed answers / defaults
- FTB Teams 2101.1.11 (newest for 1.21.1, the user's pack uses it); FTB Chunks is not installed.
- First sync on an existing world: a party that exists in one mod only is created in the other; on conflicting membership OPAC wins.
- Rank mapping: Owner<->Owner; OPAC Admin/Moderator -> FTB Officer; FTB Officer -> OPAC Moderator; Member<->Member (OPAC Claimer -> FTB Member).
- Not synced: allies, FTB colour/description, party chat.
- Invites: an invite in either mod shows in both; accepting in either joins both. FTB has no invite/rank events -> FTB-side invites and rank changes are picked up by a ~1 s check.
- Refactoring of Team Claims budget logic and new `[Team Claims]`-marked hooks in upstream files allowed; protocol change allowed (client + server same version).
- Version `1.2.0+opac.0.31.6`. No new shipped dependencies (FTB Teams, FTB Library, Architectury are already compile-only); they may be added to dev/test runs only.
- Git: working branch `v1.2-dev` off `fabric-port`, pushed to origin (github.com/navrelis/OPAC_PartyClaims). No merge into `fabric-port`/`main` unless the user says so. Never force-push.
- Release: build both jars into `ExportedJars/v1.2.0/` (not committed) + update listing text. No CurseForge upload.
- No Graphify this session (user).

## Standing constraints
- MC 1.21.1; Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, FCAP 21.1.6; NeoForge 21.1.168 (range `[21.0.0-beta, 21.2)`), NeoGradle 7.0.181, Gradle 8.14.5, loom 1.11.8, Java 21.
- Mod id `openpartiesandclaims`. Existing world data (claims, `<world>/data/opacteamclaims/**`, `opacteamclaims_data`) must keep loading.
- Upstream OPAC behaviour unchanged when Team Claims is disabled; Common delta to upstream minimal, additive, marked `[Team Claims]`.
- HARD BOUNDARY: never touch the Nytheria modpack instance; all work stays inside this repo.
- Dev server `run-server/eula.txt=true`, `online-mode=false` were authorised by the user earlier.
