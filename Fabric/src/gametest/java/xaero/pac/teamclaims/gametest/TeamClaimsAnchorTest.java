package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsAnchorTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsAnchorTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamClaimsObeyTheAnchorsOfTheirOwner(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.teamClaimsObeyTheAnchorsOfTheirOwner(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void leavingMemberHandsOverAnchoredTeamClaims(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.leavingMemberHandsOverAnchoredTeamClaims(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamClaimsAboveTheLimitSpareAnchors(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.teamClaimsAboveTheLimitSpareAnchors(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void removedPartyKeepsAnchors(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.removedPartyKeepsAnchors(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void ownersFullAnchorLimitKeepsTheAnchoredTeamClaimWithTheLeaver(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.ownersFullAnchorLimitKeepsTheAnchoredTeamClaimWithTheLeaver(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void ownersSmallerAnchorRangeKeepsTheAnchoredTeamClaimWithTheLeaver(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.ownersSmallerAnchorRangeKeepsTheAnchoredTeamClaimWithTheLeaver(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void ownersLargerAnchorRangeTakesTheAnchorAlong(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.ownersLargerAnchorRangeTakesTheAnchorAlong(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anchoredHandOverIsPerDimension(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.anchoredHandOverIsPerDimension(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anchorOnTheOwnTeamClaim(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.anchorOnTheOwnTeamClaim(helper);
    }
}
