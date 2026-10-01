package xaero.pac.teamclaims.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.player.config.PlayerConfigConstants;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.IPlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommon;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Owns the {@link TeamConfig} of every party and keeps it, and every member's team sub-config, in line with the real
 * parties.
 * <p>
 * Party changes are event-driven: the {@code [Team Claims]} hooks in OPAC's party code queue a {@link PartyEvent}
 * (member added/removed, owner changed, party name changed, party removed), and {@link #processPendingEvents()} works
 * through them in order at the end of the same server tick. A leave is processed while the leaver still has their
 * team sub-config, so their team claims can be handed over to the party owner first. {@link #reconcile()} compares
 * everything with the real parties once at server start and then every {@link #RECONCILE_INTERVAL} ticks, as a safety
 * net for anything that changes without an event (e.g. the owner's username, which the default party name uses).
 * <p>
 * Team sub-config settings: every successful owner/admin edit of a team sub-config goes through
 * {@code PlayerConfig.tryToSet}, whose hook calls {@link #onTeamSubConfigSettingChanged}, which persists the value
 * and propagates it to the other members. The only ways around that hook are (a) the player config files being edited
 * while the server is down, and (b) a team sub-config being created outside Team Claims (client packet, command,
 * claim transfer). Both are covered without polling: the team settings are applied again on every login, and the
 * {@code PlayerConfig.createSubConfig} hook ({@link #onTeamSubConfigExistenceChanged}) applies them to a sub-config
 * created by anything else.
 * <p>
 * Files: a changed config is only marked dirty; {@link #flushDirty()} serialises the dirty ones on the server thread
 * at the end of the tick and writes them (temp file + atomic move) on {@link Util#ioPool()}. All file operations of
 * one config are chained, so they never reorder. {@link #shutdown()} waits for them and writes what is still dirty
 * synchronously.
 * <p>
 * Threading: server-thread confined, except the file operations described above, which only get immutable data.
 */
public class TeamConfigManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ThreadLocal<Boolean> INTERNAL_EDIT = ThreadLocal.withInitial(() -> false);
    private static final int RECONCILE_INTERVAL = 1200;
    private static final DateTimeFormatter CORRUPT_SUFFIX_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public enum PartyEventType { MEMBER_ADDED, MEMBER_REMOVED, OWNER_CHANGED, NAME_CHANGED, PARTY_REMOVED }

    public record PartyEvent(PartyEventType type, UUID partyId, @Nullable UUID playerId) {}

    private final MinecraftServer server;
    private final Path configDir;
    private final Map<UUID, TeamConfig> teamConfigs = new HashMap<>();
    private final Map<UUID, UUID> playerToParty = new HashMap<>();
    private final ArrayDeque<PartyEvent> pendingEvents = new ArrayDeque<>();
    /** Players whose team sub-config was created by something other than Team Claims and still needs the team settings. */
    private final Set<UUID> pendingConfigure = new LinkedHashSet<>();
    private final Set<UUID> dirtyConfigs = new LinkedHashSet<>();
    /** The latest queued file operation per config, so that operations on one file never reorder. */
    private final Map<UUID, CompletableFuture<Void>> pendingFileOps = new ConcurrentHashMap<>();
    private int reconcileTickCounter = 0;

    public TeamConfigManager(MinecraftServer server) {
        this.server = server;
        // Parties are per-world, so the team config JSONs must live in the world save folder
        // instead of the server's global config directory (otherwise a second singleplayer
        // world would purge the first world's team configs via purgeOrphanedTeamConfigs).
        this.configDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("opacteamclaims").resolve("teams");
    }

    public Path getConfigDir() { return configDir; }

    // ==================== Loading ====================

    public void loadAll() {
        teamConfigs.clear();
        playerToParty.clear();
        pendingEvents.clear();
        try {
            if (!Files.exists(configDir)) Files.createDirectories(configDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "*.json")) {
                for (Path file : stream) {
                    TeamConfig config = loadConfigFile(file);
                    if (config != null) teamConfigs.put(config.getPartyId(), config);
                }
            }
            LOGGER.info("Loaded {} team configs from disk", teamConfigs.size());
        } catch (IOException e) {
            LOGGER.error("Failed to read the team config directory {}", configDir, e);
        }
        purgeOrphanedTeamConfigs();
        for (TeamConfig config : teamConfigs.values()) {
            for (UUID member : config.getMembers()) mapPlayer(member, config.getPartyId());
        }
        discoverExistingParties();
    }

    /**
     * Reads one team config file. A file that can't be read or parsed is renamed to
     * {@code <name>.json.corrupt-<timestamp>} so that its content is preserved, and null is returned; the party then
     * gets a fresh team config, written to the original file name.
     */
    @Nullable
    public TeamConfig loadConfigFile(Path file) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            return TeamConfig.fromJson(json);
        } catch (IOException | RuntimeException e) {
            Path preserved = preserveCorruptFile(file);
            LOGGER.error("[TeamClaims] Team config {} is unreadable or corrupt; {} — a fresh team config will be created for its party",
                    file.getFileName(),
                    preserved == null ? "could not preserve it either" : "preserved it as " + preserved.getFileName(), e);
            return null;
        }
    }

    @Nullable
    private Path preserveCorruptFile(Path file) {
        String baseName = file.getFileName() + ".corrupt-" + LocalDateTime.now().format(CORRUPT_SUFFIX_FORMAT);
        Path target = file.resolveSibling(baseName);
        for (int i = 1; Files.exists(target); i++) target = file.resolveSibling(baseName + "-" + i);
        try {
            Files.move(file, target);
            return target;
        } catch (IOException e) {
            LOGGER.warn("[TeamClaims] Could not rename the corrupt team config {}", file, e);
            return null;
        }
    }

    private void purgeOrphanedTeamConfigs() {
        IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
        List<UUID> toRemove = new ArrayList<>();
        for (UUID partyId : teamConfigs.keySet()) {
            if (partyManager.getPartyById(partyId) == null) toRemove.add(partyId);
        }
        for (UUID partyId : toRemove) removeTeamConfig(partyId);
    }

    private void discoverExistingParties() {
        IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
        List<IServerPartyAPI> partiesToCreate = new ArrayList<>();
        partyManager.getAllStream().forEach(party -> {
            if (!teamConfigs.containsKey(party.getId())) partiesToCreate.add(party);
        });
        for (IServerPartyAPI party : partiesToCreate) createTeamConfig(party);
    }

    // ==================== Saving ====================

    /** Marks a config for saving at the end of the tick. */
    public void markDirty(TeamConfig config) {
        dirtyConfigs.add(config.getPartyId());
    }

    /** Writes the configs that changed this tick, off the server thread. Called at the end of every tick. */
    public void flushDirty() {
        if (dirtyConfigs.isEmpty()) return;
        for (UUID partyId : dirtyConfigs) {
            TeamConfig config = teamConfigs.get(partyId);
            if (config == null) continue;
            Path file = fileFor(partyId);
            String content = config.toJsonString();
            enqueueFileOp(partyId, () -> writeAtomically(file, content));
        }
        dirtyConfigs.clear();
    }

    private Path fileFor(UUID partyId) {
        return configDir.resolve(partyId + ".json");
    }

    private void enqueueFileOp(UUID partyId, Runnable op) {
        CompletableFuture<Void> next = pendingFileOps.compute(partyId, (id, previous) ->
                (previous == null ? CompletableFuture.<Void>completedFuture(null) : previous)
                        .handleAsync((result, error) -> {
                            op.run();
                            return null;
                        }, Util.ioPool()));
        next.whenComplete((result, error) -> pendingFileOps.remove(partyId, next));
    }

    private void writeAtomically(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Path tmpFile = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmpFile, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("[TeamClaims] Failed to save team config {}", file, e);
        }
    }

    private void deleteConfigFile(UUID partyId) {
        Path file = fileFor(partyId);
        enqueueFileOp(partyId, () -> {
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
            } catch (IOException e) {
                LOGGER.warn("[TeamClaims] Failed to delete team config {}", file, e);
            }
        });
    }

    // ==================== Team configs ====================

    private boolean isSubConfigIdTaken(String subConfigId) {
        for (TeamConfig config : teamConfigs.values()) {
            if (config.getSubConfigId().equals(subConfigId)) return true;
        }
        return false;
    }

    public TeamConfig createTeamConfig(IServerPartyAPI party) {
        UUID partyId = party.getId();
        TeamConfig existing = teamConfigs.get(partyId);
        if (existing != null) return existing;
        String teamName = resolvePartyName(party);
        TeamConfig config = new TeamConfig(partyId, teamName,
                TeamConfig.allocateSubConfigId(teamName, partyId, this::isSubConfigIdTaken));
        config.setClaimsColor(TeamConfig.computeTeamColor(partyId));
        config.setClaimsName(teamName);
        teamConfigs.put(partyId, config);
        List<UUID> members = new ArrayList<>();
        party.getMemberInfoStream().forEach(member -> members.add(member.getUUID()));
        for (UUID member : members) {
            leaveStaleTeam(member, partyId);
            config.addMember(member);
            mapPlayer(member, partyId);
        }
        markDirty(config);
        for (UUID member : members) ensureSubConfigForPlayer(member, config);
        TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
        if (claimManager != null) claimManager.markPartyLimitSync(partyId);
        LOGGER.info("Created team config '{}' ({}) for party {} with {} members", teamName, config.getSubConfigId(),
                partyId, members.size());
        return config;
    }

    public void removeTeamConfig(UUID partyId) {
        TeamConfig config = teamConfigs.remove(partyId);
        if (config == null) return;
        String subConfigId = config.getSubConfigId();
        for (UUID memberUUID : new ArrayList<>(config.getMembers())) {
            unmapPlayer(memberUUID, partyId);
            removeSubConfigForPlayer(memberUUID, subConfigId);
            TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
            if (claimManager != null) {
                claimManager.forgetPlayer(memberUUID);
                claimManager.markPlayerLimitSync(memberUUID);
            }
        }
        dirtyConfigs.remove(partyId);
        deleteConfigFile(partyId);
    }

    public void onMemberJoined(UUID partyId, UUID playerUUID) {
        TeamConfig config = teamConfigs.get(partyId);
        if (config == null) return;
        leaveStaleTeam(playerUUID, partyId);
        if (config.addMember(playerUUID)) markDirty(config);
        mapPlayer(playerUUID, partyId);
        ensureSubConfigForPlayer(playerUUID, config);
        TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
        if (claimManager != null) claimManager.onPlayerJoinedParty(partyId, playerUUID);
    }

    /**
     * A member left. Their team claims are handed over to the party owner while they still have their team
     * sub-config, and only then is the sub-config removed.
     */
    public void onMemberLeft(UUID partyId, UUID playerUUID) {
        TeamConfig config = teamConfigs.get(partyId);
        if (config == null || !config.removeMember(playerUUID)) return;
        unmapPlayer(playerUUID, partyId);
        markDirty(config);
        TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
        if (claimManager != null) claimManager.onPlayerLeftParty(partyId, playerUUID);
        removeSubConfigForPlayer(playerUUID, config.getSubConfigId());
    }

    /** A player can only be in one team; a membership in a different team that was never cleaned up ends now. */
    private void leaveStaleTeam(UUID playerUUID, UUID newPartyId) {
        UUID previousPartyId = playerToParty.get(playerUUID);
        if (previousPartyId != null && !previousPartyId.equals(newPartyId)) {
            LOGGER.info("[TeamClaims] {} joined party {} while still listed in the team of party {}", playerUUID,
                    newPartyId, previousPartyId);
            onMemberLeft(previousPartyId, playerUUID);
        }
    }

    private void mapPlayer(UUID playerUUID, UUID partyId) {
        if (!partyId.equals(playerToParty.put(playerUUID, partyId))) invalidateTeamSubIndex(playerUUID);
    }

    private void unmapPlayer(UUID playerUUID, UUID partyId) {
        if (playerToParty.remove(playerUUID, partyId)) invalidateTeamSubIndex(playerUUID);
    }

    private static void invalidateTeamSubIndex(UUID playerUUID) {
        TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
        if (claimManager != null) claimManager.invalidateTeamSubIndex(playerUUID);
    }

    public void onTeamNameChanged(UUID partyId, String newName) {
        TeamConfig config = teamConfigs.get(partyId);
        if (config == null) return;
        if (config.updateTeamName(newName)) {
            config.setClaimsName(newName);
            markDirty(config);
            for (UUID memberUUID : config.getMembers()) configureTeamSubConfig(memberUUID, config);
        }
    }

    // ==================== Party events ====================

    public void queuePartyEvent(PartyEventType type, UUID partyId, @Nullable UUID playerId) {
        pendingEvents.add(new PartyEvent(type, partyId, playerId));
    }

    /**
     * Processes the queued party events in order, then applies the team settings to team sub-configs that were
     * created outside Team Claims. Called at the end of every server tick (and before shutdown).
     */
    public void processPendingEvents() {
        PartyEvent event;
        while ((event = pendingEvents.poll()) != null) {
            try {
                handlePartyEvent(event);
            } catch (Exception e) {
                LOGGER.warn("[TeamClaims] Failed to process {}; the periodic reconciliation will retry", event, e);
            }
        }
        if (!pendingConfigure.isEmpty()) {
            List<UUID> players = new ArrayList<>(pendingConfigure);
            pendingConfigure.clear();
            for (UUID playerUUID : players) configureTeamSubConfigForPlayer(playerUUID);
        }
    }

    private void handlePartyEvent(PartyEvent event) {
        IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
        UUID partyId = event.partyId();
        switch (event.type()) {
            case MEMBER_ADDED -> {
                IServerPartyAPI party = partyManager.getPartyById(partyId);
                // Gone again (or the member left again) by the end of the tick: nothing to set up
                if (party == null || party.getMemberInfo(event.playerId()) == null) return;
                if (teamConfigs.containsKey(partyId)) onMemberJoined(partyId, event.playerId());
                else createTeamConfig(party);
            }
            case MEMBER_REMOVED -> onMemberLeft(partyId, event.playerId());
            case OWNER_CHANGED, NAME_CHANGED -> {
                IServerPartyAPI party = partyManager.getPartyById(partyId);
                if (party != null) onTeamNameChanged(partyId, resolvePartyName(party));
            }
            case PARTY_REMOVED -> {
                if (partyManager.getPartyById(partyId) != null) return;
                removeTeamConfig(partyId);
                TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
                if (claimManager != null) claimManager.onPartyRemoved(partyId);
            }
        }
    }

    /**
     * Called at the end of every server tick, after {@link #processPendingEvents()}: the periodic reconciliation and
     * the hand-over of the configs that changed to the IO thread.
     */
    public void tick() {
        if (++reconcileTickCounter >= RECONCILE_INTERVAL) {
            reconcileTickCounter = 0;
            reconcile();
        }
        flushDirty();
    }

    /**
     * Brings every team config in line with the real parties: removes the configs of vanished parties, creates the
     * missing ones, processes joins/leaves nobody told us about, recreates missing team sub-configs and follows
     * party name changes. Runs at server start (after the claim manager has validated its saved data — that step
     * hands the team claims of members who left while the server was down over to the party owner, and it needs
     * their team sub-config to still exist) and then every {@link #RECONCILE_INTERVAL} ticks. Allocates nothing
     * while everything is in sync.
     */
    public void reconcile() {
        IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
        List<UUID> vanished = null;
        for (UUID partyId : teamConfigs.keySet()) {
            if (partyManager.getPartyById(partyId) == null) {
                if (vanished == null) vanished = new ArrayList<>();
                vanished.add(partyId);
            }
        }
        if (vanished != null) {
            TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
            for (UUID partyId : vanished) {
                LOGGER.info("[TeamClaims] Party {} no longer exists, removing its team config", partyId);
                removeTeamConfig(partyId);
                if (claimManager != null) claimManager.onPartyRemoved(partyId);
            }
        }
        partyManager.getAllStream().forEach(this::reconcileParty);
    }

    private void reconcileParty(IServerPartyAPI party) {
        UUID partyId = party.getId();
        TeamConfig config = teamConfigs.get(partyId);
        if (config == null) {
            createTeamConfig(party);
            return;
        }
        List<UUID> left = null;
        for (UUID storedMember : config.getMembers()) {
            if (party.getMemberInfo(storedMember) == null) {
                if (left == null) left = new ArrayList<>();
                left.add(storedMember);
            }
        }
        if (left != null) {
            for (UUID member : left) {
                LOGGER.info("[TeamClaims] Party member {} of party {} left without the change being processed", member, partyId);
                onMemberLeft(partyId, member);
            }
        }
        String subConfigId = config.getSubConfigId();
        party.getMemberInfoStream().forEach(memberInfo -> {
            UUID member = memberInfo.getUUID();
            if (!config.isMember(member)) onMemberJoined(partyId, member);
            else if (getSubConfigForMember(member, subConfigId) == null) ensureSubConfigForPlayer(member, config);
        });
        String name = resolvePartyName(party);
        if (!name.equals(config.getTeamName())) onTeamNameChanged(partyId, name);
    }

    // ==================== Team sub-configs ====================

    /** Creates the player's team sub-config if they are a team member without one, and applies the team settings. */
    public void ensureTeamSubConfigForPlayer(UUID playerUUID) {
        TeamConfig config = getTeamConfigForPlayer(playerUUID);
        if (config != null) ensureSubConfigForPlayer(playerUUID, config);
    }

    private void ensureSubConfigForPlayer(UUID playerUUID, TeamConfig teamConfig) {
        String subConfigId = teamConfig.getSubConfigId();
        IPlayerConfigAPI playerConfig = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(playerUUID);
        if (!playerConfig.subConfigExists(subConfigId)) {
            boolean previousInternal = INTERNAL_EDIT.get();
            INTERNAL_EDIT.set(true);//configured right below, not by the creation hook
            try {
                if (playerConfig.createSubConfig(subConfigId) == null) {
                    LOGGER.warn("[TeamClaims] Failed to create the team sub-config '{}' for player {}", subConfigId, playerUUID);
                    return;
                }
                LOGGER.info("Created '{}' sub-config for player {}", subConfigId, playerUUID);
            } finally {
                INTERNAL_EDIT.set(previousInternal);
            }
        }
        configureTeamSubConfig(playerUUID, teamConfig);
    }

    private void removeSubConfigForPlayer(UUID playerUUID, String subConfigId) {
        IPlayerConfigAPI playerConfig = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(playerUUID);
        if (!playerConfig.subConfigExists(subConfigId)) return;
        if (playerConfig instanceof IPlayerConfig internalConfig) {
            internalConfig.removeSubConfig(subConfigId);
        } else {
            LOGGER.warn("[TeamClaims] Can't remove the team sub-config '{}' of {}: unexpected config type {}",
                    subConfigId, playerUUID, playerConfig.getClass().getName());
        }
    }

    /**
     * Applies the team's settings to a member's team sub-config: the canonical claims color and name, every
     * persisted admin-set option, and full access for the whole party. Only values that differ are set.
     */
    private void configureTeamSubConfig(UUID playerUUID, TeamConfig teamConfig) {
        IPlayerConfigAPI subConfig = getSubConfigForMember(playerUUID, teamConfig.getSubConfigId());
        if (subConfig == null) return;
        boolean previousInternal = INTERNAL_EDIT.get();
        INTERNAL_EDIT.set(true);
        try {
            setIfDifferent(subConfig, PlayerConfigOptions.CLAIMS_COLOR, getCanonicalColor(teamConfig));
            String teamName = getCanonicalName(teamConfig);
            if (!teamName.isEmpty()) setIfDifferent(subConfig, PlayerConfigOptions.CLAIMS_NAME, teamName);
            for (Map.Entry<String, Object> entry : teamConfig.getOptions().entrySet()) {
                IPlayerConfigOptionSpecAPI<?> option = PlayerConfigOptions.OPTIONS.get(entry.getKey());
                if (option != null) setIfDifferent(subConfig, option, entry.getValue());
            }
            // Team claims always grant the whole party full access (the v0.31.6 equivalent of
            // v0.25.8's "protect claimed chunks from party = false").
            if (!fullAccessIncludesParty(subConfig.getEffective(PlayerConfigOptions.FULL_ACCESS)))
                subConfig.tryToSet(PlayerConfigOptions.FULL_ACCESS, PlayerConfigConstants.PARTY_EXCEPTION_ID);
        } catch (Exception e) {
            LOGGER.warn("[TeamClaims] Error applying the team settings to the team sub-config of {}", playerUUID, e);
        } finally {
            INTERNAL_EDIT.set(previousInternal);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setIfDifferent(IPlayerConfigAPI subConfig, IPlayerConfigOptionSpecAPI option, @Nullable Object value) {
        if (Objects.equals(subConfig.getRaw(option), value)) return;
        IPlayerConfigAPI.SetResult result = subConfig.tryToSet(option, value);
        if (result != IPlayerConfigAPI.SetResult.SUCCESS)
            // Expected when the server config no longer lets players set the option; the team keeps the value
            LOGGER.debug("[TeamClaims] Could not apply team option {} = {} ({})", option.getId(), value, result);
    }

    public void configureTeamSubConfigForPlayer(UUID playerUUID) {
        TeamConfig config = getTeamConfigForPlayer(playerUUID);
        if (config != null) configureTeamSubConfig(playerUUID, config);
    }

    /**
     * A team sub-config was created or removed somewhere. Invalidates the cached sub-config index, and a team
     * sub-config created by anything other than Team Claims itself gets the team settings at the end of the tick.
     */
    public void onTeamSubConfigExistenceChanged(UUID playerUUID, String subId, boolean exists) {
        invalidateTeamSubIndex(playerUUID);
        if (exists && !INTERNAL_EDIT.get()) {
            TeamConfig config = getTeamConfigForPlayer(playerUUID);
            if (config != null && config.getSubConfigId().equals(subId)) pendingConfigure.add(playerUUID);
        }
    }

    /**
     * Persists an owner/admin edit of a team sub-config and propagates it to every other member's team sub-config.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onTeamSubConfigSettingChanged(UUID changedByPlayer, String subId,
            IPlayerConfigOptionSpecAPI<?> option, @Nullable Object value) {
        TeamConfig config = getTeamConfigForPlayer(changedByPlayer);
        // Only the player's current team sub-config is shared (not some other team_* sub-config they may have)
        if (config == null || !config.getSubConfigId().equals(subId)) return;
        boolean changed;
        if (option == PlayerConfigOptions.CLAIMS_COLOR)
            changed = config.setClaimsColor(value instanceof Integer color ? color : null);
        else if (option == PlayerConfigOptions.CLAIMS_NAME)
            changed = config.setClaimsName(value instanceof String name ? name : null);
        else
            changed = config.setOption(option, value);
        if (changed) markDirty(config);
        boolean previousInternal = INTERNAL_EDIT.get();
        INTERNAL_EDIT.set(true);
        try {
            for (UUID memberUUID : config.getMembers()) {
                if (memberUUID.equals(changedByPlayer)) continue;
                IPlayerConfigAPI subConfig = getSubConfigForMember(memberUUID, subId);
                if (subConfig == null) continue;
                try {
                    setIfDifferent(subConfig, (IPlayerConfigOptionSpecAPI) option, value);
                } catch (Exception e) {
                    LOGGER.warn("[TeamClaims] Failed to propagate {} to the team sub-config of {}", option.getId(), memberUUID, e);
                }
            }
        } finally {
            INTERNAL_EDIT.set(previousInternal);
        }
    }

    private int getCanonicalColor(TeamConfig config) {
        Integer color = config.getClaimsColor();
        return color != null ? color : TeamConfig.computeTeamColor(config.getPartyId());
    }

    private String getCanonicalName(TeamConfig config) {
        String name = config.getClaimsName();
        return name != null ? name : config.getTeamName();
    }

    @Nullable
    private IPlayerConfigAPI getSubConfigForMember(UUID memberUUID, String subConfigId) {
        return OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(memberUUID).getSubConfig(subConfigId);
    }

    /**
     * v0.25.8's boolean "protect claimed chunks from party" option became the string-based
     * FULL_ACCESS exception group in v0.31.6. "Not protected from the party" is exactly
     * "the chosen full-access group includes party members", i.e. one of the built-in P/A/E
     * groups — the same mapping the deprecated v1 compat option performs internally, so this
     * uses the real v2 option directly instead of the deprecated wrapper.
     * <p>
     * Mirrored by the lock in {@code PlayerConfig.tryToSet}, which rejects any other value on a
     * team sub-config, so this only ever has to repair pre-existing values.
     */
    private static boolean fullAccessIncludesParty(@Nullable String fullAccessGroup) {
        return PlayerConfigConstants.PARTY_EXCEPTION_ID.equals(fullAccessGroup)
                || PlayerConfigConstants.ALLIES_EXCEPTION_ID.equals(fullAccessGroup)
                || PlayerConfigConstants.EVERYONE_EXCEPTION_ID.equals(fullAccessGroup);
    }

    // ==================== Logins / lookups ====================

    /**
     * Login handling: makes sure the player's team membership and team sub-config match their party, and applies
     * the team settings again (repairing anything edited in the player config files while the server was down).
     * Runs one tick after login, see {@code TeamClaimsCommon.onPlayerLoggedIn}.
     */
    public void onPlayerLogin(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(playerUUID);
        UUID mappedPartyId = playerToParty.get(playerUUID);
        if (party == null) {
            if (mappedPartyId != null) onMemberLeft(mappedPartyId, playerUUID);
            return;
        }
        TeamConfig config = teamConfigs.get(party.getId());
        if (config == null) {
            LOGGER.info("Player {} logged in with party but no TeamConfig — creating retroactively", playerUUID);
            createTeamConfig(party);
        } else if (!config.isMember(playerUUID) || !party.getId().equals(mappedPartyId)) {
            onMemberJoined(party.getId(), playerUUID);
        } else {
            ensureSubConfigForPlayer(playerUUID, config);
        }
    }

    public String resolvePartyName(IServerPartyAPI party) {
        IPartyMemberAPI owner = party.getOwner();
        if (owner != null) {
            String customName = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(owner.getUUID())
                    .getEffective(PlayerConfigOptions.PARTY_NAME);
            if (customName != null && !customName.isBlank()) return customName;
        }
        return party.getDefaultName();
    }

    @Nullable public TeamConfig getTeamConfig(UUID partyId) { return teamConfigs.get(partyId); }

    @Nullable
    public TeamConfig getTeamConfigForPlayer(UUID playerUUID) {
        UUID partyId = playerToParty.get(playerUUID);
        return partyId == null ? null : teamConfigs.get(partyId);
    }


    public boolean isPlayerTeamAdmin(UUID playerUUID) {
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(playerUUID);
        if (party == null) return false;
        IPartyMemberAPI member = party.getMemberInfo(playerUUID);
        if (member == null) return false;
        return member.isOwner() || member.getRank() == PartyMemberRank.ADMIN;
    }

    /**
     * Waits for the queued file operations, then writes the configs that are still dirty synchronously.
     */
    public void shutdown() {
        for (CompletableFuture<Void> pending : new ArrayList<>(pendingFileOps.values())) {
            try {
                pending.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.warn("[TeamClaims] A team config file operation didn't finish before shutdown", e);
            }
        }
        for (UUID partyId : dirtyConfigs) {
            TeamConfig config = teamConfigs.get(partyId);
            if (config != null) writeAtomically(fileFor(partyId), config.toJsonString());
        }
        dirtyConfigs.clear();
        pendingEvents.clear();
        pendingConfigure.clear();
        teamConfigs.clear();
        playerToParty.clear();
    }
}
