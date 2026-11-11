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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.glfw.GLFW;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.claims.IClientClaimsManager;
import xaero.pac.client.claims.api.IClientClaimsManagerAPI;
import xaero.pac.client.claims.player.api.IClientPlayerClaimInfoAPI;
import xaero.pac.client.claims.player.mode.ClaimingModeClientHandlers;
import xaero.pac.client.claims.player.mode.ClientClaimingModeHandler;
import xaero.pac.client.command.util.CommandUtil;
import xaero.pac.client.gui.MainMenu;
import xaero.pac.client.gui.hub.map.ChunkMapImage;
import xaero.pac.client.gui.hub.map.ClaimOverlay;
import xaero.pac.client.gui.hub.map.TerrainCache;
import xaero.pac.client.gui.widget.dropdown.DropDownWidget;
import xaero.pac.client.parties.party.IClientPartyStorage;
import xaero.pac.client.parties.party.api.IClientPartyAllyInfoAPI;
import xaero.pac.client.teamclaims.TeamClaimsClientData;
import xaero.pac.client.player.config.IPlayerConfigClientStorage;
import xaero.pac.client.player.config.IPlayerConfigClientStorageManager;
import xaero.pac.client.world.capability.ClientWorldMainCapability;
import xaero.pac.client.world.capability.api.ClientWorldCapabilityTypes;
import xaero.pac.common.claims.api.SpecialClaimOwners;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.player.mode.ClaimingMode;
import xaero.pac.common.claims.player.mode.api.ClaimingModes;
import xaero.pac.common.claims.player.mode.api.IClaimingModeAPI;
import xaero.pac.common.packet.teamclaims.TeamInfoData;
import xaero.pac.common.server.claims.command.ClaimingModeCommand;
import xaero.pac.common.server.claims.command.ClaimsCommandRegister;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The Claims tab of the hub: a map of the claimed chunks around the player (the terrain of the chunks the client has
 * loaded, drawn like a vanilla map, with the colours of the claims over it; the mouse wheel and two buttons zoom) where the
 * player selects chunks by clicking and dragging and then claims, unclaims, forceloads or unforceloads them with the
 * area requests of the client claims API, in the claiming mode they are in. Next to the map are the toggle between
 * private and team claims (the player config option {@code claims.usedSub}, which also decides what the World Map's
 * "Claim Selected" claims), the budgets, and a legend. Players who may claim in more than one claiming mode (as the
 * party, or ops as the server) get the dropdown of the claiming mode from OPAC's old main menu above them.
 * <p>
 * The answers to the requests are OPAC's normal claim result messages. The map reads the claims the client has synced,
 * so it follows by itself when the server sends a change.
 */
public class ClaimsTabScreen extends HubTabScreen {

	private static final Component TITLE = Component.translatable("gui.xaero_pac_hub_tab_claims");
	private static final Tooltip CLAIM_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_claim_tooltip"));
	private static final Tooltip UNCLAIM_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_unclaim_tooltip"));
	private static final Tooltip FORCELOAD_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_forceload_tooltip"));
	private static final Tooltip UNFORCELOAD_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_unforceload_tooltip"));
	private static final Tooltip SELECT_FIRST_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_select_first_tooltip"));
	private static final Tooltip CLEAR_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_clear_selection_tooltip"));
	private static final Tooltip CENTRE_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_centre_tooltip"));
	private static final Tooltip ZOOM_OUT_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_zoom_out_tooltip"));
	private static final Tooltip ZOOM_IN_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_zoom_in_tooltip"));
	private static final Component CLAIM = MainMenu.CLAIM;
	private static final Component UNCLAIM = MainMenu.UNCLAIM;
	private static final Component FORCELOAD = MainMenu.FORCELOAD;
	private static final Component UNFORCELOAD = MainMenu.UNFORCELOAD;
	private static final Component CLEAR_SELECTION = Component.translatable("gui.xaero_pac_hub_clear_selection");
	private static final Component CENTRE = Component.translatable("gui.xaero_pac_hub_centre");

	private static final int PANEL_GAP = 8;
	private static final int MIN_PANEL_WIDTH = 128;
	private static final int MAX_PANEL_WIDTH = 190;
	private static final int MIN_GRID_SIDE = 24;
	private static final int BUTTON_GAP = 2;
	private static final int TALL_SCREEN_HEIGHT = 260;
	private static final int LINE_STEP = 10;
	private static final int SECTION_GAP = 5;
	private static final int TOOLTIP_WIDTH = 200;
	private static final int REFRESH_INTERVAL_TICKS = 40;
	private static final int REFRESH_AFTER_ACTION_TICKS = 25;
	private static final int SWATCH_SIZE = 8;
	private static final int TWO_COLUMN_LEGEND_WIDTH = 150;
	private static final long CLAIMING_MODE_ANSWER_MILLIS = 1000;

	private static final int GRID_BORDER_COLOR = 0xFF000000;
	private static final int UNSYNCED_COLOR = 0x707070;
	private static final int PLAYER_MARKER_COLOR = 0xFFFF3030;
	private static final int SELECTION_FILL_COLOR = 0x66FFFFFF;
	private static final int SELECTION_OUTLINE_COLOR = 0xFFFFFFFF;
	private static final int HOVER_FILL_COLOR = 0x33FFFFFF;
	private static final int FORCELOAD_DOT_COLOR = 0xFFFFFFFF;
	private static final int OWN_BADGE_COLOR = 0xFFFFFFFF;
	private static final int TEAM_BADGE_COLOR = 0xFFFFD23C;
	private static final int PARTY_BADGE_COLOR = 0xFF55FF55;
	private static final int ALLY_BADGE_COLOR = 0xFFFF77FF;
	private static final int SERVER_BADGE_COLOR = 0xFF55FFFF;
	private static final int EXPIRED_BADGE_COLOR = 0xFF888888;
	private static final int TEXT_COLOR = HubBudgetLines.TEXT_COLOR;
	private static final int VALUE_COLOR = 0xFFAAAAAA;
	private static final int WARNING_COLOR = HubBudgetLines.WARNING_COLOR;
	private static final int HINT_COLOR = HubBudgetLines.HINT_COLOR;

