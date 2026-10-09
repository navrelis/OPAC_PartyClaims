package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.player.mode.ClaimingMode;
import xaero.pac.common.claims.player.mode.ClaimingModeLimits;
import xaero.pac.common.claims.player.mode.api.ClaimingModes;
import xaero.pac.common.claims.result.api.AreaClaimResult;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.claims.forceload.ForceLoadTicketManager;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.IPlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.common.server.player.config.util.ServerPlayerConfigUtils;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimManager.BudgetInfo;
import xaero.pac.teamclaims.TeamClaimManager.BudgetSettings;
import xaero.pac.teamclaims.TeamClaimManager.ClaimPos;
import xaero.pac.teamclaims.TeamClaimManager.TeamClaimSavedData;
import xaero.pac.teamclaims.TeamClaimManager.TeamData;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.TeamClaimsOverview;
import xaero.pac.teamclaims.config.TeamConfigManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.CapturingCommandSource;
import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.commandSource;
import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.localized;
import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.removePlayerQuiet;

/**
 * Dev/test-only, loader-neutral test bodies for the two independent budgets: a player's private claims against OPAC's
 * own per-player limits, and a team's claims against the team limits, which only depend on the member count; plus what
 * happens to team claims above the limit (the over-limit grace) and to the team claims of a removed party. Same
 * conventions as {@link TeamClaimsLogicTestCases} (a chunk offset of 1000 per test starting at 60000, cleanup on every
 * path, every test registered with the loader's empty structure template and the default timeout).
 * <p>
 * Every test runs in one go, in the tick it starts in. Where a real server would process a party change at the end of
 * the tick, the test calls {@link TeamConfigManager#processPendingEvents()} itself. The team limits come from
 * {@link TeamClaimManager#setBudgetSettingsOverride} (per party, so that the tests don't depend on the config and on
 * each other), and the real time the over-limit deadlines use from {@link TeamClaimManager#setClock}: a clock that
 * starts at the real time and only ever moves forward, by less than the default grace period, and is put back before
 * the test returns, so that nothing else on the server sees a deadline pass early.
 * <p>
 * Tests that need an online player (the claim limits packet, OPAC's forceload tickets, chat messages) use the vanilla
 * mock player as a MEMBER of a team owned by an offline player.
 */
public final class TeamClaimsBudgetTestCases {

    private static final String KEY = "gui.xaero_pac_team_claims_";
    private static final long HOUR = 3_600_000L;

    private TeamClaimsBudgetTestCases() {}

    // ==================== Tests: the two budgets ====================

