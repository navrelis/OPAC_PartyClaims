package xaero.pac.teamclaims.config;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * The persisted settings of one party's team, stored as {@code <world>/data/opacteamclaims/teams/<partyId>.json}.
 * <p>
 * JSON format: {@code partyId}, {@code teamName}, {@code subConfigId}, {@code members}, {@code settings}
 * (the canonical claims color/name under {@link #KEY_CLAIMS_COLOR}/{@link #KEY_CLAIMS_NAME}, plus any unknown
 * keys, which are kept as they are) and, since the option persistence was added, {@code options}: every other
 * option a party owner/admin set on the team sub-config, by option ID. Files without {@code options} load
 * unchanged. {@code roles}: the {@link TeamRole} each {@link TeamAction} requires, e.g.
 * {@code "roles": {"claim": "MODERATOR", "unclaim": "MEMBER", "forceload": "MEMBER"}}; a missing or unknown value is
 * the {@linkplain TeamRole#DEFAULT default}, so files without {@code roles} load with the behaviour without roles.
 * <p>
 * Server-thread confined: an instance is only ever read or modified on the server thread. Saving serialises it to
 * a String there ({@link #toJsonString()}); only that String is handed to the IO thread.
 */
public class TeamConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final String KEY_CLAIMS_COLOR = "opac.CLAIMS_COLOR";
    static final String KEY_CLAIMS_NAME = "opac.CLAIMS_NAME";
    /** Settings written by older versions but never read. Tolerated when reading, no longer written. */
    private static final Set<String> UNUSED_LEGACY_SETTINGS = Set.of("forceLoadShared", "allowMemberClaims");

    private static final String SUB_ID_PREFIX = TeamClaimsIntegration.TEAM_SUB_ID_PREFIX;
    /**
     * OPAC's config packets reject sub-config IDs longer than 100 characters (e.g.
     * {@code ClientboundPlayerConfigAbstractStatePacket}, {@code ServerboundSubConfigExistencePacket}), while
     * {@code PlayerConfig.isValidSubIdOrTeam} puts no length limit on team sub-config IDs.
     */
    public static final int MAX_SUB_CONFIG_ID_LENGTH = 100;
    private static final int PARTY_ID_SUFFIX_LENGTH = 8;

    private final UUID partyId;
    private String teamName;
    private final String subConfigId;
    /** The canonical claims color/name plus unknown keys, see the class description. */
    private final Map<String, JsonElement> settings = new LinkedHashMap<>();
    /** Admin-set team sub-config options other than the claims color/name, by option ID. Values are never null. */
    private final Map<String, Object> options = new LinkedHashMap<>();
    private final Set<UUID> memberUUIDs = new LinkedHashSet<>();
    /** The level every team action requires, never null. */
    private final EnumMap<TeamAction, TeamRole> roles = new EnumMap<>(TeamAction.class);

    public TeamConfig(UUID partyId, @Nullable String teamName, String subConfigId) {
        this.partyId = Objects.requireNonNull(partyId);
        this.teamName = teamName != null ? teamName : "";
        this.subConfigId = Objects.requireNonNull(subConfigId);
        for (TeamAction action : TeamAction.values()) roles.put(action, TeamRole.DEFAULT);
    }

    // ==================== Sub-config IDs ====================

    /**
     * The sub-config ID older versions derived from the team name. Only used for stored configs that don't have a
     * {@code subConfigId} yet, so that they keep the ID their members' sub-configs already use. New teams get
     * theirs from {@link #allocateSubConfigId}.
     */
    public static String buildLegacySubConfigId(String teamName) {
        String sanitized = sanitizeForSubId(teamName);
        if (sanitized.isEmpty()) sanitized = "unnamed";
        return SUB_ID_PREFIX + sanitized;
    }

    /**
     * Picks the sub-config ID of a NEW team: {@code team_<sanitised name>}, or, when the sanitised name has no
     * letter or digit left (e.g. a name made only of CJK characters) or the ID is already used by another team, the
     * same with a short suffix taken from the party UUID. Falls back to the full party UUID, which is unique, in the
     * (practically impossible) case that the suffixed ID is taken too. The result is always at most
     * {@link #MAX_SUB_CONFIG_ID_LENGTH} characters and matches OPAC's sub ID pattern.
     *
     * @param isTaken  whether a sub-config ID is already used by another team
     */
    public static String allocateSubConfigId(String teamName, UUID partyId, Predicate<String> isTaken) {
        String suffix = partyIdHex(partyId).substring(0, PARTY_ID_SUFFIX_LENGTH);
        String sanitized = sanitizeForSubId(teamName);
        boolean meaningful = sanitized.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'));
        String candidate;
        if (!meaningful) {
            candidate = SUB_ID_PREFIX + suffix;
        } else {
            candidate = SUB_ID_PREFIX + truncate(sanitized, MAX_SUB_CONFIG_ID_LENGTH - SUB_ID_PREFIX.length());
            if (isTaken.test(candidate))
                candidate = SUB_ID_PREFIX
                        + truncate(sanitized, MAX_SUB_CONFIG_ID_LENGTH - SUB_ID_PREFIX.length() - 1 - suffix.length())
                        + "_" + suffix;
        }
        if (isTaken.test(candidate))
            candidate = SUB_ID_PREFIX + partyIdHex(partyId);
        return candidate;
    }

    /** Lowercase, every character outside {@code [a-z0-9_-]} becomes '_', runs of '_' collapse, no '_' at either end. */
    private static String sanitizeForSubId(String teamName) {
        return teamName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private static String truncate(String sanitized, int maxLength) {
        if (sanitized.length() <= maxLength) return sanitized;
        return sanitized.substring(0, maxLength).replaceAll("_$", "");
    }

    private static String partyIdHex(UUID partyId) {
        return partyId.toString().replace("-", "");
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

    // ==================== Team name / members ====================

    public boolean updateTeamName(@Nullable String newName) {
        if (newName == null) newName = "";
        if (newName.equals(this.teamName)) return false;
        this.teamName = newName;
        return true;
    }

    public boolean addMember(UUID playerUUID) { return memberUUIDs.add(playerUUID); }
    public boolean removeMember(UUID playerUUID) { return memberUUIDs.remove(playerUUID); }
    public boolean isMember(UUID playerUUID) { return memberUUIDs.contains(playerUUID); }
    /** Live read-only view; copy it before changing the membership while iterating. */
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(memberUUIDs); }

    public UUID getPartyId() { return partyId; }
    public String getTeamName() { return teamName; }
    public String getSubConfigId() { return subConfigId; }

    // ==================== Canonical settings ====================

    /** The admin-set claims color, or null when none is stored. */
    @Nullable
    public Integer getClaimsColor() {
        JsonElement el = settings.get(KEY_CLAIMS_COLOR);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) return el.getAsInt();
        return null;
    }

    /** @return true if the stored value changed */
    public boolean setClaimsColor(@Nullable Integer color) {
        return putSetting(KEY_CLAIMS_COLOR, color == null ? null : new JsonPrimitive(color));
    }

    /** The admin-set claims name, or null when none is stored. */
    @Nullable
    public String getClaimsName() {
        JsonElement el = settings.get(KEY_CLAIMS_NAME);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) return el.getAsString();
        return null;
    }

    /** @return true if the stored value changed */
    public boolean setClaimsName(@Nullable String name) {
        return putSetting(KEY_CLAIMS_NAME, name == null ? null : new JsonPrimitive(name));
    }

    private boolean putSetting(String key, @Nullable JsonElement value) {
        if (value == null) return settings.remove(key) != null;
        return !value.equals(settings.put(key, value));
    }

    /** Read-only view of the persisted options (option ID to value, never null). */
    public Map<String, Object> getOptions() { return Collections.unmodifiableMap(options); }

    /**
     * Stores (or, for a null value, removes) an admin-set option. Values of a type that can't be persisted are
     * ignored.
     *
     * @return true if the stored value changed
     */
    public boolean setOption(IPlayerConfigOptionSpecAPI<?> option, @Nullable Object value) {
        if (value == null) return options.remove(option.getId()) != null;
        if (toJson(value) == null) {
            LOGGER.debug("[TeamClaims] Not persisting team option {}: unsupported value type {}", option.getId(), value.getClass());
            return false;
        }
        Object stored = value instanceof List<?> list ? List.copyOf(list) : value;
        return !stored.equals(options.put(option.getId(), stored));
    }

    // ==================== Roles ====================

    /** The lowest level a member needs for {@code action} on the team claims of this team. */
    public TeamRole getRequiredRole(TeamAction action) { return roles.get(action); }

    /** @return true if the stored value changed */
    public boolean setRequiredRole(TeamAction action, TeamRole role) {
        return roles.put(action, Objects.requireNonNull(role)) != role;
    }

    // ==================== JSON ====================

    public JsonObject toJson() {
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
        if (!options.isEmpty()) {
            JsonObject optionsObj = new JsonObject();
            for (Map.Entry<String, Object> entry : options.entrySet()) optionsObj.add(entry.getKey(), toJson(entry.getValue()));
            json.add("options", optionsObj);
        }
        JsonObject rolesObj = new JsonObject();
        for (Map.Entry<TeamAction, TeamRole> entry : roles.entrySet()) rolesObj.addProperty(entry.getKey().id(), entry.getValue().name());
        json.add("roles", rolesObj);
        return json;
    }

    public String toJsonString() { return GSON.toJson(toJson()); }

    /**
     * @throws RuntimeException (e.g. {@link JsonParseException}, {@link IllegalStateException},
     *         {@link IllegalArgumentException}) if the JSON isn't a valid team config
     */
    public static TeamConfig fromJson(JsonObject json) {
        if (!json.has("partyId") || !json.has("teamName"))
            throw new JsonParseException("TeamConfig JSON missing required fields");
        UUID partyId = UUID.fromString(json.get("partyId").getAsString());
        String teamName = json.get("teamName").getAsString();
        String subConfigId = json.has("subConfigId") ? json.get("subConfigId").getAsString() : buildLegacySubConfigId(teamName);
        TeamConfig config = new TeamConfig(partyId, teamName, subConfigId);
        if (json.has("settings")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("settings").entrySet()) {
                if (UNUSED_LEGACY_SETTINGS.contains(entry.getKey())) continue;
                config.settings.put(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        if (json.has("options")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("options").entrySet()) {
                IPlayerConfigOptionSpecAPI<?> option = PlayerConfigOptions.OPTIONS.get(entry.getKey());
                Object value = option == null ? null : fromJson(entry.getValue(), option.getType());
                if (value == null) {
                    LOGGER.warn("[TeamClaims] Ignoring unknown or invalid team option '{}' = {} of party {}",
                            entry.getKey(), entry.getValue(), partyId);
                    continue;
                }
                config.options.put(entry.getKey(), value);
            }
        }
        if (json.has("roles")) {
            JsonElement rolesJson = json.get("roles");
            if (!rolesJson.isJsonObject()) {
                LOGGER.warn("[TeamClaims] Ignoring invalid team roles {} of party {}", rolesJson, partyId);
            } else {
                for (Map.Entry<String, JsonElement> entry : rolesJson.getAsJsonObject().entrySet()) {
                    TeamAction action = TeamAction.byId(entry.getKey());
                    JsonElement value = entry.getValue();
                    TeamRole role = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                            ? TeamRole.byName(value.getAsString()) : null;
                    if (action == null || role == null) {
                        LOGGER.warn("[TeamClaims] Ignoring unknown or invalid team role '{}' = {} of party {}",
                                entry.getKey(), value, partyId);
                        continue;
                    }
                    config.roles.put(action, role);
                }
            }
        }
        if (json.has("members")) {
            for (JsonElement el : json.getAsJsonArray("members")) {
                try {
                    config.memberUUIDs.add(UUID.fromString(el.getAsString()));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[TeamClaims] Ignoring invalid member '{}' in the team config of party {}", el, partyId);
                }
            }
        }
        return config;
    }

    @Nullable
    private static JsonElement toJson(Object value) {
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof String s) return new JsonPrimitive(s);
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            for (Object element : list) {
                if (!(element instanceof String s)) return null;
                array.add(s);
            }
            return array;
        }
        return null;
    }

    @Nullable
    private static Object fromJson(JsonElement json, Class<?> type) {
        try {
            if (type == List.class) {
                if (!json.isJsonArray()) return null;
                List<String> list = new ArrayList<>();
                for (JsonElement element : json.getAsJsonArray()) list.add(element.getAsString());
                return List.copyOf(list);
            }
            if (!json.isJsonPrimitive()) return null;
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (type == Boolean.class) return primitive.isBoolean() ? primitive.getAsBoolean() : null;
            if (type == String.class) return primitive.isString() ? primitive.getAsString() : null;
            if (!primitive.isNumber()) return null;
            if (type == Integer.class) return primitive.getAsInt();
            if (type == Double.class) return primitive.getAsDouble();
            if (type == Float.class) return primitive.getAsFloat();
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "TeamConfig{partyId=" + partyId + ", teamName='" + teamName + "', members=" + memberUUIDs.size() + "}";
    }
}
