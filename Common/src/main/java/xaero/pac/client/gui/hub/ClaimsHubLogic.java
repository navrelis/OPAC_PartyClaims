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

package xaero.pac.client.gui.hub;

import xaero.pac.common.packet.teamclaims.TeamInfoData;
import xaero.pac.common.server.claims.TeamClaimsIntegration;
import xaero.pac.common.server.player.config.PlayerConfig;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The rules of the Claims tab that do not need the client: which sub-config a claim is made with, and whether a claim
 * of the local player is a team claim.
 * <p>
 * A claim becomes a team claim when the player config option {@code claims.usedSub} names the team sub-config
 * ({@code team_<name>}) at the time of the claim, and a private one when it names {@value PlayerConfig#MAIN_SUB_ID}.
 * Xaero's World Map "Claim Selected" claims with that same option, so the toggle decides it as well.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class ClaimsHubLogic {

	/**
	 * The sub-config index that the claims of the main config of a player have.
	 */
	public static final int MAIN_SUB_INDEX = -1;

	private ClaimsHubLogic() {
	}

	/**
	 * Finds the sub-config of the team of the player among the sub-config IDs of their player config.
	 *
	 * @param subConfigIds  the sub-config IDs of the player config, can contain {@value PlayerConfig#MAIN_SUB_ID}
	 * @return the ID of the team sub-config, null if there is none
	 */
	@Nullable
	public static String findTeamSubId(List<String> subConfigIds) {
		for(String id : subConfigIds)
			if(TeamClaimsIntegration.isTeamSubId(id))
				return id;
		return null;
	}

	/**
	 * Checks whether the player has sub-configs that are neither the main config nor a team sub-config.
	 */
	public static boolean hasOtherSubConfigs(List<String> subConfigIds) {
		for(String id : subConfigIds)
			if(!PlayerConfig.MAIN_SUB_ID.equals(id) && !TeamClaimsIntegration.isTeamSubId(id))
				return true;
		return false;
	}

	/**
	 * Gets the value that the option {@code claims.usedSub} gets for a choice of the toggle.
	 *
	 * @param team  whether the player picked team claims
	 * @param teamSubId  the ID of the team sub-config, null if the player has none
	 * @return the ID of the team sub-config for a team choice, {@value PlayerConfig#MAIN_SUB_ID} for a private one or when
	 *         there is no team sub-config to pick
	 */
	public static String usedSubFor(boolean team, @Nullable String teamSubId) {
		return team && teamSubId != null ? teamSubId : PlayerConfig.MAIN_SUB_ID;
	}

	/**
	 * Checks whether the value of {@code claims.usedSub} makes new claims team claims.
	 */
	public static boolean isTeamSelected(@Nullable String usedSub) {
		return TeamClaimsIntegration.isTeamSubId(usedSub);
	}

	/**
	 * Gets the sub-config index that the team claims of the local player carry. The server sends it with the team
	 * information; until that has arrived, it is known when the team sub-config is the one in use, because the client is
	 * told the index of the sub-config in use.
	 *
	 * @param info  the team information of the server, null if it has not arrived
	 * @param teamSubId  the ID of the team sub-config of the player, null if there is none
	 * @param usedSubId  the ID of the sub-config in use for claiming as a player, null if unknown
	 * @param usedSubIndex  the index of the sub-config in use, {@value #MAIN_SUB_INDEX} if unknown
	 * @return the index, null if it cannot be told
	 */
	@Nullable
	public static Integer resolveTeamSubIndex(@Nullable TeamInfoData info, @Nullable String teamSubId, @Nullable String usedSubId, int usedSubIndex) {
		if(info != null && info.available() && info.subConfigIndex() >= 0)
			return info.subConfigIndex();
		if(teamSubId != null && teamSubId.equals(usedSubId) && usedSubIndex >= 0)
			return usedSubIndex;
		return null;
	}

	/**
	 * Decides whether a claim of the local player is a team claim:
	 * <ul>
	 * <li>if the index of the team sub-config is known (see {@link #resolveTeamSubIndex}), a claim is a team claim if it
	 * has that index;</li>
	 * <li>otherwise, if the team sub-config is the only sub-config besides the main one, every claim that does not
	 * use the main config can only be a team claim;</li>
	 * <li>otherwise it cannot be told, and the claim counts as a private one.</li>
	 * </ul>
	 *
	 * @param subIndex  the sub-config index of the claim
	 * @param knownTeamSubIndex  the index of the team sub-config if it is known, otherwise null
	 * @param teamSubConfigExists  whether the player has a team sub-config
	 * @param hasOtherSubConfigs  whether the player has other sub-configs besides the main and the team one
	 */
	public static boolean isOwnTeamClaim(int subIndex, @Nullable Integer knownTeamSubIndex, boolean teamSubConfigExists, boolean hasOtherSubConfigs) {
		if(!teamSubConfigExists || subIndex == MAIN_SUB_INDEX)
			return false;
		if(knownTeamSubIndex != null)
			return subIndex == knownTeamSubIndex;
		return !hasOtherSubConfigs;
	}

}
