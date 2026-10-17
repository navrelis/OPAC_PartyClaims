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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.gui.party.PartyScreenCommands;
import xaero.pac.client.parties.party.IClientPartyStorage;
import xaero.pac.client.player.config.IPlayerConfigClientStorageManager;
import xaero.pac.client.teamclaims.TeamClaimsClientData;
import xaero.pac.common.packet.parties.PartyBudgetData;
import xaero.pac.common.packet.teamclaims.TeamInfoData;
import xaero.pac.teamclaims.config.TeamAction;
import xaero.pac.teamclaims.config.TeamRole;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Team tab of the hub: the team name, its member count and budget, the ranks that the team requires to claim,
 * unclaim and forceload team claims, the player's own territory message setting, the claim colour and name of the team,
 * a button to the full settings of the team sub-config, the choice between private and team claims, and the conversion
 * of the claims around the player.
 * <p>
 * Everything is sent as the commands that the player could type ({@code /teamclaims roles}, {@code territorymessages},
 * {@code convert}, built by {@link TeamHubLogic} from enum values only) or, for the Private/Team choice, as the option
 * packet of {@link ClaimAsToggle}. The server checks every one of them; the tab only enables what the player may do. The
 * answers of the commands are chat messages, so after an action the tab asks the server for the team information again
 * ({@link RefreshSchedule}) and shows the new values when they arrive.
 */
public class TeamTabScreen extends HubTabScreen {

	private static final Component TITLE = Component.translatable("gui.xaero_pac_hub_tab_team");
	private static final Component LOADING = Component.translatable("gui.xaero_pac_hub_team_loading");
	private static final Component ROLES_HEADING = Component.translatable("gui.xaero_pac_hub_team_roles_heading");
	private static final Component CONVERT_HEADING = Component.translatable("gui.xaero_pac_hub_team_convert_heading");
	private static final Component SETTINGS = Component.translatable("gui.xaero_pac_hub_team_settings");
	private static final Component TERRITORY_ON = Component.translatable("gui.xaero_pac_hub_team_territory_on");
	private static final Component TERRITORY_OFF = Component.translatable("gui.xaero_pac_hub_team_territory_off");
	private static final Component CONVERT_TO_TEAM = Component.translatable("gui.xaero_pac_hub_team_convert_to_team");
	private static final Component CONVERT_TO_PERSONAL = Component.translatable("gui.xaero_pac_hub_team_convert_to_personal");
	private static final Component CONVERT_CONFIRM = Component.translatable("gui.xaero_pac_hub_team_convert_confirm");
	private static final Component NONE = Component.translatable("gui.xaero_pac_hub_team_claims_name_none");
	private static final Tooltip SETTINGS_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_team_settings_tooltip"));
	private static final Tooltip SETTINGS_NO_SUB_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_team_settings_no_sub_tooltip"));
	private static final Tooltip TERRITORY_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_team_territory_tooltip"));
	private static final Tooltip RADIUS_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_team_radius_tooltip"));
	private static final Tooltip CONVERT_TO_TEAM_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_team_convert_to_team_tooltip"));
	private static final Tooltip CONVERT_TO_PERSONAL_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_team_convert_to_personal_tooltip"));

	private static final Map<TeamAction, Tooltip> ROLE_TOOLTIPS = new EnumMap<>(TeamAction.class);
	private static final Map<TeamAction, Tooltip> ROLE_READ_ONLY_TOOLTIPS = new EnumMap<>(TeamAction.class);

	static {
		for(TeamAction action : TeamAction.values()) {
			Component tooltip = Component.translatable("gui.xaero_pac_hub_team_role_tooltip_" + action.id());
			ROLE_TOOLTIPS.put(action, Tooltip.create(tooltip));
			ROLE_READ_ONLY_TOOLTIPS.put(action, Tooltip.create(Component.empty().append(tooltip).append("\n\n")
					.append(Component.translatable("gui.xaero_pac_hub_team_role_read_only").withStyle(ChatFormatting.YELLOW))));
		}
	}

	private static final int MARGIN = 8;
	private static final int BOTTOM_MARGIN = 6;
	private static final int MAX_CONTENT_WIDTH = 400;
	private static final int MIN_COLUMN_WIDTH = 140;
	private static final int COLUMN_GAP = 8;
	private static final int BUTTON_GAP = 2;
	private static final int TALL_SCREEN_HEIGHT = 260;
	private static final int LINE_STEP = 10;
	private static final int SECTION_GAP = 5;
	private static final int MAX_HEADER_LINES = 6;
	private static final int MAX_TEXT_WIDTH = 260;
	private static final int REFRESH_INTERVAL_TICKS = 40;
	private static final int REFRESH_AFTER_ACTION_TICKS = 20;
	private static final int SWATCH_SIZE = 8;

