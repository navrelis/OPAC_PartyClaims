package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimManager.BudgetInfo;
import xaero.pac.teamclaims.TeamClaimManager.BudgetSettings;
import xaero.pac.teamclaims.TeamClaimManager.ClaimPos;
import xaero.pac.teamclaims.TeamClaimManager.TeamBudget;
import xaero.pac.teamclaims.TeamClaimManager.TeamData;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.config.TeamConfigManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;

/**
 * Dev/test-only, loader-neutral test bodies for team claims together with OPAC's claiming anchors (server option
 * {@code anchorBasedClaiming}): a team claim obeys the anchors of the member who makes it, and what Team Claims does to
 * claims on its own (the hand-over of a leaving member's team claims, team claims above the team limit, the former team
 * claims of a removed party) never unclaims a chunk with an anchor and never leaves the party owner with a claim
 * outside their anchor ranges. Same conventions as {@link TeamClaimsBudgetTestCases}: offline players, a chunk offset
 * of 1000 per test starting at 120000, cleanup on every path, every test registered with the loader's empty structure
 * template and the default timeout.
 * <p>
 * Every test runs in one go, in the tick it starts in, and turns {@code anchorBasedClaiming} on for that time only
 * (the gametests run side by side on one server, and every other test expects the default, off). Anchors are placed
 * with OPAC's low-level {@code addAnchor}, which also makes the chunk a claim of the player if it is not one yet.
 */
public final class TeamClaimsAnchorTestCases {

    private TeamClaimsAnchorTestCases() {}

    // ==================== Tests ====================

