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
 * Decides how many chunks the grid of the Claims tab shows and how big a chunk is drawn, so that the grid fits into the
 * space that the screen has left at the current GUI scale and window size. The grid is always a square of an odd number
 * of cells, so that the centre chunk is a cell of its own. A chunk is drawn in one of {@link #ZOOM_STEPS} sizes, from
 * {@link #MIN_CELL} to four times that, and the grid shows as many chunks as fit at that size.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class HubGridFit {

	/**
	 * The smallest size in pixels of one drawn chunk, the most zoomed out step.
	 */
	public static final int MIN_CELL = 6;
	/**
	 * The number of zoom steps, the size of a chunk doubles with each step: 6, 12 and 24 pixels.
	 */
	public static final int ZOOM_STEPS = 3;
	/**
	 * The zoom step that the grid starts with.
	 */
	public static final int DEFAULT_ZOOM = 1;
	/**
	 * The most chunks to each side of the centre that the grid ever shows.
	 */
	public static final int MAX_RADIUS = 24;
	/**
	 * The fewest chunks to each side of the centre that the grid shows, however little space there is.
	 */
	public static final int MIN_RADIUS = 1;

	private HubGridFit() {
	}

	/**
	 * The size of the grid.
	 *
	 * @param radius  the number of chunks to each side of the centre chunk
	 * @param cell  the size of one chunk in pixels
	 */
	public record Fit(int radius, int cell) {

		/**
		 * Gets the number of chunks along each side of the grid.
		 */
		public int cells() {
			return radius * 2 + 1;
		}

		/**
		 * Gets the size of the whole grid in pixels along each side.
		 */
		public int size() {
			return cells() * cell;
		}

	}

	/**
	 * Gets the zoom step that is in range.
	 */
	public static int clampZoom(int zoom) {
		return Math.max(0, Math.min(ZOOM_STEPS - 1, zoom));
	}

	/**
	 * Gets the size in pixels of one chunk at a zoom step.
	 */
	public static int cellForZoom(int zoom) {
		return MIN_CELL << clampZoom(zoom);
	}

	/**
	 * Gets the biggest grid that fits into a square at a zoom step. Chunks are drawn at the size of the step and the
	 * radius is what fits, from {@link #MIN_RADIUS} to {@link #MAX_RADIUS}. If even the smallest grid does not fit, the
	 * radius stays at {@link #MIN_RADIUS} and the chunks shrink instead of the grid overflowing.
	 *
	 * @param side  the size in pixels of the square that is available
	 * @param zoom  the zoom step, 0 is the most zoomed out
	 * @return the radius and the chunk size, the grid is not bigger than the square unless the square is too small for
	 *         the smallest grid of 3 x 3 chunks of 1 pixel
	 */
	public static Fit fit(int side, int zoom) {
		int available = Math.max(0, side);
		int target = cellForZoom(zoom);
		int radius = radiusFor(available, target);
		if(radius >= MIN_RADIUS)
			return new Fit(Math.min(radius, MAX_RADIUS), target);
		int cell = Math.max(1, available / (MIN_RADIUS * 2 + 1));
		return new Fit(MIN_RADIUS, Math.min(cell, target));
	}

	private static int radiusFor(int side, int cell) {
		return (side / cell - 1) / 2;
	}

	/**
	 * Gets the index of the cell at an offset along the grid.
	 *
	 * @param offset  the distance in pixels from the start of the grid
	 * @param cell  the size of one cell in pixels
	 * @param cells  the number of cells along the grid
	 * @param clamp  true to answer the first or the last cell for an offset outside of the grid, false to answer -1
	 * @return the index of the cell, -1 if there is none
	 */
	public static int indexAt(int offset, int cell, int cells, boolean clamp) {
		if(cell <= 0 || cells <= 0)
			return -1;
		int index = Math.floorDiv(offset, cell);
		if(index >= 0 && index < cells)
			return index;
		return clamp ? Math.max(0, Math.min(cells - 1, index)) : -1;
	}

}
