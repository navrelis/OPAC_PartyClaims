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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the row of tab buttons at the top of every screen of the hub. The button of the open tab is inactive.
 */
public final class HubTabBar {

	/**
	 * The Y of the tab buttons.
	 */
	public static final int TOP = 3;
	/**
	 * The height of the tab buttons.
	 */
	public static final int HEIGHT = 16;
	/**
	 * The Y where the content of a tab starts, below the tab bar.
	 */
	public static final int CONTENT_TOP = TOP + HEIGHT + 5;

	private static final int GAP = 2;
	private static final int PADDING = 20;
	private static final int MIN_WIDTH = 56;
	private static final int SCREEN_MARGIN = 4;

	private HubTabBar() {
	}

	/**
	 * Creates the tab buttons, centred at the top of a screen. The buttons share the width of the screen if the labels do
	 * not fit.
	 *
	 * @param screenWidth  the width of the screen
	 * @param active  the tab that is open
	 * @param escape  the escape screen to hand on to the screen of the tab that gets opened
	 * @param parent  the parent screen to hand on to the screen of the tab that gets opened
	 */
	public static List<Button> create(Minecraft minecraft, int screenWidth, HubTab active, Screen escape, Screen parent) {
		Font font = minecraft.font;
		List<HubTab> tabs = HubTabs.getVisibleTabs();
		int[] widths = new int[tabs.size()];
		int total = GAP * (tabs.size() - 1);
		for(int i = 0; i < widths.length; i++) {
			widths[i] = Math.max(MIN_WIDTH, font.width(tabs.get(i).getLabel()) + PADDING);
			total += widths[i];
		}
		int available = screenWidth - 2 * SCREEN_MARGIN;
		if(total > available && !tabs.isEmpty()) {
			int each = Math.max(1, (available - GAP * (tabs.size() - 1)) / tabs.size());
			for(int i = 0; i < widths.length; i++)
				widths[i] = each;
			total = each * tabs.size() + GAP * (tabs.size() - 1);
		}
		List<Button> buttons = new ArrayList<>();
		int x = (screenWidth - total) / 2;
		for(int i = 0; i < widths.length; i++) {
			HubTab tab = tabs.get(i);
			Button button = Button.builder(tab.getLabel(), b -> HubTabs.open(minecraft, tab, escape, parent))
					.bounds(x, TOP, widths[i], HEIGHT).build();
			button.active = tab != active;
			buttons.add(button);
			x += widths[i] + GAP;
		}
		return buttons;
	}

}
