package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsHubTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsHubTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void selectionIsTheSameRectangleInEveryDragDirection(GameTestHelper helper) {
        TeamClaimsHubTestCases.selectionIsTheSameRectangleInEveryDragDirection(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void selectionWarnsOnlyAboveTheDefaultActionSize(GameTestHelper helper) {
        TeamClaimsHubTestCases.selectionWarnsOnlyAboveTheDefaultActionSize(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void gridFitNeverOverflowsAndKeepsEightChunksWhenItCan(GameTestHelper helper) {
        TeamClaimsHubTestCases.gridFitNeverOverflowsAndKeepsEightChunksWhenItCan(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void gridIndexAtFindsTheCellAndClampsWhenAsked(GameTestHelper helper) {
        TeamClaimsHubTestCases.gridIndexAtFindsTheCellAndClampsWhenAsked(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void claimCategoryFollowsTheOwner(GameTestHelper helper) {
        TeamClaimsHubTestCases.claimCategoryFollowsTheOwner(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void usedSubChoiceFollowsTheToggle(GameTestHelper helper) {
        TeamClaimsHubTestCases.usedSubChoiceFollowsTheToggle(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void ownTeamClaimIsRecognisedByTheSubIndex(GameTestHelper helper) {
        TeamClaimsHubTestCases.ownTeamClaimIsRecognisedByTheSubIndex(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamSubIndexMemoryForgetsWhenTheSessionChanges(GameTestHelper helper) {
        TeamClaimsHubTestCases.teamSubIndexMemoryForgetsWhenTheSessionChanges(helper);
    }
}
