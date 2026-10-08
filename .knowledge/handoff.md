# Handoff — OPAC Team Claims v1.2.0 (stopped 2026-10-09 on user request)

The session was stopped mid-way. Nothing is running any more (the T3 agent, its Gradle build and its test server were
stopped). This file is everything the next session needs. Read it, then `.knowledge/requirements.md`, `plan.md`,
`decisions.md`, `log.md`.

## 1. Prompt for the next session (paste as the first message)

```
# Role
You are Opus 5.5, acting as lead developer and project lead. You plan, delegate, supervise and verify. You never write code and never create, edit or delete project files yourself. Subagents do all implementation.

You may do these things yourself:
- write and update the files in `.knowledge/`
- run builds, tests, linters and git commands
- run the Graphify update

You are responsible for the final result.

# 1. Understand
- Read `.knowledge/handoff.md` first, then the other files in `.knowledge/`. Do NOT read or update Graphify (the user said: no need for graphify).
- Read the files the remaining tasks change or depend on yourself. Check APIs online for the exact versions. Never guess what you can look up.

# 2. Ask once
All questions of this project are answered (see `.knowledge/requirements.md` and the handoff). Ask only if something new and blocking comes up, all in one message, each with a default.

# 3. Status board: `.knowledge/`
Keep it short. It shows status, not history. It is committed and pushed with the project. Update it after every delegation and every review. After a context reset, read it first.
Files: `requirements.md` (user answers and constraints), `plan.md` (tasks with definition of done, dependencies, status), `decisions.md` (one line per decision, with its reason), `log.md` (one line per delegation or review).
Never put secrets, subagent instructions or code in these files.

# 4. Plan
Small tasks, each with a testable definition of done, order and dependencies, what can run in parallel. Saved in `plan.md` (already there, continue it).

# 5. Delegate
- Rate each task: size (short/long) and difficulty (easy/hard).
- Opus 5.5 for hard or risky work (architecture, complex logic, difficult debugging). Sonnet 5 for easy work with a clear spec, even if long. Split long tasks.
- Up to 4 subagents in parallel: at most 2 Opus 5.5 and at most 2 Sonnet 5. Default 1 Opus 5.5 plus 1–2 Sonnet 5. Don't fill slots just because they're free.
- Subagents running in parallel must never touch the same files.
- Subagents don't commit or push. You do that after review.
- Every instruction contains: goal and context; affected files with full paths; constraints (versions, code conventions, no work outside the task, no new dependencies unless approved); definition of done; return format (changed files, short summary, commands and tests run with results, open points).

# 6. Verify
- Read the full diff of every change; read the whole file when core logic changes.
- Check against the definition of done, the plan and the requirements: correctness, edge cases, error handling, side effects, consistency, security, readability.
- Run the build, the existing tests and the linter — only while no subagent is editing files in that checkout.
- Trust what you read and what runs, not the subagent's summary.
- Put anything that can't be tested automatically (in-game behaviour, UI) into a manual test checklist.
- If you find a problem, send a precise correction (what, where, why, expected result), then verify again. Never fix anything yourself, not even one line.
- Sonnet 5 fails three corrections in a row -> note it in `decisions.md`, reassign to Opus 5.5 with the full error history. Opus 5.5 fails three times -> rethink the task.

# 7. Git and GitHub
- Commit each accepted task separately with a clear message. Stage only the files of that task plus `.knowledge/`. Never `git add .` / `git add -A` while other subagents are working.
- Push when a working state is reached. Only push code that builds and passes the existing tests. Never force-push, never push secrets or local config.
- Branch: `v1.2-dev` (pushed to origin github.com/navrelis/OPAC_PartyClaims). Do NOT merge into `fabric-port` or `main` unless the user says so.

# 8. Final acceptance
1. Check the whole system end to end: full build and all test runs pass, no leftover TODOs, debug code or unused files.
2. No Graphify update (user).
3. Write `.knowledge/report.md`: what was implemented per requirement, changed files and why, key decisions, checks and tests run, manual test checklist, known limitations and recommendations.
4. Final commit and push.
5. Give the user the report.

# Task for this session
Finish OPAC Team Claims v1.2.0 from `.knowledge/handoff.md`: merge and verify T4, finish/review T3 (FTB Teams <-> OPAC party sync, saved on branch `wip/t3-ftb-sync`), then T5 (follow-ups + release prep) and T6 (final acceptance).
```

