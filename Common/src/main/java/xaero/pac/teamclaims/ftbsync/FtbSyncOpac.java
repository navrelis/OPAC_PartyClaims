package xaero.pac.teamclaims.ftbsync;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.parties.party.IPartyPlayerInfo;
import xaero.pac.common.parties.party.ally.IPartyAlly;
import xaero.pac.common.parties.party.member.IPartyMember;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.platform.Services;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.command.PartyOnCommandUpdater;
import xaero.pac.common.server.parties.party.IServerParty;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.IPlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.config.TeamConfigManager;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The OPAC half of the FTB Teams sync: every change the sync makes to an OPAC party goes through here.
 * <p>
 * The changes use OPAC's normal party objects, so the {@code [Team Claims]} party hooks fire as for any other change
 * and the existing Team Claims logic (team config, team sub-configs, hand-over of a leaver's team claims, budgets)
 * follows by itself. On top of that, each method does what the party command of the same operation does besides
 * changing the party: the member/invite limits, the command tree and permission update of the affected online
 * players, and the party chat line ({@link PartyOnCommandUpdater}). Where a party chat line names the player who
 * ran the command, {@link #ACTOR} stands in.
 * <p>
 * No FTB class is referenced here. Server-thread confined.
 */
final class FtbSyncOpac {

    /** Named in OPAC's party chat lines as the one who made a change that came from the other mod. */
    private static final String ACTOR = "FTB Teams";

    enum AddResult { ADDED, IN_OTHER_PARTY, PARTY_FULL, FAILED }

    enum InviteResult { INVITED, IN_A_PARTY, INVITE_LIMIT, PARTY_FULL, FAILED }

    private final MinecraftServer server;

    FtbSyncOpac(MinecraftServer server) {
        this.server = server;
    }

    // ==================== Reading ====================

    @Nullable
    private IServerData<?, ?> data() {
        return ServerData.from(server);
    }

    @Nullable
    IPartyManagerAPI partyManager() {
        IServerData<?, ?> data = data();
        return data == null ? null : data.getPartyManager();
    }

    boolean partiesEnabled() {
        return ServerConfig.CONFIG.partiesEnabled.get() && partyManager() != null;
    }

    @Nullable
    IServerPartyAPI party(UUID partyId) {
        IPartyManagerAPI partyManager = partyManager();
        return partyManager == null ? null : partyManager.getPartyById(partyId);
    }

    @Nullable
    IServerPartyAPI partyOf(UUID playerId) {
        IPartyManagerAPI partyManager = partyManager();
        return partyManager == null ? null : partyManager.getPartyByMember(playerId);
    }

    @Nullable
    IServerPartyAPI partyOwnedBy(UUID playerId) {
        IPartyManagerAPI partyManager = partyManager();
        return partyManager == null ? null : partyManager.getPartyByOwner(playerId);
    }

    int maxMembers() {
        return ServerConfig.CONFIG.maxPartyMembers.get();
    }

    int maxInvites() {
        return ServerConfig.CONFIG.maxPartyInvites.get();
    }

    /** The name the party shows: the owner's {@code PARTY_NAME} option, or the default name when that is empty. */
    String nameOf(IServerPartyAPI party) {
        IServerData<?, ?> data = data();
        if (data != null) {
            String customName = data.getPlayerConfigManager().getLoadedConfig(party.getOwner().getUUID())
                    .getEffective(PlayerConfigOptions.PARTY_NAME);
            if (customName != null && !customName.isBlank()) return customName;
        }
        return party.getDefaultName();
    }

    static boolean isOfficerRank(PartyMemberRank rank) {
        return rank == PartyMemberRank.ADMIN || rank == PartyMemberRank.MODERATOR;
    }

    // ==================== Writing ====================

    /**
     * Creates the party of {@code ownerProfile} with the given name (already sanitised for OPAC, see
     * {@link FtbSyncNames#toOpacName}) and sets up its team config right away, like
     * {@code TeamClaimsCommands.createPartyWithTeamName} does for {@code /<parties> create <name>}.
     *
     * @return the new party, null if OPAC did not create one (the owner already owns a party)
     */
    @Nullable
    IServerPartyAPI createParty(GameProfile ownerProfile, String opacName) {
        IServerData<?, ?> data = data();
        if (data == null) return null;
        IServerPartyAPI party = data.getPartyManager().createPartyForOwner(ownerProfile);
        if (party == null) return null;
        setName(party, opacName);
        // Team config and the owner's team sub-config right away; the queued party event then finds them
        TeamConfigManager teamConfigs = TeamClaimsCommon.getTeamConfigManager();
        if (teamConfigs != null) teamConfigs.createTeamConfig(party);
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerProfile.getId());
        if (owner != null) {
            owner.sendSystemMessage(data.getAdaptiveLocalizer().getFor(owner, "gui.xaero_parties_party_created"));
            data.getPlayerPermissionChangeHandler().sendCommandsAndUpdatePermissions(owner, data, false);
        }
        return party;
    }

    /** Removes the party like {@code /<parties> destroy confirm}: every online member gets their commands updated. */
    void removeParty(IServerPartyAPI party) {
        IServerData<?, ?> data = data();
        if (data == null) return;
        data.getPartyManager().removeParty(party);
        new PartyOnCommandUpdater().update(null, data, typed(party), data.getPlayerConfigManager(), member -> true,
                Component.translatable("gui.xaero_parties_party_destroy_members_info", actorName(ChatFormatting.YELLOW)));
    }

    /**
     * Adds a member like accepting an invitation does ({@code /<parties> join}): refused when the player is in
     * another party or the party has reached {@code maxPartyMembers}.
     */
    AddResult addMember(IServerPartyAPI party, UUID playerId, String username, PartyMemberRank rank) {
        IServerData<?, ?> data = data();
        if (data == null) return AddResult.FAILED;
        if (data.getPartyManager().getPartyByMember(playerId) != null) return AddResult.IN_OTHER_PARTY;
        if (party.getMemberCount() >= maxMembers()) return AddResult.PARTY_FULL;
        IPartyMemberAPI added = party.addMember(playerId, rank, username);
        if (added == null) return AddResult.FAILED;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(data.getAdaptiveLocalizer().getFor(player, "gui.xaero_parties_join_success",
                    party.getDefaultName()));
            data.getPlayerPermissionChangeHandler().sendCommandsAndUpdatePermissions(player, data, false);
        }
        announce(data, party, Component.translatable("gui.xaero_parties_join_success_info",
                Component.literal(added.getUsername()).withStyle(ChatFormatting.DARK_GREEN)));
        return AddResult.ADDED;
    }

    /** Removes a member like {@code /<parties> leave}. The owner cannot be removed. */
    boolean removeMember(IServerPartyAPI party, UUID playerId) {
        IServerData<?, ?> data = data();
        if (data == null) return false;
        IPartyMemberAPI removed = party.removeMember(playerId);
        if (removed == null) return false;
        announce(data, party, Component.translatable("gui.xaero_parties_leave_party_message",
                Component.literal(removed.getUsername()).withStyle(ChatFormatting.YELLOW)));
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            data.getPlayerPermissionChangeHandler().sendCommandsAndUpdatePermissions(player, data, false);
            player.sendSystemMessage(data.getAdaptiveLocalizer().getFor(player, "gui.xaero_parties_leave_caster_message",
                    party.getDefaultName()));
        }
        return true;
    }

    /** Transfers the ownership like {@code /<parties> transfer <member> confirm}. The new owner must be a member. */
    boolean changeOwner(IServerPartyAPI party, UUID newOwnerId) {
        IServerData<?, ?> data = data();
        IPartyMemberAPI newOwner = party.getMemberInfo(newOwnerId);
        if (data == null || newOwner == null || newOwner.isOwner()) return false;
        UUID oldOwnerId = party.getOwner().getUUID();
        String newOwnerName = newOwner.getUsername();
        if (!typed(party).changeOwner(newOwnerId, newOwnerName)) return false;
        for (UUID affected : new UUID[] {newOwnerId, oldOwnerId}) {
            ServerPlayer player = server.getPlayerList().getPlayer(affected);
            if (player != null) data.getPlayerPermissionChangeHandler().sendCommandsAndUpdatePermissions(player, data, false);
        }
        announce(data, party, Component.translatable("gui.xaero_parties_transfer_success",
                actorName(ChatFormatting.DARK_GREEN), Component.literal(newOwnerName).withStyle(ChatFormatting.YELLOW)));
        return true;
    }

    /** Sets a member's rank like {@code /<parties> member rank}. */
    boolean setRank(IServerPartyAPI party, UUID memberId, PartyMemberRank rank) {
        IServerData<?, ?> data = data();
        IPartyMemberAPI member = party.getMemberInfo(memberId);
        if (data == null || member == null || member.isOwner()) return false;
        if (member.getRank() == rank) return true;
        if (!party.setRank(member, rank)) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(memberId);
        if (player != null) data.getPlayerPermissionChangeHandler().sendCommandsAndUpdatePermissions(player, data, false);
        announce(data, party, Component.translatable("gui.xaero_parties_rank_party_message",
                actorName(ChatFormatting.DARK_GREEN), Component.literal(member.getUsername()).withStyle(ChatFormatting.YELLOW),
                Component.literal(rank.toString()).withStyle(style -> style.withColor(rank.getColor()))));
        return true;
    }

    /**
     * Invites a player like {@code /<parties> member invite}, with its checks, but without the chat prompt for the
     * invited player: the mod the invitation was made in has already told them. The party is remembered as the
     * player's last invitation, so OPAC's join command suggests it.
     */
    InviteResult invite(IServerPartyAPI party, UUID playerId, String username) {
        IServerData<?, ?> data = data();
        if (data == null) return InviteResult.FAILED;
        if (party.isInvited(playerId)) return InviteResult.INVITED;
        if (data.getPartyManager().getPartyByMember(playerId) != null) return InviteResult.IN_A_PARTY;
        if (party.getInviteCount() >= maxInvites()) return InviteResult.INVITE_LIMIT;
        if (party.getMemberCount() >= maxMembers()) return InviteResult.PARTY_FULL;
        if (party.invitePlayer(playerId, username) == null) return InviteResult.FAILED;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null)
            Services.PLATFORM.getEntityAccess().getPersistentData(player).putUUID("xaero_OPAC_LastInviteId", party.getId());
        announce(data, party, Component.translatable("gui.xaero_parties_invite_party_message",
                actorName(ChatFormatting.DARK_GREEN), Component.literal(username).withStyle(ChatFormatting.YELLOW)));
        return InviteResult.INVITED;
    }

    boolean uninvite(IServerPartyAPI party, UUID playerId) {
        return party.uninvitePlayer(playerId) != null;
    }

    /**
     * Makes the party show {@code opacName} (already sanitised for OPAC; empty or equal to the default name means
     * "no custom name") by setting the owner's {@code PARTY_NAME} option, as {@code CreatePartyCommand} does.
     *
     * @return false if the name could not be applied: the server does not let players configure the party name, or
     *         OPAC's validator rejects it
     */
    boolean setName(IServerPartyAPI party, String opacName) {
        IServerData<?, ?> data = data();
        if (data == null) return false;
        IPlayerConfigAPI ownerConfig = data.getPlayerConfigManager().getLoadedConfig(party.getOwner().getUUID());
        String target = opacName.isBlank() || opacName.equals(party.getDefaultName()) ? "" : opacName;
        String current = ownerConfig.getEffective(PlayerConfigOptions.PARTY_NAME);
        if (target.equals(current == null ? "" : current)) return true;
        if (!ownerConfig.isOptionAllowed(PlayerConfigOptions.PARTY_NAME)
                || ownerConfig instanceof IPlayerConfig internalConfig
                && internalConfig.isOptionDefaulted(PlayerConfigOptions.PARTY_NAME))
            return false;
        if (!PlayerConfigOptions.PARTY_NAME.getServerSideValidator().test(ownerConfig, target)) return false;
        return ownerConfig.tryToSet(PlayerConfigOptions.PARTY_NAME, target) == IPlayerConfigAPI.SetResult.SUCCESS;
    }

    // ==================== Helpers ====================

    private static Component actorName(ChatFormatting color) {
        return Component.literal(ACTOR).withStyle(color);
    }

    /** The party chat line of a change, to every online member (and to party admin mode players), like the party commands. */
    private static void announce(IServerData<?, ?> data, IServerPartyAPI party, Component message) {
        new PartyOnCommandUpdater().update(null, data, typed(party), data.getPlayerConfigManager(), member -> false, message);
    }

    @SuppressWarnings("unchecked")
    private static IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> typed(IServerPartyAPI party) {
        return (IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>) party;
    }
}
