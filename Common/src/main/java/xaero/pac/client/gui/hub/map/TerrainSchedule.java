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

package xaero.pac.client.gui.hub.map;

/**
 * When the terrain of a chunk is computed again: never computed chunks first, then chunks whose picture is older than
 * {@link #REFRESH_TICKS}, nearest to the centre of the grid first, and never more than {@link #MAX_PER_TICK} chunks in one
 * tick so that the screen does not stall.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class TerrainSchedule {

	/** The most chunks that are computed in one tick. */
	public static final int MAX_PER_TICK = 8;
	/** How many ticks a computed chunk is left alone, 5 seconds. */
	public static final int REFRESH_TICKS = 100;
	/** The tick that stands for a chunk that was never computed. */
	public static final long NEVER = -1;

	private TerrainSchedule() {
	}

	/**
	 * Checks whether a chunk is due to be computed.
	 *
	 * @param lastTick  the tick it was computed in, {@link #NEVER} if it was not
	 */
	public static boolean isDue(long lastTick, long now) {
		return lastTick == NEVER || now - lastTick >= REFRESH_TICKS;
	}

	/**
	 * Picks the chunks to compute in this tick.
	 *
	 * @param lastTicks  the tick each candidate was computed in, {@link #NEVER} if it was not
	 * @param distancesSq  the squared distance of each candidate from the centre of the grid
	 * @param count  the number of candidates in the arrays
	 * @param max  the most to pick
	 * @return the indices of the due candidates, the nearest first, at most {@code max} of them
	 */
	public static int[] pickDue(long[] lastTicks, int[] distancesSq, int count, long now, int max) {
		int[] picked = new int[Math.max(0, max)];
		int pickedCount = 0;
		while(pickedCount < picked.length) {
			int best = -1;
			for(int i = 0; i < count; i++) {
				if(!isDue(lastTicks[i], now) || contains(picked, pickedCount, i))
					continue;
				if(best == -1 || distancesSq[i] < distancesSq[best])
					best = i;
			}
			if(best == -1)
				break;
			picked[pickedCount++] = best;
		}
		return java.util.Arrays.copyOf(picked, pickedCount);
	}

	private static boolean contains(int[] values, int count, int value) {
		for(int i = 0; i < count; i++)
			if(values[i] == value)
				return true;
		return false;
	}

}
