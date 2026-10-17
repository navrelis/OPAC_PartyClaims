package xaero.pac.teamclaims.admin;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import xaero.pac.common.packet.teamclaims.AdminConfigEntry;
import xaero.pac.common.packet.teamclaims.AdminConfigEntry.ValueType;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The scalar options (boolean, int, long, double, enum, string) of OPAC's server config and of the Team Claims server
 * config, read and changed through the public API of their {@link ModConfigSpec}. List options are left out: they stay
 * toml-only.
 * <p>
 * A change is validated with the spec itself (type, range, allowed values: {@code ValueSpec.test}) and saved to the
 * toml at once:
 * <ul>
 *     <li>an option without a restart type (every Team Claims option except {@code enabled} and {@code ftbTeamsSync},
 *     the few OPAC options without {@code worldRestart()}) is live: {@code get()} returns the new value right away;</li>
 *     <li>a restart-only option is only written to the file. {@code get()} keeps returning the value the server
 *     started with until the next start (see {@link ConfigSpecCompat} for why that holds, also when the file watcher
 *     reloads the file); the entry shows the saved value as its {@code pendingValue}.</li>
 * </ul>
 * Server thread only.
 */
public final class AdminConfigs {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final String KEY = "gui.xaero_pac_team_claims_admin_config_";

    /** The two configs, by the id the packets use. */
    enum Config {
        OPAC(AdminConfigEntry.CONFIG_OPAC, () -> ServerConfig.SPEC),
        TEAM_CLAIMS(AdminConfigEntry.CONFIG_TEAM_CLAIMS, () -> TeamClaimsServerConfig.SPEC);

        final String id;
        final Supplier<ModConfigSpec> spec;

        Config(String id, Supplier<ModConfigSpec> spec) {
            this.id = id;
            this.spec = spec;
        }

        @Nullable
        static Config byId(String id) {
            for (Config config : values())
                if (config.id.equals(id)) return config;
            return null;
        }
    }

    /**
     * The outcome of {@link #set}: on success the updated entry and a confirmation, else the reason; both as a message
     * key with literal arguments.
     */
    public record Outcome(boolean success, @Nullable AdminConfigEntry entry, String messageKey, List<String> args) {
        static Outcome fail(String key, String... args) {
            return new Outcome(false, null, KEY + key, List.of(args));
        }
    }

    private AdminConfigs() {}

    // ==================== Reading ====================

    /** Every scalar option of both configs, OPAC's first, each in the order of its spec. */
    public static List<AdminConfigEntry> snapshot() {
        List<AdminConfigEntry> entries = new ArrayList<>();
        for (Config config : Config.values()) {
            ModConfigSpec spec = config.spec.get();
            if (!spec.isLoaded()) continue;
            collect(config, spec, spec.getValues().valueMap().values(), entries);
        }
        return entries;
    }

    private static void collect(Config config, ModConfigSpec spec, Iterable<Object> values, List<AdminConfigEntry> entries) {
        for (Object value : values) {
            if (value instanceof ModConfigSpec.ConfigValue<?> configValue) {
                AdminConfigEntry entry = entryOf(config, spec, configValue);
                if (entry != null) entries.add(entry);
            } else if (value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig inner) {
                collect(config, spec, inner.valueMap().values(), entries);
            }
        }
    }

    /** The entry of one option, null if there is no such scalar option (unknown, a list, a section). */
    @Nullable
    public static AdminConfigEntry entry(String configId, String path) {
        Config config = Config.byId(configId);
        if (config == null) return null;
        ModConfigSpec spec = config.spec.get();
        ModConfigSpec.ConfigValue<?> value = find(spec, path);
        return value == null ? null : entryOf(config, spec, value);
    }

