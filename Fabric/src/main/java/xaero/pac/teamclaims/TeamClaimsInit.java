package xaero.pac.teamclaims;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.event.api.OPACServerAddonRegisterEventContext;
import xaero.pac.common.event.api.v3.OPACServerAddonRegister;
import xaero.pac.common.server.claims.ServerClaimsManager;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.teamclaims.config.TeamConfigManager;
import xaero.pac.teamclaims.network.TeamConfigRemovedPayload;
import xaero.pac.teamclaims.network.TeamConfigSyncPayload;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Main integration point for Team Claims within the OPAC mod.
 * <p>
 * Registers as an OPAC server addon and sets up the bridge handler
 * so the Common module can call into the team claims logic.
 * <p>
 * {@link #init()} is common (client + dedicated server) initialization: it must not reference
 * any client-only classes. Client-only wiring (payload receivers, disconnect cache clearing)
 * lives in {@link TeamClaimsClientInit}, which is only ever called from the client entrypoint.
 */
public class TeamClaimsInit {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile boolean initialized = false;

    private static TeamClaimManager claimManager;
    private static TeamForceLoadHandler forceLoadHandler;
    private static TeamConfigManager teamConfigManager;
    private static MinecraftServer currentServer;

    /**
     * Called from both {@code onInitializeClient()} and {@code onInitializeServer()}. Idempotent.
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // Payload type + codec registration must happen on both sides so a client can decode
        // what the server sends, even though only the server ever actually sends these payloads.
        PayloadTypeRegistry.playS2C().register(TeamConfigSyncPayload.TYPE, TeamConfigSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TeamConfigRemovedPayload.TYPE, TeamConfigRemovedPayload.STREAM_CODEC);

        OPACServerAddonRegister.EVENT.register(TeamClaimsInit::onOPACRegister);
        ServerLifecycleEvents.SERVER_STARTED.register(TeamClaimsInit::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(TeamClaimsInit::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(TeamClaimsInit::onServerTick);
        ServerPlayConnectionEvents.JOIN.register(TeamClaimsInit::onPlayerJoin);
        ServerPlayConnectionEvents.DISCONNECT.register(TeamClaimsInit::onPlayerDisconnect);
        CommandRegistrationCallback.EVENT.register(TeamClaimsCommands::onRegisterCommands);

        LOGGER.info("Team Claims integration initialized");
    }

    // ==================== Server Events ====================

    private static void onOPACRegister(OPACServerAddonRegisterEventContext context) {
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

    private static void onServerStarted(MinecraftServer server) {
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

    // Fabric's PlayerManagerMixin (fabric-networking-api-v1) fires JOIN from inside the middle of
    // PlayerList.placeNewPlayer (via ServerPlayNetworkAddon.onClientReady()), which runs before
    // OPAC's own Fabric login hook (a @Inject at TAIL of placeNewPlayer, see MixinFabricPlayerList).
    // We defer our login logic by one tick so OPAC's login handling has already completed by the
    // time we run, matching the timing the legacy NeoForge code got for free from PlayerLoggedInEvent.
    private static void onPlayerJoin(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        UUID playerId = handler.getPlayer() == null ? null : handler.getPlayer().getUUID();
        if (playerId == null) return;
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
            if (sp == null) return; // disconnected before the deferred tick ran
            TeamClaimManager cm = claimManager;
            if (cm != null) {
                cm.onPlayerLogin(sp);
            }
            TeamConfigManager tcm = teamConfigManager;
            if (tcm != null) {
                tcm.syncToPlayer(sp);
            }
        }));
    }

    private static void onPlayerDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        ServerPlayer sp = handler.getPlayer();
        if (claimManager != null && sp != null) {
            claimManager.onPlayerLogout(sp);
        }
    }

    private static void onServerTick(MinecraftServer server) {
        if (claimManager != null) {
            claimManager.tick();
        }
        if (teamConfigManager != null) {
            teamConfigManager.pollForChanges();
        }
    }

    private static void onServerStopping(MinecraftServer server) {
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
        public boolean allowsTeamUnclaim(
                ServerClaimsManager claimsManager, ResourceLocation dimension,
                UUID id, int x, int z, PlayerChunkClaim currentClaim) {
            return isOtherMembersTeamClaim(id, currentClaim);
        }

        @Override
        public boolean allowsTeamForceload(
                ServerClaimsManager claimsManager, ResourceLocation dimension,
                UUID id, int x, int z, PlayerChunkClaim currentClaim) {
            return isOtherMembersTeamClaim(id, currentClaim);
        }

        private boolean isOtherMembersTeamClaim(UUID id, @Nullable PlayerChunkClaim currentClaim) {
            TeamClaimManager tcm = claimManager;
            if (tcm == null || currentClaim == null) return false;
            return tcm.isTeamClaim(currentClaim) && tcm.areInSameTeam(id, currentClaim.getPlayerId());
        }

        @Override
        @Nullable
        public ClaimResult<PlayerChunkClaim> interceptForceload(
                ServerClaimsManager claimsManager, ResourceLocation dimension,
                UUID id, int x, int z, PlayerChunkClaim currentClaim) {
            TeamClaimManager tcm = claimManager;
            if (tcm == null || currentClaim == null) return null;
            // Only team claims share a budget; personal claims keep stock OPAC behavior.
            if (!tcm.isTeamClaim(currentClaim)) return null;
            return tcm.checkTeamForceloadBudget(id, currentClaim);
        }

        @Override
        @Nullable
        public ClaimResult<PlayerChunkClaim> interceptClaim(
                ServerClaimsManager claimsManager, ResourceLocation dimension,
                UUID playerId, int subConfigIndex, int x, int z, boolean forceLoaded) {
            TeamClaimManager tcm = claimManager;
            if (tcm == null) return null;
            // Only check the team budget for claims made with the team sub-config, not personal ones
            if (!tcm.isTeamSubConfigIndex(playerId, subConfigIndex)) return null;
            // The claim being replaced (if any) decides what this claim really adds to the team's
            // totals and to the previous owner's own count — the budget check needs both.
            PlayerChunkClaim existing = claimsManager.get(dimension, x, z);
            return tcm.checkTeamClaimBudget(playerId, forceLoaded, existing);
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
        public void onTeamSubConfigSettingChanged(UUID changedByPlayer,
                IPlayerConfigOptionSpecAPI<?> option, Object value) {
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
