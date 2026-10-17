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

/**
 * The tabs of the hub screen, in the order of the tab bar. A tab is only shown while {@link HubTabs#isVisible} says so.
 */
public enum HubTab {

	PARTY("gui.xaero_pac_hub_tab_party"),
	CLAIMS("gui.xaero_pac_hub_tab_claims"),
	TEAM("gui.xaero_pac_hub_tab_team"),
	ADMIN("gui.xaero_pac_hub_tab_admin");

	private final Component label;

	HubTab(String labelKey) {
		this.label = Component.translatable(labelKey);
	}

	public Component getLabel() {
		return label;
	}

}
