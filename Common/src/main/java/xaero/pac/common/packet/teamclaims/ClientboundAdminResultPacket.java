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
import net.minecraft.network.chat.Component;
import xaero.pac.client.teamclaims.TeamClaimsClientData;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static xaero.pac.common.packet.teamclaims.TeamClaimsPacketUtil.*;

/**
 * [Team Claims] Packet 108: the outcome of an admin action, as a translation key with literal arguments, so that the
 * client shows it in its own language.
 *
 * @see Result
 */
public class ClientboundAdminResultPacket {

	public static final int MAX_PACKET_SIZE = 65536;
	public static final int MAX_KEY_LENGTH = 128;
	public static final int MAX_TARGET_LENGTH = 256;
	public static final int MAX_ARGS = 8;
	public static final int MAX_ARG_LENGTH = 4096;

	/** What the result is about. */
	public enum Action { CONFIG_SET, TEAM_BONUS_SET, PLAYER_LOOKUP, PLAYER_BONUS_SET }

	/**
	 * One result.
	 *
	 * @param target      what it is about: {@code configId:path}, the party id, the player id or the looked up name
	 * @param messageKey  the translation key of the message
	 * @param args        the literal arguments of the message
	 */
	public record Result(Action action, String target, boolean success, String messageKey, List<String> args) {

		public Result {
			args = List.copyOf(args);
		}

		/** The message in the language of the client. */
		public Component message() {
			return Component.translatable(messageKey, args.toArray());
		}
	}

	private final Result result;

	public ClientboundAdminResultPacket(Result result) {
		this.result = result;
	}

	public Result getResult() {
		return result;
	}

	public static class Codec implements BiConsumer<ClientboundAdminResultPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ClientboundAdminResultPacket> {

		@Override
		public ClientboundAdminResultPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() > MAX_PACKET_SIZE)
					return null;
				Result result = new Result(readEnum(input, Action.class), readString(input, MAX_TARGET_LENGTH),
						input.readBoolean(), readString(input, MAX_KEY_LENGTH), readStrings(input, MAX_ARGS, MAX_ARG_LENGTH));
				requireEnd(input);
				return new ClientboundAdminResultPacket(result);
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ClientboundAdminResultPacket t, FriendlyByteBuf u) {
			writeEnum(u, t.result.action());
			writeString(u, t.result.target(), MAX_TARGET_LENGTH);
			u.writeBoolean(t.result.success());
			writeString(u, t.result.messageKey(), MAX_KEY_LENGTH);
			writeStrings(u, t.result.args(), MAX_ARGS, MAX_ARG_LENGTH);
		}

	}

	public static class ClientHandler implements Consumer<ClientboundAdminResultPacket> {

		@Override
		public void accept(ClientboundAdminResultPacket t) {
			if(t == null)
				return;
			TeamClaimsClientData.INSTANCE.setLastResult(t.result);
		}

	}

}
