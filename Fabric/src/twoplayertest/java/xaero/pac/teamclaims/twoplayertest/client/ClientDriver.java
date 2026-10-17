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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import xaero.pac.teamclaims.twoplayertest.Barrier;
import xaero.pac.teamclaims.twoplayertest.Results;
import xaero.pac.teamclaims.twoplayertest.TwoPlayerTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * [Team Claims] What a scripted client can do: run code on the client thread and wait for it, wait for a condition (always
 * with a time-out, which fails the step loudly), press keys through the game's own keyboard handler, type, click
 * widgets through the screen, take screenshots, and ask the server for a snapshot. The scenario runs on its own thread
 * and every touch of the game goes through {@link #call}, so the game keeps rendering and ticking between the steps.
 */
public final class ClientDriver {

	/** Something the driver could not do; the step it happened in fails with this message. */
	public static final class DriverException extends RuntimeException {
		public DriverException(String message) {
			super(message);
		}

		public DriverException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private static final long CALL_TIMEOUT_SECONDS = 30;
	private static final long POLL_MILLIS = 50;
	private static final Duration SNAPSHOT_FILE_TIMEOUT = Duration.ofSeconds(20);

	public final Minecraft mc;
	public final String player;
	public final String other;
	public final Path out;
	public final Path playerDir;
	public final Path shotDir;
	public final Results results;
	public final Barrier barrier;
	private final AtomicLong ticks;
	private final AtomicLong frames;
	private int snapshotCount;

	public ClientDriver(Minecraft mc, String player, Path out, AtomicLong ticks, AtomicLong frames) {
		this.mc = mc;
		this.player = player;
		this.other = TwoPlayerTest.PLAYER1.equals(player) ? TwoPlayerTest.PLAYER2 : TwoPlayerTest.PLAYER1;
		this.out = out;
		this.playerDir = out.resolve(player);
		this.shotDir = playerDir.resolve("screenshots");
		this.results = new Results(player, playerDir.resolve("results.json"));
		this.barrier = new Barrier(out, player);
		this.ticks = ticks;
		this.frames = frames;
	}

	// ---- the client thread ----

	/**
	 * Runs code on the client thread and waits for its result. An exception in it, or a client thread that does not get
	 * to it within {@value #CALL_TIMEOUT_SECONDS} s, is a {@link DriverException}.
	 */
	public <T> T call(String what, Callable<T> task) {
		CompletableFuture<T> future = mc.submit((Supplier<T>) () -> {
			try {
				return task.call();
			} catch(Exception e) {
				throw new CompletionException(e);
			}
		});
		try {
			return future.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch(TimeoutException e) {
			throw new DriverException("the client thread did not run \"" + what + "\" within " + CALL_TIMEOUT_SECONDS + " s");
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new DriverException("interrupted while " + what);
		} catch(ExecutionException e) {
			Throwable cause = e.getCause() instanceof CompletionException && e.getCause().getCause() != null ? e.getCause().getCause() : e.getCause();
			if(cause instanceof DriverException driverException)
				throw driverException;
			throw new DriverException(what + ": " + cause, cause);
		}
	}

	public void run(String what, Runnable task) {
		call(what, () -> {
			task.run();
			return null;
		});
	}

	/**
	 * Checks a condition on the client thread until it holds or the time is up.
	 *
	 * @return whether it held
	 */
	public boolean poll(Duration timeout, Callable<Boolean> condition) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while(true) {
			if(Boolean.TRUE.equals(call("a check", condition)))
				return true;
			if(System.nanoTime() - deadline >= 0)
				return false;
			sleep(POLL_MILLIS);
		}
	}

	/**
	 * Waits for a condition, failing the step when the time is up.
	 */
	public void await(String what, Duration timeout, Callable<Boolean> condition) {
		if(!poll(timeout, condition))
			throw new DriverException("timed out after " + timeout.toSeconds() + " s waiting for " + what);
	}

	public long ticks() {
		return ticks.get();
	}

	public long frames() {
		return frames.get();
	}

	/**
	 * Lets client ticks pass (at most 50 ms each plus a margin, else it fails).
	 */
	public void waitTicks(int count) {
		long target = ticks.get() + count;
		long deadline = System.nanoTime() + Duration.ofMillis(count * 200L + 10_000).toNanos();
		while(ticks.get() < target) {
			if(System.nanoTime() - deadline >= 0)
				throw new DriverException("the client did not tick " + count + " times in time (is it frozen?)");
			sleep(10);
		}
	}

	/**
	 * Lets frames be drawn, so that what a step changed is on the screen (and positioned: list rows place their buttons
	 * while they draw).
	 */
	public void waitFrames(int count) {
		long target = frames.get() + count;
		long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
		while(frames.get() < target) {
			if(System.nanoTime() - deadline >= 0)
				throw new DriverException("the client drew no " + count + " frames within 20 s (window minimised?)");
			sleep(10);
		}
	}

	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new DriverException("interrupted");
		}
	}

	// ---- input ----

	/**
	 * Presses and releases a key through the game's keyboard handler, exactly as GLFW reports a key: the hub key, the
	 * screen's key handling and key mappings all see it as a real key. The release comes a tick later.
	 */
	public void pressKey(int key) {
		run("press key " + key, () -> {
			long window = mc.getWindow().getWindow();
			int scancode = GLFW.glfwGetKeyScancode(key);
			mc.keyboardHandler.keyPress(window, key, scancode, GLFW.GLFW_PRESS, 0);
		});
		waitTicks(1);
		run("release key " + key, () -> {
			long window = mc.getWindow().getWindow();
			int scancode = GLFW.glfwGetKeyScancode(key);
			mc.keyboardHandler.keyPress(window, key, scancode, GLFW.GLFW_RELEASE, 0);
		});
	}

	/**
	 * Types text into the focused widget of the open screen: the character events that GLFW sends after the key events.
	 */
	public void typeChars(String text) {
		run("type \"" + text + "\"", () -> {
			Screen screen = requireScreen();
			for(char c : text.toCharArray())
				screen.charTyped(c, 0);
		});
	}

	/**
	 * Clicks an edit box (which focuses it), removes its text with End and Backspace key presses, types the new text.
	 */
	public void replaceText(String what, Callable<EditBox> box, String text) {
		EditBox found = call("find " + what, box);
		if(found == null)
			throw new DriverException("no edit box " + what);
		run("focus " + what, () -> {
			Ui.click(requireScreen(), found);
			if(!found.isFocused())
				throw new DriverException("a click on " + what + " did not focus it: " + Ui.describe(found));
		});
		pressKey(GLFW.GLFW_KEY_END);
		int length = call("length of " + what, () -> found.getValue().length());
		for(int i = 0; i < length; i++)
			pressKey(GLFW.GLFW_KEY_BACKSPACE);
		String left = call("value of " + what, found::getValue);
		if(!left.isEmpty())
			throw new DriverException("Backspace did not empty " + what + ", it still holds \"" + left + "\"");
		typeChars(text);
		String value = call("value of " + what, found::getValue);
		if(!text.equals(value))
			throw new DriverException(what + " holds \"" + value + "\" after typing \"" + text + "\"");
	}

	/**
	 * Clicks a widget that the finder returns, through the open screen. The widget must exist, be visible and active.
	 *
	 * @return a description of what was clicked
	 */
	public String clickWidget(String what, Callable<? extends AbstractWidget> finder) {
		waitFrames(2);
		return call("click " + what, () -> {
			AbstractWidget widget = finder.call();
			if(widget == null)
				throw new DriverException("no " + what + " on " + screenName());
			if(!widget.active)
				throw new DriverException(what + " is inactive: " + Ui.describe(widget));
			String description = Ui.describe(widget);
			Ui.click(requireScreen(), widget);
			return description;
		});
	}

	public Screen requireScreen() {
		Screen screen = mc.screen;
		if(screen == null)
			throw new DriverException("no screen is open");
		return screen;
	}

	public String screenName() {
		return mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName();
	}

	// ---- window and pictures ----

	/**
	 * Sets the size the game renders at (which is what a screenshot holds; the window on the desktop keeps its size) and
	 * the GUI scale, and lets the open screen lay itself out again.
	 *
	 * @return the GUI scale the game took (it takes no scale the size cannot hold)
	 */
	public int setGuiScale(int width, int height, int scale) {
		int taken = call("set GUI scale " + scale, () -> {
			mc.getWindow().setWidth(width);
			mc.getWindow().setHeight(height);
			mc.options.guiScale().set(scale);
			mc.resizeDisplay();
			return (int) mc.getWindow().getGuiScale();
		});
		waitTicks(2);
		waitFrames(3);
		return taken;
	}

	/**
	 * Saves a picture of the last frame as {@code screenshots/<name>.png} and checks that it was written.
	 *
	 * @return the path relative to the output folder
	 */
	public String screenshot(String name) {
		if(!TwoPlayerTest.isSafeLabel(name))
			throw new DriverException("unsafe screenshot name " + name);
		waitFrames(3);
		Path file = shotDir.resolve(name + ".png");
		call("screenshot " + name, () -> {
			Files.createDirectories(shotDir);
			try(NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
				image.writeToFile(file);
			}
			return null;
		});
		try {
			if(!Files.isRegularFile(file) || Files.size(file) == 0)
				throw new DriverException("the screenshot " + file + " was not written");
		} catch(IOException e) {
			throw new DriverException("cannot check the screenshot " + file + ": " + e);
		}
		return out.relativize(file).toString().replace('\\', '/');
	}

	// ---- the server ----

	/**
	 * Asks the server for a snapshot of its state through the test command and reads it.
	 */
	public JsonObject snapshot(String label) {
		String fullLabel = player + "-" + String.format("%03d", ++snapshotCount) + "-" + label;
		if(!TwoPlayerTest.isSafeLabel(fullLabel))
			throw new DriverException("unsafe snapshot label " + fullLabel);
		Path file = TwoPlayerTest.snapshotFile(out, fullLabel);
		run("request snapshot " + fullLabel, () -> {
			if(mc.player == null)
				throw new DriverException("not connected");
			mc.player.connection.sendCommand("opactest snapshot " + fullLabel);
		});
		long deadline = System.nanoTime() + SNAPSHOT_FILE_TIMEOUT.toNanos();
		while(!Files.isRegularFile(file)) {
			if(System.nanoTime() - deadline >= 0)
				throw new DriverException("the server wrote no snapshot " + fullLabel + " within " + SNAPSHOT_FILE_TIMEOUT.toSeconds() + " s");
			sleep(100);
		}
		try {
			return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch(IOException | RuntimeException e) {
			throw new DriverException("cannot read snapshot " + file + ": " + e);
		}
	}

	/**
	 * Takes snapshots (about one a second) until one satisfies the condition or the time is up.
	 *
	 * @return the last snapshot; whether it satisfies the condition is for the caller to check again
	 */
	public JsonObject awaitServer(String label, Duration timeout, Predicate<JsonObject> condition) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while(true) {
			JsonObject snapshot = snapshot(label);
			boolean holds;
			try {
				holds = condition.test(snapshot);
			} catch(RuntimeException e) {
				holds = false;
			}
			if(holds || System.nanoTime() - deadline >= 0)
				return snapshot;
			sleep(1000);
		}
	}

	public void sendCommand(String command) {
		run("send /" + command, () -> {
			if(mc.player == null)
				throw new DriverException("not connected");
			mc.player.connection.sendCommand(command);
		});
	}

}
