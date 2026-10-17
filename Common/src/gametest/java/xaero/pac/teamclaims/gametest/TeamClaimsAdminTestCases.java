package xaero.pac.teamclaims.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.ModConfigSpec;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.packet.PacketHandlerFull;
import xaero.pac.common.packet.parties.ClientboundPartyBudgetPacket;
import xaero.pac.common.packet.payload.PacketPayload;
import xaero.pac.common.packet.payload.PacketPayloadCodec;
import xaero.pac.common.packet.teamclaims.*;
import xaero.pac.common.packet.teamclaims.AdminConfigEntry.ValueType;
import xaero.pac.common.packet.teamclaims.ClientboundAdminResultPacket.Action;
import xaero.pac.common.packet.teamclaims.ClientboundAdminResultPacket.Result;
import xaero.pac.common.packet.teamclaims.ServerboundTeamClaimsRequestPacket.Kind;
import xaero.pac.common.packet.type.PacketType;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.common.server.player.data.ServerPlayerData;
import xaero.pac.common.server.player.permission.api.UsedPermissionNodes;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimManager.BudgetInfo;
import xaero.pac.teamclaims.TeamClaimManager.BudgetSettings;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.admin.AdminAccess;
import xaero.pac.teamclaims.admin.AdminConfigs;
import xaero.pac.teamclaims.admin.TeamClaimsAdminHandler;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;
import xaero.pac.teamclaims.config.TeamRole;
import xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.CapturingCommandSource;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases.RecorderFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static xaero.pac.teamclaims.gametest.TeamClaimsBudgetTestCases.cleanupParty;
import static xaero.pac.teamclaims.gametest.TeamClaimsBudgetTestCases.teamSub;
import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.canRun;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.removePlayerQuiet;

/**
 * Dev/test-only, loader-neutral test bodies for the server side of the hub screen's Team and Admin tabs (packets 104
 * to 113): who is an admin, the config snapshot and config changes of both server configs (live and restart-only),
 * the per-team bonus (budget, persistence, over-limit, command), the player bonus and the team info. Same conventions
 * as {@link TeamClaimsPacketTestCases}: a chunk offset of 1000 per test starting at 150000, cleanup on every path, and
 * {@link TeamClaimsPacketTestCases#capture} to watch what is sent to a mock player.
 * <p>
 * Admin rights come from permission level 2: the mock player is put on the op list (level 4) for the test and taken
 * off it again. The permission node path ({@code xaero.pac_admin_menu}) needs a permission system mod, which the test
 * servers do not have; there the node check answers false, so only the op path and the rejection of everybody else are
 * covered here.
 * <p>
 * Every test runs in one go except {@link #restartOnlyOpacOptionGoesToFileOnly}, which waits (at most
 * {@link #WATCHER_WAIT_MILLIS} of real time) for the config file watcher to reload the written file and is registered
 * with {@link #WATCHER_TIMEOUT_TICKS}.
 */
public final class TeamClaimsAdminTestCases {

    /** How long (real time) {@link #restartOnlyOpacOptionGoesToFileOnly} waits for the config file watcher at most. */
    public static final long WATCHER_WAIT_MILLIS = 20_000L;
    /** Timeout {@link #restartOnlyOpacOptionGoesToFileOnly} must be registered with, far beyond the real-time wait. */
    public static final int WATCHER_TIMEOUT_TICKS = 20_000;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final long HOUR = 3_600_000L;
    private static final String OPAC = AdminConfigEntry.CONFIG_OPAC;
    private static final String TC = AdminConfigEntry.CONFIG_TEAM_CLAIMS;
    private static final String KEY = "gui.xaero_pac_team_claims_admin_";

    private TeamClaimsAdminTestCases() {}

    // ==================== Registration and codecs ====================

    /** The ten packets are registered with ids 104 to 113, their codecs, and the handler of the side that receives them. */
    public static void adminPacketsAreRegistered(GameTestHelper helper) {
        Object[][] expected = {
                {104, ClientboundAdminFlagPacket.class, ClientboundAdminFlagPacket.Codec.class, ClientboundAdminFlagPacket.ClientHandler.class, false},
                {105, ServerboundTeamClaimsRequestPacket.class, ServerboundTeamClaimsRequestPacket.Codec.class, ServerboundTeamClaimsRequestPacket.ServerHandler.class, true},
                {106, ClientboundAdminConfigPacket.class, ClientboundAdminConfigPacket.Codec.class, ClientboundAdminConfigPacket.ClientHandler.class, false},
                {107, ServerboundAdminConfigSetPacket.class, ServerboundAdminConfigSetPacket.Codec.class, ServerboundAdminConfigSetPacket.ServerHandler.class, true},
                {108, ClientboundAdminResultPacket.class, ClientboundAdminResultPacket.Codec.class, ClientboundAdminResultPacket.ClientHandler.class, false},
                {109, ClientboundAdminTeamsPacket.class, ClientboundAdminTeamsPacket.Codec.class, ClientboundAdminTeamsPacket.ClientHandler.class, false},
                {110, ServerboundAdminTeamBonusSetPacket.class, ServerboundAdminTeamBonusSetPacket.Codec.class, ServerboundAdminTeamBonusSetPacket.ServerHandler.class, true},
                {111, ClientboundAdminPlayerPacket.class, ClientboundAdminPlayerPacket.Codec.class, ClientboundAdminPlayerPacket.ClientHandler.class, false},
                {112, ServerboundAdminPlayerBonusSetPacket.class, ServerboundAdminPlayerBonusSetPacket.Codec.class, ServerboundAdminPlayerBonusSetPacket.ServerHandler.class, true},
                {113, ClientboundTeamInfoPacket.class, ClientboundTeamInfoPacket.Codec.class, ClientboundTeamInfoPacket.ClientHandler.class, false}};
        PacketHandlerFull handler = packetHandler();
        for (Object[] row : expected) {
            int id = (int) row[0];
            PacketType<?> type = handler.getByIndex(id);
            helper.assertTrue(type != null && type.getType() == row[1], "expected packet " + id + " to be " + row[1] + ", got "
                    + (type == null ? null : type.getType()));
            helper.assertTrue(type.getEncoder().getClass() == row[2] && type.getDecoder().getClass() == row[2],
                    "expected packet " + id + " to use " + row[2]);
            boolean toServer = (boolean) row[4];
            Object registered = toServer ? type.getServerHandler() : type.getClientHandler();
            Object other = toServer ? type.getClientHandler() : type.getServerHandler();
            helper.assertTrue(registered != null && registered.getClass() == row[3] && other == null,
                    "expected packet " + id + " to have only " + row[3]);
        }
        helper.succeed();
    }

