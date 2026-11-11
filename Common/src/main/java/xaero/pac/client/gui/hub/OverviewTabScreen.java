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

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.claims.IClientClaimsManager;
import xaero.pac.client.claims.player.IClientPlayerClaimInfo;
import xaero.pac.client.command.util.CommandUtil;
import xaero.pac.client.gui.MainMenu;
import xaero.pac.client.parties.party.IClientPartyStorage;
import xaero.pac.client.world.capability.ClientWorldMainCapability;
import xaero.pac.client.world.capability.api.ClientWorldCapabilityTypes;
import xaero.pac.common.claims.api.SpecialClaimOwners;
import xaero.pac.common.claims.player.IPlayerChunkClaim;
import xaero.pac.common.claims.player.IPlayerDimensionClaims;
import xaero.pac.common.server.claims.command.ClaimsCommandRegister;
import xaero.pac.common.server.parties.command.PartyCommandRegister;

import java.util.UUID;

/**
 * The Overview page of the hub, the page the hub opens with: the party of the player, the budgets and the choice between
 * private and team claims, and the chunk the player stands in with the buttons of OPAC's old main menu for it (claim,
 * forceload, anchor, about the party). The buttons send the same commands as the main menu and close the hub, as it did.
 */
public class OverviewTabScreen extends HubTabScreen {

	private static final Component TITLE = HubTab.OVERVIEW.getLabel();
	private static final Component NO_PARTY = Component.translatable("gui.xaero_pac_hub_overview_no_party");
	private static final Component OPEN_PARTY = Component.translatable("gui.xaero_pac_hub_overview_open_party");
	private static final Component CHUNK_HEADER = Component.translatable("gui.xaero_pac_hub_overview_chunk");
	private static final Component ABOUT_PARTY = Component.translatable("gui.xaero_pac_ui_about_party");
	private static final Component ABOUT_PARTY_COMMAND = Component.literal("/" + PartyCommandRegister.COMMAND_PREFIX + " about");
	private static final Component CLAIM_COMMAND = Component.literal("/" + ClaimsCommandRegister.COMMAND_PREFIX + " claim");
	private static final Component UNCLAIM_COMMAND = Component.literal("/" + ClaimsCommandRegister.COMMAND_PREFIX + " unclaim");
	private static final Component FORCELOAD_COMMAND = Component.literal("/" + ClaimsCommandRegister.COMMAND_PREFIX + " forceload");
	private static final Component UNFORCELOAD_COMMAND = Component.literal("/" + ClaimsCommandRegister.COMMAND_PREFIX + " unforceload");
	private static final Component ANCHOR_COMMAND = Component.literal("/" + ClaimsCommandRegister.COMMAND_PREFIX + " anchor");
	private static final Component UNANCHOR_COMMAND = Component.literal("/" + ClaimsCommandRegister.COMMAND_PREFIX + " unanchor");
	private static final Component UNANCHOR_CONFIRM_1 = Component.translatable("gui.xaero_pac_ui_unanchor_confirm_line1");
	private static final Component UNANCHOR_CONFIRM_2 = Component.translatable("gui.xaero_pac_ui_unanchor_confirm_line2");

	private static final int COLUMN_GAP = 12;
	private static final int BACK_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 2;
	private static final int LINE_STEP = 10;
	private static final int SECTION_GAP = 8;
	/**
	 * The distance from the top of a section to its first line or button.
	 */
	private static final int SECTION_BODY = HubSidebar.HEADER_HEIGHT + 2;
	private static final int REFRESH_INTERVAL_TICKS = 40;
	private static final int REFRESH_AFTER_ACTION_TICKS = 25;
	private static final int TEXT_COLOR = HubBudgetLines.TEXT_COLOR;
	private static final int HEADER_COLOR = 0xFFFFFFFF;
	private static final int VALUE_COLOR = 0xFFAAAAAA;

