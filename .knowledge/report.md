# Final report — OPAC Team Claims 1.3.0 (Fabric + NeoForge 1.21.1), sessions 2026-10-12 / 2026-10-13

Branch `v1.3-dev` on github.com/navrelis/OPAC_PartyClaims (off `fabric-port` `50d1919`, = 1.2.0); `fabric-port` and
`main` untouched, no merge without the owner's word. Release files: `ExportedJars/v1.3.0/`
(`opac-team-claims-fabric-1.21.1-v1.3.0.jar`, `opac-team-claims-neoforge-1.21.1-v1.3.0.jar`, `CURSEFORGE.md`; jars are
not committed). Version `1.3.0+opac.0.32.7`, mod id `openpartiesandclaims`. CurseForge: NeoForge file 9151608, Fabric file 9151657 (Release); see the last section.
(The v1.2.0 report is in the git history of this file.)

## Requirements → result
| # | Requirement | Result |
|---|---|---|
| 1 | Fork contains upstream OPAC 0.32.7 with Team Claims on top | Real git merge of `thexaero/open-parties-and-claims` `1.21` (`75ce21d6`) as commit `7d7196c3` (two parents; later upstream updates are a plain `git merge`). 9 conflicted files resolved; `Forge/` and `docs/javadoc/` stay deleted. Fork packets moved from ids 50–53 to 100–103 (upstream took 50). |
| 2 | Proof the World Map crash is gone | Linkage checker `tools/linkcheck/` on the final jars against World Map 1.47.0 + Minimap 26.6.0: Fabric 102 + 54 references OK, NeoForge 103 + 55 OK, 0 missing, exit 0. Control on the 1.2.0 jars (both loaders): exit 1, 9 missing incl. `IClientClaimsManagerAPI.usingAnchorBasedClaiming()`. Dev-client run (NeoForge, 2026-10-13, after the upload): map opened, right-click menu shown, "Claim Selected" claimed chunks, no crash, no linkage error in the log. |
| 3 | Existing tests green, new tests | Default runs 100 / 100 on Fabric and NeoForge, with FTB Teams 134 / 134 on both (was 77 / 111 in 1.2.0). New: 4 + 5 anchor tests, 7 claim limits packet tests, 7 `overLimitEnforcement` tests, and a separate save-compatibility run (4 tests per loader) that starts the server on world data written by the real 1.2.0 code. |
| 4 | README upgrade notes | "Updating from 1.2.0": server and all clients together (network version 6 → 7), existing worlds load as they are, no way back to 1.2.0. |
| 5 | Changelog, jars, `CURSEFORGE.md` | Done, see above. |
| 6 | CurseForge upload, both loaders, Beta | See the last section. |
| 7 | Acceptance, Graphify, report, push, shared log | See the last section. |
| 8 | Option to switch the over-limit rule off | `overLimitEnforcement` (Team Claims server config, default `true` = 1.2.0 behaviour, applies without restart). `false`: a team above its limit keeps all team claims and team forceloads; no deadline, no chat / login warnings, no countdown in `/teamclaims info` or on the party screen; a running or stored deadline is dropped silently at the next check; switching back on starts a fresh deadline. |

## Things you should know
- **`overLimitEnforcement = false` does not lift the limit.** A team above its limit keeps everything but still cannot
  make new team claims or team forceloads until it is back within the limit. This was the open question of the
  session; built as "no". Changing it later is a small change in the limit check.
- With the option off, `/teamclaims info` and the party screen still show the numbers against the limit (e.g. `5 / 3`,
  in red), just without a countdown. No new lang keys were needed.
