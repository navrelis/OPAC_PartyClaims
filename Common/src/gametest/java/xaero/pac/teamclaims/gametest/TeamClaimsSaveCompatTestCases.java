package xaero.pac.teamclaims.gametest;

import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.claims.ServerClaimsManager;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.ServerPlayerClaimInfo;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.server.claims.player.io.serialization.nbt.PlayerClaimInfoNbtSerializer;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.PlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimManager.BudgetInfo;
import xaero.pac.teamclaims.TeamClaimManager.ClaimPos;
import xaero.pac.teamclaims.TeamClaimManager.TeamBudget;
import xaero.pac.teamclaims.TeamClaimManager.TeamClaimSavedData;
import xaero.pac.teamclaims.TeamClaimManager.TeamData;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamRole;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;

/**
 * Dev/test-only, loader-neutral test bodies for loading a world that was saved by the fork in version 1.2.0 (OPAC
 * 0.31.6): "save compatibility". They only make sense in the dedicated runs (":Fabric:runSaveCompatTest" and
 * ":NeoForge:runSaveCompatGameTest"), which copy the fixture {@code Common/src/gametest/resources/world-data-from-1.2.0}
 * (see the README.txt there for what it holds and how it was made) into a fresh world folder before the server starts,
 * so the server has loaded the old files with the real loading code by the time a test runs. The default test runs do
 * not register these tests, and {@link #requireSaveCompatRun} fails them with a clear message if the world is not the
 * fixture.
 * <p>
 * The fixture's players, claims and settings are the constants below; the tests compare the loaded state with them and
 * change nothing (the world is thrown away afterwards).
 */
public final class TeamClaimsSaveCompatTestCases {

    /** System property the two run tasks set; the tests are meaningless without the fixture world they prepare. */
    public static final String RUN_PROPERTY = "opac.teamclaims.savecompat";

    private static final ResourceLocation NETHER = Level.NETHER.location();
    private static final String SUB_ID = "team_fixture_team";
    private static final int TEAM_SUB = 1;
    private static final UUID PARTY = UUID.fromString("557ea6ce-99d8-4c3c-957e-9762b8121a27");
    private static final UUID A = UUID.fromString("11111111-1111-4111-8111-111111111111");//party owner
    private static final UUID B = UUID.fromString("22222222-2222-4222-8222-222222222222");//party member, ADMIN
    private static final UUID C = UUID.fromString("33333333-3333-4333-8333-333333333333");//party member
    private static final UUID D = UUID.fromString("44444444-4444-4444-8444-444444444444");//in no party
    private static final List<UUID> PLAYERS = List.of(A, B, C, D);

    /** One claim of the fixture. */
    private record Claim(UUID owner, ResourceLocation dimension, int x, int z, int subConfigIndex, boolean forceloaded) {
        ClaimPos pos() {
            return new ClaimPos(dimension, x, z);
        }

        boolean isTeamClaim() {
            return subConfigIndex == TEAM_SUB;
        }

        @Override
        public String toString() {
            return owner.toString().substring(0, 2) + "@" + dimension.getPath() + " " + x + "," + z + " sub " + subConfigIndex
                    + (forceloaded ? " forceloaded" : "");
        }
    }

    /** All 17 claims, the team claims in the order in which they became team claims. */
    private static final List<Claim> CLAIMS = List.of(
            new Claim(A, Level.OVERWORLD.location(), 10, 0, -1, false),
            new Claim(A, Level.OVERWORLD.location(), 11, 0, -1, true),
            new Claim(A, Level.OVERWORLD.location(), 100, 0, TEAM_SUB, false),
            new Claim(A, Level.OVERWORLD.location(), 101, 0, TEAM_SUB, false),
            new Claim(A, Level.OVERWORLD.location(), 102, 0, TEAM_SUB, true),
            new Claim(B, Level.OVERWORLD.location(), 20, 0, -1, false),
            new Claim(B, Level.OVERWORLD.location(), 20, 1, -1, false),
            new Claim(B, NETHER, 6, 6, -1, false),
            new Claim(B, Level.OVERWORLD.location(), 110, 0, TEAM_SUB, false),
            new Claim(B, Level.OVERWORLD.location(), 111, 0, TEAM_SUB, false),
            new Claim(B, NETHER, 5, 5, TEAM_SUB, false),
            new Claim(C, Level.OVERWORLD.location(), 30, 0, -1, false),
            new Claim(C, Level.OVERWORLD.location(), 120, 0, TEAM_SUB, false),
            new Claim(D, Level.OVERWORLD.location(), 40, 0, -1, false),
            new Claim(D, Level.OVERWORLD.location(), 40, 1, -1, false),
            new Claim(D, Level.OVERWORLD.location(), 41, 0, -1, false),
            new Claim(D, NETHER, 7, 7, -1, false));

