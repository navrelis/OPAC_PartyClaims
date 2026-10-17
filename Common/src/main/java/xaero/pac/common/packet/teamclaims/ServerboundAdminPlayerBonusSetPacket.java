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

import static xaero.pac.common.packet.teamclaims.TeamClaimsPacketUtil.*;

/**
 * [Team Claims] Packet 112: an admin sets one of the four bonus options of a player through OPAC's player config (the
 * same path as {@code /opac player-config for <player> set}). Answered with {@link ClientboundAdminResultPacket} and, on
 * success, the fresh {@link ClientboundAdminPlayerPacket}.
 */
public class ServerboundAdminPlayerBonusSetPacket {

	private final UUID playerId;
	private final AdminPlayerBonus bonus;
	private final int value;

	public ServerboundAdminPlayerBonusSetPacket(UUID playerId, AdminPlayerBonus bonus, int value) {
		this.playerId = playerId;
		this.bonus = bonus;
		this.value = value;
	}

	public UUID getPlayerId() {
		return playerId;
	}

	public AdminPlayerBonus getBonus() {
		return bonus;
	}

	public int getValue() {
		return value;
	}

	public static class Codec implements BiConsumer<ServerboundAdminPlayerBonusSetPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ServerboundAdminPlayerBonusSetPacket> {

		@Override
		public ServerboundAdminPlayerBonusSetPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() != 21)
					return null;
				return new ServerboundAdminPlayerBonusSetPacket(input.readUUID(), readEnum(input, AdminPlayerBonus.class), input.readInt());
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ServerboundAdminPlayerBonusSetPacket t, FriendlyByteBuf u) {
			u.writeUUID(t.playerId);
			writeEnum(u, t.bonus);
			u.writeInt(t.value);
		}

	}

	public static class ServerHandler implements BiConsumer<ServerboundAdminPlayerBonusSetPacket, ServerPlayer> {

		@Override
		public void accept(ServerboundAdminPlayerBonusSetPacket t, ServerPlayer serverPlayer) {
			if(t == null)
				return;
			TeamClaimsAdminHandler.handlePlayerBonusSet(serverPlayer, t.playerId, t.bonus, t.value);
		}

	}

}
