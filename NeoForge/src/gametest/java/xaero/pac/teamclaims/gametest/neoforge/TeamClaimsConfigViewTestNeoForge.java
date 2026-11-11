package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsConfigViewTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsConfigViewTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsConfigViewTestNeoForge {

    private TeamClaimsConfigViewTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void groupIsTheFirstSegmentAndProtectionIsSplitByCategory(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.groupIsTheFirstSegmentAndProtectionIsSplitByCategory(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void everyRealOptionHasAGroupWithATitleInBothLanguages(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.everyRealOptionHasAGroupWithATitleInBothLanguages(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void longListsOpenOnlyTheFirstGroupAndShortListsOpenAll(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.longListsOpenOnlyTheFirstGroupAndShortListsOpenAll(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void collapseStateIsKeptPerConfigType(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.collapseStateIsKeptPerConfigType(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void searchFiltersByNameIdAndTooltipAndOpensMatchingGroups(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.searchFiltersByNameIdAndTooltipAndOpensMatchingGroups(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void aSingleGroupHasNoHeader(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.aSingleGroupHasNoHeader(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void anOpenHeaderIsNeverTheLastRowOfAColumn(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.anOpenHeaderIsNeverTheLastRowOfAColumn(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void resetTargetAndVisibility(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.resetTargetAndVisibility(helper);
    }

}
