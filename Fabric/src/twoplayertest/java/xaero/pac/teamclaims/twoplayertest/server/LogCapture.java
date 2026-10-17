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

package xaero.pac.teamclaims.twoplayertest.server;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * [Team Claims] Keeps the warnings and errors that the server logs, so that a snapshot can show them (for example the
 * warning about a rejected admin request of a player without permission). The script checks the log file as well.
 */
public final class LogCapture {

	private static final int MAX_LINES = 2000;
	private static final String APPENDER_NAME = "OpacTwoPlayerTestCapture";
	private static final List<String> LINES = new ArrayList<>();
	private static boolean installed;

	private LogCapture() {
	}

	/**
	 * Adds the appender to the root logger, once.
	 *
	 * @return false when the logging backend is not log4j core (nothing is captured then)
	 */
	public static synchronized boolean install() {
		if(installed)
			return true;
		if(!(LogManager.getContext(false) instanceof LoggerContext context))
			return false;
		Configuration configuration = context.getConfiguration();
		AbstractAppender appender = new AbstractAppender(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY) {
			@Override
			public void append(LogEvent event) {
				if(event.getLevel().isMoreSpecificThan(Level.WARN))
					add(event.getLevel() + " [" + event.getLoggerName() + "] " + event.getMessage().getFormattedMessage());
			}
		};
		appender.start();
		configuration.addAppender(appender);
		configuration.getRootLogger().addAppender(appender, Level.WARN, null);
		context.updateLoggers();
		installed = true;
		return true;
	}

	public static synchronized boolean isInstalled() {
		return installed;
	}

	private static synchronized void add(String line) {
		if(LINES.size() < MAX_LINES)
			LINES.add(line);
	}

	public static synchronized List<String> lines() {
		return List.copyOf(LINES);
	}

}