	private enum State {
		NO_MOD, NO_CLAIMS, SYNCING, READY
	}

	private enum Action {
		CLAIM, UNCLAIM, FORCELOAD, UNFORCELOAD
	}

	/**
	 * What the grid shows for one chunk.
	 *
	 * @param rgb  the colour of the claim, without alpha
	 * @param category  the kind of claim, {@link ClaimCategory#NONE} if the chunk is not claimed
	 * @param forceloaded  whether the claim is marked for forceloading
	 * @param claim  the claim, null if the chunk is not claimed
	 * @param owner  tells the owners (and their sub-configs) apart for the lines between claims, null if the chunk is not claimed
	 */
	private record Cell(int rgb, ClaimCategory category, boolean forceloaded, @Nullable IPlayerChunkClaimAPI claim, @Nullable OwnerKey owner) {
		static final Cell EMPTY = new Cell(0, ClaimCategory.NONE, false, null, null);
	}

	private record OwnerKey(UUID owner, int subConfigIndex) {
	}

	/**
	 * One entry of the legend.
	 */
	private record LegendEntry(Component label, int badgeColor, boolean dot, boolean outline) {
	}

	private static final List<LegendEntry> LEGEND = List.of(
			new LegendEntry(Component.translatable("gui.xaero_pac_hub_legend_own"), OWN_BADGE_COLOR, false, false),
			new LegendEntry(Component.translatable("gui.xaero_pac_hub_legend_team"), TEAM_BADGE_COLOR, false, false),
			new LegendEntry(Component.translatable("gui.xaero_pac_hub_legend_party"), PARTY_BADGE_COLOR, false, false),
			new LegendEntry(Component.translatable("gui.xaero_pac_hub_legend_ally"), ALLY_BADGE_COLOR, false, false),
			new LegendEntry(Component.translatable("gui.xaero_pac_hub_legend_other"), 0, false, false),
			new LegendEntry(Component.translatable("gui.xaero_pac_hub_legend_server"), SERVER_BADGE_COLOR, false, false),
			new LegendEntry(Component.translatable("gui.xaero_pac_hub_legend_forceload"), FORCELOAD_DOT_COLOR, true, false),
			new LegendEntry(Component.translatable("gui.xaero_pac_hub_legend_you"), PLAYER_MARKER_COLOR, false, true)
	);

	/**
	 * The zoom step of the grid, kept while the game runs.
	 */
	private static int zoom = HubGridFit.DEFAULT_ZOOM;

	private State state = State.NO_MOD;
	private int centerX;
	private int centerZ;
	private boolean centreChosen;
	@Nullable
	private ChunkSelection selection;
	private boolean dragging;

	private HubGridFit.Fit fit = new HubGridFit.Fit(HubGridFit.MIN_RADIUS, HubGridFit.MIN_CELL);
	private int gridLeft;
	private int gridTop;
	private int panelLeft;
	private int panelWidth;
	private int buttonHeight;
	private int textTop;
	private int backTop;
	private Cell[] cells = new Cell[0];
	private final TerrainCache terrain = new TerrainCache();
	@Nullable
	private ChunkMapImage mapImage;
	private final List<String> allyNames = new ArrayList<>();
	private final List<String> allyDefaultNames = new ArrayList<>();

	private Button claimAsButton;
	private Button claimButton;
	private Button unclaimButton;
	private Button forceloadButton;
	private Button unforceloadButton;
	private Button clearButton;
	private Button centreButton;
	private Button zoomOutButton;
	private Button zoomInButton;
	@Nullable
	private DropDownWidget claimingModeMenu;
	private List<ClaimingMode> claimModeOptions = List.of();
	@Nullable
	private IClaimingModeAPI selectedClaimingMode;
	private IClaimingModeAPI selectedEffectiveClaimingMode;
	private long lastClaimingModeChangeTime;

	@Nullable
	private String teamSubId;
	private boolean hasOtherSubConfigs;
	@Nullable
	private Integer teamSubIndex;
	private final RefreshSchedule refreshSchedule = new RefreshSchedule(REFRESH_INTERVAL_TICKS, REFRESH_AFTER_ACTION_TICKS);
	@Nullable
	private Tooltip tooLargeTooltip;
	private int tooLargeTooltipLimit;

	public ClaimsTabScreen(Screen escape, Screen parent) {
		super(escape, parent, TITLE);
	}

	@Override
	protected HubTab getTab() {
		return HubTab.CLAIMS;
	}

	// ---- data ----

	private IClientClaimsManagerAPI getClaimsManager() {
		return OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClaimsManager();
	}

	private IClientPartyStorage<?, ?, ?> getPartyStorage() {
		return OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClientPartyStorage();
	}

	private IPlayerConfigClientStorage<?> getMyConfig() {
		return OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigStorageManager().getMyPlayerConfig();
	}

	private ResourceLocation getDimension() {
		return minecraft.level.dimension().location();
	}

	private State computeState() {
		if(minecraft.level == null || minecraft.player == null)
			return State.NO_MOD;
		ClientWorldMainCapability mainCap = (ClientWorldMainCapability) OpenPartiesAndClaims.INSTANCE.getCapabilityHelper().getCapability(minecraft.level, ClientWorldCapabilityTypes.MAIN_CAP);
		if(!mainCap.getClientWorldData().serverHasMod())
			return State.NO_MOD;
		if(!mainCap.getClientWorldData().serverHasClaimsEnabled())
			return State.NO_CLAIMS;
		if(getClaimsManager().isLoading())
			return State.SYNCING;
		return State.READY;
	}

