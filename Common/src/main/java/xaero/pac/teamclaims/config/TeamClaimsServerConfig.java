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
 * picked up by the loaders' file watcher and apply from then on, except {@link #enabled} and {@link #ftbTeamsSync},
 * which are read once when the server starts.
 */
public class TeamClaimsServerConfig {

    /** File name of the config, in the {@code config} folder (a copy in the world's {@code serverconfig} folder overrides it). */
    public static final String FILE_NAME = "openpartiesandclaims-teamclaims-server.toml";

    public static final int DEFAULT_MAX_TEAM_NAME_LENGTH = 24;
    public static final int MAX_FORCELOAD_GRACE_MINUTES = 24 * 60;
    public static final int DEFAULT_CONVERT_MAX_RADIUS = 4;
    public static final int MAX_CONVERT_RADIUS = 16;
    public static final int DEFAULT_TEAM_CLAIMS_MIN_MEMBERS = 2;
    public static final int DEFAULT_TEAM_CLAIMS_BASE = 500;
    public static final int DEFAULT_TEAM_CLAIMS_PER_EXTRA_MEMBER = 25;
    public static final int DEFAULT_TEAM_FORCELOADS_BASE = 10;
    public static final int DEFAULT_TEAM_FORCELOADS_PER_EXTRA_MEMBER = 2;
    public static final int DEFAULT_OVER_LIMIT_GRACE_HOURS = 7 * 24;
    public static final int MAX_OVER_LIMIT_GRACE_HOURS = 365 * 24;
    /** Upper bound of the base and per-member options, far above anything a server would want. */
    public static final int MAX_TEAM_BUDGET_VALUE = 1_000_000;

    public final ModConfigSpec.BooleanValue enabled;
    public final ModConfigSpec.IntValue maxTeamNameLength;
    public final ModConfigSpec.IntValue forceloadGraceMinutes;
    public final ModConfigSpec.BooleanValue territoryMessagesDefault;
    public final ModConfigSpec.IntValue convertMaxRadius;
    public final ModConfigSpec.IntValue teamClaimsMinMembers;
    public final ModConfigSpec.IntValue teamClaimsBase;
    public final ModConfigSpec.IntValue teamClaimsPerExtraMember;
    public final ModConfigSpec.IntValue teamForceloadsBase;
    public final ModConfigSpec.IntValue teamForceloadsPerExtraMember;
    public final ModConfigSpec.IntValue overLimitGraceHours;
    public final ModConfigSpec.BooleanValue ftbTeamsSync;

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

        territoryMessagesDefault = builder
                .comment("""
                        Whether players see the claim "welcome" messages (the action bar line shown when entering a claim or
                        the wilderness) unless they chose otherwise with /teamclaims territorymessages <on|off>.
                        With Team Claims, walking from one claim of a team to another claim of the same team shows nothing,
                        as it is one territory. This only applies while OPAC's own claimWelcomeMessages option is enabled.""")
                .define("territoryMessagesDefault", true);

        convertMaxRadius = builder
                .comment("""
                        The largest radius (in chunks around the player's current chunk) of /teamclaims convert <toteam|topersonal> [radius].
                        0 = only the chunk the player stands in. The largest allowed value, 16, is a square of 33x33 chunks.""")
                .defineInRange("convertMaxRadius", DEFAULT_CONVERT_MAX_RADIUS, 0, MAX_CONVERT_RADIUS);

        teamClaimsMinMembers = builder
                .comment("""
                        How many members (the owner included, invited players not) a team needs before it can make team claims
                        and team forceloads at all. A team with fewer members has a team claim limit and a team forceload limit of 0.
                        Team claims have their own budget per team: they never count against a member's private claim limit
                        (OPAC's maxPlayerClaims), and private claims never count against the team's.""")
                .defineInRange("teamClaimsMinMembers", DEFAULT_TEAM_CLAIMS_MIN_MEMBERS, 1, 100);

        teamClaimsBase = builder
                .comment("""
                        The team claim limit of a team that has exactly teamClaimsMinMembers members.
                        The limit of a team is teamClaimsBase + (members - teamClaimsMinMembers) * teamClaimsPerExtraMember.""")
                .defineInRange("teamClaimsBase", DEFAULT_TEAM_CLAIMS_BASE, 0, MAX_TEAM_BUDGET_VALUE);

        teamClaimsPerExtraMember = builder
                .comment("""
                        How many team claims every member beyond teamClaimsMinMembers adds to the team claim limit.
                        With the defaults: 2 members = 500, 3 members = 525, 4 members = 550 team claims.""")
                .defineInRange("teamClaimsPerExtraMember", DEFAULT_TEAM_CLAIMS_PER_EXTRA_MEMBER, 0, MAX_TEAM_BUDGET_VALUE);

        teamForceloadsBase = builder
                .comment("""
                        The team forceload limit of a team that has exactly teamClaimsMinMembers members.
                        The limit of a team is teamForceloadsBase + (members - teamClaimsMinMembers) * teamForceloadsPerExtraMember.
                        Team forceloads never count against a member's private forceload limit (OPAC's maxPlayerClaimForceloads).""")
                .defineInRange("teamForceloadsBase", DEFAULT_TEAM_FORCELOADS_BASE, 0, MAX_TEAM_BUDGET_VALUE);

        teamForceloadsPerExtraMember = builder
                .comment("""
                        How many team forceloads every member beyond teamClaimsMinMembers adds to the team forceload limit.
                        With the defaults: 2 members = 10, 3 members = 12, 4 members = 14 team forceloads.""")
                .defineInRange("teamForceloadsPerExtraMember", DEFAULT_TEAM_FORCELOADS_PER_EXTRA_MEMBER, 0, MAX_TEAM_BUDGET_VALUE);

        overLimitGraceHours = builder
                .comment("""
                        How long (in real hours, also while the server is off) a team may stay above its team claim or team
                        forceload limit, e.g. after a member left. The team can make no new team claims during that time and its
                        members are warned. When the time is up, the most recently made team claims are unclaimed (or the most
                        recent team forceloads turned off) until the team is within its limit again. A team that gets back within
                        its limit earlier keeps everything. A running countdown keeps its end time when this option is changed.
                        0 = the excess is removed at the next check (at the latest a minute later).""")
                .defineInRange("overLimitGraceHours", DEFAULT_OVER_LIMIT_GRACE_HOURS, 0, MAX_OVER_LIMIT_GRACE_HOURS);

        ftbTeamsSync = builder
                .comment("""
                        Whether parties are kept in sync with FTB Teams when that mod is installed, so that players only manage
                        one team: creating, renaming and disbanding a party, members joining, leaving and being kicked, ownership
                        transfers, ranks (FTB officer = party admin/moderator) and invitations done in either mod happen in both.
                        Where the two mods disagree (e.g. when this is first enabled), Open Parties and Claims wins.
                        Allies, the FTB team color and description, and party chat are not synced.
                        Without FTB Teams this option does nothing. See /teamclaims ftbsync status.
                        This is read once when the server starts: changing it needs a server restart.""")
                .worldRestart()
                .define("ftbTeamsSync", true);

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
