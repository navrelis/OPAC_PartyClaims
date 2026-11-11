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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.claims.IClientClaimsManager;
import xaero.pac.client.gui.MainMenu;
import xaero.pac.client.gui.OtherPlayerConfigWaitScreen;
import xaero.pac.client.player.config.IPlayerConfigClientStorage;
import xaero.pac.client.player.config.IPlayerConfigClientStorageManager;
import xaero.pac.client.player.config.IPlayerConfigStringableOptionClientStorage;
import xaero.pac.client.world.capability.ClientWorldMainCapability;
import xaero.pac.client.world.capability.api.ClientWorldCapabilityTypes;
import xaero.pac.common.packet.config.ServerboundOtherPlayerConfigPacket;

/**
 * The Settings page of the hub: everything that OPAC's old config menu offered, in two sections. "My settings" holds the
 * player config and the config of the party claims, "Server" the configs of the server (default player config, server
 * claims, expired claims, wilderness) and the config of another player. The screens they open come back to this page.
 * <p>
 * The left column has room below "My settings" for a section of the client options.
 */
public class SettingsTabScreen extends HubTabScreen {

	private static final Component TITLE = HubTab.SETTINGS.getLabel();
	private static final Component MY_SETTINGS = Component.translatable("gui.xaero_pac_hub_settings_my");
	private static final Component SERVER = Component.translatable("gui.xaero_pac_hub_settings_server");
	private static final Component ANOTHER_PLAYER_TITLE = Component.translatable("gui.xaero_pac_ui_other_player_config_name_title");

	private static final int COLUMN_GAP = 12;
	private static final int MAX_COLUMN_WIDTH = 200;
	private static final int BACK_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 2;
	private static final int SECTION_BODY = HubSidebar.HEADER_HEIGHT + 2;
	private static final int HEADER_COLOR = 0xFFFFFFFF;

	private static String otherPlayerNameString = "";

	private boolean serverHasMod;
	private int leftColumn;
	private int rightColumn;
	private int columnWidth;
	private int otherPlayerLabelY;
	private Button myPlayerConfigButton;
	private Button partyClaimsConfigButton;
	private Button defaultConfigButton;
	private Button serverClaimsConfigButton;
	private Button expiredClaimsConfigButton;
	private Button wildernessConfigButton;
	private Button otherPlayerConfigButton;
	private EditBox otherPlayerNameBox;

	public SettingsTabScreen(Screen escape, Screen parent) {
		super(escape, parent, TITLE);
	}

	@Override
	protected HubTab getTab() {
		return HubTab.SETTINGS;
	}

