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
 * Lets requests through at most once per minimum interval. The hub screens share one of these for their team information
 * requests, so that a screen that opens right after another one does not ask twice.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class RequestThrottle {

	private final long minIntervalMillis;
	private boolean any;
	private long last;

	public RequestThrottle(long minIntervalMillis) {
		this.minIntervalMillis = minIntervalMillis;
	}

	/**
	 * Checks whether a request may be sent now and, if so, counts it as sent.
	 *
	 * @param nowMillis  the current time in milliseconds
	 */
	public synchronized boolean tryAcquire(long nowMillis) {
		if(any && nowMillis - last < minIntervalMillis && nowMillis >= last)
			return false;
		any = true;
		last = nowMillis;
		return true;
	}

	/**
	 * Forgets the last request, so the next one goes through. Used when the connection changes.
	 */
	public synchronized void reset() {
		any = false;
	}

}
