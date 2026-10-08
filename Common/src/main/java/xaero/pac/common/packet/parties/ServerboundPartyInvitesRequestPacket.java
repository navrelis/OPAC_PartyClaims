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
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.player.data.ServerPlayerData;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Sent by a client to request the list of party invitations that the player has received.
 * The server answers with a {@link ClientboundPartyInvitesPacket}.
 */
public class ServerboundPartyInvitesRequestPacket {

	/**
	 * The minimum number of server ticks between two handled requests from the same player.
	 */
	public static final int MIN_TICKS_BETWEEN_REQUESTS = 20;

	public static class Codec implements BiConsumer<ServerboundPartyInvitesRequestPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ServerboundPartyInvitesRequestPacket> {

		@Override
		public ServerboundPartyInvitesRequestPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() > 0)
					return null;
				return new ServerboundPartyInvitesRequestPacket();
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ServerboundPartyInvitesRequestPacket t, FriendlyByteBuf u) {
		}

	}

	public static class ServerHandler implements BiConsumer<ServerboundPartyInvitesRequestPacket, ServerPlayer> {

		@Override
		public void accept(ServerboundPartyInvitesRequestPacket t, ServerPlayer serverPlayer) {
			if(t == null)
				return;
			if(!ServerConfig.CONFIG.partiesEnabled.get())
				return;
			MinecraftServer server = serverPlayer.getServer();
			if(server == null)
				return;
			ServerPlayerData playerData = (ServerPlayerData) ServerPlayerData.from(serverPlayer);
			long currentTick = server.getTickCount();
			if(currentTick - playerData.getLastPartyInvitesRequestTick() < MIN_TICKS_BETWEEN_REQUESTS)
				return;
			playerData.setLastPartyInvitesRequestTick(currentTick);
			PartyInvitesSender.send(server, serverPlayer);
		}

	}

}
