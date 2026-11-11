package xaero.pac.teamclaims.fabric.worldmap;

import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.gui.hub.ClaimAsToggle;
import xaero.pac.client.gui.hub.ClaimsHubLogic;
import xaero.pac.client.teamclaims.WorldMapClaimingLogic;

import javax.annotation.Nullable;

/**
 * What the entries and the button on the map screen of Xaero's World Map ask of the claiming state of the local player.
 * No World Map class is used here.
 */
public final class TeamClaimsWorldMapState {

	private TeamClaimsWorldMapState() {
	}

	/**
	 * Gets the ID of the team sub-config of the player, the same way the Claims tab of the hub does; null if there is none.
	 */
	@Nullable
	public static String teamSubId() {
		return ClaimsHubLogic.findTeamSubId(OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigStorageManager().getMyPlayerConfig().getSubConfigIds());
	}

	/**
	 * Checks whether the claim choice can be used right now: team claims need a team sub-config, and both only apply while
	 * claiming as a player.
	 */
	public static boolean isUsable(boolean forTeam) {
		return WorldMapClaimingLogic.isUsable(forTeam, ClaimAsToggle.isClaimingAsPlayer(), teamSubId() != null);
	}

}