	/**
	 * Reads the sub-configs of the player and works out the index that the team claims of the player carry, from the team
	 * information of the server.
	 */
	private void updateSubConfigKnowledge() {
		List<String> subConfigIds = getMyConfig().getSubConfigIds();
		teamSubId = ClaimsHubLogic.findTeamSubId(subConfigIds);
		hasOtherSubConfigs = ClaimsHubLogic.hasOtherSubConfigs(subConfigIds);
		if(state != State.READY) {
			teamSubIndex = null;
			return;//the sub-config in use is only known once the claims are synced
		}
		IClientClaimsManagerAPI claimsManager = getClaimsManager();
		teamSubIndex = ClaimsHubLogic.resolveTeamSubIndex(TeamClaimsClientData.INSTANCE.getTeamInfo(), teamSubId,
				claimsManager.getCurrentSubConfigId(ClaimingModes.PLAYER), claimsManager.getCurrentSubConfigIndex(ClaimingModes.PLAYER));
	}

	/**
	 * Gets the most chunks one claim action may cover on this server, null until the server has said.
	 */
	@Nullable
	private Integer getMaxActionSize() {
		TeamInfoData info = TeamClaimsClientData.INSTANCE.getTeamInfo();
		return info == null ? null : info.maxClaimActionSize();
	}

	private Cell computeCell(IClientClaimsManagerAPI claimsManager, ResourceLocation dimension, UUID me, int chunkX, int chunkZ) {
		IPlayerChunkClaimAPI claim = claimsManager.get(dimension, chunkX, chunkZ);
		if(claim == null)
			return Cell.EMPTY;
		UUID owner = claim.getPlayerId();
		boolean serverOwner = SpecialClaimOwners.SERVER.equals(owner);
		boolean expiredOwner = SpecialClaimOwners.EXPIRED.equals(owner);
		boolean self = owner.equals(me);
		boolean partyOwned = !serverOwner && !expiredOwner && claimsManager.hasPlayerInfo(owner) && claimsManager.getPlayerInfo(owner).isPartyOwned();
		boolean partyMember = !self && !serverOwner && !expiredOwner && getPartyStorage().getParty() != null
				&& getPartyStorage().getParty().getMemberInfo(owner) != null;
		boolean team = self && ClaimsHubLogic.isOwnTeamClaim(claim.getSubConfigIndex(), teamSubIndex, teamSubId != null, hasOtherSubConfigs);
		boolean ally = false;
		if(!self && !partyMember && !serverOwner && !expiredOwner && (!allyNames.isEmpty() || !allyDefaultNames.isEmpty()) && claimsManager.hasPlayerInfo(owner)) {
			IClientPlayerClaimInfoAPI info = claimsManager.getPlayerInfo(owner);
			Component partyName = partyOwned ? info.getPartyName() : null;
			ally = ClaimCategory.isAllyOwner(allyNames, allyDefaultNames, partyName == null ? null : partyName.getString(), info.getPlayerUsername());
		}
		ClaimCategory category = ClaimCategory.classify(serverOwner, expiredOwner, self, partyMember, partyOwned, team, ally);
		int rgb = claimsManager.getColor(claim, dimension) & 0xFFFFFF;
		if(rgb == 0)
			rgb = UNSYNCED_COLOR;
		return new Cell(rgb, category, claim.isForceloadable(), claim, new OwnerKey(owner, claim.getSubConfigIndex()));
	}

	/**
	 * Reads the names of the allied parties of the player's party.
	 */
	private void readAllies() {
		allyNames.clear();
		allyDefaultNames.clear();
		IClientPartyStorage<?, ?, ?> partyStorage = getPartyStorage();
		if(partyStorage.getParty() == null)
			return;
		partyStorage.getParty().getAllyPartiesStream().forEach(ally -> {
			Object info = partyStorage.getAllyInfoStorage().get(ally.getPartyId());
			if(info instanceof IClientPartyAllyInfoAPI allyInfo) {
				allyNames.add(allyInfo.getAllyName());
				allyDefaultNames.add(allyInfo.getAllyDefaultName());
			}
		});
	}

	private void rebuildCells() {
		int count = fit.cells();
		cells = new Cell[count * count];
		if(state != State.READY) {
			java.util.Arrays.fill(cells, Cell.EMPTY);
			return;
		}
		readAllies();
		IClientClaimsManagerAPI claimsManager = getClaimsManager();
		ResourceLocation dimension = getDimension();
		UUID me = minecraft.player.getUUID();
		for(int gz = 0; gz < count; gz++)
			for(int gx = 0; gx < count; gx++)
				cells[gz * count + gx] = computeCell(claimsManager, dimension, me, chunkXAt(gx), chunkZAt(gz));
		paintMap();
	}

	/**
	 * Paints the chunks of the map picture whose terrain or claim changed since they were painted.
	 */
	private void paintMap() {
		if(mapImage == null)
			return;
		int count = fit.cells();
		for(int gz = 0; gz < count; gz++) {
			for(int gx = 0; gx < count; gx++) {
				Cell data = cells[gz * count + gx];
				TerrainCache.Entry picture = terrain.get(chunkXAt(gx), chunkZAt(gz));
				boolean claimed = data.category() != ClaimCategory.NONE;
				long signature = (picture == null ? 0L : picture.version()) << 25 | (claimed ? 1L << 24 | data.rgb() : 0L);
				mapImage.paint(gx, gz, signature, picture == null ? null : picture.argb(), claimed ? data.rgb() : -1);
			}
		}
	}

	private int chunkXAt(int gridX) {
		return centerX - fit.radius() + gridX;
	}

	private int chunkZAt(int gridZ) {
		return centerZ - fit.radius() + gridZ;
	}

	private void centreOnPlayer() {
		ChunkPos playerChunk = minecraft.player.chunkPosition();
		centerX = playerChunk.x;
		centerZ = playerChunk.z;
	}

	// ---- layout ----

