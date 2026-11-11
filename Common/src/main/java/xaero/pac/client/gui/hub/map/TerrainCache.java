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

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * The pictures of the terrain of the chunks around the centre of the grid of the Claims tab. A few chunks are drawn
 * in each tick (see {@link TerrainSchedule}), only from chunks that the client has loaded, and a picture is drawn again
 * after a few seconds. A chunk that is not loaded any more loses its picture when that is due.
 */
public final class TerrainCache {

	/**
	 * The picture of one chunk.
	 */
	public static final class Entry {
		private final int[] argb = new int[256];
		private long version;
		private long tick;

		/**
		 * Gets the 16 x 16 pixels, row by row, as opaque ARGB, 0 where there is no block.
		 */
		public int[] argb() {
			return argb;
		}

		/**
		 * Gets the number that tells this picture from the other pictures, which changes with each new picture.
		 */
		public long version() {
			return version;
		}
	}

	private final Map<Long, Entry> entries = new HashMap<>();
	private final ChunkTerrainSampler sampler = new ChunkTerrainSampler();
	private long tick;
	private long versionCounter;
	@Nullable
	private ClientLevel level;
	private long[] lastTicks = new long[0];
	private int[] distancesSq = new int[0];
	private int[] chunkXs = new int[0];
	private int[] chunkZs = new int[0];

	private static long key(int chunkX, int chunkZ) {
		return (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
	}

	/**
	 * Draws the chunks that are due.
	 *
	 * @param playerY  the height of the player, for the dimensions with a ceiling
	 * @param radius  the number of chunks to each side of the centre that the grid shows
	 */
	public void tick(ClientLevel level, int playerY, int centerX, int centerZ, int radius) {
		tick++;
		if(this.level != level) {
			entries.clear();
			this.level = level;
		}
		int cells = radius * 2 + 1;
		int total = cells * cells;
		if(lastTicks.length < total) {
			lastTicks = new long[total];
			distancesSq = new int[total];
			chunkXs = new int[total];
			chunkZs = new int[total];
		}
		int count = 0;
		for(int dz = -radius; dz <= radius; dz++) {
			for(int dx = -radius; dx <= radius; dx++) {
				int chunkX = centerX + dx;
				int chunkZ = centerZ + dz;
				Entry entry = entries.get(key(chunkX, chunkZ));
				if(level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
					if(entry != null && TerrainSchedule.isDue(entry.tick, tick))
						entries.remove(key(chunkX, chunkZ));
					continue;
				}
				lastTicks[count] = entry == null ? TerrainSchedule.NEVER : entry.tick;
				distancesSq[count] = dx * dx + dz * dz;
				chunkXs[count] = chunkX;
				chunkZs[count] = chunkZ;
				count++;
			}
		}
		boolean ceiling = level.dimensionType().hasCeiling();
		for(int index : TerrainSchedule.pickDue(lastTicks, distancesSq, count, tick, TerrainSchedule.MAX_PER_TICK))
			compute(level, chunkXs[index], chunkZs[index], ceiling, playerY + 8);
		if(entries.size() > total * 2)
			entries.keySet().removeIf(key -> Math.abs((int) (key >> 32) - centerX) > radius + 2 || Math.abs((int) (long) key - centerZ) > radius + 2);
	}

	private void compute(ClientLevel level, int chunkX, int chunkZ, boolean ceiling, int ceilingStartY) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
		if(chunk == null)
			return;
		Entry entry = entries.computeIfAbsent(key(chunkX, chunkZ), k -> new Entry());
		sampler.sample(level, chunk, level.getChunkSource().getChunkNow(chunkX, chunkZ - 1), ceiling, ceilingStartY, entry.argb);
		entry.tick = tick;
		entry.version = ++versionCounter;
	}

	/**
	 * Gets the picture of a chunk, null if there is none.
	 */
	@Nullable
	public Entry get(int chunkX, int chunkZ) {
		return entries.get(key(chunkX, chunkZ));
	}

}
