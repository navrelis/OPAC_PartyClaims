package xaero.pac.teamclaims.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import xaero.pac.client.gui.hub.ChunkSelection;
import xaero.pac.client.gui.hub.ClaimCategory;
import xaero.pac.client.gui.hub.ClaimsHubLogic;
import xaero.pac.client.gui.hub.HubGridFit;
import xaero.pac.client.gui.hub.TeamSubIndexMemory;

import java.util.List;

/**
 * Dev/test-only, loader-neutral test bodies for the pure rules behind the Claims tab of the hub screen
 * ({@link ChunkSelection}, {@link HubGridFit}, {@link ClaimCategory}, {@link ClaimsHubLogic}, {@link TeamSubIndexMemory}).
 * None of them touches a client class, so they run on the headless gametest server. Same conventions as
 * {@link TeamClaimsPartyScreenTestCases}: every test is registered with the loader's empty structure template and ends
 * with {@code helper.succeed()}.
 */
public final class TeamClaimsHubTestCases {

    private TeamClaimsHubTestCases() {}

    // ==================== Tests ====================

    /**
     * A selection is the same rectangle whichever way it was dragged (all four directions, negative coordinates), its
     * size and chunk count follow from it, and it contains exactly the chunks inside. A single click is one chunk.
     */
    public static void selectionIsTheSameRectangleInEveryDragDirection(GameTestHelper helper) {
        ChunkSelection single = ChunkSelection.single(-3, 7);
        helper.assertTrue(single.isSingle() && single.count() == 1 && single.width() == 1 && single.height() == 1,
                "expected a click to select exactly one chunk, got " + single);
        helper.assertTrue(single.contains(-3, 7) && !single.contains(-2, 7) && !single.contains(-3, 8),
                "expected the single chunk to contain only itself");

        int[][] corners = {{-2, 5, 4, -1}, {4, -1, -2, 5}, {-2, -1, 4, 5}, {4, 5, -2, -1}};
        for (int[] corner : corners) {
            ChunkSelection selection = ChunkSelection.single(corner[0], corner[1]).withEnd(corner[2], corner[3]);
            helper.assertTrue(selection.left() == -2 && selection.right() == 4 && selection.top() == -1 && selection.bottom() == 5,
                    "expected the rectangle -2..4 x -1..5 for the drag " + selection + ", got " + selection.left() + ".." + selection.right()
                            + " x " + selection.top() + ".." + selection.bottom());
            helper.assertTrue(selection.width() == 7 && selection.height() == 7 && selection.count() == 49,
                    "expected 7 x 7 = 49 chunks for the drag " + selection + ", got " + selection.width() + " x " + selection.height() + " = " + selection.count());
            helper.assertTrue(selection.contains(-2, -1) && selection.contains(4, 5) && selection.contains(0, 0),
                    "expected the corners and the middle inside the selection " + selection);
            helper.assertTrue(!selection.contains(-3, 0) && !selection.contains(5, 0) && !selection.contains(0, -2) && !selection.contains(0, 6),
                    "expected the chunks next to the rectangle to be outside the selection " + selection);
            helper.assertTrue(!selection.isSingle(), "expected a 7 x 7 selection not to be a single chunk");
        }

        // extending a selection moves only the end, the anchor stays
        ChunkSelection extended = ChunkSelection.single(10, 10).withEnd(12, 11).withEnd(8, 10);
        helper.assertTrue(extended.anchorX() == 10 && extended.anchorZ() == 10 && extended.left() == 8 && extended.right() == 10
                        && extended.top() == 10 && extended.bottom() == 10 && extended.count() == 3,
                "expected the drag back to keep the anchor and select 8..10 x 10, got " + extended);

        // the count cannot overflow
        ChunkSelection huge = ChunkSelection.single(Integer.MIN_VALUE / 2, 0).withEnd(Integer.MAX_VALUE / 2, 100000);
        helper.assertTrue(huge.count() > Integer.MAX_VALUE, "expected the chunk count of a huge selection to be a long above the int range, got " + huge.count());
        helper.succeed();
    }

    /**
     * The warning for selections that the server probably refuses starts right after the default limit of 121 chunks,
     * which is 11 x 11: 11 x 11 is fine, 11 x 12 is not.
     */
    public static void selectionWarnsOnlyAboveTheDefaultActionSize(GameTestHelper helper) {
        helper.assertTrue(ChunkSelection.DEFAULT_MAX_SINGLE_ACTION_SIZE == 121, "expected the default limit to be 121");
        ChunkSelection exactly = ChunkSelection.single(0, 0).withEnd(10, 10);
        helper.assertTrue(exactly.count() == 121 && !exactly.exceedsDefaultActionSize(), "expected 11 x 11 = 121 chunks not to exceed the limit, got " + exactly.count());
        ChunkSelection above = ChunkSelection.single(0, 0).withEnd(10, 11);
        helper.assertTrue(above.count() == 132 && above.exceedsDefaultActionSize(), "expected 11 x 12 = 132 chunks to exceed the limit, got " + above.count());
        helper.assertTrue(!ChunkSelection.single(5, 5).exceedsDefaultActionSize(), "expected one chunk not to exceed the limit");
        helper.succeed();
    }