    /**
     * Every packet survives its codec with equal content (lists, nulls, negative bonuses, the longest strings), and an
     * empty buffer, a trailing byte and broken values are rejected.
     */
    public static void adminPacketCodecs(GameTestHelper helper) {
        // 104
        ClientboundAdminFlagPacket.Codec flag = new ClientboundAdminFlagPacket.Codec();
        for (boolean allowed : new boolean[]{true, false})
            helper.assertTrue(roundTrip(flag, new ClientboundAdminFlagPacket(allowed)).isAllowed() == allowed, "expected the flag " + allowed);
        assertRejects(helper, flag, new ClientboundAdminFlagPacket(true), "the admin flag");

        // 105
        ServerboundTeamClaimsRequestPacket.Codec request = new ServerboundTeamClaimsRequestPacket.Codec();
        for (Kind kind : Kind.values()) {
            String query = kind == Kind.ADMIN_PLAYER ? "Steve_" + "x".repeat(ServerboundTeamClaimsRequestPacket.MAX_QUERY_LENGTH - 6) : "";
            ServerboundTeamClaimsRequestPacket decoded = roundTrip(request, new ServerboundTeamClaimsRequestPacket(kind, query));
            helper.assertTrue(decoded.getKind() == kind && decoded.getQuery().equals(query), "expected the request " + kind);
        }
        assertRejects(helper, request, new ServerboundTeamClaimsRequestPacket(Kind.ADMIN_TEAMS), "a request");
        FriendlyByteBuf queryOnOtherKind = buffer();
        queryOnOtherKind.writeByte(Kind.ADMIN_CONFIGS.ordinal());
        queryOnOtherKind.writeUtf("x");
        helper.assertTrue(request.apply(queryOnOtherKind) == null, "expected a query on a request without one to be rejected");
        FriendlyByteBuf unknownKind = buffer();
        unknownKind.writeByte(Kind.values().length);
        unknownKind.writeUtf("");
        helper.assertTrue(request.apply(unknownKind) == null, "expected an unknown request kind to be rejected");

        // 106
        AdminConfigEntry intEntry = new AdminConfigEntry(OPAC, "serverConfig.claims.maxPlayerClaims", ValueType.INT, "500", "500",
                "0", String.valueOf(Integer.MAX_VALUE), List.of(), "The maximum number of claims.\nRange: 0 ~ 2147483647", true, "750");
        AdminConfigEntry enumEntry = new AdminConfigEntry(OPAC, "serverConfig.claims.claimsSynchronization", ValueType.ENUM, "ALL",
                "ALL", null, null, List.of("NOT_SYNCED", "OWNED_ONLY", "ALL"), "", true, null);
        AdminConfigEntry stringEntry = new AdminConfigEntry(TC, "a.b", ValueType.STRING, "Ünïcødé ☃", "", null, null, List.of(),
                "c".repeat(AdminConfigEntry.MAX_COMMENT_LENGTH), false, null);
        ClientboundAdminConfigPacket.Codec config = new ClientboundAdminConfigPacket.Codec();
        ClientboundAdminConfigPacket configPacket = roundTrip(config, new ClientboundAdminConfigPacket(42, 1, 3, false,
                List.of(intEntry, enumEntry, stringEntry)));
        helper.assertTrue(configPacket.getSnapshotId() == 42 && configPacket.getPage() == 1 && configPacket.getPageCount() == 3
                        && !configPacket.isUpdate() && configPacket.getEntries().equals(List.of(intEntry, enumEntry, stringEntry)),
                "expected the config page to survive, got " + configPacket.getEntries());
        helper.assertTrue(roundTrip(config, new ClientboundAdminConfigPacket(0, 0, 1, true, List.of())).isUpdate(), "expected an update");
        assertRejects(helper, config, new ClientboundAdminConfigPacket(1, 0, 1, false, List.of(intEntry)), "a config page");
        helper.assertTrue(config.apply(wrap(encode(config, new ClientboundAdminConfigPacket(1, 2, 2, false, List.of())))) == null,
                "expected a page index beyond the page count to be rejected");

        // 107
        ServerboundAdminConfigSetPacket.Codec set = new ServerboundAdminConfigSetPacket.Codec();
        ServerboundAdminConfigSetPacket setPacket = roundTrip(set, new ServerboundAdminConfigSetPacket(TC, "teamClaims.teamClaimsBase", "750"));
        helper.assertTrue(setPacket.getConfigId().equals(TC) && setPacket.getPath().equals("teamClaims.teamClaimsBase")
                && setPacket.getValue().equals("750"), "expected the config set packet to survive");
        assertRejects(helper, set, new ServerboundAdminConfigSetPacket(OPAC, "x", "y"), "a config set");

        // 108
        ClientboundAdminResultPacket.Codec resultCodec = new ClientboundAdminResultPacket.Codec();
        Result result = new Result(Action.PLAYER_BONUS_SET, UUID.randomUUID().toString(), true, KEY + "player_bonus_set",
                List.of("Steve", "playerConfig.claims.bonusChunkClaims", "-5"));
        helper.assertTrue(roundTrip(resultCodec, new ClientboundAdminResultPacket(result)).getResult().equals(result),
                "expected the result to survive");
        assertRejects(helper, resultCodec, new ClientboundAdminResultPacket(result), "a result");

        // 109
        ClientboundAdminTeamsPacket.Codec teamsCodec = new ClientboundAdminTeamsPacket.Codec();
        List<AdminTeamData> teams = List.of(new AdminTeamData(UUID.randomUUID(), "Équipe", 3, 10, 525, 2, 12, 0, 0),
                new AdminTeamData(UUID.randomUUID(), "Solo", 1, 5, 5, 1, 1, 5, 1));
        helper.assertTrue(roundTrip(teamsCodec, new ClientboundAdminTeamsPacket(teams)).getTeams().equals(teams), "expected the teams to survive");
        helper.assertTrue(roundTrip(teamsCodec, new ClientboundAdminTeamsPacket(List.of())).getTeams().isEmpty(), "expected no teams");
        assertRejects(helper, teamsCodec, new ClientboundAdminTeamsPacket(teams), "a team list");
        FriendlyByteBuf negativeTeam = buffer();
        negativeTeam.writeVarInt(1);
        negativeTeam.writeUUID(UUID.randomUUID());
        negativeTeam.writeUtf("x");
        for (int i = 0; i < 7; i++) negativeTeam.writeVarInt(i == 3 ? -1 : 1);
        helper.assertTrue(teamsCodec.apply(negativeTeam) == null, "expected a negative team count to be rejected");

        // 110
        ServerboundAdminTeamBonusSetPacket.Codec teamBonus = new ServerboundAdminTeamBonusSetPacket.Codec();
        UUID partyId = UUID.randomUUID();
        ServerboundAdminTeamBonusSetPacket teamBonusPacket = roundTrip(teamBonus, new ServerboundAdminTeamBonusSetPacket(partyId, true, 17));
        helper.assertTrue(teamBonusPacket.getPartyId().equals(partyId) && teamBonusPacket.isForceloads() && teamBonusPacket.getValue() == 17,
                "expected the team bonus packet to survive");
        assertRejects(helper, teamBonus, teamBonusPacket, "a team bonus set");

        // 111
        ClientboundAdminPlayerPacket.Codec playerCodec = new ClientboundAdminPlayerPacket.Codec();
        AdminPlayerData player = new AdminPlayerData(UUID.randomUUID(), "Alex", true, 500, 10, 3, 16, 25, -2, 1, -4, 120, 3, 2);
        helper.assertTrue(roundTrip(playerCodec, new ClientboundAdminPlayerPacket(player)).getPlayer().equals(player), "expected the player to survive");
        helper.assertTrue(player.bonus(AdminPlayerBonus.ANCHOR_RANGE) == -4 && player.bonus(AdminPlayerBonus.CLAIMS) == 25, "expected bonus()");
        assertRejects(helper, playerCodec, new ClientboundAdminPlayerPacket(player), "a player summary");

        // 112
        ServerboundAdminPlayerBonusSetPacket.Codec playerBonus = new ServerboundAdminPlayerBonusSetPacket.Codec();
        for (AdminPlayerBonus bonus : AdminPlayerBonus.values()) {
            ServerboundAdminPlayerBonusSetPacket decoded = roundTrip(playerBonus, new ServerboundAdminPlayerBonusSetPacket(partyId, bonus, -3));
            helper.assertTrue(decoded.getPlayerId().equals(partyId) && decoded.getBonus() == bonus && decoded.getValue() == -3,
                    "expected the player bonus packet of " + bonus + " to survive");
        }
        assertRejects(helper, playerBonus, new ServerboundAdminPlayerBonusSetPacket(partyId, AdminPlayerBonus.CLAIMS, 1), "a player bonus set");
        byte[] badBonus = encode(playerBonus, new ServerboundAdminPlayerBonusSetPacket(partyId, AdminPlayerBonus.CLAIMS, 1));
        badBonus[16] = (byte) AdminPlayerBonus.values().length;
        helper.assertTrue(playerBonus.apply(wrap(badBonus)) == null, "expected an unknown bonus to be rejected");

        // 113
        ClientboundTeamInfoPacket.Codec infoCodec = new ClientboundTeamInfoPacket.Codec();
        TeamInfoData info = new TeamInfoData(121, true, partyId, "Équipe", "team_equipe", 3, 4, TeamRole.CLAIMER, TeamRole.MODERATOR,
                TeamRole.OWNER, false, true, 5, 2, 0xABCDEF, "Équipe claims");
        helper.assertTrue(roundTrip(infoCodec, new ClientboundTeamInfoPacket(info)).getInfo().equals(info), "expected the team info to survive");
        TeamInfoData unavailable = roundTrip(infoCodec, new ClientboundTeamInfoPacket(TeamInfoData.unavailable(64))).getInfo();
        helper.assertTrue(!unavailable.available() && unavailable.maxClaimActionSize() == 64, "expected an unavailable team info with the action size");
        assertRejects(helper, infoCodec, new ClientboundTeamInfoPacket(info), "a team info");
        TeamInfoData badIndex = new TeamInfoData(1, true, partyId, "x", "team_x", -2, 1, TeamRole.MEMBER, TeamRole.MEMBER,
                TeamRole.MEMBER, true, false, 0, 0, 0, "x");
        helper.assertTrue(infoCodec.apply(wrap(encode(infoCodec, new ClientboundTeamInfoPacket(badIndex)))) == null,
                "expected a sub-config index below -1 to be rejected");
        helper.succeed();
    }

