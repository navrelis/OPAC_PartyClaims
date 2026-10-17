package xaero.pac.teamclaims.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import xaero.pac.client.gui.party.PartyRules;
import xaero.pac.common.parties.party.member.PartyMember;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.disbandPartyQuiet;
import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.partyManager;
import static xaero.pac.teamclaims.gametest.TeamClaimsLogicTestCases.profile;

/**
 * Dev/test-only, loader-neutral test bodies for the order of the member rows of the party screen
 * ({@link PartyRules#MEMBER_ORDER}). Same conventions as {@link TeamClaimsLogicTestCases} (offline players, cleanup on
 * every path, every test registered with the loader's empty structure template and the default timeout). The comparator
 * only uses common classes, so it is tested on the server with real party members.
 * <p>
 * The bug behind these tests: the rows used to be sorted by rank first, so a member whose rank was changed with the rank
 * button moved to another row, and the row under the cursor then showed somebody else.
 */
public final class TeamClaimsPartyScreenTestCases {

    /** The order that the party screen used before the fix: the owner first, then the rank descending, then the name. */
    private static final Comparator<IPartyMemberAPI> RANK_FIRST_ORDER = Comparator
            .comparing(IPartyMemberAPI::isOwner, Comparator.<Boolean>reverseOrder())
            .thenComparing(Comparator.comparing((IPartyMemberAPI member) -> member.getRank().ordinal()).reversed())
            .thenComparing(member -> member.getUsername().toLowerCase(Locale.ROOT));

    private TeamClaimsPartyScreenTestCases() {}

    // ==================== Shared helpers ====================

    private static List<UUID> rowOrder(IServerPartyAPI party, Comparator<IPartyMemberAPI> order) {
        List<IPartyMemberAPI> members = new ArrayList<>(party.getMemberInfoStream().toList());
        members.sort(order);
        return members.stream().map(IPartyMemberAPI::getUUID).toList();
    }

    private static List<String> names(IServerPartyAPI party, List<UUID> ids) {
        return ids.stream().map(id -> party.getMemberInfo(id).getUsername()).toList();
    }

    private static UUID addMember(GameTestHelper helper, IServerPartyAPI party, String name, PartyMemberRank rank) {
        GameProfile memberProfile = profile(name);
        IPartyMemberAPI added = party.addMember(memberProfile.getId(), rank, name);
        helper.assertTrue(added != null, "expected addMember to succeed for " + name + ", got null");
        return memberProfile.getId();
    }

    private static PartyMember member(String name, PartyMemberRank rank, boolean owner, UUID id) {
        PartyMember member = new PartyMember(id, owner);
        member.setUsername(name);
        member.setRank(rank);
        return member;
    }

    // ==================== Tests ====================

