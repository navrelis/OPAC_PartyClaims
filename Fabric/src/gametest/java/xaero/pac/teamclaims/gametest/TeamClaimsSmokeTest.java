package xaero.pac.teamclaims.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrapper for the ":Fabric:runBootTest" headless gametest server, delegating to the
 * loader-neutral {@link TeamClaimsSmokeTestCases} (shared dir Common/src/gametest).
 * <p>
 * Lives entirely in the "gametest" source set (Fabric/src/gametest), which is NOT part of the
 * production jar: its own fabric.mod.json (opac_teamclaims_gametest) is what wires this class in
 * as a "fabric-gametest" entrypoint, only ever loaded by the {@code runBootTest} run.
 */
public class TeamClaimsSmokeTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamClaimsBoots(GameTestHelper helper) {
        TeamClaimsSmokeTestCases.teamClaimsBoots(helper);
    }
}
