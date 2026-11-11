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

import java.util.Locale;

/**
 * Sorts the options of the player config screen into groups. The group of an option comes from its shortened id
 * (the id without the {@code playerConfig.} root): the first segment, so {@code claims.name} and {@code claims.forceload.enabled}
 * are in {@code claims}, {@code parties.name} is in {@code parties}, and an id without a segment in front of the name
 * ({@code bonusPlayerGroups}) is in {@code general}. The options under {@code claims.protection.} are far too many for one
 * group, so they are split by the option category instead ({@code claims.protection.block_protection}, ...), except those
 * in a {@code GENERAL*} category, which stay in the group of their first segment.
 * <p>
 * Options that other mods add with an id of their own end up in a group named after their first segment.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class ConfigOptionGrouping {

	public static final String GENERAL = "general";
	private static final String PROTECTION_PREFIX = "claims.protection.";
	private static final String GENERAL_CATEGORY_PREFIX = "GENERAL";

	private ConfigOptionGrouping() {
	}

	/**
	 * @param shortenedId  the option id without the root, e.g. {@code claims.protection.exceptions.fullAccess}
	 * @param categoryName the name of the option category, e.g. {@code BLOCK_PROTECTION}
	 */
	public static String groupKey(String shortenedId, String categoryName) {
		if(shortenedId.startsWith(PROTECTION_PREFIX) && !categoryName.startsWith(GENERAL_CATEGORY_PREFIX))
			return PROTECTION_PREFIX + categoryName.toLowerCase(Locale.ROOT);
		int firstDot = shortenedId.indexOf('.');
		return firstDot <= 0 ? GENERAL : shortenedId.substring(0, firstDot);
	}

	/**
	 * A readable title for a group that has no translation, e.g. {@code my_mod} gives {@code My Mod}.
	 */
	public static String fallbackTitle(String groupKey) {
		String last = groupKey.substring(groupKey.lastIndexOf('.') + 1);
		StringBuilder title = new StringBuilder();
		boolean wordStart = true;
		for(int i = 0; i < last.length(); i++) {
			char c = last.charAt(i);
			if(c == '_' || c == '-') {
				title.append(' ');
				wordStart = true;
			} else {
				title.append(wordStart ? Character.toUpperCase(c) : c);
				wordStart = false;
			}
		}
		return title.toString();
	}

}
