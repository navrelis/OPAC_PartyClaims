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

import javax.annotation.Nullable;

/**
 * The rectangle of chunks that a player selected on the grid of the Claims tab, by dragging from one chunk to another or
 * by clicking one chunk. The two corners are kept as they were picked, so that the selection can be extended while the
 * mouse is still down; every other value is for the normalised rectangle, whichever way it was dragged.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 *
 * @param anchorX  the X of the chunk where the selection started
 * @param anchorZ  the Z of the chunk where the selection started
 * @param endX  the X of the chunk where the selection currently ends
 * @param endZ  the Z of the chunk where the selection currently ends
 */
public record ChunkSelection(int anchorX, int anchorZ, int endX, int endZ) {

	/**
	 * Creates a selection of a single chunk.
	 */
	public static ChunkSelection single(int x, int z) {
		return new ChunkSelection(x, z, x, z);
	}

	/**
	 * Gets this selection with its end moved to another chunk, as when the mouse is dragged.
	 */
	public ChunkSelection withEnd(int x, int z) {
		return new ChunkSelection(anchorX, anchorZ, x, z);
	}

	/**
	 * Gets the lowest chunk X of the selection.
	 */
	public int left() {
		return Math.min(anchorX, endX);
	}

	/**
	 * Gets the lowest chunk Z of the selection.
	 */
	public int top() {
		return Math.min(anchorZ, endZ);
	}

	/**
	 * Gets the highest chunk X of the selection.
	 */
	public int right() {
		return Math.max(anchorX, endX);
	}

	/**
	 * Gets the highest chunk Z of the selection.
	 */
	public int bottom() {
		return Math.max(anchorZ, endZ);
	}

	/**
	 * Gets how many chunks wide the selection is.
	 */
	public int width() {
		return (int) ((long) right() - left() + 1);
	}

	/**
	 * Gets how many chunks long the selection is.
	 */
	public int height() {
		return (int) ((long) bottom() - top() + 1);
	}

	/**
	 * Gets the number of chunks in the selection.
	 */
	public long count() {
		return ((long) right() - left() + 1) * ((long) bottom() - top() + 1);
	}

	/**
	 * Checks whether a chunk is inside the selection.
	 */
	public boolean contains(int x, int z) {
		return x >= left() && x <= right() && z >= top() && z <= bottom();
	}

	/**
	 * Checks whether the selection is exactly one chunk.
	 */
	public boolean isSingle() {
		return count() == 1;
	}

	/**
	 * Checks whether the selection is bigger than the server allows for one claim, unclaim or forceload action (the server
	 * option {@code maxSingleClaimActionSize}, which the server sends with the team information).
	 *
	 * @param maxActionSize  the limit of the server, null if it is not known yet, in which case nothing is too big
	 */
	public boolean exceedsActionSize(@Nullable Integer maxActionSize) {
		return maxActionSize != null && count() > maxActionSize;
	}

}
