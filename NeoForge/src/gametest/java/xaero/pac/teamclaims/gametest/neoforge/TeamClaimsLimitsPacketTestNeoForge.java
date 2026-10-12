package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.teamclaims.gametest.TeamClaimsLimitsPacketTestCases;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases.PacketRecorder;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases.RecorderFactory;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsLimitsPacketTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}, and the same loader-specific part as
 * {@link TeamClaimsPacketTestNeoForge}: the recording connection passed in answers the channel check of OPAC's
 * packet handler.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsLimitsPacketTestNeoForge {

    private static final RecorderFactory RECORDERS = NeoForgeRecorder::new;

    private TeamClaimsLimitsPacketTestNeoForge() {}

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
    public static void claimLimitsPacketCarriesPrivateNumbersInPrivateMode(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketCarriesPrivateNumbersInPrivateMode(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void claimLimitsPacketCarriesTeamNumbersInTeamMode(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketCarriesTeamNumbersInTeamMode(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void claimLimitsPacketCarriesTheAnchorFields(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketCarriesTheAnchorFields(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void claimLimitsPacketFollowsClaimsAndBudgetChanges(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketFollowsClaimsAndBudgetChanges(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void claimLimitsPacketOfThePartyModeLeavesOutTeamClaims(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketOfThePartyModeLeavesOutTeamClaims(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void claimLimitsPacketOfAPlayerWithoutATeam(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketOfAPlayerWithoutATeam(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void isOverEitherClaimingLimitForPrivateTeamAndNeither(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.isOverEitherClaimingLimitForPrivateTeamAndNeither(helper);
    }
}
