package xaero.pac.teamclaims.gametest.neoforge.ftb;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsFtbSyncTestCases;
import xaero.pac.teamclaims.gametest.neoforge.TeamClaimsGameTestMod;

/**
 * NeoForge wrappers for the FTB Teams sync tests, delegating to the loader-neutral {@link TeamClaimsFtbSyncTestCases}
 * (shared dir Common/src/gametest), where each test is documented.
 * <p>
 * Part of the "gametestFtb" source set (NeoForge/src/gametest/ftb), i.e. of the test mod
 * {@code opac_teamclaims_gametest_ftb}, which only ":NeoForge:runTeamClaimsGameTestFtb" loads (together with FTB
 * Teams, FTB Library and Architectury). NeoForge finds {@code @GameTestHolder} classes by scanning the loaded mods,
 * so the default ":NeoForge:runTeamClaimsGameTest" neither registers these tests nor loads any class that refers
 * to FTB Teams. The tests use the namespace and the empty structure template of the other test mod.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsFtbSyncTestNeoForge {

    private TeamClaimsFtbSyncTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void opacCreateMakesFtbParty(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacCreateMakesFtbParty(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void ftbCreateMakesOpacPartyWithTeam(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbCreateMakesOpacPartyWithTeam(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void opacDisbandDeletesFtbParty(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacDisbandDeletesFtbParty(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void ftbForceDisbandRemovesParty(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbForceDisbandRemovesParty(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void lastFtbMemberLeavingRemovesParty(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.lastFtbMemberLeavingRemovesParty(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void opacMembershipReachesFtb(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacMembershipReachesFtb(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void ftbMembershipReachesOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbMembershipReachesOpac(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void opacOwnerTransferReachesFtb(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacOwnerTransferReachesFtb(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void ftbOwnerTransferReachesOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbOwnerTransferReachesOpac(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void opacRenameReachesFtb(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacRenameReachesFtb(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void ftbRenameReachesOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbRenameReachesOpac(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void opacRanksReachFtbAndStayStable(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacRanksReachFtbAndStayStable(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void ftbRanksReachOpacAndStayStable(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbRanksReachOpacAndStayStable(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void opacInviteCanBeAcceptedInFtb(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacInviteCanBeAcceptedInFtb(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void ftbInviteCanBeAcceptedInOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbInviteCanBeAcceptedInOpac(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void withdrawnAndDeclinedInvitesDisappearInBoth(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.withdrawnAndDeclinedInvitesDisappearInBoth(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS, batch = TeamClaimsFtbSyncTestCases.IDLE_BATCH)
    public static void settledPairsAreNotTouchedAgain(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.settledPairsAreNotTouchedAgain(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void firstSyncCreatesTheMissingSide(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.firstSyncCreatesTheMissingSide(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void firstSyncConflictsAreDecidedForOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.firstSyncConflictsAreDecidedForOpac(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void vanishedSideIsCreatedAgain(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.vanishedSideIsCreatedAgain(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void playersUnknownToFtbArePendingUntilTheirFirstLogin(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.playersUnknownToFtbArePendingUntilTheirFirstLogin(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void ftbPartySizeLimitLeavesTheJoinPending(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbPartySizeLimitLeavesTheJoinPending(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void opacMemberLimitUndoesTheFtbJoin(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacMemberLimitUndoesTheFtbJoin(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void ftbKickHandsTeamClaimsToTheOwner(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbKickHandsTeamClaimsToTheOwner(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void pairsSurviveSaveAndLoad(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.pairsSurviveSaveAndLoad(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void statusAndResyncCommands(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.statusAndResyncCommands(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void ftbMoveBetweenPartiesMovesInOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbMoveBetweenPartiesMovesInOpac(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void seenRemovalsAreRememberedWithThePair(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.seenRemovalsAreRememberedWithThePair(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void ftbOwnerInAnotherPartyHandsItOverAndMoves(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbOwnerInAnotherPartyHandsItOverAndMoves(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void opacInviteLimitWithdrawsTheFtbInvite(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacInviteLimitWithdrawsTheFtbInvite(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void mirroredInvitesDoNotPromptThePlayerAgain(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.mirroredInvitesDoNotPromptThePlayerAgain(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void pendingWorkIsToldOnce(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.pendingWorkIsToldOnce(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public static void failingPairDoesNotStopTheOthers(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.failingPairDoesNotStopTheOthers(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS, batch = TeamClaimsFtbSyncTestCases.KEPT_BATCH)
    public static void ftbDeletionKeepsPartyWithMembersNotInFtb(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbDeletionKeepsPartyWithMembersNotInFtb(helper);
    }
}
