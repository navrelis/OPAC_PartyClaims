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

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Bridge interface for Team Claims integration.
 * <p>
 * This allows the upstream OPAC code to call into the Team Claims implementation
 * ({@code xaero.pac.teamclaims}) without a direct dependency. The handler is installed by
 * {@code TeamClaimsCommon.onAddonRegister} during OPAC's server addon registration.
 * <p>
 * The direct code modifications in ServerClaimsManager, PlayerConfig, PlayerClaimInfo,
 * PlayerSubConfig, ServerboundSubConfigExistencePacket, CreatePartyCommand,
 * ClaimsManagerSynchronizer, ServerParty, PartyManager, PlayerConfigCommonChangeHandlers and
 * ForceLoadTicketManager all call through this bridge. Every one of those hooks is a
 * no-op while {@link #getHandler()} returns null, which is the case on a vanilla client,
 * before the server addon is registered and after the server has stopped.
 * <p>
 * Every handler method is called on the server thread, except
 * {@link TeamClaimsHandler#getTeamClaimOverheadForPlayer},
 * {@link TeamClaimsHandler#getTeamForceloadOverheadForPlayer} and
 * {@link TeamClaimsHandler#isComputingOverhead}, which {@code PlayerClaimInfo} may also call from
 * the client thread of an integrated server.
 * <p>
 * Team Claims only ever applies to sub-configs of a real <i>player</i> config whose sub ID
 * starts with {@link #TEAM_SUB_ID_PREFIX}. Native party claims (server option
 * {@code partyOwnedClaims}, {@code ClaimingModes.PARTY}, the party claims config) are a
 * separate upstream feature and are never routed through this bridge.
 */
public final class TeamClaimsIntegration {

	/**
	 * Prefix of every sub-config ID that Team Claims manages.
	 */
	public static final String TEAM_SUB_ID_PREFIX = "team_";

	/**
	 * Handler interface implemented by the Team Claims module ({@code TeamClaimsBridgeHandler}).
	 * Provides all the hooks needed by the modified Common classes.
	 */
	public interface TeamClaimsHandler {

		// ==================== Claim Operation Hooks ====================

		/**
		 * Called by {@code ServerClaimsManager.tryToUnclaimHelper} when the requesting player
		 * doesn't own the claim and the request isn't forced.
		 *
		 * @return true if the requesting player may unclaim this claim because it is a team
		 *         claim of a party they're also a member of, in which case the normal unclaim
		 *         path continues instead of failing with NOT_CLAIMED_BY_USER
		 */
		boolean allowsTeamUnclaim(
				ServerClaimsManager claimsManager, ResourceLocation dimension,
				UUID id, int x, int z, PlayerChunkClaim currentClaim);

		/**
		 * Called by {@code ServerClaimsManager.tryToForceloadHelper} when the requesting player
		 * doesn't own the claim and the request isn't forced.
		 *
		 * @return true if the requesting player may toggle the forceload state of this claim
		 *         because it is a team claim of a party they're also a member of
		 */
		boolean allowsTeamForceload(
				ServerClaimsManager claimsManager, ResourceLocation dimension,
				UUID id, int x, int z, PlayerChunkClaim currentClaim);

		/**
		 * Called by {@code ServerClaimsManager.tryToForceloadHelper} before a forceload is
		 * enabled on an existing claim (never for unforceloading, server claims or forced
		 * requests). Checks the shared team forceload budget of every party member.
		 *
		 * @return non-null ClaimResult to reject the forceload, null to proceed normally
		 */
		@Nullable
		ClaimResult<PlayerChunkClaim> interceptForceload(
				ServerClaimsManager claimsManager, ResourceLocation dimension,
				UUID id, int x, int z, PlayerChunkClaim currentClaim);

		/**
		 * Called by {@code ServerClaimsManager.tryToClaimHelper} for a new claim only
		 * (never for the forceload/unforceload re-entry, server claims or forced requests).
		 * Checks the shared team claim budget of every party member.
		 *
		 * @return non-null ClaimResult to reject the claim, null to proceed normally
		 */
		@Nullable
		ClaimResult<PlayerChunkClaim> interceptClaim(
				ServerClaimsManager claimsManager, ResourceLocation dimension,
				UUID playerId, int subConfigIndex, int x, int z, boolean forceLoaded);

		// ==================== PlayerClaimInfo Overhead ====================

		/**
		 * Returns true if overhead is currently being computed (re-entrancy guard).
		 * When true, getClaimCount/getForceloadCount must NOT add overhead.
		 */
		boolean isComputingOverhead();

		/**
		 * Returns the team claim overhead for a player (team claims by other party members).
		 */
		int getTeamClaimOverheadForPlayer(UUID playerUUID);

		/**
		 * Returns the team forceload overhead for a player (team forceloads by other party members).
		 */
		int getTeamForceloadOverheadForPlayer(UUID playerUUID);

		/**
		 * Returns true if the player currently has any team claim/forceload overhead.
		 * Used by the claim limits sync to force the client to display the server-computed
		 * counts instead of counting the synced claim data locally.
		 */
		boolean hasTeamOverhead(UUID playerUUID);

		// ==================== PlayerConfig Editing ====================

		/**
		 * Returns true if the current thread is performing an internal edit
		 * (settings propagation, initial setup) that should bypass the admin check
		 * and must not be propagated again.
		 */
		boolean isInternalEditActive();

		/**
		 * Checks if a player is an admin or owner of their party.
		 */
		boolean isPlayerTeamAdmin(UUID playerUUID);

		/**
		 * Called after a setting is successfully changed on the team sub-config {@code subId} of
		 * {@code changedByPlayer}. Persists the change and propagates it to all other team members'
		 * sub-configs.
		 */
		void onTeamSubConfigSettingChanged(UUID changedByPlayer, String subId,
				IPlayerConfigOptionSpecAPI<?> option, @Nullable Object value);

		/**
		 * Returns the current MinecraftServer instance, or null.
		 */
		@Nullable
		MinecraftServer getServer();

		// ==================== Party Commands ====================

		/**
		 * Called by {@code CreatePartyCommand} for {@code /<parties> create <teamname>}. Validates the
		 * team name, creates the party for {@code ownerProfile}, names it and sets up the team config
		 * right away, then reports the outcome to {@code source}. The same code path backs
		 * {@code /teamclaims create}.
		 *
		 * @param player  the player executing the command, null when there is none
		 * @return the command result (1 on success, 0 on failure)
		 */
		int createPartyWithTeamName(CommandSourceStack source, @Nullable ServerPlayer player,
				GameProfile ownerProfile, String rawTeamName);

		// ==================== Party Events ====================
		// Called by the party hooks once the party manager has finished loading. The handler only
		// queues them; they are processed in order at the end of the current server tick.

		/**
		 * A member was added to a managed party, including the owner of a newly created party.
		 */
		void onPartyMemberAdded(UUID partyId, UUID memberId);

		/**
		 * A member left or was kicked from a party that still exists. Not called when a whole
		 * party is removed, see {@link #onPartyRemoved}.
		 */
		void onPartyMemberRemoved(UUID partyId, UUID memberId);

		/**
		 * The ownership of a party was transferred to another member.
		 */
		void onPartyOwnerChanged(UUID partyId);

		/**
		 * The party owner's {@code PARTY_NAME} option changed.
		 */
		void onPartyNameChanged(UUID partyId);

		/**
		 * A party was removed (disbanded, expired or replaced).
		 */
		void onPartyRemoved(UUID partyId);

		// ==================== Sub-Config / Forceload Events ====================

		/**
		 * A team sub-config ({@link #isTeamSubId}) of a player config was created or removed, by any
		 * code path (Team Claims itself, a client packet, a command or a claim transfer).
		 */
		void onTeamSubConfigExistenceChanged(UUID playerId, String subId, boolean exists);

		/**
		 * OPAC's own forceload ticket for a chunk was just removed. On loaders that keep the
		 * "force ticks" state per chunk rather than per ticket (Fabric), that also cleared it for a
		 * team forceload ticket of the same chunk, which the handler then restores.
		 */
		void onOpacForceloadTicketRemoved(ResourceLocation dimension, int x, int z);
	}

	@Nullable
	private static volatile TeamClaimsHandler handler;

	/**
	 * Sets the handler. Called by Team Claims during OPAC addon registration and server stop.
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

	/**
	 * Returns true if the sub-config ID is a Team Claims sub-config ID. This is a pure string
	 * check that doesn't need the handler, so it also works on the client side.
	 */
	public static boolean isTeamSubId(@Nullable String subId) {
		return subId != null && subId.startsWith(TEAM_SUB_ID_PREFIX);
	}

	/**
	 * Gets the current handler, but only if the specified sub-config ID is a team sub-config ID.
	 * Returns null otherwise, which makes every caller a no-op for regular configs.
	 */
	@Nullable
	public static TeamClaimsHandler getHandlerForSubId(@Nullable String subId) {
		return isTeamSubId(subId) ? handler : null;
	}

	private TeamClaimsIntegration() {} // Prevent instantiation
}
