package xaero.pac.teamclaims.ftbsync;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import xaero.pac.common.parties.party.member.PartyMemberRank;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The persisted state of the FTB Teams sync: which OPAC party belongs to which FTB party team, and for every such
 * pair what the two sides looked like when they last agreed (the <i>baseline</i>: owner, members with their ranks,
 * pending invitations and the name each side had). The baseline is what lets the sync tell which side changed when
 * the two differ, also for changes that have no event and for changes made while the server was down.
 * <p>
 * A pair also remembers that one of its sides was <i>seen</i> being removed ({@link Pair#isOpacRemoved()},
 * {@link Pair#isFtbRemoved()}) until the other side has followed. That is what tells a party a player disbanded
 * (the other side is removed too) from one that vanished while nobody was looking (nothing is deleted, the missing
 * side is made again), and because it is stored here and not kept for a tick, it does not matter when, or after how
 * many failed attempts, the pair is merged.
 * <p>
 * Storage: a {@link SavedData} of the overworld ({@link #DATA_NAME}, next to Team Claims' own data), so it is
 * written by the world save like FTB Teams' own files and never by the server thread on its own. The FTB team's
 * {@code getExtraData()} was not used: it could only hold the link, is copied from the personal team when a party is
 * created, is only written when FTB Teams saves that team and would be lost with the team, while this data has to
 * outlive either side to notice that one of them vanished.
 * <p>
 * Server-thread confined, like everything in Team Claims.
 */
public final class FtbSyncStore extends SavedData {

    public static final String DATA_NAME = "opacteamclaims_ftbsync";

    /** What the two mods said about one member when they last agreed. */
    public record MemberState(PartyMemberRank opacRank, boolean ftbOfficer) {}

    /** One OPAC party and its FTB party team, with the baseline of their last agreement. */
    public final class Pair {
        private final UUID opacId;
        @Nullable private UUID ftbId;
        /** False until the pair was merged once: until then the FTB side is the baseline, so OPAC wins every difference. */
        private boolean merged;
        @Nullable private UUID owner;
        private final Map<UUID, MemberState> members = new HashMap<>();
        private final Set<UUID> invites = new HashSet<>();
        @Nullable private String opacName;
        @Nullable private String ftbName;
        /** The OPAC party was seen being removed (disbanded, expired); its FTB party is still to be disbanded. */
        private boolean opacRemoved;
        /**
         * The FTB party this pair is linked to was seen being deleted; the OPAC party is still to be removed, or,
         * if it has members who never were in that FTB party, to get a new one.
         */
        private boolean ftbRemoved;

        private Pair(UUID opacId) {
            this.opacId = opacId;
        }

        public UUID opacId() { return opacId; }
        @Nullable public UUID ftbId() { return ftbId; }
        public boolean isMerged() { return merged; }
        @Nullable public UUID owner() { return owner; }
        @Nullable public String opacName() { return opacName; }
        @Nullable public String ftbName() { return ftbName; }
        public Map<UUID, MemberState> members() { return Collections.unmodifiableMap(members); }
        public Set<UUID> invites() { return Collections.unmodifiableSet(invites); }
        @Nullable public MemberState member(UUID playerId) { return members.get(playerId); }
        public boolean hasMember(UUID playerId) { return members.containsKey(playerId); }
        public boolean hasInvite(UUID playerId) { return invites.contains(playerId); }
        public boolean isOpacRemoved() { return opacRemoved; }
        public boolean isFtbRemoved() { return ftbRemoved; }

        public void markOpacRemoved() {
            if (!opacRemoved) {
                opacRemoved = true;
                setDirty();
            }
        }

        public void markFtbRemoved() {
            if (!ftbRemoved && ftbId != null) {
                ftbRemoved = true;
                setDirty();
            }
        }

        public void setMerged(boolean merged) {
            if (this.merged != merged) {
                this.merged = merged;
                setDirty();
            }
        }

        public void setOwner(@Nullable UUID owner) {
            if (!Objects.equals(this.owner, owner)) {
                this.owner = owner;
                setDirty();
            }
        }

        public void setMember(UUID playerId, MemberState state) {
            if (!state.equals(members.put(playerId, state))) setDirty();
        }

        public void removeMember(UUID playerId) {
            if (members.remove(playerId) != null) setDirty();
        }

        public void setInvite(UUID playerId, boolean invited) {
            if (invited ? invites.add(playerId) : invites.remove(playerId)) setDirty();
        }

        public void setOpacName(@Nullable String name) {
            if (!Objects.equals(opacName, name)) {
                opacName = name;
                setDirty();
            }
        }

        public void setFtbName(@Nullable String name) {
            if (!Objects.equals(ftbName, name)) {
                ftbName = name;
                setDirty();
            }
        }

        /** Forgets the baseline (not the link), e.g. before a pair is merged for the first time. */
        public void clearBaseline() {
            if (owner != null || !members.isEmpty() || !invites.isEmpty() || opacName != null || ftbName != null || merged)
                setDirty();
            merged = false;
            owner = null;
            members.clear();
            invites.clear();
            opacName = null;
            ftbName = null;
        }

        @Override
        public String toString() {
            return "pair(opac: " + opacId + ", ftb: " + ftbId + ")";
        }
    }

    private final Map<UUID, Pair> byOpac = new LinkedHashMap<>();
    private final Map<UUID, Pair> byFtb = new HashMap<>();

    public FtbSyncStore() {}

    @Nullable public Pair byOpacParty(UUID opacPartyId) { return byOpac.get(opacPartyId); }
    @Nullable public Pair byFtbTeam(UUID ftbTeamId) { return byFtb.get(ftbTeamId); }

    /** A copy, so that pairs can be added and removed while it is iterated. */
    public List<Pair> pairs() { return new ArrayList<>(byOpac.values()); }

    /** How many OPAC parties are linked to an FTB party team. */
    public int linkedCount() { return byFtb.size(); }

    public int size() { return byOpac.size(); }

    /** The pair of an OPAC party, created without an FTB team if there is none yet. */
    public Pair getOrCreate(UUID opacPartyId) {
        Pair pair = byOpac.get(opacPartyId);
        if (pair == null) {
            pair = new Pair(opacPartyId);
            byOpac.put(opacPartyId, pair);
            setDirty();
        }
        return pair;
    }

    /**
     * Links a pair to an FTB party team (or to none). A team can only belong to one pair: another pair that was
     * linked to it loses the link and its baseline. What was noted about the team the pair was linked to before
     * ({@link Pair#isFtbRemoved()}) does not apply to the new one.
     */
    public void link(Pair pair, @Nullable UUID ftbTeamId) {
        if (Objects.equals(pair.ftbId, ftbTeamId)) return;
        if (pair.ftbId != null) byFtb.remove(pair.ftbId, pair);
        if (ftbTeamId != null) {
            Pair previous = byFtb.put(ftbTeamId, pair);
            if (previous != null && previous != pair) {
                previous.ftbId = null;
                previous.ftbRemoved = false;
                previous.clearBaseline();
            }
        }
        pair.ftbId = ftbTeamId;
        pair.ftbRemoved = false;
        setDirty();
    }

    public void remove(Pair pair) {
        if (byOpac.remove(pair.opacId, pair)) setDirty();
        if (pair.ftbId != null) byFtb.remove(pair.ftbId, pair);
    }

    // ==================== Persistence ====================

    public static FtbSyncStore load(CompoundTag tag, HolderLookup.Provider provider) {
        FtbSyncStore store = new FtbSyncStore();
        ListTag pairs = tag.getList("pairs", Tag.TAG_COMPOUND);
        for (int i = 0; i < pairs.size(); i++) {
            CompoundTag pairTag = pairs.getCompound(i);
            if (!pairTag.hasUUID("opac")) continue;
            UUID opacId = pairTag.getUUID("opac");
            if (store.byOpac.containsKey(opacId)) continue;
            Pair pair = store.new Pair(opacId);
            if (pairTag.hasUUID("ftb")) {
                UUID ftbId = pairTag.getUUID("ftb");
                if (store.byFtb.putIfAbsent(ftbId, pair) == null) pair.ftbId = ftbId;
            }
            pair.opacRemoved = pairTag.getBoolean("opacRemoved");
            pair.ftbRemoved = pairTag.getBoolean("ftbRemoved") && pair.ftbId != null;
            pair.merged = pairTag.getBoolean("merged") && pair.ftbId != null;
            if (pair.merged) {
                if (pairTag.hasUUID("owner")) pair.owner = pairTag.getUUID("owner");
                if (pairTag.contains("opacName", Tag.TAG_STRING)) pair.opacName = pairTag.getString("opacName");
                if (pairTag.contains("ftbName", Tag.TAG_STRING)) pair.ftbName = pairTag.getString("ftbName");
                ListTag members = pairTag.getList("members", Tag.TAG_COMPOUND);
                for (int j = 0; j < members.size(); j++) {
                    CompoundTag memberTag = members.getCompound(j);
                    PartyMemberRank rank = rankByName(memberTag.getString("rank"));
                    if (memberTag.hasUUID("id") && rank != null)
                        pair.members.put(memberTag.getUUID("id"), new MemberState(rank, memberTag.getBoolean("officer")));
                }
                ListTag invites = pairTag.getList("invites", Tag.TAG_COMPOUND);
                for (int j = 0; j < invites.size(); j++) {
                    CompoundTag inviteTag = invites.getCompound(j);
                    if (inviteTag.hasUUID("id")) pair.invites.add(inviteTag.getUUID("id"));
                }
            }
            store.byOpac.put(opacId, pair);
        }
        return store;
    }

    @Nullable
    private static PartyMemberRank rankByName(String name) {
        for (PartyMemberRank rank : PartyMemberRank.values())
            if (rank.name().equals(name)) return rank;
        return null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag pairs = new ListTag();
        for (Pair pair : byOpac.values()) {
            CompoundTag pairTag = new CompoundTag();
            pairTag.putUUID("opac", pair.opacId);
            if (pair.ftbId != null) pairTag.putUUID("ftb", pair.ftbId);
            pairTag.putBoolean("merged", pair.merged);
            if (pair.opacRemoved) pairTag.putBoolean("opacRemoved", true);
            if (pair.ftbRemoved) pairTag.putBoolean("ftbRemoved", true);
            if (pair.owner != null) pairTag.putUUID("owner", pair.owner);
            if (pair.opacName != null) pairTag.putString("opacName", pair.opacName);
            if (pair.ftbName != null) pairTag.putString("ftbName", pair.ftbName);
            ListTag members = new ListTag();
            for (Map.Entry<UUID, MemberState> member : pair.members.entrySet()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID("id", member.getKey());
                memberTag.putString("rank", member.getValue().opacRank().name());
                memberTag.putBoolean("officer", member.getValue().ftbOfficer());
                members.add(memberTag);
            }
            pairTag.put("members", members);
            ListTag invites = new ListTag();
            for (UUID invite : pair.invites) {
                CompoundTag inviteTag = new CompoundTag();
                inviteTag.putUUID("id", invite);
                invites.add(inviteTag);
            }
            pairTag.put("invites", invites);
            pairs.add(pairTag);
        }
        tag.put("pairs", pairs);
        return tag;
    }
}
