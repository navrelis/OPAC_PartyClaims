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

import com.google.common.collect.Lists;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.player.config.IPlayerConfigClientStorage;
import xaero.pac.client.player.config.IPlayerConfigStringableOptionClientStorage;
import xaero.pac.common.claims.player.mode.api.ClaimingModes;
import xaero.pac.common.packet.config.PlayerConfigOptionValuePacket;
import xaero.pac.common.packet.config.ServerboundPlayerConfigOptionValuePacket;
import xaero.pac.common.server.player.config.PlayerConfig;
import xaero.pac.common.server.player.config.PlayerConfigOptionSpec;
import xaero.pac.common.server.player.config.api.PlayerConfigType;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import javax.annotation.Nullable;

/**
 * The "Claim as: Private / Team" toggle that the Claims tab and the Team tab share. It is the player config option
 * {@code claims.usedSub}, which decides who owns the chunks the player claims and what Xaero's World Map "Claim Selected"
 * claims. Reading, writing (the local copy and the option packet to the server), the button label and its tooltip are all
 * here, the rules are in {@link ClaimsHubLogic}.
 */
public final class ClaimAsToggle {

	private static final Component PRIVATE = Component.translatable("gui.xaero_pac_hub_claim_as_private");
	private static final Component TEAM = Component.translatable("gui.xaero_pac_hub_claim_as_team");
	private static final Tooltip TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_claim_as_tooltip"));
	private static final Tooltip NO_TEAM_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_claim_as_no_team_tooltip"));
	private static final Tooltip OTHER_MODE_TOOLTIP = Tooltip.create(Component.translatable("gui.xaero_pac_hub_claim_as_other_mode_tooltip"));

	private ClaimAsToggle() {
	}

	private static IPlayerConfigStringableOptionClientStorage<String> getUsedSubOption() {
		IPlayerConfigClientStorage<?> config = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigStorageManager().getMyPlayerConfig();
		return config.getOption(PlayerConfigOptions.USED_SUBCLAIM);
	}

	/**
	 * Gets the sub-config that new claims are made with ({@value PlayerConfig#MAIN_SUB_ID} if none is set).
	 */
	public static String getUsedSub() {
		String used = getUsedSubOption().getValue();
		return used == null ? PlayerConfig.MAIN_SUB_ID : used;
	}

	/**
	 * Checks whether new claims are team claims.
	 */
	public static boolean isTeamSelected() {
		return ClaimsHubLogic.isTeamSelected(getUsedSub());
	}

	/**
	 * Checks whether the player claims in the claiming mode that the toggle applies to (as a player, not as a party or the
	 * server).
	 */
	public static boolean isClaimingAsPlayer() {
		return OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClaimsManager().getClaimingMode() == ClaimingModes.PLAYER;
	}

	/**
	 * Switches between private and team claims: changes the local copy of the option and sends it to the server.
	 *
	 * @param teamSubId  the ID of the team sub-config of the player, null if there is none (then the choice is private)
	 */
	public static void toggle(@Nullable String teamSubId) {
		set(!isTeamSelected(), teamSubId);
	}

	/**
	 * Picks private or team claims.
	 *
	 * @param team  true for team claims
	 * @param teamSubId  the ID of the team sub-config of the player, null if there is none (then the choice is private)
	 */
	@SuppressWarnings("unchecked")
	public static void set(boolean team, @Nullable String teamSubId) {
		String value = ClaimsHubLogic.usedSubFor(team, teamSubId);
		getUsedSubOption().setValue(value);
		PlayerConfigOptionValuePacket.Entry entry = PlayerConfigOptionValuePacket.Entry.of((PlayerConfigOptionSpec<String>) PlayerConfigOptions.USED_SUBCLAIM, value);
		OpenPartiesAndClaims.INSTANCE.getPacketHandler().sendToServer(
				new ServerboundPlayerConfigOptionValuePacket(PlayerConfigType.PLAYER, null, null, Lists.newArrayList(entry)));
	}

	/**
	 * Sets the label, the tooltip and whether the button can be pressed from the state of the option.
	 *
	 * @param teamSubId  the ID of the team sub-config of the player, null if there is none
	 */
	public static void update(Button button, @Nullable String teamSubId) {
		button.setMessage(isTeamSelected() ? TEAM : PRIVATE);
		if(!isClaimingAsPlayer()) {
			button.active = false;
			button.setTooltip(OTHER_MODE_TOOLTIP);
		} else if(teamSubId == null) {
			button.active = false;
			button.setTooltip(NO_TEAM_TOOLTIP);
		} else {
			button.active = true;
			button.setTooltip(TOOLTIP);
		}
	}

}