    /**
     * A team at its team claim limit (2) and its team forceload limit (1): further team claims are rejected for every
     * member and the chunk stays unclaimed, a further team forceload is rejected, but the members' private claims and
     * a private forceload still succeed.
     */
    public static void teamFullDoesNotBlockPrivateClaims(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        GameProfile ownerProfile = profile("T60_Owner");
        GameProfile memberProfile = profile("T60_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 60000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            cm.setBudgetSettingsOverride(party.getId(), new BudgetSettings(2, 2, 0, 1, 0, 168));
            int ownerTeamSub = teamSub(server, party, ownerId);
            int memberTeamSub = teamSub(server, party, memberId);

            assertResult(helper, doClaim(server, ownerId, ownerTeamSub, x0, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "1st team claim");
            assertResult(helper, doClaim(server, memberId, memberTeamSub, x0 + 1, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "2nd team claim");
            assertResult(helper, doClaim(server, ownerId, ownerTeamSub, x0 + 2, 0), ClaimResult.Type.CLAIM_LIMIT_REACHED,
                    "the owner's team claim at the team limit");
            assertResult(helper, doClaim(server, memberId, memberTeamSub, x0 + 2, 0), ClaimResult.Type.CLAIM_LIMIT_REACHED,
                    "the member's team claim at the team limit");
            helper.assertTrue(claimsAPI(server).get(OVERWORLD, x0 + 2, 0) == null,
                    "expected the chunk to stay unclaimed after the rejected team claims");

            assertResult(helper, doClaim(server, ownerId, -1, x0 + 2, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "the owner's private claim with the team at its limit");
            assertResult(helper, doClaim(server, memberId, -1, x0 + 3, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "the member's private claim with the team at its limit");

            assertResult(helper, doForceload(server, ownerId, x0, 0, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD, "1st team forceload");
            assertResult(helper, doForceload(server, ownerId, x0 + 1, 0, true), ClaimResult.Type.FORCELOAD_LIMIT_REACHED,
                    "the owner's team forceload at the team forceload limit");
            assertResult(helper, doForceload(server, memberId, x0 + 1, 0, true), ClaimResult.Type.FORCELOAD_LIMIT_REACHED,
                    "the member's team forceload at the team forceload limit");
            helper.assertTrue(claimsAPI(server).getPlayerFullForceloadLimit(memberId) > 0,
                    "test precondition: expected the member to have a private forceload limit above 0");
            assertResult(helper, doForceload(server, memberId, x0 + 3, 0, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "the member's private forceload with the team at its forceload limit");

            BudgetInfo member = cm.getBudgetInfo(memberId);
            helper.assertTrue(member.teamClaims() == 2 && member.teamClaimLimit() == 2 && member.teamForceloads() == 1
                            && member.teamForceloadLimit() == 1 && member.privateClaims() == 1 && member.privateForceloads() == 1
                            && member.overLimitDeadline() == 0 && member.claimsOverLimit() == 0,
                    "expected the team exactly at its limits (not above) and 1 private claim and forceload of the member, got "
                            + member);
            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 3, 0);
            cleanupParty(server, party);
        }
    }

    /**
     * The team limits only depend on the member count (the owner included, invited players not), with the values of
     * the config: nothing below {@code teamClaimsMinMembers}, the base at exactly that many, and the per-member
     * amount for every member beyond. They follow joins and leaves at once, and a one-member team can make no team
     * claim at all.
     */
    public static void teamLimitFollowsMemberCount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        GameProfile ownerProfile = profile("T61_Owner");
        UUID ownerId = ownerProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 61000;
        try {
            // The formula with the default values: 0 / 500 / 525 / 550 team claims and 0 / 10 / 12 / 14 team forceloads
            BudgetSettings defaults = new BudgetSettings(2, 500, 25, 10, 2, 168);
            int[] expectedClaims = {0, 0, 500, 525, 550};
            int[] expectedForceloads = {0, 0, 10, 12, 14};
            for (int members = 0; members <= 4; members++)
                helper.assertTrue(defaults.claimLimit(members) == expectedClaims[members]
                                && defaults.forceloadLimit(members) == expectedForceloads[members],
                        "expected the default limits for " + members + " member(s) to be " + expectedClaims[members] + " / "
                                + expectedForceloads[members] + ", got " + defaults.claimLimit(members) + " / "
                                + defaults.forceloadLimit(members));
            BudgetSettings minThree = new BudgetSettings(3, 40, 7, 6, 1, 0);
            helper.assertTrue(minThree.claimLimit(2) == 0 && minThree.claimLimit(3) == 40 && minThree.claimLimit(5) == 54
                            && minThree.forceloadLimit(2) == 0 && minThree.forceloadLimit(5) == 8 && minThree.graceMillis() == 0,
                    "expected the limits to follow teamClaimsMinMembers = 3");

            // The live limits of a real party, with the config of the running server
            BudgetSettings config = BudgetSettings.fromConfig();
            party = createPartyWithTeam(server, ownerProfile, List.of());
            int ownerTeamSub = teamSub(server, party, ownerId);
            assertLimits(helper, cm, ownerId, config, 1, "a new one-member team");
            if (config.minMembers() > 1) {
                assertResult(helper, doClaim(server, ownerId, ownerTeamSub, x0, 0), ClaimResult.Type.CLAIM_LIMIT_REACHED,
                        "the team claim of a team below teamClaimsMinMembers");
                helper.assertTrue(claimsAPI(server).get(OVERWORLD, x0, 0) == null, "expected the chunk to stay unclaimed");
            }
            assertResult(helper, doClaim(server, ownerId, -1, x0 + 1, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "the private claim of the owner of a one-member team");

            List<GameProfile> joiners = List.of(profile("T61_M2"), profile("T61_M3"), profile("T61_M4"));
            for (int i = 0; i < joiners.size(); i++) {
                GameProfile joiner = joiners.get(i);
                helper.assertTrue(party.addMember(joiner.getId(), PartyMemberRank.MEMBER, joiner.getName()) != null,
                        "expected party.addMember to succeed");
                tcm.processPendingEvents();
                assertLimits(helper, cm, ownerId, config, i + 2, "after member " + (i + 2) + " joined");
                assertLimits(helper, cm, joiner.getId(), config, i + 2, "for the new member " + (i + 2));
            }
            GameProfile invited = profile("T61_Invited");
            helper.assertTrue(party.invitePlayer(invited.getId(), invited.getName()) != null, "expected the invite to succeed");
            tcm.processPendingEvents();
            assertLimits(helper, cm, ownerId, config, 4, "with an invited player, who does not count");

            helper.assertTrue(party.removeMember(joiners.get(2).getId()) != null, "expected party.removeMember to succeed");
            tcm.processPendingEvents();
            assertLimits(helper, cm, ownerId, config, 3, "after a member left");
            helper.assertTrue(!cm.getBudgetInfo(joiners.get(2).getId()).inTeam(),
                    "expected the member who left to be in no team any more");

            if (config.claimLimit(3) > 0)
                assertResult(helper, doClaim(server, ownerId, ownerTeamSub, x0, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                        "the team claim of a three-member team");
            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0, x0 + 1, 0);
            cleanupParty(server, party);
        }
    }

    /**
     * A claim that replaces the same player's own claim moves it between the two budgets, and the budget it moves
     * into is what is checked: private to team needs room in the team's budget (the stock "own claim, count
     * unaffected" shortcut must not let it through), team to private needs room in the player's private budget.
     */
    public static void reclaimIsCheckedAgainstDestinationBudget(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        GameProfile ownerProfile = profile("T62_Owner");
        UUID ownerId = ownerProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 62000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(profile("T62_Member")));
            cm.setBudgetSettingsOverride(party.getId(), new BudgetSettings(2, 1, 0, 10, 0, 168));
            int teamSub = teamSub(server, party, ownerId);
            assertResult(helper, doClaim(server, ownerId, -1, x0, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "private claim A");
            assertResult(helper, doClaim(server, ownerId, -1, x0 + 1, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "private claim B");
            assertResult(helper, doClaim(server, ownerId, -1, x0, 0), ClaimResult.Type.ALREADY_CLAIMED,
                    "claiming the own private claim again as it is");

            // Private to team: A fits into the team (0 / 1), B does not (1 / 1) although the player's count stays the same
            assertResult(helper, doClaim(server, ownerId, teamSub, x0, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "turning private claim A into a team claim");
            assertResult(helper, doClaim(server, ownerId, teamSub, x0 + 1, 0), ClaimResult.Type.CLAIM_LIMIT_REACHED,
                    "turning private claim B into a team claim with the team at its limit");
            assertSub(helper, server, x0, teamSub, "A");
            assertSub(helper, server, x0 + 1, -1, "B");
            assertBudget(helper, cm.getBudgetInfo(ownerId), 1, 1, "after private to team");

            // Team to private: no room with a private limit of 1 (B is there), room with 2
            setPrivateLimit(helper, server, ownerId, PlayerConfigOptions.BONUS_CHUNK_CLAIMS, 1);
            assertResult(helper, doClaim(server, ownerId, -1, x0, 0), ClaimResult.Type.CLAIM_LIMIT_REACHED,
                    "turning team claim A into a private claim with the private limit reached");
            assertSub(helper, server, x0, teamSub, "A");
            assertBudget(helper, cm.getBudgetInfo(ownerId), 1, 1, "after the rejected team to private");
            setPrivateLimit(helper, server, ownerId, PlayerConfigOptions.BONUS_CHUNK_CLAIMS, 2);
            assertResult(helper, doClaim(server, ownerId, -1, x0, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "turning team claim A into a private claim with private room");
            assertSub(helper, server, x0, -1, "A");
            assertBudget(helper, cm.getBudgetInfo(ownerId), 2, 0, "after team to private");

            // And back into the team, which has room again, with the private budget full (2 / 2)
            assertResult(helper, doClaim(server, ownerId, teamSub, x0 + 1, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "turning private claim B into a team claim with the private limit reached");
            assertBudget(helper, cm.getBudgetInfo(ownerId), 1, 1, "at the end");
            helper.succeed();
        } finally {
            resetBonusQuiet(server, ownerId);
            unclaimQuiet(server, x0, 0, x0 + 1, 0);
            cleanupParty(server, party);
        }
    }

    /**
     * Private and team forceloads have separate limits: with a private forceload limit of 1 and a team forceload
     * limit of 1, a second private forceload and a second team forceload are both rejected, each whatever room the
     * other budget has, for the claim owner and for a teammate. Turning one off makes room in that budget only.
     */
    public static void forceloadBudgetsAreIndependent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        GameProfile ownerProfile = profile("T63_Owner");
        GameProfile memberProfile = profile("T63_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 63000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            cm.setBudgetSettingsOverride(party.getId(), new BudgetSettings(2, 10, 0, 1, 0, 168));
            int teamSub = teamSub(server, party, ownerId);
            for (int i = 0; i < 2; i++) {
                assertResult(helper, doClaim(server, ownerId, teamSub, x0 + i, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "team claim " + i);
                assertResult(helper, doClaim(server, ownerId, -1, x0 + i, 2), ClaimResult.Type.SUCCESSFUL_CLAIM, "private claim " + i);
            }
            setPrivateLimit(helper, server, ownerId, PlayerConfigOptions.BONUS_CHUNK_FORCELOADS, 1);

            assertResult(helper, doForceload(server, ownerId, x0, 2, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD, "1st private forceload");
            assertResult(helper, doForceload(server, ownerId, x0 + 1, 2, true), ClaimResult.Type.FORCELOAD_LIMIT_REACHED,
                    "2nd private forceload at a private limit of 1");
            assertResult(helper, doForceload(server, ownerId, x0, 0, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "1st team forceload with the private forceload limit reached");
            assertResult(helper, doForceload(server, ownerId, x0 + 1, 0, true), ClaimResult.Type.FORCELOAD_LIMIT_REACHED,
                    "2nd team forceload of the claim owner at a team limit of 1");
            assertResult(helper, doForceload(server, memberId, x0 + 1, 0, true), ClaimResult.Type.FORCELOAD_LIMIT_REACHED,
                    "2nd team forceload of a teammate (with all of their private forceload room) at a team limit of 1");
            BudgetInfo owner = cm.getBudgetInfo(ownerId);
            helper.assertTrue(owner.privateForceloads() == 1 && owner.privateForceloadLimit() == 1 && owner.teamForceloads() == 1
                            && owner.teamForceloadLimit() == 1 && owner.ownedTeamForceloads() == 1,
                    "expected 1 / 1 private and 1 / 1 team forceloads, got " + owner);

            // Room in the private budget does not help the team's, and the other way around
            assertResult(helper, doForceload(server, ownerId, x0, 2, false), ClaimResult.Type.SUCCESSFUL_UNFORCELOAD, "turning the private forceload off");
            assertResult(helper, doForceload(server, ownerId, x0 + 1, 0, true), ClaimResult.Type.FORCELOAD_LIMIT_REACHED,
                    "2nd team forceload with private forceload room");
            assertResult(helper, doForceload(server, ownerId, x0, 2, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD, "the private forceload again");
            assertResult(helper, doForceload(server, memberId, x0, 0, false), ClaimResult.Type.SUCCESSFUL_UNFORCELOAD,
                    "a teammate turning the team forceload off");
            assertResult(helper, doForceload(server, ownerId, x0 + 1, 2, true), ClaimResult.Type.FORCELOAD_LIMIT_REACHED,
                    "2nd private forceload with team forceload room");
            assertResult(helper, doForceload(server, memberId, x0 + 1, 0, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD,
                    "a teammate's team forceload with team forceload room");
            helper.succeed();
        } finally {
            resetBonusQuiet(server, ownerId);
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0, 2, x0 + 1, 2);
            if (party != null) cm.deactivateTeamForceLoads(party.getId());
            cleanupParty(server, party);
        }
    }

    /**
     * OPAC enables its own forceload tickets of a player only up to the player's forceload limit. Team forceloads the
     * player technically owns are not counted for that and not held back by it: with a private forceload limit of 1,
     * two team forceloads and one private forceload of the (online) player are all enabled, a second private one is
     * not, and a full ticket update comes to the same result.
     */
    public static void teamForceloadsDoNotUseUpPrivateTickets(GameTestHelper helper) {
        Team t = Team.create(helper, "T64", 64000);
        int x0 = t.x0;
        try {
            t.cm.setBudgetSettingsOverride(t.party.getId(), new BudgetSettings(2, 10, 0, 5, 0, 168));
            int teamSub = t.teamSub(t.playerId);
            setPrivateLimit(helper, t.server, t.playerId, PlayerConfigOptions.BONUS_CHUNK_FORCELOADS, 1);
            ForceLoadTicketManager tickets = ServerData.from(t.server).getForceLoadManager();

            t.claim(t.playerId, teamSub, x0, 0, true);
            t.claim(t.playerId, -1, x0 + 1, 0, true);
            t.claim(t.playerId, teamSub, x0 + 2, 0, true);
            t.claim(t.playerId, -1, x0 + 3, 0, true);
            BudgetInfo budget = t.cm.getBudgetInfo(t.playerId);
            helper.assertTrue(budget.teamForceloads() == 2 && budget.privateForceloads() == 2 && budget.privateForceloadLimit() == 1,
                    "test setup: expected 2 team and 2 private forceloads with a private limit of 1, got " + budget);
            helper.assertTrue(tickets.isTicketEnabled(OVERWORLD, t.playerId, x0, 0) && tickets.isTicketEnabled(OVERWORLD, t.playerId, x0 + 2, 0),
                    "expected OPAC's tickets of both team forceloads to be enabled whatever the private limit is");
            helper.assertTrue(tickets.isTicketEnabled(OVERWORLD, t.playerId, x0 + 1, 0),
                    "expected OPAC's ticket of the 1st private forceload to be enabled although a team forceload was made before it");
            helper.assertTrue(!tickets.isTicketEnabled(OVERWORLD, t.playerId, x0 + 3, 0),
                    "expected OPAC's ticket of the 2nd private forceload to stay disabled at a private limit of 1");

            tickets.updateTicketsFor(t.playerId, false);
            int privateEnabled = (tickets.isTicketEnabled(OVERWORLD, t.playerId, x0 + 1, 0) ? 1 : 0)
                    + (tickets.isTicketEnabled(OVERWORLD, t.playerId, x0 + 3, 0) ? 1 : 0);
            helper.assertTrue(tickets.isTicketEnabled(OVERWORLD, t.playerId, x0, 0) && tickets.isTicketEnabled(OVERWORLD, t.playerId, x0 + 2, 0)
                            && privateEnabled == 1,
                    "expected a full ticket update to enable both team forceloads and exactly 1 private one, got "
                            + privateEnabled + " private ticket(s)");
            helper.succeed();
        } finally {
            t.cleanup(x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 3, 0);
        }
    }

    /**
     * What the claim limits packet of the PLAYER mode carries: the player's private numbers while a private
     * sub-claim is selected, the team's (count and limit, claims and forceloads) while the team sub-config is, and
     * they follow the team's claims and member count. The client is told to use these numbers for every player with a
     * team sub-config. Without the Team Claims handler the builder is stock again.
     */
    public static void claimLimitsPacketFollowsUsedSubConfig(GameTestHelper helper) {
        Team t = Team.create(helper, "T65", 65000);
        int x0 = t.x0;
        TeamClaimsIntegration.TeamClaimsHandler handler = TeamClaimsIntegration.getHandler();
        IPlayerConfigAPI playerConfig = configManager(t.server).getLoadedConfig(t.playerId);
        try {
            t.cm.setBudgetSettingsOverride(t.party.getId(), new BudgetSettings(2, 7, 5, 3, 1, 168));
            int privateClaimLimit = claimsAPI(t.server).getPlayerFullClaimLimit(t.playerId);
            int privateForceloadLimit = claimsAPI(t.server).getPlayerFullForceloadLimit(t.playerId);
            t.claim(t.playerId, -1, x0, 0, true);
            t.claim(t.playerId, -1, x0 + 1, 0, false);
            t.claim(t.playerId, t.teamSub(t.playerId), x0 + 2, 0, false);
            t.claim(t.ownerId, t.teamSub(t.ownerId), x0 + 3, 0, true);
            t.claim(t.ownerId, t.teamSub(t.ownerId), x0 + 4, 0, false);
            t.claim(t.ownerId, -1, x0 + 5, 0, false);//the owner's private claim counts for nobody else

            assertLimits(helper, t.playerLimits(), 2, privateClaimLimit, 1, privateForceloadLimit, "with the main config selected");
            helper.assertTrue(handler.usesServerSideClaimCounts(t.playerId) && handler.usesServerSideClaimCounts(t.ownerId),
                    "expected the server's counts to win on the client for the members of a team");
            helper.assertTrue(!handler.usesServerSideClaimCounts(UUID.randomUUID()),
                    "expected the client to keep counting for a player in no team");

            IPlayerConfigAPI.SetResult used = playerConfig.tryToSet(PlayerConfigOptions.USED_SUBCLAIM, t.teamSubId());
            helper.assertTrue(used == IPlayerConfigAPI.SetResult.SUCCESS, "expected selecting the team sub-claim to work, got " + used);
            assertLimits(helper, t.playerLimits(), 3, 7, 1, 3, "with the team sub-config selected");

            t.claim(t.ownerId, t.teamSub(t.ownerId), x0 + 6, 0, true);
            assertLimits(helper, t.playerLimits(), 4, 7, 2, 3, "after a teammate made a forceloaded team claim");
            claimsAPI(t.server).unclaim(OVERWORLD, x0 + 2, 0);
            assertLimits(helper, t.playerLimits(), 3, 7, 2, 3, "after the player's team claim was unclaimed");

            GameProfile joiner = profile("T65_Joiner");
            helper.assertTrue(t.party.addMember(joiner.getId(), PartyMemberRank.MEMBER, joiner.getName()) != null,
                    "expected party.addMember to succeed");
            TeamClaimsCommon.getTeamConfigManager().processPendingEvents();
            assertLimits(helper, t.playerLimits(), 3, 12, 2, 4, "after a third member joined");

            playerConfig.tryToReset(PlayerConfigOptions.USED_SUBCLAIM);
            assertLimits(helper, t.playerLimits(), 2, privateClaimLimit, 1, privateForceloadLimit, "back on the main config");

            // Stock behaviour without Team Claims: everything the player technically owns against their own limit
            t.claim(t.playerId, t.teamSub(t.playerId), x0 + 2, 0, false);
            TeamClaimsIntegration.setHandler(null);
            try {
                assertLimits(helper, t.playerLimits(), 3, privateClaimLimit, 1, privateForceloadLimit, "without the Team Claims handler");
            } finally {
                TeamClaimsIntegration.setHandler(handler);
            }
            helper.succeed();
        } finally {
            TeamClaimsIntegration.setHandler(handler);
            try {
                playerConfig.tryToReset(PlayerConfigOptions.USED_SUBCLAIM);
            } catch (Exception ignored) {
            }
            t.cleanup(x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 3, 0, x0 + 4, 0, x0 + 5, 0, x0 + 6, 0);
        }
    }

    /**
     * Area claims use the budget of their sub-config, chunk by chunk: a 3x3 team area claim with a team claim limit of
     * 3 claims exactly 3 chunks and stops at the limit, whatever private room the player has; a 2x2 private area claim
     * of the same player with a private claim limit of 2 then claims exactly 2, whatever the team's numbers are.
     */
    public static void areaClaimsUseTheBudgetOfTheirSubConfig(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        GameProfile ownerProfile = profile("T73_Owner");
        UUID ownerId = ownerProfile.getId();
        int x0 = 73000;
        IServerPartyAPI party = createPartyWithTeam(server, ownerProfile, List.of(profile("T73_Member")));
        Runnable cleanup = () -> {
            resetBonusQuiet(server, ownerId);
            for (int x = x0; x <= x0 + 2; x++)
                for (int z = 0; z <= 6; z++)
                    unclaimQuiet(server, x, z);
            cleanupParty(server, party);
        };
        AtomicReference<AreaClaimResult> teamArea = new AtomicReference<>();
        try {
            cm.setBudgetSettingsOverride(party.getId(), new BudgetSettings(2, 3, 0, 10, 0, 168));
            setPrivateLimit(helper, server, ownerId, PlayerConfigOptions.BONUS_CHUNK_CLAIMS, 2);
            claimsAPI(server).tryToClaimArea(OVERWORLD, ownerId, teamSub(server, party, ownerId), OVERWORLD, x0 + 1, 1,
                    x0, 0, x0 + 2, 2, false, teamArea::set);
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
        helper.runAfterDelay(3, () -> {
            AtomicReference<AreaClaimResult> privateArea = new AtomicReference<>();
            try {
                helper.assertTrue(teamArea.get() != null, "expected the team area claim to have finished within 3 ticks");
                BudgetInfo budget = cm.getBudgetInfo(ownerId);
                helper.assertTrue(budget.teamClaims() == 3 && budget.privateClaims() == 0,
                        "expected the team area claim to stop at the team limit of 3 (private limit 2), got " + budget);
                helper.assertTrue(teamArea.get().getResultTypesStream().anyMatch(type -> type == ClaimResult.Type.CLAIM_LIMIT_REACHED)
                                && teamArea.get().getResultTypesStream().anyMatch(type -> type == ClaimResult.Type.SUCCESSFUL_CLAIM),
                        "expected the team area claim to report claims and the reached limit, got "
                                + teamArea.get().getResultTypesStream().toList());
                claimsAPI(server).tryToClaimArea(OVERWORLD, ownerId, -1, OVERWORLD, x0, 5, x0, 5, x0 + 1, 6, false, privateArea::set);
            } catch (RuntimeException | Error e) {
                cleanup.run();
                throw e;
            }
            helper.runAfterDelay(3, () -> {
                try {
                    helper.assertTrue(privateArea.get() != null, "expected the private area claim to have finished within 3 ticks");
                    BudgetInfo budget = cm.getBudgetInfo(ownerId);
                    helper.assertTrue(budget.teamClaims() == 3 && budget.privateClaims() == 2,
                            "expected the private area claim to stop at the private limit of 2 with the team at its limit, got " + budget);
                    helper.assertTrue(privateArea.get().getResultTypesStream().anyMatch(type -> type == ClaimResult.Type.CLAIM_LIMIT_REACHED),
                            "expected the private area claim to report the reached limit, got "
                                    + privateArea.get().getResultTypesStream().toList());
                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            });
        });
    }

    /**
     * OPAC's penalty for a player above their claim limit follows the private budget only. The owner of a team claim
     * is at a private limit of 0: with nothing but the team claim they are not over it (stock would count the team
     * claim), with a private claim they are. Stock then closes every claim of that owner to everybody but for one
     * tick per cooldown; the team claim stays open for the teammate tick after tick, as it belongs to the team's
     * budget. Without the Team Claims handler the stock penalty is back for it.
     */
    public static void teamClaimStaysOpenWhenOwnerIsOverPrivateLimit(GameTestHelper helper) {
        Team t = Team.create(helper, "T72", 72000);
        int x0 = t.x0;
        TeamClaimsIntegration.TeamClaimsHandler handler = TeamClaimsIntegration.getHandler();
        Runnable cleanup = () -> {
            TeamClaimsIntegration.setHandler(handler);
            resetBonusQuiet(t.server, t.ownerId);
            t.cleanup(x0, 0, x0 + 1, 0);
        };
        IPlayerConfigAPI teamClaimConfig;
        try {
            IPlayerConfig ownerConfig = ServerData.from(t.server).getPlayerConfigManager().getLoadedConfig(t.ownerId);
            t.claim(t.ownerId, t.teamSub(t.ownerId), x0, 0, false);
            setPrivateLimit(helper, t.server, t.ownerId, PlayerConfigOptions.BONUS_CHUNK_CLAIMS, 0);
            helper.assertTrue(!ServerPlayerConfigUtils.isOverClaimLimit(ownerConfig),
                    "expected a player with only a team claim not to be over a private claim limit of 0");
            t.claim(t.ownerId, -1, x0 + 1, 0, false);
            helper.assertTrue(ServerPlayerConfigUtils.isOverClaimLimit(ownerConfig),
                    "expected a player with a private claim to be over a private claim limit of 0");
            helper.assertTrue(ServerConfig.CONFIG.overLimitClaimAccessCooldown.get() > 0,
                    "test precondition: expected OPAC's overLimitClaimAccessCooldown to be above 0");

            teamClaimConfig = OpenPACServerAPI.get(t.server).getChunkProtection().getConfig(claimsAPI(t.server).get(OVERWORLD, x0, 0));
            helper.assertTrue(hasAccess(t, teamClaimConfig), "expected the teammate to have access to the team claim");
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(hasAccess(t, teamClaimConfig),
                        "expected the teammate to still have access to the team claim a tick later, although its owner is over their private limit");
                // Stock: the first access is let through and starts the cooldown
                TeamClaimsIntegration.setHandler(null);
                try {
                    helper.assertTrue(hasAccess(t, teamClaimConfig), "expected stock OPAC to allow the first access of a cooldown");
                } finally {
                    TeamClaimsIntegration.setHandler(handler);
                }
            } catch (RuntimeException | Error e) {
                cleanup.run();
                throw e;
            }
            helper.runAfterDelay(1, () -> {
                try {
                    boolean stockAccess;
                    TeamClaimsIntegration.setHandler(null);
                    try {
                        stockAccess = hasAccess(t, teamClaimConfig);
                    } finally {
                        TeamClaimsIntegration.setHandler(handler);
                    }
                    helper.assertTrue(!stockAccess,
                            "test precondition: expected stock OPAC to close the claims of a player over their limit during the cooldown");
                    helper.assertTrue(hasAccess(t, teamClaimConfig),
                            "expected the team claim to stay open for the teammate during the cooldown");
                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            });
        });
    }

    private static boolean hasAccess(Team t, IPlayerConfigAPI claimConfig) {
        return OpenPACServerAPI.get(t.server).getChunkProtection().hasChunkAccess(claimConfig, t.player);
    }

    // ==================== Tests: party removal ====================

    /**
     * When the party is removed, a member's former team claims only stay as far as the member has private room: with
     * a private claim limit of 4 and one private claim, the 3 oldest of 5 team claims are kept as private claims and
     * the 2 newest are unclaimed; with a private forceload limit of 2 and one private forceload, the forceload that
     * was made first is kept and the other kept claim loses its forceload but stays claimed. The online member gets
     * one chat line with the numbers (and one about the forceload), the offline owner simply keeps theirs.
     */
    public static void partyRemovalKeepsWithinPrivateRoom(GameTestHelper helper) {
        Team t = Team.create(helper, "T66", 66000);
        int x0 = t.x0;
        try {
            int teamSub = t.teamSub(t.playerId);
            t.claim(t.playerId, teamSub, x0, 0, false);//c1
            t.claim(t.playerId, teamSub, x0 + 1, 0, true);//c2, the 1st forceload
            t.claim(t.playerId, teamSub, x0 + 2, 0, false);//c3
            t.claim(t.playerId, teamSub, x0 + 3, 0, true);//c4, the 2nd forceload
            t.claim(t.playerId, teamSub, x0 + 4, 0, false);//c5
            t.claim(t.playerId, teamSub, x0, 0, true);//c1 again, now forceloaded: the 3rd forceload, still the oldest claim
            t.claim(t.playerId, -1, x0 + 5, 0, true);//a private claim, forceloaded
            t.claim(t.ownerId, t.teamSub(t.ownerId), x0 + 6, 0, false);//the owner's team claim
            setPrivateLimit(helper, t.server, t.playerId, PlayerConfigOptions.BONUS_CHUNK_CLAIMS, 4);
            setPrivateLimit(helper, t.server, t.playerId, PlayerConfigOptions.BONUS_CHUNK_FORCELOADS, 2);
            TeamData teamData = t.cm.getTeamData(t.party.getId());
            helper.assertTrue(teamData != null && teamData.getClaimCount() == 6 && teamData.getForceloadCount() == 3,
                    "test setup: expected 6 team claims, 3 of them forceloaded, got " + (teamData == null ? null
                            : teamData.getClaimCount() + " / " + teamData.getForceloadCount()));
            helper.assertTrue(List.copyOf(teamData.getTrackedClaims()).get(0).equals(new ClaimPos(OVERWORLD, x0, 0)),
                    "test setup: expected the re-forceloaded claim to still be the oldest team claim");

            UUID partyId = t.party.getId();
            disbandPartyQuiet(t.server, partyId);
            List<String> chat = captureChat(t.server, t.player, () -> TeamClaimsCommon.getTeamConfigManager().processPendingEvents());

            helper.assertTrue(t.cm.getTeamData(partyId) == null, "expected the team claims of the removed party not to be tracked any more");
            assertClaim(helper, t.server, x0, 0, t.playerId, false, "c1 (kept, its forceload was the newest)");
            assertClaim(helper, t.server, x0 + 1, 0, t.playerId, true, "c2 (kept, the oldest forceload)");
            assertClaim(helper, t.server, x0 + 2, 0, t.playerId, false, "c3 (kept)");
            assertClaim(helper, t.server, x0 + 3, 0, null, false, "c4 (unclaimed)");
            assertClaim(helper, t.server, x0 + 4, 0, null, false, "c5 (unclaimed)");
            assertClaim(helper, t.server, x0 + 5, 0, t.playerId, true, "the private claim");
            assertClaim(helper, t.server, x0 + 6, 0, t.ownerId, false, "the offline owner's former team claim");
            BudgetInfo budget = t.cm.getBudgetInfo(t.playerId);
            helper.assertTrue(!budget.inTeam() && budget.privateClaims() == 4 && budget.privateClaimLimit() == 4
                            && budget.privateForceloads() == 2 && budget.privateForceloadLimit() == 2 && budget.ownedTeamClaims() == 0,
                    "expected the former member to be exactly at their private limits (4 claims, 2 forceloads), got " + budget);
            helper.assertTrue(chat.contains(localized(t.server, KEY + "party_removed_claims", "3", "2"))
                            && chat.contains(localized(t.server, KEY + "party_removed_forceloads", "1")),
                    "expected the member to be told that 3 claims were kept, 2 removed and 1 forceload turned off, got " + chat);
            helper.assertTrue(chat.stream().filter(line -> line.equals(localized(t.server, KEY + "party_removed_claims", "3", "2"))).count() == 1,
                    "expected exactly one line about the kept and removed claims, got " + chat);
            helper.succeed();
        } finally {
            t.cleanup(x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 3, 0, x0 + 4, 0, x0 + 5, 0, x0 + 6, 0);
        }
    }

    // ==================== Tests: over-limit grace ====================

    /**
     * The whole life of an over-limit deadline, with three members and a team claim limit of 5 (3 with two members):
     * <ol>
     *     <li>a member with two of the five team claims leaves: their claims go to the owner (staying where they are in
     *     the claim order), the team is 2 above its limit and gets a deadline 48 hours from now; new team claims are
     *     refused;</li>
     *     <li>the deadline and the claim order survive the saved data being written and read (and validated) again;</li>
     *     <li>a running deadline keeps its time at later checks;</li>
     *     <li>a new member joins: the team is within its limit again and the deadline is cancelled;</li>
     *     <li>that member leaves again: a new deadline, 48 hours from then;</li>
     *     <li>nothing is removed a millisecond before the deadline; at the deadline exactly the two newest team claims
     *     are unclaimed, the older three stay, and the deadline is gone.</li>
     * </ol>
     */
    public static void overLimitDeadlineLifecycle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        GameProfile ownerProfile = profile("T67_Owner");
        GameProfile leaverProfile = profile("T67_Leaver");
        GameProfile memberProfile = profile("T67_Member");
        UUID ownerId = ownerProfile.getId();
        UUID leaverId = leaverProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 67000;
        long[] now = {System.currentTimeMillis()};
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(leaverProfile, memberProfile));
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(2, 3, 2, 10, 2, 48));
            cm.setClock(() -> now[0]);
            int ownerSub = teamSub(server, party, ownerId);
            int leaverSub = teamSub(server, party, leaverId);
            // t1..t5 at x0..x0+4, claimed in that order; t2 and t4 by the member who is going to leave
            List<ClaimPos> order = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                boolean byLeaver = i == 1 || i == 3;
                assertResult(helper, doClaim(server, byLeaver ? leaverId : ownerId, byLeaver ? leaverSub : ownerSub, x0 + i, 0),
                        ClaimResult.Type.SUCCESSFUL_CLAIM, "team claim t" + (i + 1));
                order.add(new ClaimPos(OVERWORLD, x0 + i, 0));
            }
            assertResult(helper, doClaim(server, ownerId, ownerSub, x0 + 5, 0), ClaimResult.Type.CLAIM_LIMIT_REACHED,
                    "a 6th team claim at the limit of 5");
            helper.assertTrue(cm.getBudgetInfo(ownerId).overLimitDeadline() == 0, "expected no deadline for a team at its limit");

            // 1) the member leaves
            helper.assertTrue(party.removeMember(leaverId) != null, "expected party.removeMember to succeed");
            tcm.processPendingEvents();
            long firstDeadline = now[0] + 48 * HOUR;
            BudgetInfo budget = cm.getBudgetInfo(ownerId);
            helper.assertTrue(budget.memberCount() == 2 && budget.teamClaims() == 5 && budget.teamClaimLimit() == 3
                            && budget.claimsOverLimit() == 2 && budget.overLimitDeadline() == firstDeadline
                            && budget.ownedTeamClaims() == 5 && budget.forceloadOverLimitDeadline() == 0,
                    "expected 5 / 3 team claims, all of the owner now, and a deadline 48 hours from now, got " + budget);
            TeamData teamData = cm.getTeamData(partyId);
            helper.assertTrue(List.copyOf(teamData.getTrackedClaims()).equals(order),
                    "expected the hand-over to the owner to keep the claim order, got " + teamData.getTrackedClaims());
            assertResult(helper, doClaim(server, ownerId, ownerSub, x0 + 5, 0), ClaimResult.Type.CLAIM_LIMIT_REACHED,
                    "a new team claim of a team above its limit");

            // 2) written and read again: plain NBT, and through the validation a server start does
            HolderLookup.Provider registries = helper.getLevel().registryAccess();
            TeamData reread = TeamClaimSavedData.load(cm.getSavedData().save(new CompoundTag(), registries), registries).getTeamData(partyId);
            helper.assertTrue(reread != null && reread.getClaimDeadline() == firstDeadline && reread.getLoadedClaims().equals(order),
                    "expected the deadline and the claim order in the saved data, got deadline "
                            + (reread == null ? null : reread.getClaimDeadline() + " and " + reread.getLoadedClaims()));
            cm.reloadTeamDataForTesting(partyId);
            teamData = cm.getTeamData(partyId);
            helper.assertTrue(teamData.getClaimDeadline() == firstDeadline && List.copyOf(teamData.getTrackedClaims()).equals(order)
                            && teamData.getClaimCountOf(ownerId) == 5,
                    "expected the deadline, the claim order and the owners to survive a reload, got deadline "
                            + teamData.getClaimDeadline() + " and " + teamData.getTrackedClaims());

            // 3) a running deadline keeps its time
            now[0] += 10 * HOUR;
            cm.checkOverLimit(partyId);
            helper.assertTrue(cm.getTeamData(partyId).getClaimDeadline() == firstDeadline && cm.getTeamData(partyId).getClaimCount() == 5,
                    "expected a running deadline to keep its time and nothing to be removed before it");

            // 4) a new member joins: within the limit again
            GameProfile joiner = profile("T67_Joiner");
            helper.assertTrue(party.addMember(joiner.getId(), PartyMemberRank.MEMBER, joiner.getName()) != null,
                    "expected party.addMember to succeed");
            tcm.processPendingEvents();
            budget = cm.getBudgetInfo(ownerId);
            helper.assertTrue(budget.teamClaimLimit() == 5 && budget.overLimitDeadline() == 0 && budget.claimsOverLimit() == 0
                            && budget.teamClaims() == 5,
                    "expected the deadline to be cancelled with the team back within its limit, got " + budget);
            // Long after the old deadline nothing happens, as there is none
            now[0] += 40 * HOUR;
            cm.checkOverLimit(partyId);
            helper.assertTrue(cm.getTeamData(partyId).getClaimCount() == 5, "expected nothing to be removed after a cancelled deadline");

            // 5) the new member leaves again: a new deadline from now
            helper.assertTrue(party.removeMember(joiner.getId()) != null, "expected party.removeMember to succeed");
            tcm.processPendingEvents();
            long secondDeadline = now[0] + 48 * HOUR;
            helper.assertTrue(cm.getTeamData(partyId).getClaimDeadline() == secondDeadline,
                    "expected a new deadline 48 hours after the second leave, got " + cm.getTeamData(partyId).getClaimDeadline()
                            + " instead of " + secondDeadline);

            // 6) just before and at the deadline
            now[0] = secondDeadline - 1;
            cm.checkOverLimit(partyId);
            helper.assertTrue(cm.getTeamData(partyId).getClaimCount() == 5, "expected nothing to be removed before the deadline");
            now[0] = secondDeadline;
            cm.checkOverLimit(partyId);
            teamData = cm.getTeamData(partyId);
            helper.assertTrue(List.copyOf(teamData.getTrackedClaims()).equals(order.subList(0, 3)) && teamData.getClaimDeadline() == 0,
                    "expected exactly the 3 oldest team claims to be left and no deadline, got " + teamData.getTrackedClaims()
                            + " and deadline " + teamData.getClaimDeadline());
            for (int i = 0; i < 5; i++)
                assertClaim(helper, server, x0 + i, 0, i < 3 ? ownerId : null, false, "t" + (i + 1));
            assertResult(helper, doClaim(server, ownerId, ownerSub, x0 + 5, 0), ClaimResult.Type.CLAIM_LIMIT_REACHED,
                    "a new team claim of a team that is exactly at its limit again");
            helper.succeed();
        } finally {
            cm.setClock(null);
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 3, 0, x0 + 4, 0, x0 + 5, 0);
            cleanupParty(server, party);
        }
    }

    /**
     * What the online members are told, and when: a warning with the excess and the time left when the deadline
     * starts (here because the limit was lowered, as a config change would), a reminder once 24 hours have passed
     * (not earlier) and at login, the good news when the team is back within its limit, and how many team claims were
     * removed when the time is up. {@code /teamclaims info} shows the pending deadline.
     */
    public static void overLimitMessagesReachOnlineMembers(GameTestHelper helper) {
        Team t = Team.create(helper, "T68", 68000);
        int x0 = t.x0;
        long[] now = {System.currentTimeMillis()};
        try {
            UUID partyId = t.party.getId();
            BudgetSettings roomy = new BudgetSettings(2, 3, 0, 10, 0, 72);
            BudgetSettings tight = new BudgetSettings(2, 1, 0, 10, 0, 72);
            t.cm.setBudgetSettingsOverride(partyId, roomy);
            t.cm.setClock(() -> now[0]);
            int teamSub = t.teamSub(t.ownerId);
            for (int i = 0; i < 3; i++) t.claim(t.ownerId, teamSub, x0 + i, 0, false);
            helper.assertTrue(captureChat(t.server, t.player, () -> t.cm.checkOverLimit(partyId)).isEmpty(),
                    "expected no message for a team within its limit");

            long deadline = now[0] + 72 * HOUR;
            t.cm.setBudgetSettingsOverride(partyId, tight);
            List<String> started = captureChat(t.server, t.player, () -> t.cm.checkOverLimit(partyId));
            helper.assertTrue(started.equals(List.of(localized(t.server, KEY + "over_limit_claims_started", "2", "1", "3 d 0 h"))),
                    "expected the warning that 2 team claims are above the limit of 1 with 3 d 0 h left, got " + started);
            helper.assertTrue(started.get(0).contains("/teamclaims convert topersonal") && started.get(0).startsWith("[Team Claims] "),
                    "expected the warning to name /teamclaims convert topersonal as a way out, got " + started);
            helper.assertTrue(captureChat(t.server, t.player, () -> t.cm.checkOverLimit(partyId)).isEmpty(),
                    "expected no second warning at the next check");

            CapturingCommandSource info = new CapturingCommandSource();
            TeamClaimsOverview.showInfo(commandSource(helper, info), null, t.ownerId, null);
            helper.assertTrue(info.received(localized(t.server, KEY + "info_over_limit_claims", "2", "3 d 0 h"))
                            && info.received(localized(t.server, KEY + "info_team_budget", "3 / 1", "0 / 10")),
                    "expected /teamclaims info to show the pending deadline, got " + info.all());

            now[0] += 23 * HOUR + 30 * 60_000;
            helper.assertTrue(captureChat(t.server, t.player, () -> t.cm.checkOverLimit(partyId)).isEmpty(),
                    "expected no reminder before 24 hours have passed");
            List<String> login = captureChat(t.server, t.player, () -> t.cm.onPlayerLogin(t.player));
            helper.assertTrue(login.equals(List.of(localized(t.server, KEY + "over_limit_claims_reminder", "2", "1", "2 d 0 h"))),
                    "expected the reminder at login with 2 d 0 h left (48 h 30 min), got " + login);
            now[0] += 30 * 60_000;
            List<String> reminder = captureChat(t.server, t.player, () -> t.cm.checkOverLimit(partyId));
            helper.assertTrue(reminder.equals(List.of(localized(t.server, KEY + "over_limit_claims_reminder", "2", "1", "2 d 0 h"))),
                    "expected the reminder after 24 hours with 2 d 0 h left, got " + reminder);

            // The other two unit ranges, in the reminder at login and in /teamclaims info: hours and minutes, then minutes
            now[0] = deadline - 5 * HOUR - 10 * 60_000;
            login = captureChat(t.server, t.player, () -> t.cm.onPlayerLogin(t.player));
            helper.assertTrue(login.equals(List.of(localized(t.server, KEY + "over_limit_claims_reminder", "2", "1", "5 h 10 min"))),
                    "expected the reminder at login with 5 h 10 min left, got " + login);
            info = new CapturingCommandSource();
            TeamClaimsOverview.showInfo(commandSource(helper, info), null, t.ownerId, null);
            helper.assertTrue(info.received(localized(t.server, KEY + "info_over_limit_claims", "2", "5 h 10 min")),
                    "expected /teamclaims info to show 5 h 10 min, got " + info.all());
            now[0] = deadline - 20 * 60_000;
            login = captureChat(t.server, t.player, () -> t.cm.onPlayerLogin(t.player));
            helper.assertTrue(login.equals(List.of(localized(t.server, KEY + "over_limit_claims_reminder", "2", "1", "20 min"))),
                    "expected the reminder at login with 20 min left, got " + login);
            info = new CapturingCommandSource();
            TeamClaimsOverview.showInfo(commandSource(helper, info), null, t.ownerId, null);
            helper.assertTrue(info.received(localized(t.server, KEY + "info_over_limit_claims", "2", "20 min")),
                    "expected /teamclaims info to show 20 min, got " + info.all());

            t.cm.setBudgetSettingsOverride(partyId, roomy);
            List<String> resolved = captureChat(t.server, t.player, () -> t.cm.checkOverLimit(partyId));
            helper.assertTrue(resolved.equals(List.of(localized(t.server, KEY + "over_limit_claims_resolved", "3", "3"))),
                    "expected the message that the team is within its limit again, got " + resolved);
            helper.assertTrue(t.cm.getTeamData(partyId).getClaimDeadline() == 0, "expected the deadline to be cancelled");

            t.cm.setBudgetSettingsOverride(partyId, tight);
            t.cm.checkOverLimit(partyId);
            now[0] += 72 * HOUR;
            List<String> enforced = captureChat(t.server, t.player, () -> t.cm.checkOverLimit(partyId));
            helper.assertTrue(enforced.equals(List.of(localized(t.server, KEY + "over_limit_claims_enforced", "2", "1"))),
                    "expected the message that the 2 newest team claims were unclaimed, got " + enforced);
            assertClaim(helper, t.server, x0, 0, t.ownerId, false, "the oldest team claim");
            assertClaim(helper, t.server, x0 + 1, 0, null, false, "the 2nd team claim");
            assertClaim(helper, t.server, x0 + 2, 0, null, false, "the newest team claim");

            assertDurationUnits(helper, t.server);
            helper.succeed();
        } finally {
            t.cm.setClock(null);
            t.cleanup(x0, 0, x0 + 1, 0, x0 + 2, 0);
        }
    }

    /**
     * The time left in the over-limit texts is one value in the largest sensible unit pair, with the thresholds and
     * the rounding (up to the minute, at least one minute) of the party screen: days and hours from one day, hours and
     * minutes from one hour, else minutes. Checked at and around both thresholds, and that the duration is translated
     * as the argument of a message, which is how a player without the mod gets it.
     */
    private static void assertDurationUnits(GameTestHelper helper, MinecraftServer server) {
        long minute = 60_000L;
        long day = 24 * HOUR;
        assertDuration(helper, server, 7 * day, "7 d 0 h");
        assertDuration(helper, server, 2 * day + 30 * minute, "2 d 0 h");
        assertDuration(helper, server, day + HOUR + 59 * minute, "1 d 1 h");
        assertDuration(helper, server, day, "1 d 0 h");
        // Rounded up to the minute: a millisecond short of a day is a whole day, a minute and a millisecond short is not
        assertDuration(helper, server, day - 1, "1 d 0 h");
        assertDuration(helper, server, day - minute, "23 h 59 min");
        assertDuration(helper, server, day - minute - 1, "23 h 59 min");
        assertDuration(helper, server, day - minute + 1, "1 d 0 h");
        assertDuration(helper, server, 5 * HOUR + 10 * minute, "5 h 10 min");
        assertDuration(helper, server, 5 * HOUR + 10 * minute + 1, "5 h 11 min");
        assertDuration(helper, server, HOUR, "1 h 0 min");
        assertDuration(helper, server, HOUR - 1, "1 h 0 min");
        assertDuration(helper, server, HOUR - minute, "59 min");
        assertDuration(helper, server, HOUR - minute - 1, "59 min");
        assertDuration(helper, server, HOUR - minute + 1, "1 h 0 min");
        assertDuration(helper, server, 90_000, "2 min");
        assertDuration(helper, server, minute, "1 min");
        assertDuration(helper, server, minute + 1, "2 min");
        assertDuration(helper, server, 1, "1 min");
        assertDuration(helper, server, 0, "1 min");
    }

    /** {@code millis} as a nested duration is {@code expected}, alone and as the argument of the over-limit info text. */
    private static void assertDuration(GameTestHelper helper, MinecraftServer server, long millis, String expected) {
        Component duration = TeamClaimManager.durationOf(millis);
        String alone = OpenPACServerAPI.get(server).getAdaptiveTextLocalizer().getFor(null, duration).getString();
        helper.assertTrue(alone.equals(expected), "expected " + millis + " ms to be shown as '" + expected + "', got '" + alone + "'");
        String nested = OpenPACServerAPI.get(server).getAdaptiveTextLocalizer()
                .getFor(null, KEY + "info_over_limit_claims", "2", duration).getString();
        String literal = localized(server, KEY + "info_over_limit_claims", "2", expected);
        helper.assertTrue(nested.equals(literal) && nested.contains(" in " + expected + " "),
                "expected " + millis + " ms nested in the info text to read '" + literal + "', got '" + nested + "'");
    }

    /**
     * Team forceloads above the team forceload limit get their own deadline: a member leaves, the limit drops from 2
     * to 1, and when the deadline (which survives a reload, as does the order of the forceloads) passes, the most
     * recently made team forceload is turned off. That is the one of the older claim here. All claims stay, and the
     * team claims never had a deadline.
     */
    public static void forceloadOverLimitTurnsNewestOff(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        GameProfile ownerProfile = profile("T69_Owner");
        GameProfile leaverProfile = profile("T69_Leaver");
        UUID ownerId = ownerProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 69000;
        long[] now = {System.currentTimeMillis()};
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(leaverProfile, profile("T69_Member")));
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(2, 10, 0, 1, 1, 48));
            cm.setClock(() -> now[0]);
            int ownerSub = teamSub(server, party, ownerId);
            for (int i = 0; i < 3; i++)
                assertResult(helper, doClaim(server, ownerId, ownerSub, x0 + i, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "team claim " + i);
            // Forceloaded in the order t2, t1: the newest forceload is the one of the oldest claim
            assertResult(helper, doForceload(server, ownerId, x0 + 1, 0, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD, "forceload of t2");
            assertResult(helper, doForceload(server, ownerId, x0, 0, true), ClaimResult.Type.SUCCESSFUL_FORCELOAD, "forceload of t1");
            assertResult(helper, doForceload(server, ownerId, x0 + 2, 0, true), ClaimResult.Type.FORCELOAD_LIMIT_REACHED,
                    "a 3rd team forceload at the limit of 2");
            List<ClaimPos> forceloadOrder = List.of(new ClaimPos(OVERWORLD, x0 + 1, 0), new ClaimPos(OVERWORLD, x0, 0));

            helper.assertTrue(party.removeMember(leaverProfile.getId()) != null, "expected party.removeMember to succeed");
            TeamClaimsCommon.getTeamConfigManager().processPendingEvents();
            long deadline = now[0] + 48 * HOUR;
            BudgetInfo budget = cm.getBudgetInfo(ownerId);
            helper.assertTrue(budget.teamForceloads() == 2 && budget.teamForceloadLimit() == 1 && budget.forceloadsOverLimit() == 1
                            && budget.forceloadOverLimitDeadline() == deadline && budget.overLimitDeadline() == 0
                            && budget.claimsOverLimit() == 0,
                    "expected 2 / 1 team forceloads with a deadline 48 hours from now and no deadline for the claims, got " + budget);

            cm.reloadTeamDataForTesting(partyId);
            TeamData teamData = cm.getTeamData(partyId);
            helper.assertTrue(teamData.getForceloadDeadline() == deadline && List.copyOf(teamData.getForceloadedClaims()).equals(forceloadOrder),
                    "expected the forceload deadline and the forceload order to survive a reload, got "
                            + teamData.getForceloadDeadline() + " and " + teamData.getForceloadedClaims());

            now[0] = deadline - 1;
            cm.checkOverLimit(partyId);
            helper.assertTrue(cm.getTeamData(partyId).getForceloadCount() == 2, "expected nothing to change before the deadline");
            now[0] = deadline;
            cm.checkOverLimit(partyId);
            teamData = cm.getTeamData(partyId);
            helper.assertTrue(teamData.getForceloadDeadline() == 0 && teamData.getClaimCount() == 3
                            && List.copyOf(teamData.getForceloadedClaims()).equals(forceloadOrder.subList(0, 1)),
                    "expected only the older forceload to be left, all 3 claims and no deadline, got "
                            + teamData.getForceloadedClaims() + " of " + teamData.getClaimCount() + " claims");
            assertClaim(helper, server, x0, 0, ownerId, false, "t1 (its forceload was the newest)");
            assertClaim(helper, server, x0 + 1, 0, ownerId, true, "t2 (the older forceload)");
            assertClaim(helper, server, x0 + 2, 0, ownerId, false, "t3");
            helper.succeed();
        } finally {
            cm.setClock(null);
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0);
            if (party != null) cm.deactivateTeamForceLoads(party.getId());
            cleanupParty(server, party);
        }
    }

    /**
     * With {@code overLimitGraceHours = 0} there is no waiting: the check that finds the team above its limit removes
     * the excess. A member leaving a three-member team (limit 3 to 2) costs the newest team claim right then, and the
     * next one leaving (a one-member team has a limit of 0) the rest. No deadline is ever stored.
     */
    public static void graceZeroRemovesAtNextCheck(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        GameProfile ownerProfile = profile("T70_Owner");
        GameProfile firstLeaver = profile("T70_Leaver1");
        GameProfile secondLeaver = profile("T70_Leaver2");
        UUID ownerId = ownerProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 70000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(firstLeaver, secondLeaver));
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(2, 2, 1, 10, 0, 0));
            int ownerSub = teamSub(server, party, ownerId);
            for (int i = 0; i < 3; i++)
                assertResult(helper, doClaim(server, ownerId, ownerSub, x0 + i, 0), ClaimResult.Type.SUCCESSFUL_CLAIM, "team claim " + i);
            cm.checkOverLimit(partyId);
            helper.assertTrue(cm.getTeamData(partyId).getClaimCount() == 3, "expected a team at its limit to keep everything");

            helper.assertTrue(party.removeMember(firstLeaver.getId()) != null, "expected party.removeMember to succeed");
            tcm.processPendingEvents();
            TeamData teamData = cm.getTeamData(partyId);
            helper.assertTrue(teamData.getClaimCount() == 2 && teamData.getClaimDeadline() == 0,
                    "expected the newest team claim to be removed at once and no deadline, got " + teamData.getClaimCount()
                            + " claims and deadline " + teamData.getClaimDeadline());
            assertClaim(helper, server, x0, 0, ownerId, false, "the oldest team claim");
            assertClaim(helper, server, x0 + 1, 0, ownerId, false, "the 2nd team claim");
            assertClaim(helper, server, x0 + 2, 0, null, false, "the newest team claim");

            helper.assertTrue(party.removeMember(secondLeaver.getId()) != null, "expected party.removeMember to succeed");
            tcm.processPendingEvents();
            BudgetInfo budget = cm.getBudgetInfo(ownerId);
            helper.assertTrue(budget.memberCount() == 1 && budget.teamClaimLimit() == 0 && budget.teamClaims() == 0
                            && budget.overLimitDeadline() == 0,
                    "expected a one-member team to have a limit of 0 and to have lost its team claims, got " + budget);
            assertClaim(helper, server, x0, 0, null, false, "the oldest team claim");
            assertClaim(helper, server, x0 + 1, 0, null, false, "the 2nd team claim");
            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0);
            cleanupParty(server, party);
        }
    }

