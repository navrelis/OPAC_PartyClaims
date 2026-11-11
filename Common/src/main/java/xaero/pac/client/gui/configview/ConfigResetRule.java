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

package xaero.pac.client.gui.configview;

import java.util.Objects;

/**
 * What the reset button of an option sets, and when it is there.
 * <p>
 * No client classes in here, so the rules can be tested on a server.
 */
public final class ConfigResetRule {

	private ConfigResetRule() {
	}

	/**
	 * The value a reset sets. A sub-config has no value of its own by default (it uses the main config), so that is
	 * {@code null}. Otherwise it is the value of the server's default player config when the screen has one, else the
	 * default of the option itself.
	 */
	public static <T> T target(boolean subConfig, T serverDefault, T optionDefault) {
		if(subConfig)
			return null;
		return serverDefault != null ? serverDefault : optionDefault;
	}

	/**
	 * The reset button is there only for an option the player may edit that does not have the reset value yet.
	 */
	public static <T> boolean isVisible(boolean mutable, T current, T target) {
		return mutable && !Objects.equals(current, target);
	}

}