    // ==================== Admin permission ====================

    /**
     * A player without permission level 2 is no admin, the same player on the op list is one (also as a command
     * source), and the flag goes to the client when it changes, not again while it stays. A level 2 command source
     * without a player is an admin, a level 0 one is not.
     */
    public static void isAdminFollowsOpLevel(GameTestHelper helper, RecorderFactory recorders) {
        try (Admin a = new Admin(helper, recorders, false)) {
            ServerPlayer player = a.player;
            ServerPlayerData data = (ServerPlayerData) ServerPlayerData.from(player);
            helper.assertTrue(UsedPermissionNodes.ALL.get(AdminAccess.PERMISSION_NODE) == UsedPermissionNodes.ADMIN_MENU,
                    "expected the admin menu node among OPAC's used permission nodes");
            helper.assertTrue(!AdminAccess.isAdmin(player), "expected a player without permission level 2 to be no admin");
            a.capture(() -> AdminAccess.syncFlag(player, data));//whatever the state was, it is synced now
            helper.assertTrue(a.capture(() -> AdminAccess.syncFlag(player, data)).isEmpty(), "expected no flag while nothing changed");

            a.setOp(true);
            helper.assertTrue(AdminAccess.isAdmin(player), "expected an op to be an admin");
            helper.assertTrue(AdminAccess.isAdmin(player.createCommandSourceStack()), "expected the command source of an op to be an admin");
            List<Object> sent = a.capture(() -> AdminAccess.syncFlag(player, data));
            helper.assertTrue(sent.size() == 1 && sent.get(0) instanceof ClientboundAdminFlagPacket flag && flag.isAllowed(),
                    "expected the flag 'allowed' after becoming an op, got " + sent);
            helper.assertTrue(a.capture(() -> AdminAccess.syncFlag(player, data)).isEmpty(), "expected the flag only once");

            a.setOp(false);
            helper.assertTrue(!AdminAccess.isAdmin(player), "expected the player to be no admin after being taken off the op list");
            sent = a.capture(() -> AdminAccess.syncFlag(player, data));
            helper.assertTrue(sent.size() == 1 && sent.get(0) instanceof ClientboundAdminFlagPacket flag && !flag.isAllowed(),
                    "expected the flag 'not allowed' after losing op, got " + sent);

            CapturingCommandSource capture = new CapturingCommandSource();
            helper.assertTrue(AdminAccess.isAdmin(TeamClaimsHardeningTestCases.commandSource(helper, capture)),
                    "expected a level 4 source without a player to be an admin");
            helper.assertTrue(!AdminAccess.isAdmin(TeamClaimsHardeningTestCases.commandSource(helper, capture).withPermission(0)),
                    "expected a level 0 source without a player to be no admin");
            helper.succeed();
        }
    }

    // ==================== Config snapshot and changes ====================

    /**
     * The snapshot an admin gets has every scalar option of both configs and no list option (checked against the
     * fields of both config classes), with the right type, default, range, allowed values and restart flag; its pages
     * survive the codec. A second request in the same tick and the request of a non-admin get nothing.
     */
    public static void configSnapshotHasBothSpecs(GameTestHelper helper, RecorderFactory recorders) {
        try (Admin a = new Admin(helper, recorders, true); Admin other = new Admin(helper, recorders, false)) {
            List<Object> sent = a.send(new ServerboundTeamClaimsRequestPacket(Kind.ADMIN_CONFIGS));
            helper.assertTrue(!sent.isEmpty() && sent.stream().allMatch(ClientboundAdminConfigPacket.class::isInstance),
                    "expected only config pages, got " + describe(sent));
            Map<String, AdminConfigEntry> entries = new HashMap<>();
            ClientboundAdminConfigPacket.Codec codec = new ClientboundAdminConfigPacket.Codec();
            int snapshotId = ((ClientboundAdminConfigPacket) sent.get(0)).getSnapshotId();
            for (int i = 0; i < sent.size(); i++) {
                ClientboundAdminConfigPacket page = (ClientboundAdminConfigPacket) sent.get(i);
                helper.assertTrue(page.getSnapshotId() == snapshotId && page.getPage() == i && page.getPageCount() == sent.size()
                        && !page.isUpdate(), "expected page " + i + " of " + sent.size() + " of one snapshot");
                byte[] bytes = encode(codec, page);
                helper.assertTrue(bytes.length <= TeamClaimsAdminHandler.CONFIG_PAGE_BYTES + 4096,
                        "expected page " + i + " to stay near the page size, got " + bytes.length + " bytes");
                ClientboundAdminConfigPacket decoded = codec.apply(wrap(bytes));
                helper.assertTrue(decoded != null && decoded.getEntries().equals(page.getEntries()), "expected page " + i + " to survive the codec");
                for (AdminConfigEntry entry : page.getEntries())
                    helper.assertTrue(entries.put(entry.key(), entry) == null, "expected " + entry.key() + " only once");
            }
            assertMatchesConfigClass(helper, entries, OPAC, ServerConfig.CONFIG, ServerConfig.SPEC);
            assertMatchesConfigClass(helper, entries, TC, TeamClaimsServerConfig.CONFIG, TeamClaimsServerConfig.SPEC);

            assertEntry(helper, entries.get(OPAC + ":serverConfig.claims.maxPlayerClaims"), ValueType.INT, "500", "0",
                    String.valueOf(Integer.MAX_VALUE), true);
            assertEntry(helper, entries.get(TC + ":teamClaims.teamClaimsBase"), ValueType.INT, "500", "0",
                    String.valueOf(TeamClaimsServerConfig.MAX_TEAM_BUDGET_VALUE), false);
            assertEntry(helper, entries.get(TC + ":teamClaims.enabled"), ValueType.BOOLEAN, "true", null, null, true);
            assertEntry(helper, entries.get(TC + ":teamClaims.ftbTeamsSync"), ValueType.BOOLEAN, "true", null, null, true);
            assertEntry(helper, entries.get(TC + ":teamClaims.overLimitEnforcement"), ValueType.BOOLEAN, "true", null, null, false);
            assertEntry(helper, entries.get(OPAC + ":serverConfig.defaultLanguage"), ValueType.STRING, "en_us", null, null, true);
            assertEntry(helper, entries.get(OPAC + ":serverConfig.claims.claimingAnchorBonusPerPartyMember"), ValueType.DOUBLE, "0.1",
                    "0.0", String.valueOf(Double.MAX_VALUE), true);
            AdminConfigEntry sync = entries.get(OPAC + ":serverConfig.claims.claimsSynchronization");
            assertEntry(helper, sync, ValueType.ENUM, "ALL", null, null, true);
            helper.assertTrue(sync.allowedValues().containsAll(List.of("NOT_SYNCED", "OWNED_ONLY", "ALL"))
                    && sync.allowedValues().contains(sync.value()), "expected the allowed values of the enum, got " + sync.allowedValues());
            helper.assertTrue(entries.get(OPAC + ":serverConfig.claims.maxPlayerClaims").comment().contains("claim"),
                    "expected the comment of maxPlayerClaims");
            helper.assertTrue(!entries.containsKey(OPAC + ":serverConfig.claims.claimableDimensionsList"),
                    "expected no list option");
            helper.assertTrue(entries.values().stream().noneMatch(entry -> entry.restart() && entry.pendingValue() != null
                            && entry.pendingValue().equals(entry.value())), "expected no pending value equal to the running one");

            helper.assertTrue(a.send(new ServerboundTeamClaimsRequestPacket(Kind.ADMIN_CONFIGS)).isEmpty(),
                    "expected a second snapshot request in the same tick to get nothing");
            helper.assertTrue(other.send(new ServerboundTeamClaimsRequestPacket(Kind.ADMIN_CONFIGS)).isEmpty(),
                    "expected the snapshot request of a non-admin to get nothing");
            helper.succeed();
        }
    }

