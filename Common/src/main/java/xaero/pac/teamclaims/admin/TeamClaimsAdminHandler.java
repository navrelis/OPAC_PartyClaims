package xaero.pac.teamclaims.admin;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.common.packet.parties.PartyBudgetSender;
import xaero.pac.common.packet.teamclaims.AdminConfigEntry;
import xaero.pac.common.packet.teamclaims.AdminPlayerBonus;
import xaero.pac.common.packet.teamclaims.AdminTeamData;
import xaero.pac.common.packet.teamclaims.ClientboundAdminConfigPacket;
import xaero.pac.common.packet.teamclaims.ClientboundAdminPlayerPacket;
import xaero.pac.common.packet.teamclaims.ClientboundAdminResultPacket;
import xaero.pac.common.packet.teamclaims.ClientboundAdminResultPacket.Action;
import xaero.pac.common.packet.teamclaims.ClientboundAdminResultPacket.Result;
import xaero.pac.common.packet.teamclaims.ClientboundAdminTeamsPacket;
import xaero.pac.common.packet.teamclaims.ClientboundTeamInfoPacket;
import xaero.pac.common.packet.teamclaims.ServerboundTeamClaimsRequestPacket.Kind;
import xaero.pac.common.server.player.data.ServerPlayerData;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.config.TeamConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server side of the hub screen's Team and Admin packets (105, 107, 110, 112). Every admin request is checked with
 * {@link AdminAccess#checkRequest} first; a rejected one changes nothing and gets no answer. Called on the server
 * thread by OPAC's packet handling.
 */
public final class TeamClaimsAdminHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The fewest server ticks between two handled config snapshot requests of one player. */
    public static final int MIN_TICKS_BETWEEN_CONFIG_REQUESTS = 10;
    /** The size a snapshot page is kept under (one option is never split, so a page can be one large option). */
    public static final int CONFIG_PAGE_BYTES = 30000;

    private static final String TEAM_KEY = "gui.xaero_pac_team_claims_admin_team_";
    private static final AtomicInteger SNAPSHOT_IDS = new AtomicInteger();

    private TeamClaimsAdminHandler() {}

    private static void send(ServerPlayer player, Object packet) {
        OpenPartiesAndClaims.INSTANCE.getPacketHandler().sendToPlayer(player, packet);
    }

    private static void sendResult(ServerPlayer player, Action action, String target, boolean success, String key, List<String> args) {
        send(player, new ClientboundAdminResultPacket(new Result(action, target, success, key, args)));
    }

    // ==================== 105: requests ====================

    public static void handleRequest(ServerPlayer player, Kind kind, String query) {
        switch (kind) {
            case TEAM_INFO -> {
                send(player, new ClientboundTeamInfoPacket(TeamInfoBuilder.build(player)));
                PartyBudgetSender.send(player);
            }
            case ADMIN_CONFIGS -> {
                if (!AdminAccess.checkRequest(player, "server config snapshot")) return;
                MinecraftServer server = player.getServer();
                ServerPlayerData playerData = (ServerPlayerData) ServerPlayerData.from(player);
                long tick = server == null ? 0 : server.getTickCount();
                if (tick - playerData.getLastAdminConfigsRequestTick() < MIN_TICKS_BETWEEN_CONFIG_REQUESTS) {
                    LOGGER.debug("[TeamClaims] Ignored a config snapshot request of {}: too soon after the last one",
                            player.getGameProfile().getName());
                    return;
                }
                playerData.setLastAdminConfigsRequestTick(tick);
                for (ClientboundAdminConfigPacket page : configPages(AdminConfigs.snapshot()))
                    send(player, page);
            }
            case ADMIN_TEAMS -> {
                if (!AdminAccess.checkRequest(player, "team list")) return;
                send(player, new ClientboundAdminTeamsPacket(AdminTeams.list(player.getServer())));
            }
            case ADMIN_PLAYER -> {
                if (!AdminAccess.checkRequest(player, "player lookup")) return;
                GameProfile profile = AdminPlayers.find(player.getServer(), query);
                if (profile == null) {
                    sendResult(player, Action.PLAYER_LOOKUP, query, false, AdminPlayers.KEY + "unknown", List.of(query));
                    return;
                }
                send(player, new ClientboundAdminPlayerPacket(AdminPlayers.summary(player.getServer(), profile)));
            }
        }
    }

    /**
     * A snapshot split into pages of at most about {@link #CONFIG_PAGE_BYTES} bytes and
     * {@link ClientboundAdminConfigPacket#MAX_ENTRIES} options, all with one new snapshot id.
     */
    public static List<ClientboundAdminConfigPacket> configPages(List<AdminConfigEntry> entries) {
        List<List<AdminConfigEntry>> pages = new ArrayList<>();
        List<AdminConfigEntry> page = new ArrayList<>();
        int pageBytes = 0;
        for (AdminConfigEntry entry : entries) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            entry.write(buffer);
            int size = buffer.readableBytes();
            buffer.release();
            if (!page.isEmpty() && (pageBytes + size > CONFIG_PAGE_BYTES || page.size() >= ClientboundAdminConfigPacket.MAX_ENTRIES)) {
                pages.add(page);
                page = new ArrayList<>();
                pageBytes = 0;
            }
            page.add(entry);
            pageBytes += size;
        }
        pages.add(page);//an empty snapshot is one empty page
        int snapshotId = SNAPSHOT_IDS.updateAndGet(id -> id == Integer.MAX_VALUE ? 1 : id + 1);
        List<ClientboundAdminConfigPacket> packets = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++)
            packets.add(new ClientboundAdminConfigPacket(snapshotId, i, pages.size(), false, pages.get(i)));
        return packets;
    }

    // ==================== 107: config option ====================

    public static void handleConfigSet(ServerPlayer player, String configId, String path, String value) {
        if (!AdminAccess.checkRequest(player, "set " + configId + ":" + path)) return;
        AdminConfigs.Outcome outcome = AdminConfigs.set(configId, path, value, player.getGameProfile().getName());
        sendResult(player, Action.CONFIG_SET, configId + ":" + path, outcome.success(), outcome.messageKey(), outcome.args());
        if (outcome.success() && outcome.entry() != null)
            send(player, new ClientboundAdminConfigPacket(0, 0, 1, true, List.of(outcome.entry())));
    }

    // ==================== 110: team bonus ====================

    public static void handleTeamBonusSet(ServerPlayer player, UUID partyId, boolean forceloads, int value) {
        if (!AdminAccess.checkRequest(player, "team bonus of " + partyId)) return;
        Result result = setTeamBonus(player.getServer(), partyId, forceloads, value, player.getGameProfile().getName());
        send(player, new ClientboundAdminResultPacket(result));
        if (result.success())
            send(player, new ClientboundAdminTeamsPacket(AdminTeams.list(player.getServer())));
    }

    /**
     * Sets a per-team bonus ({@link TeamClaimManager#setTeamBonus}) and describes the outcome; shared by the packet and
     * {@code /teamclaims bonus}.
     */
    public static Result setTeamBonus(MinecraftServer server, UUID partyId, boolean forceloads, int value, String actor) {
        String target = partyId.toString();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        if (cm == null)
            return new Result(Action.TEAM_BONUS_SET, target, false, "gui.xaero_pac_team_claims_disabled", List.of());
        if (value < 0 || value > TeamConfig.MAX_BONUS)
            return new Result(Action.TEAM_BONUS_SET, target, false, TEAM_KEY + "bonus_invalid",
                    List.of(String.valueOf(value), String.valueOf(TeamConfig.MAX_BONUS)));
        if (!cm.setTeamBonus(partyId, forceloads, value))
            return new Result(Action.TEAM_BONUS_SET, target, false, TEAM_KEY + "unknown", List.of(target));
        AdminTeamData team = AdminTeams.get(server, partyId);
        String name = team == null ? target : team.name();
        int limit = team == null ? 0 : forceloads ? team.forceloadLimit() : team.claimLimit();
        LOGGER.info("[TeamClaims] {} set the bonus team {} of team '{}' ({}) to {}", actor, forceloads ? "forceloads" : "claims",
                name, partyId, value);
        return new Result(Action.TEAM_BONUS_SET, target, true, TEAM_KEY + (forceloads ? "bonus_forceloads_set" : "bonus_claims_set"),
                List.of(name, String.valueOf(value), String.valueOf(limit)));
    }

    // ==================== 112: player bonus ====================

    public static void handlePlayerBonusSet(ServerPlayer player, UUID playerId, AdminPlayerBonus bonus, int value) {
        if (!AdminAccess.checkRequest(player, "player bonus of " + playerId)) return;
        MinecraftServer server = player.getServer();
        GameProfile profile = AdminPlayers.find(server, playerId.toString());
        if (profile == null) {
            sendResult(player, Action.PLAYER_BONUS_SET, playerId.toString(), false, AdminPlayers.KEY + "unknown",
                    List.of(playerId.toString()));
            return;
        }
        AdminPlayers.Outcome outcome = AdminPlayers.setBonus(server, profile, bonus, value, player.getGameProfile().getName());
        sendResult(player, Action.PLAYER_BONUS_SET, playerId.toString(), outcome.success(), outcome.messageKey(), outcome.args());
        if (outcome.success())
            send(player, new ClientboundAdminPlayerPacket(AdminPlayers.summary(server, profile)));
    }
}
