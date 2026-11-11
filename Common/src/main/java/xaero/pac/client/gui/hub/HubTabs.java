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

import javax.annotation.Nullable;
import java.util.List;

/**
 * The pages of the hub screen: which of them exist right now, which screen each one opens and which one the hub opens
 * with. Every page is a screen of its own that has the sidebar ({@link HubSidebar}), so opening a page replaces the
 * screen.
 * <p>
 * To add a page: add it to {@link HubTab}, make a screen that extends {@link HubTabScreen} (or, like the
 * {@link PartyScreen}, adds the sidebar itself), return it from {@link #create} and decide in
 * {@link HubNavigation#isVisible} when the page shows.
 */
public final class HubTabs {

	/**
	 * The page that was open the last time in this game session, null before the hub was first opened.
	 */
	@Nullable
	private static HubTab lastTab;

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
	 * Checks whether a page is currently shown in the sidebar.
	 */
	public static boolean isVisible(HubTab tab) {
		return HubNavigation.isVisible(tab, isTeamTabVisible(), isAdminTabVisible());
	}

	/**
	 * Gets the pages that are currently shown, in the order of the sidebar.
	 */
	public static List<HubTab> getVisibleTabs() {
		return HubNavigation.getVisibleTabs(isTeamTabVisible(), isAdminTabVisible());
	}

	/**
	 * Gets the page that the hub opens with: the Overview the first time in this session, afterwards the page that was
	 * open the last time (the Overview if that is not shown any more).
	 */
	public static HubTab getLastTab() {
		return HubNavigation.getOpeningTab(lastTab, isTeamTabVisible(), isAdminTabVisible());
	}

	/**
	 * Creates the screen of a page and remembers the page as the last one.
	 *
	 * @param escape  the screen to open when the hub is closed, as for every OPAC screen
	 * @param parent  the screen to open when the Back button is pressed
	 */
	public static Screen create(HubTab tab, Screen escape, Screen parent) {
		if(!isVisible(tab))
			tab = HubTab.OVERVIEW;
		lastTab = tab;
		return switch(tab) {
			case OVERVIEW -> new OverviewTabScreen(escape, parent);
			case PARTY -> new PartyScreen(escape, parent);
			case CLAIMS -> new ClaimsTabScreen(escape, parent);
			case TEAM -> new TeamTabScreen(escape, parent);
			case SETTINGS -> new SettingsTabScreen(escape, parent);
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

	/**
	 * Checks whether a key closes the hub: the hub key, or OPAC's own menu key, which opens the hub as well.
	 */
	public static boolean isCloseKey(int keyCode) {
		if(isHubKey(keyCode))
			return true;
		InputConstants.Key boundKey = getModMenuBoundKey();
		return boundKey.getType() == InputConstants.Type.KEYSYM && keyCode == boundKey.getValue();
	}

	/**
	 * Checks whether a mouse button closes the hub, see {@link #isCloseKey}.
	 */
	public static boolean isCloseMouseButton(int button) {
		if(isHubMouseButton(button))
			return true;
		InputConstants.Key boundKey = getModMenuBoundKey();
		return boundKey.getType() == InputConstants.Type.MOUSE && button == boundKey.getValue();
	}

	/**
	 * Checks whether the hub key and vanilla's Social Interactions key are bound to the same key (and key modifier). They
	 * both default to P, and vanilla only gives key presses to one mapping per key, so the hub key would never fire. Then
	 * the hub takes the presses of both keys. An unbound key is never shared.
	 */
	public static boolean isSharedWithSocialInteractions(Minecraft minecraft) {//[Team Claims]
		KeyMapping hubMapping = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getKeyBindings().openHubMenu;
		KeyMapping socialMapping = minecraft.options.keySocialInteractions;
		InputConstants.Key hubKey = Services.PLATFORM.getKeyBindingHelper().getBoundKey(hubMapping);
		if(hubKey.equals(InputConstants.UNKNOWN))
			return false;
		return hubKey.equals(Services.PLATFORM.getKeyBindingHelper().getBoundKey(socialMapping))
				&& Services.PLATFORM.getKeyBindingHelper().haveSameKeyModifier(hubMapping, socialMapping);
	}

	private static InputConstants.Key getHubBoundKey() {
		KeyMapping keyMapping = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getKeyBindings().openHubMenu;
		return Services.PLATFORM.getKeyBindingHelper().getBoundKey(keyMapping);
	}

	private static InputConstants.Key getModMenuBoundKey() {
		KeyMapping keyMapping = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getKeyBindings().openModMenu;
		return Services.PLATFORM.getKeyBindingHelper().getBoundKey(keyMapping);
	}

}
