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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the options of a config screen, the collapse state and the search text into the rows the screen shows: a header
 * for every group, the options of the expanded groups, and fillers.
 * <ul>
 * <li>No search: a group shows its options when it is expanded.</li>
 * <li>Search: only options that contain every word of the search text (case-insensitive, in their name, id or tooltip) are
 * kept, groups without such an option are left out, and the groups that are left are shown expanded. The collapse state
 * itself is not changed, so it is back when the search is cleared.</li>
 * <li>A list with a single group has no headers.</li>
 * <li>The screen puts {@value #ROWS_PER_COLUMN} rows in a column. A header that opens a group is never put in the last
 * row of a column, because its options would start on the next one: a filler takes that row.</li>
 * </ul>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class ConfigViewLayout {

	public static final int ROWS_PER_COLUMN = 6;

	private ConfigViewLayout() {
	}

	public enum RowKind {
		HEADER, OPTION, FILLER
	}

	/**
	 * @param entryIndex the index of the option in the entry list for an {@link RowKind#OPTION}, otherwise -1
	 * @param shown      for a header: how many options of the group match the search (all of them without a search)
	 * @param total      for a header: how many options the group has
	 * @param expanded   for a header: whether the options of the group follow
	 */
	public record Row(RowKind kind, String groupKey, int entryIndex, int shown, int total, boolean expanded) {
	}

	/**
	 * An option as the layout sees it. The search text is the name, id and tooltip in lower case.
	 */
	public record Entry(String groupKey, String searchText) {

		public static Entry of(String groupKey, String name, String id, String tooltip) {
			return new Entry(groupKey, (name + "\n" + id + "\n" + tooltip).toLowerCase(Locale.ROOT));
		}

		boolean matches(List<String> terms) {
			for(String term : terms)
				if(!searchText.contains(term))
					return false;
			return true;
		}

	}

	/**
	 * The words of a search text in lower case, empty when there is nothing to search for.
	 */
	public static List<String> searchTerms(String query) {
		List<String> terms = new ArrayList<>();
		if(query == null)
			return terms;
		for(String term : query.toLowerCase(Locale.ROOT).trim().split("\\s+"))
			if(!term.isEmpty())
				terms.add(term);
		return terms;
	}

	/**
	 * @param leadingCells how many cells the screen fills before the first row, to know which row ends a column
	 */
	public static List<Row> compute(List<Entry> entries, ConfigViewState state, String query, int leadingCells) {
		List<String> terms = searchTerms(query);
		boolean searching = !terms.isEmpty();
		Map<String, List<Integer>> groups = new LinkedHashMap<>();
		for(int i = 0; i < entries.size(); i++)
			groups.computeIfAbsent(entries.get(i).groupKey(), k -> new ArrayList<>()).add(i);
		state.register(new ArrayList<>(groups.keySet()), entries.size());
		boolean headers = groups.size() > 1;
		List<Row> rows = new ArrayList<>();
		for(Map.Entry<String, List<Integer>> group : groups.entrySet()) {
			String key = group.getKey();
			List<Integer> shown = new ArrayList<>();
			for(int index : group.getValue())
				if(!searching || entries.get(index).matches(terms))
					shown.add(index);
			if(shown.isEmpty())
				continue;
			boolean expanded = !headers || searching || state.isExpanded(key);
			if(headers) {
				if(expanded && (leadingCells + rows.size()) % ROWS_PER_COLUMN == ROWS_PER_COLUMN - 1)
					rows.add(new Row(RowKind.FILLER, key, -1, 0, 0, false));
				rows.add(new Row(RowKind.HEADER, key, -1, shown.size(), group.getValue().size(), expanded));
			}
			if(expanded)
				for(int index : shown)
					rows.add(new Row(RowKind.OPTION, key, index, 0, 0, false));
		}
		return rows;
	}

}
