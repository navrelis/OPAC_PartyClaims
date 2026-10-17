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

import net.minecraft.network.chat.Component;
import xaero.pac.client.gui.party.PartyScreen;
import xaero.pac.common.packet.parties.PartyBudgetData;
import xaero.pac.common.packet.teamclaims.TeamInfoData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The text lines about the team budget that the Claims tab and the Team tab show, from what the server reported: the
 * team claims and forceloads against their limits (which include the bonus an admin granted), the bonus itself, and the
 * warnings about a running over-limit deadline. The texts are those of the party screen.
 */
public final class HubBudgetLines {

	public static final int TEXT_COLOR = 0xFFE0E0E0;
	public static final int HINT_COLOR = 0xFFFFCC55;
	public static final int WARNING_COLOR = 0xFFFF5555;

	private HubBudgetLines() {
	}

	/**
	 * One line of text and its colour.
	 */
	public record Line(Component text, int color) {
	}

	/**
	 * Gets the lines about the budget of the team of the player. Nothing if the player is in no team.
	 *
	 * @param budget  the budget of the server
	 * @param elapsedMillis  the time since the budget arrived, which is counted off the running deadlines
	 * @param info  the team information of the server, null if it has not arrived; only used for the bonus line
	 */
	public static List<Line> teamLines(PartyBudgetData budget, long elapsedMillis, @Nullable TeamInfoData info) {
		List<Line> lines = new ArrayList<>();
		if(!budget.available() || !budget.inTeam())
			return lines;
		if(budget.memberCount() < budget.minMembers())
			lines.add(new Line(Component.translatable("gui.xaero_pac_party_screen_budget_team_too_small", budget.minMembers(), budget.memberCount()), HINT_COLOR));
		else
			lines.add(new Line(Component.translatable("gui.xaero_pac_party_screen_budget_team",
					PartyScreen.formatBudget(budget.teamClaims(), budget.teamClaimLimit()),
					PartyScreen.formatBudget(budget.teamForceloads(), budget.teamForceloadLimit())), TEXT_COLOR));
		if(info != null && info.available() && (info.bonusClaims() > 0 || info.bonusForceloads() > 0))
			lines.add(new Line(Component.translatable("gui.xaero_pac_hub_team_bonus", info.bonusClaims(), info.bonusForceloads()), HINT_COLOR));
		if(budget.claimsOverLimit() > 0 && budget.claimMillisLeft() > 0)
			lines.add(new Line(Component.translatable("gui.xaero_pac_party_screen_budget_claims_warning",
					budget.claimsOverLimit(), PartyScreen.formatTimeLeft(budget.claimMillisLeft() - elapsedMillis)), WARNING_COLOR));
		if(budget.forceloadsOverLimit() > 0 && budget.forceloadMillisLeft() > 0)
			lines.add(new Line(Component.translatable("gui.xaero_pac_party_screen_budget_forceloads_warning",
					budget.forceloadsOverLimit(), PartyScreen.formatTimeLeft(budget.forceloadMillisLeft() - elapsedMillis)), WARNING_COLOR));
		return lines;
	}

}
