package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.teamclaims.gametest.TeamClaimsAdminTestCases;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases.PacketRecorder;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases.RecorderFactory;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsAdminTestCases} (shared dir Common/src/gametest), where each test is documented.
 * Same registration and recording connection as {@link TeamClaimsPacketTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsAdminTestNeoForge {

    private static final RecorderFactory RECORDERS = NeoForgeRecorder::new;

    private TeamClaimsAdminTestNeoForge() {}

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
    public static void adminPacketsAreRegistered(GameTestHelper helper) {
        TeamClaimsAdminTestCases.adminPacketsAreRegistered(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void adminPacketCodecs(GameTestHelper helper) {
        TeamClaimsAdminTestCases.adminPacketCodecs(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void isAdminFollowsOpLevel(GameTestHelper helper) {
        TeamClaimsAdminTestCases.isAdminFollowsOpLevel(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void configSnapshotHasBothSpecs(GameTestHelper helper) {
        TeamClaimsAdminTestCases.configSnapshotHasBothSpecs(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void liveTeamClaimsOptionAppliesAndIsSaved(GameTestHelper helper) {
        TeamClaimsAdminTestCases.liveTeamClaimsOptionAppliesAndIsSaved(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE, timeoutTicks = TeamClaimsAdminTestCases.WATCHER_TIMEOUT_TICKS)
    public static void restartOnlyOpacOptionGoesToFileOnly(GameTestHelper helper) {
        TeamClaimsAdminTestCases.restartOnlyOpacOptionGoesToFileOnly(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void invalidAndUnauthorizedRequestsChangeNothing(GameTestHelper helper) {
        TeamClaimsAdminTestCases.invalidAndUnauthorizedRequestsChangeNothing(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamBonusRaisesLimitsBelowMinMembers(GameTestHelper helper) {
        TeamClaimsAdminTestCases.teamBonusRaisesLimitsBelowMinMembers(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamBonusIsSavedAndOldJsonLoads(GameTestHelper helper) {
        TeamClaimsAdminTestCases.teamBonusIsSavedAndOldJsonLoads(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void loweringTeamBonusStartsOverLimit(GameTestHelper helper) {
        TeamClaimsAdminTestCases.loweringTeamBonusStartsOverLimit(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void bonusCommandNeedsAdmin(GameTestHelper helper) {
        TeamClaimsAdminTestCases.bonusCommandNeedsAdmin(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void playerBonusChangesPlayerLimit(GameTestHelper helper) {
        TeamClaimsAdminTestCases.playerBonusChangesPlayerLimit(helper, RECORDERS);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamInfoForOwnerAndMember(GameTestHelper helper) {
        TeamClaimsAdminTestCases.teamInfoForOwnerAndMember(helper, RECORDERS);
    }
}
