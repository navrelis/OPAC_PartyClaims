package xaero.pac.teamclaims.admin;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.common.packet.teamclaims.ClientboundAdminFlagPacket;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.player.data.ServerPlayerData;
import xaero.pac.common.server.player.permission.api.IPlayerPermissionSystemAPI;
import xaero.pac.common.server.player.permission.api.UsedPermissionNodes;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who may use the admin menu of the hub screen and {@code /teamclaims bonus}: permission level 2, or the boolean
 * permission node {@code xaero.pac_admin_menu} ({@link UsedPermissionNodes#ADMIN_MENU}) through the permission system
 * OPAC is configured to use ({@code permissionSystem} in OPAC's server config), the same way OPAC checks its own nodes
 * (e.g. {@code ServerClaimsPermissionHandler.playerHasServerClaimPermission}).
 * <p>
 * The client only hides the Admin tab ({@link #syncFlag}); every admin request is checked here on the server
 * ({@link #checkRequest}).
 */
public final class AdminAccess {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The permission node, also usable without OPAC's node renaming (it has no config option). */
    public static final String PERMISSION_NODE = "xaero.pac_admin_menu";

    /** Players whose first rejected admin request was logged as a warning; later ones only at debug level. */
    private static final Set<UUID> WARNED_PLAYERS = ConcurrentHashMap.newKeySet();

    private AdminAccess() {}

    /** Whether the player may use the admin menu: permission level 2 or the permission node. */
    public static boolean isAdmin(ServerPlayer player) {
        if (player.hasPermissions(2)) return true;
        return hasNode(player);
    }

    /**
     * Whether a command source may use the admin commands: permission level 2 (the server console, a command block, an
     * op) or a player with the permission node.
     */
    public static boolean isAdmin(CommandSourceStack source) {
        if (source.hasPermission(2)) return true;
        ServerPlayer player = source.getPlayer();
        return player != null && hasNode(player);
    }

    private static boolean hasNode(ServerPlayer player) {
        try {
            IServerData<?, ?> serverData = ServerData.from(player.getServer());
            if (serverData == null) return false;
            IPlayerPermissionSystemAPI system = serverData.getPlayerPermissionSystemManager().getUsedSystem();
            return system != null && system.getPermission(player, UsedPermissionNodes.ADMIN_MENU);
        } catch (RuntimeException e) {
            LOGGER.debug("[TeamClaims] Could not check the admin menu permission of {}", player.getGameProfile().getName(), e);
            return false;
        }
    }

    /**
     * Whether an admin request of the player may be answered. A rejected one is logged (the first one of a player as a
     * warning, every later one at debug level, so a client cannot fill the log) and gets no answer.
     *
     * @param what  what was requested, for the log
     */
    public static boolean checkRequest(ServerPlayer player, String what) {
        if (isAdmin(player)) return true;
        if (WARNED_PLAYERS.add(player.getUUID()))
            LOGGER.warn("[TeamClaims] Rejected an admin menu request ({}) of {}: no permission level 2 and no {} permission"
                    + " (further rejections of this player are logged at debug level)", what, player.getGameProfile().getName(),
                    PERMISSION_NODE);
        else
            LOGGER.debug("[TeamClaims] Rejected an admin menu request ({}) of {}", what, player.getGameProfile().getName());
        return false;
    }

    /**
     * Sends the admin menu flag to the client when it is not known there yet (after login) or changed (op level or
     * permission changes). Called from OPAC's player config permission update, which runs on login and regularly
     * while the player is online.
     */
    public static void syncFlag(ServerPlayer player, ServerPlayerData playerData) {
        boolean admin = isAdmin(player);
        if (Objects.equals(playerData.getSyncedAdminMenu(), admin)) return;
        playerData.setSyncedAdminMenu(admin);
        OpenPartiesAndClaims.INSTANCE.getPacketHandler().sendToPlayer(player, new ClientboundAdminFlagPacket(admin));
    }
}
