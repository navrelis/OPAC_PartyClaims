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

import net.minecraft.server.level.ServerPlayer;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimsCommon;

/**
 * Server side helper for sending the claim and forceload budgets of a player and of the player's team to that player.
 * Must be used on the server thread, as the Team Claims API is.
 */
public final class PartyBudgetSender {

	private PartyBudgetSender() {
	}

	/**
	 * Sends the current budgets to a player, or the information that there are none if Team Claims is not available.
	 *
	 * @param player  the player to send the budgets to, not null
	 */
	public static void send(ServerPlayer player) {
		OpenPartiesAndClaims.INSTANCE.getPacketHandler().sendToPlayer(player, new ClientboundPartyBudgetPacket(collect(player)));
	}

	private static PartyBudgetData collect(ServerPlayer player) {
		TeamClaimManager manager = TeamClaimsCommon.getClaimManager();
		if(manager == null)
			return PartyBudgetData.UNAVAILABLE;
		TeamClaimManager.BudgetInfo info = manager.getBudgetInfo(player.getUUID());
		if(!info.inTeam())
			return new PartyBudgetData(true, info.privateClaims(), info.privateClaimLimit(), info.privateForceloads(), info.privateForceloadLimit(),
					false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		TeamClaimManager.TeamBudget team = manager.getTeamBudget(info.partyId());
		int minMembers = team == null ? 0 : team.minMembers();
		int nextMemberClaims = team == null ? 0 : team.nextMemberClaims();
		int nextMemberForceloads = team == null ? 0 : team.nextMemberForceloads();
		return new PartyBudgetData(true, info.privateClaims(), info.privateClaimLimit(), info.privateForceloads(), info.privateForceloadLimit(),
				true, info.memberCount(), minMembers, info.teamClaims(), info.teamClaimLimit(), info.teamForceloads(), info.teamForceloadLimit(),
				info.claimsOverLimit(), manager.millisUntil(info.overLimitDeadline()), info.forceloadsOverLimit(), manager.millisUntil(info.forceloadOverLimitDeadline()),
				nextMemberClaims, nextMemberForceloads);
	}

}