- **Anchors (only with upstream's `anchorBasedClaiming` on, default off):** anchors stay per player and dimension, a
  team has no anchor pool; a team claim obeys the anchors of the player who makes it. Known limitations, not fixed:
  with a private limit of 0 nobody can place a first anchor (it needs one private claim); when a member leaves, team
  claims the owner cannot anchor (owner's anchor limit full, or a smaller anchor range) become private claims of the
  leaver without a limit check or chat message; a team stuck above its limit on anchor chunks still gets the "will be
  unclaimed" reminder.
- A player's own team claim sends one claim limits packet with the old team count, followed in the same tick by the
  corrected one. The client ends up right; one redundant packet.
- `isOverEitherClaimingLimit` (upstream's "player is over a limit" check) looks at private claims and anchors only; a
  team above its team limit does not make its members "over". Intended: the team has its own rule.
- The flaky FTB sync test `failingPairDoesNotStopTheOthers` (1 of 4 Fabric FTB runs in session 1) was a test ordering
  problem: a note of another test in the same tick made the sync reconcile a second time. Fixed in the test; the cause
  was reproduced by injecting such a note, not caught organically. No FTB sync main code changed in 1.3.0.
- Upstream's history is on origin with this branch. `git diff --check` reports whitespace in two upstream files; left
  as upstream has them.

## Verification (lead's own runs on the final commit, clean build)
- `gradlew clean :Fabric:build :NeoForge:build :Fabric:runBootTest :NeoForge:runTeamClaimsGameTest`: 100 / 100 on both.
- `gradlew :Fabric:runBootTestFtb :NeoForge:runTeamClaimsGameTestFtb`: 134 / 134 on both.
- `gradlew :Fabric:runSaveCompatTest :NeoForge:runSaveCompatGameTest`: 4 / 4 on both.
- Exported jars are byte-identical to the build (SHA-256 Fabric `8fbaa338…7be6547`, NeoForge `b8a3847c…4d315d0`);
  version `1.3.0+opac.0.32.7` in both; no gametest, fixture, `dev/ftb` or `dev/architectury` entries; `ftbteams`
  optional. No TODO / FIXME / `System.out` / `printStackTrace` in the Team Claims layer.
- Read by the lead: the merge delta of the 9 conflicted files and the Team Claims layer (session 1), the linkage
  checker source, every new test file of T3, the full main-code diff of `overLimitEnforcement`, the README and
  `CURSEFORGE.md` diffs.

## Not verified
- Client: only one NeoForge dev-client run (see requirement 2); Fabric client, anchor entries on the map, and the layout of the OPAC menu
  with upstream's new Anchor / Unanchor buttons next to the "Party" button are untested (coordinates only).
- Lang check of T4 (en 820 keys, de 165, fork keys identical) is the agent's claim.
- Not shown that the save-compat tests fail on wrong data (they compare exact constants of the fixture).
- `ChunkProtection` with `overLimitEnforcement = false`: unchanged code, concluded by reading, no dedicated test.
- Live reload of the new option from the config file: by reading (same mechanism as `overLimitGraceHours`).

## Manual test checklist
- Client with World Map 1.47.0 + Minimap 26.6.0 and the 1.3.0 jar, both loaders: open the map, right-click (anchor
  entries appear only with anchors on), claim / unclaim from the map.
- OPAC menu: "Party" button and the Anchor / Unanchor buttons do not overlap.
- A real 1.2.0 world opened with 1.3.0 (backup first): claims, team claims, party screen numbers.
- `anchorBasedClaiming = true`: place an anchor, team claim in range, unanchor with `confirm`, a member leaves.
- `overLimitEnforcement = false` on a team above its limit: no messages, party screen without countdown; back to
  `true`: warning with the full grace time.
- The 1.2.0 checklist (party screen, FTB Teams sync) in the git history of this file still applies.

## Usage
Delegated: Opus T1 (long, counts 2); Sonnet T2 (1), T4 (1), T3 (long, 2, in two sessions), T7 (1). Opus 2 of 7.

## Release state
- Pushed: `v1.3-dev` at origin (first push, brings upstream's history along). Not merged into `fabric-port` / `main`.
- CurseForge (project 1704256), 2026-10-13: both files uploaded as **Release** (owner: "as release and not beta"), after a second full test pass on the unchanged jars: NeoForge file 9151608 (no relations), Fabric file 9151657 (Fabric API + Forge Config API Port required, FTB Teams (Fabric) optional). The tool's slug lookup answered 404 for `forge-config-api-port` and `ftb-teams-fabric` again; the owner approved overriding the check once (plan marked resolved by hand with a note, slugs confirmed by project id 547434 / 438497). Not done: project description / summary on the website; `CURSEFORGE.md` still says "Suggested release type: Beta".
- Graphify: not updated (`graphify-out/` exists only in the main checkout, untracked, state of 1.1.0).
- 1.2.0 stays as it is: branch `fabric-port`, jars and CurseForge files 9106819 / 9108825 untouched.
