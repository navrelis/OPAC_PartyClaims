package xaero.pac.teamclaims.gametest.neoforge.savecompat;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsSaveCompatTestCases;

/**
 * NeoForge wrappers for the save compatibility tests, delegating to the loader-neutral
 * {@link TeamClaimsSaveCompatTestCases} (shared dir Common/src/gametest), where each test is documented.
 * <p>
 * Part of the "gametestSaveCompat" source set (NeoForge/src/gametest/savecompat), i.e. of the test mod
 * {@code opac_teamclaims_gametest_savecompat}, which only ":NeoForge:runSaveCompatGameTest" loads (on a world folder
 * with the data of a 1.2.0 server). The tests have their own namespace, and that run enables only it, so the tests of
 * the other test mod, which it has to load for their shared classes, are not run there.
 */
@GameTestHolder(TeamClaimsSaveCompatGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsSaveCompatTestNeoForge {

    private TeamClaimsSaveCompatTestNeoForge() {}

    @GameTest(template = TeamClaimsSaveCompatGameTestMod.EMPTY_STRUCTURE)
    public static void oldClaimsAreAllThere(GameTestHelper helper) {
        TeamClaimsSaveCompatTestCases.oldClaimsAreAllThere(helper);
    }

    @GameTest(template = TeamClaimsSaveCompatGameTestMod.EMPTY_STRUCTURE)
    public static void teamClaimsAndBudgetsMatchTheOldWorld(GameTestHelper helper) {
        TeamClaimsSaveCompatTestCases.teamClaimsAndBudgetsMatchTheOldWorld(helper);
    }

    @GameTest(template = TeamClaimsSaveCompatGameTestMod.EMPTY_STRUCTURE)
    public static void partyTeamSettingsAndRolesAreKept(GameTestHelper helper) {
        TeamClaimsSaveCompatTestCases.partyTeamSettingsAndRolesAreKept(helper);
    }

    @GameTest(template = TeamClaimsSaveCompatGameTestMod.EMPTY_STRUCTURE)
    public static void loadedDataSurvivesSaveAndReload(GameTestHelper helper) {
        TeamClaimsSaveCompatTestCases.loadedDataSurvivesSaveAndReload(helper);
    }
}
