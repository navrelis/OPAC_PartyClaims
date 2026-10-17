package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsPartyScreenTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsPartyScreenTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void memberKeepsItsRowWhenOnlyTheRankChanges(GameTestHelper helper) {
        TeamClaimsPartyScreenTestCases.memberKeepsItsRowWhenOnlyTheRankChanges(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void memberOrderIgnoresRankAndBreaksTiesById(GameTestHelper helper) {
        TeamClaimsPartyScreenTestCases.memberOrderIgnoresRankAndBreaksTiesById(helper);
    }
}
