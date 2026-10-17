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

package xaero.pac.teamclaims.twoplayertest.selfcheck;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.twoplayertest.Barrier;
import xaero.pac.teamclaims.twoplayertest.Results;
import xaero.pac.teamclaims.twoplayertest.ScenarioPlan;
import xaero.pac.teamclaims.twoplayertest.Snap;
import xaero.pac.teamclaims.twoplayertest.TwoPlayerTest;
import xaero.pac.teamclaims.twoplayertest.server.LogCapture;
import xaero.pac.teamclaims.twoplayertest.server.TomlValues;
import xaero.pac.teamclaims.twoplayertest.server.TwoPlayerTestServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * [Team Claims] The headless check of the two-player test ({@code :Fabric:runTwoPlayerTestSelfCheck -PtwoPlayerTest}): a
 * gametest server with the test mod, which checks the parts that need no game window: the snapshot of the server state
 * against a party, a team claim and a team bonus made here, the test command, the log capture, the result file (nothing
 * passes by default, a missing id fails), the barrier, the toml reader, and that the plan of the drivers is the list the
 * script expects ({@code tools/twoplayertest/expected-assertions.txt}).
 * <p>
 * Registered as the {@code fabric-gametest} entrypoint of its own dev-only mod
 * ({@code opac_teamclaims_twoplayertest_selfcheck}, source set {@code twoplayertestSelfcheck}), which only the self-check
 * run has on its classpath; the server and the clients of the real test do not.
 */
public class TwoPlayerTestSelfCheck implements FabricGameTest {

	public static final String MANIFEST_PROPERTY = "opac.twoplayertest.manifest";
	private static final Logger LOGGER = LogUtils.getLogger();

	private static void check(boolean condition, String message) {
		if(!condition)
			throw new GameTestAssertException(message);
	}

