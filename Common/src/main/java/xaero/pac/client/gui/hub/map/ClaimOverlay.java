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

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * How the claims are laid over the terrain in the Claims tab: the claim colour drawn semi-transparent over the terrain,
 * and the 1 pixel lines between the claims of different owners.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class ClaimOverlay {

	/** The alpha of a claim colour over terrain. */
	public static final int CLAIM_ALPHA = 0x78;
	/** Stands for the neighbour of a chunk at the edge of the grid, which never gets a line. */
	public static final Object OUTSIDE = new Object();

	public static final int NORTH = 1;
	public static final int EAST = 2;
	public static final int SOUTH = 4;
	public static final int WEST = 8;

	private ClaimOverlay() {
	}

	/**
	 * Lays a claim colour over a pixel.
	 *
	 * @param base  the pixel below, ARGB
	 * @param claimRgb  the colour of the claim, without alpha
	 * @param alpha  the alpha of the claim colour, 0 to 255
	 * @return the mix as opaque ARGB
	 */
	public static int blend(int base, int claimRgb, int alpha) {
		int r = mix(base >> 16 & 255, claimRgb >> 16 & 255, alpha);
		int g = mix(base >> 8 & 255, claimRgb >> 8 & 255, alpha);
		int b = mix(base & 255, claimRgb & 255, alpha);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static int mix(int below, int above, int alpha) {
		return (below * (255 - alpha) + above * alpha) / 255;
	}

	/**
	 * Gets the colour of the line along the edge of a claim, the colour of the claim made darker.
	 *
	 * @return opaque ARGB
	 */
	public static int borderColor(int claimRgb) {
		return 0xFF000000 | (claimRgb >> 16 & 255) * 2 / 5 << 16 | (claimRgb >> 8 & 255) * 2 / 5 << 8 | (claimRgb & 255) * 2 / 5;
	}

	/**
	 * Decides which edges of a chunk get a line. A claimed chunk gets a line on its north and west edge where the neighbour is
	 * another owner or not claimed, and on its east and south edge where the neighbour is not claimed, so that the line
	 * between two claims is one pixel wide. Unclaimed chunks get none, and neither do edges at the border of the grid.
	 *
	 * @param self  the owner of the chunk, null if it is not claimed
	 * @param north  the owner of the neighbour, null if it is not claimed, {@link #OUTSIDE} if it is outside of the grid
	 * @return the edges as a mask of {@link #NORTH}, {@link #EAST}, {@link #SOUTH} and {@link #WEST}
	 */
	public static int edges(@Nullable Object self, @Nullable Object north, @Nullable Object east, @Nullable Object south, @Nullable Object west) {
		if(self == null)
			return 0;
		int edges = 0;
		if(differs(self, north))
			edges |= NORTH;
		if(differs(self, west))
			edges |= WEST;
		if(east == null && differs(self, east))
			edges |= EAST;
		if(south == null && differs(self, south))
			edges |= SOUTH;
		return edges;
	}

	private static boolean differs(Object self, @Nullable Object neighbour) {
		return neighbour != OUTSIDE && !Objects.equals(self, neighbour);
	}

}
