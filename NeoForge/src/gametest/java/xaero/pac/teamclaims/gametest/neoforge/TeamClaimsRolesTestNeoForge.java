package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsRolesTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsRolesTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsRolesTestNeoForge {

    private TeamClaimsRolesTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void defaultRolesKeepBehaviour(GameTestHelper helper) {
        TeamClaimsRolesTestCases.defaultRolesKeepBehaviour(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void claimRoleRejectsLowerRanks(GameTestHelper helper) {
        TeamClaimsRolesTestCases.claimRoleRejectsLowerRanks(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void unclaimRoleRejectsLowerRanks(GameTestHelper helper) {
        TeamClaimsRolesTestCases.unclaimRoleRejectsLowerRanks(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void forceloadRoleRejectsBelowOwner(GameTestHelper helper) {
        TeamClaimsRolesTestCases.forceloadRoleRejectsBelowOwner(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void forcedActionsBypassRoles(GameTestHelper helper) {
        TeamClaimsRolesTestCases.forcedActionsBypassRoles(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void rolesCommandAndPersistence(GameTestHelper helper) {
        TeamClaimsRolesTestCases.rolesCommandAndPersistence(helper);
    }
}
