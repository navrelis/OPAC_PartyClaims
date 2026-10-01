# Requirements (session 2026-10-01)

Earlier state: Fabric-only port of upstream OPAC v0.31.6 (`1.21` branch, `c0d97b37`) + Team Claims layer, done in a previous session (old board `.fable/`, superseded by this folder).

## Goals
1. Minecraft **1.21.1 Fabric** (highest priority) and **1.21.1 NeoForge**, one shared codebase. Old MinecraftForge: out of scope.
2. Optimise and harden Team Claims (performance, robustness, dead code).
3. New features (user-selected): territory enter/leave messages; `/teamclaims info` + `list`; Team Claims server config file; team roles for claiming (who may claim/unclaim/forceload team land). Lead-picked (user delegated the choice): forceload grace period; `/teamclaims convert` (personal <-> team claims in an area).

## Constraints / defaults confirmed by user
- Versions: MC 1.21.1; Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, FCAP 21.1.6; NeoForge via upstream NeoGradle setup, compile target 21.1.168, runtime range `[21.0.0-beta, 21.2)` (newest 21.1.252 must work).
- Mod id stays `openpartiesandclaims`; version `1.1.0+opac.0.31.6`; client + server must run the same version (protocol change allowed).
- Existing world data (claims, `<world>/data/opacteamclaims/**`, `opacteamclaims_data`) must keep loading.
- No new dependencies. Upstream OPAC behaviour unchanged when Team Claims is not used; Common delta to upstream stays minimal and marked `[Team Claims]`.
- Git: baseline commit of the existing port, then one commit per accepted task on `fabric-port`, pushed to `origin` (github.com/navrelis/OPAC_PartyClaims). Never main, never force-push.
- Release jars (`ExportedJars/`) not committed; `.fable/` replaced by `.knowledge/` (legacy sources remain in commit `e901c19`).
- HARD BOUNDARY (from earlier session, still valid): never touch the Nytheria modpack instance; all work stays inside this repo.
- Dev server `run-server/eula.txt=true`, `online-mode=false` were explicitly authorised by the user earlier.
