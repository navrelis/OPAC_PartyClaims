package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsHardeningTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}; like the Fabric wrappers in
 * {@code TeamClaimsLogicTest}, they all use the default timeout.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsHardeningTestNeoForge {

    private TeamClaimsHardeningTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void quickJoinAndLeaveTransfersImmediately(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.quickJoinAndLeaveTransfersImmediately(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void overheadStaysCorrectAfterManyChanges(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.overheadStaysCorrectAfterManyChanges(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamCreateValidatesName(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.teamCreateValidatesName(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void lateJoinerGetsAdminSetOptions(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.lateJoinerGetsAdminSetOptions(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void nonAsciiTeamNamesGetDistinctSubIds(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.nonAsciiTeamNamesGetDistinctSubIds(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void corruptTeamConfigIsPreserved(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.corruptTeamConfigIsPreserved(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamForceloadTicketsAreBalanced(GameTestHelper helper) {
        TeamClaimsHardeningTestCases.teamForceloadTicketsAreBalanced(helper);
    }
}
