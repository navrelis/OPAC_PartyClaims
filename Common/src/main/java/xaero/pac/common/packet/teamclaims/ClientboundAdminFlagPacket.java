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

/**
 * [Team Claims] Packet 104: whether the player may use the admin menu (permission level 2 or the permission node
 * {@code xaero.pac_admin_menu}). Sent after login and whenever that changes. The client only uses it to show or hide the
 * Admin tab; the server checks every admin request itself.
 */
public class ClientboundAdminFlagPacket {

	private final boolean allowed;

	public ClientboundAdminFlagPacket(boolean allowed) {
		this.allowed = allowed;
	}

	public boolean isAllowed() {
		return allowed;
	}

	public static class Codec implements BiConsumer<ClientboundAdminFlagPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ClientboundAdminFlagPacket> {

		@Override
		public ClientboundAdminFlagPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() != 1)
					return null;
				return new ClientboundAdminFlagPacket(input.readBoolean());
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ClientboundAdminFlagPacket t, FriendlyByteBuf u) {
			u.writeBoolean(t.allowed);
		}

	}

	public static class ClientHandler implements Consumer<ClientboundAdminFlagPacket> {

		@Override
		public void accept(ClientboundAdminFlagPacket t) {
			if(t == null)
				return;
			TeamClaimsClientData.INSTANCE.setAdminMenuAllowed(t.allowed);
		}

	}

}
