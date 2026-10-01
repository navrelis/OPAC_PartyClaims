package xaero.pac.teamclaims.gametest.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import xaero.pac.teamclaims.gametest.TeamClaimsSmokeTestCases;

/**
 * NeoForge wrapper for the ":NeoForge:runTeamClaimsGameTest" headless gametest server, delegating
 * to the loader-neutral {@link TeamClaimsSmokeTestCases} (shared dir Common/src/gametest).
 * <p>
 * {@code @GameTestHolder} puts the tests in the {@code opac_teamclaims_gametest} namespace (the
 * one enabled via {@code neoforge.enabledGameTestNamespaces}) and makes NeoForge pick the class
 * up automatically; {@code @PrefixGameTestTemplate(false)} keeps the template name at
 * {@code opac_teamclaims_gametest:empty}. Static methods are fine: NeoForge's patched
 * {@code GameTestRegistry} invokes them without an instance.
 */
@GameTestHolder(TeamClaimsGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamClaimsSmokeTestNeoForge {

    private TeamClaimsSmokeTestNeoForge() {}

    @GameTest(template = TeamClaimsGameTestMod.EMPTY_STRUCTURE)
    public static void teamClaimsBoots(GameTestHelper helper) {
        TeamClaimsSmokeTestCases.teamClaimsBoots(helper);
    }
}
