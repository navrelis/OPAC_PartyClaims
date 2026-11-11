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
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.glfw.GLFW;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.gui.ConfigOptionRowListElement;
import xaero.pac.client.gui.MainMenu;
import xaero.pac.client.gui.PlayerConfigScreen;
import xaero.pac.client.gui.configview.ConfigViewLayout;
import xaero.pac.client.gui.hub.AdminTabScreen;
import xaero.pac.client.gui.hub.ChunkSelection;
import xaero.pac.client.gui.hub.ClaimAsToggle;
import xaero.pac.client.gui.hub.ClaimsTabScreen;
import xaero.pac.client.gui.hub.HubGridFit;
import xaero.pac.client.gui.hub.HubSidebar;
import xaero.pac.client.gui.hub.HubTab;
import xaero.pac.client.gui.hub.HubTabScreen;
import xaero.pac.client.gui.hub.HubTabs;
import xaero.pac.client.gui.hub.OverviewTabScreen;
import xaero.pac.client.gui.hub.SettingsTabScreen;
import xaero.pac.client.gui.hub.TeamHubLogic;
import xaero.pac.client.gui.hub.TeamTabScreen;
import xaero.pac.client.gui.hub.map.TerrainCache;
import xaero.pac.client.gui.party.PartyEntryList;
import xaero.pac.client.gui.party.PartyRow;
import xaero.pac.client.gui.party.PartyScreen;
import xaero.pac.client.parties.party.IClientPartyStorage;
import xaero.pac.client.teamclaims.TeamClaimsClientData;
import xaero.pac.client.teamclaims.TeamClaimsClientRequests;
import xaero.pac.client.world.capability.ClientWorldMainCapability;
import xaero.pac.client.world.capability.api.ClientWorldCapabilityTypes;
import xaero.pac.common.packet.teamclaims.AdminConfigEntry;
import xaero.pac.common.packet.teamclaims.AdminPlayerBonus;
import xaero.pac.common.packet.teamclaims.AdminPlayerData;
import xaero.pac.common.packet.teamclaims.TeamInfoData;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamRole;
import xaero.pac.teamclaims.twoplayertest.Barrier;
import xaero.pac.teamclaims.twoplayertest.ScenarioPlan;
import xaero.pac.teamclaims.twoplayertest.Snap;
import xaero.pac.teamclaims.twoplayertest.TwoPlayerTest;

import xaero.map.controls.ControlsRegister;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static xaero.pac.teamclaims.twoplayertest.ScenarioPlan.*;

/**
 * [Team Claims] The scripted scenario of one client (see {@link ScenarioPlan} for every assertion). Player1 is the op
 * and party owner, Player2 the plain member; they wait for each other at the points of a {@link Barrier}.
 * <p>
 * Every step reports exactly one assertion. It passes only when its {@link Verdict} says so for values the step
 * observed (on the client and in a server snapshot); an exception or a time-out fails it with the reason; a step whose
 * precondition failed is skipped with the reason. A barrier is always passed, also after a failure, so the other client
 * never waits in vain; when the other client is not there in time, the steps that need it fail.
 */
public final class Scenario implements Runnable {

	/** The size the game renders at during the test: GUI scale 4 needs at least 1280 x 960. */
	public static final int WIDTH = 1280;
	public static final int HEIGHT = 960;
	public static final int SCALE = 2;
	public static final String ADDRESS_PROPERTY = "opac.twoplayertest.server";
	public static final String PARTY_NAME = "TPTParty";
	public static final String FAKE_MEMBER = "Alice";
	public static final int MAX_ACTION_SIZE = 4;
	private static final String KEY_P = "key.keyboard.p";
	private static final String TEAM_CLAIMS_BASE = "teamClaims.teamClaimsBase";
	private static final String MAX_PARTY_INVITES = "serverConfig.parties.maxPartyInvites";

	private static final Duration SHORT = Duration.ofSeconds(10);
	private static final Duration MEDIUM = Duration.ofSeconds(20);
	private static final Duration OTHER_PLAYER = Duration.ofMinutes(12);

	private interface Body {
		Verdict run() throws Exception;
	}

	/**
	 * The outcome of a step: whether it passed, what it wanted and what it saw.
	 */
	private record Verdict(boolean pass, Object expected, Object actual, List<String> shots) {
	}

	private static Verdict verdict(boolean pass, Object expected, Object actual, String... shots) {
		List<String> list = new ArrayList<>();
		for(String shot : shots)
			if(shot != null)
				list.add(shot);
		return new Verdict(pass, expected, actual, list);
	}

	private final ClientDriver d;
	private final Minecraft mc;
	private final boolean first;
	private final String prefix;
	private volatile boolean done;

	// what the steps found out for the later ones
	private String dimension;
	private int chunkX;
	private int chunkZ;
	private double rankClickX;
	private double rankClickY;
	private List<String> rowOrderBefore;
	private PartyMemberRank expectedRank;

	public Scenario(ClientDriver driver) {
		this.d = driver;
		this.mc = driver.mc;
		this.first = TwoPlayerTest.PLAYER1.equals(driver.player);
		this.prefix = first ? "p1" : "p2";
	}

	public boolean isDone() {
		return done;
	}

	@Override
	public void run() {
		String why = "the scenario ran to its end";
		try {
			d.results.note("scenario of " + d.player + " starts");
			if(first)
				player1();
			else
				player2();
		} catch(Throwable t) {
			why = "the driver stopped: " + describe(t);
			d.results.note(why);
			d.barrier.abort(why);
		} finally {
			finish(why);
		}
	}

	/**
	 * Fails what was not reported, marks this client as finished and quits the game. Called once, by the scenario or by
	 * the watchdog.
	 */
	public synchronized void finish(String why) {
		if(done)
			return;
		done = true;
		d.results.finish(why);
		TwoPlayerTest.writeStatus(d.out, d.player + ".finished", why);
		mc.execute(mc::stop);
	}

	// ==================== the two scripts ====================

	private void player1() {
		if(!step(P1_JOIN, this::join)) {
			d.barrier.abort("Player1 could not join");
			return;
		}
		d.barrier.arrive("joined");
		Barrier.Outcome joined = d.barrier.await(d.other, "joined", OTHER_PLAYER);
		step(P1_PRECONDITIONS, () -> preconditions(joined));
		keySteps(P1_KEY_OPENS, P1_KEY_CLOSES, P1_KEY_TYPING);
		d.barrier.arrive("keys-done");

		step(P1_PARTY_CREATE, this::createParty);
		stepRequiring(P1_PARTY_FAKE_MEMBER, List.of(P1_PARTY_CREATE), this::addFakeMember);
		stepRequiring(P1_PARTY_INVITE, List.of(P1_PARTY_CREATE), this::invite);
		d.barrier.arrive("invited");
		Barrier.Outcome accepted = d.barrier.await(d.other, "accepted", OTHER_PLAYER);
		stepAfter(P1_PARTY_RANK_CLICK, accepted, "accepted", List.of(P1_PARTY_INVITE), this::rankClick);
		stepRequiring(P1_PARTY_ROWS_STABLE, List.of(P1_PARTY_RANK_CLICK), this::rowsStable);
		d.barrier.arrive("ranked");

		step(P1_OVERVIEW_CLAIM, this::overviewClaim);
		stepRequiring(P1_OVERVIEW_UNCLAIM, List.of(P1_OVERVIEW_CLAIM), this::overviewUnclaim);
		step(P1_SETTINGS_GROUPS, this::configGroups);
		step(P1_SETTINGS_SEARCH, this::configSearch);
		stepRequiring(P1_SETTINGS_TOGGLE, List.of(P1_SETTINGS_SEARCH), this::configToggle);
		stepRequiring(P1_SETTINGS_RESET_SHOWN, List.of(P1_SETTINGS_TOGGLE), this::configResetShown);
		stepRequiring(P1_SETTINGS_RESET, List.of(P1_SETTINGS_TOGGLE), this::configReset);
		d.run("close", () -> mc.setScreen(null));

		step(P1_CLAIMS_CLAIM_PRIVATE, this::claimPrivate);
		stepRequiring(P1_CLAIMS_UNCLAIM, List.of(P1_CLAIMS_CLAIM_PRIVATE), this::unclaim);
		stepRequiring(P1_CLAIMS_TOGGLE_TEAM, List.of(P1_PARTY_CREATE), this::toggleTeam);
		stepRequiring(P1_CLAIMS_CLAIM_TEAM, List.of(P1_CLAIMS_TOGGLE_TEAM), this::claimTeam);
		step(P1_CLAIMS_OVERSIZE, this::oversize);
		step(P1_CLAIMS_ZOOM, this::zoomShots);

		stepRequiring(P1_TEAM_VISIBLE, List.of(P1_PARTY_CREATE), this::teamTabOwner);
		stepRequiring(P1_TEAM_ROLE, List.of(P1_TEAM_VISIBLE), this::ownerChangesRole);
		d.barrier.arrive("role-changed");

		step(P1_ADMIN_VISIBLE, this::adminVisible);
		stepRequiring(P1_ADMIN_LIVE, List.of(P1_ADMIN_VISIBLE), this::adminLive);
		stepRequiring(P1_ADMIN_RESTART, List.of(P1_ADMIN_VISIBLE), this::adminRestart);
		stepRequiring(P1_ADMIN_TEAM_BONUS, List.of(P1_ADMIN_VISIBLE), this::adminTeamBonus);
		stepRequiring(P1_ADMIN_PLAYER_BONUS, List.of(P1_ADMIN_VISIBLE), this::adminPlayerBonus);
		stepRequiring(P1_ADMIN_SUGGESTION, List.of(P1_ADMIN_VISIBLE), this::adminSuggestion);
		d.barrier.arrive("admin-done");

		stepRequiring(P1_WM_BUTTON, List.of(P1_CLAIMS_TOGGLE_TEAM), this::mapButtonToggle);
		stepRequiring(P1_WM_ENTRIES, List.of(P1_PARTY_CREATE), this::mapEntries);
		stepRequiring(P1_WM_TEAM_ONCE, List.of(P1_WM_BUTTON, P1_WM_ENTRIES), this::mapClaimAsTeamOnce);
		stepRequiring(P1_WM_PRIVATE, List.of(P1_WM_ENTRIES), this::mapClaimAsPrivate);
		stepRequiring(P1_WM_TOGGLE_ENTRY, List.of(P1_WM_BUTTON, P1_WM_ENTRIES), this::mapToggleEntry);
		stepRequiring(P1_WM_OPEN_MENU, List.of(P1_WM_ENTRIES), this::mapOpenMenu);
		d.run("close", () -> mc.setScreen(null));

		shots(SHOT_TABS_PLAYER1);
		endSync();
	}

	private void player2() {
		if(!step(P2_JOIN, this::join)) {
			d.barrier.abort("Player2 could not join");
			return;
		}
		d.barrier.arrive("joined");
		d.barrier.await(d.other, "joined", OTHER_PLAYER);
		keySteps(P2_KEY_OPENS, P2_KEY_CLOSES, P2_KEY_TYPING);
		d.barrier.arrive("keys-done");

		Barrier.Outcome invited = d.barrier.await(d.other, "invited", OTHER_PLAYER);
		stepAfter(P2_PARTY_ACCEPT, invited, "invited", List.of(), this::accept);
		d.barrier.arrive("accepted");

		Barrier.Outcome roleChanged = d.barrier.await(d.other, "role-changed", OTHER_PLAYER);
		stepAfter(P2_TEAM_READ_ONLY, roleChanged, "role-changed", List.of(P2_PARTY_ACCEPT), this::memberReadOnly);
		stepRequiring(P2_TEAM_CLICK, List.of(P2_TEAM_READ_ONLY), this::memberClick);
		step(P2_ADMIN_HIDDEN, this::adminHidden);

		Barrier.Outcome adminDone = d.barrier.await(d.other, "admin-done", OTHER_PLAYER);
		stepAfter(P2_ADMIN_CRAFTED, adminDone, "admin-done", List.of(), this::crafted);
		step(P2_WM_BUTTON, this::mapButtonMember);
		stepRequiring(P2_WM_TEAM_ONCE, List.of(P2_PARTY_ACCEPT, P2_WM_BUTTON), this::mapClaimAsTeamMember);
		d.run("close", () -> mc.setScreen(null));

		shots(SHOT_TABS_PLAYER2);
		endSync();
	}

	private void endSync() {
		d.barrier.arrive("end");
		Barrier.Outcome outcome = d.barrier.await(d.other, "end", OTHER_PLAYER);
		d.results.note("end barrier: " + outcome);
	}

	// ==================== step plumbing ====================

	private boolean step(String id, Body body) {
		try {
			Verdict verdict = body.run();
			d.results.check(id, verdict.pass(), verdict.expected(), verdict.actual(), verdict.shots());
			return verdict.pass();
		} catch(Throwable t) {
			d.results.fail(id, "the step runs to its assertion", "EXCEPTION: " + describe(t) + " (screen: " + safeScreenName() + ")", failureShot(id));
			return false;
		}
	}

