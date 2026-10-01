package xaero.pac.teamclaims;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.IServerPlayerClaimInfo;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.IPlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.localization.api.IAdaptiveLocalizerAPI;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the team claims of every party (which chunk is a team claim of which party, owned by which member, and
 * whether it is forceloaded), derives every member's team claim/forceload "overhead" from that, checks the shared
 * team budget, and hands the team claims of a leaving member over to the party owner.
 * <p>
 * Threading: server-thread confined. {@link TeamData}, the indexes and the caches are only ever touched on the server
 * thread. The single exception is a call of {@link #getTeamClaimOverheadForPlayer}/{@link
 * #getTeamForceloadOverheadForPlayer} from another thread (the client thread of an integrated server, via the
 * {@code PlayerClaimInfo} hook), which reads a {@link ConcurrentHashMap} snapshot that is refreshed at the end of every
 * tick in which a member's numbers may have changed.
 */
public class TeamClaimManager implements IClaimsManagerListenerAPI {

    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Safety net only: membership, claims and logins are all event-driven. Every minute, parties that vanished
     * without a removal event are cleaned up and the forceload activation is checked against who is online.
     */
    private static final int VALIDATION_INTERVAL = 1200;

    /** Re-entrancy guard: while true, {@code PlayerClaimInfo.getClaimCount()/getForceloadCount()} add no overhead. */
    public static final ThreadLocal<Boolean> COMPUTING_OVERHEAD = ThreadLocal.withInitial(() -> false);

    private final MinecraftServer server;
    private final TeamForceLoadHandler forceLoadHandler;
    @Nullable
    private TeamClaimSavedData savedData;
    private boolean serverReady = false;

    /** Every tracked team claim, to the party that tracks it. Kept in sync with {@link TeamData} by track/untrack. */
    private final Map<ClaimPos, UUID> claimToParty = new HashMap<>();
    /** Player to the index of their team sub-config, including -1 for "none" (no party, no team sub-config). */
    private final Map<UUID, Integer> teamSubIndexCache = new HashMap<>();
    /** Parties/players whose claim limits are re-sent at the end of the tick (batched). */
    private final Set<UUID> limitSyncParties = new LinkedHashSet<>();
    private final Set<UUID> limitSyncPlayers = new LinkedHashSet<>();
    /** Overhead snapshot for callers off the server thread, see the class description. */
    private final Map<UUID, Integer> offThreadClaimOverhead = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> offThreadForceloadOverhead = new ConcurrentHashMap<>();
    private int validationTickCounter = 0;
    /**
     * Forceload grace period: parties whose last online member left while {@code forceloadGraceMinutes} is above 0,
     * to the server tick at which their forceloads are released. Due entries are looked for once a second.
     */
    private final Map<UUID, Integer> pendingDeactivations = new HashMap<>();
    private int nextGraceCheckTick = 0;
    private final Map<UUID, Integer> forceloadGraceTicksOverrides = new HashMap<>();

    public TeamClaimManager(MinecraftServer server, TeamForceLoadHandler forceLoadHandler) {
        this.server = server;
        this.forceLoadHandler = forceLoadHandler;
    }

    private Map<UUID, TeamData> teams() {
        return savedData == null ? Collections.emptyMap() : savedData.teams;
    }

    private void markSavedDataDirty() {
        if (savedData != null) savedData.setDirty();
    }

    // ==================== Team sub-config lookup ====================

    /** The index of the player's team sub-config, -1 if they are in no team or have no team sub-config. */
    int getTeamSubIndex(UUID playerUUID) {
        Integer cached = teamSubIndexCache.get(playerUUID);
        if (cached != null) return cached;
        int index = lookUpTeamSubIndex(playerUUID);
        teamSubIndexCache.put(playerUUID, index);
        return index;
    }