## 2. What the user asked for (original task, v1.2.0)
1. Combine FTB Teams with Open Parties and Claims so a player only creates ONE team: creating/inviting/joining/leaving
   etc. in either mod does the same in the other.
2. A screen opened by a hotkey instead of typing `/oparties ...`.
3. Separate private claims and team claims: 500 private per player; team claims from 2 members = 500, +25 per further
   member.
4. "Bulletproof" when a team shrinks: warning to unclaim, after 1 week real time the most recently claimed team claims
   are unclaimed automatically until the team is within its limit.
All answers/defaults the user confirmed are in `requirements.md`. FTB Teams 2101.1.11, FTB Chunks not installed, sync
optional, OPAC wins on conflicts, no Graphify, no CurseForge upload, never touch the Nytheria modpack instance.

## 3. State of the repository (main checkout `F:\Coding\OPAC_PartyClaims`)
| Branch | Commit | Content | Verified by lead |
|---|---|---|---|
| `v1.2-dev` (checked out, = origin) | 94f7b1b + board commit of this handoff | T1 budgets + T2 party screen | yes: both builds, Fabric 60/60, NeoForge 60/60 |
| `worktree-agent-ade397285ba0d2db6` (worktree `.claude/worktrees/agent-ade397285ba0d2db6`) | 90fbb77 | T4: en/de lang, budget lines on the screen, packet 53 | diff read + accepted; NOT yet built/tested by the lead, NOT merged |
| `wip/t3-ftb-sync` (local only, not pushed) | 3bf69b4 | T3 work in progress of the stopped agent | NO — never reviewed, never built by the lead |
| `worktree-agent-a3a68a585aa0969e7` | aa70489 | T2, already merged | can be removed (`git worktree remove`, branch delete) |
| `fabric-port` | 994f95b | v1.1.0 release branch (GitHub default) | untouched |

Untracked and to stay untracked: `graphify-out/`, `ExportedJars/opacteamclaimslogo.png`.

## 4. Task status
| # | Task | Status | Next step |
|---|---|---|---|
| 1 | Separate private/team budgets + over-limit grace | done, on `v1.2-dev` | – |
| 2 | Party screen + hotkey `O` + packets 50–52 | done, on `v1.2-dev` | – |
| 4 | Lang (en + de), budget numbers on the screen (packet 53), record move | accepted on its branch | `git merge --no-ff worktree-agent-ade397285ba0d2db6` into `v1.2-dev`, then run the full verification (section 7), commit board, push |
| 3 | FTB Teams <-> OPAC party sync | stopped mid-work, saved on `wip/t3-ftb-sync` | see section 5 |
| 5 | Follow-ups + release prep | open | see section 6 |
| 6 | Final acceptance + `report.md` + push | open | – |

Order: merge T4 first (files disjoint from T3: T4 = `client/**`, `common/packet/**`, `common/parties/party/ReceivedPartyInvite.java`, `lang/*.json`), verify, push. Then T3. T3 and T5 share files (lang, gametest entrypoints), so T5 runs after T3.

## 5. T3 — FTB Teams sync: what exists and how to continue
The agent (Opus) was stopped while writing the loader test wrappers; a build + Fabric test run it had just started was
killed, so it is unknown whether the code compiles or any sync test passes.