    /**
     * Saved data in the format of the version with one shared budget (two position lists per team, nothing else)
     * loads: the positions in their stored order, no deadlines. Written again it has the same lists in the same order
     * and still no deadline entries; with a deadline running, the new entries are there and read back.
     */
    public static void oldFormatSavedDataLoads(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        UUID partyId = UUID.randomUUID();
        List<ClaimPos> claims = List.of(new ClaimPos(OVERWORLD, 71007, 3), new ClaimPos(OVERWORLD, 71001, -2),
                new ClaimPos(OVERWORLD, 71004, 9));
        List<ClaimPos> forceloads = List.of(claims.get(2), claims.get(0));
        CompoundTag team = new CompoundTag();
        team.putUUID("partyId", partyId);
        team.put("trackedClaims", positions(claims));
        team.put("forceLoadedChunks", positions(forceloads));
        ListTag teams = new ListTag();
        teams.add(team);
        CompoundTag oldFormat = new CompoundTag();
        oldFormat.put("teams", teams);

        TeamClaimSavedData loaded = TeamClaimSavedData.load(oldFormat, registries);
        TeamData teamData = loaded.getTeamData(partyId);
        helper.assertTrue(teamData != null && teamData.getLoadedClaims().equals(claims) && teamData.getClaimDeadline() == 0
                        && teamData.getForceloadDeadline() == 0 && !teamData.hasOverLimitDeadline(),
                "expected the old format to load with its claim order and without deadlines, got "
                        + (teamData == null ? null : teamData.getLoadedClaims()));

        CompoundTag rewritten = loaded.save(new CompoundTag(), registries).getList("teams", 10).getCompound(0);
        helper.assertTrue(rewritten.getList("trackedClaims", 10).equals(positions(claims))
                        && rewritten.getList("forceLoadedChunks", 10).equals(positions(forceloads)),
                "expected both position lists to be written back in the same order, got " + rewritten);
        helper.assertTrue(!rewritten.contains("overLimitDeadline") && !rewritten.contains("forceloadOverLimitDeadline"),
                "expected no deadline entries without a running deadline, got " + rewritten);

        CompoundTag withDeadlines = rewritten.copy();
        withDeadlines.putLong("overLimitDeadline", 1234567890123L);
        withDeadlines.putLong("forceloadOverLimitDeadline", 9876543210L);
        TeamData reread = TeamData.load(TeamData.load(withDeadlines).save());
        helper.assertTrue(reread.getClaimDeadline() == 1234567890123L && reread.getForceloadDeadline() == 9876543210L
                        && reread.getLoadedClaims().equals(claims),
                "expected both deadlines and the claim order to survive being written and read, got "
                        + reread.getClaimDeadline() + " / " + reread.getForceloadDeadline());
        helper.succeed();
    }