	private boolean serverHasMod;
	private boolean serverHasClaimsEnabled;
	private boolean serverHasPartiesEnabled;
	private String teamSubId;
	private int leftColumn;
	private int rightColumn;
	private int columnWidth;
	private Button partyPageButton;
	private Button claimAsButton;
	private Button claimButton;
	private Button forceloadButton;
	private Button anchorButton;
	private Button aboutPartyButton;
	private final RefreshSchedule refreshSchedule = new RefreshSchedule(REFRESH_INTERVAL_TICKS, REFRESH_AFTER_ACTION_TICKS);

	public OverviewTabScreen(Screen escape, Screen parent) {
		super(escape, parent, TITLE);
	}

	@Override
	protected HubTab getTab() {
		return HubTab.OVERVIEW;
	}

	@Override
	protected void init() {
		super.init();
		columnWidth = Math.max(60, (contentWidth() - COLUMN_GAP) / 2);
		leftColumn = contentLeft();
		rightColumn = contentLeft() + contentWidth() - columnWidth;

		// left column: party, then claims
		int y = contentTop() + SECTION_BODY + 2 * LINE_STEP + 2;
		partyPageButton = addRenderableWidget(Button.builder(OPEN_PARTY, b -> HubTabs.open(minecraft, HubTab.PARTY, escape, parent)).bounds(leftColumn, y, columnWidth, BUTTON_HEIGHT).build());
		y += BUTTON_HEIGHT + SECTION_GAP + SECTION_BODY;
		claimAsButton = addRenderableWidget(Button.builder(Component.empty(), b -> onClaimAsButton()).bounds(leftColumn, y, columnWidth, BUTTON_HEIGHT).build());

		// right column: the chunk
		y = contentTop() + SECTION_BODY + 3 * LINE_STEP + 2;
		claimButton = addRenderableWidget(Button.builder(MainMenu.CLAIM, this::onClaimButton).bounds(rightColumn, y, columnWidth, BUTTON_HEIGHT).build());
		y += BUTTON_HEIGHT + BUTTON_GAP;
		forceloadButton = addRenderableWidget(Button.builder(MainMenu.FORCELOAD, this::onForceloadButton).bounds(rightColumn, y, columnWidth, BUTTON_HEIGHT).build());
		y += BUTTON_HEIGHT + BUTTON_GAP;
		if(OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClaimsManager().usingAnchorBasedClaiming()) {
			anchorButton = addRenderableWidget(Button.builder(MainMenu.ANCHOR, this::onAnchorButton).bounds(rightColumn, y, columnWidth, BUTTON_HEIGHT).build());
			y += BUTTON_HEIGHT + BUTTON_GAP;
		}
		aboutPartyButton = addRenderableWidget(Button.builder(ABOUT_PARTY, this::onAboutPartyButton).tooltip(Tooltip.create(ABOUT_PARTY_COMMAND)).bounds(rightColumn, y, columnWidth, BUTTON_HEIGHT).build());

		int backWidth = Math.min(BACK_WIDTH, contentWidth());
		addRenderableWidget(Button.builder(BACK, b -> goBack()).bounds(contentCenterX() - backWidth / 2, contentBottom() - BUTTON_HEIGHT, backWidth, BUTTON_HEIGHT).build());

		if(minecraft.player != null)
			requestTeamInfo();
		updateWidgets();
	}

	// ---- data ----

	private IClientClaimsManager<?, ?, ?> getClaimsManager() {
		return OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClaimsManager();
	}

	private IClientPartyStorage<?, ?, ?> getPartyStorage() {
		return OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClientPartyStorage();
	}

	private boolean wouldClaim(IPlayerChunkClaim currentClaim) {
		if(currentClaim == null)
			return true;
		return !currentClaim.isSameClaimType(getClaimsManager().getPotentialClaimStateReflection());
	}

	private boolean wouldAnchor(ChunkPos pos, IPlayerChunkClaim currentClaim) {
		if(currentClaim == null)
			return true;
		IClientClaimsManager<?, ?, ?> claimsManager = getClaimsManager();
		IPlayerChunkClaim potentialClaimReflection = claimsManager.getPotentialClaimStateReflection();
		if(potentialClaimReflection == null)
			return true;
		if(!currentClaim.getPlayerId().equals(potentialClaimReflection.getPlayerId()))
			return true;
		IPlayerDimensionClaims<?> playerDimensionClaims = claimsManager.getPlayerInfo(currentClaim.getPlayerId())
				.getDimension(minecraft.player.level().dimension().location());
		if(playerDimensionClaims == null)
			return true;
		return !playerDimensionClaims.getAnchors().contains(pos);
	}

