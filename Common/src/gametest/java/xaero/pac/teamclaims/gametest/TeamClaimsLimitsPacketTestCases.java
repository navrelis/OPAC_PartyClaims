package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.pac.common.claims.player.mode.ClaimingModeLimits;
import xaero.pac.common.claims.player.mode.api.ClaimingModes;
import xaero.pac.common.packet.claims.ClientboundClaimLimitsPacket;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.claims.ServerClaimsManager;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.lazypacket.LazyPacketSender;
import xaero.pac.common.server.player.config.IPlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.common.server.player.config.util.ServerPlayerConfigUtils;
import xaero.pac.common.server.player.data.ServerPlayerData;
import xaero.pac.teamclaims.TeamClaimManager.BudgetSettings;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.gametest.TeamClaimsBudgetTestCases.Team;
import xaero.pac.teamclaims.gametest.TeamClaimsPacketTestCases.RecorderFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.removePlayerQuiet;

/**
 * Dev/test-only, loader-neutral test bodies for the claim limits packet ({@link ClientboundClaimLimitsPacket}, id 17)
 * that OPAC sends to a player and that the fork fills with the team's numbers or the player's private ones, and for
 * {@link ServerPlayerConfigUtils#isOverEitherClaimingLimit}. Upstream 0.32.7 added the claiming anchor numbers to the
 * packet (per mode: anchor count, limit and range; once: whether anchor-based claiming is on).
 * <p>
 * These tests look at what the player receives, not at what the limits builder returns (the budget tests do that): the
 * claim limits sync is triggered the way the server triggers it, the lazy packet queue is run until it is empty, and
 * the packet that arrives at a {@link TeamClaimsPacketTestCases.PacketRecorder} instead of the connection of a mock
 * player is written out the way it goes over the network and read as NBT, which is the actual wire format ({@code l} =
 * the limits by claiming mode with {@code cc}/{@code fc}/{@code ac} for the counts and {@code cl}/{@code fl}/{@code al}/
 * {@code ar} for the limits and the anchor range, which are left out for an unlimited value; {@code d} = the maximum
 * claim distance; {@code a} = whether the client has to use these numbers instead of counting the synced claims;
 * {@code ac} = whether anchor-based claiming is on). Same conventions as {@link TeamClaimsBudgetTestCases}: a chunk
 * offset of 1000 per test starting at 130000, cleanup on every path, every test registered with the loader's empty
 * structure template and the default timeout. Every test runs in one go, in the tick it starts in, and turns a server
 * setting on or off for that time only (the gametests run side by side on one server).
 */
public final class TeamClaimsLimitsPacketTestCases {

    private static final ResourceLocation NETHER = Level.NETHER.location();

    private TeamClaimsLimitsPacketTestCases() {}

    // ==================== Tests ====================

    /**
     * A team member whose main config is the used sub-claim gets their private numbers (private claims and forceloads
     * against their own limits, the own team claims and the team claims of the others not among them) in the PLAYER
     * mode, and is told to use them instead of counting: {@code a} is set for every member of a team. The packet
     * has exactly the three claiming modes and the claim distance of the server.
     */
    public static void claimLimitsPacketCarriesPrivateNumbersInPrivateMode(GameTestHelper helper, RecorderFactory recorders) {
        Scene s = Scene.create(helper, recorders, "T130", 130000);
        try {
            s.t.cm.setBudgetSettingsOverride(s.t.party.getId(), new BudgetSettings(2, 7, 5, 3, 1, 168));
            int privateClaimLimit = claimsAPI(s.server).getPlayerFullClaimLimit(s.playerId);
            int privateForceloadLimit = claimsAPI(s.server).getPlayerFullForceloadLimit(s.playerId);
            s.makeClaims();

            List<CompoundTag> sent = s.capture(s::sync);
            CompoundTag wire = s.only(sent, "the sync with the main config selected");
            assertMode(helper, wire, ClaimingModes.PLAYER.getId(), 2, privateClaimLimit, 1, privateForceloadLimit, "the private numbers");
            helper.assertTrue(wire.getCompound("l").getAllKeys().equals(ClaimingModes.ALL_IMMUTABLE.keySet()),
                    "expected exactly the claiming modes " + ClaimingModes.ALL_IMMUTABLE.keySet() + ", got " + wire.getCompound("l").getAllKeys());
            helper.assertTrue(wire.getBoolean("a"), "expected a member of a team to be told to use the server's counts");
            helper.assertTrue(wire.getInt("d") == ServerConfig.CONFIG.maxClaimDistance.get(),
                    "expected the maximum claim distance of the server, got " + wire.getInt("d"));
            helper.succeed();
        } finally {
            s.close();
        }
    }

