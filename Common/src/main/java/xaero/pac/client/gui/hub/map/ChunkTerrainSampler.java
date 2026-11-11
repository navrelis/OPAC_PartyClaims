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

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * Draws a chunk like a vanilla map does: the colour of the top block of each column, lighter or darker by the height
 * difference to the column to the north, and water darker the deeper it is (see {@link TerrainShading}). In a dimension
 * with a ceiling the top block is the first solid block under air below a given height, so that the picture shows the
 * cave around the player and not the roof. Reads loaded chunks only.
 */
public final class ChunkTerrainSampler {

	private static final int NO_HEIGHT = Integer.MIN_VALUE;

	private final Column column = new Column();
	private final Column northColumn = new Column();
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private final BlockPos.MutableBlockPos depthPos = new BlockPos.MutableBlockPos();

	private static final class Column {
		private boolean known;
		private int height;
		private int rgb;
		private boolean water;
		private int depth;
	}

	/**
	 * Draws a chunk.
	 *
	 * @param north  the chunk to the north of it, for the shading of its first row, null if it is not loaded
	 * @param ceiling  whether to look for the top block under air below {@code ceilingStartY} instead of the surface
	 * @param ceilingStartY  the height to start from when looking under a ceiling
	 * @param out  receives the 16 x 16 pixels of the chunk, row by row, as opaque ARGB, 0 where there is no block
	 */
	public void sample(Level level, LevelChunk chunk, @Nullable LevelChunk north, boolean ceiling, int ceilingStartY, int[] out) {
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		for(int x = 0; x < 16; x++) {
			int previous = NO_HEIGHT;
			if(north != null) {
				scan(level, north, x, 15, ceiling, ceilingStartY, northColumn);
				if(northColumn.known)
					previous = northColumn.height;
			}
			for(int z = 0; z < 16; z++) {
				scan(level, chunk, x, z, ceiling, ceilingStartY, column);
				if(!column.known) {
					out[z * 16 + x] = 0;
					continue;
				}
				int height = column.height;
				boolean odd = ((baseX + x + baseZ + z) & 1) != 0;
				int brightness = column.water ? TerrainShading.waterBrightness(column.depth, odd)
						: TerrainShading.landBrightness(height, previous == NO_HEIGHT ? height : previous, odd);
				out[z * 16 + x] = TerrainShading.shade(column.rgb, brightness);
				previous = height;
			}
		}
	}

	private void scan(Level level, LevelChunk chunk, int x, int z, boolean ceiling, int ceilingStartY, Column result) {
		int minY = level.getMinBuildHeight();
		int blockX = chunk.getPos().getMinBlockX() + x;
		int blockZ = chunk.getPos().getMinBlockZ() + z;
		int y = ceiling ? Math.min(ceilingStartY, level.getMaxBuildHeight() - 1) : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
		if(ceiling) {
			//from the starting point down, skip the solid blocks to the first air
			while(y > minY && chunk.getBlockState(pos.set(blockX, y, blockZ)).getMapColor(level, pos) != MapColor.NONE)
				y--;
		}
		BlockState state;
		MapColor mapColor;
		while(true) {
			pos.set(blockX, y, blockZ);
			state = chunk.getBlockState(pos);
			mapColor = state.getMapColor(level, pos);
			if(mapColor != MapColor.NONE || y <= minY)
				break;
			y--;
		}
		if(mapColor == MapColor.NONE) {
			result.known = false;
			return;
		}
		result.depth = 0;
		if(!state.getFluidState().isEmpty()) {
			int depthY = y - 1;
			BlockState below;
			do {
				below = chunk.getBlockState(depthPos.set(blockX, depthY--, blockZ));
				result.depth++;
			} while(depthY > minY && !below.getFluidState().isEmpty());
			mapColor = state.getFluidState().createLegacyBlock().getMapColor(level, pos);
		}
		result.known = true;
		result.height = y;
		result.water = mapColor == MapColor.WATER;
		result.rgb = mapColor.col;
	}

}