    private int lookUpTeamSubIndex(UUID playerUUID) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        TeamConfig teamConfig = tcm == null ? null : tcm.getTeamConfigForPlayer(playerUUID);
        if (teamConfig == null) return -1;
        IPlayerConfigAPI playerConfig = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(playerUUID);
        IPlayerConfigAPI sub = playerConfig.getSubConfig(teamConfig.getSubConfigId());
        return sub == null ? -1 : sub.getSubIndex();
    }

    /**
     * Forgets the cached team sub-config index of a player. Called whenever their party membership, their team
     * config mapping or one of their team sub-configs changes.
     */
    public void invalidateTeamSubIndex(UUID playerUUID) {
        teamSubIndexCache.remove(playerUUID);
    }

    public boolean isTeamClaim(@Nullable IPlayerChunkClaimAPI claim) {
        if (claim == null) return false;
        int subIndex = claim.getSubConfigIndex();
        if (subIndex == -1) return false;
        int teamIndex = getTeamSubIndex(claim.getPlayerId());
        return teamIndex != -1 && subIndex == teamIndex;
    }

    /**
     * Whether both claims are team claims of the same team (party). Two hash lookups per claim, as it runs for every
     * player whenever they cross into a different claim. A claim of a former member that was not handed over is no
     * longer a team claim, so it never counts.
     */
    public boolean isSameTeamTerritory(@Nullable IPlayerChunkClaimAPI first, @Nullable IPlayerChunkClaimAPI second) {
        UUID firstParty = getTeamPartyOfClaim(first);
        return firstParty != null && firstParty.equals(getTeamPartyOfClaim(second));
    }

    /** The party whose team claim this is, null for no claim and for a claim that isn't a team claim. */
    @Nullable
    private UUID getTeamPartyOfClaim(@Nullable IPlayerChunkClaimAPI claim) {
        if (!isTeamClaim(claim)) return null;
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        TeamConfig teamConfig = tcm == null ? null : tcm.getTeamConfigForPlayer(claim.getPlayerId());
        return teamConfig == null ? null : teamConfig.getPartyId();
    }

    public boolean isTeamSubConfigIndex(UUID playerUUID, int subConfigIndex) {
        if (subConfigIndex == -1) return false;
        return getTeamSubIndex(playerUUID) == subConfigIndex;
    }

    /** Creates the player's team sub-config if they are a team member without one, and applies the team settings. */
    public void ensureTeamSubConfig(UUID playerUUID) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        if (tcm != null) tcm.ensureTeamSubConfigForPlayer(playerUUID);
    }

    public void ensureAllMembersHaveSubConfig(IServerPartyAPI party) {
        party.getMemberInfoStream().forEach(member -> ensureTeamSubConfig(member.getUUID()));
    }

    // ==================== Territory messages ====================

    /**
     * Whether the claim welcome messages are shown to the player: their own choice if they made one, else the
     * {@code territoryMessagesDefault} of the Team Claims server config (read each time).
     */
    public boolean areTerritoryMessagesEnabled(UUID playerId) {
        Boolean choice = getTerritoryMessagesChoice(playerId);
        return choice != null ? choice : TeamClaimsServerConfig.CONFIG.territoryMessagesDefault.get();
    }

    /** The explicit choice of the player, null if they never made one. */
    @Nullable
    public Boolean getTerritoryMessagesChoice(UUID playerId) {
        return savedData == null ? null : savedData.getTerritoryMessagesChoice(playerId);
    }

    /** Stores the explicit choice of the player. */
    public void setTerritoryMessagesEnabled(UUID playerId, boolean enabled) {
        if (savedData == null) return;//only before the server started
        savedData.setTerritoryMessagesChoice(playerId, enabled);
    }

    @VisibleForTesting
    public void clearTerritoryMessagesChoice(UUID playerId) {
        if (savedData != null) savedData.clearTerritoryMessagesChoice(playerId);
    }

    @VisibleForTesting
    @Nullable
    public TeamClaimSavedData getSavedData() { return savedData; }

    // ==================== Lifecycle ====================

    public void onServerStarted() {
        savedData = loadSavedData();
        serverReady = true;
        // Order matters: the per-claim owner/count tracking isn't persisted, so it has to be
        // rebuilt (and the claims of members who left while the server was down handed over to the
        // party owner) while those members still have their team sub-config. Only then is the
        // stored membership caught up with the real parties, which removes those sub-configs.
        validateSavedData();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        if (tcm != null) tcm.reconcile();
        warnAboutNativePartyClaims();
        LOGGER.info("Team Claims system ready — tracking {} parties", teams().size());
    }

    private TeamClaimSavedData loadSavedData() {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            LOGGER.error("[TeamClaims] The overworld isn't loaded, team claim tracking won't be saved this session");
            return new TeamClaimSavedData();
        }
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TeamClaimSavedData::new, TeamClaimSavedData::load, null),
                "opacteamclaims_data"
        );
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
        IServerPartyAPI party = getPlayerParty(player.getUUID());
        if (party == null) return;
        UUID partyId = party.getId();
        updateForceloadActivation(party);
        markPartyLimitSync(partyId);
        // OPAC's own login sync may only reach the client after ours; send the team numbers once more when settled
        server.tell(new TickTask(server.getTickCount() + 5, () -> markPartyLimitSync(partyId)));
    }

    public void onPlayerLogout(ServerPlayer player) {
        onMemberLoggedOut(player.getUUID());
    }

    /**
     * What a logout does: if the player was the last online member of their party, the team forceloads are released,
     * after the grace period if there is one (see {@link #setTeamOnline}).
     */
    public void onMemberLoggedOut(UUID playerUUID) {
        IServerPartyAPI party = getPlayerParty(playerUUID);
        if (party == null) return;
        UUID partyId = party.getId();
        if (!forceLoadHandler.isTeamActive(partyId)) return;
        boolean anyOtherOnline = party.getOnlineMemberStream()
                .anyMatch(sp -> !sp.getUUID().equals(playerUUID));
        if (!anyOtherOnline) {
            setTeamOnline(partyId, false);
        }
    }

    /**
     * Called once at the end of every server tick: the periodic safety net, the batched claim limits sync, the due
     * forceload grace periods and the "keep forceloaded dimensions ticking" of team forceloads.
     */
    public void tick() {
        if (!serverReady) return;
        if (++validationTickCounter >= VALIDATION_INTERVAL) {
            validationTickCounter = 0;
            periodicValidation();
        }
        flushLimitSyncs();
        if (!pendingDeactivations.isEmpty()) {
            int tickCount = server.getTickCount();
            if (tickCount - nextGraceCheckTick >= 0) {
                nextGraceCheckTick = tickCount + 20;
                releaseDueTeamForceLoads(tickCount);
            }
        }
        forceLoadHandler.keepLevelsTicking();
    }

    private void periodicValidation() {
        IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
        List<UUID> vanished = null;
        for (Map.Entry<UUID, TeamData> entry : teams().entrySet()) {
            IServerPartyAPI party = partyManager.getPartyById(entry.getKey());
            if (party == null) {
                if (vanished == null) vanished = new ArrayList<>();
                vanished.add(entry.getKey());
            } else if (!entry.getValue().forceLoadedChunks.isEmpty()) {
                updateForceloadActivation(party);
            }
        }
        if (vanished != null) {
            for (UUID partyId : vanished) {
                LOGGER.warn("[TeamClaims] Party {} no longer exists but its team claims were still tracked, dropping them", partyId);
                onPartyRemoved(partyId);
            }
        }
    }

    // ==================== Claim tracking ====================

    @Override
    public void onChunkChange(ResourceLocation dimension, int chunkX, int chunkZ,
                              @Nullable IPlayerChunkClaimAPI claim) {
        if (!serverReady) return;
        ClaimPos pos = new ClaimPos(dimension, chunkX, chunkZ);
        UUID trackedBy = claimToParty.get(pos);

        if (claim == null || !isTeamClaim(claim)) {
            // Unclaimed, or now a personal claim: it no longer counts for any team
            if (trackedBy != null) {
                untrackClaim(trackedBy, pos);
                markPartyLimitSync(trackedBy);
            }
            return;
        }

        // The team is the one whose sub-config the claim uses (the same mapping isTeamClaim used). That is also right
        // for a member whose leave is still queued for the end of this tick: the leave hands the claims they still
        // own in that team over to its owner.
        UUID ownerId = claim.getPlayerId();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        TeamConfig teamConfig = tcm == null ? null : tcm.getTeamConfigForPlayer(ownerId);
        if (teamConfig == null) return;//isTeamClaim was true, so there is one
        UUID partyId = teamConfig.getPartyId();
        if (trackedBy != null && !trackedBy.equals(partyId)) {
            untrackClaim(trackedBy, pos);
            markPartyLimitSync(trackedBy);
        }

        TeamData teamData = getOrCreateTeamData(partyId);
        UUID previousOwner = teamData.getOwner(pos);
        boolean changed = false;
        if (previousOwner == null) {
            teamData.putClaim(pos, ownerId);
            claimToParty.put(pos, partyId);
            changed = true;
        } else if (!previousOwner.equals(ownerId)) {
            teamData.putClaim(pos, ownerId);
            changed = true;
        }
        if (teamData.setForceloaded(pos, claim.isForceloadable())) {
            changed = true;
            if (claim.isForceloadable()) {
                if (forceLoadHandler.isTeamActive(partyId)) {
                    forceLoadHandler.addForceLoad(dimension, chunkX, chunkZ);
                } else {
                    // The team's first forceload: active right away if a member is online (adds this ticket too)
                    IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
                    if (party != null) updateForceloadActivation(party);
                }
            } else {
                forceLoadHandler.removeForceLoad(dimension, chunkX, chunkZ);
            }
        }
        if (changed) {
            markSavedDataDirty();
            // Team claims affect all members' displayed counts
            markPartyLimitSync(partyId);
        }
    }

    /**
     * Never called on the server at the time of writing ({@link IClaimsManagerListenerAPI} documents it as
     * client-only; only {@code ClientClaimsManager} fires it), so this deliberately has no region index of its own,
     * which would cost on every tracked claim change. It does one pass over the global claim index.
     */
    @Override
    public void onWholeRegionChange(ResourceLocation dimension, int regionX, int regionZ) {
        if (!serverReady || claimToParty.isEmpty()) return;
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        List<ClaimPos> stale = new ArrayList<>();
        for (ClaimPos pos : claimToParty.keySet()) {
            //a region is 512x512 blocks = 32x32 chunks
            if (!pos.dimension.equals(dimension) || pos.x >> 5 != regionX || pos.z >> 5 != regionZ) continue;
            if (!isTeamClaim(claimsManager.get(dimension, pos.x, pos.z))) stale.add(pos);
        }
        for (ClaimPos pos : stale) {
            UUID partyId = claimToParty.get(pos);
            untrackClaim(partyId, pos);
            markPartyLimitSync(partyId);
        }
    }

    @Override
    public void onDimensionChange(ResourceLocation dimension) {
        // Re-apply the team force-load tickets of the dimension
        forceLoadHandler.reassertTickets(dimension);
    }

    /** Stops tracking a claim (and releases its team forceload ticket). */
    private void untrackClaim(UUID partyId, ClaimPos pos) {
        claimToParty.remove(pos);
        TeamData teamData = teams().get(partyId);
        if (teamData != null && teamData.removeClaim(pos) != null) markSavedDataDirty();
        forceLoadHandler.removeForceLoad(pos.dimension, pos.x, pos.z);
    }

    public void activateTeamForceLoads(UUID partyId) {
        TeamData teamData = teams().get(partyId);
        if (teamData == null || teamData.forceLoadedChunks.isEmpty()) return;
        int added = 0;
        for (ClaimPos pos : teamData.forceLoadedChunks) {
            if (forceLoadHandler.addForceLoad(pos.dimension, pos.x, pos.z)) added++;
        }
        forceLoadHandler.markTeamActive(partyId);
        if (added > 0) LOGGER.info("Activated {} team force-loads for party {}", added, partyId);
    }

    /** Releases the team forceloads right now, cancelling a pending grace period. */
    public void deactivateTeamForceLoads(UUID partyId) {
        pendingDeactivations.remove(partyId);
        releaseTeamForceLoads(partyId);
    }

    private void releaseTeamForceLoads(UUID partyId) {
        TeamData teamData = teams().get(partyId);
        forceLoadHandler.markTeamInactive(partyId);
        if (teamData == null || teamData.forceLoadedChunks.isEmpty()) return;
        int removed = 0;
        for (ClaimPos pos : teamData.forceLoadedChunks) {
            if (forceLoadHandler.removeForceLoad(pos.dimension, pos.x, pos.z)) removed++;
        }
        if (removed > 0) LOGGER.info("Deactivated {} team force-loads for party {}", removed, partyId);
    }

    /** Team forceloads are active while at least one member of the party is online (plus the grace period). */
    private void updateForceloadActivation(IServerPartyAPI party) {
        setTeamOnline(party.getId(), party.getOnlineMemberStream().findAny().isPresent());
    }

    /**
     * The one place that turns "is any member of this party online" into forceload activation, used by logins,
     * logouts, membership changes and the safety net.
     * <p>
     * Online: cancels a pending deactivation and activates the forceloads if they are not active. Offline: an active
     * team is deactivated right away, or, if a forceload grace period is configured, scheduled to be deactivated when
     * it is over (an already scheduled deactivation keeps its time). The forceloads stay active until then.
     */
    public void setTeamOnline(UUID partyId, boolean anyOnline) {
        TeamData teamData = teams().get(partyId);
        if (teamData == null || teamData.forceLoadedChunks.isEmpty()) {
            pendingDeactivations.remove(partyId);
            return;
        }
        boolean isActive = forceLoadHandler.isTeamActive(partyId);
        if (anyOnline) {
            pendingDeactivations.remove(partyId);
            if (!isActive) activateTeamForceLoads(partyId);
        } else if (isActive) {
            int graceTicks = getForceloadGraceTicks(partyId);
            if (graceTicks <= 0) {
                deactivateTeamForceLoads(partyId);
            } else if (pendingDeactivations.putIfAbsent(partyId, server.getTickCount() + graceTicks) == null) {
                LOGGER.info("[TeamClaims] No member of party {} is online, its team forceloads stay active for {} more tick(s)",
                        partyId, graceTicks);
            }
        } else {
            pendingDeactivations.remove(partyId);
        }
    }

    /** Releases the forceloads of every team whose grace period is over, unless a member is online after all. */
    private void releaseDueTeamForceLoads(int tickCount) {
        IPartyManagerAPI partyManager = null;
        for (Iterator<Map.Entry<UUID, Integer>> it = pendingDeactivations.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> entry = it.next();
            if (tickCount - entry.getValue() < 0) continue;
            it.remove();
            UUID partyId = entry.getKey();
            if (partyManager == null) partyManager = OpenPACServerAPI.get(server).getPartyManager();
            IServerPartyAPI party = partyManager.getPartyById(partyId);
            if (party != null && party.getOnlineMemberStream().findAny().isPresent()) continue;
            LOGGER.info("[TeamClaims] The forceload grace period of party {} is over", partyId);
            releaseTeamForceLoads(partyId);
        }
    }

    /**
     * The forceload grace period of a party in server ticks: the test override of that party if set, else
     * {@code forceloadGraceMinutes} of the Team Claims server config (read each time, so a config reload applies to
     * the next logout).
     */
    public int getForceloadGraceTicks(UUID partyId) {
        Integer override = forceloadGraceTicksOverrides.get(partyId);
        if (override != null) return override;
        return TeamClaimsServerConfig.CONFIG.forceloadGraceMinutes.get() * 1200;
    }

    /**
     * Test hook: sets the forceload grace period of one party in ticks, ignoring the config (per party, as gametests
     * run side by side). A negative value goes back to the config.
     */
    @VisibleForTesting
    public void setForceloadGraceTicksOverride(UUID partyId, int ticks) {
        if (ticks < 0) forceloadGraceTicksOverrides.remove(partyId);
        else forceloadGraceTicksOverrides.put(partyId, ticks);
    }

    public enum ForceloadActivity { ACTIVE, GRACE, INACTIVE }

    /**
     * Whether the team's forceloads are loaded: {@code ACTIVE} while a member is online, {@code GRACE} while they stay
     * loaded only for the forceload grace period after the last member left, {@code INACTIVE} otherwise.
     */
    public ForceloadActivity getForceloadActivity(UUID partyId) {
        if (!forceLoadHandler.isTeamActive(partyId)) return ForceloadActivity.INACTIVE;
        return pendingDeactivations.containsKey(partyId) ? ForceloadActivity.GRACE : ForceloadActivity.ACTIVE;
    }

    /** Server ticks until the pending forceload release of the party (see the grace period), 0 if none is pending. */
    public int getGraceTicksLeft(UUID partyId) {
        Integer releaseTick = pendingDeactivations.get(partyId);
        return releaseTick == null ? 0 : Math.max(0, releaseTick - server.getTickCount());
    }

    @VisibleForTesting
    public boolean hasPendingDeactivation(UUID partyId) {
        return pendingDeactivations.containsKey(partyId);
    }

    /**
     * The party no longer exists: its team claims stay personal claims of whoever made them (their team sub-configs
     * are removed with the team config), so they simply stop being tracked.
     */
    public void onPartyRemoved(UUID partyId) {
        TeamData teamData = savedData == null ? null : savedData.teams.remove(partyId);
        pendingDeactivations.remove(partyId);
        forceLoadHandler.markTeamInactive(partyId);
        limitSyncParties.remove(partyId);
        if (teamData == null) return;
        for (ClaimPos pos : teamData.getTrackedClaims()) {
            claimToParty.remove(pos);
            forceLoadHandler.removeForceLoad(pos.dimension, pos.x, pos.z);
        }
        for (UUID owner : teamData.claimsByOwner.keySet()) markPlayerLimitSync(owner);
        markSavedDataDirty();
    }

    /**
     * Rebuilds the tracking from the stored positions and the live claims at server start, and hands the team claims
     * of members who left while the server was down over to the party owner.
     */
    private void validateSavedData() {
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();

        for (UUID partyId : new ArrayList<>(teams().keySet())) {
            TeamData teamData = teams().get(partyId);
            List<ClaimPos> storedClaims = teamData.takeLoadedClaims();
            IServerPartyAPI party = partyManager.getPartyById(partyId);
            if (party == null) {
                teams().remove(partyId);
                markSavedDataDirty();
                continue;
            }
            ensureAllMembersHaveSubConfig(party);
            Map<UUID, List<ClaimPos>> orphanedByFormerMember = new LinkedHashMap<>();
            int dropped = 0;
            for (ClaimPos pos : storedClaims) {
                IPlayerChunkClaimAPI claim = claimsManager.get(pos.dimension, pos.x, pos.z);
                if (claim == null || !isTeamClaim(claim) || claimToParty.containsKey(pos)) {
                    dropped++;
                    continue;
                }
                // Rebuild every surviving claim, including the ones whose technical owner is no longer a party
                // member: those are transferred to the current party owner below, which needs correct "before"
                // numbers. The live forceload flag is the truth.
                UUID claimOwner = claim.getPlayerId();
                teamData.putClaim(pos, claimOwner);
                teamData.setForceloaded(pos, claim.isForceloadable());
                claimToParty.put(pos, partyId);
                if (party.getMemberInfo(claimOwner) == null)
                    orphanedByFormerMember.computeIfAbsent(claimOwner, k -> new ArrayList<>()).add(pos);
            }
            if (dropped > 0) markSavedDataDirty();

            // Members that left (or were kicked) while the server was down: their team claims go to the current
            // party owner, exactly like a normal leave. Anything that can't be transferred falls back to the legacy
            // behaviour and simply stops being tracked (it stays a personal claim of the former member).
            for (Map.Entry<UUID, List<ClaimPos>> orphaned : orphanedByFormerMember.entrySet()) {
                UUID formerMemberId = orphaned.getKey();
                int transferred = transferTeamClaimsToOwner(party, formerMemberId, orphaned.getValue());
                dropTrackedClaimsOf(partyId, formerMemberId);
                forgetPlayer(formerMemberId);
                if (transferred > 0) {
                    LOGGER.info("[TeamClaims] Transferred {} team claim(s) of former party member {} to the party owner {}",
                            transferred, formerMemberId, ownerIdOf(party));
                    notifyTeamClaimsTransferred(party, formerMemberId, transferred);
                }
            }
            markPartyLimitSync(partyId);
        }
    }

    // ==================== Overhead ====================

    /** Team claims of the player's party made by OTHER members. Personal claims never count. */
    public int getTeamClaimOverheadForPlayer(UUID playerUUID) {
        if (!server.isSameThread()) return offThreadClaimOverhead.getOrDefault(playerUUID, 0);
        TeamData teamData = getTeamDataOfMember(playerUUID);
        return teamData == null ? 0 : teamData.getClaimCount() - teamData.getClaimCountOf(playerUUID);
    }

    /** Team forceloads of the player's party made by OTHER members. */
    public int getTeamForceloadOverheadForPlayer(UUID playerUUID) {
        if (!server.isSameThread()) return offThreadForceloadOverhead.getOrDefault(playerUUID, 0);
        TeamData teamData = getTeamDataOfMember(playerUUID);
        return teamData == null ? 0 : teamData.getForceloadCount() - teamData.getForceloadCountOf(playerUUID);
    }

    @Nullable
    private TeamData getTeamDataOfMember(UUID playerUUID) {
        if (savedData == null) return null;
        IServerPartyAPI party = getPlayerParty(playerUUID);
        return party == null ? null : savedData.teams.get(party.getId());
    }

    private void refreshOffThreadOverhead(UUID playerUUID) {
        int claims = getTeamClaimOverheadForPlayer(playerUUID);
        int forceloads = getTeamForceloadOverheadForPlayer(playerUUID);
        if (claims == 0) offThreadClaimOverhead.remove(playerUUID); else offThreadClaimOverhead.put(playerUUID, claims);
        if (forceloads == 0) offThreadForceloadOverhead.remove(playerUUID); else offThreadForceloadOverhead.put(playerUUID, forceloads);
    }

    // ==================== Claim limits sync (batched) ====================

    /** Every online member of the party gets fresh claim limits at the end of this tick. */
    public void markPartyLimitSync(UUID partyId) {
        limitSyncParties.add(partyId);
    }

    /** The player gets fresh claim limits at the end of this tick. */
    public void markPlayerLimitSync(UUID playerId) {
        limitSyncPlayers.add(playerId);
    }

    /**
     * Pushes a fresh claim limits packet to every player marked this tick, once each, however many claims changed.
     * <p>
     * The numbers themselves don't have to be assembled here: the Common hook in
     * {@code PlayerClaimInfo.getClaimCount()/getForceloadCount()} already adds each member's team overhead, so
     * {@code ClaimingModes.PLAYER}'s limits builder — and with it OPAC's own
     * {@code ClaimsManagerSynchronizer.syncClaimLimits} — reports the overhead-adjusted counts, and the hook in
     * {@code ClaimsManagerSynchronizer.syncClaimLimits} forces {@code alwaysUseLoadingValues} while a member has
     * overhead. This only makes the update immediate instead of waiting for OPAC's once-per-second limits check
     * (which also catches anything this misses).
     */
    private void flushLimitSyncs() {
        if (limitSyncParties.isEmpty() && limitSyncPlayers.isEmpty()) return;
        IServerData<?, ?> serverData = ServerData.from(server);
        if (serverData == null) {
            limitSyncParties.clear();
            limitSyncPlayers.clear();
            return;
        }
        IPartyManagerAPI partyManager = serverData.getPartyManager();
        for (UUID partyId : limitSyncParties) {
            IServerPartyAPI party = partyManager.getPartyById(partyId);
            if (party != null) party.getMemberInfoStream().forEach(member -> limitSyncPlayers.add(member.getUUID()));
        }
        limitSyncParties.clear();
        for (UUID playerId : limitSyncPlayers) {
            refreshOffThreadOverhead(playerId);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            try {
                IPlayerConfig config = serverData.getPlayerConfigManager().getLoadedConfig(playerId);
                serverData.getServerClaimsManager().getClaimsManagerSynchronizer().syncClaimLimits(config, player);
            } catch (Exception e) {
                LOGGER.warn("[TeamClaims] Failed claim limits sync for {}", playerId, e);
            }
        }
        limitSyncPlayers.clear();
    }

    // ==================== Budget Checks (called by bridge handler) ====================

    /**
     * One party member's numbers as OPAC enforces them: their personal claims/forceloads (the raw counts minus their own
     * team ones; their displayed count is that plus the whole team's total) and their full limits.
     */
    public record MemberNumbers(int personalClaims, int personalForceloads, int claimLimit, int forceloadLimit) {}

    /**
     * The {@link MemberNumbers} of a member of the party whose team claims are {@code teamData} (null: it has none), or
     * null if OPAC has no claim info for the player. Shared by the budget checks and the info command, so both always
     * agree.
     */
    @Nullable
    public MemberNumbers getMemberNumbers(@Nullable TeamData teamData, UUID memberId) {
        IServerClaimsManagerAPI claimsAPI = OpenPACServerAPI.get(server).getServerClaimsManager();
        boolean previousComputing = COMPUTING_OVERHEAD.get();
        COMPUTING_OVERHEAD.set(true);
        try {
            var memberInfo = claimsAPI.getPlayerInfo(memberId);
            if (memberInfo == null) return null;
            // Raw counts (no overhead) because COMPUTING_OVERHEAD=true
            int personalClaims = Math.max(0, memberInfo.getClaimCount()
                    - (teamData == null ? 0 : teamData.getClaimCountOf(memberId)));
            int personalForceloads = Math.max(0, memberInfo.getForceloadCount()
                    - (teamData == null ? 0 : teamData.getForceloadCountOf(memberId)));
            // Exactly the limits OPAC itself enforces in tryToClaimHelper/tryToForceloadHelper
            return new MemberNumbers(personalClaims, personalForceloads,
                    claimsAPI.getPlayerFullClaimLimit(memberId), claimsAPI.getPlayerFullForceloadLimit(memberId));
        } finally {
            COMPUTING_OVERHEAD.set(previousComputing);
        }
    }

    @Nullable
    public ClaimResult<PlayerChunkClaim> checkTeamClaimBudget(UUID playerId, boolean forceLoaded,
                                                              @Nullable PlayerChunkClaim replacedClaim) {
        IServerPartyAPI party = getPlayerParty(playerId);
        if (party == null) return null;

        List<String> overClaimBudget = new ArrayList<>();
        List<String> overForceloadBudget = new ArrayList<>();

        TeamData teamData = teams().get(party.getId());
        final int totalTeamClaims = teamData == null ? 0 : teamData.getClaimCount();
        final int totalTeamForceloads = teamData == null ? 0 : teamData.getForceloadCount();

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
        party.getMemberInfoStream().forEach(member -> {
            UUID memberId = member.getUUID();
            MemberNumbers numbers = getMemberNumbers(teamData, memberId);
            if (numbers == null) return;

            int memberPersonalClaims = numbers.personalClaims();
            if (memberId.equals(replacedMemberId))
                memberPersonalClaims = Math.max(0, memberPersonalClaims - 1);
            int memberTotalAfterClaim = memberPersonalClaims + totalTeamClaims + teamClaimDelta;
            if (memberTotalAfterClaim > numbers.claimLimit()) overClaimBudget.add(member.getUsername());

            if (teamForceloadDelta > 0) {
                int memberPersonalForceloads = numbers.personalForceloads();
                if (memberId.equals(replacedMemberId) && replacedWasForceloaded)
                    memberPersonalForceloads = Math.max(0, memberPersonalForceloads - 1);
                int memberTotalForceloadsAfter = memberPersonalForceloads + totalTeamForceloads + teamForceloadDelta;
                if (memberTotalForceloadsAfter > numbers.forceloadLimit()) overForceloadBudget.add(member.getUsername());
            }
        });

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
        IServerPartyAPI party = getPlayerParty(currentClaim.getPlayerId());
        if (party == null) return null;

        List<String> overBudget = new ArrayList<>();

        TeamData teamData = teams().get(party.getId());
        final int totalTeamForceloads = teamData == null ? 0 : teamData.getForceloadCount();

        // Check ALL members — after enabling forceload, each member's displayed forceload count =
        // personal_forceloads + total_team_forceloads + 1.
        party.getMemberInfoStream().forEach(member -> {
            MemberNumbers numbers = getMemberNumbers(teamData, member.getUUID());
            if (numbers == null) return;
            if (numbers.personalForceloads() + totalTeamForceloads + 1 > numbers.forceloadLimit())
                overBudget.add(member.getUsername());
        });

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
        return OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(playerUUID);
    }

    /** The party owner's ID, or null for a party in an inconsistent state. */
    @Nullable
    static UUID ownerIdOf(IServerPartyAPI party) {
        IPartyMemberAPI owner = party.getOwner();
        return owner == null ? null : owner.getUUID();
    }

    @Nullable
    public TeamData getTeamData(UUID partyId) { return teams().get(partyId); }

    private TeamData getOrCreateTeamData(UUID partyId) {
        return savedData.teams.computeIfAbsent(partyId, id -> {
            markSavedDataDirty();
            return new TeamData(id);
        });
    }


    // ==================== Membership changes ====================

    /**
     * A player joined a party (event-driven, see {@code TeamConfigManager}). Their numbers and those of the
     * other members change.
     */
    public void onPlayerJoinedParty(UUID partyId, UUID playerUUID) {
        forgetPlayer(playerUUID);
        markPartyLimitSync(partyId);
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
        if (party != null) updateForceloadActivation(party);
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
        TeamData teamData = teams().get(partyId);
        int transferred = 0;
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
        if (teamData != null && party != null) {
            List<ClaimPos> owned = new ArrayList<>(teamData.claimsOf(playerUUID));
            if (!owned.isEmpty()) transferred = transferTeamClaimsToOwner(party, playerUUID, owned);
        }
        if (teamData != null) dropTrackedClaimsOf(partyId, playerUUID);

        forgetPlayer(playerUUID);
        markPartyLimitSync(partyId);
        markPlayerLimitSync(playerUUID);//no longer a member, but their own count just dropped
        if (party != null) {
            updateForceloadActivation(party);
            if (transferred > 0) notifyTeamClaimsTransferred(party, playerUUID, transferred);
        }
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
     * force-load tickets and marks its saved data dirty, and the clients are synced. The claim limits
     * sync is batched to the end of the tick anyway.
     *
     * @return the number of claims that were actually transferred
     */
    private int transferTeamClaimsToOwner(IServerPartyAPI party, UUID leaverId, List<ClaimPos> positions) {
        if (positions.isEmpty()) return 0;
        UUID ownerId = ownerIdOf(party);
        if (ownerId == null) {
            LOGGER.warn("[TeamClaims] Party {} has no owner — leaving the {} team claim(s) of {} alone.",
                    party.getId(), positions.size(), leaverId);
            return 0;
        }
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
                LOGGER.warn("[TeamClaims] Failed to transfer the team claim at {} from {} to {}",
                        pos, leaverId, ownerId, e);
            }
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
            LOGGER.warn("[TeamClaims] Could not check the claim task state of {}", playerId, e);
            return true;
        }
        return false;
    }

    /** Legacy behaviour for claims that stay with the leaving player: drop them from tracking. */
    private void dropTrackedClaimsOf(UUID partyId, UUID playerUUID) {
        TeamData teamData = teams().get(partyId);
        if (teamData == null) return;
        for (ClaimPos pos : new ArrayList<>(teamData.claimsOf(playerUUID))) untrackClaim(partyId, pos);
    }

    private void notifyTeamClaimsTransferred(IServerPartyAPI party, UUID leaverId, int count) {
        IAdaptiveLocalizerAPI localizer = OpenPACServerAPI.get(server).getAdaptiveTextLocalizer();
        String countText = String.valueOf(count);
        ServerPlayer leaver = server.getPlayerList().getPlayer(leaverId);
        if (leaver != null) {
            leaver.sendSystemMessage(localizer.getFor(leaver,
                    "gui.xaero_pac_team_claims_transfer_leaver", countText).withStyle(ChatFormatting.YELLOW));
        }
        UUID ownerId = ownerIdOf(party);
        ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            owner.sendSystemMessage(localizer.getFor(owner,
                    "gui.xaero_pac_team_claims_transfer_owner", resolvePlayerName(leaverId), countText)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private String resolvePlayerName(UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) return online.getGameProfile().getName();
        var info = OpenPACServerAPI.get(server).getServerClaimsManager().getPlayerInfo(playerId);
        String username = info == null ? null : info.getPlayerUsername();
        if (username != null && !username.isEmpty()) return username;
        return playerId.toString();
    }

    /** Drops every cached value of a player whose party membership changed. */
    public void forgetPlayer(UUID playerUUID) {
        teamSubIndexCache.remove(playerUUID);
        offThreadClaimOverhead.remove(playerUUID);
        offThreadForceloadOverhead.remove(playerUUID);
    }

    // ==================== Data Types ====================

    /** A chunk in a dimension. Immutable, with a precomputed hash (it is a hot map key). */
    public static final class ClaimPos {
        public final ResourceLocation dimension;
        public final int x;
        public final int z;
        private final int hash;

        public ClaimPos(ResourceLocation dimension, int x, int z) {
            this.dimension = Objects.requireNonNull(dimension);
            this.x = x;
            this.z = z;
            this.hash = 31 * dimension.hashCode() + Long.hashCode(ChunkPos.asLong(x, z));
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClaimPos other)) return false;
            return hash == other.hash && x == other.x && z == other.z && dimension.equals(other.dimension);
        }
        @Override public int hashCode() { return hash; }
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

    /**
     * The tracked team claims of one party. Invariants: every forceloaded chunk is a tracked claim, and the per-owner
     * indexes always agree with {@link #claimOwners}. Only the positions are persisted; owners and forceload flags are
     * rebuilt from the live claims at server start ({@link #validateSavedData}).
     */
    public static final class TeamData {
        private final UUID partyId;
        /** Tracked team claim to its owner (the member who technically owns it). */
        private final Map<ClaimPos, UUID> claimOwners = new LinkedHashMap<>();
        private final Set<ClaimPos> forceLoadedChunks = new LinkedHashSet<>();
        private final Map<UUID, Set<ClaimPos>> claimsByOwner = new HashMap<>();
        private final Map<UUID, Integer> forceloadsByOwner = new HashMap<>();
        /** Positions read from disk, until {@link #validateSavedData} turns them into tracked claims. */
        @Nullable
        private List<ClaimPos> loadedClaims;

        public TeamData(UUID partyId) { this.partyId = partyId; }

        public int getClaimCount() { return claimOwners.size(); }
        public int getForceloadCount() { return forceLoadedChunks.size(); }
        public int getClaimCountOf(UUID owner) {
            Set<ClaimPos> owned = claimsByOwner.get(owner);
            return owned == null ? 0 : owned.size();
        }
        public int getForceloadCountOf(UUID owner) { return forceloadsByOwner.getOrDefault(owner, 0); }
        @Nullable public UUID getOwner(ClaimPos pos) { return claimOwners.get(pos); }
        public boolean isForceloaded(ClaimPos pos) { return forceLoadedChunks.contains(pos); }
        public Set<ClaimPos> getTrackedClaims() { return Collections.unmodifiableSet(claimOwners.keySet()); }
        Set<ClaimPos> claimsOf(UUID owner) {
            Set<ClaimPos> owned = claimsByOwner.get(owner);
            return owned == null ? Collections.emptySet() : Collections.unmodifiableSet(owned);
        }

        /** Tracks a claim or changes its owner. */
        private void putClaim(ClaimPos pos, UUID owner) {
            UUID previous = claimOwners.put(pos, owner);
            if (owner.equals(previous)) return;
            if (previous != null) {
                removeFromOwnerIndex(previous, pos);
                if (forceLoadedChunks.contains(pos)) {
                    addForceload(previous, -1);
                    addForceload(owner, 1);
                }
            }
            claimsByOwner.computeIfAbsent(owner, o -> new LinkedHashSet<>()).add(pos);
        }

        /** @return the owner of the claim that stopped being tracked, null if it wasn't */
        @Nullable
        private UUID removeClaim(ClaimPos pos) {
            UUID owner = claimOwners.remove(pos);
            if (owner == null) return null;
            removeFromOwnerIndex(owner, pos);
            if (forceLoadedChunks.remove(pos)) addForceload(owner, -1);
            return owner;
        }

        /** @return true if the forceload state of a tracked claim changed */
        private boolean setForceloaded(ClaimPos pos, boolean forceloaded) {
            UUID owner = claimOwners.get(pos);
            if (owner == null) return false;
            if (forceloaded ? forceLoadedChunks.add(pos) : forceLoadedChunks.remove(pos)) {
                addForceload(owner, forceloaded ? 1 : -1);
                return true;
            }
            return false;
        }

        private void removeFromOwnerIndex(UUID owner, ClaimPos pos) {
            Set<ClaimPos> owned = claimsByOwner.get(owner);
            if (owned != null && owned.remove(pos) && owned.isEmpty()) claimsByOwner.remove(owner);
        }

        private void addForceload(UUID owner, int delta) {
            forceloadsByOwner.merge(owner, delta, (a, b) -> a + b == 0 ? null : a + b);
        }

        private List<ClaimPos> takeLoadedClaims() {
            List<ClaimPos> result = loadedClaims == null ? List.of() : loadedClaims;
            loadedClaims = null;
            return result;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("partyId", partyId);
            ListTag claimsList = new ListTag();
            // Not validated yet (saved before the server finished starting): keep what was loaded
            Collection<ClaimPos> claims = loadedClaims != null ? loadedClaims : claimOwners.keySet();
            for (ClaimPos pos : claims) claimsList.add(pos.save());
            tag.put("trackedClaims", claimsList);
            ListTag forceList = new ListTag();
            for (ClaimPos pos : forceLoadedChunks) forceList.add(pos.save());
            tag.put("forceLoadedChunks", forceList);
            return tag;
        }

        /**
         * Reads the stored positions. The stored forceload list is not needed anymore (the live claim's forceload
         * flag is used at validation) but is still written, so older versions can read the data.
         */
        public static TeamData load(CompoundTag tag) {
            TeamData data = new TeamData(tag.getUUID("partyId"));
            ListTag claimsList = tag.getList("trackedClaims", Tag.TAG_COMPOUND);
            List<ClaimPos> claims = new ArrayList<>(claimsList.size());
            for (int i = 0; i < claimsList.size(); i++) claims.add(ClaimPos.load(claimsList.getCompound(i)));
            data.loadedClaims = claims;
            return data;
        }
    }

    public static class TeamClaimSavedData extends SavedData {
        final Map<UUID, TeamData> teams = new HashMap<>();
        /** Explicit "territory messages" choices only: a player without an entry follows the config default. */
        private final Map<UUID, Boolean> territoryMessages = new HashMap<>();
        public TeamClaimSavedData() {}

        @Nullable
        public Boolean getTerritoryMessagesChoice(UUID playerId) { return territoryMessages.get(playerId); }

        public void setTerritoryMessagesChoice(UUID playerId, boolean enabled) {
            Boolean previous = territoryMessages.put(playerId, enabled);
            if (previous == null || previous != enabled) setDirty();
        }

        public void clearTerritoryMessagesChoice(UUID playerId) {
            if (territoryMessages.remove(playerId) != null) setDirty();
        }

        public static TeamClaimSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
            TeamClaimSavedData data = new TeamClaimSavedData();
            ListTag teamsList = tag.getList("teams", Tag.TAG_COMPOUND);
            for (int i = 0; i < teamsList.size(); i++) {
                TeamData teamData = TeamData.load(teamsList.getCompound(i));
                data.teams.put(teamData.partyId, teamData);
            }
            //not there in saves from before the territory messages toggle
            ListTag messagesList = tag.getList("territoryMessages", Tag.TAG_COMPOUND);
            for (int i = 0; i < messagesList.size(); i++) {
                CompoundTag entry = messagesList.getCompound(i);
                if (entry.hasUUID("player") && entry.contains("enabled", Tag.TAG_BYTE))
                    data.territoryMessages.put(entry.getUUID("player"), entry.getBoolean("enabled"));
            }
            return data;
        }
        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag teamsList = new ListTag();
            for (TeamData teamData : teams.values()) teamsList.add(teamData.save());
            tag.put("teams", teamsList);
            if (!territoryMessages.isEmpty()) {//only written once somebody made a choice
                ListTag messagesList = new ListTag();
                for (Map.Entry<UUID, Boolean> choice : territoryMessages.entrySet()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("player", choice.getKey());
                    entry.putBoolean("enabled", choice.getValue());
                    messagesList.add(entry);
                }
                tag.put("territoryMessages", messagesList);
            }
            return tag;
        }
    }
}