    /**
     * While the team sub-config is the used sub-claim, the PLAYER mode carries the team's numbers (the team claims and
     * forceloads of all members against the team limits for the member count), the anchor numbers stay the player's own
     * and so does the flag to use the server's counts. Selecting another sub-claim again brings the private numbers back.
     */
    public static void claimLimitsPacketCarriesTeamNumbersInTeamMode(GameTestHelper helper, RecorderFactory recorders) {
        Scene s = Scene.create(helper, recorders, "T131", 131000);
        try {
            s.t.cm.setBudgetSettingsOverride(s.t.party.getId(), new BudgetSettings(2, 7, 5, 3, 1, 168));
            int privateClaimLimit = claimsAPI(s.server).getPlayerFullClaimLimit(s.playerId);
            int privateForceloadLimit = claimsAPI(s.server).getPlayerFullForceloadLimit(s.playerId);
            s.makeClaims();
            String player = ClaimingModes.PLAYER.getId();

            s.useTeamSubClaim();
            CompoundTag team = s.only(s.capture(s::sync), "the sync with the team sub-config selected");
            assertMode(helper, team, player, 3, 7, 1, 3, "the team numbers");
            helper.assertTrue(team.getBoolean("a"), "expected the team member to be told to use the server's counts in team mode too");
            s.useMainSubClaim();
            CompoundTag back = s.only(s.capture(s::sync), "the sync with the main config selected again");
            assertMode(helper, back, player, 2, privateClaimLimit, 1, privateForceloadLimit, "the private numbers again");
            helper.succeed();
        } finally {
            s.close();
        }
    }

    /**
     * The claiming anchor numbers that upstream added to the packet: per mode the anchors in the dimension of the player
     * (count, limit with the bonus of the player config, range with its bonus), and whether anchor-based claiming is
     * on. The PLAYER mode has the player's own anchor numbers in private and in team mode (the team has no anchor
     * budget), a PARTY mode without party-owned claims has zeros, the SERVER mode no limits at all. The packet decodes
     * to the same limits the builder returns.
     */
    public static void claimLimitsPacketCarriesTheAnchorFields(GameTestHelper helper, RecorderFactory recorders) {
        Scene s = Scene.create(helper, recorders, "T132", 132000);
        boolean anchorsBefore = ServerConfig.CONFIG.anchorBasedClaiming.get();
        try {
            ResourceLocation dimension = s.player.level().dimension().location();
            helper.assertTrue(Level.OVERWORLD.location().equals(dimension), "test setup: expected the mock player to be in the overworld");
            s.t.cm.setBudgetSettingsOverride(s.t.party.getId(), new BudgetSettings(2, 7, 5, 3, 1, 168));
            s.makeClaims();
            s.setBonus(PlayerConfigOptions.BONUS_CLAIMING_ANCHORS, 2);
            s.setBonus(PlayerConfigOptions.BONUS_CLAIMING_ANCHOR_RANGE, 3);
            helper.assertTrue(claimsAPI(s.server).addAnchor(dimension, new ChunkPos(s.t.x0 + 10, 0), s.playerId)
                            && claimsAPI(s.server).addAnchor(dimension, new ChunkPos(s.t.x0 + 11, 0), s.playerId),
                    "test setup: expected two anchors to be added");//they are private claims of the player, too
            int anchorLimit = claimsAPI(s.server).getPlayerFullAnchorLimit(s.playerId, dimension);
            int anchorRange = claimsAPI(s.server).getPlayerFullAnchorRange(s.playerId, dimension);
            helper.assertTrue(anchorLimit >= 2 && anchorRange >= 3, "test setup: expected the bonuses to count, got a limit of " + anchorLimit
                    + " and a range of " + anchorRange);
            String player = ClaimingModes.PLAYER.getId();

            TeamClaimsAnchorTestCases.setAnchorBasedClaiming(true);
            CompoundTag wire = s.only(s.capture(s::sync), "the sync with anchor-based claiming on");
            assertAnchors(helper, wire, player, 2, anchorLimit, anchorRange, "the player's anchors");
            helper.assertTrue(wire.getBoolean("ac"), "expected the packet to tell that anchor-based claiming is on");
            assertAnchors(helper, wire, ClaimingModes.PARTY.getId(), 0, 0, 0, "the party mode without party-owned claims");
            helper.assertTrue(wire.getCompound("l").getCompound(ClaimingModes.SERVER.getId()).contains("ac")
                            && !wire.getCompound("l").getCompound(ClaimingModes.SERVER.getId()).contains("al")
                            && !wire.getCompound("l").getCompound(ClaimingModes.SERVER.getId()).contains("ar")
                            && !wire.getCompound("l").getCompound(ClaimingModes.SERVER.getId()).contains("cl"),
                    "expected the server mode to have an anchor count and no limits, got " + wire.getCompound("l").getCompound(ClaimingModes.SERVER.getId()));

            s.useTeamSubClaim();
            CompoundTag team = s.only(s.capture(s::sync), "the sync in team mode");
            assertMode(helper, team, player, 3, 7, 1, 3, "the team numbers");
            assertAnchors(helper, team, player, 2, anchorLimit, anchorRange, "the player's anchors in team mode");
            s.useMainSubClaim();

            TeamClaimsAnchorTestCases.setAnchorBasedClaiming(false);
            CompoundTag off = s.only(s.capture(s::sync), "the sync with anchor-based claiming off");
            helper.assertTrue(!off.getBoolean("ac"), "expected the packet to tell that anchor-based claiming is off");
            assertAnchors(helper, off, player, 2, anchorLimit, anchorRange, "the player's anchors with the option off");

            // What a client makes of the packet: the same limits as the builder returns
            ClaimingModeLimits expected = s.t.playerLimits();
            ClaimingModeLimits decoded = decodedLimitsOf(s.capturePackets(s::sync), ClaimingModes.PLAYER.getId());
            helper.assertTrue(expected.equals(decoded), "expected the decoded packet to carry the limits of the builder (claims " + expected.claimCount
                    + "/" + expected.claimLimit + ", anchors " + expected.anchorCount + "/" + expected.anchorLimit + " range " + expected.anchorRange
                    + "), got " + (decoded == null ? null : "claims " + decoded.claimCount + "/" + decoded.claimLimit + ", anchors "
                    + decoded.anchorCount + "/" + decoded.anchorLimit + " range " + decoded.anchorRange));
            helper.succeed();
        } finally {
            TeamClaimsAnchorTestCases.setAnchorBasedClaiming(anchorsBefore);
            s.close();
        }
    }

