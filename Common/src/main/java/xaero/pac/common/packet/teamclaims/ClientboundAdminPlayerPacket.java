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
 * [Team Claims] Packet 111: the limit summary of the player an admin looked up or changed.
 */
public class ClientboundAdminPlayerPacket {

	public static final int MAX_PACKET_SIZE = 1024;

	private final AdminPlayerData player;

	public ClientboundAdminPlayerPacket(AdminPlayerData player) {
		this.player = player;
	}

	public AdminPlayerData getPlayer() {
		return player;
	}

	public static class Codec implements BiConsumer<ClientboundAdminPlayerPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ClientboundAdminPlayerPacket> {

		@Override
		public ClientboundAdminPlayerPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() > MAX_PACKET_SIZE)
					return null;
				AdminPlayerData player = AdminPlayerData.read(input);
				requireEnd(input);
				return new ClientboundAdminPlayerPacket(player);
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ClientboundAdminPlayerPacket t, FriendlyByteBuf u) {
			t.player.write(u);
		}

	}

	public static class ClientHandler implements Consumer<ClientboundAdminPlayerPacket> {

		@Override
		public void accept(ClientboundAdminPlayerPacket t) {
			if(t == null)
				return;
			TeamClaimsClientData.INSTANCE.setPlayer(t.player);
		}

	}

}
