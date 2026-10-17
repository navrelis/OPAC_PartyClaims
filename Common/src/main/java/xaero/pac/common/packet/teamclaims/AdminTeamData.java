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

import java.util.UUID;

import static xaero.pac.common.packet.teamclaims.TeamClaimsPacketUtil.*;

/**
 * [Team Claims] One team in the admin menu's team list. The limits are the enforced ones, the bonus included.
 */
public record AdminTeamData(UUID partyId, String name, int memberCount, int claims, int claimLimit, int forceloads,
		int forceloadLimit, int bonusClaims, int bonusForceloads) {

	public static final int MAX_NAME_LENGTH = 128;

	public void write(FriendlyByteBuf output) {
		output.writeUUID(partyId);
		writeString(output, name, MAX_NAME_LENGTH);
		output.writeVarInt(memberCount);
		output.writeVarInt(claims);
		output.writeVarInt(claimLimit);
		output.writeVarInt(forceloads);
		output.writeVarInt(forceloadLimit);
		output.writeVarInt(bonusClaims);
		output.writeVarInt(bonusForceloads);
	}

	public static AdminTeamData read(FriendlyByteBuf input) {
		return new AdminTeamData(input.readUUID(), readString(input, MAX_NAME_LENGTH), readCount(input, Integer.MAX_VALUE),
				readCount(input, Integer.MAX_VALUE), readCount(input, Integer.MAX_VALUE), readCount(input, Integer.MAX_VALUE),
				readCount(input, Integer.MAX_VALUE), readCount(input, Integer.MAX_VALUE), readCount(input, Integer.MAX_VALUE));
	}

}