	private static final int TEXT_COLOR = HubBudgetLines.TEXT_COLOR;
	private static final int VALUE_COLOR = 0xFFAAAAAA;

	/**
	 * The radius of the conversion, kept while the game runs so that it is not reset by switching tabs.
	 */
	private static int convertRadius;

	private enum State {
		LOADING, NO_TEAM, READY
	}

	/**
	 * One line of the header, already wrapped.
	 */
	private record HeaderLine(FormattedCharSequence text, int color) {
	}

	private State state = State.LOADING;
	private final RefreshSchedule refreshSchedule = new RefreshSchedule(REFRESH_INTERVAL_TICKS, REFRESH_AFTER_ACTION_TICKS);
	private boolean requested;
	private int seenRevision = -1;

	private int buttonHeight;
	private int contentLeft;
	private int leftColumnLeft;
	private int rightColumnLeft;
	private int columnWidth;
	private int headerTop;
	private int rolesHeadingTop;
	private int roleLabelWidth;
	private int appearanceTop;
	private int convertHeadingTop;
	private List<HeaderLine> headerLines = List.of();
	private final Map<TeamAction, Integer> roleRowTops = new EnumMap<>(TeamAction.class);

	private final Map<TeamAction, Button> roleButtons = new EnumMap<>(TeamAction.class);
	private Button territoryButton;
	private Button claimAsButton;
	private Button settingsButton;
	private Button radiusButton;

	/**
	 * What the player picked since the last answer of the server, shown until the answer arrives.
	 */
	private final Map<TeamAction, TeamRole> pendingRoles = new EnumMap<>(TeamAction.class);
	@Nullable
	private Boolean pendingTerritoryMessages;

	@Nullable
	private String teamSubId;

	public TeamTabScreen(Screen escape, Screen parent) {
		super(escape, parent, TITLE);
	}

	@Override
	protected HubTab getTab() {
		return HubTab.TEAM;
	}

	// ---- data ----

	@Nullable
	private static TeamInfoData getInfo() {
		return TeamClaimsClientData.INSTANCE.getTeamInfo();
	}

	private static IClientPartyStorage<?, ?, ?> getPartyStorage() {
		return OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClientPartyStorage();
	}

	private static IPlayerConfigClientStorageManager<?> getConfigManager() {
		return OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigStorageManager();
	}

	private static State computeState() {
		TeamInfoData info = getInfo();
		if(info == null)
			return State.LOADING;
		return info.available() ? State.READY : State.NO_TEAM;
	}

	private TeamRole getShownRole(TeamInfoData info, TeamAction action) {
		TeamRole pending = pendingRoles.get(action);
		if(pending != null)
			return pending;
		return switch(action) {
			case CLAIM -> info.claimRole();
			case UNCLAIM -> info.unclaimRole();
			case FORCELOAD -> info.forceloadRole();
		};
	}

	private boolean isTerritoryMessagesShown(TeamInfoData info) {
		return pendingTerritoryMessages != null ? pendingTerritoryMessages : info.territoryMessages();
	}

	private void updateTeamSubId() {
		teamSubId = ClaimsHubLogic.findTeamSubId(getConfigManager().getMyPlayerConfig().getSubConfigIds());
	}

	// ---- layout ----

	@Override
	protected void init() {
		super.init();
		state = computeState();
		seenRevision = TeamClaimsClientData.INSTANCE.getRevision();
		roleButtons.clear();
		roleRowTops.clear();
		territoryButton = null;
		claimAsButton = null;
		settingsButton = null;
		radiusButton = null;
		headerLines = List.of();
		buttonHeight = height >= TALL_SCREEN_HEIGHT ? 20 : 16;
		updateTeamSubId();
		TeamInfoData info = getInfo();
		if(state == State.READY && info != null)
			initReady(info);
		addRenderableWidget(Button.builder(BACK, b -> goBack()).bounds(width / 2 - 100, height - BOTTOM_MARGIN - buttonHeight, 200, buttonHeight).build());
		if(!requested) {
			requested = true;
			requestTeamInfo();
		}
		updateWidgets();
	}