    @Nullable
    private static ModConfigSpec.ConfigValue<?> find(ModConfigSpec spec, String path) {
        if (!spec.isLoaded() || path.isEmpty()) return null;
        List<String> parts = Arrays.asList(path.split("\\.", -1));
        if (parts.stream().anyMatch(String::isEmpty)) return null;
        try {
            Object value = spec.getValues().get(parts);
            return value instanceof ModConfigSpec.ConfigValue<?> configValue ? configValue : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static ModConfigSpec.ValueSpec valueSpecOf(ModConfigSpec spec, ModConfigSpec.ConfigValue<?> value) {
        Object valueSpec = spec.getSpec().get(value.getPath());
        return valueSpec instanceof ModConfigSpec.ValueSpec vs ? vs : null;
    }

    @Nullable
    static ValueType typeOf(@Nullable Object defaultValue) {
        if (defaultValue instanceof Boolean) return ValueType.BOOLEAN;
        if (defaultValue instanceof Integer) return ValueType.INT;
        if (defaultValue instanceof Long) return ValueType.LONG;
        if (defaultValue instanceof Double || defaultValue instanceof Float) return ValueType.DOUBLE;
        if (defaultValue instanceof Enum<?>) return ValueType.ENUM;
        if (defaultValue instanceof String) return ValueType.STRING;
        return null;//lists and anything else stay toml-only
    }

    @Nullable
    private static AdminConfigEntry entryOf(Config config, ModConfigSpec spec, ModConfigSpec.ConfigValue<?> value) {
        ModConfigSpec.ValueSpec valueSpec = valueSpecOf(spec, value);
        if (valueSpec == null) return null;
        Object defaultValue = value.getDefault();
        ValueType type = typeOf(defaultValue);
        if (type == null) return null;
        String path = String.join(".", value.getPath());
        boolean restart = ConfigSpecCompat.isRestartOnly(valueSpec);
        Object current = value.get();//for a restart-only option this also pins the value the server runs with
        String currentText = toText(current);
        String pending = null;
        if (restart) {
            Object raw = ConfigSpecCompat.getRaw(value);
            if (raw != null && !toText(raw).equals(currentText)) pending = toText(raw);
        }
        ModConfigSpec.Range<?> range = valueSpec.getRange();
        List<String> allowed = new ArrayList<>();
        if (defaultValue instanceof Enum<?> enumDefault)
            for (Object constant : enumDefault.getDeclaringClass().getEnumConstants())
                if (valueSpec.test(constant)) allowed.add(((Enum<?>) constant).name());
        String comment = valueSpec.getComment();
        return new AdminConfigEntry(config.id, path, type, currentText, toText(defaultValue),
                range == null ? null : toText(range.getMin()), range == null ? null : toText(range.getMax()), allowed,
                comment == null ? "" : comment.strip(), restart, pending);
    }

    static String toText(@Nullable Object value) {
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        return String.valueOf(value);
    }

    // ==================== Writing ====================

    /**
     * Sets an option to {@code valueText}, see the class description. Nothing changes unless the outcome is a success.
     *
     * @param actor  who does it, for the log
     */
    public static Outcome set(String configId, String path, String valueText, String actor) {
        Config config = Config.byId(configId);
        ModConfigSpec spec = config == null ? null : config.spec.get();
        ModConfigSpec.ConfigValue<?> value = spec == null ? null : find(spec, path);
        ModConfigSpec.ValueSpec valueSpec = value == null ? null : valueSpecOf(spec, value);
        ValueType type = value == null ? null : typeOf(value.getDefault());
        if (valueSpec == null || type == null)
            return Outcome.fail("unknown", configId + ":" + path);

        Object parsed = parse(type, value.getDefault(), valueText);
        if (parsed == null || !valueSpec.test(parsed)) {
            ModConfigSpec.Range<?> range = valueSpec.getRange();
            if (parsed != null && range != null)
                return Outcome.fail("out_of_range", valueText, path, toText(range.getMin()), toText(range.getMax()));
            return Outcome.fail("invalid_value", valueText, path);
        }
        boolean restart = ConfigSpecCompat.isRestartOnly(valueSpec);
        if (restart && !ConfigSpecCompat.supportsRestartOnlyWrites())
            return Outcome.fail("restart_unsupported", path);

        @SuppressWarnings("unchecked")
        ModConfigSpec.ConfigValue<Object> target = (ModConfigSpec.ConfigValue<Object>) value;
        // For a restart-only option, get() caches the running value first, so that set() (which leaves that cache
        // alone for such options) only changes the loaded config and thereby the file
        Object running = target.get();
        Object previousRaw = restart ? ConfigSpecCompat.getRaw(target) : running;
        try {
            target.set(parsed);
            spec.save();
        } catch (RuntimeException e) {
            LOGGER.error("[TeamClaims] Could not save config option {} of {} = {}", path, configId, valueText, e);
            try {
                target.set(previousRaw != null ? previousRaw : running);
            } catch (RuntimeException ignored) {
            }
            return Outcome.fail("save_failed", path);
        }
        String text = toText(parsed);
        LOGGER.info("[TeamClaims] {} set the {} server config option {} to {}{}", actor, configId, path, text,
                restart ? " (restart-only: saved to the file, the running value stays " + toText(running) + " until the next start)" : "");
        return new Outcome(true, entryOf(config, spec, value), KEY + (restart ? "saved_restart" : "saved"), List.of(path, text));
    }

    /** The value of {@code type} that {@code text} stands for, null if it stands for none. */
    @Nullable
    static Object parse(ValueType type, Object defaultValue, String text) {
        try {
            return switch (type) {
                case BOOLEAN -> {
                    String trimmed = text.trim().toLowerCase(Locale.ROOT);
                    yield trimmed.equals("true") ? Boolean.TRUE : trimmed.equals("false") ? Boolean.FALSE : null;
                }
                case INT -> Integer.parseInt(text.trim());
                case LONG -> Long.parseLong(text.trim());
                case DOUBLE -> {
                    double parsed = Double.parseDouble(text.trim());
                    if (!Double.isFinite(parsed)) yield null;
                    yield defaultValue instanceof Float ? (Object) (float) parsed : (Object) parsed;
                }
                case ENUM -> {
                    Object found = null;
                    for (Object constant : ((Enum<?>) defaultValue).getDeclaringClass().getEnumConstants())
                        if (((Enum<?>) constant).name().equalsIgnoreCase(text.trim())) found = constant;
                    yield found;
                }
                case STRING -> text;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
