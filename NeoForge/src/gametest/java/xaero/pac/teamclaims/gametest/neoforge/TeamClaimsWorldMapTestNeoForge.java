package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsWorldMapTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsWorldMapTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsWorldMapTestNeoForge {

    private TeamClaimsWorldMapTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void entriesAreUsableOnlyAsAPlayerAndTheTeamOneWithATeamSub(GameTestHelper helper) {
        TeamClaimsWorldMapTestCases.entriesAreUsableOnlyAsAPlayerAndTheTeamOneWithATeamSub(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void oneShotRestoresOnlyWhenItSwitched(GameTestHelper helper) {
        TeamClaimsWorldMapTestCases.oneShotRestoresOnlyWhenItSwitched(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void labelsShowTheModeAndTheCounts(GameTestHelper helper) {
        TeamClaimsWorldMapTestCases.labelsShowTheModeAndTheCounts(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void buttonPositionStaysOnTheScreenOnTheNamedSide(GameTestHelper helper) {
        TeamClaimsWorldMapTestCases.buttonPositionStaysOnTheScreenOnTheNamedSide(helper);
    }
}
