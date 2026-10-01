package xaero.pac.teamclaims.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.parties.command.PartyCommandRegister;
import xaero.pac.teamclaims.TeamClaimsInit;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev/test-only smoke test for the ":Fabric:runBootTest" headless gametest server.
 * <p>
 * Lives entirely in the "gametest" source set (Fabric/src/gametest), which is NOT part of the
 * production jar: its own fabric.mod.json (opac_teamclaims_gametest) is what wires this class in
 * as a "fabric-gametest" entrypoint, only ever loaded by the {@code runBootTest} run.
 * <p>
 * This does not test individual behaviors in depth -- it just asserts that Team Claims actually
 * finished wiring itself into a running dedicated server: the OPAC addon-register bridge, the
 * Fabric-side managers, the /teamclaims and /oparties commands, and the on-disk team config
 * directory.
 */
public class TeamClaimsSmokeTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void teamClaimsBoots(GameTestHelper helper) {
        // (a) TeamClaimsInit.onOPACRegister() ran during OPAC's ServerStartingCallback and set
        // the bridge handler that the Common module calls through.
        if (!TeamClaimsIntegration.isActive()) {
            helper.fail("TeamClaimsIntegration.isActive() is false: the Team Claims bridge "
                    + "handler was never registered (expected from "
                    + "TeamClaimsInit.onOPACRegister, fired on the OPAC addon-register event).");
            return;
        }

        // (b) The Fabric-side managers created in that same onOPACRegister() are all present.
        if (TeamClaimsInit.getClaimManager() == null) {
            helper.fail("TeamClaimsInit.getClaimManager() is null after Team Claims init.");
            return;
        }
        if (TeamClaimsInit.getTeamConfigManager() == null) {
            helper.fail("TeamClaimsInit.getTeamConfigManager() is null after Team Claims init.");
            return;
        }
        if (TeamClaimsInit.getForceLoadHandler() == null) {
            helper.fail("TeamClaimsInit.getForceLoadHandler() is null after Team Claims init.");
            return;
        }

        MinecraftServer server = helper.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

        // (c1) TeamClaimsCommands.onRegisterCommands() registered "/teamclaims create".
        CommandNode<CommandSourceStack> teamClaimsRoot = dispatcher.getRoot().getChild("teamclaims");
        if (teamClaimsRoot == null) {
            helper.fail("Command dispatcher has no root literal 'teamclaims' (expected from "
                    + "TeamClaimsCommands.onRegisterCommands).");
            return;
        }
        if (teamClaimsRoot.getChild("create") == null) {
            helper.fail("Root literal 'teamclaims' has no child 'create'.");
            return;
        }

        // (c2) CreatePartyCommand's Team Claims patch still exposes the "teamname" argument on
        // the OPAC parties command (real root literal from PartyCommandRegister.COMMAND_PREFIX,
        // not a hardcoded guess).
        CommandNode<CommandSourceStack> partiesRoot =
                dispatcher.getRoot().getChild(PartyCommandRegister.COMMAND_PREFIX);
        if (partiesRoot == null) {
            helper.fail("Command dispatcher has no root literal '" + PartyCommandRegister.COMMAND_PREFIX
                    + "' (PartyCommandRegister.COMMAND_PREFIX).");
            return;
        }
        CommandNode<CommandSourceStack> partiesCreate = partiesRoot.getChild("create");
        if (partiesCreate == null) {
            helper.fail("Root literal '" + PartyCommandRegister.COMMAND_PREFIX + "' has no child 'create'.");
            return;
        }
        if (partiesCreate.getChild("teamname") == null) {
            helper.fail("'" + PartyCommandRegister.COMMAND_PREFIX + " create' has no 'teamname' "
                    + "argument (expected from CreatePartyCommand's Team Claims patch).");
            return;
        }

        // (d) TeamConfigManager.loadAll() runs on ServerLifecycleEvents.SERVER_STARTED and
        // creates its config directory if missing. Verified on fabric-gametest-api-v1
        // 2.0.5+6fc22b9919 (fabric_version=0.116.17+1.21.1): the world loads and SERVER_STARTED
        // fires during MinecraftServer.runServer(), before the tick loop that runs any game
        // test, so this directory is already there by the time this method executes.
        Path teamsDir = server.getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("opacteamclaims").resolve("teams");
        if (!Files.isDirectory(teamsDir)) {
            helper.fail("Team config directory was not created by TeamConfigManager.loadAll() "
                    + "on SERVER_STARTED: " + teamsDir);
            return;
        }

        helper.succeed();
    }
}
