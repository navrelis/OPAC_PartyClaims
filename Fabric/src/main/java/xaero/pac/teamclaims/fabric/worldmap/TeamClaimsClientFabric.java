package xaero.pac.teamclaims.fabric.worldmap;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.fml.config.ModConfig;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.teamclaims.config.TeamClaimsClientConfig;

/**
 * Client initialization of Team Claims on Fabric: registers the client config and, if Xaero's World Map is installed, the
 * button on its map screen. The classes that use World Map are only referred to behind the {@code isModLoaded} check.
 */
public class TeamClaimsClientFabric implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		NeoForgeConfigRegistry.INSTANCE.register(OpenPartiesAndClaims.MOD_ID, ModConfig.Type.CLIENT,
				TeamClaimsClientConfig.SPEC, TeamClaimsClientConfig.FILE_NAME);
		if(FabricLoader.getInstance().isModLoaded("xaeroworldmap")) {
			TeamClaimsClientConfig.worldMapIntegration = true;
			WorldMapClaimingButton.register();
		}
	}

}
