# Plan — v1.2.0: FTB Teams sync, party screen, separate private/team budgets

Status: open / in progress / in review / done. Size S/L, difficulty E/H.
Parallel tasks never share files; a parallel task runs in its own git worktree. Builds only while no agent edits the same checkout.
(v1.1.0 plan: git history of this file.)

| # | Task | Size/Diff | Model | Depends | Status | Definition of done |
|---|------|-----------|-------|---------|--------|--------------------|
| 0 | Analysis, FTB Teams API research, questions, branch `v1.2-dev` | – | lead (+Sonnet research) | – | done | requirements.md, plan.md |
| 1 | Separate budgets: private limit = OPAC limit over private claims only; team pool limit by member count (claims + forceloads, config); client shows the numbers of the used sub-config; party removal converts to private within room, excess unclaimed; over-limit grace (warning, 1 week, auto-unclaim newest first); `/teamclaims info`/`convert` updated; lang (en) | L/H | Opus (main checkout) | 0 | done | private and team limits independent in both directions; grace persisted + tested with a test clock; existing + new gametests green on both loaders; upstream delta marked |
| 2 | Party screen: key `O` (not `P`: vanilla Social Interactions) + button in main menu; create/invite/accept/decline/kick/rank/transfer/rename/leave/disband via existing commands; new packets for "invites sent to me" + decline. Lang keys only reported (no lang file edits) | L/E | Sonnet (worktree, parallel to 1) | 0 | done (on worktree branch, merge after T1) | both loaders build; screen states per spec; no file shared with T1; lang key list returned |
| 3 | FTB Teams <-> OPAC party sync (optional, `ftbTeamsSync`): events + ~1 s reconciliation, loop guard, first-sync rule (OPAC wins), rank mapping, name sanitising, offline players; dev/test runs with and without FTB Teams | L/H | Opus | 1, 2 | open | every operation mirrored both ways in gametests with FTB Teams loaded; all tests also green without FTB Teams; no FTB class loaded when absent |
| 4 | Lang + screen integration: en keys of T2, `de_de.json` for all fork strings (Team Claims, sync, screen); party screen shows private/team numbers + over-limit countdown; gametests for the invite packets | L/E | Sonnet | 1, 2 (parallel to 3 if files disjoint, else after 3) | open | no raw lang key on screen; numbers match `/teamclaims info`; tests green both loaders |
| 5 | Release prep: version `1.2.0+opac.0.31.6`, README + CURSEFORGE text, leftover scan, clean build, jars to `ExportedJars/v1.2.0/` | S/E | Sonnet | 3, 4 | open | both jars built, metadata correct, docs updated |
| 6 | Final acceptance: full clean build both loaders, all test runs, leftover scan, report.md, push | – | lead | 5 | open | report.md; pushed |
