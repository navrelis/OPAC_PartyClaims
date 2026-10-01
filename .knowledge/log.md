# Log (one line per delegation / review)

- 2026-10-01 | Mapping | Sonnet (Explore, read-only) | full report: Team Claims inventory, loader audit (Fabric usage only in TeamClaimsInit/ClientInit/2 network calls), upstream platform pattern, upstream NeoForge module, 25+ perf/robustness hotspots, feature surface, tests.
- 2026-10-01 | Baseline | lead | `gradlew :Fabric:build` green on the uncommitted port; committed as baseline.
- 2026-10-01 | T1 move Team Claims to Common | Opus | delegated (main checkout).
- 2026-10-01 | T2 restore NeoForge + housekeeping | Sonnet | delegated (isolated worktree).
- 2026-10-01 | T1 review | lead | ACCEPTED: moved classes differ only in accessor names (TeamClaimsInit->TeamClaimsCommon); bridge handler identical; dead payload/client cache removed (nothing read it); tests split into Common/src/gametest shared bodies + Fabric wrappers, bodies identical. Own run: :Fabric:build green, runBootTest 9/9, jar 0 gametest entries.