	private int getContentWidth() {
		return Math.min(width - 2 * MARGIN, MAX_CONTENT_WIDTH);
	}

	private void initReady(TeamInfoData info) {
		int contentWidth = getContentWidth();
		contentLeft = (width - contentWidth) / 2;
		headerTop = HubTabBar.CONTENT_TOP;
		headerLines = computeHeaderLines(info, contentWidth);
		int top = headerTop + headerLines.size() * LINE_STEP + SECTION_GAP;
		int step = buttonHeight + BUTTON_GAP;
		boolean twoColumns = contentWidth >= 2 * MIN_COLUMN_WIDTH + COLUMN_GAP;
		columnWidth = twoColumns ? (contentWidth - COLUMN_GAP) / 2 : contentWidth;
		leftColumnLeft = contentLeft;
		rightColumnLeft = twoColumns ? contentLeft + columnWidth + COLUMN_GAP : contentLeft;

		// left column: the ranks, the territory messages and the appearance of the team
		int y = top;
		rolesHeadingTop = y;
		y += LINE_STEP + 1;
		int labelWidth = 0;
		for(TeamAction action : TeamAction.values())
			labelWidth = Math.max(labelWidth, font.width(getActionLabel(action)));
		roleLabelWidth = Math.min(labelWidth + 6, columnWidth / 2);
		for(TeamAction action : TeamAction.values()) {
			roleRowTops.put(action, y);
			roleButtons.put(action, addRenderableWidget(Button.builder(Component.empty(), b -> onRoleButton(action))
					.bounds(leftColumnLeft + roleLabelWidth, y, columnWidth - roleLabelWidth, buttonHeight).build()));
			y += step;
		}
		territoryButton = addRenderableWidget(Button.builder(TERRITORY_ON, b -> onTerritoryButton())
				.tooltip(TERRITORY_TOOLTIP).bounds(leftColumnLeft, y, columnWidth, buttonHeight).build());
		y += step;
		appearanceTop = y + 2;
		int leftBottom = appearanceTop + 2 * LINE_STEP;

		// right column: the private or team choice, the full settings and the conversion
		int ry = twoColumns ? top : leftBottom + SECTION_GAP;
		claimAsButton = addRenderableWidget(Button.builder(Component.empty(), b -> onClaimAsButton())
				.bounds(rightColumnLeft, ry, columnWidth, buttonHeight).build());
		ry += step;
		settingsButton = addRenderableWidget(Button.builder(SETTINGS, b -> onSettingsButton())
				.bounds(rightColumnLeft, ry, columnWidth, buttonHeight).build());
		ry += step + SECTION_GAP;
		convertHeadingTop = ry;
		ry += LINE_STEP + 1;
		radiusButton = addRenderableWidget(Button.builder(Component.empty(), b -> onRadiusButton())
				.tooltip(RADIUS_TOOLTIP).bounds(rightColumnLeft, ry, columnWidth, buttonHeight).build());
		ry += step;
		int halfWidth = (columnWidth - BUTTON_GAP) / 2;
		addRenderableWidget(Button.builder(CONVERT_TO_TEAM, b -> onConvertButton(true))
				.tooltip(CONVERT_TO_TEAM_TOOLTIP).bounds(rightColumnLeft, ry, halfWidth, buttonHeight).build());
		addRenderableWidget(Button.builder(CONVERT_TO_PERSONAL, b -> onConvertButton(false))
				.tooltip(CONVERT_TO_PERSONAL_TOOLTIP).bounds(rightColumnLeft + columnWidth - halfWidth, ry, halfWidth, buttonHeight).build());
	}

	private static Component getActionLabel(TeamAction action) {
		return Component.translatable("gui.xaero_pac_hub_team_role_" + action.id());
	}

	/**
	 * Gets the lines above the controls: the team name with the member count, then the budget lines of the team with the
	 * running over-limit deadlines counted down locally, as in the party screen.
	 */
	private List<HeaderLine> computeHeaderLines(TeamInfoData info, int contentWidth) {
		List<HeaderLine> lines = new ArrayList<>();
		Component name = Component.literal(info.teamName()).withStyle(ChatFormatting.GOLD);
		addHeaderLines(lines, Component.translatable("gui.xaero_pac_hub_team_header", name, info.memberCount()), -1, contentWidth);
		IClientPartyStorage<?, ?, ?> partyStorage = getPartyStorage();
		PartyBudgetData budget = partyStorage.getBudget();
		if(budget != null) {
			long elapsed = Math.max(0, System.currentTimeMillis() - partyStorage.getBudgetReceivedTime());
			for(HubBudgetLines.Line line : HubBudgetLines.teamLines(budget, elapsed, info))
				addHeaderLines(lines, line.text(), line.color(), contentWidth);
		}
		return lines.size() > MAX_HEADER_LINES ? List.copyOf(lines.subList(0, MAX_HEADER_LINES)) : lines;
	}

