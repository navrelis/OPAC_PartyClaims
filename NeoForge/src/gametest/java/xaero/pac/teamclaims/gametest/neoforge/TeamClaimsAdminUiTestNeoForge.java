package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsAdminUiTestCases;

@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsAdminUiTestNeoForge {

    private TeamClaimsAdminUiTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void valueRulesCheckTypeAndRange(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.valueRulesCheckTypeAndRange(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void saveIsOnlyEnabledForAValidChangedValue(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.saveIsOnlyEnabledForAValidChangedValue(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void toggleAndCycleSendTheNextValue(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.toggleAndCycleSendTheNextValue(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void configRowsGroupAndFilter(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.configRowsGroupAndFilter(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void displayTextsFollowTheEntry(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.displayTextsFollowTheEntry(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void playerTotalsAndBonusBoxesFollowTheServerRules(GameTestHelper helper) {
        TeamClaimsAdminUiTestCases.playerTotalsAndBonusBoxesFollowTheServerRules(helper);
    }
}
