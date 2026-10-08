package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.ParseResults;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import xaero.pac.common.claims.player.IPlayerChunkClaim;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.claims.player.ServerPlayerClaimWelcomer;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.data.ServerPlayerData;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimManager.TeamClaimSavedData;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.config.TeamClaimsServerConfig;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.localized;
import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;

/**
 * Dev/test-only, loader-neutral test bodies for the team-aware claim welcome messages and the per-player
 * {@code /teamclaims territorymessages} toggle. Same conventions as {@link TeamClaimsLogicTestCases} (offline players,
 * a chunk offset of 1000 per test, cleanup on every path, every test registered with the loader's empty structure
 * template and the default timeout).
 * <p>
 * The welcomer tests use the vanilla mock player ({@link GameTestHelper#makeMockServerPlayerInLevel}), which joins the
 * server through the normal login path. Its packet listener is replaced by one that records what would have been sent
 * to the client, the player is moved from chunk to chunk and OPAC's own {@link ServerPlayerClaimWelcomer} is run for
 * it, exactly as OPAC's player tick does. Everything happens in the one tick of the test, so the real tick never runs for
 * the mock player, and it is removed again at the end.
 */
public final class TeamClaimsTerritoryTestCases {

    private TeamClaimsTerritoryTestCases() {}

    // ==================== Tests ====================

