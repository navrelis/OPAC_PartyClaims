package xaero.pac.teamclaims.ftbsync;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The optional two-way sync between OPAC parties and FTB Teams party teams, so that players only manage one team.
 * <p>
 * <b>What is synced:</b> a party existing at all (create / disband), its members (join, leave, kick), its owner,
 * its name (OPAC's {@code PARTY_NAME} option and FTB's {@code display_name} property, see {@link FtbSyncNames}),
 * ranks (OPAC owner = FTB owner; OPAC ADMIN and MODERATOR = FTB officer; OPAC CLAIMER and MEMBER = FTB member) and
 * pending invitations. <b>Not synced:</b> allies (the two mods mean different things by them: FTB allies single
 * players, OPAC whole parties), FTB's team color, description and every other team property, and party chat.
 * <p>
 * <b>How:</b> every OPAC party is paired with one FTB party team ({@link FtbSyncStore}), and the sync is a
 * three-way merge per pair: the two current states are compared with the baseline of their last agreement, which
 * says which side changed, and that change is applied to the other side ({@link FtbSyncEngine}). A difference that
 * the baseline cannot explain (first sync, both sides changed) is decided for OPAC. Events only say <i>when</i> to
 * merge a pair: the {@code [Team Claims]} party hooks on the OPAC side and FTB Teams' {@code TeamEvent}s only add
 * a {@link Work} item to a queue, and the queue is worked off in order at the end of the server tick. What has no
 * event in FTB Teams (invitations, declining one, promoting and demoting) is found by merging every pair every
 * {@link #PAIR_INTERVAL} ticks. A full reconciliation, which also looks for parties that exist in only one mod, runs
 * once when the server has started and both mods have loaded their data, when FTB Teams gets to know a player (their
 * first login with it installed, for whatever waited for that), every {@link #FULL_INTERVAL} ticks, and on
 * {@code /teamclaims ftbsync resync}.
 * <p>
 * <b>Removals:</b> a party that is removed in one mod while the sync sees it happen (a hook or an event says so) is
 * removed in the other mod too; the note of that is kept with the pair ({@link FtbSyncStore.Pair#isOpacRemoved()},
 * {@link FtbSyncStore.Pair#isFtbRemoved()}) until it is done, however long that takes. A side that is simply gone
 * (the server was down, the sync was off) is never a reason to delete anything: the missing side is made again.
 * The same goes for an FTB party that is deleted while its OPAC party has members who were not in it yet.
 * <p>
 * <b>No feedback loops:</b> while the sync applies changes ({@link #isApplying()}), the notes of both sides are
 * ignored, and the merge itself is idempotent: it only writes where the two sides differ and records the new
 * baseline, so merging a pair twice changes nothing the second time.
 * <p>
 * <b>Isolation:</b> this class and everything it needs when FTB Teams is absent reference no FTB class.
 * {@link #createIfEnabled} only creates the {@link FtbSyncEngine} (the only class besides {@link FtbSyncEvents} that
 * does) when FTB Teams is installed, and without it returns null, so nothing here runs at all. The engine is only
 * ever named here as the type of a field and as the receiver of calls, which the JVM resolves when they are first
 * executed, so loading and verifying this class loads neither the engine nor any FTB class.
 * <p>
 * <b>Failures:</b> nothing that goes wrong in here reaches the server's tick or FTB Teams' own code. An unexpected
 * exception while merging one pair is recorded as pending for that pair and the others go on; a
 * {@link LinkageError} (an FTB Teams version without the classes and methods used here) stops the sync for this
 * session and is reported by the status command.
 * <p>
 * Server-thread confined, like the rest of Team Claims.
 */
public final class FtbTeamsSync {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Every pair is merged this often, for the FTB Teams changes that have no event. */
    public static final int PAIR_INTERVAL = 20;
    /** A full reconciliation runs this often, as a safety net. */
    static final int FULL_INTERVAL = 1200;
    /** A full reconciliation repeats until nothing changes any more, at most this many times. */
    private static final int MAX_FULL_PASSES = 4;
    /** While something stays pending, the log is reminded of it at most this often (with one line for all of it). */
    private static final long WARN_REPEAT_MILLIS = 60L * 60L * 1000L;

    /** Why the sync is not running on a server where Team Claims is active, as a message key. */
    public static final String OFF_CONFIG = "gui.xaero_pac_team_claims_ftbsync_status_off_config";
    public static final String OFF_NO_FTB_TEAMS = "gui.xaero_pac_team_claims_ftbsync_status_off_no_ftb_teams";
    public static final String OFF_ERROR = "gui.xaero_pac_team_claims_ftbsync_status_off_error";

    enum WorkType { OPAC_PARTY_CHANGED, OPAC_PARTY_REMOVED, FTB_TEAM_CHANGED, FTB_TEAM_DELETED, FTB_PLAYER_KNOWN }

    /** One note taken by a hook or a listener: what happened to which party, team or player. */
    record Work(WorkType type, UUID id) {}

    /** Identifies one thing that could not be synced: about which party or team, what, and which player. */
    private record PendingKey(UUID subject, String kind, @Nullable UUID playerId) {}

    private static final class PendingItem {
        String reasonKey;
        Object[] reasonArgs;
        String label;
        boolean seen;
    }

    /** One line of {@code /teamclaims ftbsync status}: the party it is about and why something is pending. */
    public record PendingView(String label, String reasonKey, Object[] reasonArgs) {}

    private final MinecraftServer server;
    private final FtbSyncOpac opac;
    private final FtbSyncEngine engine;
    private FtbSyncStore store = new FtbSyncStore();
    private final ArrayDeque<Work> queue = new ArrayDeque<>();
    private final Map<PendingKey, PendingItem> pending = new LinkedHashMap<>();
    private boolean applying;
    private boolean serverStarted;
    /** False until the full reconciliation at the start has run, and again whenever the sync could not work for a while. */
    private boolean initialSyncDone;
    private boolean failed;
    private boolean paused;
    private int tickCounter;
    private long appliedChanges;
    private long lastTickErrorMillis;
    private long lastPendingLogMillis = System.currentTimeMillis();

    private FtbTeamsSync(MinecraftServer server) {
        this.server = server;
        this.opac = new FtbSyncOpac(server);
        this.engine = new FtbSyncEngine(server, this, opac);
    }

    /**
     * Creates the sync for this server if it is to run: the {@code ftbTeamsSync} option is on and FTB Teams is
     * installed. Called while Team Claims registers itself, so only on a server where Team Claims is active.
     *
     * @param offReason  receives the message key of the reason when null is returned
     */
    @Nullable
    public static FtbTeamsSync createIfEnabled(MinecraftServer server, String[] offReason) {
        if (!TeamClaimsServerConfig.CONFIG.ftbTeamsSync.get()) {
            offReason[0] = OFF_CONFIG;
            LOGGER.info("[TeamClaims] FTB Teams sync is off ('ftbTeamsSync' = false in {})", TeamClaimsServerConfig.FILE_NAME);
            return null;
        }
        if (OpenPartiesAndClaims.INSTANCE == null || !OpenPartiesAndClaims.INSTANCE.getModSupport().FTB_TEAMS) {
            offReason[0] = OFF_NO_FTB_TEAMS;
            return null;
        }
        try {
            FtbTeamsSync sync = new FtbTeamsSync(server);
            sync.engine.registerListeners();
            LOGGER.info("[TeamClaims] FTB Teams found: parties are kept in sync with FTB Teams party teams");
            return sync;
        } catch (LinkageError e) {
            offReason[0] = OFF_ERROR;
            LOGGER.error("[TeamClaims] FTB Teams sync could not start, this FTB Teams version is not supported", e);
            return null;
        }
    }

    // ==================== Lifecycle ====================

    /** Loads the saved pairs. Called when the server has started, after the Team Claims managers. */
    public void onServerStarted() {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            LOGGER.error("[TeamClaims] The overworld isn't loaded, the FTB Teams sync state won't be saved this session");
            store = new FtbSyncStore();
        } else {
            store = overworld.getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(FtbSyncStore::new, FtbSyncStore::load, null), FtbSyncStore.DATA_NAME);
        }
        serverStarted = true;
    }

    /**
     * End of every server tick, after Team Claims processed its own party events: works off the queued notes in
     * order, then runs whichever periodic pass is due. The caller processes the party events again afterwards,
     * so what the sync changed in OPAC reaches Team Claims in the same tick.
     */
    public void tick() {
        if (failed || paused) return;
        try {
            if (!serverStarted || !opac.partiesEnabled() || !engine.isFtbReady()) {
                // Nothing to sync with right now (parties are disabled, FTB Teams has no data loaded). The notes
                // taken meanwhile are dropped; the full reconciliation that runs when this is over covers them.
                queue.clear();
                initialSyncDone = false;
                return;
            }
            if (!initialSyncDone) {
                // Once both mods have loaded their data. Whatever was noted before that is covered by it.
                initialSyncDone = true;
                queue.clear();
                runFullReconciliation();
                return;
            }
            processQueue();
            tickCounter++;
            if (tickCounter % FULL_INTERVAL == 0) runFullReconciliation();
            else if (tickCounter % PAIR_INTERVAL == 0) reconcilePairs();
        } catch (LinkageError e) {
            fail(e);
        } catch (RuntimeException e) {
            // Never into the server's tick. Everything that merges a pair catches its own failures, so this is
            // not expected; if it happens every tick, it is not logged every tick.
            long now = System.currentTimeMillis();
            if (now - lastTickErrorMillis >= WARN_REPEAT_MILLIS) {
                lastTickErrorMillis = now;
                LOGGER.error("[TeamClaims] FTB Teams sync failed in this tick; it goes on with the next one", e);
            }
        }
    }

    public void shutdown() {
        queue.clear();
        pending.clear();
        serverStarted = false;
    }

    private void fail(LinkageError e) {
        failed = true;
        queue.clear();
        LOGGER.error("[TeamClaims] FTB Teams sync stopped: this FTB Teams version is not supported. "
                + "Parties are no longer synced until the server is restarted with a supported version.", e);
    }

    // ==================== Notes from the two sides ====================

    /** An OPAC party hook fired: the party got a member, lost one, changed its owner, name, a rank or an invitation. */
    public void onOpacPartyChanged(UUID partyId) {
        note(WorkType.OPAC_PARTY_CHANGED, partyId);
    }

    /**
     * An OPAC party was removed (destroyed by its owner, expired, removed by an admin) and the sync sees it happen:
     * its FTB party is to go with it. That is noted with the pair, see {@link FtbSyncStore.Pair#isOpacRemoved()}.
     */
    public void onOpacPartyRemoved(UUID partyId) {
        if (applying || failed || paused) return;
        FtbSyncStore.Pair pair = store.byOpacParty(partyId);
        if (pair != null) pair.markOpacRemoved();
        note(WorkType.OPAC_PARTY_REMOVED, partyId);
    }

    void onFtbTeamChanged(UUID teamId) {
        note(WorkType.FTB_TEAM_CHANGED, teamId);
    }

    /**
     * An FTB party team was deleted (its last member left, or it was force-disbanded) and the sync sees it happen:
     * its OPAC party is to go with it, unless that has members who were not in the FTB party yet. That is noted
     * with the pair, see {@link FtbSyncStore.Pair#isFtbRemoved()}.
     */
    void onFtbTeamDeleted(UUID teamId) {
        if (applying || failed || paused) return;
        FtbSyncStore.Pair pair = store.byFtbTeam(teamId);
        if (pair != null) pair.markFtbRemoved();
        note(WorkType.FTB_TEAM_DELETED, teamId);
    }

    /** FTB Teams created the personal team of a player, i.e. it knows the player from now on. */
    void onFtbPlayerKnown(UUID playerId) {
        note(WorkType.FTB_PLAYER_KNOWN, playerId);
    }

    private void note(WorkType type, UUID id) {
        if (applying || failed || paused) return;//our own change echoing back (or not to be seen, in a test)
        Work last = queue.peekLast();
        if (last != null && last.type() == type && last.id().equals(id)) return;
        queue.add(new Work(type, id));
    }

    /** Whether the sync is applying changes right now, in which case notes from either side are its own echo. */
    public boolean isApplying() {
        return applying;
    }

    // ==================== Processing ====================

    private void processQueue() {
        if (queue.isEmpty()) return;
        boolean playerKnown = false;
        applying = true;
        try {
            Work work;
            while ((work = queue.poll()) != null) {
                if (work.type() == WorkType.FTB_PLAYER_KNOWN) {
                    playerKnown = true;
                    continue;
                }
                try {
                    engine.handle(work);
                } catch (RuntimeException e) {
                    LOGGER.warn("[TeamClaims] FTB Teams sync failed to process {}; the next reconciliation will retry", work, e);
                }
            }
        } finally {
            applying = false;
        }
        // Whatever waited for a player to be known to FTB Teams (a member to add, an owner to make, an FTB party to
        // create) can be done now
        if (playerKnown) runFullReconciliation();
    }

    /** Merges every pair once. */
    private void reconcilePairs() {
        applying = true;
        try {
            for (FtbSyncStore.Pair pair : store.pairs()) {
                try {
                    engine.reconcilePair(pair);
                } catch (RuntimeException e) {
                    LOGGER.warn("[TeamClaims] FTB Teams sync failed to reconcile {}", pair, e);
                }
            }
        } finally {
            applying = false;
        }
    }

    /**
     * Brings all parties of both mods in line: pairs up the parties that exist in only one of them (creating the
     * other side) and merges every pair, repeating while that still changes something (a change to one pair can
     * make another one mergeable, e.g. a player moving between two FTB parties).
     *
     * @return how many changes were applied to either mod
     */
    private long runFullReconciliation() {
        long before = appliedChanges;
        applying = true;
        try {
            for (PendingItem item : pending.values()) item.seen = false;
            boolean complete = true;
            long passStart;
            int passes = 0;
            do {
                passStart = appliedChanges;
                try {
                    engine.reconcileEverything();
                } catch (RuntimeException e) {
                    LOGGER.warn("[TeamClaims] FTB Teams sync failed during a full reconciliation", e);
                    complete = false;
                    break;
                }
            } while (appliedChanges != passStart && ++passes < MAX_FULL_PASSES);
            // What a complete pass did not find pending again is resolved (or its party is gone)
            if (complete) pending.values().removeIf(item -> !item.seen);
            remindOfPending();
        } finally {
            applying = false;
        }
        return appliedChanges - before;
    }

    /** One line for everything that is still pending, so that it is neither forgotten nor filling the log. */
    private void remindOfPending() {
        long now = System.currentTimeMillis();
        if (pending.isEmpty() || now - lastPendingLogMillis < WARN_REPEAT_MILLIS) return;
        lastPendingLogMillis = now;
        LOGGER.warn("[TeamClaims] FTB Teams sync: {} change(s) are still waiting to be synced, see /teamclaims ftbsync status",
                pending.size());
    }

    /**
     * {@code /teamclaims ftbsync resync}: the full reconciliation, now.
     *
     * @return how many changes were applied, -1 if the sync cannot run right now (see {@link #getStateKey()})
     */
    public long resync() {
        if (!isRunning() || paused) return -1;
        try {
            queue.clear();
            return runFullReconciliation();
        } catch (LinkageError e) {
            fail(e);
            return -1;
        }
    }

    /**
     * While paused, the sync neither takes notes nor does anything at the end of the tick, as if the server ran
     * without it. Lets a test set up parties that the sync has not seen (the state after enabling it on an existing
     * server, or after one mod's data changed while the server was down) within one tick, before {@link #resync()}.
     */
    @VisibleForTesting
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) queue.clear();
    }

    /**
     * Works off the queued notes right away instead of at the end of the tick, so that a test can change a server
     * option only for the duration of one operation.
     */
    @VisibleForTesting
    public void runPendingWork() {
        if (!isRunning() || paused) return;
        try {
            processQueue();
        } catch (LinkageError e) {
            fail(e);
        }
    }

    // ==================== Used by the engine ====================

    void countChange() {
        appliedChanges++;
    }

    /** Starts the bookkeeping of what is pending about one party or team: what is not reported again is resolved. */
    void beginSubject(UUID subject) {
        for (Map.Entry<PendingKey, PendingItem> entry : pending.entrySet())
            if (entry.getKey().subject().equals(subject)) entry.getValue().seen = false;
    }

    void endSubject(UUID subject) {
        Iterator<Map.Entry<PendingKey, PendingItem>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PendingKey, PendingItem> entry = iterator.next();
            if (entry.getKey().subject().equals(subject) && !entry.getValue().seen) iterator.remove();
        }
    }

    /**
     * Records that something could not be synced and stays to be retried. The first time (and when the reason
     * changes) this is logged as a warning and told to the given players. While it stays pending, the merges that
     * find it again say nothing; the log only gets a reminder of everything that is pending, at most every
     * {@link #WARN_REPEAT_MILLIS}.
     *
     * @param subject  the OPAC party (or, for an FTB party without one, the FTB team) it is about
     * @param label  the name of that party, for the status command
     * @param tell  the players to tell, if they are online
     */
    void pending(UUID subject, String kind, @Nullable UUID playerId, String label, String logMessage,
            String reasonKey, Object[] reasonArgs, UUID... tell) {
        if (!record(subject, kind, playerId, label, logMessage, null, reasonKey, reasonArgs)) return;
        Set<UUID> told = new HashSet<>();
        for (UUID recipient : tell)
            if (recipient != null && told.add(recipient)) tell(recipient, reasonKey, reasonArgs);
    }

    /**
     * Records that merging a pair (or creating the party of an FTB party) failed with an unexpected exception. It
     * is pending like everything else that could not be done: listed by the status command, retried by the next
     * merge, and logged (with the stack trace) the first time.
     */
    void failure(UUID subject, String label, RuntimeException error) {
        record(subject, "error", null, label, "unexpected error: " + error, error,
                "gui.xaero_pac_team_claims_ftbsync_pending_error", new Object[] {error.toString()});
    }

    /** @return whether this is new: nothing was pending under this key, or it was for another reason */
    private boolean record(UUID subject, String kind, @Nullable UUID playerId, String label, String logMessage,
            @Nullable RuntimeException error, String reasonKey, Object[] reasonArgs) {
        PendingKey key = new PendingKey(subject, kind, playerId);
        PendingItem item = pending.get(key);
        boolean isNew = item == null || !reasonKey.equals(item.reasonKey) || !Arrays.equals(reasonArgs, item.reasonArgs);
        if (item == null) {
            item = new PendingItem();
            pending.put(key, item);
        }
        item.reasonKey = reasonKey;
        item.reasonArgs = reasonArgs;
        item.label = label;
        item.seen = true;
        if (isNew) {
            lastPendingLogMillis = System.currentTimeMillis();
            String message = "[TeamClaims] FTB Teams sync, party '{}': {}. This is retried until it works; "
                    + "see /teamclaims ftbsync status.";
            if (error == null) LOGGER.warn(message, label, logMessage);
            else LOGGER.warn(message, label, logMessage, error);
        }
        return isNew;
    }

    /** Tells an online player, in their language, what the sync did or could not do. */
    void tell(UUID playerId, String key, Object... args) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        IServerData<?, ?> data = ServerData.from(server);
        if (player == null || data == null) return;
        MutableComponent message = Component.literal("");
        message.append(data.getAdaptiveLocalizer().getFor(player, "gui.xaero_pac_team_claims_ftbsync_prefix")
                .withStyle(ChatFormatting.GOLD));
        message.append(data.getAdaptiveLocalizer().getFor(player, key, args).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(message);
    }

    // ==================== Status ====================

    /** Whether the sync is working right now (not stopped by an error, both mods ready). */
    public boolean isRunning() {
        return !failed && serverStarted && opac.partiesEnabled() && initialSyncDone;
    }

    /** The message key of the current state, for {@code /teamclaims ftbsync status}. */
    public String getStateKey() {
        if (failed) return OFF_ERROR;
        if (!opac.partiesEnabled()) return "gui.xaero_pac_team_claims_ftbsync_status_off_parties";
        if (!serverStarted || !initialSyncDone) return "gui.xaero_pac_team_claims_ftbsync_status_waiting";
        return "gui.xaero_pac_team_claims_ftbsync_status_active";
    }

    /** How many OPAC parties are linked to an FTB party team. */
    public int getLinkedPairCount() {
        return store.linkedCount();
    }

    public int getPendingCount() {
        return pending.size();
    }

    public List<PendingView> getPending() {
        List<PendingView> result = new ArrayList<>(pending.size());
        for (PendingItem item : pending.values()) result.add(new PendingView(item.label, item.reasonKey, item.reasonArgs));
        return result;
    }

    /** How many changes the sync has applied to either mod since the server started. */
    public long getAppliedChangeCount() {
        return appliedChanges;
    }

    /** The saved pairs. For the status command and tests. */
    public FtbSyncStore getStore() {
        return store;
    }
}
