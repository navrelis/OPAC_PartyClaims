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

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The Team tab. For now a placeholder; the team settings screen replaces it (TODO Team tab, task 4: return the real
 * screen from {@link HubTabs#create}).
 */
public class TeamTabScreen extends HubTabScreen {

	private static final Component TITLE = Component.translatable("gui.xaero_pac_hub_tab_team");
	private static final Component PLACEHOLDER = Component.translatable("gui.xaero_pac_hub_team_placeholder");
	private static final int MAX_TEXT_WIDTH = 260;
	private static final int LINE_STEP = 10;

	public TeamTabScreen(Screen escape, Screen parent) {
		super(escape, parent, TITLE);
	}

	@Override
	protected HubTab getTab() {
		return HubTab.TEAM;
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(Button.builder(BACK, b -> goBack()).bounds(width / 2 - 100, height - 26, 200, 20).build());
	}

	@Override
	protected void renderPreDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
		super.renderPreDropdown(guiGraphics, mouseX, mouseY, partial);
		List<FormattedCharSequence> lines = font.split(PLACEHOLDER, Math.min(width - 16, MAX_TEXT_WIDTH));
		int y = HubTabBar.CONTENT_TOP + 20;
		for(FormattedCharSequence line : lines) {
			guiGraphics.drawCenteredString(font, line, width / 2, y, 0xFFAAAAAA);
			y += LINE_STEP;
		}
	}

}
