package xaero.pac.teamclaims.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.parties.command.PartyCommandRegister;
import xaero.pac.teamclaims.TeamClaimsCommon;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev/test-only, loader-neutral smoke test body for the headless gametest server (vanilla
 * {@link GameTestHelper} + OPAC + Team Claims API only).
 * <p>
 * Lives in the non-shipped shared dir Common/src/gametest, which is NOT a source set of Common
 * itself and is never part of any production jar: each loader adds it as an extra source dir of
 * its own test-only gametest source set and registers a thin {@code @GameTest} wrapper (on
 * Fabric: {@code TeamClaimsSmokeTest}, wired in through the gametest mod's fabric.mod.json and
 * only ever loaded by the {@code runBootTest} run).
 * <p>
 * This does not test individual behaviors in depth -- it just asserts that Team Claims actually
 * finished wiring itself into a running dedicated server: the OPAC addon-register bridge, the
 * managers, the /teamclaims and /oparties commands, and the on-disk team config directory.
 */
public final class TeamClaimsSmokeTestCases {

    private TeamClaimsSmokeTestCases() {}

    public static void teamClaimsBoots(GameTestHelper helper) {
        // (a) TeamClaimsCommon.onAddonRegister() ran on the OPAC addon-register event and set
        // the bridge handler that the Common module calls through.
        if (!TeamClaimsIntegration.isActive()) {
            helper.fail("TeamClaimsIntegration.isActive() is false: the Team Claims bridge "
                    + "handler was never registered (expected from "
                    + "TeamClaimsCommon.onAddonRegister, fired on the OPAC addon-register event).");
            return;
        }

        // (b) The managers created in that same onAddonRegister() are all present.
        if (TeamClaimsCommon.getClaimManager() == null) {
            helper.fail("TeamClaimsCommon.getClaimManager() is null after Team Claims init.");
            return;
        }
        if (TeamClaimsCommon.getTeamConfigManager() == null) {
            helper.fail("TeamClaimsCommon.getTeamConfigManager() is null after Team Claims init.");
            return;
        }
        if (TeamClaimsCommon.getForceLoadHandler() == null) {
            helper.fail("TeamClaimsCommon.getForceLoadHandler() is null after Team Claims init.");
            return;
        }

        MinecraftServer server = helper.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

        // (c1) TeamClaimsCommon.registerCommands() registered "/teamclaims create".
        CommandNode<CommandSourceStack> teamClaimsRoot = dispatcher.getRoot().getChild("teamclaims");
        if (teamClaimsRoot == null) {
            helper.fail("Command dispatcher has no root literal 'teamclaims' (expected from "
                    + "TeamClaimsCommon.registerCommands).");
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

        // (d) TeamConfigManager.loadAll() runs on TeamClaimsCommon.onServerStarted and
        // creates its config directory if missing. This relies on the loader's "server started"
        // event firing before the tick loop that runs any game test. Verified on Fabric
        // (fabric-gametest-api-v1 2.0.5+6fc22b9919, fabric_version=0.116.17+1.21.1): the world
        // loads and SERVER_STARTED fires during MinecraftServer.runServer(), so this directory is
        // already there by the time this method executes.
        Path teamsDir = server.getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("opacteamclaims").resolve("teams");
        if (!Files.isDirectory(teamsDir)) {
            helper.fail("Team config directory was not created by TeamConfigManager.loadAll() "
                    + "on server start: " + teamsDir);
            return;
        }

        helper.succeed();
    }
}
