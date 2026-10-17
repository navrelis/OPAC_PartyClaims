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

package xaero.pac.client.teamclaims;

import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.common.packet.teamclaims.AdminPlayerBonus;
import xaero.pac.common.packet.teamclaims.ServerboundAdminConfigSetPacket;
import xaero.pac.common.packet.teamclaims.ServerboundAdminPlayerBonusSetPacket;
import xaero.pac.common.packet.teamclaims.ServerboundAdminTeamBonusSetPacket;
import xaero.pac.common.packet.teamclaims.ServerboundTeamClaimsRequestPacket;
import xaero.pac.common.packet.teamclaims.ServerboundTeamClaimsRequestPacket.Kind;

import java.util.UUID;

/**
 * [Team Claims] What the hub screen sends to the server for the Team and Admin tabs. The answers land in
 * {@link TeamClaimsClientData#INSTANCE}. The server ignores admin requests of players without the permission, and a
 * config snapshot request more often than every {@code 10} ticks.
 */
public final class TeamClaimsClientRequests {

	private TeamClaimsClientRequests() {
	}

	/** Asks for the Team tab information of the own team (answer: team info and budget). */
	public static void requestTeamInfo() {
		send(new ServerboundTeamClaimsRequestPacket(Kind.TEAM_INFO));
	}

	/** Asks for all scalar options of both server configs (answer: config snapshot pages). */
	public static void requestAdminConfigs() {
		send(new ServerboundTeamClaimsRequestPacket(Kind.ADMIN_CONFIGS));
	}

	/** Asks for the list of all teams (answer: teams). */
	public static void requestAdminTeams() {
		send(new ServerboundTeamClaimsRequestPacket(Kind.ADMIN_TEAMS));
	}

	/** Looks up a player by name or UUID (answer: player summary, or a result with an error). */
	public static void requestAdminPlayer(String nameOrUuid) {
		String query = nameOrUuid.trim();
		if(query.length() > ServerboundTeamClaimsRequestPacket.MAX_QUERY_LENGTH)
			query = query.substring(0, ServerboundTeamClaimsRequestPacket.MAX_QUERY_LENGTH);
		send(new ServerboundTeamClaimsRequestPacket(Kind.ADMIN_PLAYER, query));
	}

	/**
	 * Sets a server config option (answer: a result and, on success, the updated entry).
	 *
	 * @param configId  {@code opac} or {@code teamclaims}
	 * @param path      the full path of the option, as in its entry
	 * @param value     the new value as text, in the form of the entry's values
	 */
	public static void setAdminConfig(String configId, String path, String value) {
		send(new ServerboundAdminConfigSetPacket(configId, path, value));
	}

	/** Sets the bonus team claims ({@code forceloads} false) or bonus team forceloads of a team (answer: a result and the teams). */
	public static void setTeamBonus(UUID partyId, boolean forceloads, int value) {
		send(new ServerboundAdminTeamBonusSetPacket(partyId, forceloads, value));
	}

	/** Sets a bonus option of a player (answer: a result and the fresh player summary). */
	public static void setPlayerBonus(UUID playerId, AdminPlayerBonus bonus, int value) {
		send(new ServerboundAdminPlayerBonusSetPacket(playerId, bonus, value));
	}

	private static void send(Object packet) {
		OpenPartiesAndClaims.INSTANCE.getPacketHandler().sendToServer(packet);
	}

}
