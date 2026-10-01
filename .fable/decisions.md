# Decisions (one line each, with reason)

- `.fable/` lives in `OPAC_PartyClaims/` (the project being changed), not in the Nytheria instance — board belongs to the code it tracks.
- Upstream reference clones kept in session scratchpad, not in the repo — avoids polluting the project; re-clonable.
- Loom 1.11.8 + Gradle 8.14.5 instead of upstream 1.8-SNAPSHOT/8.10 — old loom plugin markers no longer resolvable; 1.11.x is newest line on Gradle 8 + Java 21.
- Version string `0.31.6+teamclaims.1` — semver build metadata keeps other mods' OPAC version ranges matching.
- Tasks run sequentially (one Gradle build dir; parallel builds on half-edited Common give false failures).
- Team Claims login handling deferred 1 tick after Fabric JOIN — JOIN fires before OPAC's own login hook; legacy NeoForge event fired after it.
- Overhead stays in mode-agnostic PlayerClaimInfo.getClaimCount (as legacy): leaks into native PARTY mode only if `partyOwnedClaims=true`, which a Team Claims server keeps off — documented limitation, not fixed.
- Leaving a party keeps legacy behavior (leaver's team claims become their personal claims) — vision was confirmed 1:1; transfer-to-owner listed as recommendation only.
- No migration of old `config/opacteamclaims/teams` JSONs to per-world storage — fork never ran in production on Fabric; configs are rebuilt from parties on start.
- Boot test uses Fabric gametest headless server (-Dfabric-api.gametest), not a normal dedicated server — avoids accepting the Minecraft EULA on the user's behalf; run-server eula stays the user's step.
- Null profile cache on GameTestServer is worked around by a mixin inside the gametest mod, not by patching OPAC — it cannot happen on real servers, and Common should stay a minimal delta to upstream.
- CORRECTION to earlier decision: overhead does not 'leak' into PARTY mode — upstream uses one pool per party owner by design; limitation 2 in report replaced by a startup WARN explaining the shared pool.
- Leave/kick now transfers team claims to the party owner (user chose this in phase 2); disband unchanged; no give-back on rejoin.
- Release version `1.0.0+opac.0.31.6`: user wants v1.0.0; build metadata keeps the upstream OPAC base visible; 1.0.0 still satisfies other mods' `>=` ranges on mod id openpartiesandclaims.