    // ==================== Helpers ====================

    static int teamSub(MinecraftServer server, IServerPartyAPI party, UUID memberId) {
        return teamSubIndexOf(server, TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId(), memberId);
    }

    private static void assertResult(GameTestHelper helper, ClaimResult<?> result, ClaimResult.Type expected, String what) {
        helper.assertTrue(result.getResultType() == expected,
                "expected " + what + " to give " + expected + ", got " + result.getResultType());
    }

    /** The claim at chunk (x, z): of {@code expectedOwner} with that forceload state, or none if that is null. */
    private static void assertClaim(GameTestHelper helper, MinecraftServer server, int x, int z, @Nullable UUID expectedOwner,
            boolean expectedForceloaded, String what) {
        IPlayerChunkClaimAPI claim = claimsAPI(server).get(OVERWORLD, x, z);
        boolean matches = expectedOwner == null ? claim == null
                : claim != null && expectedOwner.equals(claim.getPlayerId()) && claim.isForceloadable() == expectedForceloaded;
        helper.assertTrue(matches, "expected " + what + " at (" + x + ", " + z + ") to be "
                + (expectedOwner == null ? "unclaimed" : "a claim of " + expectedOwner + (expectedForceloaded ? ", forceloaded" : ", not forceloaded"))
                + ", got " + (claim == null ? "no claim" : claim.getPlayerId() + ", forceloaded " + claim.isForceloadable()));
    }

