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

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.gui.party.PartyScreen;
import xaero.pac.client.parties.party.IClientPartyStorage;
import xaero.pac.client.teamclaims.TeamClaimsClientData;
import xaero.pac.common.platform.Services;

import java.util.ArrayList;
import java.util.List;

/**
 * The tabs of the hub screen: which of them exist right now, which screen each one opens and which one the hub opens
 * with. Every tab is a screen of its own that draws the tab bar ({@link HubTabBar}), so opening a tab replaces the screen.
 * <p>
 * To add a tab or replace the placeholder of one: make a screen that extends {@link HubTabScreen} (or, like the
 * {@link PartyScreen}, adds the tab bar itself), return it from {@link #create} and decide in {@link #isVisible} when the
 * tab shows.
 */
public final class HubTabs {

	private static HubTab lastTab = HubTab.PARTY;

	private HubTabs() {
	}

	/**
	 * Checks whether the player may see the Admin tab, which is the flag that the server sends. The server checks every
	 * admin request itself, so this only decides whether the tab is shown.
	 */
	public static boolean isAdminTabVisible() {
		return TeamClaimsClientData.INSTANCE.isAdminMenuAllowed();
	}

	/**
	 * Checks whether the player is in a party that has a team, which is what the Team tab is for. The team information that
	 * the server sends decides; before it has arrived, the budget that the server sends with the party information does.
	 */
	public static boolean isTeamTabVisible() {
		IClientPartyStorage<?, ?, ?> partyStorage = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClientPartyStorage();
		return TeamHubLogic.isTeamTabVisible(partyStorage.getParty() != null, TeamClaimsClientData.INSTANCE.getTeamInfo(), partyStorage.getBudget());
	}

	/**
	 * Checks whether a tab is currently shown in the tab bar.
	 */
	public static boolean isVisible(HubTab tab) {
		return switch(tab) {
			case PARTY, CLAIMS -> true;
			case TEAM -> isTeamTabVisible();
			case ADMIN -> isAdminTabVisible();
		};
	}

	/**
	 * Gets the tabs that are currently shown, in the order of the tab bar.
	 */
	public static List<HubTab> getVisibleTabs() {
		List<HubTab> tabs = new ArrayList<>();
		for(HubTab tab : HubTab.values())
			if(isVisible(tab))
				tabs.add(tab);
		return tabs;
	}

	/**
	 * Gets the tab that was open the last time in this session, the Party tab if that is not shown any more.
	 */
	public static HubTab getLastTab() {
		return isVisible(lastTab) ? lastTab : HubTab.PARTY;
	}

	/**
	 * Creates the screen of a tab and remembers the tab as the last one.
	 *
	 * @param escape  the screen to open when the hub is closed, as for every OPAC screen
	 * @param parent  the screen to open when the Back button is pressed
	 */
	public static Screen create(HubTab tab, Screen escape, Screen parent) {
		if(!isVisible(tab))
			tab = HubTab.PARTY;
		lastTab = tab;
		return switch(tab) {
			case PARTY -> new PartyScreen(escape, parent);
			case CLAIMS -> new ClaimsTabScreen(escape, parent);
			case TEAM -> new TeamTabScreen(escape, parent);
			case ADMIN -> new AdminTabScreen(escape, parent);
		};
	}

	/**
	 * Opens a tab.
	 */
	public static void open(Minecraft minecraft, HubTab tab, Screen escape, Screen parent) {
		minecraft.setScreen(create(tab, escape, parent));
	}

	/**
	 * Opens the hub with the tab that was open the last time.
	 */
	public static void openLast(Minecraft minecraft, Screen escape, Screen parent) {
		open(minecraft, getLastTab(), escape, parent);
	}

	/**
	 * Checks whether a key is the key that opens the hub, which closes it as well.
	 */
	public static boolean isHubKey(int keyCode) {
		InputConstants.Key boundKey = getHubBoundKey();
		return boundKey.getType() == InputConstants.Type.KEYSYM && keyCode == boundKey.getValue();
	}

	/**
	 * Checks whether a mouse button is bound to the key that opens the hub.
	 */
	public static boolean isHubMouseButton(int button) {
		InputConstants.Key boundKey = getHubBoundKey();
		return boundKey.getType() == InputConstants.Type.MOUSE && button == boundKey.getValue();
	}

	private static InputConstants.Key getHubBoundKey() {
		KeyMapping keyMapping = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getKeyBindings().openHubMenu;
		return Services.PLATFORM.getKeyBindingHelper().getBoundKey(keyMapping);
	}

}
