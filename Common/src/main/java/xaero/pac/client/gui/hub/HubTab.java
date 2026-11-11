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

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The pages of the hub screen, in the order of the sidebar. A page is only shown while {@link HubTabs#isVisible} says so
 * (the rule itself is {@link HubNavigation#isVisible}).
 */
public enum HubTab {

	OVERVIEW("gui.xaero_pac_hub_tab_overview", Items.COMPASS),
	PARTY("gui.xaero_pac_hub_tab_party", Items.WHITE_BANNER),
	CLAIMS("gui.xaero_pac_hub_tab_claims", Items.MAP),
	TEAM("gui.xaero_pac_hub_tab_team", Items.SHIELD),
	SETTINGS("gui.xaero_pac_hub_tab_settings", Items.COMPARATOR),
	ADMIN("gui.xaero_pac_hub_tab_admin", Items.COMMAND_BLOCK);

	private final Component label;
	private final Item icon;

	HubTab(String labelKey, Item icon) {
		this.label = Component.translatable(labelKey);
		this.icon = icon;
	}

	public Component getLabel() {
		return label;
	}

	/**
	 * Gets the item that the sidebar shows as the icon of the page.
	 */
	public Item getIcon() {
		return icon;
	}

}