	// ---- actions ----

	private void onClaimAsButton() {
		ClaimAsToggle.toggle(teamSubId);
		refreshSchedule.afterAction();
		updateWidgets();
	}

	private void onClaimButton(Button b) {
		IPlayerChunkClaim currentClaim = getClaimsManager().get(minecraft.level.dimension().location(), minecraft.player.chunkPosition().x, minecraft.player.chunkPosition().z);
		if(wouldClaim(currentClaim))
			CommandUtil.sendCommand(minecraft, CLAIM_COMMAND.getString().substring(1));
		else
			CommandUtil.sendCommand(minecraft, UNCLAIM_COMMAND.getString().substring(1));
		onClose();
	}

	private void onForceloadButton(Button b) {
		IPlayerChunkClaim currentClaim = getClaimsManager().get(minecraft.level.dimension().location(), minecraft.player.chunkPosition().x, minecraft.player.chunkPosition().z);
		if(currentClaim == null)
			return;
		if(!currentClaim.isForceloadable())
			CommandUtil.sendCommand(minecraft, FORCELOAD_COMMAND.getString().substring(1));
		else
			CommandUtil.sendCommand(minecraft, UNFORCELOAD_COMMAND.getString().substring(1));
		onClose();
	}

	private void onAnchorButton(Button b) {
		ChunkPos chunkPos = minecraft.player.chunkPosition();
		IPlayerChunkClaim currentClaim = getClaimsManager().get(minecraft.level.dimension().location(), chunkPos.x, chunkPos.z);
		if(wouldAnchor(chunkPos, currentClaim)) {
			CommandUtil.sendCommand(minecraft, ANCHOR_COMMAND.getString().substring(1));
			onClose();
			return;
		}
		minecraft.setScreen(new ConfirmScreen(result -> {
			if(result)
				CommandUtil.sendCommand(minecraft, UNANCHOR_COMMAND.getString().substring(1) + " confirm");
			minecraft.setScreen(this);
		}, UNANCHOR_CONFIRM_1, UNANCHOR_CONFIRM_2));
	}

	private void onAboutPartyButton(Button b) {
		CommandUtil.sendCommand(minecraft, ABOUT_PARTY_COMMAND.getString().substring(1));
		minecraft.setScreen(null);
	}

	/**
	 * Asks for the team information, which the server answers together with the budgets.
	 */
	private void requestTeamInfo() {
		if(TeamInfoRefresh.request())
			refreshSchedule.onRequestSent();
	}

	// ---- widget state ----

