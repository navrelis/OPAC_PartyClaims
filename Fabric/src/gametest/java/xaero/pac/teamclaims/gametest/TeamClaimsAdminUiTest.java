package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class TeamClaimsAdminUiTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void valueRulesCheckTypeAndRange(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.valueRulesCheckTypeAndRange(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void saveIsOnlyEnabledForAValidChangedValue(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.saveIsOnlyEnabledForAValidChangedValue(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void toggleAndCycleSendTheNextValue(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.toggleAndCycleSendTheNextValue(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void configRowsGroupAndFilter(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.configRowsGroupAndFilter(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void displayTextsFollowTheEntry(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.displayTextsFollowTheEntry(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void playerTotalsAndBonusBoxesFollowTheServerRules(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.playerTotalsAndBonusBoxesFollowTheServerRules(helper);
    }
}
