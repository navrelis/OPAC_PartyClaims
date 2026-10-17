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

package xaero.pac.common.packet.teamclaims;

import net.minecraft.network.FriendlyByteBuf;
import xaero.pac.client.teamclaims.TeamClaimsClientData;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static xaero.pac.common.packet.teamclaims.TeamClaimsPacketUtil.requireEnd;

/**
 * [Team Claims] Packet 113: the Team tab's information about the team of the player, the answer to a
 * {@link ServerboundTeamClaimsRequestPacket.Kind#TEAM_INFO} request (followed by the budget packet 103).
 */
public class ClientboundTeamInfoPacket {

	public static final int MAX_PACKET_SIZE = 2048;

	private final TeamInfoData info;

	public ClientboundTeamInfoPacket(TeamInfoData info) {
		this.info = info;
	}

	public TeamInfoData getInfo() {
		return info;
	}

	public static class Codec implements BiConsumer<ClientboundTeamInfoPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ClientboundTeamInfoPacket> {

		@Override
		public ClientboundTeamInfoPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() > MAX_PACKET_SIZE)
					return null;
				TeamInfoData info = TeamInfoData.read(input);
				requireEnd(input);
				return new ClientboundTeamInfoPacket(info);
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ClientboundTeamInfoPacket t, FriendlyByteBuf u) {
			t.info.write(u);
		}

	}

	public static class ClientHandler implements Consumer<ClientboundTeamInfoPacket> {

		@Override
		public void accept(ClientboundTeamInfoPacket t) {
			if(t == null)
				return;
			TeamClaimsClientData.INSTANCE.setTeamInfo(t.info);
		}

	}

}
