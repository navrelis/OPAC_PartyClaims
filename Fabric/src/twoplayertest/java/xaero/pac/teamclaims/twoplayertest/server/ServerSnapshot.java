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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.packet.teamclaims.AdminConfigEntry;
import xaero.pac.common.packet.teamclaims.AdminPlayerBonus;
import xaero.pac.common.parties.party.api.IPartyPlayerInfoAPI;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.admin.AdminAccess;
import xaero.pac.teamclaims.admin.AdminConfigs;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * [Team Claims] What the server knows, as JSON, for the assertions of the client drivers: every party (its members with
 * their ranks, the invited players, the team with its roles, bonus and budget), the claims of the chunks around every
 * online player (owner, sub-config index and whether it is a team claim), each online and party player's budget and
 * bonus options, every scalar option of both server configs (the running value and the one saved for the next start),
 * the values in the toml files as they are on disk, and the warnings logged since the start.
 * <p>
 * Only reads (server thread). Every part catches its own exceptions and reports them in {@code errors}, so a broken
 * part shows up as such instead of as missing data.
 */
public final class ServerSnapshot {

	/** How many chunks around each online player the claims list covers. */
	public static final int CLAIM_RADIUS = 6;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

	private ServerSnapshot() {
	}

	public static JsonObject build(MinecraftServer server, String label, String requestedBy) {
		JsonObject root = new JsonObject();
		JsonArray errors = new JsonArray();
		root.addProperty("label", label);
		root.addProperty("requestedBy", requestedBy);
		root.addProperty("time", Instant.now().toString());
		root.addProperty("tick", server.getTickCount());
		root.addProperty("teamClaimsActive", TeamClaimsCommon.isActive());
		run(errors, "players", () -> root.add("players", players(server)));
		run(errors, "parties", () -> root.add("parties", parties(server)));
		run(errors, "claims", () -> root.add("claims", claims(server)));
		run(errors, "playerData", () -> root.add("playerData", playerData(server)));
		run(errors, "config", () -> root.add("config", config()));
		run(errors, "configFiles", () -> root.add("configFiles", configFiles(server)));
		JsonArray warnings = new JsonArray();
		for(String line : LogCapture.lines())
			warnings.add(line);
		root.add("warnings", warnings);
		root.addProperty("logCaptureInstalled", LogCapture.isInstalled());
		root.add("errors", errors);
		return root;
	}

	public static String toJson(JsonObject snapshot) {
		return GSON.toJson(snapshot);
	}

	private static void run(JsonArray errors, String part, Runnable body) {
		try {
			body.run();
		} catch(RuntimeException e) {
			errors.add(part + ": " + e);
		}
	}

	private static JsonArray players(MinecraftServer server) {
		JsonArray array = new JsonArray();
		for(ServerPlayer player : server.getPlayerList().getPlayers()) {
			JsonObject object = new JsonObject();
			object.addProperty("name", player.getGameProfile().getName());
			object.addProperty("uuid", player.getUUID().toString());
			object.addProperty("op", server.getPlayerList().isOp(player.getGameProfile()));
			object.addProperty("permissionLevel", server.getProfilePermissions(player.getGameProfile()));
			object.addProperty("adminMenu", AdminAccess.isAdmin(player));
			object.addProperty("gameMode", player.gameMode.getGameModeForPlayer().getName());
			object.addProperty("dimension", player.level().dimension().location().toString());
			ChunkPos chunk = player.chunkPosition();
			object.addProperty("chunkX", chunk.x);
			object.addProperty("chunkZ", chunk.z);
			array.add(object);
		}
		return array;
	}

	private static JsonObject member(IPartyMemberAPI member) {
		JsonObject object = new JsonObject();
		object.addProperty("name", member.getUsername());
		object.addProperty("uuid", member.getUUID().toString());
		object.addProperty("rank", member.getRank() == null ? null : member.getRank().name());
		object.addProperty("owner", member.isOwner());
		return object;
	}

