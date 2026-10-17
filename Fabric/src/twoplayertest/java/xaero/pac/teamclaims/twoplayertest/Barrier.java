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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * [Team Claims] The step barrier of the two clients: a client that reached a point writes {@code sync/<player>.<point>},
 * the other one waits for that file. A client that gives up writes {@code sync/<player>.aborted}, which ends every wait
 * of the other one at once instead of after its time-out.
 */
public final class Barrier {

	/**
	 * What came of a wait.
	 */
	public enum Outcome {
		REACHED, TIMED_OUT, OTHER_ABORTED, INTERRUPTED
	}

	private static final long POLL_MILLIS = 100;

	private final Path dir;
	private final String self;

	public Barrier(Path out, String self) {
		this.dir = TwoPlayerTest.syncDir(out);
		this.self = self;
	}

	private Path file(String player, String point) {
		if(!TwoPlayerTest.isSafeLabel(point))
			throw new IllegalArgumentException("unsafe barrier name " + point);
		return dir.resolve(player + "." + point);
	}

	/**
	 * Marks that this client reached a point.
	 */
	public void arrive(String point) {
		try {
			TwoPlayerTest.writeAtomically(file(self, point), Instant.now().toString());
		} catch(IOException e) {
			throw new IllegalStateException("cannot write barrier " + point + ": " + e, e);
		}
	}

	/**
	 * Marks that this client gave up; the other one stops waiting for it.
	 */
	public void abort(String why) {
		try {
			TwoPlayerTest.writeAtomically(file(self, "aborted"), Instant.now() + " " + why);
		} catch(IOException | IllegalArgumentException e) {
			System.err.println("[TwoPlayerTest] cannot write the abort marker: " + e);
		}
	}

	public boolean hasArrived(String player, String point) {
		return Files.isRegularFile(file(player, point));
	}

	public boolean hasAborted(String player) {
		return Files.isRegularFile(file(player, "aborted"));
	}

	/**
	 * Waits until the other player reached a point.
	 */
	public Outcome await(String other, String point, Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while(true) {
			if(hasArrived(other, point))
				return Outcome.REACHED;
			if(hasAborted(other))
				return Outcome.OTHER_ABORTED;
			if(System.nanoTime() - deadline >= 0)
				return Outcome.TIMED_OUT;
			try {
				Thread.sleep(POLL_MILLIS);
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
				return Outcome.INTERRUPTED;
			}
		}
	}

}
