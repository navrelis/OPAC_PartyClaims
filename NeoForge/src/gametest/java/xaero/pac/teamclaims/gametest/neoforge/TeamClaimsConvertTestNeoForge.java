package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsConvertTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsConvertTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsConvertTestNeoForge {

    private TeamClaimsConvertTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void toTeamConvertsOwnClaims(GameTestHelper helper) {
        TeamClaimsConvertTestCases.toTeamConvertsOwnClaims(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void toPersonalConvertsBack(GameTestHelper helper) {
        TeamClaimsConvertTestCases.toPersonalConvertsBack(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void toTeamStopsAtTeamLimit(GameTestHelper helper) {
        TeamClaimsConvertTestCases.toTeamStopsAtTeamLimit(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void toPersonalStopsAtPrivateLimit(GameTestHelper helper) {
        TeamClaimsConvertTestCases.toPersonalStopsAtPrivateLimit(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void toTeamSkipsForceloadedAtTeamForceloadLimit(GameTestHelper helper) {
        TeamClaimsConvertTestCases.toTeamSkipsForceloadedAtTeamForceloadLimit(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void conversionNeedsTeamRoles(GameTestHelper helper) {
        TeamClaimsConvertTestCases.conversionNeedsTeamRoles(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void toTeamForceloadedNeedsForceloadRole(GameTestHelper helper) {
        TeamClaimsConvertTestCases.toTeamForceloadedNeedsForceloadRole(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void convertRadiusAndTeamChecks(GameTestHelper helper) {
        TeamClaimsConvertTestCases.convertRadiusAndTeamChecks(helper);
    }
}
