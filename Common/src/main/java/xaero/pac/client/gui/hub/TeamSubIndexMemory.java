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

import xaero.pac.common.server.claims.TeamClaimsIntegration;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Remembers the sub-config indices of the team sub-configs that the client has seen as the sub-config in use, because
 * that is the only time that the server tells the client the index of a sub-config. It forgets everything when the
 * session (the connection to a server) changes, since indices are not comparable between servers.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class TeamSubIndexMemory {

	private Object session;
	private final Map<String, Integer> indices = new HashMap<>();

	/**
	 * Notes the sub-config that is currently in use. Only the sub-configs of teams are remembered.
	 *
	 * @param session  an object that is the same for as long as the connection to the server lasts, null for none
	 * @param subId  the ID of the sub-config in use
	 * @param subIndex  the index of the sub-config in use
	 */
	public void observe(@Nullable Object session, @Nullable String subId, int subIndex) {
		resetOnNewSession(session);
		if(TeamClaimsIntegration.isTeamSubId(subId))
			indices.put(subId, subIndex);
	}

	/**
	 * Gets the remembered index of a team sub-config.
	 *
	 * @return the index, null if it has not been seen in this session
	 */
	@Nullable
	public Integer get(@Nullable Object session, @Nullable String subId) {
		resetOnNewSession(session);
		return subId == null ? null : indices.get(subId);
	}

	private void resetOnNewSession(@Nullable Object session) {
		if(this.session != session) {
			this.session = session;
			indices.clear();
		}
	}

}
