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

package xaero.pac.client.gui.party;

import net.minecraft.client.Minecraft;
import xaero.pac.client.command.util.CommandUtil;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.server.parties.command.PartyCommandRegister;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Builds the party commands that the party screen sends. Every builder returns null instead of a command when an
 * argument could change the structure of the command, so that no free text ends up in a command by accident.
 */
public final class PartyScreenCommands {

	private static final String PREFIX = PartyCommandRegister.COMMAND_PREFIX + " ";

	private PartyScreenCommands() {
	}

	@Nullable
	public static String create(@Nullable String partyName) {
		if(partyName == null || partyName.isEmpty())
			return PREFIX + "create";
		if(!partyName.equals(partyName.trim()) || !PartyRules.isValidPartyName(partyName))
			return null;
		return PREFIX + "create " + partyName;
	}

	public static String join(UUID partyId) {
		return PREFIX + "join " + partyId;
	}

	@Nullable
	public static String invite(String playerName) {
		return PartyRules.isSafePlayerName(playerName) ? PREFIX + "member invite " + playerName : null;
	}

	/**
	 * The kick command also cancels an invitation when the name is the one of an invited player.
	 */
	@Nullable
	public static String kick(String playerName) {
		return PartyRules.isSafePlayerName(playerName) ? PREFIX + "member kick " + playerName : null;
	}

	@Nullable
	public static String rank(PartyMemberRank rank, String playerName) {
		return PartyRules.isSafePlayerName(playerName) ? PREFIX + "member rank " + rank.name() + " " + playerName : null;
	}

	/**
	 * The transfer command only has an effect with the confirm literal, the screen asks for the confirmation itself.
	 */
	@Nullable
	public static String transferConfirmed(String playerName) {
		return PartyRules.isSafePlayerName(playerName) ? PREFIX + "transfer " + playerName + " confirm" : null;
	}

	public static String leave() {
		return PREFIX + "leave";
	}

	/**
	 * The destroy command only has an effect with the confirm literal, the screen asks for the confirmation itself.
	 */
	public static String destroyConfirmed() {
		return PREFIX + "destroy confirm";
	}

	public static void send(Minecraft minecraft, @Nullable String command) {
		if(command == null || minecraft.player == null)
			return;
		CommandUtil.sendCommand(minecraft, command);
	}

}