	private static JsonArray parties(MinecraftServer server) {
		OpenPACServerAPI api = OpenPACServerAPI.get(server);
		IPlayerConfigManagerAPI configs = api.getPlayerConfigManager();
		TeamConfigManager teamConfigs = TeamClaimsCommon.getTeamConfigManager();
		TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
		JsonArray array = new JsonArray();
		List<IServerPartyAPI> parties = new ArrayList<>(api.getPartyManager().getAllStream().toList());
		parties.sort(Comparator.comparing(party -> party.getId().toString()));
		for(IServerPartyAPI party : parties) {
			JsonObject object = new JsonObject();
			object.addProperty("id", party.getId().toString());
			object.addProperty("defaultName", party.getDefaultName());
			IPartyMemberAPI owner = party.getOwner();
			object.add("owner", member(owner));
			IPlayerConfigAPI ownerConfig = configs.getLoadedConfig(owner.getUUID());
			object.addProperty("name", ownerConfig == null ? null : ownerConfig.getEffective(PlayerConfigOptions.PARTY_NAME));
			JsonArray members = new JsonArray();
			// by name, so that the order says nothing about how the server keeps them
			for(IPartyMemberAPI member : party.getMemberInfoStream().sorted(Comparator.comparing(m -> m.getUsername().toLowerCase(Locale.ROOT))).toList())
				members.add(member(member));
			object.add("members", members);
			JsonArray invited = new JsonArray();
			for(IPartyPlayerInfoAPI invite : party.getInvitedPlayersStream().toList()) {
				JsonObject inviteObject = new JsonObject();
				inviteObject.addProperty("name", invite.getUsername());
				inviteObject.addProperty("uuid", invite.getUUID().toString());
				invited.add(inviteObject);
			}
			object.add("invited", invited);
			TeamConfig teamConfig = teamConfigs == null ? null : teamConfigs.getTeamConfig(party.getId());
			JsonObject team = new JsonObject();
			team.addProperty("exists", teamConfig != null);
			if(teamConfig != null) {
				team.addProperty("teamName", teamConfig.getTeamName());
				team.addProperty("subConfigId", teamConfig.getSubConfigId());
				JsonObject roles = new JsonObject();
				for(TeamAction action : TeamAction.values())
					roles.addProperty(action.id(), teamConfig.getRequiredRole(action).name());
				team.add("roles", roles);
				if(claimManager != null) {
					TeamClaimManager.TeamBudget budget = claimManager.getTeamBudget(party.getId());
					JsonObject budgetObject = new JsonObject();
					budgetObject.addProperty("memberCount", budget.memberCount());
					budgetObject.addProperty("minMembers", budget.minMembers());
					budgetObject.addProperty("claims", budget.claims());
					budgetObject.addProperty("claimLimit", budget.claimLimit());
					budgetObject.addProperty("forceloads", budget.forceloads());
					budgetObject.addProperty("forceloadLimit", budget.forceloadLimit());
					budgetObject.addProperty("bonusClaims", budget.bonusClaims());
					budgetObject.addProperty("bonusForceloads", budget.bonusForceloads());
					team.add("budget", budgetObject);
					team.addProperty("bonusClaims", claimManager.teamBonus(party.getId(), false));
					team.addProperty("bonusForceloads", claimManager.teamBonus(party.getId(), true));
				}
			}
			object.add("team", team);
			array.add(object);
		}
		return array;
	}

	private static JsonArray claims(MinecraftServer server) {
		IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
		TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
		JsonArray array = new JsonArray();
		Set<String> seen = new LinkedHashSet<>();
		// around every online player and around the world spawn (where the players start)
		List<Map.Entry<ResourceLocation, ChunkPos>> centers = new ArrayList<>();
		for(ServerPlayer player : server.getPlayerList().getPlayers())
			centers.add(Map.entry(player.level().dimension().location(), player.chunkPosition()));
		centers.add(Map.entry(server.overworld().dimension().location(), new ChunkPos(server.overworld().getSharedSpawnPos())));
		for(Map.Entry<ResourceLocation, ChunkPos> centerEntry : centers) {
			ResourceLocation dimension = centerEntry.getKey();
			ChunkPos center = centerEntry.getValue();
			for(int x = center.x - CLAIM_RADIUS; x <= center.x + CLAIM_RADIUS; x++)
				for(int z = center.z - CLAIM_RADIUS; z <= center.z + CLAIM_RADIUS; z++) {
					String key = dimension + "|" + x + "|" + z;
					if(!seen.add(key))
						continue;
					IPlayerChunkClaimAPI claim = claims.get(dimension, x, z);
					if(claim == null)
						continue;
					JsonObject object = new JsonObject();
					object.addProperty("dimension", dimension.toString());
					object.addProperty("x", x);
					object.addProperty("z", z);
					object.addProperty("owner", claim.getPlayerId().toString());
					var ownerInfo = claims.getPlayerInfo(claim.getPlayerId());
					object.addProperty("ownerName", ownerInfo == null ? null : ownerInfo.getPlayerUsername());
					object.addProperty("subConfigIndex", claim.getSubConfigIndex());
					object.addProperty("forceloadable", claim.isForceloadable());
					object.addProperty("teamClaim", claimManager != null && claimManager.isTeamClaim(claim));
					array.add(object);
				}
		}
		return array;
	}

