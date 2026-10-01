package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsTerritoryTestCases;

/**
 * NeoForge wrappers for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsTerritoryTestCases} (shared dir Common/src/gametest), where each test is
 * documented. Same registration as {@link TeamClaimsLogicTestNeoForge}.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsTerritoryTestNeoForge {

    private TeamClaimsTerritoryTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void sameTeamTerritoryDecision(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.sameTeamTerritoryDecision(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void territoryMessagesDefaultFollowsConfig(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.territoryMessagesDefaultFollowsConfig(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void territoryMessageChoicesArePersisted(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.territoryMessageChoicesArePersisted(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void welcomerShowsOneMessagePerTeamTerritory(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.welcomerShowsOneMessagePerTeamTerritory(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void territoryMessagesCommandControlsWelcomer(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.territoryMessagesCommandControlsWelcomer(helper);
    }

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void territoryMessagesCommandNeedsNoParty(GameTestHelper helper) {
        TeamClaimsTerritoryTestCases.territoryMessagesCommandNeedsNoParty(helper);
    }
}