    /**
     * New packets after changes, in team mode: a team claim of another member reaches the player with the batched sync of
     * the end of the tick, with nothing changed no packet is sent (neither by that nor by OPAC's once-per-second check),
     * a change nobody announces (here the budget settings) arrives with the once-per-second check, and a member who joins
     * changes the limits that arrive. A claim of the player's own is announced by OPAC at once, in team mode and in
     * private mode (what the player ends up with after the end of the tick is right), with nothing left for the rest.
     */
    public static void claimLimitsPacketFollowsClaimsAndBudgetChanges(GameTestHelper helper, RecorderFactory recorders) {
        Scene s = Scene.create(helper, recorders, "T133", 133000);
        try {
            int x0 = s.t.x0;
            s.t.cm.setBudgetSettingsOverride(s.t.party.getId(), new BudgetSettings(2, 7, 5, 3, 1, 168));
            s.makeClaims();
            String player = ClaimingModes.PLAYER.getId();
            s.useTeamSubClaim();
            s.capture(s::tick);//what the setup marked for the end of the tick is out of the way
            assertMode(helper, s.only(s.capture(s::sync), "the baseline"), player, 3, 7, 1, 3, "the team numbers to start with");

            // A team claim of another member: the batched sync of the end of the tick
            s.t.claim(s.ownerId, s.t.teamSub(s.ownerId), x0 + 6, 0, false);
            assertMode(helper, s.only(s.capture(s::tick), "the end of the tick after a teammate's team claim"), player, 4, 7, 1, 3,
                    "the team numbers after a teammate's team claim");
            // Nothing changed: nothing sent
            helper.assertTrue(s.capture(s::tick).isEmpty(), "expected no packet from the end of the tick when nothing changed");
            helper.assertTrue(s.capture(s::check).isEmpty(), "expected no packet from the once-per-second check when nothing changed");

            // The budget settings change without anybody telling Team Claims: found by OPAC's own check
            s.t.cm.setBudgetSettingsOverride(s.t.party.getId(), new BudgetSettings(2, 9, 5, 3, 1, 168));
            helper.assertTrue(s.capture(s::tick).isEmpty(), "expected no packet from the end of the tick for a change nobody announced");
            assertMode(helper, s.only(s.capture(s::check), "the once-per-second check after the new budget settings"), player, 4, 9, 1, 3,
                    "the team numbers after the budget settings changed");

            // A member joins: the limits for three members
            GameProfile joiner = profile("T133_Joiner");
            helper.assertTrue(s.t.party.addMember(joiner.getId(), PartyMemberRank.MEMBER, joiner.getName()) != null, "expected party.addMember to succeed");
            TeamClaimsCommon.getTeamConfigManager().processPendingEvents();
            List<CompoundTag> afterJoin = s.capture(s::tick);
            helper.assertTrue(!afterJoin.isEmpty(), "expected a packet at the end of the tick after a member joined");
            assertMode(helper, afterJoin.get(afterJoin.size() - 1), player, 4, 14, 1, 4, "the last team numbers after a third member joined");

            // A claim of the player's own: OPAC sends the limits of the claiming player at once (observed: the count of that first packet
            // is still the one from before the claim was tracked as a team claim, the batched sync of the end of the tick corrects it). What the player ends up with
            // after the end of the tick are the numbers with that claim
            List<CompoundTag> own = s.capture(() -> {
                s.t.claim(s.playerId, s.t.teamSub(s.playerId), x0 + 8, 0, false);
                s.tick();
            });
            helper.assertTrue(!own.isEmpty(), "expected a packet after the player's own team claim");
            assertMode(helper, own.get(own.size() - 1), player, 5, 14, 1, 4, "the last team numbers after the player's own team claim");
            helper.assertTrue(s.capture(s::check).isEmpty(), "expected the once-per-second check to find nothing new after that");

            // Private mode: the player's own private claim is announced at once as well, nothing is left for the other ways
            s.useMainSubClaim();
            s.capture(s::tick);
            assertMode(helper, s.only(s.capture(s::sync), "the baseline in private mode"), player, 2, claimsAPI(s.server).getPlayerFullClaimLimit(s.playerId), 1,
                    claimsAPI(s.server).getPlayerFullForceloadLimit(s.playerId), "the private numbers to start with");
            List<CompoundTag> found = s.capture(() -> s.t.claim(s.playerId, -1, x0 + 7, 0, false));
            assertMode(helper, s.only(found, "the player's own private claim"), player, 3, claimsAPI(s.server).getPlayerFullClaimLimit(s.playerId), 1,
                    claimsAPI(s.server).getPlayerFullForceloadLimit(s.playerId), "the private numbers after a private claim");
            helper.assertTrue(s.capture(s::tick).isEmpty() && s.capture(s::check).isEmpty(),
                    "expected neither the end of the tick nor the once-per-second check to have anything left to send");
            helper.succeed();
        } finally {
            s.close();
        }
    }

