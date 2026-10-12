# Requirements (session 2026-10-12, v1.3.0)

Earlier state: `1.2.0+opac.0.31.6` on `fabric-port` (`50d1919`) — fork of OPAC v0.31.6 (`c0d97b37`), MC 1.21.1, Fabric + NeoForge, Team Claims layer in `Common/src/main/java/xaero/pac/teamclaims/`. Requirements of v1.2.0: git history of this file.

## Why
Xaero's World Map 1.47.0 crashes on every right-click on the map (`NoSuchMethodError IClientClaimsManagerAPI.usingAnchorBasedClaiming()`): it calls client API that upstream OPAC added in 0.32.x and the fork lacks.

## Goals (owner)
1. Fork contains upstream OPAC 0.32.7 (`thexaero/open-parties-and-claims`, branch `1.21`, `75ce21d6`) completely (API, packets, config, commands, save format, lang) with Team Claims on top. Version `1.3.0+opac.0.32.7`.
2. Proof the crash is gone: automated linkage check of every `xaero.pac.*` class/method/field referenced by World Map 1.47.0 and Minimap 26.6.0 (NeoForge + Fabric 1.21.1 jars) against the built jars; if possible a dev-client run with World Map 1.47.0 (open map, right-click).
3. All existing tests green on both loaders, with and without FTB Teams (77 / 111), plus new tests: limits and budgets with the new upstream claim limit packet, anchors together with team claiming mode, loading a 1.2.0 world save.
4. README upgrade notes: server and all clients update together (network version changed); what happens to existing worlds.
5. Changelog, both jars in `ExportedJars/v1.3.0/`, `CURSEFORGE.md` updated.
6. Upload to CurseForge project 1704256, both loaders, Beta, with the owner's uploader in `F:\Coding\CurseForgeUpload` (dry run, remote dry run, review, upload). Token never printed. Only jars that passed every check. Nothing goes to project 1704237.
7. Final acceptance, Graphify update, `report.md`, push, one line in `F:\Coding\.knowledge\log.md`.

## Owner decisions / defaults
- Full port, own worktree and session. Working branch `v1.3-dev` off `50d1919`, pushed to origin; no merge into `fabric-port` / `main` without the owner's word. Never force-push.
- Behaviour of 1.2.0 stays: budgets, limits, over-limit auto-unclaim, FTB Teams sync, party screen. Anchor-based claiming keeps upstream's default (off) and must work together with team claims.
- No new shipped dependencies. Refactoring of the Team Claims layer allowed where the port needs it.
- Downloading the Xaero jars needs the owner's OK (ask once: file name, source, size).

## Standing constraints
- MC 1.21.1; Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, FCAP 21.1.6; NeoForge 21.1.168 (range `[21.0.0-beta, 21.2)`), NeoGradle 7.0.181, Gradle 8.14.5, loom 1.11.8, Java 21.
- Mod id `openpartiesandclaims`. Existing world data (claims, `<world>/data/opacteamclaims/**`, `opacteamclaims_data`) must keep loading.
- Upstream OPAC behaviour unchanged when Team Claims is disabled; Common delta to upstream minimal, additive, marked `[Team Claims]`.
- HARD BOUNDARY: never touch the Nytheria modpack instance; all work stays inside this repo.
