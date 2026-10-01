# Final report — OPAC Team Claims 1.1.0 (Fabric + NeoForge 1.21.1), session 2026-10-01

Branch `fabric-port` on github.com/navrelis/OPAC_PartyClaims (main untouched). Release files: `ExportedJars/v1.1.0/`
(`opac-team-claims-fabric-1.21.1-v1.1.0.jar`, `opac-team-claims-neoforge-1.21.1-v1.1.0.jar`, `CURSEFORGE.md`).
Version `1.1.0+opac.0.31.6`, mod id `openpartiesandclaims` (drop-in replacement for OPAC; client + server).

## Answer to "did you copy over the mod's source?"
Yes. The previous session had already re-based the fork on upstream OPAC **v0.31.6** (`1.21` branch, commit
`c0d97b37`, still the newest upstream code for 1.21.1 on 2026-10-01) and ported Team Claims to Fabric — but nothing was
committed. This session committed that as the baseline (`b6fe205`) and built on it. Verified at the end: `Common`,
`Fabric`, `NeoForge`, `CreateSupportCommon` differ from upstream only by `[Team Claims]`-marked hooks, entrypoint lines
and metadata.

## Requirements → implementation
| Requirement | Result |
|---|---|
| Fabric 1.21.1 (priority) | Loader 0.19.5 / Fabric API 0.116.17 / FCAP 21.1.6; builds, 44/44 gametests |
| NeoForge 1.21.1 | Upstream NeoForge module restored unchanged (NeoGradle 7.0.181, NeoForge 21.1.168, range `[21.0.0-beta, 21.2)`) + `TeamClaimsNeoForge` adapter; 44/44 gametests on NeoForge's gametest server |
| One codebase | All Team Claims logic in `Common/src/main/java/xaero/pac/teamclaims/` (no loader imports); thin adapters `TeamClaimsFabric` / `TeamClaimsNeoForge` |
| Optimise + improve Team Claims | 17 findings fixed (T4): event-driven membership (party hooks, same tick, no 3 s poll, quick join+leave no longer missed); claim->party and per-owner indexes; O(1) overhead; claim-limit sync batched once per tick; team JSON saved off-thread with dirty set; corrupt files preserved; all admin-set options reach late joiners; team forceloads use OPAC forceTicks (random ticks/natural spawning); one validated create path; unique sub-config ids; null-safety; dead client sync removed |
| Feature: team server config | `openpartiesandclaims-teamclaims-server.toml`: `enabled`, `maxTeamNameLength`, `forceloadGraceMinutes`, `territoryMessagesDefault`, `convertMaxRadius` |
| Feature: territory messages | OPAC already had claim welcome messages; now team-aware (one team = one territory) + `/teamclaims territorymessages [on|off]` per player |
| Feature: `/teamclaims info` + `list` | info [player]: totals, forceload state, per-member `personal + team = count / limit`, remaining budget and who limits it; list [page]: paged claim list with click buttons |
| Feature: team roles | `/teamclaims roles <claim|unclaim|forceload> <member|claimer|moderator|admin|owner>`; default member = old behaviour; enforced via claim hook + OPAC's claim-action listener API |
| Feature (lead pick): forceload grace period | team forceloads stay loaded N minutes after the last member left |
| Feature (lead pick): convert | `/teamclaims convert toteam|topersonal [radius]`, keeps forceloads, respects budget and roles |

## Key decisions (full list in decisions.md)
- Logic in Common + thin loader adapters (7 of 11 classes were already loader-neutral).
- NeoForge kept on upstream's NeoGradle setup (user choice); worked unchanged with Gradle 8.14.5 + loom 1.11.8.
- Upstream hooks stay minimal, additive and no-ops without the Team Claims handler; roles use OPAC's official addon API instead of new hooks.
- Team Claims config is its own SERVER toml registered from the adapters (no upstream change, no file collision).
- Territory messages extend OPAC's existing welcome messages instead of adding a competing system.
- Role rules that close bypasses: personal claim over an own-team team claim needs the unclaim role; a forceloaded new team claim (convert) needs the forceload role too.
- Fork metadata: sources/issues point to the fork repo, homepage stays upstream OPAC.

