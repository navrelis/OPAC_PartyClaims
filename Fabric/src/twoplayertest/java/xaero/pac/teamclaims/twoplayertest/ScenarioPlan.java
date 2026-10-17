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

package xaero.pac.teamclaims.twoplayertest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [Team Claims] Every assertion the two client drivers report, by id, in the order they run. A driver must report each
 * of its ids exactly once (passed, failed or skipped with a reason); the script holds the same list in
 * {@code tools/twoplayertest/expected-assertions.txt} and counts an id that nobody reported as failed, so a driver that
 * stops early can never look green. The self-check gametest compares the two lists.
 * <p>
 * No Minecraft classes here, so the server side (self-check) can read it too.
 */
public final class ScenarioPlan {

	public static final String GUI_SCALE_LOW = "gui2";
	public static final String GUI_SCALE_HIGH = "gui4";

	private static final Map<String, String> PLAYER1 = new LinkedHashMap<>();
	private static final Map<String, String> PLAYER2 = new LinkedHashMap<>();

	// ---- Player1 (op, party owner) ----
	public static final String P1_JOIN = p1("p1.join", "Player1 joins the dedicated server and the server has OPAC (handshake)");
	public static final String P1_PRECONDITIONS = p1("p1.server.preconditions", "Server runs with teamClaimsMinMembers = 2 and maxSingleClaimActionSize = 4 from the test toml; Player1 is op, Player2 is not");
	public static final String P1_KEY_OPENS = p1("p1.key-p.opens-hub", "P (default key bindings, Social Interactions also on P) opens the hub");
	public static final String P1_KEY_CLOSES = p1("p1.key-p.closes-hub", "P closes the open hub");
	public static final String P1_KEY_TYPING = p1("p1.key-p.typing-keeps-hub", "P typed into a focused edit box of the hub does not close it and goes into the box");
	public static final String P1_PARTY_CREATE = p1("p1.party.create", "Create button of the Party tab creates the party \"TPTParty\" owned by Player1 (server)");
	public static final String P1_PARTY_FAKE_MEMBER = p1("p1.party.fake-member", "Server adds the offline member \"Alice\" (sorts between the owner and Player2, so a re-sort by rank would move rows)");
	public static final String P1_PARTY_INVITE = p1("p1.party.invite", "Invite box + Invite button invite Player2 (server)");
	public static final String P1_PARTY_RANK_CLICK = p1("p1.party.rank-click-changes-clicked-member", "A click on the rank button of Player2's row changes Player2's rank (server) and nobody else's");
	public static final String P1_PARTY_ROWS_STABLE = p1("p1.party.rows-stay-in-place", "After the rank change the rows keep their order and the row under the clicked point is still Player2");
	public static final String P1_CLAIMS_CLAIM_PRIVATE = p1("p1.claims.claim-private", "Claims tab: select the own chunk, Claim as Private, Claim button: server has a private claim of Player1 there");
	public static final String P1_CLAIMS_UNCLAIM = p1("p1.claims.unclaim", "Claims tab: Unclaim button: server has no claim there any more");
	public static final String P1_CLAIMS_TOGGLE_TEAM = p1("p1.claims.toggle-team", "Claims tab: the Private/Team toggle switches to Team (button text and the player config on the client)");
	public static final String P1_CLAIMS_CLAIM_TEAM = p1("p1.claims.claim-team", "Claims tab: Claim with Team selected: server has a team claim (team flag) there");
	public static final String P1_CLAIMS_OVERSIZE = p1("p1.claims.oversize-disables-actions", "Claims tab: a 3x3 selection (9 > 4 chunks) disables Claim/Unclaim/Forceload/Unforceload; a 2x1 selection enables them");
	public static final String P1_TEAM_VISIBLE = p1("p1.team.tab-visible", "Team tab is in the tab bar of the team owner and opens");
	public static final String P1_TEAM_ROLE = p1("p1.team.owner-changes-role", "Team tab: the owner's click on the Claim role button changes the team's claim role (server)");
	public static final String P1_ADMIN_VISIBLE = p1("p1.admin.tab-visible", "Admin tab is in the tab bar of Player1 (op) and opens");
	public static final String P1_ADMIN_LIVE = p1("p1.admin.live-option", "Admin tab: teamClaims.teamClaimsBase 500 -> 777 takes effect at once (running value and team claim limit on the server)");
	public static final String P1_ADMIN_RESTART = p1("p1.admin.restart-option-pending", "Admin tab: serverConfig.parties.maxPartyInvites 16 -> 17 is shown pending, saved to the file, running value stays 16");
	public static final String P1_ADMIN_TEAM_BONUS = p1("p1.admin.team-bonus", "Admin tab, Teams: bonus team claims 50 raise the team claim limit by 50 (server)");
	public static final String P1_ADMIN_PLAYER_BONUS = p1("p1.admin.player-bonus-lookup", "Admin tab, Players: look up Player2, bonus claims 25 (server player config)");
	public static final String P1_ADMIN_SUGGESTION = p1("p1.admin.players-suggestion-click", "Admin tab, Players: a click on an online-player suggestion (it rebuilds the buttons during its own click) does not crash and looks the player up");