    /**
     * A team claim has to be within the range of an anchor of the member who makes it (another member's anchor does not
     * count). Party members still unclaim each other's team claims, except a chunk with an anchor of its owner, which
     * OPAC refuses for everybody. The unclaims that come with the removal of an anchor only ever take the claims of the
     * anchor's owner, team claims included, never those of another party member. With the option off again, the chunk
     * with the anchor is unclaimed like any other team claim.
     */
    public static void teamClaimsObeyTheAnchorsOfTheirOwner(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        GameProfile ownerProfile = profile("T120_Owner");
        GameProfile memberProfile = profile("T120_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 120000;
        boolean anchorsBefore = ServerConfig.CONFIG.anchorBasedClaiming.get();
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            TeamClaimsCommon.getTeamConfigManager().processPendingEvents();
            cm.setBudgetSettingsOverride(party.getId(), new BudgetSettings(1, 100, 0, 10, 0, 168));
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
            int ownerSub = teamSubIndexOf(server, subId, ownerId);
            int memberSub = teamSubIndexOf(server, subId, memberId);
            setAnchorBasedClaiming(true);

            assertType(helper, doClaim(server, memberId, memberSub, x0 + 1, 0), ClaimResult.Type.ANCHOR_TOO_FAR,
                    "a team claim of a member without an anchor");
            addAnchor(helper, server, memberId, x0, 0);
            assertType(helper, doClaim(server, memberId, memberSub, x0 + 1, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "the member's team claim within the range of their anchor");
            assertType(helper, doClaim(server, memberId, memberSub, x0 + 3, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "the member's 2nd team claim within the range of their anchor");
            assertType(helper, doClaim(server, memberId, memberSub, x0 + 100, 0), ClaimResult.Type.ANCHOR_TOO_FAR,
                    "the member's team claim outside the range of their anchor");
            assertType(helper, doClaim(server, ownerId, ownerSub, x0 + 2, 0), ClaimResult.Type.ANCHOR_TOO_FAR,
                    "the owner's team claim within the range of the member's anchor only");
            assertType(helper, doClaim(server, memberId, memberSub, x0, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "making the chunk with the member's anchor a team claim");
            helper.assertTrue(anchors(server, memberId).equals(Set.of(new ChunkPos(x0, 0))),
                    "expected the member's anchor to stay when its chunk becomes a team claim, got " + anchors(server, memberId));
            assertTeamClaims(helper, cm, party.getId(), 3, "after the member's three team claims");

            // Unclaiming a team claim of another member
            assertType(helper, doUnclaim(server, ownerId, x0 + 1, 0), ClaimResult.Type.SUCCESSFUL_UNCLAIM,
                    "the owner's unclaim of the member's team claim");
            assertType(helper, doUnclaim(server, ownerId, x0, 0), ClaimResult.Type.CANT_UNCLAIM_ANCHOR,
                    "the owner's unclaim of the member's team claim with the anchor");
            assertType(helper, doUnclaim(server, memberId, x0, 0), ClaimResult.Type.CANT_UNCLAIM_ANCHOR,
                    "the member's unclaim of their own team claim with the anchor");
            assertOwner(helper, server, x0, 0, memberId, "the team claim with the anchor");
            helper.assertTrue(anchors(server, memberId).equals(Set.of(new ChunkPos(x0, 0))),
                    "expected the member's anchor to still be there, got " + anchors(server, memberId));
            assertTeamClaims(helper, cm, party.getId(), 2, "after the owner unclaimed one of the member's team claims");

            // The unclaims of an anchor removal, as OPAC's area task makes them for the anchor's owner
            addAnchor(helper, server, ownerId, x0 + 5, 0);
            assertType(helper, doClaim(server, ownerId, ownerSub, x0 + 4, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "the owner's team claim within the range of their own anchor");
            var claimsManager = ServerData.from(server).getServerClaimsManager();
            ClaimResult<PlayerChunkClaim> others = claimsManager.tryToUnclaimHelper(OVERWORLD, memberId, x0 + 4, 0, x0 + 4, 0, false, true);
            assertType(helper, others, ClaimResult.Type.NOT_CLAIMED_BY_USER,
                    "the removal of the member's anchor reaching the owner's team claim");
            assertOwner(helper, server, x0 + 4, 0, ownerId, "the owner's team claim");
            ClaimResult<PlayerChunkClaim> own = claimsManager.tryToUnclaimHelper(OVERWORLD, memberId, x0 + 3, 0, x0 + 3, 0, false, true);
            assertType(helper, own, ClaimResult.Type.SUCCESSFUL_UNCLAIM, "the removal of the member's anchor reaching their own team claim");
            assertOwner(helper, server, x0 + 3, 0, null, "the member's team claim that depended on the anchor");
            assertTeamClaims(helper, cm, party.getId(), 2, "after the member's team claim went with the anchor");
            helper.assertTrue(cm.getOwnedTeamClaimCount(memberId) == 1 && cm.getOwnedTeamClaimCount(ownerId) == 1,
                    "expected one team claim each to be left, got " + cm.getOwnedTeamClaimCount(memberId) + " and "
                            + cm.getOwnedTeamClaimCount(ownerId));

            // Without anchor-based claiming everything is as before
            setAnchorBasedClaiming(false);
            assertType(helper, doUnclaim(server, ownerId, x0, 0), ClaimResult.Type.SUCCESSFUL_UNCLAIM,
                    "the owner's unclaim of the member's team claim with the anchor, anchor-based claiming being off");
            assertType(helper, doClaim(server, ownerId, ownerSub, x0 + 100, 0), ClaimResult.Type.SUCCESSFUL_CLAIM,
                    "the owner's team claim far from every anchor, anchor-based claiming being off");
            helper.succeed();
        } finally {
            setAnchorBasedClaiming(anchorsBefore);
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 3, 0, x0 + 4, 0, x0 + 5, 0, x0 + 100, 0);
            TeamClaimsBudgetTestCases.cleanupParty(server, party);
        }
    }

    /**
     * A member with three anchors leaves. The anchor on a team claim that none of the claims they keep depends on goes
     * to the party owner together with the team claims around it. The team claim near an anchor on a private claim
     * stays with the leaver (the owner would have no anchor for it), and so does a team claim with an anchor that a
     * private claim of the leaver depends on, along with that anchor. Nobody ends up with a claim outside their own
     * anchor ranges and no anchor is lost.
     */
    public static void leavingMemberHandsOverAnchoredTeamClaims(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        GameProfile ownerProfile = profile("T121_Owner");
        GameProfile memberProfile = profile("T121_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 121000;
        int[] chunks = {x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 100, 0, x0 + 101, 0, x0 + 102, 0, x0 + 200, 0, x0 + 201, 0, x0 + 202, 0};
        boolean anchorsBefore = ServerConfig.CONFIG.anchorBasedClaiming.get();
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            tcm.processPendingEvents();
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(1, 100, 0, 10, 0, 168));
            String subId = tcm.getTeamConfig(partyId).getSubConfigId();
            int memberSub = teamSubIndexOf(server, subId, memberId);
            setAnchorBasedClaiming(true);

            // A: an anchor on a team claim with two team claims around it
            addAnchor(helper, server, memberId, x0, 0);
            claim(helper, server, memberId, memberSub, x0, 0);
            claim(helper, server, memberId, memberSub, x0 + 1, 0);
            claim(helper, server, memberId, memberSub, x0 + 2, 0);
            // B: an anchor on a private claim with a team claim and a private claim around it
            addAnchor(helper, server, memberId, x0 + 100, 0);
            claim(helper, server, memberId, memberSub, x0 + 101, 0);
            claim(helper, server, memberId, -1, x0 + 102, 0);
            // C: an anchor on a team claim with a team claim and a private claim around it
            addAnchor(helper, server, memberId, x0 + 200, 0);
            claim(helper, server, memberId, memberSub, x0 + 200, 0);
            claim(helper, server, memberId, memberSub, x0 + 201, 0);
            claim(helper, server, memberId, -1, x0 + 202, 0);
            assertTeamClaims(helper, cm, partyId, 6, "test setup");
            helper.assertTrue(anchors(server, ownerId).isEmpty(), "test setup: expected the owner to have no anchor");

            helper.assertTrue(party.removeMember(memberId) != null, "expected party.removeMember to succeed");
            tcm.processPendingEvents();

            int ownerSub = teamSubIndexOf(server, subId, ownerId);
            for (int dx = 0; dx <= 2; dx++) {
                IPlayerChunkClaimAPI claim = claimsAPI(server).get(OVERWORLD, x0 + dx, 0);
                helper.assertTrue(claim != null && ownerId.equals(claim.getPlayerId()) && claim.getSubConfigIndex() == ownerSub,
                        "expected the team claim at (" + (x0 + dx) + ", 0) to be a team claim of the owner now, got " + claim);
            }
            helper.assertTrue(anchors(server, ownerId).equals(Set.of(new ChunkPos(x0, 0))),
                    "expected the anchor of the handed-over team claims to be the owner's now, got " + anchors(server, ownerId));
            helper.assertTrue(anchors(server, memberId).equals(Set.of(new ChunkPos(x0 + 100, 0), new ChunkPos(x0 + 200, 0))),
                    "expected the leaver to keep exactly the two anchors their own claims depend on, got " + anchors(server, memberId));
            for (int base : new int[]{x0 + 100, x0 + 200}) {
                for (int dx = 0; dx <= 2; dx++)
                    assertOwner(helper, server, base + dx, 0, memberId, "the claim the leaver keeps");
            }
            assertTeamClaims(helper, cm, partyId, 3, "after the hand-over");
            helper.assertTrue(cm.getOwnedTeamClaimCount(ownerId) == 3 && cm.getOwnedTeamClaimCount(memberId) == 0,
                    "expected the owner to own the three team claims and the leaver none, got "
                            + cm.getOwnedTeamClaimCount(ownerId) + " and " + cm.getOwnedTeamClaimCount(memberId));
            helper.assertTrue(claimsAPI(server).getPlayerInfo(ownerId).getClaimCount() == 3
                            && claimsAPI(server).getPlayerInfo(memberId).getClaimCount() == 6,
                    "expected 3 claims of the owner and 6 of the leaver, got "
                            + claimsAPI(server).getPlayerInfo(ownerId).getClaimCount() + " and "
                            + claimsAPI(server).getPlayerInfo(memberId).getClaimCount());
            helper.succeed();
        } finally {
            setAnchorBasedClaiming(anchorsBefore);
            unclaimQuiet(server, chunks);
            TeamClaimsBudgetTestCases.cleanupParty(server, party);
        }
    }

    /**
     * A team above its team claim limit without a grace period: the most recently made team claims are unclaimed, but
     * not the chunk with an anchor, although it is the newest team claim. With nothing else left to unclaim the team
     * stays above its limit, and checking again changes nothing.
     */
    public static void teamClaimsAboveTheLimitSpareAnchors(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        GameProfile ownerProfile = profile("T122_Owner");
        GameProfile memberProfile = profile("T122_Member");
        UUID ownerId = ownerProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 122000;
        boolean anchorsBefore = ServerConfig.CONFIG.anchorBasedClaiming.get();
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            TeamClaimsCommon.getTeamConfigManager().processPendingEvents();
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(2, 3, 0, 1, 0, 0));
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId).getSubConfigId();
            int ownerSub = teamSubIndexOf(server, subId, ownerId);
            setAnchorBasedClaiming(true);

            addAnchor(helper, server, ownerId, x0, 0);
            claim(helper, server, ownerId, ownerSub, x0 + 1, 0);
            claim(helper, server, ownerId, ownerSub, x0 + 2, 0);
            claim(helper, server, ownerId, ownerSub, x0, 0);//the chunk with the anchor is the newest team claim
            assertTeamClaims(helper, cm, partyId, 3, "test setup");

            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(2, 2, 0, 1, 0, 0));
            cm.checkOverLimit(partyId);
            assertOwner(helper, server, x0, 0, ownerId, "the team claim with the anchor (the newest one)");
            assertOwner(helper, server, x0 + 2, 0, null, "the newest team claim without an anchor");
            assertOwner(helper, server, x0 + 1, 0, ownerId, "the oldest team claim");
            assertTeamClaims(helper, cm, partyId, 2, "after one team claim above the limit was unclaimed");

            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(2, 0, 0, 1, 0, 0));
            cm.checkOverLimit(partyId);
            assertOwner(helper, server, x0 + 1, 0, null, "the last team claim without an anchor");
            assertOwner(helper, server, x0, 0, ownerId, "the team claim with the anchor");
            helper.assertTrue(anchors(server, ownerId).equals(Set.of(new ChunkPos(x0, 0))),
                    "expected the anchor to still be there, got " + anchors(server, ownerId));
            cm.checkOverLimit(partyId);
            TeamBudget budget = cm.getTeamBudget(partyId);
            TeamData teamData = cm.getTeamData(partyId);
            helper.assertTrue(budget != null && budget.claims() == 1 && budget.claimsOverLimit() == 1 && teamData != null
                            && teamData.getTrackedClaims().equals(Set.of(new ClaimPos(OVERWORLD, x0, 0))),
                    "expected the team to keep the team claim with the anchor above its limit of 0, got " + budget);
            helper.succeed();
        } finally {
            setAnchorBasedClaiming(anchorsBefore);
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0);
            TeamClaimsBudgetTestCases.cleanupParty(server, party);
        }
    }

    /**
     * A party is removed and the former team claims of a member do not all fit into their private limit: the newest
     * ones are unclaimed, but not the chunk with an anchor, although it is the newest: the next older ones go instead,
     * so the player does not lose the anchor the claims they keep depend on.
     */
    public static void removedPartyKeepsAnchors(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        GameProfile ownerProfile = profile("T123_Owner");
        GameProfile memberProfile = profile("T123_Member");
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 123000;
        boolean anchorsBefore = ServerConfig.CONFIG.anchorBasedClaiming.get();
        IPlayerConfigAPI memberConfig = configManager(server).getLoadedConfig(memberId);
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            TeamClaimsCommon.getTeamConfigManager().processPendingEvents();
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(2, 100, 0, 10, 0, 168));
            String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId).getSubConfigId();
            int memberSub = teamSubIndexOf(server, subId, memberId);
            setAnchorBasedClaiming(true);

            addAnchor(helper, server, memberId, x0, 0);
            claim(helper, server, memberId, memberSub, x0 + 1, 0);
            claim(helper, server, memberId, memberSub, x0 + 2, 0);
            claim(helper, server, memberId, memberSub, x0 + 3, 0);
            claim(helper, server, memberId, memberSub, x0, 0);//the chunk with the anchor is the newest team claim
            assertTeamClaims(helper, cm, partyId, 4, "test setup");
            int limit = claimsAPI(server).getPlayerFullClaimLimit(memberId);
            IPlayerConfigAPI.SetResult set = memberConfig.tryToSet(PlayerConfigOptions.BONUS_CHUNK_CLAIMS,
                    memberConfig.getEffective(PlayerConfigOptions.BONUS_CHUNK_CLAIMS) + 2 - limit);
            helper.assertTrue(set == IPlayerConfigAPI.SetResult.SUCCESS && claimsAPI(server).getPlayerFullClaimLimit(memberId) == 2,
                    "test setup: expected the member's private claim limit to be 2, got " + set + " and "
                            + claimsAPI(server).getPlayerFullClaimLimit(memberId));

            disbandPartyQuiet(server, partyId);
            TeamClaimsCommon.getTeamConfigManager().processPendingEvents();

            helper.assertTrue(cm.getTeamData(partyId) == null, "expected the team claims of the removed party not to be tracked any more");
            assertOwner(helper, server, x0, 0, memberId, "the former team claim with the anchor (the newest one)");
            assertOwner(helper, server, x0 + 1, 0, memberId, "the oldest former team claim");
            assertOwner(helper, server, x0 + 2, 0, null, "the 2nd newest former team claim without an anchor");
            assertOwner(helper, server, x0 + 3, 0, null, "the newest former team claim without an anchor");
            helper.assertTrue(anchors(server, memberId).equals(Set.of(new ChunkPos(x0, 0))),
                    "expected the anchor to still be there, got " + anchors(server, memberId));
            helper.succeed();
        } finally {
            setAnchorBasedClaiming(anchorsBefore);
            try {
                memberConfig.tryToReset(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
            } catch (Exception ignored) {
            }
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 3, 0);
            TeamClaimsBudgetTestCases.cleanupParty(server, party);
        }
    }

    /**
     * The hand-over of a leaving member's anchor needs room in the party owner's anchor limit (per player and
     * dimension, the team has none of its own): with the owner's limit full, the team claim with the leaver's anchor
     * and the claim next to it stay with the leaver, anchor included, while a team claim within the range of one of the
     * owner's own anchors is still handed over (without an anchor). The owner's anchors stay exactly as they were.
     */
    public static void ownersFullAnchorLimitKeepsTheAnchoredTeamClaimWithTheLeaver(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        GameProfile ownerProfile = profile("T124_Owner");
        GameProfile memberProfile = profile("T124_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 124000;
        boolean anchorsBefore = ServerConfig.CONFIG.anchorBasedClaiming.get();
        List<Integer> cleanup = new ArrayList<>(List.of(x0, 0, x0 + 1, 0, x0 + 10, 0, x0 + 20, 0));
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            tcm.processPendingEvents();
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(1, 100, 0, 10, 0, 168));
            String subId = tcm.getTeamConfig(partyId).getSubConfigId();
            int memberSub = teamSubIndexOf(server, subId, memberId);
            setAnchorBasedClaiming(true);

            // The owner's anchor limit is full: one anchor near the leaver's claims, the others far away
            int limit = claimsAPI(server).getPlayerFullAnchorLimit(ownerId, OVERWORLD);
            helper.assertTrue(limit >= 1 && limit <= 10, "test setup: expected a small anchor limit for the owner, got " + limit);
            addAnchor(helper, server, ownerId, x0 + 20, 0);
            fillAnchorLimit(helper, server, ownerId, limit, x0, cleanup);
            helper.assertTrue(anchors(server, ownerId).size() == limit, "test setup: expected the owner to be at the anchor limit " + limit);

            // The leaver: an anchor on a team claim with a 2nd team claim next to it (not within 16 chunks of the owner's
            // anchor), and a team claim that is within range of both the leaver's and the owner's anchor
            addAnchor(helper, server, memberId, x0, 0);
            claim(helper, server, memberId, memberSub, x0, 0);
            claim(helper, server, memberId, memberSub, x0 + 1, 0);
            claim(helper, server, memberId, memberSub, x0 + 10, 0);
            assertTeamClaims(helper, cm, partyId, 3, "test setup");

            helper.assertTrue(party.removeMember(memberId) != null, "expected party.removeMember to succeed");
            tcm.processPendingEvents();

            assertOwner(helper, server, x0, 0, memberId, "the team claim with the leaver's anchor (the owner has no room for it)");
            assertOwner(helper, server, x0 + 1, 0, memberId, "the team claim next to it (not within the range of the owner's anchor)");
            assertOwner(helper, server, x0 + 10, 0, ownerId, "the team claim within the range of the owner's own anchor");
            helper.assertTrue(claimsAPI(server).get(OVERWORLD, x0 + 10, 0).getSubConfigIndex() == teamSubIndexOf(server, subId, ownerId),
                    "expected the handed-over claim to be a team claim of the owner");
            helper.assertTrue(anchors(server, memberId).equals(Set.of(new ChunkPos(x0, 0))),
                    "expected the leaver to keep their anchor, got " + anchors(server, memberId));
            helper.assertTrue(anchors(server, ownerId).size() == limit && !anchors(server, ownerId).contains(new ChunkPos(x0, 0)),
                    "expected the owner's anchors to stay as they were (" + limit + "), got " + anchors(server, ownerId));
            assertTeamClaims(helper, cm, partyId, 1, "after the hand-over");
            helper.assertTrue(cm.getOwnedTeamClaimCount(ownerId) == 1 && cm.getOwnedTeamClaimCount(memberId) == 0,
                    "expected the owner to own the one handed-over team claim and the leaver none, got "
                            + cm.getOwnedTeamClaimCount(ownerId) + " and " + cm.getOwnedTeamClaimCount(memberId));
            helper.succeed();
        } finally {
            setAnchorBasedClaiming(anchorsBefore);
            unclaimQuiet(server, cleanup.stream().mapToInt(Integer::intValue).toArray());
            TeamClaimsBudgetTestCases.cleanupParty(server, party);
        }
    }

    /**
     * The anchor of a leaving member only goes along if the owner's anchor range is not smaller than the leaver's
     * (the owner would otherwise cover less than the leaver did). The leaver has a bonus of 8 chunks of anchor range
     * here: the team claim with their anchor stays with them, anchor included, while the team claim within the range of
     * the owner's own anchor is handed over.
     */
    public static void ownersSmallerAnchorRangeKeepsTheAnchoredTeamClaimWithTheLeaver(GameTestHelper helper) {
        anchorRangeHandOver(helper, "T125", 125000, 8, 0, false);
    }

    /**
     * The same hand-over with the owner's anchor range larger than the leaver's (a bonus of 4 chunks): the leaver's
     * anchor goes along with its team claim to the owner, who gets it as a new anchor, and so does the other team claim.
     */
    public static void ownersLargerAnchorRangeTakesTheAnchorAlong(GameTestHelper helper) {
        anchorRangeHandOver(helper, "T126", 126000, 0, 4, true);
    }

    /**
     * Anchors, their limits and the hand-over are per dimension. The owner's anchor limit is full in the overworld but
     * not in the nether: a leaving member's overworld team claim with an anchor stays with them, their nether team claim
     * with an anchor goes to the owner along with the anchor, and so does the claim next to it.
     */
    public static void anchoredHandOverIsPerDimension(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        GameProfile ownerProfile = profile("T127_Owner");
        GameProfile memberProfile = profile("T127_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 127000;
        boolean anchorsBefore = ServerConfig.CONFIG.anchorBasedClaiming.get();
        List<Integer> cleanup = new ArrayList<>(List.of(x0, 0, x0 + 1, 0, x0 + 10, 0, x0 + 20, 0));
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            tcm.processPendingEvents();
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(1, 100, 0, 10, 0, 168));
            String subId = tcm.getTeamConfig(partyId).getSubConfigId();
            int memberSub = teamSubIndexOf(server, subId, memberId);
            setAnchorBasedClaiming(true);

            int limit = claimsAPI(server).getPlayerFullAnchorLimit(ownerId, OVERWORLD);
            helper.assertTrue(limit >= 1 && limit <= 10 && claimsAPI(server).getPlayerFullAnchorLimit(ownerId, NETHER) == limit,
                    "test setup: expected the same small anchor limit for the owner in both dimensions, got " + limit);
            addAnchor(helper, server, ownerId, x0 + 20, 0);
            fillAnchorLimit(helper, server, ownerId, limit, x0, cleanup);
            // Overworld: the same claims as in the full-limit test; nether: an anchor on a team claim with one next to it
            addAnchor(helper, server, memberId, x0, 0);
            claim(helper, server, memberId, memberSub, x0, 0);
            claim(helper, server, memberId, memberSub, x0 + 10, 0);
            addAnchorIn(helper, server, NETHER, memberId, x0, 0);
            claimIn(helper, server, NETHER, memberId, memberSub, x0, 0);
            claimIn(helper, server, NETHER, memberId, memberSub, x0 + 1, 0);
            assertTeamClaims(helper, cm, partyId, 4, "test setup");

            helper.assertTrue(party.removeMember(memberId) != null, "expected party.removeMember to succeed");
            tcm.processPendingEvents();

            int ownerSub = teamSubIndexOf(server, subId, ownerId);
            assertOwner(helper, server, x0, 0, memberId, "the overworld team claim with the leaver's anchor (no room in the overworld)");
            assertOwner(helper, server, x0 + 10, 0, ownerId, "the overworld team claim within the range of the owner's anchor");
            for (int dx = 0; dx <= 1; dx++) {
                IPlayerChunkClaimAPI claim = claimsAPI(server).get(NETHER, x0 + dx, 0);
                helper.assertTrue(claim != null && ownerId.equals(claim.getPlayerId()) && claim.getSubConfigIndex() == ownerSub,
                        "expected the nether team claim at (" + (x0 + dx) + ", 0) to be a team claim of the owner now, got " + claim);
            }
            helper.assertTrue(anchors(server, memberId, OVERWORLD).equals(Set.of(new ChunkPos(x0, 0))) && anchors(server, memberId, NETHER).isEmpty(),
                    "expected the leaver to keep only the overworld anchor, got " + anchors(server, memberId, OVERWORLD) + " and "
                            + anchors(server, memberId, NETHER) + " in the nether");
            helper.assertTrue(anchors(server, ownerId, OVERWORLD).size() == limit && anchors(server, ownerId, NETHER).equals(Set.of(new ChunkPos(x0, 0))),
                    "expected the owner to have " + limit + " anchors in the overworld and the leaver's anchor in the nether, got "
                            + anchors(server, ownerId, OVERWORLD) + " and " + anchors(server, ownerId, NETHER));
            assertTeamClaims(helper, cm, partyId, 3, "after the hand-over");
            helper.succeed();
        } finally {
            setAnchorBasedClaiming(anchorsBefore);
            unclaimQuiet(server, cleanup.stream().mapToInt(Integer::intValue).toArray());
            for (int dx = 0; dx <= 1; dx++) {
                try {
                    claimsAPI(server).unclaim(NETHER, x0 + dx, 0);
                } catch (Exception ignored) {
                }
            }
            TeamClaimsBudgetTestCases.cleanupParty(server, party);
        }
    }

    /**
     * A claiming anchor on a team claim of the player's own: the anchor is added to the claim as it is, which stays a
     * team claim (nothing is claimed anew, so neither budget changes). The anchor counts against the anchor limit of the
     * player who places it, not against the team: with the owner's limit full the owner's next anchor is refused,
     * the member's is not. A member cannot place an anchor on a team claim of the owner, which is not theirs to claim.
     */
    public static void anchorOnTheOwnTeamClaim(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        GameProfile ownerProfile = profile("T128_Owner");
        GameProfile memberProfile = profile("T128_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        int x0 = 128000;
        boolean anchorsBefore = ServerConfig.CONFIG.anchorBasedClaiming.get();
        List<Integer> cleanup = new ArrayList<>(List.of(x0, 0, x0 + 1, 0, x0 + 2, 0));
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            tcm.processPendingEvents();
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(1, 100, 0, 10, 0, 168));
            String subId = tcm.getTeamConfig(partyId).getSubConfigId();
            int ownerSub = teamSubIndexOf(server, subId, ownerId);
            int memberSub = teamSubIndexOf(server, subId, memberId);
            setAnchorBasedClaiming(true);

            int limit = claimsAPI(server).getPlayerFullAnchorLimit(ownerId, OVERWORLD);
            helper.assertTrue(limit >= 2 && limit <= 10, "test setup: expected a small anchor limit for the owner, got " + limit);
            // Team claims made without anchors (the low-level claim does not look at them)
            claimIn(helper, server, OVERWORLD, ownerId, ownerSub, x0, 0);
            claimIn(helper, server, OVERWORLD, ownerId, ownerSub, x0 + 1, 0);
            claimIn(helper, server, OVERWORLD, memberId, memberSub, x0 + 2, 0);
            assertTeamClaims(helper, cm, partyId, 3, "test setup");
            BudgetInfo before = cm.getBudgetInfo(ownerId);

            assertType(helper, claimsAPI(server).tryToAddAnchor(OVERWORLD, ownerId, OVERWORLD, x0, 0, x0, 0, false),
                    ClaimResult.Type.SUCCESSFUL_ANCHOR, "the owner's anchor on their own team claim");
            IPlayerChunkClaimAPI claim = claimsAPI(server).get(OVERWORLD, x0, 0);
            helper.assertTrue(claim != null && ownerId.equals(claim.getPlayerId()) && claim.getSubConfigIndex() == ownerSub,
                    "expected the chunk with the anchor to stay a team claim of the owner, got " + claim);
            helper.assertTrue(anchors(server, ownerId).equals(Set.of(new ChunkPos(x0, 0))),
                    "expected exactly the new anchor, got " + anchors(server, ownerId));
            helper.assertTrue(cm.getBudgetInfo(ownerId).equals(before),
                    "expected the anchor not to change the budget, got " + cm.getBudgetInfo(ownerId) + " instead of " + before);
            assertTeamClaims(helper, cm, partyId, 3, "after the anchor on the team claim");
            assertType(helper, claimsAPI(server).tryToAddAnchor(OVERWORLD, ownerId, OVERWORLD, x0, 0, x0, 0, false),
                    ClaimResult.Type.ANCHOR_ALREADY_EXISTS, "a second anchor on the same team claim");

            // The owner's limit: fill it up with anchors on other chunks, the next one is refused
            fillAnchorLimit(helper, server, ownerId, limit, x0, cleanup);
            helper.assertTrue(anchors(server, ownerId).size() == limit && anchors(server, memberId).isEmpty(),
                    "expected the owner to have " + limit + " anchors and the member none");
            assertType(helper, claimsAPI(server).tryToAddAnchor(OVERWORLD, ownerId, OVERWORLD, x0 + 1, 0, x0 + 1, 0, false),
                    ClaimResult.Type.ANCHOR_LIMIT_REACHED, "the owner's anchor on their 2nd team claim with the anchor limit full");

            // The member's limit is their own
            assertType(helper, claimsAPI(server).tryToAddAnchor(OVERWORLD, memberId, OVERWORLD, x0 + 2, 0, x0 + 2, 0, false),
                    ClaimResult.Type.SUCCESSFUL_ANCHOR, "the member's anchor on their own team claim");
            // ...but a team claim of the owner is not the member's to claim
            ClaimResult<IPlayerChunkClaimAPI> notTheirs = claimsAPI(server).tryToAddAnchor(OVERWORLD, memberId, OVERWORLD, x0 + 1, 0, x0 + 1, 0, false);
            helper.assertTrue(!notTheirs.getResultType().success,
                    "expected the member's anchor on a team claim of the owner to be refused, got " + notTheirs.getResultType());
            assertOwner(helper, server, x0 + 1, 0, ownerId, "the owner's team claim after the member's refused anchor");
            helper.assertTrue(anchors(server, memberId).equals(Set.of(new ChunkPos(x0 + 2, 0))),
                    "expected the member to have only the anchor on their own team claim, got " + anchors(server, memberId));
            assertTeamClaims(helper, cm, partyId, 3, "at the end");
            helper.succeed();
        } finally {
            setAnchorBasedClaiming(anchorsBefore);
            unclaimQuiet(server, cleanup.stream().mapToInt(Integer::intValue).toArray());
            TeamClaimsBudgetTestCases.cleanupParty(server, party);
        }
    }

    /**
     * The shared setup of the two anchor range tests. The leaver has an anchor on a team claim with another team claim
     * 12 chunks away, within the range of the owner's own anchor 20 chunks from the leaver's. The ranges differ by the
     * given bonuses (the bonus claiming anchor range of the player configs).
     */
    private static void anchorRangeHandOver(GameTestHelper helper, String name, int x0, int leaverRangeBonus, int ownerRangeBonus,
            boolean anchorGoesAlong) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        GameProfile ownerProfile = profile(name + "_Owner");
        GameProfile memberProfile = profile(name + "_Member");
        UUID ownerId = ownerProfile.getId();
        UUID memberId = memberProfile.getId();
        IServerPartyAPI party = null;
        boolean anchorsBefore = ServerConfig.CONFIG.anchorBasedClaiming.get();
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            tcm.processPendingEvents();
            UUID partyId = party.getId();
            cm.setBudgetSettingsOverride(partyId, new BudgetSettings(1, 100, 0, 10, 0, 168));
            String subId = tcm.getTeamConfig(partyId).getSubConfigId();
            int memberSub = teamSubIndexOf(server, subId, memberId);
            setAnchorBasedClaiming(true);
            setAnchorRangeBonus(helper, server, memberId, leaverRangeBonus);
            setAnchorRangeBonus(helper, server, ownerId, ownerRangeBonus);
            int leaverRange = claimsAPI(server).getPlayerFullAnchorRange(memberId, OVERWORLD);
            int ownerRange = claimsAPI(server).getPlayerFullAnchorRange(ownerId, OVERWORLD);
            helper.assertTrue((leaverRange <= ownerRange) == anchorGoesAlong && leaverRange >= 12 && ownerRange >= 8,
                    "test setup: expected the anchor ranges " + leaverRange + " (leaver) and " + ownerRange + " (owner) to fit the test");
            helper.assertTrue(claimsAPI(server).getPlayerFullAnchorLimit(ownerId, OVERWORLD) >= 2,
                    "test setup: expected the owner to have room for another anchor");

            addAnchor(helper, server, ownerId, x0 + 20, 0);
            addAnchor(helper, server, memberId, x0, 0);
            claim(helper, server, memberId, memberSub, x0, 0);
            claim(helper, server, memberId, memberSub, x0 + 12, 0);
            assertTeamClaims(helper, cm, partyId, 2, "test setup");

            helper.assertTrue(party.removeMember(memberId) != null, "expected party.removeMember to succeed");
            tcm.processPendingEvents();

            assertOwner(helper, server, x0 + 12, 0, ownerId, "the team claim within the range of the owner's anchor");
            assertOwner(helper, server, x0, 0, anchorGoesAlong ? ownerId : memberId,
                    "the team claim with the leaver's anchor (leaver range " + leaverRange + ", owner range " + ownerRange + ")");
            if (anchorGoesAlong) {
                helper.assertTrue(anchors(server, ownerId).equals(Set.of(new ChunkPos(x0, 0), new ChunkPos(x0 + 20, 0)))
                                && anchors(server, memberId).isEmpty(),
                        "expected the leaver's anchor to be the owner's now, got " + anchors(server, ownerId) + " and " + anchors(server, memberId));
                assertTeamClaims(helper, cm, partyId, 2, "after the hand-over");
            } else {
                helper.assertTrue(anchors(server, ownerId).equals(Set.of(new ChunkPos(x0 + 20, 0)))
                                && anchors(server, memberId).equals(Set.of(new ChunkPos(x0, 0))),
                        "expected the anchors to stay where they were, got " + anchors(server, ownerId) + " and " + anchors(server, memberId));
                assertTeamClaims(helper, cm, partyId, 1, "after the hand-over");
            }
            helper.succeed();
        } finally {
            setAnchorBasedClaiming(anchorsBefore);
            for (UUID id : List.of(ownerId, memberId)) {
                try {
                    configManager(server).getLoadedConfig(id).tryToReset(PlayerConfigOptions.BONUS_CLAIMING_ANCHOR_RANGE);
                } catch (Exception ignored) {
                }
            }
            unclaimQuiet(server, x0, 0, x0 + 12, 0, x0 + 20, 0);
            TeamClaimsBudgetTestCases.cleanupParty(server, party);
        }
    }

    // ==================== Helpers ====================

    private static final ResourceLocation NETHER = Level.NETHER.location();

    /**
     * Gives the player anchors up to {@code limit} in the overworld: they already have one near {@code x0}, the others
     * are placed far from it (and from each other) and their chunks added to {@code cleanup}.
     */
    private static void fillAnchorLimit(GameTestHelper helper, MinecraftServer server, UUID playerId, int limit, int x0, List<Integer> cleanup) {
        for (int i = 1; i < limit; i++) {
            addAnchor(helper, server, playerId, x0 + 500 + 40 * i, 0);
            cleanup.add(x0 + 500 + 40 * i);
            cleanup.add(0);
        }
    }

    private static void setAnchorRangeBonus(GameTestHelper helper, MinecraftServer server, UUID playerId, int bonus) {
        if (bonus == 0) return;
        IPlayerConfigAPI.SetResult result = configManager(server).getLoadedConfig(playerId).tryToSet(PlayerConfigOptions.BONUS_CLAIMING_ANCHOR_RANGE, bonus);
        helper.assertTrue(result == IPlayerConfigAPI.SetResult.SUCCESS, "test setup: expected the anchor range bonus " + bonus + " to be accepted, got " + result);
    }

    /** OPAC's low-level claim (no checks): the claim of {@code playerId} with the given sub-config. */
    private static void claimIn(GameTestHelper helper, MinecraftServer server, ResourceLocation dimension, UUID playerId, int subIndex, int x, int z) {
        helper.assertTrue(claimsAPI(server).claim(dimension, playerId, subIndex, x, z, false) != null,
                "test setup: expected the claim at (" + x + ", " + z + ") in " + dimension + " to be made");
    }

    private static void addAnchorIn(GameTestHelper helper, MinecraftServer server, ResourceLocation dimension, UUID playerId, int x, int z) {
        helper.assertTrue(claimsAPI(server).addAnchor(dimension, new ChunkPos(x, z), playerId),
                "test setup: expected an anchor to be added at (" + x + ", " + z + ") in " + dimension);
    }

    private static Set<ChunkPos> anchors(MinecraftServer server, UUID playerId, ResourceLocation dimension) {
        IPlayerDimensionClaimsAPI dimensionClaims = claimsAPI(server).getPlayerInfo(playerId).getDimension(dimension);
        return dimensionClaims == null ? Set.of() : Set.copyOf(dimensionClaims.getAnchors());
    }

    /**
     * Sets the "anchor-based claiming" server setting. It needs a world restart in the config, so its value is cached
     * and {@code set} leaves the cache alone: the cache is dropped to make the new value count right away.
     */
    static void setAnchorBasedClaiming(boolean enabled) {
        ServerConfig.CONFIG.anchorBasedClaiming.set(enabled);
        ServerConfig.CONFIG.anchorBasedClaiming.clearCache();
    }

    static void addAnchor(GameTestHelper helper, MinecraftServer server, UUID playerId, int x, int z) {
        helper.assertTrue(claimsAPI(server).addAnchor(OVERWORLD, new ChunkPos(x, z), playerId),
                "test setup: expected an anchor to be added at (" + x + ", " + z + ")");
    }

    static void claim(GameTestHelper helper, MinecraftServer server, UUID playerId, int subIndex, int x, int z) {
        assertType(helper, doClaim(server, playerId, subIndex, x, z), ClaimResult.Type.SUCCESSFUL_CLAIM,
                "test setup: the claim at (" + x + ", " + z + ")");
    }

    static Set<ChunkPos> anchors(MinecraftServer server, UUID playerId) {
        IPlayerDimensionClaimsAPI dimensionClaims = claimsAPI(server).getPlayerInfo(playerId).getDimension(OVERWORLD);
        return dimensionClaims == null ? Set.of() : Set.copyOf(dimensionClaims.getAnchors());
    }

    static void assertType(GameTestHelper helper, ClaimResult<?> result, ClaimResult.Type expected, String what) {
        helper.assertTrue(result.getResultType() == expected, "expected " + what + " to result in " + expected + ", got "
                + result.getResultType());
    }

    static void assertOwner(GameTestHelper helper, MinecraftServer server, int x, int z, @Nullable UUID ownerId, String what) {
        IPlayerChunkClaimAPI claim = claimsAPI(server).get(OVERWORLD, x, z);
        UUID actual = claim == null ? null : claim.getPlayerId();
        helper.assertTrue(ownerId == null ? actual == null : ownerId.equals(actual), "expected " + what + " at (" + x + ", " + z
                + ") to be " + (ownerId == null ? "unclaimed" : "a claim of " + ownerId) + ", got " + (actual == null ? "unclaimed" : actual));
    }

    static void assertTeamClaims(GameTestHelper helper, TeamClaimManager cm, UUID partyId, int expected, String stage) {
        TeamBudget budget = cm.getTeamBudget(partyId);
        helper.assertTrue(budget != null && budget.claims() == expected, stage + ": expected " + expected + " team claim(s), got "
                + (budget == null ? null : budget.claims()));
    }
}
