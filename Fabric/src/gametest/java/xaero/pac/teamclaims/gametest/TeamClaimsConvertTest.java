package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsConvertTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsConvertTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void toTeamConvertsOwnClaims(GameTestHelper helper) {
        TeamClaimsConvertTestCases.toTeamConvertsOwnClaims(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void toPersonalConvertsBack(GameTestHelper helper) {
        TeamClaimsConvertTestCases.toPersonalConvertsBack(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void toTeamStopsAtBudget(GameTestHelper helper) {
        TeamClaimsConvertTestCases.toTeamStopsAtBudget(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void conversionNeedsTeamRoles(GameTestHelper helper) {
        TeamClaimsConvertTestCases.conversionNeedsTeamRoles(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void toTeamForceloadedNeedsForceloadRole(GameTestHelper helper) {
        TeamClaimsConvertTestCases.toTeamForceloadedNeedsForceloadRole(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void convertRadiusAndTeamChecks(GameTestHelper helper) {
        TeamClaimsConvertTestCases.convertRadiusAndTeamChecks(helper);
    }
}