    /**
     * The decision of the bridge hook: two team claims of different members of one team (in the same dimension) are one
     * territory, and nothing else is: team vs personal claim, two different teams, claim vs nothing, and the same claims
     * in two different dimensions.
     */
    public static void sameTeamTerritoryDecision(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T20_Owner");
        GameProfile memberProfile = profile("T20_Member");
        GameProfile otherOwnerProfile = profile("T20_OtherOwner");
        IServerPartyAPI party = null;
        IServerPartyAPI otherParty = null;
        int x0 = 20000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            otherParty = createPartyWithTeam(server, otherOwnerProfile, List.of(profile("T20_OtherMember")));//2 members to make team claims
            claim(helper, server, party.getId(), ownerProfile, true, x0);
            claim(helper, server, party.getId(), memberProfile, true, x0 + 1);
            claim(helper, server, party.getId(), ownerProfile, false, x0 + 2);
            claim(helper, server, otherParty.getId(), otherOwnerProfile, true, x0 + 3);
            IPlayerChunkClaim ownerTeam = claimAt(server, x0);
            IPlayerChunkClaim memberTeam = claimAt(server, x0 + 1);
            IPlayerChunkClaim ownerPersonal = claimAt(server, x0 + 2);
            IPlayerChunkClaim otherTeam = claimAt(server, x0 + 3);

            TeamClaimsIntegration.TeamClaimsHandler handler = TeamClaimsIntegration.getHandler();
            ResourceKey<Level> overworld = Level.OVERWORLD;
            helper.assertTrue(handler.isSameTeamTerritory(ownerTeam, overworld, memberTeam, overworld),
                    "expected the team claims of two members of one team to be one territory");
            helper.assertTrue(handler.isSameTeamTerritory(memberTeam, overworld, ownerTeam, overworld),
                    "expected the same the other way around");
            helper.assertTrue(!handler.isSameTeamTerritory(ownerTeam, overworld, ownerPersonal, overworld),
                    "expected a team claim and a personal claim of the same player not to be one territory");
            helper.assertTrue(!handler.isSameTeamTerritory(ownerPersonal, overworld, memberTeam, overworld),
                    "expected a personal claim and a team claim not to be one territory");
            helper.assertTrue(!handler.isSameTeamTerritory(ownerPersonal, overworld, ownerPersonal, overworld),
                    "expected two personal claims never to be a team territory");
            helper.assertTrue(!handler.isSameTeamTerritory(ownerTeam, overworld, otherTeam, overworld),
                    "expected the team claims of two different teams not to be one territory");
            helper.assertTrue(!handler.isSameTeamTerritory(ownerTeam, overworld, null, overworld),
                    "expected a team claim and no claim not to be one territory");
            helper.assertTrue(!handler.isSameTeamTerritory(null, overworld, memberTeam, overworld),
                    "expected no claim and a team claim not to be one territory");
            helper.assertTrue(!handler.isSameTeamTerritory(null, null, null, overworld),
                    "expected no claim at all not to be a team territory");
            helper.assertTrue(!handler.isSameTeamTerritory(ownerTeam, Level.NETHER, memberTeam, overworld),
                    "expected the same team's claims in two different dimensions not to be one territory");
            helper.assertTrue(!handler.isSameTeamTerritory(ownerTeam, null, memberTeam, overworld),
                    "expected a claim without a known dimension not to be one territory");
            helper.succeed();
        } finally {
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 3, 0);
            if (party != null) disbandPartyQuiet(server, party.getId());
            if (otherParty != null) disbandPartyQuiet(server, otherParty.getId());
        }
    }

    /**
     * Without a choice a player follows {@code territoryMessagesDefault}, read at use time; an explicit choice wins over
     * it whichever way it is, and is not stored when there is none.
     */
    public static void territoryMessagesDefaultFollowsConfig(GameTestHelper helper) {
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamClaimsIntegration.TeamClaimsHandler handler = TeamClaimsIntegration.getHandler();
        UUID playerId = UUID.randomUUID();
        var config = TeamClaimsServerConfig.CONFIG.territoryMessagesDefault;
        helper.assertTrue(config.getDefault(), "expected 'territoryMessagesDefault' to default to true");
        try {
            config.set(true);
            helper.assertTrue(cm.areTerritoryMessagesEnabled(playerId) && !handler.areTerritoryMessagesSuppressed(playerId),
                    "expected the messages to be on for a player without a choice while the default is on");
            config.set(false);
            helper.assertTrue(!cm.areTerritoryMessagesEnabled(playerId) && handler.areTerritoryMessagesSuppressed(playerId),
                    "expected the messages to be off for a player without a choice while the default is off");
            helper.assertTrue(cm.getTerritoryMessagesChoice(playerId) == null,
                    "expected reading the default not to store a choice");

            cm.setTerritoryMessagesEnabled(playerId, true);
            helper.assertTrue(cm.areTerritoryMessagesEnabled(playerId) && !handler.areTerritoryMessagesSuppressed(playerId),
                    "expected an explicit 'on' to win over a default of off");
            config.set(true);
            cm.setTerritoryMessagesEnabled(playerId, false);
            helper.assertTrue(!cm.areTerritoryMessagesEnabled(playerId) && handler.areTerritoryMessagesSuppressed(playerId),
                    "expected an explicit 'off' to win over a default of on");
            cm.clearTerritoryMessagesChoice(playerId);
            helper.assertTrue(cm.areTerritoryMessagesEnabled(playerId),
                    "expected the player to follow the default again without a choice");
            helper.succeed();
        } finally {
            cm.clearTerritoryMessagesChoice(playerId);
            config.set(config.getDefault());
        }
    }

    /**
     * The choices are stored in the saved data of Team Claims, mark it dirty when they change, survive a save/load
     * round trip, are only written once there is one, and a save without them (from before the toggle) loads unchanged.
     */
    public static void territoryMessageChoicesArePersisted(GameTestHelper helper) {
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        HolderLookup.Provider provider = helper.getLevel().registryAccess();
        UUID off = UUID.randomUUID();
        UUID on = UUID.randomUUID();
        try {
            TeamClaimSavedData live = cm.getSavedData();
            helper.assertTrue(live != null, "expected the Team Claims saved data to be loaded");
            cm.setTerritoryMessagesEnabled(off, false);
            cm.setTerritoryMessagesEnabled(on, true);
            helper.assertTrue(live.isDirty(), "expected a choice to mark the saved data dirty");
            helper.assertTrue(Boolean.FALSE.equals(cm.getTerritoryMessagesChoice(off))
                            && Boolean.TRUE.equals(cm.getTerritoryMessagesChoice(on)),
                    "expected both choices to be readable back");

            TeamClaimSavedData loaded = TeamClaimSavedData.load(live.save(new CompoundTag(), provider), provider);
            helper.assertTrue(Boolean.FALSE.equals(loaded.getTerritoryMessagesChoice(off)),
                    "expected the 'off' choice to survive the save/load round trip, got "
                            + loaded.getTerritoryMessagesChoice(off));
            helper.assertTrue(Boolean.TRUE.equals(loaded.getTerritoryMessagesChoice(on)),
                    "expected the 'on' choice to survive the save/load round trip, got "
                            + loaded.getTerritoryMessagesChoice(on));
            helper.assertTrue(loaded.getTerritoryMessagesChoice(UUID.randomUUID()) == null,
                    "expected a player without a choice to have none after loading");

            // Fresh data: nothing is written without a choice, and an unchanged choice doesn't dirty it
            TeamClaimSavedData fresh = new TeamClaimSavedData();
            CompoundTag freshTag = fresh.save(new CompoundTag(), provider);
            helper.assertTrue(!freshTag.contains("territoryMessages"),
                    "expected no territory messages entry to be written while there is no choice");
            helper.assertTrue(!fresh.isDirty(), "expected fresh saved data not to be dirty");
            fresh.setTerritoryMessagesChoice(off, false);
            helper.assertTrue(fresh.isDirty(), "expected a new choice to mark the saved data dirty");
            fresh.setDirty(false);
            fresh.setTerritoryMessagesChoice(off, false);
            helper.assertTrue(!fresh.isDirty(), "expected repeating a choice not to mark the saved data dirty");
            fresh.setTerritoryMessagesChoice(off, true);
            helper.assertTrue(fresh.isDirty(), "expected changing a choice to mark the saved data dirty");

            // A save from before the toggle: only the "teams" list
            CompoundTag oldSave = new CompoundTag();
            oldSave.put("teams", new ListTag());
            TeamClaimSavedData oldLoaded = TeamClaimSavedData.load(oldSave, provider);
            helper.assertTrue(oldLoaded.getTerritoryMessagesChoice(off) == null,
                    "expected a save without the territory messages list to load without choices");
            helper.succeed();
        } finally {
            cm.clearTerritoryMessagesChoice(off);
            cm.clearTerritoryMessagesChoice(on);
        }
    }

    /**
     * OPAC's welcomer with a player walking over a team's land: no message between claims of the same team, whoever of
     * the members claimed them, a message when entering or leaving the land, entering another team's land, a personal
     * claim or the wilderness. Without the Team Claims handler the stock behaviour is back: the same walk shows a
     * message at every member boundary. {@code lastClaimCheck} is kept up to date throughout.
     */
    public static void welcomerShowsOneMessagePerTeamTerritory(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T21_Owner");
        GameProfile memberProfile = profile("T21_Member");
        GameProfile otherOwnerProfile = profile("T21_OtherOwner");
        IServerPartyAPI party = null;
        IServerPartyAPI otherParty = null;
        WelcomeSetup welcome = null;
        int x0 = 21000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(memberProfile));
            otherParty = createPartyWithTeam(server, otherOwnerProfile, List.of(profile("T21_OtherMember")));//2 members to make team claims
            claim(helper, server, party.getId(), ownerProfile, true, x0);                 // team A, owner
            claim(helper, server, party.getId(), memberProfile, true, x0 + 1);            // team A, member
            claim(helper, server, party.getId(), ownerProfile, true, x0 + 2);             // team A, owner again
            claim(helper, server, party.getId(), ownerProfile, false, x0 + 3);            // personal claim
            claim(helper, server, otherParty.getId(), otherOwnerProfile, true, x0 + 4);   // team B
            helper.assertTrue(ServerConfig.CONFIG.claimWelcomeMessages.get(), "expected OPAC's claimWelcomeMessages option to be on");
            welcome = WelcomeSetup.create(helper);

            welcome.walkTo(x0, 1, "wilderness to team A");
            welcome.walkTo(x0 + 1, 0, "owner's to member's team claim");
            welcome.walkTo(x0 + 2, 0, "member's to owner's team claim");
            welcome.walkTo(x0, 0, "back to the first team claim");
            welcome.walkTo(x0 + 3, 1, "team A to a personal claim");
            welcome.walkTo(x0, 1, "a personal claim to team A");
            welcome.walkTo(x0 + 4, 1, "team A to team B");
            welcome.walkTo(x0 + 5, 1, "team B to the wilderness");
            welcome.walkTo(x0 + 1, 1, "wilderness to team A again");
            welcome.walkTo(x0 + 1, 0, "standing still");

            // Stock behaviour (no handler): the very same move between two team claims of one team shows a message
            TeamClaimsIntegration.TeamClaimsHandler handler = TeamClaimsIntegration.getHandler();
            TeamClaimsIntegration.setHandler(null);
            try {
                welcome.walkTo(x0, 1, "owner's to member's team claim without Team Claims");
            } finally {
                TeamClaimsIntegration.setHandler(handler);
            }
            helper.succeed();
        } finally {
            if (welcome != null) welcome.close();
            unclaimQuiet(server, x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 3, 0, x0 + 4, 0);
            if (party != null) disbandPartyQuiet(server, party.getId());
            if (otherParty != null) disbandPartyQuiet(server, otherParty.getId());
        }
    }

    /**
     * {@code /teamclaims territorymessages} with and without an argument, run as a player who is in no party: reports
     * the state, sets the choice, and a player who turned the messages off gets none at all, team claims or not, while
     * the last claim check keeps following them, so turning them on again shows nothing stale.
     */
    public static void territoryMessagesCommandControlsWelcomer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("T22_Owner");
        IServerPartyAPI party = null;
        WelcomeSetup welcome = null;
        var defaultConfig = TeamClaimsServerConfig.CONFIG.territoryMessagesDefault;
        int x0 = 22000;
        try {
            party = createPartyWithTeam(server, ownerProfile, List.of(profile("T22_Member")));//2 members to make team claims
            claim(helper, server, party.getId(), ownerProfile, true, x0);
            claim(helper, server, party.getId(), ownerProfile, false, x0 + 1);
            helper.assertTrue(ServerConfig.CONFIG.claimWelcomeMessages.get(), "expected OPAC's claimWelcomeMessages option to be on");
            defaultConfig.set(true);
            welcome = WelcomeSetup.create(helper);
            UUID playerId = welcome.player.getUUID();
            TeamClaimManager cm = TeamClaimsCommon.getClaimManager();

            welcome.run("teamclaims territorymessages");
            welcome.assertSystemMessage(server, "gui.xaero_pac_team_claims_territory_messages_status_on");
            welcome.run("teamclaims territorymessages off");
            welcome.assertSystemMessage(server, "gui.xaero_pac_team_claims_territory_messages_off");
            helper.assertTrue(Boolean.FALSE.equals(cm.getTerritoryMessagesChoice(playerId)),
                    "expected 'off' to store an explicit choice");
            welcome.run("teamclaims territorymessages");
            welcome.assertSystemMessage(server, "gui.xaero_pac_team_claims_territory_messages_status_off");

            welcome.walkTo(x0, 0, "wilderness to a team claim with the messages off");
            welcome.walkTo(x0 + 1, 0, "a team claim to a personal claim with the messages off");
            welcome.walkTo(x0 + 2, 0, "a personal claim to the wilderness with the messages off");

            welcome.run("teamclaims territorymessages on");
            welcome.assertSystemMessage(server, "gui.xaero_pac_team_claims_territory_messages_on");
            helper.assertTrue(Boolean.TRUE.equals(cm.getTerritoryMessagesChoice(playerId)),
                    "expected 'on' to store an explicit choice");
            welcome.walkTo(x0 + 2, 0, "standing still after turning the messages on");
            welcome.walkTo(x0, 1, "wilderness to a team claim with the messages on");
            helper.succeed();
        } finally {
            if (welcome != null) {
                TeamClaimsCommon.getClaimManager().clearTerritoryMessagesChoice(welcome.player.getUUID());
                welcome.close();
            }
            defaultConfig.set(defaultConfig.getDefault());
            unclaimQuiet(server, x0, 0, x0 + 1, 0);
            if (party != null) disbandPartyQuiet(server, party.getId());
        }
    }

    /**
     * {@code /teamclaims territorymessages} is available to a player in no party and to one in a party (it needs
     * nothing but an active Team Claims, in particular not a party: it is not behind the {@code partiesEnabled}
     * requirement that {@code /teamclaims create} keeps, together with "not in a party yet").
     * <p>
     * The {@code partiesEnabled} option itself can't be flipped from a test: it is a {@code worldRestart()} value, whose
     * cached value {@code ConfigValue.set} does not change while the world runs.
     */
    public static void territoryMessagesCommandNeedsNoParty(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        WelcomeSetup welcome = null;
        IServerPartyAPI party = null;
        try {
            welcome = WelcomeSetup.create(helper);
            helper.assertTrue(welcome.canRun("teamclaims create Some Team"),
                    "expected /teamclaims create to be available to a player without a party");
            helper.assertTrue(welcome.canRun("teamclaims territorymessages"),
                    "expected /teamclaims territorymessages to be available to a player without a party");
            helper.assertTrue(welcome.canRun("teamclaims territorymessages off")
                            && welcome.canRun("teamclaims territorymessages on"),
                    "expected /teamclaims territorymessages on|off to be available to a player without a party");
            helper.assertTrue(!welcome.canRun("teamclaims territorymessages maybe"),
                    "expected /teamclaims territorymessages to take nothing but on or off");

            // Party sync packets go through the loader's network layer, which needs the real connection of the player
            welcome.player.connection = welcome.originalListener;
            party = createPartyWithTeam(server, welcome.player.getGameProfile(), List.of());
            helper.assertTrue(!welcome.canRun("teamclaims create Some Team"),
                    "expected /teamclaims create to be unavailable to a player in a party");
            helper.assertTrue(welcome.canRun("teamclaims territorymessages")
                            && welcome.canRun("teamclaims territorymessages off"),
                    "expected /teamclaims territorymessages to stay available to a player in a party");
            helper.succeed();
        } finally {
            if (welcome != null) {
                TeamClaimsCommon.getClaimManager().clearTerritoryMessagesChoice(welcome.player.getUUID());
                welcome.close();
            }
            if (party != null) disbandPartyQuiet(server, party.getId());
        }
    }

    // ==================== Helpers ====================

    /** A claim of the team (or a personal claim) of {@code profile} at chunk (x, 0); asserts that it succeeded. */
    private static void claim(GameTestHelper helper, MinecraftServer server, UUID partyId, GameProfile profile,
            boolean team, int x) {
        int subIndex = team ? teamSubIndexOf(server, TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId)
                .getSubConfigId(), profile.getId()) : -1;
        ClaimResult<?> result = doClaim(server, profile.getId(), subIndex, x, 0);
        helper.assertTrue(result.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM,
                "expected the " + (team ? "team" : "personal") + " claim of " + profile.getName() + " at chunk " + x
                        + " to succeed, got " + result.getResultType());
    }

    @Nullable
    private static IPlayerChunkClaim claimAt(MinecraftServer server, int x) {
        return (IPlayerChunkClaim) claimsAPI(server).get(OVERWORLD, x, 0);
    }

    /** Receives everything that would be sent to the mock player, instead of a network connection. */
    private static final class RecordingListener extends ServerGamePacketListenerImpl {
        final List<Packet<?>> sent = new ArrayList<>();

        RecordingListener(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet) {
            sent.add(packet);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            sent.add(packet);
        }
    }

    /**
     * A mock player on the server whose packets are recorded, plus OPAC's welcomer to run for it. {@link #close} removes
     * the player again.
     */
    private static final class WelcomeSetup {
        final GameTestHelper helper;
        final MinecraftServer server;
        final ServerPlayer player;
        final ServerGamePacketListenerImpl originalListener;
        final RecordingListener recorder;
        final ServerPlayerClaimWelcomer welcomer = new ServerPlayerClaimWelcomer();
        int lastChunkX = Integer.MIN_VALUE;

        private WelcomeSetup(GameTestHelper helper, ServerPlayer player) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.player = player;
            this.originalListener = player.connection;
            this.recorder = new RecordingListener(server, player);//also installs itself as player.connection
        }

        static WelcomeSetup create(GameTestHelper helper) {
            return new WelcomeSetup(helper, helper.makeMockServerPlayerInLevel());
        }

        void close() {
            try {
                player.connection = originalListener;
                server.getPlayerList().remove(player);
            } catch (Exception ignored) {
            }
        }

        private ServerPlayerData playerData() {
            return (ServerPlayerData) ServerPlayerData.from(player);
        }

        /**
         * Moves the player to chunk (x, 0), runs the welcomer once, and checks that it showed exactly
         * {@code expectedMessages} welcome messages (the first move starts from "no claim checked yet", so any claim
         * is new to the player then), and that the last claim check now is the claim there.
         */
        void walkTo(int x, int expectedMessages, String move) {
            player.setPos(x * 16 + 8, 64, 8);
            helper.assertTrue(player.chunkPosition().x == x && player.chunkPosition().z == 0,
                    "expected the mock player to be in chunk " + x + ", got " + player.chunkPosition());
            recorder.sent.clear();
            welcomer.onPlayerTick(playerData(), player, ServerData.from(server));
            long messages = recorder.sent.stream().filter(p -> p instanceof ClientboundSetActionBarTextPacket).count();
            helper.assertTrue(messages == expectedMessages,
                    "expected " + expectedMessages + " welcome message(s) for " + move + ", got " + messages);
            Object expectedClaim = claimsAPI(server).get(OVERWORLD, x, 0);
            helper.assertTrue(java.util.Objects.equals(expectedClaim, playerData().getLastClaimCheck()),
                    "expected the last claim check to follow the player to chunk " + x + " for " + move + ", got "
                            + playerData().getLastClaimCheck());
            helper.assertTrue(playerData().getLastClaimCheckDim() == Level.OVERWORLD,
                    "expected the last claim check dimension to be the overworld for " + move);
        }

        /** Runs a command as the mock player and records what it sends back. */
        void run(String command) {
            recorder.sent.clear();
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }

        boolean canRun(String command) {
            CommandSourceStack source = player.createCommandSourceStack();
            ParseResults<CommandSourceStack> parsed = server.getCommands().getDispatcher().parse(command, source);
            return !parsed.getReader().canRead() && parsed.getContext().getCommand() != null;
        }

        /** The last command sent the (server side translated) text of {@code key} to the player as a chat message. */
        void assertSystemMessage(MinecraftServer server, String key) {
            String expected = localized(server, key);
            List<String> received = recorder.sent.stream().filter(p -> p instanceof ClientboundSystemChatPacket)
                    .map(p -> ((ClientboundSystemChatPacket) p).content()).map(Component::getString).toList();
            helper.assertTrue(received.contains(expected),
                    "expected the message '" + expected + "', got " + received);
        }
    }
}
