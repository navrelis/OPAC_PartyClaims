package xaero.pac.teamclaims.config;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.PlayerConfigOptions;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsInit;
import xaero.pac.teamclaims.network.TeamConfigSyncPayload;
import xaero.pac.teamclaims.network.TeamConfigRemovedPayload;

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
        this.configDir = server.getServerDirectory().resolve("config").resolve("opacteamclaims").resolve("teams");
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
        syncToOnlineMembers(config);
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
            ServerPlayer player = server.getPlayerList().getPlayer(memberUUID);
            if (player != null) {
                try { PacketDistributor.sendToPlayer(player, new TeamConfigRemovedPayload(partyId)); } catch (Exception e) { LOGGER.debug("Failed to send config removal to {}", memberUUID, e); }
            }
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
            syncToOnlineMembers(config);
        }
    }

    public void onMemberLeft(UUID partyId, UUID playerUUID) {
        TeamConfig config = teamConfigs.get(partyId);
        if (config == null) return;
        if (config.removeMember(playerUUID)) {
            playerToParty.remove(playerUUID);
            save(config);
            TeamClaimManager claimManager = TeamClaimsInit.getClaimManager();
            if (claimManager != null) claimManager.onPlayerLeftParty(partyId, playerUUID);
            removeSubConfigForPlayer(playerUUID, config.getSubConfigId());
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                try { PacketDistributor.sendToPlayer(player, new TeamConfigRemovedPayload(partyId)); } catch (Exception ignored) {}
            }
            syncToOnlineMembers(config);
        }
    }

    public void onTeamNameChanged(UUID partyId, String newName) {
        TeamConfig config = teamConfigs.get(partyId);
        if (config == null) return;
        if (config.updateTeamName(newName)) {
            config.setSetting("opac.CLAIMS_NAME", new JsonPrimitive(newName));
            save(config);
            for (UUID memberUUID : config.getMembers()) configureTeamSubConfig(memberUUID, config);
            syncToOnlineMembers(config);
        }
    }

    private void ensureSubConfigsForTeam(TeamConfig config) {
        for (UUID memberUUID : config.getMembers()) ensureSubConfigForPlayer(memberUUID, config.getSubConfigId(), config);
    }

    private void ensureSubConfigForPlayer(UUID playerUUID, String subConfigId, @Nullable TeamConfig teamConfig) {
        try {
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigs();
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
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigs();
            IPlayerConfigAPI playerConfig = configManager.getLoadedConfig(playerUUID);
            if (playerConfig.subConfigExists(subConfigId)) {
                if (playerConfig instanceof xaero.pac.common.server.player.config.IPlayerConfig internalConfig) {
                    internalConfig.removeSubConfig(subConfigId);
                }
            }
            TeamClaimManager claimManager = TeamClaimsInit.getClaimManager();
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
                subConfig.tryToSet(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS_FROM_PARTY, false);
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
    public void onTeamSubConfigSettingChanged(UUID changedByPlayer, IPlayerConfigOptionSpecAPI option, Comparable value) {
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
                try { subConfig.tryToSet(option, value); } catch (Exception e) { LOGGER.debug("Failed to propagate setting to {}", memberUUID, e); }
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
                    Boolean protectFromParty = subConfig.getEffective(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS_FROM_PARTY);
                    if (protectFromParty != null && protectFromParty) {
                        INTERNAL_EDIT.set(true);
                        try { subConfig.tryToSet(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS_FROM_PARTY, false); } finally { INTERNAL_EDIT.remove(); }
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
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigs();
            IPlayerConfigAPI playerConfig = configManager.getLoadedConfig(memberUUID);
            return playerConfig.getSubConfig(subConfigId);
        } catch (Exception e) { return null; }
    }

    public void syncToOnlineMembers(TeamConfig config) {
        TeamConfigSyncPayload payload;
        try { payload = new TeamConfigSyncPayload(config); } catch (Exception e) { return; }
        for (UUID memberUUID : config.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberUUID);
            if (player != null) {
                try { PacketDistributor.sendToPlayer(player, payload); } catch (Exception ignored) {}
            }
        }
    }

    public void syncToPlayer(ServerPlayer player) {
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
                if (party != null) config = createTeamConfig(party);
            } catch (Exception ignored) {}
        }
        if (config != null) {
            try { PacketDistributor.sendToPlayer(player, new TeamConfigSyncPayload(config)); } catch (Exception ignored) {}
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
                            TeamClaimManager claimMgr = TeamClaimsInit.getClaimManager();
                            if (claimMgr != null) claimMgr.onPlayerLeftParty(partyId2, memberUUID);
                            removeSubConfigForPlayer(memberUUID, config.getSubConfigId());
                            ServerPlayer player = server.getPlayerList().getPlayer(memberUUID);
                            if (player != null) {
                                try { PacketDistributor.sendToPlayer(player, new TeamConfigRemovedPayload(partyId2)); } catch (Exception ignored) {}
                            }
                            membershipChanged = true;
                        }
                    }
                }
                String prevName = lastKnownNames.get(partyId2);
                String currName = currentNames.get(partyId2);
                if (currName != null && !currName.equals(prevName)) onTeamNameChanged(partyId2, currName);
                if (membershipChanged) { dirtyConfigs.add(config); syncToOnlineMembers(config); }
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
            IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigs();
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