	@Override
	protected void init() {
		super.init();
		state = computeState();
		if(!centreChosen && minecraft.player != null) {
			centreOnPlayer();
			centreChosen = true;
		}
		claimAsButton = null;
		claimButton = null;
		unclaimButton = null;
		forceloadButton = null;
		unforceloadButton = null;
		clearButton = null;
		centreButton = null;
		zoomOutButton = null;
		zoomInButton = null;
		claimingModeMenu = null;
		buttonHeight = height >= TALL_SCREEN_HEIGHT ? 20 : 16;
		backTop = contentBottom() - buttonHeight;
		if(state == State.READY)
			initReady();
		else
			initMessageOnly();
		int backWidth = Math.min(200, contentWidth());
		addRenderableWidget(Button.builder(BACK, b -> goBack()).bounds(state == State.READY ? panelLeft : contentCenterX() - backWidth / 2, backTop, state == State.READY ? panelWidth : backWidth, buttonHeight).build());
		if(minecraft.player != null && state != State.NO_MOD) {
			updateSubConfigKnowledge();
			requestTeamInfo();
		}
		rebuildCells();
		updateWidgets();
	}

	private void initMessageOnly() {
		fit = new HubGridFit.Fit(HubGridFit.MIN_RADIUS, HubGridFit.MIN_CELL);
	}

	private void initReady() {
		int top = contentTop();
		int totalWidth = contentWidth();
		int availableHeight = contentBottom() - top;
		int side = Math.max(MIN_GRID_SIDE, Math.min(availableHeight, totalWidth - PANEL_GAP - MIN_PANEL_WIDTH));
		fit = HubGridFit.fit(side, zoom);
		if(mapImage == null)
			mapImage = new ChunkMapImage(minecraft.getTextureManager());
		mapImage.resize(fit.cells());
		int gridSize = fit.size();
		panelWidth = Math.max(60, Math.min(MAX_PANEL_WIDTH, totalWidth - gridSize - PANEL_GAP));
		int usedWidth = gridSize + PANEL_GAP + panelWidth;
		gridLeft = contentLeft() + Math.max(0, (totalWidth - usedWidth) / 2);
		gridTop = top;
		panelLeft = gridLeft + gridSize + PANEL_GAP;

		int y = top;
		if(ClaimsHubLogic.isClaimingModeChoiceShown(getUsableClaimingModes().size())) {
			claimingModeMenu = addRenderableWidget(createClaimingModeDropdown(y));
			y += DropDownWidget.LINE_HEIGHT + BUTTON_GAP + 1;
		}
		claimAsButton = addRenderableWidget(Button.builder(Component.empty(), b -> onClaimAsButton()).bounds(panelLeft, y, panelWidth, buttonHeight).build());
		y += buttonHeight + BUTTON_GAP;
		int halfWidth = (panelWidth - BUTTON_GAP) / 2;
		int rightLeft = panelLeft + panelWidth - halfWidth;
		claimButton = addRenderableWidget(Button.builder(CLAIM, b -> onActionButton(Action.CLAIM)).bounds(panelLeft, y, halfWidth, buttonHeight).build());
		unclaimButton = addRenderableWidget(Button.builder(UNCLAIM, b -> onActionButton(Action.UNCLAIM)).bounds(rightLeft, y, halfWidth, buttonHeight).build());
		y += buttonHeight + BUTTON_GAP;
		forceloadButton = addRenderableWidget(Button.builder(FORCELOAD, b -> onActionButton(Action.FORCELOAD)).bounds(panelLeft, y, halfWidth, buttonHeight).build());
		unforceloadButton = addRenderableWidget(Button.builder(UNFORCELOAD, b -> onActionButton(Action.UNFORCELOAD)).bounds(rightLeft, y, halfWidth, buttonHeight).build());
		y += buttonHeight + BUTTON_GAP;
		clearButton = addRenderableWidget(Button.builder(CLEAR_SELECTION, b -> clearSelection()).tooltip(CLEAR_TOOLTIP).bounds(panelLeft, y, halfWidth, buttonHeight).build());
		centreButton = addRenderableWidget(Button.builder(CENTRE, b -> onCentreButton()).tooltip(CENTRE_TOOLTIP).bounds(rightLeft, y, halfWidth, buttonHeight).build());
		y += buttonHeight + BUTTON_GAP;
		zoomOutButton = addRenderableWidget(Button.builder(Component.literal("-"), b -> changeZoom(-1)).tooltip(ZOOM_OUT_TOOLTIP).bounds(panelLeft, y, buttonHeight, buttonHeight).build());
		zoomInButton = addRenderableWidget(Button.builder(Component.literal("+"), b -> changeZoom(1)).tooltip(ZOOM_IN_TOOLTIP).bounds(panelLeft + buttonHeight + BUTTON_GAP, y, buttonHeight, buttonHeight).build());
		textTop = y + buttonHeight + SECTION_GAP;
	}

	/**
	 * Closes the texture of the map when the screen closes.
	 */
	@Override
	public void removed() {
		super.removed();
		if(mapImage != null) {
			mapImage.close();
			mapImage = null;
		}
	}

	// ---- actions ----

	/**
	 * Zooms one step in or out around the centre of the grid. The grid has another size then, so the screen is laid out again.
	 */
	private void changeZoom(int direction) {
		int newZoom = HubGridFit.clampZoom(zoom + direction);
		if(newZoom == zoom)
			return;
		zoom = newZoom;
		rebuildWidgets();
	}

	private void clearSelection() {
		selection = null;
		dragging = false;
	}

	private void onCentreButton() {
		centreOnPlayer();
		rebuildCells();
	}

	private void onClaimAsButton() {
		ClaimAsToggle.toggle(teamSubId);
		refreshSchedule.afterAction();
		updateWidgets();
	}

	/**
	 * Gets the claiming modes that the player may use, sorted by their IDs, by the rule of OPAC's main menu.
	 */
	private List<ClaimingMode> getUsableClaimingModes() {
		IClientClaimsManager<?, ?, ?> claimsManager = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClaimsManager();
		IPlayerConfigClientStorageManager<?> configs = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigStorageManager();
		List<ClaimingMode> modes = new ArrayList<>();
		for(IClaimingModeAPI claimingMode : ClaimingModes.ALL_IMMUTABLE.values()) {
			ClientClaimingModeHandler claimingModeHandler = ClaimingModeClientHandlers.get(claimingMode);
			if(claimingModeHandler.getVisibilityGetter() != null && !claimingModeHandler.getVisibilityGetter().test(claimsManager, configs))
				continue;
			modes.add((ClaimingMode) claimingMode);
		}
		modes.sort(Comparator.comparing(IClaimingModeAPI::getId));
		return modes;
	}

