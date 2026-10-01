package xaero.pac.teamclaims.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import xaero.pac.common.event.api.v3.OPACServerAddonRegister;
import xaero.pac.teamclaims.TeamClaimsCommon;

/**
 * Fabric adapter for Team Claims: registers the Fabric API events and forwards them to the
 * loader-neutral {@link TeamClaimsCommon}. All Team Claims logic lives in Common.
 * <p>
 * {@link #init()} is common (client + dedicated server) initialization and only registers
 * server-side events, so it never references client-only classes.
 */
public class TeamClaimsFabric {

    private static volatile boolean initialized = false;

    /**
     * Called from both {@code onInitializeClient()} and {@code onInitializeServer()}. Idempotent.
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        OPACServerAddonRegister.EVENT.register(TeamClaimsCommon::onAddonRegister);
        ServerLifecycleEvents.SERVER_STARTED.register(TeamClaimsCommon::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(TeamClaimsCommon::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(TeamClaimsCommon::onServerTickEnd);
        // JOIN fires from the middle of PlayerList.placeNewPlayer, before OPAC's own Fabric login
        // hook (MixinFabricPlayerList, TAIL of placeNewPlayer). TeamClaimsCommon.onPlayerLoggedIn
        // defers its work by one tick, so OPAC's login handling has completed by then.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> TeamClaimsCommon.onPlayerLoggedIn(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TeamClaimsCommon.onPlayerLoggedOut(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register(TeamClaimsCommon::registerCommands);

        TeamClaimsCommon.LOGGER.info("Team Claims integration initialized");
    }
}
