package xaero.pac.client.teamclaims;

import net.minecraft.network.chat.Component;
import xaero.pac.teamclaims.config.TeamClaimsClientConfig.ButtonPosition;

import javax.annotation.Nullable;

/**
 * The rules behind the Team Claims entries and the button on the map screen of Xaero's World Map (the Fabric classes
 * in {@code xaero.pac.teamclaims.fabric.worldmap}): which entries can be used, whether a one-shot claim has to put the
 * claim mode back, the labels and where the button sits. Nothing here touches a World Map class or a screen, so it can be
 * tested on the headless gametest server.
 */
public final class WorldMapClaimingLogic {

	public static final String KEY_BUTTON = "gui.xaero_pac_wm_button";
	public static final String KEY_MODE_TEAM = "gui.xaero_pac_wm_mode_team";
	public static final String KEY_MODE_PRIVATE = "gui.xaero_pac_wm_mode_private";
	public static final String KEY_TOGGLE_MODE = "gui.xaero_pac_wm_toggle_mode";

	/** Size of the button on the map screen. */
	public static final int BUTTON_WIDTH = 150;
	public static final int BUTTON_HEIGHT = 20;
	private static final int MARGIN = 2;
	/** The map screen has its settings button (30 pixels wide) in the top left and a column of buttons at the left and right edge. */
	private static final int SIDE_CLEARANCE = 34;
	/** The map screen writes the coordinates, biome and more of the cursor in the top centre, three lines of 10 pixels. */
	private static final int TOP_CENTER_CLEARANCE = 34;

	private WorldMapClaimingLogic() {
	}

	/**
	 * Checks whether a claim entry (or the button) can be used: claiming as a player is what the {@code claims.usedSub} choice
	 * applies to, and a team claim also needs a team sub-config. A private claim needs no more than the claiming mode.
	 *
	 * @param forTeam  true for the team entry
	 * @param claimingAsPlayer  whether the claiming mode is the player one
	 * @param hasTeamSub  whether the player has a team sub-config
	 */
	public static boolean isUsable(boolean forTeam, boolean claimingAsPlayer, boolean hasTeamSub) {
		return claimingAsPlayer && (!forTeam || hasTeamSub);
	}

	/**
	 * Checks whether a one-shot claim has to change the claim mode back afterwards: only if the claim was made with the
	 * other choice than the one that was selected.
	 */
	public static boolean restoresAfterOneShot(boolean wasTeam, boolean claimedAsTeam) {
		return wasTeam != claimedAsTeam;
	}

	/**
	 * Formats the claims used against the limit: {@code 34/120}, with the infinity sign for the biggest possible limit.
	 */
	public static String formatCount(int used, int limit) {
		return used + "/" + (limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit));
	}

	public static Component modeName(boolean team) {
		return Component.translatable(team ? KEY_MODE_TEAM : KEY_MODE_PRIVATE);
	}

	/**
	 * Gets the label of the button on the map screen: the current choice and, if known, the claims used of its pool.
	 *
	 * @param used  the claims used of the selected pool, null if the server has not reported them
	 * @param limit  the limit of that pool
	 */
	public static Component buttonLabel(boolean team, @Nullable Integer used, int limit) {
		Component mode = modeName(team);
		if(used != null)
			mode = Component.empty().append(mode).append(" " + formatCount(used, limit));
		return Component.translatable(KEY_BUTTON, mode);
	}

	/**
	 * Gets the label of the right-click entry that switches the claim mode: the mode it switches to, the other one.
	 */
	public static Component toggleLabel(boolean team) {
		return Component.translatable(KEY_TOGGLE_MODE, modeName(!team));
	}

	/**
	 * Gets the top left corner of the button on the screen, in a place the buttons and texts of the map screen leave free.
	 *
	 * @return {x, y}
	 */
	public static int[] buttonPosition(ButtonPosition position, int screenWidth, int screenHeight, int buttonWidth, int buttonHeight) {
		int left = SIDE_CLEARANCE;
		int right = screenWidth - buttonWidth - SIDE_CLEARANCE;
		int top = MARGIN;
		int bottom = screenHeight - buttonHeight - MARGIN;
		return switch(position) {
			case TOP_LEFT -> new int[]{left, top};
			case TOP_CENTER -> new int[]{(screenWidth - buttonWidth) / 2, TOP_CENTER_CLEARANCE};
			case TOP_RIGHT -> new int[]{right, top};
			case BOTTOM_LEFT -> new int[]{left, bottom};
			case BOTTOM_RIGHT -> new int[]{right, bottom};
		};
	}

}
