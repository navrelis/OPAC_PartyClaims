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
import java.util.ArrayList;
import java.util.List;

/**
 * [Team Claims] Shared reading/writing helpers of the Team Claims packets (ids 104 and up). Every reader throws on bad
 * input; the codecs catch that and return null, which drops the packet.
 */
public final class TeamClaimsPacketUtil {

	private TeamClaimsPacketUtil() {
	}

	/** A var-int that must lie in {@code [0, max]}. */
	public static int readCount(FriendlyByteBuf input, int max) {
		int value = input.readVarInt();
		if(value < 0 || value > max)
			throw new IllegalArgumentException("count out of range: " + value);
		return value;
	}

	/** An enum constant written by {@link #writeEnum}. */
	public static <E extends Enum<E>> E readEnum(FriendlyByteBuf input, Class<E> type) {
		E[] values = type.getEnumConstants();
		int ordinal = input.readByte();
		if(ordinal < 0 || ordinal >= values.length)
			throw new IllegalArgumentException("unknown " + type.getSimpleName() + " " + ordinal);
		return values[ordinal];
	}

	public static void writeEnum(FriendlyByteBuf output, Enum<?> value) {
		output.writeByte(value.ordinal());
	}

	/** A string of at most {@code maxLength} characters, cut to that length when writing. */
	public static void writeString(FriendlyByteBuf output, @Nullable String value, int maxLength) {
		String nonNull = value == null ? "" : value;
		output.writeUtf(nonNull.length() > maxLength ? nonNull.substring(0, maxLength) : nonNull, maxLength);
	}

	public static String readString(FriendlyByteBuf input, int maxLength) {
		return input.readUtf(maxLength);
	}

	public static void writeNullableString(FriendlyByteBuf output, @Nullable String value, int maxLength) {
		output.writeBoolean(value != null);
		if(value != null)
			writeString(output, value, maxLength);
	}

	@Nullable
	public static String readNullableString(FriendlyByteBuf input, int maxLength) {
		return input.readBoolean() ? readString(input, maxLength) : null;
	}

	/** At most {@code maxCount} strings of at most {@code maxLength} characters (more are cut off when writing). */
	public static void writeStrings(FriendlyByteBuf output, List<String> values, int maxCount, int maxLength) {
		int count = Math.min(values.size(), maxCount);
		output.writeVarInt(count);
		for(int i = 0; i < count; i++)
			writeString(output, values.get(i), maxLength);
	}

	public static List<String> readStrings(FriendlyByteBuf input, int maxCount, int maxLength) {
		int count = readCount(input, maxCount);
		List<String> result = new ArrayList<>(count);
		for(int i = 0; i < count; i++)
			result.add(readString(input, maxLength));
		return List.copyOf(result);
	}

	/** Throws if anything is left in the buffer. */
	public static void requireEnd(FriendlyByteBuf input) {
		if(input.readableBytes() > 0)
			throw new IllegalArgumentException("trailing bytes");
	}

}
