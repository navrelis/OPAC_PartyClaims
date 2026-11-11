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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xaero.pac.client.gui.XPACScreen;

/**
 * The base of the screens of the hub pages other than the party screen: it adds the sidebar ({@link HubSidebar}) and
 * draws the title of the page instead of the OPAC title, opens the Overview when its own page is not shown any more, and
 * closes with the key that opens the hub. The page lays out its widgets in the content area right of the sidebar
 * ({@link #contentLeft()} and the others).
 */
public abstract class HubTabScreen extends XPACScreen {

	protected static final Component BACK = Component.translatable("gui.xaero_pac_back");

	private HubSidebar sidebar;

	protected HubTabScreen(Screen escape, Screen parent, Component title) {
		super(escape, parent, title);
	}

	/**
	 * Gets the page that this screen is the content of.
	 */
	protected abstract HubTab getTab();

	@Override
	protected void init() {
		super.init();
		sidebar = HubSidebar.create(minecraft, width, height, getTab(), escape, parent);
		for(Button entry : sidebar.getEntries())
			addRenderableWidget(entry);
	}

	/**
	 * Gets the X where the content area of the page starts, right of the sidebar.
	 */
	protected int contentLeft() {
		return sidebar.contentLeft();
	}

	/**
	 * Gets the width of the content area of the page.
	 */
	protected int contentWidth() {
		return sidebar.contentWidth();
	}

	/**
	 * Gets the Y where the content area of the page starts, below the title of the page.
	 */
	protected int contentTop() {
		return sidebar.contentTop();
	}

	/**
	 * Gets the Y where the content area of the page ends.
	 */
	protected int contentBottom() {
		return sidebar.contentBottom();
	}

	/**
	 * Gets the X of the middle of the content area.
	 */
	protected int contentCenterX() {
		return contentLeft() + contentWidth() / 2;
	}

	@Override
	protected boolean isTitleShown() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
		super.renderBackground(guiGraphics, mouseX, mouseY, partial);
		sidebar.renderPanel(guiGraphics, minecraft, getTab().getLabel());
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
			HubTabs.open(minecraft, HubTab.OVERVIEW, escape, parent);
	}

	/**
	 * Checks whether a text field has the focus, in which case the key that opens the hub is text for the field and does
	 * not close the hub.
	 */
	protected boolean isTextFieldFocused() {
		GuiEventListener focused = getFocused();
		while(focused instanceof ContainerEventHandler container && container.getFocused() != null)
			focused = container.getFocused();
		return focused instanceof EditBox editBox && editBox.isFocused();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(!isTextFieldFocused() && HubTabs.isCloseKey(keyCode)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if(!isTextFieldFocused() && HubTabs.isCloseMouseButton(button)) {
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
