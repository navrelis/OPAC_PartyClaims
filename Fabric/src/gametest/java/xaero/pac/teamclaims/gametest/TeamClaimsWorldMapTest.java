package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsWorldMapTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsWorldMapTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void entriesAreUsableOnlyAsAPlayerAndTheTeamOneWithATeamSub(GameTestHelper helper) {
        TeamClaimsWorldMapTestCases.entriesAreUsableOnlyAsAPlayerAndTheTeamOneWithATeamSub(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void oneShotRestoresOnlyWhenItSwitched(GameTestHelper helper) {
        TeamClaimsWorldMapTestCases.oneShotRestoresOnlyWhenItSwitched(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void labelsShowTheModeAndTheCounts(GameTestHelper helper) {
        TeamClaimsWorldMapTestCases.labelsShowTheModeAndTheCounts(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void buttonPositionStaysOnTheScreenOnTheNamedSide(GameTestHelper helper) {
        TeamClaimsWorldMapTestCases.buttonPositionStaysOnTheScreenOnTheNamedSide(helper);
    }
}
