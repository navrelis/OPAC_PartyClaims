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

import net.minecraft.network.chat.Component;
import xaero.pac.common.parties.party.member.PartyMemberRank;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Immutable description of a row of the party screen list. The screen compares the rows of the current client party
 * data with the rows that are currently displayed and only rebuilds the list when they differ.
 */
public sealed interface PartyRow {

	/**
	 * A member of the party.
	 *
	 * @param nextRank  the rank that the rank button assigns, null if the local player cannot change this rank
	 * @param canKick  true if the local player can remove this member
	 * @param canTransfer  true if the local player can make this member the owner
	 */
	record Member(UUID id, String name, PartyMemberRank rank, boolean owner, boolean self,
				  @Nullable PartyMemberRank nextRank, boolean canKick, boolean canTransfer) implements PartyRow {
	}

	/**
	 * A player invited to the party of the local player.
	 *
	 * @param canCancel  true if the local player can cancel the invitation
	 */
	record SentInvite(UUID id, String name, boolean canCancel) implements PartyRow {
	}

	/**
	 * An invitation to a party that was sent to the local player.
	 */
	record ReceivedInvite(UUID partyId, String partyName, String ownerName) implements PartyRow {
	}

	record Header(Component title) implements PartyRow {
	}

	record Message(Component text) implements PartyRow {
	}

}