## Changed files (by area)
- Common: `xaero/pac/teamclaims/**` (facade, managers, roles, overview, convert, names, config), bridge `common/server/claims/TeamClaimsIntegration.java`; `[Team Claims]` hooks in 14 upstream files (ServerClaimsManager, PlayerClaimInfo, ClaimsManagerSynchronizer, PlayerConfig, PlayerConfigOptions, PlayerSubConfig, ServerboundSubConfigExistencePacket, CreatePartyCommand, ServerParty, PartyManager, PlayerConfigCommonChangeHandlers, ForceLoadTicketManager, ServerPlayerClaimWelcomer, PlayerConfigScreen) + `en_us.json`.
- Shared tests: `Common/src/gametest/**` (never shipped).
- Fabric: `teamclaims/fabric/TeamClaimsFabric.java`, entrypoint lines, `fabric.mod.json` (contact.issues), gametest wrappers.
- NeoForge: restored module, `teamclaims/neoforge/TeamClaimsNeoForge.java`, entrypoint lines, `build.gradle` gametest source set/run, test mod `NeoForge/src/gametest/**`.
- Build/repo: `settings.gradle` (NeoForge), root `build.gradle` (idea-ext), `gradle.properties` (version, URLs, no local JDK path), CI workflow (both loaders + both gametest runs), `.gitignore`, README, CurseForge listings.

## Checks run (by the lead)
- After every task: full diff review, both builds, both gametest suites.
- Final: `gradlew clean :Fabric:build :NeoForge:build :Fabric:runBootTest :NeoForge:runTeamClaimsGameTest` → BUILD SUCCESSFUL; Fabric "All 44 required tests passed", NeoForge "All 44 required tests passed".
- Production jars: 0 gametest entries; exported jars content-identical (CRC) to the final clean build.
- Recursive diff vs upstream `c0d97b37`: only the files listed above; every modified upstream Java file carries a `[Team Claims]` marker.
- Leftover scan: no TODO/FIXME/System.out/printStackTrace in Team Claims code; all 71 Team Claims lang keys used and present.

## Manual test checklist (cannot be automated headless)
Setup: `start-all.bat` (Fabric) or `gradlew :NeoForge:runClient` / `runServer`; two accounts in one party.
1. Join the server with both clients (Fabric, then NeoForge) — no disconnect, no log errors.
2. `/teamclaims create Test` → party + `team_test` sub-config appear in the OPAC config screen for both players.
3. Select the team sub-config, claim on Xaero's World Map (drag area) → team claims show in the shared color/name; both players' counters show personal + team.
4. Teammate unclaims / toggles forceload on your team claim in the map UI; non-member cannot.
5. Non-admin member: team sub-config options are read-only, delete button disabled.
6. Walk across team land claimed by both players → no repeated action-bar message; `/teamclaims territorymessages off` silences all.
7. `/teamclaims roles unclaim admin` → member's unclaim in the map UI is refused and the reason is readable (also on the map mod's UI).
8. `/teamclaims info`, `/teamclaims list` (click page arrows), `/teamclaims convert toteam 1`.
9. Forceloaded team chunk keeps a farm running while one member is online; with `forceloadGraceMinutes=2`, stays loaded ~2 min after the last member logs off.
10. Leave the party → your team claims move to the owner, both get a chat line.
11. Set `enabled=false`, restart → stock OPAC behaviour, `/teamclaims` unavailable.

## Known limitations / recommendations
- Not tested in a real multiplayer session (only headless gametests incl. mock players) — use the checklist above.
- Client and server must run the same version; a 1.0.0 client with a 1.1.0 server is not supported.
- Going back to stock OPAC: stock caps sub-config ids at 16 chars; longer team sub-configs are not loaded by stock and their claims fall back to the main config (documented in README/listing).
- The Team Claims config lives in the global `config/` folder (per-world override in `<world>/serverconfig/`), like OPAC's own.
- `/teamclaims convert` calls OPAC's claim helper directly, so OPAC's own per-claim chat output/result packet is not sent (Team Claims prints its own summary).
- Map-UI display of addon rejection reasons depends on Xaero's map mods (OPAC forwards them).
- Gradle prints "deprecated features, incompatible with Gradle 9"; a later Gradle 9 move needs newer loom/NeoGradle.
- Release type suggestion: Beta (first public NeoForge build).

## Knowledge graph
Graphify graph of the final code: `graphify-out/` (local, not committed — see decisions.md): `graph.json` (9,520 nodes,
35,874 edges, 247 communities), `GRAPH_REPORT.md`, `graph.html` (aggregated community view). Core abstractions by
connectivity: `IServerData`, `IPlayerChunkClaim`, `IPlayerConfig`/`PlayerConfig`, `IServerClaimsManager`, `ServerCore`.
Health note: 3,191 dangling-endpoint edges point to Minecraft/JDK types outside the indexed corpus (expected for a mod).
Update later with `/graphify . --update`.
