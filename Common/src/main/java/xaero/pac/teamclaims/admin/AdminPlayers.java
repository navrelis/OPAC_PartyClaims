package xaero.pac.teamclaims.admin;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import org.slf4j.Logger;
import xaero.pac.common.packet.teamclaims.AdminPlayerBonus;
import xaero.pac.common.packet.teamclaims.AdminPlayerData;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommon;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The players of the admin menu: finding one, their limit summary, and changing their four bonus options through OPAC's
 * player config ({@link IPlayerConfigAPI#tryToSet}, the same call {@code /opac player-config for <player> set} makes,
 * so every OPAC change handler, sync and save runs). Server thread only.
 */
public final class AdminPlayers {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final String KEY = "gui.xaero_pac_team_claims_admin_player_";

    /** The outcome of {@link #setBonus}, as a message key with literal arguments. */
    public record Outcome(boolean success, String messageKey, List<String> args) {}

    private AdminPlayers() {}

    /**
     * The player an admin means with {@code query}: a UUID or a name (any case), online or offline. An offline player
     * is known if OPAC has claim data of them (every player who joined since OPAC was installed, with the name of their
     * last login); a UUID is also found in the server's profile cache. Names are deliberately not looked up through the
     * profile cache: for an unknown name that asks Mojang's servers (blocking the server thread) or, in offline mode,
     * makes up a profile for any name. Null if nobody is found.
     */
    @Nullable
    public static GameProfile find(MinecraftServer server, String query) {
        String input = query.trim();
        if (input.isEmpty()) return null;
        UUID id = AdminTeams.parseUuid(input);
        if (id != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) return online.getGameProfile();
            IServerPlayerClaimInfoAPI info = knownClaimInfo(server, id);
            Optional<GameProfile> cached = profileCache(server) == null ? Optional.empty() : profileCache(server).get(id);
            if (cached.isPresent()) return cached.get();
            if (info != null) return new GameProfile(id, usernameOf(info, id));
            return null;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers())
            if (online.getGameProfile().getName().equalsIgnoreCase(input)) return online.getGameProfile();
        IServerPlayerClaimInfoAPI info = claims(server).getPlayerInfoStream()
                .filter(i -> input.equalsIgnoreCase(i.getPlayerUsername())).findFirst().orElse(null);
        return info == null ? null : new GameProfile(info.getPlayerId(), info.getPlayerUsername());
    }

    @Nullable
    private static GameProfileCache profileCache(MinecraftServer server) {
        try {
            return server.getProfileCache();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static IServerClaimsManagerAPI claims(MinecraftServer server) {
        return OpenPACServerAPI.get(server).getServerClaimsManager();
    }

    /** The claim data OPAC already has of the player, without creating any. */
    @Nullable
    private static IServerPlayerClaimInfoAPI knownClaimInfo(MinecraftServer server, UUID id) {
        return claims(server).getPlayerInfoStream().filter(info -> id.equals(info.getPlayerId())).findFirst().orElse(null);
    }

    private static String usernameOf(IServerPlayerClaimInfoAPI info, UUID id) {
        String name = info.getPlayerUsername();
        return name == null || name.isEmpty() ? id.toString() : name;
    }

    /** The limit summary of a player, see {@link AdminPlayerData}. */
    public static AdminPlayerData summary(MinecraftServer server, GameProfile profile) {
        UUID id = profile.getId();
        IServerClaimsManagerAPI claims = claims(server);
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        int baseClaims = online != null ? claims.getPlayerBaseClaimLimit(online) : claims.getPlayerBaseClaimLimit(id);
        int baseForceloads = online != null ? claims.getPlayerBaseForceloadLimit(online) : claims.getPlayerBaseForceloadLimit(id);
        int baseAnchors = online != null ? claims.getPlayerBaseAnchorLimit(online) : claims.getPlayerBaseAnchorLimit(id);
        int baseAnchorRange = online != null ? claims.getPlayerBaseAnchorRange(online) : claims.getPlayerBaseAnchorRange(id);
        IPlayerConfigAPI config = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(id);
        IServerPlayerClaimInfoAPI info = knownClaimInfo(server, id);
        int claimCount = 0;
        int forceloadCount = 0;
        int anchors = 0;
        if (info != null) {
            TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
            if (cm != null) {
                TeamClaimManager.BudgetInfo budget = cm.getBudgetInfo(id);
                claimCount = budget.privateClaims();
                forceloadCount = budget.privateForceloads();
            } else {
                claimCount = info.getClaimCount();
                forceloadCount = info.getForceloadCount();
            }
            anchors = info.getStream().mapToInt(entry -> entry.getValue().getAnchors().size()).sum();
        }
        return new AdminPlayerData(id, profile.getName() == null ? id.toString() : profile.getName(), online != null,
                baseClaims, baseForceloads, baseAnchors, baseAnchorRange, bonus(config, AdminPlayerBonus.CLAIMS),
                bonus(config, AdminPlayerBonus.FORCELOADS), bonus(config, AdminPlayerBonus.ANCHORS),
                bonus(config, AdminPlayerBonus.ANCHOR_RANGE), claimCount, forceloadCount, anchors);
    }

    private static int bonus(IPlayerConfigAPI config, AdminPlayerBonus bonus) {
        Integer value = config.getEffective(bonus.option());
        return value == null ? 0 : value;
    }

    /**
     * Sets one bonus option of a player to {@code value} in their player config, see the class description.
     *
     * @param actor  who does it, for the log
     */
    public static Outcome setBonus(MinecraftServer server, GameProfile profile, AdminPlayerBonus bonus, int value, String actor) {
        String optionId = bonus.option().getId();
        if (Math.abs((long) value) > AdminPlayerBonus.MAX_ABS_VALUE)
            return new Outcome(false, KEY + "bonus_invalid", List.of(String.valueOf(value), optionId));
        IPlayerConfigAPI config = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(profile.getId());
        IPlayerConfigAPI.SetResult result = config.tryToSet(bonus.option(), value);
        String name = profile.getName() == null ? profile.getId().toString() : profile.getName();
        return switch (result) {
            case SUCCESS -> {
                LOGGER.info("[TeamClaims] {} set the player config option {} of {} to {}", actor, optionId, name, value);
                yield new Outcome(true, KEY + "bonus_set", List.of(name, optionId, String.valueOf(value)));
            }
            case DEFAULTED -> new Outcome(false, KEY + "bonus_forced", List.of(optionId, String.valueOf(config.getEffective(bonus.option()))));
            case ILLEGAL_OPTION -> new Outcome(false, KEY + "bonus_illegal", List.of(optionId));
            default -> new Outcome(false, KEY + "bonus_invalid", List.of(String.valueOf(value), optionId));
        };
    }
}
