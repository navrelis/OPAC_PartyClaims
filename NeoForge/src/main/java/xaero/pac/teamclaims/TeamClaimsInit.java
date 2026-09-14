package xaero.pac.teamclaims;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.Logger;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.event.api.OPACServerAddonRegisterEvent;
import xaero.pac.common.server.claims.ServerClaimsManager;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigOptionSpecAPI;
import xaero.pac.teamclaims.config.TeamConfigManager;
import xaero.pac.teamclaims.network.ClientPayloadHandler;
import xaero.pac.teamclaims.network.ClientTeamConfigCache;
import xaero.pac.teamclaims.network.TeamConfigRemovedPayload;
import xaero.pac.teamclaims.network.TeamConfigSyncPayload;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Main integration point for Team Claims within the OPAC mod.
 * <p>
 * Registers as an OPAC server addon and sets up the bridge handler
 * so the Common module can call into the team claims logic.
 */
public class TeamClaimsInit {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static TeamClaimManager claimManager;
    private static TeamForceLoadHandler forceLoadHandler;
    private static TeamConfigManager teamConfigManager;
    private static MinecraftServer currentServer;

    /**
     * Called from the NeoForge mod constructor to register events.
     */
    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(TeamClaimsInit.class);
        NeoForge.EVENT_BUS.register(TeamClaimsCommands.class);
        modEventBus.addListener(TeamClaimsInit::onRegisterPayloads);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(ClientEventHandler.class);
        }

        LOGGER.info("Team Claims integration initialized");
    }

    // ==================== Network Registration ====================

    private static void onRegisterPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                TeamConfigSyncPayload.TYPE,
                TeamConfigSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleConfigSync(payload, context)
        );

        registrar.playToClient(
                TeamConfigRemovedPayload.TYPE,
                TeamConfigRemovedPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleConfigRemoved(payload, context)
        );
    }

    // ==================== Server Events ====================

    @SubscribeEvent
    public static void onOPACRegister(OPACServerAddonRegisterEvent event) {
        MinecraftServer server = event.getServer();
        currentServer = server;
        forceLoadHandler = new TeamForceLoadHandler(server);
        claimManager = new TeamClaimManager(server, forceLoadHandler);
        teamConfigManager = new TeamConfigManager(server);

        event.getClaimsManagerTrackerAPI().register(claimManager);

        // Set the bridge handler so Common code can call into us
        TeamClaimsIntegration.setHandler(new TeamClaimsBridgeHandler());

        LOGGER.info("Team Claims addon registered with Open Parties and Claims");
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
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

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            if (claimManager != null) {
                claimManager.onPlayerLogin(sp);
            }
            if (teamConfigManager != null) {
                teamConfigManager.syncToPlayer(sp);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (claimManager != null && event.getEntity() instanceof ServerPlayer sp) {
            claimManager.onPlayerLogout(sp);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (claimManager != null) {
            claimManager.tick();
        }
        if (teamConfigManager != null) {
            teamConfigManager.pollForChanges();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
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

    // ==================== Client Events ====================

    public static class ClientEventHandler {
        @SubscribeEvent
        public static void onClientDisconnected(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientTeamConfigCache.clear();
        }
    }

    // ==================== Accessors ====================

    public static TeamClaimManager getClaimManager() { return claimManager; }
    public static TeamForceLoadHandler getForceLoadHandler() { return forceLoadHandler; }
    public static TeamConfigManager getTeamConfigManager() { return teamConfigManager; }
    @Nullable public static MinecraftServer getServer() { return currentServer; }

    // ==================== Bridge Handler ====================

    /**
     * Implementation of the bridge interface that delegates to TeamClaimManager
     * and TeamConfigManager.
     */
    private static class TeamClaimsBridgeHandler implements TeamClaimsIntegration.TeamClaimsHandler {

        @Override
        @Nullable
        public ClaimResult<PlayerChunkClaim> interceptUnclaim(
                ServerClaimsManager claimsManager, ResourceLocation dimension,
                UUID id, int x, int z, boolean replace, @Nullable PlayerChunkClaim currentClaim) {
            if (replace || currentClaim == null) return null;
            TeamClaimManager tcm = claimManager;
            if (tcm == null) return null;
            if (tcm.isTeamClaim(currentClaim) && tcm.areInSameTeam(id, currentClaim.getPlayerId())) {
                claimsManager.unclaim(dimension, x, z);
                return new ClaimResult<>(null, ClaimResult.Type.SUCCESSFUL_UNCLAIM);
            }
            return null;
        }

        @Override
        @Nullable
        public ClaimResult<PlayerChunkClaim> interceptForceload(
                ServerClaimsManager claimsManager, ResourceLocation dimension,
                UUID id, int x, int z, boolean enable, boolean replace,
                boolean isServer, @Nullable PlayerChunkClaim currentClaim) {
            if (replace) return null;
            TeamClaimManager tcm = claimManager;
            if (tcm == null) return null;

            // Team forceload toggle: allow team members to toggle each other's team claims
            if (currentClaim != null && !java.util.Objects.equals(id, currentClaim.getPlayerId())) {
                if (tcm.isTeamClaim(currentClaim) && tcm.areInSameTeam(id, currentClaim.getPlayerId())) {
                    if (currentClaim.isForceloadable() == enable) {
                        return new ClaimResult<>(currentClaim,
                                enable ? ClaimResult.Type.ALREADY_FORCELOADABLE : ClaimResult.Type.ALREADY_UNFORCELOADED);
                    }
                    PlayerChunkClaim result = claimsManager.claim(dimension,
                            currentClaim.getPlayerId(), currentClaim.getSubConfigIndex(),
                            x, z, enable);
                    if (result != null) {
                        return new ClaimResult<>(result,
                                enable ? ClaimResult.Type.SUCCESSFUL_FORCELOAD : ClaimResult.Type.SUCCESSFUL_UNFORCELOAD);
                    }
                    return null; // claim() returned null — fall through to default handling
                }
            }

            // Team forceload budget check
            if (enable && !isServer && currentClaim != null && tcm.isTeamClaim(currentClaim)) {
                ClaimResult<PlayerChunkClaim> budgetResult = tcm.checkTeamForceloadBudget(
                        id, currentClaim);
                if (budgetResult != null) return budgetResult;
            }

            return null;
        }

        @Override
        @Nullable
        public ClaimResult<PlayerChunkClaim> interceptClaim(
                ServerClaimsManager claimsManager, ResourceLocation dimension,
                UUID playerId, int subConfigIndex, int x, int z,
                boolean forceLoaded, boolean replace, boolean isServer) {
            if (isServer || replace) return null;
            TeamClaimManager tcm = claimManager;
            if (tcm == null) return null;
            // Only check team budget for team sub-config claims, not personal claims
            if (!tcm.isTeamSubConfigIndex(playerId, subConfigIndex)) return null;
            return tcm.checkTeamClaimBudget(playerId, forceLoaded);
        }

        @Override
        public boolean isComputingOverhead() {
            return TeamClaimManager.COMPUTING_OVERHEAD.get();
        }

        @Override
        public int getTeamClaimOverheadForPlayer(UUID playerUUID) {
            TeamClaimManager tcm = claimManager;
            return tcm != null ? tcm.getTeamClaimOverheadForPlayer(playerUUID) : 0;
        }

        @Override
        public int getTeamForceloadOverheadForPlayer(UUID playerUUID) {
            TeamClaimManager tcm = claimManager;
            return tcm != null ? tcm.getTeamForceloadOverheadForPlayer(playerUUID) : 0;
        }

        @Override
        public boolean isInternalEditActive() {
            return TeamConfigManager.INTERNAL_EDIT.get();
        }

        @Override
        public boolean isPlayerTeamAdmin(UUID playerUUID) {
            TeamConfigManager tcm = teamConfigManager;
            return tcm != null && tcm.isPlayerTeamAdmin(playerUUID);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void onTeamSubConfigSettingChanged(UUID changedByPlayer,
                IPlayerConfigOptionSpecAPI option, Comparable value) {
            TeamConfigManager tcm = teamConfigManager;
            if (tcm != null) {
                tcm.onTeamSubConfigSettingChanged(changedByPlayer, option, value);
            }
        }

        @Override
        @Nullable
        public MinecraftServer getServer() {
            return currentServer;
        }

        @Override
        public boolean hasTeamOverhead(UUID playerUUID) {
            TeamClaimManager cm = claimManager;
            if (cm == null) return false;
            return cm.getTeamClaimOverheadForPlayer(playerUUID) > 0
                    || cm.getTeamForceloadOverheadForPlayer(playerUUID) > 0;
        }

        @Override
        public void onPartyCreated(ServerPlayer owner) {
            if (currentServer == null) return;
            try {
                var partyManager = OpenPACServerAPI.get(currentServer).getPartyManager();
                var party = partyManager.getPartyByMember(owner.getUUID());
                if (party == null) return;
                TeamConfigManager tcm = teamConfigManager;
                if (tcm != null) {
                    tcm.createTeamConfig(party);
                }
                TeamClaimManager cm = claimManager;
                if (cm != null) {
                    cm.ensureAllMembersHaveSubConfig(party);
                }
            } catch (Exception e) {
                LOGGER.warn("Error in onPartyCreated for {}: {}", owner.getUUID(), e.getMessage());
            }
        }
    }
}
