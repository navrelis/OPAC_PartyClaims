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

package xaero.pac.common.packet.teamclaims;

import net.minecraft.network.FriendlyByteBuf;
import xaero.pac.client.teamclaims.TeamClaimsClientData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static xaero.pac.common.packet.teamclaims.TeamClaimsPacketUtil.*;

/**
 * [Team Claims] Packet 106: scalar options of the two server configs for the admin menu.
 * <ul>
 *     <li>a snapshot ({@code update} false) is split into {@code pageCount} pages with the same {@code snapshotId},
 *     sent in order; the client has the full list once all pages of one snapshot arrived;</li>
 *     <li>an update ({@code update} true, one page) replaces the entries it carries, e.g. the option that was just set.</li>
 * </ul>
 */
public class ClientboundAdminConfigPacket {

	public static final int MAX_PACKET_SIZE = 262144;
	public static final int MAX_ENTRIES = 512;
	public static final int MAX_PAGES = 256;

	private final int snapshotId;
	private final int page;
	private final int pageCount;
	private final boolean update;
	private final List<AdminConfigEntry> entries;

	public ClientboundAdminConfigPacket(int snapshotId, int page, int pageCount, boolean update, List<AdminConfigEntry> entries) {
		this.snapshotId = snapshotId;
		this.page = page;
		this.pageCount = pageCount;
		this.update = update;
		this.entries = List.copyOf(entries);
	}

	public int getSnapshotId() {
		return snapshotId;
	}

	public int getPage() {
		return page;
	}

	public int getPageCount() {
		return pageCount;
	}

	public boolean isUpdate() {
		return update;
	}

	public List<AdminConfigEntry> getEntries() {
		return entries;
	}

	public static class Codec implements BiConsumer<ClientboundAdminConfigPacket, FriendlyByteBuf>, Function<FriendlyByteBuf, ClientboundAdminConfigPacket> {

		@Override
		public ClientboundAdminConfigPacket apply(FriendlyByteBuf input) {
			try {
				if(input.readableBytes() > MAX_PACKET_SIZE)
					return null;
				int snapshotId = readCount(input, Integer.MAX_VALUE);
				int pageCount = readCount(input, MAX_PAGES);
				int page = readCount(input, MAX_PAGES);
				if(pageCount == 0 || page >= pageCount)
					return null;
				boolean update = input.readBoolean();
				int count = readCount(input, MAX_ENTRIES);
				List<AdminConfigEntry> entries = new ArrayList<>(count);
				for(int i = 0; i < count; i++)
					entries.add(AdminConfigEntry.read(input));
				requireEnd(input);
				return new ClientboundAdminConfigPacket(snapshotId, page, pageCount, update, entries);
			} catch(Throwable t) {
				return null;
			}
		}

		@Override
		public void accept(ClientboundAdminConfigPacket t, FriendlyByteBuf u) {
			u.writeVarInt(t.snapshotId);
			u.writeVarInt(t.pageCount);
			u.writeVarInt(t.page);
			u.writeBoolean(t.update);
			int count = Math.min(t.entries.size(), MAX_ENTRIES);
			u.writeVarInt(count);
			for(int i = 0; i < count; i++)
				t.entries.get(i).write(u);
		}

	}

	public static class ClientHandler implements Consumer<ClientboundAdminConfigPacket> {

		@Override
		public void accept(ClientboundAdminConfigPacket t) {
			if(t == null)
				return;
			TeamClaimsClientData.INSTANCE.onConfigPacket(t);
		}

	}

}
