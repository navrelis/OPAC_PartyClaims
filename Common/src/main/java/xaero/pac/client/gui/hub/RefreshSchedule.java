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

/**
 * Decides when a hub screen asks the server for fresh team information and budgets: regularly while it is open, and a
 * short while after the player did something, because the answer to a command arrives as a chat message and the changed
 * numbers have to be asked for.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class RefreshSchedule {

	private final int periodTicks;
	private final int afterActionTicks;
	private int sinceLastRequest;
	private int actionCountdown;

	/**
	 * @param periodTicks  the number of ticks after which a request is due when nothing else asked for one
	 * @param afterActionTicks  the number of ticks between an action and the request that follows it
	 */
	public RefreshSchedule(int periodTicks, int afterActionTicks) {
		this.periodTicks = periodTicks;
		this.afterActionTicks = afterActionTicks;
	}

	/**
	 * Notes that the player did something that changes the numbers, so a request is due soon. Another action restarts the
	 * wait, so a series of clicks results in one request.
	 */
	public void afterAction() {
		actionCountdown = afterActionTicks;
	}

	/**
	 * Notes that a request was sent without this schedule asking for it, so the next regular one is a whole period away.
	 */
	public void onRequestSent() {
		sinceLastRequest = 0;
	}

	/**
	 * Advances the schedule by a tick.
	 *
	 * @return true if a request is due now
	 */
	public boolean tick() {
		boolean due = false;
		if(actionCountdown > 0 && --actionCountdown == 0)
			due = true;
		if(++sinceLastRequest >= periodTicks)
			due = true;
		if(due)
			sinceLastRequest = 0;
		return due;
	}

}
