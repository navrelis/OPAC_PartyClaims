package xaero.pac.teamclaims;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import xaero.pac.common.event.api.OPACServerAddonRegisterEventContext;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.teamclaims.config.TeamConfigManager;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Loader-neutral entry point of Team Claims.
 * <p>
 * Holds the per-server manager singletons and exposes plain lifecycle methods. Each mod loader
 * only needs a thin adapter that forwards its own events to these methods (see
 * {@code xaero.pac.teamclaims.fabric.TeamClaimsFabric}):
 * <ul>
 *     <li>{@link #onAddonRegister} on OPAC's server addon register event</li>
 *     <li>{@link #onServerStarted} / {@link #onServerStopping} on server start/stop</li>
 *     <li>{@link #onServerTickEnd} at the end of every server tick</li>
 *     <li>{@link #onPlayerLoggedIn} / {@link #onPlayerLoggedOut} on player join/leave</li>
 *     <li>{@link #registerCommands} on command registration</li>
 * </ul>
 * Everything here runs on the server thread. No loader (Fabric/NeoForge) classes may be
 * referenced from this package.
 */
public final class TeamClaimsCommon {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static TeamClaimManager claimManager;
    private static TeamForceLoadHandler forceLoadHandler;
    private static TeamConfigManager teamConfigManager;
    private static MinecraftServer currentServer;

    private TeamClaimsCommon() {}

    // ==================== Lifecycle ====================

    /**
     * Creates the managers for this server and installs the bridge handler that the Common OPAC
     * hooks call into. Must be called from OPAC's server addon register event, which fires while
     * the server is starting, before {@link #onServerStarted}.
     */
    public static void onAddonRegister(OPACServerAddonRegisterEventContext context) {
        MinecraftServer server = context.getServer();
        currentServer = server;
        forceLoadHandler = new TeamForceLoadHandler(server);
        claimManager = new TeamClaimManager(server, forceLoadHandler);
        teamConfigManager = new TeamConfigManager(server);

        context.getClaimsManagerTrackerAPI().register(claimManager);

        // Set the bridge handler so Common code can call into us
        TeamClaimsIntegration.setHandler(new TeamClaimsBridgeHandler());

        LOGGER.info("Team Claims addon registered with Open Parties and Claims");
    }

    public static void onServerStarted(MinecraftServer server) {
        if (teamConfigManager != null) {
            teamConfigManager.loadAll();
        }
        if (claimManager != null) {
            claimManager.onServerStarted();
        }
        if (forceLoadHandler != null) {
            forceLoadHandler.onServerStarted();
        }
    }

    public static void onServerTickEnd(MinecraftServer server) {
        if (claimManager != null) {
            claimManager.tick();
        }
        if (teamConfigManager != null) {
            teamConfigManager.pollForChanges();
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        if (forceLoadHandler != null) {
            forceLoadHandler.onServerStopping();
        }
        if (teamConfigManager != null) {
            teamConfigManager.shutdown();
        }
        TeamClaimsIntegration.setHandler(null);
        claimManager = null;
        forceLoadHandler = null;
        teamConfigManager = null;
        currentServer = null;
    }

    /**
     * Player login. The actual handling is always deferred by one server tick, so it runs after
     * OPAC's own login handling no matter in which order a loader fires its join event relative
     * to OPAC's login hook (on Fabric, {@code ServerPlayConnectionEvents.JOIN} fires from the
     * middle of {@code PlayerList.placeNewPlayer}, before OPAC's own login hook at its TAIL).
     * If the player disconnects before that tick, nothing happens.
     */
    public static void onPlayerLoggedIn(ServerPlayer player) {
        if (player == null) return;
        UUID playerId = player.getUUID();
        MinecraftServer server = player.server;
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
            if (sp == null) return; // disconnected before the deferred tick ran
            TeamClaimManager cm = claimManager;
            if (cm != null) {
                cm.onPlayerLogin(sp);
            }
            TeamConfigManager tcm = teamConfigManager;
            if (tcm != null) {
                tcm.onPlayerLogin(sp);
            }
        }));
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        if (claimManager != null && player != null) {
            claimManager.onPlayerLogout(player);
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        TeamClaimsCommands.onRegisterCommands(dispatcher, registryAccess, environment);
    }

    // ==================== Accessors ====================

    public static TeamClaimManager getClaimManager() { return claimManager; }
    public static TeamForceLoadHandler getForceLoadHandler() { return forceLoadHandler; }
    public static TeamConfigManager getTeamConfigManager() { return teamConfigManager; }
    @Nullable public static MinecraftServer getServer() { return currentServer; }
}