    /** The tracked team claims in the order of opacteamclaims_data.dat (the order in which they were made). */
    private static final List<ClaimPos> TEAM_CLAIM_ORDER = List.of(
            new ClaimPos(Level.OVERWORLD.location(), 100, 0), new ClaimPos(Level.OVERWORLD.location(), 101, 0),
            new ClaimPos(Level.OVERWORLD.location(), 102, 0), new ClaimPos(Level.OVERWORLD.location(), 110, 0),
            new ClaimPos(Level.OVERWORLD.location(), 111, 0), new ClaimPos(NETHER, 5, 5),
            new ClaimPos(Level.OVERWORLD.location(), 120, 0));

    private TeamClaimsSaveCompatTestCases() {}

    // ==================== Tests ====================

    /**
     * Every claim of the old world is there with its owner, sub-config index and forceload flag, nobody has a claim
     * that the fixture does not have (also not the server and the expired-claims owners), nobody has an anchor, and
     * nothing is converted or unclaimed in the 20 ticks after the load (the checks run again then).
     */
    public static void oldClaimsAreAllThere(GameTestHelper helper) {
        MinecraftServer server = requireSaveCompatRun(helper);
        assertClaimsAreTheFixtureClaims(helper, server, "right after the load");
        helper.runAfterDelay(20, () -> {
            assertClaimsAreTheFixtureClaims(helper, server, "20 ticks after the load");
            helper.succeed();
        });
    }

    /**
     * Team Claims tracks exactly the 7 team claims of the fixture per party and owner, in their order, with the one
     * team forceload; the team and private budgets show the fixture's counts; no over-limit deadline was started; the
     * team claims are team claims and the private ones are not.
     */
    public static void teamClaimsAndBudgetsMatchTheOldWorld(GameTestHelper helper) {
        MinecraftServer server = requireSaveCompatRun(helper);
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamData teamData = cm.getTeamData(PARTY);
        helper.assertTrue(teamData != null, "expected Team Claims to track the team claims of the old party");
        helper.assertTrue(List.copyOf(teamData.getTrackedClaims()).equals(TEAM_CLAIM_ORDER),
                "expected the team claims of the old world in their saved order " + TEAM_CLAIM_ORDER + ", got " + teamData.getTrackedClaims());
        for (Claim claim : CLAIMS) {
            if (!claim.isTeamClaim()) continue;
            helper.assertTrue(claim.owner.equals(teamData.getOwner(claim.pos())),
                    "expected " + claim + " to be tracked as a team claim of its owner, got " + teamData.getOwner(claim.pos()));
        }
        helper.assertTrue(teamData.getClaimCountOf(A) == 3 && teamData.getClaimCountOf(B) == 3 && teamData.getClaimCountOf(C) == 1
                        && teamData.getClaimCountOf(D) == 0,
                "expected 3, 3, 1 and 0 team claims for the owner, B, C and D, got " + teamData.getClaimCountOf(A) + ", "
                        + teamData.getClaimCountOf(B) + ", " + teamData.getClaimCountOf(C) + " and " + teamData.getClaimCountOf(D));
        helper.assertTrue(teamData.getForceloadedClaims().equals(Set.of(new ClaimPos(Level.OVERWORLD.location(), 102, 0)))
                        && teamData.getForceloadCountOf(A) == 1 && teamData.getForceloadCountOf(B) == 0,
                "expected exactly the team claim at 102,0 to be a team forceload, got " + teamData.getForceloadedClaims());
        helper.assertTrue(teamData.getClaimDeadline() == 0 && teamData.getForceloadDeadline() == 0,
                "expected no over-limit deadline after the load");

        TeamBudget team = cm.getTeamBudget(PARTY);
        helper.assertTrue(team != null && team.memberCount() == 3 && team.claims() == 7 && team.forceloads() == 1
                        && team.claimsOverLimit() == 0 && team.forceloadsOverLimit() == 0 && team.claimDeadline() == 0
                        && team.forceloadDeadline() == 0,
                "expected the team budget of 3 members with 7 team claims and 1 team forceload within its limits, got " + team);

        assertBudget(helper, cm.getBudgetInfo(A), 2, 1, 3, 1, true, "the party owner");
        assertBudget(helper, cm.getBudgetInfo(B), 3, 0, 3, 0, true, "member B");
        assertBudget(helper, cm.getBudgetInfo(C), 1, 0, 1, 0, true, "member C");
        assertBudget(helper, cm.getBudgetInfo(D), 4, 0, 0, 0, false, "player D without a party");

        IServerClaimsManagerAPI claimsAPI = claimsAPI(server);
        for (Claim claim : CLAIMS) {
            IPlayerChunkClaimAPI loaded = claimsAPI.get(claim.dimension, claim.x, claim.z);
            helper.assertTrue(cm.isTeamClaim(loaded) == claim.isTeamClaim(),
                    "expected " + claim + " " + (claim.isTeamClaim() ? "to be" : "not to be") + " a team claim");
        }
        helper.succeed();
    }