	private void updateWidgets() {
		if(minecraft.level == null || minecraft.player == null)
			return;
		ClientWorldMainCapability mainCap = (ClientWorldMainCapability) OpenPartiesAndClaims.INSTANCE.getCapabilityHelper().getCapability(minecraft.level, ClientWorldCapabilityTypes.MAIN_CAP);
		serverHasMod = mainCap.getClientWorldData().serverHasMod();
		serverHasClaimsEnabled = mainCap.getClientWorldData().serverHasClaimsEnabled();
		serverHasPartiesEnabled = mainCap.getClientWorldData().serverHasPartiesEnabled();
		partyPageButton.visible = serverHasPartiesEnabled;
		aboutPartyButton.visible = serverHasPartiesEnabled;
		claimAsButton.visible = claimButton.visible = forceloadButton.visible = serverHasClaimsEnabled;
		if(anchorButton != null)
			anchorButton.visible = serverHasClaimsEnabled;
		partyPageButton.active = serverHasMod && serverHasPartiesEnabled;
		aboutPartyButton.active = serverHasMod && getPartyStorage().getParty() != null;

		teamSubId = ClaimsHubLogic.findTeamSubId(OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigStorageManager().getMyPlayerConfig().getSubConfigIds());
		ClaimAsToggle.update(claimAsButton, teamSubId);
		claimAsButton.active &= serverHasMod;

		claimButton.active = false;
		forceloadButton.active = false;
		if(anchorButton != null)
			anchorButton.active = false;
		IClientClaimsManager<?, ?, ?> claimsManager = getClaimsManager();
		if(!serverHasMod || claimsManager.isLoading())
			return;
		ChunkPos chunkPos = minecraft.player.chunkPosition();
		IPlayerChunkClaim currentClaim = claimsManager.get(minecraft.level.dimension().location(), chunkPos.x, chunkPos.z);
		UUID claimTargetUUID = claimsManager.getPotentialClaimStateReflection() == null ? null : claimsManager.getPotentialClaimStateReflection().getPlayerId();
		claimButton.active = true;
		boolean wouldClaim = wouldClaim(currentClaim);
		claimButton.setMessage(wouldClaim ? MainMenu.CLAIM : MainMenu.UNCLAIM);
		claimButton.setTooltip(Tooltip.create(wouldClaim ? CLAIM_COMMAND : UNCLAIM_COMMAND));

		forceloadButton.active = currentClaim != null && (claimsManager.isAdminMode() || currentClaim.getPlayerId().equals(claimTargetUUID));
		boolean wouldForceload = currentClaim == null || !currentClaim.isForceloadable();
		forceloadButton.setMessage(wouldForceload ? MainMenu.FORCELOAD : MainMenu.UNFORCELOAD);
		forceloadButton.setTooltip(Tooltip.create(wouldForceload ? FORCELOAD_COMMAND : UNFORCELOAD_COMMAND));

		if(anchorButton != null) {
			anchorButton.active = !claimsManager.getClaimingMode().isGlobal();
			boolean wouldAnchor = wouldAnchor(chunkPos, currentClaim);
			anchorButton.setMessage(wouldAnchor ? MainMenu.ANCHOR : MainMenu.UNANCHOR);
			anchorButton.setTooltip(Tooltip.create(wouldAnchor ? ANCHOR_COMMAND : UNANCHOR_COMMAND));
		}
	}

	// ---- screen ----

	@Override
	public void tick() {
		super.tick();
		if(refreshSchedule.tick())
			requestTeamInfo();
		updateWidgets();
	}

