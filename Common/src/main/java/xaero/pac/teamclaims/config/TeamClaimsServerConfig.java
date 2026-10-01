package xaero.pac.teamclaims.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The server config of Team Claims, a {@link ModConfigSpec} like OPAC's own
 * {@link xaero.pac.common.server.config.ServerConfig} but registered by the loader adapters under its own file name
 * ({@link #FILE_NAME}), since a second SERVER config of the same mod must not collide with
 * {@code openpartiesandclaims-server.toml}.
 * <p>
 * Loading: the values are only readable once the loader has loaded the SERVER configs of the starting server, which
 * is before OPAC's server-about-to-start handling (and with it
 * {@link xaero.pac.teamclaims.TeamClaimsCommon#onAddonRegister}) on both Fabric and NeoForge, but <em>after</em> the
 * commands were registered (the server's {@code Commands} are built before it starts). Reading a value earlier than
 * that, or after the server stopped, throws {@link IllegalStateException}. Edits of the file while the server runs are
 * picked up by the loaders' file watcher and apply from then on, except {@link #enabled}, which is read once when the
 * server starts.
 */
public class TeamClaimsServerConfig {

    /** File name of the config, in the {@code config} folder (a copy in the world's {@code serverconfig} folder overrides it). */
    public static final String FILE_NAME = "openpartiesandclaims-teamclaims-server.toml";

    public static final int DEFAULT_MAX_TEAM_NAME_LENGTH = 24;
    public static final int MAX_FORCELOAD_GRACE_MINUTES = 24 * 60;

    public final ModConfigSpec.BooleanValue enabled;
    public final ModConfigSpec.IntValue maxTeamNameLength;
    public final ModConfigSpec.IntValue forceloadGraceMinutes;

    private TeamClaimsServerConfig(ModConfigSpec.Builder builder) {
        builder.push("teamClaims");

        enabled = builder
                .comment("""
                        Whether Team Claims is active on this server.
                        When false, Team Claims does not start: Open Parties and Claims behaves exactly like the original mod,
                        /teamclaims is unavailable and no team claim tracking, team configs or team forceloads are managed.
                        Existing Team Claims data stays untouched on disk, so enabling it again later picks it up.
                        This is read once when the server starts: changing it needs a server restart.""")
                .worldRestart()
                .define("enabled", true);

        maxTeamNameLength = builder
                .comment("""
                        The maximum length (in characters, after removing formatting codes) of a team name given to
                        /teamclaims create <name> or to the team name argument of the party create command.""")
                .defineInRange("maxTeamNameLength", DEFAULT_MAX_TEAM_NAME_LENGTH, 1, 100);

        forceloadGraceMinutes = builder
                .comment("""
                        How long (in minutes) the forceloaded chunks of a team stay loaded after the last online member of the team
                        went offline. If a member comes back before the time is up, the forceloads simply stay active.
                        0 = the forceloads are released right away when the last member goes offline.""")
                .defineInRange("forceloadGraceMinutes", 0, 0, MAX_FORCELOAD_GRACE_MINUTES);

        builder.pop();
    }

    public static final ModConfigSpec SPEC;
    public static final TeamClaimsServerConfig CONFIG;
    static {
        final Pair<TeamClaimsServerConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(TeamClaimsServerConfig::new);
        SPEC = specPair.getRight();
        CONFIG = specPair.getLeft();
    }
}