    /**
     * The party (owner, member ranks), the team config (name, sub-config ID, members and the custom team roles), the
     * team sub-configs of the members (index 1, name, colour) and the player config of the owner (claims name, party
     * name) of the old world are all there.
     */
    public static void partyTeamSettingsAndRolesAreKept(GameTestHelper helper) {
        MinecraftServer server = requireSaveCompatRun(helper);
        IServerPartyAPI party = partyManager(server).getPartyById(PARTY);
        helper.assertTrue(party != null, "expected the old party to be loaded");
        IPartyMemberAPI owner = party.getOwner();
        helper.assertTrue(owner != null && A.equals(owner.getUUID()), "expected the party owner to be FixOwner, got " + owner);
        IPartyMemberAPI memberB = party.getMemberInfo(B);
        IPartyMemberAPI memberC = party.getMemberInfo(C);
        helper.assertTrue(memberB != null && memberB.getRank() == PartyMemberRank.ADMIN && memberC != null
                        && memberC.getRank() == PartyMemberRank.MEMBER && party.getMemberInfo(D) == null,
                "expected B to be an ADMIN, C a MEMBER and D not a member of the old party");

        TeamConfig team = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(PARTY);
        helper.assertTrue(team != null, "expected the team config of the old party to be loaded");
        helper.assertTrue("Fixture Team".equals(team.getTeamName()) && SUB_ID.equals(team.getSubConfigId())
                        && team.getMembers().equals(Set.of(A, B, C)),
                "expected the team 'Fixture Team' with sub-config " + SUB_ID + " and members A, B and C, got '" + team.getTeamName()
                        + "', " + team.getSubConfigId() + " and " + team.getMembers());
        helper.assertTrue(team.getRequiredRole(TeamAction.CLAIM) == TeamRole.MODERATOR
                        && team.getRequiredRole(TeamAction.FORCELOAD) == TeamRole.ADMIN
                        && team.getRequiredRole(TeamAction.UNCLAIM) == TeamRole.MEMBER,
                "expected the team roles claim = MODERATOR, forceload = ADMIN and unclaim = MEMBER, got "
                        + team.getRequiredRole(TeamAction.CLAIM) + ", " + team.getRequiredRole(TeamAction.FORCELOAD) + " and "
                        + team.getRequiredRole(TeamAction.UNCLAIM));
        TeamClaimsIntegration.TeamClaimsHandler handler = TeamClaimsIntegration.getHandler();
        for (UUID member : List.of(A, B, C)) {
            IPlayerConfigAPI sub = configManager(server).getLoadedConfig(member).getSubConfig(SUB_ID);
            helper.assertTrue(sub != null && sub.getSubIndex() == TEAM_SUB,
                    "expected " + member + " to have the team sub-config " + SUB_ID + " with index " + TEAM_SUB + ", got "
                            + (sub == null ? null : sub.getSubIndex()));
            helper.assertTrue("Fixture Team".equals(sub.getEffective(PlayerConfigOptions.CLAIMS_NAME))
                            && sub.getEffective(PlayerConfigOptions.CLAIMS_COLOR) == 16745191,
                    "expected the team sub-config of " + member + " to have the team's claims name and colour, got '"
                            + sub.getEffective(PlayerConfigOptions.CLAIMS_NAME) + "' and " + sub.getEffective(PlayerConfigOptions.CLAIMS_COLOR));
            helper.assertTrue(handler.isTeamSubConfigIndex(member, TEAM_SUB), "expected index 1 to be the team sub-config of " + member);
        }
        helper.assertTrue(configManager(server).getLoadedConfig(D).getSubConfig(SUB_ID) == null,
                "expected D, who is in no party, not to have the team sub-config");
        IPlayerConfigAPI ownerConfig = configManager(server).getLoadedConfig(A);
        helper.assertTrue("Fixture Land".equals(ownerConfig.getEffective(PlayerConfigOptions.CLAIMS_NAME))
                        && "Fixture Team".equals(ownerConfig.getEffective(PlayerConfigOptions.PARTY_NAME)),
                "expected the owner's player config to keep the claims name 'Fixture Land' and the party name 'Fixture Team', got '"
                        + ownerConfig.getEffective(PlayerConfigOptions.CLAIMS_NAME) + "' and '"
                        + ownerConfig.getEffective(PlayerConfigOptions.PARTY_NAME) + "'");
        helper.succeed();
    }