	@Override
	protected void renderPreDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
		super.renderPreDropdown(guiGraphics, mouseX, mouseY, partial);
		int top = contentTop();
		if(!serverHasMod) {
			guiGraphics.drawCenteredString(font, MainMenu.NO_HANDSHAKE, contentCenterX(), top + 20, 0xFFFF5555);
			return;
		}
		renderParty(guiGraphics, top);
		renderClaims(guiGraphics, top + SECTION_BODY + 2 * LINE_STEP + 2 + BUTTON_HEIGHT + SECTION_GAP);
		renderChunk(guiGraphics, top);
	}

	private void renderHeader(GuiGraphics guiGraphics, Component label, int left, int y) {
		HubSidebar.renderHeader(guiGraphics, font, label, left, y, left + columnWidth, HEADER_COLOR);
	}

	private void renderLine(GuiGraphics guiGraphics, Component line, int y, int color) {
		guiGraphics.drawString(font, font.plainSubstrByWidth(line.getString(), columnWidth), leftColumn, y, color);
	}

	private void renderParty(GuiGraphics guiGraphics, int top) {
		renderHeader(guiGraphics, HubTab.PARTY.getLabel(), leftColumn, top);
		if(!serverHasPartiesEnabled) {
			guiGraphics.drawString(font, MainMenu.NO_PARTIES, leftColumn, top + SECTION_BODY, VALUE_COLOR);
			return;
		}
		IClientPartyStorage<?, ?, ?> partyStorage = getPartyStorage();
		int y = top + SECTION_BODY;
		if(partyStorage.getParty() == null) {
			renderLine(guiGraphics, partyStorage.isLoading() ? MainMenu.PARTY_SYNCING : NO_PARTY, y, TEXT_COLOR);
			return;
		}
		renderLine(guiGraphics, Component.translatable("gui.xaero_pac_ui_party_name", partyStorage.getPartyName()), y, TEXT_COLOR);
		renderLine(guiGraphics, Component.translatable("gui.xaero_pac_ui_party_member_count", partyStorage.getUIMemberCount() + " / " + partyStorage.getMemberLimit()), y + LINE_STEP, TEXT_COLOR);
	}

	private void renderClaims(GuiGraphics guiGraphics, int top) {
		renderHeader(guiGraphics, HubTab.CLAIMS.getLabel(), leftColumn, top);
		if(!serverHasClaimsEnabled) {
			guiGraphics.drawString(font, MainMenu.NO_CLAIMS, leftColumn, top + SECTION_BODY, VALUE_COLOR);
			return;
		}
		int y = top + SECTION_BODY + BUTTON_HEIGHT + SECTION_GAP / 2;
		if(getClaimsManager().isLoading()) {
			guiGraphics.drawString(font, MainMenu.CLAIMS_SYNCING, leftColumn, y, -1);
			return;
		}
		for(HubBudgetLines.Line line : HubBudgetLines.allLines()) {
			for(FormattedCharSequence part : font.split(line.text(), columnWidth)) {
				if(y + font.lineHeight > contentBottom() - BUTTON_HEIGHT - BUTTON_GAP)
					return;
				guiGraphics.drawString(font, part, leftColumn, y, line.color());
				y += LINE_STEP;
			}
		}
	}

	private void renderChunk(GuiGraphics guiGraphics, int top) {
		HubSidebar.renderHeader(guiGraphics, font, CHUNK_HEADER, rightColumn, top, rightColumn + columnWidth, HEADER_COLOR);
		if(!serverHasClaimsEnabled)
			return;
		IClientClaimsManager<?, ?, ?> claimsManager = getClaimsManager();
		if(claimsManager.isLoading() || minecraft.player == null)
			return;
		ChunkPos chunkPos = minecraft.player.chunkPosition();
		int y = top + SECTION_BODY;
		drawChunkLine(guiGraphics, Component.translatable("gui.xaero_pac_hub_tt_chunk", chunkPos.x, chunkPos.z), y, TEXT_COLOR);
		y += LINE_STEP;
		IPlayerChunkClaim claim = claimsManager.get(minecraft.level.dimension().location(), chunkPos.x, chunkPos.z);
		if(claim == null) {
			drawChunkLine(guiGraphics, Component.translatable("gui.xaero_pac_hub_tt_unclaimed"), y, VALUE_COLOR);
			return;
		}
		UUID owner = claim.getPlayerId();
		drawChunkLine(guiGraphics, Component.translatable("gui.xaero_pac_hub_tt_owner", getOwnerName(claimsManager, owner)), y, TEXT_COLOR);
		y += LINE_STEP;
		if(claimsManager.hasPlayerInfo(owner)) {
			IClientPlayerClaimInfo<?> info = claimsManager.getPlayerInfo(owner);
			String claimsName = info.getClaimsName(claim.getSubConfigIndex());
			if(claimsName == null && claim.getSubConfigIndex() != -1)
				claimsName = info.getClaimsName();
			if(claimsName != null && !claimsName.isEmpty())
				drawChunkLine(guiGraphics, Component.translatable("gui.xaero_pac_hub_tt_name", claimsName), y, TEXT_COLOR);
		}
	}

	private void drawChunkLine(GuiGraphics guiGraphics, Component line, int y, int color) {
		guiGraphics.drawString(font, font.plainSubstrByWidth(line.getString(), columnWidth), rightColumn, y, color);
	}

	private String getOwnerName(IClientClaimsManager<?, ?, ?> claimsManager, UUID owner) {
		if(SpecialClaimOwners.SERVER.equals(owner))
			return Component.translatable("gui.xaero_pac_hub_tt_server_owner").getString();
		if(SpecialClaimOwners.EXPIRED.equals(owner))
			return Component.translatable("gui.xaero_pac_hub_tt_expired_owner").getString();
		if(!claimsManager.hasPlayerInfo(owner))
			return owner.toString();
		IClientPlayerClaimInfo<?> info = claimsManager.getPlayerInfo(owner);
		Component partyName = info.isPartyOwned() ? info.getPartyName() : null;
		return partyName != null ? partyName.getString() : info.getPlayerUsername();
	}

}
