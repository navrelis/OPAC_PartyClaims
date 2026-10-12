package xaero.pac.teamclaims.gametest.savecompat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import xaero.pac.teamclaims.gametest.TeamClaimsSaveCompatTestCases;

/**
 * Fabric wrappers for the save compatibility tests, delegating to the loader-neutral
 * {@link TeamClaimsSaveCompatTestCases} (shared dir Common/src/gametest), where each test is documented.
 * <p>
 * Part of the "gametestSaveCompat" source set (Fabric/src/gametest/savecompat), whose fabric.mod.json (mod
 * {@code opac_teamclaims_gametest_savecompat}) registers this class as a "fabric-gametest" entrypoint. Only
 * ":Fabric:runSaveCompatTest" has that source set on its classpath (and starts the server on the world data of a 1.2.0
 * server), so the default ":Fabric:runBootTest" neither registers these tests nor needs the fixture. Not part of the
 * production jar.
 */
public class TeamClaimsSaveCompatTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void oldClaimsAreAllThere(GameTestHelper helper) {
        TeamClaimsSaveCompatTestCases.oldClaimsAreAllThere(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamClaimsAndBudgetsMatchTheOldWorld(GameTestHelper helper) {
        TeamClaimsSaveCompatTestCases.teamClaimsAndBudgetsMatchTheOldWorld(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void partyTeamSettingsAndRolesAreKept(GameTestHelper helper) {
        TeamClaimsSaveCompatTestCases.partyTeamSettingsAndRolesAreKept(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void loadedDataSurvivesSaveAndReload(GameTestHelper helper) {
        TeamClaimsSaveCompatTestCases.loadedDataSurvivesSaveAndReload(helper);
    }
}
