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

/**
 * The claim and forceload budgets of a player and of the player's team, as reported by the server.
 * A limit of {@link Integer#MAX_VALUE} means unlimited.
 *
 * @param available  false if the server has no Team Claims budgets, then all other values are 0 / false
 * @param privateClaims  the claims of the player that are not team claims
 * @param privateClaimLimit  the claim limit of the player
 * @param privateForceloads  the forceloaded claims of the player that are not team claims
 * @param privateForceloadLimit  the forceload limit of the player
 * @param inTeam  whether the player is a member of a team, the values of the team are 0 if not
 * @param memberCount  the number of members of the team
 * @param minMembers  the number of members a team needs to have team claims
 * @param teamClaims  the team claims of the team
 * @param teamClaimLimit  the team claim limit for the current member count
 * @param teamForceloads  the forceloaded team claims of the team
 * @param teamForceloadLimit  the team forceload limit for the current member count
 * @param claimsOverLimit  the number of team claims above the limit
 * @param claimMillisLeft  the time left in milliseconds until the newest team claims above the limit are unclaimed, 0 if no deadline is running
 * @param forceloadsOverLimit  the number of team forceloads above the limit
 * @param forceloadMillisLeft  the time left in milliseconds until the newest team forceloads above the limit are turned off, 0 if no deadline is running
 * @param nextMemberClaims  how many team claims one more member would add to the limit
 * @param nextMemberForceloads  how many team forceloads one more member would add to the limit
 */
public record PartyBudgetData(boolean available, int privateClaims, int privateClaimLimit, int privateForceloads,
		int privateForceloadLimit, boolean inTeam, int memberCount, int minMembers, int teamClaims, int teamClaimLimit,
		int teamForceloads, int teamForceloadLimit, int claimsOverLimit, long claimMillisLeft, int forceloadsOverLimit,
		long forceloadMillisLeft, int nextMemberClaims, int nextMemberForceloads) {

	public static final PartyBudgetData UNAVAILABLE = new PartyBudgetData(false, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

}