    /**
     * Whatever space the screen leaves (every size from 0 to 600 pixels, which covers GUI scales 1 to 4 on small and big
     * windows), the grid is an odd square that never overflows that space, never has chunks bigger or smaller than the
     * limits, shows at least 17 x 17 chunks wherever that fits at the smallest chunk size, and gets smaller in steps
     * instead of jumping.
     */
    public static void gridFitNeverOverflowsAndKeepsEightChunksWhenItCan(GameTestHelper helper) {
        int previousRadius = -1;
        int previousSize = -1;
        for (int side = 0; side <= 600; side++) {
            HubGridFit.Fit fit = HubGridFit.fit(side);
            helper.assertTrue(fit.cells() % 2 == 1 && fit.cells() == fit.radius() * 2 + 1, "expected an odd number of cells for the side " + side + ", got " + fit);
            helper.assertTrue(fit.radius() >= HubGridFit.MIN_RADIUS && fit.radius() <= HubGridFit.MAX_RADIUS, "expected the radius within its limits for the side " + side + ", got " + fit);
            helper.assertTrue(fit.cell() >= 1 && fit.cell() <= HubGridFit.MAX_CELL, "expected the chunk size within its limits for the side " + side + ", got " + fit);
            if (side >= 3) // below that not even 3 x 3 chunks of one pixel fit
                helper.assertTrue(fit.size() <= side, "expected the grid not to overflow the side " + side + ", got " + fit + " = " + fit.size());
            if (side >= (HubGridFit.PREFERRED_MIN_RADIUS * 2 + 1) * HubGridFit.MIN_CELL)
                helper.assertTrue(fit.radius() >= HubGridFit.PREFERRED_MIN_RADIUS, "expected at least 8 chunks to each side for the side " + side + ", got " + fit);
            else if (side >= 3 * HubGridFit.MIN_CELL)
                helper.assertTrue(fit.cell() >= HubGridFit.MIN_CELL, "expected the grid to shrink its radius before the chunks below the minimum size for the side " + side + ", got " + fit);
            if (side >= 20) // from here the grid only gets bigger with more space
                helper.assertTrue(fit.size() >= previousSize - HubGridFit.MAX_CELL && fit.radius() >= Math.min(previousRadius, HubGridFit.PREFERRED_MIN_RADIUS) - 1,
                        "expected the grid not to shrink a lot when the side grows to " + side + ", got " + fit + " after size " + previousSize + " and radius " + previousRadius);
            previousRadius = fit.radius();
            previousSize = fit.size();
        }
        // examples: GUI scale 2 on a 1080p window leaves about 240 pixels, a GUI scale 4 window about 90
        HubGridFit.Fit roomy = HubGridFit.fit(240);
        helper.assertTrue(roomy.radius() == 8 && roomy.cell() == 14, "expected 17 x 17 chunks of 14 pixels for 240 pixels, got " + roomy);
        HubGridFit.Fit tight = HubGridFit.fit(90);
        helper.assertTrue(tight.radius() == 7 && tight.cell() == 6, "expected 15 x 15 chunks of 6 pixels for 90 pixels, got " + tight);
        HubGridFit.Fit huge = HubGridFit.fit(10_000);
        helper.assertTrue(huge.radius() == HubGridFit.MAX_RADIUS && huge.cell() == HubGridFit.MAX_CELL, "expected the biggest grid for plenty of space, got " + huge);
        helper.succeed();
    }

    /**
     * The cell under the mouse: the cells start at the grid's edge, the pixels beyond the last cell are outside, and with
     * clamping a mouse outside of the grid (as in a drag) gives the nearest cell on that side.
     */
    public static void gridIndexAtFindsTheCellAndClampsWhenAsked(GameTestHelper helper) {
        helper.assertTrue(HubGridFit.indexAt(0, 10, 5, false) == 0 && HubGridFit.indexAt(9, 10, 5, false) == 0, "expected pixels 0 and 9 in cell 0");
        helper.assertTrue(HubGridFit.indexAt(10, 10, 5, false) == 1 && HubGridFit.indexAt(49, 10, 5, false) == 4, "expected pixel 10 in cell 1 and pixel 49 in cell 4");
        helper.assertTrue(HubGridFit.indexAt(50, 10, 5, false) == -1 && HubGridFit.indexAt(-1, 10, 5, false) == -1, "expected pixels 50 and -1 outside of the grid");
        helper.assertTrue(HubGridFit.indexAt(50, 10, 5, true) == 4 && HubGridFit.indexAt(500, 10, 5, true) == 4, "expected clamping to the last cell");
        helper.assertTrue(HubGridFit.indexAt(-1, 10, 5, true) == 0 && HubGridFit.indexAt(-500, 10, 5, true) == 0, "expected clamping to the first cell");
        helper.assertTrue(HubGridFit.indexAt(3, 0, 5, true) == -1 && HubGridFit.indexAt(3, 10, 0, true) == -1, "expected no cell for a grid without size");
        helper.succeed();
    }

