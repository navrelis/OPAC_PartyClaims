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
 * [Team Claims] Packet 105: a client asks for data of the hub screen.
 * <ul>
 *     <li>{@link Kind#TEAM_INFO}: any player; answered with {@link ClientboundTeamInfoPacket} and the budget packet;</li>
 *     <li>{@link Kind#ADMIN_CONFIGS}: admins only; answered with {@link ClientboundAdminConfigPacket} pages;</li>
 *     <li>{@link Kind#ADMIN_TEAMS}: admins only; answered with {@link ClientboundAdminTeamsPacket};</li>
 *     <li>{@link Kind#ADMIN_PLAYER}: admins only, {@code query} is a player name or UUID; answered with
 *     {@link ClientboundAdminPlayerPacket}, or {@link ClientboundAdminResultPacket} if no such player is known.</li>
 * </ul>
 * The query is empty for every other kind. A request an admin-only kind of a player who is no admin gets no answer.
 */
public class ServerboundTeamClaimsRequestPacket {

	public static final int MAX_QUERY_LENGTH = 64;

	public enum Kind { TEAM_INFO, ADMIN_CONFIGS, ADMIN_TEAMS, ADMIN_PLAYER }

	private final Kind kind;
	private final String query;

	public ServerboundTeamClaimsRequestPacket(Kind kind) {
		this(kind, "");
	}

	public ServerboundTeamClaimsRequestPacket(Kind kind, String query) {
		this.kind = kind;
		this.query = query;
	}

	public Kind getKind() {
		return kind;
	}

	public String getQuery() {
		return query;
	}

	public static class Codec implements BiConsumer<ServerboundTeamClaimsRequestPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ServerboundTeamClaimsRequestPacket> {

		@Override
		public ServerboundTeamClaimsRequestPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() > 1 + 3 + MAX_QUERY_LENGTH * 3)
					return null;
				Kind kind = readEnum(input, Kind.class);
				String query = readString(input, MAX_QUERY_LENGTH);
				requireEnd(input);
				if(kind != Kind.ADMIN_PLAYER && !query.isEmpty())
					return null;
				return new ServerboundTeamClaimsRequestPacket(kind, query);
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ServerboundTeamClaimsRequestPacket t, FriendlyByteBuf u) {
			writeEnum(u, t.kind);
			writeString(u, t.query, MAX_QUERY_LENGTH);
		}

	}

	public static class ServerHandler implements BiConsumer<ServerboundTeamClaimsRequestPacket, ServerPlayer> {

		@Override
		public void accept(ServerboundTeamClaimsRequestPacket t, ServerPlayer serverPlayer) {
			if(t == null)
				return;
			TeamClaimsAdminHandler.handleRequest(serverPlayer, t.kind, t.query);
		}

	}

}
