package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.ParseResults;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimManager.ForceloadActivity;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.TeamClaimsOverview;
import xaero.pac.teamclaims.config.TeamConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.CapturingCommandSource;
import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.commandSource;
import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.localized;
import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;

/**
 * Dev/test-only, loader-neutral test bodies for the read-only commands {@code /teamclaims info [player]} and
 * {@code /teamclaims list [page]}. Same conventions as {@link TeamClaimsLogicTestCases} (offline players, a chunk offset
 * of 1000 per test starting at 30000, cleanup on every path, every test registered with the loader's empty structure
 * template and the default timeout).
 * <p>
 * Most tests call {@link TeamClaimsOverview#showInfo}/{@link TeamClaimsOverview#showList} directly with a capturing
 * command source, which needs no player. The tests of the command tree itself (permission, arguments) run the commands
 * as the vanilla mock player, with the output redirected to a capturing source. The expected texts are always built from
 * the lang keys, with the same arguments the command uses.
 */
public final class TeamClaimsOverviewTestCases {

    private static final Logger LOGGER = LogUtils.getLogger();

    private TeamClaimsOverviewTestCases() {}

    // ==================== Tests ====================

    /**
     * A 3 member team (owner, admin, member) with 5 team claims (3 of the owner, 2 of the admin, one of the owner's
     * forceloaded) and a personal claim of the owner: the team totals, every member line ({@code personal + team =
     * count / limit}, in the order owner, admin, member), the red line of the member who is at their claim limit, and the
     * budget with the member who limits it (claims: the member, 0 left; forceloads: the admin, with the lowest limit).
     */
    public static void infoShowsTeamNumbersAndBudget(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T30_Owner");
        GameProfile adminProfile = profile("T30_Admin");
        GameProfile memberProfile = profile("T30_Member");
        UUID ownerId = ownerProfile.getId();
        UUID adminId = adminProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 30000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(adminProfile, memberProfile));
            UUID partyId = party.getId();
            party.setRank(party.getMemberInfo(adminId), PartyMemberRank.ADMIN);
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId).getSubConfigId();
            int ownerSub = teamSubIndexOf(server, subId, ownerId);
            int adminSub = teamSubIndexOf(server, subId, adminId);
            for (int i = 0; i < 3; i++) assertClaimed(helper, doClaim(server, ownerId, ownerSub, x0 + i, 0), "owner team claim " + i);
            for (int i = 3; i < 5; i++) assertClaimed(helper, doClaim(server, adminId, adminSub, x0 + i, 0), "admin team claim " + i);
            assertClaimed(helper, doClaim(server, ownerId, -1, x0 + 10, 0), "owner personal claim");
            ClaimResult<?> forceload = doForceload(server, ownerId, x0, 0, true);
            helper.assertTrue(forceload.getResultType() == ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "expected the forceload to succeed, got " + forceload.getResultType());

            // The member's claim limit goes down to exactly their count (5), the admin's forceload limit to 4
            int memberClaimLimit = 5;
            int adminForceloadLimit = 4;
            setLimit(helper, server, memberId, PlayerConfigOptions.BONUS_CHUNK_CLAIMS, claimsAPI(server).getPlayerFullClaimLimit(memberId), memberClaimLimit);
            setLimit(helper, server, adminId, PlayerConfigOptions.BONUS_CHUNK_FORCELOADS, claimsAPI(server).getPlayerFullForceloadLimit(adminId), adminForceloadLimit);
            int ownerClaimLimit = claimsAPI(server).getPlayerFullClaimLimit(ownerId);
            int ownerForceloadLimit = claimsAPI(server).getPlayerFullForceloadLimit(ownerId);
            int adminClaimLimit = claimsAPI(server).getPlayerFullClaimLimit(adminId);
            int memberForceloadLimit = claimsAPI(server).getPlayerFullForceloadLimit(memberId);

            CapturingCommandSource capture = new CapturingCommandSource();
            int result = TeamClaimsOverview.showInfo(commandSource(helper, capture), null, ownerId, null);
            helper.assertTrue(result == 1, "expected /teamclaims info to succeed, got " + result + " and " + capture.all());
            log("/teamclaims info", capture);

            TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId);
            String teamName = teamConfig.getTeamName() == null || teamConfig.getTeamName().isBlank()
                    ? TeamClaimsCommon.getTeamConfigManager().resolvePartyName(party) : teamConfig.getTeamName();
            List<String> lines = lines(capture);
            assertLine(helper, lines, localized(server, "gui.xaero_pac_team_claims_info_header", teamName, "T30_Owner"));
            assertLine(helper, lines, localized(server, "gui.xaero_pac_team_claims_info_totals", "5", "1"));
            ForceloadActivity activity = TeamClaimsCommon.getClaimManager().getForceloadActivity(partyId);
            assertLine(helper, lines, localized(server, activity == ForceloadActivity.ACTIVE
                    ? "gui.xaero_pac_team_claims_info_forceload_active" : "gui.xaero_pac_team_claims_info_forceload_inactive"));

            String ownerLine = memberLine(server, "T30_Owner", "owner", "1 + 5 = 6 / " + ownerClaimLimit, "0 + 1 = 1 / " + ownerForceloadLimit);
            String adminLine = memberLine(server, "T30_Admin", "admin", "0 + 5 = 5 / " + adminClaimLimit, "0 + 1 = 1 / " + adminForceloadLimit);
            String memberLine = memberLine(server, "T30_Member", "member", "0 + 5 = 5 / " + memberClaimLimit, "0 + 1 = 1 / " + memberForceloadLimit);
            int ownerAt = assertLine(helper, lines, ownerLine);
            int adminAt = assertLine(helper, lines, adminLine);
            int memberAt = assertLine(helper, lines, memberLine);
            helper.assertTrue(ownerAt < adminAt && adminAt < memberAt,
                    "expected the member lines in the order owner, admin, member, got " + lines);
            helper.assertTrue(!isRed(capture.messages().get(ownerAt)) && !isRed(capture.messages().get(adminAt)),
                    "expected the lines of members below their limits not to be red");
            helper.assertTrue(isRed(capture.messages().get(memberAt)),
                    "expected the line of the member at their claim limit to be red");

            assertLine(helper, lines, localized(server, "gui.xaero_pac_team_claims_info_budget_claims", "0", "T30_Member"));
            assertLine(helper, lines, localized(server, "gui.xaero_pac_team_claims_info_budget_forceloads",
                    String.valueOf(adminForceloadLimit - 1), "T30_Admin"));
            helper.succeed();
        } finally {
            resetLimitQuiet(server, memberId, PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
            resetLimitQuiet(server, adminId, PlayerConfigOptions.BONUS_CHUNK_FORCELOADS);
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 3, 0, x0 + 4, 0, x0 + 10, 0);
            if (party != null) {
                TeamClaimsCommon.getClaimManager().deactivateTeamForceLoads(party.getId());
                disbandPartyQuiet(server, party.getId());
            }
        }
    }

    /**
     * The forceload state line follows the team: active while a member is online, active with the minutes left once the
     * last one is gone and a grace period applies, inactive once it is released.
     */
    public static void infoShowsForceloadState(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T31_Owner");
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        int x0 = 31000;
        IServerPartyAPI party = createPartyWithTeam(server, ownerProfile, List.of());
        UUID partyId = party.getId();
        UUID ownerId = ownerProfile.getId();
        try {
            cm.setForceloadGraceTicksOverride(partyId, 40);
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId).getSubConfigId();
            assertClaimed(helper, doClaim(server, ownerId, teamSubIndexOf(server, subId, ownerId), x0, 0), "team claim");
            ClaimResult<?> forceload = doForceload(server, ownerId, x0, 0, true);
            helper.assertTrue(forceload.getResultType() == ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "expected the forceload to succeed, got " + forceload.getResultType());
        } catch (RuntimeException | Error e) {
            cleanupForceloadState(server, cm, partyId, x0);
            throw e;
        }
        // The party events of the setup tick re-check the activation, so the team is only activated one tick later
        helper.runAfterDelay(1, () -> {
            try {
                cm.setTeamOnline(partyId, true);
                assertInfoLine(helper, server, ownerId, localized(server, "gui.xaero_pac_team_claims_info_forceload_active"), "while a member is online");
                cm.onMemberLoggedOut(ownerId);
                assertInfoLine(helper, server, ownerId, localized(server, "gui.xaero_pac_team_claims_info_forceload_grace", "1"),
                        "during the grace period (40 ticks, shown as 1 minute)");
                cm.deactivateTeamForceLoads(partyId);
                assertInfoLine(helper, server, ownerId, localized(server, "gui.xaero_pac_team_claims_info_forceload_inactive"), "once released");
                helper.succeed();
            } finally {
                cleanupForceloadState(server, cm, partyId, x0);
            }
        });
    }

    /**
     * The commands as a player: {@code /teamclaims info} and {@code list} for the player's own team, and the
     * {@code info <player>} branch only with permission level 2 (it is not even parsed below that).
     */
    public static void overviewCommandsRunAsTeamPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        IServerPartyAPI party = null;
        int x0 = 32000;
        try {
            party = createPartyWithTeam(server, player.getGameProfile(), List.of());
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            assertClaimed(helper, doClaim(server, player.getUUID(), teamSubIndexOf(server, subId, player.getUUID()), x0, 0), "team claim");
            String name = player.getGameProfile().getName();

            CapturingCommandSource info = run(server, player, 0, "teamclaims info");
            helper.assertTrue(info.received(localized(server, "gui.xaero_pac_team_claims_info_totals", "1", "0")),
                    "expected /teamclaims info to show the team totals, got " + info.all());
            CapturingCommandSource list = run(server, player, 0, "teamclaims list");
            helper.assertTrue(list.received(localized(server, "gui.xaero_pac_team_claims_list_entry", "overworld", String.valueOf(x0), "0",
                            String.valueOf(x0 * 16 + 8), "8", name)),
                    "expected /teamclaims list to show the team claim, got " + list.all());
            helper.assertTrue(list.received(localized(server, "gui.xaero_pac_team_claims_list_footer", "1", "1")),
                    "expected /teamclaims list to show the page footer, got " + list.all());
            CapturingCommandSource badPage = run(server, player, 0, "teamclaims list 2");
            helper.assertTrue(badPage.received(localized(server, "gui.xaero_pac_team_claims_list_page_invalid", "2", "1")),
                    "expected /teamclaims list 2 to be rejected, got " + badPage.all());
            helper.assertTrue(!canRun(server, player, 0, "teamclaims list 0"), "expected page 0 to be rejected by the argument");

            helper.assertTrue(!canRun(server, player, 0, "teamclaims info SomeName") && !canRun(server, player, 1, "teamclaims info SomeName"),
                    "expected /teamclaims info <player> to be unavailable below permission level 2");
            helper.assertTrue(canRun(server, player, 2, "teamclaims info SomeName"),
                    "expected /teamclaims info <player> to be available with permission level 2");
            helper.assertTrue(canRun(server, player, 0, "teamclaims info") && canRun(server, player, 0, "teamclaims list 1"),
                    "expected /teamclaims info and list to need no permission");
            CapturingCommandSource other = run(server, player, 2, "teamclaims info @p");
            helper.assertTrue(other.received(localized(server, "gui.xaero_pac_team_claims_info_totals", "1", "0")),
                    "expected /teamclaims info <player> to show the team of that player, got " + other.all());
            helper.succeed();
        } finally {
            removePlayerQuiet(server, player);
            unclaimQuiet(server, x0, 0);
            if (party != null) disbandPartyQuiet(server, party.getId());
        }
    }

    /** A player in no team gets the "not in a team" failure from {@code info}, {@code list} and {@code info <player>}. */
    public static void overviewCommandsWithoutTeam(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            String name = player.getGameProfile().getName();
            CapturingCommandSource info = run(server, player, 0, "teamclaims info");
            helper.assertTrue(info.received(localized(server, "gui.xaero_pac_team_claims_info_no_team")),
                    "expected /teamclaims info to fail for a player without a team, got " + info.all());
            CapturingCommandSource list = run(server, player, 0, "teamclaims list");
            helper.assertTrue(list.received(localized(server, "gui.xaero_pac_team_claims_info_no_team")),
                    "expected /teamclaims list to fail for a player without a team, got " + list.all());
            CapturingCommandSource other = run(server, player, 2, "teamclaims info @p");
            helper.assertTrue(other.received(localized(server, "gui.xaero_pac_team_claims_info_player_no_team", name)),
                    "expected /teamclaims info <player> to name the player without a team, got " + other.all());

            CapturingCommandSource direct = new CapturingCommandSource();
            int result = TeamClaimsOverview.showInfo(commandSource(helper, direct), null, UUID.randomUUID(), null);
            helper.assertTrue(result == 0 && direct.received(localized(server, "gui.xaero_pac_team_claims_info_no_team")),
                    "expected an unknown player to be in no team, got result " + result + " and " + direct.all());
            helper.succeed();
        } finally {
            removePlayerQuiet(server, player);
        }
    }

    /**
     * 12 team claims of two members, claimed in a scrambled order, two of them forceloaded: page 1 has the first 10
     * sorted by x, then z, page 2 the other 2, every line has the short dimension, chunk, block centre, owner and
     * forceload marker, the footer numbers are right, and its buttons point to the neighbouring pages (an edge page
     * has no button on that side). A page past the end fails, and a team without team claims gets the hint.
     */
    public static void listPagesAreSortedAndNavigable(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T32_Owner");
        GameProfile memberProfile = profile("T32_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 34000;
        int[][] offsets = {{3, 0}, {1, 5}, {1, -2}, {7, 0}, {0, 0}, {2, 0}, {11, 0}, {9, 1}, {5, 0}, {4, 0}, {10, 0}, {6, 0}};
        int ownerClaims = 7;//the first 7 of the offsets are the owner's, the other 5 the member's
        List<Integer> forceloaded = List.of(2 * 1000 + 0, 9 * 1000 + 1);//encoded x * 1000 + z of the forceloaded offsets
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            UUID partyId = party.getId();
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId).getSubConfigId();

            CapturingCommandSource empty = new CapturingCommandSource();
            int emptyResult = TeamClaimsOverview.showList(commandSource(helper, empty), null, ownerId, 1);
            helper.assertTrue(emptyResult == 1 && empty.received(localized(server, "gui.xaero_pac_team_claims_list_empty", subId)),
                    "expected the hint for a team without team claims, got result " + emptyResult + " and " + empty.all());

            List<int[]> claimed = new ArrayList<>();
            for (int i = 0; i < offsets.length; i++) {
                UUID claimer = i < ownerClaims ? ownerId : memberId;
                int x = x0 + offsets[i][0];
                int z = offsets[i][1];
                assertClaimed(helper, doClaim(server, claimer, teamSubIndexOf(server, subId, claimer), x, z), "team claim " + i);
                if (forceloaded.contains(offsets[i][0] * 1000 + offsets[i][1])) {
                    ClaimResult<?> forceload = claimsAPI(server).tryToForceload(OVERWORLD, claimer, OVERWORLD, x, z, x, z, true, false);
                    helper.assertTrue(forceload.getResultType() == ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                            "expected the forceload of team claim " + i + " to succeed, got " + forceload.getResultType());
                }
                claimed.add(new int[]{x, z, i < ownerClaims ? 0 : 1, forceloaded.contains(offsets[i][0] * 1000 + offsets[i][1]) ? 1 : 0});
            }
            claimed.sort(Comparator.<int[]>comparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
            TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId);
            String teamName = teamConfig.getTeamName() == null || teamConfig.getTeamName().isBlank()
                    ? TeamClaimsCommon.getTeamConfigManager().resolvePartyName(party) : teamConfig.getTeamName();

            for (int page = 1; page <= 2; page++) {
                CapturingCommandSource capture = new CapturingCommandSource();
                int result = TeamClaimsOverview.showList(commandSource(helper, capture), null, memberId, page);
                helper.assertTrue(result == 1, "expected /teamclaims list " + page + " to succeed, got " + capture.all());
                log("/teamclaims list " + page, capture);
                List<String> lines = lines(capture);
                int entries = page == 1 ? 10 : 2;
                helper.assertTrue(lines.size() == entries + 2,
                        "expected the header, " + entries + " entries and the footer on page " + page + ", got " + lines);
                helper.assertTrue(lines.get(0).equals(localized(server, "gui.xaero_pac_team_claims_list_header", teamName, "12")),
                        "expected the list header on page " + page + ", got " + lines.get(0));
                for (int i = 0; i < entries; i++) {
                    int[] claim = claimed.get((page - 1) * 10 + i);
                    String expected = localized(server, claim[3] == 1 ? "gui.xaero_pac_team_claims_list_entry_forceloaded"
                                    : "gui.xaero_pac_team_claims_list_entry", "overworld", String.valueOf(claim[0]), String.valueOf(claim[1]),
                            String.valueOf(claim[0] * 16 + 8), String.valueOf(claim[1] * 16 + 8), claim[2] == 0 ? "T32_Owner" : "T32_Member");
                    helper.assertTrue(lines.get(1 + i).equals(expected),
                            "expected entry " + i + " of page " + page + " to be '" + expected + "', got '" + lines.get(1 + i) + "'");
                }
                String footer = lines.get(lines.size() - 1);
                helper.assertTrue(footer.equals(localized(server, "gui.xaero_pac_team_claims_list_footer", String.valueOf(page), "2") + " [<] [>]"),
                        "expected the footer of page " + page + ", got '" + footer + "'");
                List<String> clicks = clickCommands(capture.messages().get(capture.messages().size() - 1));
                helper.assertTrue(clicks.equals(page == 1 ? List.of("/teamclaims list 2") : List.of("/teamclaims list 1")),
                        "expected the footer of page " + page + " to link to the other page only, got " + clicks);
            }

            CapturingCommandSource past = new CapturingCommandSource();
            int pastResult = TeamClaimsOverview.showList(commandSource(helper, past), null, ownerId, 3);
            helper.assertTrue(pastResult == 0 && past.received(localized(server, "gui.xaero_pac_team_claims_list_page_invalid", "3", "2")),
                    "expected page 3 of 2 to be rejected, got result " + pastResult + " and " + past.all());
            helper.assertTrue(TeamClaimsOverview.shortDimension(ResourceLocation.withDefaultNamespace("overworld")).equals("overworld")
                            && TeamClaimsOverview.shortDimension(ResourceLocation.fromNamespaceAndPath("other", "realm")).equals("other:realm"),
                    "expected only the minecraft namespace to be left out of a dimension name");
            helper.succeed();
        } finally {
            for (int[] offset : offsets) unclaimQuiet(server, x0 + offset[0], offset[1]);
            if (party != null) {
                TeamClaimsCommon.getClaimManager().deactivateTeamForceLoads(party.getId());
                disbandPartyQuiet(server, party.getId());
            }
        }
    }

    // ==================== Helpers ====================

    private static void assertClaimed(GameTestHelper helper, ClaimResult<?> result, String what) {
        helper.assertTrue(result.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                "expected the " + what + " to succeed, got " + result.getResultType());
    }

    /** Lowers the full limit of a player from {@code current} to {@code target} with the matching bonus option. */
    private static void setLimit(GameTestHelper helper, MinecraftServer server, UUID playerId,
            IPlayerConfigOptionSpecAPI<Integer> bonusOption, int current, int target) {
        IPlayerConfigAPI.SetResult result = configManager(server).getLoadedConfig(playerId).tryToSet(bonusOption, target - current);
        helper.assertTrue(result == IPlayerConfigAPI.SetResult.SUCCESS, "expected setting the limit bonus to succeed, got " + result);
    }

    private static void resetLimitQuiet(MinecraftServer server, UUID playerId,
            IPlayerConfigOptionSpecAPI<Integer> bonusOption) {
        try {
            configManager(server).getLoadedConfig(playerId).tryToReset(bonusOption);
        } catch (Exception ignored) {
        }
    }

    private static String memberLine(MinecraftServer server, String name, String rank, String claims, String forceloads) {
        return localized(server, "gui.xaero_pac_team_claims_info_member", name, rank, "○", claims, forceloads);
    }

    private static List<String> lines(CapturingCommandSource capture) {
        return capture.messages().stream().map(Component::getString).toList();
    }

    /** The index of the line, which has to be there as a whole. */
    private static int assertLine(GameTestHelper helper, List<String> lines, String expected) {
        int at = lines.indexOf(expected);
        helper.assertTrue(at >= 0, "expected the line '" + expected + "', got " + lines);
        return at;
    }

    private static void assertInfoLine(GameTestHelper helper, MinecraftServer server, UUID subjectId, String expected, String when) {
        CapturingCommandSource capture = new CapturingCommandSource();
        TeamClaimsOverview.showInfo(commandSource(helper, capture), null, subjectId, null);
        helper.assertTrue(lines(capture).contains(expected), "expected /teamclaims info to show '" + expected + "' " + when
                + ", got " + lines(capture));
    }

    private static void cleanupForceloadState(MinecraftServer server, TeamClaimManager cm, UUID partyId, int x0) {
        cm.setForceloadGraceTicksOverride(partyId, -1);
        unclaimQuiet(server, x0, 0);
        cm.deactivateTeamForceLoads(partyId);
        disbandPartyQuiet(server, partyId);
    }

    private static boolean isRed(Component message) {
        TextColor color = message.getStyle().getColor();
        return color != null && color.equals(TextColor.fromLegacyFormat(ChatFormatting.RED));
    }

    private static List<String> clickCommands(Component message) {
        return message.toFlatList(Style.EMPTY).stream().map(c -> c.getStyle().getClickEvent()).filter(Objects::nonNull)
                .filter(click -> click.getAction() == ClickEvent.Action.RUN_COMMAND).map(ClickEvent::getValue).toList();
    }

    /** The source of the mock player, with the given permission level and its output going to {@code capture}. */
    private static CommandSourceStack playerSource(ServerPlayer player, CapturingCommandSource capture, int permissionLevel) {
        return player.createCommandSourceStack().withSource(capture).withPermission(permissionLevel);
    }

    private static CapturingCommandSource run(MinecraftServer server, ServerPlayer player, int permissionLevel, String command) {
        CapturingCommandSource capture = new CapturingCommandSource();
        server.getCommands().performPrefixedCommand(playerSource(player, capture, permissionLevel), command);
        return capture;
    }

    private static boolean canRun(MinecraftServer server, ServerPlayer player, int permissionLevel, String command) {
        ParseResults<CommandSourceStack> parsed = server.getCommands().getDispatcher()
                .parse(command, playerSource(player, new CapturingCommandSource(), permissionLevel));
        return !parsed.getReader().canRead() && parsed.getContext().getCommand() != null;
    }

    private static void removePlayerQuiet(MinecraftServer server, ServerPlayer player) {
        try {
            server.getPlayerList().remove(player);
        } catch (Exception ignored) {
        }
    }

    /** Writes the rendered text of a command output to the log, for a look at what players get. */
    private static void log(String command, CapturingCommandSource capture) {
        LOGGER.info("[TeamClaims gametest] rendered output of {}:\n{}", command, String.join("\n", lines(capture)));
    }
}