	/**
	 * Creates the dropdown of the claiming mode at the top of the panel, as OPAC's main menu had it: the default mode
	 * first, then the modes the player may use.
	 */
	private DropDownWidget createClaimingModeDropdown(int y) {
		IClientClaimsManager<?, ?, ?> claimsManager = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClaimsManager();
		claimModeOptions = new ArrayList<>(getUsableClaimingModes());
		claimModeOptions.add(0, null);//default mode
		selectedClaimingMode = claimsManager.getRawClaimingMode();
		selectedEffectiveClaimingMode = claimsManager.getEffectiveClaimingMode(selectedClaimingMode);
		ClaimingMode effectiveDefaultMode = (ClaimingMode) claimsManager.getEffectiveClaimingMode(null);
		int selectedClaimMode = selectedClaimingMode == null ? 0 : claimModeOptions.indexOf(selectedClaimingMode);
		return DropDownWidget.Builder.begin()
				.setCallback(this::onClaimingMode)
				.setContainer(this)
				.setX(panelLeft)
				.setY(y)
				.setW(panelWidth)
				.setOptions(
						claimModeOptions.stream()
								.map(mode -> mode == null ?
										Component.translatable(
												"gui.xaero_pac_claiming_as_default", effectiveDefaultMode.getActiveLabel()
										) : mode.getActiveLabel()
								)
								.map(Component::getString)
								.toList()
								.toArray(new String[0])
				)
				.setSelected(selectedClaimMode)
				.setNarrationTitle(Component.translatable("gui.xaero_pac_claiming_as_narration"))
				.build();
	}

	/**
	 * Switches the claiming mode with OPAC's claiming mode command, as the main menu did.
	 */
	private boolean onClaimingMode(DropDownWidget dropDownWidget, int index) {
		IClientClaimsManager<?, ?, ?> claimsManager = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClaimsManager();
		claimsManager.setClaimingMode(selectedClaimingMode = claimModeOptions.get(index));
		selectedEffectiveClaimingMode = claimsManager.getEffectiveClaimingMode(selectedClaimingMode);
		String modePrefix = selectedClaimingMode == null ? ClaimingModeCommand.DEFAULT_MODE_PREFIX : selectedClaimingMode.getId();
		CommandUtil.sendCommand(minecraft, ClaimsCommandRegister.COMMAND_PREFIX + " " + modePrefix + "-claim-mode");
		lastClaimingModeChangeTime = System.currentTimeMillis();
		refreshSchedule.afterAction();
		updateWidgets();
		return true;
	}

	/**
	 * Shows the claiming mode of the server in the dropdown when it changed some other way, but not right after the player
	 * picked one, before the server has answered.
	 */
	private void updateClaimingModeDropdown() {
		if(claimingModeMenu == null || System.currentTimeMillis() - lastClaimingModeChangeTime < CLAIMING_MODE_ANSWER_MILLIS)
			return;
		IClientClaimsManager<?, ?, ?> claimsManager = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClaimsManager();
		if(claimsManager.getClaimingMode() == selectedEffectiveClaimingMode && claimsManager.getRawClaimingMode() == selectedClaimingMode)
			return;
		replaceRenderableWidget(claimingModeMenu, claimingModeMenu = createClaimingModeDropdown(claimingModeMenu.getY()));
	}

	/**
	 * Sends the area request for the selection. The claiming mode is null, which is the claiming mode the player is in
	 * (OPAC's main menu and the World Map do the same). The server answers with its normal claim result message.
	 */
	private void onActionButton(Action action) {
		if(selection == null || state != State.READY || selection.exceedsActionSize(getMaxActionSize()))
			return;
		IClientClaimsManagerAPI claimsManager = getClaimsManager();
		ResourceLocation dimension = getDimension();
		int left = selection.left();
		int top = selection.top();
		int right = selection.right();
		int bottom = selection.bottom();
		IClaimingModeAPI currentMode = null;//null is the claiming mode the player is in
		switch(action) {
			case CLAIM -> claimsManager.requestAreaClaim(dimension, left, top, right, bottom, currentMode);
			case UNCLAIM -> claimsManager.requestAreaUnclaim(dimension, left, top, right, bottom, currentMode);
			case FORCELOAD -> claimsManager.requestAreaForceload(dimension, left, top, right, bottom, true, currentMode);
			case UNFORCELOAD -> claimsManager.requestAreaForceload(dimension, left, top, right, bottom, false, currentMode);
		}
		refreshSchedule.afterAction();
	}

	/**
	 * Asks for the team information, which the server answers together with the budgets and the limit of one claim action.
	 */
	private void requestTeamInfo() {
		if(TeamInfoRefresh.request())
			refreshSchedule.onRequestSent();
	}

	// ---- widget state ----

	private void updateWidgets() {
		if(state != State.READY || claimAsButton == null)
			return;
		ClaimAsToggle.update(claimAsButton, teamSubId);
		boolean hasSelection = selection != null;
		boolean tooLarge = hasSelection && selection.exceedsActionSize(getMaxActionSize());
		setActionButton(claimButton, hasSelection, tooLarge, CLAIM_TOOLTIP);
		setActionButton(unclaimButton, hasSelection, tooLarge, UNCLAIM_TOOLTIP);
		setActionButton(forceloadButton, hasSelection, tooLarge, FORCELOAD_TOOLTIP);
		setActionButton(unforceloadButton, hasSelection, tooLarge, UNFORCELOAD_TOOLTIP);
		clearButton.active = hasSelection;
		zoomOutButton.active = zoom > 0;
		zoomInButton.active = zoom < HubGridFit.ZOOM_STEPS - 1;
		if(openDropdown != null) {
			// the open dropdown of the claiming mode covers these buttons
			for(Button button : List.of(claimAsButton, claimButton, unclaimButton, forceloadButton, unforceloadButton))
				button.setTooltip(null);
		}
	}

