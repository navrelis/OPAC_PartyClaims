# Final report — OPAC Team Claims 1.2.0 (Fabric + NeoForge 1.21.1), sessions 2026-10-08 / 2026-10-09

Branch `v1.2-dev` on github.com/navrelis/OPAC_PartyClaims, merged into `fabric-port` (GitHub default) on 2026-10-09;
`main` untouched. Release files: `ExportedJars/v1.2.0/`
(`opac-team-claims-fabric-1.21.1-v1.2.0.jar`, `opac-team-claims-neoforge-1.21.1-v1.2.0.jar`, `CURSEFORGE.md`; jars are
not committed). Version `1.2.0+opac.0.31.6`, mod id `openpartiesandclaims`. CurseForge (project 1704256, Beta): NeoForge file
9106819 uploaded; the Fabric file is still open (see `handoff.md`).
(The v1.1.0 report is in the git history of this file.)

## Requirements → implementation
| # | Requirement | Result |
|---|---|---|
| 1 | One team for FTB Teams and OPAC | Optional two-way sync (`ftbTeamsSync`, default on, only with FTB Teams installed; tested with FTB Teams 2101.1.11). Mirrored both ways, also for offline players: create, disband, join, leave, kick, owner, name, ranks, invitations. Three-way merge per party pair against a saved baseline; OPAC wins on the first sync and on conflicts; what cannot be mirrored yet stays pending and is retried. `/teamclaims ftbsync status` / `resync` (permission level 2). Nothing of FTB Teams is in the jars and no FTB class is loaded without it. |
| 2 | Screen instead of `/oparties ...` | Party screen on key `O` (rebindable; `P` is vanilla's Social Interactions) and a "Party" button in the OPAC menu: create, invitations sent to you (accept / decline), members and ranks, invite, cancel invite, kick, make owner, rename, leave, disband, private and team budget lines with the over-limit countdown. English + German. Packets 50–53. |
| 3 | Separate private and team claims | Private claims count against OPAC's own limit only (500 by default); team claims against the team pool only: 0 below 2 members, 500 with 2, +25 per further member; team forceloads 10, +2 per further member. All values are options of the Team Claims server config. |
| 4 | "Bulletproof" when a team shrinks | A team above its limit is warned (at once, at login, daily) with the time left and the ways out; after `overLimitGraceHours` (7 days real time, also while the server is off) the newest team claims are unclaimed until the team is within its limit; same for team forceloads (forceload turned off, claim stays). The deadline is cancelled when the team is back within the limit. Shown in chat, `/teamclaims info` and the party screen. |

## Things you should know (decisions and behaviour)
- **Solo teams lose their team claims after the update unless something is done.** A team with one member has a team
  limit of 0 (`teamClaimsMinMembers = 2`). On the first start with 1.2.0 such teams get the warning and, a week later,
  their team claims are unclaimed. Ways out: a second member joins, `/teamclaims convert topersonal`, or you set
  `teamClaimsMinMembers = 1`. README and the CurseForge text have an "Updating from 1.1.0" section for this.
- **Disband:** team claims become private claims of whoever made them only as far as that player has private room; the
  rest is unclaimed at once, newest first (otherwise disbanding would turn a team pool into extra private land). The
  confirmation in the party screen says so.
- **Disbanding in FTB Teams disbands the OPAC party too** (last FTB member leaves, or `force-disband`), with the claim
  consequences above. Exception (found in review and fixed): if the OPAC party still has members FTB Teams never had
  (not logged in since FTB Teams was installed, FTB party full, join refused), the party is kept, the FTB party is
  created again and the former FTB owner is told.
- The deadline is cancelled as soon as the team is back within its limit, so re-inviting a member resets it.
- Zero time left reads "1 min" (same rounding as the party screen); the time value loses its white colour for players
  without the mod on the client.
- Third-party mods using the OPAC API still see a player's own team claims in `getClaimCount()`.
- FTB Teams has no events for invitations, declines and rank changes: those are picked up within about a second.
  The hooks and FTB events do not say who acted, so messages go to the affected player, the party owner or the FTB
  owner and officers.
- After a server crash between a disband and the next world save, the disband is read as "one side vanished" and the
  party is created again from the other mod (the removal note is saved with the world).
- An OPAC-side owner transfer renames the party to the new owner's party name (OPAC behaviour) and FTB follows.

## What can stay "pending" in the FTB Teams sync (retried, listed by `/teamclaims ftbsync status`)
A member or owner FTB Teams has never seen (resolved at their first login); FTB party at `max_party_size`; a join,
ownership transfer, leave, create or disband FTB Teams refuses; a new OPAC owner who is not in the FTB party yet; an FTB
party whose owner is in another OPAC party; an FTB party OPAC did not create a party for; an unexpected error while
merging one pair (the other pairs go on). Resolved at once for OPAC instead of pending: an FTB join, invitation or name
OPAC cannot take is undone and the players concerned are told.

## Changed files (88 files, by area; since `fabric-port` 994f95b)
- **Budgets + grace (T1):** `teamclaims/TeamClaimManager`, `TeamClaimsBridgeHandler`, `TeamClaimsOverview`,
  `TeamClaimsConvert`, `config/TeamClaimsServerConfig`; marked hooks in `common/server/claims/**` (claims manager,
  claim/forceload helpers, synchronizer, ticket manager, about/transfer commands, chunk protection), `common/claims/player/**`.
- **Party screen (T2, T4):** `client/gui/party/**` (5 files), key binding, main menu button, `client/parties/party/**`,
  packets `common/packet/parties/**` (7 files) + `PacketRegister`, `common/parties/party/ReceivedPartyInvite`,
  `ServerPlayerData` (request rate limit).
- **FTB Teams sync (T3):** `teamclaims/ftbsync/**` (7 classes), hooks in `ServerParty` (invite, un-invite, rank) and
  `TeamClaimsIntegration`, lifecycle in `TeamClaimsCommon`, command node, `ftbTeamsSync` option; build: FTB and
  Architectury mavens, compile-only dependencies, `gametestFtb` source sets, runs `:Fabric:runBootTestFtb` and
  `:NeoForge:runTeamClaimsGameTestFtb`, CI workflow; `ftbteams` as suggested / optional dependency in both metadata files.
- **Texts (T4, T5a, T5b):** `en_us.json`, new `de_de.json` (fork strings: Team Claims, party screen, FTB sync).
- **Tests:** `Common/src/gametest/**` (budget, packet and FTB sync cases), wrappers in `Fabric/src/gametest/**` and
  `NeoForge/src/gametest/**`.
- **Release:** `gradle.properties` (version, description), `README.md`, `ExportedJars/v1.2.0/CURSEFORGE.md`.

## Checks run by the lead
- After every task: full diff read, both builds, all test runs (details per task in `log.md`).
- Corrections sent after review: FTB-side deletion with pending members (T3), stale mod description (T5b).
- Final run on the release state: `gradlew clean :Fabric:build :NeoForge:build :Fabric:runBootTest :NeoForge:runTeamClaimsGameTest`, then `gradlew :Fabric:runBootTestFtb :NeoForge:runTeamClaimsGameTestFtb` -> BUILD SUCCESSFUL twice; `:Fabric:runBootTest` and
  `:NeoForge:runTeamClaimsGameTest` without FTB Teams 77/77 each; `:Fabric:runBootTestFtb` and
  `:NeoForge:runTeamClaimsGameTestFtb` with FTB Teams 111/111 each (77 + 34 sync tests).
- Jars: no `dev/ftb`, `dev/architectury` or gametest entries; version 1.2.0 in both metadata files; exported jars
  have the same content as the final build (only the build timestamp in the manifest differs).
- Lang: `en_us.json` and `de_de.json` valid JSON, same placeholders per key, same key order; every Team Claims,
  party-screen and FTB-sync key used in code exists in both, none unused.
- Leftover scan: no TODO / FIXME / System.out / printStackTrace in anything changed since 1.1.0.

## Manual test checklist (needs two clients; cannot be automated)
1. Key `O` opens the party screen and closes it again when no text field is focused; the key shows in Controls;
   "Party" button in the OPAC menu (apostrophe key).
2. No party: create with / without a name (invalid characters turn the box red); a second player's invite appears
   within about 2 s; Accept joins, Decline removes it.
3. In a party as owner / admin / moderator / member: Kick, rank button, Make owner (confirm), Invite (text + player
   suggestions), Cancel invite, Rename, Leave / Disband (confirm) — buttons appear exactly when the server allows it.
4. Budget lines: private line always; team line in a team; yellow hint with fewer than 2 members; red countdown while
   over the limit ("d h" / "h min" / "min"), gone after unclaiming.
5. Window at 320x240 (largest GUI scale): nothing overlaps, at least 3 member rows visible.
6. German language: no raw keys, long confirmation texts wrap.
7. Budgets: with 500/500 private claims, team claims still work and vice versa; `/teamclaims info` matches the screen.
8. Kick a member of a team that uses its whole limit → warning to all online members, again at login and daily; after
   the deadline (set `overLimitGraceHours` small) the newest team claims are gone.
9. Disband with more team claims than private room → chat line with kept / unclaimed counts.
10. With FTB Teams installed: every party action done in OPAC shows in FTB Teams and the other way round (create,
    invite, accept, decline, leave, kick, transfer, rename, promote / demote, disband); `/teamclaims ftbsync status`.
11. Server without FTB Teams behaves as before; a 1.1.0 world with a solo team shows the warning after the update.

## Known limitations and recommendations
- Not tested in a real multiplayer session or with a real client: the party screen and the FTB Teams sync are covered by
  headless gametests (mock and offline players) only. Use the checklist above before a public release; suggested
  release type Beta.
- Client and server must both run 1.2.0.
- Not covered by an automated test: an FTB Teams version without the classes the sync uses (the sync then turns
  itself off and says so), a permission mod refusing FTB's party creation, FTB refusals other than "party full",
  `partyOwnedClaims` together with Team Claims, the rate limit of the sync's log warnings.
- The sync uses FTB Teams implementation classes (its public API has almost no writes), so a newer FTB Teams version
  may need an update of the sync; it fails closed (sync off, server keeps running).
- German covers the fork's own texts; upstream OPAC texts stay English.
- `graphify-out/` was not updated (your instruction).
- Gradle prints "deprecated features, incompatible with Gradle 9"; a later move needs newer loom / NeoGradle.