	private void addHeaderLines(List<HeaderLine> lines, Component text, int color, int contentWidth) {
		for(FormattedCharSequence part : font.split(text, contentWidth))
			lines.add(new HeaderLine(part, color));
	}

	// ---- actions ----

	private void sendCommand(@Nullable String command) {
		PartyScreenCommands.send(minecraft, command);
		refreshSchedule.afterAction();
	}

	private void requestTeamInfo() {
		if(TeamInfoRefresh.request())
			refreshSchedule.onRequestSent();
	}

	private void onRoleButton(TeamAction action) {
		TeamInfoData info = getInfo();
		if(info == null || !info.canEdit())
			return;
		TeamRole next = TeamHubLogic.nextRole(getShownRole(info, action));
		pendingRoles.put(action, next);
		sendCommand(TeamHubLogic.rolesCommand(action, next));
		updateWidgets();
	}

	private void onTerritoryButton() {
		TeamInfoData info = getInfo();
		if(info == null)
			return;
		boolean on = !isTerritoryMessagesShown(info);
		pendingTerritoryMessages = on;
		sendCommand(TeamHubLogic.territoryMessagesCommand(on));
		updateWidgets();
	}

	private void onClaimAsButton() {
		ClaimAsToggle.toggle(teamSubId);
		refreshSchedule.afterAction();
		updateWidgets();
	}

	/**
	 * Opens the settings screen of OPAC for the team sub-config of the player. That screen shows the sub-config that is
	 * selected in the config of the player, and is read-only for everybody but the party owner and admins.
	 */
	private void onSettingsButton() {
		if(teamSubId == null)
			return;
		IPlayerConfigClientStorageManager<?> manager = getConfigManager();
		manager.getMyPlayerConfig().setSelectedSubConfig(teamSubId);
		manager.openMyPlayerConfigScreen(escape, this);
	}

	private void onRadiusButton() {
		convertRadius = TeamHubLogic.nextRadius(convertRadius, TeamHubLogic.DEFAULT_MAX_CONVERT_RADIUS);
		updateWidgets();
	}

	private void onConvertButton(boolean toTeam) {
		String command = TeamHubLogic.convertCommand(toTeam, convertRadius);
		if(command == null)
			return;
		int side = 2 * convertRadius + 1;
		openConfirmation(
				Component.translatable(toTeam ? "gui.xaero_pac_hub_team_convert_confirm_title_team" : "gui.xaero_pac_hub_team_convert_confirm_title_personal"),
				Component.translatable(toTeam ? "gui.xaero_pac_hub_team_convert_confirm_message_team" : "gui.xaero_pac_hub_team_convert_confirm_message_personal", convertRadius, side),
				() -> sendCommand(command));
	}

	private void openConfirmation(Component title, Component message, Runnable onConfirm) {
		minecraft.setScreen(new ConfirmScreen(accepted -> {
			minecraft.setScreen(this);
			if(accepted)
				onConfirm.run();
		}, title, message, CONVERT_CONFIRM, CommonComponents.GUI_CANCEL));
	}

	// ---- widget state ----

	private void updateWidgets() {
		TeamInfoData info = getInfo();
		if(state != State.READY || info == null || territoryButton == null)
			return;
		for(TeamAction action : TeamAction.values()) {
			Button button = roleButtons.get(action);
			button.setMessage(Component.translatable("gui.xaero_pac_hub_team_role_" + getShownRole(info, action).id()));
			button.active = info.canEdit();
			button.setTooltip(info.canEdit() ? ROLE_TOOLTIPS.get(action) : ROLE_READ_ONLY_TOOLTIPS.get(action));
		}
		territoryButton.setMessage(isTerritoryMessagesShown(info) ? TERRITORY_ON : TERRITORY_OFF);
		ClaimAsToggle.update(claimAsButton, teamSubId);
		settingsButton.active = teamSubId != null;
		settingsButton.setTooltip(teamSubId != null ? SETTINGS_TOOLTIP : SETTINGS_NO_SUB_TOOLTIP);
		radiusButton.setMessage(Component.translatable("gui.xaero_pac_hub_team_radius", convertRadius));
	}

