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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static xaero.pac.common.packet.teamclaims.TeamClaimsPacketUtil.*;

/**
 * [Team Claims] Packet 109: every team of the server for the admin menu, sorted by name; at most {@link #MAX_TEAMS}
 * (more are cut off).
 */
public class ClientboundAdminTeamsPacket {

	public static final int MAX_TEAMS = 2048;
	public static final int MAX_PACKET_SIZE = 1048576;

	private final List<AdminTeamData> teams;

	public ClientboundAdminTeamsPacket(List<AdminTeamData> teams) {
		this.teams = List.copyOf(teams);
	}

	public List<AdminTeamData> getTeams() {
		return teams;
	}

	public static class Codec implements BiConsumer<ClientboundAdminTeamsPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ClientboundAdminTeamsPacket> {

		@Override
		public ClientboundAdminTeamsPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() > MAX_PACKET_SIZE)
					return null;
				int count = readCount(input, MAX_TEAMS);
				List<AdminTeamData> teams = new ArrayList<>(count);
				for(int i = 0; i < count; i++)
					teams.add(AdminTeamData.read(input));
				requireEnd(input);
				return new ClientboundAdminTeamsPacket(teams);
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ClientboundAdminTeamsPacket t, FriendlyByteBuf u) {
			int count = Math.min(t.teams.size(), MAX_TEAMS);
			u.writeVarInt(count);
			for(int i = 0; i < count; i++)
				t.teams.get(i).write(u);
		}

	}

	public static class ClientHandler implements Consumer<ClientboundAdminTeamsPacket> {

		@Override
		public void accept(ClientboundAdminTeamsPacket t) {
			if(t == null)
				return;
			TeamClaimsClientData.INSTANCE.setTeams(t.teams);
		}

	}

}
