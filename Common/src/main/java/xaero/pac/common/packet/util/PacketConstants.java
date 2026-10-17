/*
 * Open Parties and Claims - adds chunk claims and player parties to Minecraft
 * Copyright (C) 2026, Xaero <xaero1996@gmail.com> and contributors
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

package xaero.pac.common.packet.util;

import net.minecraft.network.chat.Component;

public class PacketConstants {

	/**
	 * [Team Claims] Upstream's network version (7 in OPAC 0.32.7) times 1000 plus the protocol revision of the fork
	 * ({@link #TEAM_CLAIMS_PROTOCOL_REVISION}), so that it can never equal a version of upstream OPAC or of another
	 * Team Claims release with other packets. Team Claims 1.3.0 and older sent upstream's plain 7.
	 */
	public static final int UPSTREAM_NETWORK_VERSION = 7;
	/** [Team Claims] Raised whenever the fork's packets change: 1 = 1.4.0 (packets 104 to 113). */
	public static final int TEAM_CLAIMS_PROTOCOL_REVISION = 1;
	public static final int NETWORK_VERSION = UPSTREAM_NETWORK_VERSION * 1000 + TEAM_CLAIMS_PROTOCOL_REVISION;
	public static final Component NETWORK_VERSION_MISMATCH =
			Component.literal("Open Parties and Claims versions between server and client aren't compatible!");

}
