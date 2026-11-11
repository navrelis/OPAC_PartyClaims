package xaero.pac.teamclaims.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The client config of Team Claims, a {@link ModConfigSpec} registered by the loader adapters under its own file name
 * ({@link #FILE_NAME}) next to the {@code openpartiesandclaims-teamclaims-server.toml} of {@link TeamClaimsServerConfig}.
 * It only exists on the client. Reading a value before the loader has loaded the file throws {@link IllegalStateException}.
 */
public class TeamClaimsClientConfig {

    /** File name of the config, in the {@code config} folder. */
    public static final String FILE_NAME = "openpartiesandclaims-teamclaims-client.toml";

    /** Where the claiming button sits on the screen of Xaero's World Map. */
    public enum ButtonPosition {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public final ModConfigSpec.BooleanValue worldMapButton;
    public final ModConfigSpec.EnumValue<ButtonPosition> worldMapButtonPosition;

    private TeamClaimsClientConfig(ModConfigSpec.Builder builder) {
        builder.push("worldMap");

        worldMapButton = builder
                .comment("""
                        Whether the map screen of Xaero's World Map (Fabric only) shows a button that tells whether you are
                        claiming privately or for your team, with the claims used and the limit, and switches between the two.
                        The extra entries of the map's right-click menu are not affected by this.""")
                .define("worldMapButton", true);

        worldMapButtonPosition = builder
                .comment("""
                        Where the button sits on the map screen. Possible values: TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT.""")
                .defineEnum("worldMapButtonPosition", ButtonPosition.TOP_CENTER);

        builder.pop();
    }

    public static final ModConfigSpec SPEC;
    public static final TeamClaimsClientConfig CONFIG;
    static {
        final Pair<TeamClaimsClientConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(TeamClaimsClientConfig::new);
        SPEC = specPair.getRight();
        CONFIG = specPair.getLeft();
    }
}
