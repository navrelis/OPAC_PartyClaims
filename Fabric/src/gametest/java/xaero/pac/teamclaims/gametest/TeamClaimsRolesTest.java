package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsRolesTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsRolesTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void defaultRolesKeepBehaviour(GameTestHelper helper) {
        TeamClaimsRolesTestCases.defaultRolesKeepBehaviour(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void claimRoleRejectsLowerRanks(GameTestHelper helper) {
        TeamClaimsRolesTestCases.claimRoleRejectsLowerRanks(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void unclaimRoleRejectsLowerRanks(GameTestHelper helper) {
        TeamClaimsRolesTestCases.unclaimRoleRejectsLowerRanks(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void forceloadRoleRejectsBelowOwner(GameTestHelper helper) {
        TeamClaimsRolesTestCases.forceloadRoleRejectsBelowOwner(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void forcedActionsBypassRoles(GameTestHelper helper) {
        TeamClaimsRolesTestCases.forcedActionsBypassRoles(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void rolesCommandAndPersistence(GameTestHelper helper) {
        TeamClaimsRolesTestCases.rolesCommandAndPersistence(helper);
    }
}
