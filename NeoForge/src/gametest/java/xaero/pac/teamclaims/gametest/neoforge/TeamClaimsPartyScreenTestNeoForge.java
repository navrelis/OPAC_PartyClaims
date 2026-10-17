package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsPartyScreenTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsPartyScreenTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsPartyScreenTestNeoForge {

    private TeamClaimsPartyScreenTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void memberKeepsItsRowWhenOnlyTheRankChanges(GameTestHelper helper) {
        TeamClaimsPartyScreenTestCases.memberKeepsItsRowWhenOnlyTheRankChanges(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void memberOrderIgnoresRankAndBreaksTiesById(GameTestHelper helper) {
        TeamClaimsPartyScreenTestCases.memberOrderIgnoresRankAndBreaksTiesById(helper);
    }
}
