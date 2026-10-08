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
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.parties.party.ClientReceivedPartyInvite;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Sent to a player to replace the full list of party invitations that the player has received.
 */
public class ClientboundPartyInvitesPacket {

	public static final int MAX_ENTRIES = 64;
	public static final int MAX_PARTY_NAME_LENGTH = 128;
	public static final int MAX_OWNER_NAME_LENGTH = 64;
	private static final int MAX_PACKET_SIZE = 65536;

	private final List<ClientReceivedPartyInvite> invites;

	public ClientboundPartyInvitesPacket(List<ClientReceivedPartyInvite> invites) {
		super();
		this.invites = invites;
	}

	private static String limitLength(String string, int maxLength) {
		if(string == null)
			return "";
		return string.length() > maxLength ? string.substring(0, maxLength) : string;
	}

	public static class Codec implements BiConsumer<ClientboundPartyInvitesPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ClientboundPartyInvitesPacket> {

		@Override
		public ClientboundPartyInvitesPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() > MAX_PACKET_SIZE)
					return null;
				int count = input.readVarInt();
				if(count < 0 || count > MAX_ENTRIES)
					return null;
				List<ClientReceivedPartyInvite> invites = new ArrayList<>(count);
				for(int i = 0; i < count; i++) {
					UUID partyId = input.readUUID();
					String partyName = input.readUtf(MAX_PARTY_NAME_LENGTH);
					String ownerName = input.readUtf(MAX_OWNER_NAME_LENGTH);
					invites.add(new ClientReceivedPartyInvite(partyId, partyName, ownerName));
				}
				if(input.readableBytes() > 0)
					return null;
				return new ClientboundPartyInvitesPacket(invites);
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ClientboundPartyInvitesPacket t, FriendlyByteBuf u) {
			int count = Math.min(t.invites.size(), MAX_ENTRIES);
			u.writeVarInt(count);
			for(int i = 0; i < count; i++) {
				ClientReceivedPartyInvite invite = t.invites.get(i);
				u.writeUUID(invite.partyId());
				u.writeUtf(limitLength(invite.partyName(), MAX_PARTY_NAME_LENGTH), MAX_PARTY_NAME_LENGTH);
				u.writeUtf(limitLength(invite.ownerName(), MAX_OWNER_NAME_LENGTH), MAX_OWNER_NAME_LENGTH);
			}
		}

	}

	public static class ClientHandler implements Consumer<ClientboundPartyInvitesPacket> {

		@Override
		public void accept(ClientboundPartyInvitesPacket t) {
			if(t == null)
				return;
			OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClientPartyStorage().setReceivedInvites(t.invites);
		}

	}

}
