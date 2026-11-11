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

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * The kinds of chunk that the grid of the Claims tab tells apart, besides the colour that each claim has from its owner.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public enum ClaimCategory {

	/** Nobody claimed the chunk (or the server did not synchronise the claim to this client). */
	NONE,
	/** A claim of the local player that is not a team claim. */
	OWN_PRIVATE,
	/** A claim of the local player that belongs to the team of their party. */
	OWN_TEAM,
	/** A claim of another member of the party of the local player, or a claim owned by that party. */
	PARTY,
	/** A claim of a member of an allied party. */
	ALLY,
	/** A claim of any other player. */
	OTHER,
	/** A server claim. */
	SERVER,
	/** An expired claim, which the server keeps until somebody takes it. */
	EXPIRED;

	/**
	 * Decides the category of a claimed chunk. The server and expired owners come first, then the claims owned by a party
	 * (which are the party's, whoever is the owner), then the claims of the local player, and then the other members of
	 * the party, then the allies of that party.
	 *
	 * @param serverOwner  whether the owner is the server
	 * @param expiredOwner  whether the claim is an expired one
	 * @param self  whether the owner is the local player
	 * @param partyMember  whether the owner is a member of the party of the local player
	 * @param partyOwned  whether the claim is owned by a party, as with the server option {@code partyOwnedClaims}
	 * @param team  whether the claim, if it is the local player's, uses the team sub-config
	 * @param ally  whether the owner (or the party that owns the claim) is an ally of the party of the local player
	 */
	public static ClaimCategory classify(boolean serverOwner, boolean expiredOwner, boolean self, boolean partyMember, boolean partyOwned, boolean team, boolean ally) {
		if(serverOwner)
			return SERVER;
		if(expiredOwner)
			return EXPIRED;
		if(partyOwned)
			return self || partyMember ? PARTY : ally ? ALLY : OTHER;
		if(self)
			return team ? OWN_TEAM : OWN_PRIVATE;
		if(partyMember)
			return PARTY;
		return ally ? ALLY : OTHER;
	}

	/**
	 * Tells whether the owner of a claim belongs to an allied party. The client is told the ID, the name and the default
	 * name of each ally, but not which players belong to it, so the owner is matched by name: a claim owned by a party
	 * carries the name of the party, and the default name of a party is made from the name of its owner (the same as
	 * {@code Party.getDefaultName}), which finds the owner of an ally whose claims are the owner's own.
	 *
	 * @param allyNames  the names of the allied parties
	 * @param allyDefaultNames  the default names of the allied parties
	 * @param ownerPartyName  the name of the party that owns the claim, null if no party owns it
	 * @param ownerUsername  the name of the player that owns the claim, null if unknown
	 */
	public static boolean isAllyOwner(Collection<String> allyNames, Collection<String> allyDefaultNames, @Nullable String ownerPartyName, @Nullable String ownerUsername) {
		if(ownerPartyName != null && allyNames.contains(ownerPartyName))
			return true;
		return ownerUsername != null && allyDefaultNames.contains(String.format("%s's Party", ownerUsername));
	}

}