    /**
     * A live Team Claims option set through the packet applies at once (the next {@code get()} returns it, also after
     * the caches are reset the way a reload of the file does it) and is in the toml; the answer is a success and the
     * updated entry. Set back the same way in the same tick, so no other test sees it.
     */
    public static void liveTeamClaimsOptionAppliesAndIsSaved(GameTestHelper helper, RecorderFactory recorders) {
        ModConfigSpec.IntValue option = TeamClaimsServerConfig.CONFIG.convertMaxRadius;
        String path = "teamClaims.convertMaxRadius";
        int original = option.get();
        int changed = original == 7 ? 8 : 7;
        try (Admin a = new Admin(helper, recorders, true)) {
            List<Object> sent = a.send(new ServerboundAdminConfigSetPacket(TC, path, String.valueOf(changed)));
            Result result = result(helper, sent, Action.CONFIG_SET);
            helper.assertTrue(result.success() && result.messageKey().equals(KEY + "config_saved") && result.target().equals(TC + ":" + path),
                    "expected the change to be confirmed, got " + result);
            AdminConfigEntry update = update(helper, sent);
            helper.assertTrue(update.path().equals(path) && update.value().equals(String.valueOf(changed)) && !update.restart()
                    && update.pendingValue() == null, "expected the updated live entry, got " + update);
            helper.assertTrue(option.get() == changed, "expected the live option to apply at once, got " + option.get());
            TeamClaimsServerConfig.SPEC.afterReload();
            helper.assertTrue(option.get() == changed, "expected the live option to stay after a reload, got " + option.get());
            helper.assertTrue(fileValue(helper, TeamClaimsServerConfig.FILE_NAME, "convertMaxRadius").equals(String.valueOf(changed)),
                    "expected the new value in the toml");
            helper.succeed();
        } finally {
            AdminConfigs.set(TC, path, String.valueOf(original), "gametest");
            helper.assertTrue(option.get() == original, "expected the option to be set back");
        }
    }

    /**
     * A restart-only OPAC option set through the packet goes to the toml only: {@code get()} keeps the running value
     * right away, after the caches are reset like a reload of the file does it, and after the config file watcher of the
     * loader really reloaded the written file (seen as a new loaded config object of the spec; the test waits for it
     * for up to {@link #WATCHER_WAIT_MILLIS}). The entry shows the saved value as pending. Set back the same way, after
     * which nothing is pending.
     */
    public static void restartOnlyOpacOptionGoesToFileOnly(GameTestHelper helper, RecorderFactory recorders) {
        ModConfigSpec.IntValue option = ServerConfig.CONFIG.maxPartyInvites;
        String path = "serverConfig.parties.maxPartyInvites";
        int original = option.get();
        int changed = original + 3;
        Admin a = new Admin(helper, recorders, true);
        boolean[] restored = {false};
        Runnable restore = () -> {
            if (restored[0]) return;
            restored[0] = true;
            AdminConfigs.set(OPAC, path, String.valueOf(original), "gametest");
            a.close();
        };
        Object loadedBefore = loadedConfigOf(ServerConfig.SPEC);
        long start = System.currentTimeMillis();
        try {
            List<Object> sent = a.send(new ServerboundAdminConfigSetPacket(OPAC, path, String.valueOf(changed)));
            Result result = result(helper, sent, Action.CONFIG_SET);
            helper.assertTrue(result.success() && result.messageKey().equals(KEY + "config_saved_restart"),
                    "expected the restart-only change to be confirmed as such, got " + result);
            AdminConfigEntry update = update(helper, sent);
            helper.assertTrue(update.restart() && update.value().equals(String.valueOf(original))
                    && String.valueOf(changed).equals(update.pendingValue()), "expected the running value and the pending one, got " + update);
            helper.assertTrue(option.get() == original, "expected the running value to stay, got " + option.get());
            helper.assertTrue(fileValue(helper, "openpartiesandclaims-server.toml", "maxPartyInvites").equals(String.valueOf(changed)),
                    "expected the new value in the toml");
            ServerConfig.SPEC.afterReload();
            helper.assertTrue(option.get() == original, "expected the running value to stay after a reload, got " + option.get());
        } catch (RuntimeException | Error e) {
            restore.run();
            throw e;
        }
        waitForReload(helper, loadedBefore, start, () -> {
            try {
                helper.assertTrue(loadedConfigOf(ServerConfig.SPEC) != loadedBefore,
                        "expected the config file watcher to reload the written file within " + WATCHER_WAIT_MILLIS + " ms");
                LOGGER.info("[TeamClaims gametest] the config file watcher reloaded the OPAC server config {} ms after the write",
                        System.currentTimeMillis() - start);
                helper.assertTrue(option.get() == original, "expected the running value to stay after the file watcher reloaded the file, got "
                        + option.get());
                AdminConfigEntry entry = AdminConfigs.entry(OPAC, path);
                helper.assertTrue(entry != null && String.valueOf(changed).equals(entry.pendingValue()),
                        "expected the value to stay pending, got " + entry);
                restore.run();
                helper.assertTrue(option.get() == original, "expected the running value after setting it back");
                AdminConfigEntry back = AdminConfigs.entry(OPAC, path);
                helper.assertTrue(back != null && back.pendingValue() == null, "expected nothing pending after setting it back, got " + back);
                helper.assertTrue(fileValue(helper, "openpartiesandclaims-server.toml", "maxPartyInvites").equals(String.valueOf(original)),
                        "expected the original value in the toml again");
                helper.succeed();
            } finally {
                restore.run();
            }
        });
    }

