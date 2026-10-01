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
        // Only team claims share a budget; personal claims keep stock OPAC behavior.
        if (!tcm.isTeamClaim(currentClaim)) return null;
        return tcm.checkTeamForceloadBudget(id, currentClaim);
    }

    @Override
    @Nullable
    public ClaimResult<PlayerChunkClaim> interceptClaim(
            ServerClaimsManager claimsManager, ResourceLocation dimension,
            UUID playerId, int subConfigIndex, int x, int z, boolean forceLoaded) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        MinecraftServer server = TeamClaimsCommon.getServer();
        if (tcm == null || server == null) return null;
        // The claim being replaced (if any) decides what this claim really adds to the team's
        // totals and to the previous owner's own count — the budget check needs both.
        PlayerChunkClaim existing = claimsManager.get(dimension, x, z);
        // Only check the team role and budget for claims made with the team sub-config, not personal ones.
        // A personal claim replacing a team claim of the own team still takes it away from the team,
        // so it needs the team's unclaim role.
        if (!tcm.isTeamSubConfigIndex(playerId, subConfigIndex))
            return TeamRoles.checkClaimOverTeamClaim(tcm, server, playerId, existing);
        // The role comes first: a member who may not make team claims gets that reason, not a budget one
        ClaimResult<PlayerChunkClaim> roleResult = TeamRoles.checkClaim(server, playerId);
        if (roleResult != null) return roleResult;
        return tcm.checkTeamClaimBudget(playerId, forceLoaded, existing);
    }

    @Override
    public boolean isComputingOverhead() {
        return TeamClaimManager.COMPUTING_OVERHEAD.get();
    }

    @Override
    public int getTeamClaimOverheadForPlayer(UUID playerUUID) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        return tcm != null ? tcm.getTeamClaimOverheadForPlayer(playerUUID) : 0;
    }

    @Override
    public int getTeamForceloadOverheadForPlayer(UUID playerUUID) {
        TeamClaimManager tcm = TeamClaimsCommon.getClaimManager();
        return tcm != null ? tcm.getTeamForceloadOverheadForPlayer(playerUUID) : 0;
    }

    @Override
    public boolean hasTeamOverhead(UUID playerUUID) {
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        if (cm == null) return false;
        return cm.getTeamClaimOverheadForPlayer(playerUUID) > 0
                || cm.getTeamForceloadOverheadForPlayer(playerUUID) > 0;
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

    private static void queue(PartyEventType type, UUID partyId, @Nullable UUID playerId) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        if (tcm != null) tcm.queuePartyEvent(type, partyId, playerId);
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
