# Final report — OPAC PartyClaims → Fabric 1.21.1 (2026-09-20)

## Result
`Fabric/build/libs/open-parties-and-claims-fabric-1.21.1-0.31.6+teamclaims.1.jar`
Patched OPAC (mod id `openpartiesandclaims`), built on upstream **v0.31.6** (`c0d97b37`), Fabric Loader 0.19.5, fabric-api 0.116.17, Forge Config API Port 21.1.6. Replaces the stock OPAC jar on **client and server**. Branch `fabric-port`, **nothing committed**.

## Requirements → implementation
| Requirement | Status |
|---|---|
| Remove NeoForge, Fabric only | NeoForge/, neoforge_docs/, other_mods/, run dirs, MDK README removed; modules = Common, Fabric, CreateSupportCommon |
| Base on 0.31.6 (pack version) | Common/Fabric sources = upstream + Team Claims delta only (verified by recursive diff); production refmap and file list identical to the stock 0.31.6 jar except Team Claims classes |
| Team sub-config `team_<name>` per member, long ids allowed | `PlayerConfig.isValidSubIdOrTeam`, `USED_SUBCLAIM` validator, `TeamClaimManager.ensureTeamSubConfig` |
| Shared budget (overhead on every member, reject if any member over limit) | `PlayerClaimInfo.getClaimCount/getForceloadCount`, `ServerClaimsManager.tryToClaimHelper/tryToForceloadHelper` → `interceptClaim/interceptForceload`; client shows server numbers when overhead > 0 (`ClaimsManagerSynchronizer`) |
| Any member may unclaim / toggle forceload on team claims | predicates `allowsTeamUnclaim/allowsTeamForceload`; upstream's own unclaim path runs (transfer checks, action listeners) |
| Admin/owner-only synced settings, party always has full access | `PlayerConfig.tryToSet` gate + propagation; "protect from party" re-expressed as `FULL_ACCESS ∈ {party, allies, everyone}` lock; client screen read-only for non-admins |
| Team sub-config not deletable | server guard in `ServerboundSubConfigExistencePacket`, delete button disabled |
| Forceload while any member online | `TeamForceLoadHandler` tickets, unchanged logic |
| `/openpac-parties create [teamname]`, `/teamclaims create <name>` | `CreatePartyCommand` (honours 0.31.6 party impersonation), `TeamClaimsCommands` |
| Native `partyOwnedClaims` untouched | hooks only fire for PLAYER-type configs' `team_*` subs; handler null ⇒ stock behaviour |
| Fabric 2-client + server test setup | `:Fabric:runClient`, `runClient2`, `runServer` (run-client1/2, run-server), `start-all.bat`, `stop-all.bat` |
| Fix bugs found | see below |

## Bugs fixed
- Teammate forceload toggle bypassed the shared budget and OPAC's action listeners (legacy handler did the toggle itself) → now goes through upstream path with budget check.
- Forceload re-entering `tryToClaimHelper` would have been budget-checked twice on 0.31.6 → claim hook limited to `ClaimingAction.CLAIM`.
- Re-claiming an existing team claim could be rejected at exactly the limit (+1 counted needlessly).
- Teammate toggle was checked against the requester's limits instead of the claim owner's.
- Team JSONs lived in global `config/opacteamclaims/teams` → a second singleplayer world purged the first world's teams. Now `<world>/data/opacteamclaims/teams` (no migration of old files).
- Hardcoded English `§` chat strings → 12 `gui.xaero_pac_team_claims_*` lang keys via OPAC's AdaptiveLocalizer (works for clients without the lang file too).
- Fabric fires JOIN before OPAC's own login hook → Team Claims login handling deferred by one tick.
- Hand-built claim-limits packet replaced by OPAC's own synchronizer (no double counting, no fight with OPAC's periodic sync).

## Files
- Hooked upstream files (all blocks marked `// [Team Claims]`): `Common/.../PlayerConfigScreen`, `PlayerClaimInfo`, `ServerboundSubConfigExistencePacket`, `ServerClaimsManager`, `ClaimsManagerSynchronizer`, `CreatePartyCommand`, `PlayerConfig`, `api/v2/PlayerConfigOptions`, `PlayerSubConfig`, `en_us.json`; `Fabric/.../OpenPartiesAndClaimsFabric` (init calls).
- New: `Common/.../server/claims/TeamClaimsIntegration.java` (bridge); `Fabric/src/main/java/xaero/pac/teamclaims/**` (11 classes, incl. client-only `TeamClaimsClientInit`); `Fabric/src/gametest/**` (smoke test mod, not packaged).
- Build: `settings.gradle` (modules, loom plugin resolution), `gradle.properties` (versions, `0.31.6+teamclaims.1`), `Fabric/build.gradle` (loom 1.11.8, FCAP 21.1.6, runs, gametest source set), Gradle wrapper 8.14.5, root `build.gradle` (idea-ext plugin removed), README, CI workflow, bats.
- Not re-applied on purpose: the fork's cosmetic deprecation edits in Patreon/ConfigUtil/Reflection/ChunkProtection/ServerConfig.

