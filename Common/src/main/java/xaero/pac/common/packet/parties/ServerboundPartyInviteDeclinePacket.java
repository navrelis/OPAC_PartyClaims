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

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.IServerParty;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Sent by a client to decline a party invitation that the player has received.
 * The server answers with a {@link ClientboundPartyInvitesPacket} containing the remaining invitations.
 */
public class ServerboundPartyInviteDeclinePacket {

	private static final int PACKET_SIZE = 16;

	private final UUID partyId;

	public ServerboundPartyInviteDeclinePacket(UUID partyId) {
		super();
		this.partyId = partyId;
	}

	public UUID getPartyId() {
		return partyId;
	}

	public static class Codec implements BiConsumer<ServerboundPartyInviteDeclinePacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ServerboundPartyInviteDeclinePacket> {

		@Override
		public ServerboundPartyInviteDeclinePacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() != PACKET_SIZE)
					return null;
				return new ServerboundPartyInviteDeclinePacket(input.readUUID());
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ServerboundPartyInviteDeclinePacket t, FriendlyByteBuf u) {
			u.writeUUID(t.partyId);
		}

	}

	public static class ServerHandler implements BiConsumer<ServerboundPartyInviteDeclinePacket, ServerPlayer> {

		@Override
		public void accept(ServerboundPartyInviteDeclinePacket t, ServerPlayer serverPlayer) {
			if(t == null)
				return;
			if(!ServerConfig.CONFIG.partiesEnabled.get())
				return;
			MinecraftServer server = serverPlayer.getServer();
			IServerData<IServerClaimsManager<IPlayerChunkClaim, IServerPlayerClaimInfo<IPlayerDimensionClaims<IPlayerClaimPosList>>, IServerDimensionClaimsManager<IServerRegionClaims>>, IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> serverData = ServerData.from(server);
			if(serverData == null)
				return;
			UUID playerId = serverPlayer.getUUID();
			IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> party = serverData.getPartyManager().getPartyById(t.partyId);
			if(party == null || !party.isInvited(playerId))
				return;
			party.uninvitePlayer(playerId);
			PartyInvitesSender.send(server, serverPlayer);
		}

	}

}
