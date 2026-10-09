package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.common.packet.PacketHandlerFull;
import xaero.pac.common.packet.parties.ClientboundPartyBudgetPacket;
import xaero.pac.common.packet.parties.ClientboundPartyInvitesPacket;
import xaero.pac.common.packet.parties.PartyBudgetData;
import xaero.pac.common.packet.parties.PartyBudgetSender;
import xaero.pac.common.packet.parties.ServerboundPartyInviteDeclinePacket;
import xaero.pac.common.packet.parties.ServerboundPartyInvitesRequestPacket;
import xaero.pac.common.packet.payload.PacketPayload;
import xaero.pac.common.packet.payload.PacketPayloadCodec;
import xaero.pac.common.packet.type.PacketType;
import xaero.pac.common.parties.party.ReceivedPartyInvite;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI.SetResult;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.common.server.player.data.ServerPlayerData;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimManager.BudgetSettings;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.gametest.TeamClaimsBudgetTestCases.Team;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.*;
import static xaero.pac.teamclaims.gametest.TeamClaimsOverviewTestCases.removePlayerQuiet;

/**
 * Dev/test-only, loader-neutral test bodies for the party screen's four packets (ids 50 to 53): the request for the
 * received invitations (50) and its answer (51), declining an invitation (52), and the claim and forceload budgets
 * (53) that the request also triggers. Same conventions as {@link TeamClaimsBudgetTestCases}: a chunk offset of 1000
 * per test starting at 90000, cleanup of parties, players, claims and clock on every path, every test registered with
 * the loader's empty structure template and the default timeout.
 * <p>
 * The codec tests work on buffers. The server tests deliver a packet the way the network does (encode, then decode
 * with {@link PacketPayloadCodec}, then the registered server handler) and watch what the server sends to a mock
 * player: {@link PacketRecorder} replaces the connection of the player for the duration of an action and keeps the
 * OPAC packet objects inside the custom-payload packets, which is the same on both loaders. The one loader-specific
 * part is passed in by the wrappers as a {@link RecorderFactory}: NeoForge asks the connection whether it has a channel
 * before it sends, which a mock connection has none to answer.
 * <p>
 * Every test runs in one go, in the tick it starts in, except {@link #invitesRequestRateLimitOverRealTicks}, which
 * waits for 20 server ticks and is registered with {@link #RATE_LIMIT_TIMEOUT_TICKS}.
 */
public final class TeamClaimsPacketTestCases {

    /** Timeout {@link #invitesRequestRateLimitOverRealTicks} must be registered with: it waits for 20 server ticks. */
    public static final int RATE_LIMIT_TIMEOUT_TICKS = 100;

    private static final long HOUR = 3_600_000L;
    private static final int ID_REQUEST = 50;
    private static final int ID_INVITES = 51;
    private static final int ID_DECLINE = 52;
    private static final int ID_BUDGET = 53;

    private TeamClaimsPacketTestCases() {}

    // ==================== Loader hook ====================

    /** Makes the recording connection of a mock player; the NeoForge wrappers pass one that answers the channel check. */
    @FunctionalInterface
    public interface RecorderFactory {
        PacketRecorder create(MinecraftServer server, ServerPlayer player);
    }

    /**
     * A connection that keeps the OPAC packets that would be sent to the player (the packet objects inside their
     * custom-payload packets) in the order they were sent, and drops everything else.
     */
    public static class PacketRecorder extends ServerGamePacketListenerImpl {
        private final List<Object> packets = new ArrayList<>();

