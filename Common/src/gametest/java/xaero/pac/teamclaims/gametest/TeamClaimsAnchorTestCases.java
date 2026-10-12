package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
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
import xaero.pac.teamclaims.TeamClaimManager.BudgetSettings;
import xaero.pac.teamclaims.TeamClaimManager.ClaimPos;
import xaero.pac.teamclaims.TeamClaimManager.TeamBudget;
import xaero.pac.teamclaims.TeamClaimManager.TeamData;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.config.TeamConfigManager;

import javax.annotation.Nullable;
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

    // ==================== Helpers ====================

    /**
     * Sets the "anchor-based claiming" server setting. It needs a world restart in the config, so its value is cached
     * and {@code set} leaves the cache alone: the cache is dropped to make the new value count right away.
     */
    private static void setAnchorBasedClaiming(boolean enabled) {
        ServerConfig.CONFIG.anchorBasedClaiming.set(enabled);
        ServerConfig.CONFIG.anchorBasedClaiming.clearCache();
    }

    private static void addAnchor(GameTestHelper helper, MinecraftServer server, UUID playerId, int x, int z) {
        helper.assertTrue(claimsAPI(server).addAnchor(OVERWORLD, new ChunkPos(x, z), playerId),
                "test setup: expected an anchor to be added at (" + x + ", " + z + ")");
    }

    private static void claim(GameTestHelper helper, MinecraftServer server, UUID playerId, int subIndex, int x, int z) {
        assertType(helper, doClaim(server, playerId, subIndex, x, z), ClaimResult.Type.SUCCESSFUL_CLAIM,
                "test setup: the claim at (" + x + ", " + z + ")");
    }

    private static Set<ChunkPos> anchors(MinecraftServer server, UUID playerId) {
        IPlayerDimensionClaimsAPI dimensionClaims = claimsAPI(server).getPlayerInfo(playerId).getDimension(OVERWORLD);
        return dimensionClaims == null ? Set.of() : Set.copyOf(dimensionClaims.getAnchors());
    }

    private static void assertType(GameTestHelper helper, ClaimResult<?> result, ClaimResult.Type expected, String what) {
        helper.assertTrue(result.getResultType() == expected, "expected " + what + " to result in " + expected + ", got "
                + result.getResultType());
    }

    private static void assertOwner(GameTestHelper helper, MinecraftServer server, int x, int z, @Nullable UUID ownerId, String what) {
        IPlayerChunkClaimAPI claim = claimsAPI(server).get(OVERWORLD, x, z);
        UUID actual = claim == null ? null : claim.getPlayerId();
        helper.assertTrue(ownerId == null ? actual == null : ownerId.equals(actual), "expected " + what + " at (" + x + ", " + z
                + ") to be " + (ownerId == null ? "unclaimed" : "a claim of " + ownerId) + ", got " + (actual == null ? "unclaimed" : actual));
    }

    private static void assertTeamClaims(GameTestHelper helper, TeamClaimManager cm, UUID partyId, int expected, String stage) {
        TeamBudget budget = cm.getTeamBudget(partyId);
        helper.assertTrue(budget != null && budget.claims() == expected, stage + ": expected " + expected + " team claim(s), got "
                + (budget == null ? null : budget.claims()));
    }
}
