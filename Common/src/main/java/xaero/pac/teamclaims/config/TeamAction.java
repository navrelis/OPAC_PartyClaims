package xaero.pac.teamclaims.config;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The team claim actions a team can restrict to a minimum {@link TeamRole}. Only ever about the team claims of the
 * player's own team; personal claims are never restricted.
 * <ul>
 *     <li>{@link #CLAIM}: making a team claim, i.e. claiming with the team sub-config (also over an existing claim)</li>
 *     <li>{@link #UNCLAIM}: unclaiming a team claim, the player's own or a teammate's</li>
 *     <li>{@link #FORCELOAD}: turning the forceload of a team claim on or off, the player's own or a teammate's</li>
 * </ul>
 */
public enum TeamAction {
    CLAIM, UNCLAIM, FORCELOAD;

    private final String id = name().toLowerCase(Locale.ROOT);

    /** The lowercase name, used as the key in the {@code roles} object of the team config and by {@code /teamclaims roles}. */
    public String id() { return id; }

    /** The localized name, e.g. "Making team claims". */
    public Component displayName() { return Component.translatable("gui.xaero_pac_team_claims_roles_action_" + id); }

    @Nullable
    public static TeamAction byId(@Nullable String id) {
        if (id == null) return null;
        for (TeamAction action : values())
            if (action.id.equalsIgnoreCase(id)) return action;
        return null;
    }
}
