# Decisions (one line each, with reason)

- Earlier session decisions (loom 1.11.8 + Gradle 8.14.5, per-world team JSON, transfer-on-leave, gametest boot test, ...) live in git history of `.fable/decisions.md` (commit baseline) and stay valid unless overridden below.
- Upstream base stays v0.31.6 `c0d97b37` — still HEAD of upstream `1.21` branch on 2026-10-01; no newer 1.21.1 code exists.
- Team Claims logic moves to Common with thin per-loader adapters — 7/11 classes are already loader-agnostic; one implementation for Fabric + NeoForge.
- NeoForge built with upstream NeoGradle 7.0.181 / NeoForge 21.1.168 (user choice) — least divergence from upstream.
- Optional features picked by lead: forceload grace period + `/teamclaims convert`; not pooled-budget (redesign of core budget logic, high risk) and not activity log (low value).
- Status board renamed `.fable/` -> `.knowledge/` (session convention).
- T4 runs before T3 in parallel: T3 (NeoForge adapter) only depends on the stable TeamClaimsCommon lifecycle API, which T4 must not change; avoids idle time.
- Worktree branch from T2 is reused for T3 and merged into fabric-port after T4 is committed — keeps main checkout stable for the running T4 agent.
- Team Claims config is its own SERVER toml (openpartiesandclaims-teamclaims-server.toml) registered from the Team Claims adapters — no new upstream hook, no collision with OPAC's server toml.
- T6 re-scoped: upstream OPAC already shows action-bar claim welcome messages; Team Claims extends them (team territory = one territory, per-player toggle) instead of adding a second, competing message system.
- T8 roles design: claim-role check in existing interceptClaim hook (it knows the subConfigIndex); unclaim/forceload roles via OPAC's official IClaimActionListenerAPI (addon register context) -> no new upstream hooks, forced/admin actions bypass naturally. Rank thresholds reuse OPAC PartyMemberRank (MEMBER<CLAIMER<MODERATOR<ADMIN) + OWNER; default MEMBER keeps current behaviour.
- Roles: claiming over an own-team team claim with a personal sub-config requires the unclaim level (otherwise unclaim role is bypassable).
- A forceloaded new team claim (only convert creates one) needs claim AND forceload role — keeps the forceload role non-bypassable.
- Fork metadata: sources/issues URLs point to github.com/navrelis/OPAC_PartyClaims (fork bugs must not go to upstream); homepage stays upstream OPAC.
