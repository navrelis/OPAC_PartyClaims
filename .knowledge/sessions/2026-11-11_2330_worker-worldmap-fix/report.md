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