    private static void assertSub(GameTestHelper helper, MinecraftServer server, int x, int expectedSub, String what) {
        IPlayerChunkClaimAPI claim = claimsAPI(server).get(OVERWORLD, x, 0);
        helper.assertTrue(claim != null && claim.getSubConfigIndex() == expectedSub,
                "expected claim " + what + " to have the sub-config index " + expectedSub + ", got "
                        + (claim == null ? "no claim" : claim.getSubConfigIndex()));
    }

    private static void assertBudget(GameTestHelper helper, BudgetInfo budget, int privateClaims, int teamClaims, String stage) {
        helper.assertTrue(budget.privateClaims() == privateClaims && budget.teamClaims() == teamClaims,
                stage + ": expected " + privateClaims + " private and " + teamClaims + " team claim(s), got " + budget);
    }

    /** The member count and both team limits of the player's budget info, against the given settings. */
    private static void assertLimits(GameTestHelper helper, TeamClaimManager cm, UUID playerId, BudgetSettings settings,
            int members, String stage) {
        BudgetInfo budget = cm.getBudgetInfo(playerId);
        helper.assertTrue(budget.inTeam() && budget.memberCount() == members && budget.teamClaimLimit() == settings.claimLimit(members)
                        && budget.teamForceloadLimit() == settings.forceloadLimit(members),
                stage + ": expected " + members + " member(s) and the team limits " + settings.claimLimit(members) + " / "
                        + settings.forceloadLimit(members) + ", got " + budget);
    }

