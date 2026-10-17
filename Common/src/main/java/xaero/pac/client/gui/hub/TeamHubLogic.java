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

import xaero.pac.common.packet.parties.PartyBudgetData;
import xaero.pac.common.packet.teamclaims.TeamInfoData;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamRole;

import javax.annotation.Nullable;

/**
 * The rules of the Team tab that do not need the client: which role a click on a role button picks, the commands that the
 * tab sends and whether the tab is shown.
 * <p>
 * Every command is built from enum values and a clamped number only, never from free text. The server checks every command
 * itself, so the tab only decides what it offers.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class TeamHubLogic {

	/**
	 * The command that all Team Claims commands start with, without the slash.
	 */
	public static final String COMMAND_PREFIX = "teamclaims";
	/**
	 * The default of the server option {@code convertMaxRadius}. The client is not told the real value, so the radius
	 * selector goes up to this and the server answers when a bigger radius would be allowed or not.
	 */
	public static final int DEFAULT_MAX_CONVERT_RADIUS = 4;
	/**
	 * The largest value the server option {@code convertMaxRadius} can have, which no command is built beyond.
	 */
	public static final int ABSOLUTE_MAX_CONVERT_RADIUS = 16;

	private TeamHubLogic() {
	}

	/**
	 * Gets the role that a click on the role button of a team action picks: the next higher one, the lowest after the
	 * highest.
	 */
	public static TeamRole nextRole(TeamRole current) {
		TeamRole[] roles = TeamRole.values();
		return roles[(current.ordinal() + 1) % roles.length];
	}

	/**
	 * Gets the radius that a click on the radius button picks: the next bigger one, 0 after the biggest.
	 *
	 * @param max  the biggest radius to offer
	 */
	public static int nextRadius(int current, int max) {
		int safeMax = Math.max(0, Math.min(max, ABSOLUTE_MAX_CONVERT_RADIUS));
		return current < 0 || current >= safeMax ? 0 : current + 1;
	}

	/**
	 * Builds the command that sets the role a team action needs.
	 */
	public static String rolesCommand(TeamAction action, TeamRole role) {
		return COMMAND_PREFIX + " roles " + action.id() + " " + role.id();
	}

	/**
	 * Builds the command that turns the territory messages of the player on or off.
	 */
	public static String territoryMessagesCommand(boolean on) {
		return COMMAND_PREFIX + " territorymessages " + (on ? "on" : "off");
	}

	/**
	 * Builds the command that converts the claims around the player.
	 *
	 * @param toTeam  true to turn personal claims into team claims, false for the other way around
	 * @return the command, null if the radius is outside of 0 to {@value #ABSOLUTE_MAX_CONVERT_RADIUS}
	 */
	@Nullable
	public static String convertCommand(boolean toTeam, int radius) {
		if(radius < 0 || radius > ABSOLUTE_MAX_CONVERT_RADIUS)
			return null;
		return COMMAND_PREFIX + " convert " + (toTeam ? "toteam" : "topersonal") + " " + radius;
	}

	/**
	 * Checks whether the Team tab is shown. The team information is what decides once the server has sent it, as long as
	 * the player is in a party; before that, the budget that the server sends with the party information does.
	 *
	 * @param inParty  whether the player is in a party
	 * @param info  the team information of the server, null if it has not arrived
	 * @param budget  the budget of the server, null if it has not arrived
	 */
	public static boolean isTeamTabVisible(boolean inParty, @Nullable TeamInfoData info, @Nullable PartyBudgetData budget) {
		if(!inParty)
			return false;
		if(info != null)
			return info.available();
		return budget != null && budget.available() && budget.inTeam();
	}

}
