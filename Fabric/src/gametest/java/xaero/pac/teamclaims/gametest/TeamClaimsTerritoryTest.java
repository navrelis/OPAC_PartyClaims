package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsTerritoryTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Part of the "gametest" source set only, see {@link TeamClaimsSmokeTest}.
 */
public class TeamClaimsTerritoryTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void sameTeamTerritoryDecision(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.sameTeamTerritoryDecision(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void territoryMessagesDefaultFollowsConfig(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.territoryMessagesDefaultFollowsConfig(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void territoryMessageChoicesArePersisted(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.territoryMessageChoicesArePersisted(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void welcomerShowsOneMessagePerTeamTerritory(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.welcomerShowsOneMessagePerTeamTerritory(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void territoryMessagesCommandControlsWelcomer(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.territoryMessagesCommandControlsWelcomer(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void territoryMessagesCommandNeedsNoParty(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.territoryMessagesCommandNeedsNoParty(helper);
    }
}
