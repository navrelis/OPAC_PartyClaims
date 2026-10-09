package xaero.pac.teamclaims.gametest.neoforge.ftb;

import net.neoforged.fml.common.Mod;

/**
 * Dev/test-only NeoForge mod "opac_teamclaims_gametest_ftb": the entry point of the "gametestFtb" source set
 * (NeoForge/src/gametest/ftb), which carries the FTB Teams sync gametests ({@link TeamClaimsFtbSyncTestNeoForge}).
 * It depends on {@code ftbteams} and is only loaded by ":NeoForge:runTeamClaimsGameTestFtb"; it needs no code.
 */
@Mod(TeamClaimsFtbGameTestMod.MOD_ID)
public class TeamClaimsFtbGameTestMod {

    public static final String MOD_ID = "opac_teamclaims_gametest_ftb";
}
