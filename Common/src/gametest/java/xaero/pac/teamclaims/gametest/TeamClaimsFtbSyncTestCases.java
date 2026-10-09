package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamPropertiesChangedEvent;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import dev.ftb.mods.ftbteams.data.AbstractTeam;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.PlayerTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.parties.party.IParty;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommands;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.ftbsync.FtbSyncNames;
import xaero.pac.teamclaims.ftbsync.FtbSyncStore;
import xaero.pac.teamclaims.ftbsync.FtbTeamsSync;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.commandSource;
import static xaero.pac.teamclaims.gametest.TeamClaimsHardeningTestCases.localized;
import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.removePlayerQuiet;

/**
 * Dev/test-only, loader-neutral test bodies for the FTB Teams party sync ({@code xaero.pac.teamclaims.ftbsync}).
 * <p>
 * Compiled into every loader's gametest source set (FTB Teams is a compile-only dependency there), but only the
 * FTB runs ({@code :Fabric:runBootTestFtb}, {@code :NeoForge:runTeamClaimsGameTestFtb}) have FTB Teams installed and
 * register the wrappers of these tests (the {@code opac_teamclaims_gametest_ftb} test mod of each loader). In the
 * default runs nothing refers to this class, so it is never loaded there.
 * <p>
 * Conventions: every test uses its own players (random UUIDs, unique names) and parties and cleans up on every
 * path. Unless a test says otherwise the players are <b>offline</b>: {@link #knownPlayer} only makes FTB Teams
 * create their personal team, as their first login would. The FTB side is driven through the same methods FTB's
 * own commands call ({@code TeamManagerImpl.createParty}, {@code PartyTeam.join/leave/kick/transferOwnership/
 * forceDisband/invite/promote/demote}, {@code AbstractTeam.settings/declineInvitation}), the OPAC side through its
 * party API and the Team Claims create path. A change is made in one tick and checked {@link #EVENT} ticks later
 * when the mod it was made in has an event for it (the sync runs at the end of the tick), {@link #POLLED} ticks
 * later when it has none (the periodic merge). The first step of a test runs one tick after its players were made
 * known to FTB Teams: that is answered with a full reconciliation at the end of the tick, like a first login, and
 * the steps are to show what the hooks and events do. Tests that need a server option changed for one operation, or
 * parties the sync has not seen, do all of it within one tick ({@code FtbTeamsSync.runPendingWork/setPaused/
 * resync}), so they cannot affect the tests running next to them. Every test must be registered with the loader's
 * empty structure template and a timeout of {@link #TIMEOUT_TICKS}; {@link #settledPairsAreNotTouchedAgain}
 * additionally in the batch {@link #IDLE_BATCH} and {@link #ftbDeletionKeepsPartyWithMembersNotInFtb} in the batch
 * {@link #KEPT_BATCH}, so that each of the two runs with no other test next to it.
 */
public final class TeamClaimsFtbSyncTestCases {

    public static final int TIMEOUT_TICKS = 400;
    /** The batch of {@link #settledPairsAreNotTouchedAgain}, which counts every change the sync makes. */
    public static final String IDLE_BATCH = "teamclaims_ftbsync_idle";
    /** The batch of {@link #ftbDeletionKeepsPartyWithMembersNotInFtb}, which also counts every change the sync makes. */
    public static final String KEPT_BATCH = "teamclaims_ftbsync_kept";

    private static final int EVENT = 2;
    private static final int POLLED = FtbTeamsSync.PAIR_INTERVAL + 5;
    private static final String KEY = "gui.xaero_pac_team_claims_ftbsync_";

    private TeamClaimsFtbSyncTestCases() {}

    // ==================== Helpers ====================

    /** The steps of a test, some ticks apart. Cleans up when a step fails and after the last one, then succeeds. */
    private static final class Steps {
        private final GameTestHelper helper;
        private final Runnable cleanup;
        private final List<Integer> delays = new ArrayList<>();
        private final List<Runnable> steps = new ArrayList<>();

        Steps(GameTestHelper helper, Runnable cleanup) {
            this.helper = helper;
            this.cleanup = cleanup;
        }

        Steps now(Runnable step) {
            return after(0, step);
        }

        Steps after(int ticks, Runnable step) {
            delays.add(ticks);
            steps.add(step);
            return this;
        }

        /** Starts one tick from now, see the class comment. */
        void run() {
            helper.runAfterDelay(1, () -> runFrom(0));
        }

        private void runFrom(int index) {
            if (index == steps.size()) {
                cleanupQuiet();
                helper.succeed();
                return;
            }
            Runnable body = () -> {
                try {
                    steps.get(index).run();
                } catch (RuntimeException | Error e) {
                    cleanupQuiet();
                    throw e;
                }
                runFrom(index + 1);
            };
            if (delays.get(index) == 0) body.run();
            else helper.runAfterDelay(delays.get(index), body);
        }

        private void cleanupQuiet() {
            try {
                cleanup.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static FtbTeamsSync sync(GameTestHelper helper) {
        FtbTeamsSync sync = TeamClaimsCommon.getFtbTeamsSync();
        helper.assertTrue(sync != null, "expected the FTB Teams sync to exist in a run with FTB Teams installed, reason for none: "
                + TeamClaimsCommon.getFtbTeamsSyncOffReason());
        helper.assertTrue(sync.isRunning(), "expected the FTB Teams sync to be running, state: " + sync.getStateKey());
        return sync;
    }

    private static TeamManagerImpl ftb() {
        return TeamManagerImpl.INSTANCE;
    }

    /** An offline player that FTB Teams knows (has a personal team for), as after their first login. */
    private static GameProfile knownPlayer(String name) {
        GameProfile profile = profile(name);
        ftb().playerLoggedIn(null, profile.getId(), profile.getName());
        return profile;
    }

    private static CommandSourceStack console(MinecraftServer server) {
        return server.createCommandSourceStack().withSuppressedOutput();
    }

    @Nullable
    private static PartyTeam ftbPartyOf(UUID playerId) {
        PlayerTeam personal = ftb().getPersonalTeamForPlayerID(playerId);
        return personal != null && personal.getEffectiveTeam() instanceof PartyTeam party ? party : null;
    }

    @Nullable
    private static PartyTeam ftbPartyById(@Nullable UUID teamId) {
        AbstractTeam team = teamId == null ? null : ftb().getTeamMap().get(teamId);
        return team instanceof PartyTeam party ? party : null;
    }

    private static TeamRank ftbRank(PartyTeam team, UUID playerId) {
        return team.getPlayersByRank(TeamRank.NONE).getOrDefault(playerId, TeamRank.NONE);
    }

    private static PartyTeam ftbCreate(GameProfile owner, String name) {
        try {
            return ftb().createParty(owner.getId(), null, name, null, null);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("test setup: FTB Teams did not create the party '" + name + "': " + e.getMessage());
        }
    }

    private static void ftbJoin(PartyTeam team, GameProfile player) {
        try {
            team.join(null, player);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("test setup: FTB Teams did not let " + player.getName() + " join: " + e.getMessage());
        }
    }

    private static void ftbLeave(PartyTeam team, GameProfile player) {
        try {
            team.leave(player.getId());
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("test setup: FTB Teams did not let " + player.getName() + " leave: " + e.getMessage());
        }
    }

    private static void ftbKick(MinecraftServer server, PartyTeam team, GameProfile player) {
        try {
            team.kick(console(server), List.of(player));
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("test setup: FTB Teams did not kick " + player.getName() + ": " + e.getMessage());
        }
    }

    private static void ftbTransfer(MinecraftServer server, PartyTeam team, GameProfile newOwner) {
        try {
            team.transferOwnership(console(server), newOwner);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("test setup: FTB Teams did not transfer the ownership: " + e.getMessage());
        }
    }

    private static void ftbDisband(MinecraftServer server, PartyTeam team) {
        try {
            team.forceDisband(console(server));
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("test setup: FTB Teams did not disband the party: " + e.getMessage());
        }
    }

    /** What {@code PartyTeam.invite} does to the team, for a test without an online inviter (which that method needs). */
    private static void ftbInviteWithoutInviter(PartyTeam team, GameProfile player) {
        team.addMember(player.getId(), TeamRank.INVITED);
        team.markDirty();
    }

    /** Creates an OPAC party through the Team Claims create path of {@code /<parties> create <name>}. */
    private static IServerPartyAPI opacCreate(GameTestHelper helper, GameProfile owner, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        TeamClaimsHardeningTestCases.CapturingCommandSource capture = new TeamClaimsHardeningTestCases.CapturingCommandSource();
        int result = TeamClaimsCommands.createPartyWithTeamName(commandSource(helper, capture), null, owner, name, false);
        helper.assertTrue(result == 1, "test setup: expected the party '" + name + "' to be created, got: " + capture.all());
        IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
        helper.assertTrue(party != null, "test setup: expected " + owner.getName() + " to own a party");
        return party;
    }

    private static void opacAdd(GameTestHelper helper, IServerPartyAPI party, GameProfile player, PartyMemberRank rank) {
        helper.assertTrue(party.addMember(player.getId(), rank, player.getName()) != null,
                "test setup: expected " + player.getName() + " to be added to the party");
    }

    private static String opacName(IServerPartyAPI party) {
        return TeamClaimsCommon.getTeamConfigManager().resolvePartyName(party);
    }

    private static void setOpacName(GameTestHelper helper, MinecraftServer server, GameProfile owner, String name) {
        IPlayerConfigAPI.SetResult result = configManager(server).getLoadedConfig(owner.getId())
                .tryToSet(PlayerConfigOptions.PARTY_NAME, name);
        helper.assertTrue(result == IPlayerConfigAPI.SetResult.SUCCESS, "test setup: expected the party name '" + name
                + "' to be set, got " + result);
    }

    private static Set<UUID> opacMembers(IServerPartyAPI party) {
        Set<UUID> ids = new HashSet<>();
        party.getMemberInfoStream().forEach(member -> ids.add(member.getUUID()));
        return ids;
    }

    private static Set<UUID> ids(GameProfile... players) {
        Set<UUID> ids = new HashSet<>();
        for (GameProfile player : players) ids.add(player.getId());
        return ids;
    }

    @Nullable
    private static PartyMemberRank opacRank(IServerPartyAPI party, GameProfile player) {
        IPartyMemberAPI member = party.getMemberInfo(player.getId());
        return member == null ? null : member.getRank();
    }

    /** The FTB party an OPAC party is linked to. Fails the test when there is none. */
    private static PartyTeam linkedTeam(GameTestHelper helper, IServerPartyAPI party) {
        FtbSyncStore.Pair pair = sync(helper).getStore().byOpacParty(party.getId());
        helper.assertTrue(pair != null && pair.ftbId() != null, "expected the party " + party.getId() + " to be linked to an FTB Teams party");
        PartyTeam team = ftbPartyById(pair.ftbId());
        helper.assertTrue(team != null && team.isValid(), "expected the linked FTB Teams party " + pair.ftbId() + " to exist");
        return team;
    }

    private static boolean hasPending(FtbTeamsSync sync, String reasonKey, String firstArg) {
        for (FtbTeamsSync.PendingView pending : sync.getPending())
            if (pending.reasonKey().equals(reasonKey) && pending.reasonArgs().length > 0 && firstArg.equals(pending.reasonArgs()[0]))
                return true;
        return false;
    }

    private static boolean inNoParty(MinecraftServer server, GameProfile... players) {
        for (GameProfile player : players)
            if (partyManager(server).getPartyByMember(player.getId()) != null || ftbPartyOf(player.getId()) != null) return false;
        return true;
    }

    /**
     * How many of the chat lines are the sync's message {@code key} with exactly these arguments. Compares the
     * arguments themselves and not only the text, which does not show them for a key without a translation.
     */
    private static int toldCount(MinecraftServer server, List<Component> chat, String key, Object... args) {
        String text = localized(server, key, args);
        int count = 0;
        for (Component line : chat) {
            for (Component part : line.getSiblings()) {
                if (!(part.getContents() instanceof TranslatableContents contents) || !part.getString().equals(text)) continue;
                Object[] actual = contents.getArgs();
                boolean same = actual.length == args.length;
                for (int i = 0; same && i < args.length; i++) same = String.valueOf(args[i]).equals(String.valueOf(actual[i]));
                if (same) count++;
            }
        }
        return count;
    }

    private static List<String> texts(List<Component> chat) {
        return chat.stream().map(Component::getString).toList();
    }

    /** Removes whatever party the players are still in, in both mods; never throws. */
    private static void cleanup(MinecraftServer server, GameProfile... players) {
        for (GameProfile player : players) {
            try {
                IServerPartyAPI party = partyManager(server).getPartyByMember(player.getId());
                if (party != null) disbandPartyQuiet(server, party.getId());
            } catch (RuntimeException ignored) {
            }
            try {
                PartyTeam team = ftbPartyOf(player.getId());
                if (team != null) team.forceDisband(console(server));
            } catch (CommandSyntaxException | RuntimeException ignored) {
            }
            try {
                configManager(server).getLoadedConfig(player.getId()).tryToReset(PlayerConfigOptions.PARTY_NAME);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * Runs {@code action} and returns the chat lines the mock player was sent meanwhile. Unlike the recorder of the
     * budget tests, this one has a channel like the mock player's own connection: FTB Teams asks the connection
     * whether the client can receive its packets before it sends one (it cannot, so only chat arrives).
     */
    private static List<Component> captureChat(MinecraftServer server, ServerPlayer player, Runnable action) {
        ServerGamePacketListenerImpl original = player.connection;
        ChatRecorder recorder = new ChatRecorder(server, player);//also installs itself as player.connection
        try {
            action.run();
        } finally {
            player.connection = original;
        }
        return recorder.chat;
    }

    private static final class ChatRecorder extends ServerGamePacketListenerImpl {
        final List<Component> chat = new ArrayList<>();

        ChatRecorder(MinecraftServer server, ServerPlayer player) {
            super(server, mockConnection(), player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        /** As {@code GameTestHelper.makeMockServerPlayerInLevel} makes it. */
        private static Connection mockConnection() {
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            return connection;
        }

        @Override
        public void send(Packet<?> packet) {
            record(packet);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            record(packet);
        }

        private void record(Packet<?> packet) {
            if (packet instanceof ClientboundSystemChatPacket chatPacket && !chatPacket.overlay()) chat.add(chatPacket.content());
        }
    }

    // ==================== Create / disband ====================

    /** FS1) A party created in OPAC (with a name) gets an FTB party with the same owner and name, and the two are linked. */
    public static void opacCreateMakesFtbParty(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS1_Owner");
        new Steps(helper, () -> cleanup(server, owner))
                .now(() -> {
                    sync(helper);
                    opacCreate(helper, owner, "FS One");
                })
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    PartyTeam team = linkedTeam(helper, party);
                    helper.assertTrue(team == ftbPartyOf(owner.getId()), "expected the owner to be in the linked FTB Teams party");
                    helper.assertTrue(owner.getId().equals(team.getOwner()), "expected the FTB Teams party to have the same owner, got " + team.getOwner());
                    helper.assertTrue("FS One".equals(team.getDisplayName()), "expected the FTB Teams party to be named 'FS One', got '" + team.getDisplayName() + "'");
                    helper.assertTrue(team.getMembers().equals(ids(owner)), "expected the FTB Teams party to have just the owner, got " + team.getMembers());
                })
                .run();
    }

    /**
     * FS2) A party created in FTB Teams becomes a full OPAC party: same owner and name, with its team config and the
     * owner's team sub-config, as {@code /<parties> create <name>} makes it.
     */
    public static void ftbCreateMakesOpacPartyWithTeam(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS2_Owner");
        PartyTeam[] team = new PartyTeam[1];
        new Steps(helper, () -> cleanup(server, owner))
                .now(() -> {
                    sync(helper);
                    team[0] = ftbCreate(owner, "FS Two");
                })
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(party != null, "expected an OPAC party for the owner of the new FTB Teams party");
                    helper.assertTrue(linkedTeam(helper, party) == team[0], "expected the new party to be linked to the FTB Teams party");
                    helper.assertTrue("FS Two".equals(opacName(party)), "expected the party to be named 'FS Two', got '" + opacName(party) + "'");
                    TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId());
                    helper.assertTrue(teamConfig != null, "expected a team config for the party created from FTB Teams");
                    helper.assertTrue("FS Two".equals(teamConfig.getTeamName()), "expected the team to be named 'FS Two', got '" + teamConfig.getTeamName() + "'");
                    helper.assertTrue(teamConfig.isMember(owner.getId()), "expected the owner to be a team member");
                    helper.assertTrue(configManager(server).getLoadedConfig(owner.getId()).subConfigExists(teamConfig.getSubConfigId()),
                            "expected the owner to have the team sub-config '" + teamConfig.getSubConfigId() + "'");
                })
                .run();
    }

    /** FS3) Destroying a party in OPAC deletes its FTB party; all members are back in their personal FTB teams. */
    public static void opacDisbandDeletesFtbParty(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS3_Owner");
        GameProfile member = knownPlayer("FS3_Member");
        UUID[] teamId = new UUID[1];
        UUID[] partyId = new UUID[1];
        new Steps(helper, () -> cleanup(server, owner, member))
                .now(() -> {
                    sync(helper);
                    opacAdd(helper, opacCreate(helper, owner, "FS Three"), member, PartyMemberRank.MEMBER);
                })
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    PartyTeam team = linkedTeam(helper, party);
                    helper.assertTrue(team.getMembers().equals(ids(owner, member)), "expected both members in the FTB Teams party, got " + team.getMembers());
                    teamId[0] = team.getId();
                    partyId[0] = party.getId();
                    partyManager(server).removePartyById(party.getId());
                })
                .after(EVENT, () -> {
                    helper.assertTrue(ftbPartyById(teamId[0]) == null, "expected the FTB Teams party to be deleted with the party");
                    helper.assertTrue(ftbPartyOf(owner.getId()) == null && ftbPartyOf(member.getId()) == null,
                            "expected both players to be back in their personal FTB teams");
                    helper.assertTrue(sync(helper).getStore().byOpacParty(partyId[0]) == null, "expected the link to be gone");
                })
                .after(POLLED, () -> {
                    sync(helper).resync();
                    helper.assertTrue(inNoParty(server, owner, member), "expected the destroyed party not to come back in either mod");
                })
                .run();
    }

