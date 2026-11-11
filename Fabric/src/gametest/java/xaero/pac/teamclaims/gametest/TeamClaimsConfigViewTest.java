package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsConfigViewTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsConfigViewTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void groupIsTheFirstSegmentAndProtectionIsSplitByCategory(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.groupIsTheFirstSegmentAndProtectionIsSplitByCategory(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyRealOptionHasAGroupWithATitleInBothLanguages(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.everyRealOptionHasAGroupWithATitleInBothLanguages(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void longListsOpenOnlyTheFirstGroupAndShortListsOpenAll(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.longListsOpenOnlyTheFirstGroupAndShortListsOpenAll(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void collapseStateIsKeptPerConfigType(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.collapseStateIsKeptPerConfigType(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void searchFiltersByNameIdAndTooltipAndOpensMatchingGroups(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.searchFiltersByNameIdAndTooltipAndOpensMatchingGroups(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aSingleGroupHasNoHeader(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.aSingleGroupHasNoHeader(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anOpenHeaderIsNeverTheLastRowOfAColumn(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.anOpenHeaderIsNeverTheLastRowOfAColumn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void resetTargetAndVisibility(GameTestHelper helper) {
        TeamClaimsConfigViewTestCases.resetTargetAndVisibility(helper);
    }

}