        public PacketRecorder(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
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
            if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof PacketPayload<?> payload)
                packets.add(payload.getPacket());
        }
    }

    // ==================== Tests: registration and codecs ====================

    /** The four packets are registered with ids 50 to 53, their codecs, and the handler of the side that receives them. */
    public static void packetsAreRegisteredWithIds50To53(GameTestHelper helper) {
        PacketHandlerFull handler = packetHandler();
        assertRegistered(helper, handler, ID_REQUEST, ServerboundPartyInvitesRequestPacket.class,
                ServerboundPartyInvitesRequestPacket.Codec.class, ServerboundPartyInvitesRequestPacket.ServerHandler.class, true);
        assertRegistered(helper, handler, ID_INVITES, ClientboundPartyInvitesPacket.class,
                ClientboundPartyInvitesPacket.Codec.class, ClientboundPartyInvitesPacket.ClientHandler.class, false);
        assertRegistered(helper, handler, ID_DECLINE, ServerboundPartyInviteDeclinePacket.class,
                ServerboundPartyInviteDeclinePacket.Codec.class, ServerboundPartyInviteDeclinePacket.ServerHandler.class, true);
        assertRegistered(helper, handler, ID_BUDGET, ClientboundPartyBudgetPacket.class,
                ClientboundPartyBudgetPacket.Codec.class, ClientboundPartyBudgetPacket.ClientHandler.class, false);
        helper.succeed();
    }

    /** Request (50): no content, so nothing is written, an empty buffer decodes, and anything left over is rejected. */
    public static void requestPacketCodec(GameTestHelper helper) {
        ServerboundPartyInvitesRequestPacket.Codec codec = new ServerboundPartyInvitesRequestPacket.Codec();
        FriendlyByteBuf out = buffer();
        codec.accept(new ServerboundPartyInvitesRequestPacket(), out);
        helper.assertTrue(out.readableBytes() == 0, "expected the request to write no bytes, got " + out.readableBytes());
        helper.assertTrue(codec.apply(out) != null, "expected an empty buffer to decode to a request");
        FriendlyByteBuf extra = buffer();
        extra.writeByte(1);
        helper.assertTrue(codec.apply(extra) == null, "expected a request with a trailing byte to be rejected");
        helper.assertTrue(codec.apply(wrap(new byte[64])) == null, "expected a request with 64 trailing bytes to be rejected");
        helper.succeed();
    }

    /**
     * Invitations (51): equal content after a round trip, at most 64 entries (more are cut off when writing and
     * refused when reading), names cut to 128 and 64 characters when writing and refused when longer, a missing name is
     * written as an empty one, and a count that is negative or too large, missing or trailing bytes, an empty buffer and
     * more than 65536 bytes are all rejected.
     */
    public static void invitesPacketCodec(GameTestHelper helper) {
        ClientboundPartyInvitesPacket.Codec codec = new ClientboundPartyInvitesPacket.Codec();
        List<ReceivedPartyInvite> invites = List.of(new ReceivedPartyInvite(UUID.randomUUID(), "Alpha Party", "OwnerA"),
                new ReceivedPartyInvite(UUID.randomUUID(), "Équipe Ünïque 東京", "Owner_B"));
        assertInvitesRoundTrip(helper, codec, invites, invites, "two invitations");
        assertInvitesRoundTrip(helper, codec, List.of(), List.of(), "no invitation");

        List<ReceivedPartyInvite> full = new ArrayList<>();
        for (int i = 0; i < ClientboundPartyInvitesPacket.MAX_ENTRIES; i++)
            full.add(new ReceivedPartyInvite(UUID.randomUUID(), "Party " + i, "Owner " + i));
        assertInvitesRoundTrip(helper, codec, full, full, "the maximum of " + ClientboundPartyInvitesPacket.MAX_ENTRIES + " invitations");
        List<ReceivedPartyInvite> tooMany = new ArrayList<>(full);
        tooMany.add(new ReceivedPartyInvite(UUID.randomUUID(), "One too many", "Owner"));
        assertInvitesRoundTrip(helper, codec, tooMany, full, "one invitation more than the maximum");

        String maxParty = "p".repeat(ClientboundPartyInvitesPacket.MAX_PARTY_NAME_LENGTH);
        String maxOwner = "o".repeat(ClientboundPartyInvitesPacket.MAX_OWNER_NAME_LENGTH);
        UUID id = UUID.randomUUID();
        assertInvitesRoundTrip(helper, codec, List.of(new ReceivedPartyInvite(id, maxParty, maxOwner)),
                List.of(new ReceivedPartyInvite(id, maxParty, maxOwner)), "names of the maximum length");
        assertInvitesRoundTrip(helper, codec, List.of(new ReceivedPartyInvite(id, maxParty + "extra", maxOwner + "extra")),
                List.of(new ReceivedPartyInvite(id, maxParty, maxOwner)), "names above the maximum length");
        assertInvitesRoundTrip(helper, codec, List.of(new ReceivedPartyInvite(id, null, null)),
                List.of(new ReceivedPartyInvite(id, "", "")), "missing names");

        byte[] valid = encode(codec, new ClientboundPartyInvitesPacket(invites));
        helper.assertTrue(codec.apply(wrap(valid)) != null, "test setup: expected the valid bytes to decode");
        helper.assertTrue(codec.apply(wrap(concat(valid, new byte[]{0}))) == null, "expected a trailing byte to be rejected");
        helper.assertTrue(codec.apply(wrap(Arrays.copyOf(valid, valid.length - 1))) == null,
                "expected a missing last byte to be rejected");
        helper.assertTrue(codec.apply(wrap(new byte[0])) == null, "expected an empty buffer to be rejected");
        helper.assertTrue(codec.apply(wrap(new byte[ClientboundPartyInvitesPacket.MAX_ENTRIES * 1024 + 1])) == null,
                "expected more than 65536 bytes to be rejected");
        FriendlyByteBuf noEntries = buffer();
        noEntries.writeVarInt(0);
        noEntries.writeByte(0);
        helper.assertTrue(codec.apply(noEntries) == null, "expected a trailing byte after an empty list to be rejected");

        FriendlyByteBuf negative = buffer();
        negative.writeVarInt(-1);
        helper.assertTrue(codec.apply(negative) == null, "expected a negative count to be rejected");
        FriendlyByteBuf tooLarge = buffer();
        tooLarge.writeVarInt(ClientboundPartyInvitesPacket.MAX_ENTRIES + 1);
        helper.assertTrue(codec.apply(tooLarge) == null, "expected a count above the maximum to be rejected");
        FriendlyByteBuf missingEntry = buffer();
        missingEntry.writeVarInt(2);
        missingEntry.writeUUID(UUID.randomUUID()).writeUtf("Only one", 128).writeUtf("Owner", 64);
        helper.assertTrue(codec.apply(missingEntry) == null, "expected a count of 2 with 1 entry to be rejected");

        FriendlyByteBuf longParty = buffer();
        longParty.writeVarInt(1);
        longParty.writeUUID(UUID.randomUUID()).writeUtf("p".repeat(ClientboundPartyInvitesPacket.MAX_PARTY_NAME_LENGTH + 1), 32767)
                .writeUtf("Owner", 64);
        helper.assertTrue(codec.apply(longParty) == null, "expected a party name of 129 characters to be rejected");
        FriendlyByteBuf longOwner = buffer();
        longOwner.writeVarInt(1);
        longOwner.writeUUID(UUID.randomUUID()).writeUtf("Party", 128)
                .writeUtf("o".repeat(ClientboundPartyInvitesPacket.MAX_OWNER_NAME_LENGTH + 1), 32767);
        helper.assertTrue(codec.apply(longOwner) == null, "expected an owner name of 65 characters to be rejected");
        helper.succeed();
    }

    /** Decline (52): the party id survives a round trip, 16 bytes are written, and any other size is rejected. */
    public static void declinePacketCodec(GameTestHelper helper) {
        ServerboundPartyInviteDeclinePacket.Codec codec = new ServerboundPartyInviteDeclinePacket.Codec();
        UUID partyId = UUID.randomUUID();
        byte[] bytes = encode(codec, new ServerboundPartyInviteDeclinePacket(partyId));
        helper.assertTrue(bytes.length == 16, "expected the decline to be 16 bytes, got " + bytes.length);
        ServerboundPartyInviteDeclinePacket decoded = codec.apply(wrap(bytes));
        helper.assertTrue(decoded != null && partyId.equals(decoded.getPartyId()),
                "expected the party id " + partyId + " after the round trip, got " + (decoded == null ? null : decoded.getPartyId()));
        helper.assertTrue(codec.apply(wrap(new byte[0])) == null, "expected an empty buffer to be rejected");
        helper.assertTrue(codec.apply(wrap(Arrays.copyOf(bytes, 15))) == null, "expected 15 bytes to be rejected");
        helper.assertTrue(codec.apply(wrap(concat(bytes, new byte[]{0}))) == null, "expected 17 bytes to be rejected");
        helper.assertTrue(codec.apply(wrap(concat(bytes, bytes))) == null, "expected 32 bytes to be rejected");
        helper.succeed();
    }

    /**
     * Budget (53): equal content after a round trip (unlimited limits and long time spans too), an unavailable budget
     * is a single byte and decodes to {@link PartyBudgetData#UNAVAILABLE} whatever else it carried, and a negative
     * count or time, missing or trailing bytes, an empty buffer and more than 256 bytes are all rejected.
     */
    public static void budgetPacketCodec(GameTestHelper helper) {
        ClientboundPartyBudgetPacket.Codec codec = new ClientboundPartyBudgetPacket.Codec();
        PartyBudgetData full = new PartyBudgetData(true, 5, 100, 2, 10, true, 3, 2, 12, 500, 4, 10, 7, 50 * HOUR + 1234, 1, 1234L, 25, 2);
        assertBudgetRoundTrip(helper, codec, full, full, "a full budget");
        PartyBudgetData unlimited = new PartyBudgetData(true, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, true, 4, 2,
                0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0, 0, 0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertBudgetRoundTrip(helper, codec, unlimited, unlimited, "unlimited limits");
        PartyBudgetData longSpan = new PartyBudgetData(true, 1, 2, 3, 4, true, 5, 6, 7, 8, 9, 10, 11, Long.MAX_VALUE, 13, Long.MAX_VALUE, 15, 16);
        assertBudgetRoundTrip(helper, codec, longSpan, longSpan, "the longest time span");
        PartyBudgetData noTeam = new PartyBudgetData(true, 2, 50, 1, 5, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertBudgetRoundTrip(helper, codec, noTeam, noTeam, "a budget without a team");
        assertBudgetRoundTrip(helper, codec, PartyBudgetData.UNAVAILABLE, PartyBudgetData.UNAVAILABLE, "an unavailable budget");
        PartyBudgetData unavailableWithNumbers = new PartyBudgetData(false, 9, 9, 9, 9, true, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9);
        helper.assertTrue(encode(codec, new ClientboundPartyBudgetPacket(unavailableWithNumbers)).length == 1,
                "expected an unavailable budget to be a single byte");
        assertBudgetRoundTrip(helper, codec, unavailableWithNumbers, PartyBudgetData.UNAVAILABLE, "an unavailable budget with numbers");

        byte[] valid = encode(codec, new ClientboundPartyBudgetPacket(full));
        helper.assertTrue(valid.length <= 256, "test setup: expected a full budget to fit the 256 bytes the decoder takes");
        helper.assertTrue(codec.apply(wrap(concat(valid, new byte[]{0}))) == null, "expected a trailing byte to be rejected");
        helper.assertTrue(codec.apply(wrap(Arrays.copyOf(valid, valid.length - 1))) == null,
                "expected a missing last byte to be rejected");
        helper.assertTrue(codec.apply(wrap(Arrays.copyOf(valid, 1))) == null, "expected a budget with only its flag to be rejected");
        helper.assertTrue(codec.apply(wrap(new byte[0])) == null, "expected an empty buffer to be rejected");
        helper.assertTrue(codec.apply(wrap(new byte[257])) == null, "expected more than 256 bytes to be rejected");
        helper.assertTrue(codec.apply(wrap(new byte[]{0, 0})) == null, "expected a trailing byte after an unavailable budget to be rejected");

        PartyBudgetData[] negatives = {
                new PartyBudgetData(true, -1, 100, 2, 10, true, 3, 2, 12, 500, 4, 10, 7, 1000L, 1, 2000L, 25, 2),
                new PartyBudgetData(true, 5, 100, 2, 10, true, 3, 2, -12, 500, 4, 10, 7, 1000L, 1, 2000L, 25, 2),
                new PartyBudgetData(true, 5, 100, 2, 10, true, 3, 2, 12, 500, 4, 10, 7, -1L, 1, 2000L, 25, 2),
                new PartyBudgetData(true, 5, 100, 2, 10, true, 3, 2, 12, 500, 4, 10, 7, 1000L, 1, -2000L, 25, 2),
                new PartyBudgetData(true, 5, 100, 2, 10, true, 3, 2, 12, 500, 4, 10, 7, 1000L, 1, 2000L, 25, -2)};
        String[] negativeFields = {"the first count", "a team count", "the claim time left", "the forceload time left", "the last count"};
        for (int i = 0; i < negatives.length; i++)
            helper.assertTrue(codec.apply(wrap(encode(codec, new ClientboundPartyBudgetPacket(negatives[i])))) == null,
                    "expected a negative value in " + negativeFields[i] + " to be rejected");
        helper.succeed();
    }

    // ==================== Tests: request (50) and answer (51) ====================

    /**
     * A player invited by two parties (one with a custom party name, one with the default) asks for the invitations:
     * the server answers with exactly one invitations packet with those two (party id, party name, owner name) and then
     * one budget packet, and the invitations packet survives the real codec.
     */
    public static void invitesRequestAnswersWithReceivedInvites(GameTestHelper helper, RecorderFactory recorders) {
        try (Scene s = new Scene(helper, recorders)) {
            ServerPlayer player = s.player();
            GameProfile ownerA = profile("T90_OwnerA");
            GameProfile ownerB = profile("T90_OwnerB");
            IServerPartyAPI a = s.party(ownerA);
            IServerPartyAPI b = s.party(ownerB);
            s.setPartyName(ownerA.getId(), "Équipe Ünïque Δ");
            invite(helper, a, player);
            invite(helper, b, player);
            IServerPartyAPI other = s.party(profile("T90_OwnerC"));
            helper.assertTrue(other.invitePlayer(UUID.randomUUID(), "T90_Someone") != null, "test setup: expected the invite of another player");

            List<Object> sent = s.request(player);
            helper.assertTrue(sent.size() == 2 && sent.get(0) instanceof ClientboundPartyInvitesPacket
                            && sent.get(1) instanceof ClientboundPartyBudgetPacket,
                    "expected one invitations packet followed by one budget packet, got " + describe(sent));
            List<ReceivedPartyInvite> received = ((ClientboundPartyInvitesPacket) sent.get(0)).getInvites();
            Set<ReceivedPartyInvite> expected = Set.of(new ReceivedPartyInvite(a.getId(), "Équipe Ünïque Δ", ownerA.getName()),
                    new ReceivedPartyInvite(b.getId(), b.getDefaultName(), ownerB.getName()));
            helper.assertTrue(received.size() == 2 && new HashSet<>(received).equals(expected),
                    "expected exactly the invitations " + expected + ", got " + received);
            helper.assertTrue(!b.getDefaultName().isEmpty() && !b.getDefaultName().equals("Équipe Ünïque Δ"),
                    "test setup: expected the default party name to differ from the custom one, got '" + b.getDefaultName() + "'");
            helper.assertTrue(((ClientboundPartyBudgetPacket) sent.get(1)).getBudget().equals(privateOnly(s.server, player.getUUID(), 0, 0)),
                    "expected the budget packet of a player without a team, got " + ((ClientboundPartyBudgetPacket) sent.get(1)).getBudget());

            ClientboundPartyInvitesPacket.Codec codec = new ClientboundPartyInvitesPacket.Codec();
            ClientboundPartyInvitesPacket decoded = codec.apply(wrap(encode(codec, (ClientboundPartyInvitesPacket) sent.get(0))));
            helper.assertTrue(decoded != null && decoded.getInvites().equals(received),
                    "expected the invitations packet of the server to survive the codec, got " + (decoded == null ? null : decoded.getInvites()));
            helper.succeed();
        }
    }

    /** A player nobody invited gets an empty list (and the budget), also while other players are invited. */
    public static void invitesRequestWithoutInvitesGetsEmptyList(GameTestHelper helper, RecorderFactory recorders) {
        try (Scene s = new Scene(helper, recorders)) {
            ServerPlayer player = s.player();
            IServerPartyAPI party = s.party(profile("T91_Owner"));
            helper.assertTrue(party.invitePlayer(UUID.randomUUID(), "T91_Someone") != null, "test setup: expected the invite of another player");

            List<Object> sent = s.request(player);
            helper.assertTrue(sent.size() == 2 && sent.get(0) instanceof ClientboundPartyInvitesPacket
                            && sent.get(1) instanceof ClientboundPartyBudgetPacket,
                    "expected one invitations packet followed by one budget packet, got " + describe(sent));
            helper.assertTrue(((ClientboundPartyInvitesPacket) sent.get(0)).getInvites().isEmpty(),
                    "expected an empty list, got " + ((ClientboundPartyInvitesPacket) sent.get(0)).getInvites());
            helper.succeed();
        }
    }

    /**
     * A player who is a member of a party is still told about the invitations of other parties, but not about an
     * invitation to the party they are a member of already.
     */
    public static void invitesRequestOfPartyMember(GameTestHelper helper, RecorderFactory recorders) {
        try (Scene s = new Scene(helper, recorders)) {
            ServerPlayer player = s.player();
            GameProfile ownerHome = profile("T92_OwnerHome");
            GameProfile ownerOther = profile("T92_OwnerOther");
            IServerPartyAPI home = s.party(ownerHome);
            helper.assertTrue(home.addMember(player.getUUID(), PartyMemberRank.MEMBER,
                    player.getGameProfile().getName()) != null, "test setup: expected the player to join the party");
            IServerPartyAPI other = s.party(ownerOther);
            invite(helper, home, player);
            invite(helper, other, player);

            List<Object> sent = s.request(player);
            List<ReceivedPartyInvite> received = invitesOf(helper, sent, "for a party member");
            helper.assertTrue(received.equals(List.of(new ReceivedPartyInvite(other.getId(), other.getDefaultName(), ownerOther.getName()))),
                    "expected only the invitation of the other party, got " + received);
            helper.succeed();
        }
    }

    // ==================== Tests: rate limit ====================

    /**
     * One request per 20 ticks per player: the first is answered, a second in the same tick is not, a request 19 ticks
     * after the last answered one is not, one after 20 ticks is; another player is not held back by the first.
     */
    public static void invitesRequestIsRateLimited(GameTestHelper helper, RecorderFactory recorders) {
        try (Scene s = new Scene(helper, recorders)) {
            helper.assertTrue(ServerboundPartyInvitesRequestPacket.MIN_TICKS_BETWEEN_REQUESTS == 20,
                    "expected the interval between requests to be 20 ticks");
            ServerPlayer player = s.player();
            ServerPlayer second = s.player();
            invite(helper, s.party(profile("T93_Owner")), player);
            ServerPlayerData data = (ServerPlayerData) ServerPlayerData.from(player);
            data.setLastPartyInvitesRequestTick(-1000);
            long tick = s.server.getTickCount();

            helper.assertTrue(isAnswer(s.request(player)), "expected the first request to be answered");
            helper.assertTrue(data.getLastPartyInvitesRequestTick() == tick,
                    "expected the tick of the answered request to be stored, got " + data.getLastPartyInvitesRequestTick());
            List<Object> repeated = s.request(player);
            helper.assertTrue(repeated.isEmpty(), "expected a second request in the same tick not to be answered, got " + describe(repeated));
            helper.assertTrue(isAnswer(s.request(second)), "expected another player not to be held back by the first");

            data.setLastPartyInvitesRequestTick(tick - 19);
            List<Object> early = s.request(player);
            helper.assertTrue(early.isEmpty(), "expected a request 19 ticks after the last one not to be answered, got " + describe(early));
            helper.assertTrue(data.getLastPartyInvitesRequestTick() == tick - 19, "expected an unanswered request not to restart the interval");
            data.setLastPartyInvitesRequestTick(tick - 20);
            helper.assertTrue(isAnswer(s.request(player)), "expected a request 20 ticks after the last one to be answered");
            helper.assertTrue(data.getLastPartyInvitesRequestTick() == tick, "expected the new tick to be stored");
            helper.succeed();
        }
    }

    /**
     * The same limit with the server really ticking: answered at once, not answered in the same tick and 10 ticks
     * later, answered once 20 ticks have passed.
     */
    public static void invitesRequestRateLimitOverRealTicks(GameTestHelper helper, RecorderFactory recorders) {
        Scene s = new Scene(helper, recorders);
        boolean handedOver = false;
        try {
            ServerPlayer player = s.player();
            invite(helper, s.party(profile("T94_Owner")), player);
            long start = s.server.getTickCount();
            helper.assertTrue(isAnswer(s.request(player)), "expected the first request to be answered");
            helper.assertTrue(s.request(player).isEmpty(), "expected a second request in the same tick not to be answered");
            handedOver = true;
            helper.runAfterDelay(10, () -> {
                try {
                    long elapsed = s.server.getTickCount() - start;
                    helper.assertTrue(elapsed < ServerboundPartyInvitesRequestPacket.MIN_TICKS_BETWEEN_REQUESTS,
                            "test setup: expected less than 20 ticks to have passed, got " + elapsed);
                    helper.assertTrue(s.request(player).isEmpty(), "expected a request after " + elapsed + " ticks not to be answered");
                    helper.runAfterDelay(10, () -> {
                        try {
                            long total = s.server.getTickCount() - start;
                            helper.assertTrue(total >= ServerboundPartyInvitesRequestPacket.MIN_TICKS_BETWEEN_REQUESTS,
                                    "test setup: expected 20 ticks to have passed, got " + total);
                            List<Object> sent = s.request(player);
                            helper.assertTrue(isAnswer(sent), "expected a request after " + total + " ticks to be answered, got " + describe(sent));
                            helper.succeed();
                        } finally {
                            s.close();
                        }
                    });
                } catch (RuntimeException | Error e) {
                    s.close();
                    throw e;
                }
            });
        } finally {
            if (!handedOver) s.close();
        }
    }

    // ==================== Tests: decline (52) ====================

    /**
     * Declining removes exactly that invitation: the party no longer lists the player as invited, the other party still
     * does, the invitation of another player to the declined party stays, the party itself stays, and the server answers
     * with the remaining invitations only (no budget). Declining the last one answers with an empty list.
     */
    public static void declineRemovesOnlyThatInvite(GameTestHelper helper, RecorderFactory recorders) {
        try (Scene s = new Scene(helper, recorders)) {
            ServerPlayer player = s.player();
            UUID playerId = player.getUUID();
            GameProfile ownerA = profile("T95_OwnerA");
            GameProfile ownerB = profile("T95_OwnerB");
            IServerPartyAPI a = s.party(ownerA);
            IServerPartyAPI b = s.party(ownerB);
            invite(helper, a, player);
            invite(helper, b, player);
            UUID someone = UUID.randomUUID();
            helper.assertTrue(a.invitePlayer(someone, "T95_Someone") != null, "test setup: expected the invite of another player");

            List<Object> sent = s.send(player, new ServerboundPartyInviteDeclinePacket(a.getId()));
            helper.assertTrue(sent.size() == 1 && sent.get(0) instanceof ClientboundPartyInvitesPacket,
                    "expected only the invitations packet after a decline, got " + describe(sent));
            helper.assertTrue(((ClientboundPartyInvitesPacket) sent.get(0)).getInvites()
                            .equals(List.of(new ReceivedPartyInvite(b.getId(), b.getDefaultName(), ownerB.getName()))),
                    "expected only the invitation of the other party to remain, got " + ((ClientboundPartyInvitesPacket) sent.get(0)).getInvites());
            helper.assertTrue(!a.isInvited(playerId) && b.isInvited(playerId),
                    "expected the player to be uninvited by the declined party only");
            helper.assertTrue(a.isInvited(someone) && a.getInviteCount() == 1,
                    "expected the invitation of another player to the declined party to stay, got " + a.getInviteCount() + " invite(s)");
            helper.assertTrue(partyManager(s.server).getPartyById(a.getId()) != null, "expected the declined party to still exist");

            sent = s.send(player, new ServerboundPartyInviteDeclinePacket(b.getId()));
            helper.assertTrue(sent.size() == 1 && sent.get(0) instanceof ClientboundPartyInvitesPacket
                            && ((ClientboundPartyInvitesPacket) sent.get(0)).getInvites().isEmpty(),
                    "expected an empty invitations list after the last decline, got " + describe(sent));
            helper.assertTrue(!b.isInvited(playerId), "expected the last invitation to be removed");
            helper.succeed();
        }
    }

    /**
     * Declining something that is not there does nothing and answers nothing: an unknown party id, a party that never
     * invited the player (its invitation of another player stays), an invitation that was already declined, and a
     * decline that did not arrive intact. Nothing else is changed, nothing is thrown.
     */
    public static void declineOfUnknownOrUninvitedPartyDoesNothing(GameTestHelper helper, RecorderFactory recorders) {
        try (Scene s = new Scene(helper, recorders)) {
            ServerPlayer player = s.player();
            UUID playerId = player.getUUID();
            IServerPartyAPI inviting = s.party(profile("T96_OwnerA"));
            IServerPartyAPI never = s.party(profile("T96_OwnerB"));
            invite(helper, inviting, player);
            UUID someone = UUID.randomUUID();
            helper.assertTrue(never.invitePlayer(someone, "T96_Someone") != null, "test setup: expected the invite of another player");

            List<Object> sent = s.send(player, new ServerboundPartyInviteDeclinePacket(UUID.randomUUID()));
            helper.assertTrue(sent.isEmpty(), "expected nothing after declining an unknown party, got " + describe(sent));
            sent = s.send(player, new ServerboundPartyInviteDeclinePacket(never.getId()));
            helper.assertTrue(sent.isEmpty(), "expected nothing after declining a party that never invited the player, got " + describe(sent));
            helper.assertTrue(never.isInvited(someone) && never.getInviteCount() == 1 && !never.isInvited(playerId),
                    "expected the party that never invited the player to be unchanged");
            helper.assertTrue(inviting.isInvited(playerId), "expected the real invitation to be untouched so far");

            sent = s.sendRaw(player, ID_DECLINE, new byte[15]);
            helper.assertTrue(sent.isEmpty(), "expected nothing after a decline of 15 bytes, got " + describe(sent));
            sent = s.sendRaw(player, ID_DECLINE, new byte[17]);
            helper.assertTrue(sent.isEmpty(), "expected nothing after a decline of 17 bytes, got " + describe(sent));
            helper.assertTrue(inviting.isInvited(playerId), "expected the real invitation to survive the malformed declines");

            sent = s.send(player, new ServerboundPartyInviteDeclinePacket(inviting.getId()));
            helper.assertTrue(sent.size() == 1 && sent.get(0) instanceof ClientboundPartyInvitesPacket, "expected the answer to the real decline");
            sent = s.send(player, new ServerboundPartyInviteDeclinePacket(inviting.getId()));
            helper.assertTrue(sent.isEmpty(), "expected nothing after declining the same invitation twice, got " + describe(sent));
            helper.assertTrue(!inviting.isInvited(playerId), "expected the invitation to be gone");
            helper.succeed();
        }
    }

    // ==================== Tests: handlers ====================

    /**
     * With parties disabled neither handler answers, the interval of the request limit is not used up, and a decline
     * keeps the invitation; a packet that did not decode (null) is ignored by both, also with parties enabled; once the
     * parties are enabled again the same requests work, so the silence came from the setting.
     */
    public static void handlersIgnoreDisabledPartiesAndNullPackets(GameTestHelper helper, RecorderFactory recorders) {
        boolean before = ServerConfig.CONFIG.partiesEnabled.get();
        try (Scene s = new Scene(helper, recorders)) {
            ServerPlayer player = s.player();
            UUID playerId = player.getUUID();
            IServerPartyAPI party = s.party(profile("T97_Owner"));
            invite(helper, party, player);
            ServerPlayerData data = (ServerPlayerData) ServerPlayerData.from(player);
            data.setLastPartyInvitesRequestTick(-1000);
            ServerboundPartyInvitesRequestPacket.ServerHandler requestHandler = new ServerboundPartyInvitesRequestPacket.ServerHandler();
            ServerboundPartyInviteDeclinePacket.ServerHandler declineHandler = new ServerboundPartyInviteDeclinePacket.ServerHandler();

            setPartiesEnabled(false);
            List<Object> sent = s.request(player);
            helper.assertTrue(sent.isEmpty(), "expected no answer to a request with parties disabled, got " + describe(sent));
            helper.assertTrue(data.getLastPartyInvitesRequestTick() == -1000, "expected the request limit not to be used up with parties disabled");
            sent = s.send(player, new ServerboundPartyInviteDeclinePacket(party.getId()));
            helper.assertTrue(sent.isEmpty() && party.isInvited(playerId),
                    "expected a decline with parties disabled to change and answer nothing, got " + describe(sent));
            setPartiesEnabled(before);

            sent = s.capture(player, () -> {
                requestHandler.accept(null, player);
                declineHandler.accept(null, player);
            });
            helper.assertTrue(sent.isEmpty() && party.isInvited(playerId) && data.getLastPartyInvitesRequestTick() == -1000,
                    "expected null packets to be ignored, got " + describe(sent));
            sent = s.sendRaw(player, ID_REQUEST, new byte[]{1});
            helper.assertTrue(sent.isEmpty(), "expected a request with a trailing byte to be dropped, got " + describe(sent));

            helper.assertTrue(isAnswer(s.request(player)), "expected the request to work with parties enabled");
            sent = s.send(player, new ServerboundPartyInviteDeclinePacket(party.getId()));
            helper.assertTrue(sent.size() == 1 && !party.isInvited(playerId), "expected the decline to work with parties enabled");
            helper.succeed();
        } finally {
            setPartiesEnabled(before);
        }
    }

    // ==================== Tests: budget packet (53) ====================

    /** A player in no team: their private claims and forceloads against their own limits, everything about a team 0 or false. */
    public static void budgetPacketOfPlayerWithoutTeam(GameTestHelper helper, RecorderFactory recorders) {
        int x0 = 91000;
        try (Scene s = new Scene(helper, recorders)) {
            ServerPlayer player = s.player();
            UUID playerId = player.getUUID();
            s.undo(() -> unclaimQuiet(s.server, x0, 0, x0 + 1, 0));
            helper.assertTrue(claimsAPI(s.server).claim(OVERWORLD, playerId, -1, x0, 0, true) != null
                            && claimsAPI(s.server).claim(OVERWORLD, playerId, -1, x0 + 1, 0, false) != null,
                    "test setup: expected the private claims to be made");

            PartyBudgetData budget = s.budget(player);
            PartyBudgetData expected = privateOnly(s.server, playerId, 2, 1);
            helper.assertTrue(budget.equals(expected), "expected " + expected + ", got " + budget);
            helper.assertTrue(budget.available() && !budget.inTeam() && budget.privateClaims() == 2 && budget.privateForceloads() == 1
                            && budget.memberCount() == 0 && budget.teamClaims() == 0 && budget.claimMillisLeft() == 0,
                    "expected only the private numbers (2 claims, 1 forceload), got " + budget);
            helper.succeed();
        }
    }

    /**
     * A member of a team of two: the private numbers of the player (the team claims of the owner and the member's own
     * team claim not among them), the team claims and forceloads with their limits for two members, the member count and
     * minimum, no deadline, and what a third member would add.
     */
    public static void budgetPacketOfTeamMember(GameTestHelper helper, RecorderFactory recorders) {
        Team t = Team.create(helper, "T98", 92000);
        int x0 = t.x0;
        try {
            t.cm.setBudgetSettingsOverride(t.party.getId(), new BudgetSettings(2, 10, 5, 4, 2, 168));
            int ownerSub = t.teamSub(t.ownerId);
            int memberSub = t.teamSub(t.playerId);
            t.claim(t.ownerId, ownerSub, x0, 0, true);//a team claim with a team forceload
            t.claim(t.ownerId, ownerSub, x0 + 1, 0, false);
            t.claim(t.playerId, memberSub, x0 + 2, 0, false);//a team claim the member owns
            t.claim(t.playerId, -1, x0 + 3, 0, true);//a private claim with a private forceload
            t.claim(t.playerId, -1, x0 + 4, 0, false);

            PartyBudgetData expected = new PartyBudgetData(true, 2, claimsAPI(t.server).getPlayerFullClaimLimit(t.playerId), 1,
                    claimsAPI(t.server).getPlayerFullForceloadLimit(t.playerId), true, 2, 2, 3, 10, 1, 4, 0, 0, 0, 0, 5, 2);
            PartyBudgetData budget = budgetSentTo(t.server, t.player, recorders);
            helper.assertTrue(budget.equals(expected), "expected " + expected + ", got " + budget);
            helper.succeed();
        } finally {
            t.cleanup(x0, 0, x0 + 1, 0, x0 + 2, 0, x0 + 3, 0, x0 + 4, 0);
        }
    }

    /**
     * A team below the minimum member count (one member, two needed): it is a team, with the member count and the
     * minimum, but its limits are 0 and a second member would add the whole base amount.
     */
    public static void budgetPacketOfTeamBelowMinimum(GameTestHelper helper, RecorderFactory recorders) {
        int x0 = 93000;
        try (Scene s = new Scene(helper, recorders)) {
            ServerPlayer player = s.player();
            UUID playerId = player.getUUID();
            IServerPartyAPI party = createPartyWithTeam(s.server, player.getGameProfile(), List.of());
            s.parties.add(party);
            s.cm.setBudgetSettingsOverride(party.getId(), new BudgetSettings(2, 10, 5, 4, 2, 168));
            s.undo(() -> s.cm.setBudgetSettingsOverride(party.getId(), null));
            s.undo(() -> unclaimQuiet(s.server, x0, 0));
            helper.assertTrue(claimsAPI(s.server).claim(OVERWORLD, playerId, -1, x0, 0, false) != null, "test setup: expected the private claim");

            PartyBudgetData expected = new PartyBudgetData(true, 1, claimsAPI(s.server).getPlayerFullClaimLimit(playerId), 0,
                    claimsAPI(s.server).getPlayerFullForceloadLimit(playerId), true, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 10, 4);
            PartyBudgetData budget = s.budget(player);
            helper.assertTrue(budget.equals(expected), "expected " + expected + ", got " + budget);
            helper.succeed();
        }
    }

    /**
     * A team above its claim and forceload limits with running deadlines: the number above the limits and the time
     * LEFT by the manager's clock (not the deadline) for each, counting down as the clock moves, and 0 once the time is
     * up, never negative.
     */
    public static void budgetPacketShowsTimeLeftOfOverLimitDeadline(GameTestHelper helper, RecorderFactory recorders) {
        Team t = Team.create(helper, "T99", 94000);
        int x0 = t.x0;
        long[] now = {System.currentTimeMillis()};
        try {
            UUID partyId = t.party.getId();
            t.cm.setBudgetSettingsOverride(partyId, new BudgetSettings(2, 3, 4, 2, 3, 72));
            int ownerSub = t.teamSub(t.ownerId);
            for (int i = 0; i < 3; i++) t.claim(t.ownerId, ownerSub, x0 + i, 0, i < 2);
            t.cm.setClock(() -> now[0]);
            t.cm.setBudgetSettingsOverride(partyId, new BudgetSettings(2, 1, 4, 1, 3, 72));
            capture(t.server, t.player, recorders, () -> t.cm.checkOverLimit(partyId));
            long deadline = now[0] + 72 * HOUR;
            helper.assertTrue(t.cm.getTeamData(partyId).getClaimDeadline() == deadline && t.cm.getTeamData(partyId).getForceloadDeadline() == deadline,
                    "test setup: expected both deadlines 72 hours from now");

            int privateClaimLimit = claimsAPI(t.server).getPlayerFullClaimLimit(t.playerId);
            int privateForceloadLimit = claimsAPI(t.server).getPlayerFullForceloadLimit(t.playerId);
            assertOverLimitBudget(helper, budgetSentTo(t.server, t.player, recorders), privateClaimLimit, privateForceloadLimit,
                    72 * HOUR, "right after the deadlines started");
            now[0] += 10 * HOUR + 30_000;
            assertOverLimitBudget(helper, budgetSentTo(t.server, t.player, recorders), privateClaimLimit, privateForceloadLimit,
                    72 * HOUR - 10 * HOUR - 30_000, "10 hours and 30 seconds later");
            now[0] = deadline - 1;
            assertOverLimitBudget(helper, budgetSentTo(t.server, t.player, recorders), privateClaimLimit, privateForceloadLimit,
                    1, "1 millisecond before the deadlines");
            now[0] = deadline + 5000;
            assertOverLimitBudget(helper, budgetSentTo(t.server, t.player, recorders), privateClaimLimit, privateForceloadLimit,
                    0, "after the deadlines");
            helper.succeed();
        } finally {
            t.cm.setClock(null);
            t.cleanup(x0, 0, x0 + 1, 0, x0 + 2, 0);
        }
    }

    private static void assertOverLimitBudget(GameTestHelper helper, PartyBudgetData budget, int privateClaimLimit,
            int privateForceloadLimit, long millisLeft, String stage) {
        PartyBudgetData expected = new PartyBudgetData(true, 0, privateClaimLimit, 0, privateForceloadLimit, true, 2, 2, 3, 1, 2, 1,
                2, millisLeft, 1, millisLeft, 4, 3);
        helper.assertTrue(budget.equals(expected), stage + ": expected " + expected + ", got " + budget);
    }

    // ==================== Helpers ====================

    /**
     * Sets the "parties enabled" server setting. It needs a world restart in the config, so its value is cached and
     * {@code set} leaves the cache alone: the cache is dropped to make the new value count right away.
     */
    private static void setPartiesEnabled(boolean enabled) {
        ServerConfig.CONFIG.partiesEnabled.set(enabled);
        ServerConfig.CONFIG.partiesEnabled.clearCache();
    }

    private static PacketHandlerFull packetHandler() {
        return (PacketHandlerFull) OpenPartiesAndClaims.INSTANCE.getPacketHandler();
    }

    private static void assertRegistered(GameTestHelper helper, PacketHandlerFull handler, int id, Class<?> packetClass,
            Class<?> codecClass, Class<?> handlerClass, boolean toServer) {
        PacketType<?> type = handler.getByIndex(id);
        helper.assertTrue(type != null && type.getIndex() == id && type.getType() == packetClass,
                "expected packet " + id + " to be registered as " + packetClass.getSimpleName() + ", got " + (type == null ? null : type.getType()));
        helper.assertTrue(type.getEncoder().getClass() == codecClass && type.getDecoder().getClass() == codecClass,
                "expected packet " + id + " to use " + codecClass.getName() + " for both directions");
        Object registered = toServer ? type.getServerHandler() : type.getClientHandler();
        Object otherSide = toServer ? type.getClientHandler() : type.getServerHandler();
        helper.assertTrue(registered != null && registered.getClass() == handlerClass && otherSide == null,
                "expected packet " + id + " to have " + handlerClass.getName() + " only, got " + registered + " and " + otherSide);
    }

    private static void invite(GameTestHelper helper, IServerPartyAPI party, ServerPlayer player) {
        helper.assertTrue(party.invitePlayer(player.getUUID(), player.getGameProfile().getName()) != null,
                "test setup: expected the invite of the player to " + party.getId());
    }

    /** The budget of a player in no team: their claims and forceloads against their own limits. */
    private static PartyBudgetData privateOnly(MinecraftServer server, UUID playerId, int claims, int forceloads) {
        return new PartyBudgetData(true, claims, claimsAPI(server).getPlayerFullClaimLimit(playerId), forceloads,
                claimsAPI(server).getPlayerFullForceloadLimit(playerId), false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** The answer to a handled request: the invitations, then the budget. */
    private static boolean isAnswer(List<Object> sent) {
        return sent.size() == 2 && sent.get(0) instanceof ClientboundPartyInvitesPacket && sent.get(1) instanceof ClientboundPartyBudgetPacket;
    }

    private static List<ReceivedPartyInvite> invitesOf(GameTestHelper helper, List<Object> sent, String what) {
        List<ClientboundPartyInvitesPacket> packets = sent.stream().filter(ClientboundPartyInvitesPacket.class::isInstance)
                .map(ClientboundPartyInvitesPacket.class::cast).toList();
        helper.assertTrue(packets.size() == 1, "expected one invitations packet " + what + ", got " + describe(sent));
        return packets.get(0).getInvites();
    }

    private static String describe(List<Object> sent) {
        return sent.stream().map(packet -> packet.getClass().getSimpleName()).toList().toString();
    }

    private static void assertInvitesRoundTrip(GameTestHelper helper, ClientboundPartyInvitesPacket.Codec codec,
            List<ReceivedPartyInvite> written, List<ReceivedPartyInvite> expected, String what) {
        FriendlyByteBuf buffer = buffer();
        codec.accept(new ClientboundPartyInvitesPacket(written), buffer);
        ClientboundPartyInvitesPacket decoded = codec.apply(buffer);
        helper.assertTrue(decoded != null && decoded.getInvites().equals(expected),
                "expected " + what + " to come out as " + expected.size() + " equal invitation(s), got " + (decoded == null ? null : decoded.getInvites()));
    }

    private static void assertBudgetRoundTrip(GameTestHelper helper, ClientboundPartyBudgetPacket.Codec codec, PartyBudgetData written,
            PartyBudgetData expected, String what) {
        ClientboundPartyBudgetPacket decoded = codec.apply(wrap(encode(codec, new ClientboundPartyBudgetPacket(written))));
        helper.assertTrue(decoded != null && decoded.getBudget().equals(expected),
                "expected " + what + " to come out as " + expected + ", got " + (decoded == null ? null : decoded.getBudget()));
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static FriendlyByteBuf wrap(byte[] bytes) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static <P> byte[] encode(BiConsumer<P, FriendlyByteBuf> encoder, P packet) {
        FriendlyByteBuf buffer = buffer();
        encoder.accept(packet, buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    /**
     * Runs {@code action} with the connection of the player replaced by a recorder and returns the OPAC packets that
     * were sent to the player meanwhile, in order. The connection is back in place when this returns.
     */
    private static List<Object> capture(MinecraftServer server, ServerPlayer player, RecorderFactory recorders, Runnable action) {
        ServerGamePacketListenerImpl original = player.connection;
        PacketRecorder recorder = recorders.create(server, player);
        try {
            action.run();
        } finally {
            player.connection = original;
        }
        return List.copyOf(recorder.packets);
    }

    /** The content of the one budget packet {@link PartyBudgetSender#send} sends to the player. */
    private static PartyBudgetData budgetSentTo(MinecraftServer server, ServerPlayer player, RecorderFactory recorders) {
        List<Object> sent = capture(server, player, recorders, () -> PartyBudgetSender.send(player));
        if (sent.size() != 1 || !(sent.get(0) instanceof ClientboundPartyBudgetPacket packet))
            throw new IllegalStateException("expected the budget sender to send one budget packet, got " + describe(sent));
        return packet.getBudget();
    }

    /** Delivers a packet to the server handler the way the network does: written with its id, read by the payload codec. */
    private static void deliver(ServerPlayer player, Object packet) {
        FriendlyByteBuf buffer = buffer();
        encodeWithId(packet, buffer);
        deliver(player, buffer);
    }

    private static <P> void encodeWithId(P packet, FriendlyByteBuf buffer) {
        PacketHandlerFull.encodePacket(packetHandler().createPayload(packet).getPacketType(), packet, buffer);
    }

    private static void deliver(ServerPlayer player, FriendlyByteBuf buffer) {
        deliverTyped(new PacketPayloadCodec().decode(buffer), player);
    }

    private static <P> void deliverTyped(PacketPayload<P> payload, ServerPlayer player) {
        payload.getPacketType().getServerHandler().accept(payload.getPacket(), player);
    }

    /** The mock player, the parties and the claims of one test, and everything to undo, torn down by {@link #close}. */
    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final MinecraftServer server;
        final TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        final RecorderFactory recorders;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<IServerPartyAPI> parties = new ArrayList<>();
        final List<UUID> namedOwners = new ArrayList<>();
        final List<Runnable> undo = new ArrayList<>();
        private final AtomicBoolean closed = new AtomicBoolean();

        Scene(GameTestHelper helper, RecorderFactory recorders) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.recorders = recorders;
        }

        ServerPlayer player() {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            players.add(player);
            return player;
        }

        /** A new plain party (no team) of an offline owner. */
        IServerPartyAPI party(GameProfile owner) {
            IServerPartyAPI party = partyManager(server).createPartyForOwner(owner);
            helper.assertTrue(party != null, "test setup: expected a new party for " + owner.getName());
            parties.add(party);
            return party;
        }

        void setPartyName(UUID ownerId, String name) {
            namedOwners.add(ownerId);
            SetResult result = configManager(server).getLoadedConfig(ownerId).tryToSet(PlayerConfigOptions.PARTY_NAME, name);
            helper.assertTrue(result == SetResult.SUCCESS, "test setup: expected the party name '" + name + "' to be accepted, got " + result);
        }

        void undo(Runnable action) {
            undo.add(action);
        }

        /** What the server sends to the player while {@code action} runs. */
        List<Object> capture(ServerPlayer player, Runnable action) {
            return TeamClaimsPacketTestCases.capture(server, player, recorders, action);
        }

        List<Object> send(ServerPlayer player, Object packet) {
            return capture(player, () -> deliver(player, packet));
        }

        List<Object> request(ServerPlayer player) {
            return send(player, new ServerboundPartyInvitesRequestPacket());
        }

        /** A packet of the given id with a body the codec decides on, delivered as it would arrive. */
        List<Object> sendRaw(ServerPlayer player, int id, byte[] body) {
            FriendlyByteBuf buffer = buffer();
            buffer.writeByte(id);
            buffer.writeBytes(body);
            return capture(player, () -> deliver(player, buffer));
        }

        PartyBudgetData budget(ServerPlayer player) {
            return budgetSentTo(server, player, recorders);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            for (int i = undo.size() - 1; i >= 0; i--) {
                try {
                    undo.get(i).run();
                } catch (Exception ignored) {
                }
            }
            for (UUID ownerId : namedOwners) {
                try {
                    configManager(server).getLoadedConfig(ownerId).tryToReset(PlayerConfigOptions.PARTY_NAME);
                } catch (Exception ignored) {
                }
            }
            for (ServerPlayer player : players) removePlayerQuiet(server, player);
            for (IServerPartyAPI party : parties) disbandPartyQuiet(server, party.getId());
        }
    }

}
