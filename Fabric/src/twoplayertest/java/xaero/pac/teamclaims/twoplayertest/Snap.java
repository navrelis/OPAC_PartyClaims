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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * [Team Claims] Reading a server snapshot ({@code server.ServerSnapshot}): every getter answers null for what is not
 * there, so an assertion compares the observed value (null included) with the expected one and never guesses. No
 * Minecraft classes, so the self-check reads snapshots with the same code as the clients.
 */
public final class Snap {

	private Snap() {
	}

	public static String str(JsonObject object, String key) {
		if(object == null)
			return null;
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}

	public static Integer integer(JsonObject object, String key) {
		if(object == null)
			return null;
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? null : element.getAsInt();
	}

	public static Boolean bool(JsonObject object, String key) {
		if(object == null)
			return null;
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? null : element.getAsBoolean();
	}

	public static JsonObject obj(JsonObject object, String key) {
		if(object == null)
			return null;
		JsonElement element = object.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	public static List<JsonObject> list(JsonObject object, String key) {
		List<JsonObject> list = new ArrayList<>();
		if(object == null)
			return list;
		JsonElement element = object.get(key);
		if(element != null && element.isJsonArray())
			for(JsonElement item : element.getAsJsonArray())
				if(item.isJsonObject())
					list.add(item.getAsJsonObject());
		return list;
	}

	public static JsonObject player(JsonObject snapshot, String name) {
		for(JsonObject player : list(snapshot, "players"))
			if(name.equals(str(player, "name")))
				return player;
		return null;
	}

	/**
	 * Gets the party that this player owns.
	 */
	public static JsonObject partyOwnedBy(JsonObject snapshot, String ownerName) {
		for(JsonObject party : list(snapshot, "parties"))
			if(ownerName.equals(str(obj(party, "owner"), "name")))
				return party;
		return null;
	}

	public static JsonObject member(JsonObject party, String name) {
		for(JsonObject member : list(party, "members"))
			if(name.equals(str(member, "name")))
				return member;
		return null;
	}

	public static String rank(JsonObject party, String name) {
		return str(member(party, name), "rank");
	}

	public static boolean isInvited(JsonObject party, String name) {
		for(JsonObject invite : list(party, "invited"))
			if(name.equals(str(invite, "name")))
				return true;
		return false;
	}

	public static List<String> memberNames(JsonObject party) {
		List<String> names = new ArrayList<>();
		for(JsonObject member : list(party, "members"))
			names.add(str(member, "name") + ":" + str(member, "rank") + (Boolean.TRUE.equals(bool(member, "owner")) ? "(owner)" : ""));
		return names;
	}

	public static JsonObject team(JsonObject party) {
		return obj(party, "team");
	}

	public static String role(JsonObject party, String action) {
		return str(obj(team(party), "roles"), action);
	}

	public static Integer teamBudget(JsonObject party, String key) {
		return integer(obj(team(party), "budget"), key);
	}

	public static JsonObject claimAt(JsonObject snapshot, String dimension, int x, int z) {
		for(JsonObject claim : list(snapshot, "claims"))
			if(dimension.equals(str(claim, "dimension")) && Integer.valueOf(x).equals(integer(claim, "x")) && Integer.valueOf(z).equals(integer(claim, "z")))
				return claim;
		return null;
	}

	public static String describeClaim(JsonObject claim) {
		if(claim == null)
			return "unclaimed";
		return "claim of " + str(claim, "ownerName") + " (sub " + integer(claim, "subConfigIndex") + ", teamClaim=" + bool(claim, "teamClaim") + ")";
	}

	public static JsonObject playerData(JsonObject snapshot, String name) {
		return obj(obj(snapshot, "playerData"), name);
	}

	public static Integer bonus(JsonObject snapshot, String player, String bonus) {
		return integer(obj(playerData(snapshot, player), "bonus"), bonus);
	}

	public static Integer playerBudget(JsonObject snapshot, String player, String key) {
		return integer(obj(playerData(snapshot, player), "budget"), key);
	}

	/**
	 * Gets what the server runs with, by {@code <config id>:<path>}.
	 */
	public static String running(JsonObject snapshot, String key) {
		return str(obj(obj(snapshot, "config"), "running"), key);
	}

	public static String pending(JsonObject snapshot, String key) {
		return str(obj(obj(snapshot, "config"), "pending"), key);
	}

	public static Boolean restartOnly(JsonObject snapshot, String key) {
		return bool(obj(obj(snapshot, "config"), "restartOnly"), key);
	}

	/**
	 * Gets a value as the config file holds it.
	 */
	public static String fileValue(JsonObject snapshot, String configId, String path) {
		return str(obj(obj(obj(snapshot, "configFiles"), configId), "values"), path);
	}

	public static Boolean fileIsOverride(JsonObject snapshot, String configId) {
		return bool(obj(obj(snapshot, "configFiles"), configId), "override");
	}

	public static boolean warningContains(JsonObject snapshot, String... parts) {
		JsonElement warnings = snapshot == null ? null : snapshot.get("warnings");
		if(warnings == null || !warnings.isJsonArray())
			return false;
		for(JsonElement line : warnings.getAsJsonArray()) {
			String text = line.getAsString();
			boolean all = true;
			for(String part : parts)
				all &= text.contains(part);
			if(all)
				return true;
		}
		return false;
	}

	public static JsonArray errors(JsonObject snapshot) {
		JsonElement errors = snapshot == null ? null : snapshot.get("errors");
		return errors != null && errors.isJsonArray() ? errors.getAsJsonArray() : new JsonArray();
	}

}
