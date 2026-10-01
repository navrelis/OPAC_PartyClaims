package xaero.pac.teamclaims;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.ServerClaimsManager;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.teamclaims.config.TeamConfigManager;

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
        if (tcm == null) return null;
        // Only check the team budget for claims made with the team sub-config, not personal ones
        if (!tcm.isTeamSubConfigIndex(playerId, subConfigIndex)) return null;
        // The claim being replaced (if any) decides what this claim really adds to the team's
        // totals and to the previous owner's own count — the budget check needs both.
        PlayerChunkClaim existing = claimsManager.get(dimension, x, z);
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
    public boolean isInternalEditActive() {
        return TeamConfigManager.INTERNAL_EDIT.get();
    }

    @Override
    public boolean isPlayerTeamAdmin(UUID playerUUID) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        return tcm != null && tcm.isPlayerTeamAdmin(playerUUID);
    }

    @Override
    public void onTeamSubConfigSettingChanged(UUID changedByPlayer,
            IPlayerConfigOptionSpecAPI<?> option, Object value) {
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        if (tcm != null) {
            tcm.onTeamSubConfigSettingChanged(changedByPlayer, option, value);
        }
    }

    @Override
    @Nullable
    public MinecraftServer getServer() {
        return TeamClaimsCommon.getServer();
    }

    @Override
    public boolean hasTeamOverhead(UUID playerUUID) {
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        if (cm == null) return false;
        return cm.getTeamClaimOverheadForPlayer(playerUUID) > 0
                || cm.getTeamForceloadOverheadForPlayer(playerUUID) > 0;
    }

    @Override
    public void onPartyCreated(ServerPlayer owner) {
        MinecraftServer currentServer = TeamClaimsCommon.getServer();
        if (currentServer == null) return;
        try {
            var partyManager = OpenPACServerAPI.get(currentServer).getPartyManager();
            var party = partyManager.getPartyByMember(owner.getUUID());
            if (party == null) return;
            TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
            if (tcm != null) {
                tcm.createTeamConfig(party);
            }
            TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
            if (cm != null) {
                cm.ensureAllMembersHaveSubConfig(party);
            }
        } catch (Exception e) {
            TeamClaimsCommon.LOGGER.warn("Error in onPartyCreated for {}: {}", owner.getUUID(), e.getMessage());
        }
    }
}
