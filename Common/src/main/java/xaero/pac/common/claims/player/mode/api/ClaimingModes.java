/*
 * Open Parties and Claims - adds chunk claims and player parties to Minecraft
 * Copyright (C) 2026, Xaero <xaero1996@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of version 3 of the GNU Lesser General Public License
 * (LGPL-3.0-only) as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received copies of the GNU Lesser General Public License
 * and the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package xaero.pac.common.claims.player.mode.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.claims.player.mode.ClaimingMode;
import xaero.pac.common.claims.player.mode.ClaimingModeLimits;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.claims.command.ClaimsClaimCommands;
import xaero.pac.common.server.claims.player.IServerPlayerClaimInfo;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.player.config.IPlayerConfig;
import xaero.pac.common.server.player.config.PlayerConfig;
import xaero.pac.common.server.player.config.api.PlayerConfigType;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Access point for all claiming modes in the mod
 */
public class ClaimingModes {

	/**
	 * Map of all claiming modes by ID
	 */
	private static final Map<String, IClaimingModeAPI> ALL = new HashMap<>();
	/**
	 * Immutable view of the map of all claiming modes by ID
	 */
	public static final Map<String, IClaimingModeAPI> ALL_IMMUTABLE = Collections.unmodifiableMap(ALL);

	/**
	 * Mode for claiming as yourself
	 */
	public static final IClaimingModeAPI PLAYER = ClaimingMode.Builder.begin()
			.setId("player")
			.setConfigType(PlayerConfigType.PLAYER)
			.setSubClaimOption(PlayerConfigOptions.USED_SUBCLAIM)
			.setCommandVisibilityRequirement(s -> true)
			.setClaimConfigGetter(playerConfig -> playerConfig)
			.setLimitsBuilder((player, claimsManager) -> {
				UUID playerId = player.getUUID();
				IServerPlayerClaimInfo<?> playerClaims = claimsManager.getPlayerInfo(playerId);
				int claimCount = playerClaims.getClaimCount();
				int forceloadCount = playerClaims.getForceloadCount();
				int claimLimit = claimsManager.getPlayerFullClaimLimit(playerId);
				int forceloadLimit = claimsManager.getPlayerFullForceloadLimit(playerId);
				// [Team Claims] private and team claims are two separate budgets: the player sees the team's
				// numbers while their team sub-config is the used sub-claim, else their private numbers
				xaero.pac.common.server.claims.TeamClaimsIntegration.TeamClaimsHandler tcHandler =
						xaero.pac.common.server.claims.TeamClaimsIntegration.getHandler();
				if(tcHandler != null) {
					xaero.pac.common.server.claims.TeamClaimsIntegration.BudgetNumbers tcTeamBudget = tcHandler.getUsedTeamBudget(playerId);
					if(tcTeamBudget != null) {
						claimCount = tcTeamBudget.claimCount();
						forceloadCount = tcTeamBudget.forceloadCount();
						claimLimit = tcTeamBudget.claimLimit();
						forceloadLimit = tcTeamBudget.forceloadLimit();
					} else {
						claimCount -= tcHandler.getOwnedTeamClaimCount(playerId);
						forceloadCount -= tcHandler.getOwnedTeamForceloadCount(playerId);
					}
				}
				return new ClaimingModeLimits(
						ClaimingModes.PLAYER, claimCount, forceloadCount, claimLimit, forceloadLimit
				);
			})
			.setCanBeImpersonated(true)
			.setActiveLabel(Component.translatable("gui.xaero_pac_claiming_as_player"))
			.setEnableMessage(Component.translatable("gui.xaero_claims_player_mode_enabled"))
			.setDisableMessage(Component.translatable("gui.xaero_claims_player_mode_disabled"))
			.build(ALL);

