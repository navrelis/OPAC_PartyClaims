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

package xaero.pac.teamclaims.twoplayertest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * [Team Claims] The automated two-player test of the 1.4 hub (dev only). One dedicated Fabric server and two dev clients
 * (Player1, an op, and Player2, not an op) run with this mod ({@code opac_teamclaims_twoplayertest}, source set
 * {@code Fabric/src/twoplayertest}); a scripted driver in each client ({@code client.Scenario}) presses the real keys,
 * clicks the real widgets of the hub, asks the server for snapshots of its state ({@code /opactest snapshot}, written
 * by {@code server.ServerSnapshot}) and writes one result per assertion. {@code tools/twoplayertest/Run-TwoPlayerTest.ps1}
 * starts everything, waits, stops what it started and merges the results.
 * <p>
 * <b>Off unless asked for, twice over.</b> The source set and the run configs only exist when Gradle runs with
 * {@code -PtwoPlayerTest}, so the shipped jar never contains any of this. And even in such a run the mod does nothing
 * unless the JVM was started with {@value #OUT_PROPERTY} and {@value #ROLE_PROPERTY}, which only the run configs of the
 * test set.
 * <p>
 * The output folder: {@code status/} (who started, who finished, pids), {@code sync/} (the step barrier of the two
 * clients), {@code control/} (the request to stop the server), {@code server/snapshots/} (the snapshots),
 * {@code Player1/}, {@code Player2/} (each client's {@code results.json} and {@code screenshots/}).
 */
public final class TwoPlayerTest {

	/** The output folder of the run (absolute). */
	public static final String OUT_PROPERTY = "opac.twoplayertest.out";
	/** {@code server}, {@code selfcheck}, {@code Player1} or {@code Player2}. */
	public static final String ROLE_PROPERTY = "opac.twoplayertest.role";
	/** The longest the whole scenario of one client may take, in minutes (default {@value #DEFAULT_TIMEOUT_MINUTES}). */
	public static final String TIMEOUT_PROPERTY = "opac.twoplayertest.timeoutMinutes";

	public static final String ROLE_SERVER = "server";
	public static final String ROLE_SELFCHECK = "selfcheck";
	public static final String PLAYER1 = "Player1";
	public static final String PLAYER2 = "Player2";
	public static final int DEFAULT_TIMEOUT_MINUTES = 25;
	public static final String WORLD_NAME = "PerfTest-OPACHub";

	private static final Pattern SAFE_LABEL = Pattern.compile("[A-Za-z0-9._-]{1,80}");

	private TwoPlayerTest() {
	}

	/**
	 * Gets the output folder, null when this JVM is not part of the test (no {@value #OUT_PROPERTY}).
	 */
	public static Path outDir() {
		String out = System.getProperty(OUT_PROPERTY);
		if(out == null || out.isBlank())
			return null;
		return Path.of(out).toAbsolutePath().normalize();
	}

	/**
	 * Gets the role of this JVM, null when it is not part of the test.
	 */
	public static String role() {
		String role = System.getProperty(ROLE_PROPERTY);
		return role == null || role.isBlank() ? null : role.trim();
	}

	public static boolean isActive() {
		return outDir() != null && role() != null;
	}

	public static int timeoutMinutes() {
		try {
			return Math.max(1, Integer.parseInt(System.getProperty(TIMEOUT_PROPERTY, String.valueOf(DEFAULT_TIMEOUT_MINUTES)).trim()));
		} catch(NumberFormatException e) {
			return DEFAULT_TIMEOUT_MINUTES;
		}
	}

	/**
	 * Checks that a label (of a snapshot, a barrier, a screenshot) can be used as a file name as it is.
	 */
	public static boolean isSafeLabel(String label) {
		return label != null && SAFE_LABEL.matcher(label).matches();
	}

	public static Path statusDir(Path out) {
		return out.resolve("status");
	}

	public static Path syncDir(Path out) {
		return out.resolve("sync");
	}

	public static Path controlDir(Path out) {
		return out.resolve("control");
	}

	public static Path snapshotDir(Path out) {
		return out.resolve("server").resolve("snapshots");
	}

	public static Path snapshotFile(Path out, String label) {
		return snapshotDir(out).resolve(label + ".json");
	}

	public static Path stopServerFile(Path out) {
		return controlDir(out).resolve("stop-server");
	}

	/**
	 * Writes a file so that a reader never sees half of it: a temporary file next to it, then a move.
	 */
	public static void writeAtomically(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(temp, content, StandardCharsets.UTF_8);
		try {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch(IOException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Writes {@code status/<name>} with the pid and the time, which tells the script that this JVM got that far.
	 */
	public static void writeStatus(Path out, String name, String detail) {
		try {
			writeAtomically(statusDir(out).resolve(name), "pid=" + ProcessHandle.current().pid() + "\ntime=" + Instant.now() + "\n" + (detail == null ? "" : detail + "\n"));
		} catch(IOException e) {
			System.err.println("[TwoPlayerTest] could not write the status file " + name + ": " + e);
		}
	}

}