    /**
     * Rejected changes change nothing: an admin gets a failure for a value out of range, of the wrong type, an unknown
     * option or config and a list option; a non-admin gets no answer at all to a config change, a team or player bonus
     * change or any admin request, while the team info request is open to everybody.
     */
    public static void invalidAndUnauthorizedRequestsChangeNothing(GameTestHelper helper, RecorderFactory recorders) {
        int radius = TeamClaimsServerConfig.CONFIG.convertMaxRadius.get();
        String path = "teamClaims.convertMaxRadius";
        try (Admin a = new Admin(helper, recorders, true); Admin user = new Admin(helper, recorders, false)) {
            Object[][] cases = {
                    {TC, path, "99", "config_out_of_range"},
                    {TC, path, "-1", "config_out_of_range"},
                    {TC, path, "abc", "config_invalid_value"},
                    {TC, path, "2.5", "config_invalid_value"},
                    {TC, "teamClaims.enabled", "maybe", "config_invalid_value"},
                    {OPAC, "serverConfig.claims.claimsSynchronization", "SOMETIMES", "config_invalid_value"},
                    {TC, "teamClaims.noSuchOption", "1", "config_unknown"},
                    {"nosuchconfig", path, "1", "config_unknown"},
                    {OPAC, "serverConfig.claims.claimableDimensionsList", "[]", "config_unknown"},
                    {OPAC, "serverConfig.claims", "1", "config_unknown"},
                    {OPAC, "", "1", "config_unknown"}};
            for (Object[] c : cases) {
                Result result = result(helper, a.send(new ServerboundAdminConfigSetPacket((String) c[0], (String) c[1], (String) c[2])), Action.CONFIG_SET);
                helper.assertTrue(!result.success() && result.messageKey().equals(KEY + c[3]),
                        "expected " + c[0] + ":" + c[1] + " = " + c[2] + " to fail with " + c[3] + ", got " + result);
            }
            helper.assertTrue(TeamClaimsServerConfig.CONFIG.convertMaxRadius.get() == radius, "expected no change by the rejected values");

            helper.assertTrue(user.send(new ServerboundAdminConfigSetPacket(TC, path, String.valueOf(radius == 3 ? 2 : 3))).isEmpty(),
                    "expected no answer to the config change of a non-admin");
            helper.assertTrue(TeamClaimsServerConfig.CONFIG.convertMaxRadius.get() == radius, "expected no change by a non-admin");
            for (Kind kind : List.of(Kind.ADMIN_CONFIGS, Kind.ADMIN_TEAMS, Kind.ADMIN_PLAYER))
                helper.assertTrue(user.send(new ServerboundTeamClaimsRequestPacket(kind, kind == Kind.ADMIN_PLAYER ? a.player.getStringUUID() : ""))
                        .isEmpty(), "expected no answer to the " + kind + " request of a non-admin");
            helper.assertTrue(!user.send(new ServerboundTeamClaimsRequestPacket(Kind.TEAM_INFO)).isEmpty(),
                    "expected an answer to the team info request of anybody");

            GameProfile owner = profile("T152_Owner");
            IServerPartyAPI party = createPartyWithTeam(a.server, owner, List.of());
            try {
                helper.assertTrue(user.send(new ServerboundAdminTeamBonusSetPacket(party.getId(), false, 50)).isEmpty(),
                        "expected no answer to the team bonus change of a non-admin");
                helper.assertTrue(TeamClaimsCommon.getClaimManager().teamBonus(party.getId(), false) == 0, "expected no team bonus");
                Result negative = result(helper, a.send(new ServerboundAdminTeamBonusSetPacket(party.getId(), false, -1)), Action.TEAM_BONUS_SET);
                helper.assertTrue(!negative.success() && negative.messageKey().equals(KEY + "team_bonus_invalid"),
                        "expected a negative team bonus to be refused, got " + negative);
                Result unknown = result(helper, a.send(new ServerboundAdminTeamBonusSetPacket(UUID.randomUUID(), false, 1)), Action.TEAM_BONUS_SET);
                helper.assertTrue(!unknown.success() && unknown.messageKey().equals(KEY + "team_unknown"),
                        "expected the bonus of an unknown team to be refused, got " + unknown);
            } finally {
                cleanupParty(a.server, party);
            }

            UUID target = a.player.getUUID();
            helper.assertTrue(user.send(new ServerboundAdminPlayerBonusSetPacket(target, AdminPlayerBonus.CLAIMS, 99)).isEmpty(),
                    "expected no answer to the player bonus change of a non-admin");
            helper.assertTrue(bonusOf(a.server, target, AdminPlayerBonus.CLAIMS) == 0, "expected no player bonus");
            Result tooLarge = result(helper, a.send(new ServerboundAdminPlayerBonusSetPacket(target, AdminPlayerBonus.CLAIMS,
                    AdminPlayerBonus.MAX_ABS_VALUE + 1)), Action.PLAYER_BONUS_SET);
            helper.assertTrue(!tooLarge.success() && bonusOf(a.server, target, AdminPlayerBonus.CLAIMS) == 0,
                    "expected a too large player bonus to be refused, got " + tooLarge);
            Result unknownPlayer = result(helper, a.send(new ServerboundAdminPlayerBonusSetPacket(UUID.randomUUID(), AdminPlayerBonus.CLAIMS, 1)),
                    Action.PLAYER_BONUS_SET);
            helper.assertTrue(!unknownPlayer.success() && unknownPlayer.messageKey().equals(KEY + "player_unknown"),
                    "expected the bonus of an unknown player to be refused, got " + unknownPlayer);
            helper.succeed();
        }
    }

    // ==================== Per-team bonus ====================

