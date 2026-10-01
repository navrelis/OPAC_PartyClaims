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
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;
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
 * Everything here runs on the server thread. The managers ({@link TeamClaimManager},
 * {@link TeamConfigManager}, {@link TeamForceLoadHandler}) and their data (e.g.
 * {@link TeamClaimManager.TeamData}) are server-thread confined; the fields below are
 * {@code volatile} only so that a reader on another thread (the bridge handler, called from the client
 * thread of an integrated server) sees a fully published manager or null. No loader (Fabric/NeoForge)
 * classes may be referenced from this package.
 */
public final class TeamClaimsCommon {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile TeamClaimManager claimManager;
    private static volatile TeamForceLoadHandler forceLoadHandler;
    private static volatile TeamConfigManager teamConfigManager;
    private static volatile MinecraftServer currentServer;

    private TeamClaimsCommon() {}

    // ==================== Lifecycle ====================

    /**
     * Creates the managers for this server and installs the bridge handler that the Common OPAC
     * hooks call into. Must be called from OPAC's server addon register event, which fires while
     * the server is starting, before {@link #onServerStarted}.
     * <p>
     * Does nothing but log when {@code enabled} is false in the {@linkplain TeamClaimsServerConfig Team Claims
     * server config}: no bridge handler and no managers, so OPAC behaves like stock and every other method here
     * finds nothing to do ({@link #isActive()} is false). The loaders load the SERVER configs of a server before
     * OPAC's server-about-to-start handling, which is what fires the addon register event, so the value is
     * readable here.
     */
    public static void onAddonRegister(OPACServerAddonRegisterEventContext context) {
        if (!TeamClaimsServerConfig.CONFIG.enabled.get()) {
            LOGGER.info("Team Claims is disabled ('enabled' = false in {}), Open Parties and Claims runs without it",
                    TeamClaimsServerConfig.FILE_NAME);
            return;
        }
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
        TeamConfigManager tcm = teamConfigManager;
        if (tcm != null) tcm.loadAll();
        TeamForceLoadHandler flh = forceLoadHandler;
        if (flh != null) flh.onServerStarted();
        TeamClaimManager cm = claimManager;
        if (cm != null) cm.onServerStarted();
    }

    /**
     * End of every server tick: first the party events queued by OPAC's party hooks during this tick
     * (and the periodic reconciliation), then the claim manager (batched claim limits sync, safety net),
     * then the dirty team config files are handed to the IO thread.
     */
    public static void onServerTickEnd(MinecraftServer server) {
        TeamConfigManager tcm = teamConfigManager;
        TeamClaimManager cm = claimManager;
        if (tcm != null) tcm.processPendingEvents();
        if (cm != null) cm.tick();
        if (tcm != null) tcm.tick();
    }

    public static void onServerStopping(MinecraftServer server) {
        TeamConfigManager tcm = teamConfigManager;
        if (tcm != null) tcm.processPendingEvents();
        TeamForceLoadHandler flh = forceLoadHandler;
        if (flh != null) flh.onServerStopping();
        if (tcm != null) tcm.shutdown();
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
        if (player == null || !isActive()) return;
        UUID playerId = player.getUUID();
        MinecraftServer server = player.server;
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
            if (sp == null) return; // disconnected before the deferred tick ran
            TeamConfigManager tcm = teamConfigManager;
            if (tcm != null) {
                tcm.onPlayerLogin(sp);
            }
            TeamClaimManager cm = claimManager;
            if (cm != null) {
                cm.onPlayerLogin(sp);
            }
        }));
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        TeamClaimManager cm = claimManager;
        if (cm != null && player != null) {
            cm.onPlayerLogout(player);
        }
    }

    /**
     * Always registers {@code /teamclaims}: the server's commands are built before the loaders load the server config,
     * so {@code enabled} is not readable yet. The command checks {@link #isActive()} when it is used instead.
     */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        TeamClaimsCommands.onRegisterCommands(dispatcher, registryAccess, environment);
    }

    // ==================== Accessors ====================

    /**
     * Whether Team Claims is running on the current server: false before the server started, after it stopped and
     * on a server where it is disabled by the {@code enabled} option.
     */
    public static boolean isActive() { return claimManager != null; }

    public static TeamClaimManager getClaimManager() { return claimManager; }
    public static TeamForceLoadHandler getForceLoadHandler() { return forceLoadHandler; }
    public static TeamConfigManager getTeamConfigManager() { return teamConfigManager; }
    @Nullable public static MinecraftServer getServer() { return currentServer; }
}
