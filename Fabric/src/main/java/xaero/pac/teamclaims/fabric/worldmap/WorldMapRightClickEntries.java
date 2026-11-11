package xaero.pac.teamclaims.fabric.worldmap;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.map.MapProcessor;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.mods.pac.SupportOpenPartiesAndClaims;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.claims.api.IClientClaimsManagerAPI;
import xaero.pac.client.gui.hub.ClaimAsToggle;
import xaero.pac.client.gui.hub.HubTabs;
import xaero.pac.client.teamclaims.WorldMapClaimingLogic;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * The Team Claims entries of the right-click menu of the map screen of Xaero's World Map: claim the selection once as team or
 * as private, switch the claim mode, and open the claims menu. The switch and the menu entry follow the "Claim Selected" and
 * the "Unclaim Selected" entries of World Map (a selection that World Map shows as claimed offers "Unclaim Selected" instead
 * of "Claim Selected"); the one-shot claims only follow "Claim Selected".
 * <p>
 * The one-shot claims follow the "Claim Selected" entry of World Map ({@code SupportOpenPartiesAndClaims$2}): the same area,
 * clamped to the claim distance unless in admin mode, the same message for a large area and the same
 * {@code requestAreaClaim} call with no claiming mode (the one in use). The server takes the packet of the option change and
 * the packet of the claim request in the order they arrive and handles each completely (the claim reads the sub-config in
 * use when it arrives), so changing the option, claiming and changing it back works as a single claim.
 */
public final class WorldMapRightClickEntries {

	private static final String WORLD_MAP_CLAIM_KEY = "gui.xaero_pac_claim_chunks";
	private static final String WORLD_MAP_UNCLAIM_KEY = "gui.xaero_pac_unclaim_chunks";
	private static final String LARGE_CLAIM_MESSAGE_KEY = "gui.xaero_pac_requested_large_claim";
	private static final String INTERRUPT_KEY = "gui.xaero_pac_interrupt_claiming";
	/** The area (in chunks) from which World Map says that a large area is being claimed. */
	private static final int LARGE_AREA = 512;

	private WorldMapRightClickEntries() {
	}

	private static final class ActionOption extends RightClickOption {

		private final Consumer<Screen> action;

		private ActionOption(String name, int index, IRightClickableElement target, boolean active, Consumer<Screen> action) {
			super(name, index, target);
			this.active = active;
			this.action = action;
		}

		@Override
		public void onAction(Screen screen) {
			action.accept(screen);
		}

	}

	/**
	 * Adds the entries after those of World Map, if World Map offers the "Claim Selected" or the "Unclaim Selected" entry (so
	 * the server has the mod and there is a selection that can be claimed or unclaimed). The one-shot claims are only added
	 * next to "Claim Selected".
	 */
	public static void add(IRightClickableElement screen, ArrayList<RightClickOption> options, ResourceKey<Level> dimension, MapTileSelection selection, MapProcessor mapProcessor) {
		if(selection == null)
			return;
		boolean claimEntry = hasWorldMapEntry(options, WORLD_MAP_CLAIM_KEY);
		if(!claimEntry && !hasWorldMapEntry(options, WORLD_MAP_UNCLAIM_KEY))
			return;
		boolean team = ClaimAsToggle.isTeamSelected();
		if(claimEntry) {
			options.add(new ActionOption("gui.xaero_pac_wm_claim_as_team", options.size(), screen, TeamClaimsWorldMapState.isUsable(true),
					s -> claimOnce(true, dimension.location(), selection, mapProcessor)));
			options.add(new ActionOption("gui.xaero_pac_wm_claim_as_private", options.size(), screen, TeamClaimsWorldMapState.isUsable(false),
					s -> claimOnce(false, dimension.location(), selection, mapProcessor)));
		}
		options.add(new ActionOption(WorldMapClaimingLogic.KEY_TOGGLE_MODE, options.size(), screen, TeamClaimsWorldMapState.isUsable(true),
				s -> ClaimAsToggle.toggle(TeamClaimsWorldMapState.teamSubId())).setNameFormatArgs(WorldMapClaimingLogic.modeName(!team)));
		options.add(new ActionOption("gui.xaero_pac_wm_open_menu", options.size(), screen, true,
				s -> HubTabs.openLast(Minecraft.getInstance(), null, s)));
	}

	private static boolean hasWorldMapEntry(ArrayList<RightClickOption> options, String key) {
		for(RightClickOption option : options)
			if(option.getDisplayName().getContents() instanceof TranslatableContents contents && key.equals(contents.getKey()))
				return true;
		return false;
	}

	/**
	 * Claims the selection as team or private: the choice is switched for this claim only and put back if it was another one.
	 */
	private static void claimOnce(boolean team, ResourceLocation dimension, MapTileSelection selection, MapProcessor mapProcessor) {
		boolean wasTeam = ClaimAsToggle.isTeamSelected();
		String teamSubId = TeamClaimsWorldMapState.teamSubId();
		boolean restore = WorldMapClaimingLogic.restoresAfterOneShot(wasTeam, team);
		if(restore)
			ClaimAsToggle.set(team, teamSubId);
		requestSelectionClaim(dimension, selection, mapProcessor);
		if(restore)
			ClaimAsToggle.set(wasTeam, teamSubId);
	}

	/**
	 * Sends the area claim request of the selection exactly as "Claim Selected" of World Map does.
	 */
	private static void requestSelectionClaim(ResourceLocation dimension, MapTileSelection selection, MapProcessor mapProcessor) {
		IClientClaimsManagerAPI claimsManager = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClaimsManager();
		ChunkPos playerChunk = Minecraft.getInstance().player.chunkPosition();
		int left = selection.getLeft();
		int top = selection.getTop();
		int right = selection.getRight();
		int bottom = selection.getBottom();
		if(!claimsManager.isAdminMode()) {
			int distance = claimsManager.getMaxClaimDistance();
			left = Math.max(left, playerChunk.x - distance);
			top = Math.max(top, playerChunk.z - distance);
			right = Math.min(right, playerChunk.x + distance);
			bottom = Math.min(bottom, playerChunk.z + distance);
		}
		int width = right - left + 1;
		int height = bottom - top + 1;
		if(width > 0 && height > 0 && width * height >= LARGE_AREA)
			mapProcessor.getMessageBox().addMessageWithSource(SupportOpenPartiesAndClaims.CLAIMS_MESSAGE_SOURCE,
					Component.translatable(LARGE_CLAIM_MESSAGE_KEY, Component.translatable(INTERRUPT_KEY).withStyle(ChatFormatting.GOLD)));
		claimsManager.requestAreaClaim(dimension, left, top, right, bottom, null);
	}

}
