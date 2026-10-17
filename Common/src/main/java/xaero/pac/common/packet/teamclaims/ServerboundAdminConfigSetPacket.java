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

import java.util.function.BiConsumer;
import java.util.function.Function;

import static xaero.pac.common.packet.teamclaims.TeamClaimsPacketUtil.*;

/**
 * [Team Claims] Packet 107: an admin sets one option of a server config ({@link AdminConfigEntry#CONFIG_OPAC} or
 * {@link AdminConfigEntry#CONFIG_TEAM_CLAIMS}) to a value given as text. The server validates it against the spec and
 * answers with {@link ClientboundAdminResultPacket} and, on success, an update {@link ClientboundAdminConfigPacket}.
 */
public class ServerboundAdminConfigSetPacket {

	private final String configId;
	private final String path;
	private final String value;

	public ServerboundAdminConfigSetPacket(String configId, String path, String value) {
		this.configId = configId;
		this.path = path;
		this.value = value;
	}

	public String getConfigId() {
		return configId;
	}

	public String getPath() {
		return path;
	}

	public String getValue() {
		return value;
	}

	public static class Codec implements BiConsumer<ServerboundAdminConfigSetPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ServerboundAdminConfigSetPacket> {

		@Override
		public ServerboundAdminConfigSetPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() > 32768)
					return null;
				String configId = readString(input, AdminConfigEntry.MAX_CONFIG_ID_LENGTH);
				String path = readString(input, AdminConfigEntry.MAX_PATH_LENGTH);
				String value = readString(input, AdminConfigEntry.MAX_VALUE_LENGTH);
				requireEnd(input);
				return new ServerboundAdminConfigSetPacket(configId, path, value);
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ServerboundAdminConfigSetPacket t, FriendlyByteBuf u) {
			writeString(u, t.configId, AdminConfigEntry.MAX_CONFIG_ID_LENGTH);
			writeString(u, t.path, AdminConfigEntry.MAX_PATH_LENGTH);
			writeString(u, t.value, AdminConfigEntry.MAX_VALUE_LENGTH);
		}

	}

	public static class ServerHandler implements BiConsumer<ServerboundAdminConfigSetPacket, ServerPlayer> {

		@Override
		public void accept(ServerboundAdminConfigSetPacket t, ServerPlayer serverPlayer) {
			if(t == null)
				return;
			TeamClaimsAdminHandler.handleConfigSet(serverPlayer, t.configId, t.path, t.value);
		}

	}

}
