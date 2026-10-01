package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsOverviewTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsOverviewTestNeoForge {

    private TeamClaimsOverviewTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void infoShowsTeamNumbersAndBudget(GameTestHelper helper) {
        TeamClaimsOverviewTestCases.infoShowsTeamNumbersAndBudget(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void infoShowsForceloadState(GameTestHelper helper) {
        TeamClaimsOverviewTestCases.infoShowsForceloadState(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void overviewCommandsRunAsTeamPlayer(GameTestHelper helper) {
        TeamClaimsOverviewTestCases.overviewCommandsRunAsTeamPlayer(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void overviewCommandsWithoutTeam(GameTestHelper helper) {
        TeamClaimsOverviewTestCases.overviewCommandsWithoutTeam(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void listPagesAreSortedAndNavigable(GameTestHelper helper) {
        TeamClaimsOverviewTestCases.listPagesAreSortedAndNavigable(helper);
    }
}
