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

package xaero.pac.client.teamclaims;

import xaero.pac.common.packet.teamclaims.AdminConfigEntry;
import xaero.pac.common.packet.teamclaims.AdminPlayerData;
import xaero.pac.common.packet.teamclaims.AdminTeamData;
import xaero.pac.common.packet.teamclaims.ClientboundAdminConfigPacket;
import xaero.pac.common.packet.teamclaims.ClientboundAdminResultPacket;
import xaero.pac.common.packet.teamclaims.TeamInfoData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [Team Claims] Client-side store of what the server sent for the hub screen's Team and Admin tabs (packets 104 to 113).
 * The packet handlers fill it on the client thread; screens read it and compare {@link #getRevision()} to notice
 * changes. Everything is reset when the client leaves a server ({@code ClientData.reset}). Requests go through
 * {@link TeamClaimsClientRequests}.
 */
public final class TeamClaimsClientData {

	public static final TeamClaimsClientData INSTANCE = new TeamClaimsClientData();

	private boolean adminMenuAllowed;
	/** The last complete config snapshot (both configs), by {@link AdminConfigEntry#key()}, in server order. */
	private Map<String, AdminConfigEntry> configEntries = Map.of();
	private boolean configsReceived;
	private int pendingSnapshotId = -1;
	private final List<List<AdminConfigEntry>> pendingPages = new ArrayList<>();
	private List<AdminTeamData> teams = List.of();
	private boolean teamsReceived;
	@Nullable
	private AdminPlayerData player;
	@Nullable
	private ClientboundAdminResultPacket.Result lastResult;
	@Nullable
	private TeamInfoData teamInfo;
	private int revision;

	private TeamClaimsClientData() {
	}

	/** Grows by one on every change of anything here. */
	public int getRevision() {
		return revision;
	}

	/** Whether the server says this player may use the admin menu (only for showing the tab; the server checks). */
	public boolean isAdminMenuAllowed() {
		return adminMenuAllowed;
	}

	/** Whether a full config snapshot arrived since the last reset. */
	public boolean hasConfigs() {
		return configsReceived;
	}

	/** The options of both server configs from the last snapshot, kept up to date by later updates. */
	public List<AdminConfigEntry> getConfigEntries() {
		return List.copyOf(configEntries.values());
	}

	/** The options of one config ({@link AdminConfigEntry#CONFIG_OPAC} or {@link AdminConfigEntry#CONFIG_TEAM_CLAIMS}). */
	public List<AdminConfigEntry> getConfigEntries(String configId) {
		return configEntries.values().stream().filter(entry -> entry.configId().equals(configId)).toList();
	}

	@Nullable
	public AdminConfigEntry getConfigEntry(String configId, String path) {
		return configEntries.get(configId + ":" + path);
	}

	public boolean hasTeams() {
		return teamsReceived;
	}

	public List<AdminTeamData> getTeams() {
		return teams;
	}

	/** The player last looked up or changed in the admin menu, null if none. */
	@Nullable
	public AdminPlayerData getPlayer() {
		return player;
	}

	/** The outcome of the last admin action (or failed player lookup), null if none. */
	@Nullable
	public ClientboundAdminResultPacket.Result getLastResult() {
		return lastResult;
	}

	/** The Team tab information from the last answer, null before the first one. */
	@Nullable
	public TeamInfoData getTeamInfo() {
		return teamInfo;
	}

	// ==================== Set by the packet handlers ====================

	public void setAdminMenuAllowed(boolean allowed) {
		adminMenuAllowed = allowed;
		if(!allowed)
			clearAdminData();
		revision++;
	}

	public void onConfigPacket(ClientboundAdminConfigPacket packet) {
		if(packet.isUpdate()) {
			Map<String, AdminConfigEntry> updated = new LinkedHashMap<>(configEntries);
			for(AdminConfigEntry entry : packet.getEntries())
				updated.put(entry.key(), entry);
			configEntries = updated;
			revision++;
			return;
		}
		if(packet.getSnapshotId() != pendingSnapshotId || packet.getPage() == 0) {
			pendingSnapshotId = packet.getSnapshotId();
			pendingPages.clear();
		}
		if(packet.getPage() != pendingPages.size())
			return;//a page is missing, wait for the next snapshot
		pendingPages.add(packet.getEntries());
		if(pendingPages.size() < packet.getPageCount())
			return;
		Map<String, AdminConfigEntry> entries = new LinkedHashMap<>();
		for(List<AdminConfigEntry> page : pendingPages)
			for(AdminConfigEntry entry : page)
				entries.put(entry.key(), entry);
		configEntries = entries;
		configsReceived = true;
		pendingPages.clear();
		pendingSnapshotId = -1;
		revision++;
	}

	public void setTeams(List<AdminTeamData> teams) {
		this.teams = List.copyOf(teams);
		teamsReceived = true;
		revision++;
	}

	public void setPlayer(@Nullable AdminPlayerData player) {
		this.player = player;
		revision++;
	}

	public void setLastResult(@Nullable ClientboundAdminResultPacket.Result result) {
		this.lastResult = result;
		revision++;
	}

	public void setTeamInfo(@Nullable TeamInfoData teamInfo) {
		this.teamInfo = teamInfo;
		revision++;
	}

	private void clearAdminData() {
		configEntries = Map.of();
		configsReceived = false;
		pendingSnapshotId = -1;
		pendingPages.clear();
		teams = List.of();
		teamsReceived = false;
		player = null;
		lastResult = null;
	}

	/** Forgets everything, when the client leaves a server. */
	public void reset() {
		adminMenuAllowed = false;
		clearAdminData();
		teamInfo = null;
		revision++;
	}

}
