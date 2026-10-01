package xaero.pac.teamclaims.gametest.neoforge;

import net.neoforged.fml.common.Mod;

/**
 * Dev/test-only NeoForge mod "opac_teamclaims_gametest" that carries the Team Claims gametests.
 * <p>
 * Lives entirely in the NeoForge "gametest" source set (NeoForge/src/gametest, plus the shared
 * loader-neutral Common/src/gametest/java as an extra source dir). That source set is never read by
 * the production {@code jar}/{@code sourcesJar} tasks; it is only loaded as a second mod by the
 * {@code :NeoForge:runTeamClaimsGameTest} run (see NeoForge/build.gradle).
 * <p>
 * Tests are discovered by NeoForge itself: every class annotated with
 * {@link net.neoforged.neoforge.gametest.GameTestHolder} in a loaded mod is scanned by
 * {@code GameTestHooks.registerGametests()}, so this class needs no code. Its own
 * META-INF/neoforge.mods.toml also applies the shared
 * {@code xaero.pac.teamclaims.gametest.mixin.MixinGameTestProfileCache}.
 */
@Mod(TeamClaimsGameTestMod.MOD_ID)
public class TeamClaimsGameTestMod {

    public static final String MOD_ID = "opac_teamclaims_gametest";

    /**
     * Template of every Team Claims gametest: an empty 8x8x8 structure (all air, like Fabric's
     * {@code fabric-gametest-api-v1:empty}), shipped in this source set's resources as
     * data/opac_teamclaims_gametest/structure/empty.nbt. Vanilla 1.21.1 ships no empty template.
     * Resolved as {@code opac_teamclaims_gametest:empty} because the test classes use
     * {@code @GameTestHolder(MOD_ID)} and {@code @PrefixGameTestTemplate(false)}.
     */
    public static final String EMPTY_STRUCTURE = "empty";
}
