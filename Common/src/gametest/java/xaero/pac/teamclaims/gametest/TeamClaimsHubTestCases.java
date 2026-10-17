package xaero.pac.teamclaims.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import xaero.pac.client.gui.hub.ChunkSelection;
import xaero.pac.client.gui.hub.ClaimCategory;
import xaero.pac.client.gui.hub.ClaimsHubLogic;
import xaero.pac.client.gui.hub.HubGridFit;
import xaero.pac.client.gui.hub.RefreshSchedule;
import xaero.pac.client.gui.hub.RequestThrottle;
import xaero.pac.client.gui.hub.TeamHubLogic;
import xaero.pac.common.packet.parties.PartyBudgetData;
import xaero.pac.common.packet.teamclaims.TeamInfoData;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamRole;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dev/test-only, loader-neutral test bodies for the pure rules behind the Claims tab of the hub screen
 * ({@link ChunkSelection}, {@link HubGridFit}, {@link ClaimCategory}, {@link ClaimsHubLogic}, {@link TeamHubLogic}, {@link RefreshSchedule}, {@link RequestThrottle}).
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
     * A selection is too big for the buttons when it has more chunks than the limit the server sent: 11 x 11 = 121 is fine
     * at the default limit of 121, 11 x 12 is not; a limit of 0 blocks everything; while the limit is not known, nothing is
     * blocked.
     */
    public static void selectionIsTooBigOnlyAboveTheServerActionSize(GameTestHelper helper) {
        ChunkSelection exactly = ChunkSelection.single(0, 0).withEnd(10, 10);
        helper.assertTrue(exactly.count() == 121 && !exactly.exceedsActionSize(121), "expected 11 x 11 = 121 chunks not to exceed a limit of 121, got " + exactly.count());
        ChunkSelection above = ChunkSelection.single(0, 0).withEnd(10, 11);
        helper.assertTrue(above.count() == 132 && above.exceedsActionSize(121), "expected 11 x 12 = 132 chunks to exceed a limit of 121, got " + above.count());
        helper.assertTrue(!above.exceedsActionSize(132) && above.exceedsActionSize(131), "expected the limit to be inclusive");
        helper.assertTrue(!ChunkSelection.single(5, 5).exceedsActionSize(1) && ChunkSelection.single(5, 5).exceedsActionSize(0), "expected one chunk to fit a limit of 1 but not 0");
        helper.assertTrue(!above.exceedsActionSize(null), "expected nothing to be too big while the limit is unknown");
        ChunkSelection huge = ChunkSelection.single(Integer.MIN_VALUE / 2, 0).withEnd(Integer.MAX_VALUE / 2, 100000);
        helper.assertTrue(huge.exceedsActionSize(Integer.MAX_VALUE), "expected a huge selection to exceed even the biggest limit");
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
     * A claim of the local player is a team claim if it has the index of the team sub-config; while that index is not
     * known, it is one if the team sub-config is the only one besides main and the claim is not on the main config.
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
     * The index of the team claims comes from the team information of the server; before it arrives, it is known when the
     * team sub-config is the one in use; otherwise it is unknown (null).
     */
    public static void teamSubIndexComesFromTheTeamInfoBeforeTheSubInUse(GameTestHelper helper) {
        TeamInfoData info = teamInfo(true, 4, TeamRole.MEMBER, TeamRole.MEMBER, TeamRole.MEMBER, true);
        // resolveTeamSubIndex(info, teamSubId, usedSubId, usedSubIndex)
        helper.assertTrue(Integer.valueOf(4).equals(ClaimsHubLogic.resolveTeamSubIndex(info, "team_nytheria", "main", -1)), "expected the index of the team information");
        helper.assertTrue(Integer.valueOf(4).equals(ClaimsHubLogic.resolveTeamSubIndex(info, "team_nytheria", "team_nytheria", 9)), "expected the team information to win over the sub-config in use");
        helper.assertTrue(Integer.valueOf(9).equals(ClaimsHubLogic.resolveTeamSubIndex(null, "team_nytheria", "team_nytheria", 9)), "expected the index of the sub-config in use while the team information is missing");
        helper.assertTrue(ClaimsHubLogic.resolveTeamSubIndex(null, "team_nytheria", "main", -1) == null, "expected no index when the team sub-config is not in use");
        helper.assertTrue(ClaimsHubLogic.resolveTeamSubIndex(null, "team_nytheria", "team_other", 2) == null, "expected no index when another sub-config is in use");
        helper.assertTrue(ClaimsHubLogic.resolveTeamSubIndex(null, null, "main", -1) == null, "expected no index without a team sub-config");
        TeamInfoData noIndex = teamInfo(true, -1, TeamRole.MEMBER, TeamRole.MEMBER, TeamRole.MEMBER, true);
        helper.assertTrue(Integer.valueOf(7).equals(ClaimsHubLogic.resolveTeamSubIndex(noIndex, "team_nytheria", "team_nytheria", 7)), "expected the sub-config in use when the team information has no index");
        helper.assertTrue(ClaimsHubLogic.resolveTeamSubIndex(TeamInfoData.unavailable(121), "team_nytheria", "main", -1) == null, "expected an unavailable team information to give no index");
        helper.succeed();
    }

    /**
     * A click on a role button picks the next rank and wraps around after the owner, so five clicks make a full circle; the
     * radius button does the same up to its maximum and never beyond the largest radius the server allows.
     */
    public static void roleAndRadiusButtonsCycleAndWrap(GameTestHelper helper) {
        helper.assertTrue(TeamHubLogic.nextRole(TeamRole.MEMBER) == TeamRole.CLAIMER, "expected claimer after member");
        helper.assertTrue(TeamHubLogic.nextRole(TeamRole.CLAIMER) == TeamRole.MODERATOR, "expected moderator after claimer");
        helper.assertTrue(TeamHubLogic.nextRole(TeamRole.MODERATOR) == TeamRole.ADMIN, "expected admin after moderator");
        helper.assertTrue(TeamHubLogic.nextRole(TeamRole.ADMIN) == TeamRole.OWNER, "expected owner after admin");
        helper.assertTrue(TeamHubLogic.nextRole(TeamRole.OWNER) == TeamRole.MEMBER, "expected the circle to close with member after owner");
        for (TeamRole start : TeamRole.values()) {
            TeamRole role = start;
            Set<TeamRole> seen = new HashSet<>();
            for (int i = 0; i < TeamRole.values().length; i++) {
                seen.add(role);
                role = TeamHubLogic.nextRole(role);
            }
            helper.assertTrue(role == start && seen.size() == TeamRole.values().length, "expected five clicks from " + start + " to visit every role and come back, got " + role);
        }

        int max = TeamHubLogic.DEFAULT_MAX_CONVERT_RADIUS;
        helper.assertTrue(max == 4, "expected the default maximum radius to be the server default 4");
        helper.assertTrue(TeamHubLogic.nextRadius(0, max) == 1 && TeamHubLogic.nextRadius(3, max) == 4, "expected the radius to grow by one");
        helper.assertTrue(TeamHubLogic.nextRadius(max, max) == 0, "expected the radius to wrap to 0 after the maximum");
        helper.assertTrue(TeamHubLogic.nextRadius(-3, max) == 0 && TeamHubLogic.nextRadius(99, max) == 0, "expected an out-of-range radius to go back to 0");
        helper.assertTrue(TeamHubLogic.nextRadius(5, 1000) == 6 && TeamHubLogic.nextRadius(TeamHubLogic.ABSOLUTE_MAX_CONVERT_RADIUS, 1000) == 0,
                "expected the radius never to go beyond the largest radius the server allows");
        helper.succeed();
    }

    /**
     * The commands of the Team tab are exactly the ones the command trees accept: every combination of action and role for
     * {@code roles}, on and off for the territory messages, and {@code convert} with a radius from 0 to 16, built from enum
     * values and numbers only. A radius outside of that gives no command at all.
     */
    public static void teamCommandsAreBuiltFromEnumValuesOnly(GameTestHelper helper) {
        helper.assertTrue("teamclaims roles claim member".equals(TeamHubLogic.rolesCommand(TeamAction.CLAIM, TeamRole.MEMBER)), "expected the claim/member roles command");
        helper.assertTrue("teamclaims roles unclaim moderator".equals(TeamHubLogic.rolesCommand(TeamAction.UNCLAIM, TeamRole.MODERATOR)), "expected the unclaim/moderator roles command");
        helper.assertTrue("teamclaims roles forceload owner".equals(TeamHubLogic.rolesCommand(TeamAction.FORCELOAD, TeamRole.OWNER)), "expected the forceload/owner roles command");
        Set<String> commands = new HashSet<>();
        for (TeamAction action : TeamAction.values())
            for (TeamRole role : TeamRole.values()) {
                String command = TeamHubLogic.rolesCommand(action, role);
                helper.assertTrue(command.matches("teamclaims roles (claim|unclaim|forceload) (member|claimer|moderator|admin|owner)"), "expected a command of the roles syntax, got " + command);
                commands.add(command);
            }
        helper.assertTrue(commands.size() == 15, "expected 15 different roles commands, got " + commands.size());

        helper.assertTrue("teamclaims territorymessages on".equals(TeamHubLogic.territoryMessagesCommand(true)), "expected the territory messages on command");
        helper.assertTrue("teamclaims territorymessages off".equals(TeamHubLogic.territoryMessagesCommand(false)), "expected the territory messages off command");

        helper.assertTrue("teamclaims convert toteam 0".equals(TeamHubLogic.convertCommand(true, 0)), "expected the convert toteam command with radius 0");
        helper.assertTrue("teamclaims convert topersonal 4".equals(TeamHubLogic.convertCommand(false, 4)), "expected the convert topersonal command with radius 4");
        helper.assertTrue("teamclaims convert toteam 16".equals(TeamHubLogic.convertCommand(true, 16)), "expected the largest radius to be accepted");
        helper.assertTrue(TeamHubLogic.convertCommand(true, -1) == null && TeamHubLogic.convertCommand(false, 17) == null && TeamHubLogic.convertCommand(true, Integer.MAX_VALUE) == null,
                "expected no command for a radius outside of 0 to 16");
        for (int radius = 0; radius <= TeamHubLogic.ABSOLUTE_MAX_CONVERT_RADIUS; radius++)
            helper.assertTrue(TeamHubLogic.convertCommand(radius % 2 == 0, radius).matches("teamclaims convert (toteam|topersonal) \\d{1,2}"), "expected a command of the convert syntax for the radius " + radius);
        helper.succeed();
    }

    /**
     * The Team tab is only for players in a party. The team information decides once it has arrived (available or not),
     * before that the budget does (in a team or not, and only if the server has budgets at all).
     */
    public static void teamTabVisibilityFollowsTheTeamInfoThenTheBudget(GameTestHelper helper) {
        TeamInfoData available = teamInfo(true, 2, TeamRole.MEMBER, TeamRole.MEMBER, TeamRole.MEMBER, false);
        TeamInfoData unavailable = TeamInfoData.unavailable(121);
        PartyBudgetData inTeam = budget(true, true);
        PartyBudgetData noTeam = budget(true, false);
        // isTeamTabVisible(inParty, info, budget)
        helper.assertTrue(TeamHubLogic.isTeamTabVisible(true, available, null), "expected the tab with available team information");
        helper.assertTrue(TeamHubLogic.isTeamTabVisible(true, available, noTeam), "expected the team information to win over an older budget");
        helper.assertTrue(!TeamHubLogic.isTeamTabVisible(true, unavailable, inTeam), "expected no tab with unavailable team information, whatever the budget says");
        helper.assertTrue(!TeamHubLogic.isTeamTabVisible(false, available, inTeam), "expected no tab outside of a party, whatever the information says");
        helper.assertTrue(TeamHubLogic.isTeamTabVisible(true, null, inTeam), "expected the budget to decide without team information");
        helper.assertTrue(!TeamHubLogic.isTeamTabVisible(true, null, noTeam), "expected no tab without a team in the budget");
        helper.assertTrue(!TeamHubLogic.isTeamTabVisible(true, null, PartyBudgetData.UNAVAILABLE), "expected no tab without budgets");
        helper.assertTrue(!TeamHubLogic.isTeamTabVisible(true, null, null), "expected no tab without any information");
        helper.succeed();
    }

    /**
     * The refresh schedule asks once per period, asks a set time after an action (a series of actions makes one request),
     * and starts the period over after any request.
     */
    public static void refreshScheduleAsksPeriodicallyAndAfterActions(GameTestHelper helper) {
        RefreshSchedule schedule = new RefreshSchedule(40, 20);
        int due = 0;
        for (int tick = 1; tick <= 120; tick++) {
            boolean fired = schedule.tick();
            if (fired) {
                due++;
                helper.assertTrue(tick % 40 == 0, "expected a periodic request only every 40 ticks, got one at " + tick);
            }
        }
        helper.assertTrue(due == 3, "expected 3 periodic requests in 120 ticks, got " + due);

        schedule = new RefreshSchedule(40, 20);
        for (int i = 0; i < 5; i++)
            helper.assertTrue(!schedule.tick(), "expected nothing to be due at first");
        schedule.afterAction();
        for (int i = 0; i < 10; i++)
            schedule.tick();
        schedule.afterAction();//another action restarts the wait
        int fireTick = -1;
        for (int tick = 1; tick <= 30 && fireTick < 0; tick++)
            if (schedule.tick())
                fireTick = tick;
        helper.assertTrue(fireTick == 20, "expected the request 20 ticks after the last action, got " + fireTick);
        // the period starts over after that request: the next periodic one is 40 ticks later
        int next = -1;
        for (int tick = 1; tick <= 60 && next < 0; tick++)
            if (schedule.tick())
                next = tick;
        helper.assertTrue(next == 40, "expected the next periodic request 40 ticks after the action request, got " + next);

        schedule = new RefreshSchedule(40, 20);
        for (int i = 0; i < 30; i++)
            schedule.tick();
        schedule.onRequestSent();
        for (int i = 0; i < 39; i++)
            helper.assertTrue(!schedule.tick(), "expected a request made elsewhere to push the periodic one back");
        helper.assertTrue(schedule.tick(), "expected the periodic request 40 ticks after the request made elsewhere");
        helper.succeed();
    }

    /**
     * The shared throttle lets the first request through, refuses the ones within the interval, lets one through again
     * after it (also if the clock jumped back), and starts over after a reset.
     */
    public static void requestThrottleLetsOneRequestPerInterval(GameTestHelper helper) {
        RequestThrottle throttle = new RequestThrottle(400);
        helper.assertTrue(throttle.tryAcquire(1000), "expected the first request to go through");
        helper.assertTrue(!throttle.tryAcquire(1000) && !throttle.tryAcquire(1399), "expected requests within 400 ms to be refused");
        helper.assertTrue(throttle.tryAcquire(1400), "expected a request after 400 ms to go through");
        helper.assertTrue(!throttle.tryAcquire(1500), "expected the interval to count from the last request that went through");
        helper.assertTrue(throttle.tryAcquire(0), "expected a request to go through when the clock went back");
        throttle.reset();
        helper.assertTrue(throttle.tryAcquire(1), "expected a request to go through after a reset");
        helper.succeed();
    }

    // ==================== Helpers ====================

    private static TeamInfoData teamInfo(boolean canEdit, int subIndex, TeamRole claim, TeamRole unclaim, TeamRole forceload, boolean territory) {
        return new TeamInfoData(121, true, new UUID(1, 2), "Nytheria", "team_nytheria", subIndex, 3, claim, unclaim, forceload, territory, canEdit, 0, 0, 0x336699, "Home");
    }

    private static PartyBudgetData budget(boolean available, boolean inTeam) {
        return new PartyBudgetData(available, 1, 10, 0, 5, inTeam, inTeam ? 3 : 0, 2, 4, 20, 1, 5, 0, 0, 0, 0, 2, 1);
    }

}
