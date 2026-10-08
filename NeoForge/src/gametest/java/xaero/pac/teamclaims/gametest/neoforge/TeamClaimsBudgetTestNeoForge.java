package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsBudgetTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsBudgetTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsBudgetTestNeoForge {

    private TeamClaimsBudgetTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamFullDoesNotBlockPrivateClaims(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.teamFullDoesNotBlockPrivateClaims(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamLimitFollowsMemberCount(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.teamLimitFollowsMemberCount(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void reclaimIsCheckedAgainstDestinationBudget(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.reclaimIsCheckedAgainstDestinationBudget(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void forceloadBudgetsAreIndependent(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.forceloadBudgetsAreIndependent(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamForceloadsDoNotUseUpPrivateTickets(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.teamForceloadsDoNotUseUpPrivateTickets(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void claimLimitsPacketFollowsUsedSubConfig(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.claimLimitsPacketFollowsUsedSubConfig(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void areaClaimsUseTheBudgetOfTheirSubConfig(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.areaClaimsUseTheBudgetOfTheirSubConfig(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamClaimStaysOpenWhenOwnerIsOverPrivateLimit(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.teamClaimStaysOpenWhenOwnerIsOverPrivateLimit(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void partyRemovalKeepsWithinPrivateRoom(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.partyRemovalKeepsWithinPrivateRoom(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void overLimitDeadlineLifecycle(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.overLimitDeadlineLifecycle(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void overLimitMessagesReachOnlineMembers(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.overLimitMessagesReachOnlineMembers(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void forceloadOverLimitTurnsNewestOff(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.forceloadOverLimitTurnsNewestOff(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void graceZeroRemovesAtNextCheck(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.graceZeroRemovesAtNextCheck(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void oldFormatSavedDataLoads(GameTestHelper helper) {
        TeamClaimsBudgetTestCases.oldFormatSavedDataLoads(helper);
    }
}