    /**
     * The per-team bonus adds to the team limits, also for a team below {@code teamClaimsMinMembers} (limit 0 without
     * it): set through the packet by an admin, two bonus team claims allow exactly two team claims, one bonus team
     * forceload exactly one team forceload; the answer is a success and the team list with the new limits.
     */
    public static void teamBonusRaisesLimitsBelowMinMembers(GameTestHelper helper, RecorderFactory recorders) {
        GameProfile owner = profile("T153_Owner");
        UUID ownerId = owner.getId();
        int x0 = 153000;
        IServerPartyAPI party = null;
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        try (Admin a = new Admin(helper, recorders, true)) {
            party = createPartyWithTeam(a.server, owner, List.of());
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(2, 5, 1, 2, 1, 168));
            int sub = teamSub(a.server, party, ownerId);
            assertClaim(helper, doClaim(a.server, ownerId, sub, x0, 0), ClaimResult.Type.CLAIM_LIMIT_REACHED, "a team claim of a one-member team");

            List<Object> sent = a.send(new ServerboundAdminTeamBonusSetPacket(partyId, false, 2));
            Result result = result(helper, sent, Action.TEAM_BONUS_SET);
            helper.assertTrue(result.success() && result.messageKey().equals(KEY + "team_bonus_claims_set")
                    && result.args().size() == 3 && result.args().get(1).equals("2") && result.args().get(2).equals("2"),
                    "expected the bonus to be confirmed with the new limit 2, got " + result);
            AdminTeamData team = teamOf(helper, sent, partyId);
            helper.assertTrue(team.bonusClaims() == 2 && team.claimLimit() == 2 && team.forceloadLimit() == 0 && team.memberCount() == 1,
                    "expected the team list with the bonus, got " + team);
            assertClaim(helper, doClaim(a.server, ownerId, sub, x0, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "the 1st bonus team claim");
            assertClaim(helper, doClaim(a.server, ownerId, sub, x0 + 1, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "the 2nd bonus team claim");
            assertClaim(helper, doClaim(a.server, ownerId, sub, x0 + 2, 0), ClaimResult.Type.CLAIM_LIMIT_REACHED, "a 3rd team claim");
            assertClaim(helper, doForceload(a.server, ownerId, x0, 0, true), ClaimResult.Type.FORCELOAD_LIMIT_REACHED,
                    "a team forceload without forceload bonus");

            result = result(helper, a.send(new ServerboundAdminTeamBonusSetPacket(partyId, true, 1)), Action.TEAM_BONUS_SET);
            helper.assertTrue(result.success() && result.messageKey().equals(KEY + "team_bonus_forceloads_set"),
                    "expected the forceload bonus to be confirmed, got " + result);
            assertClaim(helper, doForceload(a.server, ownerId, x0, 0, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD, "the bonus team forceload");
            assertClaim(helper, doForceload(a.server, ownerId, x0 + 1, 0, true), ClaimResult.Type.FORCELOAD_LIMIT_REACHED, "a 2nd team forceload");
            BudgetInfo budget = cm.getBudgetInfo(ownerId);
            helper.assertTrue(budget.teamClaims() == 2 && budget.teamClaimLimit() == 2 && budget.teamForceloads() == 1
                    && budget.teamForceloadLimit() == 1 && budget.claimsOverLimit() == 0,
                    "expected the budget with the bonus, got " + budget);
            helper.succeed();
        } finally {
            if (party != null) cm.deactivateTeamForceLoads(party.getId());
            unclaimQuiet(helper.getLevel().getServer(), x0, 0, x0 + 1, 0, x0 + 2, 0);
            cleanupParty(helper.getLevel().getServer(), party);
        }
    }

    /**
     * The bonus is stored in the team JSON and read back; a JSON of 1.3.0 without the fields loads with 0 (and is
     * written without them while they are 0), invalid values load as 0, and values outside {@code [0, MAX_BONUS]} are
     * refused when set.
     */
    public static void teamBonusIsSavedAndOldJsonLoads(GameTestHelper helper) {
        UUID partyId = UUID.randomUUID();
        TeamConfig config = new TeamConfig(partyId, "Bonus Team", "team_bonus_team");
        helper.assertTrue(config.getBonusClaims() == 0 && config.getBonusForceloads() == 0, "expected no bonus on a new team");
        JsonObject plain = config.toJson();
        helper.assertTrue(!plain.has("bonusClaims") && !plain.has("bonusForceloads"), "expected no bonus fields while they are 0");
        helper.assertTrue(config.setBonus(false, 40) && config.setBonus(true, 3) && !config.setBonus(true, 3), "expected the bonus to change once");
        TeamConfig reread = TeamConfig.fromJson(JsonParser.parseString(config.toJsonString()).getAsJsonObject());
        helper.assertTrue(reread.getBonusClaims() == 40 && reread.getBonusForceloads() == 3, "expected the bonus to survive the JSON, got "
                + reread.getBonusClaims() + " / " + reread.getBonusForceloads());

        String old = "{\"partyId\":\"" + partyId + "\",\"teamName\":\"Old Team\",\"subConfigId\":\"team_old_team\",\"members\":[],"
                + "\"settings\":{},\"roles\":{\"claim\":\"MEMBER\",\"unclaim\":\"MEMBER\",\"forceload\":\"MEMBER\"}}";
        TeamConfig oldConfig = TeamConfig.fromJson(JsonParser.parseString(old).getAsJsonObject());
        helper.assertTrue(oldConfig.getBonusClaims() == 0 && oldConfig.getBonusForceloads() == 0, "expected a 1.3.0 team JSON to load with no bonus");
        JsonObject broken = JsonParser.parseString(old).getAsJsonObject();
        broken.addProperty("bonusClaims", -5);
        broken.addProperty("bonusForceloads", "lots");
        TeamConfig brokenConfig = TeamConfig.fromJson(broken);
        helper.assertTrue(brokenConfig.getBonusClaims() == 0 && brokenConfig.getBonusForceloads() == 0, "expected invalid bonus values to load as 0");

        for (int bad : new int[]{-1, TeamConfig.MAX_BONUS + 1}) {
            boolean refused = false;
            try {
                config.setBonus(false, bad);
            } catch (IllegalArgumentException e) {
                refused = true;
            }
            helper.assertTrue(refused && config.getBonusClaims() == 40, "expected the bonus " + bad + " to be refused");
        }
        helper.succeed();
    }

    /**
     * Lowering the bonus below what a team uses puts it above its limit, and the over-limit rule applies as after a
     * member left: a deadline {@code overLimitGraceHours} from now, the excess counted; raising it again cancels it.
     */
    public static void loweringTeamBonusStartsOverLimit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        GameProfile owner = profile("T155_Owner");
        UUID ownerId = owner.getId();
        int x0 = 155000;
        long[] now = {System.currentTimeMillis()};
        IServerPartyAPI party = null;
        try {
            party = createPartyWithTeam(server, owner, List.of());
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(2, 5, 1, 2, 1, 48));
            cm.setClock(() -> now[0]);
            helper.assertTrue(cm.setTeamBonus(partyId, false, 3), "expected the bonus to be set");
            int sub = teamSub(server, party, ownerId);
            for (int i = 0; i < 3; i++)
                assertClaim(helper, doClaim(server, ownerId, sub, x0 + i, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "bonus team claim " + (i + 1));

            helper.assertTrue(cm.setTeamBonus(partyId, false, 1), "expected the lower bonus to be set");
            BudgetInfo budget = cm.getBudgetInfo(ownerId);
            helper.assertTrue(budget.teamClaims() == 3 && budget.teamClaimLimit() == 1 && budget.claimsOverLimit() == 2
                            && budget.overLimitDeadline() == now[0] + 48 * HOUR,
                    "expected 3 / 1 team claims with a deadline 48 hours from now, got " + budget);
            helper.assertTrue(claimsAPI(server).get(OVERWORLD, x0 + 2, 0) != null, "expected nothing to be unclaimed before the deadline");

            helper.assertTrue(cm.setTeamBonus(partyId, false, 3), "expected the bonus to be raised again");
            budget = cm.getBudgetInfo(ownerId);
            helper.assertTrue(budget.claimsOverLimit() == 0 && budget.overLimitDeadline() == 0,
                    "expected the deadline to be cancelled, got " + budget);
            helper.succeed();
        } finally {
            cm.setClock(null);
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0);
            cleanupParty(server, party);
        }
    }