    /**
     * Writing the loaded data and reading it again keeps the content: every player's claim info goes through OPAC's NBT
     * serializer (which now writes an anchor list per dimension, empty here) and back, Team Claims' saved data goes
     * through its NBT writer and reader, and the team config through its JSON.
     */
    public static void loadedDataSurvivesSaveAndReload(GameTestHelper helper) {
        MinecraftServer server = requireSaveCompatRun(helper);
        ServerClaimsManager claimsManager = (ServerClaimsManager) (Object) ServerData.from(server).getServerClaimsManager();
        PlayerClaimInfoNbtSerializer serializer = PlayerClaimInfoNbtSerializer.Builder.begin().setClaimsManager(claimsManager).build();
        List<UUID> owners = new ArrayList<>(PLAYERS);
        owners.add(PlayerConfig.SERVER_CLAIM_UUID);
        for (UUID owner : owners) {
            ServerPlayerClaimInfo info = claimsManager.getPlayerInfo(owner);
            CompoundTag saved = serializer.serialize(info);
            CompoundTag dimensions = saved.getCompound("dimensions");
            for (String dimension : dimensions.getAllKeys()) {
                helper.assertTrue(dimensions.getCompound(dimension).contains("anchors") && dimensions.getCompound(dimension).getList("anchors", 10).isEmpty(),
                        "expected the saved claims of " + owner + " in " + dimension + " to have an empty list of anchors, got "
                                + dimensions.getCompound(dimension));
            }
            ServerPlayerClaimInfo reloaded = serializer.deserialize(owner, claimsManager.getPlayerClaimInfoManager(), saved);
            helper.assertTrue(claimsOf(info).equals(claimsOf(reloaded)),
                    "expected the claims of " + owner + " to be the same after saving and loading again, got " + claimsOf(reloaded)
                            + " instead of " + claimsOf(info));
            helper.assertTrue(info.getClaimCount() == reloaded.getClaimCount() && info.getForceloadCount() == reloaded.getForceloadCount()
                            && info.getPlayerUsername().equals(reloaded.getPlayerUsername()),
                    "expected the counts and the name of " + owner + " to be the same after saving and loading again");
        }

        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        TeamData reread = TeamClaimSavedData.load(cm.getSavedData().save(new CompoundTag(), registries), registries).getTeamData(PARTY);
        helper.assertTrue(reread != null && reread.getLoadedClaims().equals(TEAM_CLAIM_ORDER),
                "expected Team Claims' saved data to keep the tracked claims in their order after saving and loading again, got "
                        + (reread == null ? null : reread.getLoadedClaims()));

        TeamConfig team = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(PARTY);
        TeamConfig teamAgain = TeamConfig.fromJson(JsonParser.parseString(team.toJsonString()).getAsJsonObject());
        helper.assertTrue(teamAgain.toJsonString().equals(team.toJsonString()) && teamAgain.getMembers().equals(team.getMembers())
                        && teamAgain.getRequiredRole(TeamAction.CLAIM) == TeamRole.MODERATOR,
                "expected the team config to be the same after saving and loading again, got " + teamAgain.toJsonString());
        helper.succeed();
    }

    // ==================== Helpers ====================

    /** The server of the test; fails with a clear message if this is not the save compatibility run. */
    private static MinecraftServer requireSaveCompatRun(GameTestHelper helper) {
        helper.assertTrue(Boolean.getBoolean(RUN_PROPERTY),
                "this test only works in the save compatibility run (system property " + RUN_PROPERTY + "), which starts the server on the "
                        + "world data of the fixture world-data-from-1.2.0");
        return helper.getLevel().getServer();
    }

