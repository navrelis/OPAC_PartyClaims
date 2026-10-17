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

package xaero.pac.teamclaims.twoplayertest.server;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [Team Claims] Reads the plain {@code key = value} lines of a server config toml as the file holds them, by their full
 * path ({@code [serverConfig.parties]} + {@code maxPartyInvites = 17} is {@code serverConfig.parties.maxPartyInvites}),
 * so that a snapshot shows what was saved to the file next to what the server runs with. Values keep their toml form
 * except that the quotes of a string are removed. Lists and inline tables are not needed and come out as their text.
 */
public final class TomlValues {

	private TomlValues() {
	}

	public static Map<String, String> parse(String toml) {
		Map<String, String> values = new LinkedHashMap<>();
		String section = "";
		for(String rawLine : toml.split("\\R")) {
			String line = rawLine.strip();
			if(line.isEmpty() || line.startsWith("#"))
				continue;
			if(line.startsWith("[") && line.endsWith("]")) {
				section = line.substring(line.startsWith("[[") ? 2 : 1, line.length() - (line.endsWith("]]") ? 2 : 1)).strip();
				continue;
			}
			int equals = line.indexOf('=');
			if(equals <= 0)
				continue;
			String key = line.substring(0, equals).strip();
			String value = stripComment(line.substring(equals + 1).strip());
			if(value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
				value = value.substring(1, value.length() - 1);
			values.put(section.isEmpty() ? key : section + "." + key, value);
		}
		return values;
	}

	private static String stripComment(String value) {
		boolean inString = false;
		for(int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if(c == '"' && (i == 0 || value.charAt(i - 1) != '\\'))
				inString = !inString;
			else if(c == '#' && !inString)
				return value.substring(0, i).strip();
		}
		return value;
	}

}