    /**
     * A party like the one of the bug report (owner Navrelis, four ADMINs and one MEMBER) plus members with mixed case
     * names and ranks. Every member goes through every rank, MEMBER, CLAIMER, MODERATOR and ADMIN, so that every
     * transition from each rank to each other rank is covered. After every single rank change the rows have to be exactly
     * as before, so the member has the same row index, nobody else moved, and the owner is still first. The old order is
     * checked on the scenario of the report, to prove that it would have failed with it.
     */
    public static void memberKeepsItsRowWhenOnlyTheRankChanges(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile ownerProfile = profile("Navrelis");
        IServerPartyAPI party = null;
        try {
            party = partyManager(server).createPartyForOwner(ownerProfile);
            helper.assertTrue(party != null, "expected createPartyForOwner to return a new party, got null");
            UUID ownerId = ownerProfile.getId();
            List<UUID> memberIds = new ArrayList<>();
            for (String name : new String[]{"404Hollow404", "Brat_wurst1", "DerZocker42", "DrCrazy27"})
                memberIds.add(addMember(helper, party, name, PartyMemberRank.ADMIN));
            memberIds.add(addMember(helper, party, "Parotol_", PartyMemberRank.MEMBER));
            memberIds.add(addMember(helper, party, "alpha_tester", PartyMemberRank.CLAIMER));
            memberIds.add(addMember(helper, party, "Beta_Tester", PartyMemberRank.MODERATOR));
            memberIds.add(addMember(helper, party, "gamma_x", PartyMemberRank.MEMBER));
            memberIds.add(addMember(helper, party, "Delta_X", PartyMemberRank.ADMIN));
            memberIds.add(addMember(helper, party, "zed", PartyMemberRank.CLAIMER));

            // the scenario of the report: with the old order, the first ADMIN who becomes a MEMBER moves down
            UUID firstAdmin = memberIds.get(0);
            List<UUID> oldBefore = rowOrder(party, RANK_FIRST_ORDER);
            helper.assertTrue(party.setRank(party.getMemberInfo(firstAdmin), PartyMemberRank.MEMBER), "expected the rank change to succeed");
            List<UUID> oldAfter = rowOrder(party, RANK_FIRST_ORDER);
            helper.assertTrue(oldBefore.indexOf(firstAdmin) != oldAfter.indexOf(firstAdmin),
                    "expected the old rank-first order to move 404Hollow404 when he becomes a MEMBER, rows before " + names(party, oldBefore)
                            + ", after " + names(party, oldAfter));
            helper.assertTrue(party.setRank(party.getMemberInfo(firstAdmin), PartyMemberRank.ADMIN), "expected the rank change to succeed");

            List<UUID> expected = rowOrder(party, PartyRules.MEMBER_ORDER);
            List<String> expectedNames = names(party, expected);
            helper.assertTrue(expectedNames.equals(List.of("Navrelis", "404Hollow404", "alpha_tester", "Beta_Tester", "Brat_wurst1",
                            "Delta_X", "DerZocker42", "DrCrazy27", "gamma_x", "Parotol_", "zed")) && expected.get(0).equals(ownerId),
                    "expected the owner first and then the rows ordered by name without regard to case, got " + expectedNames);

            int transitions = 0;
            for (UUID memberId : memberIds) {
                String name = party.getMemberInfo(memberId).getUsername();
                for (PartyMemberRank from : PartyMemberRank.values()) {
                    for (PartyMemberRank to : PartyMemberRank.values()) {
                        if (from == to)
                            continue;
                        helper.assertTrue(party.setRank(party.getMemberInfo(memberId), from) || party.getMemberInfo(memberId).getRank() == from,
                                "expected " + name + " to be set to " + from);
                        List<UUID> before = rowOrder(party, PartyRules.MEMBER_ORDER);
                        helper.assertTrue(party.setRank(party.getMemberInfo(memberId), to),
                                "expected the rank change of " + name + " from " + from + " to " + to + " to succeed");
                        helper.assertTrue(party.getMemberInfo(memberId).getRank() == to, "expected the rank of " + name + " to be " + to);
                        List<UUID> after = rowOrder(party, PartyRules.MEMBER_ORDER);
                        helper.assertTrue(before.equals(expected) && after.equals(expected) && after.indexOf(memberId) == expected.indexOf(memberId),
                                "expected the rows to stay " + expectedNames + " when " + name + " goes from " + from + " to " + to
                                        + ", got " + names(party, after));
                        helper.assertTrue(after.get(0).equals(ownerId), "expected the owner on the first row");
                        transitions++;
                    }
                }
            }
            helper.assertTrue(transitions == memberIds.size() * 12, "expected 12 rank transitions for each member, ran " + transitions);
            helper.succeed();
        } finally {
            if (party != null)
                disbandPartyQuiet(server, party.getId());
        }
    }

    /**
     * The comparator on its own, with member objects that are not in a party: whatever the ranks and whatever the order
     * of the input, the owner is first, the others are ordered by name without regard to case, and members with the
     * same name (cannot happen in a party, but the order has to be total) are ordered by id.
     */
    public static void memberOrderIgnoresRankAndBreaksTiesById(GameTestHelper helper) {
        PartyMember owner = member("Navrelis", PartyMemberRank.ADMIN, true, new UUID(0, 99));
        PartyMember lowerA = member("alpha", PartyMemberRank.MEMBER, false, new UUID(0, 5));
        PartyMember upperB = member("Bravo", PartyMemberRank.ADMIN, false, new UUID(0, 4));
        PartyMember lowerC = member("charlie", PartyMemberRank.MODERATOR, false, new UUID(0, 3));
        PartyMember sameNameLow = member("Dup", PartyMemberRank.CLAIMER, false, new UUID(0, 1));
        PartyMember sameNameHigh = member("dup", PartyMemberRank.ADMIN, false, new UUID(0, 2));
        List<PartyMember> expected = List.of(owner, lowerA, upperB, lowerC, sameNameLow, sameNameHigh);
        Random random = new Random(1234);
        for (int round = 0; round < 50; round++) {
            List<PartyMember> shuffled = new ArrayList<>(expected);
            Collections.shuffle(shuffled, random);
            for (PartyMember member : shuffled)
                if (!member.isOwner())
                    member.setRank(PartyMemberRank.values()[random.nextInt(PartyMemberRank.values().length)]);
            shuffled.sort(PartyRules.MEMBER_ORDER);
            helper.assertTrue(shuffled.equals(expected),
                    "expected the owner, then alpha, Bravo, charlie, Dup (id 1), dup (id 2), got " + shuffled);
        }
        helper.succeed();
    }
}