	// ---- Player2 (not op, party member) ----
	public static final String P2_JOIN = p2("p2.join", "Player2 joins the dedicated server and the server has OPAC (handshake)");
	public static final String P2_KEY_OPENS = p2("p2.key-p.opens-hub", "P (default key bindings) opens the hub");
	public static final String P2_KEY_CLOSES = p2("p2.key-p.closes-hub", "P closes the open hub");
	public static final String P2_KEY_TYPING = p2("p2.key-p.typing-keeps-hub", "P typed into a focused edit box of the hub does not close it and goes into the box");
	public static final String P2_PARTY_ACCEPT = p2("p2.party.accept", "Party tab: Accept on the received invitation makes Player2 a member (server)");
	public static final String P2_TEAM_READ_ONLY = p2("p2.team.member-read-only", "Team tab: Player2 (member) sees the owner's new claim role and every role button is inactive");
	public static final String P2_TEAM_CLICK = p2("p2.team.member-click-no-change", "Team tab: a click on an inactive role button changes nothing on the server");
	public static final String P2_ADMIN_HIDDEN = p2("p2.admin.tab-hidden", "No Admin tab for Player2 (not op): the server flag is off and the tab bar has no Admin button");
	public static final String P2_ADMIN_CRAFTED = p2("p2.admin.crafted-request-rejected", "Admin packets sent directly by Player2 change nothing, get no config data, and the server logs the rejection warning");

	public static final List<String> SHOT_TABS_PLAYER1 = List.of("party", "claims", "team", "admin");
	public static final List<String> SHOT_TABS_PLAYER2 = List.of("party", "claims", "team");

	static {
		for(String tab : SHOT_TABS_PLAYER1)
			for(String scale : List.of(GUI_SCALE_LOW, GUI_SCALE_HIGH))
				p1(shotId("p1", tab, scale), "Screenshot of the " + tab + " tab at GUI scale " + scale.substring(3));
		for(String tab : SHOT_TABS_PLAYER2)
			for(String scale : List.of(GUI_SCALE_LOW, GUI_SCALE_HIGH))
				p2(shotId("p2", tab, scale), "Screenshot of the " + tab + " tab at GUI scale " + scale.substring(3));
	}

	private ScenarioPlan() {
	}

	public static String shotId(String prefix, String tab, String scale) {
		return prefix + ".shots." + tab + "." + scale;
	}

	private static String p1(String id, String description) {
		return add(PLAYER1, id, description);
	}

	private static String p2(String id, String description) {
		return add(PLAYER2, id, description);
	}

	private static String add(Map<String, String> plan, String id, String description) {
		if(PLAYER1.containsKey(id) || PLAYER2.containsKey(id))
			throw new IllegalStateException("duplicate assertion id " + id);
		plan.put(id, description);
		return id;
	}

	/**
	 * Gets the ids and descriptions of a player's assertions, in order.
	 */
	public static Map<String, String> of(String player) {
		return Collections.unmodifiableMap(TwoPlayerTest.PLAYER1.equals(player) ? PLAYER1 : PLAYER2);
	}

	public static String describe(String id) {
		String description = PLAYER1.get(id);
		return description != null ? description : PLAYER2.get(id);
	}

	/**
	 * Gets all ids of both players, Player1's first.
	 */
	public static List<String> allIds() {
		List<String> ids = new ArrayList<>(PLAYER1.keySet());
		ids.addAll(PLAYER2.keySet());
		return ids;
	}

}
