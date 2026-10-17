/*
 * Open Parties and Claims - adds chunk claims and player parties to Minecraft
 * Copyright (C) 2022-2026, Xaero <xaero1996@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of version 3 of the GNU Lesser General Public License
 * (LGPL-3.0-only) as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received copies of the GNU Lesser General Public License
 * and the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package xaero.pac.teamclaims.twoplayertest.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.twoplayertest.TwoPlayerTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * [Team Claims] The server side of the two-player test: the {@code /opactest} command that writes snapshots of the
 * server state for the client drivers ({@link ServerSnapshot}) and adds an offline party member, a calm world (peaceful,
 * no day cycle, creative mode for the two players), and the stop request of the script ({@code control/stop-server}).
 * Does nothing unless {@link TwoPlayerTest#isActive()}.
 */
public class TwoPlayerTestServer implements DedicatedServerModInitializer {

	public static final Logger LOGGER = LogUtils.getLogger();
	public static final String COMMAND = "opactest";
	private static final int STOP_CHECK_INTERVAL_TICKS = 20;

	private static Path out;

	@Override
	public void onInitializeServer() {
		if(!TwoPlayerTest.isActive())
			return;
		out = TwoPlayerTest.outDir();
		String role = TwoPlayerTest.role();
		boolean captured = LogCapture.install();
		LOGGER.info("[TwoPlayerTest] server side active (role {}, output {}, log capture {})", role, out, captured ? "on" : "UNAVAILABLE");
		TwoPlayerTest.writeStatus(out, role + ".init", "logCapture=" + captured);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(TwoPlayerTestServer::onServerStarted);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(TwoPlayerTestServer::onTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TwoPlayerTest.writeStatus(out, role + ".stopped", null));
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(COMMAND)
				.then(Commands.literal("snapshot")
						.then(Commands.argument("label", StringArgumentType.word())
								.executes(TwoPlayerTestServer::snapshot)))
				.then(Commands.literal("fakemember")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("name", StringArgumentType.word())
								.executes(TwoPlayerTestServer::fakeMember))));
	}

	private static void onServerStarted(MinecraftServer server) {
		if(!TwoPlayerTest.ROLE_SERVER.equals(TwoPlayerTest.role()))
			return;
		server.setDifficulty(Difficulty.PEACEFUL, true);
		server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
		server.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		TwoPlayerTest.writeStatus(out, "server.ready", "world=" + server.getWorldData().getLevelName() + "\nteamClaimsActive=" + TeamClaimsCommon.isActive());
		LOGGER.info("[TwoPlayerTest] server ready (world {})", server.getWorldData().getLevelName());
	}

	private static void onJoin(ServerPlayer player) {
		if(!TwoPlayerTest.ROLE_SERVER.equals(TwoPlayerTest.role()))
			return;
		// creative: nothing can hurt the players while the drivers sit in screens
		player.setGameMode(GameType.CREATIVE);
	}

	private static void onTick(MinecraftServer server) {
		if(server.getTickCount() % STOP_CHECK_INTERVAL_TICKS != 0)
			return;
		if(Files.isRegularFile(TwoPlayerTest.stopServerFile(out))) {
			LOGGER.info("[TwoPlayerTest] the script asked the server to stop");
			try {
				Files.delete(TwoPlayerTest.stopServerFile(out));
			} catch(IOException e) {
				LOGGER.warn("[TwoPlayerTest] could not delete the stop request: {}", e.toString());
			}
			server.halt(false);
		}
	}

	/**
	 * Writes a snapshot to the file the label names and answers the path; the label is checked to be a plain file name.
	 */
	public static Path writeSnapshot(MinecraftServer server, String label, String requestedBy) throws IOException {
		if(!TwoPlayerTest.isSafeLabel(label))
			throw new IOException("unsafe snapshot label " + label);
		Path file = TwoPlayerTest.snapshotFile(out, label);
		TwoPlayerTest.writeAtomically(file, ServerSnapshot.toJson(ServerSnapshot.build(server, label, requestedBy)));
		return file;
	}

	private static int snapshot(CommandContext<CommandSourceStack> context) {
		String label = StringArgumentType.getString(context, "label");
		CommandSourceStack source = context.getSource();
		try {
			Path file = writeSnapshot(source.getServer(), label, source.getTextName());
			source.sendSuccess(() -> Component.literal("[TwoPlayerTest] snapshot " + label + " written"), false);
			LOGGER.info("[TwoPlayerTest] snapshot {} for {} written to {}", label, source.getTextName(), file);
			return 1;
		} catch(IOException | RuntimeException e) {
			LOGGER.error("[TwoPlayerTest] snapshot {} failed", label, e);
			source.sendFailure(Component.literal("[TwoPlayerTest] snapshot " + label + " failed: " + e));
			return 0;
		}
	}

	/**
	 * Adds an offline member (offline-mode UUID of the name) to the party that the player owns, as the gametests do it.
	 */
	private static int fakeMember(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "name");
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayer();
		if(player == null) {
			source.sendFailure(Component.literal("[TwoPlayerTest] only a player can add a fake member to their party"));
			return 0;
		}
		IServerPartyAPI party = OpenPACServerAPI.get(source.getServer()).getPartyManager().getPartyByOwner(player.getUUID());
		if(party == null) {
			source.sendFailure(Component.literal("[TwoPlayerTest] " + player.getGameProfile().getName() + " owns no party"));
			return 0;
		}
		UUID id = UUIDUtil.createOfflinePlayerUUID(name);
		IPartyMemberAPI added = party.addMember(id, PartyMemberRank.MEMBER, name);
		if(added == null) {
			source.sendFailure(Component.literal("[TwoPlayerTest] could not add " + name + " to the party"));
			return 0;
		}
		TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
		if(claimManager != null)
			claimManager.ensureAllMembersHaveSubConfig(party);
		LOGGER.info("[TwoPlayerTest] added the offline member {} ({}) to the party {}", name, id, party.getId());
		source.sendSuccess(() -> Component.literal("[TwoPlayerTest] added " + name), false);
		return 1;
	}

	public static Path getOut() {
		return out;
	}

}
