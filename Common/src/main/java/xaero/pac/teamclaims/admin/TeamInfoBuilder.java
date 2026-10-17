package xaero.pac.teamclaims.admin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.packet.teamclaims.TeamInfoData;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;

import java.util.UUID;

/**
 * The Team tab information of a player's team ({@link TeamInfoData}). Changes go through the existing commands
 * ({@code /teamclaims roles}, {@code territorymessages}, {@code convert}) and OPAC's config option packets for the team
 * sub-config, which check who may do what. Server thread only.
 */
public final class TeamInfoBuilder {

    private TeamInfoBuilder() {}

    public static TeamInfoData build(ServerPlayer player) {
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        int maxClaimActionSize = ServerConfig.CONFIG.maxSingleClaimActionSize.get();
        if (cm == null || tcm == null) return TeamInfoData.unavailable(maxClaimActionSize);
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUUID();
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(playerId);
        TeamConfig config = party == null ? null : tcm.getTeamConfig(party.getId());
        if (config == null) return TeamInfoData.unavailable(maxClaimActionSize);
        int color = tcm.getCanonicalColor(config);
        String claimsName = tcm.getCanonicalName(config);
        IPlayerConfigAPI subConfig = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(playerId)
                .getSubConfig(config.getSubConfigId());
        int subConfigIndex = subConfig == null ? -1 : subConfig.getSubIndex();
        if (subConfig != null) {
            Integer subColor = subConfig.getEffective(PlayerConfigOptions.CLAIMS_COLOR);
            String subName = subConfig.getEffective(PlayerConfigOptions.CLAIMS_NAME);
            if (subColor != null) color = subColor;
            if (subName != null) claimsName = subName;
        }
        return new TeamInfoData(maxClaimActionSize, true, party.getId(), AdminTeams.nameOf(tcm, config, party),
                config.getSubConfigId(), subConfigIndex, party.getMemberCount(), config.getRequiredRole(TeamAction.CLAIM),
                config.getRequiredRole(TeamAction.UNCLAIM), config.getRequiredRole(TeamAction.FORCELOAD), cm.areTerritoryMessagesEnabled(playerId),
                tcm.isPlayerTeamAdmin(playerId), config.getBonusClaims(), config.getBonusForceloads(), color & 0xFFFFFF,
                claimsName);
    }
}
