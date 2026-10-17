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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [Team Claims] The assertions of one client driver, written to {@code <out>/<player>/results.json} after every change,
 * so that the script finds what was done even when the client dies halfway.
 * <p>
 * Nothing passes by default: an assertion only passes when {@link #check} is told so for a value that was observed,
 * an exception or a time-out makes it fail, and {@link #finish} turns every planned id that was never reported into a
 * failure. An id outside the plan, or one reported twice, is a failure as well.
 */
public final class Results {

	public enum Status {
		PASS, FAIL, SKIPPED
	}

	/**
	 * One assertion.
	 *
	 * @param expected  what the assertion wants, in words or as a value
	 * @param actual  what was observed (or why nothing could be observed)
	 */
	public record Entry(String id, String player, String description, Status status, String expected, String actual,
						List<String> screenshots, String time) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final String player;
	private final Path file;
	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private final List<String> notes = new ArrayList<>();
	private boolean finished;

	public Results(String player, Path file) {
		this.player = player;
		this.file = file;
	}

	public String getPlayer() {
		return player;
	}

	public synchronized boolean hasPassed(String id) {
		Entry entry = entries.get(id);
		return entry != null && entry.status() == Status.PASS;
	}

	public synchronized boolean isReported(String id) {
		return entries.containsKey(id);
	}

	public synchronized Entry get(String id) {
		return entries.get(id);
	}

	public synchronized List<Entry> getEntries() {
		return List.copyOf(entries.values());
	}

	/**
	 * Records an assertion that was evaluated: it passes exactly when {@code pass} is true.
	 */
	public synchronized Entry check(String id, boolean pass, Object expected, Object actual, List<String> screenshots) {
		return put(id, pass ? Status.PASS : Status.FAIL, String.valueOf(expected), String.valueOf(actual), screenshots);
	}

	public synchronized Entry fail(String id, Object expected, String reason, List<String> screenshots) {
		return put(id, Status.FAIL, String.valueOf(expected), reason, screenshots);
	}

	public synchronized Entry skip(String id, String reason) {
		return put(id, Status.SKIPPED, "-", "SKIPPED: " + reason, List.of());
	}

	public synchronized void note(String text) {
		notes.add(Instant.now() + " " + text);
		write();
	}

	private Entry put(String id, Status status, String expected, String actual, List<String> screenshots) {
		String description = ScenarioPlan.describe(id);
		if(description == null || !ScenarioPlan.of(player).containsKey(id)) {
			// not in the plan of this player: recorded under its own id so that it shows, and failed
			String key = "unplanned." + id;
			Entry entry = new Entry(key, player, "Assertion outside the plan of " + player, Status.FAIL, "an id from ScenarioPlan", "reported " + id + " (" + status + "): " + actual,
					List.copyOf(screenshots), Instant.now().toString());
			entries.put(key, entry);
			write();
			return entry;
		}
		Entry previous = entries.get(id);
		Entry entry;
		if(previous != null)
			entry = new Entry(id, player, description, Status.FAIL, previous.expected(), "reported twice: first " + previous.status() + " (" + previous.actual() + "), then " + status + " (" + actual + ")",
					concat(previous.screenshots(), screenshots), Instant.now().toString());
		else
			entry = new Entry(id, player, description, status, expected, actual, List.copyOf(screenshots), Instant.now().toString());
		entries.put(id, entry);
		write();
		return entry;
	}

	private static List<String> concat(List<String> a, List<String> b) {
		List<String> all = new ArrayList<>(a);
		all.addAll(b);
		return all;
	}

	/**
	 * Fails every planned id of this player that was not reported, and marks the file as complete.
	 *
	 * @param why  why the driver stopped, for the failed ones
	 */
	public synchronized void finish(String why) {
		for(Map.Entry<String, String> planned : ScenarioPlan.of(player).entrySet())
			if(!entries.containsKey(planned.getKey()))
				entries.put(planned.getKey(), new Entry(planned.getKey(), player, planned.getValue(), Status.FAIL, "reported by the driver",
						"never reported: " + why, List.of(), Instant.now().toString()));
		finished = true;
		write();
	}

	public synchronized String toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("player", player);
		root.addProperty("finished", finished);
		root.addProperty("written", Instant.now().toString());
		JsonArray array = new JsonArray();
		for(Entry entry : entries.values()) {
			JsonObject object = new JsonObject();
			object.addProperty("id", entry.id());
			object.addProperty("player", entry.player());
			object.addProperty("description", entry.description());
			object.addProperty("status", entry.status().name());
			object.addProperty("expected", entry.expected());
			object.addProperty("actual", entry.actual());
			JsonArray shots = new JsonArray();
			for(String shot : entry.screenshots())
				shots.add(shot);
			object.add("screenshots", shots);
			object.addProperty("time", entry.time());
			array.add(object);
		}
		root.add("results", array);
		JsonArray noteArray = new JsonArray();
		for(String note : notes)
			noteArray.add(note);
		root.add("notes", noteArray);
		return GSON.toJson(root);
	}

	private void write() {
		try {
			TwoPlayerTest.writeAtomically(file, toJson());
		} catch(IOException e) {
			System.err.println("[TwoPlayerTest] could not write " + file + ": " + e);
		}
	}

}
