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
 * [Team Claims] The limit summary of one player in the admin menu. The full limit of a kind is its base plus its bonus
 * ({@link Integer#MAX_VALUE} stands for unlimited).
 *
 * @param baseClaimLimit      OPAC's claim limit without the bonus (config, permission node, party bonus)
 * @param baseForceloadLimit  OPAC's forceload limit without the bonus
 * @param baseAnchorLimit     OPAC's claiming anchor limit without the bonus
 * @param baseAnchorRange     OPAC's claiming anchor range without the bonus
 * @param claims              the claims that count against the player's own limit (team claims do not, with Team Claims)
 * @param forceloads          the forceloads that count against the player's own forceload limit
 * @param anchors             the player's claiming anchors in all dimensions
 */
public record AdminPlayerData(UUID playerId, String name, boolean online, int baseClaimLimit, int baseForceloadLimit,
		int baseAnchorLimit, int baseAnchorRange, int bonusClaims, int bonusForceloads, int bonusAnchors,
		int bonusAnchorRange, int claims, int forceloads, int anchors) {

	public static final int MAX_NAME_LENGTH = 64;

	/** The value of one of the four bonus options. */
	public int bonus(AdminPlayerBonus bonus) {
		return switch(bonus) {
			case CLAIMS -> bonusClaims;
			case FORCELOADS -> bonusForceloads;
			case ANCHORS -> bonusAnchors;
			case ANCHOR_RANGE -> bonusAnchorRange;
		};
	}

	public void write(FriendlyByteBuf output) {
		output.writeUUID(playerId);
		writeString(output, name, MAX_NAME_LENGTH);
		output.writeBoolean(online);
		output.writeInt(baseClaimLimit);
		output.writeInt(baseForceloadLimit);
		output.writeInt(baseAnchorLimit);
		output.writeInt(baseAnchorRange);
		output.writeInt(bonusClaims);
		output.writeInt(bonusForceloads);
		output.writeInt(bonusAnchors);
		output.writeInt(bonusAnchorRange);
		output.writeVarInt(claims);
		output.writeVarInt(forceloads);
		output.writeVarInt(anchors);
	}

	public static AdminPlayerData read(FriendlyByteBuf input) {
		return new AdminPlayerData(input.readUUID(), readString(input, MAX_NAME_LENGTH), input.readBoolean(),
				input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(),
				input.readInt(), input.readInt(), readCount(input, Integer.MAX_VALUE), readCount(input, Integer.MAX_VALUE),
				readCount(input, Integer.MAX_VALUE));
	}

}
