package xaero.pac.teamclaims;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import xaero.pac.common.claims.player.IPlayerChunkClaim;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.claims.ServerClaimsManager;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.teamclaims.config.TeamConfigManager;
import xaero.pac.teamclaims.config.TeamConfigManager.PartyEventType;
import xaero.pac.teamclaims.ftbsync.FtbTeamsSync;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Implementation of the bridge interface that delegates to TeamClaimManager
 * and TeamConfigManager. Installed by {@link TeamClaimsCommon#onAddonRegister}.
 */
class TeamClaimsBridgeHandler implements TeamClaimsIntegration.TeamClaimsHandler {

    @Override
    public boolean allowsTeamUnclaim(
            ServerClaimsManager claimsManager, ResourceLocation dimension,
            UUID id, int x, int z, PlayerChunkClaim currentClaim) {
        return isOtherMembersTeamClaim(id, currentClaim);
    }

    @Override
    public boolean allowsTeamForceload(
            ServerClaimsManager claimsManager, ResourceLocation dimension,
            UUID id, int x, int z, PlayerChunkClaim currentClaim) {
        return isOtherMembersTeamClaim(id, currentClaim);
    }

    private boolean isOtherMembersTeamClaim(UUID id, @Nullable PlayerChunkClaim currentClaim) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        if (tcm == null || currentClaim == null) return false;
        return tcm.isTeamClaim(currentClaim) && tcm.areInSameTeam(id, currentClaim.getPlayerId());
    }

    @Override
    @Nullable
    public ClaimResult<PlayerChunkClaim> interceptForceload(
            ServerClaimsManager claimsManager, ResourceLocation dimension,
            UUID id, int x, int z, PlayerChunkClaim currentClaim) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        if (tcm == null || currentClaim == null) return null;
        // Only a team claim's forceload counts for the team; a private one is OPAC's own check on the private count.
        if (!tcm.isTeamClaim(currentClaim)) return null;
        return tcm.checkTeamForceloadBudget(id, new TeamClaimManager.ClaimPos(dimension, x, z), currentClaim);
    }

    @Override
    @Nullable
    public ClaimResult<PlayerChunkClaim> interceptClaim(
            ServerClaimsManager claimsManager, ResourceLocation dimension,
            UUID playerId, int subConfigIndex, int x, int z, boolean forceLoaded) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        MinecraftServer server = TeamClaimsCommon.getServer();
        if (tcm == null || server == null) return null;
        // The claim being replaced (if any) decides what this claim really adds to which budget.
        PlayerChunkClaim existing = claimsManager.get(dimension, x, z);
        if (!tcm.isTeamSubConfigIndex(playerId, subConfigIndex)) {
            // A private claim. Replacing a team claim of the own team takes it away from the team, so it needs the
            // team's unclaim role. The private claim limit is OPAC's own check (on the private count); only a
            // forceloaded private claim, which OPAC itself never makes, has to be checked for the forceload limit here.
            ClaimResult<PlayerChunkClaim> roleResult = TeamRoles.checkClaimOverTeamClaim(tcm, server, playerId, existing);
            if (roleResult != null || !forceLoaded) return roleResult;
            return tcm.checkPrivateForceloadBudget(playerId, existing);
        }
        // A team claim. The role comes first: a member who may not make team claims gets that reason, not a budget one.
        // A forceloaded team claim also needs the forceload role (OPAC's claim commands never claim forceloaded, convert
        // does). Then the team's own budget, which is all that limits a team claim.
        ClaimResult<PlayerChunkClaim> roleResult = TeamRoles.checkClaim(server, playerId, forceLoaded);
        if (roleResult != null) return roleResult;
        return tcm.checkTeamClaimBudget(playerId, new TeamClaimManager.ClaimPos(dimension, x, z), forceLoaded, existing);
    }

    @Override
    public boolean isTeamSubConfigIndex(UUID playerId, int subConfigIndex) {
        TeamClaimManager tcm = serverThreadClaimManager();
        return tcm != null && tcm.isTeamSubConfigIndex(playerId, subConfigIndex);
    }

    @Override
    public boolean isTeamClaim(@Nullable IPlayerChunkClaim claim) {
        TeamClaimManager tcm = serverThreadClaimManager();
        return tcm != null && tcm.isTeamClaim(claim);
    }

    /**
     * The claim manager for the two lookups above, which use its server-thread confined caches: null (so "not a team
     * claim", the stock behaviour) should one of the hooks ever run on another thread.
     */
    @Nullable
    private static TeamClaimManager serverThreadClaimManager() {
        MinecraftServer server = TeamClaimsCommon.getServer();
        return server != null && server.isSameThread() ? TeamClaimsCommon.getClaimManager() : null;
    }

    @Override
    public int getOwnedTeamClaimCount(UUID playerId) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        return tcm != null ? tcm.getOwnedTeamClaimCount(playerId) : 0;
    }

    @Override
    public int getOwnedTeamForceloadCount(UUID playerId) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        return tcm != null ? tcm.getOwnedTeamForceloadCount(playerId) : 0;
    }

    @Override
    @Nullable
    public TeamClaimsIntegration.BudgetNumbers getUsedTeamBudget(UUID playerId) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        return tcm != null ? tcm.getUsedTeamBudget(playerId) : null;
    }

    @Override
    public boolean usesServerSideClaimCounts(UUID playerId) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        return tcm != null && tcm.usesServerSideClaimCounts(playerId);
    }

    @Override
    public boolean hasPrivateForceloadLimitChangedUnnoticed(UUID playerId, int privateForceloadLimit) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        return tcm != null && tcm.hasPrivateForceloadLimitChangedUnnoticed(playerId, privateForceloadLimit);
    }

    @Override
    public boolean isInternalEditActive() {
        return TeamConfigManager.INTERNAL_EDIT.get();
    }

    @Override
    public boolean isPlayerTeamAdmin(UUID playerUUID) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        return tcm != null && tcm.isPlayerTeamAdmin(playerUUID);
    }

    @Override
    public void onTeamSubConfigSettingChanged(UUID changedByPlayer, String subId,
            IPlayerConfigOptionSpecAPI<?> option, @Nullable Object value) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        if (tcm != null) {
            tcm.onTeamSubConfigSettingChanged(changedByPlayer, subId, option, value);
        }
    }

    @Override
    @Nullable
    public MinecraftServer getServer() {
        return TeamClaimsCommon.getServer();
    }

    @Override
    public int createPartyWithTeamName(CommandSourceStack source, @Nullable ServerPlayer player,
            GameProfile ownerProfile, String rawTeamName) {
        return TeamClaimsCommands.createPartyWithTeamName(source, player, ownerProfile, rawTeamName, false);
    }

    // ==================== Party events (queued, processed at the end of the tick) ====================

    @Override
    public void onPartyMemberAdded(UUID partyId, UUID memberId) {
        queue(PartyEventType.MEMBER_ADDED, partyId, memberId);
    }

    @Override
    public void onPartyMemberRemoved(UUID partyId, UUID memberId) {
        queue(PartyEventType.MEMBER_REMOVED, partyId, memberId);
    }

    @Override
    public void onPartyOwnerChanged(UUID partyId) {
        queue(PartyEventType.OWNER_CHANGED, partyId, null);
    }

    @Override
    public void onPartyNameChanged(UUID partyId) {
        queue(PartyEventType.NAME_CHANGED, partyId, null);
    }

    @Override
    public void onPartyRemoved(UUID partyId) {
        queue(PartyEventType.PARTY_REMOVED, partyId, null);
    }

    @Override
    public void onPartyInviteChanged(UUID partyId, UUID playerId, boolean invited) {
        // Only the FTB Teams sync cares about invitations: Team Claims itself only counts members
        FtbTeamsSync sync = TeamClaimsCommon.getFtbTeamsSync();
        if (sync != null) sync.onOpacPartyChanged(partyId);
    }

    @Override
    public void onPartyMemberRankChanged(UUID partyId, UUID memberId) {
        // The team roles read the rank when it is needed; only the FTB Teams sync has to hear about a change
        FtbTeamsSync sync = TeamClaimsCommon.getFtbTeamsSync();
        if (sync != null) sync.onOpacPartyChanged(partyId);
    }

    /**
     * Queues the event for the team config manager and, when the FTB Teams sync runs, tells it that the party
     * changed. Both only take a note; the work is done at the end of the tick.
     */
    private static void queue(PartyEventType type, UUID partyId, @Nullable UUID playerId) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        if (tcm != null) tcm.queuePartyEvent(type, partyId, playerId);
        FtbTeamsSync sync = TeamClaimsCommon.getFtbTeamsSync();
        if (sync != null) {
            if (type == PartyEventType.PARTY_REMOVED) sync.onOpacPartyRemoved(partyId);
            else sync.onOpacPartyChanged(partyId);
        }
    }

    // ==================== Sub-config / forceload events ====================

    @Override
    public void onTeamSubConfigExistenceChanged(UUID playerId, String subId, boolean exists) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        if (tcm != null) tcm.onTeamSubConfigExistenceChanged(playerId, subId, exists);
    }

    @Override
    public void onOpacForceloadTicketRemoved(ResourceLocation dimension, int x, int z) {
        TeamForceLoadHandler handler = TeamClaimsCommon.getForceLoadHandler();
        if (handler != null) handler.onOpacTicketRemoved(dimension, x, z);
    }

    // ==================== Claim welcome messages ====================

    @Override
    public boolean isSameTeamTerritory(@Nullable IPlayerChunkClaim lastClaim, @Nullable ResourceKey<Level> lastDimension,
            @Nullable IPlayerChunkClaim currentClaim, @Nullable ResourceKey<Level> currentDimension) {
        if (lastClaim == null || currentClaim == null || lastDimension == null || !lastDimension.equals(currentDimension))
            return false;
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        return tcm != null && tcm.isSameTeamTerritory(lastClaim, currentClaim);
    }

    @Override
    public boolean areTerritoryMessagesSuppressed(UUID playerId) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        return tcm != null && !tcm.areTerritoryMessagesEnabled(playerId);
    }
}