	private static Path scratch(String name) throws IOException {
		Path out = TwoPlayerTest.outDir();
		check(out != null, "the self-check needs -D" + TwoPlayerTest.OUT_PROPERTY);
		Path dir = out.resolve("selfcheck-scratch").resolve(name);
		if(Files.exists(dir))
			try(var walk = Files.walk(dir)) {
				for(Path path : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList())
					Files.delete(path);
			}
		Files.createDirectories(dir);
		return dir;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void planMatchesTheScriptManifest(GameTestHelper helper) {
		String manifest = System.getProperty(MANIFEST_PROPERTY);
		check(manifest != null, "the self-check needs -D" + MANIFEST_PROPERTY);
		List<String> expected = new ArrayList<>();
		try {
			for(String line : Files.readAllLines(Path.of(manifest), StandardCharsets.UTF_8)) {
				String id = line.strip();
				if(!id.isEmpty() && !id.startsWith("#"))
					expected.add(id);
			}
		} catch(IOException e) {
			throw new GameTestAssertException("cannot read " + manifest + ": " + e);
		}
		List<String> plan = ScenarioPlan.allIds();
		check(plan.equals(expected), "the plan of the drivers " + plan + " is not the list of " + manifest + " " + expected);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void snapshotShowsTheServerState(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		OpenPACServerAPI api = OpenPACServerAPI.get(server);
		check(TeamClaimsCommon.isActive(), "Team Claims is not active on the self-check server");
		GameProfile owner = new GameProfile(UUIDUtil.createOfflinePlayerUUID("SelfOwner"), "SelfOwner");
		GameProfile member = new GameProfile(UUIDUtil.createOfflinePlayerUUID("SelfMember"), "SelfMember");
		ResourceLocation overworld = server.overworld().dimension().location();
		ChunkPos spawn = new ChunkPos(server.overworld().getSharedSpawnPos());
		int teamX = spawn.x + 2, privateX = spawn.x + 3, z = spawn.z + 1;
		IServerPartyAPI party = api.getPartyManager().createPartyForOwner(owner);
		check(party != null, "could not create a party");
		UUID partyId = party.getId();
		try {
			check(party.addMember(member.getId(), PartyMemberRank.MEMBER, member.getName()) != null, "could not add the member");
			TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().createTeamConfig(party);
			TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
			claimManager.ensureAllMembersHaveSubConfig(party);
			check(claimManager.setTeamBonus(partyId, false, 42), "could not set the team bonus");
			IPlayerConfigAPI sub = api.getPlayerConfigManager().getLoadedConfig(owner.getId()).getSubConfig(teamConfig.getSubConfigId());
			check(sub != null, "the owner has no team sub-config " + teamConfig.getSubConfigId());
			check(api.getServerClaimsManager().claim(overworld, owner.getId(), sub.getSubIndex(), teamX, z, false) != null, "could not make the team claim");
			check(api.getServerClaimsManager().claim(overworld, owner.getId(), -1, privateX, z, false) != null, "could not make the private claim");

			Path file = TwoPlayerTestServer.writeSnapshot(server, "selfcheck-state", "selfcheck");
			JsonObject s = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			check(Snap.errors(s).isEmpty(), "the snapshot has errors " + Snap.errors(s));
			JsonObject partyJson = Snap.partyOwnedBy(s, "SelfOwner");
			check(partyJson != null, "no party of SelfOwner in " + s.get("parties"));
			check("MEMBER".equals(Snap.rank(partyJson, "SelfMember")), "SelfMember is not a MEMBER: " + Snap.memberNames(partyJson));
			check(Boolean.TRUE.equals(Snap.bool(Snap.obj(partyJson, "owner"), "owner")), "the owner is not marked as owner");
			check(Boolean.TRUE.equals(Snap.bool(Snap.team(partyJson), "exists")), "the team is missing");
			check(Integer.valueOf(42).equals(Snap.integer(Snap.team(partyJson), "bonusClaims")), "team bonus " + Snap.integer(Snap.team(partyJson), "bonusClaims") + " instead of 42");
			check(Integer.valueOf(2).equals(Snap.teamBudget(partyJson, "memberCount")), "team member count " + Snap.teamBudget(partyJson, "memberCount"));
			check(teamConfig.getSubConfigId().equals(Snap.str(Snap.team(partyJson), "subConfigId")), "team sub-config id");
			check("MEMBER".equals(Snap.role(partyJson, "claim")), "claim role " + Snap.role(partyJson, "claim"));
			JsonObject teamClaim = Snap.claimAt(s, overworld.toString(), teamX, z);
			check(teamClaim != null && owner.getId().toString().equals(Snap.str(teamClaim, "owner")) && Boolean.TRUE.equals(Snap.bool(teamClaim, "teamClaim")),
					"team claim " + Snap.describeClaim(teamClaim));
			JsonObject privateClaim = Snap.claimAt(s, overworld.toString(), privateX, z);
			check(privateClaim != null && Boolean.FALSE.equals(Snap.bool(privateClaim, "teamClaim")), "private claim " + Snap.describeClaim(privateClaim));
			check(Snap.claimAt(s, overworld.toString(), spawn.x + 4, z) == null, "a claim where there is none");
			check(Snap.bonus(s, "SelfOwner", "CLAIMS") != null, "no bonus of SelfOwner in " + Snap.playerData(s, "SelfOwner"));
			check(Integer.valueOf(1).equals(Snap.playerBudget(s, "SelfOwner", "teamClaims")), "team claims of SelfOwner " + Snap.playerBudget(s, "SelfOwner", "teamClaims"));
			check(String.valueOf(TeamClaimsServerConfig.CONFIG.teamClaimsMinMembers.get()).equals(Snap.running(s, "teamclaims:teamClaims.teamClaimsMinMembers")),
					"running teamClaimsMinMembers " + Snap.running(s, "teamclaims:teamClaims.teamClaimsMinMembers"));
			check(String.valueOf(ServerConfig.CONFIG.maxPartyInvites.get()).equals(Snap.running(s, "opac:serverConfig.parties.maxPartyInvites")),
					"running maxPartyInvites " + Snap.running(s, "opac:serverConfig.parties.maxPartyInvites"));
			check(String.valueOf(ServerConfig.CONFIG.maxSingleClaimActionSize.get()).equals(Snap.running(s, "opac:serverConfig.claims.maxSingleClaimActionSize")),
					"running maxSingleClaimActionSize " + Snap.running(s, "opac:serverConfig.claims.maxSingleClaimActionSize"));
			check(Boolean.TRUE.equals(Snap.restartOnly(s, "opac:serverConfig.parties.maxPartyInvites")), "maxPartyInvites is not restart-only");
			check(Boolean.FALSE.equals(Snap.restartOnly(s, "teamclaims:teamClaims.teamClaimsBase")), "teamClaimsBase is not live");
			check(Snap.fileValue(s, "teamclaims", "teamClaims.teamClaimsBase") != null, "no file value of teamClaimsBase in " + s.get("configFiles"));
			check(Snap.fileValue(s, "opac", "serverConfig.parties.maxPartyInvites") != null, "no file value of maxPartyInvites in " + s.get("configFiles"));
			check(server.getCommands().getDispatcher().getRoot().getChild(TwoPlayerTestServer.COMMAND) != null, "the /" + TwoPlayerTestServer.COMMAND + " command is not registered");
		} catch(IOException e) {
			throw new GameTestAssertException("snapshot file: " + e);
		} finally {
			api.getServerClaimsManager().unclaim(overworld, teamX, z);
			api.getServerClaimsManager().unclaim(overworld, privateX, z);
			try {
				TeamClaimsCommon.getTeamConfigManager().removeTeamConfig(partyId);
			} catch(RuntimeException ignored) {
			}
			api.getPartyManager().removePartyById(partyId);
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void logCaptureKeepsWarnings(GameTestHelper helper) {
		check(LogCapture.install(), "the log capture could not be installed");
		String marker = "[TeamClaims] Rejected an admin menu request (selfcheck) of SelfCheckPlayer";
		LOGGER.warn(marker);
		LOGGER.info("[TwoPlayerTest] an info line that the capture must not keep");
		List<String> lines = LogCapture.lines();
		check(lines.stream().anyMatch(line -> line.contains(marker)), "the warning is not captured: " + lines);
		check(lines.stream().noneMatch(line -> line.contains("an info line that the capture must not keep")), "an info line was captured");
		JsonObject snapshot = new JsonObject();
		com.google.gson.JsonArray warnings = new com.google.gson.JsonArray();
		lines.forEach(warnings::add);
		snapshot.add("warnings", warnings);
		check(Snap.warningContains(snapshot, "Rejected an admin menu request", "SelfCheckPlayer"), "Snap.warningContains does not find it");
		check(!Snap.warningContains(snapshot, "Rejected an admin menu request", "SomebodyElse"), "Snap.warningContains finds a line that is not there");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void resultsNeverPassByDefault(GameTestHelper helper) {
		try {
			Path dir = scratch("results");
			Path file = dir.resolve("results.json");
			Results results = new Results(TwoPlayerTest.PLAYER1, file);
			results.check(ScenarioPlan.P1_JOIN, true, "joined", "joined", List.of());
			results.check(ScenarioPlan.P1_KEY_OPENS, false, "hub", "SocialInteractionsScreen", List.of());
			results.skip(ScenarioPlan.P1_PARTY_FAKE_MEMBER, "test");
			results.check(ScenarioPlan.P1_KEY_CLOSES, true, "x", "x", List.of());
			results.check(ScenarioPlan.P1_KEY_CLOSES, true, "x", "x", List.of());// twice: must fail
			results.check(ScenarioPlan.P2_JOIN, true, "x", "x", List.of());// Player2's id in Player1's results: must fail
			results.finish("self-check");
			JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			check(Boolean.TRUE.equals(Snap.bool(json, "finished")), "not marked finished");
			int pass = 0, fail = 0, skipped = 0;
			for(JsonObject entry : Snap.list(json, "results"))
				switch(Snap.str(entry, "status")) {
					case "PASS" -> pass++;
					case "FAIL" -> fail++;
					case "SKIPPED" -> skipped++;
					default -> throw new GameTestAssertException("unknown status in " + entry);
				}
			int planned = ScenarioPlan.of(TwoPlayerTest.PLAYER1).size();
			check(pass == 1, "expected only the one passed check to pass, got " + pass);
			check(skipped == 1, "expected one skip, got " + skipped);
			// planned minus the pass and the skip, plus the unplanned entry
			check(fail == planned - 2 + 1, "expected " + (planned - 1) + " failures, got " + fail);
			check(results.get(ScenarioPlan.P1_KEY_CLOSES).status() == Results.Status.FAIL, "an id reported twice did not fail");
			check(results.get(ScenarioPlan.P1_ADMIN_SUGGESTION).actual().startsWith("never reported"), "an id that was never reported did not fail as such");
			check(results.isReported("unplanned." + ScenarioPlan.P2_JOIN), "an id outside the plan was not recorded as a failure");
		} catch(IOException e) {
			throw new GameTestAssertException("results file: " + e);
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void barrierWaitsAndTimesOut(GameTestHelper helper) {
		try {
			Path out = scratch("barrier");
			Barrier one = new Barrier(out, TwoPlayerTest.PLAYER1);
			Barrier two = new Barrier(out, TwoPlayerTest.PLAYER2);
			long start = System.nanoTime();
			check(one.await(TwoPlayerTest.PLAYER2, "point", Duration.ofMillis(300)) == Barrier.Outcome.TIMED_OUT, "a missing point did not time out");
			check(System.nanoTime() - start >= Duration.ofMillis(300).toNanos(), "the time-out came too early");
			two.arrive("point");
			check(one.await(TwoPlayerTest.PLAYER2, "point", Duration.ofMillis(300)) == Barrier.Outcome.REACHED, "a reached point was not seen");
			check(one.await(TwoPlayerTest.PLAYER2, "other", Duration.ofMillis(100)) == Barrier.Outcome.TIMED_OUT, "another point counted as reached");
			two.abort("self-check");
			check(one.await(TwoPlayerTest.PLAYER2, "later", Duration.ofSeconds(30)) == Barrier.Outcome.OTHER_ABORTED, "an abort did not end the wait");
			check(!TwoPlayerTest.isSafeLabel("../escape") && !TwoPlayerTest.isSafeLabel("a b") && TwoPlayerTest.isSafeLabel("Player1-001-party-created"), "label check");
		} catch(IOException e) {
			throw new GameTestAssertException("barrier: " + e);
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tomlValuesFollowTheSections(GameTestHelper helper) {
		String toml = """
				#comment
				[serverConfig]
					defaultLanguage = "en_us"
					[serverConfig.parties]
						#Max invites
						maxPartyInvites = 17 # trailing comment
					[serverConfig.claims]
						maxSingleClaimActionSize = 4
				[teamClaims]
					teamClaimsMinMembers = 2
					name = "a # not a comment"
				""";
		Map<String, String> values = TomlValues.parse(toml);
		check("en_us".equals(values.get("serverConfig.defaultLanguage")), "string value " + values);
		check("17".equals(values.get("serverConfig.parties.maxPartyInvites")), "nested section value " + values);
		check("4".equals(values.get("serverConfig.claims.maxSingleClaimActionSize")), "second section " + values);
		check("2".equals(values.get("teamClaims.teamClaimsMinMembers")), "other table " + values);
		check("a # not a comment".equals(values.get("teamClaims.name")), "a # inside a string " + values);
		helper.succeed();
	}

}
