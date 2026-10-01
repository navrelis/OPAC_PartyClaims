package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsConfigTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsConfigTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsConfigTestNeoForge {

    private TeamClaimsConfigTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void serverConfigDefaultsAndTeamClaimsActive(GameTestHelper helper) {
        TeamClaimsConfigTestCases.serverConfigDefaultsAndTeamClaimsActive(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void maxTeamNameLengthIsConfigurable(GameTestHelper helper) {
        TeamClaimsConfigTestCases.maxTeamNameLengthIsConfigurable(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void forceloadGraceKeepsTeamLoadedUntilItPasses(GameTestHelper helper) {
        TeamClaimsConfigTestCases.forceloadGraceKeepsTeamLoadedUntilItPasses(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void forceloadGraceCancelledByReturningMember(GameTestHelper helper) {
        TeamClaimsConfigTestCases.forceloadGraceCancelledByReturningMember(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void noGraceReleasesForceloadsImmediately(GameTestHelper helper) {
        TeamClaimsConfigTestCases.noGraceReleasesForceloadsImmediately(helper);
    }
}
