package xaero.pac.teamclaims.admin;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Bridges two versions of NeoForge's {@link ModConfigSpec} API, through their public methods only.
 * <p>
 * Common compiles against the config API of Forge Config API Port 21.0.5, where a {@link ModConfigSpec.ValueSpec} has
 * {@code needsWorldRestart()} and {@link ModConfigSpec.ConfigValue} has no public {@code getRaw()}. The loaders run the
 * 21.1 API (NeoForge 21.1, Forge Config API Port 21.1 on Fabric), where those are {@code restartType()} and
 * {@code getRaw()}. Both are looked up once by name.
 * <p>
 * The 21.1 API is also what makes a restart-only write safe ({@link #supportsRestartOnlyWrites()}): there,
 * {@code ConfigValue.set} on an option with a restart type only changes the loaded config (and so the file on
 * {@code save()}), not the cached value {@code get()} returns, and a reload of the file (the file watcher, or a save)
 * only clears the caches of options without a restart type ({@code ModConfigSpec.afterReload}). In the older API
 * {@code set} also replaced the cached value, so a restart-only option would be half applied at once; there such writes
 * are refused.
 */
final class ConfigSpecCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable private static final Method RESTART_TYPE = find(ModConfigSpec.ValueSpec.class, "restartType");
    @Nullable private static final Method NEEDS_WORLD_RESTART = find(ModConfigSpec.ValueSpec.class, "needsWorldRestart");
    @Nullable private static final Method GET_RAW = find(ModConfigSpec.ConfigValue.class, "getRaw");

    private ConfigSpecCompat() {}

    @Nullable
    private static Method find(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Whether a change of the option only takes effect after a restart (world or game restart type). */
    static boolean isRestartOnly(ModConfigSpec.ValueSpec spec) {
        try {
            if (RESTART_TYPE != null) {
                Object type = RESTART_TYPE.invoke(spec);
                return type instanceof Enum<?> restartType && !"NONE".equals(restartType.name());
            }
            if (NEEDS_WORLD_RESTART != null)
                return Boolean.TRUE.equals(NEEDS_WORLD_RESTART.invoke(spec));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[TeamClaims] Could not read the restart type of a config option, treating it as restart-only", e);
        }
        return true;
    }

    /** Whether restart-only options can be written to the file without changing their running value, see above. */
    static boolean supportsRestartOnlyWrites() {
        return RESTART_TYPE != null && GET_RAW != null;
    }

    /**
     * The value in the loaded config (what the file holds), which differs from {@code get()} for a restart-only option
     * that was changed since the start. Null where the API cannot tell.
     */
    @Nullable
    static Object getRaw(ModConfigSpec.ConfigValue<?> value) {
        if (GET_RAW == null) return null;
        try {
            return GET_RAW.invoke(value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[TeamClaims] Could not read the raw value of config option {}", value.getPath(), e);
            return null;
        }
    }
}
