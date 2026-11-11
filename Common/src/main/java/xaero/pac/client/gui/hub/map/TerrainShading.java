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
 * The shading of vanilla maps (at scale 0), for the terrain behind the claims of the Claims tab: land is lighter or darker
 * by the height difference to the block to the north, water by its depth, with the checkerboard noise that vanilla adds.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class TerrainShading {

	/** The brightness steps of a map colour, the same order as vanilla's {@code MapColor.Brightness}. */
	public static final int LOW = 0;
	public static final int NORMAL = 1;
	public static final int HIGH = 2;
	public static final int LOWEST = 3;

	private static final int[] MODIFIERS = {180, 220, 255, 135};

	private TerrainShading() {
	}

	/**
	 * Gets the brightness of a land block.
	 *
	 * @param height  the height of the block
	 * @param northHeight  the height of the block to the north of it
	 * @param oddColumn  whether the X and Z coordinates of the block add up to an odd number
	 */
	public static int landBrightness(int height, int northHeight, boolean oddColumn) {
		double shade = (height - northHeight) * 4.0 / 5.0 + ((oddColumn ? 1 : 0) - 0.5) * 0.4;
		if(shade > 0.6)
			return HIGH;
		if(shade < -0.6)
			return LOW;
		return NORMAL;
	}

	/**
	 * Gets the brightness of a water block, deeper water is darker.
	 *
	 * @param depth  the number of water blocks below the surface, plus the surface block's own bottom neighbour as vanilla counts it
	 * @param oddColumn  whether the X and Z coordinates of the block add up to an odd number
	 */
	public static int waterBrightness(int depth, boolean oddColumn) {
		double shade = depth * 0.1 + (oddColumn ? 0.2 : 0);
		if(shade < 0.5)
			return HIGH;
		if(shade > 0.9)
			return LOW;
		return NORMAL;
	}

	/**
	 * Applies a brightness to a map colour.
	 *
	 * @param rgb  the colour of the block, without alpha
	 * @return the shaded colour as opaque ARGB
	 */
	public static int shade(int rgb, int brightness) {
		int modifier = MODIFIERS[brightness];
		int r = (rgb >> 16 & 255) * modifier / 255;
		int g = (rgb >> 8 & 255) * modifier / 255;
		int b = (rgb & 255) * modifier / 255;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

}
