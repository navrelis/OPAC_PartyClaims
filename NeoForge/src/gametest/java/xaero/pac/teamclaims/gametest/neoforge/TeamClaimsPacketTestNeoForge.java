package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases.PacketRecorder;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases.RecorderFactory;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsPacketTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 * <p>
 * The one loader-specific part: OPAC's packet handler asks the connection of a player whether it has OPAC's channel
 * before it sends, which the connection of a mock player has no way to answer. The recording connection passed in
 * answers it for OPAC's channel.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsPacketTestNeoForge {

    private static final RecorderFactory RECORDERS = NeoForgeRecorder::new;

    private TeamClaimsPacketTestNeoForge() {}

    /** A recording connection that has OPAC's channel. */
    private static final class NeoForgeRecorder extends PacketRecorder {
        NeoForgeRecorder(MinecraftServer server, ServerPlayer player) {
            super(server, player);
        }

        @Override
        public boolean hasChannel(ResourceLocation payloadId) {
            return OpenPartiesAndClaims.MAIN_CHANNEL_LOCATION.equals(payloadId);
        }
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void packetsAreRegisteredWithIds50To53(GameTestHelper helper) {
        TeamClaimsPacketTestCases.packetsAreRegisteredWithIds50To53(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void requestPacketCodec(GameTestHelper helper) {
        TeamClaimsPacketTestCases.requestPacketCodec(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void invitesPacketCodec(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesPacketCodec(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void declinePacketCodec(GameTestHelper helper) {
        TeamClaimsPacketTestCases.declinePacketCodec(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void budgetPacketCodec(GameTestHelper helper) {
        TeamClaimsPacketTestCases.budgetPacketCodec(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void invitesRequestAnswersWithReceivedInvites(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesRequestAnswersWithReceivedInvites(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void invitesRequestWithoutInvitesGetsEmptyList(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesRequestWithoutInvitesGetsEmptyList(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void invitesRequestOfPartyMember(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesRequestOfPartyMember(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void invitesRequestIsRateLimited(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesRequestIsRateLimited(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsPacketTestCases.RATE_LIMIT_TIMEOUT_TICKS)
    public static void invitesRequestRateLimitOverRealTicks(GameTestHelper helper) {
        TeamClaimsPacketTestCases.invitesRequestRateLimitOverRealTicks(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void declineRemovesOnlyThatInvite(GameTestHelper helper) {
        TeamClaimsPacketTestCases.declineRemovesOnlyThatInvite(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void declineOfUnknownOrUninvitedPartyDoesNothing(GameTestHelper helper) {
        TeamClaimsPacketTestCases.declineOfUnknownOrUninvitedPartyDoesNothing(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void handlersIgnoreDisabledPartiesAndNullPackets(GameTestHelper helper) {
        TeamClaimsPacketTestCases.handlersIgnoreDisabledPartiesAndNullPackets(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void budgetPacketOfPlayerWithoutTeam(GameTestHelper helper) {
        TeamClaimsPacketTestCases.budgetPacketOfPlayerWithoutTeam(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void budgetPacketOfTeamMember(GameTestHelper helper) {
        TeamClaimsPacketTestCases.budgetPacketOfTeamMember(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void budgetPacketOfTeamBelowMinimum(GameTestHelper helper) {
        TeamClaimsPacketTestCases.budgetPacketOfTeamBelowMinimum(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void budgetPacketShowsTimeLeftOfOverLimitDeadline(GameTestHelper helper) {
        TeamClaimsPacketTestCases.budgetPacketShowsTimeLeftOfOverLimitDeadline(helper, RECORDERS);
    }
}