	private void setActionButton(Button button, boolean hasSelection, boolean tooLarge, Tooltip tooltip) {
		button.active = hasSelection && !tooLarge;
		button.setTooltip(!hasSelection ? SELECT_FIRST_TOOLTIP : tooLarge ? getTooLargeTooltip() : tooltip);
	}

	/**
	 * Gets the tooltip of the action buttons for a selection that is bigger than the server allows, which names the limit.
	 */
	private Tooltip getTooLargeTooltip() {
		Integer limit = getMaxActionSize();
		int safeLimit = limit == null ? 0 : limit;
		if(tooLargeTooltip == null || tooLargeTooltipLimit != safeLimit) {
			tooLargeTooltip = Tooltip.create(Component.translatable("gui.xaero_pac_hub_too_large_tooltip", safeLimit));
			tooLargeTooltipLimit = safeLimit;
		}
		return tooLargeTooltip;
	}

	// ---- screen ----

	@Override
	public void tick() {
		super.tick();
		State currentState = computeState();
		if(currentState != state) {
			rebuildWidgets();
			return;
		}
		if(state == State.NO_MOD)
			return;
		if(state == State.READY && (claimingModeMenu != null) != ClaimsHubLogic.isClaimingModeChoiceShown(getUsableClaimingModes().size())) {
			rebuildWidgets();
			return;
		}
		updateClaimingModeDropdown();
		updateSubConfigKnowledge();
		if(refreshSchedule.tick())
			requestTeamInfo();
		if(state == State.READY)
			terrain.tick(minecraft.level, minecraft.player.getBlockY(), centerX, centerZ, fit.radius());
		rebuildCells();
		updateWidgets();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(state == State.READY && !HubTabs.isHubKey(keyCode)) {
			int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? fit.radius() : 1;
			boolean panned = true;
			switch(keyCode) {
				case GLFW.GLFW_KEY_LEFT -> centerX -= step;
				case GLFW.GLFW_KEY_RIGHT -> centerX += step;
				case GLFW.GLFW_KEY_UP -> centerZ -= step;
				case GLFW.GLFW_KEY_DOWN -> centerZ += step;
				default -> panned = false;
			}
			if(panned) {
				rebuildCells();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/**
	 * Gets the chunk under the mouse.
	 *
	 * @param clamp  true to answer the nearest chunk of the grid for a mouse outside of it
	 * @return the grid column and row packed as {column, row}, null if the mouse is outside of the grid and not clamping
	 */
	@Nullable
	private int[] getGridCellAt(double mouseX, double mouseY, boolean clamp) {
		if(state != State.READY)
			return null;
		int column = HubGridFit.indexAt((int) Math.floor(mouseX) - gridLeft, fit.cell(), fit.cells(), clamp);
		int row = HubGridFit.indexAt((int) Math.floor(mouseY) - gridTop, fit.cell(), fit.cells(), clamp);
		if(column < 0 || row < 0)
			return null;
		return new int[]{column, row};
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int[] cell = getGridCellAt(mouseX, mouseY, false);
		if(cell != null && openDropdown == null) {
			if(button == 0) {
				int chunkX = chunkXAt(cell[0]);
				int chunkZ = chunkZAt(cell[1]);
				ChunkSelection single = ChunkSelection.single(chunkX, chunkZ);
				// clicking the one chunk that is selected deselects it
				selection = single.equals(selection) ? null : single;
				dragging = selection != null;
				return true;
			}
			if(button == 1) {
				clearSelection();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(scrollY != 0 && openDropdown == null && getGridCellAt(mouseX, mouseY, false) != null) {
			changeZoom(scrollY > 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if(dragging && button == 0 && selection != null) {
			int[] cell = getGridCellAt(mouseX, mouseY, true);
			if(cell != null)
				selection = selection.withEnd(chunkXAt(cell[0]), chunkZAt(cell[1]));
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if(dragging && button == 0) {
			dragging = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	// ---- rendering ----

	@Override
	protected void renderPreDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
		super.renderPreDropdown(guiGraphics, mouseX, mouseY, partial);
		switch(state) {
			case NO_MOD -> guiGraphics.drawCenteredString(font, MainMenu.NO_HANDSHAKE, contentCenterX(), contentTop() + 20, WARNING_COLOR);
			case NO_CLAIMS -> guiGraphics.drawCenteredString(font, MainMenu.NO_CLAIMS, contentCenterX(), contentTop() + 20, VALUE_COLOR);
			case SYNCING -> guiGraphics.drawCenteredString(font, MainMenu.CLAIMS_SYNCING, contentCenterX(), contentTop() + 20, -1);
			case READY -> {
				renderGrid(guiGraphics, mouseX, mouseY);
				renderPanelText(guiGraphics);
				renderCellTooltip(guiGraphics, mouseX, mouseY);
			}
		}
	}

	private void renderGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		int count = fit.cells();
		int cell = fit.cell();
		int size = fit.size();
		guiGraphics.fill(gridLeft - 1, gridTop - 1, gridLeft + size + 1, gridTop + size + 1, GRID_BORDER_COLOR);
		// the terrain and the claim colours are one picture
		mapImage.upload();
		mapImage.setSmooth(cell < ChunkMapImage.CHUNK_PIXELS);
		int pixels = mapImage.getPixelSize();
		guiGraphics.blit(mapImage.getId(), gridLeft, gridTop, size, size, 0, 0, pixels, pixels, pixels, pixels);
		ChunkPos playerChunk = minecraft.player.chunkPosition();
		int[] hovered = getGridCellAt(mouseX, mouseY, false);
		for(int gz = 0; gz < count; gz++) {
			for(int gx = 0; gx < count; gx++) {
				int x0 = gridLeft + gx * cell;
				int y0 = gridTop + gz * cell;
				int x1 = x0 + cell;
				int y1 = y0 + cell;
				Cell data = cells[gz * count + gx];
				if(data.category() != ClaimCategory.NONE) {
					renderClaimBorders(guiGraphics, gx, gz, count, x0, y0, x1, y1, data);
					renderBadge(guiGraphics, data.category(), x0, y0, cell);
					if(data.forceloaded())
						renderDot(guiGraphics, x0, y0, cell);
				}
				int chunkX = chunkXAt(gx);
				int chunkZ = chunkZAt(gz);
				if(selection != null && selection.contains(chunkX, chunkZ))
					guiGraphics.fill(x0, y0, x1, y1, SELECTION_FILL_COLOR);
				if(chunkX == playerChunk.x && chunkZ == playerChunk.z)
					renderOutline(guiGraphics, x0, y0, x1, y1, PLAYER_MARKER_COLOR);
				if(hovered != null && hovered[0] == gx && hovered[1] == gz)
					guiGraphics.fill(x0, y0, x1, y1, HOVER_FILL_COLOR);
			}
		}
		renderSelectionOutline(guiGraphics, count, cell);
	}

	/**
	 * Draws the 1 pixel lines along the edges of a claim where the next chunk has another owner or is not claimed.
	 */
	private void renderClaimBorders(GuiGraphics guiGraphics, int gx, int gz, int count, int x0, int y0, int x1, int y1, Cell data) {
		int edges = ClaimOverlay.edges(data.owner(), ownerAt(gx, gz - 1, count), ownerAt(gx + 1, gz, count), ownerAt(gx, gz + 1, count), ownerAt(gx - 1, gz, count));
		if(edges == 0)
			return;
		int color = ClaimOverlay.borderColor(data.rgb());
		if((edges & ClaimOverlay.NORTH) != 0)
			guiGraphics.fill(x0, y0, x1, y0 + 1, color);
		if((edges & ClaimOverlay.SOUTH) != 0)
			guiGraphics.fill(x0, y1 - 1, x1, y1, color);
		if((edges & ClaimOverlay.WEST) != 0)
			guiGraphics.fill(x0, y0, x0 + 1, y1, color);
		if((edges & ClaimOverlay.EAST) != 0)
			guiGraphics.fill(x1 - 1, y0, x1, y1, color);
	}

	@Nullable
	private Object ownerAt(int gx, int gz, int count) {
		if(gx < 0 || gz < 0 || gx >= count || gz >= count)
			return ClaimOverlay.OUTSIDE;
		return cells[gz * count + gx].owner();
	}

	private void renderSelectionOutline(GuiGraphics guiGraphics, int count, int cell) {
		if(selection == null)
			return;
		int firstColumn = Math.max(0, selection.left() - chunkXAt(0));
		int lastColumn = Math.min(count - 1, selection.right() - chunkXAt(0));
		int firstRow = Math.max(0, selection.top() - chunkZAt(0));
		int lastRow = Math.min(count - 1, selection.bottom() - chunkZAt(0));
		if(firstColumn > lastColumn || firstRow > lastRow)
			return;
		renderOutline(guiGraphics, gridLeft + firstColumn * cell, gridTop + firstRow * cell, gridLeft + (lastColumn + 1) * cell, gridTop + (lastRow + 1) * cell, SELECTION_OUTLINE_COLOR);
	}

	private static void renderOutline(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int color) {
		guiGraphics.fill(x0, y0, x1, y0 + 1, color);
		guiGraphics.fill(x0, y1 - 1, x1, y1, color);
		guiGraphics.fill(x0, y0, x0 + 1, y1, color);
		guiGraphics.fill(x1 - 1, y0, x1, y1, color);
	}

	private static int getBadgeColor(ClaimCategory category) {
		return switch(category) {
			case OWN_PRIVATE -> OWN_BADGE_COLOR;
			case OWN_TEAM -> TEAM_BADGE_COLOR;
			case PARTY -> PARTY_BADGE_COLOR;
			case ALLY -> ALLY_BADGE_COLOR;
			case SERVER -> SERVER_BADGE_COLOR;
			case EXPIRED -> EXPIRED_BADGE_COLOR;
			case OTHER, NONE -> 0;
		};
	}

	/**
	 * Draws the mark of the kind of claim in the top left corner of the chunk. The claims of other players have none:
	 * their colour is the mark.
	 */
	private void renderBadge(GuiGraphics guiGraphics, ClaimCategory category, int x0, int y0, int size) {
		int color = getBadgeColor(category);
		if(color == 0)
			return;
		renderMark(guiGraphics, x0, y0, size, color, false);
	}

	private void renderDot(GuiGraphics guiGraphics, int x0, int y0, int size) {
		renderMark(guiGraphics, x0, y0, size, FORCELOAD_DOT_COLOR, true);
	}

	/**
	 * Draws a small square in a corner of a chunk, with a dark outline when the chunk is big enough.
	 */
	private static void renderMark(GuiGraphics guiGraphics, int x0, int y0, int size, int color, boolean bottomRight) {
		int mark = Math.max(2, size / 3);
		boolean outlined = size >= 8;
		int box = outlined ? mark + 2 : mark;
		int x = bottomRight ? x0 + size - box - (outlined ? 0 : 1) : x0 + (outlined ? 0 : 1);
		int y = bottomRight ? y0 + size - box - (outlined ? 0 : 1) : y0 + (outlined ? 0 : 1);
		if(outlined) {
			guiGraphics.fill(x, y, x + box, y + box, 0xFF000000);
			guiGraphics.fill(x + 1, y + 1, x + box - 1, y + box - 1, color);
		} else
			guiGraphics.fill(x, y, x + box, y + box, color);
	}

	private void renderPanelText(GuiGraphics guiGraphics) {
		int y = textTop;
		int bottom = backTop - 3;
		// selection
		List<FormattedCharSequence> selectionLines = new ArrayList<>();
		if(selection == null)
			selectionLines.addAll(font.split(Component.translatable("gui.xaero_pac_hub_selection_none"), panelWidth));
		else {
			selectionLines.addAll(font.split(Component.translatable("gui.xaero_pac_hub_selection", selection.width(), selection.height(), selection.count()), panelWidth));
			if(selection.exceedsActionSize(getMaxActionSize())) {
				for(FormattedCharSequence line : font.split(Component.translatable("gui.xaero_pac_hub_selection_large", getMaxActionSize()), panelWidth))
					selectionLines.add(line);
			}
		}
		for(int i = 0; i < selectionLines.size() && y + font.lineHeight <= bottom; i++) {
			guiGraphics.drawString(font, selectionLines.get(i), panelLeft, y, i == 0 ? TEXT_COLOR : HINT_COLOR);
			y += LINE_STEP;
		}
		y += SECTION_GAP - 2;
		// budgets
		for(HubBudgetLines.Line line : HubBudgetLines.allLines()) {
			for(FormattedCharSequence part : font.split(line.text(), panelWidth)) {
				if(y + font.lineHeight > bottom)
					return;
				guiGraphics.drawString(font, part, panelLeft, y, line.color());
				y += LINE_STEP;
			}
		}
		y += SECTION_GAP;
		renderLegend(guiGraphics, y, bottom);
	}

	private void renderLegend(GuiGraphics guiGraphics, int top, int bottom) {
		boolean twoColumns = panelWidth >= TWO_COLUMN_LEGEND_WIDTH;
		int columnWidth = twoColumns ? panelWidth / 2 : panelWidth;
		int rows = twoColumns ? (LEGEND.size() + 1) / 2 : LEGEND.size();
		for(int i = 0; i < LEGEND.size(); i++) {
			int column = twoColumns ? i / rows : 0;
			int row = twoColumns ? i % rows : i;
			int x = panelLeft + column * columnWidth;
			int y = top + row * LINE_STEP;
			if(y + SWATCH_SIZE > bottom)
				continue;
			LegendEntry entry = LEGEND.get(i);
			renderSwatch(guiGraphics, entry, x, y);
			guiGraphics.drawString(font, font.plainSubstrByWidth(entry.label().getString(), columnWidth - SWATCH_SIZE - 6), x + SWATCH_SIZE + 3, y, VALUE_COLOR);
		}
	}

	private void renderSwatch(GuiGraphics guiGraphics, LegendEntry entry, int x, int y) {
		guiGraphics.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, 0xFF000000);
		guiGraphics.fill(x + 1, y + 1, x + SWATCH_SIZE - 1, y + SWATCH_SIZE - 1, 0xFF555555);
		if(entry.outline())
			renderOutline(guiGraphics, x + 1, y + 1, x + SWATCH_SIZE - 1, y + SWATCH_SIZE - 1, entry.badgeColor());
		else if(entry.badgeColor() != 0)
			guiGraphics.fill(x + 2, y + 2, x + 5, y + 5, entry.badgeColor());
	}

	private String getOwnerName(IPlayerChunkClaimAPI claim, ClaimCategory category) {
		if(category == ClaimCategory.SERVER)
			return Component.translatable("gui.xaero_pac_hub_tt_server_owner").getString();
		if(category == ClaimCategory.EXPIRED)
			return Component.translatable("gui.xaero_pac_hub_tt_expired_owner").getString();
		IClientClaimsManagerAPI claimsManager = getClaimsManager();
		if(!claimsManager.hasPlayerInfo(claim.getPlayerId()))
			return claim.getPlayerId().toString();
		IClientPlayerClaimInfoAPI info = claimsManager.getPlayerInfo(claim.getPlayerId());
		Component partyName = info.isPartyOwned() ? info.getPartyName() : null;
		return partyName != null ? partyName.getString() : info.getPlayerUsername();
	}

	private static String getCategoryKey(ClaimCategory category) {
		return switch(category) {
			case OWN_PRIVATE -> "gui.xaero_pac_hub_tt_own_private";
			case OWN_TEAM -> "gui.xaero_pac_hub_tt_own_team";
			case PARTY -> "gui.xaero_pac_hub_tt_party";
			case ALLY -> "gui.xaero_pac_hub_tt_ally";
			case OTHER -> "gui.xaero_pac_hub_tt_other";
			case SERVER -> "gui.xaero_pac_hub_tt_server";
			case EXPIRED -> "gui.xaero_pac_hub_tt_expired";
			case NONE -> "gui.xaero_pac_hub_tt_unclaimed";
		};
	}

	private void renderCellTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		int[] hovered = getGridCellAt(mouseX, mouseY, false);
		if(hovered == null || openDropdown != null)
			return;
		Cell data = cells[hovered[1] * fit.cells() + hovered[0]];
		int chunkX = chunkXAt(hovered[0]);
		int chunkZ = chunkZAt(hovered[1]);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("gui.xaero_pac_hub_tt_chunk", chunkX, chunkZ));
		int categoryColor = getBadgeColor(data.category()) == 0 ? VALUE_COLOR : getBadgeColor(data.category());
		lines.add(Component.translatable(getCategoryKey(data.category())).withStyle(s -> s.withColor(categoryColor & 0xFFFFFF)));
		IPlayerChunkClaimAPI claim = data.claim();
		if(claim != null) {
			lines.add(Component.translatable("gui.xaero_pac_hub_tt_owner", Component.literal(getOwnerName(claim, data.category())).withStyle(s -> s.withColor(VALUE_COLOR))));
			Component name = getClaimsManager().getFullName(claim, getDimension(), true);
			lines.add(Component.translatable("gui.xaero_pac_hub_tt_name", name.copy().withStyle(s -> s.withColor(VALUE_COLOR))));
			if(data.forceloaded())
				lines.add(Component.translatable("gui.xaero_pac_hub_tt_forceloaded").withStyle(s -> s.withColor(HINT_COLOR)));
		}
		ChunkPos playerChunk = minecraft.player.chunkPosition();
		if(chunkX == playerChunk.x && chunkZ == playerChunk.z)
			lines.add(Component.translatable("gui.xaero_pac_hub_tt_you_here").withStyle(s -> s.withColor(PLAYER_MARKER_COLOR)));
		List<FormattedCharSequence> tooltip = new ArrayList<>();
		for(Component line : lines)
			tooltip.addAll(font.split(line, TOOLTIP_WIDTH));
		guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY);
	}

}
