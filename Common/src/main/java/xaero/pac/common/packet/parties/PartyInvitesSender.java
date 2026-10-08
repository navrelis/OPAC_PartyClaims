/*
 * Open Parties and Claims - adds chunk claims and player parties to Minecraft
 * Copyright (C) 2022-2026, Xaero <xaero1996@gmail.com> and contributors
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

package xaero.pac.common.packet.parties;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.parties.party.ClientReceivedPartyInvite;
import xaero.pac.common.claims.player.IPlayerChunkClaim;
import xaero.pac.common.claims.player.IPlayerClaimPosList;
import xaero.pac.common.claims.player.IPlayerDimensionClaims;
import xaero.pac.common.parties.party.IPartyPlayerInfo;
import xaero.pac.common.parties.party.ally.IPartyAlly;
import xaero.pac.common.parties.party.member.IPartyMember;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.claims.IServerClaimsManager;
import xaero.pac.common.server.claims.IServerDimensionClaimsManager;
import xaero.pac.common.server.claims.IServerRegionClaims;
import xaero.pac.common.server.claims.player.IServerPlayerClaimInfo;
import xaero.pac.common.server.parties.party.IPartyManager;
import xaero.pac.common.server.parties.party.IServerParty;
import xaero.pac.common.server.player.config.IPlayerConfig;
import xaero.pac.common.server.player.config.IPlayerConfigManager;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Server side helper for collecting the party invitations that a player has received and sending them to that player.
 */
public final class PartyInvitesSender {

	private PartyInvitesSender() {
	}

	/**
	 * Sends the current list of received party invitations to a player.
	 *
	 * @param server  the server, not null
	 * @param player  the player to send the list to, not null
	 */
	public static void send(MinecraftServer server, ServerPlayer player) {
		IServerData<IServerClaimsManager<IPlayerChunkClaim, IServerPlayerClaimInfo<IPlayerDimensionClaims<IPlayerClaimPosList>>, IServerDimensionClaimsManager<IServerRegionClaims>>, IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> serverData = ServerData.from(server);
		if(serverData == null)
			return;
		List<ClientReceivedPartyInvite> invites = collect(serverData, player.getUUID());
		OpenPartiesAndClaims.INSTANCE.getPacketHandler().sendToPlayer(player, new ClientboundPartyInvitesPacket(invites));
	}

	private static List<ClientReceivedPartyInvite> collect(IServerData<IServerClaimsManager<IPlayerChunkClaim, IServerPlayerClaimInfo<IPlayerDimensionClaims<IPlayerClaimPosList>>, IServerDimensionClaimsManager<IServerRegionClaims>>, IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> serverData, UUID playerId) {
		IPartyManager<IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> partyManager = serverData.getPartyManager();
		IPlayerConfigManager playerConfigs = serverData.getPlayerConfigManager();
		List<ClientReceivedPartyInvite> result = new ArrayList<>();
		Iterator<IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> partyIterator =
				partyManager.getTypedAllStream().filter(party -> party.isInvited(playerId) && party.getMemberInfo(playerId) == null).iterator();
		while(partyIterator.hasNext() && result.size() < ClientboundPartyInvitesPacket.MAX_ENTRIES) {
			IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> party = partyIterator.next();
			IPartyMember owner = party.getOwner();
			result.add(new ClientReceivedPartyInvite(party.getId(), getPartyName(playerConfigs, party), owner.getUsername()));
		}
		return result;
	}

	private static String getPartyName(IPlayerConfigManager playerConfigs, IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> party) {
		IPlayerConfig ownerConfig = playerConfigs.getLoadedConfig(party.getOwner().getUUID());
		String customName = ownerConfig == null ? null : ownerConfig.getEffective(PlayerConfigOptions.PARTY_NAME);
		return customName == null || customName.isEmpty() ? party.getDefaultName() : customName;
	}

}