	private boolean stepRequiring(String id, List<String> requires, Body body) {
		for(String required : requires)
			if(!d.results.hasPassed(required)) {
				d.results.skip(id, "needs " + required + ", which " + (d.results.isReported(required) ? "is " + d.results.get(required).status() : "was not reported"));
				return false;
			}
		return step(id, body);
	}

	private boolean stepAfter(String id, Barrier.Outcome outcome, String point, List<String> requires, Body body) {
		if(outcome != Barrier.Outcome.REACHED) {
			d.results.fail(id, d.other + " reaches \"" + point + "\"", d.other + " did not: " + outcome, List.of());
			return false;
		}
		return stepRequiring(id, requires, body);
	}

	private List<String> failureShot(String id) {
		try {
			return List.of(d.screenshot("FAILED-" + id));
		} catch(Throwable ignored) {
			return List.of();
		}
	}

	private String safeScreenName() {
		try {
			return d.call("screen name", d::screenName);
		} catch(Throwable t) {
			return "unknown";
		}
	}

	static String describe(Throwable t) {
		StringWriter writer = new StringWriter();
		t.printStackTrace(new PrintWriter(writer));
		String[] lines = writer.toString().split("\\R");
		StringBuilder text = new StringBuilder();
		for(int i = 0; i < lines.length && i < 6; i++)
			text.append(i == 0 ? "" : " | ").append(lines[i].strip());
		return text.toString();
	}

	// ==================== client state ====================

	private static IClientPartyStorage<?, ?, ?> partyStorage() {
		return OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClientPartyStorage();
	}

	private boolean serverHasMod() {
		if(mc.level == null)
			return false;
		ClientWorldMainCapability mainCap = (ClientWorldMainCapability) OpenPartiesAndClaims.INSTANCE.getCapabilityHelper().getCapability(mc.level, ClientWorldCapabilityTypes.MAIN_CAP);
		return mainCap.getClientWorldData().serverHasMod();
	}

	private static boolean isHub(Screen screen) {
		return screen instanceof PartyScreen || screen instanceof HubTabScreen;
	}

	private void openTab(HubTab tab) {
		d.run("open the " + tab + " tab", () -> HubTabs.open(mc, tab, null, null));
	}

	/**
	 * Gets the entry of a page in the sidebar of an open hub screen, null if there is none.
	 */
	private static Button sidebarEntry(Screen screen, HubTab tab) {
		if(!isHub(screen))
			return null;
		HubSidebar sidebar = Ui.get(screen, "sidebar");
		String label = tab.getLabel().getString();
		for(Button entry : sidebar.getEntries())
			if(label.equals(entry.getMessage().getString()))
				return entry;
		return null;
	}

	/**
	 * Opens a page with a click on its entry in the sidebar of the open hub screen.
	 */
	private String clickTab(HubTab tab) {
		return d.clickWidget("the sidebar entry \"" + tab.getLabel().getString() + "\"", () -> sidebarEntry(d.requireScreen(), tab));
	}

	/**
	 * Goes to a page of the hub the way a player does: opens the hub (at its last page) if it is not open, then clicks
	 * the page in the sidebar.
	 */
	private void navigate(HubTab tab, Class<? extends Screen> page) {
		if(!d.call("hub?", () -> isHub(mc.screen))) {
			d.run("open the hub", () -> HubTabs.openLast(mc, null, null));
			d.await("the hub", SHORT, () -> isHub(mc.screen));
		}
		if(!d.call(tab + " page?", () -> page.isInstance(mc.screen)))
			clickTab(tab);
		d.await("the " + tab + " page", MEDIUM, () -> page.isInstance(mc.screen));
		d.waitFrames(3);
	}

	private void rememberOwnChunk() {
		d.run("own chunk", () -> {
			ChunkPos chunk = mc.player.chunkPosition();
			chunkX = chunk.x;
			chunkZ = chunk.z;
			dimension = mc.level.dimension().location().toString();
		});
	}

	private UUID myId() {
		return d.call("own uuid", () -> mc.player.getUUID());
	}

	// ==================== join ====================