    private static void assertLimits(GameTestHelper helper, ClaimingModeLimits limits, int claimCount, int claimLimit,
            int forceloadCount, int forceloadLimit, String stage) {
        helper.assertTrue(limits.claimCount == claimCount && limits.claimLimit == claimLimit
                        && limits.forceloadCount == forceloadCount && limits.forceloadLimit == forceloadLimit,
                stage + ": expected the claim limits packet to carry claims " + claimCount + " / " + claimLimit + " and forceloads "
                        + forceloadCount + " / " + forceloadLimit + ", got claims " + limits.claimCount + " / " + limits.claimLimit
                        + " and forceloads " + limits.forceloadCount + " / " + limits.forceloadLimit);
    }

    /** Sets the full private limit of a player to {@code target} with the matching bonus option. */
    private static void setPrivateLimit(GameTestHelper helper, MinecraftServer server, UUID playerId,
            IPlayerConfigOptionSpecAPI<Integer> bonusOption, int target) {
        boolean claims = bonusOption == PlayerConfigOptions.BONUS_CHUNK_CLAIMS;
        int current = claims ? claimsAPI(server).getPlayerFullClaimLimit(playerId) : claimsAPI(server).getPlayerFullForceloadLimit(playerId);
        IPlayerConfigAPI config = configManager(server).getLoadedConfig(playerId);
        IPlayerConfigAPI.SetResult result = config.tryToSet(bonusOption, config.getEffective(bonusOption) + target - current);
        int limit = claims ? claimsAPI(server).getPlayerFullClaimLimit(playerId) : claimsAPI(server).getPlayerFullForceloadLimit(playerId);
        helper.assertTrue(result == IPlayerConfigAPI.SetResult.SUCCESS && limit == target,
                "test setup: expected the private limit to be set to " + target + " with " + bonusOption.getId() + ", got "
                        + result + " and " + limit);
    }

