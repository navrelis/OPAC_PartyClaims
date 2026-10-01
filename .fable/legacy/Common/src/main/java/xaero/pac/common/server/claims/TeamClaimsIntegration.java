/*
 * Open Parties and Claims - adds chunk claims and player parties to Minecraft
 * Copyright (C) 2022-2025, Xaero <xaero1996@gmail.com> and contributors
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

package xaero.pac.common.server.claims;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.player.config.api.IPlayerConfigOptionSpecAPI;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Bridge interface for Team Claims integration.
 * <p>
 * This allows the Common module to call into the NeoForge-specific Team Claims
 * implementation without a direct dependency. The handler is set by the NeoForge
 * module during initialization.
 * <p>
 * The direct code modifications in ServerClaimsManager, PlayerConfig, PlayerClaimInfo,
 * PlayerConfigSynchronizer, and PlayerSubConfig all call through this bridge.
 */
public final class TeamClaimsIntegration {

	/**
	 * Handler interface implemented by the NeoForge Team Claims module.
	 * Provides all the hooks needed by the modified Common classes.
	 */
	public interface TeamClaimsHandler {

		// ==================== Claim Operation Intercepts ====================

		/**
		 * Called at the beginning of tryToUnclaimHelper.
		 * Allows team members to unclaim each other's team claims.
		 * @return non-null ClaimResult to override the method, null to proceed normally
		 */
		@Nullable
		ClaimResult<PlayerChunkClaim> interceptUnclaim(
				ServerClaimsManager claimsManager, ResourceLocation dimension,
				UUID id, int x, int z, boolean replace, @Nullable PlayerChunkClaim currentClaim);

		/**
		 * Called at the beginning of tryToForceloadHelper.
		 * Allows team members to toggle forceload on each other's team claims,
		 * and checks team forceload budgets.
		 * @return non-null ClaimResult to override the method, null to proceed normally
		 */
		@Nullable
		ClaimResult<PlayerChunkClaim> interceptForceload(
				ServerClaimsManager claimsManager, ResourceLocation dimension,
				UUID id, int x, int z, boolean enable, boolean replace,
				boolean isServer, @Nullable PlayerChunkClaim currentClaim);

		/**
		 * Called at the beginning of tryToClaimHelper.
		 * Checks team claim budgets for all party members.
		 * @return non-null ClaimResult to override the method, null to proceed normally
		 */
		@Nullable
		ClaimResult<PlayerChunkClaim> interceptClaim(
				ServerClaimsManager claimsManager, ResourceLocation dimension,
				UUID playerId, int subConfigIndex, int x, int z,
				boolean forceLoaded, boolean replace, boolean isServer);

		// ==================== PlayerClaimInfo Overhead ====================

		/**
		 * Returns true if overhead is currently being computed (re-entrancy guard).
		 * When true, getClaimCount/getForceloadCount should NOT add overhead.
		 */
		boolean isComputingOverhead();

		/**
		 * Returns the team claim overhead for a player (claims by other party members).
		 */
		int getTeamClaimOverheadForPlayer(UUID playerUUID);

		/**
		 * Returns the team forceload overhead for a player.
		 */
		int getTeamForceloadOverheadForPlayer(UUID playerUUID);

		// ==================== PlayerConfig Editing ====================

		/**
		 * Returns true if the current thread is performing an internal edit
		 * (settings propagation, initial setup) that should bypass admin checks.
		 */
		boolean isInternalEditActive();

		/**
		 * Checks if a player is an admin or owner of their party.
		 */
		boolean isPlayerTeamAdmin(UUID playerUUID);

		/**
		 * Called after a setting is successfully changed on a team sub-config.
		 * Propagates the change to all other team members' sub-configs.
		 */
		@SuppressWarnings("rawtypes")
		void onTeamSubConfigSettingChanged(UUID changedByPlayer,
				IPlayerConfigOptionSpecAPI option, Comparable value);

		/**
		 * Returns the current MinecraftServer instance, or null.
		 */
		@Nullable
		MinecraftServer getServer();

		/**
		 * Returns true if the player is in a party that has tracked team claims.
		 * Used by the base sync to force loading values for correct count display.
		 */
		boolean hasTeamOverhead(UUID playerUUID);

		/**
		 * Called immediately after a party is created.
		 * Creates the team config and sub-config for the owner right away
		 * instead of waiting for the polling cycle.
		 */
		void onPartyCreated(net.minecraft.server.level.ServerPlayer owner);
	}

	@Nullable
	private static volatile TeamClaimsHandler handler;

	/**
	 * Sets the handler. Called by the NeoForge module during OPAC addon registration.
	 */
	public static void setHandler(@Nullable TeamClaimsHandler h) {
		handler = h;
	}

	/**
	 * Gets the current handler, or null if not initialized.
	 */
	@Nullable
	public static TeamClaimsHandler getHandler() {
		return handler;
	}

	/**
	 * Returns true if Team Claims integration is active.
	 */
	public static boolean isActive() {
		return handler != null;
	}

	private TeamClaimsIntegration() {} // Prevent instantiation
}
