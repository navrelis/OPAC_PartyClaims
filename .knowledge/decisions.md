# Decisions (one line each, with reason)

- Earlier session decisions (loom 1.11.8 + Gradle 8.14.5, per-world team JSON, transfer-on-leave, gametest boot test, ...) live in git history of `.fable/decisions.md` (commit baseline) and stay valid unless overridden below.
- Upstream base stays v0.31.6 `c0d97b37` — still HEAD of upstream `1.21` branch on 2026-10-01; no newer 1.21.1 code exists.
- Team Claims logic moves to Common with thin per-loader adapters — 7/11 classes are already loader-agnostic; one implementation for Fabric + NeoForge.
- NeoForge built with upstream NeoGradle 7.0.181 / NeoForge 21.1.168 (user choice) — least divergence from upstream.
- Optional features picked by lead: forceload grace period + `/teamclaims convert`; not pooled-budget (redesign of core budget logic, high risk) and not activity log (low value).
- Status board renamed `.fable/` -> `.knowledge/` (session convention).
