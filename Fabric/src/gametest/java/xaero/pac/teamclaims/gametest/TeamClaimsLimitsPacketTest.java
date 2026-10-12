package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases.RecorderFactory;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsLimitsPacketTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}. Fabric needs nothing
 * loader-specific to watch what is sent to a mock player: the plain recording connection is passed in.
 */
public class TeamClaimsLimitsPacketTest implements FabricGameTest {

    private static final RecorderFactory RECORDERS = TeamClaimsPacketTestCases.PacketRecorder::new;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void claimLimitsPacketCarriesPrivateNumbersInPrivateMode(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketCarriesPrivateNumbersInPrivateMode(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void claimLimitsPacketCarriesTeamNumbersInTeamMode(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketCarriesTeamNumbersInTeamMode(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void claimLimitsPacketCarriesTheAnchorFields(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketCarriesTheAnchorFields(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void claimLimitsPacketFollowsClaimsAndBudgetChanges(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketFollowsClaimsAndBudgetChanges(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void claimLimitsPacketOfThePartyModeLeavesOutTeamClaims(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketOfThePartyModeLeavesOutTeamClaims(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void claimLimitsPacketOfAPlayerWithoutATeam(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.claimLimitsPacketOfAPlayerWithoutATeam(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void isOverEitherClaimingLimitForPrivateTeamAndNeither(GameTestHelper helper) {
        TeamClaimsLimitsPacketTestCases.isOverEitherClaimingLimitForPrivateTeamAndNeither(helper);
    }
}