    /**
     * The kind of a claimed chunk: the server and expired owners win over everything, a claim owned by a party is the
     * party's whoever owns it, the local player's claims are team claims or private ones, and other party members are
     * told apart from other players.
     */
    public static void claimCategoryFollowsTheOwner(GameTestHelper helper) {
        // classify(server, expired, self, partyMember, partyOwned, team)
        helper.assertTrue(ClaimCategory.classify(true, false, false, false, false, false) == ClaimCategory.SERVER, "expected SERVER");
        helper.assertTrue(ClaimCategory.classify(true, true, true, true, true, true) == ClaimCategory.SERVER, "expected SERVER to win over everything");
        helper.assertTrue(ClaimCategory.classify(false, true, true, true, true, true) == ClaimCategory.EXPIRED, "expected EXPIRED to win over the player");
        helper.assertTrue(ClaimCategory.classify(false, false, true, false, false, false) == ClaimCategory.OWN_PRIVATE, "expected OWN_PRIVATE");
        helper.assertTrue(ClaimCategory.classify(false, false, true, false, false, true) == ClaimCategory.OWN_TEAM, "expected OWN_TEAM");
        helper.assertTrue(ClaimCategory.classify(false, false, false, true, false, false) == ClaimCategory.PARTY, "expected PARTY for another party member");
        helper.assertTrue(ClaimCategory.classify(false, false, false, true, false, true) == ClaimCategory.PARTY, "expected PARTY: the team flag only counts for the local player");
        helper.assertTrue(ClaimCategory.classify(false, false, false, false, false, false) == ClaimCategory.OTHER, "expected OTHER");
        helper.assertTrue(ClaimCategory.classify(false, false, false, false, true, false) == ClaimCategory.OTHER, "expected OTHER for the party claim of another party");
        helper.assertTrue(ClaimCategory.classify(false, false, true, false, true, false) == ClaimCategory.PARTY, "expected PARTY for a party claim of the local player");
        helper.assertTrue(ClaimCategory.classify(false, false, false, true, true, false) == ClaimCategory.PARTY, "expected PARTY for a party claim of a party member");
        helper.succeed();
    }

    /**
     * Which value the toggle writes to {@code claims.usedSub}: the team sub-config for team claims, "main" for private
     * ones, and "main" again when there is no team sub-config to pick. Reading the option back tells team from private.
     */
    public static void usedSubChoiceFollowsTheToggle(GameTestHelper helper) {
        helper.assertTrue("team_nytheria".equals(ClaimsHubLogic.usedSubFor(true, "team_nytheria")), "expected the team sub-config for a team choice");
        helper.assertTrue("main".equals(ClaimsHubLogic.usedSubFor(false, "team_nytheria")), "expected main for a private choice");
        helper.assertTrue("main".equals(ClaimsHubLogic.usedSubFor(true, null)), "expected main for a team choice without a team sub-config");
        helper.assertTrue("main".equals(ClaimsHubLogic.usedSubFor(false, null)), "expected main for a private choice without a team sub-config");

        helper.assertTrue(ClaimsHubLogic.isTeamSelected("team_nytheria") && ClaimsHubLogic.isTeamSelected("team_0a1b2c3d"), "expected team_ sub-configs to count as team claims");
        helper.assertTrue(!ClaimsHubLogic.isTeamSelected("main") && !ClaimsHubLogic.isTeamSelected("base") && !ClaimsHubLogic.isTeamSelected("teams") && !ClaimsHubLogic.isTeamSelected(null),
                "expected main, other sub-configs and null not to count as team claims");

        helper.assertTrue("team_nytheria".equals(ClaimsHubLogic.findTeamSubId(List.of("main", "base", "team_nytheria"))), "expected to find the team sub-config among the sub-configs");
        helper.assertTrue(ClaimsHubLogic.findTeamSubId(List.of("main", "base")) == null && ClaimsHubLogic.findTeamSubId(List.of()) == null, "expected no team sub-config to be found");
        helper.assertTrue(!ClaimsHubLogic.hasOtherSubConfigs(List.of("main", "team_nytheria")) && !ClaimsHubLogic.hasOtherSubConfigs(List.of("main")),
                "expected main and team sub-configs not to count as other sub-configs");
        helper.assertTrue(ClaimsHubLogic.hasOtherSubConfigs(List.of("main", "base", "team_nytheria")), "expected base to count as another sub-config");

        // a round trip of the toggle
        List<String> subs = List.of("main", "team_nytheria");
        String teamSub = ClaimsHubLogic.findTeamSubId(subs);
        String used = "main";
        used = ClaimsHubLogic.usedSubFor(!ClaimsHubLogic.isTeamSelected(used), teamSub);
        helper.assertTrue("team_nytheria".equals(used) && ClaimsHubLogic.isTeamSelected(used), "expected the first click to switch to the team sub-config, got " + used);
        used = ClaimsHubLogic.usedSubFor(!ClaimsHubLogic.isTeamSelected(used), teamSub);
        helper.assertTrue("main".equals(used) && !ClaimsHubLogic.isTeamSelected(used), "expected the second click to switch back to main, got " + used);
        helper.succeed();
    }

