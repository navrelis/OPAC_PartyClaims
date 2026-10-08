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

import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.player.config.PlayerConfigStringOptionSpec;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * Client side mirror of the rank requirements that the party commands check on the server.
 * The server always has the final word, these rules only decide which actions the party screen offers.
 */
public final class PartyRules {

	/**
	 * Player names that are safe to send as a single word argument of a party command.
	 */
	private static final Pattern SAFE_PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_.\\-]{1,32}$");

	private PartyRules() {
	}

	public static boolean isSafePlayerName(@Nullable String name) {
		return name != null && SAFE_PLAYER_NAME.matcher(name).matches();
	}

	public static int getPartyNameMaxLength() {
		return ((PlayerConfigStringOptionSpec) PlayerConfigOptions.PARTY_NAME).getMaxLength();
	}

	/**
	 * Checks whether a party name is accepted by the validator of the party name player config option.
	 * An empty name is valid and means that the default party name is used.
	 */
	public static boolean isValidPartyName(String name) {
		return ((PlayerConfigStringOptionSpec) PlayerConfigOptions.PARTY_NAME).getClientSideValidatorInternal().test(null, name);
	}

	/**
	 * Same requirement as the invite command and the kick command, which is also used for cancelling invites.
	 */
	public static boolean canInviteAndKick(IPartyMemberAPI local) {
		return local.isOwner() || local.getRank().ordinal() >= PartyMemberRank.MODERATOR.ordinal();
	}

	/**
	 * Same requirement as the rank command, the transfer command, the destroy command and the party name option.
	 */
	public static boolean canChangeRanks(IPartyMemberAPI local) {
		return local.isOwner() || local.getRank().ordinal() >= PartyMemberRank.ADMIN.ordinal();
	}

	public static boolean canRename(IPartyMemberAPI local) {
		return local.isOwner();
	}

	public static boolean canKick(IPartyMemberAPI local, IPartyMemberAPI target) {
		if(!canInviteAndKick(local) || target.isOwner() || target.getUUID().equals(local.getUUID()))
			return false;
		return local.isOwner() || target.getRank().ordinal() <= local.getRank().ordinal();
	}

	public static boolean canTransferOwnership(IPartyMemberAPI local, IPartyMemberAPI target) {
		return local.isOwner() && !target.isOwner();
	}

	/**
	 * Checks whether a rank is one that the local member is allowed to give to the target member.
	 */
	public static boolean canAssignRank(IPartyMemberAPI local, IPartyMemberAPI target, PartyMemberRank rank) {
		if(!canChangeRanks(local) || target.isOwner() || target.getUUID().equals(local.getUUID()))
			return false;
		if(local.isOwner())
			return true;
		return target.getRank().ordinal() < local.getRank().ordinal() && rank.ordinal() < local.getRank().ordinal();
	}

	/**
	 * Gets the rank that a single click on the rank button of a member should assign: the next rank, in the order of the
	 * rank enum, that the local member is allowed to assign. Wraps around after the last one, so every assignable
	 * rank can be reached by clicking repeatedly.
	 *
	 * @return the next rank, null if the rank of the target cannot be changed by the local member
	 */
	@Nullable
	public static PartyMemberRank getNextRank(IPartyMemberAPI local, IPartyMemberAPI target) {
		PartyMemberRank[] ranks = PartyMemberRank.values();
		PartyMemberRank current = target.getRank();
		for(int offset = 1; offset < ranks.length; offset++) {
			PartyMemberRank candidate = ranks[(current.ordinal() + offset) % ranks.length];
			if(canAssignRank(local, target, candidate))
				return candidate;
		}
		return null;
	}

}