    /** FS4) {@code /ftbteams party force-disband} removes the OPAC party and its team config. */
    public static void ftbForceDisbandRemovesParty(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS4_Owner");
        GameProfile member = knownPlayer("FS4_Member");
        PartyTeam[] team = new PartyTeam[1];
        UUID[] partyId = new UUID[1];
        new Steps(helper, () -> cleanup(server, owner, member))
                .now(() -> {
                    sync(helper);
                    team[0] = ftbCreate(owner, "FS Four");
                    ftbJoin(team[0], member);
                })
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(party != null && party.getMemberInfo(member.getId()) != null, "expected the OPAC party with both members");
                    partyId[0] = party.getId();
                    ftbDisband(server, team[0]);
                })
                .after(EVENT, () -> {
                    helper.assertTrue(partyManager(server).getPartyById(partyId[0]) == null, "expected the OPAC party to be removed with the FTB Teams party");
                    helper.assertTrue(partyManager(server).getPartyByMember(member.getId()) == null, "expected the member to be in no party");
                    helper.assertTrue(TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId[0]) == null, "expected the team config to be removed");
                    helper.assertTrue(sync(helper).getStore().byOpacParty(partyId[0]) == null, "expected the link to be gone");
                })
                .after(POLLED, () -> {
                    sync(helper).resync();
                    helper.assertTrue(inNoParty(server, owner, member), "expected the disbanded party not to come back in either mod");
                })
                .run();
    }

    /** FS5) FTB Teams has no disband for players: the party is deleted when its last member leaves, and the OPAC party goes too. */
    public static void lastFtbMemberLeavingRemovesParty(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS5_Owner");
        PartyTeam[] team = new PartyTeam[1];
        UUID[] partyId = new UUID[1];
        new Steps(helper, () -> cleanup(server, owner))
                .now(() -> {
                    sync(helper);
                    team[0] = ftbCreate(owner, "FS Five");
                })
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(party != null, "expected the OPAC party of the FTB Teams party");
                    partyId[0] = party.getId();
                    ftbLeave(team[0], owner);
                })
                .after(EVENT, () -> {
                    helper.assertTrue(partyManager(server).getPartyById(partyId[0]) == null, "expected the OPAC party to be removed when the FTB Teams party's last member left");
                    helper.assertTrue(TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId[0]) == null, "expected the team config to be removed");
                })
                .run();
    }

    // ==================== Members ====================

    /**
     * FS6) OPAC members join the FTB party; a member removed in OPAC (its leave and kick commands both end in
     * {@code removeMember}) leaves the FTB party. All players are offline.
     */
    public static void opacMembershipReachesFtb(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS6_Owner");
        GameProfile first = knownPlayer("FS6_First");
        GameProfile second = knownPlayer("FS6_Second");
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        new Steps(helper, () -> cleanup(server, owner, first, second))
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Six");
                    opacAdd(helper, party[0], first, PartyMemberRank.MEMBER);
                })
                .after(EVENT, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(team.getMembers().equals(ids(owner, first)), "expected the first member in the FTB Teams party, got " + team.getMembers());
                    opacAdd(helper, party[0], second, PartyMemberRank.MEMBER);
                })
                .after(EVENT, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(team.getMembers().equals(ids(owner, first, second)), "expected all three in the FTB Teams party, got " + team.getMembers());
                    helper.assertTrue(ftbRank(team, second.getId()) == TeamRank.MEMBER, "expected a plain FTB member, got " + ftbRank(team, second.getId()));
                    helper.assertTrue(party[0].removeMember(first.getId()) != null, "test setup: expected the member to be removed");
                })
                .after(EVENT, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(team.getMembers().equals(ids(owner, second)), "expected the removed member to have left the FTB Teams party, got " + team.getMembers());
                    helper.assertTrue(ftbPartyOf(first.getId()) == null, "expected the removed member to be back in their personal FTB team");
                })
                .run();
    }

    /**
     * FS7) Players who join an FTB party ({@code force-add}, i.e. {@code PartyTeam.join}) join the OPAC party and its
     * team; leaving and being kicked in FTB Teams removes them from both. All players are offline.
     */
    public static void ftbMembershipReachesOpac(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS7_Owner");
        GameProfile leaver = knownPlayer("FS7_Leaver");
        GameProfile kicked = knownPlayer("FS7_Kicked");
        PartyTeam[] team = new PartyTeam[1];
        new Steps(helper, () -> cleanup(server, owner, leaver, kicked))
                .now(() -> {
                    sync(helper);
                    team[0] = ftbCreate(owner, "FS Seven");
                    ftbJoin(team[0], leaver);
                })
                .after(EVENT, () -> ftbJoin(team[0], kicked))
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(party != null && opacMembers(party).equals(ids(owner, leaver, kicked)),
                            "expected all three FTB members in the OPAC party, got " + (party == null ? "no party" : opacMembers(party)));
                    helper.assertTrue(opacRank(party, kicked) == PartyMemberRank.MEMBER, "expected a plain member, got " + opacRank(party, kicked));
                    TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId());
                    helper.assertTrue(teamConfig != null && teamConfig.getMembers().equals(ids(owner, leaver, kicked)),
                            "expected all three in the team config");
                    helper.assertTrue(configManager(server).getLoadedConfig(kicked.getId()).subConfigExists(teamConfig.getSubConfigId()),
                            "expected the member who joined in FTB Teams to have the team sub-config");
                    ftbLeave(team[0], leaver);
                    ftbKick(server, team[0], kicked);
                })
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(party != null && opacMembers(party).equals(ids(owner)),
                            "expected only the owner left in the OPAC party, got " + (party == null ? "no party" : opacMembers(party)));
                    TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId());
                    helper.assertTrue(teamConfig.getMembers().equals(ids(owner)), "expected only the owner left in the team config");
                    helper.assertTrue(!configManager(server).getLoadedConfig(leaver.getId()).subConfigExists(teamConfig.getSubConfigId()),
                            "expected the leaver's team sub-config to be removed");
                })
                .run();
    }

    /**
     * FS27) A player who leaves one FTB party and joins another within one tick moves between the two OPAC parties
     * (and teams) as well: the notes of the two changes are worked off in the order they happened.
     */
    public static void ftbMoveBetweenPartiesMovesInOpac(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile fromOwner = knownPlayer("FS27_FromOwner");
        GameProfile toOwner = knownPlayer("FS27_ToOwner");
        GameProfile mover = knownPlayer("FS27_Mover");
        PartyTeam[] teams = new PartyTeam[2];
        new Steps(helper, () -> cleanup(server, fromOwner, toOwner, mover))
                .now(() -> {
                    sync(helper);
                    teams[0] = ftbCreate(fromOwner, "FS27 From");
                    ftbJoin(teams[0], mover);
                    teams[1] = ftbCreate(toOwner, "FS27 To");
                })
                .after(EVENT, () -> {
                    IServerPartyAPI from = partyManager(server).getPartyByOwner(fromOwner.getId());
                    helper.assertTrue(from != null && from.getMemberInfo(mover.getId()) != null && partyManager(server).getPartyByOwner(toOwner.getId()) != null,
                            "expected both OPAC parties, the first one with the player");
                    ftbLeave(teams[0], mover);
                    ftbJoin(teams[1], mover);
                })
                .after(EVENT, () -> {
                    IServerPartyAPI from = partyManager(server).getPartyByOwner(fromOwner.getId());
                    IServerPartyAPI to = partyManager(server).getPartyByOwner(toOwner.getId());
                    helper.assertTrue(opacMembers(from).equals(ids(fromOwner)), "expected the player to have left the first OPAC party, got " + opacMembers(from));
                    helper.assertTrue(opacMembers(to).equals(ids(toOwner, mover)), "expected the player to have joined the second OPAC party, got " + opacMembers(to));
                    helper.assertTrue(ftbPartyOf(mover.getId()) == teams[1], "expected the player to stay in the second FTB Teams party");
                    TeamConfig fromTeam = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(from.getId());
                    TeamConfig toTeam = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(to.getId());
                    helper.assertTrue(!fromTeam.isMember(mover.getId()) && toTeam.isMember(mover.getId()), "expected the player to have moved between the two teams");
                    IPlayerConfigAPI moverConfig = configManager(server).getLoadedConfig(mover.getId());
                    helper.assertTrue(!moverConfig.subConfigExists(fromTeam.getSubConfigId()) && moverConfig.subConfigExists(toTeam.getSubConfigId()),
                            "expected the player to have only the second team's sub-config");
                })
                .run();
    }

    // ==================== Owner ====================

    /** FS8) An ownership transfer in OPAC transfers the FTB party; the old owner is an admin there and an officer here. */
    public static void opacOwnerTransferReachesFtb(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS8_Owner");
        GameProfile heir = knownPlayer("FS8_Heir");
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        new Steps(helper, () -> cleanup(server, owner, heir))
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Eight");
                    opacAdd(helper, party[0], heir, PartyMemberRank.MEMBER);
                })
                .after(EVENT, () -> {
                    helper.assertTrue(owner.getId().equals(linkedTeam(helper, party[0]).getOwner()), "expected the FTB owner to be the party owner");
                    helper.assertTrue(((IParty<?, ?, ?>) party[0]).changeOwner(heir.getId(), heir.getName()), "test setup: expected the transfer to work");
                })
                .after(EVENT, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(heir.getId().equals(team.getOwner()), "expected the FTB Teams party to have the new owner, got " + team.getOwner());
                    helper.assertTrue(ftbRank(team, owner.getId()) == TeamRank.OFFICER, "expected the old owner to be an FTB officer, got " + ftbRank(team, owner.getId()));
                    helper.assertTrue(opacRank(party[0], owner) == PartyMemberRank.ADMIN, "expected the old owner to stay ADMIN, got " + opacRank(party[0], owner));
                    // OPAC takes the party name from the (new) owner's config; FTB follows
                    helper.assertTrue(opacName(party[0]).equals(team.getDisplayName()), "expected the FTB Teams party to follow the party's name '"
                            + opacName(party[0]) + "', got '" + team.getDisplayName() + "'");
                })
                .run();
    }

    /** FS9) An ownership transfer in FTB Teams changes the OPAC owner, and the party keeps its name. */
    public static void ftbOwnerTransferReachesOpac(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS9_Owner");
        GameProfile heir = knownPlayer("FS9_Heir");
        PartyTeam[] team = new PartyTeam[1];
        UUID[] partyId = new UUID[1];
        new Steps(helper, () -> cleanup(server, owner, heir))
                .now(() -> {
                    sync(helper);
                    team[0] = ftbCreate(owner, "FS Nine");
                    ftbJoin(team[0], heir);
                })
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(party != null && party.getMemberInfo(heir.getId()) != null, "expected the OPAC party with both members");
                    partyId[0] = party.getId();
                    ftbTransfer(server, team[0], heir);
                })
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyById(partyId[0]);
                    helper.assertTrue(party != null && heir.getId().equals(party.getOwner().getUUID()),
                            "expected the OPAC party to have the new owner, got " + (party == null ? "no party" : party.getOwner().getUsername()));
                    helper.assertTrue(opacRank(party, owner) == PartyMemberRank.ADMIN && !party.getMemberInfo(owner.getId()).isOwner(),
                            "expected the old owner to be an ADMIN member, got " + opacRank(party, owner));
                    helper.assertTrue("FS Nine".equals(opacName(party)), "expected the party to keep its name, got '" + opacName(party) + "'");
                    helper.assertTrue("FS Nine".equals(team[0].getDisplayName()), "expected the FTB Teams party to keep its name, got '" + team[0].getDisplayName() + "'");
                    TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(partyId[0]);
                    helper.assertTrue(teamConfig != null && "FS Nine".equals(teamConfig.getTeamName()), "expected the team to keep its name");
                })
                .run();
    }

    // ==================== Name ====================

    /**
     * FS10) Renaming the party in OPAC renames the FTB party. A name FTB Teams rejects (shorter than 3 characters)
     * is padded there, and the padded name is not written back.
     */
    public static void opacRenameReachesFtb(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS10_Owner");
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        new Steps(helper, () -> cleanup(server, owner))
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Ten");
                })
                .after(EVENT, () -> {
                    helper.assertTrue("FS Ten".equals(linkedTeam(helper, party[0]).getDisplayName()), "expected the FTB Teams party to be named 'FS Ten'");
                    setOpacName(helper, server, owner, "Ten Renamed");
                })
                .after(EVENT, () -> {
                    helper.assertTrue("Ten Renamed".equals(linkedTeam(helper, party[0]).getDisplayName()),
                            "expected the FTB Teams party to be renamed, got '" + linkedTeam(helper, party[0]).getDisplayName() + "'");
                    setOpacName(helper, server, owner, "Ab");
                })
                .after(EVENT, () -> {
                    helper.assertTrue("Ab_".equals(linkedTeam(helper, party[0]).getDisplayName()),
                            "expected the too short name to be padded for FTB Teams, got '" + linkedTeam(helper, party[0]).getDisplayName() + "'");
                    helper.assertTrue("Ab".equals(opacName(party[0])), "expected the party to keep its own name, got '" + opacName(party[0]) + "'");
                })
                .after(POLLED, () -> {
                    helper.assertTrue("Ab".equals(opacName(party[0])) && "Ab_".equals(linkedTeam(helper, party[0]).getDisplayName()),
                            "expected both names to stay as they are after the periodic merge, got '" + opacName(party[0]) + "' / '"
                                    + linkedTeam(helper, party[0]).getDisplayName() + "'");
                })
                .run();
    }

    /**
     * FS11) Renaming the party in FTB Teams (its settings command) renames the OPAC party and the team. A name OPAC
     * rejects (formatting code, characters outside its pattern, longer than the maximum team name length) is
     * sanitised there, FTB keeps what was typed, and nothing is written back.
     */
    public static void ftbRenameReachesOpac(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS11_Owner");
        PartyTeam[] team = new PartyTeam[1];
        String rejected = "Blue <Team> §cNo.1 with a very long tail";
        new Steps(helper, () -> cleanup(server, owner))
                .now(() -> {
                    sync(helper);
                    team[0] = ftbCreate(owner, "FS Eleven");
                })
                .after(EVENT, () -> {
                    helper.assertTrue(partyManager(server).getPartyByOwner(owner.getId()) != null, "expected the OPAC party of the FTB Teams party");
                    team[0].settings(console(server), TeamProperties.DISPLAY_NAME, "Eleven Renamed");
                })
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue("Eleven Renamed".equals(opacName(party)), "expected the party to be renamed, got '" + opacName(party) + "'");
                    TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId());
                    helper.assertTrue("Eleven Renamed".equals(teamConfig.getTeamName()), "expected the team to be renamed, got '" + teamConfig.getTeamName() + "'");
                    team[0].settings(console(server), TeamProperties.DISPLAY_NAME, rejected);
                })
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    String expected = FtbSyncNames.toOpacName(rejected);
                    helper.assertTrue("Blue _Team_ No_1 with a".equals(expected), "expected the sanitised name to be 'Blue _Team_ No_1 with a', got '" + expected + "'");
                    helper.assertTrue(PlayerConfigOptions.PARTY_NAME.getServerSideValidator().test(
                            configManager(server).getLoadedConfig(owner.getId()), expected), "expected OPAC to accept the sanitised name");
                    helper.assertTrue(expected.equals(opacName(party)), "expected the party to get the sanitised name, got '" + opacName(party) + "'");
                    helper.assertTrue(rejected.equals(team[0].getDisplayName()), "expected FTB Teams to keep the name as typed, got '" + team[0].getDisplayName() + "'");
                })
                .after(POLLED, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(FtbSyncNames.toOpacName(rejected).equals(opacName(party)) && rejected.equals(team[0].getDisplayName()),
                            "expected both names to stay as they are after the periodic merge, got '" + opacName(party) + "' / '"
                                    + team[0].getDisplayName() + "'");
                })
                .run();
    }

    // ==================== Ranks ====================

    /**
     * FS12) OPAC ranks reach FTB Teams as officer (ADMIN, MODERATOR) or member (CLAIMER, MEMBER), and the periodic
     * merge and a full reconciliation leave the OPAC ranks alone: an ADMIN stays ADMIN although FTB only knows
     * "officer", a CLAIMER stays CLAIMER although FTB only knows "member".
     */
    public static void opacRanksReachFtbAndStayStable(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS12_Owner");
        GameProfile admin = knownPlayer("FS12_Admin");
        GameProfile claimer = knownPlayer("FS12_Claimer");
        GameProfile moderator = knownPlayer("FS12_Moderator");
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        new Steps(helper, () -> cleanup(server, owner, admin, claimer, moderator))
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Twelve");
                    opacAdd(helper, party[0], admin, PartyMemberRank.ADMIN);
                    opacAdd(helper, party[0], claimer, PartyMemberRank.CLAIMER);
                    opacAdd(helper, party[0], moderator, PartyMemberRank.MODERATOR);
                })
                .after(EVENT, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(ftbRank(team, owner.getId()) == TeamRank.OWNER, "expected the owner to be the FTB owner");
                    helper.assertTrue(ftbRank(team, admin.getId()) == TeamRank.OFFICER, "expected the ADMIN to be an FTB officer, got " + ftbRank(team, admin.getId()));
                    helper.assertTrue(ftbRank(team, moderator.getId()) == TeamRank.OFFICER, "expected the MODERATOR to be an FTB officer, got " + ftbRank(team, moderator.getId()));
                    helper.assertTrue(ftbRank(team, claimer.getId()) == TeamRank.MEMBER, "expected the CLAIMER to be an FTB member, got " + ftbRank(team, claimer.getId()));
                })
                .after(POLLED, () -> {
                    sync(helper).resync();
                    helper.assertTrue(opacRank(party[0], admin) == PartyMemberRank.ADMIN, "expected the ADMIN to stay ADMIN, got " + opacRank(party[0], admin));
                    helper.assertTrue(opacRank(party[0], claimer) == PartyMemberRank.CLAIMER, "expected the CLAIMER to stay CLAIMER, got " + opacRank(party[0], claimer));
                    helper.assertTrue(opacRank(party[0], moderator) == PartyMemberRank.MODERATOR, "expected the MODERATOR to stay MODERATOR");
                    helper.assertTrue(party[0].setRank(party[0].getMemberInfo(moderator.getId()), PartyMemberRank.MEMBER), "test setup: expected the rank change to work");
                    helper.assertTrue(party[0].setRank(party[0].getMemberInfo(claimer.getId()), PartyMemberRank.MODERATOR), "test setup: expected the rank change to work");
                    // within one class: nothing to do in FTB
                    helper.assertTrue(party[0].setRank(party[0].getMemberInfo(admin.getId()), PartyMemberRank.MODERATOR), "test setup: expected the rank change to work");
                })
                .after(EVENT, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(ftbRank(team, moderator.getId()) == TeamRank.MEMBER, "expected the demoted member to be an FTB member, got " + ftbRank(team, moderator.getId()));
                    helper.assertTrue(ftbRank(team, claimer.getId()) == TeamRank.OFFICER, "expected the promoted member to be an FTB officer, got " + ftbRank(team, claimer.getId()));
                    helper.assertTrue(ftbRank(team, admin.getId()) == TeamRank.OFFICER, "expected ADMIN -> MODERATOR to stay an FTB officer");
                    helper.assertTrue(opacRank(party[0], admin) == PartyMemberRank.MODERATOR, "expected the OPAC rank to be the one that was set");
                })
                .run();
    }

    /**
     * FS13) Promoting and demoting in FTB Teams (no event, found by the periodic merge) changes the OPAC rank only
     * for the member whose class really changed: a promoted MEMBER becomes MODERATOR, a demoted ADMIN becomes
     * MEMBER, and an untouched ADMIN (officer) and CLAIMER (member) keep their ranks.
     */
    public static void ftbRanksReachOpacAndStayStable(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS13_Owner");
        GameProfile admin = knownPlayer("FS13_Admin");
        GameProfile demoted = knownPlayer("FS13_Demoted");
        GameProfile promoted = knownPlayer("FS13_Promoted");
        GameProfile claimer = knownPlayer("FS13_Claimer");
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        ServerPlayer caller = helper.makeMockServerPlayerInLevel();//FTB's promote/demote want an online caller for their chat line
        new Steps(helper, () -> {
            removePlayerQuiet(server, caller);
            cleanup(server, owner, admin, demoted, promoted, claimer);
        })
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Thirteen");
                    opacAdd(helper, party[0], admin, PartyMemberRank.ADMIN);
                    opacAdd(helper, party[0], demoted, PartyMemberRank.ADMIN);
                    opacAdd(helper, party[0], promoted, PartyMemberRank.MEMBER);
                    opacAdd(helper, party[0], claimer, PartyMemberRank.CLAIMER);
                })
                .after(EVENT, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    try {
                        team.promote(caller, List.of(promoted));
                        team.demote(caller, List.of(demoted));
                    } catch (CommandSyntaxException e) {
                        throw new IllegalStateException("test setup: FTB Teams did not change the ranks: " + e.getMessage());
                    }
                })
                .after(POLLED, () -> {
                    helper.assertTrue(opacRank(party[0], promoted) == PartyMemberRank.MODERATOR, "expected the member promoted in FTB Teams to be MODERATOR, got " + opacRank(party[0], promoted));
                    helper.assertTrue(opacRank(party[0], demoted) == PartyMemberRank.MEMBER, "expected the admin demoted in FTB Teams to be MEMBER, got " + opacRank(party[0], demoted));
                    helper.assertTrue(opacRank(party[0], admin) == PartyMemberRank.ADMIN, "expected the untouched ADMIN to stay ADMIN, got " + opacRank(party[0], admin));
                    helper.assertTrue(opacRank(party[0], claimer) == PartyMemberRank.CLAIMER, "expected the untouched CLAIMER to stay CLAIMER, got " + opacRank(party[0], claimer));
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(ftbRank(team, promoted.getId()) == TeamRank.OFFICER && ftbRank(team, demoted.getId()) == TeamRank.MEMBER,
                            "expected the FTB ranks to stay as FTB Teams set them");
                })
                .after(POLLED, () -> {
                    helper.assertTrue(opacRank(party[0], promoted) == PartyMemberRank.MODERATOR && opacRank(party[0], demoted) == PartyMemberRank.MEMBER
                                    && opacRank(party[0], admin) == PartyMemberRank.ADMIN && opacRank(party[0], claimer) == PartyMemberRank.CLAIMER,
                            "expected all ranks to stay as they are after another periodic merge");
                })
                .run();
    }

    // ==================== Invitations ====================

    /** FS14) An OPAC invitation exists in FTB Teams too, and accepting it there makes the player a member in both. */
    public static void opacInviteCanBeAcceptedInFtb(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS14_Owner");
        GameProfile invited = knownPlayer("FS14_Invited");
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        new Steps(helper, () -> cleanup(server, owner, invited))
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Fourteen");
                })
                .after(EVENT, () -> helper.assertTrue(party[0].invitePlayer(invited.getId(), invited.getName()) != null, "test setup: expected the invitation to be made"))
                .after(EVENT, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(ftbRank(team, invited.getId()) == TeamRank.INVITED, "expected the OPAC invitation to exist in FTB Teams, got rank " + ftbRank(team, invited.getId()));
                    helper.assertTrue(!team.getMembers().contains(invited.getId()), "expected the invited player not to be a member yet");
                    ftbJoin(team, invited);//what "/ftbteams party join" does for an invited player
                })
                .after(EVENT, () -> {
                    helper.assertTrue(party[0].getMemberInfo(invited.getId()) != null, "expected the player who accepted in FTB Teams to be an OPAC member");
                    helper.assertTrue(!party[0].isInvited(invited.getId()), "expected the OPAC invitation to be used up");
                    helper.assertTrue(ftbRank(linkedTeam(helper, party[0]), invited.getId()) == TeamRank.MEMBER, "expected an FTB member");
                })
                .run();
    }

    /**
     * FS15) An invitation made in FTB Teams (by its online owner, with FTB's real invite method; no event, found by
     * the periodic merge) exists in OPAC too, and accepting it there makes the player a member in both. The party is
     * created in FTB Teams by an online player, which is the path that fires FTB's {@code CREATED} event.
     */
    public static void ftbInviteCanBeAcceptedInOpac(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer ownerPlayer = helper.makeMockServerPlayerInLevel();
        GameProfile owner = ownerPlayer.getGameProfile();
        GameProfile invited = knownPlayer("FS15_Invited");
        PartyTeam[] team = new PartyTeam[1];
        new Steps(helper, () -> {
            cleanup(server, owner, invited);
            removePlayerQuiet(server, ownerPlayer);
        })
                .now(() -> {
                    sync(helper);
                    try {
                        team[0] = ftb().createParty(ownerPlayer, "FS Fifteen");
                    } catch (CommandSyntaxException e) {
                        throw new IllegalStateException("test setup: FTB Teams did not create the party: " + e.getMessage());
                    }
                })
                .after(EVENT, () -> {
                    helper.assertTrue(partyManager(server).getPartyByOwner(owner.getId()) != null, "expected the OPAC party of the online FTB owner");
                    try {
                        team[0].invite(ownerPlayer, List.of(invited));
                    } catch (CommandSyntaxException e) {
                        throw new IllegalStateException("test setup: FTB Teams did not invite: " + e.getMessage());
                    }
                    helper.assertTrue(ftbRank(team[0], invited.getId()) == TeamRank.INVITED, "test setup: expected an FTB invitation");
                })
                .after(POLLED, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(party.isInvited(invited.getId()), "expected the FTB Teams invitation to exist in OPAC");
                    //what "/<parties> join" does for an invited player
                    helper.assertTrue(party.addMember(invited.getId(), null, invited.getName()) != null, "test setup: expected the join to work");
                })
                .after(EVENT, () -> {
                    helper.assertTrue(ftbPartyOf(invited.getId()) == team[0], "expected the player who accepted in OPAC to be in the FTB Teams party");
                    helper.assertTrue(ftbRank(team[0], invited.getId()) == TeamRank.MEMBER, "expected an FTB member, got " + ftbRank(team[0], invited.getId()));
                    helper.assertTrue(!partyManager(server).getPartyByOwner(owner.getId()).isInvited(invited.getId()), "expected the OPAC invitation to be used up");
                })
                .run();
    }

    /**
     * FS16) An invitation that is withdrawn or declined in one mod disappears in the other: an OPAC invitation
     * withdrawn in OPAC, an OPAC invitation declined in FTB Teams (its decline command, by an online player), and an
     * FTB invitation declined in OPAC (the party screen's decline, {@code uninvitePlayer}).
     */
    public static void withdrawnAndDeclinedInvitesDisappearInBoth(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS16_Owner");
        GameProfile withdrawn = knownPlayer("FS16_Withdrawn");
        GameProfile ftbInvited = knownPlayer("FS16_FtbInvited");
        ServerPlayer decliner = helper.makeMockServerPlayerInLevel();
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        new Steps(helper, () -> {
            cleanup(server, owner, withdrawn, ftbInvited, decliner.getGameProfile());
            removePlayerQuiet(server, decliner);
        })
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Sixteen");
                })
                .after(EVENT, () -> {
                    helper.assertTrue(party[0].invitePlayer(withdrawn.getId(), withdrawn.getName()) != null, "test setup: expected the invitation to be made");
                    helper.assertTrue(party[0].invitePlayer(decliner.getUUID(), decliner.getGameProfile().getName()) != null, "test setup: expected the invitation to be made");
                })
                .after(EVENT, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(ftbRank(team, withdrawn.getId()) == TeamRank.INVITED && ftbRank(team, decliner.getUUID()) == TeamRank.INVITED,
                            "expected both OPAC invitations to exist in FTB Teams");
                    helper.assertTrue(party[0].uninvitePlayer(withdrawn.getId()) != null, "test setup: expected the invitation to be withdrawn");
                    try {
                        helper.assertTrue(team.declineInvitation(decliner.createCommandSourceStack().withSuppressedOutput()) == 1,
                                "test setup: expected FTB Teams to accept the decline");
                    } catch (CommandSyntaxException e) {
                        throw new IllegalStateException("test setup: FTB Teams did not decline: " + e.getMessage());
                    }
                    ftbInviteWithoutInviter(team, ftbInvited);
                })
                .after(POLLED, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(ftbRank(team, withdrawn.getId()) != TeamRank.INVITED, "expected the invitation withdrawn in OPAC to be gone in FTB Teams");
                    helper.assertTrue(!party[0].isInvited(decliner.getUUID()), "expected the invitation declined in FTB Teams to be gone in OPAC");
                    helper.assertTrue(party[0].isInvited(ftbInvited.getId()), "expected the FTB Teams invitation to exist in OPAC");
                    helper.assertTrue(party[0].uninvitePlayer(ftbInvited.getId()) != null, "test setup: expected the invitation to be declined");
                })
                .after(EVENT, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(ftbRank(team, ftbInvited.getId()) != TeamRank.INVITED, "expected the invitation declined in OPAC to be gone in FTB Teams");
                })
                .after(POLLED, () -> {
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(party[0].getInviteCount() == 0, "expected no invitation to come back in OPAC, got " + party[0].getInviteCount());
                    helper.assertTrue(!team.getPlayersByRank(TeamRank.NONE).containsValue(TeamRank.INVITED), "expected no invitation to come back in FTB Teams");
                })
                .run();
    }

    // ==================== No feedback loop ====================

    /**
     * FS17) After parties were created and changed in both mods and everything has been mirrored, the sync is idle:
     * across two more periodic merges it applies no change at all, a full reconciliation changes nothing, and a
     * second one neither, and none of that marks the saved sync state as changed. Counts every change the sync makes
     * on the server, so it must run alone (own batch).
     */
    public static void settledPairsAreNotTouchedAgain(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile opacOwner = knownPlayer("FS17_OpacOwner");
        GameProfile opacAdmin = knownPlayer("FS17_OpacAdmin");
        GameProfile opacInvited = knownPlayer("FS17_OpacInvited");
        GameProfile ftbOwner = knownPlayer("FS17_FtbOwner");
        GameProfile ftbMember = knownPlayer("FS17_FtbMember");
        long[] settled = new long[1];
        new Steps(helper, () -> cleanup(server, opacOwner, opacAdmin, opacInvited, ftbOwner, ftbMember))
                .now(() -> {
                    sync(helper);
                    IServerPartyAPI party = opacCreate(helper, opacOwner, "X");//needs padding for FTB
                    opacAdd(helper, party, opacAdmin, PartyMemberRank.ADMIN);
                    party.invitePlayer(opacInvited.getId(), opacInvited.getName());
                    PartyTeam team = ftbCreate(ftbOwner, "FS <17> §bparty");//needs sanitising for OPAC
                    ftbJoin(team, ftbMember);
                })
                .after(POLLED, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(opacOwner.getId());
                    helper.assertTrue(linkedTeam(helper, party).getMembers().equals(ids(opacOwner, opacAdmin)), "test setup: expected the OPAC party to be mirrored");
                    IServerPartyAPI mirrored = partyManager(server).getPartyByOwner(ftbOwner.getId());
                    helper.assertTrue(mirrored != null && opacMembers(mirrored).equals(ids(ftbOwner, ftbMember)), "test setup: expected the FTB Teams party to be mirrored");
                    settled[0] = sync(helper).getAppliedChangeCount();
                    server.overworld().getDataStorage().save();
                    helper.assertTrue(!sync(helper).getStore().isDirty(), "test setup: expected the world save to have taken the sync state");
                })
                .after(2 * POLLED, () -> {
                    FtbTeamsSync sync = sync(helper);
                    helper.assertTrue(sync.getAppliedChangeCount() == settled[0], "expected the periodic merges to change nothing once everything is mirrored, but "
                            + (sync.getAppliedChangeCount() - settled[0]) + " change(s) were applied");
                    long first = sync.resync();
                    long second = sync.resync();
                    helper.assertTrue(first == 0 && second == 0, "expected a full reconciliation to change nothing, got " + first + " and then " + second + " change(s)");
                    helper.assertTrue(sync.getPendingCount() == 0, "expected nothing to be pending, got " + sync.getPendingCount());
                    helper.assertTrue(!sync.getStore().isDirty(), "expected the sync state not to be marked for saving while nothing changes");
                })
                .run();
    }

    // ==================== First sync ====================

    /**
     * FS18) Parties the sync has never seen (the state when it is first enabled on a server that has run both mods):
     * a party that only exists in OPAC is created in FTB Teams and one that only exists in FTB Teams is created in
     * OPAC, each with the same owner, members, ranks, name and pending invitations.
     */
    public static void firstSyncCreatesTheMissingSide(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile opacOwner = knownPlayer("FS18_OpacOwner");
        GameProfile opacAdmin = knownPlayer("FS18_OpacAdmin");
        GameProfile opacMember = knownPlayer("FS18_OpacMember");
        GameProfile opacInvited = knownPlayer("FS18_OpacInvited");
        GameProfile ftbOwner = knownPlayer("FS18_FtbOwner");
        GameProfile ftbOfficer = knownPlayer("FS18_FtbOfficer");
        GameProfile ftbMember = knownPlayer("FS18_FtbMember");
        GameProfile ftbInvited = knownPlayer("FS18_FtbInvited");
        Runnable cleanup = () -> cleanup(server, opacOwner, opacAdmin, opacMember, opacInvited, ftbOwner, ftbOfficer, ftbMember, ftbInvited);
        try {
            FtbTeamsSync sync = sync(helper);
            IServerPartyAPI opacParty;
            PartyTeam ftbParty;
            sync.setPaused(true);
            try {
                opacParty = opacCreate(helper, opacOwner, "FS18 Opac");
                opacAdd(helper, opacParty, opacAdmin, PartyMemberRank.ADMIN);
                opacAdd(helper, opacParty, opacMember, PartyMemberRank.MEMBER);
                opacParty.invitePlayer(opacInvited.getId(), opacInvited.getName());
                ftbParty = ftbCreate(ftbOwner, "FS18 Ftb");
                ftbJoin(ftbParty, ftbOfficer);
                ftbJoin(ftbParty, ftbMember);
                ftbParty.addMember(ftbOfficer.getId(), TeamRank.OFFICER);//as PartyTeam.promote does
                ftbInviteWithoutInviter(ftbParty, ftbInvited);
            } finally {
                sync.setPaused(false);
            }
            helper.assertTrue(sync.getStore().byOpacParty(opacParty.getId()) == null && ftbPartyOf(opacOwner.getId()) == null
                    && partyManager(server).getPartyByOwner(ftbOwner.getId()) == null, "test setup: expected the sync not to have seen the two parties");
            helper.assertTrue(sync.resync() > 0, "expected the full reconciliation to apply changes");

            PartyTeam created = linkedTeam(helper, opacParty);
            helper.assertTrue(opacOwner.getId().equals(created.getOwner()), "expected the new FTB Teams party to have the party's owner");
            helper.assertTrue(created.getMembers().equals(ids(opacOwner, opacAdmin, opacMember)), "expected the new FTB Teams party to have the party's members, got " + created.getMembers());
            helper.assertTrue(ftbRank(created, opacAdmin.getId()) == TeamRank.OFFICER && ftbRank(created, opacMember.getId()) == TeamRank.MEMBER,
                    "expected the ranks to be mirrored to FTB Teams");
            helper.assertTrue("FS18 Opac".equals(created.getDisplayName()), "expected the new FTB Teams party to have the party's name, got '" + created.getDisplayName() + "'");
            helper.assertTrue(ftbRank(created, opacInvited.getId()) == TeamRank.INVITED, "expected the pending OPAC invitation in FTB Teams");
            helper.assertTrue(opacRank(opacParty, opacAdmin) == PartyMemberRank.ADMIN, "expected the OPAC ranks to be untouched");

            IServerPartyAPI mirrored = partyManager(server).getPartyByOwner(ftbOwner.getId());
            helper.assertTrue(mirrored != null && linkedTeam(helper, mirrored) == ftbParty, "expected an OPAC party linked to the FTB Teams party");
            helper.assertTrue(opacMembers(mirrored).equals(ids(ftbOwner, ftbOfficer, ftbMember)), "expected the new OPAC party to have the FTB members, got " + opacMembers(mirrored));
            helper.assertTrue(opacRank(mirrored, ftbOfficer) == PartyMemberRank.MODERATOR && opacRank(mirrored, ftbMember) == PartyMemberRank.MEMBER,
                    "expected the FTB ranks to be mirrored to OPAC, got " + opacRank(mirrored, ftbOfficer) + " / " + opacRank(mirrored, ftbMember));
            helper.assertTrue("FS18 Ftb".equals(opacName(mirrored)), "expected the new OPAC party to have the FTB name, got '" + opacName(mirrored) + "'");
            helper.assertTrue(mirrored.isInvited(ftbInvited.getId()), "expected the pending FTB Teams invitation in OPAC");
            TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(mirrored.getId());
            helper.assertTrue(teamConfig != null && teamConfig.isMember(ftbOwner.getId()), "expected a team config for the new OPAC party");
            helper.assertTrue(ftbRank(ftbParty, ftbOfficer.getId()) == TeamRank.OFFICER && "FS18 Ftb".equals(ftbParty.getDisplayName()),
                    "expected the FTB Teams party to be untouched");
            helper.succeed();
        } finally {
            cleanup.run();
        }
    }

    /**
     * FS19) Where the two mods disagree about a player when the sync first sees their parties, OPAC wins and FTB
     * Teams is adjusted. A party whose owner already owns an FTB party is linked to that one, which then gets the
     * OPAC members (the FTB-only member is removed, the OPAC-only member added); a player who is in another FTB
     * party than their OPAC party says is moved; and the FTB party they were moved out of, which exists in FTB
     * Teams only, gets an OPAC party of its own.
     */
    public static void firstSyncConflictsAreDecidedForOpac(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS19_Owner");
        GameProfile opacOnly = knownPlayer("FS19_OpacOnly");
        GameProfile ftbOnly = knownPlayer("FS19_FtbOnly");
        GameProfile elsewhere = knownPlayer("FS19_Elsewhere");
        GameProfile otherOwner = knownPlayer("FS19_OtherOwner");
        Runnable cleanup = () -> cleanup(server, owner, opacOnly, ftbOnly, elsewhere, otherOwner);
        try {
            FtbTeamsSync sync = sync(helper);
            IServerPartyAPI party;
            PartyTeam ownTeam;
            PartyTeam otherTeam;
            sync.setPaused(true);
            try {
                party = opacCreate(helper, owner, "FS19 Party");
                opacAdd(helper, party, opacOnly, PartyMemberRank.MEMBER);
                opacAdd(helper, party, elsewhere, PartyMemberRank.MEMBER);
                ownTeam = ftbCreate(owner, "FS19 Old Name");
                ftbJoin(ownTeam, ftbOnly);
                otherTeam = ftbCreate(otherOwner, "FS19 Other");
                ftbJoin(otherTeam, elsewhere);
            } finally {
                sync.setPaused(false);
            }
            sync.resync();

            helper.assertTrue(linkedTeam(helper, party) == ownTeam, "expected the party to be linked to the FTB Teams party its owner already had");
            helper.assertTrue(ownTeam.getMembers().equals(ids(owner, opacOnly, elsewhere)), "expected the FTB Teams party to have exactly the OPAC members, got " + ownTeam.getMembers());
            helper.assertTrue(ftbPartyOf(ftbOnly.getId()) == null, "expected the FTB-only member to be back in their personal FTB team");
            helper.assertTrue(ftbPartyOf(elsewhere.getId()) == ownTeam, "expected the player to be moved to the FTB Teams party of their OPAC party");
            helper.assertTrue("FS19 Party".equals(ownTeam.getDisplayName()), "expected the FTB Teams party to get the OPAC name, got '" + ownTeam.getDisplayName() + "'");
            helper.assertTrue(opacMembers(party).equals(ids(owner, opacOnly, elsewhere)), "expected the OPAC party to be untouched, got " + opacMembers(party));
            helper.assertTrue(partyManager(server).getPartyByMember(ftbOnly.getId()) == null, "expected the FTB-only member to be in no OPAC party");

            helper.assertTrue(otherTeam.isValid() && otherTeam.getMembers().equals(ids(otherOwner)), "expected the other FTB Teams party to keep only its owner, got " + otherTeam.getMembers());
            IServerPartyAPI otherParty = partyManager(server).getPartyByOwner(otherOwner.getId());
            helper.assertTrue(otherParty != null && linkedTeam(helper, otherParty) == otherTeam && opacMembers(otherParty).equals(ids(otherOwner)),
                    "expected the other FTB Teams party to get an OPAC party of just its owner");
            helper.succeed();
        } finally {
            cleanup.run();
        }
    }

    /**
     * FS20) One side of a pair vanished while the sync was not looking (the server was down, or the sync was off):
     * nothing is deleted because of it. A party whose FTB party is gone gets a new one; an FTB party whose OPAC
     * party is gone gets a new OPAC party. That happens once: the new pair is then left alone by the periodic merges
     * and by further full reconciliations.
     */
    public static void vanishedSideIsCreatedAgain(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS20_Owner");
        GameProfile member = knownPlayer("FS20_Member");
        IServerPartyAPI[] party = new IServerPartyAPI[2];
        PartyTeam[] team = new PartyTeam[1];
        new Steps(helper, () -> cleanup(server, owner, member))
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Twenty");
                    opacAdd(helper, party[0], member, PartyMemberRank.MEMBER);
                })
                .after(EVENT, () -> {
                    FtbTeamsSync sync = sync(helper);
                    PartyTeam firstTeam = linkedTeam(helper, party[0]);
                    sync.setPaused(true);
                    try {
                        ftbDisband(server, firstTeam);
                    } finally {
                        sync.setPaused(false);
                    }
                    sync.resync();
                    helper.assertTrue(partyManager(server).getPartyById(party[0].getId()) == party[0], "expected the party to survive its FTB Teams party vanishing");
                    PartyTeam secondTeam = linkedTeam(helper, party[0]);
                    helper.assertTrue(secondTeam != firstTeam && secondTeam.getMembers().equals(ids(owner, member)),
                            "expected a new FTB Teams party with both members, got " + secondTeam.getMembers());

                    UUID vanishedPartyId = party[0].getId();
                    sync.setPaused(true);
                    try {
                        disbandPartyQuiet(server, vanishedPartyId);
                    } finally {
                        sync.setPaused(false);
                    }
                    sync.resync();
                    helper.assertTrue(secondTeam.isValid() && ftbPartyOf(member.getId()) == secondTeam, "expected the FTB Teams party to survive its party vanishing");
                    IServerPartyAPI secondParty = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(secondParty != null && !secondParty.getId().equals(vanishedPartyId) && opacMembers(secondParty).equals(ids(owner, member)),
                            "expected a new OPAC party with both members");
                    helper.assertTrue(linkedTeam(helper, secondParty) == secondTeam, "expected the new party to be linked to the FTB Teams party");
                    helper.assertTrue(sync.getStore().byOpacParty(vanishedPartyId) == null, "expected the link of the vanished party to be gone");
                    party[1] = secondParty;
                    team[0] = secondTeam;
                })
                .after(POLLED, () -> {
                    FtbTeamsSync sync = sync(helper);
                    sync.resync();
                    sync.resync();
                    helper.assertTrue(partyManager(server).getPartyByOwner(owner.getId()) == party[1] && opacMembers(party[1]).equals(ids(owner, member)),
                            "expected the new OPAC party to stay as it is");
                    helper.assertTrue(team[0].isValid() && linkedTeam(helper, party[1]) == team[0] && team[0].getMembers().equals(ids(owner, member)),
                            "expected the FTB Teams party to stay as it is and linked");
                })
                .run();
    }

    // ==================== Pending work, limits ====================

    /**
     * FS21) A player FTB Teams has never seen cannot be put into an FTB party: the member is reported as pending and
     * joins as soon as FTB Teams knows them (their first login with FTB Teams installed). The same for a party whose
     * owner FTB Teams does not know: its FTB party is created at the owner's first login. All players are offline.
     */
    public static void playersUnknownToFtbArePendingUntilTheirFirstLogin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS21_Owner");
        GameProfile unknownMember = profile("FS21_Unknown");
        GameProfile unknownOwner = profile("FS21_UnknownOwner");
        IServerPartyAPI[] party = new IServerPartyAPI[2];
        new Steps(helper, () -> cleanup(server, owner, unknownMember, unknownOwner))
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Twentyone");
                    opacAdd(helper, party[0], unknownMember, PartyMemberRank.MEMBER);
                    party[1] = opacCreate(helper, unknownOwner, "FS Twentyone B");
                })
                .after(EVENT, () -> {
                    FtbTeamsSync sync = sync(helper);
                    helper.assertTrue(linkedTeam(helper, party[0]).getMembers().equals(ids(owner)), "expected only the known owner in the FTB Teams party");
                    helper.assertTrue(hasPending(sync, KEY + "pending_player_unknown", unknownMember.getName()), "expected the unknown member to be reported as pending");
                    FtbSyncStore.Pair pair = sync.getStore().byOpacParty(party[1].getId());
                    helper.assertTrue(pair != null && pair.ftbId() == null, "expected the party of the unknown owner to have no FTB Teams party yet");
                    helper.assertTrue(hasPending(sync, KEY + "pending_owner_unknown", unknownOwner.getName()), "expected the unknown owner to be reported as pending");
                    helper.assertTrue(party[0].getMemberInfo(unknownMember.getId()) != null, "expected the OPAC party to be untouched");
                    // their first login with FTB Teams installed
                    ftb().playerLoggedIn(null, unknownMember.getId(), unknownMember.getName());
                    ftb().playerLoggedIn(null, unknownOwner.getId(), unknownOwner.getName());
                })
                .after(EVENT, () -> {
                    FtbTeamsSync sync = sync(helper);
                    helper.assertTrue(linkedTeam(helper, party[0]).getMembers().equals(ids(owner, unknownMember)), "expected the member to join the FTB Teams party at their first login");
                    PartyTeam created = linkedTeam(helper, party[1]);
                    helper.assertTrue(unknownOwner.getId().equals(created.getOwner()) && "FS Twentyone B".equals(created.getDisplayName()),
                            "expected the FTB Teams party to be created at the owner's first login");
                    helper.assertTrue(!hasPending(sync, KEY + "pending_player_unknown", unknownMember.getName())
                            && !hasPending(sync, KEY + "pending_owner_unknown", unknownOwner.getName()), "expected nothing to be pending for the two any more");
                })
                .run();
    }

    /**
     * FS22) FTB Teams' {@code max_party_size} is reached: a member added in OPAC stays an OPAC member (OPAC wins and
     * FTB Teams cannot be made to take them), is reported as pending, and joins the FTB party by the periodic merge
     * once the limit allows it.
     */
    public static void ftbPartySizeLimitLeavesTheJoinPending(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS22_Owner");
        GameProfile member = knownPlayer("FS22_Member");
        GameProfile tooMany = knownPlayer("FS22_TooMany");
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        new Steps(helper, () -> cleanup(server, owner, member, tooMany))
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Twentytwo");
                    opacAdd(helper, party[0], member, PartyMemberRank.MEMBER);
                })
                .after(EVENT, () -> {
                    FtbTeamsSync sync = sync(helper);
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(team.getMembers().size() == 2, "test setup: expected two FTB members");
                    int limitBefore = dev.ftb.mods.ftbteams.config.ServerConfig.MAX_TEAM_SIZE.get();
                    dev.ftb.mods.ftbteams.config.ServerConfig.MAX_TEAM_SIZE.set(2);
                    try {
                        opacAdd(helper, party[0], tooMany, PartyMemberRank.MEMBER);
                        sync.runPendingWork();
                    } finally {
                        dev.ftb.mods.ftbteams.config.ServerConfig.MAX_TEAM_SIZE.set(limitBefore);
                    }
                    helper.assertTrue(party[0].getMemberInfo(tooMany.getId()) != null, "expected the member to stay in the OPAC party");
                    helper.assertTrue(ftbPartyOf(tooMany.getId()) == null && team.getMembers().size() == 2, "expected FTB Teams not to have taken the member over its limit");
                    helper.assertTrue(hasPending(sync, KEY + "pending_ftb_party_full", tooMany.getName()), "expected the member to be reported as pending because the FTB Teams party is full");
                })
                .after(POLLED, () -> {
                    FtbTeamsSync sync = sync(helper);
                    helper.assertTrue(ftbPartyOf(tooMany.getId()) == linkedTeam(helper, party[0]), "expected the member to join the FTB Teams party once the limit allows it");
                    helper.assertTrue(!hasPending(sync, KEY + "pending_ftb_party_full", tooMany.getName()), "expected the member not to be pending any more");
                })
                .run();
    }

    /**
     * FS23) OPAC's {@code maxPartyMembers} is reached: a player who joins the FTB party cannot join the OPAC party,
     * so OPAC wins, the join is undone in FTB Teams, and the (online) player is told why. The option is only read
     * when the server starts, so the party is really filled up to it (from the FTB side, which also shows a whole
     * group of joins of one tick being mirrored).
     */
    public static void opacMemberLimitUndoesTheFtbJoin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS23_Owner");
        GameProfile member = knownPlayer("FS23_Member");
        ServerPlayer tooMany = helper.makeMockServerPlayerInLevel();
        PartyTeam[] team = new PartyTeam[1];
        new Steps(helper, () -> {
            cleanup(server, owner, member, tooMany.getGameProfile());
            removePlayerQuiet(server, tooMany);
        })
                .now(() -> {
                    sync(helper);
                    int limit = ServerConfig.CONFIG.maxPartyMembers.get();
                    helper.assertTrue(limit >= 2 && limit <= 128, "test setup: expected a maxPartyMembers the test can fill a party to, got " + limit);
                    team[0] = ftbCreate(owner, "FS Twentythree");
                    ftbJoin(team[0], member);
                    for (int i = 2; i < limit; i++) ftbJoin(team[0], knownPlayer("FS23_Filler" + i));
                })
                .after(EVENT, () -> {
                    FtbTeamsSync sync = sync(helper);
                    int limit = ServerConfig.CONFIG.maxPartyMembers.get();
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(party != null && party.getMemberCount() == limit && team[0].getMembers().size() == limit,
                            "test setup: expected the party to be full in both mods with " + limit + " members, got "
                                    + (party == null ? "no party" : party.getMemberCount()) + " / " + team[0].getMembers().size());
                    List<Component> chat = captureChat(server, tooMany, () -> {
                        try {
                            team[0].join(tooMany, tooMany.getGameProfile());
                        } catch (CommandSyntaxException e) {
                            throw new IllegalStateException("test setup: FTB Teams did not let the player join: " + e.getMessage());
                        }
                        sync.runPendingWork();
                    });
                    helper.assertTrue(party.getMemberInfo(tooMany.getUUID()) == null && party.getMemberCount() == limit, "expected the OPAC party not to take a member over its limit");
                    helper.assertTrue(ftbPartyOf(tooMany.getUUID()) == null && team[0].getMembers().size() == limit,
                            "expected the join to be undone in FTB Teams, got " + team[0].getMembers().size() + " members");
                    helper.assertTrue(toldCount(server, chat, KEY + "reverted_join_full", "FS Twentythree", limit) == 1,
                            "expected the player to be told once why the join was undone, got " + texts(chat));
                })
                .after(POLLED, () -> helper.assertTrue(ftbPartyOf(tooMany.getUUID()) == null
                                && partyManager(server).getPartyByMember(tooMany.getUUID()) == null && team[0].getMembers().contains(member.getId()),
                        "expected the two sides to stay in agreement after the periodic merge"))
                .run();
    }

    // ==================== Team Claims ====================

    /**
     * FS24) A membership change that comes from FTB Teams drives Team Claims like any other: the team limit follows
     * the member count, and when a member is kicked in FTB Teams their team claim is handed over to the party owner,
     * their team sub-config is removed and the team limit shrinks.
     */
    public static void ftbKickHandsTeamClaimsToTheOwner(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS24_Owner");
        GameProfile claimer = knownPlayer("FS24_Claimer");
        GameProfile third = knownPlayer("FS24_Third");
        int x0 = 24_000;
        PartyTeam[] team = new PartyTeam[1];
        int[] limitWithThree = new int[1];
        new Steps(helper, () -> {
            unclaimQuiet(server, x0, 0);
            cleanup(server, owner, claimer, third);
        })
                .now(() -> {
                    sync(helper);
                    team[0] = ftbCreate(owner, "FS Twentyfour");
                    ftbJoin(team[0], claimer);
                    ftbJoin(team[0], third);
                })
                .after(EVENT, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(party != null && party.getMemberCount() == 3, "expected the OPAC party with three members");
                    TeamClaimManager claimManager = TeamClaimsCommon.getClaimManager();
                    String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
                    int claimerSub = teamSubIndexOf(server, subId, claimer.getId());
                    helper.assertTrue(claimerSub != -1, "expected the member who joined in FTB Teams to have the team sub-config");
                    limitWithThree[0] = claimManager.getBudgetInfo(owner.getId()).teamClaimLimit();
                    helper.assertTrue(claimManager.getBudgetInfo(owner.getId()).memberCount() == 3 && limitWithThree[0] > 0,
                            "expected a team claim limit for three members, got " + claimManager.getBudgetInfo(owner.getId()));
                    ClaimResult<IPlayerChunkClaimAPI> claim = doClaim(server, claimer.getId(), claimerSub, x0, 0);
                    helper.assertTrue(claim.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM, "expected the member's team claim to succeed, got " + claim.getResultType());
                    ftbKick(server, team[0], claimer);
                })
                .after(EVENT + 1, () -> {
                    IServerPartyAPI party = partyManager(server).getPartyByOwner(owner.getId());
                    helper.assertTrue(party.getMemberInfo(claimer.getId()) == null, "expected the member kicked in FTB Teams to be out of the OPAC party");
                    String subId = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId()).getSubConfigId();
                    IPlayerChunkClaimAPI claim = claimsAPI(server).get(OVERWORLD, x0, 0);
                    helper.assertTrue(claim != null && owner.getId().equals(claim.getPlayerId()), "expected the kicked member's team claim to belong to the owner, got "
                            + (claim == null ? "unclaimed" : claim.getPlayerId()));
                    helper.assertTrue(claim.getSubConfigIndex() == teamSubIndexOf(server, subId, owner.getId()), "expected the handed over claim to use the owner's team sub-config");
                    helper.assertTrue(!configManager(server).getLoadedConfig(claimer.getId()).subConfigExists(subId), "expected the kicked member's team sub-config to be removed");
                    TeamClaimManager.BudgetInfo budget = TeamClaimsCommon.getClaimManager().getBudgetInfo(owner.getId());
                    helper.assertTrue(budget.memberCount() == 2 && budget.teamClaimLimit() < limitWithThree[0], "expected the team claim limit to shrink from "
                            + limitWithThree[0] + " with the member count, got " + budget);
                    helper.assertTrue(budget.teamClaims() == 1, "expected the team to still have its one team claim, got " + budget.teamClaims());
                })
                .run();
    }

    // ==================== Removals, conflicts and failures ====================

    /**
     * FS28) A removal the sync saw is remembered with the pair and not just for a tick: it is saved and loaded with
     * the sync state and acted on by whatever merges the pair next (here a full reconciliation in the same tick,
     * which drops the queued notes first). A party destroyed in OPAC takes its FTB party with it, an FTB party
     * disbanded in FTB Teams takes its OPAC party with it, and neither is mistaken for a side that vanished
     * unnoticed, which would be made again (see FS20).
     */
    public static void seenRemovalsAreRememberedWithThePair(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile opacOwner = knownPlayer("FS28_OpacOwner");
        GameProfile opacMember = knownPlayer("FS28_OpacMember");
        GameProfile ftbOwner = knownPlayer("FS28_FtbOwner");
        GameProfile ftbMember = knownPlayer("FS28_FtbMember");
        PartyTeam[] ftbTeam = new PartyTeam[1];
        new Steps(helper, () -> cleanup(server, opacOwner, opacMember, ftbOwner, ftbMember))
                .now(() -> {
                    sync(helper);
                    opacAdd(helper, opacCreate(helper, opacOwner, "FS28 Opac"), opacMember, PartyMemberRank.MEMBER);
                    ftbTeam[0] = ftbCreate(ftbOwner, "FS28 Ftb");
                    ftbJoin(ftbTeam[0], ftbMember);
                })
                .after(EVENT, () -> {
                    FtbTeamsSync sync = sync(helper);
                    FtbSyncStore store = sync.getStore();
                    IServerPartyAPI opacParty = partyManager(server).getPartyByOwner(opacOwner.getId());
                    PartyTeam mirroredTeam = linkedTeam(helper, opacParty);
                    IServerPartyAPI mirroredParty = partyManager(server).getPartyByOwner(ftbOwner.getId());
                    helper.assertTrue(mirroredParty != null && linkedTeam(helper, mirroredParty) == ftbTeam[0], "test setup: expected the OPAC party of the FTB Teams party");
                    UUID opacPartyId = opacParty.getId();
                    UUID mirroredPartyId = mirroredParty.getId();

                    partyManager(server).removePartyById(opacPartyId);
                    ftbDisband(server, ftbTeam[0]);
                    FtbSyncStore.Pair opacPair = store.byOpacParty(opacPartyId);
                    FtbSyncStore.Pair ftbPair = store.byOpacParty(mirroredPartyId);
                    helper.assertTrue(opacPair != null && opacPair.isOpacRemoved() && !opacPair.isFtbRemoved(), "expected the pair to say that its OPAC party was removed");
                    helper.assertTrue(ftbPair != null && ftbPair.isFtbRemoved() && !ftbPair.isOpacRemoved(), "expected the pair to say that its FTB Teams party was deleted");
                    FtbSyncStore loaded = FtbSyncStore.load(store.save(new CompoundTag(), server.registryAccess()), server.registryAccess());
                    helper.assertTrue(loaded.byOpacParty(opacPartyId).isOpacRemoved() && !loaded.byOpacParty(opacPartyId).isFtbRemoved()
                                    && loaded.byOpacParty(mirroredPartyId).isFtbRemoved() && !loaded.byOpacParty(mirroredPartyId).isOpacRemoved(),
                            "expected both notes to be saved and loaded with their pairs");

                    sync.resync();
                    helper.assertTrue(!mirroredTeam.isValid() && ftbPartyOf(opacOwner.getId()) == null && ftbPartyOf(opacMember.getId()) == null,
                            "expected the FTB Teams party of the destroyed party to be disbanded");
                    helper.assertTrue(partyManager(server).getPartyById(mirroredPartyId) == null, "expected the party of the disbanded FTB Teams party to be removed");
                    helper.assertTrue(store.byOpacParty(opacPartyId) == null && store.byOpacParty(mirroredPartyId) == null, "expected both pairs to be gone");
                    helper.assertTrue(inNoParty(server, opacOwner, opacMember, ftbOwner, ftbMember), "expected neither party to be made again by the full reconciliation");
                })
                .after(POLLED, () -> {
                    sync(helper).resync();
                    helper.assertTrue(inNoParty(server, opacOwner, opacMember, ftbOwner, ftbMember), "expected neither party to come back later");
                })
                .run();
    }

    /**
     * FS29) First sync, and a player OPAC has as a member of one party owns another party in FTB Teams, which FTB
     * Teams does not let its owner leave: OPAC wins all the same. The FTB party is handed to its highest ranked
     * other member, the player moves to the FTB party of their OPAC party, and the FTB party they left, which only
     * exists in FTB Teams, gets an OPAC party of its new owner.
     */
    public static void ftbOwnerInAnotherPartyHandsItOverAndMoves(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS29_Owner");
        GameProfile mover = knownPlayer("FS29_Mover");
        GameProfile officer = knownPlayer("FS29_Officer");
        GameProfile plain = knownPlayer("FS29_Plain");
        Runnable cleanup = () -> cleanup(server, owner, mover, officer, plain);
        try {
            FtbTeamsSync sync = sync(helper);
            IServerPartyAPI party;
            PartyTeam moversTeam;
            sync.setPaused(true);
            try {
                party = opacCreate(helper, owner, "FS29 Party");
                opacAdd(helper, party, mover, PartyMemberRank.MEMBER);
                moversTeam = ftbCreate(mover, "FS29 Movers");
                ftbJoin(moversTeam, plain);
                ftbJoin(moversTeam, officer);
                moversTeam.addMember(officer.getId(), TeamRank.OFFICER);//as PartyTeam.promote does
            } finally {
                sync.setPaused(false);
            }
            sync.resync();

            PartyTeam team = linkedTeam(helper, party);
            helper.assertTrue(team != moversTeam && team.getMembers().equals(ids(owner, mover)) && ftbPartyOf(mover.getId()) == team,
                    "expected the player to be moved to the FTB Teams party of their OPAC party, got " + team.getMembers());
            helper.assertTrue(opacMembers(party).equals(ids(owner, mover)), "expected the OPAC party to be untouched, got " + opacMembers(party));
            helper.assertTrue(moversTeam.isValid() && officer.getId().equals(moversTeam.getOwner()) && moversTeam.getMembers().equals(ids(officer, plain)),
                    "expected the FTB Teams party the player owned to be handed to its officer, got owner " + moversTeam.getOwner() + " and " + moversTeam.getMembers());
            IServerPartyAPI handedOver = partyManager(server).getPartyByOwner(officer.getId());
            helper.assertTrue(handedOver != null && linkedTeam(helper, handedOver) == moversTeam && opacMembers(handedOver).equals(ids(officer, plain)),
                    "expected the handed over FTB Teams party to get an OPAC party of its new owner");
            helper.assertTrue(sync.resync() == 0, "expected a second full reconciliation to change nothing");
            helper.succeed();
        } finally {
            cleanup.run();
        }
    }

    /**
     * FS30) OPAC's {@code maxPartyInvites} is reached: an invitation made in FTB Teams cannot exist in OPAC, so OPAC
     * wins, the invitation is withdrawn in FTB Teams, and the (online) owner of the FTB party is told why. Like the
     * member limit, the option is only read when the server starts, so the invitations are really filled up to it.
     */
    public static void opacInviteLimitWithdrawsTheFtbInvite(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer ownerPlayer = helper.makeMockServerPlayerInLevel();
        GameProfile owner = ownerPlayer.getGameProfile();
        GameProfile tooMany = knownPlayer("FS30_TooMany");
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        new Steps(helper, () -> {
            cleanup(server, owner, tooMany);
            removePlayerQuiet(server, ownerPlayer);
        })
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Thirty");
                })
                .after(EVENT, () -> {
                    int limit = ServerConfig.CONFIG.maxPartyInvites.get();
                    helper.assertTrue(limit >= 1 && limit <= 128, "test setup: expected a maxPartyInvites the test can fill a party to, got " + limit);
                    PartyTeam team = linkedTeam(helper, party[0]);
                    for (int i = 0; i < limit; i++) ftbInviteWithoutInviter(team, profile("FS30_Invited" + i));
                })
                .after(POLLED, () -> {
                    FtbTeamsSync sync = sync(helper);
                    int limit = ServerConfig.CONFIG.maxPartyInvites.get();
                    PartyTeam team = linkedTeam(helper, party[0]);
                    helper.assertTrue(party[0].getInviteCount() == limit, "test setup: expected " + limit + " invitations in OPAC, got " + party[0].getInviteCount());
                    List<Component> chat = captureChat(server, ownerPlayer, () -> {
                        ftbInviteWithoutInviter(team, tooMany);
                        sync.resync();
                    });
                    helper.assertTrue(!party[0].isInvited(tooMany.getId()) && party[0].getInviteCount() == limit, "expected OPAC not to take an invitation over its limit");
                    helper.assertTrue(ftbRank(team, tooMany.getId()) != TeamRank.INVITED, "expected the invitation to be withdrawn in FTB Teams, got rank " + ftbRank(team, tooMany.getId()));
                    helper.assertTrue(toldCount(server, chat, KEY + "reverted_invite_limit", tooMany.getName(), limit) == 1,
                            "expected the owner to be told once why the invitation was withdrawn, got " + texts(chat));
                })
                .after(POLLED, () -> {
                    int limit = ServerConfig.CONFIG.maxPartyInvites.get();
                    PartyTeam team = linkedTeam(helper, party[0]);
                    long ftbInvites = team.getPlayersByRank(TeamRank.NONE).values().stream().filter(rank -> rank == TeamRank.INVITED).count();
                    helper.assertTrue(party[0].getInviteCount() == limit && ftbInvites == limit,
                            "expected the other invitations to stay in both mods, got " + party[0].getInviteCount() + " / " + ftbInvites);
                })
                .run();
    }

    /**
     * FS31) An invitation is announced to the invited player by the mod it was made in and not a second time by the
     * other one: mirroring an OPAC invitation to FTB Teams, and an FTB invitation (which FTB Teams itself announces
     * with its accept and decline buttons) to OPAC, sends the online invited player nothing.
     */
    public static void mirroredInvitesDoNotPromptThePlayerAgain(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile opacOwner = knownPlayer("FS31_OpacOwner");
        ServerPlayer opacGuest = helper.makeMockServerPlayerInLevel();
        ServerPlayer ftbOwnerPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer ftbGuest = helper.makeMockServerPlayerInLevel();
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        PartyTeam[] team = new PartyTeam[1];
        new Steps(helper, () -> {
            cleanup(server, opacOwner, opacGuest.getGameProfile(), ftbOwnerPlayer.getGameProfile(), ftbGuest.getGameProfile());
            removePlayerQuiet(server, opacGuest);
            removePlayerQuiet(server, ftbOwnerPlayer);
            removePlayerQuiet(server, ftbGuest);
        })
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, opacOwner, "FS31 Opac");
                    try {
                        team[0] = ftb().createParty(ftbOwnerPlayer, "FS31 Ftb");
                    } catch (CommandSyntaxException e) {
                        throw new IllegalStateException("test setup: FTB Teams did not create the party: " + e.getMessage());
                    }
                })
                .after(EVENT, () -> {
                    FtbTeamsSync sync = sync(helper);
                    IServerPartyAPI mirrored = partyManager(server).getPartyByOwner(ftbOwnerPlayer.getUUID());
                    helper.assertTrue(mirrored != null, "test setup: expected the OPAC party of the FTB Teams party");

                    List<Component> opacInviteChat = captureChat(server, opacGuest, () -> {
                        helper.assertTrue(party[0].invitePlayer(opacGuest.getUUID(), opacGuest.getGameProfile().getName()) != null, "test setup: expected the invitation to be made");
                        sync.runPendingWork();
                    });
                    helper.assertTrue(ftbRank(linkedTeam(helper, party[0]), opacGuest.getUUID()) == TeamRank.INVITED, "expected the OPAC invitation to exist in FTB Teams");
                    helper.assertTrue(opacInviteChat.isEmpty(), "expected mirroring an OPAC invitation to send the invited player nothing, got " + texts(opacInviteChat));

                    List<Component> ftbPrompt = captureChat(server, ftbGuest, () -> {
                        try {
                            team[0].invite(ftbOwnerPlayer, List.of(ftbGuest.getGameProfile()));
                        } catch (CommandSyntaxException e) {
                            throw new IllegalStateException("test setup: FTB Teams did not invite: " + e.getMessage());
                        }
                    });
                    helper.assertTrue(!ftbPrompt.isEmpty(), "test setup: expected FTB Teams to announce its invitation to the invited player");
                    List<Component> ftbInviteChat = captureChat(server, ftbGuest, sync::resync);
                    helper.assertTrue(mirrored.isInvited(ftbGuest.getUUID()), "expected the FTB Teams invitation to exist in OPAC");
                    helper.assertTrue(ftbInviteChat.isEmpty(), "expected mirroring an FTB Teams invitation to send the invited player nothing, got " + texts(ftbInviteChat));
                })
                .run();
    }

    /**
     * FS32) What cannot be synced yet is told to the players concerned once, when it is first found, and not again
     * by every merge that still finds it pending; the status command counts it.
     */
    public static void pendingWorkIsToldOnce(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer ownerPlayer = helper.makeMockServerPlayerInLevel();
        GameProfile owner = ownerPlayer.getGameProfile();
        GameProfile unknown = profile("FS32_Unknown");
        ServerGamePacketListenerImpl ownConnection = ownerPlayer.connection;
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        ChatRecorder[] recorder = new ChatRecorder[1];
        new Steps(helper, () -> {
            ownerPlayer.connection = ownConnection;
            cleanup(server, owner, unknown);
            removePlayerQuiet(server, ownerPlayer);
        })
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "FS Thirtytwo");
                })
                .after(EVENT, () -> {
                    linkedTeam(helper, party[0]);
                    recorder[0] = new ChatRecorder(server, ownerPlayer);//also installs itself as the player's connection
                    opacAdd(helper, party[0], unknown, PartyMemberRank.MEMBER);
                })
                .after(2 * POLLED, () -> {
                    FtbTeamsSync sync = sync(helper);
                    sync.resync();
                    ownerPlayer.connection = ownConnection;
                    helper.assertTrue(hasPending(sync, KEY + "pending_player_unknown", unknown.getName()), "expected the unknown member to be pending");
                    int told = toldCount(server, recorder[0].chat, KEY + "pending_player_unknown", unknown.getName());
                    helper.assertTrue(told == 1, "expected the owner to be told once that the member is pending, got " + told + " times: " + texts(recorder[0].chat));

                    TeamClaimsHardeningTestCases.CapturingCommandSource capture = new TeamClaimsHardeningTestCases.CapturingCommandSource();
                    try {
                        server.getCommands().getDispatcher().execute("teamclaims ftbsync status", commandSource(helper, capture).withPermission(2));
                    } catch (CommandSyntaxException e) {
                        throw new IllegalStateException("expected the status command to run: " + e.getMessage());
                    }
                    helper.assertTrue(capture.received(localized(server, KEY + "status_pending", sync.getPendingCount())),
                            "expected the status to give the number of pending entries, got " + capture.all());
                })
                .run();
    }

    /**
     * FS33) An unexpected exception while one pair is merged (here thrown by a listener of "another mod" on FTB
     * Teams' {@code PROPERTIES_CHANGED} event, which the sync fires like FTB's own rename does) does not get out of
     * the sync and does not stop the pairs after it. It is reported as pending for that pair, and the next merge
     * finishes the job.
     */
    public static void failingPairDoesNotStopTheOthers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile failingOwner = knownPlayer("FS33_FailingOwner");
        GameProfile otherOwner = knownPlayer("FS33_OtherOwner");
        IServerPartyAPI[] party = new IServerPartyAPI[2];
        String error = new IllegalStateException("FS33: the listener of another mod fails").toString();
        new Steps(helper, () -> cleanup(server, failingOwner, otherOwner))
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, failingOwner, "FS33 Failing");
                    party[1] = opacCreate(helper, otherOwner, "FS33 Other");
                })
                .after(EVENT, () -> {
                    FtbTeamsSync sync = sync(helper);
                    UUID failingTeamId = linkedTeam(helper, party[0]).getId();
                    linkedTeam(helper, party[1]);
                    Consumer<TeamPropertiesChangedEvent> failingListener = event -> {
                        if (event.getTeam().getId().equals(failingTeamId)) throw new IllegalStateException("FS33: the listener of another mod fails");
                    };
                    TeamEvent.PROPERTIES_CHANGED.register(failingListener);
                    try {
                        setOpacName(helper, server, failingOwner, "FS33 Failing Renamed");
                        setOpacName(helper, server, otherOwner, "FS33 Other Renamed");
                        sync.runPendingWork();
                    } finally {
                        TeamEvent.PROPERTIES_CHANGED.unregister(failingListener);
                    }
                    helper.assertTrue(sync.isRunning(), "expected the sync to go on after the failure, state: " + sync.getStateKey());
                    helper.assertTrue(hasPending(sync, KEY + "pending_error", error), "expected the failure to be reported as pending for its pair");
                    helper.assertTrue("FS33 Other Renamed".equals(linkedTeam(helper, party[1]).getDisplayName()),
                            "expected the pair after the failing one to be merged, got '" + linkedTeam(helper, party[1]).getDisplayName() + "'");
                })
                .after(POLLED, () -> {
                    FtbTeamsSync sync = sync(helper);
                    helper.assertTrue("FS33 Failing Renamed".equals(linkedTeam(helper, party[0]).getDisplayName()),
                            "expected the next merge to finish the rename, got '" + linkedTeam(helper, party[0]).getDisplayName() + "'");
                    helper.assertTrue(!hasPending(sync, KEY + "pending_error", error), "expected the failure not to be pending any more");
                })
                .run();
    }

    /**
     * FS34) An FTB party that is deleted in FTB Teams does not take its OPAC party with it while that has a member
     * who never was in the FTB party (here a player FTB Teams does not know yet): the mods disagreed about who is in
     * the party, so OPAC wins. The party, its members and its team config stay, a new FTB party is created and
     * linked, the member is still pending, and the online owner is told once why the FTB party is back. First for
     * the owner leaving their FTB party as its last member, then, with a second member who is in both mods, for
     * {@code force-disband}. Neither repeats itself: a full reconciliation right afterwards changes nothing, and
     * the periodic merges after it apply no change. Counts every change the sync makes, so it must run alone (own
     * batch).
     */
    public static void ftbDeletionKeepsPartyWithMembersNotInFtb(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer ownerPlayer = helper.makeMockServerPlayerInLevel();
        GameProfile owner = ownerPlayer.getGameProfile();
        GameProfile unknown = profile("FS34_Unknown");
        GameProfile mirrored = knownPlayer("FS34_Mirrored");
        String partyName = "FS Thirtyfour";
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        PartyTeam[] team = new PartyTeam[1];
        long[] settled = new long[1];
        new Steps(helper, () -> {
            cleanup(server, owner, unknown, mirrored);
            removePlayerQuiet(server, ownerPlayer);
        })
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, partyName);
                    opacAdd(helper, party[0], unknown, PartyMemberRank.MEMBER);
                })
                .after(EVENT, () -> {
                    FtbTeamsSync sync = sync(helper);
                    PartyTeam firstTeam = linkedTeam(helper, party[0]);
                    helper.assertTrue(firstTeam.getMembers().equals(ids(owner)), "test setup: expected only the owner in the FTB Teams party, got " + firstTeam.getMembers());
                    helper.assertTrue(hasPending(sync, KEY + "pending_player_unknown", unknown.getName()), "test setup: expected the unknown member to be pending");

                    // "/ftbteams party leave" of the owner, who is the last member FTB Teams has
                    List<Component> chat = captureChat(server, ownerPlayer, () -> {
                        ftbLeave(firstTeam, owner);
                        sync.runPendingWork();
                    });
                    helper.assertTrue(!firstTeam.isValid(), "test setup: expected FTB Teams to delete the party its last member left");
                    team[0] = assertPartyKept(helper, server, party[0], firstTeam, owner, partyName, unknown, ids(owner, unknown), ids(owner));
                    helper.assertTrue(toldCount(server, chat, KEY + "reverted_disband", partyName) == 1,
                            "expected the owner to be told once why the FTB Teams party is back, got " + texts(chat));
                    helper.assertTrue(toldCount(server, chat, KEY + "pending_player_unknown", unknown.getName()) == 0,
                            "expected the owner not to be told again that the member is pending, got " + texts(chat));
                    long changes = sync.resync();
                    helper.assertTrue(changes == 0, "expected a full reconciliation to change nothing after the FTB Teams party was created again, got " + changes + " change(s)");
                    helper.assertTrue(linkedTeam(helper, party[0]) == team[0], "expected the new FTB Teams party to stay linked");

                    opacAdd(helper, party[0], mirrored, PartyMemberRank.MEMBER);
                })
                .after(POLLED, () -> {
                    FtbTeamsSync sync = sync(helper);
                    PartyTeam secondTeam = linkedTeam(helper, party[0]);
                    helper.assertTrue(secondTeam == team[0] && secondTeam.getMembers().equals(ids(owner, mirrored)),
                            "expected the same FTB Teams party, now with the second member, got " + secondTeam.getMembers());

                    // "/ftbteams force-disband": kicks the member who is in both mods, then the owner leaves
                    List<Component> chat = captureChat(server, ownerPlayer, () -> {
                        ftbDisband(server, secondTeam);
                        sync.runPendingWork();
                    });
                    helper.assertTrue(!secondTeam.isValid(), "test setup: expected FTB Teams to delete the disbanded party");
                    team[0] = assertPartyKept(helper, server, party[0], secondTeam, owner, partyName, unknown, ids(owner, unknown, mirrored), ids(owner, mirrored));
                    helper.assertTrue(toldCount(server, chat, KEY + "reverted_disband", partyName) == 1,
                            "expected the owner to be told once why the FTB Teams party is back, got " + texts(chat));
                    long changes = sync.resync();
                    helper.assertTrue(changes == 0, "expected a full reconciliation to change nothing after the FTB Teams party was created again, got " + changes + " change(s)");
                    settled[0] = sync.getAppliedChangeCount();
                })
                .after(2 * POLLED, () -> {
                    FtbTeamsSync sync = sync(helper);
                    helper.assertTrue(sync.getAppliedChangeCount() == settled[0], "expected the periodic merges to change nothing afterwards, but "
                            + (sync.getAppliedChangeCount() - settled[0]) + " change(s) were applied");
                    helper.assertTrue(linkedTeam(helper, party[0]) == team[0] && team[0].getMembers().equals(ids(owner, mirrored)),
                            "expected the FTB Teams party to stay as it is, got " + team[0].getMembers());
                    helper.assertTrue(opacMembers(party[0]).equals(ids(owner, unknown, mirrored)), "expected the party to stay as it is, got " + opacMembers(party[0]));
                    helper.assertTrue(hasPending(sync, KEY + "pending_player_unknown", unknown.getName()) && sync.getPendingCount() == 1,
                            "expected the unknown member to be the one thing that is pending, got " + sync.getPendingCount() + " pending");
                })
                .run();
    }

    /**
     * What FS34 expects after the FTB party of {@code party} was deleted in FTB Teams although {@code pendingMember}
     * was not in it: the OPAC party with its members and its team config is untouched, and a new FTB party of the
     * owner with the party's name is linked to it and has the members FTB Teams can have.
     *
     * @return the new FTB party
     */
    private static PartyTeam assertPartyKept(GameTestHelper helper, MinecraftServer server, IServerPartyAPI party, PartyTeam deletedTeam,
            GameProfile owner, String partyName, GameProfile pendingMember, Set<UUID> expectedOpacMembers, Set<UUID> expectedFtbMembers) {
        FtbTeamsSync sync = sync(helper);
        helper.assertTrue(partyManager(server).getPartyById(party.getId()) == party && partyManager(server).getPartyByOwner(owner.getId()) == party,
                "expected the party to survive the deletion of its FTB Teams party");
        helper.assertTrue(opacMembers(party).equals(expectedOpacMembers), "expected the party to keep its members, got " + opacMembers(party));
        TeamConfig teamConfig = TeamClaimsCommon.getTeamConfigManager().getTeamConfig(party.getId());
        helper.assertTrue(teamConfig != null && teamConfig.getMembers().equals(expectedOpacMembers), "expected the party to keep its team config with all members");
        helper.assertTrue(configManager(server).getLoadedConfig(pendingMember.getId()).subConfigExists(teamConfig.getSubConfigId()),
                "expected the member who is not in FTB Teams yet to keep the team sub-config");
        PartyTeam newTeam = linkedTeam(helper, party);
        helper.assertTrue(newTeam != deletedTeam && owner.getId().equals(newTeam.getOwner()) && ftbPartyOf(owner.getId()) == newTeam,
                "expected a new FTB Teams party of the owner to be linked to the party");
        helper.assertTrue(newTeam.getMembers().equals(expectedFtbMembers), "expected the new FTB Teams party to have the members FTB Teams can have, got " + newTeam.getMembers());
        helper.assertTrue(partyName.equals(newTeam.getDisplayName()), "expected the new FTB Teams party to have the party's name, got '" + newTeam.getDisplayName() + "'");
        FtbSyncStore.Pair pair = sync.getStore().byOpacParty(party.getId());
        helper.assertTrue(pair != null && !pair.isFtbRemoved() && !pair.isOpacRemoved(), "expected nothing to be noted as removed for the pair any more");
        helper.assertTrue(hasPending(sync, KEY + "pending_player_unknown", pendingMember.getName()), "expected the member who is not in FTB Teams yet to still be pending");
        return newTeam;
    }

    // ==================== Persistence, commands ====================

    /**
     * FS25) The sync state is world saved data, and a pair with its whole baseline survives a save/load round trip:
     * link, owner, members with ranks, invitations and both names.
     */
    public static void pairsSurviveSaveAndLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile owner = knownPlayer("FS25_Owner");
        GameProfile admin = knownPlayer("FS25_Admin");
        GameProfile invited = knownPlayer("FS25_Invited");
        IServerPartyAPI[] party = new IServerPartyAPI[1];
        new Steps(helper, () -> cleanup(server, owner, admin, invited))
                .now(() -> {
                    sync(helper);
                    party[0] = opacCreate(helper, owner, "Xy");
                    opacAdd(helper, party[0], admin, PartyMemberRank.ADMIN);
                    party[0].invitePlayer(invited.getId(), invited.getName());
                })
                .after(EVENT, () -> {
                    FtbSyncStore store = sync(helper).getStore();
                    helper.assertTrue(server.overworld().getDataStorage().get(
                                    new net.minecraft.world.level.saveddata.SavedData.Factory<>(FtbSyncStore::new, FtbSyncStore::load, null),
                                    FtbSyncStore.DATA_NAME) == store, "expected the sync state to be saved data of the overworld");
                    FtbSyncStore.Pair pair = store.byOpacParty(party[0].getId());
                    helper.assertTrue(pair != null && pair.isMerged() && pair.ftbId() != null, "test setup: expected a merged pair");

                    FtbSyncStore loaded = FtbSyncStore.load(store.save(new CompoundTag(), server.registryAccess()), server.registryAccess());
                    FtbSyncStore.Pair loadedPair = loaded.byOpacParty(party[0].getId());
                    helper.assertTrue(loadedPair != null && loaded.byFtbTeam(pair.ftbId()) == loadedPair, "expected the link to be loaded in both directions");
                    helper.assertTrue(pair.ftbId().equals(loadedPair.ftbId()) && loadedPair.isMerged(), "expected the loaded pair to be linked and merged");
                    helper.assertTrue(owner.getId().equals(loadedPair.owner()), "expected the owner to be loaded, got " + loadedPair.owner());
                    helper.assertTrue(loadedPair.members().equals(pair.members()) && loadedPair.members().size() == 2, "expected the members to be loaded, got " + loadedPair.members());
                    helper.assertTrue(new FtbSyncStore.MemberState(PartyMemberRank.ADMIN, true).equals(loadedPair.member(admin.getId())),
                            "expected the admin's ranks to be loaded, got " + loadedPair.member(admin.getId()));
                    helper.assertTrue(loadedPair.invites().equals(ids(invited)), "expected the invitation to be loaded, got " + loadedPair.invites());
                    helper.assertTrue("Xy".equals(loadedPair.opacName()) && "Xy_".equals(loadedPair.ftbName()),
                            "expected both names to be loaded, got '" + loadedPair.opacName() + "' / '" + loadedPair.ftbName() + "'");
                    helper.assertTrue(loaded.linkedCount() == store.linkedCount() && loaded.size() == store.size(), "expected every pair to be loaded");
                })
                .run();
    }

    /**
     * FS26) {@code /teamclaims ftbsync status} and {@code resync} work for permission level 2 (also from the console)
     * and are not available below it.
     */
    public static void statusAndResyncCommands(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FtbTeamsSync sync = sync(helper);
        TeamClaimsHardeningTestCases.CapturingCommandSource capture = new TeamClaimsHardeningTestCases.CapturingCommandSource();
        CommandSourceStack admin = commandSource(helper, capture).withPermission(2);
        try {
            int status = server.getCommands().getDispatcher().execute("teamclaims ftbsync status", admin);
            helper.assertTrue(status == 1, "expected the status command to report a running sync, got " + status + ": " + capture.all());
            helper.assertTrue(capture.received(localized(server, KEY + "status_active")), "expected the status to say that the sync is active, got " + capture.all());
            helper.assertTrue(capture.received(localized(server, KEY + "status_pairs", sync.getLinkedPairCount())), "expected the status to give the number of linked parties, got " + capture.all());
            int messagesBefore = capture.messages().size();
            int resync = server.getCommands().getDispatcher().execute("teamclaims ftbsync resync", admin);
            helper.assertTrue(resync == 1 && capture.messages().size() == messagesBefore + 1, "expected the resync command to run and report, got " + resync + ": " + capture.all());
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("expected the ftbsync commands to run for permission level 2: " + e.getMessage());
        }
        for (String command : List.of("teamclaims ftbsync status", "teamclaims ftbsync resync")) {
            ParseResults<CommandSourceStack> parsed = server.getCommands().getDispatcher().parse(command, admin.withPermission(1));
            helper.assertTrue(parsed.getReader().canRead() || parsed.getContext().getCommand() == null,
                    "expected '" + command + "' not to be available below permission level 2");
        }
        helper.succeed();
    }
}
