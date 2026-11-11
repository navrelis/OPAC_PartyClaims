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

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * The picture of the whole grid of the Claims tab in one texture, 16 x 16 pixels for each chunk: the terrain with the claim
 * colour over it, or the plain background where there is no terrain. It is painted chunk by chunk, only the chunks whose
 * content changed, and drawn with one call. The texture has to be closed when the screen closes.
 */
public final class ChunkMapImage implements AutoCloseable {

	/** The pixels along one side of a chunk. */
	public static final int CHUNK_PIXELS = 16;

	private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("openpartiesandclaims", "hub_claims_map");
	private static final int BACKGROUND_A = 0xFF262626;
	private static final int BACKGROUND_B = 0xFF2D2D2D;

	private final TextureManager textureManager;
	@Nullable
	private NativeImage image;
	@Nullable
	private DynamicTexture texture;
	private long[] painted = new long[0];
	private int cells;
	private boolean dirty;

	public ChunkMapImage(TextureManager textureManager) {
		this.textureManager = textureManager;
	}

	/**
	 * Makes the picture fit a grid of {@code cells} x {@code cells} chunks. Nothing happens when it already does.
	 */
	public void resize(int cells) {
		if(texture != null && this.cells == cells)
			return;
		close();
		this.cells = cells;
		image = new NativeImage(cells * CHUNK_PIXELS, cells * CHUNK_PIXELS, true);
		texture = new DynamicTexture(image);
		textureManager.register(ID, texture);
		painted = new long[cells * cells];
		Arrays.fill(painted, Long.MIN_VALUE);
		dirty = true;
	}

	/**
	 * Paints one chunk of the grid, unless it still shows the same content.
	 *
	 * @param signature  a number that changes with the content, the same number means the same picture
	 * @param terrain  the 16 x 16 pixels of the terrain, null if there is none
	 * @param claimRgb  the colour of the claim over the chunk, -1 if it is not claimed
	 */
	public void paint(int column, int row, long signature, @Nullable int[] terrain, int claimRgb) {
		int index = row * cells + column;
		if(image == null || painted[index] == signature)
			return;
		painted[index] = signature;
		int background = ((column + row) & 1) == 0 ? BACKGROUND_A : BACKGROUND_B;
		for(int py = 0; py < CHUNK_PIXELS; py++) {
			for(int px = 0; px < CHUNK_PIXELS; px++) {
				int base = terrain == null ? 0 : terrain[py * CHUNK_PIXELS + px];
				boolean hasTerrain = base != 0;
				int argb = hasTerrain ? base : background;
				if(claimRgb >= 0)
					argb = ClaimOverlay.blend(argb, claimRgb, hasTerrain ? ClaimOverlay.CLAIM_ALPHA : 255);
				image.setPixelRGBA(column * CHUNK_PIXELS + px, row * CHUNK_PIXELS + py, toAbgr(argb));
			}
		}
		dirty = true;
	}

	private static int toAbgr(int argb) {
		return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF;
	}

	/**
	 * Sends the changes to the graphics card. Call before drawing.
	 */
	public void upload() {
		if(texture == null || !dirty)
			return;
		texture.upload();
		dirty = false;
	}

	/**
	 * Smooths the picture when it is drawn smaller than it is, a hard edge otherwise. Call before drawing.
	 */
	public void setSmooth(boolean smooth) {
		if(texture != null)
			texture.setFilter(smooth, false);
	}

	public ResourceLocation getId() {
		return ID;
	}

	/**
	 * Gets the size of the picture in pixels along each side.
	 */
	public int getPixelSize() {
		return cells * CHUNK_PIXELS;
	}

	@Override
	public void close() {
		if(texture != null) {
			textureManager.release(ID);
			texture = null;
			image = null;
		}
	}

}
