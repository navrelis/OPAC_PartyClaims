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
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.IServerPlayerClaimInfo;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.IPlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Tracks the team claims of every party (which chunk is a team claim of which party, owned by which member, in which
 * order they became team claims, and whether they are forceloaded) and enforces the team's own budget.
 * <p>
 * Two independent budgets:
 * <ul>
 *     <li><b>private</b>, per player: every claim of the player that is not a team claim, against OPAC's own
 *     {@code getPlayerFullClaimLimit}/{@code getPlayerFullForceloadLimit}. OPAC's {@code getClaimCount()} still counts
 *     everything the player technically owns, so the private count is that minus {@link #getOwnedTeamClaimCount};</li>
 *     <li><b>team</b>, per party, a pool: every tracked team claim of the party, whoever technically owns it, against a
 *     limit that only depends on the member count ({@link BudgetSettings}).</li>
 * </ul>
 * Neither counts against the other. A team that ends up above its limit (a member left, the config changed, data of
 * an older version) can make no new team claims and gets a deadline in real time ({@link #checkOverLimit}); when it
 * passes, the most recently made team claims are unclaimed until the team is within its limit. The team claims of a
 * leaving member are handed over to the party owner; those of a removed party stay private claims of their owners only
 * as far as these have private room ({@link #onPartyRemoved}).
 * <p>
 * Threading: server-thread confined. {@link TeamData}, the indexes and the caches are only ever touched on the server
 * thread. The single exception is a call of {@link #getOwnedTeamClaimCount}/{@link #getOwnedTeamForceloadCount} from
 * another thread, which reads a {@link ConcurrentHashMap} snapshot that is refreshed at the end of every tick in which
 * a player's numbers may have changed.
 */
public class TeamClaimManager implements IClaimsManagerListenerAPI {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "gui.xaero_pac_team_claims_";
    /**
     * Safety net and slow clock: membership, claims and logins are all event-driven. Every minute, parties that
     * vanished without a removal event are cleaned up, the forceload activation is checked against who is online and
     * every team is checked against its limits and its over-limit deadlines.
     */
    private static final int VALIDATION_INTERVAL = 1200;
    private static final long MILLIS_PER_HOUR = 3_600_000L;
    /** How often the online members of a team above its limit are reminded of the deadline. */
    private static final long OVER_LIMIT_REMINDER_INTERVAL = 24 * MILLIS_PER_HOUR;

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
    /** Snapshot of the team claims/forceloads each player owns, for callers off the server thread, see the class description. */
    private final Map<UUID, Integer> offThreadOwnedClaims = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> offThreadOwnedForceloads = new ConcurrentHashMap<>();
    private int validationTickCounter = 0;
    /**
     * Forceload grace period: parties whose last online member left while {@code forceloadGraceMinutes} is above 0,
     * to the server tick at which their forceloads are released. Due entries are looked for once a second.
     */
    private final Map<UUID, Integer> pendingDeactivations = new HashMap<>();
    private int nextGraceCheckTick = 0;
    private final Map<UUID, Integer> forceloadGraceTicksOverrides = new HashMap<>();
    /** Real time in epoch milliseconds, for the over-limit deadlines. Only replaced by tests. */
    private LongSupplier clock = System::currentTimeMillis;
    private final Map<UUID, BudgetSettings> budgetSettingsOverrides = new HashMap<>();
    /** Parties with a running over-limit deadline whose team claims changed this tick: checked at the end of the tick. */
    private final Set<UUID> overLimitRechecks = new LinkedHashSet<>();
    /** The private forceload limit of each online player as last seen by the claim limits check, see {@link #hasPrivateForceloadLimitChangedUnnoticed}. */
    private final Map<UUID, Integer> lastPrivateForceloadLimits = new HashMap<>();
    /** While true, a budget rejection is not explained to the player in chat ({@code /teamclaims convert} reports itself). */
    private boolean budgetMessagesSuppressed = false;

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
        return isTeamClaim(claim) ? getTeamPartyId(claim.getPlayerId()) : null;
    }

    /**
     * The party whose team the player is in according to the team configs, null if none. This is the party that
     * tracks the player's team claims; it lags behind OPAC's party membership until the queued party event is
     * processed at the end of the tick.
     */
    @Nullable
    private UUID getTeamPartyId(UUID playerUUID) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        TeamConfig teamConfig = tcm == null ? null : tcm.getTeamConfigForPlayer(playerUUID);
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
        // Now that the team sub-configs are known: OPAC made its forceload tickets while loading the claims,
        // when it could not tell a team forceload from a private one yet
        refreshOpacTicketsOfTeamForceloadOwners();
        // The member counts and the config may have changed while the server was down, and data of the version
        // with one shared budget may be above the team limits: this starts (or continues) the deadlines
        checkAllOverLimits();
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
     * Team Claims and upstream's native party claims ({@code partyOwnedClaims}) both work, but they count in
     * different budgets, which is worth stating once.
     */
    private void warnAboutNativePartyClaims() {
        if (!ServerConfig.CONFIG.partyOwnedClaims.get()) return;
        LOGGER.warn("[TeamClaims] The server option 'partyOwnedClaims' is enabled while Team Claims is active. "
                + "The two features use different budgets: native party claims are owned by the party owner and count "
                + "as the owner's private claims, against the owner's own limit (which "
                + "'claimBonusPerPartyMember'/'claimBonusForPartyOwner' raise). They never count against the team's own "
                + "budget ('teamClaimsBase' etc. in {}), and team claims never count against anybody's private limit. "
                + "A native party claim made with the owner's 'team_*' sub-config is indistinguishable from, and is "
                + "treated as, a team claim of the owner: it counts against the team budget instead.",
                TeamClaimsServerConfig.FILE_NAME);
    }

    public void onPlayerLogin(ServerPlayer player) {
        IServerPartyAPI party = getPlayerParty(player.getUUID());
        if (party == null) return;
        UUID partyId = party.getId();
        updateForceloadActivation(party);
        markPartyLimitSync(partyId);
        // OPAC's own login sync may only reach the client after ours; send the team numbers once more when settled
        server.tell(new TickTask(server.getTickCount() + 5, () -> markPartyLimitSync(partyId)));
        remindOfOverLimit(party, player);
    }

    public void onPlayerLogout(ServerPlayer player) {
        lastPrivateForceloadLimits.remove(player.getUUID());
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
     * Called once at the end of every server tick: the periodic safety net, the over-limit checks of teams whose
     * claims changed, the batched claim limits sync, the due forceload grace periods and the "keep forceloaded
     * dimensions ticking" of team forceloads.
     */
    public void tick() {
        if (!serverReady) return;
        if (++validationTickCounter >= VALIDATION_INTERVAL) {
            validationTickCounter = 0;
            periodicValidation();
        }
        if (!overLimitRechecks.isEmpty()) {
            List<UUID> parties = new ArrayList<>(overLimitRechecks);
            overLimitRechecks.clear();
            for (UUID partyId : parties) checkOverLimit(partyId);
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
            TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
            for (UUID partyId : vanished) {
                LOGGER.warn("[TeamClaims] Party {} no longer exists but its team claims were still tracked, dropping them", partyId);
                // The same order as for a removal event: the team config (and the team sub-configs) first
                if (tcm != null) tcm.removeTeamConfig(partyId);
                onPartyRemoved(partyId);
            }
        }
        checkAllOverLimits();
    }

    // ==================== Claim tracking ====================

    @Override
    public void onChunkChange(ResourceLocation dimension, int chunkX, int chunkZ,
                              @Nullable IPlayerChunkClaimAPI claim) {
        if (!serverReady) return;
        ClaimPos pos = new ClaimPos(dimension, chunkX, chunkZ);
        UUID trackedBy = claimToParty.get(pos);

        if (claim == null || !isTeamClaim(claim)) {
            // Unclaimed, or now a private claim: it no longer counts for any team
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
        UUID partyId = getTeamPartyId(ownerId);
        if (partyId == null) return;//isTeamClaim was true, so there is one
        if (trackedBy != null && !trackedBy.equals(partyId)) {
            untrackClaim(trackedBy, pos);
            markPartyLimitSync(trackedBy);
        }

        TeamData teamData = getOrCreateTeamData(partyId);
        UUID previousOwner = teamData.getOwner(pos);
        boolean changed = false;
        if (previousOwner == null) {
            // A new team claim of this team: the newest one in the claim order
            teamData.putClaim(pos, ownerId);
            claimToParty.put(pos, partyId);
            changed = true;
        } else if (!previousOwner.equals(ownerId)) {
            // Only the owner changes (e.g. the hand-over of a leaving member): its place in the claim order stays
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
                if (teamData.hasOverLimitDeadline()) overLimitRechecks.add(partyId);
            }
        }
        if (changed) {
            markSavedDataDirty();
            // Team claims affect what every member is shown
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
        if (teamData != null && teamData.removeClaim(pos) != null) {
            markSavedDataDirty();
            // Fewer team claims: a team above its limit may be back within it, which ends its deadline
            if (teamData.hasOverLimitDeadline()) overLimitRechecks.add(partyId);
        }
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
     * The party no longer exists (disbanded, expired, replaced). Its team sub-configs are removed with the team
     * config before this is called, so its team claims have just become private claims of whoever technically owned
     * them. They only stay as far as that player has private room: see {@link #settleFormerTeamClaims}. Works with
     * the players offline.
     */
    public void onPartyRemoved(UUID partyId) {
        TeamData teamData = savedData == null ? null : savedData.teams.remove(partyId);
        pendingDeactivations.remove(partyId);
        forceLoadHandler.markTeamInactive(partyId);
        limitSyncParties.remove(partyId);
        overLimitRechecks.remove(partyId);
        if (teamData == null) return;
        // The former team claims of every owner, oldest first, and no longer tracked by anything
        Map<UUID, List<ClaimPos>> claimsByOwner = new LinkedHashMap<>();
        for (Map.Entry<ClaimPos, UUID> entry : teamData.claimOwners.entrySet()) {
            ClaimPos pos = entry.getKey();
            claimToParty.remove(pos);
            forceLoadHandler.removeForceLoad(pos.dimension, pos.x, pos.z);
            claimsByOwner.computeIfAbsent(entry.getValue(), owner -> new ArrayList<>()).add(pos);
        }
        List<ClaimPos> forceloadOrder = new ArrayList<>(teamData.forceLoadedChunks);
        markSavedDataDirty();
        for (Map.Entry<UUID, List<ClaimPos>> entry : claimsByOwner.entrySet()) {
            try {
                settleFormerTeamClaims(entry.getKey(), entry.getValue(), forceloadOrder);
            } catch (Exception e) {
                LOGGER.warn("[TeamClaims] Failed to settle the former team claims of {} after party {} was removed",
                        entry.getKey(), partyId, e);
            }
            forgetPlayer(entry.getKey());
            markPlayerLimitSync(entry.getKey());
        }
    }

    /**
     * What happens to the former team claims of one player once their party is gone and the claims count against the
     * player's private limits: the oldest ones are kept as far as the player has private claim room, the others are
     * unclaimed, newest first. Of the kept ones, the forceloads that were made first stay forceloaded as far as the
     * player has private forceload room; the others keep the claim but lose the forceload. The player is told the
     * outcome if they are online.
     *
     * @param formerClaims    the tracked team claims the player owned, in the order they became team claims
     * @param forceloadOrder  the team's former team forceloads (of any owner), in the order they were forceloaded
     */
    private void settleFormerTeamClaims(UUID ownerId, List<ClaimPos> formerClaims, List<ClaimPos> forceloadOrder) {
        IServerClaimsManagerAPI claimsAPI = OpenPACServerAPI.get(server).getServerClaimsManager();
        var info = claimsAPI.getPlayerInfo(ownerId);
        if (info == null) return;
        // Only what still is a claim of that player
        List<ClaimPos> claims = new ArrayList<>(formerClaims.size());
        Set<ClaimPos> forceloaded = new HashSet<>();
        for (ClaimPos pos : formerClaims) {
            IPlayerChunkClaimAPI claim = claimsAPI.get(pos.dimension, pos.x, pos.z);
            if (claim == null || !ownerId.equals(claim.getPlayerId())) continue;
            claims.add(pos);
            if (claim.isForceloadable()) forceloaded.add(pos);
        }
        if (claims.isEmpty()) return;
        if (hasClaimTaskInProgress(claimsAPI, ownerId)) {
            LOGGER.warn("[TeamClaims] Not checking the {} former team claim(s) of {} against their private limit: a claim "
                    + "transfer/replacement is in progress. They stay private claims.", claims.size(), ownerId);
            return;
        }
        int otherClaims = Math.max(0, info.getClaimCount() - claims.size());
        int otherForceloads = Math.max(0, info.getForceloadCount() - forceloaded.size());
        int keep = roomFor(claims.size(), claimsAPI.getPlayerFullClaimLimit(ownerId), otherClaims);

        int unclaimed = 0;
        for (int i = claims.size() - 1; i >= keep; i--) {
            ClaimPos pos = claims.get(i);
            claimsAPI.unclaim(pos.dimension, pos.x, pos.z);
            //still there only when claims are disabled server-wide, in which case nothing changes
            if (claimsAPI.get(pos.dimension, pos.x, pos.z) == null) unclaimed++;
        }

        Set<ClaimPos> kept = new HashSet<>(claims.subList(0, keep));
        List<ClaimPos> keptForceloads = new ArrayList<>();
        for (ClaimPos pos : forceloadOrder) {
            if (kept.contains(pos) && forceloaded.contains(pos)) keptForceloads.add(pos);
        }
        int keepForceloads = roomFor(keptForceloads.size(), claimsAPI.getPlayerFullForceloadLimit(ownerId), otherForceloads);
        int unforceloaded = 0;
        for (int i = keptForceloads.size() - 1; i >= keepForceloads; i--) {
            if (unforceload(claimsAPI, keptForceloads.get(i))) unforceloaded++;
        }
        if (!keptForceloads.isEmpty()) {
            // The kept forceloads are private ones from now on, which OPAC's tickets have to follow
            IServerData<?, ?> serverData = ServerData.from(server);
            if (serverData != null) serverData.getForceLoadManager().updateTicketsFor(ownerId, false);
        }

        int keptCount = claims.size() - unclaimed;
        LOGGER.info("[TeamClaims] Former team claims of {}: {} kept as private claims, {} unclaimed, {} no longer forceloaded",
                ownerId, keptCount, unclaimed, unforceloaded);
        ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
        if (player == null) return;
        tell(player, unclaimed > 0 ? ChatFormatting.RED : ChatFormatting.YELLOW, "party_removed_claims",
                String.valueOf(keptCount), String.valueOf(unclaimed));
        if (unforceloaded > 0) tell(player, ChatFormatting.RED, "party_removed_forceloads", String.valueOf(unforceloaded));
    }

    /** How many of {@code wanted} more fit under {@code limit} with {@code used} already counting against it. */
    private static int roomFor(int wanted, int limit, int used) {
        return (int) Math.max(0, Math.min(wanted, (long) limit - used));
    }

    /**
     * Turns the forceload of a claim off with OPAC's low-level claim call (the same one a forceload toggle ends in),
     * keeping its owner and sub-config.
     *
     * @return true if the claim is no longer forceloaded
     */
    private boolean unforceload(IServerClaimsManagerAPI claimsAPI, ClaimPos pos) {
        IPlayerChunkClaimAPI current = claimsAPI.get(pos.dimension, pos.x, pos.z);
        if (current == null || !current.isForceloadable()) return true;
        IPlayerChunkClaimAPI result = claimsAPI.claim(pos.dimension, current.getPlayerId(), current.getSubConfigIndex(),
                pos.x, pos.z, false);
        return result != null && !result.isForceloadable();//null only when claims are disabled server-wide
    }

    /**
     * Rebuilds the tracking from the stored positions and the live claims at server start, and hands the team claims
     * of members who left while the server was down over to the party owner.
     */
    private void validateSavedData() {
        for (UUID partyId : new ArrayList<>(teams().keySet())) validateTeam(partyId);
    }

    /**
     * Turns the stored positions of one team into tracked claims: every position that still is a team claim, in the
     * stored order, which is the order in which the chunks became team claims. The forceloads follow the live claims
     * and come in the stored forceload order, then whatever else is forceloaded in claim order.
     */
    private void validateTeam(UUID partyId) {
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        TeamData teamData = teams().get(partyId);
        if (teamData == null) return;
        List<ClaimPos> storedClaims = teamData.takeLoadedClaims();
        List<ClaimPos> storedForceloads = teamData.takeLoadedForceloads();
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
        if (party == null) {
            // Gone while the server was down, which no party event told us and OPAC itself never does: the claims
            // are left alone (they are private claims of their owners now) instead of being measured against
            // private limits on the strength of a party file that may just be missing
            LOGGER.warn("[TeamClaims] Party {} no longer exists, its {} tracked team claim(s) stay with their owners as "
                    + "private claims", partyId, storedClaims.size());
            teams().remove(partyId);
            markSavedDataDirty();
            return;
        }
        ensureAllMembersHaveSubConfig(party);
        Map<UUID, List<ClaimPos>> orphanedByFormerMember = new LinkedHashMap<>();
        Set<ClaimPos> liveForceloads = new LinkedHashSet<>();
        int dropped = 0;
        for (ClaimPos pos : storedClaims) {
            IPlayerChunkClaimAPI claim = claimsManager.get(pos.dimension, pos.x, pos.z);
            if (claim == null || !isTeamClaim(claim) || claimToParty.containsKey(pos)) {
                dropped++;
                continue;
            }
            // Rebuild every surviving claim, including the ones whose technical owner is no longer a party
            // member: those are transferred to the current party owner below. The live forceload flag is the truth.
            UUID claimOwner = claim.getPlayerId();
            teamData.putClaim(pos, claimOwner);
            claimToParty.put(pos, partyId);
            if (claim.isForceloadable()) liveForceloads.add(pos);
            if (party.getMemberInfo(claimOwner) == null)
                orphanedByFormerMember.computeIfAbsent(claimOwner, k -> new ArrayList<>()).add(pos);
        }
        for (ClaimPos pos : storedForceloads) {
            if (liveForceloads.contains(pos)) teamData.setForceloaded(pos, true);
        }
        for (ClaimPos pos : liveForceloads) teamData.setForceloaded(pos, true);
        if (dropped > 0) markSavedDataDirty();

        // Members that left (or were kicked) while the server was down: their team claims go to the current
        // party owner, exactly like a normal leave. Anything that can't be transferred falls back to the legacy
        // behaviour and simply stops being tracked (it stays a private claim of the former member).
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

    /**
     * Test hook: writes the tracked team claims of one party to NBT, reads them back and validates them, which is
     * what a server restart does to them (per party, as gametests run side by side).
     */
    @VisibleForTesting
    public void reloadTeamDataForTesting(UUID partyId) {
        TeamData current = teams().get(partyId);
        if (savedData == null || current == null) return;
        TeamData reloaded = TeamData.load(current.save());
        for (ClaimPos pos : current.getTrackedClaims()) claimToParty.remove(pos);
        savedData.teams.put(partyId, reloaded);
        validateTeam(partyId);
    }

    /**
     * OPAC enables its own forceload tickets when it loads the claims, before the team sub-configs are known, so it
     * treated every team forceload as a private one of its owner (enabled only within the owner's private limit).
     */
    private void refreshOpacTicketsOfTeamForceloadOwners() {
        IServerData<?, ?> serverData = ServerData.from(server);
        if (serverData == null) return;
        Set<UUID> owners = new LinkedHashSet<>();
        for (TeamData teamData : teams().values()) owners.addAll(teamData.forceloadsByOwner.keySet());
        for (UUID owner : owners) {
            try {
                serverData.getForceLoadManager().updateTicketsFor(owner, false);
            } catch (Exception e) {
                LOGGER.warn("[TeamClaims] Could not refresh the forceload tickets of {}", owner, e);
            }
        }
    }

    // ==================== Budgets ====================

    /**
     * What decides the team limits and the over-limit grace period: the values of the Team Claims server config.
     * <ul>
     *     <li>fewer than {@code minMembers} members: both team limits are 0;</li>
     *     <li>else {@code base + (members - minMembers) * perExtraMember}.</li>
     * </ul>
     */
    public record BudgetSettings(int minMembers, int claimsBase, int claimsPerExtraMember, int forceloadsBase,
            int forceloadsPerExtraMember, int overLimitGraceHours) {

        /** The current values of the Team Claims server config. */
        public static BudgetSettings fromConfig() {
            TeamClaimsServerConfig config = TeamClaimsServerConfig.CONFIG;
            return new BudgetSettings(config.teamClaimsMinMembers.get(), config.teamClaimsBase.get(),
                    config.teamClaimsPerExtraMember.get(), config.teamForceloadsBase.get(),
                    config.teamForceloadsPerExtraMember.get(), config.overLimitGraceHours.get());
        }

        public int claimLimit(int members) {
            return limit(members, claimsBase, claimsPerExtraMember);
        }

        public int forceloadLimit(int members) {
            return limit(members, forceloadsBase, forceloadsPerExtraMember);
        }

        private int limit(int members, int base, int perExtraMember) {
            if (members < minMembers) return 0;
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, base + (long) (members - minMembers) * perExtraMember));
        }

        public long graceMillis() {
            return Math.max(0, overLimitGraceHours) * MILLIS_PER_HOUR;
        }
    }

    /**
     * The budget settings of a party: the test override of that party if set, else the Team Claims server config,
     * read each time they are needed, so a config reload applies right away.
     */
    public BudgetSettings getBudgetSettings(UUID partyId) {
        BudgetSettings override = budgetSettingsOverrides.get(partyId);
        return override != null ? override : BudgetSettings.fromConfig();
    }

    /**
     * Test hook: sets the budget settings of one party, ignoring the config (per party, as gametests run side by
     * side). Null goes back to the config.
     */
    @VisibleForTesting
    public void setBudgetSettingsOverride(UUID partyId, @Nullable BudgetSettings settings) {
        if (settings == null) budgetSettingsOverrides.remove(partyId);
        else budgetSettingsOverrides.put(partyId, settings);
    }

    /**
     * Test hook: replaces the real-time clock (epoch milliseconds) the over-limit deadlines use. Null goes back to
     * the system clock.
     */
    @VisibleForTesting
    public void setClock(@Nullable LongSupplier clock) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /** The team budget of one party. */
    public record TeamBudget(UUID partyId, int memberCount, int minMembers, int claims, int claimLimit, int forceloads,
            int forceloadLimit, int nextMemberClaims, int nextMemberForceloads, long claimDeadline,
            long forceloadDeadline) {

        /** How many team claims the team has above its limit, 0 if it is within it. */
        public int claimsOverLimit() {
            return Math.max(0, claims - claimLimit);
        }

        /** How many team forceloads the team has above its limit, 0 if it is within it. */
        public int forceloadsOverLimit() {
            return Math.max(0, forceloads - forceloadLimit);
        }
    }

    /**
     * The team budget of a party as it is enforced: the team totals, the limits for its current member count (the
     * owner included, invited players not), what one more member would add, and the pending over-limit deadlines
     * (epoch milliseconds, 0 = none). Null if the party does not exist.
     */
    @Nullable
    public TeamBudget getTeamBudget(UUID partyId) {
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
        return party == null ? null : teamBudgetOf(party);
    }

    private TeamBudget teamBudgetOf(IServerPartyAPI party) {
        UUID partyId = party.getId();
        TeamData teamData = teams().get(partyId);
        BudgetSettings settings = getBudgetSettings(partyId);
        int members = party.getMemberCount();
        int claimLimit = settings.claimLimit(members);
        int forceloadLimit = settings.forceloadLimit(members);
        return new TeamBudget(partyId, members, settings.minMembers(),
                teamData == null ? 0 : teamData.getClaimCount(), claimLimit,
                teamData == null ? 0 : teamData.getForceloadCount(), forceloadLimit,
                settings.claimLimit(members + 1) - claimLimit, settings.forceloadLimit(members + 1) - forceloadLimit,
                teamData == null ? 0 : teamData.getClaimDeadline(), teamData == null ? 0 : teamData.getForceloadDeadline());
    }

    /**
     * Both budgets of one player, as they are enforced and shown ({@code /teamclaims info} uses the same numbers).
     * The team part is only filled in for a player in a team ({@link #inTeam()}), else it is all 0.
     *
     * @param privateClaims               the player's claims that are not team claims
     * @param privateClaimLimit           OPAC's full claim limit of the player
     * @param privateForceloads           the player's forceloaded claims that are not team claims
     * @param privateForceloadLimit       OPAC's full forceload limit of the player
     * @param partyId                     the player's party, null if they are in no team
     * @param memberCount                 the members of the party, the owner included, invited players not
     * @param teamClaims                  the team claims of the party, whoever technically owns them
     * @param teamClaimLimit              the team claim limit for that member count
     * @param teamForceloads              the forceloaded team claims of the party
     * @param teamForceloadLimit          the team forceload limit for that member count
     * @param overLimitDeadline           when the newest team claims above the limit are unclaimed (epoch
     *                                    milliseconds), 0 if no deadline is pending
     * @param claimsOverLimit             how many team claims the team has above its limit
     * @param forceloadOverLimitDeadline  the same deadline for the team forceloads, 0 if none is pending
     * @param forceloadsOverLimit         how many team forceloads the team has above its limit
     * @param ownedTeamClaims             how many of the team claims the player technically owns
     * @param ownedTeamForceloads         how many of the team forceloads the player technically owns
     */
    public record BudgetInfo(int privateClaims, int privateClaimLimit, int privateForceloads, int privateForceloadLimit,
            @Nullable UUID partyId, int memberCount, int teamClaims, int teamClaimLimit, int teamForceloads,
            int teamForceloadLimit, long overLimitDeadline, int claimsOverLimit, long forceloadOverLimitDeadline,
            int forceloadsOverLimit, int ownedTeamClaims, int ownedTeamForceloads) {

        public boolean inTeam() {
            return partyId != null;
        }
    }

    /**
     * The read API for the budgets of a player: their private claims/forceloads and limits, and, if they are a member
     * of a party that has a team, the team's. Works for offline players. Server thread only.
     */
    public BudgetInfo getBudgetInfo(UUID playerId) {
        IServerClaimsManagerAPI claimsAPI = OpenPACServerAPI.get(server).getServerClaimsManager();
        var info = claimsAPI.getPlayerInfo(playerId);
        int ownedTeamClaims = getOwnedTeamClaimCount(playerId);
        int ownedTeamForceloads = getOwnedTeamForceloadCount(playerId);
        int privateClaims = info == null ? 0 : Math.max(0, info.getClaimCount() - ownedTeamClaims);
        int privateForceloads = info == null ? 0 : Math.max(0, info.getForceloadCount() - ownedTeamForceloads);
        int privateClaimLimit = claimsAPI.getPlayerFullClaimLimit(playerId);
        int privateForceloadLimit = claimsAPI.getPlayerFullForceloadLimit(playerId);
        IServerPartyAPI party = getPlayerParty(playerId);
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        if (party == null || tcm == null || tcm.getTeamConfig(party.getId()) == null)
            return new BudgetInfo(privateClaims, privateClaimLimit, privateForceloads, privateForceloadLimit,
                    null, 0, 0, 0, 0, 0, 0, 0, 0, 0, ownedTeamClaims, ownedTeamForceloads);
        TeamBudget team = teamBudgetOf(party);
        return new BudgetInfo(privateClaims, privateClaimLimit, privateForceloads, privateForceloadLimit,
                team.partyId(), team.memberCount(), team.claims(), team.claimLimit(), team.forceloads(),
                team.forceloadLimit(), team.claimDeadline(), team.claimsOverLimit(), team.forceloadDeadline(),
                team.forceloadsOverLimit(), ownedTeamClaims, ownedTeamForceloads);
    }

    /**
     * How many of the claims the player technically owns are team claims (of the team they are in). OPAC's claim
     * count of the player minus this is their private claim count.
     */
    public int getOwnedTeamClaimCount(UUID playerUUID) {
        if (!server.isSameThread()) return offThreadOwnedClaims.getOrDefault(playerUUID, 0);
        TeamData teamData = getTeamDataOfMember(playerUUID);
        return teamData == null ? 0 : teamData.getClaimCountOf(playerUUID);
    }

    /** How many of the forceloaded claims the player technically owns are team claims, see {@link #getOwnedTeamClaimCount}. */
    public int getOwnedTeamForceloadCount(UUID playerUUID) {
        if (!server.isSameThread()) return offThreadOwnedForceloads.getOrDefault(playerUUID, 0);
        TeamData teamData = getTeamDataOfMember(playerUUID);
        return teamData == null ? 0 : teamData.getForceloadCountOf(playerUUID);
    }

    @Nullable
    private TeamData getTeamDataOfMember(UUID playerUUID) {
        if (savedData == null) return null;
        UUID partyId = getTeamPartyId(playerUUID);
        return partyId == null ? null : savedData.teams.get(partyId);
    }

    private void refreshOffThreadOwnedCounts(UUID playerUUID) {
        int claims = getOwnedTeamClaimCount(playerUUID);
        int forceloads = getOwnedTeamForceloadCount(playerUUID);
        if (claims == 0) offThreadOwnedClaims.remove(playerUUID); else offThreadOwnedClaims.put(playerUUID, claims);
        if (forceloads == 0) offThreadOwnedForceloads.remove(playerUUID); else offThreadOwnedForceloads.put(playerUUID, forceloads);
    }

    /**
     * The team's numbers for the claim limits packet of a player whose used sub-claim ({@code USED_SUBCLAIM}) is
     * their team sub-config, null in every other case (the packet then carries the player's private numbers).
     */
    @Nullable
    public TeamClaimsIntegration.BudgetNumbers getUsedTeamBudget(UUID playerId) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        TeamConfig teamConfig = tcm == null ? null : tcm.getTeamConfigForPlayer(playerId);
        if (teamConfig == null || getTeamSubIndex(playerId) == -1) return null;
        IPlayerConfigAPI playerConfig = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(playerId);
        if (!teamConfig.getSubConfigId().equals(playerConfig.getEffective(PlayerConfigOptions.USED_SUBCLAIM))) return null;
        TeamBudget team = getTeamBudget(teamConfig.getPartyId());
        if (team == null) return null;
        return new TeamClaimsIntegration.BudgetNumbers(team.claims(), team.claimLimit(), team.forceloads(), team.forceloadLimit());
    }

    /**
     * Whether the client of this player has to show the counts the server sends instead of counting the synced claims
     * itself: always for a player with a team sub-config, as the client would count their own team claims as private
     * claims and knows nothing of the team claims of the other members.
     */
    public boolean usesServerSideClaimCounts(UUID playerId) {
        return getTeamSubIndex(playerId) != -1;
    }

    /**
     * Remembers the private forceload limit of an online player and tells whether it changed since the last call
     * while the player's claim limits packet carries the team's limits. OPAC notices a changed forceload limit (and
     * then updates the player's forceload tickets) by comparing the limits it synced, which only works while those
     * are the private ones.
     */
    public boolean hasPrivateForceloadLimitChangedUnnoticed(UUID playerId, int privateForceloadLimit) {
        Integer previous = lastPrivateForceloadLimits.put(playerId, privateForceloadLimit);
        return previous != null && previous != privateForceloadLimit && getUsedTeamBudget(playerId) != null;
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
     * The numbers themselves are not assembled here: the hook in the limits builder of {@code ClaimingModes.PLAYER}
     * reports the team's numbers ({@link #getUsedTeamBudget}) or the player's private ones, so OPAC's own
     * {@code ClaimsManagerSynchronizer.syncClaimLimits} sends the right packet, and the hook in there makes the client
     * use these numbers ({@link #usesServerSideClaimCounts}). This only makes the update immediate for every member
     * instead of waiting for OPAC's once-per-second limits check, which also catches anything this misses (e.g. the
     * player selecting another sub-claim).
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
            refreshOffThreadOwnedCounts(playerId);
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

    // ==================== Budget checks (called by the bridge handler) ====================

    /**
     * While suppressed, a rejected budget check does not tell the player why in chat. For callers that report the
     * outcome themselves ({@code /teamclaims convert}); to be reset in a {@code finally}.
     */
    void setBudgetMessagesSuppressed(boolean suppressed) {
        budgetMessagesSuppressed = suppressed;
    }

    /**
     * The team's budget for a claim {@code playerId} makes with their team sub-config at {@code pos}: null if it
     * fits, else the rejection. Only what the claim adds to the team's totals counts: nothing for a chunk that
     * already is a team claim of this team (only its owner changes), one claim for anything else, including a private
     * claim of the same player that becomes a team claim. A forceloaded claim also adds a team forceload unless the
     * chunk already is one.
     * <p>
     * Nobody's private numbers matter here, and a team above its limit is refused like one at its limit.
     */
    @Nullable
    public ClaimResult<PlayerChunkClaim> checkTeamClaimBudget(UUID playerId, ClaimPos pos, boolean forceLoaded,
            @Nullable PlayerChunkClaim replacedClaim) {
        UUID partyId = getTeamPartyId(playerId);
        if (partyId == null) return null;//not a team claim after all
        TeamData teamData = teams().get(partyId);
        boolean replacesOwnTeamClaim = replacedClaim != null && partyId.equals(claimToParty.get(pos));
        boolean addsClaim = !replacesOwnTeamClaim;
        boolean addsForceload = forceLoaded && !(replacesOwnTeamClaim && teamData != null && teamData.isForceloaded(pos));
        if (!addsClaim && !addsForceload) return null;

        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
        BudgetSettings settings = getBudgetSettings(partyId);
        int members = party == null ? 0 : party.getMemberCount();
        int teamClaims = teamData == null ? 0 : teamData.getClaimCount();
        int teamForceloads = teamData == null ? 0 : teamData.getForceloadCount();
        int claimLimit = settings.claimLimit(members);
        int forceloadLimit = settings.forceloadLimit(members);
        ServerPlayer player = budgetMessagesSuppressed ? null : server.getPlayerList().getPlayer(playerId);
        if (addsClaim && teamClaims >= claimLimit) {
            if (members < settings.minMembers())
                tell(player, ChatFormatting.RED, "team_too_small", String.valueOf(settings.minMembers()), String.valueOf(members));
            else
                tell(player, ChatFormatting.RED, "team_claim_limit", String.valueOf(teamClaims), String.valueOf(claimLimit));
            return new ClaimResult<>(replacedClaim, ClaimResult.Type.CLAIM_LIMIT_REACHED);
        }
        if (addsForceload && teamForceloads >= forceloadLimit) {
            tell(player, ChatFormatting.RED, "team_forceload_limit", String.valueOf(teamForceloads), String.valueOf(forceloadLimit));
            return new ClaimResult<>(replacedClaim, ClaimResult.Type.FORCELOAD_LIMIT_REACHED);
        }
        return null;
    }

    /**
     * The team's forceload budget for turning the forceload of the team claim at {@code pos} on: null if it fits,
     * else the rejection. Whoever asks, only the team forceloads of the claim's team against its team forceload limit
     * count.
     */
    @Nullable
    public ClaimResult<PlayerChunkClaim> checkTeamForceloadBudget(UUID requesterId, ClaimPos pos, PlayerChunkClaim currentClaim) {
        UUID partyId = claimToParty.get(pos);
        if (partyId == null) partyId = getTeamPartyId(currentClaim.getPlayerId());
        if (partyId == null) return null;
        TeamBudget team = getTeamBudget(partyId);
        int teamForceloads = team == null ? 0 : team.forceloads();
        int forceloadLimit = team == null ? 0 : team.forceloadLimit();
        if (teamForceloads < forceloadLimit) return null;
        if (!budgetMessagesSuppressed)
            tell(server.getPlayerList().getPlayer(requesterId), ChatFormatting.RED, "team_forceload_limit",
                    String.valueOf(teamForceloads), String.valueOf(forceloadLimit));
        return new ClaimResult<>(currentClaim, ClaimResult.Type.FORCELOAD_LIMIT_REACHED);
    }

    /**
     * The player's private forceload budget for a forceloaded private claim of {@code playerId} that replaces
     * {@code replacedClaim} (only {@code /teamclaims convert topersonal} makes such a claim; OPAC's own forceload
     * toggle is checked by OPAC): null if it fits, else the rejection. Nothing is added if the chunk already is a
     * forceloaded private claim of the player.
     */
    @Nullable
    public ClaimResult<PlayerChunkClaim> checkPrivateForceloadBudget(UUID playerId, @Nullable PlayerChunkClaim replacedClaim) {
        if (replacedClaim != null && replacedClaim.isForceloadable() && playerId.equals(replacedClaim.getPlayerId())
                && !isTeamClaim(replacedClaim))
            return null;
        IServerClaimsManagerAPI claimsAPI = OpenPACServerAPI.get(server).getServerClaimsManager();
        var info = claimsAPI.getPlayerInfo(playerId);
        if (info == null) return null;
        int privateForceloads = Math.max(0, info.getForceloadCount() - getOwnedTeamForceloadCount(playerId));
        int forceloadLimit = claimsAPI.getPlayerFullForceloadLimit(playerId);
        if (privateForceloads < forceloadLimit) return null;
        if (!budgetMessagesSuppressed)
            tell(server.getPlayerList().getPlayer(playerId), ChatFormatting.RED, "private_forceload_limit",
                    String.valueOf(privateForceloads), String.valueOf(forceloadLimit));
        return new ClaimResult<>(replacedClaim, ClaimResult.Type.FORCELOAD_LIMIT_REACHED);
    }

    // ==================== Over-limit grace ====================

    /** Checks every team that has team claims against its limits, see {@link #checkOverLimit}. */
    private void checkAllOverLimits() {
        for (UUID partyId : new ArrayList<>(teams().keySet())) {
            try {
                checkOverLimit(partyId);
            } catch (Exception e) {
                LOGGER.warn("[TeamClaims] Failed to check party {} against its team limits", partyId, e);
            }
        }
    }

    /**
     * Compares the team claims and the team forceloads of a party with its limits and moves the over-limit grace
     * along, separately for the two:
     * <ul>
     *     <li>within the limit: a running deadline is cancelled and the online members are told;</li>
     *     <li>above the limit without a deadline: one starts, {@code overLimitGraceHours} of real time from now, and
     *     the online members are warned. It is stored with the team (it survives restarts, and the time the server is
     *     off counts);</li>
     *     <li>above the limit with a running deadline: the deadline keeps its time whatever happens to the limit or
     *     the config in between, and the online members are reminded once every 24 real hours;</li>
     *     <li>above the limit at or after the deadline (or with a grace period of 0): the newest team claims are
     *     unclaimed, or the newest team forceloads turned off, until the team is within its limit, and the online
     *     members are told how many.</li>
     * </ul>
     * Called on every membership change, at server start, once a minute, and at the end of a tick in which a team with
     * a running deadline lost a team claim or forceload. Does nothing for a party without team claims or one that no
     * longer exists.
     */
    public void checkOverLimit(UUID partyId) {
        TeamData teamData = teams().get(partyId);
        if (teamData == null) return;
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
        if (party == null) return;//its removal is handled by the party event or the safety net
        BudgetSettings settings = getBudgetSettings(partyId);
        int members = party.getMemberCount();
        long now = clock.getAsLong();
        // Claims first: unclaiming a forceloaded team claim also takes a team forceload away
        checkOverLimit(party, teamData, settings, now, false, settings.claimLimit(members));
        checkOverLimit(party, teamData, settings, now, true, settings.forceloadLimit(members));
    }

    private void checkOverLimit(IServerPartyAPI party, TeamData teamData, BudgetSettings settings, long now,
            boolean forceloads, int limit) {
        UUID partyId = party.getId();
        String what = forceloads ? "forceloads" : "claims";
        int count = forceloads ? teamData.getForceloadCount() : teamData.getClaimCount();
        int over = count - limit;
        long deadline = forceloads ? teamData.getForceloadDeadline() : teamData.getClaimDeadline();
        if (over <= 0) {
            if (deadline == 0) return;
            teamData.setOverLimit(forceloads, 0, 0);
            markSavedDataDirty();
            LOGGER.info("[TeamClaims] Party {} is within its team {} limit again ({} / {}), the deadline is cancelled",
                    partyId, what, count, limit);
            tellMembers(party, ChatFormatting.GREEN, "over_limit_" + what + "_resolved", String.valueOf(count), String.valueOf(limit));
            return;
        }
        if (deadline == 0) {
            long graceMillis = settings.graceMillis();
            if (graceMillis > 0) {
                teamData.setOverLimit(forceloads, now + graceMillis, now);
                markSavedDataDirty();
                LOGGER.info("[TeamClaims] Party {} is {} above its team {} limit of {}: the newest are removed in {} hour(s) "
                        + "unless it gets back within the limit", partyId, over, what, limit, settings.overLimitGraceHours());
                tellMembers(party, ChatFormatting.RED, "over_limit_" + what + "_started", String.valueOf(over),
                        String.valueOf(limit), hoursOf(graceMillis), minutesOf(graceMillis));
                return;
            }
            //no grace period: removed right away
        } else if (now < deadline) {
            if (now - teamData.getOverLimitWarnedAt(forceloads) >= OVER_LIMIT_REMINDER_INTERVAL) {
                teamData.setOverLimit(forceloads, deadline, now);
                markSavedDataDirty();
                tellMembers(party, ChatFormatting.RED, "over_limit_" + what + "_reminder", String.valueOf(over),
                        String.valueOf(limit), hoursOf(deadline - now), minutesOf(deadline - now));
            }
            return;
        }

        int removed = forceloads ? removeNewestTeamForceloads(teamData, over) : removeNewestTeamClaims(teamData, over);
        int stillOver = (forceloads ? teamData.getForceloadCount() : teamData.getClaimCount()) - limit;
        // Whatever could not be removed now (e.g. a claim transfer in progress) is tried again at the next check
        teamData.setOverLimit(forceloads, stillOver > 0 ? (deadline == 0 ? now : deadline) : 0, stillOver > 0 ? now : 0);
        markSavedDataDirty();
        if (removed > 0) {
            LOGGER.info("[TeamClaims] Party {} stayed above its team {} limit of {}: removed the {} newest",
                    partyId, what, limit, removed);
            tellMembers(party, ChatFormatting.RED, "over_limit_" + what + "_enforced", String.valueOf(removed), String.valueOf(limit));
            markPartyLimitSync(partyId);
        }
    }

    /**
     * Unclaims the {@code count} most recently made team claims of the team, newest first, with OPAC's low-level
     * unclaim (no role or permission involved, this is the server acting).
     *
     * @return how many team claims the team has less afterwards
     */
    private int removeNewestTeamClaims(TeamData teamData, int count) {
        List<ClaimPos> newest = teamData.newestClaims(count);
        IServerClaimsManagerAPI claimsAPI = OpenPACServerAPI.get(server).getServerClaimsManager();
        if (anyOwnerHasClaimTask(claimsAPI, teamData, newest)) return 0;
        int before = teamData.getClaimCount();
        for (ClaimPos pos : newest) {
            try {
                // Only what really is a team claim of this team is unclaimed; anything else was tracked by mistake
                if (isLiveTeamClaimOf(teamData.partyId, claimsAPI.get(pos.dimension, pos.x, pos.z)))
                    claimsAPI.unclaim(pos.dimension, pos.x, pos.z);
                else
                    untrackClaim(teamData.partyId, pos);
            } catch (Exception e) {
                LOGGER.warn("[TeamClaims] Failed to unclaim the team claim at {} of a team above its limit", pos, e);
            }
        }
        return before - teamData.getClaimCount();
    }

    private boolean isLiveTeamClaimOf(UUID partyId, @Nullable IPlayerChunkClaimAPI claim) {
        return isTeamClaim(claim) && partyId.equals(getTeamPartyId(claim.getPlayerId()));
    }

    /**
     * Turns the {@code count} most recently made team forceloads of the team off, newest first. The claims stay.
     *
     * @return how many team forceloads the team has less afterwards
     */
    private int removeNewestTeamForceloads(TeamData teamData, int count) {
        List<ClaimPos> newest = teamData.newestForceloads(count);
        IServerClaimsManagerAPI claimsAPI = OpenPACServerAPI.get(server).getServerClaimsManager();
        if (anyOwnerHasClaimTask(claimsAPI, teamData, newest)) return 0;
        int before = teamData.getForceloadCount();
        for (ClaimPos pos : newest) {
            try {
                IPlayerChunkClaimAPI current = claimsAPI.get(pos.dimension, pos.x, pos.z);
                if (!isLiveTeamClaimOf(teamData.partyId, current)) {
                    untrackClaim(teamData.partyId, pos);//tracked by mistake
                } else if (current.isForceloadable()) {
                    unforceload(claimsAPI, pos);
                } else if (teamData.setForceloaded(pos, false)) {
                    //tracked as forceloaded by mistake
                    forceLoadHandler.removeForceLoad(pos.dimension, pos.x, pos.z);
                    markSavedDataDirty();
                }
            } catch (Exception e) {
                LOGGER.warn("[TeamClaims] Failed to turn the team forceload at {} of a team above its limit off", pos, e);
            }
        }
        return before - teamData.getForceloadCount();
    }

    /** See {@link #hasClaimTaskInProgress}: no claim of such an owner is changed underneath the task. */
    private boolean anyOwnerHasClaimTask(IServerClaimsManagerAPI claimsAPI, TeamData teamData, List<ClaimPos> claims) {
        Set<UUID> owners = new HashSet<>();
        for (ClaimPos pos : claims) {
            UUID owner = teamData.getOwner(pos);
            if (owner != null && owners.add(owner) && hasClaimTaskInProgress(claimsAPI, owner)) {
                LOGGER.debug("[TeamClaims] Not removing team claims above the limit yet: a claim transfer/replacement of {} is in progress",
                        owner);
                return true;
            }
        }
        return false;
    }

    /** At login: a member of a team with a running over-limit deadline is told about it. */
    private void remindOfOverLimit(IServerPartyAPI party, ServerPlayer player) {
        TeamData teamData = teams().get(party.getId());
        if (teamData == null || !teamData.hasOverLimitDeadline()) return;
        TeamBudget team = teamBudgetOf(party);
        long now = clock.getAsLong();
        if (team.claimDeadline() != 0 && team.claimsOverLimit() > 0) {
            long left = Math.max(0, team.claimDeadline() - now);
            tell(player, ChatFormatting.RED, "over_limit_claims_reminder", String.valueOf(team.claimsOverLimit()),
                    String.valueOf(team.claimLimit()), hoursOf(left), minutesOf(left));
        }
        if (team.forceloadDeadline() != 0 && team.forceloadsOverLimit() > 0) {
            long left = Math.max(0, team.forceloadDeadline() - now);
            tell(player, ChatFormatting.RED, "over_limit_forceloads_reminder", String.valueOf(team.forceloadsOverLimit()),
                    String.valueOf(team.forceloadLimit()), hoursOf(left), minutesOf(left));
        }
    }

    /** The time until {@code deadline} (epoch milliseconds) by this manager's clock, never negative. */
    public long millisUntil(long deadline) {
        return Math.max(0, deadline - clock.getAsLong());
    }

    /** The whole hours of a time span that is shown as hours and minutes, rounded up to the minute. */
    static String hoursOf(long millis) {
        return String.valueOf((millis + 59_999) / 60_000 / 60);
    }

    /** The minutes beyond the whole hours of a time span that is shown as hours and minutes, rounded up to the minute. */
    static String minutesOf(long millis) {
        return String.valueOf((millis + 59_999) / 60_000 % 60);
    }

    // ==================== Messages ====================

    /** A localized {@code gui.xaero_pac_team_claims_*} chat line for one player, nothing if {@code player} is null. */
    private void tell(@Nullable ServerPlayer player, ChatFormatting style, String key, Object... args) {
        if (player == null) return;
        player.sendSystemMessage(OpenPACServerAPI.get(server).getAdaptiveTextLocalizer()
                .getFor(player, KEY + key, args).withStyle(style));
    }

    private void tellMembers(IServerPartyAPI party, ChatFormatting style, String key, Object... args) {
        party.getOnlineMemberStream().forEach(member -> tell(member, style, key, args));
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
     * A player joined a party (event-driven, see {@code TeamConfigManager}). The team limits grow with the member
     * count, which may end an over-limit deadline, and everybody's numbers change.
     */
    public void onPlayerJoinedParty(UUID partyId, UUID playerUUID) {
        forgetPlayer(playerUUID);
        markPartyLimitSync(partyId);
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
        if (party != null) updateForceloadActivation(party);
        checkOverLimit(partyId);
    }

    /**
     * A member left or was kicked from a party that still exists. Every team claim technically
     * owned by them is re-assigned to the current party owner (as an owner team claim), so that the
     * team keeps its territory. Must be called <b>before</b> the leaver's team sub-config is
     * removed. Works with the leaver and/or the owner offline.
     * <p>
     * The hand-over changes nothing about the team's totals, but the team limits shrink with the member count: a
     * team that is above them now gets an over-limit deadline ({@link #checkOverLimit}).
     * <p>
     * Whatever cannot be transferred keeps the legacy behaviour: it stops being tracked and stays a
     * private claim of the leaver (their team sub-config is removed right after this, so the claim
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
        markPlayerLimitSync(playerUUID);//no longer a member, but their own numbers just changed
        if (party != null) {
            updateForceloadActivation(party);
            if (transferred > 0) notifyTeamClaimsTransferred(party, playerUUID, transferred);
        }
        checkOverLimit(partyId);
    }

    /**
     * Re-assigns the given tracked team claims of {@code leaverId} to the current party owner,
     * keeping the forceloadable flag, in every dimension.
     * <p>
     * This is budget-neutral — the team's totals don't change, the claims stay team claims and nobody's
     * private count is involved — so it deliberately does <b>not</b> go through
     * {@code tryToClaim*} and its limit checks. It uses the same low-level
     * {@link IServerClaimsManagerAPI#claim} call OPAC's own claim replacement/transfer tasks use,
     * which fires the normal claims tracker callbacks: {@link #onChunkChange} therefore does all
     * the TeamData bookkeeping (owner, per-player counts, force-load state; a claim keeps its place in the claim
     * order, as only its owner changes), OPAC moves its own
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
                    + "transfer/replacement is in progress. They stay private claims of the leaving player.",
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
        String countText = String.valueOf(count);
        tell(server.getPlayerList().getPlayer(leaverId), ChatFormatting.YELLOW, "transfer_leaver", countText);
        UUID ownerId = ownerIdOf(party);
        tell(ownerId == null ? null : server.getPlayerList().getPlayer(ownerId), ChatFormatting.YELLOW, "transfer_owner",
                resolvePlayerName(leaverId), countText);
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
        offThreadOwnedClaims.remove(playerUUID);
        offThreadOwnedForceloads.remove(playerUUID);
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
     * indexes always agree with {@link #claimOwners}.
     * <p>
     * Order: {@link #claimOwners} iterates in the order in which the chunks became team claims of this team, and
     * {@link #forceLoadedChunks} in the order in which they became team forceloads. A change of the owner (the
     * hand-over of a leaving member) keeps a claim's place; a chunk that stops being a team claim and becomes one
     * again later is a new, newest claim. Both orders are what is persisted (the positions, as two lists), so they
     * survive a restart; owners and the forceload flags themselves are rebuilt from the live claims at server start
     * ({@link TeamClaimManager#validateTeam}). Saves of older versions have the same two lists, in the same order.
     * <p>
     * Also holds the team's over-limit deadlines (real time, epoch milliseconds, 0 = none), which are persisted too.
     */
    public static final class TeamData {
        private final UUID partyId;
        /** Tracked team claim to its owner (the member who technically owns it), oldest team claim first. */
        private final Map<ClaimPos, UUID> claimOwners = new LinkedHashMap<>();
        /** The forceloaded team claims, oldest team forceload first. */
        private final Set<ClaimPos> forceLoadedChunks = new LinkedHashSet<>();
        private final Map<UUID, Set<ClaimPos>> claimsByOwner = new HashMap<>();
        private final Map<UUID, Integer> forceloadsByOwner = new HashMap<>();
        /** Positions read from disk, until {@link TeamClaimManager#validateTeam} turns them into tracked claims. */
        @Nullable
        private List<ClaimPos> loadedClaims;
        @Nullable
        private List<ClaimPos> loadedForceloads;
        /** When the team claims above the limit are removed, and when the members were last told. */
        private long claimDeadline;
        private long claimWarnedAt;
        /** The same for the team forceloads above the limit. */
        private long forceloadDeadline;
        private long forceloadWarnedAt;

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
        /** The tracked team claims, in the order in which they became team claims (oldest first). */
        public Set<ClaimPos> getTrackedClaims() { return Collections.unmodifiableSet(claimOwners.keySet()); }
        /** The forceloaded team claims, in the order in which they became team forceloads (oldest first). */
        public Set<ClaimPos> getForceloadedClaims() { return Collections.unmodifiableSet(forceLoadedChunks); }
        Set<ClaimPos> claimsOf(UUID owner) {
            Set<ClaimPos> owned = claimsByOwner.get(owner);
            return owned == null ? Collections.emptySet() : Collections.unmodifiableSet(owned);
        }

        /** When the team claims above the limit are unclaimed (epoch milliseconds), 0 if no deadline is running. */
        public long getClaimDeadline() { return claimDeadline; }
        /** When the team forceloads above the limit are turned off (epoch milliseconds), 0 if no deadline is running. */
        public long getForceloadDeadline() { return forceloadDeadline; }
        public boolean hasOverLimitDeadline() { return claimDeadline != 0 || forceloadDeadline != 0; }

        private long getOverLimitWarnedAt(boolean forceloads) { return forceloads ? forceloadWarnedAt : claimWarnedAt; }

        private void setOverLimit(boolean forceloads, long deadline, long warnedAt) {
            if (forceloads) {
                forceloadDeadline = deadline;
                forceloadWarnedAt = warnedAt;
            } else {
                claimDeadline = deadline;
                claimWarnedAt = warnedAt;
            }
        }

        /** The {@code count} most recently made team claims, newest first. */
        private List<ClaimPos> newestClaims(int count) {
            return newest(claimOwners.keySet(), count);
        }

        /** The {@code count} most recently made team forceloads, newest first. */
        private List<ClaimPos> newestForceloads(int count) {
            return newest(forceLoadedChunks, count);
        }

        private static List<ClaimPos> newest(Collection<ClaimPos> oldestFirst, int count) {
            List<ClaimPos> all = new ArrayList<>(oldestFirst);
            List<ClaimPos> result = new ArrayList<>(all.subList(Math.max(0, all.size() - Math.max(0, count)), all.size()));
            Collections.reverse(result);
            return result;
        }

        /** Tracks a claim (as the newest one) or changes the owner of a tracked one (which keeps its place). */
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

        private List<ClaimPos> takeLoadedForceloads() {
            List<ClaimPos> result = loadedForceloads == null ? List.of() : loadedForceloads;
            loadedForceloads = null;
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
            Collection<ClaimPos> forceloads = loadedForceloads != null ? loadedForceloads : forceLoadedChunks;
            for (ClaimPos pos : forceloads) forceList.add(pos.save());
            tag.put("forceLoadedChunks", forceList);
            //only written while a deadline is running
            if (claimDeadline != 0) {
                tag.putLong("overLimitDeadline", claimDeadline);
                tag.putLong("overLimitWarnedAt", claimWarnedAt);
            }
            if (forceloadDeadline != 0) {
                tag.putLong("forceloadOverLimitDeadline", forceloadDeadline);
                tag.putLong("forceloadOverLimitWarnedAt", forceloadWarnedAt);
            }
            return tag;
        }

        /**
         * Reads the stored positions, both lists in their stored order, and the over-limit deadlines, which are not
         * there in saves from before the separate budgets (and whenever no deadline is running).
         */
        public static TeamData load(CompoundTag tag) {
            TeamData data = new TeamData(tag.getUUID("partyId"));
            data.loadedClaims = loadPositions(tag.getList("trackedClaims", Tag.TAG_COMPOUND));
            data.loadedForceloads = loadPositions(tag.getList("forceLoadedChunks", Tag.TAG_COMPOUND));
            data.claimDeadline = tag.getLong("overLimitDeadline");
            data.claimWarnedAt = tag.getLong("overLimitWarnedAt");
            data.forceloadDeadline = tag.getLong("forceloadOverLimitDeadline");
            data.forceloadWarnedAt = tag.getLong("forceloadOverLimitWarnedAt");
            return data;
        }

        private static List<ClaimPos> loadPositions(ListTag list) {
            List<ClaimPos> positions = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) positions.add(ClaimPos.load(list.getCompound(i)));
            return positions;
        }

        /** The stored team claim positions of data that was loaded but not validated yet, in their stored order. */
        @VisibleForTesting
        public List<ClaimPos> getLoadedClaims() {
            return loadedClaims == null ? List.of() : Collections.unmodifiableList(loadedClaims);
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

        /** The data of one team, null if it has none. */
        @VisibleForTesting
        @Nullable
        public TeamData getTeamData(UUID partyId) { return teams.get(partyId); }

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
