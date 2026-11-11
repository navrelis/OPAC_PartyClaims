package xaero.pac.teamclaims.fabric.worldmap;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import xaero.map.mods.SupportMods;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.gui.hub.ClaimAsToggle;
import xaero.pac.client.gui.hub.TeamInfoRefresh;
import xaero.pac.client.teamclaims.WorldMapClaimingLogic;
import xaero.pac.common.packet.parties.PartyBudgetData;
import xaero.pac.teamclaims.config.TeamClaimsClientConfig;

/**
 * The button on the map screen of Xaero's World Map that shows whether the player claims privately or for the team, with the
 * claims used and the limit of that pool, and switches between the two. Only loaded when World Map is installed.
 */
public final class WorldMapClaimingButton {

	/** How often (in ticks) the button asks the server for the budget, which the answer to the team information request carries. */
	private static final int REFRESH_TICKS = 100;

	/** Compared by name: GuiMap extends a class of the library bundled in World Map, which is not on the compile classpath. */
	private static final String MAP_SCREEN_CLASS = "xaero.map.gui.GuiMap";

	private WorldMapClaimingButton() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if(MAP_SCREEN_CLASS.equals(screen.getClass().getName()) && TeamClaimsClientConfig.CONFIG.worldMapButton.get())
				addButton(screen, width, height);
		});
	}

	private static void addButton(Screen screen, int width, int height) {
		int[] at = WorldMapClaimingLogic.buttonPosition(TeamClaimsClientConfig.CONFIG.worldMapButtonPosition.get(), width, height,
				WorldMapClaimingLogic.BUTTON_WIDTH, WorldMapClaimingLogic.BUTTON_HEIGHT);
		Button button = Button.builder(WorldMapClaimingLogic.buttonLabel(false, null, 0), b -> ClaimAsToggle.toggle(TeamClaimsWorldMapState.teamSubId()))
				.bounds(at[0], at[1], WorldMapClaimingLogic.BUTTON_WIDTH, WorldMapClaimingLogic.BUTTON_HEIGHT).build();
		Screens.getButtons(screen).add(button);
		TeamInfoRefresh.request();
		int[] ticks = {0};
		ScreenEvents.afterTick(screen).register(s -> {
			if(++ticks[0] % REFRESH_TICKS == 0)
				TeamInfoRefresh.request();
			update(button);
		});
		update(button);
	}

	private static void update(Button button) {
		button.visible = SupportMods.xaeroPac != null && SupportMods.xaeroPac.serverHasMod();
		if(!button.visible)
			return;
		boolean team = ClaimAsToggle.isTeamSelected();
		Integer used = null;
		int limit = 0;
		PartyBudgetData budget = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClientPartyStorage().getBudget();
		if(budget != null && budget.available()) {
			if(!team) {
				used = budget.privateClaims();
				limit = budget.privateClaimLimit();
			} else if(budget.inTeam()) {
				used = budget.teamClaims();
				limit = budget.teamClaimLimit();
			}
		}
		ClaimAsToggle.update(button, TeamClaimsWorldMapState.teamSubId());//the active state and the tooltip
		button.setMessage(WorldMapClaimingLogic.buttonLabel(team, used, limit));
	}

}
