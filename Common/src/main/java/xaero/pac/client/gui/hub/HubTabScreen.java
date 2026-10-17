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

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xaero.pac.client.gui.XPACScreen;

/**
 * The base of the screens of the hub tabs other than the party screen: it draws the tab bar instead of the title, opens
 * the Party tab when its own tab is not shown any more, and closes with the key that opens the hub.
 */
public abstract class HubTabScreen extends XPACScreen {

	protected static final Component BACK = Component.translatable("gui.xaero_pac_back");

	protected HubTabScreen(Screen escape, Screen parent, Component title) {
		super(escape, parent, title);
	}

	/**
	 * Gets the tab that this screen is the content of.
	 */
	protected abstract HubTab getTab();

	@Override
	protected void init() {
		super.init();
		for(Button button : HubTabBar.create(minecraft, width, getTab(), escape, parent))
			addRenderableWidget(button);
	}

	@Override
	protected boolean isTitleShown() {
		return false;
	}

	/**
	 * Does not focus a widget when opening, so that the key that opens the screen also closes it.
	 */
	@Override
	protected void setInitialFocus() {
	}

	@Override
	public void tick() {
		super.tick();
		if(!HubTabs.isVisible(getTab()))
			HubTabs.open(minecraft, HubTab.PARTY, escape, parent);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(HubTabs.isHubKey(keyCode)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if(HubTabs.isHubMouseButton(button)) {
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return getEscape() != null && getEscape().isPauseScreen();
	}

}