    /**
     * The PARTY mode (OPAC's own party claims, option {@code partyOwnedClaims}) shows the private numbers of the party
     * owner, not their team claims, which have a budget of their own; the PLAYER mode is the player's as before.
     */
    public static void claimLimitsPacketOfThePartyModeLeavesOutTeamClaims(GameTestHelper helper, RecorderFactory recorders) {
        Scene s = Scene.create(helper, recorders, "T134", 134000);
        boolean partyClaimsBefore = ServerConfig.CONFIG.partyOwnedClaims.get();
        try {
            s.t.cm.setBudgetSettingsOverride(s.t.party.getId(), new BudgetSettings(2, 7, 5, 3, 1, 168));
            s.makeClaims();
            s.t.claim(s.ownerId, -1, s.t.x0 + 5, 0, true);//a forceloaded private claim of the owner
            setPartyOwnedClaims(true);
            int ownerClaimLimit = claimsAPI(s.server).getPlayerFullClaimLimit(s.ownerId);
            int ownerForceloadLimit = claimsAPI(s.server).getPlayerFullForceloadLimit(s.ownerId);
            int playerClaimLimit = claimsAPI(s.server).getPlayerFullClaimLimit(s.playerId);
            int playerForceloadLimit = claimsAPI(s.server).getPlayerFullForceloadLimit(s.playerId);

            CompoundTag wire = s.only(s.capture(s::sync), "the sync with party-owned claims on");
            assertMode(helper, wire, ClaimingModes.PARTY.getId(), 1, ownerClaimLimit, 1, ownerForceloadLimit,
                    "the owner's private numbers (their two team claims are not among them)");
            assertMode(helper, wire, ClaimingModes.PLAYER.getId(), 2, playerClaimLimit, 1, playerForceloadLimit, "the player's private numbers");
            helper.succeed();
        } finally {
            setPartyOwnedClaims(partyClaimsBefore);
            s.close();
        }
    }

