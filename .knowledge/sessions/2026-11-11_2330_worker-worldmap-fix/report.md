# worker-worldmap-fix report

## Task 1 - xaerolib dev runtime for the World Map integration (state: DONE)
Merged claude/opac-1.5-unified-menu (already up to date) and claude/suspicious-lichterman-76e72b (no conflicts). Only file changed: `Fabric/build.gradle`.

What changed
- Detached-style config `xaeroWorldMapRaw` (resolvable, non-transitive) holds the raw World Map jar. At configuration time the nested `META-INF/jars/xaerolib-*.jar` is extracted to `Fabric/build/xaerolib/xaerolib-fabric-1.21.1-1.7.3.jar` (only if missing; the name carries the xaerolib version). No absolute paths, nothing shipped.
- A Gradle task with builtBy does NOT work: Loom checksums the files of mod configurations during configuration (`ModConfigurationRemapper.resolveArtifacts`) -> "Failed to compute checksum" because the file does not exist yet. Hence the configuration-time step.
- `modCompileOnly` gets World Map + the xaerolib file (compiles against both).
- Runtime: NOT `modLocalRuntime`. With xaerolib loaded, the headless `runBootTest` server crashed: xaerolib `CommonEvents.serverStarting` -> "IllegalStateException: Registry is not frozen yet!" (its server hooks assume a real dedicated server, not vanilla GameTestServer). So World Map + xaerolib are on the runtime classpath of the two client runs only: new empty source set `clientWorldMap` (+ `loom.createRemapConfigurations`, `modClientWorldMapRuntimeOnly`), `source(sourceSets.clientWorldMap)` on `client` and `client2`. All server/gametest runs stay without World Map (which also proves the mod works without it).
- Step 2 (GuiMap direct reference): not applied. `screen.getClass().getName().equals(...)` in WorldMapClaimingButton must stay: `instanceof GuiMap` would throw NoClassDefFoundError on every screen when World Map is absent. The mixin cast `(IRightClickableElement)(Object) screen` left as is (no benefit).

Commands / results
- `gradlew.bat :Fabric:build :NeoForge:build :Fabric:runBootTest :NeoForge:runTeamClaimsGameTest` -> BUILD SUCCESSFUL (final run).
- `:Fabric:runBootTest` "All 147 required tests passed"; `:NeoForge:runTeamClaimsGameTest` "All 147 required tests passed" (135 + 8 config-view + 4 world map).
- Jars (`unzip -l | grep 'xaero/map/\|xaero/lib/\|META-INF/jars'`): 0 matches in the Fabric and NeoForge jars and their sources jars. The Fabric jar contains only `xaero/pac/teamclaims/fabric/worldmap/*` (+ mixin) and `openpartiesandclaims.worldmap.mixins.json`; the NeoForge jar has no worldmap classes.
- `:Fabric:runClient` reached the title screen (render loop idle, window "Minecraft* 1.21.1" responding), 59 mods loaded incl. xaerolib 1.7.3, xaeroworldmap 1.47.0. `run-client1/logs/latest.log`: WorldMap logged "Open Parties And Claims found!"; `jcmd GC.class_histogram` showed `TeamClaimsClientFabric`, `WorldMapMixinPlugin`, `WorldMapClaimingButton` lambda and `TeamClaimsClientConfig` loaded (client entrypoint + mixin plugin of our config applied). WARN/ERROR lines: none from OPAC/Team Claims or our mixin; only World Map's own "Online mod data expired" (offline version check), forgeconfigapiport refmap/IoUtils (pre-existing), missing goat-horn sounds / shader sampler (vanilla dev noise). Closed only the java process whose command line held this worktree path. Not tested: the right-click entries and the map button in an actual world.

Open points
- Known limitation (no code): if the server refuses the `usedSub` change (player over a claim limit and not OP), a one-shot "Claim as ..." runs with the old choice.
- The first `runClient` printed a WARN for the empty source set's missing classes folder; fixed by not adding its own output to the classpath.

## Task 2 (T5) - map zoom config, client options on Settings, convert radius 16 (state: DONE)
Merged claude/opac-1.5-unified-menu first (clean).
- `Fabric/src/gametest/resources/fabric.mod.json`: added TeamClaimsMapTest -> Fabric runs 156 like NeoForge.
- `TeamClaimsClientConfig`: `claimsMapZoom` (int 0..2, default `HubGridFit.DEFAULT_ZOOM` = 1, comment 6/12/24 px) in section `claimsMap`; static `worldMapIntegration` flag, set by `TeamClaimsClientFabric` when World Map is loaded.
- `HubGridFit.startZoom(session, configured)`; `ClaimsTabScreen` session `zoom` now starts at -1 (= none picked) and `zoomStep()` falls back to the config. Zooming in the screen never writes the config.
- `SettingsTabScreen`: "Client options" section in the left column below "My settings" (`HubSidebar.renderHeader`): CycleButtons World Map button on/off, button position (5), map zoom (small/medium/large). The two World Map ones only when `worldMapIntegration`. Each change: `ConfigValue.set` + `TeamClaimsClientConfig.SPEC.save()`. Options that do not fit above Back are left out (header only drawn if one fits). Lang keys (12) appended at the end of en_us/de_de.
- Convert radius: `TeamTabScreen` cycles up to `TeamHubLogic.ABSOLUTE_MAX_CONVERT_RADIUS` (16); the now unused `DEFAULT_MAX_CONVERT_RADIUS` is removed, javadoc and the `roleAndRadiusButtonsCycleAndWrap` test updated.
- Tests: no new test methods; the pure logic checks (startZoom, 3 zoom steps 6/12/24, 5 positions) are in the existing grid-fit test, so the count stays 156 on both.
- `gradlew.bat :Fabric:build :NeoForge:build :Fabric:runBootTest :NeoForge:runTeamClaimsGameTest`: BUILD SUCCESSFUL; Fabric "All 156 required tests passed", NeoForge "All 156 required tests passed".
Open points: the Settings screen was not opened in a running client (layout/overlap with Back on small windows untested; it drops options that do not fit). SPEC.save() needs the config loaded (it is on both loaders once the client runs).
