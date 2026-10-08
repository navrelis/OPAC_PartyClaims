package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsLogicTestCases} and {@link TeamClaimsHardeningTestCases} (shared dir
 * Common/src/gametest), where each test is documented.
 * <p>
 * Lives entirely in the "gametest" source set (Fabric/src/gametest), which is NOT part of the
 * production jar (see {@link TeamClaimsSmokeTest} for the full explanation of that setup).
 */
public class TeamClaimsLogicTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamSubConfigCreated(GameTestHelper helper) {
        TeamClaimsLogicTestCases.teamSubConfigCreated(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamClaimsCountInTeamBudgetOnly(GameTestHelper helper) {
        TeamClaimsLogicTestCases.teamClaimsCountInTeamBudgetOnly(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void privateLimitDoesNotBlockTeamClaims(GameTestHelper helper) {
        TeamClaimsLogicTestCases.privateLimitDoesNotBlockTeamClaims(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teammateCanUnclaimTeamClaim(GameTestHelper helper) {
        TeamClaimsLogicTestCases.teammateCanUnclaimTeamClaim(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teammateCanToggleForceload(GameTestHelper helper) {
        TeamClaimsLogicTestCases.teammateCanToggleForceload(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void nonAdminCannotEditTeamSubConfig(GameTestHelper helper) {
        TeamClaimsLogicTestCases.nonAdminCannotEditTeamSubConfig(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsLogicTestCases.LEAVING_MEMBER_TIMEOUT_TICKS)
    public void leavingMemberClaimsTransferToOwner(GameTestHelper helper) {
        TeamClaimsLogicTestCases.leavingMemberClaimsTransferToOwner(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void longTeamNameSubConfig(GameTestHelper helper) {
        TeamClaimsLogicTestCases.longTeamNameSubConfig(helper);
    }

    // Shared bodies in TeamClaimsHardeningTestCases

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void quickJoinAndLeaveTransfersImmediately(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.quickJoinAndLeaveTransfersImmediately(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void budgetsStayCorrectAfterManyChanges(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.budgetsStayCorrectAfterManyChanges(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamCreateValidatesName(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.teamCreateValidatesName(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void lateJoinerGetsAdminSetOptions(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.lateJoinerGetsAdminSetOptions(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void nonAsciiTeamNamesGetDistinctSubIds(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.nonAsciiTeamNamesGetDistinctSubIds(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void corruptTeamConfigIsPreserved(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.corruptTeamConfigIsPreserved(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamForceloadTicketsAreBalanced(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.teamForceloadTicketsAreBalanced(helper);
    }
}
