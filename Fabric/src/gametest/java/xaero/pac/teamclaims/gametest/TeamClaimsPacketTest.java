package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases.RecorderFactory;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsPacketTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}. Fabric needs nothing
 * loader-specific to watch what is sent to a mock player: the plain recording connection is passed in.
 */
public class TeamClaimsPacketTest implements FabricGameTest {

    private static final RecorderFactory RECORDERS = TeamClaimsPacketTestCases.PacketRecorder::new;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void packetsAreRegisteredWithIds50To53(GameTestHelper helper) {
        TeamClaimsPacketTestCases.packetsAreRegisteredWithIds50To53(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void requestPacketCodec(GameTestHelper helper) {
        TeamClaimsPacketTestCases.requestPacketCodec(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void invitesPacketCodec(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesPacketCodec(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void declinePacketCodec(GameTestHelper helper) {
        TeamClaimsPacketTestCases.declinePacketCodec(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void budgetPacketCodec(GameTestHelper helper) {
        TeamClaimsPacketTestCases.budgetPacketCodec(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void invitesRequestAnswersWithReceivedInvites(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesRequestAnswersWithReceivedInvites(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void invitesRequestWithoutInvitesGetsEmptyList(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesRequestWithoutInvitesGetsEmptyList(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void invitesRequestOfPartyMember(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesRequestOfPartyMember(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void invitesRequestIsRateLimited(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesRequestIsRateLimited(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsPacketTestCases.RATE_LIMIT_TIMEOUT_TICKS)
    public void invitesRequestRateLimitOverRealTicks(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesRequestRateLimitOverRealTicks(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void declineRemovesOnlyThatInvite(GameTestHelper helper) {
        TeamClaimsPacketTestCases.declineRemovesOnlyThatInvite(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void declineOfUnknownOrUninvitedPartyDoesNothing(GameTestHelper helper) {
        TeamClaimsPacketTestCases.declineOfUnknownOrUninvitedPartyDoesNothing(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void handlersIgnoreDisabledPartiesAndNullPackets(GameTestHelper helper) {
        TeamClaimsPacketTestCases.handlersIgnoreDisabledPartiesAndNullPackets(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void budgetPacketOfPlayerWithoutTeam(GameTestHelper helper) {
        TeamClaimsPacketTestCases.budgetPacketOfPlayerWithoutTeam(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void budgetPacketOfTeamMember(GameTestHelper helper) {
        TeamClaimsPacketTestCases.budgetPacketOfTeamMember(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void budgetPacketOfTeamBelowMinimum(GameTestHelper helper) {
        TeamClaimsPacketTestCases.budgetPacketOfTeamBelowMinimum(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void budgetPacketShowsTimeLeftOfOverLimitDeadline(GameTestHelper helper) {
        TeamClaimsPacketTestCases.budgetPacketShowsTimeLeftOfOverLimitDeadline(helper, RECORDERS);
    }
}
