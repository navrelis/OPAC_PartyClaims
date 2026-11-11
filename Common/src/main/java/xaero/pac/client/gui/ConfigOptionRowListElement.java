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

package xaero.pac.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * [Team Claims] A row of the player config screen: the value element of an option, a reset button after it (only while
 * {@code resetVisible} says so) and a background that alternates from row to row. The row is {@link #WIDTH} wide, the
 * value element is {@link #WIDTH} minus the {@link #RESET_SLOT} of the reset button.
 */
public final class ConfigOptionRowListElement extends WidgetListElement<ConfigOptionRowListElement> {

	public static final int WIDTH = 200;
	public static final int RESET_SLOT = 20;
	private static final int RESET_WIDTH = 18;
	private static final int BACKGROUND_EVEN = 0x30000000;
	private static final int BACKGROUND_ODD = 0x60000000;

	private final WidgetListElement<?> valueElement;
	private final BooleanSupplier resetVisible;
	private final Runnable resetAction;
	private boolean odd;

	public ConfigOptionRowListElement(int h, WidgetListElement<?> valueElement, BooleanSupplier resetVisible, Runnable resetAction) {
		super(WIDTH, h, true, null, valueElement.getTooltip());
		this.valueElement = valueElement;
		this.resetVisible = resetVisible;
		this.resetAction = resetAction;
	}

	public void setOdd(boolean odd) {
		this.odd = odd;
	}

	@Override
	public AbstractWidget screenInit(int x, int y, WidgetListScreen screen, List<EditBox> tickableBoxes) {
		this.x = x;
		this.y = y;
		AbstractWidget widget = valueElement.screenInit(x, y, screen, tickableBoxes);
		if(resetVisible.getAsBoolean()) {
			Minecraft minecraft = Minecraft.getInstance();
			screen.addRenderableWidget(Button.builder(Component.literal("↺"), b -> {
				resetAction.run();
				screen.init(minecraft, screen.width, screen.height);
			}).bounds(x + w - RESET_WIDTH, y, RESET_WIDTH, h)
					.tooltip(Tooltip.create(Component.translatable("gui.xaero_pac_config_reset_tooltip"))).build());
		}
		return widget;
	}

	@Override
	public boolean isOver(int mouseX, int mouseY) {
		return valueElement.isOver(mouseX, mouseY);
	}

	@Override
	public void render(GuiGraphics guiGraphics) {
		valueElement.render(guiGraphics);
	}

	@Override
	public void renderRowBackground(GuiGraphics guiGraphics) {
		guiGraphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, odd ? BACKGROUND_ODD : BACKGROUND_EVEN);
	}

}
