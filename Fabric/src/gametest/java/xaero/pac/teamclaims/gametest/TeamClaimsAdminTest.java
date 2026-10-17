package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases.RecorderFactory;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the loader-neutral
 * {@link TeamClaimsAdminTestCases} (shared dir Common/src/gametest), where each test is documented. Same setup as
 * {@link TeamClaimsPacketTest}: the plain recording connection is passed in.
 */
public class TeamClaimsAdminTest implements FabricGameTest {

    private static final RecorderFactory RECORDERS = TeamClaimsPacketTestCases.PacketRecorder::new;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void adminPacketsAreRegistered(GameTestHelper helper) {
        TeamClaimsAdminTestCases.adminPacketsAreRegistered(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void adminPacketCodecs(GameTestHelper helper) {
        TeamClaimsAdminTestCases.adminPacketCodecs(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void isAdminFollowsOpLevel(GameTestHelper helper) {
        TeamClaimsAdminTestCases.isAdminFollowsOpLevel(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void configSnapshotHasBothSpecs(GameTestHelper helper) {
        TeamClaimsAdminTestCases.configSnapshotHasBothSpecs(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void liveTeamClaimsOptionAppliesAndIsSaved(GameTestHelper helper) {
        TeamClaimsAdminTestCases.liveTeamClaimsOptionAppliesAndIsSaved(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsAdminTestCases.WATCHER_TIMEOUT_TICKS)
    public void restartOnlyOpacOptionGoesToFileOnly(GameTestHelper helper) {
        TeamClaimsAdminTestCases.restartOnlyOpacOptionGoesToFileOnly(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void invalidAndUnauthorizedRequestsChangeNothing(GameTestHelper helper) {
        TeamClaimsAdminTestCases.invalidAndUnauthorizedRequestsChangeNothing(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamBonusRaisesLimitsBelowMinMembers(GameTestHelper helper) {
        TeamClaimsAdminTestCases.teamBonusRaisesLimitsBelowMinMembers(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamBonusIsSavedAndOldJsonLoads(GameTestHelper helper) {
        TeamClaimsAdminTestCases.teamBonusIsSavedAndOldJsonLoads(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void loweringTeamBonusStartsOverLimit(GameTestHelper helper) {
        TeamClaimsAdminTestCases.loweringTeamBonusStartsOverLimit(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bonusCommandNeedsAdmin(GameTestHelper helper) {
        TeamClaimsAdminTestCases.bonusCommandNeedsAdmin(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void playerBonusChangesPlayerLimit(GameTestHelper helper) {
        TeamClaimsAdminTestCases.playerBonusChangesPlayerLimit(helper, RECORDERS);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamInfoForOwnerAndMember(GameTestHelper helper) {
        TeamClaimsAdminTestCases.teamInfoForOwnerAndMember(helper, RECORDERS);
    }
}