On `wip/t3-ftb-sync` (diff: `git diff v1.2-dev wip/t3-ftb-sync`):
- `Common/src/main/java/xaero/pac/teamclaims/ftbsync/`: `FtbTeamsSync` (entry, queue, intervals, isolation — no FTB class referenced), `FtbSyncEngine` (the only class + `FtbSyncEvents` that touch FTB; three-way merge per pair against a stored baseline, OPAC wins without a usable baseline), `FtbSyncStore` (SavedData of the overworld: pairs + baselines), `FtbSyncOpac` (all writes to OPAC parties incl. the side effects of the party commands), `FtbSyncEvents` (TeamEvent listeners that only enqueue and never throw), `FtbSyncNames` (deterministic name sanitising), `FtbSyncCommands` (`/teamclaims ftbsync status|resync`).
- Hooks/edits: `TeamClaimsIntegration` (+ handler methods), `ServerParty` (invite / un-invite / rank hooks, marked `[Team Claims]`), `TeamClaimsBridgeHandler`, `TeamClaimsCommon` (lifecycle), `TeamClaimsCommands`, `TeamClaimsServerConfig` (`ftbTeamsSync`).
- Build: root `build.gradle` (repos `maven.ftb.dev/releases`, `maven.architectury.dev`), `gradle.properties` (`ftb_teams_version=2101.1.11`, `ftb_library_version=2101.1.30`, `architectury_version=13.0.8`), compile-only deps switched from curse.maven to those mavens in `Common`/`Fabric`/`NeoForge` `build.gradle`; new source set `gametestFtb` (`Fabric/src/gametest/ftb`, `NeoForge/src/gametest/ftb`) and new runs `:Fabric:runBootTestFtb` (run dir `run-boottest-ftb`) and `:NeoForge:runTeamClaimsGameTestFtb`; `.github/workflows/build.yml`; `fabric.mod.json` + `neoforge.mods.toml` optional dependency on `ftbteams`.
- Tests: `Common/src/gametest/java/xaero/pac/teamclaims/gametest/TeamClaimsFtbSyncTestCases.java` (27 test methods) + wrappers under the two `gametest/ftb` folders (wrappers were being written when stopped — check they are complete and list every test).
- Lang keys `gui.xaero_pac_team_claims_ftbsync_*` are used in code but NOT in any lang file (by design: T5 adds them). Collect them with `grep -rho "ftbsync_[a-z_]*" Common/src/main/java/xaero/pac/teamclaims/ftbsync | sort -u` (check how the key prefix is built).

How to continue (recommended): delegate to Opus 5.5 in the main checkout with `wip/t3-ftb-sync` checked out as a
working tree to finish (or `git merge --squash wip/t3-ftb-sync` into `v1.2-dev` AFTER T4 is merged, leave it
uncommitted, and let the agent continue there). Give the agent the full requirements below, tell it the code is an
unverified draft of a stopped agent that it must first read and build, then complete. Then review the whole diff
yourself, run all four test runs, commit, push.

T3 requirements (the original delegation, condensed — all still valid):
1. Activation: `ftbTeamsSync` (default true, read once at server start); runs only with Team Claims active AND FTB Teams installed AND the option on. Without FTB Teams no FTB class may be loaded; the 60 existing tests must pass without FTB on the classpath.
2. Persisted mapping OPAC party <-> FTB party team that survives restarts and copes with a vanished side.
3. Mirrored both ways, online and offline where possible: create (with name), disband/remove, join, leave, kick, owner transfer, name (sanitised deterministically, never looping), ranks (Owner<->OWNER; OPAC ADMIN/MODERATOR -> FTB OFFICER; FTB OFFICER -> OPAC MODERATOR; CLAIMER/MEMBER <-> MEMBER; an OPAC ADMIN/CLAIMER must not be degraded by a later reconciliation — only change the OPAC rank when the FTB side changed class), invites (exist in both, withdraw/decline removes both, accepting in either joins both; ideally no second chat prompt). Not synced: allies, FTB colour/description, party chat.
4. OPAC -> FTB via the queued party events + new marked hooks (invite, un-invite, rank); FTB -> OPAC via `TeamEvent` listeners (only enqueue, never throw) and a reconciliation every 20 ticks for what has no event (invites, declines, promote/demote). Queue applied at tick end, in order.
5. No feedback loops: re-entrancy guard AND idempotent apply; full reconciliation at server start, after a player's first FTB login, and once a minute.
6. First sync: a party existing in one mod only is created in the other; conflicting membership -> OPAC wins. Operations that cannot be mirrored (FTB `max_party_size`, OPAC `maxPartyMembers`, player without an FTB personal team yet, FTB owner who would have to leave their party, permission plugin): never crash, resolve by OPAC-wins or keep as pending work that is retried, one rate-limited WARN, localized message to the acting player. The report must list what ends "pending".
7. A party created from FTB becomes a full OPAC party with Team Claims set up as with `/oparties create <name>`; FTB-side membership changes drive the existing Team Claims logic through the normal OPAC party paths.
8. `/teamclaims ftbsync status` and `resync` (permission level 2), localized.
9. `ftbteams` as optional/suggested dependency in both loader metadata files; nothing from FTB Teams / FTB Library / Architectury inside the jars.
10. Default runs (`:Fabric:runBootTest`, `:NeoForge:runTeamClaimsGameTest`) stay WITHOUT FTB Teams and stay green; the two FTB runs run ALL existing tests + the sync tests with the same fail gating; CI runs them too.
11. Tests for every operation in each direction, stability rule for ranks, no feedback loop, first sync incl. conflict, offline members, mapping save/load round trip, size-limit conflict, Team Claims interplay (FTB-side removal hands team claims to the owner and shrinks the team limit).
Files T3 may touch: `Common/src/main/java/xaero/pac/teamclaims/**`, `.../common/server/claims/TeamClaimsIntegration.java`, `.../common/server/parties/**` (marked hooks), `.../common/mods/**`, the two loader adapters + mod metadata, all `build.gradle` files + `gradle.properties` + `.github/workflows/build.yml`, the gametest folders. Not: `client/**`, `common/packet/**`, `common/parties/**`, lang files, README, `.knowledge/`.

