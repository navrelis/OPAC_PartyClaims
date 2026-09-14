package xaero.pac.teamclaims;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;
import xaero.pac.common.packet.claims.ClientboundClaimLimitsPacket;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.PlayerConfigOptions;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamClaimManager implements IClaimsManagerListenerAPI {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MinecraftServer server;
    private final TeamForceLoadHandler forceLoadHandler;
    private TeamClaimSavedData savedData;
    private boolean serverReady = false;
    private volatile boolean suppressListenerCallbacks = false;

    public static final ThreadLocal<Boolean> COMPUTING_OVERHEAD = ThreadLocal.withInitial(() -> false);

    private final Map<UUID, Integer> teamSubIndexCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> cachedClaimOverhead = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> cachedForceloadOverhead = new ConcurrentHashMap<>();

    public TeamClaimManager(MinecraftServer server, TeamForceLoadHandler forceLoadHandler) {
        this.server = server;
        this.forceLoadHandler = forceLoadHandler;
    }

    private TeamClaimSavedData getSavedData() {
        if (savedData == null) {
            savedData = server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(TeamClaimSavedData::new, TeamClaimSavedData::load, null),
                    "opacteamclaims_data"
            );
        }
        return savedData;
    }

    @Nullable
    private String resolveSubConfigId(UUID playerUUID) {
        TeamConfigManager tcm = TeamClaimsInit.getTeamConfigManager();
        if (tcm != null) {
            TeamConfig tc = tcm.getTeamConfigForPlayer(playerUUID);
            if (tc != null) return tc.getSubConfigId();
        }
        IServerPartyAPI party = getPlayerParty(playerUUID);
        if (party != null) {
            String teamName = (tcm != null) ? tcm.resolvePartyName(party) : party.getDefaultName();
            return TeamConfig.buildSubConfigId(teamName);
        }
        return null;
    }

    private String resolveSubConfigIdForParty(UUID partyId) {
        TeamConfigManager tcm = TeamClaimsInit.getTeamConfigManager();
        if (tcm != null) {
            TeamConfig tc = tcm.getTeamConfig(partyId);
            if (tc != null) return tc.getSubConfigId();
        }
        // Fallback: look up party to get its name
        try {
            IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
            if (party != null) {
                String teamName = (tcm != null) ? tcm.resolvePartyName(party) : party.getDefaultName();
                return TeamConfig.buildSubConfigId(teamName);
            }
        } catch (Exception ignored) {}
        return TeamConfig.buildSubConfigIdFromUUID(partyId);
    }

    public void ensureTeamSubConfig(UUID playerUUID) {
        String subConfigId = resolveSubConfigId(playerUUID);
        if (subConfigId == null) return;
        try {
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigs();
            IPlayerConfigAPI playerConfig = configManager.getLoadedConfig(playerUUID);
            IPlayerConfigAPI existingSub = playerConfig.getSubConfig(subConfigId);
            if (existingSub != null) {
                teamSubIndexCache.put(playerUUID, existingSub.getSubIndex());
            } else {
                IPlayerConfigAPI newSub = playerConfig.createSubConfig(subConfigId);
                if (newSub != null) {
                    teamSubIndexCache.put(playerUUID, newSub.getSubIndex());
                    LOGGER.info("Created '{}' sub-config for player {} (sub-index {})",
                            subConfigId, playerUUID, newSub.getSubIndex());
                } else {
                    LOGGER.warn("Failed to create '{}' sub-config for player {}", subConfigId, playerUUID);
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error creating team sub-config for player {}: {}", playerUUID, e.getMessage());
            return;
        }
        TeamConfigManager tcm = TeamClaimsInit.getTeamConfigManager();
        if (tcm != null) {
            tcm.configureTeamSubConfigForPlayer(playerUUID);
        }
    }

    public void ensureAllMembersHaveSubConfig(IServerPartyAPI party) {
        party.getMemberInfoStream().forEach(member -> ensureTeamSubConfig(member.getUUID()));
    }

    private int getTeamSubIndex(UUID playerUUID) {
        Integer cached = teamSubIndexCache.get(playerUUID);
        if (cached != null) return cached;
        String subConfigId = resolveSubConfigId(playerUUID);
        if (subConfigId == null) return -1;
        try {
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigs();
            IPlayerConfigAPI playerConfig = configManager.getLoadedConfig(playerUUID);
            IPlayerConfigAPI sub = playerConfig.getSubConfig(subConfigId);
            if (sub != null) {
                int index = sub.getSubIndex();
                teamSubIndexCache.put(playerUUID, index);
                return index;
            }
        } catch (Exception e) {
            LOGGER.debug("Error looking up team sub-config for {}: {}", playerUUID, e.getMessage());
        }
        return -1;
    }

    public boolean isTeamClaim(@Nullable IPlayerChunkClaimAPI claim) {
        if (claim == null) return false;
        int subIndex = claim.getSubConfigIndex();
        if (subIndex == -1) return false;
        int teamIndex = getTeamSubIndex(claim.getPlayerId());
        return teamIndex != -1 && subIndex == teamIndex;
    }

    public void onServerStarted() {
        serverReady = true;
        validateSavedData();
        LOGGER.info("Team Claims system ready — tracking {} parties", getSavedData().teams.size());
    }

    public void onPlayerLogin(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        IServerPartyAPI party = getPlayerParty(playerUUID);
        if (party == null) return;

        TeamConfigManager tcm = TeamClaimsInit.getTeamConfigManager();
        if (tcm != null && tcm.getTeamConfig(party.getId()) == null) {
            LOGGER.info("Player {} logged in with party but no TeamConfig — creating retroactively", playerUUID);
            tcm.createTeamConfig(party);
        }

        ensureAllMembersHaveSubConfig(party);
        UUID partyId = party.getId();

        if (!forceLoadHandler.isTeamActive(partyId)) {
            TeamData teamData = getSavedData().teams.get(partyId);
            if (teamData != null && !teamData.forceLoadedChunks.isEmpty()) {
                activateTeamForceLoads(partyId);
            }
        }

        updateOverheadCacheForParty(partyId);

        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 5, () -> {
            syncClaimLimitsForTeamMembers(partyId, null);
        }));
    }

    public void onPlayerLogout(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        IServerPartyAPI party = getPlayerParty(playerUUID);
        if (party == null) return;
        UUID partyId = party.getId();
        if (!forceLoadHandler.isTeamActive(partyId)) return;
        boolean anyOtherOnline = party.getOnlineMemberStream()
                .anyMatch(sp -> !sp.getUUID().equals(playerUUID));
        if (!anyOtherOnline) {
            deactivateTeamForceLoads(partyId);
        }
    }

    @Override
    public void onChunkChange(ResourceLocation dimension, int chunkX, int chunkZ,
                              @Nullable IPlayerChunkClaimAPI claim) {
        if (!serverReady || suppressListenerCallbacks) return;
        ClaimPos pos = new ClaimPos(dimension, chunkX, chunkZ);

        if (claim == null) {
            UUID affectedPartyId = findPartyForTrackedClaim(pos);
            removeFromAllTeamTracking(pos);
            if (affectedPartyId != null) {
                updateOverheadCacheForParty(affectedPartyId);
                syncClaimLimitsForTeamMembers(affectedPartyId, null);
            }
            return;
        }

        UUID playerId = claim.getPlayerId();
        IServerPartyAPI party = getPlayerParty(playerId);

        if (isTeamClaim(claim)) {
            if (party == null) return;
            UUID partyId = party.getId();
            TeamData teamData = getOrCreateTeamData(partyId);

            UUID previousOwner = teamData.claimOwners.put(pos, playerId);
            if (teamData.trackedClaims.add(pos)) {
                teamData.claimCountByPlayer.merge(playerId, 1, Integer::sum);
            } else if (previousOwner != null && !previousOwner.equals(playerId)) {
                teamData.claimCountByPlayer.merge(previousOwner, -1, Integer::sum);
                teamData.claimCountByPlayer.merge(playerId, 1, Integer::sum);
            }

            if (claim.isForceloadable()) {
                if (teamData.forceLoadedChunks.add(pos)) {
                    teamData.forceloadCountByPlayer.merge(playerId, 1, Integer::sum);
                    if (forceLoadHandler.isTeamActive(partyId)) {
                        forceLoadHandler.addForceLoad(dimension, chunkX, chunkZ);
                    }
                } else if (previousOwner != null && !previousOwner.equals(playerId)) {
                    teamData.forceloadCountByPlayer.merge(previousOwner, -1, Integer::sum);
                    teamData.forceloadCountByPlayer.merge(playerId, 1, Integer::sum);
                }
            } else {
                if (teamData.forceLoadedChunks.remove(pos)) {
                    UUID forceloadOwner = (previousOwner != null) ? previousOwner : playerId;
                    teamData.forceloadCountByPlayer.merge(forceloadOwner, -1, Integer::sum);
                    forceLoadHandler.removeForceLoad(dimension, chunkX, chunkZ);
                }
            }
            getSavedData().setDirty();
            // Team claims affect all members' displayed counts — update overhead and sync
            updateOverheadCacheForParty(partyId);
            syncClaimLimitsForTeamMembers(partyId, null);
        } else {
            UUID affectedPartyId = findPartyForTrackedClaim(pos);
            removeFromAllTeamTracking(pos);
            if (affectedPartyId != null) {
                updateOverheadCacheForParty(affectedPartyId);
                syncClaimLimitsForTeamMembers(affectedPartyId, null);
            }
        }
        // Non-team (personal) claims only affect the claiming player's count.
        // Base OPAC already handles syncing the claiming player's limits.
    }

    @Override
    public void onWholeRegionChange(ResourceLocation dimension, int regionX, int regionZ) {
        if (!serverReady || suppressListenerCallbacks) return;
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        for (TeamData teamData : getSavedData().teams.values()) {
            List<ClaimPos> toRemove = new ArrayList<>();
            for (ClaimPos pos : teamData.trackedClaims) {
                if (!pos.dimension.equals(dimension)) continue;
                int rX = pos.x >> 9;
                int rZ = pos.z >> 9;
                if (rX != regionX || rZ != regionZ) continue;
                IPlayerChunkClaimAPI current = claimsManager.get(dimension, pos.x, pos.z);
                if (!isTeamClaim(current)) toRemove.add(pos);
            }
            boolean changed = false;
            for (ClaimPos pos : toRemove) {
                teamData.trackedClaims.remove(pos);
                UUID owner = teamData.claimOwners.remove(pos);
                if (owner != null) teamData.claimCountByPlayer.merge(owner, -1, Integer::sum);
                if (teamData.forceLoadedChunks.remove(pos)) {
                    if (owner != null) teamData.forceloadCountByPlayer.merge(owner, -1, Integer::sum);
                    forceLoadHandler.removeForceLoad(pos.dimension, pos.x, pos.z);
                }
                changed = true;
            }
            if (changed) getSavedData().setDirty();
        }
    }

    @Override
    public void onDimensionChange(ResourceLocation dimension) {
        // Re-apply force-load tickets when a dimension is reloaded
        for (Map.Entry<UUID, TeamData> entry : getSavedData().teams.entrySet()) {
            UUID partyId = entry.getKey();
            if (!forceLoadHandler.isTeamActive(partyId)) continue;
            TeamData teamData = entry.getValue();
            for (ClaimPos pos : teamData.forceLoadedChunks) {
                if (pos.dimension.equals(dimension)) {
                    forceLoadHandler.addForceLoad(pos.dimension, pos.x, pos.z);
                }
            }
        }
    }

    private void removeFromAllTeamTracking(ClaimPos pos) {
        for (TeamData teamData : getSavedData().teams.values()) {
            boolean removedClaim = teamData.trackedClaims.remove(pos);
            boolean removedForceload = teamData.forceLoadedChunks.remove(pos);
            if (removedClaim || removedForceload) {
                UUID owner = teamData.claimOwners.remove(pos);
                if (owner != null) {
                    if (removedClaim) teamData.claimCountByPlayer.merge(owner, -1, Integer::sum);
                    if (removedForceload) teamData.forceloadCountByPlayer.merge(owner, -1, Integer::sum);
                }
                if (removedForceload) forceLoadHandler.removeForceLoad(pos.dimension, pos.x, pos.z);
                getSavedData().setDirty();
            }
        }
    }

    public void activateTeamForceLoads(UUID partyId) {
        TeamData teamData = getSavedData().teams.get(partyId);
        if (teamData == null || teamData.forceLoadedChunks.isEmpty()) return;
        for (ClaimPos pos : teamData.forceLoadedChunks) {
            forceLoadHandler.addForceLoad(pos.dimension, pos.x, pos.z);
        }
        forceLoadHandler.markTeamActive(partyId);
        LOGGER.info("Activated {} team force-loads for party {}", teamData.forceLoadedChunks.size(), partyId);
    }

    public void deactivateTeamForceLoads(UUID partyId) {
        TeamData teamData = getSavedData().teams.get(partyId);
        if (teamData == null || teamData.forceLoadedChunks.isEmpty()) return;
        for (ClaimPos pos : teamData.forceLoadedChunks) {
            forceLoadHandler.removeForceLoad(pos.dimension, pos.x, pos.z);
        }
        forceLoadHandler.markTeamInactive(partyId);
        LOGGER.info("Deactivated {} team force-loads for party {}", teamData.forceLoadedChunks.size(), partyId);
    }

    private void cleanupParty(UUID partyId) {
        TeamData teamData = getSavedData().teams.get(partyId);
        if (teamData == null) return;
        for (ClaimPos pos : teamData.forceLoadedChunks) {
            forceLoadHandler.removeForceLoad(pos.dimension, pos.x, pos.z);
        }
        forceLoadHandler.markTeamInactive(partyId);
        getSavedData().teams.remove(partyId);
        getSavedData().setDirty();
    }

    private void validateSavedData() {
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();

        for (UUID partyId : new ArrayList<>(getSavedData().teams.keySet())) {
            IServerPartyAPI party = partyManager.getPartyById(partyId);
            if (party == null) {
                cleanupParty(partyId);
                continue;
            }
            ensureAllMembersHaveSubConfig(party);
            TeamData teamData = getSavedData().teams.get(partyId);
            Set<ClaimPos> claimsToRemove = new HashSet<>();
            List<ClaimPos> forceLoadsToRemove = new ArrayList<>();
            teamData.claimOwners.clear();
            teamData.claimCountByPlayer.clear();
            teamData.forceloadCountByPlayer.clear();

            for (ClaimPos pos : teamData.trackedClaims) {
                IPlayerChunkClaimAPI claim = claimsManager.get(pos.dimension, pos.x, pos.z);
                if (claim == null || !isTeamClaim(claim)) {
                    claimsToRemove.add(pos);
                } else if (party.getMemberInfo(claim.getPlayerId()) == null) {
                    claimsToRemove.add(pos);
                } else {
                    UUID claimOwner = claim.getPlayerId();
                    teamData.claimOwners.put(pos, claimOwner);
                    teamData.claimCountByPlayer.merge(claimOwner, 1, Integer::sum);
                    if (claim.isForceloadable() && teamData.forceLoadedChunks.contains(pos)) {
                        teamData.forceloadCountByPlayer.merge(claimOwner, 1, Integer::sum);
                    }
                }
            }

            for (ClaimPos pos : teamData.forceLoadedChunks) {
                if (claimsToRemove.contains(pos) || !teamData.trackedClaims.contains(pos)) {
                    forceLoadsToRemove.add(pos);
                } else {
                    IPlayerChunkClaimAPI claim = claimsManager.get(pos.dimension, pos.x, pos.z);
                    if (claim == null || !claim.isForceloadable()) {
                        forceLoadsToRemove.add(pos);
                    }
                }
            }

            if (!claimsToRemove.isEmpty() || !forceLoadsToRemove.isEmpty()) {
                teamData.trackedClaims.removeAll(claimsToRemove);
                teamData.forceLoadedChunks.removeAll(forceLoadsToRemove);
                getSavedData().setDirty();
            }
        }
    }

    private int tickCounter = 0;
    private static final int VALIDATION_INTERVAL = 1200;
    private int claimSyncTickCounter = 0;
    private static final int CLAIM_SYNC_INTERVAL = 100;

    public void tick() {
        if (!serverReady) return;
        if (++claimSyncTickCounter >= CLAIM_SYNC_INTERVAL) {
            claimSyncTickCounter = 0;
            refreshOverheadCacheAndSync();
        }
        if (++tickCounter < VALIDATION_INTERVAL) return;
        tickCounter = 0;

        IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
        for (UUID partyId : new ArrayList<>(getSavedData().teams.keySet())) {
            IServerPartyAPI party = partyManager.getPartyById(partyId);
            if (party == null) { cleanupParty(partyId); continue; }
            TeamData teamData = getSavedData().teams.get(partyId);
            if (teamData == null) continue;
            ensureAllMembersHaveSubConfig(party);
            if (!teamData.forceLoadedChunks.isEmpty()) {
                boolean anyOnline = party.getOnlineMemberStream().findAny().isPresent();
                boolean isActive = forceLoadHandler.isTeamActive(partyId);
                if (anyOnline && !isActive) activateTeamForceLoads(partyId);
                else if (!anyOnline && isActive) deactivateTeamForceLoads(partyId);
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerUUID = player.getUUID();
            IServerPartyAPI party = partyManager.getPartyByMember(playerUUID);
            if (party != null) ensureTeamSubConfig(playerUUID);
        }
    }

    private void refreshOverheadCacheAndSync() {
        try {
            IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
            Set<UUID> partiesSynced = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID playerUUID = player.getUUID();
                IServerPartyAPI party = partyManager.getPartyByMember(playerUUID);
                if (party == null) continue;
                boolean changed = recomputeOverheadForPlayer(playerUUID, party);
                if (changed && partiesSynced.add(party.getId())) {
                    syncClaimLimitsForTeamMembers(party.getId(), null);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error in periodic overhead refresh: {}", e.getMessage());
        }
    }

    private boolean recomputeOverheadForPlayer(UUID playerUUID, IServerPartyAPI party) {
        // Overhead = team claims/forceloads made by OTHER party members.
        // Personal claims are NOT included — they only count for the player who made them.
        UUID partyId = party.getId();
        TeamData teamData = getSavedData().teams.get(partyId);
        int claimOverhead = 0;
        int forceloadOverhead = 0;
        if (teamData != null) {
            for (Map.Entry<UUID, Integer> entry : teamData.claimCountByPlayer.entrySet()) {
                if (!entry.getKey().equals(playerUUID)) {
                    claimOverhead += Math.max(0, entry.getValue());
                }
            }
            for (Map.Entry<UUID, Integer> entry : teamData.forceloadCountByPlayer.entrySet()) {
                if (!entry.getKey().equals(playerUUID)) {
                    forceloadOverhead += Math.max(0, entry.getValue());
                }
            }
        }
        Integer prevClaims = cachedClaimOverhead.put(playerUUID, claimOverhead);
        Integer prevForceloads = cachedForceloadOverhead.put(playerUUID, forceloadOverhead);
        return (prevClaims == null || prevClaims != claimOverhead ||
                prevForceloads == null || prevForceloads != forceloadOverhead);
    }

    public int getTeamClaimOverheadForPlayer(UUID playerUUID) {
        return cachedClaimOverhead.getOrDefault(playerUUID, 0);
    }

    public int getTeamForceloadOverheadForPlayer(UUID playerUUID) {
        return cachedForceloadOverhead.getOrDefault(playerUUID, 0);
    }

    private void updateOverheadCacheForParty(UUID partyId) {
        try {
            IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
            if (party == null) return;
            var members = party.getMemberInfoStream().toList();
            for (var member : members) {
                recomputeOverheadForPlayer(member.getUUID(), party);
            }
        } catch (Exception e) {
            LOGGER.debug("Error updating overhead cache for party {}: {}", partyId, e.getMessage());
        }
    }

    @Nullable
    private UUID findPartyForTrackedClaim(ClaimPos pos) {
        for (Map.Entry<UUID, TeamData> entry : getSavedData().teams.entrySet()) {
            if (entry.getValue().trackedClaims.contains(pos)) return entry.getKey();
        }
        return null;
    }

    private void syncClaimLimitsForTeamMembers(UUID partyId, @Nullable UUID excludePlayerId) {
        try {
            IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
            if (party == null) return;
            IServerClaimsManagerAPI claimsApi = OpenPACServerAPI.get(server).getServerClaimsManager();
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigs();
            int maxClaimDistance = ServerConfig.CONFIG.maxClaimDistance.get();

            party.getMemberInfoStream().forEach(member -> {
                UUID memberId = member.getUUID();
                if (memberId.equals(excludePlayerId)) return;
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player == null) return;
                try {
                    COMPUTING_OVERHEAD.set(true);
                    int rawClaimCount;
                    int rawForceloadCount;
                    try {
                        var playerInfo = claimsApi.getPlayerInfo(memberId);
                        if (playerInfo == null) return;
                        rawClaimCount = playerInfo.getClaimCount();
                        rawForceloadCount = playerInfo.getForceloadCount();
                    } finally {
                        COMPUTING_OVERHEAD.remove();
                    }
                    int totalClaimCount = rawClaimCount + cachedClaimOverhead.getOrDefault(memberId, 0);
                    int totalForceloadCount = rawForceloadCount + cachedForceloadOverhead.getOrDefault(memberId, 0);
                    // Force loading values when this member has team overhead so the client
                    // uses the server-computed count instead of its local (overhead-unaware) count
                    boolean memberHasOverhead = cachedClaimOverhead.getOrDefault(memberId, 0) > 0
                            || cachedForceloadOverhead.getOrDefault(memberId, 0) > 0;
                    boolean alwaysUseLoadingValues = memberHasOverhead
                            || ServerConfig.CONFIG.claimsSynchronization.get() == ServerConfig.ClaimsSyncType.NOT_SYNCED;
                    IPlayerConfigAPI memberConfig = configManager.getLoadedConfig(memberId);
                    int claimLimit = claimsApi.getPlayerBaseClaimLimit(player)
                            + memberConfig.getEffective(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
                    int forceloadLimit = claimsApi.getPlayerBaseForceloadLimit(player)
                            + memberConfig.getEffective(PlayerConfigOptions.BONUS_CHUNK_FORCELOADS);
                    ClientboundClaimLimitsPacket packet = new ClientboundClaimLimitsPacket(
                            totalClaimCount, totalForceloadCount,
                            claimLimit, forceloadLimit,
                            maxClaimDistance, alwaysUseLoadingValues);
                    packet.prepare();
                    OpenPartiesAndClaims.INSTANCE.getPacketHandler().sendToPlayer(player, packet);
                } catch (Exception e) {
                    LOGGER.warn("[TeamClaims] Failed claim limits sync for {}: {}", memberId, e.getMessage());
                }
            });
        } catch (Exception e) {
            LOGGER.warn("[TeamClaims] Error syncing team claim limits: {}", e.getMessage());
        }
    }

    // ==================== Budget Checks (called by bridge handler) ====================

    @Nullable
    public ClaimResult<PlayerChunkClaim> checkTeamClaimBudget(UUID playerId, boolean forceLoaded) {
        var partyManager = OpenPACServerAPI.get(server).getPartyManager();
        var party = partyManager.getPartyByMember(playerId);
        if (party == null) return null;

        IServerClaimsManagerAPI claimsAPI = OpenPACServerAPI.get(server).getServerClaimsManager();
        IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigs();
        List<String> overClaimBudget = new ArrayList<>();
        List<String> overForceloadBudget = new ArrayList<>();

        // Compute total team claims/forceloads from TeamData
        TeamData teamData = getSavedData().teams.get(party.getId());
        int totalTeamClaims = 0;
        int totalTeamForceloads = 0;
        if (teamData != null) {
            for (int count : teamData.claimCountByPlayer.values()) totalTeamClaims += Math.max(0, count);
            for (int count : teamData.forceloadCountByPlayer.values()) totalTeamForceloads += Math.max(0, count);
        }
        final int fTotalTeamClaims = totalTeamClaims;
        final int fTotalTeamForceloads = totalTeamForceloads;

        // Check ALL members (including self) — each member's displayed count =
        // personal_claims + total_team_claims. After the new team claim: + 1.
        COMPUTING_OVERHEAD.set(true);
        try {
            party.getMemberInfoStream().forEach(member -> {
                UUID memberId = member.getUUID();
                var memberInfo = claimsAPI.getPlayerInfo(memberId);
                if (memberInfo == null) return;

                int rawClaimCount = memberInfo.getClaimCount(); // raw (no overhead) because COMPUTING_OVERHEAD=true
                int memberTeamClaims = teamData != null ? Math.max(0, teamData.claimCountByPlayer.getOrDefault(memberId, 0)) : 0;
                int memberPersonalClaims = rawClaimCount - memberTeamClaims;
                int memberTotalAfterClaim = memberPersonalClaims + fTotalTeamClaims + 1;

                int memberClaimLimit = claimsAPI.getPlayerBaseClaimLimit(memberId)
                        + configManager.getLoadedConfig(memberId).getEffective(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
                if (memberTotalAfterClaim > memberClaimLimit) overClaimBudget.add(member.getUsername());

                if (forceLoaded) {
                    int rawForceloadCount = memberInfo.getForceloadCount();
                    int memberTeamForceloads = teamData != null ? Math.max(0, teamData.forceloadCountByPlayer.getOrDefault(memberId, 0)) : 0;
                    int memberPersonalForceloads = rawForceloadCount - memberTeamForceloads;
                    int memberTotalForceloadsAfter = memberPersonalForceloads + fTotalTeamForceloads + 1;

                    int memberForceloadLimit = claimsAPI.getPlayerBaseForceloadLimit(memberId)
                            + configManager.getLoadedConfig(memberId).getEffective(PlayerConfigOptions.BONUS_CHUNK_FORCELOADS);
                    if (memberTotalForceloadsAfter > memberForceloadLimit) overForceloadBudget.add(member.getUsername());
                }
            });
        } finally {
            COMPUTING_OVERHEAD.remove();
        }

        if (!overClaimBudget.isEmpty() || !overForceloadBudget.isEmpty()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                if (!overClaimBudget.isEmpty()) {
                    player.sendSystemMessage(Component.literal(
                            "\u00A7c[Team Claims] Cannot claim: " + String.join(", ", overClaimBudget)
                                    + " would exceed their claim limit."));
                }
                if (!overForceloadBudget.isEmpty()) {
                    player.sendSystemMessage(Component.literal(
                            "\u00A7c[Team Claims] Cannot claim: " + String.join(", ", overForceloadBudget)
                                    + " would exceed their forceload limit."));
                }
            }
            return new ClaimResult<>(null, ClaimResult.Type.CLAIM_LIMIT_REACHED);
        }
        return null;
    }

    @Nullable
    public ClaimResult<PlayerChunkClaim> checkTeamForceloadBudget(UUID requesterId, PlayerChunkClaim currentClaim) {
        var partyManager = OpenPACServerAPI.get(server).getPartyManager();
        var party = partyManager.getPartyByMember(currentClaim.getPlayerId());
        if (party == null) return null;

        IServerClaimsManagerAPI claimsAPI = OpenPACServerAPI.get(server).getServerClaimsManager();
        IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigs();
        List<String> overBudget = new ArrayList<>();

        // Compute total team forceloads
        TeamData teamData = getSavedData().teams.get(party.getId());
        int totalTeamForceloads = 0;
        if (teamData != null) {
            for (int count : teamData.forceloadCountByPlayer.values()) totalTeamForceloads += Math.max(0, count);
        }
        final int fTotalTeamForceloads = totalTeamForceloads;

        // Check ALL members — after enabling forceload, each member's displayed forceload count =
        // personal_forceloads + total_team_forceloads + 1.
        COMPUTING_OVERHEAD.set(true);
        try {
            party.getMemberInfoStream().forEach(member -> {
                UUID memberId = member.getUUID();
                var memberInfo = claimsAPI.getPlayerInfo(memberId);
                if (memberInfo == null) return;
                int rawForceloadCount = memberInfo.getForceloadCount();
                int memberTeamForceloads = teamData != null ? Math.max(0, teamData.forceloadCountByPlayer.getOrDefault(memberId, 0)) : 0;
                int memberPersonalForceloads = rawForceloadCount - memberTeamForceloads;
                int memberTotal = memberPersonalForceloads + fTotalTeamForceloads + 1;
                int memberForceloadLimit = claimsAPI.getPlayerBaseForceloadLimit(memberId)
                        + configManager.getLoadedConfig(memberId).getEffective(PlayerConfigOptions.BONUS_CHUNK_FORCELOADS);
                if (memberTotal > memberForceloadLimit) overBudget.add(member.getUsername());
            });
        } finally {
            COMPUTING_OVERHEAD.remove();
        }

        if (!overBudget.isEmpty()) {
            ServerPlayer player = server.getPlayerList().getPlayer(requesterId);
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                        "\u00A7c[Team Claims] Cannot enable forceload: " + String.join(", ", overBudget)
                                + " would exceed their forceload limit."));
            }
            return new ClaimResult<>(currentClaim, ClaimResult.Type.FORCELOAD_LIMIT_REACHED);
        }
        return null;
    }

    // ==================== Utility ====================

    public boolean areInSameTeam(UUID player1, UUID player2) {
        if (player1 == null || player2 == null) return false;
        if (player1.equals(player2)) return true;
        IServerPartyAPI party = getPlayerParty(player1);
        if (party == null) return false;
        return party.getMemberInfo(player2) != null;
    }

    @Nullable
    private IServerPartyAPI getPlayerParty(UUID playerUUID) {
        try {
            return OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(playerUUID);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public TeamData getTeamData(UUID partyId) { return getSavedData().teams.get(partyId); }
    public TeamData getOrCreateTeamData(UUID partyId) {
        return getSavedData().teams.computeIfAbsent(partyId, id -> {
            getSavedData().setDirty();
            return new TeamData(id);
        });
    }
    public Map<UUID, TeamData> getAllTeams() { return Collections.unmodifiableMap(getSavedData().teams); }
    public TeamForceLoadHandler getForceLoadHandler() { return forceLoadHandler; }

    public boolean isTeamSubConfigIndex(UUID playerUUID, int subConfigIndex) {
        if (subConfigIndex == -1) return false;
        return getTeamSubIndex(playerUUID) == subConfigIndex;
    }

    public void onPlayerLeftParty(UUID partyId, UUID playerUUID) {
        TeamData teamData = getSavedData().teams.get(partyId);
        if (teamData == null) return;
        List<ClaimPos> toRemove = new ArrayList<>();
        for (Map.Entry<ClaimPos, UUID> entry : teamData.claimOwners.entrySet()) {
            if (entry.getValue().equals(playerUUID)) toRemove.add(entry.getKey());
        }
        for (ClaimPos pos : toRemove) {
            teamData.trackedClaims.remove(pos);
            teamData.claimOwners.remove(pos);
            if (teamData.forceLoadedChunks.remove(pos)) {
                forceLoadHandler.removeForceLoad(pos.dimension, pos.x, pos.z);
            }
        }
        teamData.claimCountByPlayer.remove(playerUUID);
        teamData.forceloadCountByPlayer.remove(playerUUID);
        if (!toRemove.isEmpty()) getSavedData().setDirty();

        // Clean caches for the player who left
        teamSubIndexCache.remove(playerUUID);
        cachedClaimOverhead.remove(playerUUID);
        cachedForceloadOverhead.remove(playerUUID);

        // Recompute overhead for remaining members
        updateOverheadCacheForParty(partyId);
        syncClaimLimitsForTeamMembers(partyId, null);
    }

    public void invalidateCacheForPlayer(UUID playerUUID) {
        teamSubIndexCache.remove(playerUUID);
    }

    // ==================== Data Types ====================

    public static class ClaimPos {
        public final ResourceLocation dimension;
        public final int x;
        public final int z;
        public ClaimPos(ResourceLocation dimension, int x, int z) {
            this.dimension = dimension;
            this.x = x;
            this.z = z;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClaimPos claimPos = (ClaimPos) o;
            return x == claimPos.x && z == claimPos.z && dimension.equals(claimPos.dimension);
        }
        @Override public int hashCode() { return Objects.hash(dimension, x, z); }
        @Override public String toString() { return "[" + x + ", " + z + "] in " + dimension; }
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", dimension.toString());
            tag.putInt("x", x);
            tag.putInt("z", z);
            return tag;
        }
        public static ClaimPos load(CompoundTag tag) {
            return new ClaimPos(ResourceLocation.parse(tag.getString("dim")), tag.getInt("x"), tag.getInt("z"));
        }
    }

    public static class TeamData {
        public UUID partyId;
        public final Set<ClaimPos> trackedClaims = new LinkedHashSet<>();
        public final Set<ClaimPos> forceLoadedChunks = new LinkedHashSet<>();
        public final Map<ClaimPos, UUID> claimOwners = new HashMap<>();
        public final Map<UUID, Integer> claimCountByPlayer = new HashMap<>();
        public final Map<UUID, Integer> forceloadCountByPlayer = new HashMap<>();
        public TeamData(UUID partyId) { this.partyId = partyId; }
        public TeamData() {}
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("partyId", partyId);
            ListTag claimsList = new ListTag();
            for (ClaimPos pos : trackedClaims) claimsList.add(pos.save());
            tag.put("trackedClaims", claimsList);
            ListTag forceList = new ListTag();
            for (ClaimPos pos : forceLoadedChunks) forceList.add(pos.save());
            tag.put("forceLoadedChunks", forceList);
            return tag;
        }
        public static TeamData load(CompoundTag tag) {
            TeamData data = new TeamData();
            data.partyId = tag.getUUID("partyId");
            ListTag claimsList = tag.getList("trackedClaims", Tag.TAG_COMPOUND);
            for (int i = 0; i < claimsList.size(); i++) data.trackedClaims.add(ClaimPos.load(claimsList.getCompound(i)));
            ListTag forceList = tag.getList("forceLoadedChunks", Tag.TAG_COMPOUND);
            for (int i = 0; i < forceList.size(); i++) data.forceLoadedChunks.add(ClaimPos.load(forceList.getCompound(i)));
            return data;
        }
    }

    public static class TeamClaimSavedData extends SavedData {
        final Map<UUID, TeamData> teams = new HashMap<>();
        public TeamClaimSavedData() {}
        public static TeamClaimSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
            TeamClaimSavedData data = new TeamClaimSavedData();
            ListTag teamsList = tag.getList("teams", Tag.TAG_COMPOUND);
            for (int i = 0; i < teamsList.size(); i++) {
                TeamData teamData = TeamData.load(teamsList.getCompound(i));
                data.teams.put(teamData.partyId, teamData);
            }
            return data;
        }
        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag teamsList = new ListTag();
            for (TeamData teamData : teams.values()) teamsList.add(teamData.save());
            tag.put("teams", teamsList);
            return tag;
        }
    }
}
