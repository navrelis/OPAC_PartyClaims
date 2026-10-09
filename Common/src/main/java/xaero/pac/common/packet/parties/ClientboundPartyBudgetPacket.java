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
import xaero.pac.OpenPartiesAndClaims;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Sent to a player to report the claim and forceload budgets of the player and of the player's team.
 * The time left of the over-limit deadlines is sent as a duration so that differences between the clocks of the
 * server and the client do not matter.
 */
public class ClientboundPartyBudgetPacket {

	private static final int MAX_PACKET_SIZE = 256;

	private final PartyBudgetData budget;

	public ClientboundPartyBudgetPacket(PartyBudgetData budget) {
		super();
		this.budget = budget;
	}

	public PartyBudgetData getBudget() {
		return budget;
	}

	public static class Codec implements BiConsumer<ClientboundPartyBudgetPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ClientboundPartyBudgetPacket> {

		private static int readCount(FriendlyByteBuf input) {
			int value = input.readVarInt();
			if(value < 0)
				throw new IllegalArgumentException();
			return value;
		}

		private static long readMillis(FriendlyByteBuf input) {
			long value = input.readVarLong();
			if(value < 0)
				throw new IllegalArgumentException();
			return value;
		}

		@Override
		public ClientboundPartyBudgetPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() > MAX_PACKET_SIZE)
					return null;
				boolean available = input.readBoolean();
				if(!available) {
					if(input.readableBytes() > 0)
						return null;
					return new ClientboundPartyBudgetPacket(PartyBudgetData.UNAVAILABLE);
				}
				int privateClaims = readCount(input);
				int privateClaimLimit = readCount(input);
				int privateForceloads = readCount(input);
				int privateForceloadLimit = readCount(input);
				boolean inTeam = input.readBoolean();
				int memberCount = readCount(input);
				int minMembers = readCount(input);
				int teamClaims = readCount(input);
				int teamClaimLimit = readCount(input);
				int teamForceloads = readCount(input);
				int teamForceloadLimit = readCount(input);
				int claimsOverLimit = readCount(input);
				long claimMillisLeft = readMillis(input);
				int forceloadsOverLimit = readCount(input);
				long forceloadMillisLeft = readMillis(input);
				int nextMemberClaims = readCount(input);
				int nextMemberForceloads = readCount(input);
				if(input.readableBytes() > 0)
					return null;
				return new ClientboundPartyBudgetPacket(new PartyBudgetData(true, privateClaims, privateClaimLimit, privateForceloads,
						privateForceloadLimit, inTeam, memberCount, minMembers, teamClaims, teamClaimLimit, teamForceloads, teamForceloadLimit,
						claimsOverLimit, claimMillisLeft, forceloadsOverLimit, forceloadMillisLeft, nextMemberClaims, nextMemberForceloads));
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ClientboundPartyBudgetPacket t, FriendlyByteBuf u) {
			PartyBudgetData budget = t.budget;
			u.writeBoolean(budget.available());
			if(!budget.available())
				return;
			u.writeVarInt(budget.privateClaims());
			u.writeVarInt(budget.privateClaimLimit());
			u.writeVarInt(budget.privateForceloads());
			u.writeVarInt(budget.privateForceloadLimit());
			u.writeBoolean(budget.inTeam());
			u.writeVarInt(budget.memberCount());
			u.writeVarInt(budget.minMembers());
			u.writeVarInt(budget.teamClaims());
			u.writeVarInt(budget.teamClaimLimit());
			u.writeVarInt(budget.teamForceloads());
			u.writeVarInt(budget.teamForceloadLimit());
			u.writeVarInt(budget.claimsOverLimit());
			u.writeVarLong(budget.claimMillisLeft());
			u.writeVarInt(budget.forceloadsOverLimit());
			u.writeVarLong(budget.forceloadMillisLeft());
			u.writeVarInt(budget.nextMemberClaims());
			u.writeVarInt(budget.nextMemberForceloads());
		}

	}

	public static class ClientHandler implements Consumer<ClientboundPartyBudgetPacket> {

		@Override
		public void accept(ClientboundPartyBudgetPacket t) {
			if(t == null)
				return;
			OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClientPartyStorage().setBudget(t.budget, System.currentTimeMillis());
		}

	}

}
