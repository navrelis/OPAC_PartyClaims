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

package xaero.pac.client.gui.hub;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import xaero.pac.client.gui.party.PartyRules;
import xaero.pac.client.teamclaims.TeamClaimsClientData;
import xaero.pac.client.teamclaims.TeamClaimsClientRequests;
import xaero.pac.common.packet.teamclaims.AdminConfigEntry;
import xaero.pac.common.packet.teamclaims.AdminPlayerBonus;
import xaero.pac.common.packet.teamclaims.AdminPlayerData;
import xaero.pac.common.packet.teamclaims.AdminTeamData;
import xaero.pac.common.packet.teamclaims.ClientboundAdminResultPacket;
import xaero.pac.teamclaims.config.TeamConfig;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * [Team Claims] The Admin tab of the hub: the server's config options (OPAC's and Team Claims'), the bonus limits of a
 * player and the bonus limits of the teams. It is only shown to players that the server allows
 * ({@link TeamClaimsClientData#isAdminMenuAllowed}); the server checks every request again.
 * <p>
 * Every section is a scrollable list of rows with the widgets of the row. Boolean and enum options send as soon as their
 * button is pressed; numbers and text have an edit box and a Save button (Enter in the box saves too). The answers come
 * back through {@link TeamClaimsClientData}: the screen compares its revision every tick and rebuilds the rows when it
 * changed, keeping what the admin has typed but not saved. The last result message of the server is shown at the bottom.
 * The pure rules (what a valid value is, grouping and filtering) are in {@link AdminValueRules} and
 * {@link AdminConfigView}.
 */
public class AdminTabScreen extends HubTabScreen {

	private static final Component TITLE = Component.translatable("gui.xaero_pac_hub_tab_admin");
	private static final Component SAVE = Component.translatable("gui.xaero_pac_hub_admin_save");
	private static final Component REFRESH = Component.translatable("gui.xaero_pac_hub_admin_refresh");
	private static final Component LOOK_UP = Component.translatable("gui.xaero_pac_hub_admin_lookup");
	private static final Component ON = Component.translatable("gui.xaero_pac_hub_admin_on").withStyle(ChatFormatting.GREEN);
	private static final Component OFF = Component.translatable("gui.xaero_pac_hub_admin_off").withStyle(ChatFormatting.RED);
	private static final Component FILTER_HINT = Component.translatable("gui.xaero_pac_hub_admin_filter_hint");
	private static final Component TEAM_FILTER_HINT = Component.translatable("gui.xaero_pac_hub_admin_team_filter_hint");
	private static final Component PLAYER_HINT = Component.translatable("gui.xaero_pac_hub_admin_player_hint");
	private static final Tooltip REFRESH_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_admin_refresh_tooltip"));
	private static final Tooltip LOOK_UP_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_admin_lookup_tooltip"));
	private static final Tooltip CYCLE_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_admin_cycle_tooltip"));
	private static final Tooltip TOGGLE_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_admin_toggle_tooltip"));
	private static final Tooltip SAVE_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_admin_save_tooltip"));

	private static final int MARGIN = 8;
	private static final int MAX_LIST_WIDTH = 440;
	private static final int SCROLLBAR_WIDTH = 5;
	private static final int ROW_HEIGHT = 22;
	private static final int TEAM_ROW_HEIGHT = 46;
	private static final int EDIT_HEIGHT = 20;
	private static final int SAVE_WIDTH = 40;
	private static final int BUTTON_GAP = 2;
	private static final int SUGGESTION_HEIGHT = 16;
	private static final int TALL_SCREEN_HEIGHT = 260;
	private static final int TOOLTIP_WIDTH = 220;
	private static final int RESULT_HEIGHT = 20;
	private static final int BONUS_BOX_WIDTH = 56;
	private static final int TEAM_BOX_WIDTH = 44;
	private static final int TEAM_SAVE_WIDTH = 38;
	private static final int MAX_PLAYER_QUERY_LENGTH = 64;
	/**
	 * The largest bonus of a team that the server accepts.
	 */
	private static final int MAX_TEAM_BONUS = TeamConfig.MAX_BONUS;
	/**
	 * The fewest milliseconds between two config requests of the client. The server ignores a config request that comes
	 * less than 10 ticks (half a second) after the last one.
	 */
	private static final long CONFIG_REQUEST_GAP_MILLIS = 650;
	private static final int REFRESH_COOLDOWN_TICKS = 16;
	private static final int CONFIG_RETRY_TICKS = 40;

	private static final int TEXT_COLOR = 0xFFE0E0E0;
	private static final int VALUE_COLOR = 0xFFAAAAAA;
	private static final int HEADER_COLOR = 0xFFFFD27F;
	private static final int WARNING_COLOR = 0xFFFF5555;
	private static final int HINT_COLOR = 0xFFFFCC55;
	private static final int SUCCESS_COLOR = 0xFF55FF55;
	private static final int VALID_TEXT_COLOR = 0xFFE0E0E0;
	private static final int INVALID_TEXT_COLOR = 0xFFFF5555;
	private static final int BADGE_COLOR = 0xFF9A5B00;
	private static final int LIST_BACKGROUND = 0x66000000;
	private static final int STRIPE_COLOR = 0x22FFFFFF;
	private static final int HEADER_BACKGROUND = 0x44000000;
	private static final int SCROLLBAR_TRACK = 0x66000000;
	private static final int SCROLLBAR_THUMB = 0xFFA0A0A0;

	/**
	 * The sections of the tab.
	 */
	private enum Section {
		OPAC("gui.xaero_pac_hub_admin_section_opac", AdminConfigEntry.CONFIG_OPAC),
		TEAM_CLAIMS("gui.xaero_pac_hub_admin_section_team_claims", AdminConfigEntry.CONFIG_TEAM_CLAIMS),
		PLAYERS("gui.xaero_pac_hub_admin_section_players", null),
		TEAMS("gui.xaero_pac_hub_admin_section_teams", null);

		private final Component label;
		@Nullable
		private final String configId;

		Section(String labelKey, @Nullable String configId) {
			this.label = Component.translatable(labelKey);
			this.configId = configId;
		}

		boolean isConfig() {
			return configId != null;
		}
	}

	/**
	 * Draws the texts of a row.
	 */
	private interface RowPainter {
		void paint(GuiGraphics guiGraphics, int y);
	}

	/**
	 * A widget of a row and how far below the top of the row it is.
	 */
	private record Placed(AbstractWidget widget, int dy) {
	}

	/**
	 * One line of a list: the widgets in it, how it draws its texts, and the tooltip over its label.
	 */
	private static final class ListRow {

		private final int height;
		private final boolean header;
		private final List<Placed> widgets = new ArrayList<>();
		private RowPainter painter;
		private Supplier<List<Component>> tooltip;
		private int tooltipWidth;
		/**
		 * The Y of the row on the screen, -1 while it is scrolled out of the list.
		 */
		private int y = -1;

		private ListRow(int height, boolean header) {
			this.height = height;
			this.header = header;
		}

	}

	/**
	 * An edit box that calls a function when Enter is pressed in it.
	 */
	private static final class EnterBox extends EditBox {

		private final Runnable onEnter;

		private EnterBox(Font font, int x, int y, int width, int height, Component message, Runnable onEnter) {
			super(font, x, y, width, height, message);
			this.onEnter = onEnter;
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && isFocused() && visible) {
				onEnter.run();
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

	}

	private static Section lastSection = Section.OPAC;
	private static long lastConfigRequestMillis;

	private Section section = lastSection;
	private String filterText = "";
	/**
	 * What the admin typed into the boxes of the rows and has not saved, by a key of the row.
	 */
	private final Map<String, String> drafts = new HashMap<>();
	private final Map<String, EditBox> boxes = new HashMap<>();
	private final List<ListRow> rows = new ArrayList<>();
	private final List<Button> suggestionButtons = new ArrayList<>();
	private List<String> displayedSuggestions = List.of();
	private String displayedSuggestionFilter;

	private int seenRevision = -1;
	@Nullable
	private ClientboundAdminResultPacket.Result lastSeenResult = TeamClaimsClientData.INSTANCE.getLastResult();
	@Nullable
	private ClientboundAdminResultPacket.Result shownResult;
	@Nullable
	private UUID shownPlayerId;
	@Nullable
	private AdminPlayerData lastSeenPlayer = TeamClaimsClientData.INSTANCE.getPlayer();
	private boolean configsRequested;
	private boolean teamsRequested;
	private boolean playerRequested;
	private int refreshCooldown;
	private int configWaitTicks;

	private int buttonHeight;
	private int listLeft;
	private int listWidth;
	private int listTop;
	private int listHeight;
	private int resultTop;
	private int scrollbarLeft;
	private int suggestionTop;
	private int scroll;
	private int maxScroll;
	private int visibleCount;
	private boolean draggingScrollbar;
	private EditBox filterBox;
	private EditBox playerBox;
	private Button refreshButton;
	private Button lookUpButton;
	private Component emptyMessage = Component.empty();

	public AdminTabScreen(Screen escape, Screen parent) {
		super(escape, parent, TITLE);
	}

	@Override
	protected HubTab getTab() {
		return HubTab.ADMIN;
	}

	private TeamClaimsClientData getData() {
		return TeamClaimsClientData.INSTANCE;
	}

	// ---- layout ----

	@Override
	protected void init() {
		super.init();
		rows.clear();
		boxes.clear();
		suggestionButtons.clear();
		displayedSuggestions = List.of();
		displayedSuggestionFilter = null;
		filterBox = null;
		playerBox = null;
		refreshButton = null;
		lookUpButton = null;
		draggingScrollbar = false;
		buttonHeight = height >= TALL_SCREEN_HEIGHT ? 20 : 16;
		listWidth = Math.min(MAX_LIST_WIDTH, width - 2 * MARGIN - SCROLLBAR_WIDTH - 3);
		listLeft = (width - listWidth - SCROLLBAR_WIDTH - 3) / 2;
		scrollbarLeft = listLeft + listWidth + 3;

		int y = HubTabBar.CONTENT_TOP;
		initSectionButtons(y);
		y += buttonHeight + 5;
		int backTop = height - 6 - buttonHeight;
		resultTop = backTop - 2 - RESULT_HEIGHT;
		int backWidth = Math.min(200, width - 2 * MARGIN);
		addRenderableWidget(Button.builder(BACK, b -> goBack()).bounds((width - backWidth) / 2, backTop, backWidth, buttonHeight).build());

		if(section == Section.PLAYERS) {
			initPlayerHeader(y);
			y += buttonHeight + BUTTON_GAP;
			suggestionTop = y;
			y += SUGGESTION_HEIGHT + 4;
		} else {
			initFilterHeader(y);
			y += buttonHeight + 4;
		}
		listTop = y;
		listHeight = Math.max(0, resultTop - 2 - listTop);

		requestData();
		seenRevision = getData().getRevision();
		rebuildRows();
	}

	private void initSectionButtons(int y) {
		Section[] sections = Section.values();
		int total = Math.min(width - 2 * MARGIN, sections.length * 110 + (sections.length - 1) * BUTTON_GAP);
		int each = (total - (sections.length - 1) * BUTTON_GAP) / sections.length;
		int x = (width - total) / 2;
		for(Section other : sections) {
			Button button = Button.builder(other.label, b -> setSection(other)).bounds(x, y, each, buttonHeight).build();
			button.active = other != section;
			addRenderableWidget(button);
			x += each + BUTTON_GAP;
		}
	}

	private void initFilterHeader(int y) {
		int refreshWidth = Math.min(80, Math.max(50, font.width(REFRESH) + 14));
		int boxWidth = Math.min(200, listWidth - refreshWidth - BUTTON_GAP);
		filterBox = new EditBox(font, listLeft, y, boxWidth, buttonHeight, FILTER_HINT);
		filterBox.setMaxLength(64);
		filterBox.setHint(section == Section.TEAMS ? TEAM_FILTER_HINT : FILTER_HINT);
		filterBox.setValue(filterText);
		filterBox.setResponder(text -> {
			filterText = text;
			scroll = 0;
			rebuildRows();
		});
		addRenderableWidget(filterBox);
		refreshButton = addRenderableWidget(Button.builder(REFRESH, b -> onRefreshButton()).tooltip(REFRESH_TOOLTIP)
				.bounds(listLeft + boxWidth + BUTTON_GAP, y, refreshWidth, buttonHeight).build());
		refreshButton.active = refreshCooldown <= 0;
	}

	private void initPlayerHeader(int y) {
		int lookUpWidth = Math.min(80, Math.max(50, font.width(LOOK_UP) + 14));
		int boxWidth = Math.min(220, listWidth - lookUpWidth - BUTTON_GAP);
		playerBox = new EnterBox(font, listLeft, y, boxWidth, buttonHeight, PLAYER_HINT, () -> lookUp(playerBox.getValue()));
		playerBox.setMaxLength(MAX_PLAYER_QUERY_LENGTH);
		playerBox.setHint(PLAYER_HINT);
		AdminPlayerData player = getData().getPlayer();
		playerBox.setValue(filterText.isEmpty() && player != null ? player.name() : filterText);
		playerBox.setResponder(text -> {
			filterText = text;
			updateWidgets();
			refreshSuggestions();
		});
		addRenderableWidget(playerBox);
		lookUpButton = addRenderableWidget(Button.builder(LOOK_UP, b -> lookUp(playerBox.getValue())).tooltip(LOOK_UP_TOOLTIP)
				.bounds(listLeft + boxWidth + BUTTON_GAP, y, lookUpWidth, buttonHeight).build());
		updateWidgets();
	}

	private void updateWidgets() {
		if(lookUpButton != null && playerBox != null)
			lookUpButton.active = !playerBox.getValue().trim().isEmpty();
	}

	// ---- requests ----

	private void setSection(Section newSection) {
		if(newSection == section)
			return;
		section = newSection;
		lastSection = newSection;
		filterText = "";
		drafts.clear();
		scroll = 0;
		shownResult = null;
		shownPlayerId = null;
		rebuildWidgets();
	}

	private void requestData() {
		if(section.isConfig() && !configsRequested)
			requestConfigs();
		if(section == Section.TEAMS && !teamsRequested) {
			TeamClaimsClientRequests.requestAdminTeams();
			teamsRequested = true;
			refreshCooldown = REFRESH_COOLDOWN_TICKS;
		}
		AdminPlayerData player = getData().getPlayer();
		if(section == Section.PLAYERS && !playerRequested && player != null) {
			TeamClaimsClientRequests.requestAdminPlayer(player.playerId().toString());
			playerRequested = true;
		}
	}

	/**
	 * Asks for the config snapshot, unless one was asked for less than the server's minimum gap ago (the server would
	 * ignore it). Both configs come in one snapshot, so changing between the two config sections needs no new request.
	 */
	private void requestConfigs() {
		long now = System.currentTimeMillis();
		if(now - lastConfigRequestMillis < CONFIG_REQUEST_GAP_MILLIS)
			return;
		lastConfigRequestMillis = now;
		configsRequested = true;
		configWaitTicks = 0;
		TeamClaimsClientRequests.requestAdminConfigs();
		refreshCooldown = REFRESH_COOLDOWN_TICKS;
	}

	private void onRefreshButton() {
		if(section.isConfig())
			requestConfigs();
		else if(section == Section.TEAMS) {
			TeamClaimsClientRequests.requestAdminTeams();
			refreshCooldown = REFRESH_COOLDOWN_TICKS;
		}
		if(refreshButton != null)
			refreshButton.active = refreshCooldown <= 0;
	}

	private void lookUp(String text) {
		String query = text.trim();
		if(query.isEmpty())
			return;
		shownResult = null;
		TeamClaimsClientRequests.requestAdminPlayer(query);
	}

	// ---- rows ----

	/**
	 * Rebuilds the rows of the list from the data of the client, keeping the unsaved texts and the focus of the boxes.
	 */
	private void rebuildRows() {
		String focusedKey = null;
		int cursor = 0;
		for(Map.Entry<String, EditBox> entry : boxes.entrySet())
			if(entry.getValue().isFocused()) {
				focusedKey = entry.getKey();
				cursor = entry.getValue().getCursorPosition();
			}
		for(ListRow row : rows)
			for(Placed placed : row.widgets)
				removeWidget(placed.widget());
		rows.clear();
		boxes.clear();
		if(section.isConfig())
			buildConfigRows();
		else if(section == Section.PLAYERS)
			buildPlayerRows();
		else
			buildTeamRows();
		for(ListRow row : rows)
			for(Placed placed : row.widgets)
				addRenderableWidget(placed.widget());
		layoutList();
		if(focusedKey != null) {
			EditBox box = boxes.get(focusedKey);
			if(box != null && box.visible) {
				setFocused(box);
				box.setFocused(true);
				box.setCursorPosition(Math.min(cursor, box.getValue().length()));
				box.setHighlightPos(box.getCursorPosition());
			}
		}
	}

	private int getRowsHeight() {
		int total = 0;
		for(ListRow row : rows)
			total += row.height;
		return total;
	}

	/**
	 * Puts the rows that fit below the scroll position on the screen and hides the others.
	 */
	private void layoutList() {
		int used = 0;
		int start = rows.size();
		while(start > 0 && used + rows.get(start - 1).height <= listHeight) {
			used += rows.get(start - 1).height;
			start--;
		}
		maxScroll = Math.max(0, Math.min(start, rows.size() - 1));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		int y = listTop;
		int limit = listTop + listHeight;
		boolean fits = true;
		visibleCount = 0;
		for(int i = 0; i < rows.size(); i++) {
			ListRow row = rows.get(i);
			boolean show = i >= scroll && fits && (y + row.height <= limit || visibleCount == 0);
			if(i >= scroll && !show)
				fits = false;
			row.y = show ? y : -1;
			for(Placed placed : row.widgets) {
				placed.widget().visible = show;
				if(show)
					placed.widget().setY(y + placed.dy());
				else if(placed.widget().isFocused()) {
					placed.widget().setFocused(false);
					if(getFocused() == placed.widget())
						setFocused(null);
				}
			}
			if(show) {
				y += row.height;
				visibleCount++;
			}
		}
	}

	private void setScroll(int newScroll) {
		scroll = newScroll;
		layoutList();
	}

	private ListRow addRow(int rowHeight, boolean header) {
		ListRow row = new ListRow(rowHeight, header);
		rows.add(row);
		return row;
	}

	private EditBox createBox(ListRow row, String key, String saved, int x, int dy, int boxWidth, int maxLength, Component message, Consumer<String> onChange, Runnable onEnter) {
		EnterBox box = new EnterBox(font, x, 0, boxWidth, EDIT_HEIGHT, message, onEnter);
		box.setMaxLength(maxLength);
		String draft = drafts.get(key);
		box.setValue(draft == null || draft.equals(saved) ? saved : draft);
		box.setResponder(text -> {
			drafts.put(key, text);
			onChange.accept(text);
		});
		boxes.put(key, box);
		row.widgets.add(new Placed(box, dy));
		return box;
	}

	private Button createButton(ListRow row, Component label, int x, int dy, int buttonWidth, Button.OnPress onPress) {
		Button button = Button.builder(label, onPress).bounds(x, 0, buttonWidth, EDIT_HEIGHT).build();
		row.widgets.add(new Placed(button, dy));
		return button;
	}

	private static void setSaveState(Button save, boolean enabled, Component problem) {
		save.active = enabled;
		save.setTooltip(enabled ? SAVE_TOOLTIP : Tooltip.create(problem));
	}

	private static Component getProblem(AdminValueRules.Status status, @Nullable String range) {
		return switch(status) {
			case OK -> Component.translatable("gui.xaero_pac_hub_admin_unchanged");
			case EMPTY -> Component.translatable("gui.xaero_pac_hub_admin_problem_empty");
			case NOT_A_NUMBER -> Component.translatable("gui.xaero_pac_hub_admin_problem_number");
			case OUT_OF_RANGE -> Component.translatable("gui.xaero_pac_hub_admin_problem_range", range == null ? "" : range);
			case NOT_ALLOWED -> Component.translatable("gui.xaero_pac_hub_admin_problem_allowed");
			case TOO_LONG -> Component.translatable("gui.xaero_pac_hub_admin_problem_long");
		};
	}

	// ---- config rows ----

	private void buildConfigRows() {
		List<AdminConfigEntry> entries = getData().getConfigEntries(section.configId);
		if(!getData().hasConfigs()) {
			emptyMessage = Component.translatable("gui.xaero_pac_hub_admin_loading_configs");
			return;
		}
		List<AdminConfigView.Row> configRows = AdminConfigView.buildRows(entries, filterText);
		emptyMessage = configRows.isEmpty() ? Component.translatable("gui.xaero_pac_hub_admin_no_matches") : Component.empty();
		for(AdminConfigView.Row configRow : configRows) {
			if(configRow.isHeader())
				buildHeaderRow(configRow.header());
			else
				buildEntryRow(configRow.entry());
		}
	}

	private void buildHeaderRow(String label) {
		ListRow row = addRow(ROW_HEIGHT, true);
		String text = label.isEmpty() ? Component.translatable("gui.xaero_pac_hub_admin_general").getString() : label;
		row.painter = (guiGraphics, y) -> {
			guiGraphics.fill(listLeft, y + 4, listLeft + listWidth, y + ROW_HEIGHT - 1, HEADER_BACKGROUND);
			guiGraphics.drawString(font, clip(text, listWidth - 8), listLeft + 4, y + 9, HEADER_COLOR);
		};
	}

	private void buildEntryRow(AdminConfigEntry entry) {
		ListRow row = addRow(ROW_HEIGHT, false);
		String key = "c:" + entry.key();
		String saved = AdminValueRules.getSavedValue(entry);
		int right = listLeft + listWidth;
		int editorsWidth;
		switch(entry.type()) {
			case BOOLEAN -> {
				editorsWidth = clamp(listWidth * 3 / 10, 44, 70);
				boolean on = saved.trim().equalsIgnoreCase("true");
				Button toggle = createButton(row, on ? ON : OFF, right - editorsWidth, 1, editorsWidth, b -> sendConfig(entry, AdminValueRules.getNextValue(entry, false)));
				toggle.setTooltip(TOGGLE_TOOLTIP);
			}
			case ENUM -> {
				editorsWidth = clamp(listWidth * 35 / 100, 70, 140);
				Button cycle = createButton(row, Component.literal(saved), right - editorsWidth, 1, editorsWidth, b -> sendConfig(entry, AdminValueRules.getNextValue(entry, hasShiftDown())));
				cycle.setTooltip(CYCLE_TOOLTIP);
				cycle.active = !entry.allowedValues().isEmpty();
			}
			default -> {
				int boxWidth = entry.type() == AdminConfigEntry.ValueType.STRING ? clamp(listWidth * 3 / 10, 70, 180) : clamp(listWidth / 5, 56, 100);
				editorsWidth = boxWidth + BUTTON_GAP + SAVE_WIDTH;
				EditBox[] boxHolder = new EditBox[1];
				Runnable doSave = () -> saveEntry(entry, boxHolder[0]);
				Button save = createButton(row, SAVE, right - SAVE_WIDTH, 1, SAVE_WIDTH, b -> doSave.run());
				Consumer<String> onChange = text -> {
					AdminValueRules.Status status = AdminValueRules.check(entry, text);
					if(boxHolder[0] != null)
						boxHolder[0].setTextColor(status == AdminValueRules.Status.OK ? VALID_TEXT_COLOR : INVALID_TEXT_COLOR);
					setSaveState(save, AdminValueRules.canSave(entry, text), getProblem(status, AdminConfigView.getRangeText(entry)));
				};
				boxHolder[0] = createBox(row, key, saved, right - editorsWidth, 1, boxWidth, AdminValueRules.MAX_TEXT_LENGTH, Component.literal(entry.path()), onChange, doSave);
				onChange.accept(boxHolder[0].getValue());
			}
		}
		int nameWidth = Math.max(30, listWidth - editorsWidth - 6);
		row.tooltipWidth = nameWidth;
		row.painter = (guiGraphics, y) -> paintEntry(guiGraphics, y, entry, nameWidth);
		row.tooltip = () -> getEntryTooltip(entry);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private void saveEntry(AdminConfigEntry entry, EditBox box) {
		String text = box.getValue();
		if(!AdminValueRules.canSave(entry, text))
			return;
		sendConfig(entry, AdminValueRules.normalise(entry, text));
	}

	private void sendConfig(AdminConfigEntry entry, @Nullable String value) {
		if(value == null)
			return;
		shownResult = null;
		TeamClaimsClientRequests.setAdminConfig(entry.configId(), entry.path(), value);
	}

	private String clip(String text, int maxWidth) {
		if(font.width(text) <= maxWidth)
			return text;
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
	}

	private void paintEntry(GuiGraphics guiGraphics, int y, AdminConfigEntry entry, int nameWidth) {
		int x = listLeft + 3;
		int textWidth = nameWidth - 3;
		if(entry.restart()) {
			String badge = Component.translatable(nameWidth >= 140 ? "gui.xaero_pac_hub_admin_badge_restart" : "gui.xaero_pac_hub_admin_badge_restart_short").getString();
			int badgeWidth = font.width(badge) + 6;
			int badgeLeft = listLeft + nameWidth - badgeWidth;
			guiGraphics.fill(badgeLeft, y + 2, badgeLeft + badgeWidth, y + 12, BADGE_COLOR);
			guiGraphics.drawString(font, badge, badgeLeft + 3, y + 3, 0xFFFFFFFF);
			textWidth = badgeLeft - x - 3;
		}
		String name = AdminConfigView.humanise(AdminConfigView.getLastSegment(entry.path()));
		guiGraphics.drawString(font, clip(name, textWidth), x, y + 3, TEXT_COLOR);
		if(AdminConfigView.hasPending(entry))
			guiGraphics.drawString(font, clip(Component.translatable("gui.xaero_pac_hub_admin_pending", entry.pendingValue()).getString(), nameWidth - 3), x, y + 13, HINT_COLOR);
		else
			guiGraphics.drawString(font, clip(Component.translatable("gui.xaero_pac_hub_admin_default", entry.defaultValue()).getString(), nameWidth - 3), x, y + 13, VALUE_COLOR);
	}

	private List<Component> getEntryTooltip(AdminConfigEntry entry) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(entry.path()).withStyle(ChatFormatting.GOLD));
		if(!entry.comment().isEmpty())
			lines.add(Component.literal(entry.comment()).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("gui.xaero_pac_hub_admin_tt_default", entry.defaultValue()));
		String range = AdminConfigView.getRangeText(entry);
		if(range != null)
			lines.add(Component.translatable("gui.xaero_pac_hub_admin_tt_range", range));
		if(entry.type() == AdminConfigEntry.ValueType.ENUM && !entry.allowedValues().isEmpty())
			lines.add(Component.translatable("gui.xaero_pac_hub_admin_tt_allowed", String.join(", ", entry.allowedValues())));
		lines.add(Component.translatable("gui.xaero_pac_hub_admin_tt_running", entry.value()));
		if(entry.restart()) {
			if(AdminConfigView.hasPending(entry))
				lines.add(Component.translatable("gui.xaero_pac_hub_admin_tt_pending", entry.pendingValue()).withStyle(ChatFormatting.YELLOW));
			lines.add(Component.translatable("gui.xaero_pac_hub_admin_tt_restart").withStyle(ChatFormatting.YELLOW));
		}
		return lines;
	}

	// ---- player rows ----

	private void buildPlayerRows() {
		AdminPlayerData player = getData().getPlayer();
		if(player == null) {
			emptyMessage = Component.translatable("gui.xaero_pac_hub_admin_player_none");
			shownPlayerId = null;
			return;
		}
		if(!player.playerId().equals(shownPlayerId)) {
			drafts.clear();
			shownPlayerId = player.playerId();
		}
		emptyMessage = Component.empty();
		ListRow info = addRow(ROW_HEIGHT, true);
		info.painter = (guiGraphics, y) -> {
			guiGraphics.fill(listLeft, y + 1, listLeft + listWidth, y + ROW_HEIGHT - 1, HEADER_BACKGROUND);
			Component state = Component.translatable(player.online() ? "gui.xaero_pac_hub_admin_player_online" : "gui.xaero_pac_hub_admin_player_offline");
			int stateWidth = font.width(state);
			guiGraphics.drawString(font, clip(player.name(), listWidth - stateWidth - 14), listLeft + 4, y + 3, HEADER_COLOR);
			guiGraphics.drawString(font, state, listLeft + listWidth - stateWidth - 4, y + 3, player.online() ? SUCCESS_COLOR : VALUE_COLOR);
			guiGraphics.drawString(font, clip(player.playerId().toString(), listWidth - 8), listLeft + 4, y + 12, VALUE_COLOR);
		};
		for(AdminPlayerBonus bonus : AdminPlayerBonus.values())
			buildBonusRow(player, bonus);
	}

	private void buildBonusRow(AdminPlayerData player, AdminPlayerBonus bonus) {
		ListRow row = addRow(ROW_HEIGHT, false);
		String key = "p:" + bonus.name();
		int right = listLeft + listWidth;
		int editorsWidth = BONUS_BOX_WIDTH + BUTTON_GAP + SAVE_WIDTH;
		String saved = Integer.toString(player.bonus(bonus));
		String range = -AdminPlayerBonus.MAX_ABS_VALUE + " .. " + AdminPlayerBonus.MAX_ABS_VALUE;
		EditBox[] boxHolder = new EditBox[1];
		Runnable doSave = () -> {
			EditBox box = boxHolder[0];
			if(box != null && AdminValueRules.checkInt(box.getValue(), -AdminPlayerBonus.MAX_ABS_VALUE, AdminPlayerBonus.MAX_ABS_VALUE) == AdminValueRules.Status.OK)
				sendPlayerBonus(player, bonus, AdminValueRules.parseInt(box.getValue()));
		};
		Button save = createButton(row, SAVE, right - SAVE_WIDTH, 1, SAVE_WIDTH, b -> doSave.run());
		Consumer<String> onChange = text -> {
			AdminValueRules.Status status = AdminValueRules.checkInt(text, -AdminPlayerBonus.MAX_ABS_VALUE, AdminPlayerBonus.MAX_ABS_VALUE);
			if(boxHolder[0] != null)
				boxHolder[0].setTextColor(status == AdminValueRules.Status.OK ? VALID_TEXT_COLOR : INVALID_TEXT_COLOR);
			boolean changed = status == AdminValueRules.Status.OK && AdminValueRules.parseInt(text) != player.bonus(bonus);
			setSaveState(save, changed, getProblem(status, range));
		};
		boxHolder[0] = createBox(row, key, saved, right - editorsWidth, 1, BONUS_BOX_WIDTH, 8, Component.translatable(getBonusKey(bonus)), onChange, doSave);
		onChange.accept(boxHolder[0].getValue());
		int textWidth = Math.max(30, listWidth - editorsWidth - 6);
		row.tooltipWidth = textWidth;
		row.painter = (guiGraphics, y) -> paintBonusRow(guiGraphics, y, player, bonus, textWidth);
		row.tooltip = () -> List.of(Component.translatable(getBonusKey(bonus)).withStyle(ChatFormatting.GOLD),
				Component.translatable("gui.xaero_pac_hub_admin_player_bonus_tooltip", range).withStyle(ChatFormatting.GRAY));
	}

	private static String getBonusKey(AdminPlayerBonus bonus) {
		return switch(bonus) {
			case CLAIMS -> "gui.xaero_pac_hub_admin_bonus_claims";
			case FORCELOADS -> "gui.xaero_pac_hub_admin_bonus_forceloads";
			case ANCHORS -> "gui.xaero_pac_hub_admin_bonus_anchors";
			case ANCHOR_RANGE -> "gui.xaero_pac_hub_admin_bonus_anchor_range";
		};
	}

	private String formatLimit(int limit) {
		return limit == Integer.MAX_VALUE ? Component.translatable("gui.xaero_pac_hub_admin_unlimited").getString() : Integer.toString(limit);
	}

	private void paintBonusRow(GuiGraphics guiGraphics, int y, AdminPlayerData player, AdminPlayerBonus bonus, int textWidth) {
		int x = listLeft + 3;
		guiGraphics.drawString(font, clip(Component.translatable(getBonusKey(bonus)).getString(), textWidth - 3), x, y + 3, TEXT_COLOR);
		int base = AdminConfigView.getBase(player, bonus);
		int total = AdminConfigView.getTotal(base, player.bonus(bonus));
		int used = AdminConfigView.getUsed(player, bonus);
		Component line;
		int color = VALUE_COLOR;
		if(used < 0)
			line = Component.translatable("gui.xaero_pac_hub_admin_player_total", formatLimit(total), formatLimit(base));
		else {
			line = Component.translatable("gui.xaero_pac_hub_admin_player_usage", used, formatLimit(total), formatLimit(base));
			if(AdminConfigView.isOverLimit(used, total))
				color = WARNING_COLOR;
		}
		guiGraphics.drawString(font, clip(line.getString(), textWidth - 3), x, y + 13, color);
	}

	private void sendPlayerBonus(AdminPlayerData player, AdminPlayerBonus bonus, int value) {
		shownResult = null;
		TeamClaimsClientRequests.setPlayerBonus(player.playerId(), bonus, value);
	}

	private List<String> computeSuggestions() {
		ClientPacketListener connection = minecraft.getConnection();
		if(connection == null)
			return List.of();
		String filter = playerBox == null ? "" : playerBox.getValue().trim().toLowerCase(Locale.ROOT);
		List<String> suggestions = new ArrayList<>();
		for(PlayerInfo playerInfo : connection.getOnlinePlayers()) {
			String name = playerInfo.getProfile().getName();
			if(!PartyRules.isSafePlayerName(name))
				continue;
			if(!filter.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(filter))
				continue;
			suggestions.add(name);
		}
		suggestions.sort(String.CASE_INSENSITIVE_ORDER);
		return suggestions;
	}

	private void refreshSuggestions() {
		if(section != Section.PLAYERS || playerBox == null)
			return;
		List<String> suggestions = computeSuggestions();
		String filter = playerBox.getValue();
		if(suggestions.equals(displayedSuggestions) && filter.equals(displayedSuggestionFilter))
			return;
		displayedSuggestions = suggestions;
		displayedSuggestionFilter = filter;
		for(Button button : suggestionButtons)
			removeWidget(button);
		suggestionButtons.clear();
		int x = listLeft;
		for(String name : suggestions) {
			int buttonWidth = font.width(name) + 10;
			if(x + buttonWidth > listLeft + listWidth)
				break;
			Button button = Button.builder(Component.literal(name), b -> {
				playerBox.setValue(name);
				lookUp(name);
			}).bounds(x, suggestionTop, buttonWidth, SUGGESTION_HEIGHT)
					.tooltip(Tooltip.create(Component.translatable("gui.xaero_pac_hub_admin_suggestion_tooltip", name))).build();
			suggestionButtons.add(addRenderableWidget(button));
			x += buttonWidth + BUTTON_GAP;
		}
	}

	// ---- team rows ----

	private void buildTeamRows() {
		if(!getData().hasTeams()) {
			emptyMessage = Component.translatable("gui.xaero_pac_hub_admin_loading_teams");
			return;
		}
		List<String> terms = AdminConfigView.getTerms(filterText);
		for(AdminTeamData team : getData().getTeams()) {
			String name = team.name().toLowerCase(Locale.ROOT);
			boolean matches = true;
			for(String term : terms)
				if(!name.contains(term))
					matches = false;
			if(matches)
				buildTeamRow(team);
		}
		emptyMessage = rows.isEmpty() ? Component.translatable(terms.isEmpty() ? "gui.xaero_pac_hub_admin_no_teams" : "gui.xaero_pac_hub_admin_no_matches") : Component.empty();
	}

	private void buildTeamRow(AdminTeamData team) {
		ListRow row = addRow(TEAM_ROW_HEIGHT, false);
		int halfWidth = (listWidth - 6) / 2;
		for(int column = 0; column < 2; column++) {
			boolean forceloads = column == 1;
			int columnLeft = listLeft + column * (halfWidth + 6);
			buildTeamBonusControls(row, team, forceloads, columnLeft, halfWidth);
		}
		row.tooltipWidth = listWidth;
		row.painter = (guiGraphics, y) -> paintTeamRow(guiGraphics, y, team, halfWidth);
		row.tooltip = () -> List.of(Component.literal(team.name()).withStyle(ChatFormatting.GOLD),
				Component.translatable("gui.xaero_pac_hub_admin_team_id", team.partyId().toString()).withStyle(ChatFormatting.GRAY),
				Component.translatable("gui.xaero_pac_hub_admin_team_bonus_tooltip", MAX_TEAM_BONUS).withStyle(ChatFormatting.GRAY));
	}

	private void buildTeamBonusControls(ListRow row, AdminTeamData team, boolean forceloads, int columnLeft, int halfWidth) {
		String key = "t:" + team.partyId() + (forceloads ? ":f" : ":c");
		int current = forceloads ? team.bonusForceloads() : team.bonusClaims();
		int saveLeft = columnLeft + halfWidth - TEAM_SAVE_WIDTH;
		int boxLeft = saveLeft - BUTTON_GAP - TEAM_BOX_WIDTH;
		EditBox[] boxHolder = new EditBox[1];
		Runnable doSave = () -> {
			EditBox box = boxHolder[0];
			if(box != null && AdminValueRules.checkInt(box.getValue(), 0, MAX_TEAM_BONUS) == AdminValueRules.Status.OK) {
				shownResult = null;
				TeamClaimsClientRequests.setTeamBonus(team.partyId(), forceloads, AdminValueRules.parseInt(box.getValue()));
			}
		};
		Button save = createButton(row, SAVE, saveLeft, 24, TEAM_SAVE_WIDTH, b -> doSave.run());
		String range = "0 .. " + MAX_TEAM_BONUS;
		Consumer<String> onChange = text -> {
			AdminValueRules.Status status = AdminValueRules.checkInt(text, 0, MAX_TEAM_BONUS);
			if(boxHolder[0] != null)
				boxHolder[0].setTextColor(status == AdminValueRules.Status.OK ? VALID_TEXT_COLOR : INVALID_TEXT_COLOR);
			boolean changed = status == AdminValueRules.Status.OK && AdminValueRules.parseInt(text) != current;
			setSaveState(save, changed, getProblem(status, range));
		};
		boxHolder[0] = createBox(row, key, Integer.toString(current), boxLeft, 24, TEAM_BOX_WIDTH, 8,
				Component.translatable(forceloads ? "gui.xaero_pac_hub_admin_team_bonus_forceloads" : "gui.xaero_pac_hub_admin_team_bonus_claims"), onChange, doSave);
		onChange.accept(boxHolder[0].getValue());
	}

	private void paintTeamRow(GuiGraphics guiGraphics, int y, AdminTeamData team, int halfWidth) {
		int x = listLeft + 3;
		String members = Component.translatable("gui.xaero_pac_hub_admin_team_members", team.memberCount()).getString();
		int membersWidth = font.width(members);
		guiGraphics.drawString(font, clip(team.name(), listWidth - membersWidth - 14), x, y + 3, TEXT_COLOR);
		guiGraphics.drawString(font, members, listLeft + listWidth - membersWidth - 3, y + 3, VALUE_COLOR);
		String claims = Component.translatable("gui.xaero_pac_hub_admin_team_claims", team.claims(), team.claimLimit()).getString();
		String forceloads = Component.translatable("gui.xaero_pac_hub_admin_team_forceloads", team.forceloads(), team.forceloadLimit()).getString();
		guiGraphics.drawString(font, claims, x, y + 13, team.claims() > team.claimLimit() ? WARNING_COLOR : VALUE_COLOR);
		int forceloadsLeft = x + font.width(claims) + 10;
		guiGraphics.drawString(font, clip(forceloads, listLeft + listWidth - forceloadsLeft - 3), forceloadsLeft, y + 13, team.forceloads() > team.forceloadLimit() ? WARNING_COLOR : VALUE_COLOR);
		for(int column = 0; column < 2; column++) {
			int columnLeft = listLeft + column * (halfWidth + 6);
			int labelWidth = halfWidth - TEAM_SAVE_WIDTH - BUTTON_GAP - TEAM_BOX_WIDTH - 4;
			String label = Component.translatable(column == 1 ? "gui.xaero_pac_hub_admin_team_bonus_forceloads" : "gui.xaero_pac_hub_admin_team_bonus_claims").getString();
			guiGraphics.drawString(font, clip(label, labelWidth), columnLeft + 3, y + 24 + 6, TEXT_COLOR);
		}
	}

	// ---- screen ----

	@Override
	public void tick() {
		super.tick();
		if(minecraft.screen != this)
			return;
		if(refreshCooldown > 0) {
			refreshCooldown--;
			if(refreshButton != null)
				refreshButton.active = refreshCooldown <= 0;
		}
		if(section.isConfig()) {
			if(!configsRequested)
				requestConfigs();//the first request was too soon after another screen's
			else if(!getData().hasConfigs() && ++configWaitTicks >= CONFIG_RETRY_TICKS) {
				configWaitTicks = 0;
				long now = System.currentTimeMillis();
				lastConfigRequestMillis = Math.min(lastConfigRequestMillis, now - CONFIG_REQUEST_GAP_MILLIS);
				requestConfigs();
			}
		}
		int revision = getData().getRevision();
		if(revision != seenRevision) {
			seenRevision = revision;
			onDataChanged();
		}
		refreshSuggestions();
	}

	private void onDataChanged() {
		ClientboundAdminResultPacket.Result result = getData().getLastResult();
		if(result != null && result != lastSeenResult) {
			lastSeenResult = result;
			shownResult = result;
		}
		AdminPlayerData player = getData().getPlayer();
		if(player != lastSeenPlayer) {
			lastSeenPlayer = player;
			if(shownResult != null && shownResult.action() == ClientboundAdminResultPacket.Action.PLAYER_LOOKUP)
				shownResult = null;//a failed lookup, and now a player was found
		}
		rebuildRows();
	}

	private boolean isTextFieldFocused() {
		return getFocused() instanceof EditBox box && box.isFocused();
	}

	/**
	 * Lets the key that opens the hub be typed into a box instead of closing the screen.
	 */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(isTextFieldFocused() && HubTabs.isHubKey(keyCode))
			return getFocused().keyPressed(keyCode, scanCode, modifiers);
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private boolean isOverList(double mouseX, double mouseY) {
		return mouseX >= listLeft - 2 && mouseX < scrollbarLeft + SCROLLBAR_WIDTH && mouseY >= listTop && mouseY < listTop + listHeight;
	}

	private boolean isOverScrollbar(double mouseX, double mouseY) {
		return maxScroll > 0 && mouseX >= scrollbarLeft - 1 && mouseX < scrollbarLeft + SCROLLBAR_WIDTH + 1 && mouseY >= listTop && mouseY < listTop + listHeight;
	}

	private void scrollToMouse(double mouseY) {
		if(maxScroll <= 0 || listHeight <= 0)
			return;
		double fraction = (mouseY - listTop) / listHeight;
		setScroll((int) Math.round(Math.max(0, Math.min(1, fraction)) * maxScroll));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if(button == 0 && openDropdown == null && isOverScrollbar(mouseX, mouseY)) {
			draggingScrollbar = true;
			scrollToMouse(mouseY);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if(draggingScrollbar && button == 0) {
			scrollToMouse(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if(draggingScrollbar && button == 0) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(openDropdown == null && mouseY >= listTop && scrollY != 0 && maxScroll > 0) {
			int step = section == Section.TEAMS ? 1 : 2;
			setScroll(scroll - (int) Math.signum(scrollY) * step);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ---- rendering ----

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
		super.renderBackground(guiGraphics, mouseX, mouseY, partial);
		if(listHeight <= 0)
			return;
		guiGraphics.fill(listLeft - 2, listTop - 1, listLeft + listWidth + 1, listTop + listHeight + 1, LIST_BACKGROUND);
		int index = 0;
		for(ListRow row : rows) {
			if(row.y >= 0 && !row.header && (scroll + index++) % 2 == 1)
				guiGraphics.fill(listLeft, row.y, listLeft + listWidth, row.y + row.height, STRIPE_COLOR);
		}
		if(maxScroll > 0) {
			guiGraphics.fill(scrollbarLeft, listTop, scrollbarLeft + SCROLLBAR_WIDTH, listTop + listHeight, SCROLLBAR_TRACK);
			int thumbHeight = Math.max(10, listHeight * visibleCount / Math.max(1, rows.size()));
			int thumbTop = listTop + (listHeight - thumbHeight) * scroll / maxScroll;
			guiGraphics.fill(scrollbarLeft, thumbTop, scrollbarLeft + SCROLLBAR_WIDTH, thumbTop + thumbHeight, SCROLLBAR_THUMB);
		}
	}

	@Override
	protected void renderPreDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
		super.renderPreDropdown(guiGraphics, mouseX, mouseY, partial);
		for(ListRow row : rows)
			if(row.y >= 0 && row.painter != null)
				row.painter.paint(guiGraphics, row.y);
		if(rows.isEmpty() || getRowsHeight() == 0) {
			for(FormattedCharSequence line : font.split(emptyMessage, listWidth - 16)) {
				guiGraphics.drawCenteredString(font, line, width / 2, listTop + 8, VALUE_COLOR);
				break;
			}
		}
		if(filterBox != null && section.isConfig() && getData().hasConfigs())
			renderCount(guiGraphics);
		renderResult(guiGraphics);
		renderRowTooltip(guiGraphics, mouseX, mouseY);
	}

	private void renderCount(GuiGraphics guiGraphics) {
		int total = getData().getConfigEntries(section.configId).size();
		int shown = AdminConfigView.countEntries(AdminConfigView.buildRows(getData().getConfigEntries(section.configId), filterText));
		String text = Component.translatable("gui.xaero_pac_hub_admin_count", shown, total).getString();
		int left = refreshButton.getX() + refreshButton.getWidth() + 6;
		int available = listLeft + listWidth - left;
		if(font.width(text) <= available)
			guiGraphics.drawString(font, text, left, filterBox.getY() + (buttonHeight - font.lineHeight) / 2, VALUE_COLOR);
	}

	private void renderResult(GuiGraphics guiGraphics) {
		if(shownResult == null)
			return;
		int color = shownResult.success() ? SUCCESS_COLOR : WARNING_COLOR;
		int y = resultTop;
		int lines = 0;
		for(FormattedCharSequence line : font.split(shownResult.message(), width - 2 * MARGIN)) {
			if(++lines > 2)
				break;
			guiGraphics.drawString(font, line, MARGIN, y, color);
			y += font.lineHeight + 1;
		}
	}

	private void renderRowTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if(openDropdown != null || !isOverList(mouseX, mouseY))
			return;
		for(ListRow row : rows) {
			if(row.y < 0 || row.tooltip == null || mouseY < row.y || mouseY >= row.y + row.height)
				continue;
			if(mouseX < listLeft || mouseX >= listLeft + row.tooltipWidth)
				return;
			List<FormattedCharSequence> tooltip = new ArrayList<>();
			for(Component line : row.tooltip.get())
				tooltip.addAll(font.split(line, TOOLTIP_WIDTH));
			guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY);
			return;
		}
	}

}
