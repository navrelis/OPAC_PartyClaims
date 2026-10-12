package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsAnchorTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsAnchorTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsAnchorTestNeoForge {

    private TeamClaimsAnchorTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamClaimsObeyTheAnchorsOfTheirOwner(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.teamClaimsObeyTheAnchorsOfTheirOwner(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void leavingMemberHandsOverAnchoredTeamClaims(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.leavingMemberHandsOverAnchoredTeamClaims(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamClaimsAboveTheLimitSpareAnchors(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.teamClaimsAboveTheLimitSpareAnchors(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void removedPartyKeepsAnchors(GameTestHelper helper) {
        TeamClaimsAnchorTestCases.removedPartyKeepsAnchors(helper);
    }
}
