package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating
 * to the loader-neutral {@link TeamClaimsLogicTestCases} (shared dir Common/src/gametest), where
 * each test is documented. See {@link TeamClaimsSmokeTestNeoForge} for how registration works.
 * <p>
 * Adding a shared test case = one more static method here (or in a new {@code @GameTestHolder}
 * class) with {@code @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)} that delegates
 * to the Common method.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsLogicTestNeoForge {

    private TeamClaimsLogicTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamSubConfigCreated(GameTestHelper helper) {
        TeamClaimsLogicTestCases.teamSubConfigCreated(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamClaimCountsAsOverhead(GameTestHelper helper) {
        TeamClaimsLogicTestCases.teamClaimCountsAsOverhead(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamClaimRejectedWhenTeammateAtLimit(GameTestHelper helper) {
        TeamClaimsLogicTestCases.teamClaimRejectedWhenTeammateAtLimit(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teammateCanUnclaimTeamClaim(GameTestHelper helper) {
        TeamClaimsLogicTestCases.teammateCanUnclaimTeamClaim(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teammateCanToggleForceload(GameTestHelper helper) {
        TeamClaimsLogicTestCases.teammateCanToggleForceload(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void nonAdminCannotEditTeamSubConfig(GameTestHelper helper) {
        TeamClaimsLogicTestCases.nonAdminCannotEditTeamSubConfig(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsLogicTestCases.LEAVING_MEMBER_TIMEOUT_TICKS)
    public static void leavingMemberClaimsTransferToOwner(GameTestHelper helper) {
        TeamClaimsLogicTestCases.leavingMemberClaimsTransferToOwner(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void longTeamNameSubConfig(GameTestHelper helper) {
        TeamClaimsLogicTestCases.longTeamNameSubConfig(helper);
    }
}
