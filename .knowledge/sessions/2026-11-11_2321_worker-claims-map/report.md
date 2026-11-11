# Report worker claims-map

## Task 1: Claims tab map (terrain, overlay, zoom, allies)
Commits: f7db848f (work), 7d72023d (merge of claude/opac-1.5-unified-menu, real Overview/Settings, TEMP stubs gone; the stub commit 4ca75ba2 is still in history, content replaced).

Files
- `hub/ClaimsTabScreen.java`: one map texture instead of per-cell fills, borders, zoom buttons + wheel, ally detection, `removed()` frees the texture. Fields `gridLeft/gridTop/fit/centerX/centerZ` unchanged (two-player harness).
- `hub/HubGridFit.java`: `fit(side, zoom)`, 3 steps = chunk 6/12/24 px (default 12), radius = what fits (max 24). Old `fit(side)`, PREFERRED_MIN_RADIUS, MAX_CELL removed.
- `hub/ClaimCategory.java`: `ALLY`, `classify(..., ally)`, `isAllyOwner(...)`.
- New `hub/map/`: `TerrainShading` (vanilla map shading), `TerrainSchedule` (8 chunks/tick, 100-tick refresh, nearest first), `ClaimOverlay` (alpha 0x78 blend, 1 px edge rule), `ChunkTerrainSampler` (WORLD_SURFACE or ceiling scan from playerY+8, water depth), `TerrainCache` (per-chunk int[256] + version), `ChunkMapImage` (one NativeImage/DynamicTexture, 16 px per chunk, repaints only changed chunks).
- Lang en_us/de_de after `gui.xaero_pac_hub_centre_tooltip`: zoom_out/in tooltips, legend_ally ("Ally"), tt_ally.
- Tests: `TeamClaimsMapTestCases` (+Fabric/NeoForge wrappers, 5 tests); `TeamClaimsHubTestCases` fit test rewritten (renamed `gridFitNeverOverflowsAndFollowsTheZoomStep`, wrappers renamed) and classify calls got the ally arg.

Rendering: terrain per loaded chunk (no loading) -> CPU array; whole grid composited into one texture (claim colour blended at alpha 0x78 over terrain, opaque over checkerboard where no terrain); one blit per frame, only changed chunks repainted and uploaded. Edges, badges, forceload dot, player marker, selection, hover are fills on top. Linear filter when chunk < 16 px.

Results: `:Fabric:build :NeoForge:build` green (merged state); `:NeoForge:runTeamClaimsGameTest` All 152 passed; `:Fabric:runBootTest` All 147 passed (no xaerolib problem here).

Open points
- NOT looked at in-game (no client run): alpha 0x78, border colour (claim colour at 40%), linear filter and zoom-row layout are untuned guesses.
- Ally detection is name based: the client only knows ally id/name/default name, not members. Matches party-owned claims by party name, and an ally party's owner by "<name>'s Party" default name. Non-owner members of an ally with private (non party-owned) claims show as "Others". A real fix needs the server to send the ally party id per claim owner.
- Zoom row (- / +) adds one button row to the panel; on short screens the legend loses a line sooner.
- Seamless cells now (the 1 px gap between cells at >=8 px is gone).
- Texture id is fixed (`openpartiesandclaims:hub_claims_map`), fine while only one Claims screen exists.
- Water shading ties (exactly 0.6 / -0.6) follow the same floating point as vanilla.
