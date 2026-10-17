package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsHubTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsHubTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsHubTestNeoForge {

    private TeamClaimsHubTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void selectionIsTheSameRectangleInEveryDragDirection(GameTestHelper helper) {
        TeamClaimsHubTestCases.selectionIsTheSameRectangleInEveryDragDirection(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void selectionWarnsOnlyAboveTheDefaultActionSize(GameTestHelper helper) {
        TeamClaimsHubTestCases.selectionWarnsOnlyAboveTheDefaultActionSize(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void gridFitNeverOverflowsAndKeepsEightChunksWhenItCan(GameTestHelper helper) {
        TeamClaimsHubTestCases.gridFitNeverOverflowsAndKeepsEightChunksWhenItCan(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void gridIndexAtFindsTheCellAndClampsWhenAsked(GameTestHelper helper) {
        TeamClaimsHubTestCases.gridIndexAtFindsTheCellAndClampsWhenAsked(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void claimCategoryFollowsTheOwner(GameTestHelper helper) {
        TeamClaimsHubTestCases.claimCategoryFollowsTheOwner(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void usedSubChoiceFollowsTheToggle(GameTestHelper helper) {
        TeamClaimsHubTestCases.usedSubChoiceFollowsTheToggle(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void ownTeamClaimIsRecognisedByTheSubIndex(GameTestHelper helper) {
        TeamClaimsHubTestCases.ownTeamClaimIsRecognisedByTheSubIndex(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamSubIndexMemoryForgetsWhenTheSessionChanges(GameTestHelper helper) {
        TeamClaimsHubTestCases.teamSubIndexMemoryForgetsWhenTheSessionChanges(helper);
    }
}