	@Override
	protected void init() {
		super.init();
		columnWidth = Math.max(60, Math.min(MAX_COLUMN_WIDTH, (contentWidth() - COLUMN_GAP) / 2));
		leftColumn = contentLeft();
		rightColumn = leftColumn + columnWidth + COLUMN_GAP;
		int rowStep = BUTTON_HEIGHT + BUTTON_GAP;

		int y = contentTop() + SECTION_BODY;
		myPlayerConfigButton = addRenderableWidget(Button.builder(Component.translatable("gui.xaero_pac_ui_my_player_config"), b -> getConfigs().openMyPlayerConfigScreen(escape, this)).bounds(leftColumn, y, columnWidth, BUTTON_HEIGHT).build());
		partyClaimsConfigButton = addRenderableWidget(Button.builder(Component.translatable("gui.xaero_pac_ui_party_claims_config"), b -> getConfigs().openPartyClaimsConfigScreen(escape, this)).bounds(leftColumn, y + rowStep, columnWidth, BUTTON_HEIGHT).build());

		defaultConfigButton = addRenderableWidget(Button.builder(Component.translatable("gui.xaero_pac_ui_default_player_config"), b -> getConfigs().openDefaultPlayerConfigScreen(escape, this)).bounds(rightColumn, y, columnWidth, BUTTON_HEIGHT).build());
		serverClaimsConfigButton = addRenderableWidget(Button.builder(Component.translatable("gui.xaero_pac_ui_server_claims_config"), b -> getConfigs().openServerClaimsConfigScreen(escape, this)).bounds(rightColumn, y + rowStep, columnWidth, BUTTON_HEIGHT).build());
		expiredClaimsConfigButton = addRenderableWidget(Button.builder(Component.translatable("gui.xaero_pac_ui_expired_claims_config"), b -> getConfigs().openExpiredClaimsConfigScreen(escape, this)).bounds(rightColumn, y + 2 * rowStep, columnWidth, BUTTON_HEIGHT).build());
		wildernessConfigButton = addRenderableWidget(Button.builder(Component.translatable("gui.xaero_pac_ui_wilderness_config"), b -> getConfigs().openWildernessConfigScreen(escape, this)).bounds(rightColumn, y + 3 * rowStep, columnWidth, BUTTON_HEIGHT).build());

		otherPlayerLabelY = y + 4 * rowStep + 6;
		int boxTop = otherPlayerLabelY + font.lineHeight + 3;
		otherPlayerNameBox = addRenderableWidget(new EditBox(font, rightColumn, boxTop, columnWidth, BUTTON_HEIGHT, Component.translatable("gui.xaero_pac_ui_other_player_config_name_field")));
		otherPlayerConfigButton = addRenderableWidget(Button.builder(Component.translatable("gui.xaero_pac_ui_other_player_config_button"), this::onOtherPlayerConfigButton).bounds(rightColumn, boxTop + rowStep, columnWidth, BUTTON_HEIGHT).build());
		updateOtherPlayerButton();

		int backWidth = Math.min(BACK_WIDTH, contentWidth());
		addRenderableWidget(Button.builder(BACK, b -> goBack()).bounds(contentCenterX() - backWidth / 2, contentBottom() - BUTTON_HEIGHT, backWidth, BUTTON_HEIGHT).build());

		IClientClaimsManager<?, ?, ?> claimsManager = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClaimsManager();
		IPlayerConfigClientStorageManager<IPlayerConfigClientStorage<IPlayerConfigStringableOptionClientStorage<?>>> configStorage = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigStorageManager();
		ClientWorldMainCapability mainCap = (ClientWorldMainCapability) OpenPartiesAndClaims.INSTANCE.getCapabilityHelper().getCapability(minecraft.level, ClientWorldCapabilityTypes.MAIN_CAP);
		otherPlayerNameBox.setValue(otherPlayerNameString);
		otherPlayerNameBox.setFilter(s -> s.matches(ServerboundOtherPlayerConfigPacket.OWNER_NAME_REGEX));
		otherPlayerNameBox.setResponder(s -> {otherPlayerNameString = s; updateOtherPlayerButton();});
		otherPlayerNameBox.setEditable(mainCap.getClientWorldData().serverHasMod() && configStorage.isAdmin());

		serverHasMod = mainCap.getClientWorldData().serverHasMod();
		myPlayerConfigButton.active = serverHasMod && configStorage.getMyPlayerConfig().getPermissions().canView();
		serverClaimsConfigButton.active = serverHasMod && configStorage.getServerClaimsConfig().getPermissions().canView();
		expiredClaimsConfigButton.active = serverHasMod && configStorage.getExpiredClaimsConfig().getPermissions().canView();
		wildernessConfigButton.active = serverHasMod && configStorage.getWildernessConfig().getPermissions().canView();
		defaultConfigButton.active = serverHasMod && configStorage.getDefaultPlayerConfig().getPermissions().canView();
		partyClaimsConfigButton.active = serverHasMod && claimsManager.usingPartyOwnedClaims() && claimsManager.isInParty()
				&& configStorage.getPartyClaimsConfig().getPermissions().canView();
	}

	private IPlayerConfigClientStorageManager<?> getConfigs() {
		return OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigStorageManager();
	}

	private boolean isPlayerNameAllowed() {
		return !otherPlayerNameString.isEmpty() && !otherPlayerNameString.equalsIgnoreCase(minecraft.player.getGameProfile().getName());
	}

	private void onOtherPlayerConfigButton(Button b) {
		if(isPlayerNameAllowed())
			minecraft.setScreen(new OtherPlayerConfigWaitScreen(escape, this, otherPlayerNameString));
	}

	private void updateOtherPlayerButton() {
		ClientWorldMainCapability mainCap = (ClientWorldMainCapability) OpenPartiesAndClaims.INSTANCE.getCapabilityHelper().getCapability(minecraft.level, ClientWorldCapabilityTypes.MAIN_CAP);
		otherPlayerConfigButton.active = mainCap.getClientWorldData().serverHasMod() && minecraft.player.hasPermissions(2) && isPlayerNameAllowed();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(otherPlayerNameBox.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && isPlayerNameAllowed()) {
			setFocused(null);
			otherPlayerConfigButton.onPress();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void renderPreDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
		super.renderPreDropdown(guiGraphics, mouseX, mouseY, partial);
		int top = contentTop();
		HubSidebar.renderHeader(guiGraphics, font, MY_SETTINGS, leftColumn, top, leftColumn + columnWidth, HEADER_COLOR);
		HubSidebar.renderHeader(guiGraphics, font, SERVER, rightColumn, top, rightColumn + columnWidth, HEADER_COLOR);
		guiGraphics.drawString(font, font.plainSubstrByWidth(ANOTHER_PLAYER_TITLE.getString(), columnWidth), rightColumn, otherPlayerLabelY, -1);
		if(!serverHasMod)
			guiGraphics.drawCenteredString(font, MainMenu.NO_HANDSHAKE, contentCenterX(), contentBottom() - BUTTON_HEIGHT - 12, 0xFFFF5555);
	}

}
