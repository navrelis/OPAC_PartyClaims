package xaero.pac.teamclaims;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.IServerClaimsManager;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.IServerPlayerClaimInfo;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.IPlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.localization.api.IAdaptiveLocalizerAPI;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;

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
    /**
     * While true, {@link #onChunkChange} still does all the TeamData bookkeeping but skips the
     * party-wide overhead recomputation and claim limits packet. Used for batch operations
     * (the claim transfer of a leaving member), which do one update at the end instead of one
     * per claim. Only ever touched on the server thread.
     */
    private boolean deferTeamLimitSync = false;

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
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigManager();
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
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigManager();
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
        // Order matters: the per-claim owner/count tracking isn't persisted, so it has to be
        // rebuilt (and the claims of members who left while the server was down handed over to the
        // party owner) while those members still have their team sub-config. Only then is the
        // stored membership caught up with the real parties, which removes those sub-configs.
        validateSavedData();
        TeamConfigManager tcm = TeamClaimsInit.getTeamConfigManager();
        if (tcm != null) tcm.reconcileMembershipWithParties();
        warnAboutNativePartyClaims();
        LOGGER.info("Team Claims system ready — tracking {} parties", getSavedData().teams.size());
    }

    /**
     * Team Claims and upstream's native party claims ({@code partyOwnedClaims}) both work, and
     * their numbers agree — but they share one pool per player, which is worth stating once.
     */
    private void warnAboutNativePartyClaims() {
        if (!ServerConfig.CONFIG.partyOwnedClaims.get()) return;
        LOGGER.warn("[TeamClaims] The server option 'partyOwnedClaims' is enabled while Team Claims is active. "
                + "Both features share one claim/forceload pool per player: native party claims are owned by the party "
                + "owner, so they are counted as the owner's personal claims and therefore reduce the claim budget of "
                + "the WHOLE team, while every member's own (un-bonused) limit still caps the team total — "
                + "'claimBonusPerPartyMember'/'claimBonusForPartyOwner' only raise the owner's limit. A native party "
                + "claim made with the owner's 'team_*' sub-config is indistinguishable from, and is treated as, a "
                + "team claim of the owner.");
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
            if (affectedPartyId != null) updateOverheadAndSync(affectedPartyId);
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
            updateOverheadAndSync(partyId);
        } else {
            UUID affectedPartyId = findPartyForTrackedClaim(pos);
            removeFromAllTeamTracking(pos);
            if (affectedPartyId != null) updateOverheadAndSync(affectedPartyId);
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
            Map<UUID, List<ClaimPos>> orphanedByFormerMember = new LinkedHashMap<>();
            teamData.claimOwners.clear();
            teamData.claimCountByPlayer.clear();
            teamData.forceloadCountByPlayer.clear();

            for (ClaimPos pos : teamData.trackedClaims) {
                IPlayerChunkClaimAPI claim = claimsManager.get(pos.dimension, pos.x, pos.z);
                if (claim == null || !isTeamClaim(claim)) {
                    claimsToRemove.add(pos);
                } else {
                    UUID claimOwner = claim.getPlayerId();
                    // Rebuild the counts for every surviving claim, including the ones whose
                    // technical owner is no longer a party member: those are transferred to the
                    // current party owner below and the transfer needs correct "before" numbers.
                    teamData.claimOwners.put(pos, claimOwner);
                    teamData.claimCountByPlayer.merge(claimOwner, 1, Integer::sum);
                    if (claim.isForceloadable() && teamData.forceLoadedChunks.contains(pos)) {
                        teamData.forceloadCountByPlayer.merge(claimOwner, 1, Integer::sum);
                    }
                    if (party.getMemberInfo(claimOwner) == null)
                        orphanedByFormerMember.computeIfAbsent(claimOwner, k -> new ArrayList<>()).add(pos);
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

            // Members that left (or were kicked) while the server was down, or that the membership
            // poll missed: their team claims go to the current party owner, exactly like a normal
            // leave. Anything that can't be transferred falls back to the legacy behaviour and
            // simply stops being tracked (it stays a personal claim of the former member).
            for (Map.Entry<UUID, List<ClaimPos>> orphaned : orphanedByFormerMember.entrySet()) {
                UUID formerMemberId = orphaned.getKey();
                int transferred = transferTeamClaimsToOwner(party, formerMemberId, orphaned.getValue());
                dropTrackedClaimsOf(teamData, formerMemberId);
                invalidateCachesForPlayer(formerMemberId);
                if (transferred > 0) {
                    LOGGER.info("[TeamClaims] Transferred {} team claim(s) of former party member {} to the party owner {}",
                            transferred, formerMemberId, party.getOwner().getUUID());
                    notifyTeamClaimsTransferred(partyId, formerMemberId, transferred);
                }
            }
            if (!orphanedByFormerMember.isEmpty()) updateOverheadCacheForParty(partyId);
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

    /**
     * Recomputes every member's overhead and pushes fresh claim limits, unless a batch operation
     * has asked for it to be done once at the end instead of once per changed chunk.
     */
    private void updateOverheadAndSync(UUID partyId) {
        if (deferTeamLimitSync) return;
        updateOverheadCacheForParty(partyId);
        syncClaimLimitsForTeamMembers(partyId, null);
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

    /**
     * Pushes a fresh claim limits packet to every online member of the party.
     * <p>
     * The numbers themselves no longer have to be assembled here: the Common hook in
     * {@code PlayerClaimInfo.getClaimCount()/getForceloadCount()} already adds each member's team
     * overhead, so {@code ClaimingModes.PLAYER}'s limits builder — and with it OPAC's own
     * {@code ClaimsManagerSynchronizer.syncClaimLimits} — reports the overhead-adjusted counts,
     * while the other claiming modes keep their stock values. The hook in
     * {@code ClaimsManagerSynchronizer.syncClaimLimits} also takes care of forcing
     * {@code alwaysUseLoadingValues} while a member has overhead. All this does is make the update
     * immediate instead of waiting for OPAC's once-per-second limits check.
     */
    private void syncClaimLimitsForTeamMembers(UUID partyId, @Nullable UUID excludePlayerId) {
        try {
            IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
            if (party == null) return;
            // The public API only exposes IServerClaimsManagerAPI, but the claim limits sync lives on
            // the internal IServerClaimsManager. The runtime instance always implements both.
            IServerClaimsManager<?, ?, ?> internalClaimsManager =
                    (IServerClaimsManager<?, ?, ?>) OpenPACServerAPI.get(server).getServerClaimsManager();
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigManager();

            party.getMemberInfoStream().forEach(member -> {
                UUID memberId = member.getUUID();
                if (memberId.equals(excludePlayerId)) return;
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player == null) return;
                try {
                    IPlayerConfig memberConfig = (IPlayerConfig) configManager.getLoadedConfig(memberId);
                    internalClaimsManager.getClaimsManagerSynchronizer().syncClaimLimits(memberConfig, player);
                } catch (Exception e) {
                    LOGGER.warn("[TeamClaims] Failed claim limits sync for {}: {}", memberId, e.getMessage());
                }
            });
        } catch (Exception e) {
            LOGGER.warn("[TeamClaims] Error syncing team claim limits: {}", e.getMessage());
        }
    }

    /**
     * Same as {@link #syncClaimLimitsForTeamMembers} for a single player, used for a player who is
     * no longer a party member but whose numbers just changed (a leaver whose claims were moved).
     */
    private void syncClaimLimitsForPlayer(UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return;
        try {
            IServerClaimsManager<?, ?, ?> internalClaimsManager =
                    (IServerClaimsManager<?, ?, ?>) OpenPACServerAPI.get(server).getServerClaimsManager();
            IPlayerConfig config = (IPlayerConfig) OpenPACServerAPI.get(server)
                    .getPlayerConfigManager().getLoadedConfig(playerId);
            internalClaimsManager.getClaimsManagerSynchronizer().syncClaimLimits(config, player);
        } catch (Exception e) {
            LOGGER.warn("[TeamClaims] Failed claim limits sync for {}: {}", playerId, e.getMessage());
        }
    }

    // ==================== Budget Checks (called by bridge handler) ====================

    @Nullable
    public ClaimResult<PlayerChunkClaim> checkTeamClaimBudget(UUID playerId, boolean forceLoaded,
                                                              @Nullable PlayerChunkClaim replacedClaim) {
        var partyManager = OpenPACServerAPI.get(server).getPartyManager();
        var party = partyManager.getPartyByMember(playerId);
        if (party == null) return null;

        IServerClaimsManagerAPI claimsAPI = OpenPACServerAPI.get(server).getServerClaimsManager();
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

        // What the new claim actually adds. Re-claiming a chunk that already is a team claim of this
        // party leaves the team totals untouched and only moves the per-member attribution, and a
        // chunk that is taken over from one of the members stops counting for that member.
        boolean replacedIsOwnTeamClaim = replacedClaim != null && isTeamClaim(replacedClaim)
                && party.getMemberInfo(replacedClaim.getPlayerId()) != null;
        final int teamClaimDelta = replacedIsOwnTeamClaim ? 0 : 1;
        final int teamForceloadDelta =
                !forceLoaded ? 0 : (replacedIsOwnTeamClaim && replacedClaim.isForceloadable() ? 0 : 1);
        if (teamClaimDelta == 0 && teamForceloadDelta == 0)
            return null;//nothing is added to the team's totals, so nobody's count can grow
        final UUID replacedMemberId = replacedIsOwnTeamClaim || replacedClaim == null ? null : replacedClaim.getPlayerId();
        final boolean replacedWasForceloaded = replacedClaim != null && replacedClaim.isForceloadable();

        // Check ALL members (including self) — each member's displayed count =
        // personal_claims + total_team_claims, plus what this claim adds.
        COMPUTING_OVERHEAD.set(true);
        try {
            party.getMemberInfoStream().forEach(member -> {
                UUID memberId = member.getUUID();
                var memberInfo = claimsAPI.getPlayerInfo(memberId);
                if (memberInfo == null) return;

                int rawClaimCount = memberInfo.getClaimCount(); // raw (no overhead) because COMPUTING_OVERHEAD=true
                int memberTeamClaims = teamData != null ? Math.max(0, teamData.claimCountByPlayer.getOrDefault(memberId, 0)) : 0;
                int memberPersonalClaims = Math.max(0, rawClaimCount - memberTeamClaims);
                if (memberId.equals(replacedMemberId))
                    memberPersonalClaims = Math.max(0, memberPersonalClaims - 1);
                int memberTotalAfterClaim = memberPersonalClaims + fTotalTeamClaims + teamClaimDelta;

                // Exactly the limit OPAC itself enforces in tryToClaimHelper/tryToForceloadHelper
                int memberClaimLimit = claimsAPI.getPlayerFullClaimLimit(memberId);
                if (memberTotalAfterClaim > memberClaimLimit) overClaimBudget.add(member.getUsername());

                if (teamForceloadDelta > 0) {
                    int rawForceloadCount = memberInfo.getForceloadCount();
                    int memberTeamForceloads = teamData != null ? Math.max(0, teamData.forceloadCountByPlayer.getOrDefault(memberId, 0)) : 0;
                    int memberPersonalForceloads = Math.max(0, rawForceloadCount - memberTeamForceloads);
                    if (memberId.equals(replacedMemberId) && replacedWasForceloaded)
                        memberPersonalForceloads = Math.max(0, memberPersonalForceloads - 1);
                    int memberTotalForceloadsAfter = memberPersonalForceloads + fTotalTeamForceloads + teamForceloadDelta;

                    int memberForceloadLimit = claimsAPI.getPlayerFullForceloadLimit(memberId);
                    if (memberTotalForceloadsAfter > memberForceloadLimit) overForceloadBudget.add(member.getUsername());
                }
            });
        } finally {
            COMPUTING_OVERHEAD.remove();
        }

        if (!overClaimBudget.isEmpty() || !overForceloadBudget.isEmpty()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                IAdaptiveLocalizerAPI localizer = OpenPACServerAPI.get(server).getAdaptiveTextLocalizer();
                if (!overClaimBudget.isEmpty()) {
                    player.sendSystemMessage(localizer.getFor(player, "gui.xaero_pac_team_claims_claim_budget",
                            String.join(", ", overClaimBudget)).withStyle(ChatFormatting.RED));
                }
                if (!overForceloadBudget.isEmpty()) {
                    player.sendSystemMessage(localizer.getFor(player, "gui.xaero_pac_team_claims_forceload_budget_claim",
                            String.join(", ", overForceloadBudget)).withStyle(ChatFormatting.RED));
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
                int memberPersonalForceloads = Math.max(0, rawForceloadCount - memberTeamForceloads);
                int memberTotal = memberPersonalForceloads + fTotalTeamForceloads + 1;
                // Exactly the limit OPAC itself enforces
                int memberForceloadLimit = claimsAPI.getPlayerFullForceloadLimit(memberId);
                if (memberTotal > memberForceloadLimit) overBudget.add(member.getUsername());
            });
        } finally {
            COMPUTING_OVERHEAD.remove();
        }

        if (!overBudget.isEmpty()) {
            ServerPlayer player = server.getPlayerList().getPlayer(requesterId);
            if (player != null) {
                player.sendSystemMessage(OpenPACServerAPI.get(server).getAdaptiveTextLocalizer()
                        .getFor(player, "gui.xaero_pac_team_claims_forceload_budget", String.join(", ", overBudget))
                        .withStyle(ChatFormatting.RED));
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

    /**
     * A member left or was kicked from a party that still exists. Every team claim technically
     * owned by them is re-assigned to the current party owner (as an owner team claim), so that the
     * team keeps its territory. Must be called <b>before</b> the leaver's team sub-config is
     * removed. Works with the leaver and/or the owner offline.
     * <p>
     * Whatever cannot be transferred keeps the legacy behaviour: it stops being tracked and stays a
     * personal claim of the leaver (their team sub-config is removed right after this, so the claim
     * stops being a team claim).
     */
    public void onPlayerLeftParty(UUID partyId, UUID playerUUID) {
        TeamData teamData = getSavedData().teams.get(partyId);
        if (teamData == null) {
            invalidateCachesForPlayer(playerUUID);
            return;
        }
        int transferred = 0;
        List<ClaimPos> owned = getTrackedClaimsOf(teamData, playerUUID);
        if (!owned.isEmpty()) {
            IServerPartyAPI party = null;
            try {
                party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
            } catch (Exception e) {
                LOGGER.warn("[TeamClaims] Could not look up party {} while {} left: {}", partyId, playerUUID, e.getMessage());
            }
            if (party != null)
                transferred = transferTeamClaimsToOwner(party, playerUUID, owned);
        }
        dropTrackedClaimsOf(teamData, playerUUID);

        invalidateCachesForPlayer(playerUUID);
        // Recompute overhead for remaining members, then sync everyone whose numbers changed
        updateOverheadCacheForParty(partyId);
        syncClaimLimitsForTeamMembers(partyId, null);
        syncClaimLimitsForPlayer(playerUUID);//no longer a member, but their own count just dropped
        if (transferred > 0) notifyTeamClaimsTransferred(partyId, playerUUID, transferred);
    }

    /**
     * Re-assigns the given tracked team claims of {@code leaverId} to the current party owner,
     * keeping the forceloadable flag, in every dimension.
     * <p>
     * This is budget-neutral — the team's totals don't change and every member's displayed count
     * stays {@code personal + team total} — so it deliberately does <b>not</b> go through
     * {@code tryToClaim*} and its limit checks. It uses the same low-level
     * {@link IServerClaimsManagerAPI#claim} call OPAC's own claim replacement/transfer tasks use,
     * which fires the normal claims tracker callbacks: {@link #onChunkChange} therefore does all
     * the TeamData bookkeeping (owner, per-player counts, force-load state), OPAC moves its own
     * force-load tickets and marks its saved data dirty, and the clients are synced.
     *
     * @return the number of claims that were actually transferred
     */
    private int transferTeamClaimsToOwner(IServerPartyAPI party, UUID leaverId, List<ClaimPos> positions) {
        if (positions.isEmpty()) return 0;
        UUID ownerId = party.getOwner().getUUID();
        if (ownerId.equals(leaverId)) {
            //can't happen through OPAC: the owner can't leave without destroying the party
            LOGGER.warn("[TeamClaims] {} left party {} as its owner — leaving their {} team claim(s) alone.",
                    leaverId, party.getId(), positions.size());
            return 0;
        }
        IServerClaimsManagerAPI claimsAPI = OpenPACServerAPI.get(server).getServerClaimsManager();
        if (hasClaimTaskInProgress(claimsAPI, leaverId) || hasClaimTaskInProgress(claimsAPI, ownerId)) {
            LOGGER.warn("[TeamClaims] Not transferring {} team claim(s) of {} to the party owner {}: a claim "
                    + "transfer/replacement is in progress. They stay personal claims of the leaving player.",
                    positions.size(), leaverId, ownerId);
            return 0;
        }
        ensureTeamSubConfig(ownerId);//works offline, creates the sub-config if the owner has none yet
        int ownerSubIndex = getTeamSubIndex(ownerId);
        if (ownerSubIndex == -1) {
            LOGGER.warn("[TeamClaims] Not transferring {} team claim(s) of {}: the party owner {} has no team sub-config.",
                    positions.size(), leaverId, ownerId);
            return 0;
        }
        int transferred = 0;
        boolean previousDefer = deferTeamLimitSync;
        deferTeamLimitSync = true;
        try {
            for (ClaimPos pos : positions) {
                IPlayerChunkClaimAPI current = claimsAPI.get(pos.dimension, pos.x, pos.z);
                if (current == null || !leaverId.equals(current.getPlayerId())) {
                    LOGGER.warn("[TeamClaims] Not transferring the tracked team claim at {}: it is no longer owned by {}.",
                            pos, leaverId);
                    continue;
                }
                try {
                    //null only when claims are disabled server-wide, in which case nothing changed
                    if (claimsAPI.claim(pos.dimension, ownerId, ownerSubIndex, pos.x, pos.z, current.isForceloadable()) != null)
                        transferred++;
                    else
                        LOGGER.warn("[TeamClaims] Could not transfer the team claim at {}: claims are disabled.", pos);
                } catch (Exception e) {
                    LOGGER.warn("[TeamClaims] Failed to transfer the team claim at {} from {} to {}: {}",
                            pos, leaverId, ownerId, e.getMessage());
                }
            }
        } finally {
            deferTeamLimitSync = previousDefer;
        }
        return transferred;
    }

    /**
     * OPAC refuses claim changes for a player while one of its spread-out claim transfer/replacement
     * tasks is running for them. Those tasks iterate the player's own claims, so a batch re-assign
     * underneath them is not safe.
     */
    private boolean hasClaimTaskInProgress(IServerClaimsManagerAPI claimsAPI, UUID playerId) {
        try {
            var info = claimsAPI.getPlayerInfo(playerId);
            if (info instanceof IServerPlayerClaimInfo<?> internalInfo)
                return internalInfo.isTransferInProgress() || internalInfo.isReplacementInProgress();
        } catch (Exception e) {
            LOGGER.warn("[TeamClaims] Could not check the claim task state of {}: {}", playerId, e.getMessage());
            return true;
        }
        return false;
    }

    private List<ClaimPos> getTrackedClaimsOf(TeamData teamData, UUID playerUUID) {
        List<ClaimPos> result = new ArrayList<>();
        for (Map.Entry<ClaimPos, UUID> entry : teamData.claimOwners.entrySet()) {
            if (playerUUID.equals(entry.getValue())) result.add(entry.getKey());
        }
        return result;
    }

    /** Legacy behaviour for claims that stay with the leaving player: drop them from tracking. */
    private void dropTrackedClaimsOf(TeamData teamData, UUID playerUUID) {
        List<ClaimPos> remaining = getTrackedClaimsOf(teamData, playerUUID);
        for (ClaimPos pos : remaining) {
            teamData.trackedClaims.remove(pos);
            teamData.claimOwners.remove(pos);
            if (teamData.forceLoadedChunks.remove(pos)) {
                forceLoadHandler.removeForceLoad(pos.dimension, pos.x, pos.z);
            }
        }
        boolean hadClaimCount = teamData.claimCountByPlayer.remove(playerUUID) != null;
        boolean hadForceloadCount = teamData.forceloadCountByPlayer.remove(playerUUID) != null;
        if (!remaining.isEmpty() || hadClaimCount || hadForceloadCount) getSavedData().setDirty();
    }

    private void notifyTeamClaimsTransferred(UUID partyId, UUID leaverId, int count) {
        try {
            IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
            if (party == null) return;
            IAdaptiveLocalizerAPI localizer = OpenPACServerAPI.get(server).getAdaptiveTextLocalizer();
            String countText = String.valueOf(count);
            ServerPlayer leaver = server.getPlayerList().getPlayer(leaverId);
            if (leaver != null) {
                leaver.sendSystemMessage(localizer.getFor(leaver,
                        "gui.xaero_pac_team_claims_transfer_leaver", countText).withStyle(ChatFormatting.YELLOW));
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(party.getOwner().getUUID());
            if (owner != null) {
                owner.sendSystemMessage(localizer.getFor(owner,
                        "gui.xaero_pac_team_claims_transfer_owner", resolvePlayerName(leaverId), countText)
                        .withStyle(ChatFormatting.YELLOW));
            }
        } catch (Exception e) {
            LOGGER.warn("[TeamClaims] Failed to announce the team claim transfer of {}: {}", leaverId, e.getMessage());
        }
    }

    private String resolvePlayerName(UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) return online.getGameProfile().getName();
        try {
            var info = OpenPACServerAPI.get(server).getServerClaimsManager().getPlayerInfo(playerId);
            String username = info == null ? null : info.getPlayerUsername();
            if (username != null && !username.isEmpty()) return username;
        } catch (Exception ignored) {}
        return playerId.toString();
    }

    private void invalidateCachesForPlayer(UUID playerUUID) {
        teamSubIndexCache.remove(playerUUID);
        cachedClaimOverhead.remove(playerUUID);
        cachedForceloadOverhead.remove(playerUUID);
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
