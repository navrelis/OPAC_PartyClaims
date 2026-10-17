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

package xaero.pac.teamclaims.twoplayertest.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * [Team Claims] Finding widgets of a screen by what they show, reading the private state of the hub screens, and
 * clicking through the screen's own event handling. Client thread only.
 * <p>
 * Widgets are matched by their visible message (the translated text, so the lang keys of {@code en_us.json} name
 * them), never by position. A click is a {@code mouseClicked} and {@code mouseReleased} on the screen at the centre of
 * the widget, so it goes through every handler a real click goes through (the screen, a list, its row, the widget), and
 * an overlapping widget that would take a real click takes this one too.
 */
public final class Ui {

	private Ui() {
	}

	public static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/**
	 * Gets every widget of a screen, also those inside lists and their rows, in the order the screen hands events to them.
	 */
	public static List<GuiEventListener> all(ContainerEventHandler container) {
		List<GuiEventListener> found = new ArrayList<>();
		collect(container, found);
		return found;
	}

	private static void collect(ContainerEventHandler container, List<GuiEventListener> found) {
		for(GuiEventListener child : List.copyOf(container.children())) {
			found.add(child);
			if(child instanceof ContainerEventHandler inner)
				collect(inner, found);
		}
	}

	public static List<Button> buttons(Screen screen, Predicate<Button> filter) {
		List<Button> buttons = new ArrayList<>();
		for(GuiEventListener listener : all(screen))
			if(listener instanceof Button button && filter.test(button))
				buttons.add(button);
		return buttons;
	}

	/**
	 * Gets the visible buttons that show exactly this text.
	 */
	public static List<Button> buttonsWithText(Screen screen, String text) {
		return buttons(screen, button -> button.visible && text.equals(button.getMessage().getString()));
	}

	/**
	 * Gets the one visible button that shows this text, null if there is none; fails if there are several.
	 */
	public static Button button(Screen screen, String text) {
		List<Button> found = buttonsWithText(screen, text);
		if(found.size() > 1)
			throw new IllegalStateException(found.size() + " buttons show \"" + text + "\"; expected one");
		return found.isEmpty() ? null : found.get(0);
	}

	/**
	 * Gets the visible edit boxes whose message (the name the box was made with: its hint, or the option path of an
	 * admin row) is exactly this text.
	 */
	public static List<EditBox> editBoxes(Screen screen, String message) {
		List<EditBox> boxes = new ArrayList<>();
		for(GuiEventListener listener : all(screen))
			if(listener instanceof EditBox box && box.visible && message.equals(box.getMessage().getString()))
				boxes.add(box);
		return boxes;
	}

	public static EditBox editBox(Screen screen, String message) {
		List<EditBox> found = editBoxes(screen, message);
		if(found.size() > 1)
			throw new IllegalStateException(found.size() + " edit boxes are named \"" + message + "\"; expected one");
		return found.isEmpty() ? null : found.get(0);
	}

	public static double centerX(AbstractWidget widget) {
		return widget.getX() + widget.getWidth() / 2.0;
	}

	public static double centerY(AbstractWidget widget) {
		return widget.getY() + widget.getHeight() / 2.0;
	}

	public static boolean contains(AbstractWidget widget, double x, double y) {
		return widget.visible && x >= widget.getX() && y >= widget.getY() && x < widget.getX() + widget.getWidth() && y < widget.getY() + widget.getHeight();
	}

	/**
	 * Presses and releases the left mouse button at a point of the screen.
	 *
	 * @return whether the screen took the press
	 */
	public static boolean clickAt(Screen screen, double x, double y) {
		boolean taken = screen.mouseClicked(x, y, 0);
		screen.mouseReleased(x, y, 0);
		return taken;
	}

	/**
	 * Clicks the centre of a widget through the screen. The widget must be on the screen (positioned by a frame).
	 *
	 * @return whether the screen took the press
	 */
	public static boolean click(Screen screen, AbstractWidget widget) {
		if(!widget.visible)
			throw new IllegalStateException("the widget \"" + widget.getMessage().getString() + "\" is not visible");
		return clickAt(screen, centerX(widget), centerY(widget));
	}

	public static String describe(Object object) {
		if(object == null)
			return "null";
		if(object instanceof AbstractWidget widget)
			return widget.getClass().getSimpleName() + "(\"" + widget.getMessage().getString() + "\" at " + widget.getX() + "," + widget.getY()
					+ " " + widget.getWidth() + "x" + widget.getHeight() + (widget.active ? "" : ", inactive") + (widget.visible ? "" : ", hidden") + ")";
		return object.getClass().getSimpleName();
	}

	// ---- private state of the screens (dev run: the names are the source names) ----

	public static Field findField(Class<?> type, String name) {
		for(Class<?> c = type; c != null; c = c.getSuperclass()) {
			try {
				Field field = c.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			} catch(NoSuchFieldException ignored) {
			}
		}
		throw new IllegalStateException("no field " + name + " in " + type.getName());
	}

	@SuppressWarnings("unchecked")
	public static <T> T get(Object object, String name) {
		try {
			return (T) findField(object.getClass(), name).get(object);
		} catch(IllegalAccessException e) {
			throw new IllegalStateException("cannot read " + name + " of " + object.getClass().getName(), e);
		}
	}

	public static int getInt(Object object, String name) {
		Object value = get(object, name);
		return ((Number) value).intValue();
	}

	/**
	 * Gets the name of a private enum field, e.g. the state of a hub screen.
	 */
	public static String enumName(Object object, String name) {
		Object value = get(object, name);
		return value == null ? "null" : ((Enum<?>) value).name();
	}

}
