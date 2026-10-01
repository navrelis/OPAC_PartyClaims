package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsOverviewTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsOverviewTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void infoShowsTeamNumbersAndBudget(GameTestHelper helper) {
        TeamClaimsOverviewTestCases.infoShowsTeamNumbersAndBudget(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void infoShowsForceloadState(GameTestHelper helper) {
        TeamClaimsOverviewTestCases.infoShowsForceloadState(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void overviewCommandsRunAsTeamPlayer(GameTestHelper helper) {
        TeamClaimsOverviewTestCases.overviewCommandsRunAsTeamPlayer(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void overviewCommandsWithoutTeam(GameTestHelper helper) {
        TeamClaimsOverviewTestCases.overviewCommandsWithoutTeam(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void listPagesAreSortedAndNavigable(GameTestHelper helper) {
        TeamClaimsOverviewTestCases.listPagesAreSortedAndNavigable(helper);
    }
}