    private static void resetBonusQuiet(MinecraftServer server, UUID playerId) {
        for (IPlayerConfigOptionSpecAPI<Integer> bonusOption : List.of(PlayerConfigOptions.BONUS_CHUNK_CLAIMS,
                PlayerConfigOptions.BONUS_CHUNK_FORCELOADS)) {
            try {
                configManager(server).getLoadedConfig(playerId).tryToReset(bonusOption);
            } catch (Exception ignored) {
            }
        }
    }

    /** Removes the budget override of the party and the party itself; never throws. */
    static void cleanupParty(MinecraftServer server, @Nullable IServerPartyAPI party) {
        if (party == null) return;
        try {
            TeamClaimsCommon.getClaimManager().setBudgetSettingsOverride(party.getId(), null);
        } catch (Exception ignored) {
        }
        disbandPartyQuiet(server, party.getId());
    }

    private static ListTag positions(List<ClaimPos> positions) {
        ListTag list = new ListTag();
        for (ClaimPos pos : positions) list.add(pos.save());
        return list;
    }

    /**
     * Runs {@code action} with the chat lines sent to the mock player being recorded instead of going to its
     * connection, and returns their texts. Only for actions that send the player nothing but chat: OPAC's own packets
     * need the real connection, which is back in place when this returns.
     */
    private static List<String> captureChat(MinecraftServer server, ServerPlayer player, Runnable action) {
        ServerGamePacketListenerImpl original = player.connection;
        ChatRecorder recorder = new ChatRecorder(server, player);//also installs itself as player.connection
        try {
            action.run();
        } finally {
            player.connection = original;
        }
        return recorder.chat.stream().map(Component::getString).toList();
    }