	// ---- screen ----

	@Override
	public void tick() {
		super.tick();
		if(minecraft == null || minecraft.player == null)
			return;
		updateTeamSubId();
		State currentState = computeState();
		TeamInfoData info = getInfo();
		boolean layoutChanged = currentState != state
				|| currentState == State.READY && info != null && computeHeaderLines(info, getContentWidth()).size() != headerLines.size();
		int revision = TeamClaimsClientData.INSTANCE.getRevision();
		if(revision != seenRevision) {
			seenRevision = revision;
			pendingRoles.clear();
			pendingTerritoryMessages = null;
		}
		if(layoutChanged) {
			rebuildWidgets();
			return;
		}
		if(refreshSchedule.tick())
			requestTeamInfo();
		updateWidgets();
	}

	// ---- rendering ----

	@Override
	protected void renderPreDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
		super.renderPreDropdown(guiGraphics, mouseX, mouseY, partial);
		TeamInfoData info = getInfo();
		switch(state) {
			case LOADING -> renderMessage(guiGraphics, LOADING, TEXT_COLOR);
			case NO_TEAM -> renderMessage(guiGraphics, getNoTeamText(), VALUE_COLOR);
			case READY -> {
				if(info != null)
					renderReady(guiGraphics, info);
			}
		}
	}

	private Component getNoTeamText() {
		PartyBudgetData budget = getPartyStorage().getBudget();
		if(budget != null && budget.available() && budget.minMembers() > 0)
			return Component.translatable("gui.xaero_pac_hub_team_no_team", budget.minMembers());
		return Component.translatable("gui.xaero_pac_hub_team_no_team_unknown");
	}

	private void renderMessage(GuiGraphics guiGraphics, Component message, int color) {
		int y = HubTabBar.CONTENT_TOP + 20;
		for(FormattedCharSequence line : font.split(message, Math.min(width - 2 * MARGIN, MAX_TEXT_WIDTH))) {
			guiGraphics.drawCenteredString(font, line, width / 2, y, color);
			y += LINE_STEP;
		}
	}

	private void renderReady(GuiGraphics guiGraphics, TeamInfoData info) {
		int y = headerTop;
		for(HeaderLine line : headerLines) {
			guiGraphics.drawString(font, line.text(), contentLeft, y, line.color());
			y += LINE_STEP;
		}
		guiGraphics.drawString(font, font.plainSubstrByWidth(ROLES_HEADING.getString(), columnWidth), leftColumnLeft, rolesHeadingTop, VALUE_COLOR);
		for(TeamAction action : TeamAction.values()) {
			Integer rowTop = roleRowTops.get(action);
			if(rowTop != null)
				guiGraphics.drawString(font, getActionLabel(action), leftColumnLeft, rowTop + (buttonHeight - font.lineHeight) / 2, TEXT_COLOR);
		}
		renderAppearance(guiGraphics, info);
		guiGraphics.drawString(font, font.plainSubstrByWidth(CONVERT_HEADING.getString(), columnWidth), rightColumnLeft, convertHeadingTop, VALUE_COLOR);
	}

	/**
	 * Shows the claim colour and the claims name of the team sub-config, which the full settings screen edits.
	 */
	private void renderAppearance(GuiGraphics guiGraphics, TeamInfoData info) {
		int rgb = info.claimsColor() & 0xFFFFFF;
		int x = leftColumnLeft;
		int y = appearanceTop;
		guiGraphics.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, 0xFF000000);
		guiGraphics.fill(x + 1, y + 1, x + SWATCH_SIZE - 1, y + SWATCH_SIZE - 1, 0xFF000000 | rgb);
		Component colour = Component.translatable("gui.xaero_pac_hub_team_colour", String.format("#%06X", rgb));
		guiGraphics.drawString(font, font.plainSubstrByWidth(colour.getString(), columnWidth - SWATCH_SIZE - 4), x + SWATCH_SIZE + 4, y, VALUE_COLOR);
		Component claimsName = Component.translatable("gui.xaero_pac_hub_team_claims_name", info.claimsName().isEmpty() ? NONE : Component.literal(info.claimsName()));
		guiGraphics.drawString(font, font.plainSubstrByWidth(claimsName.getString(), columnWidth), x, y + LINE_STEP, VALUE_COLOR);
	}

}
