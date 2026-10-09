package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsBudgetTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsBudgetTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamFullDoesNotBlockPrivateClaims(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.teamFullDoesNotBlockPrivateClaims(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamLimitFollowsMemberCount(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.teamLimitFollowsMemberCount(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void reclaimIsCheckedAgainstDestinationBudget(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.reclaimIsCheckedAgainstDestinationBudget(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void forceloadBudgetsAreIndependent(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.forceloadBudgetsAreIndependent(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamForceloadsDoNotUseUpPrivateTickets(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.teamForceloadsDoNotUseUpPrivateTickets(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void claimLimitsPacketFollowsUsedSubConfig(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.claimLimitsPacketFollowsUsedSubConfig(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void areaClaimsUseTheBudgetOfTheirSubConfig(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.areaClaimsUseTheBudgetOfTheirSubConfig(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamClaimStaysOpenWhenOwnerIsOverPrivateLimit(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.teamClaimStaysOpenWhenOwnerIsOverPrivateLimit(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void partyRemovalKeepsWithinPrivateRoom(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.partyRemovalKeepsWithinPrivateRoom(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void overLimitDeadlineLifecycle(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.overLimitDeadlineLifecycle(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void overLimitMessagesReachOnlineMembers(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.overLimitMessagesReachOnlineMembers(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void forceloadOverLimitTurnsNewestOff(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.forceloadOverLimitTurnsNewestOff(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void graceZeroRemovesAtNextCheck(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.graceZeroRemovesAtNextCheck(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void oldFormatSavedDataLoads(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.oldFormatSavedDataLoads(helper);
    }
}
