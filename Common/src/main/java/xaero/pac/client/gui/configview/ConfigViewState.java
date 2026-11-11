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

package xaero.pac.client.gui.configview;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which groups of a config screen are expanded. One instance per config type is kept for the whole game session
 * ({@link #forType}), so a screen that is opened again (or rebuilt after switching the sub-config) looks as the player left it.
 * <p>
 * The first time a group is seen it gets a default: with a short list ({@value #SHORT_LIST_LIMIT} options or fewer, what
 * fits on a page) every group is expanded, otherwise only the first group is, and only if no group was seen before.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class ConfigViewState {

	public static final int SHORT_LIST_LIMIT = 12;
	private static final Map<String, ConfigViewState> STATES = new HashMap<>();

	private final Map<String, Boolean> expanded = new HashMap<>();

	public static ConfigViewState forType(String configType) {
		return STATES.computeIfAbsent(configType, t -> new ConfigViewState());
	}

	public static void clearAll() {
		STATES.clear();
	}

	/**
	 * Gives the groups that have not been seen yet their default.
	 *
	 * @param groupsInOrder the group keys in display order
	 * @param optionCount   the number of options in all groups together
	 */
	public void register(List<String> groupsInOrder, int optionCount) {
		boolean nothingSeenBefore = expanded.isEmpty();
		boolean first = true;
		for(String group : groupsInOrder) {
			if(!expanded.containsKey(group))
				expanded.put(group, optionCount <= SHORT_LIST_LIMIT || nothingSeenBefore && first);
			first = false;
		}
	}

	public boolean isExpanded(String group) {
		return expanded.getOrDefault(group, true);
	}

	public void toggle(String group) {
		expanded.put(group, !isExpanded(group));
	}

}