    /**
     * {@code /teamclaims bonus <team> claims|forceloads <n>} sets the bonus of the team named or of the party id for
     * permission level 2, and is not available at level 0; {@code /teamclaims bonus <team>} shows it.
     */
    public static void bonusCommandNeedsAdmin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        String name = "T156 Bonus " + Integer.toHexString(UUID.randomUUID().hashCode());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        IServerPartyAPI party = null;
        try {
            party = createPartyWithTeam(server, profile("T156_Owner"), List.of());
            UUID partyId = party.getId();
            TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
            tcm.onTeamNameChanged(partyId, name);
            helper.assertTrue(tcm.getTeamConfig(partyId).getTeamName().equals(name), "test setup: expected the team name");
            String byName = "teamclaims bonus \"" + name + "\"";
            String byId = "teamclaims bonus " + partyId;
            helper.assertTrue(!canRun(server, player, 0, byName + " claims 4"), "expected the command to be unavailable at level 0");
            helper.assertTrue(canRun(server, player, 2, byName + " claims 4"), "expected the command to be available at level 2");

            CapturingCommandSource capture = TeamClaimsOverviewTestCases.run(server, player, 0, byName + " claims 4");
            helper.assertTrue(cm.teamBonus(partyId, false) == 0, "expected no change by a level 0 player, got " + capture.all());
            TeamClaimsOverviewTestCases.run(server, player, 2, byName + " claims 4");
            TeamClaimsOverviewTestCases.run(server, player, 2, byId + " forceloads 2");
            helper.assertTrue(cm.teamBonus(partyId, false) == 4 && cm.teamBonus(partyId, true) == 2,
                    "expected the bonus 4 / 2, got " + cm.teamBonus(partyId, false) + " / " + cm.teamBonus(partyId, true));
            capture = TeamClaimsOverviewTestCases.run(server, player, 2, byId);
            helper.assertTrue(capture.messages().size() == 1 && capture.received(name), "expected one line about the team, got " + capture.all());
            capture = TeamClaimsOverviewTestCases.run(server, player, 2, "teamclaims bonus \"No such team " + partyId + "\" claims 1");
            helper.assertTrue(cm.teamBonus(partyId, false) == 4 && capture.messages().size() == 1, "expected an unknown team to change nothing");
            helper.succeed();
        } finally {
            removePlayerQuiet(server, player);
            cleanupParty(server, party);
        }
    }

    // ==================== Players ====================

    /**
     * An admin looks a player up by UUID and gets their summary (OPAC's base limits, the four bonus values, the claim
     * count); setting a bonus through the packet goes through OPAC's player config, so OPAC's full limit of the player
     * changes by it, and the answer is a success and the fresh summary. An unknown name gets a failure.
     */
    public static void playerBonusChangesPlayerLimit(GameTestHelper helper, RecorderFactory recorders) {
        try (Admin a = new Admin(helper, recorders, true); Admin target = new Admin(helper, recorders, false)) {
            UUID targetId = target.player.getUUID();
            List<Object> sent = a.send(new ServerboundTeamClaimsRequestPacket(Kind.ADMIN_PLAYER, targetId.toString()));
            helper.assertTrue(sent.size() == 1 && sent.get(0) instanceof ClientboundAdminPlayerPacket, "expected one player summary, got " + describe(sent));
            AdminPlayerData before = ((ClientboundAdminPlayerPacket) sent.get(0)).getPlayer();
            int baseClaims = claimsAPI(a.server).getPlayerBaseClaimLimit(target.player);
            helper.assertTrue(before.playerId().equals(targetId) && before.online() && before.baseClaimLimit() == baseClaims
                            && before.bonusClaims() == 0 && before.bonusAnchorRange() == 0 && before.claims() == 0,
                    "expected the summary of the target, got " + before);
            int fullBefore = claimsAPI(a.server).getPlayerFullClaimLimit(targetId);

            sent = a.send(new ServerboundAdminPlayerBonusSetPacket(targetId, AdminPlayerBonus.CLAIMS, 7));
            Result result = result(helper, sent, Action.PLAYER_BONUS_SET);
            helper.assertTrue(result.success() && result.messageKey().equals(KEY + "player_bonus_set"), "expected the bonus to be confirmed, got " + result);
            AdminPlayerData after = sent.stream().filter(ClientboundAdminPlayerPacket.class::isInstance)
                    .map(p -> ((ClientboundAdminPlayerPacket) p).getPlayer()).findFirst().orElse(null);
            helper.assertTrue(after != null && after.bonusClaims() == 7, "expected the fresh summary with the bonus, got " + after);
            helper.assertTrue(claimsAPI(a.server).getPlayerFullClaimLimit(targetId) == fullBefore + 7,
                    "expected OPAC's claim limit of the player to grow by 7, got " + claimsAPI(a.server).getPlayerFullClaimLimit(targetId));
            helper.assertTrue(bonusOf(a.server, targetId, AdminPlayerBonus.CLAIMS) == 7, "expected the bonus in the player config");

            result = result(helper, a.send(new ServerboundAdminPlayerBonusSetPacket(targetId, AdminPlayerBonus.ANCHOR_RANGE, -2)), Action.PLAYER_BONUS_SET);
            helper.assertTrue(result.success() && bonusOf(a.server, targetId, AdminPlayerBonus.ANCHOR_RANGE) == -2,
                    "expected a negative anchor range bonus, got " + result);

            Result unknown = result(helper, a.send(new ServerboundTeamClaimsRequestPacket(Kind.ADMIN_PLAYER, "T157_Nobody_" + UUID.randomUUID().toString().substring(0, 8))),
                    Action.PLAYER_LOOKUP);
            helper.assertTrue(!unknown.success() && unknown.messageKey().equals(KEY + "player_unknown"), "expected an unknown player, got " + unknown);
            helper.succeed();
        }
    }

    // ==================== Team info ====================

    /**
     * The team info of the owner and of a member of a team: name, ids, member count, roles, own territory messages,
     * the bonus and the claim colour and name of the team sub-config; only the owner may edit. It comes with the
     * budget. A player in no team gets an unavailable one; everybody gets the server's largest claim action.
     */
    public static void teamInfoForOwnerAndMember(GameTestHelper helper, RecorderFactory recorders) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        IServerPartyAPI party = null;
        try (Admin owner = new Admin(helper, recorders, false); Admin member = new Admin(helper, recorders, false);
             Admin loner = new Admin(helper, recorders, false)) {
            party = createPartyWithTeam(server, owner.player.getGameProfile(), List.of(member.player.getGameProfile()));
            cm.setTeamBonus(party.getId(), false, 6);
            TeamConfig config = tcm.getTeamConfig(party.getId());
            int maxAction = ServerConfig.CONFIG.maxSingleClaimActionSize.get();

            for (Admin who : List.of(owner, member)) {
                List<Object> sent = who.send(new ServerboundTeamClaimsRequestPacket(Kind.TEAM_INFO));
                helper.assertTrue(sent.size() == 2 && sent.get(0) instanceof ClientboundTeamInfoPacket && sent.get(1) instanceof ClientboundPartyBudgetPacket budget
                        && budget.getBudget().inTeam(), "expected the team info and the budget, got " + describe(sent));
                TeamInfoData info = ((ClientboundTeamInfoPacket) sent.get(0)).getInfo();
                UUID id = who.player.getUUID();
                IPlayerConfigAPI sub = configManager(server).getLoadedConfig(id).getSubConfig(config.getSubConfigId());
                boolean isOwner = who == owner;
                helper.assertTrue(info.available() && info.partyId().equals(party.getId()) && info.subConfigId().equals(config.getSubConfigId())
                                && info.subConfigIndex() == teamSubIndexOf(server, config.getSubConfigId(), id) && info.subConfigIndex() >= 0
                                && info.memberCount() == 2 && info.claimRole() == TeamRole.DEFAULT && info.unclaimRole() == TeamRole.DEFAULT
                                && info.forceloadRole() == TeamRole.DEFAULT && info.territoryMessages() == cm.areTerritoryMessagesEnabled(id)
                                && info.canEdit() == isOwner && info.bonusClaims() == 6 && info.bonusForceloads() == 0
                                && info.maxClaimActionSize() == maxAction && sub != null
                                && info.claimsColor() == (sub.getEffective(PlayerConfigOptions.CLAIMS_COLOR) & 0xFFFFFF)
                                && info.claimsName().equals(sub.getEffective(PlayerConfigOptions.CLAIMS_NAME)),
                        "expected the team info of the " + (isOwner ? "owner" : "member") + ", got " + info);
            }
            List<Object> sent = loner.send(new ServerboundTeamClaimsRequestPacket(Kind.TEAM_INFO));
            TeamInfoData none = ((ClientboundTeamInfoPacket) sent.get(0)).getInfo();
            helper.assertTrue(!none.available() && none.maxClaimActionSize() == maxAction, "expected no team info for a player in no team, got " + none);
            helper.succeed();
        } finally {
            cleanupParty(server, party);
        }
    }

    // ==================== Helpers ====================

    /** Runs {@code then} once the spec has another loaded config than {@code before}, or the real-time wait is over. */
    private static void waitForReload(GameTestHelper helper, Object before, long start, Runnable then) {
        helper.runAfterDelay(1, () -> {
            if (loadedConfigOf(ServerConfig.SPEC) == before && System.currentTimeMillis() - start < WATCHER_WAIT_MILLIS)
                waitForReload(helper, before, start, then);
            else
                then.run();
        });
    }

    /** The loaded config object of a spec (test-only access to its private field; replaced by every file reload). */
    private static Object loadedConfigOf(ModConfigSpec spec) {
        try {
            Field field = ModConfigSpec.class.getDeclaredField("loadedConfig");
            field.setAccessible(true);
            return field.get(spec);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("test setup: cannot read the loaded config of the spec", e);
        }
    }

    /** A mock player, optionally on the op list, whose packets are watched; taken off the op list and removed on close. */
    private static final class Admin implements AutoCloseable {
        final GameTestHelper helper;
        final MinecraftServer server;
        final RecorderFactory recorders;
        final ServerPlayer player;
        private boolean op;
        private boolean closed;

        Admin(GameTestHelper helper, RecorderFactory recorders, boolean op) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.recorders = recorders;
            this.player = helper.makeMockServerPlayerInLevel();
            setOp(op);
        }

        void setOp(boolean op) {
            this.op = op;
            if (op) server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 4, false));
            else server.getPlayerList().getOps().remove(player.getGameProfile());
        }

        List<Object> capture(Runnable action) {
            return TeamClaimsPacketTestCases.capture(server, player, recorders, action);
        }

        List<Object> send(Object packet) {
            return capture(() -> deliver(player, packet));
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                if (op) setOp(false);
            } catch (Exception ignored) {
            }
            try {
                for (AdminPlayerBonus bonus : AdminPlayerBonus.values())
                    configManager(server).getLoadedConfig(player.getUUID()).tryToReset(bonus.option());
            } catch (Exception ignored) {
            }
            removePlayerQuiet(server, player);
        }
    }

    /**
     * Every public config value field of a config class: its scalar options are in the snapshot with their path and a
     * type, its list options are not.
     */
    private static void assertMatchesConfigClass(GameTestHelper helper, Map<String, AdminConfigEntry> entries, String configId,
            Object config, ModConfigSpec spec) {
        Set<String> expected = new HashSet<>();
        for (Field field : config.getClass().getFields()) {
            if (!ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) continue;
            ModConfigSpec.ConfigValue<?> value;
            try {
                value = (ModConfigSpec.ConfigValue<?>) field.get(config);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            String key = configId + ":" + String.join(".", value.getPath());
            if (value.getDefault() instanceof List<?>) {
                helper.assertTrue(!entries.containsKey(key), "expected the list option " + key + " not to be in the snapshot");
                continue;
            }
            expected.add(key);
            AdminConfigEntry entry = entries.get(key);
            helper.assertTrue(entry != null, "expected the option " + key + " in the snapshot");
            helper.assertTrue(entry.value().equals(text(value.get())) && entry.defaultValue().equals(text(value.getDefault())),
                    "expected the running value and default of " + key + ", got " + entry);
        }
        long inSnapshot = entries.keySet().stream().filter(key -> key.startsWith(configId + ":")).count();
        helper.assertTrue(inSnapshot == expected.size(), "expected exactly the " + expected.size() + " scalar options of " + configId
                + ", got " + inSnapshot);
        helper.assertTrue(!expected.isEmpty() && spec.isLoaded(), "test setup: expected options in " + configId);
    }

    private static String text(Object value) {
        return value instanceof Enum<?> e ? e.name() : String.valueOf(value);
    }

    private static void assertEntry(GameTestHelper helper, AdminConfigEntry entry, ValueType type, String defaultValue, String min,
            String max, boolean restart) {
        helper.assertTrue(entry != null, "expected the entry in the snapshot");
        helper.assertTrue(entry.type() == type && entry.defaultValue().equals(defaultValue) && java.util.Objects.equals(entry.min(), min)
                        && java.util.Objects.equals(entry.max(), max) && entry.restart() == restart,
                "expected " + entry.path() + " to be " + type + ", default " + defaultValue + ", range " + min + " to " + max
                        + ", restart " + restart + ", got " + entry);
    }

    /** The one admin result among the sent packets, which has to be about {@code action}. */
    private static Result result(GameTestHelper helper, List<Object> sent, Action action) {
        List<Result> results = sent.stream().filter(ClientboundAdminResultPacket.class::isInstance)
                .map(p -> ((ClientboundAdminResultPacket) p).getResult()).toList();
        helper.assertTrue(results.size() == 1 && results.get(0).action() == action, "expected one " + action + " result, got " + describe(sent));
        ClientboundAdminResultPacket.Codec codec = new ClientboundAdminResultPacket.Codec();
        helper.assertTrue(roundTrip(codec, new ClientboundAdminResultPacket(results.get(0))).getResult().equals(results.get(0)),
                "expected the result to survive the codec");
        return results.get(0);
    }

    /** The one entry of the update config packet among the sent packets. */
    private static AdminConfigEntry update(GameTestHelper helper, List<Object> sent) {
        List<ClientboundAdminConfigPacket> updates = sent.stream().filter(ClientboundAdminConfigPacket.class::isInstance)
                .map(ClientboundAdminConfigPacket.class::cast).toList();
        helper.assertTrue(updates.size() == 1 && updates.get(0).isUpdate() && updates.get(0).getEntries().size() == 1,
                "expected one update with one entry, got " + describe(sent));
        return updates.get(0).getEntries().get(0);
    }

    private static AdminTeamData teamOf(GameTestHelper helper, List<Object> sent, UUID partyId) {
        List<ClientboundAdminTeamsPacket> lists = sent.stream().filter(ClientboundAdminTeamsPacket.class::isInstance)
                .map(ClientboundAdminTeamsPacket.class::cast).toList();
        helper.assertTrue(lists.size() == 1, "expected one team list, got " + describe(sent));
        AdminTeamData team = lists.get(0).getTeams().stream().filter(t -> t.partyId().equals(partyId)).findFirst().orElse(null);
        helper.assertTrue(team != null, "expected the team in the list");
        return team;
    }

    private static int bonusOf(MinecraftServer server, UUID playerId, AdminPlayerBonus bonus) {
        Integer value = configManager(server).getLoadedConfig(playerId).getEffective(bonus.option());
        return value == null ? 0 : value;
    }

    private static void assertClaim(GameTestHelper helper, ClaimResult<?> result, ClaimResult.Type expected, String what) {
        helper.assertTrue(result.getResultType() == expected, "expected " + what + " to be " + expected + ", got " + result.getResultType());
    }

    /**
     * The value of {@code key} in the toml of a server config as it is on disk: the copy in the world's
     * {@code serverconfig} folder if there is one, else the one in the {@code config} folder (where both loaders read
     * server configs from).
     */
    private static String fileValue(GameTestHelper helper, String fileName, String key) {
        MinecraftServer server = helper.getLevel().getServer();
        Path override = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(fileName);
        Path file = Files.exists(override) ? override : server.getServerDirectory().resolve("config").resolve(fileName);
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*(\\S+)\\s*$").matcher(text);
            helper.assertTrue(matcher.find(), "expected " + key + " in " + file);
            return matcher.group(1);
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + file, e);
        }
    }

    private static String describe(List<Object> sent) {
        return sent.stream().map(packet -> packet.getClass().getSimpleName()).toList().toString();
    }

    private static PacketHandlerFull packetHandler() {
        return (PacketHandlerFull) OpenPartiesAndClaims.INSTANCE.getPacketHandler();
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static FriendlyByteBuf wrap(byte[] bytes) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
    }

    private static <P> byte[] encode(BiConsumer<P, FriendlyByteBuf> encoder, P packet) {
        FriendlyByteBuf buffer = buffer();
        encoder.accept(packet, buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    private static <P, C extends BiConsumer<P, FriendlyByteBuf> & Function<FriendlyByteBuf, P>> P roundTrip(C codec, P packet) {
        P decoded = codec.apply(wrap(encode(codec, packet)));
        if (decoded == null) throw new IllegalStateException("expected " + packet.getClass().getSimpleName() + " to decode");
        return decoded;
    }

    /** An empty buffer, a trailing byte and a missing last byte are all rejected. */
    private static <P, C extends BiConsumer<P, FriendlyByteBuf> & Function<FriendlyByteBuf, P>> void assertRejects(GameTestHelper helper,
            C codec, P valid, String what) {
        byte[] bytes = encode(codec, valid);
        helper.assertTrue(codec.apply(wrap(new byte[0])) == null, "expected an empty " + what + " to be rejected");
        byte[] longer = Arrays.copyOf(bytes, bytes.length + 1);
        helper.assertTrue(codec.apply(wrap(longer)) == null, "expected " + what + " with a trailing byte to be rejected");
        helper.assertTrue(bytes.length <= 1 || codec.apply(wrap(Arrays.copyOf(bytes, bytes.length - 1))) == null,
                "expected " + what + " without its last byte to be rejected");
    }

    /** Delivers a packet to its server handler the way the network does: written with its id, read by the payload codec. */
    private static void deliver(ServerPlayer player, Object packet) {
        FriendlyByteBuf buffer = buffer();
        encodeWithId(packet, buffer);
        deliverTyped(new PacketPayloadCodec().decode(buffer), player);
    }

    private static <P> void encodeWithId(P packet, FriendlyByteBuf buffer) {
        PacketHandlerFull.encodePacket(packetHandler().createPayload(packet).getPacketType(), packet, buffer);
    }

    private static <P> void deliverTyped(PacketPayload<P> payload, ServerPlayer player) {
        payload.getPacketType().getServerHandler().accept(payload.getPacket(), player);
    }
}
