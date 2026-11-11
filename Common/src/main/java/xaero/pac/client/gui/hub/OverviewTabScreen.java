package xaero.pac.client.gui.hub;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** TEMP stub (dropped on merge). */
public class OverviewTabScreen extends HubTabScreen {

	public OverviewTabScreen(Screen escape, Screen parent) {
		super(escape, parent, Component.empty());
	}

	@Override
	protected HubTab getTab() {
		return HubTab.OVERVIEW;
	}

}
