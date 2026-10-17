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

import xaero.pac.client.teamclaims.TeamClaimsClientRequests;

/**
 * Asks the server for the team information, which the server answers together with the budgets. Shared by the Claims tab,
 * the Team tab and the party screen, with one throttle so that screens opening one after the other ask once.
 */
public final class TeamInfoRefresh {

	private static final long MIN_INTERVAL_MILLIS = 400;
	private static final RequestThrottle THROTTLE = new RequestThrottle(MIN_INTERVAL_MILLIS);

	private TeamInfoRefresh() {
	}

	/**
	 * Sends the request, unless one was sent a moment ago.
	 *
	 * @return whether a request was sent
	 */
	public static boolean request() {
		if(!THROTTLE.tryAcquire(System.currentTimeMillis()))
			return false;
		TeamClaimsClientRequests.requestTeamInfo();
		return true;
	}

}
