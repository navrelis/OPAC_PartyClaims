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
import xaero.pac.common.packet.teamclaims.AdminConfigEntry.ValueType;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * [Team Claims] The client-side rules for the values that the Admin tab sends: whether the text in an edit box is a valid
 * value for an option (type, range, allowed constants), whether it differs from what the server has saved, and what a
 * toggle or cycle button sends next. The server validates every value again with the real spec, so these only decide
 * when the Save button is enabled. Free of client classes so that the gametests can run it.
 */
public final class AdminValueRules {

	/**
	 * The longest text of a value that the server accepts ({@link AdminConfigEntry#MAX_VALUE_LENGTH}).
	 */
	public static final int MAX_TEXT_LENGTH = AdminConfigEntry.MAX_VALUE_LENGTH;

	private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

	/**
	 * What a text is as a value of an option.
	 */
	public enum Status {
		/**
		 * The text is a valid value.
		 */
		OK,
		/**
		 * There is no text in a box that needs one.
		 */
		EMPTY,
		/**
		 * The text is no number (or no whole number) of the type of the option.
		 */
		NOT_A_NUMBER,
		/**
		 * The number is outside of the range of the option.
		 */
		OUT_OF_RANGE,
		/**
		 * The text is none of the allowed constants (or no true/false).
		 */
		NOT_ALLOWED,
		/**
		 * The text is longer than the server accepts.
		 */
		TOO_LONG
	}

	private AdminValueRules() {
	}

	/**
	 * Checks a text against the type, range and allowed constants of an option.
	 */
	public static Status check(AdminConfigEntry entry, String text) {
		return check(entry.type(), text, entry.min(), entry.max(), entry.allowedValues());
	}

	/**
	 * Checks a text against a type, an optional range given as numbers in text form, and the allowed constants of an
	 * enum.
	 *
	 * @param min  the smallest number, null or not a number for none
	 * @param max  the largest number, null or not a number for none
	 */
	public static Status check(ValueType type, String text, @Nullable String min, @Nullable String max, List<String> allowedValues) {
		if(text.length() > MAX_TEXT_LENGTH)
			return Status.TOO_LONG;
		switch(type) {
			case STRING:
				return Status.OK;
			case BOOLEAN:
				String lowerCase = text.trim().toLowerCase(Locale.ROOT);
				return lowerCase.equals("true") || lowerCase.equals("false") ? Status.OK : Status.NOT_ALLOWED;
			case ENUM:
				return findConstant(allowedValues, text) == null ? Status.NOT_ALLOWED : Status.OK;
			default:
				break;
		}
		String trimmed = text.trim();
		if(trimmed.isEmpty())
			return Status.EMPTY;
		BigDecimal number = parseNumber(type, trimmed);
		if(number == null)
			return Status.NOT_A_NUMBER;
		BigDecimal lowest = parseBound(min);
		BigDecimal highest = parseBound(max);
		if(lowest != null && number.compareTo(lowest) < 0 || highest != null && number.compareTo(highest) > 0)
			return Status.OUT_OF_RANGE;
		return Status.OK;
	}

	/**
	 * Checks a text as a whole number in a range, as used by the bonus boxes of players and teams.
	 */
	public static Status checkInt(String text, int min, int max) {
		return check(ValueType.INT, text, Integer.toString(min), Integer.toString(max), List.of());
	}

	/**
	 * Parses a whole number of a box that {@link #checkInt} accepted.
	 */
	public static int parseInt(String text) {
		return Integer.parseInt(text.trim());
	}

	@Nullable
	private static BigDecimal parseNumber(ValueType type, String trimmed) {
		try {
			switch(type) {
				case INT:
					return BigDecimal.valueOf(Integer.parseInt(trimmed));
				case LONG:
					return BigDecimal.valueOf(Long.parseLong(trimmed));
				case DOUBLE:
					if(!DECIMAL.matcher(trimmed).matches() || !Double.isFinite(Double.parseDouble(trimmed)))
						return null;
					return new BigDecimal(trimmed);
				default:
					return null;
			}
		} catch(NumberFormatException e) {
			return null;
		}
	}

	@Nullable
	private static BigDecimal parseBound(@Nullable String bound) {
		if(bound == null)
			return null;
		try {
			return new BigDecimal(bound.trim());
		} catch(NumberFormatException e) {
			return null;//"Infinity" and the like: no bound
		}
	}

	@Nullable
	private static String findConstant(List<String> allowedValues, String text) {
		String trimmed = text.trim();
		for(String allowed : allowedValues)
			if(allowed.equalsIgnoreCase(trimmed))
				return allowed;
		return null;
	}

	/**
	 * Gets the value that the toml has saved for an option: the pending value of a restart-only option that has one, else
	 * the value the server runs with. This is what the edit box and the buttons start from.
	 */
	public static String getSavedValue(AdminConfigEntry entry) {
		return entry.pendingValue() != null ? entry.pendingValue() : entry.value();
	}

	/**
	 * Checks whether two texts are the same value of a type: numbers by their value (5 and 5.0), booleans and enum
	 * constants without regard to the case, strings exactly.
	 */
	public static boolean isSameValue(ValueType type, String first, String second) {
		switch(type) {
			case STRING:
				return first.equals(second);
			case BOOLEAN:
			case ENUM:
				return first.trim().equalsIgnoreCase(second.trim());
			default:
				BigDecimal firstNumber = parseNumber(type, first.trim());
				BigDecimal secondNumber = parseNumber(type, second.trim());
				if(firstNumber == null || secondNumber == null)
					return first.trim().equals(second.trim());
				return firstNumber.compareTo(secondNumber) == 0;
		}
	}

	/**
	 * Checks whether the Save button of an option is enabled for the text in its box: the text is valid and not the value
	 * that is saved already.
	 */
	public static boolean canSave(AdminConfigEntry entry, String text) {
		return check(entry, text) == Status.OK && !isSameValue(entry.type(), text, getSavedValue(entry));
	}

	/**
	 * Gets the text to send for a text that {@link #check} accepted: numbers trimmed, boolean and enum in the exact
	 * spelling the server uses, strings as they are.
	 */
	public static String normalise(AdminConfigEntry entry, String text) {
		switch(entry.type()) {
			case STRING:
				return text;
			case BOOLEAN:
				return text.trim().toLowerCase(Locale.ROOT);
			case ENUM:
				String constant = findConstant(entry.allowedValues(), text);
				return constant == null ? text.trim() : constant;
			default:
				return text.trim();
		}
	}

	/**
	 * Gets the value that a toggle (boolean) or cycle (enum) button sends when it is pressed: the opposite of the saved
	 * boolean, or the next (previous) allowed constant after the saved one. Null for the other types and for an enum
	 * without allowed constants.
	 */
	@Nullable
	public static String getNextValue(AdminConfigEntry entry, boolean backwards) {
		String saved = getSavedValue(entry);
		if(entry.type() == ValueType.BOOLEAN)
			return saved.trim().equalsIgnoreCase("true") ? "false" : "true";
		if(entry.type() != ValueType.ENUM || entry.allowedValues().isEmpty())
			return null;
		List<String> allowed = entry.allowedValues();
		int index = -1;
		for(int i = 0; i < allowed.size(); i++)
			if(allowed.get(i).equalsIgnoreCase(saved.trim()))
				index = i;
		if(index < 0)
			return allowed.get(backwards ? allowed.size() - 1 : 0);
		int step = backwards ? allowed.size() - 1 : 1;
		return allowed.get((index + step) % allowed.size());
	}

}
