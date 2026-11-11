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
import java.util.ArrayList;
import java.util.List;

/**
 * The rules of the navigation of the hub: which pages the sidebar shows, which page the hub opens with, and how wide the
 * sidebar is (with labels, or only the icons when the screen is narrow).
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class HubNavigation {

	/**
	 * The space between the sidebar and the edges of the screen and its entries.
	 */
	public static final int PANEL_PADDING = 3;
	/**
	 * The space in an entry to the left of the icon.
	 */
	public static final int ENTRY_PADDING = 4;
	/**
	 * The size of the item icons.
	 */
	public static final int ICON_SIZE = 16;
	/**
	 * The space between the icon and the label of an entry.
	 */
	public static final int ICON_GAP = 5;
	/**
	 * The space in an entry to the right of the label.
	 */
	public static final int LABEL_PADDING = 6;
	/**
	 * The most that the sidebar takes with labels; longer labels are cut.
	 */
	public static final int MAX_EXPANDED_WIDTH = 120;
	/**
	 * The width of the content area below which the sidebar shows only the icons. 360 keeps the lists of the Admin page
	 * and the two columns of the Overview and Team pages usable at GUI scale 3 on a 1280 x 720 window (426 wide).
	 */
	public static final int MIN_CONTENT_WIDTH = 360;
	/**
	 * The width of the sidebar with only the icons.
	 */
	public static final int COLLAPSED_WIDTH = 2 * PANEL_PADDING + 2 * ENTRY_PADDING + ICON_SIZE;

	private HubNavigation() {
	}

	/**
	 * Checks whether a page is shown in the sidebar. Overview, Party, Claims and Settings always are.
	 *
	 * @param teamVisible  whether the player is in a party that has a team
	 * @param adminVisible  whether the server allows the player the admin menu
	 */
	public static boolean isVisible(HubTab tab, boolean teamVisible, boolean adminVisible) {
		return switch(tab) {
			case OVERVIEW, PARTY, CLAIMS, SETTINGS -> true;
			case TEAM -> teamVisible;
			case ADMIN -> adminVisible;
		};
	}

	/**
	 * Gets the pages that the sidebar shows, in its order.
	 */
	public static List<HubTab> getVisibleTabs(boolean teamVisible, boolean adminVisible) {
		List<HubTab> tabs = new ArrayList<>();
		for(HubTab tab : HubTab.values())
			if(isVisible(tab, teamVisible, adminVisible))
				tabs.add(tab);
		return tabs;
	}

	/**
	 * Gets the page that the hub opens with: the Overview the first time in a game session, afterwards the page that was
	 * open the last time, or the Overview again if that page is not shown any more.
	 *
	 * @param lastTab  the page that was open the last time, null if the hub has not been open in this session
	 */
	public static HubTab getOpeningTab(@Nullable HubTab lastTab, boolean teamVisible, boolean adminVisible) {
		if(lastTab == null || !isVisible(lastTab, teamVisible, adminVisible))
			return HubTab.OVERVIEW;
		return lastTab;
	}

	/**
	 * Gets the width of the sidebar with labels.
	 *
	 * @param widestLabel  the width in pixels of the longest label
	 */
	public static int getExpandedWidth(int widestLabel) {
		int width = 2 * PANEL_PADDING + ENTRY_PADDING + ICON_SIZE + ICON_GAP + Math.max(0, widestLabel) + LABEL_PADDING;
		return Math.min(MAX_EXPANDED_WIDTH, width);
	}

	/**
	 * Checks whether the sidebar shows only the icons, which it does when the labels would leave less than
	 * {@link #MIN_CONTENT_WIDTH} for the page.
	 *
	 * @param screenWidth  the width of the screen in GUI pixels
	 * @param widestLabel  the width in pixels of the longest label
	 */
	public static boolean isSidebarCollapsed(int screenWidth, int widestLabel) {
		return screenWidth - getExpandedWidth(widestLabel) < MIN_CONTENT_WIDTH;
	}

	/**
	 * Gets the width of the sidebar on a screen.
	 */
	public static int getSidebarWidth(int screenWidth, int widestLabel) {
		return isSidebarCollapsed(screenWidth, widestLabel) ? COLLAPSED_WIDTH : getExpandedWidth(widestLabel);
	}

}