	/**
	 * The online players and every member of a party: budget (private and team) and bonus options.
	 */
	private static JsonObject playerData(MinecraftServer server) {
		OpenPACServerAPI api = OpenPACServerAPI.get(server);
		IPlayerConfigManagerAPI configs = api.getPlayerConfigManager();
		TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
		Set<UUID> ids = new LinkedHashSet<>();
		java.util.Map<UUID, String> names = new java.util.HashMap<>();
		for(ServerPlayer player : server.getPlayerList().getPlayers()) {
			ids.add(player.getUUID());
			names.put(player.getUUID(), player.getGameProfile().getName());
		}
		api.getPartyManager().getAllStream().forEach(party -> party.getMemberInfoStream().forEach(member -> {
			ids.add(member.getUUID());
			names.put(member.getUUID(), member.getUsername());
		}));
		JsonObject object = new JsonObject();
		for(UUID id : ids) {
			JsonObject player = new JsonObject();
			player.addProperty("uuid", id.toString());
			IPlayerConfigAPI config = configs.getLoadedConfig(id);
			JsonObject bonus = new JsonObject();
			for(AdminPlayerBonus kind : AdminPlayerBonus.values())
				bonus.addProperty(kind.name(), config == null ? null : config.getEffective(kind.option()));
			player.add("bonus", bonus);
			player.addProperty("usedSub", config == null ? null : config.getEffective(PlayerConfigOptions.USED_SUBCLAIM));
			if(claimManager != null) {
				TeamClaimManager.BudgetInfo budget = claimManager.getBudgetInfo(id);
				JsonObject budgetObject = new JsonObject();
				budgetObject.addProperty("privateClaims", budget.privateClaims());
				budgetObject.addProperty("privateClaimLimit", budget.privateClaimLimit());
				budgetObject.addProperty("privateForceloads", budget.privateForceloads());
				budgetObject.addProperty("privateForceloadLimit", budget.privateForceloadLimit());
				budgetObject.addProperty("inTeam", budget.inTeam());
				budgetObject.addProperty("teamClaims", budget.teamClaims());
				budgetObject.addProperty("teamClaimLimit", budget.teamClaimLimit());
				player.add("budget", budgetObject);
			}
			object.add(names.getOrDefault(id, id.toString()), player);
		}
		return object;
	}

	/**
	 * Every scalar option of both server configs: {@code running} is what the server runs with, {@code pending} what is
	 * saved for the next start of a restart-only option (only where it differs), by {@code <config id>:<path>}.
	 */
	private static JsonObject config() {
		JsonObject object = new JsonObject();
		JsonObject running = new JsonObject();
		JsonObject pending = new JsonObject();
		JsonObject restartOnly = new JsonObject();
		for(AdminConfigEntry entry : AdminConfigs.snapshot()) {
			String key = entry.configId() + ":" + entry.path();
			running.addProperty(key, entry.value());
			restartOnly.addProperty(key, entry.restart());
			if(entry.pendingValue() != null)
				pending.addProperty(key, entry.pendingValue());
		}
		object.add("running", running);
		object.add("pending", pending);
		object.add("restartOnly", restartOnly);
		object.addProperty("teamClaimsLoaded", TeamClaimsServerConfig.SPEC.isLoaded());
		return object;
	}

	/**
	 * The values of the server config files as they are on disk: the copy in the world's {@code serverconfig} folder
	 * when there is one (it overrides the one in {@code config}), else the one in {@code config}.
	 */
	private static JsonObject configFiles(MinecraftServer server) {
		JsonObject object = new JsonObject();
		for(Map.Entry<String, String> file : Map.of(AdminConfigEntry.CONFIG_OPAC, "openpartiesandclaims-server.toml",
				AdminConfigEntry.CONFIG_TEAM_CLAIMS, TeamClaimsServerConfig.FILE_NAME).entrySet()) {
			Path override = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(file.getValue());
			Path global = server.getServerDirectory().resolve("config").resolve(file.getValue());
			Path used = Files.isRegularFile(override) ? override : global;
			JsonObject fileObject = new JsonObject();
			fileObject.addProperty("path", used.toAbsolutePath().normalize().toString());
			fileObject.addProperty("override", used == override);
			JsonObject values = new JsonObject();
			try {
				for(Map.Entry<String, String> value : TomlValues.parse(Files.readString(used, StandardCharsets.UTF_8)).entrySet())
					values.addProperty(value.getKey(), value.getValue());
				fileObject.add("values", values);
			} catch(IOException e) {
				fileObject.addProperty("error", e.toString());
			}
			object.add(file.getKey(), fileObject);
		}
		return object;
	}

}