FTB Teams 2101.1.11 facts (verified from source by the research agent; source: github.com/FTBTeam/FTB-Teams tag
`v2101.1.11`, branch `1.21.1/main` — re-clone it into the new session's scratchpad for reference):
- Mod ids `ftbteams`, needs `ftblibrary` (>= 2101.1.30) and `architectury` (13.0.8+). Fabric artifacts are intermediary-mapped, NeoForge/common Mojang-named.
- Public API (`dev.ftb.mods.ftbteams.api`) has almost no writes: `TeamManager.createPartyTeam(ServerPlayer, ...)` (online only), `Team.setProperty` (no event, no client sync -> call `syncOnePropertyToAll`). Writes need impl classes: `TeamManagerImpl.INSTANCE.createParty(UUID, @Nullable ServerPlayer, name, desc, color)`, `PartyTeam.join(@Nullable ServerPlayer, GameProfile)`, `leave(UUID)`, `kick(source, profiles)`, `transferOwnership(source, profile)`, `forceDisband(source)`, `invite(ServerPlayer, profiles)` (needs an online inviter, sends chat buttons).
- A personal team exists only after the player logged in once with FTB Teams installed.
- Events (`TeamEvent.*`, Architectury, server thread, fired AFTER the change; `CREATED` for a party fires BEFORE the creator is added and only for an online creator): CREATED, DELETED, PLAYER_CHANGED (also offline), PLAYER_JOINED_PARTY (online only), PLAYER_LEFT_PARTY, OWNERSHIP_TRANSFERRED, PROPERTIES_CHANGED (fires even when nothing changed), ADD_ALLY/REMOVE_ALLY, PLAYER_LOGGED_IN. NO event for invite, decline, promote, demote. A throwing listener crashes FTB's own code path.
- `getRankForPlayer` returns INVITED for everyone when the team is `FREE_TO_JOIN`.
- No player-facing disband in FTB (party vanishes when the last member leaves, or `force-disband`).
- Config `ftbteams-server.snbt`: `max_party_size`, `limited_lives`.
- No existing OPAC<->FTB sync mod was found; upstream OPAC only has the read-only `PlayerFTBPartySystem`.

## 6. T5 — follow-ups + release prep (Sonnet, after T3 and T4 are merged)
- Lang: add every `gui.xaero_pac_team_claims_ftbsync_*` key to `en_us.json` and `de_de.json` (German informal "du"; keep placeholders and the `[Team Claims]` prefix; `de_de.json` keeps the key order of `en_us.json`).
- Over-limit chat texts: show days ("7 d 0 h" instead of "168 h 0 min") and mention `/teamclaims convert topersonal` and "get a second member" as ways out (keys `gui.xaero_pac_team_claims_over_limit_*`, helper `hoursOf/minutesOf` in `TeamClaimManager`).
- Gametests for packets 50–53 (invites request/list/decline incl. rate limit, budget packet content) on both loaders.
- `.gitignore`: add `run-boottest-ftb/` if T3 keeps that run dir.
- Version `1.2.0+opac.0.31.6` in `gradle.properties`; README (separate budgets, new options, over-limit grace, disband rule, party screen + key, FTB Teams sync, how to run the tests with/without FTB) and `ExportedJars/v1.2.0/CURSEFORGE.md`; leftover scan (TODO/FIXME/System.out, unused lang keys); clean build; both jars to `ExportedJars/v1.2.0/` (jars are not committed).

## 7. Commands
- Full verification without FTB (from the repo root, Git Bash): `./gradlew :Fabric:build :NeoForge:build :Fabric:runBootTest :NeoForge:runTeamClaimsGameTest` -> expect "All 60 required tests passed" twice (more after T5). Takes 5–10 min cold.
- With FTB (after T3): `./gradlew :Fabric:runBootTestFtb :NeoForge:runTeamClaimsGameTestFtb`.
- Jar check: no `dev/ftb`, `dev/architectury` or gametest entries in `Fabric/build/libs` / `NeoForge/build/libs`.
- Never run two Gradle invocations in the same checkout at once; never build while an agent edits that checkout.

## 8. Things that cost time this session (avoid them)
- A subagent started with `isolation: "worktree"` gets a worktree based on the OLD `main` root commit (e901c19), not on the current branch. Put the wanted base commit in the instruction and allow `git reset --hard <commit>` on the agent's own throwaway branch as its first step.
- `python` is not on the Git Bash PATH (use `py`, or sed/PowerShell).
- The lead's shell working directory can jump into a worktree after reading there — use absolute paths / `git -C`.
- Git prints many CRLF warnings on this repo; filter them (`2>/dev/null`) to keep output readable.
- Other Claude sessions run on this machine with their own Gradle daemons: only stop Java processes whose command line contains this repo's path.
- The default party-screen key is `O`, not `P` (vanilla binds `P` to Social Interactions).

## 9. Decisions and behaviour the user should hear about in the final report
- Solo teams: a team with one member has team limit 0 (`teamClaimsMinMembers = 2`). On the first start after the update such teams get the one-week warning and then lose their team claims unless a second member joins, they convert the claims (`/teamclaims convert topersonal`) or the admin sets `teamClaimsMinMembers = 1`.
- Disband: team claims become private claims of whoever made them only within that player's private room; the excess is unclaimed at once, newest first (otherwise disbanding would turn a team pool into extra private land). The disband confirmation says so.
- The deadline is cancelled as soon as the team is back within its limit; re-inviting a member therefore resets it.
- Third-party API users still see `getClaimCount()` including a player's own team claims.
- The same rule applies to team forceloads (newest are un-forceloaded, the claim stays).

## 10. Manual test checklist so far (needs two clients; cannot be automated)
1. Key `O` opens the party screen, `O` closes it when no text field is focused; the key shows in Controls; "Party" button in the OPAC menu (apostrophe key).
2. No party: create with/without a name (invalid characters turn the box red); a second player's invite appears within ~2 s; Accept joins, Decline removes it.
3. In a party as owner / admin / moderator / member: Kick, rank button (cycles all ranks), Make owner (confirm), Invite (text + tab-list suggestions), Cancel invite, Rename, Leave / Disband (confirm) — buttons appear exactly when the server would allow the action.
4. Budget lines: private line always; team line in a team; yellow hint when the team has fewer than 2 members; red countdown while over the limit ("d h" / "h min" / "min"), disappears after unclaiming.
5. Window at 320x240 (largest GUI scale): nothing overlaps, at least 3 member rows visible.
6. German language: no raw keys, long confirmation texts wrap.
7. Budgets: with 500/500 private claims, team claims still work and vice versa; `/teamclaims info` matches the screen.
8. Kick a member of a team that uses its whole limit -> warning to all online members, again at login and daily; after the deadline (set `overLimitGraceHours` small for the test) the newest team claims are gone.
9. Disband with more team claims than private room -> chat line with kept/unclaimed counts.
10. After T3: every party action done in OPAC shows in FTB Teams and the other way round (create, invite, accept, leave, kick, transfer, rename, rank, disband); server without FTB Teams behaves as before.
