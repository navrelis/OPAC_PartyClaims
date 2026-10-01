# Plan — OPAC PartyClaims → Fabric 1.21.1 (Loader 0.19.5), base upstream v0.31.6

Status legend: open / in progress / in review / done. Tasks run sequentially unless noted (shared Gradle build dir).

| # | Task | Model | Status | Definition of done |
|---|------|-------|--------|--------------------|
| 0 | Analysis + question round | lead | done | requirements.md written |
| 1 | Skeleton rebase: branch `fabric-port`; Common/, Fabric/, CreateSupportCommon/, root gradle = upstream `c0d97b37`; remove NeoForge/, neoforge_docs/, other_mods/, run-*, NeoForge bats; loader 0.19.5 / fabric-api 0.116.x / FCAP 21.1.x; version `0.31.6+teamclaims.1`; stash old Team Claims sources under `.fable/legacy/` for reference | Sonnet | done | `gradlew :Fabric:build` green; tree == upstream except listed config edits |
| 2 | Platform port: `xaero.pac.teamclaims/**` NeoForge → Fabric API (events, payloads, tickets, env checks) + bridge file in Common + init from `OpenPartiesAndClaimsFabric`; adapt to 0.31.6 API (`api.v2.PlayerConfigOptions` etc.) | Sonnet | done | compiles; handler registered on OPAC addon register; no NeoForge imports |
| 3 | Re-fit the 12 Common hooks onto 0.31.6 code (claims manager, claim info counts, config set/sync/sub-config, sub-config delete guard, create-party name, config screen) | Opus | done | every `[Team Claims]` hook from fork.diff present or consciously replaced; build green; native party-claims paths untouched |
| 4 | Bug fixes: team JSON storage per-world; chat strings → lang keys (server-localized); anything found in review | Sonnet | done | build green, behavior list in report |
| 5 | Dev test setup: loom runs client1/client2/server with run dirs, start-all/stop-all for Fabric, README, CI workflow | Sonnet | done | `gradlew :Fabric:runServer` boots |
| 6 | Integration: dedicated server boot test with built jar + pack-equivalent deps, log check, leftover scan (TODO/debug/NeoForge refs), report.md | lead + Sonnet | done | clean boot, Team Claims init lines in log |

## Analysis facts (do not re-derive)
- Fork base = upstream `1.21` commit `53671d55` (v0.25.8). Target = upstream HEAD `c0d97b37` (v0.31.6).
- Fork delta: 12 Common files patched (all marked `[Team Claims]`), new bridge `Common/.../server/claims/TeamClaimsIntegration.java`, new `NeoForge/.../xaero/pac/teamclaims/**` (10 files, ~2100 LOC). Original state is git commit `e901c19`.
- High-churn hook targets between base and HEAD: ServerClaimsManager (+416/-132), PlayerConfigOptions (moved to `api/v2`), PlayerConfigSynchronizer (+292), PlayerConfig (+216), ClaimsManagerSynchronizer (+193), PlayerConfigScreen (+184).
- Upstream ≥0.31 has native party claims (`partyOwnedClaims`, ClaimingModes PLAYER/PARTY) — must stay functional and off by default.
- Nytheria pack: Loader 0.19.5, fabric-api 0.116.17, FCAP 21.1.6, Xaero minimap 26.5.0 / worldmap 1.46.0, FTB Teams present, `primaryPartySystem=default`.
- Scratch (session scratchpad): `opac-upstream` (HEAD clone), `opac-base` (worktree @53671d55), `fork.diff` (full fork delta).
- Verification trick: `diff -r --strip-trailing-cr <scratch>/opac-upstream/<mod> <project>/<mod>` must show only Team Claims delta.

## Phase 2 (user request 2026-09-20, after report)
| # | Task | Model | Status | Definition of done |
|---|------|-------|--------|--------------------|
| 7 | partyOwnedClaims coexistence: analyse whether overhead really mis-counts in native PARTY mode (owner's PLAYER and PARTY numbers come from the same playerInfo pool upstream); fix if a real inconsistency exists, otherwise startup WARN + README note | Opus | done | reasoning documented; build + bootTest green |
| 8 | On leave/kick: leaver's team claims transfer to party owner (stay team claims, keep forceload), before the leaver's team sub-config is removed; disband unchanged | Opus (same agent as 7) | done | tracking/overhead consistent; build + bootTest green |
| 9 | Fake-player gametests for team logic (claim, budget reject, teammate unclaim/forceload, non-admin edit reject, admin propagate, delete guard, leave→transfer); dev run-server: `eula=true` (user authorised explicitly) + `online-mode=false` | Sonnet | done | all gametests pass headless; prod jar still clean |
| 10 | Integration + report update | lead | done | clean build, bootTest, report.md phase-2 section |

## Phase 3 (user request: v1.0.0 release export)
| # | Task | Model | Status | Definition of done |
|---|------|-------|--------|--------------------|
| 11 | Version → `1.0.0+opac.0.31.6`, display name "Open Parties and Claims: Team Claims", clean build + 9 gametests, `ExportedJars/v1.0.0/` with jar (`opac-team-claims-fabric-1.21.1-v1.0.0.jar`) + `CURSEFORGE.md` (summary ≤256 chars, categories, detailed description; text supplied by lead) | Sonnet | done | jar + md in folder, summary length verified, tests green |