## Checks run by the lead
- `gradlew clean :Fabric:build` → BUILD SUCCESSFUL.
- `gradlew :Fabric:runBootTest` (headless Fabric gametest server, no EULA needed) → "All 1 required tests passed": bridge active, managers created, `/teamclaims create` + `teamname` argument registered, team dir created; log shows Loader 0.19.5, mod 0.31.6+teamclaims.1, no ERROR / Mixin failures.
- Recursive diff vs upstream clone after every task; full read of every hook diff; refmap equality vs stock jar; production jar contains no gametest classes.

## Known limitations / recommendations
1. **Not tested in a real multiplayer session** — claim/unclaim/forceload/budget/settings flows were desk-checked (7 scenarios) but need your in-game test with `start-all.bat`. You must set `eula=true` in `run-server\eula.txt` and `online-mode=false` yourself.
2. (superseded in phase 2) `partyOwnedClaims` can coexist: upstream uses ONE pool per party owner, so native party claims count as the owner's personal claims and reduce the whole team's budget; a WARN at server start explains this when both are on.
3. (superseded in phase 2) Leaving/kick now transfers the member's team claims to the party owner.
4. A sub-config a player names `team_*` on a server WITHOUT this fork shows a greyed delete button client-side (string check only).
5. `detectAndPropagateSettingChanges` (20-tick poll) is last-writer-wins; `findPartyForTrackedClaim` is O(parties × claims) per chunk change — fine for a pack server, not for hundreds of parties.
6. Old world data: existing claims made with stock 0.31.6 load unchanged (same data format). Old NeoForge-era `opacteamclaims` JSONs are not migrated.
7. Deployment is out of scope: the user asked that the Nytheria modpack is not touched by this project.
8. Nothing is committed; `.fable/legacy/` holds the original NeoForge sources and fork diff for reference (original state is also commit `e901c19`).

# Phase 2 (2026-09-20)

## Done
- **Automated logic tests** — `Fabric/src/gametest/.../TeamClaimsLogicTest.java`, 8 tests on offline UUID players against the real OPAC server API, run headless with `gradlew :Fabric:runBootTest` (9/9 incl. smoke test, rerun-stable, world wiped before each run, not packaged): team sub-config creation; overhead counting; team claim rejected when a teammate is at limit while a personal claim still works; teammate unclaim vs. personal claim / non-member; teammate forceload toggle; non-admin edit rejected, owner/admin edit propagates, `FULL_ACCESS` lock; leave → claims transferred to owner with forceload flag, sub-config removed; long party name sub-config id.
- **`partyOwnedClaims` coexistence** — analysis showed there is no leak (native PARTY numbers are the owner's single pool by upstream design). Real fixes instead: budget checks now use `getPlayerFullClaimLimit/ForceloadLimit` (exactly what OPAC enforces, party bonuses included); exact deltas when a claim REPLACES an existing one (converting your own personal claim, or an existing team claim, was rejected one claim early at the limit); startup WARN describing the shared pool.
- **Transfer on leave/kick** — team claims of the leaver are re-assigned to the current party owner via `ServerClaimsManager.claim` (normal tracker callbacks + client sync, party-wide resync batched), budget-neutral, skipped with a WARN if OPAC has a claim transfer/replacement task running for either player (then legacy behaviour). Also applied at server start for members who left while the server was down, followed by a new membership reconciliation (previously such changes were never noticed). Leaver and owner get a localized chat line. Disband unchanged; no give-back on rejoin.
- **Dev server** — `run-server/eula.txt` = `eula=true` (explicitly authorised by the user) and `server.properties` with `online-mode=false`; `start-all.bat` works out of the box.

## Still only verifiable in game (by the user)
- Config screen read-only state for non-admins; delete button; claim numbers on Xaero's map/`/openpac-claims` UI with overhead; chat messages; the sub-config delete packet guard (needs a real client); forceload tickets actually keeping chunks loaded while a member is online.

## Notes
- Membership changes are picked up by a 60-tick poll; a party created through the command is known immediately, but a member who joins and leaves again within ~3 s of party creation would not trigger a transfer.
- Nytheria boundary: the instance was only ever read (mod list, OPAC config, stock jar for a refmap comparison) before the user set the boundary; nothing was written there, and it is not accessed any more.

# Phase 3 — v1.0.0 release export (2026-09-20)
- Version `1.0.0+opac.0.31.6`, display name "Open Parties and Claims: Team Claims" (mod id unchanged).
- `ExportedJars/v1.0.0/opac-team-claims-fabric-1.21.1-v1.0.0.jar` + `CURSEFORGE.md` (summary 234/256 chars, main category Server Utility + 4 additional, full description). Clean build and 9/9 game tests on this exact version.
- Commands are `/oparties create [name]` and `/teamclaims create <name>` (earlier report lines saying `/openpac-parties` were wrong).
