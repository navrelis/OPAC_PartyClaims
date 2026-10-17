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

import javax.annotation.Nullable;
import java.util.List;

import static xaero.pac.common.packet.teamclaims.TeamClaimsPacketUtil.*;

/**
 * [Team Claims] One scalar option of a server config spec as the admin menu shows it. Every value is a string in the
 * form the option takes when it is set ({@code true}/{@code false}, a decimal number, an enum constant name, or the text
 * itself).
 *
 * @param configId       {@code opac} (OPAC's server config) or {@code teamclaims} (the Team Claims server config)
 * @param path           the full dotted path in the toml, e.g. {@code serverConfig.claims.maxPlayerClaims}
 * @param type           the value type
 * @param value          the value the server runs with right now
 * @param defaultValue   the default of the option
 * @param min            the smallest allowed number, null without a range
 * @param max            the largest allowed number, null without a range
 * @param allowedValues  the allowed constants of an enum option, empty for any other type
 * @param comment        the comment of the option in the toml (for a tooltip), empty if it has none
 * @param restart        whether a change only takes effect after a server restart
 * @param pendingValue   for a restart-only option: the value saved in the toml that is not running yet, else null
 */
public record AdminConfigEntry(String configId, String path, ValueType type, String value, String defaultValue,
		@Nullable String min, @Nullable String max, List<String> allowedValues, String comment, boolean restart,
		@Nullable String pendingValue) {

	public static final String CONFIG_OPAC = "opac";
	public static final String CONFIG_TEAM_CLAIMS = "teamclaims";

	public static final int MAX_CONFIG_ID_LENGTH = 32;
	public static final int MAX_PATH_LENGTH = 256;
	public static final int MAX_VALUE_LENGTH = 4096;
	public static final int MAX_COMMENT_LENGTH = 4096;
	public static final int MAX_ALLOWED_VALUES = 64;
	public static final int MAX_ALLOWED_VALUE_LENGTH = 128;

	public enum ValueType { BOOLEAN, INT, LONG, DOUBLE, ENUM, STRING }

	public AdminConfigEntry {
		allowedValues = List.copyOf(allowedValues);
	}

	/** The key that identifies the option across both configs. */
	public String key() {
		return configId + ":" + path;
	}

	public void write(FriendlyByteBuf output) {
		writeString(output, configId, MAX_CONFIG_ID_LENGTH);
		writeString(output, path, MAX_PATH_LENGTH);
		writeEnum(output, type);
		writeString(output, value, MAX_VALUE_LENGTH);
		writeString(output, defaultValue, MAX_VALUE_LENGTH);
		writeNullableString(output, min, MAX_VALUE_LENGTH);
		writeNullableString(output, max, MAX_VALUE_LENGTH);
		writeStrings(output, allowedValues, MAX_ALLOWED_VALUES, MAX_ALLOWED_VALUE_LENGTH);
		writeString(output, comment, MAX_COMMENT_LENGTH);
		output.writeBoolean(restart);
		writeNullableString(output, pendingValue, MAX_VALUE_LENGTH);
	}

	public static AdminConfigEntry read(FriendlyByteBuf input) {
		return new AdminConfigEntry(readString(input, MAX_CONFIG_ID_LENGTH), readString(input, MAX_PATH_LENGTH),
				readEnum(input, ValueType.class), readString(input, MAX_VALUE_LENGTH), readString(input, MAX_VALUE_LENGTH),
				readNullableString(input, MAX_VALUE_LENGTH), readNullableString(input, MAX_VALUE_LENGTH),
				readStrings(input, MAX_ALLOWED_VALUES, MAX_ALLOWED_VALUE_LENGTH), readString(input, MAX_COMMENT_LENGTH),
				input.readBoolean(), readNullableString(input, MAX_VALUE_LENGTH));
	}

}