    private static void assertClaimsAreTheFixtureClaims(GameTestHelper helper, MinecraftServer server, String stage) {
        IServerClaimsManagerAPI claimsAPI = claimsAPI(server);
        for (Claim claim : CLAIMS) {
            IPlayerChunkClaimAPI loaded = claimsAPI.get(claim.dimension, claim.x, claim.z);
            helper.assertTrue(loaded != null && claim.owner.equals(loaded.getPlayerId()) && loaded.getSubConfigIndex() == claim.subConfigIndex
                            && loaded.isForceloadable() == claim.forceloaded,
                    stage + ": expected " + claim + ", got " + (loaded == null ? "an unclaimed chunk" : loaded.getPlayerId() + " sub "
                            + loaded.getSubConfigIndex() + (loaded.isForceloadable() ? " forceloaded" : "")));
        }
        for (UUID player : PLAYERS) {
            Set<Claim> expected = new HashSet<>();
            for (Claim claim : CLAIMS) if (claim.owner.equals(player)) expected.add(claim);
            IServerPlayerClaimInfoAPI info = claimsAPI.getPlayerInfo(player);
            Set<Claim> actual = claimsOf(info, player);
            helper.assertTrue(actual.equals(expected), stage + ": expected " + player + " to have exactly " + expected + ", got " + actual);
            helper.assertTrue(info.getClaimCount() == expected.size()
                            && info.getForceloadCount() == expected.stream().filter(Claim::forceloaded).count(),
                    stage + ": expected the claim count of " + player + " to be " + expected.size() + ", got " + info.getClaimCount()
                            + " claims and " + info.getForceloadCount() + " forceloads");
            for (var entry : info.getStream().toList()) {
                helper.assertTrue(entry.getValue().getAnchors().isEmpty(),
                        stage + ": expected " + player + " to have no anchors in " + entry.getKey() + ", got " + entry.getValue().getAnchors());
            }
        }
        long total = claimsAPI.getPlayerInfoStream().mapToLong(IServerPlayerClaimInfoAPI::getClaimCount).sum();
        helper.assertTrue(total == CLAIMS.size(), stage + ": expected " + CLAIMS.size() + " claims in the whole world, got " + total);
        for (UUID noClaims : List.of(PlayerConfig.SERVER_CLAIM_UUID, PlayerConfig.EXPIRED_CLAIM_UUID)) {
            helper.assertTrue(claimsAPI.getPlayerInfo(noClaims).getClaimCount() == 0,
                    stage + ": expected " + noClaims + " not to have claims, got " + claimsAPI.getPlayerInfo(noClaims).getClaimCount());
        }
    }

    private static void assertBudget(GameTestHelper helper, BudgetInfo budget, int privateClaims, int privateForceloads,
            int teamClaims, int teamForceloads, boolean inTeam, String who) {
        helper.assertTrue(budget.inTeam() == inTeam && budget.privateClaims() == privateClaims && budget.privateForceloads() == privateForceloads
                        && budget.ownedTeamClaims() == teamClaims && budget.ownedTeamForceloads() == teamForceloads
                        && budget.privateClaims() <= budget.privateClaimLimit() && budget.overLimitDeadline() == 0,
                "expected " + who + " to have " + privateClaims + " private claims (" + privateForceloads + " forceloaded) and "
                        + teamClaims + " team claims (" + teamForceloads + " forceloaded) within the limits, got " + budget);
        if (inTeam)
            helper.assertTrue(budget.teamClaims() == 7 && budget.teamForceloads() == 1 && budget.claimsOverLimit() == 0,
                    "expected the team budget of " + who + " to show 7 team claims and 1 team forceload, got " + budget);
    }

    private static Set<Claim> claimsOf(ServerPlayerClaimInfo info) {
        return claimsOf(info, info.getPlayerId());
    }

    private static Set<Claim> claimsOf(IServerPlayerClaimInfoAPI info, UUID owner) {
        Set<Claim> claims = new HashSet<>();
        info.getStream().forEach(entry -> entry.getValue().getStream().forEach(posList -> {
            IPlayerChunkClaimAPI state = posList.getClaimState();
            posList.getStream().forEach((ChunkPos pos) -> claims.add(
                    new Claim(owner, entry.getKey(), pos.x, pos.z, state.getSubConfigIndex(), state.isForceloadable())));
        }));
        return claims;
    }
}
