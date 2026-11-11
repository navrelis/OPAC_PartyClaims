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

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The sidebar on the left of every page of the hub: a dark panel with one entry per page (item icon and label), the
 * entry of the open page highlighted. On a narrow screen it shows only the icons and the labels become tooltips
 * ({@link HubNavigation#isSidebarCollapsed}). It also knows the content area to the right of it, where the pages lay out
 * their widgets, and draws the headers of pages and sections.
 */
public final class HubSidebar {

	/**
	 * The Y of the first entry.
	 */
	public static final int TOP = 4;
	/**
	 * The height of an entry.
	 */
	public static final int ENTRY_HEIGHT = 20;
	/**
	 * The Y of the title of a page, level with the label of the first entry.
	 */
	public static final int TITLE_TOP = TOP + (ENTRY_HEIGHT - 8) / 2;
	/**
	 * The Y where the content of a page starts, below its title.
	 */
	public static final int CONTENT_TOP = TITLE_TOP + 16;
	/**
	 * The height that a section header ({@link #renderHeader}) takes, including the space below it.
	 */
	public static final int HEADER_HEIGHT = 13;

	private static final int ENTRY_GAP = 2;
	private static final int CONTENT_MARGIN = 8;
	private static final int BOTTOM_MARGIN = 6;
	private static final int HEADER_LINE_GAP = 4;

	private static final ResourceLocation MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/menu_list_background.png");
	private static final ResourceLocation INWORLD_MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
	private static final int SEPARATOR_COLOR = 0x40FFFFFF;
	private static final int CURRENT_COLOR = 0x50FFFFFF;
	private static final int CURRENT_MARK_COLOR = 0xFFFFFFFF;
	private static final int HOVER_COLOR = 0x28FFFFFF;
	private static final int FOCUS_COLOR = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFFC8C8C8;
	private static final int CURRENT_LABEL_COLOR = 0xFFFFFFFF;
	private static final int HEADER_LINE_COLOR = 0x50FFFFFF;

	private final int width;
	private final int screenWidth;
	private final int screenHeight;
	private final List<Button> entries;

	private HubSidebar(int width, int screenWidth, int screenHeight, List<Button> entries) {
		this.width = width;
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.entries = entries;
	}

	/**
	 * Creates the sidebar of a page with one entry for every page that is shown.
	 *
	 * @param active  the page that is open
	 * @param escape  the escape screen to hand on to the page that gets opened
	 * @param parent  the parent screen to hand on to the page that gets opened
	 */
	public static HubSidebar create(Minecraft minecraft, int screenWidth, int screenHeight, HubTab active, Screen escape, Screen parent) {
		Font font = minecraft.font;
		List<HubTab> tabs = HubTabs.getVisibleTabs();
		int widestLabel = 0;
		for(HubTab tab : tabs)
			widestLabel = Math.max(widestLabel, font.width(tab.getLabel()));
		boolean collapsed = HubNavigation.isSidebarCollapsed(screenWidth, widestLabel);
		int width = collapsed ? HubNavigation.COLLAPSED_WIDTH : HubNavigation.getExpandedWidth(widestLabel);
		int entryWidth = width - 2 * HubNavigation.PANEL_PADDING;
		List<Button> entries = new ArrayList<>();
		int y = TOP;
		for(HubTab tab : tabs) {
			entries.add(new Entry(HubNavigation.PANEL_PADDING, y, entryWidth, ENTRY_HEIGHT, tab, tab == active, collapsed,
					b -> HubTabs.open(minecraft, tab, escape, parent)));
			y += ENTRY_HEIGHT + ENTRY_GAP;
		}
		return new HubSidebar(width, screenWidth, screenHeight, entries);
	}

	/**
	 * Gets the entries, which the page adds as its widgets.
	 */
	public List<Button> getEntries() {
		return entries;
	}

	public int getWidth() {
		return width;
	}

	/**
	 * Gets the X where the content area of the page starts.
	 */
	public int contentLeft() {
		return width + CONTENT_MARGIN;
	}

	/**
	 * Gets the width of the content area of the page.
	 */
	public int contentWidth() {
		return Math.max(0, screenWidth - CONTENT_MARGIN - contentLeft());
	}

	/**
	 * Gets the Y where the content area of the page starts, below the title of the page.
	 */
	public int contentTop() {
		return CONTENT_TOP;
	}

	/**
	 * Gets the Y where the content area of the page ends.
	 */
	public int contentBottom() {
		return screenHeight - BOTTOM_MARGIN;
	}

	/**
	 * Draws the panel behind the entries and the title of the page. Call it after the background of the screen and
	 * before the widgets.
	 */
	public void renderPanel(GuiGraphics guiGraphics, Minecraft minecraft, Component title) {
		RenderSystem.enableBlend();
		ResourceLocation background = minecraft.level == null ? MENU_LIST_BACKGROUND : INWORLD_MENU_LIST_BACKGROUND;
		guiGraphics.blit(background, 0, 0, 0.0F, 0.0F, width, screenHeight, 32, 32);
		RenderSystem.disableBlend();
		guiGraphics.fill(width - 1, 0, width, screenHeight, SEPARATOR_COLOR);
		renderHeader(guiGraphics, minecraft.font, title, contentLeft(), TITLE_TOP, contentLeft() + contentWidth(), CURRENT_LABEL_COLOR);
	}

	/**
	 * Draws a header: a label and a thin line from its end to the right edge.
	 *
	 * @param left  the X of the label
	 * @param y  the Y of the label
	 * @param right  where the line ends
	 * @param color  the colour of the label
	 */
	public static void renderHeader(GuiGraphics guiGraphics, Font font, Component label, int left, int y, int right, int color) {
		String text = font.plainSubstrByWidth(label.getString(), Math.max(0, right - left));
		guiGraphics.drawString(font, text, left, y, color);
		int lineLeft = left + font.width(text) + HEADER_LINE_GAP;
		if(lineLeft < right)
			guiGraphics.fill(lineLeft, y + font.lineHeight / 2, right, y + font.lineHeight / 2 + 1, HEADER_LINE_COLOR);
	}

	/**
	 * One entry of the sidebar. It is a vanilla button (inactive for the open page) that draws itself as a row of the
	 * panel instead of the button texture.
	 */
	private static final class Entry extends Button {

		private final ItemStack icon;
		private final boolean current;
		private final boolean collapsed;

		private Entry(int x, int y, int width, int height, HubTab tab, boolean current, boolean collapsed, OnPress onPress) {
			super(x, y, width, height, tab.getLabel(), onPress, DEFAULT_NARRATION);
			this.icon = new ItemStack(tab.getIcon());
			this.current = current;
			this.collapsed = collapsed;
			this.active = !current;
			if(collapsed)
				setTooltip(Tooltip.create(tab.getLabel()));
		}

		@Override
		protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
			int left = getX();
			int top = getY();
			int right = left + getWidth();
			int bottom = top + getHeight();
			if(current) {
				guiGraphics.fill(left, top, right, bottom, CURRENT_COLOR);
				guiGraphics.fill(left, top, left + 2, bottom, CURRENT_MARK_COLOR);
			} else if(isHovered())
				guiGraphics.fill(left, top, right, bottom, HOVER_COLOR);
			if(isFocused())
				guiGraphics.renderOutline(left, top, getWidth(), getHeight(), FOCUS_COLOR);
			int iconLeft = left + HubNavigation.ENTRY_PADDING;
			guiGraphics.renderItem(icon, iconLeft, top + (getHeight() - HubNavigation.ICON_SIZE) / 2);
			if(collapsed)
				return;
			Font font = Minecraft.getInstance().font;
			int labelLeft = iconLeft + HubNavigation.ICON_SIZE + HubNavigation.ICON_GAP;
			String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, right - labelLeft - 2));
			guiGraphics.drawString(font, label, labelLeft, top + (getHeight() - font.lineHeight) / 2 + 1,
					current || isHovered() ? CURRENT_LABEL_COLOR : LABEL_COLOR);
		}

	}

}
