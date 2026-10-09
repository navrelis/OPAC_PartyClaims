package xaero.pac.teamclaims.gametest.ftb;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import xaero.pac.teamclaims.gametest.TeamClaimsFtbSyncTestCases;

/**
 * Fabric wrappers for the FTB Teams sync tests, delegating to the loader-neutral {@link TeamClaimsFtbSyncTestCases}
 * (shared dir Common/src/gametest), where each test is documented.
 * <p>
 * Part of the "gametestFtb" source set (Fabric/src/gametest/ftb), whose fabric.mod.json (mod
 * {@code opac_teamclaims_gametest_ftb}, which depends on {@code ftbteams}) registers this class as a
 * "fabric-gametest" entrypoint. Only ":Fabric:runBootTestFtb" has that source set (and FTB Teams, FTB Library and
 * Architectury) on its classpath, so the default ":Fabric:runBootTest" neither registers these tests nor loads any
 * class that refers to FTB Teams. Not part of the production jar.
 */
public class TeamClaimsFtbSyncTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void opacCreateMakesFtbParty(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacCreateMakesFtbParty(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void ftbCreateMakesOpacPartyWithTeam(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbCreateMakesOpacPartyWithTeam(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void opacDisbandDeletesFtbParty(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacDisbandDeletesFtbParty(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void ftbForceDisbandRemovesParty(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbForceDisbandRemovesParty(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void lastFtbMemberLeavingRemovesParty(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.lastFtbMemberLeavingRemovesParty(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void opacMembershipReachesFtb(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacMembershipReachesFtb(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void ftbMembershipReachesOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbMembershipReachesOpac(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void opacOwnerTransferReachesFtb(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacOwnerTransferReachesFtb(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void ftbOwnerTransferReachesOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbOwnerTransferReachesOpac(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void opacRenameReachesFtb(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacRenameReachesFtb(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void ftbRenameReachesOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbRenameReachesOpac(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void opacRanksReachFtbAndStayStable(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacRanksReachFtbAndStayStable(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void ftbRanksReachOpacAndStayStable(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbRanksReachOpacAndStayStable(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void opacInviteCanBeAcceptedInFtb(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacInviteCanBeAcceptedInFtb(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void ftbInviteCanBeAcceptedInOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbInviteCanBeAcceptedInOpac(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void withdrawnAndDeclinedInvitesDisappearInBoth(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.withdrawnAndDeclinedInvitesDisappearInBoth(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS, batch = TeamClaimsFtbSyncTestCases.IDLE_BATCH)
    public void settledPairsAreNotTouchedAgain(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.settledPairsAreNotTouchedAgain(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void firstSyncCreatesTheMissingSide(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.firstSyncCreatesTheMissingSide(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void firstSyncConflictsAreDecidedForOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.firstSyncConflictsAreDecidedForOpac(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void vanishedSideIsCreatedAgain(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.vanishedSideIsCreatedAgain(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void playersUnknownToFtbArePendingUntilTheirFirstLogin(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.playersUnknownToFtbArePendingUntilTheirFirstLogin(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void ftbPartySizeLimitLeavesTheJoinPending(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbPartySizeLimitLeavesTheJoinPending(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void opacMemberLimitUndoesTheFtbJoin(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacMemberLimitUndoesTheFtbJoin(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void ftbKickHandsTeamClaimsToTheOwner(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbKickHandsTeamClaimsToTheOwner(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void pairsSurviveSaveAndLoad(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.pairsSurviveSaveAndLoad(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void statusAndResyncCommands(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.statusAndResyncCommands(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void ftbMoveBetweenPartiesMovesInOpac(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbMoveBetweenPartiesMovesInOpac(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void seenRemovalsAreRememberedWithThePair(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.seenRemovalsAreRememberedWithThePair(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void ftbOwnerInAnotherPartyHandsItOverAndMoves(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbOwnerInAnotherPartyHandsItOverAndMoves(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void opacInviteLimitWithdrawsTheFtbInvite(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.opacInviteLimitWithdrawsTheFtbInvite(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void mirroredInvitesDoNotPromptThePlayerAgain(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.mirroredInvitesDoNotPromptThePlayerAgain(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void pendingWorkIsToldOnce(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.pendingWorkIsToldOnce(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS)
    public void failingPairDoesNotStopTheOthers(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.failingPairDoesNotStopTheOthers(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsFtbSyncTestCases.TIMEOUT_TICKS, batch = TeamClaimsFtbSyncTestCases.KEPT_BATCH)
    public void ftbDeletionKeepsPartyWithMembersNotInFtb(GameTestHelper helper) {
        TeamClaimsFtbSyncTestCases.ftbDeletionKeepsPartyWithMembersNotInFtb(helper);
    }
}
