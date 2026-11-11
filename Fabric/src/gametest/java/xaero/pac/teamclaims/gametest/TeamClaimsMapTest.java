package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsMapTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsMapTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void landIsShadedByTheHeightDifferenceToTheNorth(GameTestHelper helper) {
        TeamClaimsMapTestCases.landIsShadedByTheHeightDifferenceToTheNorth(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void waterIsDarkerTheDeeperItIs(GameTestHelper helper) {
        TeamClaimsMapTestCases.waterIsDarkerTheDeeperItIs(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void terrainScheduleKeepsTheBudgetAndPicksTheNearestFirst(GameTestHelper helper) {
        TeamClaimsMapTestCases.terrainScheduleKeepsTheBudgetAndPicksTheNearestFirst(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void claimOverlayMixesColoursAndDrawsOneLineBetweenOwners(GameTestHelper helper) {
        TeamClaimsMapTestCases.claimOverlayMixesColoursAndDrawsOneLineBetweenOwners(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void alliedClaimsAreTheirOwnCategory(GameTestHelper helper) {
        TeamClaimsMapTestCases.alliedClaimsAreTheirOwnCategory(helper);
    }
}