    /**
     * A claim of the local player is a team claim if it has the index of the team sub-config; while that index has not
     * been seen, it is one if the team sub-config is the only one besides main and the claim is not on the main config.
     */
    public static void ownTeamClaimIsRecognisedByTheSubIndex(GameTestHelper helper) {
        // isOwnTeamClaim(subIndex, knownTeamSubIndex, teamSubConfigExists, hasOtherSubConfigs)
        helper.assertTrue(ClaimsHubLogic.isOwnTeamClaim(3, 3, true, true), "expected the known team index to be a team claim even with other sub-configs");
        helper.assertTrue(!ClaimsHubLogic.isOwnTeamClaim(2, 3, true, false), "expected another index not to be a team claim when the team index is known");
        helper.assertTrue(!ClaimsHubLogic.isOwnTeamClaim(ClaimsHubLogic.MAIN_SUB_INDEX, 3, true, false), "expected the main config never to be a team claim");
        helper.assertTrue(!ClaimsHubLogic.isOwnTeamClaim(ClaimsHubLogic.MAIN_SUB_INDEX, null, true, false), "expected the main config never to be a team claim");
        helper.assertTrue(ClaimsHubLogic.isOwnTeamClaim(0, null, true, false), "expected a non-main claim to be the team's when there is no other sub-config");
        helper.assertTrue(!ClaimsHubLogic.isOwnTeamClaim(0, null, true, true), "expected a claim of an unknown sub-config not to count as a team claim when there are other sub-configs");
        helper.assertTrue(!ClaimsHubLogic.isOwnTeamClaim(0, null, false, false), "expected no team claims without a team sub-config");
        helper.assertTrue(!ClaimsHubLogic.isOwnTeamClaim(3, 3, false, false), "expected no team claims without a team sub-config, whatever was remembered");
        helper.succeed();
    }

    /**
     * The remembered sub-config indices: only team sub-configs are kept, the latest index wins, and a new session (a new
     * connection) forgets everything, since indices of different servers must not be mixed up.
     */
    public static void teamSubIndexMemoryForgetsWhenTheSessionChanges(GameTestHelper helper) {
        Object sessionA = new Object();
        Object sessionB = new Object();
        TeamSubIndexMemory memory = new TeamSubIndexMemory();
        helper.assertTrue(memory.get(sessionA, "team_nytheria") == null, "expected nothing to be remembered at first");
        memory.observe(sessionA, "main", -1);
        memory.observe(sessionA, "base", 4);
        helper.assertTrue(memory.get(sessionA, "main") == null && memory.get(sessionA, "base") == null, "expected only team sub-configs to be remembered");
        memory.observe(sessionA, "team_nytheria", 5);
        helper.assertTrue(Integer.valueOf(5).equals(memory.get(sessionA, "team_nytheria")), "expected the index 5 to be remembered");
        memory.observe(sessionA, "team_nytheria", 6);
        helper.assertTrue(Integer.valueOf(6).equals(memory.get(sessionA, "team_nytheria")), "expected the latest index 6 to win");
        helper.assertTrue(memory.get(sessionA, null) == null, "expected no index for no sub-config");
        helper.assertTrue(memory.get(sessionB, "team_nytheria") == null, "expected a new session to forget the index");
        helper.assertTrue(memory.get(sessionA, "team_nytheria") == null, "expected going back to the old session not to bring it back");
        memory.observe(sessionB, "team_other", 1);
        helper.assertTrue(Integer.valueOf(1).equals(memory.get(sessionB, "team_other")), "expected the index of the new session to be remembered");
        helper.succeed();
    }

}
