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

import xaero.pac.common.packet.teamclaims.AdminConfigEntry;
import xaero.pac.common.packet.teamclaims.AdminPlayerBonus;
import xaero.pac.common.packet.teamclaims.AdminPlayerData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * [Team Claims] What the Admin tab lists, without any drawing: the config options grouped by the section of the toml
 * they are in (with a text filter over name, path and comment), the readable name of an option, the range and pending
 * texts, and the totals of a player's limits. Free of client classes so that the gametests can run it.
 */
public final class AdminConfigView {

	/**
	 * A line of the option list: a group header or an option.
	 *
	 * @param header  the label of the group, empty for options that are not in a section, null for an option row
	 * @param entry  the option, null for a header
	 */
	public record Row(@Nullable String header, @Nullable AdminConfigEntry entry) {

		public boolean isHeader() {
			return entry == null;
		}

	}

	private AdminConfigView() {
	}

	/**
	 * Gets the section of the toml that an option is in: its path without the last segment, empty if it has no section.
	 */
	public static String getGroup(String path) {
		int lastDot = path.lastIndexOf('.');
		return lastDot < 0 ? "" : path.substring(0, lastDot);
	}

	/**
	 * Gets the last segment of a path, which is the name of the option.
	 */
	public static String getLastSegment(String path) {
		return path.substring(path.lastIndexOf('.') + 1);
	}

	/**
	 * Turns the name of an option from the toml into words: {@code maxPlayerClaims} becomes {@code Max player claims}.
	 */
	public static String humanise(String name) {
		String[] words = name.replace('_', ' ').trim().split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|\\s+");
		StringBuilder result = new StringBuilder();
		for(String word : words) {
			if(word.isEmpty())
				continue;
			if(result.length() > 0)
				result.append(' ');
			boolean acronym = word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT));
			if(result.length() == 0 || acronym)
				result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			else
				result.append(Character.toLowerCase(word.charAt(0))).append(word.substring(1));
		}
		return result.toString();
	}

	/**
	 * Gets the first segment that all options share, which is {@code serverConfig} for OPAC and {@code teamClaims} for
	 * Team Claims. Empty if the options have none in common.
	 */
	public static String getCommonRoot(List<AdminConfigEntry> entries) {
		String root = null;
		for(AdminConfigEntry entry : entries) {
			String first = entry.path().contains(".") ? entry.path().substring(0, entry.path().indexOf('.')) : "";
			if(root == null)
				root = first;
			else if(!root.equals(first))
				return "";
		}
		return root == null ? "" : root;
	}

	/**
	 * Gets the label of a group header: the section path without the root that all options share, empty for the options
	 * directly under the root.
	 */
	public static String getGroupLabel(String group, String commonRoot) {
		if(group.equals(commonRoot))
			return "";
		if(!commonRoot.isEmpty() && group.startsWith(commonRoot + "."))
			return group.substring(commonRoot.length() + 1);
		return group;
	}

	/**
	 * Splits the text of a search box into lower case words.
	 */
	public static List<String> getTerms(String filter) {
		List<String> terms = new ArrayList<>();
		for(String term : filter.toLowerCase(Locale.ROOT).trim().split("\\s+"))
			if(!term.isEmpty())
				terms.add(term);
		return terms;
	}

	/**
	 * Checks whether an option matches every word of a filter: each word is in its path, readable name or comment.
	 */
	public static boolean matches(AdminConfigEntry entry, List<String> terms) {
		if(terms.isEmpty())
			return true;
		String haystack = (entry.path() + " " + humanise(getLastSegment(entry.path())) + " " + entry.comment()).toLowerCase(Locale.ROOT);
		for(String term : terms)
			if(!haystack.contains(term))
				return false;
		return true;
	}

	/**
	 * Builds the rows of the option list: the options that match the filter, in the order the server sent them, each group
	 * of options that share a section under one header (a header appears again if the server sends the options of a
	 * section apart, which it does not).
	 *
	 * @param entries  the options of one config
	 * @param filter  the text of the search box, may be empty
	 */
	public static List<Row> buildRows(List<AdminConfigEntry> entries, String filter) {
		List<String> terms = getTerms(filter);
		String commonRoot = getCommonRoot(entries);
		List<Row> rows = new ArrayList<>();
		String currentGroup = null;
		for(AdminConfigEntry entry : entries) {
			if(!matches(entry, terms))
				continue;
			String group = getGroup(entry.path());
			if(!group.equals(currentGroup)) {
				rows.add(new Row(getGroupLabel(group, commonRoot), null));
				currentGroup = group;
			}
			rows.add(new Row(null, entry));
		}
		return rows;
	}

	/**
	 * Counts the option rows in rows from {@link #buildRows}.
	 */
	public static int countEntries(List<Row> rows) {
		int count = 0;
		for(Row row : rows)
			if(!row.isHeader())
				count++;
		return count;
	}

	/**
	 * Checks whether a restart-only option has a saved value that the server does not run with yet.
	 */
	public static boolean hasPending(AdminConfigEntry entry) {
		return entry.restart() && entry.pendingValue() != null && !entry.pendingValue().equals(entry.value());
	}

	/**
	 * Gets the range of a number option as {@code min .. max}, with a missing end left empty ({@code 0 ..}). Null for an
	 * option without a range.
	 */
	@Nullable
	public static String getRangeText(AdminConfigEntry entry) {
		if(entry.min() == null && entry.max() == null)
			return null;
		return (entry.min() == null ? "" : entry.min()) + " .. " + (entry.max() == null ? "" : entry.max());
	}

	/**
	 * Shortens a text to a number of characters, with {@code ...} at the end if it was longer.
	 */
	public static String shorten(String text, int maxLength) {
		if(text.length() <= maxLength)
			return text;
		return text.substring(0, Math.max(0, maxLength - 3)) + "...";
	}

	// ---- players ----

	/**
	 * Gets the limit a player has: the base plus the bonus, {@link Integer#MAX_VALUE} for unlimited (a base that is
	 * unlimited, or a sum that does not fit).
	 */
	public static int getTotal(int base, int bonus) {
		if(base == Integer.MAX_VALUE)
			return Integer.MAX_VALUE;
		long sum = (long) base + bonus;
		return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(Integer.MIN_VALUE, sum);
	}

	/**
	 * Gets the base of a bonus of a player.
	 */
	public static int getBase(AdminPlayerData player, AdminPlayerBonus bonus) {
		return switch(bonus) {
			case CLAIMS -> player.baseClaimLimit();
			case FORCELOADS -> player.baseForceloadLimit();
			case ANCHORS -> player.baseAnchorLimit();
			case ANCHOR_RANGE -> player.baseAnchorRange();
		};
	}

	/**
	 * Gets what the player uses of a bonus kind (claims, forceloads, anchors), -1 for the anchor range which is no count.
	 */
	public static int getUsed(AdminPlayerData player, AdminPlayerBonus bonus) {
		return switch(bonus) {
			case CLAIMS -> player.claims();
			case FORCELOADS -> player.forceloads();
			case ANCHORS -> player.anchors();
			case ANCHOR_RANGE -> -1;
		};
	}

	/**
	 * Checks whether a player uses more than the limit.
	 */
	public static boolean isOverLimit(int used, int limit) {
		return used >= 0 && limit != Integer.MAX_VALUE && used > limit;
	}

}