    /**
     * A player in no team: the PLAYER mode carries all their claims against their limits, the PARTY and SERVER modes
     * nothing, and the client counts the synced claims itself ({@code a} not set) as long as the server synchronizes the
     * claims at all; if it does not ({@code claimsSynchronization = NOT_SYNCED}) the client has to use the server's
     * numbers for everybody.
     */
    public static void claimLimitsPacketOfAPlayerWithoutATeam(GameTestHelper helper, RecorderFactory recorders) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID playerId = player.getUUID();
        int x0 = 135000;
        ServerConfig.ClaimsSyncType syncBefore = ServerConfig.CONFIG.claimsSynchronization.get();
        try {
            claimsAPI(server).claim(OVERWORLD, playerId, -1, x0, 0, true);
            claimsAPI(server).claim(OVERWORLD, playerId, -1, x0 + 1, 0, false);
            int claimLimit = claimsAPI(server).getPlayerFullClaimLimit(playerId);
            int forceloadLimit = claimsAPI(server).getPlayerFullForceloadLimit(playerId);
            Scene s = new Scene(helper, recorders, null, player);

            CompoundTag wire = s.only(s.capture(s::sync), "the sync of a player without a team");
            assertMode(helper, wire, ClaimingModes.PLAYER.getId(), 2, claimLimit, 1, forceloadLimit, "the player's numbers");
            assertMode(helper, wire, ClaimingModes.PARTY.getId(), 0, 0, 0, 0, "the party mode without party-owned claims");
            helper.assertTrue(!wire.getBoolean("a"), "expected the client to count the synced claims itself for a player without a team");

            setClaimsSynchronization(ServerConfig.ClaimsSyncType.NOT_SYNCED);
            CompoundTag notSynced = s.only(s.capture(s::sync), "the sync without claims synchronization");
            helper.assertTrue(notSynced.getBoolean("a"), "expected the client to be told to use the server's numbers when no claims are synced");
            assertMode(helper, notSynced, ClaimingModes.PLAYER.getId(), 2, claimLimit, 1, forceloadLimit, "the player's numbers without claims synchronization");
            helper.succeed();
        } finally {
            setClaimsSynchronization(syncBefore);
            unclaimQuiet(server, x0, 0, x0 + 1, 0);
            removePlayerQuiet(server, player);
        }
    }

    /**
     * {@link ServerPlayerConfigUtils#isOverEitherClaimingLimit}: a player over their private claim limit is over (at the
     * limit is not over), and the own team claims do not count towards it, however many there are; a team above its
     * team claim limit does not make its members over, the team has its own over-limit handling; a player with more
     * claiming anchors than their limit in a dimension is over in that dimension (and for the call without a
     * dimension), not in another one; a player who is within everything is not over.
     */
    public static void isOverEitherClaimingLimitForPrivateTeamAndNeither(GameTestHelper helper) {
        Team t = Team.create(helper, "T136", 136000);
        int x0 = t.x0;
        try {
            t.cm.setBudgetSettingsOverride(t.party.getId(), new BudgetSettings(1, 100, 0, 10, 0, 168));
            IPlayerConfig playerConfig = (IPlayerConfig) (Object) configManager(t.server).getLoadedConfig(t.playerId);
            IPlayerConfig ownerConfig = (IPlayerConfig) (Object) configManager(t.server).getLoadedConfig(t.ownerId);

            // Neither: private claims exactly at the limit (2 of 2), plenty of team claims on top
            TeamClaimsBudgetTestCases.setPrivateLimit(helper, t.server, t.playerId, PlayerConfigOptions.BONUS_CHUNK_CLAIMS, 2);
            t.claim(t.playerId, -1, x0, 0, false);
            t.claim(t.playerId, -1, x0 + 1, 0, false);
            for (int i = 0; i < 3; i++) t.claim(t.playerId, t.teamSub(t.playerId), x0 + 10 + i, 0, false);
            t.claim(t.ownerId, t.teamSub(t.ownerId), x0 + 20, 0, false);
            helper.assertTrue(!ServerPlayerConfigUtils.isOverEitherClaimingLimit(playerConfig),
                    "expected a player exactly at their private claim limit, with 3 team claims on top, not to be over a limit");
            helper.assertTrue(!ServerPlayerConfigUtils.isOverEitherClaimingLimit(ownerConfig),
                    "expected the owner within their limits not to be over a limit");

            // Over the private limit
            t.claim(t.playerId, -1, x0 + 2, 0, false);
            helper.assertTrue(ServerPlayerConfigUtils.isOverEitherClaimingLimit(playerConfig)
                            && ServerPlayerConfigUtils.isOverEitherClaimingLimit(playerConfig, OVERWORLD),
                    "expected a player with 3 private claims and a private claim limit of 2 to be over a limit");
            helper.assertTrue(!ServerPlayerConfigUtils.isOverEitherClaimingLimit(ownerConfig),
                    "expected the owner not to be over a limit because of a teammate's private claims");
            TeamClaimsBudgetTestCases.setPrivateLimit(helper, t.server, t.playerId, PlayerConfigOptions.BONUS_CHUNK_CLAIMS, 3);
            helper.assertTrue(!ServerPlayerConfigUtils.isOverEitherClaimingLimit(playerConfig),
                    "expected the player not to be over a limit any more once the limit reaches their claim count");

            // The team over its own limit: nobody's private count is involved
            t.cm.setBudgetSettingsOverride(t.party.getId(), new BudgetSettings(1, 1, 0, 10, 0, 168));
            helper.assertTrue(t.cm.getTeamBudget(t.party.getId()).claimsOverLimit() > 0,
                    "test setup: expected the team to be over its team claim limit, got " + t.cm.getTeamBudget(t.party.getId()));
            helper.assertTrue(!ServerPlayerConfigUtils.isOverEitherClaimingLimit(playerConfig)
                            && !ServerPlayerConfigUtils.isOverEitherClaimingLimit(ownerConfig),
                    "expected a team over its team claim limit not to make its members over their claiming limits");

            // Anchors: more than the limit in the overworld
            TeamClaimsBudgetTestCases.resetBonusQuiet(t.server, t.playerId);
            int anchorLimit = claimsAPI(t.server).getPlayerFullAnchorLimit(t.playerId, OVERWORLD);
            helper.assertTrue(anchorLimit >= 0 && anchorLimit <= 10 && claimsAPI(t.server).getPlayerFullClaimLimit(t.playerId) > anchorLimit + 5,
                    "test setup: expected a small anchor limit and room for the claims of the anchors, got " + anchorLimit);
            for (int i = 0; i <= anchorLimit; i++) {
                helper.assertTrue(claimsAPI(t.server).addAnchor(OVERWORLD, new ChunkPos(x0 + 100 + 40 * i, 0), t.playerId),
                        "test setup: expected an anchor to be added");
            }
            helper.assertTrue(ServerPlayerConfigUtils.isOverEitherClaimingLimit(playerConfig)
                            && ServerPlayerConfigUtils.isOverEitherClaimingLimit(playerConfig, OVERWORLD),
                    "expected a player with " + (anchorLimit + 1) + " anchors and an anchor limit of " + anchorLimit + " to be over a limit");
            helper.assertTrue(!ServerPlayerConfigUtils.isOverEitherClaimingLimit(playerConfig, NETHER),
                    "expected the player not to be over a limit in the nether, where they have no anchors");
            helper.assertTrue(!ServerPlayerConfigUtils.isOverEitherClaimingLimit(ownerConfig),
                    "expected the owner not to be over a limit because of a teammate's anchors");
            for (int i = 0; i <= anchorLimit; i++) {
                claimsAPI(t.server).removeAnchor(OVERWORLD, new ChunkPos(x0 + 100 + 40 * i, 0), t.playerId);
            }
            helper.assertTrue(!ServerPlayerConfigUtils.isOverEitherClaimingLimit(playerConfig),
                    "expected the player not to be over a limit once the anchors are gone");
            helper.succeed();
        } finally {
            List<Integer> chunks = new ArrayList<>();
            for (int dx : new int[]{0, 1, 2, 10, 11, 12, 20}) {
                chunks.add(x0 + dx);
                chunks.add(0);
            }
            for (int i = 0; i <= 10; i++) {
                chunks.add(x0 + 100 + 40 * i);
                chunks.add(0);
            }
            t.cleanup(chunks.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    // ==================== Helpers ====================

    private static void setPartyOwnedClaims(boolean enabled) {
        ServerConfig.CONFIG.partyOwnedClaims.set(enabled);
        ServerConfig.CONFIG.partyOwnedClaims.clearCache();
    }

    private static void setClaimsSynchronization(ServerConfig.ClaimsSyncType type) {
        ServerConfig.CONFIG.claimsSynchronization.set(type);
        ServerConfig.CONFIG.claimsSynchronization.clearCache();
    }

    /** An int from a mode's wire tag; the limits that are left out are unlimited. */
    private static int number(CompoundTag modeTag, String key) {
        return modeTag.contains(key, Tag.TAG_INT) ? modeTag.getInt(key) : Integer.MAX_VALUE;
    }

    /** The claim and forceload numbers of a mode in a packet. */
    private static void assertMode(GameTestHelper helper, CompoundTag wire, String mode, int claims, int claimLimit, int forceloads,
            int forceloadLimit, String what) {
        CompoundTag tag = wire.getCompound("l").getCompound(mode);
        helper.assertTrue(tag.getInt("cc") == claims && number(tag, "cl") == claimLimit && tag.getInt("fc") == forceloads
                        && number(tag, "fl") == forceloadLimit,
                "expected " + what + " in the " + mode + " mode to be claims " + claims + " / " + claimLimit + " and forceloads " + forceloads
                        + " / " + forceloadLimit + ", got claims " + tag.getInt("cc") + " / " + number(tag, "cl") + " and forceloads "
                        + tag.getInt("fc") + " / " + number(tag, "fl"));
    }

    /** The anchor numbers of a mode in a packet. */
    private static void assertAnchors(GameTestHelper helper, CompoundTag wire, String mode, int count, int limit, int range, String what) {
        CompoundTag tag = wire.getCompound("l").getCompound(mode);
        helper.assertTrue(tag.getInt("ac") == count && number(tag, "al") == limit && number(tag, "ar") == range,
                "expected " + what + " in the " + mode + " mode to be " + count + " anchors, limit " + limit + ", range " + range + ", got "
                        + tag.getInt("ac") + ", " + number(tag, "al") + ", " + number(tag, "ar"));
    }

    /** What a client reads from the packet: the limits of one mode, decoded with the packet's own decoder. */
    private static ClaimingModeLimits decodedLimitsOf(List<ClientboundClaimLimitsPacket> sent, String modeId) {
        if (sent.size() != 1) throw new IllegalStateException("expected one claim limits packet to decode, got " + sent.size());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundClaimLimitsPacket.ENCODER.accept(sent.get(0), buffer);
        ClientboundClaimLimitsPacket decoded = ClientboundClaimLimitsPacket.DECODER.apply(buffer);
        if (decoded == null) throw new IllegalStateException("expected the claim limits packet to decode");
        try {
            Field field = ClientboundClaimLimitsPacket.class.getDeclaredField("limits");
            field.setAccessible(true);
            Collection<?> limits = (Collection<?>) field.get(decoded);
            for (Object entry : limits) {
                ClaimingModeLimits modeLimits = (ClaimingModeLimits) entry;
                if (modeLimits.mode.getId().equals(modeId)) return modeLimits;
            }
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not read the decoded limits", e);
        }
    }

    /** The packet as it goes over the network, read as NBT. */
    private static CompoundTag wireOf(ClientboundClaimLimitsPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundClaimLimitsPacket.ENCODER.accept(packet, buffer);
        return (CompoundTag) buffer.readNbt(NbtAccounter.unlimitedHeap());
    }

    /**
     * A team with the mock player as a member (see {@link Team}) whose claim limits packets are watched. The claim limits
     * sync is triggered the way the server does it, then OPAC's lazy packet queue is run until the player has everything.
     */
    private static final class Scene {
        final GameTestHelper helper;
        final MinecraftServer server;
        final RecorderFactory recorders;
        final Team t;
        final ServerPlayer player;
        final UUID playerId;
        final UUID ownerId;
        final ServerClaimsManager claimsManager;
        final LazyPacketSender lazySender;

        Scene(GameTestHelper helper, RecorderFactory recorders, Team t, ServerPlayer player) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.recorders = recorders;
            this.t = t;
            this.player = player;
            this.playerId = player.getUUID();
            this.ownerId = t == null ? null : t.ownerId;
            this.claimsManager = (ServerClaimsManager) (Object) ServerData.from(server).getServerClaimsManager();
            this.lazySender = ServerData.from(server).getServerTickHandler().getLazyPacketSender();
        }

        static Scene create(GameTestHelper helper, RecorderFactory recorders, String name, int x0) {
            Team t = Team.create(helper, name, x0);
            return new Scene(helper, recorders, t, t.player);
        }

        /**
         * The claims of the numbers the tests look at: 2 private claims of the player (the first forceloaded), a team claim
         * of the player, a forceloaded team claim of the owner, another team claim of the owner, and a private claim of the
         * owner, which counts for nobody else. In private mode that makes 2 claims and 1 forceload, in team mode 3 and 1.
         */
        void makeClaims() {
            int x0 = t.x0;
            t.claim(playerId, -1, x0, 0, true);
            t.claim(playerId, -1, x0 + 1, 0, false);
            t.claim(playerId, t.teamSub(playerId), x0 + 2, 0, false);
            t.claim(ownerId, t.teamSub(ownerId), x0 + 3, 0, true);
            t.claim(ownerId, t.teamSub(ownerId), x0 + 4, 0, false);
            t.claim(ownerId, -1, x0 + 5, 0, false);
        }

        void setBonus(xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI<Integer> option, int value) {
            IPlayerConfigAPI.SetResult result = configManager(server).getLoadedConfig(playerId).tryToSet(option, value);
            helper.assertTrue(result == IPlayerConfigAPI.SetResult.SUCCESS, "test setup: expected " + option.getId() + " = " + value + " to be accepted, got " + result);
        }

        void useTeamSubClaim() {
            IPlayerConfigAPI.SetResult used = configManager(server).getLoadedConfig(playerId).tryToSet(PlayerConfigOptions.USED_SUBCLAIM, t.teamSubId());
            helper.assertTrue(used == IPlayerConfigAPI.SetResult.SUCCESS, "test setup: expected selecting the team sub-claim to work, got " + used);
        }

        void useMainSubClaim() {
            configManager(server).getLoadedConfig(playerId).tryToReset(PlayerConfigOptions.USED_SUBCLAIM);
        }

        // ---- the ways the server syncs the claim limits ----

        /** OPAC's own sync, as after a login or a changed config. */
        void sync() {
            claimsManager.getClaimsManagerSynchronizer().syncClaimLimits((IPlayerConfig) (Object) configManager(server).getLoadedConfig(playerId), player);
        }

        /** The batched sync Team Claims does at the end of every tick for the players it marked. */
        void tick() {
            TeamClaimsCommon.getClaimManager().tick();
        }

        /** OPAC's check of the limits, which it makes once a second for every player and sends a packet if they changed. */
        void check() {
            ServerPlayerData playerData = (ServerPlayerData) ServerPlayerData.from(player);
            playerData.setLastClaimLimitsCheckTime(0);
            claimsManager.getClaimsManagerSynchronizer().updateClaimLimitsSyncOnTick(playerData, player);
        }

        /** Runs the lazy packet queue (confirming what the player would confirm) until everything is sent to the player. */
        private void drain() {
            for (int i = 0; i < 20; i++) {
                lazySender.onConfirmation(player);
                lazySender.onServerTick();
            }
        }

        /**
         * The claim limits packets the player receives from {@code trigger}: everything that was queued for the player
         * before (their login, the setup of the test) is sent and dropped first.
         */
        List<ClientboundClaimLimitsPacket> capturePackets(Runnable trigger) {
            TeamClaimsPacketTestCases.capture(server, player, recorders, this::drain);
            List<Object> sent = TeamClaimsPacketTestCases.capture(server, player, recorders, () -> {
                trigger.run();
                drain();
            });
            List<ClientboundClaimLimitsPacket> limits = new ArrayList<>();
            for (Object packet : sent) if (packet instanceof ClientboundClaimLimitsPacket p) limits.add(p);
            return limits;
        }

        List<CompoundTag> capture(Runnable trigger) {
            return capturePackets(trigger).stream().map(TeamClaimsLimitsPacketTestCases::wireOf).toList();
        }

        CompoundTag only(List<CompoundTag> sent, String when) {
            helper.assertTrue(sent.size() == 1, "expected exactly one claim limits packet from " + when + ", got " + sent.size());
            return sent.get(0);
        }

        /** Unclaims the chunks of the test and removes everything it made; never throws. */
        void close() {
            int x0 = t.x0;
            int[] chunks = new int[2 * 12];
            for (int i = 0; i < 12; i++) {
                chunks[2 * i] = x0 + i;
                chunks[2 * i + 1] = 0;
            }
            try {
                configManager(server).getLoadedConfig(playerId).tryToReset(PlayerConfigOptions.USED_SUBCLAIM);
                configManager(server).getLoadedConfig(playerId).tryToReset(PlayerConfigOptions.BONUS_CLAIMING_ANCHORS);
                configManager(server).getLoadedConfig(playerId).tryToReset(PlayerConfigOptions.BONUS_CLAIMING_ANCHOR_RANGE);
            } catch (Exception ignored) {
            }
            t.cleanup(chunks);
        }
    }
}
