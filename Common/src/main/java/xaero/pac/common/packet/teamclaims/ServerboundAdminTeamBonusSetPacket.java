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
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.teamclaims.admin.TeamClaimsAdminHandler;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * [Team Claims] Packet 110: an admin sets the per-team bonus team claims ({@code forceloads} false) or bonus team
 * forceloads of a team. Answered with {@link ClientboundAdminResultPacket} and, on success, the fresh
 * {@link ClientboundAdminTeamsPacket}.
 */
public class ServerboundAdminTeamBonusSetPacket {

	private final UUID partyId;
	private final boolean forceloads;
	private final int value;

	public ServerboundAdminTeamBonusSetPacket(UUID partyId, boolean forceloads, int value) {
		this.partyId = partyId;
		this.forceloads = forceloads;
		this.value = value;
	}

	public UUID getPartyId() {
		return partyId;
	}

	public boolean isForceloads() {
		return forceloads;
	}

	public int getValue() {
		return value;
	}

	public static class Codec implements BiConsumer<ServerboundAdminTeamBonusSetPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ServerboundAdminTeamBonusSetPacket> {

		@Override
		public ServerboundAdminTeamBonusSetPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() != 21)
					return null;
				return new ServerboundAdminTeamBonusSetPacket(input.readUUID(), input.readBoolean(), input.readInt());
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ServerboundAdminTeamBonusSetPacket t, FriendlyByteBuf u) {
			u.writeUUID(t.partyId);
			u.writeBoolean(t.forceloads);
			u.writeInt(t.value);
		}

	}

	public static class ServerHandler implements BiConsumer<ServerboundAdminTeamBonusSetPacket, ServerPlayer> {

		@Override
		public void accept(ServerboundAdminTeamBonusSetPacket t, ServerPlayer serverPlayer) {
			if(t == null)
				return;
			TeamClaimsAdminHandler.handleTeamBonusSet(serverPlayer, t.partyId, t.forceloads, t.value);
		}

	}

}
