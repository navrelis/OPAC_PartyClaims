package xaero.pac.teamclaims.config;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.common.player.config.PlayerConfigConstants;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommon;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamConfigManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final ThreadLocal<Boolean> INTERNAL_EDIT = ThreadLocal.withInitial(() -> false);
    private static final int POLL_INTERVAL = 60;
    private static final int SETTINGS_SYNC_INTERVAL = 20;

    private final MinecraftServer server;
    private final Path configDir;
    private int pollTickCounter = 0;
    private int settingsSyncCounter = 0;
    private final Map<UUID, TeamConfig> teamConfigs = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToParty = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> lastKnownMembers = new HashMap<>();
    private final Map<UUID, String> lastKnownNames = new HashMap<>();

    public TeamConfigManager(MinecraftServer server) {
        this.server = server;
        // Parties are per-world, so the team config JSONs must live in the world save folder
        // instead of the server's global config directory (otherwise a second singleplayer
        // world would purge the first world's team configs via purgeOrphanedTeamConfigs).
        this.configDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("opacteamclaims").resolve("teams");
    }

    public void loadAll() {
        teamConfigs.clear();
        playerToParty.clear();
        lastKnownMembers.clear();
        lastKnownNames.clear();
        try {
            if (!Files.exists(configDir)) { Files.createDirectories(configDir); return; }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "*.json")) {
                for (Path file : stream) {
                    try {
                        String content = Files.readString(file);
                        JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                        TeamConfig config = TeamConfig.fromJson(json);
                        teamConfigs.put(config.getPartyId(), config);
                    } catch (Exception e) {
                        LOGGER.error("Failed to load team config from {}", file.getFileName(), e);
                    }
                }
            }
            LOGGER.info("Loaded {} team configs from disk", teamConfigs.size());
        } catch (IOException e) {
            LOGGER.error("Failed to initialize team config directory", e);
        }
        purgeOrphanedTeamConfigs();
        for (TeamConfig config : teamConfigs.values()) {
            for (UUID member : config.getMembers()) playerToParty.put(member, config.getPartyId());
        }
        discoverExistingParties();
        takePartySnapshot();
    }

    private void purgeOrphanedTeamConfigs() {
        try {
            IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
            List<UUID> toRemove = new ArrayList<>();
            for (UUID partyId : teamConfigs.keySet()) {
                if (partyManager.getPartyById(partyId) == null) toRemove.add(partyId);
            }
            for (UUID partyId : toRemove) {
                TeamConfig config = teamConfigs.remove(partyId);
                if (config != null) {
                    String staleSubConfigId = config.getSubConfigId();
                    for (UUID memberUUID : config.getMembers()) removeSubConfigForPlayer(memberUUID, staleSubConfigId);
                    deleteConfigFile(partyId);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error purging orphaned team configs", e);
        }
    }

    private void discoverExistingParties() {
        try {
            IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
            List<IServerPartyAPI> partiesToCreate = new ArrayList<>();
            partyManager.getAllStream().forEach(party -> {
                if (!teamConfigs.containsKey(party.getId())) partiesToCreate.add(party);
            });
            for (IServerPartyAPI party : partiesToCreate) {
                createTeamConfig(party);
            }
        } catch (Exception e) {
            LOGGER.error("Error discovering existing parties", e);
        }
    }

    public void save(TeamConfig config) {
        try {
            if (!Files.exists(configDir)) Files.createDirectories(configDir);
            Path file = configDir.resolve(config.getPartyId().toString() + ".json");
            Path tmpFile = configDir.resolve(config.getPartyId().toString() + ".json.tmp");
            Files.writeString(tmpFile, config.toJsonString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save team config for {}", config.getPartyId(), e);
        }
    }

    private void deleteConfigFile(UUID partyId) {
        try {
            Path file = configDir.resolve(partyId.toString() + ".json");
            Files.deleteIfExists(file);
            Files.deleteIfExists(configDir.resolve(partyId.toString() + ".json.tmp"));
        } catch (IOException e) {
            LOGGER.error("Failed to delete team config for {}", partyId, e);
        }
    }

    public TeamConfig createTeamConfig(IServerPartyAPI party) {
        UUID partyId = party.getId();
        TeamConfig existing = teamConfigs.get(partyId);
        if (existing != null) return existing;
        String teamName = resolvePartyName(party);
        TeamConfig config = new TeamConfig(partyId, teamName);
        config.setSetting("opac.CLAIMS_COLOR", new JsonPrimitive(TeamConfig.computeTeamColor(partyId)));
        config.setSetting("opac.CLAIMS_NAME", new JsonPrimitive(teamName));
        party.getMemberInfoStream().forEach(member -> {
            config.addMember(member.getUUID());
            playerToParty.put(member.getUUID(), partyId);
        });
        teamConfigs.put(partyId, config);
        save(config);
        ensureSubConfigsForTeam(config);
        LOGGER.info("Created team config '{}' for party {} with {} members", teamName, partyId, config.getMembers().size());
        return config;
    }

    public void removeTeamConfig(UUID partyId) {
        TeamConfig config = teamConfigs.remove(partyId);
        if (config == null) return;
        String subConfigId = config.getSubConfigId();
        for (UUID memberUUID : config.getMembers()) {
            playerToParty.remove(memberUUID);
            removeSubConfigForPlayer(memberUUID, subConfigId);
        }
        deleteConfigFile(partyId);
        lastKnownMembers.remove(partyId);
        lastKnownNames.remove(partyId);
    }

    public void onMemberJoined(UUID partyId, UUID playerUUID) {
        TeamConfig config = teamConfigs.get(partyId);
        if (config == null) return;
        if (config.addMember(playerUUID)) {
            playerToParty.put(playerUUID, partyId);
            save(config);
            ensureSubConfigForPlayer(playerUUID, config.getSubConfigId(), config);
        }
    }

    public void onMemberLeft(UUID partyId, UUID playerUUID) {
        TeamConfig config = teamConfigs.get(partyId);
        if (config == null) return;
        if (config.removeMember(playerUUID)) {
            playerToParty.remove(playerUUID);
            save(config);
            TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
            if (claimManager != null) claimManager.onPlayerLeftParty(partyId, playerUUID);
            removeSubConfigForPlayer(playerUUID, config.getSubConfigId());
        }
    }

    /**
     * Reconciles every stored team config with the actual party membership once, at server start.
     * <p>
     * {@link #loadAll()} only takes a snapshot of the current parties, so a membership change that
     * happened while the server was down is never seen by {@link #pollForChanges()}. Running the
     * normal join/leave handling here closes that gap: it removes the stale team sub-configs of
     * members who left and creates the missing ones for members who joined.
     * <p>
     * Called from {@code TeamClaimManager.onServerStarted()} <b>after</b> the claim manager has
     * validated its saved data — that step is the one that hands the team claims of a member who
     * left while the server was down over to the party owner, and it needs their team sub-config
     * to still exist. By the time this runs, those claims are no longer tracked for them, so the
     * {@code onMemberLeft} call below has nothing left to transfer.
     */
    public void reconcileMembershipWithParties() {
        try {
            IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
            for (TeamConfig config : new ArrayList<>(teamConfigs.values())) {
                UUID partyId = config.getPartyId();
                IServerPartyAPI party = partyManager.getPartyById(partyId);
                if (party == null) continue;//already handled by purgeOrphanedTeamConfigs
                Set<UUID> currentMembers = new LinkedHashSet<>();
                party.getMemberInfoStream().forEach(m -> currentMembers.add(m.getUUID()));
                for (UUID storedMember : config.getMembers()) {//a copy, safe to modify the config
                    if (currentMembers.contains(storedMember)) continue;
                    LOGGER.info("Party member {} of party {} left while the server was down", storedMember, partyId);
                    onMemberLeft(partyId, storedMember);
                }
                for (UUID currentMember : currentMembers) {
                    if (!config.isMember(currentMember)) onMemberJoined(partyId, currentMember);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error reconciling team configs with the current party membership", e);
        }
    }

    public void onTeamNameChanged(UUID partyId, String newName) {
        TeamConfig config = teamConfigs.get(partyId);
        if (config == null) return;
        if (config.updateTeamName(newName)) {
            config.setSetting("opac.CLAIMS_NAME", new JsonPrimitive(newName));
            save(config);
            for (UUID memberUUID : config.getMembers()) configureTeamSubConfig(memberUUID, config);
        }
    }

    private void ensureSubConfigsForTeam(TeamConfig config) {
        for (UUID memberUUID : config.getMembers()) ensureSubConfigForPlayer(memberUUID, config.getSubConfigId(), config);
    }

    private void ensureSubConfigForPlayer(UUID playerUUID, String subConfigId, @Nullable TeamConfig teamConfig) {
        try {
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigManager();
            IPlayerConfigAPI playerConfig = configManager.getLoadedConfig(playerUUID);
            if (!playerConfig.subConfigExists(subConfigId)) {
                IPlayerConfigAPI newSub = playerConfig.createSubConfig(subConfigId);
                if (newSub == null) return;
            }
            if (teamConfig != null) configureTeamSubConfig(playerUUID, teamConfig);
        } catch (Exception e) {
            LOGGER.error("Error creating sub-config '{}' for player {}", subConfigId, playerUUID, e);
        }
    }

    private void ensureSubConfigForPlayer(UUID playerUUID, String subConfigId) {
        UUID partyId = playerToParty.get(playerUUID);
        TeamConfig config = partyId != null ? teamConfigs.get(partyId) : null;
        ensureSubConfigForPlayer(playerUUID, subConfigId, config);
    }

    private void removeSubConfigForPlayer(UUID playerUUID, String subConfigId) {
        try {
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigManager();
            IPlayerConfigAPI playerConfig = configManager.getLoadedConfig(playerUUID);
            if (playerConfig.subConfigExists(subConfigId)) {
                if (playerConfig instanceof xaero.pac.common.server.player.config.IPlayerConfig internalConfig) {
                    internalConfig.removeSubConfig(subConfigId);
                }
            }
            TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
            if (claimManager != null) claimManager.invalidateCacheForPlayer(playerUUID);
        } catch (Exception e) {
            LOGGER.error("Error removing sub-config '{}' from player {}", subConfigId, playerUUID, e);
        }
    }

    private void configureTeamSubConfig(UUID playerUUID, TeamConfig teamConfig) {
        try {
            IPlayerConfigAPI subConfig = getSubConfigForMember(playerUUID, teamConfig.getSubConfigId());
            if (subConfig == null) return;
            int teamColor = getCanonicalColor(teamConfig);
            String teamName = getCanonicalName(teamConfig);
            INTERNAL_EDIT.set(true);
            try {
                subConfig.tryToSet(PlayerConfigOptions.CLAIMS_COLOR, teamColor);
                if (teamName != null && !teamName.isEmpty()) subConfig.tryToSet(PlayerConfigOptions.CLAIMS_NAME, teamName);
                // Team claims always grant the whole party full access (the v0.31.6 equivalent of
                // v0.25.8's "protect claimed chunks from party = false").
                setProtectFromPartyDisabled(playerUUID, teamConfig.getSubConfigId());
            } finally {
                INTERNAL_EDIT.remove();
            }
        } catch (Exception e) {
            LOGGER.error("Error configuring team sub-config for player {}", playerUUID, e);
        }
    }

    public void configureTeamSubConfigForPlayer(UUID playerUUID) {
        UUID partyId = playerToParty.get(playerUUID);
        TeamConfig config = partyId != null ? teamConfigs.get(partyId) : null;
        if (config != null) configureTeamSubConfig(playerUUID, config);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onTeamSubConfigSettingChanged(UUID changedByPlayer, IPlayerConfigOptionSpecAPI<?> option, Object value) {
        UUID partyId = playerToParty.get(changedByPlayer);
        TeamConfig config = partyId != null ? teamConfigs.get(partyId) : null;
        if (config == null) return;
        boolean settingChanged = false;
        if (option == PlayerConfigOptions.CLAIMS_COLOR && value instanceof Integer intVal) {
            config.setSetting("opac.CLAIMS_COLOR", new JsonPrimitive(intVal));
            settingChanged = true;
        } else if (option == PlayerConfigOptions.CLAIMS_NAME && value instanceof String strVal) {
            config.setSetting("opac.CLAIMS_NAME", new JsonPrimitive(strVal));
            settingChanged = true;
        }
        if (settingChanged) save(config);
        INTERNAL_EDIT.set(true);
        try {
            for (UUID memberUUID : config.getMembers()) {
                if (memberUUID.equals(changedByPlayer)) continue;
                IPlayerConfigAPI subConfig = getSubConfigForMember(memberUUID, config.getSubConfigId());
                if (subConfig == null) continue;
                try { subConfig.tryToSet((IPlayerConfigOptionSpecAPI) option, value); } catch (Exception e) { LOGGER.debug("Failed to propagate setting to {}", memberUUID, e); }
            }
        } finally {
            INTERNAL_EDIT.remove();
        }
    }

    private void detectAndPropagateSettingChanges() {
        for (TeamConfig config : teamConfigs.values()) {
            int canonColor = getCanonicalColor(config);
            String canonName = getCanonicalName(config);
            boolean colorChanged = false;
            boolean nameChanged = false;
            int newColor = canonColor;
            String newName = canonName;
            for (UUID memberUUID : config.getMembers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberUUID);
                if (player == null) continue;
                IPlayerConfigAPI subConfig = getSubConfigForMember(memberUUID, config.getSubConfigId());
                if (subConfig == null) continue;
                try {
                    if (!colorChanged) {
                        int memberColor = subConfig.getEffective(PlayerConfigOptions.CLAIMS_COLOR);
                        if (memberColor != canonColor) { newColor = memberColor; colorChanged = true; }
                    }
                    if (!nameChanged) {
                        String memberName = subConfig.getEffective(PlayerConfigOptions.CLAIMS_NAME);
                        if (memberName != null && !memberName.equals(canonName)) { newName = memberName; nameChanged = true; }
                    }
                } catch (Exception ignored) {}
                try {
                    if (isProtectedFromParty(memberUUID, config.getSubConfigId())) {
                        INTERNAL_EDIT.set(true);
                        try { setProtectFromPartyDisabled(memberUUID, config.getSubConfigId()); } finally { INTERNAL_EDIT.remove(); }
                    }
                } catch (Exception ignored) {}
            }
            if (colorChanged || nameChanged) {
                if (colorChanged) config.setSetting("opac.CLAIMS_COLOR", new JsonPrimitive(newColor));
                if (nameChanged) config.setSetting("opac.CLAIMS_NAME", new JsonPrimitive(newName));
                save(config);
                final int finalColor = newColor;
                final String finalName = newName;
                for (UUID memberUUID : config.getMembers()) {
                    IPlayerConfigAPI subConfig = getSubConfigForMember(memberUUID, config.getSubConfigId());
                    if (subConfig == null) continue;
                    INTERNAL_EDIT.set(true);
                    try {
                        if (colorChanged) { int cc = subConfig.getEffective(PlayerConfigOptions.CLAIMS_COLOR); if (cc != finalColor) subConfig.tryToSet(PlayerConfigOptions.CLAIMS_COLOR, finalColor); }
                        if (nameChanged) { String cn = subConfig.getEffective(PlayerConfigOptions.CLAIMS_NAME); if (!Objects.equals(cn, finalName)) subConfig.tryToSet(PlayerConfigOptions.CLAIMS_NAME, finalName); }
                    } catch (Exception ignored) {} finally { INTERNAL_EDIT.remove(); }
                }
            }
        }
    }

    private int getCanonicalColor(TeamConfig config) {
        JsonElement el = config.getSetting("opac.CLAIMS_COLOR");
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) return el.getAsInt();
        return TeamConfig.computeTeamColor(config.getPartyId());
    }

    private String getCanonicalName(TeamConfig config) {
        JsonElement el = config.getSetting("opac.CLAIMS_NAME");
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) return el.getAsString();
        return config.getTeamName();
    }

    @Nullable
    private IPlayerConfigAPI getSubConfigForMember(UUID memberUUID, String subConfigId) {
        try {
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigManager();
            IPlayerConfigAPI playerConfig = configManager.getLoadedConfig(memberUUID);
            return playerConfig.getSubConfig(subConfigId);
        } catch (Exception e) { return null; }
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

    private void setProtectFromPartyDisabled(UUID playerUUID, String subConfigId) {
        try {
            IPlayerConfigAPI subConfig = getSubConfigForMember(playerUUID, subConfigId);
            if (subConfig == null) return;
            if (fullAccessIncludesParty(subConfig.getEffective(PlayerConfigOptions.FULL_ACCESS))) return;
            subConfig.tryToSet(PlayerConfigOptions.FULL_ACCESS, PlayerConfigConstants.PARTY_EXCEPTION_ID);
        } catch (Exception e) {
            LOGGER.debug("Failed to grant the party full access to team claims of {}: {}", playerUUID, e.getMessage());
        }
    }

    private boolean isProtectedFromParty(UUID playerUUID, String subConfigId) {
        try {
            IPlayerConfigAPI subConfig = getSubConfigForMember(playerUUID, subConfigId);
            if (subConfig == null) return false;
            return !fullAccessIncludesParty(subConfig.getEffective(PlayerConfigOptions.FULL_ACCESS));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Login handling (formerly {@code syncToPlayer}, minus the dead S2C config payload): repairs
     * the player-to-party lookup for this player and creates their party's team config if it is
     * still missing. Runs one tick after login, see {@code TeamClaimsCommon.onPlayerLoggedIn}.
     */
    public void onPlayerLogin(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        UUID partyId = playerToParty.get(playerUUID);
        TeamConfig config = partyId != null ? teamConfigs.get(partyId) : null;
        if (config == null || !config.isMember(playerUUID)) {
            for (TeamConfig c : teamConfigs.values()) {
                if (c.isMember(playerUUID)) { config = c; playerToParty.put(playerUUID, c.getPartyId()); break; }
            }
        }
        if (config == null) {
            try {
                IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
                IServerPartyAPI party = partyManager.getPartyByMember(playerUUID);
                if (party != null) createTeamConfig(party);
            } catch (Exception ignored) {}
        }
    }

    private void takePartySnapshot() {
        try {
            IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
            lastKnownMembers.clear();
            lastKnownNames.clear();
            partyManager.getAllStream().forEach(party -> {
                Set<UUID> members = new HashSet<>();
                party.getMemberInfoStream().forEach(m -> members.add(m.getUUID()));
                lastKnownMembers.put(party.getId(), members);
                lastKnownNames.put(party.getId(), resolvePartyName(party));
            });
        } catch (Exception ignored) {}
    }

    public void pollForChanges() {
        if (++settingsSyncCounter >= SETTINGS_SYNC_INTERVAL) {
            settingsSyncCounter = 0;
            detectAndPropagateSettingChanges();
        }
        if (++pollTickCounter < POLL_INTERVAL) return;
        pollTickCounter = 0;

        Map<UUID, Set<UUID>> currentMembers = new HashMap<>();
        Map<UUID, String> currentNames = new HashMap<>();
        try {
            IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
            partyManager.getAllStream().forEach(party -> {
                Set<UUID> members = new HashSet<>();
                party.getMemberInfoStream().forEach(m -> members.add(m.getUUID()));
                currentMembers.put(party.getId(), members);
                currentNames.put(party.getId(), resolvePartyName(party));
            });
            for (UUID partyId2 : currentMembers.keySet()) {
                if (!lastKnownMembers.containsKey(partyId2) || !teamConfigs.containsKey(partyId2)) {
                    IServerPartyAPI party = partyManager.getPartyById(partyId2);
                    if (party != null && !teamConfigs.containsKey(partyId2)) createTeamConfig(party);
                }
            }
            for (UUID partyId2 : new ArrayList<>(lastKnownMembers.keySet())) {
                if (!currentMembers.containsKey(partyId2)) removeTeamConfig(partyId2);
            }
            Set<TeamConfig> dirtyConfigs = new LinkedHashSet<>();
            for (UUID partyId2 : currentMembers.keySet()) {
                TeamConfig config = teamConfigs.get(partyId2);
                if (config == null) continue;
                Set<UUID> prevMembers = lastKnownMembers.getOrDefault(partyId2, Set.of());
                Set<UUID> currMembers = currentMembers.get(partyId2);
                boolean membershipChanged = false;
                for (UUID memberUUID : currMembers) {
                    if (!prevMembers.contains(memberUUID)) {
                        if (config.addMember(memberUUID)) {
                            playerToParty.put(memberUUID, partyId2);
                            ensureSubConfigForPlayer(memberUUID, config.getSubConfigId(), config);
                            membershipChanged = true;
                        }
                    }
                }
                for (UUID memberUUID : prevMembers) {
                    if (!currMembers.contains(memberUUID)) {
                        if (config.removeMember(memberUUID)) {
                            playerToParty.remove(memberUUID);
                            TeamClaimManager claimMgr = TeamClaimsCommon.getClaimManager();
                            if (claimMgr != null) claimMgr.onPlayerLeftParty(partyId2, memberUUID);
                            removeSubConfigForPlayer(memberUUID, config.getSubConfigId());
                            membershipChanged = true;
                        }
                    }
                }
                String prevName = lastKnownNames.get(partyId2);
                String currName = currentNames.get(partyId2);
                if (currName != null && !currName.equals(prevName)) onTeamNameChanged(partyId2, currName);
                if (membershipChanged) dirtyConfigs.add(config);
            }
            for (TeamConfig dirtyConfig : dirtyConfigs) save(dirtyConfig);
        } catch (Exception e) {
            LOGGER.error("Error polling for party changes", e);
        }
        // Always update snapshot with what we successfully read, even on error
        lastKnownMembers.clear();
        lastKnownMembers.putAll(currentMembers);
        lastKnownNames.clear();
        lastKnownNames.putAll(currentNames);
    }

    public String resolvePartyName(IServerPartyAPI party) {
        try {
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigManager();
            IPlayerConfigAPI ownerConfig = configManager.getLoadedConfig(party.getOwner().getUUID());
            String customName = ownerConfig.getEffective(PlayerConfigOptions.PARTY_NAME);
            if (customName != null && !customName.isBlank()) return customName;
        } catch (Exception ignored) {}
        return party.getDefaultName();
    }

    @Nullable public TeamConfig getTeamConfig(UUID partyId) { return teamConfigs.get(partyId); }

    @Nullable
    public TeamConfig getTeamConfigForPlayer(UUID playerUUID) {
        UUID partyId = playerToParty.get(playerUUID);
        if (partyId != null) {
            TeamConfig config = teamConfigs.get(partyId);
            if (config != null && config.isMember(playerUUID)) return config;
            playerToParty.remove(playerUUID);
        }
        return null;
    }

    public Collection<TeamConfig> getAllTeamConfigs() { return Collections.unmodifiableCollection(teamConfigs.values()); }

    public boolean isPlayerTeamAdmin(UUID playerUUID) {
        try {
            IPartyManagerAPI partyManager = OpenPACServerAPI.get(server).getPartyManager();
            IServerPartyAPI party = partyManager.getPartyByMember(playerUUID);
            if (party == null) return false;
            IPartyMemberAPI member = party.getMemberInfo(playerUUID);
            if (member == null) return false;
            return member.isOwner() || member.getRank() == PartyMemberRank.ADMIN;
        } catch (Exception e) { return false; }
    }

    public void shutdown() {
        for (TeamConfig config : teamConfigs.values()) save(config);
        teamConfigs.clear();
        playerToParty.clear();
        lastKnownMembers.clear();
        lastKnownNames.clear();
    }
}
