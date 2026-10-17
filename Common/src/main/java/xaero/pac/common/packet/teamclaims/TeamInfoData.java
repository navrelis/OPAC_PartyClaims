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
import xaero.pac.teamclaims.config.TeamRole;

import java.util.UUID;

import static xaero.pac.common.packet.teamclaims.TeamClaimsPacketUtil.*;

/**
 * [Team Claims] What the Team tab shows about the team of the player who asked. The budget numbers (limits with the
 * bonus, over-limit deadlines) come with the budget packet (103) that is sent together with this.
 *
 * @param maxClaimActionSize the server's {@code maxSingleClaimActionSize} (OPAC server config): the most chunks one
 *                           area claim/unclaim/forceload request may cover; always filled, also without a team
 * @param available          false if the player is in no team (or Team Claims is not active); everything else is then
 *                           empty
 * @param subConfigId        the id of the team sub-config of the members (OPAC's player config screen edits it)
 * @param subConfigIndex     the index of the asking player's team sub-config, the sub-config index their team claims
 *                           carry (-1 if the player has no team sub-config right now)
 * @param memberCount        the members, the owner included
 * @param claimRole          the lowest level that may make team claims
 * @param unclaimRole        the lowest level that may unclaim team claims
 * @param forceloadRole      the lowest level that may turn team forceloads on and off
 * @param territoryMessages  whether the asking player sees the claim welcome messages
 * @param canEdit            whether the asking player is the party owner or a party admin (may change roles, convert,
 *                           edit the team sub-config)
 * @param bonusClaims        the per-team bonus team claims an admin granted
 * @param bonusForceloads    the per-team bonus team forceloads an admin granted
 * @param claimsColor        the claim colour of the team sub-config (RGB)
 * @param claimsName         the claims name of the team sub-config
 */
public record TeamInfoData(int maxClaimActionSize, boolean available, UUID partyId, String teamName, String subConfigId,
		int subConfigIndex, int memberCount,
		TeamRole claimRole, TeamRole unclaimRole, TeamRole forceloadRole, boolean territoryMessages, boolean canEdit,
		int bonusClaims, int bonusForceloads, int claimsColor, String claimsName) {

	public static final int MAX_TEAM_NAME_LENGTH = 128;
	public static final int MAX_SUB_CONFIG_ID_LENGTH = 100;
	public static final int MAX_CLAIMS_NAME_LENGTH = 256;

	/** The information for a player in no team (or while Team Claims is not active). */
	public static TeamInfoData unavailable(int maxClaimActionSize) {
		return new TeamInfoData(maxClaimActionSize, false, new UUID(0, 0), "", "", -1, 0, TeamRole.DEFAULT,
				TeamRole.DEFAULT, TeamRole.DEFAULT, false, false, 0, 0, 0, "");
	}

	public void write(FriendlyByteBuf output) {
		output.writeVarInt(maxClaimActionSize);
		output.writeBoolean(available);
		if(!available)
			return;
		output.writeUUID(partyId);
		writeString(output, teamName, MAX_TEAM_NAME_LENGTH);
		writeString(output, subConfigId, MAX_SUB_CONFIG_ID_LENGTH);
		output.writeInt(subConfigIndex);
		output.writeVarInt(memberCount);
		writeEnum(output, claimRole);
		writeEnum(output, unclaimRole);
		writeEnum(output, forceloadRole);
		output.writeBoolean(territoryMessages);
		output.writeBoolean(canEdit);
		output.writeVarInt(bonusClaims);
		output.writeVarInt(bonusForceloads);
		output.writeInt(claimsColor);
		writeString(output, claimsName, MAX_CLAIMS_NAME_LENGTH);
	}

	public static TeamInfoData read(FriendlyByteBuf input) {
		int maxClaimActionSize = readCount(input, Integer.MAX_VALUE);
		if(!input.readBoolean())
			return unavailable(maxClaimActionSize);
		return new TeamInfoData(maxClaimActionSize, true, input.readUUID(), readString(input, MAX_TEAM_NAME_LENGTH),
				readString(input, MAX_SUB_CONFIG_ID_LENGTH), readSubIndex(input), readCount(input, Integer.MAX_VALUE),
				readEnum(input, TeamRole.class), readEnum(input, TeamRole.class), readEnum(input, TeamRole.class),
				input.readBoolean(), input.readBoolean(), readCount(input, Integer.MAX_VALUE),
				readCount(input, Integer.MAX_VALUE), input.readInt(), readString(input, MAX_CLAIMS_NAME_LENGTH));
	}

	private static int readSubIndex(FriendlyByteBuf input) {
		int index = input.readInt();
		if(index < -1)
			throw new IllegalArgumentException("sub-config index " + index);
		return index;
	}

}
