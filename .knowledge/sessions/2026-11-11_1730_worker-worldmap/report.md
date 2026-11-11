# worker-worldmap report (1.5.0 World Map integration)

## Task 1 - state: IMPLEMENTED, ONE BLOCKER OPEN (stopped at context limit)
Files: Fabric/build.gradle (Modrinth exclusiveContent + modCompileOnly/modLocalRuntime maven.modrinth:xaeros-world-map:XIR7GzC3),
Fabric/src/main/java/xaero/pac/teamclaims/fabric/worldmap/{WorldMapMixinPlugin,TeamClaimsClientFabric,WorldMapClaimingButton,WorldMapRightClickEntries,TeamClaimsWorldMapState}.java,
.../worldmap/mixin/MixinWorldMapSupportOpenPartiesAndClaims.java, Fabric/src/main/resources/openpartiesandclaims.worldmap.mixins.json,
fabric.mod.json (client entrypoint + mixin config), Common: teamclaims/config/TeamClaimsClientConfig.java, client/teamclaims/WorldMapClaimingLogic.java,
NeoForge TeamClaimsNeoForge (registers CLIENT config, dist CLIENT only), lang en/de (after other_mode_tooltip), gametests TeamClaimsWorldMapTestCases + Fabric/NeoForge wrappers (+4 tests).

## Results
- :Fabric:build green, :NeoForge:build green, :NeoForge:runTeamClaimsGameTest "All 139 required tests passed".
- :Fabric:runBootTest FAILS (blocker): Fabric loader "HARD_DEP_NO_CANDIDATE xaeroworldmap 1.47.0 {depends xaerolib >=1.0}". xaerolib is only nested (META-INF/jars/xaerolib-fabric-1.21.1-1.7.3.jar) in the World Map jar; Loader does not pick it up in dev. xaerolib is not on Modrinth for 1.21.1.
  Not yet done: runClient check, jar-does-not-contain-WM check (modCompileOnly/modLocalRuntime are not include()d, so expected fine), Fabric 139 count.
- Compile note: xaero.lib.* (xaerolib) is not on the compile classpath, so GuiMap cannot be named in code that needs its supertypes: the button checks the class name "xaero.map.gui.GuiMap"; the mixin passes the screen cast via (IRightClickableElement)(Object).

## Suggested fix for blocker (next worker)
Add a Gradle task that unzips META-INF/jars/xaerolib-*.jar out of the resolved World Map jar into build/, and add that jar as a mod dependency for dev runtime (modLocalRuntime files(...builtBy)), or restrict World Map to client runs only (separate remap config / custom run classpath, like gametestFtb's createRemapConfigurations) and test whether runClient resolves the nested lib. Then run :Fabric:build :NeoForge:build :Fabric:runBootTest (expect 139), then :Fabric:runClient and check the log for mixin/World Map errors.

## javap facts relied on (WM 1.47.0, Mojang-remapped jar)
- SupportOpenPartiesAndClaims.addRightClickOptions(GuiMap, ArrayList<RightClickOption>, ResourceKey<Level>, MapTileSelection, MapProcessor): void; early returns (null sel / wrong dim) skip TAIL; selection null jumps to the last return.
- "Claim Selected" = anonymous $2: onAction(Screen): if largeArea -> mapProcessor.getMessageBox().addMessageWithSource(CLAIMS_MESSAGE_SOURCE(public static), translatable("gui.xaero_pac_requested_large_claim", GOLD "gui.xaero_pac_interrupt_claiming")); then IClientClaimsManagerAPI.requestAreaClaim(ResourceLocation dim,l,t,r,b,null). Bounds clamped to playerChunk +- getMaxClaimDistance() unless isAdminMode(); large = w*h >= 512. Claim entry key "gui.xaero_pac_claim_chunks" (our entries only added when it is in the list).
- RightClickOption(String name,int index,IRightClickableElement target), abstract onAction(Screen), protected boolean active, setNameFormatArgs(Object...). MapTileSelection getLeft/getTop/getRight/getBottom.
- GuiMap buttons: settings 30x30 at (0,0); columns at left and right edges (bottom-anchored, 20x20); cursor coordinates/biome text drawn centred at the top (y=2, +10 per line) -> TOP_CENTER y=34, side positions x=34 from the edge.

## Packet order check (verified in code)
Fabric: ServerPacketReceiverFabric -> ServerPacketReceiver/PacketReceiver.receive: executor.execute(task) on the server thread (runs directly if already on it), all packets use one payload type/channel, so FIFO. ServerboundPlayerConfigOptionValuePacket.ServerHandler sets the option synchronously (tryToSet); PlayerClaimActionRequestHandler.onReceive reads playerConfig.getEffective(usedSub option) at that moment and claims synchronously. So set -> claim -> restore works.
Caveat: the option change is refused (and the server resyncs the old value) when the player is over a claim limit and not OP (ServerPlayerConfigUtils.isOverEitherClaimingLimit) - then the one-shot claim would run with the old choice. Also the server drops a second claim request in the same tick (lastRequestTickCounter).

## Open points
- A custom (non main/non team) usedSub is restored as Private (ClaimAsToggle.set only knows main/team); the hub toggle has the same behaviour.
- All 4 entries are shown only when WM's Claim entry is present (literal reading of the brief); toggle/open could show more often.
- Button position offsets are from bytecode only, not seen on screen. Button asks the server for team info (budget) via TeamInfoRefresh every 5 s while the map is open; numbers are blank until the budget arrives.
- IForgeConfigHelper.registerClientConfig has no file-name parameter, so the client config is registered directly (like the server one) in TeamClaimsClientFabric / TeamClaimsNeoForge.
