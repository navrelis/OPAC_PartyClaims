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

package xaero.pac.teamclaims.twoplayertest.client;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import xaero.pac.teamclaims.twoplayertest.TwoPlayerTest;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [Team Claims] The client side of the two-player test: counts ticks and frames, starts the {@link Scenario} of this
 * player on its own thread and a watchdog that ends it after {@link TwoPlayerTest#timeoutMinutes()} minutes (failing
 * whatever was not reported). Does nothing unless {@link TwoPlayerTest#isActive()} with the role Player1 or Player2.
 */
public class TwoPlayerTestClient implements ClientModInitializer {

	private static final Logger LOGGER = LogUtils.getLogger();

	@Override
	public void onInitializeClient() {
		if(!TwoPlayerTest.isActive())
			return;
		String player = TwoPlayerTest.role();
		Path out = TwoPlayerTest.outDir();
		if(!TwoPlayerTest.PLAYER1.equals(player) && !TwoPlayerTest.PLAYER2.equals(player)) {
			LOGGER.error("[TwoPlayerTest] a client needs the role {} or {}, not {}; the driver does not start", TwoPlayerTest.PLAYER1, TwoPlayerTest.PLAYER2, player);
			TwoPlayerTest.writeStatus(out, "client-" + player + ".bad-role", null);
			return;
		}
		TwoPlayerTest.writeStatus(out, player + ".started", null);
		AtomicLong ticks = new AtomicLong();
		AtomicLong frames = new AtomicLong();
		ClientTickEvents.END_CLIENT_TICK.register(client -> ticks.incrementAndGet());
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> frames.incrementAndGet()));
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
			if(Minecraft.getInstance().screen == null)
				frames.incrementAndGet();
		});
		Minecraft mc = Minecraft.getInstance();
		ClientDriver driver = new ClientDriver(mc, player, out, ticks, frames);
		Scenario scenario = new Scenario(driver);
		Thread thread = new Thread(scenario, "OPAC-TwoPlayerTest-" + player);
		thread.setDaemon(true);
		thread.start();
		int minutes = TwoPlayerTest.timeoutMinutes();
		Thread watchdog = new Thread(() -> {
			try {
				thread.join(TimeUnit.MINUTES.toMillis(minutes));
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			if(!scenario.isDone()) {
				String why = "watchdog: the scenario did not finish within " + minutes + " min";
				LOGGER.error("[TwoPlayerTest] {}", why);
				driver.results.note(why);
				driver.barrier.abort(why);
				thread.interrupt();
				scenario.finish(why);
			}
		}, "OPAC-TwoPlayerTest-Watchdog-" + player);
		watchdog.setDaemon(true);
		watchdog.start();
		LOGGER.info("[TwoPlayerTest] driver of {} started (output {}, time-out {} min)", player, out, minutes);
	}

}
