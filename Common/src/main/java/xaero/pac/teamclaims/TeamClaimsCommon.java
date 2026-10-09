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
import xaero.pac.teamclaims.ftbsync.FtbTeamsSync;

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
 * When FTB Teams is installed (and the {@code ftbTeamsSync} option is on), {@link #onAddonRegister} also creates the
 * {@link FtbTeamsSync}, which keeps OPAC parties and FTB Teams party teams in line. Without FTB Teams it is never
 * created and no FTB class is ever loaded.
 * <p>
 * Everything here runs on the server thread. The managers ({@link TeamClaimManager},
 * {@link TeamConfigManager}, {@link TeamForceLoadHandler}) and their data (e.g.
 * {@link TeamClaimManager.TeamData}) are server-thread confined; the fields below are
 * {@code volatile} only so that a reader on another thread (the bridge handler, should one of its
 * count methods ever be called off the server thread) sees a fully published manager or null. No loader (Fabric/NeoForge)
 * classes may be referenced from this package.
 */
public final class TeamClaimsCommon {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile TeamClaimManager claimManager;
    private static volatile TeamForceLoadHandler forceLoadHandler;
    private static volatile TeamConfigManager teamConfigManager;
    private static volatile MinecraftServer currentServer;
    @Nullable private static volatile FtbTeamsSync ftbTeamsSync;
    /** Why there is no {@link #ftbTeamsSync} on a server where Team Claims is active, as a message key. */
    private static volatile String ftbTeamsSyncOffReason = FtbTeamsSync.OFF_NO_FTB_TEAMS;

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
        // Team roles for unclaiming and forceloading team claims. The listener manager belongs to this server's claims
        // manager, so this registers exactly one listener per server start.
        context.getClaimActionListenerManagerAPI().register(new TeamRoles.Listener());

        // Set the bridge handler so Common code can call into us
        TeamClaimsIntegration.setHandler(new TeamClaimsBridgeHandler());

        // The optional sync with FTB Teams: only created (and FTB classes only loaded) when FTB Teams is installed
        String[] syncOffReason = {FtbTeamsSync.OFF_NO_FTB_TEAMS};
        ftbTeamsSync = FtbTeamsSync.createIfEnabled(server, syncOffReason);
        ftbTeamsSyncOffReason = syncOffReason[0];

        LOGGER.info("Team Claims addon registered with Open Parties and Claims");
    }

    public static void onServerStarted(MinecraftServer server) {
        TeamConfigManager tcm = teamConfigManager;
        if (tcm != null) tcm.loadAll();
        TeamForceLoadHandler flh = forceLoadHandler;
        if (flh != null) flh.onServerStarted();
        TeamClaimManager cm = claimManager;
        if (cm != null) cm.onServerStarted();
        FtbTeamsSync sync = ftbTeamsSync;
        if (sync != null) sync.onServerStarted();
    }

    /**
     * End of every server tick: first the party events queued by OPAC's party hooks during this tick
     * (and the periodic reconciliation), then the FTB Teams sync (if it runs) and the party events its changes to
     * OPAC parties queued, then the claim manager (batched claim limits sync, safety net), then the dirty team
     * config files are handed to the IO thread.
     */
    public static void onServerTickEnd(MinecraftServer server) {
        TeamConfigManager tcm = teamConfigManager;
        TeamClaimManager cm = claimManager;
        FtbTeamsSync sync = ftbTeamsSync;
        if (tcm != null) tcm.processPendingEvents();
        if (sync != null) {
            sync.tick();
            // A party change that came from FTB Teams reaches Team Claims in the same tick, like any other
            if (tcm != null) tcm.processPendingEvents();
        }
        if (cm != null) cm.tick();
        if (tcm != null) tcm.tick();
    }

    public static void onServerStopping(MinecraftServer server) {
        TeamConfigManager tcm = teamConfigManager;
        if (tcm != null) tcm.processPendingEvents();
        FtbTeamsSync sync = ftbTeamsSync;
        if (sync != null) sync.shutdown();
        ftbTeamsSync = null;
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

    /** The FTB Teams sync of the current server, null when it does not run (see {@link #getFtbTeamsSyncOffReason()}). */
    @Nullable public static FtbTeamsSync getFtbTeamsSync() { return ftbTeamsSync; }

    /** The message key of why {@link #getFtbTeamsSync()} is null while Team Claims is active. */
    public static String getFtbTeamsSyncOffReason() { return ftbTeamsSyncOffReason; }
}
