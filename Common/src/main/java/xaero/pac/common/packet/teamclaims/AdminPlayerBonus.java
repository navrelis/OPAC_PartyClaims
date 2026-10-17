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

import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

/**
 * [Team Claims] The four per-player bonus options an admin can change in the admin menu, which are OPAC player config
 * options (op-configurable, set like {@code /opac player-config for <player> set <option> <value>}).
 */
public enum AdminPlayerBonus {

	CLAIMS(PlayerConfigOptions.BONUS_CHUNK_CLAIMS),
	FORCELOADS(PlayerConfigOptions.BONUS_CHUNK_FORCELOADS),
	ANCHORS(PlayerConfigOptions.BONUS_CLAIMING_ANCHORS),
	ANCHOR_RANGE(PlayerConfigOptions.BONUS_CLAIMING_ANCHOR_RANGE);

	/** The largest bonus (positive or negative) the admin menu sets. */
	public static final int MAX_ABS_VALUE = 1_000_000;

	private final IPlayerConfigOptionSpecAPI<Integer> option;

	AdminPlayerBonus(IPlayerConfigOptionSpecAPI<Integer> option) {
		this.option = option;
	}

	public IPlayerConfigOptionSpecAPI<Integer> option() {
		return option;
	}

}
