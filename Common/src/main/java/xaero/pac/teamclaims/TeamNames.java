package xaero.pac.teamclaims;

import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * The one team name validation used by both {@code /teamclaims create <name>} and
 * {@code /<parties> create <teamname>} (see {@link TeamClaimsCommands#createPartyWithTeamName}).
 */
public final class TeamNames {

    /** Minecraft formatting codes written with '§' or '&', e.g. "&a" or "§l". */
    private static final Pattern FORMATTING_CODE = Pattern.compile("[§&][0-9a-fk-orA-FK-OR]");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private TeamNames() {}

    /**
     * The outcome of {@link #validate}: either a usable {@link #name()} or the lang key of the reason it was
     * rejected ({@link #errorKey()}) with the arguments of that message ({@link #errorArgs()}), never both.
     */
    public record Result(@Nullable String name, @Nullable String errorKey, Object... errorArgs) {
        public boolean isValid() { return name != null; }
    }

    /** The configured maximum length of a team name ({@code maxTeamNameLength} of the Team Claims server config). */
    public static int getMaxLength() {
        return TeamClaimsServerConfig.CONFIG.maxTeamNameLength.get();
    }

    /**
     * Strips formatting codes and any remaining '§'/'&' characters, collapses whitespace and trims.
     */
    public static String sanitize(String rawName) {
        String withoutCodes = FORMATTING_CODE.matcher(rawName).replaceAll("");
        String withoutPrefixes = withoutCodes.replace("§", "").replace("&", "");
        return WHITESPACE_RUN.matcher(withoutPrefixes).replaceAll(" ").trim();
    }

    /**
     * Sanitises {@code rawName} and checks that the result is not blank, at most {@link #getMaxLength()}
     * characters long, and accepted by OPAC's own {@code PARTY_NAME} option validator.
     *
     * @param ownerConfig  the config the name will be set on, null to skip the {@code PARTY_NAME} validator
     */
    public static Result validate(String rawName, @Nullable IPlayerConfigAPI ownerConfig) {
        String name = sanitize(rawName);
        if (name.isEmpty())
            return new Result(null, "gui.xaero_pac_team_claims_create_name_empty");
        int maxLength = getMaxLength();
        if (name.length() > maxLength)
            return new Result(null, "gui.xaero_pac_team_claims_create_name_too_long", maxLength);
        if (ownerConfig != null && !PlayerConfigOptions.PARTY_NAME.getServerSideValidator().test(ownerConfig, name))
            return new Result(null, "gui.xaero_pac_team_claims_create_name_invalid");
        return new Result(name, null);
    }
}
