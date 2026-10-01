package xaero.pac.teamclaims.config;

import com.google.gson.*;

import java.util.*;

public class TeamConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final UUID partyId;
    private String teamName;
    private String subConfigId;
    private final Map<String, JsonElement> settings = new LinkedHashMap<>();
    private final Set<UUID> memberUUIDs = new LinkedHashSet<>();

    public TeamConfig(UUID partyId, String teamName) {
        this.partyId = Objects.requireNonNull(partyId);
        this.teamName = teamName != null ? teamName : "";
        this.subConfigId = buildSubConfigId(this.teamName);
        initDefaults();
    }

    private TeamConfig(UUID partyId, String teamName, String subConfigId,
                       Map<String, JsonElement> settings, Set<UUID> members) {
        this.partyId = partyId;
        this.teamName = teamName;
        this.subConfigId = subConfigId;
        this.settings.putAll(settings);
        this.memberUUIDs.addAll(members);
    }

    private void initDefaults() {
        settings.putIfAbsent("forceLoadShared", new JsonPrimitive(true));
        settings.putIfAbsent("allowMemberClaims", new JsonPrimitive(true));
    }

    public static String buildSubConfigId(String teamName) {
        // Sanitize: lowercase, keep only [a-z0-9_-], collapse multiple underscores
        String sanitized = teamName.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (sanitized.isEmpty()) sanitized = "unnamed";
        return "team_" + sanitized;
    }

    /** @deprecated Use buildSubConfigId(String teamName) instead */
    @Deprecated
    public static String buildSubConfigIdFromUUID(UUID partyId) {
        return "team_" + String.format("%08x", partyId.getLeastSignificantBits() & 0xFFFFFFFFL);
    }

    public static int computeTeamColor(UUID partyId) {
        int hashCode = partyId.hashCode();
        int r = (hashCode >> 16) & 0xFF;
        int g = (hashCode >> 8) & 0xFF;
        int b = hashCode & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max > 0) { r = r * 255 / max; g = g * 255 / max; b = b * 255 / max; }
        int color = (r << 16) | (g << 8) | b;
        if (color == 0) color = 0xFF0000;
        return color;
    }

    public synchronized boolean updateTeamName(String newName) {
        if (newName == null) newName = "";
        if (newName.equals(this.teamName)) return false;
        this.teamName = newName;
        return true;
    }

    public synchronized boolean addMember(UUID playerUUID) { return memberUUIDs.add(playerUUID); }
    public synchronized boolean removeMember(UUID playerUUID) { return memberUUIDs.remove(playerUUID); }
    public synchronized boolean isMember(UUID playerUUID) { return memberUUIDs.contains(playerUUID); }
    public synchronized Set<UUID> getMembers() { return new LinkedHashSet<>(memberUUIDs); }
    public synchronized int getMemberCount() { return memberUUIDs.size(); }
    public synchronized void setMembers(Collection<UUID> members) { memberUUIDs.clear(); memberUUIDs.addAll(members); }

    public synchronized void setSetting(String key, JsonElement value) {
        settings.put(key, value == null ? JsonNull.INSTANCE : value.deepCopy());
    }
    public synchronized JsonElement getSetting(String key) {
        JsonElement el = settings.get(key);
        return el != null ? el.deepCopy() : null;
    }
    public synchronized boolean getBoolSetting(String key, boolean defaultVal) {
        JsonElement el = settings.get(key);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
        return defaultVal;
    }
    public synchronized Map<String, JsonElement> getSettings() { return new LinkedHashMap<>(settings); }

    public UUID getPartyId() { return partyId; }
    public synchronized String getTeamName() { return teamName; }
    public synchronized String getSubConfigId() { return subConfigId; }

    public synchronized JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("partyId", partyId.toString());
        json.addProperty("teamName", teamName);
        json.addProperty("subConfigId", subConfigId);
        JsonArray membersArr = new JsonArray();
        for (UUID uuid : memberUUIDs) membersArr.add(uuid.toString());
        json.add("members", membersArr);
        JsonObject settingsObj = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : settings.entrySet()) settingsObj.add(entry.getKey(), entry.getValue().deepCopy());
        json.add("settings", settingsObj);
        return json;
    }

    public static TeamConfig fromJson(JsonObject json) {
        if (!json.has("partyId") || !json.has("teamName"))
            throw new JsonParseException("TeamConfig JSON missing required fields");
        UUID partyId = UUID.fromString(json.get("partyId").getAsString());
        String teamName = json.get("teamName").getAsString();
        String subConfigId = json.has("subConfigId") ? json.get("subConfigId").getAsString() : buildSubConfigId(teamName);
        Map<String, JsonElement> settings = new LinkedHashMap<>();
        if (json.has("settings")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("settings").entrySet())
                settings.put(entry.getKey(), entry.getValue().deepCopy());
        }
        Set<UUID> members = new LinkedHashSet<>();
        if (json.has("members")) {
            for (JsonElement el : json.getAsJsonArray("members")) {
                try { members.add(UUID.fromString(el.getAsString())); } catch (IllegalArgumentException ignored) {}
            }
        }
        TeamConfig config = new TeamConfig(partyId, teamName, subConfigId, settings, members);
        config.initDefaults();
        return config;
    }

    public String toJsonString() { return GSON.toJson(toJson()); }

    @Override
    public String toString() {
        return "TeamConfig{partyId=" + partyId + ", teamName='" + getTeamName() + "', members=" + getMemberCount() + "}";
    }
}
