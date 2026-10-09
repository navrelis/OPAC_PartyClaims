package xaero.pac.teamclaims.ftbsync;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamPropertiesChangedEvent;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import dev.ftb.mods.ftbteams.api.property.TeamPropertyCollection;
import dev.ftb.mods.ftbteams.config.ServerConfig;
import dev.ftb.mods.ftbteams.data.AbstractTeam;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.PlayerTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import org.slf4j.Logger;
import xaero.pac.common.parties.party.api.IPartyPlayerInfoAPI;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.teamclaims.ftbsync.FtbSyncStore.MemberState;
import xaero.pac.teamclaims.ftbsync.FtbSyncStore.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The FTB Teams half of the sync and the merge itself. Together with {@link FtbSyncEvents} this is the only class
 * that references FTB Teams, and it is only loaded when FTB Teams is installed (see {@link FtbTeamsSync}).
 * <p>
 * <b>The merge of one pair</b> ({@link #reconcilePair}) compares the OPAC party and the FTB party team with the
 * baseline of their last agreement, element by element, in this order: members to add, owner, members to remove,
 * ranks, invitations, name. For each element, the side that differs from the baseline is the one that changed, and
 * the other side follows. Without a usable baseline (first merge of a pair, or both changed) OPAC wins. An element
 * only enters the baseline once both sides agree on it, so whatever could not be applied is tried again in the same
 * direction by the next merge.
 * <p>
 * <b>A pair with a side that no longer exists:</b> if the sync saw that side being removed (the pair says so, see
 * {@link Pair#isOpacRemoved()} and {@link Pair#isFtbRemoved()}), the other side is removed too, and the pair with
 * it. If it is just gone, nothing is deleted: a missing FTB party is created again from the OPAC party, and an FTB
 * party whose OPAC party is gone loses its pair and gets a new OPAC party like any FTB party that has none. One
 * exception: an FTB party that was deleted while the OPAC party has members who never were in it (they still wait
 * to join it, see {@link #wasMirroredCompletely}) does not take the OPAC party with it. The mods disagreed about who
 * is in the party, so OPAC wins and the FTB party is created again.
 * <p>
 * <b>Failures:</b> a refusal by FTB Teams ({@code CommandSyntaxException}) is noted as pending and tried again by
 * the next merge. Any other exception while merging a pair is caught in {@link #reconcilePair}, noted as pending for
 * that pair, and does not stop the merge of the other pairs.
 * <p>
 * <b>FTB Teams methods used</b> (2101.1.11; the public API has no writes besides creating a party for an online
 * player and setting properties, so these are methods of its implementation classes):
 * <ul>
 * <li>create: {@code TeamManagerImpl.createParty(UUID, ServerPlayer, String, String, Color4I)}, with the online
 * owner if there is one (so FTB's own events fire as usual) and without a player object when that is refused
 * (FTB then skips its command permission check) or the owner is offline;</li>
 * <li>join / leave: {@code PartyTeam.join(ServerPlayer, GameProfile)} and {@code PartyTeam.leave(UUID)}, which
 * both work for offline players and need no invitation;</li>
 * <li>owner: {@code PartyTeam.transferOwnership(CommandSourceStack, GameProfile)};</li>
 * <li>disband: {@code PartyTeam.forceDisband(CommandSourceStack)};</li>
 * <li>ranks and invitations: {@code AbstractTeamBase.addMember(UUID, TeamRank)} / {@code removeMember(UUID)}
 * followed by {@code markDirty()} and {@code TeamManagerImpl.syncToAll(team)}, i.e. what
 * {@code PartyTeam.promote/demote/invite} do to the team minus their chat output. In particular an OPAC
 * invitation becomes an FTB invitation without a second prompt for the invited player and without needing an
 * online inviter, which {@code PartyTeam.invite} would both require;</li>
 * <li>name: {@code setProperty(DISPLAY_NAME)}, the {@code PROPERTIES_CHANGED} event and
 * {@code syncOnePropertyToAll}, like FTB's own settings command.</li>
 * </ul>
 * FTB Teams saves a team to disk itself whenever a party is created or deleted; that is its own behaviour for
 * these operations, whoever triggers them.
 * <p>
 * Server-thread confined.
 */
final class FtbSyncEngine {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "gui.xaero_pac_team_claims_ftbsync_";

    private final MinecraftServer server;
    private final FtbTeamsSync sync;
    private final FtbSyncOpac opac;
    /** The OPAC parties whose pair is being merged right now (a merge can trigger the merge of another pair). */
    private final Set<UUID> inProgress = new HashSet<>();

    FtbSyncEngine(MinecraftServer server, FtbTeamsSync sync, FtbSyncOpac opac) {
        this.server = server;
        this.sync = sync;
        this.opac = opac;
    }

    void registerListeners() {
        FtbSyncEvents.register();
    }

    /** FTB Teams creates its manager when the server is about to start; before that there is nothing to sync with. */
    boolean isFtbReady() {
        return FTBTeamsAPI.api().isManagerLoaded() && TeamManagerImpl.INSTANCE != null;
    }

    // ==================== Entry points ====================

    void handle(FtbTeamsSync.Work work) {
        UUID id = work.id();
        switch (work.type()) {
            case OPAC_PARTY_CHANGED, OPAC_PARTY_REMOVED -> {
                Pair pair = sync.getStore().byOpacParty(id);
                if (pair == null) {
                    if (opac.party(id) == null) return;
                    pair = sync.getStore().getOrCreate(id);
                }
                reconcilePair(pair);
            }
            case FTB_TEAM_CHANGED, FTB_TEAM_DELETED -> {
                Pair pair = sync.getStore().byFtbTeam(id);
                if (pair != null) {
                    reconcilePair(pair);
                } else {
                    PartyTeam team = partyTeam(id);
                    if (team != null) createOpacPartyFor(team);
                }
            }
            case FTB_PLAYER_KNOWN -> {
                // Answered with a full reconciliation by FtbTeamsSync, never passed on
            }
        }
    }

    /** One pass of the full reconciliation: every OPAC party gets a pair, every pair is merged, every FTB party without a pair gets an OPAC party. */
    void reconcileEverything() {
        IPartyManagerAPI partyManager = opac.partyManager();
        if (partyManager == null) return;
        List<UUID> opacPartyIds = new ArrayList<>();
        partyManager.getAllStream().forEach(party -> opacPartyIds.add(party.getId()));
        for (UUID partyId : opacPartyIds) sync.getStore().getOrCreate(partyId);
        for (Pair pair : sync.getStore().pairs()) reconcilePair(pair);
        for (PartyTeam team : partyTeams())
            if (sync.getStore().byFtbTeam(team.getId()) == null) createOpacPartyFor(team);
    }

    /**
     * Merges one pair. Idempotent: when both sides agree with each other, nothing is written. Also handles a side
     * that no longer exists. An unexpected exception ends the merge of this pair only: it is noted as pending and
     * the next merge tries again.
     */
    void reconcilePair(Pair pair) {
        if (sync.getStore().byOpacParty(pair.opacId()) != pair) return;//removed in the meantime
        if (!inProgress.add(pair.opacId())) return;
        sync.beginSubject(pair.opacId());
        try {
            merge(pair);
        } catch (RuntimeException e) {
            sync.failure(pair.opacId(), pair.opacName() != null ? pair.opacName() : pair.opacId().toString(), e);
        } finally {
            sync.endSubject(pair.opacId());
            inProgress.remove(pair.opacId());
        }
    }

    // ==================== The merge ====================

    private void merge(Pair pair) {
        IServerPartyAPI party = opac.party(pair.opacId());
        PartyTeam team = partyTeam(pair.ftbId());
        if (party == null) {
            // Disbanded (or expired) in OPAC where the sync saw it: the FTB party goes with it. If FTB Teams
            // refuses, the pair stays as it is and this is tried again.
            if (team != null && pair.isOpacRemoved() && !disbandFtbParty(pair, team)) return;
            // Otherwise gone without the sync seeing it happen (server was down, sync was off): only the link is
            // dropped. An FTB party that is still there counts as existing in one mod only and gets a new party
            // from the next full reconciliation.
            sync.getStore().remove(pair);
            return;
        }
        if (team == null && pair.ftbId() != null) {
            if (pair.isFtbRemoved() && wasMirroredCompletely(pair, party)) {
                // Deleted in FTB Teams where the sync saw it (last member left, or force-disband), and everybody
                // in the party was in that FTB party: the party goes with it. The pair is only removed afterwards,
                // so a failure here is tried again.
                LOGGER.info("[TeamClaims] FTB Teams sync: the FTB Teams party of '{}' was deleted, removing the party",
                        opac.nameOf(party));
                opac.removeParty(party);
                sync.countChange();
                sync.getStore().remove(pair);
                return;
            }
            if (pair.isFtbRemoved()) {
                // Deleted in FTB Teams, but the party has members who never were in that FTB party (they still
                // wait to join it). The two mods disagreed about who is in the party, so its end in FTB Teams
                // does not speak for all of it: OPAC wins, and whoever owned the FTB party is told why it is back.
                String partyName = opac.nameOf(party);
                LOGGER.info("[TeamClaims] FTB Teams sync: the FTB Teams party of '{}' was deleted, but the party has members "
                        + "who were not in it yet; keeping the party and creating its FTB Teams party again", partyName);
                sync.tell(pair.owner() != null ? pair.owner() : party.getOwner().getUUID(), KEY + "reverted_disband", partyName);
            }
            // Gone without the sync seeing it happen, or not for the whole party: OPAC wins, the FTB party is made
            // again below
            sync.getStore().link(pair, null);
            pair.clearBaseline();
        }
        if (team == null) {
            team = linkOrCreateFtbParty(pair, party);
            if (team == null) return;
        }
        // Before the first merge of a pair there is no agreement to compare with: the FTB side stands in for the
        // baseline, which makes every difference a change on the OPAC side, i.e. OPAC wins.
        boolean first = !pair.isMerged();
        if (first) pair.clearBaseline();

        addMembers(pair, party, team, first);
        if (isUnlinked(pair, team)) return;
        mergeOwner(pair, party, team, first);
        removeMembers(pair, party, team, first);
        if (isUnlinked(pair, team)) return;
        mergeRanks(pair, party, team, first);
        mergeInvites(pair, party, team, first);
        mergeName(pair, party, team);
        pair.setMerged(true);
    }

    /**
     * Whether every current member of the OPAC party was a member of the pair's FTB party: the pair was merged and
     * all of them are in its baseline. A member only enters the baseline once both mods have them (after joining
     * the FTB party, or coming from it), and only leaves it when a merge has processed them leaving, so a member
     * who still waits to join the FTB party (unknown to FTB Teams, party full, refused) or was added in OPAC a
     * moment ago is not in it, while one who was kicked from the FTB party as part of its deletion still is. A
     * baseline that is incomplete for any other reason only errs towards keeping the party.
     */
    private static boolean wasMirroredCompletely(Pair pair, IServerPartyAPI party) {
        if (!pair.isMerged()) return false;
        for (UUID memberId : opacMemberIds(party))
            if (!pair.hasMember(memberId)) return false;
        return true;
    }

    /** Whether a step of the merge made the FTB party of the pair disappear (its last member left). */
    private boolean isUnlinked(Pair pair, PartyTeam team) {
        return !team.getId().equals(pair.ftbId()) || partyTeam(pair.ftbId()) != team;
    }

    private static Set<UUID> opacMemberIds(IServerPartyAPI party) {
        Set<UUID> ids = new HashSet<>();
        party.getMemberInfoStream().forEach(member -> ids.add(member.getUUID()));
        return ids;
    }

    private static Set<UUID> union(Set<UUID> a, Set<UUID> b, Set<UUID> c) {
        Set<UUID> all = new HashSet<>(a);
        all.addAll(b);
        all.addAll(c);
        return all;
    }

    /** Members one side has and the other never had: the other side gets them. */
    private void addMembers(Pair pair, IServerPartyAPI party, PartyTeam team, boolean first) {
        Set<UUID> opacMembers = opacMemberIds(party);
        Set<UUID> ftbMembers = new HashSet<>(team.getMembers());
        for (UUID playerId : union(opacMembers, ftbMembers, pair.members().keySet())) {
            boolean inOpac = opacMembers.contains(playerId);
            boolean inFtb = ftbMembers.contains(playerId);
            boolean inBaseline = first ? inFtb : pair.hasMember(playerId);
            if (inOpac == inFtb) {
                if (!inOpac) pair.removeMember(playerId);
                continue;
            }
            if (inBaseline) continue;//a removal, see removeMembers
            if (inOpac) addToFtb(pair, party, team, playerId);
            else addToOpac(pair, party, team, playerId);
            if (isUnlinked(pair, team)) return;
        }
    }

    /** Members the baseline has and one side no longer does: they left (or were kicked) there, the other side follows. */
    private void removeMembers(Pair pair, IServerPartyAPI party, PartyTeam team, boolean first) {
        Set<UUID> opacMembers = opacMemberIds(party);
        Set<UUID> ftbMembers = new HashSet<>(team.getMembers());
        for (UUID playerId : union(opacMembers, ftbMembers, pair.members().keySet())) {
            boolean inOpac = opacMembers.contains(playerId);
            boolean inFtb = ftbMembers.contains(playerId);
            boolean inBaseline = first ? inFtb : pair.hasMember(playerId);
            if (inOpac == inFtb || !inBaseline) continue;
            if (inOpac) {
                // Left in FTB Teams. OPAC cannot remove its owner; then OPAC wins and the owner is put back.
                if (party.getOwner().getUUID().equals(playerId)) {
                    addToFtb(pair, party, team, playerId);
                } else if (opac.removeMember(party, playerId)) {
                    sync.countChange();
                    pair.removeMember(playerId);
                }
            } else {
                // Left in OPAC
                if (leaveFtbParty(pair, opac.nameOf(party), team, playerId)) pair.removeMember(playerId);
            }
            if (isUnlinked(pair, team)) return;
        }
    }

    /** An OPAC member who is not in the FTB party: joins it, leaving whatever other FTB party they are in. */
    private void addToFtb(Pair pair, IServerPartyAPI party, PartyTeam team, UUID playerId) {
        IPartyMemberAPI member = party.getMemberInfo(playerId);
        if (member == null) return;
        String playerName = member.getUsername();
        String partyName = opac.nameOf(party);
        UUID ownerId = party.getOwner().getUUID();
        if (manager().getPersonalTeamForPlayerID(playerId) == null) {
            sync.pending(pair.opacId(), "member", playerId, partyName,
                    playerName + " is not known to FTB Teams yet and joins the FTB Teams party at their next login",
                    KEY + "pending_player_unknown", new Object[] {playerName}, ownerId);
            return;
        }
        if (ServerConfig.isPartyFull(team.getMembers().size())) {
            int max = ServerConfig.MAX_TEAM_SIZE.get();
            sync.pending(pair.opacId(), "member", playerId, partyName,
                    playerName + " cannot join the FTB Teams party, it is full (max_party_size = " + max
                            + " in the FTB Teams server config)",
                    KEY + "pending_ftb_party_full", new Object[] {playerName, max}, playerId, ownerId);
            return;
        }
        PartyTeam current = partyOf(playerId);
        if (current != null && current != team) {
            // In another FTB party. If that one has a pair, its own merge may take the player out of it
            Pair otherPair = sync.getStore().byFtbTeam(current.getId());
            if (otherPair != null && !inProgress.contains(otherPair.opacId())) {
                reconcilePair(otherPair);
                current = partyOf(playerId);
            }
            // Still there: the membership differs between the mods, OPAC wins
            if (current != null && current != team) {
                if (!leaveFtbParty(pair, partyName, current, playerId)) return;
                sync.tell(playerId, KEY + "moved", team.getDisplayName());
            }
        }
        if (isUnlinked(pair, team)) return;
        if (!team.getMembers().contains(playerId)) {
            try {
                team.join(server.getPlayerList().getPlayer(playerId), new GameProfile(playerId, playerName));
                sync.countChange();
            } catch (CommandSyntaxException e) {
                sync.pending(pair.opacId(), "member", playerId, partyName,
                        playerName + " could not join the FTB Teams party: " + e.getMessage(),
                        KEY + "pending_ftb_rejected", new Object[] {playerName, e.getMessage()}, playerId, ownerId);
                return;
            }
        }
        boolean officer = !member.isOwner() && FtbSyncOpac.isOfficerRank(member.getRank());
        if (officer && rawRank(team, playerId) == TeamRank.MEMBER) setFtbRank(team, playerId, TeamRank.OFFICER);
        pair.setMember(playerId, new MemberState(member.getRank(), officer || member.isOwner()));
    }

    /** An FTB member who is not in the OPAC party: joins it, unless OPAC says no, in which case they leave the FTB party again. */
    private void addToOpac(Pair pair, IServerPartyAPI party, PartyTeam team, UUID playerId) {
        String playerName = ftbPlayerName(playerId);
        IServerPartyAPI current = opac.partyOf(playerId);
        if (current != null && current != party) {
            // In another OPAC party. If that one has a pair, its own merge may take the player out of it
            Pair otherPair = sync.getStore().byOpacParty(current.getId());
            if (otherPair != null && !inProgress.contains(otherPair.opacId())) reconcilePair(otherPair);
            if (isUnlinked(pair, team) || !team.getMembers().contains(playerId)) return;
        }
        boolean officer = rawRank(team, playerId).isOfficerOrBetter();
        PartyMemberRank rank = officer ? PartyMemberRank.MODERATOR : PartyMemberRank.MEMBER;
        FtbSyncOpac.AddResult result = opac.addMember(party, playerId, playerName, rank);
        if (result == FtbSyncOpac.AddResult.ADDED) {
            sync.countChange();
            pair.setMember(playerId, new MemberState(rank, officer));
            return;
        }
        // OPAC wins: the join is undone in FTB Teams, and the player is told why
        String ftbPartyName = team.getDisplayName();
        LOGGER.info("[TeamClaims] FTB Teams sync: {} joined the FTB Teams party '{}' but cannot join its party ({}), "
                + "removing them from the FTB Teams party again", playerName, ftbPartyName, result);
        if (!leaveFtbParty(pair, opac.nameOf(party), team, playerId)) return;
        switch (result) {
            case IN_OTHER_PARTY -> sync.tell(playerId, KEY + "reverted_join_other_party", ftbPartyName);
            case PARTY_FULL -> sync.tell(playerId, KEY + "reverted_join_full", ftbPartyName, opac.maxMembers());
            default -> sync.tell(playerId, KEY + "reverted_join", ftbPartyName);
        }
    }

    private void mergeOwner(Pair pair, IServerPartyAPI party, PartyTeam team, boolean first) {
        UUID opacOwner = party.getOwner().getUUID();
        UUID ftbOwner = team.getOwner();
        if (opacOwner.equals(ftbOwner)) {
            pair.setOwner(opacOwner);
            return;
        }
        boolean onlyFtbChanged = !first && opacOwner.equals(pair.owner());
        if (onlyFtbChanged && opac.changeOwner(party, ftbOwner)) {
            sync.countChange();
            pair.setOwner(ftbOwner);
            // OPAC takes the party name from the owner's config, so it just changed with the owner while FTB kept
            // its name. The change came from FTB, so FTB's name is the one to keep: make the name step see it as new.
            pair.setOpacName(opac.nameOf(party));
            pair.setFtbName(null);
            return;
        }
        // OPAC changed its owner, or OPAC wins
        String ownerName = party.getOwner().getUsername();
        if (!team.getMembers().contains(opacOwner)) {
            sync.pending(pair.opacId(), "owner", opacOwner, opac.nameOf(party),
                    ownerName + " cannot become the owner of the FTB Teams party before being a member of it",
                    KEY + "pending_owner", new Object[] {ownerName}, opacOwner);
            return;
        }
        try {
            team.transferOwnership(source(), new GameProfile(opacOwner, ownerName));
            sync.countChange();
            if (opacOwner.equals(team.getOwner())) pair.setOwner(opacOwner);
        } catch (CommandSyntaxException e) {
            sync.pending(pair.opacId(), "owner", opacOwner, opac.nameOf(party),
                    "the ownership of the FTB Teams party could not be given to " + ownerName + ": " + e.getMessage(),
                    KEY + "pending_ftb_rejected", new Object[] {ownerName, e.getMessage()}, opacOwner);
        }
    }

    /**
     * Ranks only have two classes that both mods know: officer (OPAC ADMIN and MODERATOR, FTB OFFICER) and plain
     * member (OPAC CLAIMER and MEMBER, FTB MEMBER). Nothing is touched while the classes agree, which is what
     * keeps an ADMIN an ADMIN and a CLAIMER a CLAIMER. When they differ, OPAC follows only if its rank is still
     * the one of the baseline while FTB's class is not; in every other case FTB follows OPAC.
     */
    private void mergeRanks(Pair pair, IServerPartyAPI party, PartyTeam team, boolean first) {
        List<IPartyMemberAPI> members = new ArrayList<>();
        party.getMemberInfoStream().forEach(members::add);
        boolean ftbChanged = false;
        for (IPartyMemberAPI member : members) {
            UUID playerId = member.getUUID();
            TeamRank ftbRank = rawRank(team, playerId);
            if (!ftbRank.isMemberOrBetter()) continue;//not in both
            boolean ftbOfficer = ftbRank.isOfficerOrBetter();
            if (member.isOwner() || ftbRank == TeamRank.OWNER) {
                // The owner is the owner step's business; only remember that both sides have this member
                pair.setMember(playerId, new MemberState(member.getRank(), ftbOfficer));
                continue;
            }
            PartyMemberRank opacRank = member.getRank();
            boolean opacOfficer = FtbSyncOpac.isOfficerRank(opacRank);
            if (opacOfficer != ftbOfficer) {
                MemberState baseline = first ? null : pair.member(playerId);
                boolean onlyFtbChanged = baseline != null && baseline.opacRank() == opacRank
                        && baseline.ftbOfficer() != ftbOfficer;
                PartyMemberRank followed = ftbOfficer ? PartyMemberRank.MODERATOR : PartyMemberRank.MEMBER;
                if (onlyFtbChanged && opac.setRank(party, playerId, followed)) {
                    sync.countChange();
                    opacRank = followed;
                } else {
                    team.addMember(playerId, opacOfficer ? TeamRank.OFFICER : TeamRank.MEMBER);
                    sync.countChange();
                    ftbChanged = true;
                    ftbOfficer = opacOfficer;
                }
            }
            pair.setMember(playerId, new MemberState(opacRank, ftbOfficer));
        }
        if (ftbChanged) publishFtbRanks(team);
    }

    /**
     * Pending invitations exist in both mods, so one can be accepted in either. Without a baseline entry an
     * invitation is new and the other side gets it; with one, the side that no longer has it withdrew it (or the
     * player declined it there) and the other side loses it too.
     */
    private void mergeInvites(Pair pair, IServerPartyAPI party, PartyTeam team, boolean first) {
        Map<UUID, String> opacInvites = new HashMap<>();
        party.getInvitedPlayersStream().forEach((IPartyPlayerInfoAPI invite) -> opacInvites.put(invite.getUUID(), invite.getUsername()));
        Set<UUID> ftbInvites = new HashSet<>();
        for (Map.Entry<UUID, TeamRank> rank : team.getPlayersByRank(TeamRank.NONE).entrySet())
            if (rank.getValue() == TeamRank.INVITED) ftbInvites.add(rank.getKey());
        if (opacInvites.isEmpty() && ftbInvites.isEmpty() && pair.invites().isEmpty()) return;
        boolean ftbChanged = false;
        for (UUID playerId : union(opacInvites.keySet(), ftbInvites, pair.invites())) {
            boolean inOpac = opacInvites.containsKey(playerId);
            boolean inFtb = ftbInvites.contains(playerId);
            boolean inBaseline = !first && pair.hasInvite(playerId);
            if (inOpac == inFtb) {
                pair.setInvite(playerId, inOpac);
            } else if (inOpac && inBaseline) {
                if (opac.uninvite(party, playerId)) sync.countChange();
                pair.setInvite(playerId, false);
            } else if (inOpac) {
                if (rawRank(team, playerId).isMemberOrBetter()) continue;//already a member there, nothing to invite
                team.addMember(playerId, TeamRank.INVITED);
                sync.countChange();
                ftbChanged = true;
                pair.setInvite(playerId, true);
            } else if (inBaseline) {
                team.removeMember(playerId);
                sync.countChange();
                ftbChanged = true;
                pair.setInvite(playerId, false);
            } else {
                String playerName = ftbPlayerName(playerId);
                FtbSyncOpac.InviteResult result = party.getMemberInfo(playerId) != null ? FtbSyncOpac.InviteResult.IN_A_PARTY
                        : opac.invite(party, playerId, playerName);
                if (result == FtbSyncOpac.InviteResult.INVITED) {
                    sync.countChange();
                    pair.setInvite(playerId, true);
                    continue;
                }
                // OPAC wins: the invitation is withdrawn in FTB Teams. There is no event that says who made it,
                // so the owner and the officers of the FTB party are told.
                LOGGER.info("[TeamClaims] FTB Teams sync: the FTB Teams invitation of {} to '{}' cannot exist in its party ({}), "
                        + "withdrawing it", playerName, team.getDisplayName(), result);
                team.removeMember(playerId);
                sync.countChange();
                ftbChanged = true;
                pair.setInvite(playerId, false);
                for (UUID staff : team.getPlayersByRank(TeamRank.OFFICER).keySet()) {
                    switch (result) {
                        case INVITE_LIMIT -> sync.tell(staff, KEY + "reverted_invite_limit", playerName, opac.maxInvites());
                        case PARTY_FULL -> sync.tell(staff, KEY + "reverted_invite_full", playerName, opac.maxMembers());
                        case IN_A_PARTY -> sync.tell(staff, KEY + "reverted_invite_in_party", playerName);
                        default -> sync.tell(staff, KEY + "reverted_invite", playerName);
                    }
                }
            }
        }
        if (ftbChanged) publishFtbRanks(team);
    }

    /**
     * The baseline holds the name each side really had after the last merge, so a name that had to be sanitised
     * for the other side is not seen as a difference. Only FTB's name changed: OPAC follows. Otherwise FTB follows.
     */
    private void mergeName(Pair pair, IServerPartyAPI party, PartyTeam team) {
        String opacName = opac.nameOf(party);
        String ftbName = team.getDisplayName();
        boolean opacChanged = !opacName.equals(pair.opacName());
        boolean ftbChanged = !ftbName.equals(pair.ftbName());
        if (!opacChanged && !ftbChanged) return;
        if (!opacChanged) {
            if (opac.setName(party, FtbSyncNames.toOpacName(ftbName))) {
                String newOpacName = opac.nameOf(party);
                if (!newOpacName.equals(opacName)) sync.countChange();
                pair.setOpacName(newOpacName);
                pair.setFtbName(ftbName);
                return;
            }
            // OPAC cannot take a name (the server does not let players set the party name): OPAC wins
            for (UUID staff : team.getPlayersByRank(TeamRank.OFFICER).keySet())
                sync.tell(staff, KEY + "reverted_name", opacName);
        }
        String target = FtbSyncNames.toFtbName(opacName);
        if (!target.equals(ftbName)) {
            TeamPropertyCollection previous = team.getProperties().copy();
            team.setProperty(TeamProperties.DISPLAY_NAME, target);
            sync.countChange();
            try {
                TeamEvent.PROPERTIES_CHANGED.invoker().accept(new TeamPropertiesChangedEvent(team, previous));
            } finally {
                // Also when a listener of another mod fails: the name is set, so the clients are to know it
                team.syncOnePropertyToAll(server, TeamProperties.DISPLAY_NAME, target);
            }
        }
        pair.setOpacName(opacName);
        pair.setFtbName(team.getDisplayName());
    }

    // ==================== A party that exists in only one mod ====================

    /**
     * The FTB party of an OPAC party that has none: the FTB party its owner is in, if that one has no pair yet
     * (it is then brought in line by the first merge, OPAC winning), otherwise a new one with the party's name.
     *
     * @return null if there is none yet (noted as pending)
     */
    @Nullable
    private PartyTeam linkOrCreateFtbParty(Pair pair, IServerPartyAPI party) {
        UUID ownerId = party.getOwner().getUUID();
        String ownerName = party.getOwner().getUsername();
        String partyName = opac.nameOf(party);
        if (manager().getPersonalTeamForPlayerID(ownerId) == null) {
            sync.pending(pair.opacId(), "create", ownerId, partyName,
                    "its owner " + ownerName + " is not known to FTB Teams yet, the FTB Teams party is created at their next login",
                    KEY + "pending_owner_unknown", new Object[] {ownerName});
            return null;
        }
        PartyTeam current = partyOf(ownerId);
        if (current != null) {
            Pair otherPair = sync.getStore().byFtbTeam(current.getId());
            IServerPartyAPI ownedByFtbOwner = opac.partyOwnedBy(current.getOwner());
            if (otherPair == null && (ownedByFtbOwner == null || ownedByFtbOwner == party)) {
                LOGGER.info("[TeamClaims] FTB Teams sync: linking party '{}' to the existing FTB Teams party '{}' of its owner",
                        partyName, current.getDisplayName());
                sync.getStore().link(pair, current.getId());
                pair.clearBaseline();
                return current;
            }
            // The owner is in an FTB party that belongs to another party: the membership differs, OPAC wins
            if (otherPair != null && !inProgress.contains(otherPair.opacId())) {
                reconcilePair(otherPair);
                current = partyOf(ownerId);
            }
            if (current != null && !leaveFtbParty(pair, partyName, current, ownerId)) return null;
        }
        String error;
        PartyTeam created = null;
        ServerPlayer onlineOwner = server.getPlayerList().getPlayer(ownerId);
        String ftbName = FtbSyncNames.toFtbName(partyName);
        try {
            created = manager().createParty(ownerId, onlineOwner, ftbName, null, null);
            error = null;
        } catch (CommandSyntaxException e) {
            error = e.getMessage();
        }
        if (created == null && onlineOwner != null) {
            // Refused for the player (FTB checks its create command's permission, which a permission mod can
            // deny): OPAC wins, so the party is created without asking as that player
            try {
                created = manager().createParty(ownerId, null, ftbName, null, null);
                server.getPlayerList().sendPlayerPermissionLevel(onlineOwner);
            } catch (CommandSyntaxException e) {
                error = e.getMessage();
            }
        }
        if (created == null) {
            sync.pending(pair.opacId(), "create", ownerId, partyName,
                    "the FTB Teams party could not be created: " + error,
                    KEY + "pending_ftb_rejected", new Object[] {ownerName, String.valueOf(error)}, ownerId);
            return null;
        }
        sync.countChange();
        LOGGER.info("[TeamClaims] FTB Teams sync: created the FTB Teams party '{}' for party '{}'", ftbName, partyName);
        sync.getStore().link(pair, created.getId());
        pair.clearBaseline();
        // Both sides agree on the owner and the name so far; members, ranks and invitations follow from the merge
        pair.setOwner(ownerId);
        pair.setMember(ownerId, new MemberState(party.getOwner().getRank(), true));
        pair.setOpacName(partyName);
        pair.setFtbName(created.getDisplayName());
        pair.setMerged(true);
        return created;
    }

    /**
     * The OPAC party of an FTB party that has no pair: a new party of the FTB owner with the FTB party's name and
     * its team config, exactly as {@code /<parties> create <name>} makes one. The other members, their ranks and
     * the pending invitations then come from the first merge, which sees them as new on the FTB side.
     * <p>
     * If the FTB owner already is in an OPAC party, the membership differs between the mods and OPAC wins: merging
     * that party's pair moves the owner to its FTB party (or links this FTB party to it), after which this FTB party
     * has another owner, a pair, or is gone.
     */
    private void createOpacPartyFor(PartyTeam team) {
        UUID subject = team.getId();
        sync.beginSubject(subject);
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                if (partyTeam(team.getId()) != team || team.getMembers().isEmpty()) return;
                if (sync.getStore().byFtbTeam(team.getId()) != null) return;
                UUID ownerId = team.getOwner();
                IServerPartyAPI existing = opac.partyOf(ownerId);
                if (existing == null) {
                    createOpacPartyFor(team, ownerId);
                    return;
                }
                Pair existingPair = sync.getStore().getOrCreate(existing.getId());
                if (inProgress.contains(existingPair.opacId())) return;//that merge is dealing with it
                reconcilePair(existingPair);
            }
            if (partyTeam(team.getId()) == team && sync.getStore().byFtbTeam(team.getId()) == null) {
                String ownerName = ftbPlayerName(team.getOwner());
                sync.pending(subject, "create", team.getOwner(), team.getDisplayName(),
                        "no party can be created for the FTB Teams party: its owner " + ownerName
                                + " is in another party and cannot be moved to that party's FTB Teams party yet",
                        KEY + "pending_opac_party", new Object[] {ownerName}, team.getOwner());
            }
        } catch (RuntimeException e) {
            sync.failure(subject, team.getDisplayName(), e);
        } finally {
            sync.endSubject(subject);
        }
    }

    private void createOpacPartyFor(PartyTeam team, UUID ownerId) {
        String ownerName = ftbPlayerName(ownerId);
        String ftbName = team.getDisplayName();
        IServerPartyAPI party = opac.createParty(new GameProfile(ownerId, ownerName), FtbSyncNames.toOpacName(ftbName));
        if (party == null) {
            sync.pending(team.getId(), "create", ownerId, ftbName,
                    "Open Parties and Claims did not create a party for the FTB Teams party of " + ownerName,
                    KEY + "pending_opac_rejected", new Object[] {ownerName}, ownerId);
            return;
        }
        sync.countChange();
        LOGGER.info("[TeamClaims] FTB Teams sync: created party '{}' for the FTB Teams party '{}' of {}",
                opac.nameOf(party), ftbName, ownerName);
        Pair pair = sync.getStore().getOrCreate(party.getId());
        sync.getStore().link(pair, team.getId());
        pair.clearBaseline();
        // The baseline is the new party: just the owner. The rest of the FTB party is new compared to it, and so is
        // FTB's name: the name step then records the pair of names, or, if OPAC could not take the name, gives
        // FTB the name OPAC has.
        pair.setOwner(ownerId);
        pair.setMember(ownerId, new MemberState(party.getOwner().getRank(), true));
        pair.setOpacName(opac.nameOf(party));
        pair.setFtbName(null);
        pair.setMerged(true);
        reconcilePair(pair);
    }

    // ==================== FTB Teams access ====================

    private static TeamManagerImpl manager() {
        return TeamManagerImpl.INSTANCE;
    }

    /** A source for the FTB methods that want one for their chat output, which nobody is to receive. */
    private CommandSourceStack source() {
        return server.createCommandSourceStack().withSuppressedOutput();
    }

    @Nullable
    private PartyTeam partyTeam(@Nullable UUID teamId) {
        if (teamId == null) return null;
        AbstractTeam team = manager().getTeamMap().get(teamId);
        return team instanceof PartyTeam partyTeam && partyTeam.isValid() ? partyTeam : null;
    }

    private List<PartyTeam> partyTeams() {
        List<PartyTeam> teams = new ArrayList<>();
        for (AbstractTeam team : manager().getTeamMap().values())
            if (team instanceof PartyTeam partyTeam && partyTeam.isValid()) teams.add(partyTeam);
        return teams;
    }

    /** The FTB party a player is in, null when they are in none (or FTB Teams does not know them). */
    @Nullable
    private PartyTeam partyOf(UUID playerId) {
        PlayerTeam personal = manager().getPersonalTeamForPlayerID(playerId);
        return personal != null && personal.getEffectiveTeam() instanceof PartyTeam partyTeam ? partyTeam : null;
    }

    /**
     * The rank as stored in the team, never null. Not {@code getRankForPlayer}: that reports INVITED for everybody
     * when the team's "free to join" property is on.
     */
    private static TeamRank rawRank(PartyTeam team, UUID playerId) {
        TeamRank rank = team.getPlayersByRank(TeamRank.NONE).get(playerId);
        return rank == null ? TeamRank.NONE : rank;
    }

    private void setFtbRank(PartyTeam team, UUID playerId, TeamRank rank) {
        team.addMember(playerId, rank);
        sync.countChange();
        publishFtbRanks(team);
    }

    /** After changing ranks directly: marks the team for saving and sends it to the clients, as FTB's own rank commands do. */
    private void publishFtbRanks(PartyTeam team) {
        team.markDirty();
        manager().syncToAll(team);
    }

    /** The name FTB Teams knows a player by, else the server's profile cache, else the UUID. */
    private String ftbPlayerName(UUID playerId) {
        PlayerTeam personal = manager().getPersonalTeamForPlayerID(playerId);
        if (personal != null && !personal.getPlayerName().isEmpty()) return personal.getPlayerName();
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache profileCache = server.getProfileCache();
        if (profileCache != null) {
            GameProfile cached = profileCache.get(playerId).orElse(null);
            if (cached != null && cached.getName() != null) return cached.getName();
        }
        return playerId.toString();
    }

    /**
     * Takes a player out of an FTB party. FTB does not let an owner leave while there are other members, so the
     * ownership is handed over first: to the owner of the paired OPAC party if that player is in the FTB party, else
     * to its highest ranked other member. A party whose last member leaves is deleted by FTB Teams; a pair that was
     * linked to it then has no FTB party and gets a new one from its next merge.
     *
     * @param pair  the pair being merged, which this is pending for if FTB Teams refuses
     * @param partyName  the name of that pair's OPAC party
     * @return false if FTB Teams refused (noted as pending)
     */
    private boolean leaveFtbParty(Pair pair, String partyName, PartyTeam team, UUID playerId) {
        try {
            if (team.isOwner(playerId) && team.getMembers().size() > 1) {
                UUID successor = successorOf(team, playerId);
                team.transferOwnership(source(), new GameProfile(successor, ftbPlayerName(successor)));
                sync.countChange();
            }
            team.leave(playerId);
            sync.countChange();
        } catch (CommandSyntaxException e) {
            String playerName = ftbPlayerName(playerId);
            sync.pending(pair.opacId(), "leave", playerId, partyName,
                    playerName + " could not be taken out of the FTB Teams party '" + team.getDisplayName() + "': " + e.getMessage(),
                    KEY + "pending_ftb_rejected", new Object[] {playerName, String.valueOf(e.getMessage())});
            return false;
        }
        if (partyTeam(team.getId()) == null) {
            Pair linked = sync.getStore().byFtbTeam(team.getId());
            if (linked != null) {
                sync.getStore().link(linked, null);
                linked.clearBaseline();
            }
        }
        return true;
    }

    private UUID successorOf(PartyTeam team, UUID leavingOwner) {
        Map<UUID, TeamRank> members = team.getPlayersByRank(TeamRank.MEMBER);
        Pair pair = sync.getStore().byFtbTeam(team.getId());
        IServerPartyAPI party = pair == null ? null : opac.party(pair.opacId());
        if (party != null) {
            UUID opacOwner = party.getOwner().getUUID();
            if (!opacOwner.equals(leavingOwner) && members.containsKey(opacOwner)) return opacOwner;
        }
        UUID best = null;
        int bestPower = Integer.MIN_VALUE;
        for (Map.Entry<UUID, TeamRank> member : members.entrySet()) {
            UUID candidate = member.getKey();
            if (candidate.equals(leavingOwner)) continue;
            int power = member.getValue().getPower();
            if (best == null || power > bestPower || power == bestPower && candidate.compareTo(best) < 0) {
                best = candidate;
                bestPower = power;
            }
        }
        return best == null ? leavingOwner : best;
    }

    /**
     * Disbands the FTB party of a pair whose OPAC party was removed.
     *
     * @return false if FTB Teams refused (noted as pending)
     */
    private boolean disbandFtbParty(Pair pair, PartyTeam team) {
        String ftbName = team.getDisplayName();
        try {
            team.forceDisband(source());
        } catch (CommandSyntaxException e) {
            sync.pending(pair.opacId(), "disband", null, pair.opacName() != null ? pair.opacName() : ftbName,
                    "its FTB Teams party '" + ftbName + "' could not be disbanded: " + e.getMessage(),
                    KEY + "pending_ftb_disband", new Object[] {ftbName, String.valueOf(e.getMessage())}, pair.owner());
            return false;
        }
        sync.countChange();
        LOGGER.info("[TeamClaims] FTB Teams sync: party {} was removed, disbanded its FTB Teams party '{}'", pair.opacId(), ftbName);
        return true;
    }
}