	private Verdict join() {
		d.await("the game to finish loading", Duration.ofMinutes(8), () -> mc.getOverlay() == null && mc.screen != null);
		d.run("game options", () -> {
			mc.options.pauseOnLostFocus = false;
			mc.getToasts().clear();
		});
		int scale = d.setGuiScale(WIDTH, HEIGHT, SCALE);
		String address = System.getProperty(ADDRESS_PROPERTY, "localhost:25565");
		int attempts = 0;
		String lastScreen = "";
		while(true) {
			attempts++;
			d.run("connect to " + address, () -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
					new ServerData("OPAC two-player test", address, ServerData.Type.OTHER), false, null));
			d.poll(Duration.ofSeconds(90), () -> mc.player != null && mc.level != null || mc.screen instanceof DisconnectedScreen);
			if(d.call("connected?", () -> mc.player != null && mc.level != null))
				break;
			lastScreen = d.call("screen", () -> mc.screen == null ? "none" : mc.screen.getClass().getSimpleName() + " \"" + mc.screen.getTitle().getString() + "\"");
			d.results.note("connection attempt " + attempts + " failed, screen " + lastScreen);
			if(attempts >= 3)
				return verdict(false, "connected to " + address, "not connected after " + attempts + " attempts; last screen " + lastScreen, d.screenshot(prefix + "-join-failed"));
			ClientDriver.sleep(5000);
		}
		d.await("the OPAC handshake of the server", Duration.ofSeconds(60), this::serverHasMod);
		d.await("the party and claim data of the server", Duration.ofSeconds(60), () -> !partyStorage().isLoading()
				&& !OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClaimsManager().isLoading());
		d.waitTicks(20);
		String name = d.call("own name", () -> mc.player.getGameProfile().getName());
		String shot = d.screenshot(prefix + "-joined");
		return verdict(d.player.equals(name) && scale == SCALE, "connected as " + d.player + " with the OPAC handshake and synced data, GUI scale " + SCALE,
				"connected as " + name + " after " + attempts + " attempt(s), handshake and sync done, GUI scale " + scale, shot);
	}

	private Verdict preconditions(Barrier.Outcome otherJoined) {
		JsonObject s = d.snapshot("preconditions");
		String minMembers = Snap.running(s, "teamclaims:teamClaims.teamClaimsMinMembers");
		String maxAction = Snap.running(s, "opac:serverConfig.claims.maxSingleClaimActionSize");
		JsonObject p1 = Snap.player(s, TwoPlayerTest.PLAYER1);
		JsonObject p2 = Snap.player(s, TwoPlayerTest.PLAYER2);
		Boolean p1Op = Snap.bool(p1, "op");
		Boolean p1Admin = Snap.bool(p1, "adminMenu");
		Boolean p2Op = Snap.bool(p2, "op");
		Boolean p2Admin = Snap.bool(p2, "adminMenu");
		Boolean tcOverride = Snap.fileIsOverride(s, AdminConfigEntry.CONFIG_TEAM_CLAIMS);
		Boolean opacOverride = Snap.fileIsOverride(s, AdminConfigEntry.CONFIG_OPAC);
		Boolean capture = Snap.bool(s, "logCaptureInstalled");
		Boolean active = Snap.bool(s, "teamClaimsActive");
		int errors = Snap.errors(s).size();
		boolean pass = otherJoined == Barrier.Outcome.REACHED && "2".equals(minMembers) && String.valueOf(MAX_ACTION_SIZE).equals(maxAction)
				&& Boolean.TRUE.equals(p1Op) && Boolean.TRUE.equals(p1Admin) && Boolean.FALSE.equals(p2Op) && Boolean.FALSE.equals(p2Admin)
				&& Boolean.TRUE.equals(tcOverride) && Boolean.TRUE.equals(opacOverride) && Boolean.TRUE.equals(capture) && Boolean.TRUE.equals(active) && errors == 0;
		return verdict(pass, "Player2 joined; teamClaimsMinMembers=2; maxSingleClaimActionSize=" + MAX_ACTION_SIZE + "; both tomls from the world's serverconfig; Team Claims active;"
						+ " Player1 op+admin; Player2 neither; log capture on; no snapshot errors",
				"Player2 " + otherJoined + "; teamClaimsMinMembers=" + minMembers + "; maxSingleClaimActionSize=" + maxAction + "; overrides teamclaims=" + tcOverride
						+ " opac=" + opacOverride + "; Team Claims active=" + active + "; Player1 op=" + p1Op + " admin=" + p1Admin + "; Player2 op=" + p2Op + " admin=" + p2Admin
						+ "; log capture=" + capture + "; snapshot errors=" + Snap.errors(s));
	}

	// ==================== the P key ====================

	private void keySteps(String opensId, String closesId, String typingId) {
		step(opensId, this::keyOpens);
		step(closesId, this::keyCloses);
		step(typingId, this::keyTyping);
	}

	private Verdict keyOpens() {
		d.run("close screens", () -> mc.setScreen(null));
		d.waitTicks(5);
		String hubKey = d.call("hub key", () -> OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getKeyBindings().openHubMenu.saveString());
		String socialKey = d.call("social interactions key", () -> mc.options.keySocialInteractions.saveString());
		d.pressKey(GLFW.GLFW_KEY_P);
		d.poll(Duration.ofSeconds(3), () -> mc.screen != null);
		d.waitTicks(10);
		String shown = d.call("screen", d::screenName);
		boolean hub = d.call("hub open?", () -> isHub(mc.screen));
		String shot = d.screenshot(prefix + "-key-p-pressed");
		if(!hub)
			d.run("close what P opened", () -> mc.setScreen(null));
		boolean defaults = KEY_P.equals(hubKey) && KEY_P.equals(socialKey);
		return verdict(hub && defaults, "default bindings (hub " + KEY_P + ", Social Interactions " + KEY_P + "); after P a hub screen (PartyScreen or a HubTabScreen)",
				"bindings hub " + hubKey + ", Social Interactions " + socialKey + "; screen after P: " + shown, shot);
	}

	private Verdict keyCloses() {
		boolean openedByKey = d.call("hub open?", () -> isHub(mc.screen));
		if(!openedByKey) {
			d.run("open the hub", () -> HubTabs.openLast(mc, null, null));
			d.await("the hub screen", SHORT, () -> isHub(mc.screen));
		}
		d.waitTicks(5);
		String before = d.call("screen", d::screenName);
		d.pressKey(GLFW.GLFW_KEY_P);
		d.poll(Duration.ofSeconds(3), () -> mc.screen == null);
		d.waitTicks(5);
		String after = d.call("screen", d::screenName);
		boolean closed = d.call("closed?", () -> mc.screen == null);
		String shot = d.screenshot(prefix + "-key-p-close");
		if(!closed)
			d.run("close", () -> mc.setScreen(null));
		return verdict(closed, "no screen after P on the open hub", "before " + before + (openedByKey ? "" : " (opened by the driver, P had not opened it)") + ", after P: " + after, shot);
	}

	private Verdict keyTyping() {
		String hint = Ui.text("gui.xaero_pac_party_screen_create_name_hint");
		openTab(HubTab.PARTY);
		d.await("the Party tab with its create box", MEDIUM, () -> mc.screen instanceof PartyScreen && Ui.editBox(mc.screen, hint) != null);
		d.waitFrames(3);
		Screen before = d.call("screen", () -> mc.screen);
		String valueBefore = d.call("focus the create box", () -> {
			EditBox box = Ui.editBox(mc.screen, hint);
			Ui.click(mc.screen, box);
			if(!box.isFocused())
				throw new ClientDriver.DriverException("a click on the create box did not focus it");
			return box.getValue();
		});
		d.pressKey(GLFW.GLFW_KEY_P);
		d.typeChars("p");// the character event that GLFW sends after the key event
		d.waitTicks(10);
		Screen after = d.call("screen", () -> mc.screen);
		String valueAfter = d.call("box value", () -> {
			EditBox box = mc.screen == null ? null : Ui.editBox(mc.screen, hint);
			return box == null ? "(no create box)" : box.getValue();
		});
		String shot = d.screenshot(prefix + "-key-p-typing");
		// leave the box empty again
		if(after instanceof PartyScreen && !valueAfter.isEmpty() && !valueAfter.startsWith("("))
			for(int i = 0; i < valueAfter.length(); i++)
				d.pressKey(GLFW.GLFW_KEY_BACKSPACE);
		d.run("close", () -> mc.setScreen(null));
		boolean pass = after == before && (valueBefore + "p").equals(valueAfter);
		return verdict(pass, "the same Party screen stays open and the box holds \"" + valueBefore + "p\"",
				"screen " + (after == before ? "unchanged" : "changed to " + (after == null ? "none" : after.getClass().getSimpleName())) + ", box holds \"" + valueAfter + "\"", shot);
	}

	// ==================== party ====================

	private Verdict createParty() {
		String hint = Ui.text("gui.xaero_pac_party_screen_create_name_hint");
		openTab(HubTab.PARTY);
		d.await("the Party tab with its create box", MEDIUM, () -> mc.screen instanceof PartyScreen && Ui.editBox(mc.screen, hint) != null);
		d.replaceText("the create box", () -> Ui.editBox(mc.screen, hint), PARTY_NAME);
		String clicked = d.clickWidget("the Create button", () -> Ui.button(d.requireScreen(), Ui.text("gui.xaero_pac_party_screen_create")));
		d.await("the client to be in a party", MEDIUM, () -> partyStorage().getParty() != null);
		JsonObject s = d.awaitServer("party-created", SHORT, x -> Snap.partyOwnedBy(x, d.player) != null);
		JsonObject party = Snap.partyOwnedBy(s, d.player);
		d.waitTicks(10);
		String shot = d.screenshot("p1-party-created");
		String name = Snap.str(party, "name");
		String teamName = Snap.str(Snap.team(party), "teamName");
		boolean named = PARTY_NAME.equals(name) || PARTY_NAME.equals(teamName);
		return verdict(party != null && named, "a party owned by Player1, named " + PARTY_NAME + " (party name or team name)",
				party == null ? "no party of Player1 on the server (clicked " + clicked + ")" : "party name " + name + ", team name " + teamName + ", members " + Snap.memberNames(party), shot);
	}

	private Verdict addFakeMember() {
		d.sendCommand("opactest fakemember " + FAKE_MEMBER);
		JsonObject s = d.awaitServer("fake-member", SHORT, x -> Snap.member(Snap.partyOwnedBy(x, d.player), FAKE_MEMBER) != null);
		JsonObject party = Snap.partyOwnedBy(s, d.player);
		String rank = Snap.rank(party, FAKE_MEMBER);
		return verdict("MEMBER".equals(rank), FAKE_MEMBER + " is a MEMBER of Player1's party", "members " + Snap.memberNames(party));
	}

	private Verdict invite() {
		String hint = Ui.text("gui.xaero_pac_party_screen_invite_hint");
		d.await("the invite box of the Party tab", MEDIUM, () -> mc.screen instanceof PartyScreen && Ui.editBox(mc.screen, hint) != null);
		d.replaceText("the invite box", () -> Ui.editBox(mc.screen, hint), TwoPlayerTest.PLAYER2);
		String clicked = d.clickWidget("the Invite button", () -> Ui.button(d.requireScreen(), Ui.text("gui.xaero_pac_party_screen_invite")));
		JsonObject s = d.awaitServer("invited", SHORT, x -> Snap.isInvited(Snap.partyOwnedBy(x, d.player), TwoPlayerTest.PLAYER2));
		boolean invited = Snap.isInvited(Snap.partyOwnedBy(s, d.player), TwoPlayerTest.PLAYER2);
		return verdict(invited, "Player2 is invited to Player1's party", (invited ? "invited" : "not invited") + " after a click on " + clicked);
	}

	/**
	 * Gets the Accept button of the received invitation from this owner, once the row has drawn (positioned) it.
	 */
	private Button acceptButtonOf(String owner) {
		if(!(mc.screen instanceof PartyScreen screen))
			return null;
		PartyEntryList list = Ui.get(screen, "list");
		if(list == null)
			return null;
		for(PartyEntryList.PartyEntry entry : list.children()) {
			if(!entry.getClass().getSimpleName().equals("ReceivedInviteEntry"))
				continue;
			PartyRow.ReceivedInvite invite = Ui.get(entry, "invite");
			Button accept = Ui.get(entry, "acceptButton");
			if(owner.equals(invite.ownerName()) && accept.getY() > 0)
				return accept;
		}
		return null;
	}

	private Verdict accept() {
		openTab(HubTab.PARTY);
		d.await("Player1's invitation with an Accept button", Duration.ofSeconds(30), () -> acceptButtonOf(TwoPlayerTest.PLAYER1) != null);
		String clicked = d.clickWidget("the Accept button", () -> acceptButtonOf(TwoPlayerTest.PLAYER1));
		d.await("the client to be in Player1's party", MEDIUM, () -> partyStorage().getParty() != null);
		JsonObject s = d.awaitServer("accepted", SHORT, x -> Snap.member(Snap.partyOwnedBy(x, TwoPlayerTest.PLAYER1), d.player) != null);
		JsonObject party = Snap.partyOwnedBy(s, TwoPlayerTest.PLAYER1);
		d.waitTicks(10);
		String shot = d.screenshot("p2-party-joined");
		String rank = Snap.rank(party, d.player);
		return verdict("MEMBER".equals(rank), "Player2 is a MEMBER of Player1's party", "after a click on " + clicked + ": members " + Snap.memberNames(party), shot);
	}

	/**
	 * A member row of the party list as it is on the screen.
	 */
	private record MemberRow(int index, PartyRow.Member member, Button rankButton) {
	}

	private List<MemberRow> memberRows() {
		List<MemberRow> rows = new ArrayList<>();
		if(!(mc.screen instanceof PartyScreen screen))
			return rows;
		PartyEntryList list = Ui.get(screen, "list");
		if(list == null)
			return rows;
		List<PartyEntryList.PartyEntry> entries = list.children();
		for(int i = 0; i < entries.size(); i++) {
			PartyEntryList.PartyEntry entry = entries.get(i);
			if(entry.getClass().getSimpleName().equals("MemberEntry"))
				rows.add(new MemberRow(i, Ui.get(entry, "member"), Ui.get(entry, "rankButton")));
		}
		return rows;
	}

	private MemberRow memberRow(String name) {
		for(MemberRow row : memberRows())
			if(name.equals(row.member().name()))
				return row;
		return null;
	}

	private static List<String> names(List<MemberRow> rows) {
		List<String> names = new ArrayList<>();
		for(MemberRow row : rows)
			names.add(row.member().name());
		return names;
	}

	private Verdict rankClick() {
		JsonObject s0 = d.snapshot("before-rank");
		JsonObject party0 = Snap.partyOwnedBy(s0, d.player);
		if(Snap.member(party0, TwoPlayerTest.PLAYER2) == null)
			return verdict(false, "Player2 is a member of Player1's party", "members " + Snap.memberNames(party0));
		if(!d.call("party screen?", () -> mc.screen instanceof PartyScreen))
			openTab(HubTab.PARTY);
		d.await("Player2's row with a rank button", MEDIUM, () -> {
			MemberRow row = memberRow(TwoPlayerTest.PLAYER2);
			return row != null && row.rankButton() != null && row.rankButton().getY() > 0;
		});
		d.waitFrames(3);
		Object[] before = d.call("rows before the click", () -> {
			MemberRow row = memberRow(TwoPlayerTest.PLAYER2);
			return new Object[]{names(memberRows()), row.member().nextRank(), Ui.centerX(row.rankButton()), Ui.centerY(row.rankButton()), Ui.describe(row.rankButton())};
		});
		@SuppressWarnings("unchecked")
		List<String> order = (List<String>) before[0];
		PartyMemberRank next = (PartyMemberRank) before[1];
		double x = (double) before[2];
		double y = (double) before[3];
		if(next == null)
			return verdict(false, "Player2's row offers a next rank", "no next rank on " + before[4]);
		String rank2Before = Snap.rank(party0, TwoPlayerTest.PLAYER2);
		String aliceBefore = Snap.rank(party0, FAKE_MEMBER);
		String ownerBefore = Snap.str(Snap.obj(party0, "owner"), "name");
		boolean taken = d.call("click Player2's rank button", () -> Ui.clickAt(mc.screen, x, y));
		JsonObject s1 = d.awaitServer("after-rank", Duration.ofSeconds(15), s -> next.name().equals(Snap.rank(Snap.partyOwnedBy(s, d.player), TwoPlayerTest.PLAYER2)));
		JsonObject party1 = Snap.partyOwnedBy(s1, d.player);
		rankClickX = x;
		rankClickY = y;
		rowOrderBefore = order;
		expectedRank = next;
		boolean pass = taken && next.name().equals(Snap.rank(party1, TwoPlayerTest.PLAYER2)) && Objects.equals(aliceBefore, Snap.rank(party1, FAKE_MEMBER))
				&& Objects.equals(ownerBefore, Snap.str(Snap.obj(party1, "owner"), "name"));
		return verdict(pass, "Player2 " + rank2Before + " -> " + next + "; " + FAKE_MEMBER + " stays " + aliceBefore + "; owner stays " + ownerBefore,
				"click on " + before[4] + " at " + x + "," + y + (taken ? "" : " (the screen did not take it)") + "; rows " + order + "; server now " + Snap.memberNames(party1));
	}

	private Verdict rowsStable() {
		String rankText = PartyEntryList.getRankName(expectedRank, false).getString();
		d.await("Player2's new rank on the client", Duration.ofSeconds(15), () -> {
			IPartyMemberAPI member = partyStorage().getParty() == null ? null : partyStorage().getParty().getMemberInfo(memberId(TwoPlayerTest.PLAYER2));
			return member != null && member.getRank() == expectedRank;
		});
		d.await("Player2's row to show the new rank", SHORT, () -> {
			MemberRow row = memberRow(TwoPlayerTest.PLAYER2);
			return row != null && row.rankButton() != null && rankText.equals(row.rankButton().getMessage().getString()) && row.rankButton().getY() > 0;
		});
		d.waitFrames(3);
		Object[] after = d.call("rows after the click", () -> {
			PartyScreen screen = (PartyScreen) mc.screen;
			PartyEntryList list = Ui.get(screen, "list");
			Optional<GuiEventListener> under = list.getChildAt(rankClickX, rankClickY);
			String rowUnder = "none";
			if(under.isPresent() && under.get().getClass().getSimpleName().equals("MemberEntry"))
				rowUnder = ((PartyRow.Member) Ui.get(under.get(), "member")).name();
			else if(under.isPresent())
				rowUnder = under.get().getClass().getSimpleName();
			String buttonUnder = "none";
			for(MemberRow row : memberRows())
				if(row.rankButton() != null && Ui.contains(row.rankButton(), rankClickX, rankClickY))
					buttonUnder = row.member().name() + " (" + row.rankButton().getMessage().getString() + ")";
			return new Object[]{names(memberRows()), rowUnder, buttonUnder};
		});
		String shot = d.screenshot("p1-party-after-rank-click");
		@SuppressWarnings("unchecked")
		List<String> order = (List<String>) after[0];
		boolean pass = order.equals(rowOrderBefore) && TwoPlayerTest.PLAYER2.equals(after[1]) && (TwoPlayerTest.PLAYER2 + " (" + rankText + ")").equals(after[2]);
		return verdict(pass, "rows " + rowOrderBefore + "; the row and the rank button under the clicked point are Player2's, showing " + rankText,
				"rows " + order + "; row under the point: " + after[1] + "; rank button under the point: " + after[2], shot);
	}

	private UUID memberId(String name) {
		for(MemberRow row : memberRows())
			if(name.equals(row.member().name()))
				return row.member().id();
		return new UUID(0, 0);
	}

	// ==================== claims ====================

	/**
	 * Where the chunks of the grid of the Claims tab are on the screen.
	 */
	private record Grid(int left, int top, int cell, int radius, int centerX, int centerZ) {
		boolean holds(int x, int z) {
			return Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius;
		}

		double screenX(int chunkX) {
			return left + (chunkX - (centerX - radius)) * cell + cell / 2.0;
		}

		double screenY(int chunkZ) {
			return top + (chunkZ - (centerZ - radius)) * cell + cell / 2.0;
		}
	}

	private Grid grid(ClaimsTabScreen screen) {
		HubGridFit.Fit fit = Ui.get(screen, "fit");
		return new Grid(Ui.getInt(screen, "gridLeft"), Ui.getInt(screen, "gridTop"), fit.cell(), fit.radius(), Ui.getInt(screen, "centerX"), Ui.getInt(screen, "centerZ"));
	}

	private ClaimsTabScreen claimsScreen() {
		if(!(mc.screen instanceof ClaimsTabScreen screen))
			throw new ClientDriver.DriverException("the Claims tab is not open but " + d.screenName());
		return screen;
	}

	private void openClaimsTab() {
		if(!d.call("claims tab?", () -> mc.screen instanceof ClaimsTabScreen)) {
			if(d.call("hub?", () -> isHub(mc.screen)))
				clickTab(HubTab.CLAIMS);
			else
				openTab(HubTab.CLAIMS);
		}
		d.await("the Claims tab, ready, with the team info", MEDIUM, () -> mc.screen instanceof ClaimsTabScreen screen && "READY".equals(Ui.enumName(screen, "state"))
				&& TeamClaimsClientData.INSTANCE.getTeamInfo() != null);
		d.waitFrames(3);
		rememberOwnChunk();
	}

	private Button actionButton(String text) {
		return Ui.button(claimsScreen(), text);
	}

	private ChunkSelection selection() {
		return Ui.get(claimsScreen(), "selection");
	}

	/**
	 * Selects one chunk with a click on the grid (clearing the selection first, since a click on the one selected chunk
	 * deselects it).
	 */
	private void selectChunk(int x, int z) {
		d.run("select chunk " + x + "," + z, () -> {
			ClaimsTabScreen screen = claimsScreen();
			if(selection() != null)
				Ui.click(screen, Ui.button(screen, Ui.text("gui.xaero_pac_hub_clear_selection")));
			Grid grid = grid(screen);
			if(!grid.holds(x, z))
				throw new ClientDriver.DriverException("chunk " + x + "," + z + " is not on the grid " + grid);
			Ui.clickAt(screen, grid.screenX(x), grid.screenY(z));
			if(!ChunkSelection.single(x, z).equals(selection()))
				throw new ClientDriver.DriverException("a click on chunk " + x + "," + z + " selected " + selection());
		});
		d.waitTicks(2);
	}

	/**
	 * Drags a selection over the grid, from one chunk to another.
	 */
	private void dragSelection(int x0, int z0, int x1, int z1) {
		d.run("drag " + x0 + "," + z0 + " -> " + x1 + "," + z1, () -> {
			ClaimsTabScreen screen = claimsScreen();
			if(selection() != null)
				Ui.click(screen, Ui.button(screen, Ui.text("gui.xaero_pac_hub_clear_selection")));
			Grid grid = grid(screen);
			double sx = grid.screenX(x0), sy = grid.screenY(z0), ex = grid.screenX(x1), ey = grid.screenY(z1);
			screen.mouseClicked(sx, sy, 0);
			screen.mouseDragged(ex, ey, 0, ex - sx, ey - sy);
			screen.mouseReleased(ex, ey, 0);
		});
		d.waitTicks(2);
	}

	private String claimAsText() {
		Button button = Ui.get(claimsScreen(), "claimAsButton");
		return button.getMessage().getString();
	}

	private Verdict claimPrivate() {
		openClaimsTab();
		String privateText = Ui.text("gui.xaero_pac_hub_claim_as_private");
		if(!privateText.equals(d.call("claim as", this::claimAsText))) {
			d.clickWidget("the claim-as toggle", () -> Ui.get(claimsScreen(), "claimAsButton"));
			d.await("Claim as: Private", SHORT, () -> privateText.equals(claimAsText()));
		}
		selectChunk(chunkX, chunkZ);
		String clicked = d.clickWidget("the Claim button", () -> actionButton(MainMenu.CLAIM.getString()));
		JsonObject s = d.awaitServer("claimed-private", Duration.ofSeconds(15), x -> Snap.claimAt(x, dimension, chunkX, chunkZ) != null);
		JsonObject claim = Snap.claimAt(s, dimension, chunkX, chunkZ);
		d.waitTicks(10);
		String shot = d.screenshot("p1-claims-private-claim");
		boolean pass = claim != null && myId().toString().equals(Snap.str(claim, "owner")) && Boolean.FALSE.equals(Snap.bool(claim, "teamClaim"));
		return verdict(pass, "chunk " + chunkX + "," + chunkZ + ": private claim of Player1 (teamClaim=false)", "after a click on " + clicked + ": " + Snap.describeClaim(claim), shot);
	}

	private Verdict unclaim() {
		if(!ChunkSelection.single(chunkX, chunkZ).equals(d.call("selection", this::selection)))
			selectChunk(chunkX, chunkZ);
		String clicked = d.clickWidget("the Unclaim button", () -> actionButton(MainMenu.UNCLAIM.getString()));
		JsonObject s = d.awaitServer("unclaimed", Duration.ofSeconds(15), x -> Snap.claimAt(x, dimension, chunkX, chunkZ) == null);
		JsonObject claim = Snap.claimAt(s, dimension, chunkX, chunkZ);
		return verdict(claim == null, "chunk " + chunkX + "," + chunkZ + " unclaimed", "after a click on " + clicked + ": " + Snap.describeClaim(claim));
	}

	private Verdict toggleTeam() {
		openClaimsTab();
		String teamText = Ui.text("gui.xaero_pac_hub_claim_as_team");
		String before = d.call("claim as", this::claimAsText);
		String clicked = d.clickWidget("the claim-as toggle", () -> Ui.get(claimsScreen(), "claimAsButton"));
		boolean shown = d.poll(SHORT, () -> teamText.equals(claimAsText()) && ClaimAsToggle.isTeamSelected());
		String after = d.call("claim as", this::claimAsText);
		JsonObject s = d.awaitServer("used-sub", SHORT, x -> {
			String teamSub = Snap.str(Snap.team(Snap.partyOwnedBy(x, d.player)), "subConfigId");
			return teamSub != null && teamSub.equals(Snap.str(Snap.playerData(x, d.player), "usedSub"));
		});
		String teamSub = Snap.str(Snap.team(Snap.partyOwnedBy(s, d.player)), "subConfigId");
		String usedSub = Snap.str(Snap.playerData(s, d.player), "usedSub");
		String shot = d.screenshot("p1-claims-team-selected");
		boolean pass = shown && teamSub != null && teamSub.equals(usedSub);
		return verdict(pass, "button \"" + teamText + "\" and the player's claims.usedSub = the team sub-config on the server",
				"button \"" + before + "\" -> \"" + after + "\" (clicked " + clicked + "); server usedSub " + usedSub + ", team sub-config " + teamSub, shot);
	}

	private Verdict claimTeam() {
		selectChunk(chunkX, chunkZ);
		String clicked = d.clickWidget("the Claim button", () -> actionButton(MainMenu.CLAIM.getString()));
		JsonObject s = d.awaitServer("claimed-team", Duration.ofSeconds(15), x -> Snap.claimAt(x, dimension, chunkX, chunkZ) != null);
		JsonObject claim = Snap.claimAt(s, dimension, chunkX, chunkZ);
		d.waitTicks(10);
		String shot = d.screenshot("p1-claims-team-claim");
		boolean pass = claim != null && myId().toString().equals(Snap.str(claim, "owner")) && Boolean.TRUE.equals(Snap.bool(claim, "teamClaim"));
		return verdict(pass, "chunk " + chunkX + "," + chunkZ + ": team claim (teamClaim=true) made by Player1", "after a click on " + clicked + ": " + Snap.describeClaim(claim), shot);
	}

	private String actionStates() {
		List<String> states = new ArrayList<>();
		for(var label : List.of(MainMenu.CLAIM, MainMenu.UNCLAIM, MainMenu.FORCELOAD, MainMenu.UNFORCELOAD)) {
			Button button = actionButton(label.getString());
			states.add(label.getString() + "=" + (button == null ? "missing" : button.active ? "active" : "inactive"));
		}
		return String.join(", ", states);
	}

	private boolean allActions(boolean active) {
		for(var label : List.of(MainMenu.CLAIM, MainMenu.UNCLAIM, MainMenu.FORCELOAD, MainMenu.UNFORCELOAD)) {
			Button button = actionButton(label.getString());
			if(button == null || button.active != active)
				return false;
		}
		return true;
	}

	private Verdict oversize() {
		openClaimsTab();
		Integer limit = d.call("max action size", () -> {
			TeamInfoData info = TeamClaimsClientData.INSTANCE.getTeamInfo();
			return info == null ? null : info.maxClaimActionSize();
		});
		dragSelection(chunkX - 1, chunkZ - 1, chunkX + 1, chunkZ + 1);
		Object[] big = d.call("3x3 state", () -> new Object[]{selection() == null ? 0L : selection().count(), allActions(false), actionStates()});
		String shot = d.screenshot("p1-claims-oversize");
		dragSelection(chunkX, chunkZ, chunkX + 1, chunkZ);
		Object[] small = d.call("2x1 state", () -> new Object[]{selection() == null ? 0L : selection().count(), allActions(true), actionStates()});
		d.run("clear the selection", () -> Ui.click(claimsScreen(), Ui.button(claimsScreen(), Ui.text("gui.xaero_pac_hub_clear_selection"))));
		boolean pass = Objects.equals(limit, MAX_ACTION_SIZE) && Long.valueOf(9).equals(big[0]) && Boolean.TRUE.equals(big[1])
				&& Long.valueOf(2).equals(small[0]) && Boolean.TRUE.equals(small[1]);
		return verdict(pass, "limit " + MAX_ACTION_SIZE + "; 9 chunks: all four action buttons inactive; 2 chunks: all four active",
				"limit " + limit + "; " + big[0] + " chunks: " + big[2] + "; " + small[0] + " chunks: " + small[2], shot);
	}

	// ==================== team ====================

	private TeamTabScreen teamScreen() {
		if(!(mc.screen instanceof TeamTabScreen screen))
			throw new ClientDriver.DriverException("the Team tab is not open but " + d.screenName());
		return screen;
	}

	private Button roleButton(TeamAction action) {
		Map<TeamAction, Button> buttons = Ui.get(teamScreen(), "roleButtons");
		return buttons.get(action);
	}

	private static String roleText(String roleName) {
		return Ui.text("gui.xaero_pac_hub_team_role_" + roleName.toLowerCase(Locale.ROOT));
	}

	private Verdict teamTabOwner() {
		if(!d.call("hub?", () -> isHub(mc.screen)))
			openTab(HubTab.PARTY);
		boolean listed = d.poll(MEDIUM, () -> sidebarEntry(mc.screen, HubTab.TEAM) != null);
		if(!listed)
			return verdict(false, "a Team button in the tab bar", "none on " + d.call("screen", d::screenName), d.screenshot("p1-team-tab-missing"));
		String clicked = clickTab(HubTab.TEAM);
		boolean ready = d.poll(MEDIUM, () -> mc.screen instanceof TeamTabScreen screen && "READY".equals(Ui.enumName(screen, "state")));
		String state = d.call("state", () -> mc.screen instanceof TeamTabScreen screen ? "TeamTabScreen " + Ui.enumName(screen, "state") : d.screenName());
		String shot = d.screenshot("p1-team-tab");
		return verdict(ready, "the Team tab opens and is READY", "clicked " + clicked + "; " + state, shot);
	}

	private Verdict ownerChangesRole() {
		JsonObject s0 = d.snapshot("before-role");
		String before = Snap.role(Snap.partyOwnedBy(s0, d.player), TeamAction.CLAIM.id());
		if(before == null)
			return verdict(false, "the team of Player1 has a claim role", "none in the snapshot");
		TeamRole expected = TeamHubLogic.nextRole(TeamRole.valueOf(before));
		String clicked = d.clickWidget("the Claim role button", () -> roleButton(TeamAction.CLAIM));
		JsonObject s1 = d.awaitServer("after-role", Duration.ofSeconds(15), x -> expected.name().equals(Snap.role(Snap.partyOwnedBy(x, d.player), TeamAction.CLAIM.id())));
		String after = Snap.role(Snap.partyOwnedBy(s1, d.player), TeamAction.CLAIM.id());
		boolean shown = d.poll(SHORT, () -> roleText(expected.name()).equals(roleButton(TeamAction.CLAIM).getMessage().getString()));
		String shot = d.screenshot("p1-team-role-changed");
		return verdict(expected.name().equals(after) && shown, "claim role " + before + " -> " + expected + " on the server and on the button",
				"after a click on " + clicked + ": server " + after + ", button " + (shown ? "shows it" : "does not show it"), shot);
	}

	private Verdict memberReadOnly() {
		openTab(HubTab.PARTY);
		boolean listed = d.poll(MEDIUM, () -> sidebarEntry(mc.screen, HubTab.TEAM) != null);
		if(!listed)
			return verdict(false, "a Team button in Player2's tab bar", "none on " + d.call("screen", d::screenName), d.screenshot("p2-team-tab-missing"));
		clickTab(HubTab.TEAM);
		d.await("the Team tab, ready", MEDIUM, () -> mc.screen instanceof TeamTabScreen screen && "READY".equals(Ui.enumName(screen, "state")));
		JsonObject s = d.snapshot("team-roles");
		String serverRole = Snap.role(Snap.partyOwnedBy(s, TwoPlayerTest.PLAYER1), TeamAction.CLAIM.id());
		String expectedText = serverRole == null ? "?" : roleText(serverRole);
		boolean shown = d.poll(MEDIUM, () -> expectedText.equals(roleButton(TeamAction.CLAIM).getMessage().getString()));
		String states = d.call("role buttons", () -> {
			List<String> list = new ArrayList<>();
			for(TeamAction action : TeamAction.values())
				list.add(action.id() + "=" + Ui.describe(roleButton(action)));
			TeamInfoData info = TeamClaimsClientData.INSTANCE.getTeamInfo();
			list.add("canEdit=" + (info == null ? "no team info" : info.canEdit()));
			return String.join("; ", list);
		});
		boolean readOnly = d.call("read-only?", () -> {
			for(TeamAction action : TeamAction.values())
				if(roleButton(action).active)
					return false;
			TeamInfoData info = TeamClaimsClientData.INSTANCE.getTeamInfo();
			return info != null && !info.canEdit();
		});
		String shot = d.screenshot("p2-team-read-only");
		boolean pass = shown && readOnly && serverRole != null && !TeamRole.DEFAULT.name().equals(serverRole);
		return verdict(pass, "the claim role the owner set (not the default " + TeamRole.DEFAULT + ") shows as \"" + expectedText + "\"; all role buttons inactive; canEdit=false",
				"server claim role " + serverRole + "; " + (shown ? "shown" : "NOT shown") + "; " + states, shot);
	}

	private Verdict memberClick() {
		JsonObject s0 = d.snapshot("before-member-click");
		String before = Snap.role(Snap.partyOwnedBy(s0, TwoPlayerTest.PLAYER1), TeamAction.CLAIM.id());
		String clicked = d.call("click the inactive Claim role button", () -> {
			Button button = roleButton(TeamAction.CLAIM);
			String description = Ui.describe(button);
			Ui.clickAt(teamScreen(), Ui.centerX(button), Ui.centerY(button));
			return description;
		});
		d.waitTicks(40);
		JsonObject s1 = d.snapshot("after-member-click");
		String after = Snap.role(Snap.partyOwnedBy(s1, TwoPlayerTest.PLAYER1), TeamAction.CLAIM.id());
		boolean stillInactive = d.call("still inactive?", () -> !roleButton(TeamAction.CLAIM).active);
		return verdict(before != null && before.equals(after) && stillInactive, "claim role stays " + before + " and the button stays inactive",
				"clicked " + clicked + "; claim role now " + after + "; button " + (stillInactive ? "inactive" : "ACTIVE"));
	}

	// ==================== admin ====================

	private Verdict adminHidden() {
		openTab(HubTab.PARTY);
		d.waitTicks(10);
		Object[] state = d.call("admin tab state", () -> new Object[]{TeamClaimsClientData.INSTANCE.isAdminMenuAllowed(), HubTabs.isAdminTabVisible(),
				Ui.buttonsWithText(mc.screen, HubTab.ADMIN.getLabel().getString()).size()});
		String shot = d.screenshot("p2-no-admin-tab");
		openTab(HubTab.ADMIN);
		d.waitTicks(5);
		String opened = d.call("screen", d::screenName);
		boolean adminOpened = d.call("admin screen?", () -> mc.screen instanceof AdminTabScreen);
		d.run("close", () -> mc.setScreen(null));
		boolean pass = Boolean.FALSE.equals(state[0]) && Boolean.FALSE.equals(state[1]) && Integer.valueOf(0).equals(state[2]) && !adminOpened;
		return verdict(pass, "server flag off, tab not visible, no Admin button, and opening the Admin tab falls back to another tab",
				"flag " + state[0] + ", visible " + state[1] + ", Admin buttons " + state[2] + ", opening it gives " + opened, shot);
	}

	private Verdict adminVisible() {
		boolean allowed = d.poll(SHORT, () -> TeamClaimsClientData.INSTANCE.isAdminMenuAllowed());
		if(!d.call("hub?", () -> isHub(mc.screen)))
			openTab(HubTab.PARTY);
		boolean listed = d.poll(SHORT, () -> sidebarEntry(mc.screen, HubTab.ADMIN) != null);
		if(!listed)
			return verdict(false, "the server flag and an Admin button in the tab bar", "flag " + allowed + ", no Admin button on " + d.call("screen", d::screenName),
					d.screenshot("p1-admin-tab-missing"));
		String clicked = clickTab(HubTab.ADMIN);
		boolean open = d.poll(SHORT, () -> mc.screen instanceof AdminTabScreen);
		String shot = d.screenshot("p1-admin-tab");
		return verdict(allowed && open, "server flag on, Admin button in the tab bar, the Admin tab opens", "flag " + allowed + ", clicked " + clicked + ", screen " + d.call("screen", d::screenName), shot);
	}

	private AdminTabScreen adminScreen() {
		if(!(mc.screen instanceof AdminTabScreen screen))
			throw new ClientDriver.DriverException("the Admin tab is not open but " + d.screenName());
		return screen;
	}

	/**
	 * Opens a section of the Admin tab with its button (a section that is open already has an inactive button).
	 */
	private void adminSection(String labelKey) {
		if(!d.call("admin tab?", () -> mc.screen instanceof AdminTabScreen)) {
			openTab(HubTab.ADMIN);
			d.await("the Admin tab", SHORT, () -> mc.screen instanceof AdminTabScreen);
		}
		String label = Ui.text(labelKey);
		d.waitFrames(2);
		boolean open = d.call("section open?", () -> {
			Button button = Ui.button(adminScreen(), label);
			if(button == null)
				throw new ClientDriver.DriverException("no section button \"" + label + "\"");
			return !button.active;
		});
		if(!open)
			d.clickWidget("the section button \"" + label + "\"", () -> Ui.button(adminScreen(), label));
		d.await("the section \"" + label + "\" to be open", SHORT, () -> mc.screen instanceof AdminTabScreen screen && Ui.button(screen, label) != null && !Ui.button(screen, label).active);
		d.waitFrames(2);
	}

	/**
	 * Narrows the option list with the search box, then types a value into the box of an option and presses Enter.
	 */
	private void setAdminOption(String filter, String path, String value) {
		String filterHint = Ui.text("gui.xaero_pac_hub_admin_filter_hint");
		d.replaceText("the search box", () -> Ui.editBox(adminScreen(), filterHint), filter);
		d.await("the box of " + path, SHORT, () -> Ui.editBox(adminScreen(), path) != null);
		d.waitFrames(2);
		d.replaceText("the box of " + path, () -> Ui.editBox(adminScreen(), path), value);
		d.pressKey(GLFW.GLFW_KEY_ENTER);
	}

	private Verdict adminLive() {
		adminSection("gui.xaero_pac_hub_admin_section_team_claims");
		d.await("the config options from the server", MEDIUM, () -> TeamClaimsClientData.INSTANCE.hasConfigs());
		String key = AdminConfigEntry.CONFIG_TEAM_CLAIMS + ":" + TEAM_CLAIMS_BASE;
		JsonObject s0 = d.snapshot("before-live-option");
		String before = Snap.running(s0, key);
		Integer limitBefore = Snap.teamBudget(Snap.partyOwnedBy(s0, d.player), "claimLimit");
		setAdminOption("teamClaimsBase", TEAM_CLAIMS_BASE, "777");
		JsonObject s1 = d.awaitServer("after-live-option", Duration.ofSeconds(15), x -> "777".equals(Snap.running(x, key)));
		String after = Snap.running(s1, key);
		Integer limitAfter = Snap.teamBudget(Snap.partyOwnedBy(s1, d.player), "claimLimit");
		String file = Snap.fileValue(s1, AdminConfigEntry.CONFIG_TEAM_CLAIMS, TEAM_CLAIMS_BASE);
		boolean clientShows = d.poll(SHORT, () -> {
			AdminConfigEntry entry = TeamClaimsClientData.INSTANCE.getConfigEntry(AdminConfigEntry.CONFIG_TEAM_CLAIMS, TEAM_CLAIMS_BASE);
			return entry != null && "777".equals(entry.value());
		});
		String shot = d.screenshot("p1-admin-live-option");
		boolean pass = "500".equals(before) && "777".equals(after) && "777".equals(file) && limitBefore != null && limitAfter != null && limitAfter == limitBefore + 277 && clientShows;
		return verdict(pass, TEAM_CLAIMS_BASE + " 500 -> 777 running and in the file, team claim limit +277, the client entry shows 777",
				"running " + before + " -> " + after + ", file " + file + ", team claim limit " + limitBefore + " -> " + limitAfter + ", client entry " + (clientShows ? "777" : "not updated"), shot);
	}

	private Verdict adminRestart() {
		adminSection("gui.xaero_pac_hub_admin_section_opac");
		d.await("the config options from the server", MEDIUM, () -> TeamClaimsClientData.INSTANCE.hasConfigs());
		String key = AdminConfigEntry.CONFIG_OPAC + ":" + MAX_PARTY_INVITES;
		JsonObject s0 = d.snapshot("before-restart-option");
		String before = Snap.running(s0, key);
		setAdminOption("maxPartyInvites", MAX_PARTY_INVITES, "17");
		JsonObject s1 = d.awaitServer("after-restart-option", Duration.ofSeconds(15), x -> "17".equals(Snap.fileValue(x, AdminConfigEntry.CONFIG_OPAC, MAX_PARTY_INVITES)));
		String running = Snap.running(s1, key);
		String pending = Snap.pending(s1, key);
		String file = Snap.fileValue(s1, AdminConfigEntry.CONFIG_OPAC, MAX_PARTY_INVITES);
		Boolean restartOnly = Snap.restartOnly(s1, key);
		boolean clientPending = d.poll(SHORT, () -> {
			AdminConfigEntry entry = TeamClaimsClientData.INSTANCE.getConfigEntry(AdminConfigEntry.CONFIG_OPAC, MAX_PARTY_INVITES);
			return entry != null && "17".equals(entry.pendingValue()) && "16".equals(entry.value());
		});
		d.waitFrames(3);
		String shot = d.screenshot("p1-admin-restart-option");
		boolean pass = "16".equals(before) && "16".equals(running) && "17".equals(pending) && "17".equals(file) && Boolean.TRUE.equals(restartOnly) && clientPending;
		return verdict(pass, MAX_PARTY_INVITES + ": restart-only, file 17, pending 17, running stays 16, the client entry shows 16 with 17 pending",
				"restart-only " + restartOnly + ", running " + before + " -> " + running + ", pending " + pending + ", file " + file + ", client entry " + (clientPending ? "16 / pending 17" : "not as expected"), shot);
	}

	private Verdict adminTeamBonus() {
		adminSection("gui.xaero_pac_hub_admin_section_teams");
		d.await("the teams from the server", MEDIUM, () -> TeamClaimsClientData.INSTANCE.hasTeams());
		JsonObject s0 = d.snapshot("before-team-bonus");
		JsonObject party0 = Snap.partyOwnedBy(s0, d.player);
		Integer bonusBefore = Snap.integer(Snap.team(party0), "bonusClaims");
		Integer limitBefore = Snap.teamBudget(party0, "claimLimit");
		String boxName = Ui.text("gui.xaero_pac_hub_admin_team_bonus_claims");
		d.await("the bonus claims box of the team", SHORT, () -> Ui.editBoxes(adminScreen(), boxName).size() == 1);
		d.waitFrames(2);
		d.replaceText("the bonus claims box of the team", () -> Ui.editBox(adminScreen(), boxName), "50");
		d.pressKey(GLFW.GLFW_KEY_ENTER);
		JsonObject s1 = d.awaitServer("after-team-bonus", Duration.ofSeconds(15), x -> Integer.valueOf(50).equals(Snap.integer(Snap.team(Snap.partyOwnedBy(x, d.player)), "bonusClaims")));
		JsonObject party1 = Snap.partyOwnedBy(s1, d.player);
		Integer bonusAfter = Snap.integer(Snap.team(party1), "bonusClaims");
		Integer limitAfter = Snap.teamBudget(party1, "claimLimit");
		d.waitTicks(10);
		String shot = d.screenshot("p1-admin-team-bonus");
		boolean pass = Integer.valueOf(50).equals(bonusAfter) && bonusBefore != null && limitBefore != null && limitAfter != null && limitAfter == limitBefore + 50 - bonusBefore;
		return verdict(pass, "team bonus claims " + bonusBefore + " -> 50, team claim limit +" + (bonusBefore == null ? "?" : 50 - bonusBefore),
				"team bonus claims " + bonusBefore + " -> " + bonusAfter + ", team claim limit " + limitBefore + " -> " + limitAfter, shot);
	}

	private Verdict adminPlayerBonus() {
		adminSection("gui.xaero_pac_hub_admin_section_players");
		String playerHint = Ui.text("gui.xaero_pac_hub_admin_player_hint");
		d.replaceText("the player box", () -> Ui.editBox(adminScreen(), playerHint), TwoPlayerTest.PLAYER2);
		String clicked = d.clickWidget("the Look up button", () -> Ui.button(adminScreen(), Ui.text("gui.xaero_pac_hub_admin_lookup")));
		d.await("the summary of Player2", SHORT, () -> {
			AdminPlayerData player = TeamClaimsClientData.INSTANCE.getPlayer();
			return player != null && TwoPlayerTest.PLAYER2.equals(player.name());
		});
		String boxName = Ui.text("gui.xaero_pac_hub_admin_bonus_claims");
		d.await("the bonus claims box of Player2", SHORT, () -> Ui.editBox(adminScreen(), boxName) != null);
		d.waitFrames(2);
		JsonObject s0 = d.snapshot("before-player-bonus");
		Integer bonusBefore = Snap.bonus(s0, TwoPlayerTest.PLAYER2, AdminPlayerBonus.CLAIMS.name());
		Integer limitBefore = Snap.playerBudget(s0, TwoPlayerTest.PLAYER2, "privateClaimLimit");
		d.replaceText("the bonus claims box of Player2", () -> Ui.editBox(adminScreen(), boxName), "25");
		d.pressKey(GLFW.GLFW_KEY_ENTER);
		JsonObject s1 = d.awaitServer("after-player-bonus", Duration.ofSeconds(15), x -> Integer.valueOf(25).equals(Snap.bonus(x, TwoPlayerTest.PLAYER2, AdminPlayerBonus.CLAIMS.name())));
		Integer bonusAfter = Snap.bonus(s1, TwoPlayerTest.PLAYER2, AdminPlayerBonus.CLAIMS.name());
		Integer limitAfter = Snap.playerBudget(s1, TwoPlayerTest.PLAYER2, "privateClaimLimit");
		d.waitTicks(10);
		String shot = d.screenshot("p1-admin-player-bonus");
		boolean pass = Integer.valueOf(25).equals(bonusAfter) && bonusBefore != null && limitBefore != null && limitAfter != null && limitAfter == limitBefore + 25 - bonusBefore;
		return verdict(pass, "Player2 looked up; bonus claims " + bonusBefore + " -> 25; private claim limit +" + (bonusBefore == null ? "?" : 25 - bonusBefore),
				"looked up with " + clicked + "; bonus claims " + bonusBefore + " -> " + bonusAfter + "; private claim limit " + limitBefore + " -> " + limitAfter, shot);
	}

	private Verdict adminSuggestion() {
		adminSection("gui.xaero_pac_hub_admin_section_players");
		String playerHint = Ui.text("gui.xaero_pac_hub_admin_player_hint");
		d.replaceText("the player box", () -> Ui.editBox(adminScreen(), playerHint), "");
		d.await("the online-player suggestion \"Player1\"", SHORT, () -> Ui.button(adminScreen(), TwoPlayerTest.PLAYER1) != null);
		d.waitFrames(2);
		String clicked;
		Throwable thrown = null;
		try {
			clicked = d.call("click the suggestion Player1", () -> {
				Button button = Ui.button(adminScreen(), TwoPlayerTest.PLAYER1);
				String description = Ui.describe(button);
				Ui.click(adminScreen(), button);
				return description;
			});
		} catch(ClientDriver.DriverException e) {
			thrown = e.getCause() != null ? e.getCause() : e;
			clicked = "the suggestion Player1";
		}
		d.waitTicks(5);
		boolean stillOpen = d.call("admin tab?", () -> mc.screen instanceof AdminTabScreen);
		boolean lookedUp = d.poll(SHORT, () -> {
			AdminPlayerData player = TeamClaimsClientData.INSTANCE.getPlayer();
			return player != null && TwoPlayerTest.PLAYER1.equals(player.name());
		});
		String boxValue = d.call("player box", () -> {
			EditBox box = mc.screen instanceof AdminTabScreen screen ? Ui.editBox(screen, playerHint) : null;
			return box == null ? "(no player box)" : box.getValue();
		});
		String suggestions = d.call("suggestions", () -> mc.screen instanceof AdminTabScreen screen ? String.valueOf(Ui.<Object>get(screen, "displayedSuggestions")) : "-");
		String shot = d.screenshot("p1-admin-suggestion-click");
		boolean pass = thrown == null && stillOpen && lookedUp && TwoPlayerTest.PLAYER1.equals(boxValue);
		return verdict(pass, "no exception; the Admin tab stays open; Player1 is looked up and in the box",
				(thrown == null ? "no exception" : "EXCEPTION (the real game would crash): " + describe(thrown)) + "; clicked " + clicked + "; admin tab open " + stillOpen
						+ "; looked up Player1 " + lookedUp + "; box \"" + boxValue + "\"; suggestions now " + suggestions, shot);
	}

	private Verdict crafted() {
		JsonObject s0 = d.snapshot("before-crafted");
		String baseKey = AdminConfigEntry.CONFIG_TEAM_CLAIMS + ":" + TEAM_CLAIMS_BASE;
		String baseBefore = Snap.running(s0, baseKey);
		String invitesFileBefore = Snap.fileValue(s0, AdminConfigEntry.CONFIG_OPAC, MAX_PARTY_INVITES);
		Integer teamBonusBefore = Snap.integer(Snap.team(Snap.partyOwnedBy(s0, TwoPlayerTest.PLAYER1)), "bonusClaims");
		Integer myBonusBefore = Snap.bonus(s0, d.player, AdminPlayerBonus.CLAIMS.name());
		boolean warnedBefore = Snap.warningContains(s0, "Rejected an admin menu request", d.player);
		UUID partyId = d.call("party id", () -> partyStorage().getParty() == null ? null : partyStorage().getParty().getId());
		UUID me = myId();
		// the normal client request path of the Admin tab, without the tab
		d.run("send admin requests", () -> {
			TeamClaimsClientRequests.setAdminConfig(AdminConfigEntry.CONFIG_TEAM_CLAIMS, TEAM_CLAIMS_BASE, "999");
			TeamClaimsClientRequests.setAdminConfig(AdminConfigEntry.CONFIG_OPAC, MAX_PARTY_INVITES, "99");
			if(partyId != null)
				TeamClaimsClientRequests.setTeamBonus(partyId, false, 999);
			TeamClaimsClientRequests.setPlayerBonus(me, AdminPlayerBonus.CLAIMS, 999);
			TeamClaimsClientRequests.requestAdminConfigs();
			TeamClaimsClientRequests.requestAdminTeams();
			TeamClaimsClientRequests.requestAdminPlayer(TwoPlayerTest.PLAYER1);
		});
		d.waitTicks(60);
		JsonObject s1 = d.snapshot("after-crafted");
		String baseAfter = Snap.running(s1, baseKey);
		String invitesFileAfter = Snap.fileValue(s1, AdminConfigEntry.CONFIG_OPAC, MAX_PARTY_INVITES);
		Integer teamBonusAfter = Snap.integer(Snap.team(Snap.partyOwnedBy(s1, TwoPlayerTest.PLAYER1)), "bonusClaims");
		Integer myBonusAfter = Snap.bonus(s1, d.player, AdminPlayerBonus.CLAIMS.name());
		boolean warned = Snap.warningContains(s1, "Rejected an admin menu request", d.player);
		Object[] client = d.call("client admin data", () -> new Object[]{TeamClaimsClientData.INSTANCE.hasConfigs(), TeamClaimsClientData.INSTANCE.hasTeams(),
				TeamClaimsClientData.INSTANCE.getPlayer() != null, TeamClaimsClientData.INSTANCE.isAdminMenuAllowed()});
		boolean unchanged = baseBefore != null && baseBefore.equals(baseAfter) && invitesFileBefore != null && invitesFileBefore.equals(invitesFileAfter)
				&& Objects.equals(teamBonusBefore, teamBonusAfter) && myBonusBefore != null && myBonusBefore.equals(myBonusAfter);
		boolean noData = Boolean.FALSE.equals(client[0]) && Boolean.FALSE.equals(client[1]) && Boolean.FALSE.equals(client[2]) && Boolean.FALSE.equals(client[3]);
		boolean pass = unchanged && noData && warned && partyId != null;
		return verdict(pass, "in a party; teamClaimsBase, maxPartyInvites (file), the team bonus and Player2's bonus unchanged; no admin data on the client;"
						+ " the server log has \"Rejected an admin menu request ... of Player2\"",
				"party " + partyId + "; teamClaimsBase " + baseBefore + " -> " + baseAfter + "; maxPartyInvites file " + invitesFileBefore + " -> " + invitesFileAfter
						+ "; team bonus " + teamBonusBefore + " -> " + teamBonusAfter + "; Player2 bonus " + myBonusBefore + " -> " + myBonusAfter
						+ "; client configs/teams/player/flag " + client[0] + "/" + client[1] + "/" + client[2] + "/" + client[3]
						+ "; warning " + (warned ? "logged" : "NOT logged") + (warnedBefore ? " (it was already there before the requests)" : ""));
	}

	// ==================== overview ====================

	private Button overviewClaimButton() {
		if(!(mc.screen instanceof OverviewTabScreen screen))
			throw new ClientDriver.DriverException("the Overview page is not open but " + d.screenName());
		return Ui.get(screen, "claimButton");
	}

	private boolean overviewClaimShows(String text) {
		return mc.screen instanceof OverviewTabScreen && text.equals(overviewClaimButton().getMessage().getString());
	}

	private Verdict overviewClaim() {
		navigate(HubTab.OVERVIEW, OverviewTabScreen.class);
		rememberOwnChunk();
		String claimText = MainMenu.CLAIM.getString();
		boolean label = d.poll(SHORT, () -> overviewClaimShows(claimText));
		String shot = d.screenshot("p1-overview");
		String clicked = d.clickWidget("the Claim button of the Overview page", this::overviewClaimButton);
		JsonObject s = d.awaitServer("overview-claimed", Duration.ofSeconds(15), x -> Snap.claimAt(x, dimension, chunkX, chunkZ) != null);
		JsonObject claim = Snap.claimAt(s, dimension, chunkX, chunkZ);
		String screen = d.call("screen", d::screenName);
		boolean pass = label && "no screen".equals(screen) && claim != null && myId().toString().equals(Snap.str(claim, "owner")) && Boolean.FALSE.equals(Snap.bool(claim, "teamClaim"));
		return verdict(pass, "button \"" + claimText + "\"; after the click no screen and chunk " + chunkX + "," + chunkZ + " a private claim of Player1",
				"button " + (label ? "showed it" : "did NOT show it") + "; clicked " + clicked + "; screen after: " + screen + "; " + Snap.describeClaim(claim), shot);
	}

	private Verdict overviewUnclaim() {
		navigate(HubTab.OVERVIEW, OverviewTabScreen.class);
		String unclaimText = MainMenu.UNCLAIM.getString();
		boolean label = d.poll(SHORT, () -> overviewClaimShows(unclaimText));
		String shot = d.screenshot("p1-overview-claimed");
		String clicked = d.clickWidget("the Unclaim button of the Overview page", this::overviewClaimButton);
		JsonObject s = d.awaitServer("overview-unclaimed", Duration.ofSeconds(15), x -> Snap.claimAt(x, dimension, chunkX, chunkZ) == null);
		JsonObject claim = Snap.claimAt(s, dimension, chunkX, chunkZ);
		String screen = d.call("screen", d::screenName);
		return verdict(label && claim == null && "no screen".equals(screen), "button \"" + unclaimText + "\"; after the click no screen and chunk " + chunkX + "," + chunkZ + " unclaimed",
				"button " + (label ? "showed it" : "did NOT show it") + "; clicked " + clicked + "; screen after: " + screen + "; " + Snap.describeClaim(claim), shot);
	}

	// ==================== settings: the player config screen ====================

	private static final String SHARE_SEARCH = "shareLocationWithParty";
	private static final String SHARE_ID = "parties.sharelocationwithparty";
	private static final String SHARE_SNAPSHOT_KEY = "parties.shareLocationWithParty";
	private static final String RESET_TEXT = "↺";

	private PlayerConfigScreen configScreen() {
		if(!(mc.screen instanceof PlayerConfigScreen screen))
			throw new ClientDriver.DriverException("the player config screen is not open but " + d.screenName());
		return screen;
	}

	private void openMyPlayerConfig() {
		if(d.call("config screen?", () -> mc.screen instanceof PlayerConfigScreen screen && Ui.get(screen, "searchBox") != null))
			return;
		navigate(HubTab.SETTINGS, SettingsTabScreen.class);
		d.clickWidget("the My player config button", () -> Ui.button(d.requireScreen(), Ui.text("gui.xaero_pac_ui_my_player_config")));
		d.await("the player config screen with its options", MEDIUM, () -> mc.screen instanceof PlayerConfigScreen screen && Ui.get(screen, "searchBox") != null);
		d.waitFrames(3);
	}

	private static boolean isGroupHeader(Button button) {
		String text = button.getMessage().getString();
		return text.startsWith("▾ ") || text.startsWith("▸ ");
	}

	private List<String> groupHeaders() {
		List<String> headers = new ArrayList<>();
		for(Button button : Ui.buttons(configScreen(), b -> b.visible && isGroupHeader(b)))
			headers.add(button.getMessage().getString());
		return headers;
	}

	private static String headerTitle(String header) {
		String rest = header.substring(2);
		int count = rest.lastIndexOf(" (");
		return count < 0 ? rest : rest.substring(0, count);
	}

	private String clickGroupHeader(String title) {
		return d.clickWidget("the group header \"" + title + "\"", () -> {
			for(Button button : Ui.buttons(configScreen(), b -> b.visible && isGroupHeader(b)))
				if(headerTitle(button.getMessage().getString()).equals(title))
					return button;
			return null;
		});
	}

	private Verdict configGroups() {
		openMyPlayerConfig();
		List<String> before = d.call("group headers", this::groupHeaders);
		String shot = d.screenshot("p1-config-grouped");
		long expandedBefore = before.stream().filter(h -> h.startsWith("▾ ")).count();
		if(before.isEmpty())
			return verdict(false, "group headers on the first page", "none", shot);
		String title = headerTitle(before.get(0));
		clickGroupHeader(title);
		d.waitFrames(3);
		List<String> collapsed = d.call("group headers", this::groupHeaders);
		String collapsedShot = d.screenshot("p1-config-groups-collapsed");
		clickGroupHeader(title);
		d.waitFrames(3);
		List<String> after = d.call("group headers", this::groupHeaders);
		boolean firstCollapsed = !collapsed.isEmpty() && collapsed.get(0).startsWith("▸ ") && collapsed.stream().noneMatch(h -> h.startsWith("▾ ")) && collapsed.size() > 1;
		boolean pass = expandedBefore == 1 && before.get(0).startsWith("▾ ") && firstCollapsed && !after.isEmpty() && after.get(0).startsWith("▾ ");
		return verdict(pass, "only the first group (\"" + title + "\") expanded; a click on its header collapses it (all groups listed, collapsed), another expands it again",
				"headers " + before + " -> after a click " + collapsed + " -> after another " + after, shot, collapsedShot);
	}

	private List<?> shownOptionRows() {
		PlayerConfigScreen screen = configScreen();
		List<?> elements = Ui.get(screen, "elements");
		List<Object> shown = new ArrayList<>();
		for(Object row : Ui.<List<?>>get(screen, "optionRows"))
			if(elements.contains(Ui.get(row, "element")))
				shown.add(row);
		return shown;
	}

	private static String optionId(Object row) {
		ConfigViewLayout.Entry entry = Ui.get(row, "entry");
		return entry.searchText().split("\n")[1];
	}

	private ConfigOptionRowListElement shareElement() {
		for(Object row : Ui.<List<?>>get(configScreen(), "optionRows"))
			if(SHARE_ID.equals(optionId(row)))
				return Ui.get(row, "element");
		throw new ClientDriver.DriverException("no option row " + SHARE_ID);
	}

	/**
	 * Gets the value widget of the shared-location row: the widget at the row's position that is not its reset button.
	 */
	private AbstractWidget shareValueWidget() {
		ConfigOptionRowListElement element = shareElement();
		int x = Ui.getInt(element, "x");
		int y = Ui.getInt(element, "y");
		for(GuiEventListener listener : Ui.all(configScreen()))
			if(listener instanceof AbstractWidget widget && widget.visible && widget.getX() == x && widget.getY() == y && !RESET_TEXT.equals(widget.getMessage().getString()))
				return widget;
		return null;
	}

	private Button shareResetButton() {
		int y = Ui.getInt(shareElement(), "y");
		for(Button button : Ui.buttonsWithText(configScreen(), RESET_TEXT))
			if(button.getY() == y)
				return button;
		return null;
	}

	private Boolean shareOnServer(JsonObject snapshot) {
		return Snap.bool(Snap.obj(Snap.playerData(snapshot, d.player), "options"), SHARE_SNAPSHOT_KEY);
	}

	private Verdict configSearch() {
		openMyPlayerConfig();
		d.replaceText("the config search box", () -> Ui.get(configScreen(), "searchBox"), SHARE_SEARCH);
		d.waitFrames(3);
		List<String> shown = d.call("shown options", () -> {
			List<String> ids = new ArrayList<>();
			for(Object row : shownOptionRows())
				ids.add(optionId(row));
			return ids;
		});
		String shot = d.screenshot("p1-config-search");
		boolean onlyMatches = shown.stream().allMatch(id -> id.contains(SHARE_SEARCH.toLowerCase(Locale.ROOT)));
		return verdict(shown.contains(SHARE_ID) && onlyMatches, "the option " + SHARE_ID + " shown, every shown option matches \"" + SHARE_SEARCH + "\"", "shown: " + shown, shot);
	}

	private Verdict configToggle() {
		Boolean before = shareOnServer(d.snapshot("before-config-toggle"));
		String clicked = d.clickWidget("the value of Share Position With Party", this::shareValueWidget);
		JsonObject s = d.awaitServer("config-toggled", Duration.ofSeconds(15), x -> Boolean.FALSE.equals(shareOnServer(x)));
		Boolean after = shareOnServer(s);
		return verdict(Boolean.TRUE.equals(before) && Boolean.FALSE.equals(after), SHARE_SNAPSHOT_KEY + " true -> false on the server",
				"clicked " + clicked + "; server " + before + " -> " + after);
	}

	private Verdict configResetShown() {
		d.waitFrames(5);
		boolean shown = d.call("reset button?", () -> shareResetButton() != null);
		String shot = d.screenshot("p1-config-changed");
		return verdict(shown, "the " + RESET_TEXT + " button next to the changed option right after the click",
				shown ? "it is there" : "it is NOT there (it only appears after a relayout of the page)", shot);
	}

	private Verdict configReset() {
		boolean relayout = !d.call("reset button?", () -> shareResetButton() != null);
		if(relayout)// the search box retyped lays the page out again
			d.replaceText("the config search box", () -> Ui.get(configScreen(), "searchBox"), SHARE_SEARCH);
		String shot = d.screenshot("p1-config-reset-button");
		String clicked = d.clickWidget("the reset button", this::shareResetButton);
		JsonObject s = d.awaitServer("config-reset", Duration.ofSeconds(15), x -> Boolean.TRUE.equals(shareOnServer(x)));
		Boolean after = shareOnServer(s);
		boolean gone = d.poll(SHORT, () -> mc.screen instanceof PlayerConfigScreen && shareResetButton() == null);
		d.replaceText("the config search box", () -> Ui.get(configScreen(), "searchBox"), "");
		return verdict(Boolean.TRUE.equals(after) && gone, SHARE_SNAPSHOT_KEY + " back to true on the server and the reset button gone",
				"clicked " + clicked + (relayout ? " (after a relayout, see " + P1_SETTINGS_RESET_SHOWN + ")" : "") + "; server " + after + "; reset button " + (gone ? "gone" : "still there"), shot);
	}

	// ==================== claims map zoom ====================

	private int zoomStep() {
		return Ui.invoke(claimsScreen(), "zoomStep", new Class<?>[0]);
	}

	/**
	 * Whether every chunk of the grid that the client has loaded has its terrain picture.
	 */
	private boolean terrainFilled() {
		ClaimsTabScreen screen = claimsScreen();
		Grid grid = grid(screen);
		TerrainCache terrain = Ui.get(screen, "terrain");
		for(int x = grid.centerX() - grid.radius(); x <= grid.centerX() + grid.radius(); x++)
			for(int z = grid.centerZ() - grid.radius(); z <= grid.centerZ() + grid.radius(); z++)
				if(mc.level.getChunkSource().getChunkNow(x, z) != null && terrain.get(x, z) == null)
					return false;
		return true;
	}

	private Verdict zoomShots() {
		openClaimsTab();
		while(d.call("zoom", this::zoomStep) > 0)
			d.clickWidget("the zoom-out button", () -> Ui.get(claimsScreen(), "zoomOutButton"));
		List<String> seen = new ArrayList<>();
		List<String> shots = new ArrayList<>();
		int[] radii = new int[3];
		boolean ok = true;
		for(int step = 0; step <= 2; step++) {
			if(step > 0)
				d.clickWidget("the zoom-in button", () -> Ui.get(claimsScreen(), "zoomInButton"));
			int zoom = d.call("zoom", this::zoomStep);
			boolean filled = d.poll(MEDIUM, this::terrainFilled);
			d.waitFrames(5);
			Grid grid = d.call("grid", () -> grid(claimsScreen()));
			radii[step] = grid.radius();
			shots.add(d.screenshot("p1-claims-zoom-" + step));
			seen.add("zoom " + zoom + ": radius " + grid.radius() + ", cell " + grid.cell() + (filled ? ", terrain drawn" : ", terrain NOT complete"));
			ok &= zoom == step && filled;
		}
		d.clickWidget("the zoom-out button", () -> Ui.get(claimsScreen(), "zoomOutButton"));
		int restored = d.call("zoom", this::zoomStep);
		boolean pass = ok && radii[0] > radii[1] && radii[1] > radii[2] && restored == 1;
		return new Verdict(pass, "zoom 0, 1, 2 with fewer chunks per step, the terrain of every loaded chunk drawn; back at zoom 1",
				String.join("; ", seen) + "; back at zoom " + restored, shots);
	}

	// ==================== World Map ====================

	private static final String GUI_MAP = "xaero.map.gui.GuiMap";
	private static final String KEY_CLAIM_AS_TEAM = "gui.xaero_pac_wm_claim_as_team";
	private static final String KEY_CLAIM_AS_PRIVATE = "gui.xaero_pac_wm_claim_as_private";
	private static final String KEY_TOGGLE_MODE = "gui.xaero_pac_wm_toggle_mode";
	private static final String KEY_OPEN_MENU = "gui.xaero_pac_wm_open_menu";
	private static final String KEY_WORLD_MAP_CLAIM = "gui.xaero_pac_claim_chunks";

	private static boolean isMap(Screen screen) {
		return screen != null && GUI_MAP.equals(screen.getClass().getName());
	}

	/**
	 * Opens World Map with its key (default M), as a player does.
	 */
	private void openMap() {
		if(d.call("map?", () -> isMap(mc.screen)))
			return;
		d.run("close", () -> mc.setScreen(null));
		d.waitTicks(5);
		int key = d.call("map key", () -> InputConstants.getKey(ControlsRegister.keyOpenMap.saveString()).getValue());
		d.pressKey(key);
		d.await("the World Map screen", Duration.ofSeconds(30), () -> isMap(mc.screen));
		d.waitTicks(40);
	}

	private static String modeLabel(boolean team) {
		return Ui.text("gui.xaero_pac_wm_button", Ui.text(team ? "gui.xaero_pac_wm_mode_team" : "gui.xaero_pac_wm_mode_private"));
	}

	private Button mapButton() {
		String prefix = Ui.text("gui.xaero_pac_wm_button", "");
		List<Button> found = Ui.buttons(d.requireScreen(), b -> b.visible && b.getMessage().getString().startsWith(prefix));
		return found.size() == 1 ? found.get(0) : null;
	}

	private String mapButtonText() {
		Button button = mapButton();
		return button == null ? "(no button)" : button.getMessage().getString();
	}

	private String teamSubOnServer(JsonObject snapshot) {
		JsonObject party = Snap.partyOwnedBy(snapshot, TwoPlayerTest.PLAYER1);
		return Snap.str(Snap.team(party), "subConfigId");
	}

	private boolean usesTeamSub(JsonObject snapshot) {
		String teamSub = teamSubOnServer(snapshot);
		return teamSub != null && teamSub.equals(Snap.str(Snap.playerData(snapshot, d.player), "usedSub"));
	}

	private static String optionKey(RightClickOption option) {
		Component name = option.getDisplayName();
		return name.getContents() instanceof TranslatableContents contents ? contents.getKey() : name.getString();
	}

	/**
	 * Selects chunks on the open World Map and opens its right-click menu on them, exactly as GuiMap does on a right
	 * click (handleRightClick with the map as the target).
	 *
	 * @return the keys and states of the menu's entries, in order
	 */
	private List<String> rightClickMenu(int x0, int z0, int x1, int z1) {
		List<String> entries = d.call("the right-click menu on " + x0 + "," + z0 + " .. " + x1 + "," + z1, () -> {
			Screen map = d.requireScreen();
			if(!isMap(map))
				throw new ClientDriver.DriverException("the World Map is not open but " + d.screenName());
			// closing a menu clears the selection, so close the last one before selecting (handleRightClick would after)
			if(Ui.get(map, "rightClickMenu") != null) {
				Ui.invoke(map, "closeRightClick", new Class<?>[0]);
				Ui.set(map, "rightClickMenu", null);
			}
			MapTileSelection selection = new MapTileSelection(x0, z0);
			selection.setEnd(x1, z1);
			Ui.set(map, "mapTileSelection", selection);
			Ui.set(map, "rightClickDim", mc.level.dimension());
			Ui.set(map, "rightClickX", x0 * 16 + 8);
			Ui.set(map, "rightClickY", mc.player.getBlockY());
			Ui.set(map, "rightClickZ", z0 * 16 + 8);
			Ui.set(map, "rightClickCoordinateScale", 1.0);
			Ui.invoke(map, "handleRightClick", new Class<?>[]{IRightClickableElement.class, int.class, int.class}, map, map.width / 2, map.height / 2);
			Object menu = Ui.get(map, "rightClickMenu");
			if(menu == null)
				throw new ClientDriver.DriverException("no right-click menu opened");
			List<String> list = new ArrayList<>();
			for(RightClickOption option : Ui.<List<RightClickOption>>get(menu, "actionOptions"))
				list.add(optionKey(option) + (option.isActive() ? "" : " (inactive)"));
			return list;
		});
		d.waitFrames(3);
		return entries;
	}

	/**
	 * Runs an entry of the open right-click menu the way a click on it does (GuiRightClickMenu.selectId).
	 */
	private void selectEntry(String key) {
		d.run("select the right-click entry " + key, () -> {
			Object menu = Ui.get(d.requireScreen(), "rightClickMenu");
			List<RightClickOption> options = Ui.get(menu, "actionOptions");
			for(int i = 0; i < options.size(); i++)
				if(key.equals(optionKey(options.get(i)))) {
					Ui.invoke(menu, "selectId", new Class<?>[]{int.class, boolean.class}, i, true);
					return;
				}
			throw new ClientDriver.DriverException("no entry " + key + " in the right-click menu");
		});
	}

	private List<JsonObject> claimsIn(JsonObject snapshot, int x0, int z0, int x1, int z1) {
		List<JsonObject> claims = new ArrayList<>();
		for(int x = x0; x <= x1; x++)
			for(int z = z0; z <= z1; z++) {
				JsonObject claim = Snap.claimAt(snapshot, dimension, x, z);
				if(claim != null)
					claims.add(claim);
			}
		return claims;
	}

	private String describeClaims(List<JsonObject> claims) {
		List<String> list = new ArrayList<>();
		for(JsonObject claim : claims)
			list.add(Snap.describeClaim(claim));
		return claims.size() + " claim(s) " + list;
	}

	private Verdict mapButtonToggle() {
		rememberOwnChunk();
		openMap();
		String before = d.call("button", this::mapButtonText);
		String shot = d.screenshot("p1-worldmap");
		String clicked = d.clickWidget("the Claiming button of the map", this::mapButton);
		boolean shown = d.poll(SHORT, () -> mapButtonText().startsWith(modeLabel(false)) && !ClaimAsToggle.isTeamSelected());
		JsonObject s = d.awaitServer("map-private", SHORT, x -> !usesTeamSub(x));
		String after = d.call("button", this::mapButtonText);
		String shotAfter = d.screenshot("p1-worldmap-private");
		boolean pass = before.startsWith(modeLabel(true)) && shown && !usesTeamSub(s);
		return verdict(pass, "button \"" + modeLabel(true) + "...\", after a click \"" + modeLabel(false) + "...\" and the server's usedSub no longer the team sub-config",
				"button \"" + before + "\" -> \"" + after + "\" (clicked " + clicked + "); server usedSub " + Snap.str(Snap.playerData(s, d.player), "usedSub"), shot, shotAfter);
	}

	private Verdict mapEntries() {
		rememberOwnChunk();
		openMap();
		List<String> entries = rightClickMenu(chunkX + 2, chunkZ + 2, chunkX + 3, chunkZ + 3);
		String shot = d.screenshot("p1-worldmap-right-click");
		int worldMap = -1;
		for(int i = 0; i < entries.size() && worldMap < 0; i++)
			if(entries.get(i).startsWith(KEY_WORLD_MAP_CLAIM))
				worldMap = i;
		List<String> ours = List.of(KEY_CLAIM_AS_TEAM, KEY_CLAIM_AS_PRIVATE, KEY_TOGGLE_MODE, KEY_OPEN_MENU);
		int first = entries.indexOf(KEY_CLAIM_AS_TEAM);
		boolean pass = worldMap >= 0 && first > worldMap && first + ours.size() <= entries.size() && entries.subList(first, first + ours.size()).equals(ours);
		return verdict(pass, "World Map's " + KEY_WORLD_MAP_CLAIM + ", after it our 4 entries in order, all active: " + ours, "entries " + entries, shot);
	}

	private Verdict mapClaimAsTeamOnce() {
		openMap();
		int x0 = chunkX + 2, z0 = chunkZ + 2, x1 = chunkX + 3, z1 = chunkZ + 3;
		rightClickMenu(x0, z0, x1, z1);
		selectEntry(KEY_CLAIM_AS_TEAM);
		JsonObject s = d.awaitServer("map-team-claims", Duration.ofSeconds(15), x -> claimsIn(x, x0, z0, x1, z1).size() == 4 && !usesTeamSub(x));
		List<JsonObject> claims = claimsIn(s, x0, z0, x1, z1);
		boolean team = claims.size() == 4 && claims.stream().allMatch(c -> Boolean.TRUE.equals(Snap.bool(c, "teamClaim")));
		boolean privateAgain = d.poll(SHORT, () -> !ClaimAsToggle.isTeamSelected() && mapButtonText().startsWith(modeLabel(false)));
		d.waitTicks(20);
		String shot = d.screenshot("p1-worldmap-team-claimed");
		return verdict(team && !usesTeamSub(s) && privateAgain, "chunks " + x0 + "," + z0 + " .. " + x1 + "," + z1 + ": 4 team claims; mode back to Private (server usedSub, button)",
				describeClaims(claims) + "; server usedSub " + Snap.str(Snap.playerData(s, d.player), "usedSub") + "; button \"" + d.call("button", this::mapButtonText) + "\"", shot);
	}

	private Verdict mapClaimAsPrivate() {
		openMap();
		int x0 = chunkX - 3, z0 = chunkZ + 2, x1 = chunkX - 2, z1 = chunkZ + 3;
		rightClickMenu(x0, z0, x1, z1);
		selectEntry(KEY_CLAIM_AS_PRIVATE);
		JsonObject s = d.awaitServer("map-private-claims", Duration.ofSeconds(15), x -> claimsIn(x, x0, z0, x1, z1).size() == 4);
		List<JsonObject> claims = claimsIn(s, x0, z0, x1, z1);
		String me = myId().toString();
		boolean pass = claims.size() == 4 && claims.stream().allMatch(c -> Boolean.FALSE.equals(Snap.bool(c, "teamClaim")) && me.equals(Snap.str(c, "owner")));
		d.waitTicks(20);
		String shot = d.screenshot("p1-worldmap-private-claimed");
		return verdict(pass, "chunks " + x0 + "," + z0 + " .. " + x1 + "," + z1 + ": 4 private claims of Player1", describeClaims(claims), shot);
	}

	private Verdict mapToggleEntry() {
		openMap();
		rightClickMenu(chunkX - 3, chunkZ - 1, chunkX - 2, chunkZ);// unclaimed: World Map offers our entries only next to its Claim Selected
		selectEntry(KEY_TOGGLE_MODE);
		JsonObject s = d.awaitServer("map-team-mode", SHORT, this::usesTeamSub);
		boolean shown = d.poll(SHORT, () -> ClaimAsToggle.isTeamSelected() && mapButtonText().startsWith(modeLabel(true)));
		String shot = d.screenshot("p1-worldmap-team-mode");
		return verdict(usesTeamSub(s) && shown, "server usedSub = the team sub-config and button \"" + modeLabel(true) + "...\"",
				"server usedSub " + Snap.str(Snap.playerData(s, d.player), "usedSub") + "; button \"" + d.call("button", this::mapButtonText) + "\"", shot);
	}

	private Verdict mapOpenMenu() {
		openMap();
		Screen map = d.call("map", () -> mc.screen);
		rightClickMenu(chunkX - 3, chunkZ - 1, chunkX - 2, chunkZ);
		selectEntry(KEY_OPEN_MENU);
		boolean open = d.poll(SHORT, () -> isHub(mc.screen));
		d.waitTicks(10);
		Object[] state = d.call("hub", () -> new Object[]{d.screenName(), isHub(mc.screen) ? Ui.get(mc.screen, "parent") : null});
		String shot = d.screenshot("p1-worldmap-open-menu");
		return verdict(open && state[1] == map, "a hub page opens with the World Map as its parent",
				"screen " + state[0] + ", parent " + (state[1] == null ? "none" : state[1].getClass().getSimpleName() + (state[1] == map ? " (the map)" : " (another screen)")), shot);
	}

	private Verdict mapButtonMember() {
		rememberOwnChunk();
		openMap();
		boolean team = d.call("mode", ClaimAsToggle::isTeamSelected);
		String text = d.call("button", this::mapButtonText);
		String shot = d.screenshot("p2-worldmap");
		return verdict(text.startsWith(modeLabel(team)), "a Claiming button with the mode \"" + modeLabel(team) + "...\"", "button \"" + text + "\"", shot);
	}

	private Verdict mapClaimAsTeamMember() {
		openMap();
		JsonObject s0 = d.snapshot("before-member-map-claim");
		JsonObject party = Snap.partyOwnedBy(s0, TwoPlayerTest.PLAYER1);
		String rank = Snap.rank(party, d.player);
		String claimRole = Snap.role(party, TeamAction.CLAIM.id());
		String usedBefore = Snap.str(Snap.playerData(s0, d.player), "usedSub");
		boolean allowed = TeamRole.valueOf(rank).isAtLeast(TeamRole.valueOf(claimRole));
		int x0 = chunkX + 2, z0 = chunkZ - 3, x1 = chunkX + 3, z1 = chunkZ - 2;
		List<String> entries = rightClickMenu(x0, z0, x1, z1);
		String menuShot = d.screenshot("p2-worldmap-right-click");
		selectEntry(KEY_CLAIM_AS_TEAM);
		JsonObject s;
		if(allowed)
			s = d.awaitServer("member-map-claims", Duration.ofSeconds(15), x -> claimsIn(x, x0, z0, x1, z1).size() == 4);
		else {
			d.waitTicks(60);
			s = d.snapshot("member-map-claims");
		}
		List<JsonObject> claims = claimsIn(s, x0, z0, x1, z1);
		String usedAfter = Snap.str(Snap.playerData(s, d.player), "usedSub");
		boolean claimsOk = allowed ? claims.size() == 4 && claims.stream().allMatch(c -> Boolean.TRUE.equals(Snap.bool(c, "teamClaim"))) : claims.isEmpty();
		d.waitTicks(20);
		String shot = d.screenshot("p2-worldmap-team-claim");
		return verdict(claimsOk && Objects.equals(usedBefore, usedAfter) && entries.contains(KEY_CLAIM_AS_TEAM),
				"rank " + rank + " vs claim role " + claimRole + " -> " + (allowed ? "4 team claims" : "no claims") + " at " + x0 + "," + z0 + " .. " + x1 + "," + z1 + "; usedSub stays " + usedBefore,
				"entries " + entries + "; " + describeClaims(claims) + "; usedSub " + usedBefore + " -> " + usedAfter, menuShot, shot);
	}

	// ==================== screenshots ====================

	private static Class<? extends Screen> screenClassOf(String tab) {
		return switch(tab) {
			case "overview" -> OverviewTabScreen.class;
			case "party" -> PartyScreen.class;
			case "claims" -> ClaimsTabScreen.class;
			case "team" -> TeamTabScreen.class;
			case "settings" -> SettingsTabScreen.class;
			case "admin" -> AdminTabScreen.class;
			default -> throw new IllegalArgumentException(tab);
		};
	}

	private void shots(List<String> tabs) {
		for(String tab : tabs)
			for(String scaleName : List.of(GUI_SCALE_LOW, GUI_SCALE_HIGH)) {
				int scale = Integer.parseInt(scaleName.substring(3));
				String id = ScenarioPlan.shotId(prefix, tab, scaleName);
				step(id, () -> shot(id, tab, scale));
			}
		try {
			d.setGuiScale(WIDTH, HEIGHT, SCALE);
			d.run("close", () -> mc.setScreen(null));
		} catch(RuntimeException e) {
			d.results.note("could not reset after the screenshots: " + describe(e));
		}
	}

	private Verdict shot(String id, String tab, int scale) throws Exception {
		HubTab hubTab = HubTab.valueOf(tab.toUpperCase(Locale.ROOT));
		Class<? extends Screen> expected = screenClassOf(tab);
		if(!d.call("tab visible?", () -> HubTabs.isVisible(hubTab)))
			return verdict(false, "the " + tab + " tab is shown for " + d.player, "it is not in the tab bar");
		openTab(hubTab);
		int taken = d.setGuiScale(WIDTH, HEIGHT, scale);
		boolean open = d.poll(SHORT, () -> expected.isInstance(mc.screen));
		d.waitTicks(20);
		String shot = d.screenshot(id);
		Object[] state = d.call("window", () -> new Object[]{mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight(), (int) mc.getWindow().getGuiScale(), d.screenName()});
		boolean pass = open && taken == scale && Integer.valueOf(scale).equals(state[1]);
		return verdict(pass, expected.getSimpleName() + " at GUI scale " + scale + " (" + WIDTH + "x" + HEIGHT + ")", state[2] + " at GUI scale " + state[1] + " (" + state[0] + ")", shot);
	}

}
