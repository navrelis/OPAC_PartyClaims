package xaero.pac.teamclaims.fabric.worldmap.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.mods.pac.SupportOpenPartiesAndClaims;
import xaero.pac.teamclaims.fabric.worldmap.WorldMapRightClickEntries;

import java.util.ArrayList;

/**
 * Adds the Team Claims entries to the end of the right-click menu that Xaero's World Map builds for the selected chunks.
 */
@Mixin(value = SupportOpenPartiesAndClaims.class, remap = false)
public class MixinWorldMapSupportOpenPartiesAndClaims {

	// The screen is cast through Object because its superclass is in the library bundled in World Map, which is not on the compile classpath.
	@Inject(method = "addRightClickOptions", at = @At("TAIL"), remap = false, require = 0)
	private void opacTeamClaims$addRightClickOptions(GuiMap screen, ArrayList<RightClickOption> options, ResourceKey<Level> dimension,
													MapTileSelection selection, MapProcessor mapProcessor, CallbackInfo info) {
		WorldMapRightClickEntries.add((IRightClickableElement) (Object) screen, options, dimension, selection, mapProcessor);
	}

}
