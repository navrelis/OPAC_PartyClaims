package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsMapTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsMapTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsMapTestNeoForge {

    private TeamClaimsMapTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void landIsShadedByTheHeightDifferenceToTheNorth(GameTestHelper helper) {
        TeamClaimsMapTestCases.landIsShadedByTheHeightDifferenceToTheNorth(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void waterIsDarkerTheDeeperItIs(GameTestHelper helper) {
        TeamClaimsMapTestCases.waterIsDarkerTheDeeperItIs(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void terrainScheduleKeepsTheBudgetAndPicksTheNearestFirst(GameTestHelper helper) {
        TeamClaimsMapTestCases.terrainScheduleKeepsTheBudgetAndPicksTheNearestFirst(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void claimOverlayMixesColoursAndDrawsOneLineBetweenOwners(GameTestHelper helper) {
        TeamClaimsMapTestCases.claimOverlayMixesColoursAndDrawsOneLineBetweenOwners(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void alliedClaimsAreTheirOwnCategory(GameTestHelper helper) {
        TeamClaimsMapTestCases.alliedClaimsAreTheirOwnCategory(helper);
    }
}
