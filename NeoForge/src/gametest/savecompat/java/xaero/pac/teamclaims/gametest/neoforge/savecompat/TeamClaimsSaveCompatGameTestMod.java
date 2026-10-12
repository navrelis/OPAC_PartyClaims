package xaero.pac.teamclaims.gametest.neoforge.savecompat;

import net.neoforged.fml.common.Mod;

/**
 * Dev/test-only NeoForge mod "opac_teamclaims_gametest_savecompat": the entry point of the "gametestSaveCompat" source
 * set (NeoForge/src/gametest/savecompat), which carries the save compatibility gametests
 * ({@link TeamClaimsSaveCompatTestNeoForge}). It is only loaded by ":NeoForge:runSaveCompatGameTest"; it needs no code.
 * Its tests have a namespace and an empty structure template of their own, which is what keeps them out of the default
 * run and the tests of the other test mod out of this one.
 */
@Mod(TeamClaimsSaveCompatGameTestMod.MOD_ID)
public class TeamClaimsSaveCompatGameTestMod {

    public static final String MOD_ID = "opac_teamclaims_gametest_savecompat";

    /** Template of the tests: data/opac_teamclaims_gametest_savecompat/structure/empty.nbt, an empty 8x8x8 structure. */
    public static final String EMPTY_STRUCTURE = "empty";
}