	/**
	 * Mode for claiming as your party
	 */
	public static final IClaimingModeAPI PARTY = ClaimingMode.Builder.begin()
			.setId("party")
			.setConfigType(PlayerConfigType.PARTY_CLAIMS)
			.setSubClaimOption(PlayerConfigOptions.USED_PARTY_SUBCLAIM)
			.setCommandVisibilityRequirement(ClaimsClaimCommands.getPartyClaimRequirement())
			.setForcedUUIDGetter((original, claimsManager) ->
					claimsManager.getPartySystemManager().getPrimaryPartyOwnerByMember(original)
			)
			.setPermissionChecker((playerId, claimsManager) -> {
				ServerPlayer player = claimsManager.getConfigManager().getServer().getPlayerList().getPlayer(playerId);
				if(claimsManager.getPermissionHandler().playerHasPartyClaimPermission(player, playerId))
					return null;
				if(claimsManager.getPartySystemManager().isInAPrimaryParty(playerId))
					return ClaimResult.Type.NO_PARTY_PERMISSION;
				return ClaimResult.Type.NOT_IN_PARTY;
			})
			.setClaimConfigGetter(playerConfig -> {
				UUID playerId = playerConfig.getPlayerId();
				IPlayerConfig partyOwnerConfig = playerId == null ? playerConfig : playerConfig.getManager().getPartyOwnerConfig(playerId);
				if(partyOwnerConfig == null)
					partyOwnerConfig = playerConfig;
				return partyOwnerConfig;
			})
			.setLimitsBuilder((player, claimsManager) -> {
				int partyClaimCount = 0;
				int partyForceloadCount = 0;
				int partyClaimLimit = 0;
				int partyForceloadLimit = 0;
				if(ServerConfig.CONFIG.partyOwnedClaims.get()){
					UUID partyOwner = claimsManager.getPartySystemManager().getPrimaryPartyOwnerByMember(player.getUUID());
					if(partyOwner != null) {
						IServerPlayerClaimInfo<?> partyOwnerClaims = claimsManager.getPlayerInfo(partyOwner);
						partyClaimCount = partyOwnerClaims.getClaimCount();
						partyForceloadCount = partyOwnerClaims.getForceloadCount();
						partyClaimLimit = claimsManager.getPlayerFullClaimLimit(partyOwner);
						partyForceloadLimit = claimsManager.getPlayerFullForceloadLimit(partyOwner);
						// [Team Claims] native party claims are private claims of the party owner, the owner's
						// team claims have their own budget
						xaero.pac.common.server.claims.TeamClaimsIntegration.TeamClaimsHandler tcHandler =
								xaero.pac.common.server.claims.TeamClaimsIntegration.getHandler();
						if(tcHandler != null) {
							partyClaimCount -= tcHandler.getOwnedTeamClaimCount(partyOwner);
							partyForceloadCount -= tcHandler.getOwnedTeamForceloadCount(partyOwner);
						}
					}
				}
				return new ClaimingModeLimits(
						ClaimingModes.PARTY, partyClaimCount, partyForceloadCount, partyClaimLimit, partyForceloadLimit
				);
			})
			.setCanBeImpersonated(true)
			.setActiveLabel(Component.translatable("gui.xaero_pac_claiming_as_party"))
			.setEnableMessage(Component.translatable("gui.xaero_claims_party_mode_enabled"))
			.setDisableMessage(Component.translatable("gui.xaero_claims_party_mode_disabled"))
			.build(ALL);

	/**
	 * Mode for claiming as the server
	 */
	public static final IClaimingModeAPI SERVER = ClaimingMode.Builder.begin()
			.setId("server")
			.setConfigType(PlayerConfigType.SERVER)
			.setSubClaimOption(PlayerConfigOptions.USED_SERVER_SUBCLAIM)
			.setCommandVisibilityRequirement(ClaimsClaimCommands.getServerClaimCommandRequirement())
			.setForcedUUIDGetter((original, claimsManager) ->
					PlayerConfig.SERVER_CLAIM_UUID
			)
			.setClientCountsSourceId(PlayerConfig.SERVER_CLAIM_UUID)
			.setPermissionChecker((playerId, claimsManager) -> {
				if(PlayerConfig.SERVER_CLAIM_UUID.equals(playerId))
					return null;
				ServerPlayer player = claimsManager.getConfigManager().getServer().getPlayerList().getPlayer(playerId);
				if(player == null)
					return ClaimResult.Type.NO_SERVER_PERMISSION;
				if(claimsManager.getPermissionHandler().playerHasServerClaimPermission(player))
					return null;
				return ClaimResult.Type.NO_SERVER_PERMISSION;
			})
			.setClaimConfigGetter(playerConfig -> playerConfig.getManager().getServerClaimConfig())
			.setLimitsBuilder((player, claimsManager) -> {
				int serverClaimCount = 0;
				int serverForceloadCount = 0;
				if(claimsManager.getPermissionHandler().playerHasServerClaimPermission(player)){
					IServerPlayerClaimInfo<?> serverClaims = claimsManager.getPlayerInfo(PlayerConfig.SERVER_CLAIM_UUID);
					serverClaimCount = serverClaims.getClaimCount();
					serverForceloadCount = serverClaims.getForceloadCount();
				}
				return new ClaimingModeLimits(
						ClaimingModes.SERVER, serverClaimCount, serverForceloadCount, Integer.MAX_VALUE, Integer.MAX_VALUE
				);
			})
			.setActiveLabel(Component.translatable("gui.xaero_pac_claiming_as_server"))
			.setEnableMessage(Component.translatable("gui.xaero_claims_server_mode_enabled"))
			.setDisableMessage(Component.translatable("gui.xaero_claims_server_mode_disabled"))
			.build(ALL);

	/**
	 * Gets the claiming mode with a specified ID.
	 *
	 * @param id  the String ID of the claiming mode, not null
	 * @return the claiming mode, null if it doesn't exist
	 */
	@Nullable
	public static IClaimingModeAPI get(@Nonnull String id){
		return ALL.get(id);
	}

}
