package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsConfigTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsConfigTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void serverConfigDefaultsAndTeamClaimsActive(GameTestHelper helper) {
        TeamClaimsConfigTestCases.serverConfigDefaultsAndTeamClaimsActive(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void maxTeamNameLengthIsConfigurable(GameTestHelper helper) {
        TeamClaimsConfigTestCases.maxTeamNameLengthIsConfigurable(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void forceloadGraceKeepsTeamLoadedUntilItPasses(GameTestHelper helper) {
        TeamClaimsConfigTestCases.forceloadGraceKeepsTeamLoadedUntilItPasses(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void forceloadGraceCancelledByReturningMember(GameTestHelper helper) {
        TeamClaimsConfigTestCases.forceloadGraceCancelledByReturningMember(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void noGraceReleasesForceloadsImmediately(GameTestHelper helper) {
        TeamClaimsConfigTestCases.noGraceReleasesForceloadsImmediately(helper);
    }
}