    /** Receives what would be sent to the mock player and keeps the chat lines. */
    private static final class ChatRecorder extends ServerGamePacketListenerImpl {
        final List<Component> chat = new ArrayList<>();

        ChatRecorder(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet) {
            record(packet);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            record(packet);
        }

        private void record(Packet<?> packet) {
            if (packet instanceof ClientboundSystemChatPacket chatPacket && !chatPacket.overlay()) chat.add(chatPacket.content());
        }
    }

    /**
     * A team owned by an offline player with the mock player as a MEMBER. The claims of the setups are made with
     * OPAC's low-level {@code claim} (no checks), so they don't depend on the rules under test. {@link #cleanup}
     * unclaims the given chunks and removes everything again.
     */
    static final class Team {
        final GameTestHelper helper;
        final MinecraftServer server;
        final TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        final ServerPlayer player;
        final UUID playerId;
        final UUID ownerId;
        final IServerPartyAPI party;
        final int x0;

        private Team(GameTestHelper helper, ServerPlayer player, UUID ownerId, IServerPartyAPI party, int x0) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.player = player;
            this.playerId = player.getUUID();
            this.ownerId = ownerId;
            this.party = party;
            this.x0 = x0;
        }

        static Team create(GameTestHelper helper, String name, int x0) {
            MinecraftServer server = helper.getLevel().getServer();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            try {
                GameProfile ownerProfile = profile(name + "_Owner");
                IServerPartyAPI party = createPartyWithTeam(server, ownerProfile, List.of(player.getGameProfile()));
                return new Team(helper, player, ownerProfile.getId(), party, x0);
            } catch (RuntimeException | Error e) {
                removePlayerQuiet(server, player);
                throw e;
            }
        }

        String teamSubId() {
            return TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
        }

        int teamSub(UUID memberId) {
            return teamSubIndexOf(server, teamSubId(), memberId);
        }

        void claim(UUID id, int subIndex, int x, int z, boolean forceloaded) {
            IPlayerChunkClaimAPI claim = claimsAPI(server).claim(OVERWORLD, id, subIndex, x, z, forceloaded);
            helper.assertTrue(claim != null, "test setup: expected the claim at (" + x + ", " + z + ") to be made");
        }

        /** What the limits builder of the PLAYER claiming mode puts into the claim limits packet of the mock player. */
        ClaimingModeLimits playerLimits() {
            return ((ClaimingMode) ClaimingModes.PLAYER).getLimitsBuilder().apply(player, ServerData.from(server).getServerClaimsManager());
        }

        void cleanup(int... xzPairs) {
            resetBonusQuiet(server, playerId);
            unclaimQuiet(server, xzPairs);
            try {
                cm.setBudgetSettingsOverride(party.getId(), null);
                cm.deactivateTeamForceLoads(party.getId());
            } catch (Exception ignored) {
            }
            removePlayerQuiet(server, player);
            disbandPartyQuiet(server, party.getId());
        }
    }
}
