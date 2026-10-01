package xaero.pac.teamclaims.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xaero.pac.common.event.api.v2.OPACServerAddonRegisterEvent;
import xaero.pac.teamclaims.TeamClaimsCommon;

/**
 * NeoForge adapter for Team Claims: registers listeners on {@link NeoForge#EVENT_BUS} and
 * forwards them to the loader-neutral {@link TeamClaimsCommon}. All Team Claims logic lives in
 * Common; this is the NeoForge counterpart of {@code xaero.pac.teamclaims.fabric.TeamClaimsFabric}.
 * <p>
 * {@link #init()} is called from the {@code @Mod} constructor on both dists and only registers
 * server-side (game bus) events, so it never references client-only classes.
 * <p>
 * Priorities mirror the order the Fabric adapter gets for free from registration order (OPAC's own
 * Fabric listeners are registered before {@code TeamClaimsFabric.init()}):
 * <ul>
 *     <li>server started / stopping, end of server tick and command registration run
 *     <em>after</em> OPAC's own listeners ({@link EventPriority#LOW}; OPAC uses the default
 *     NORMAL priority and only registers its game-bus listeners later, in FMLCommonSetupEvent, so
 *     at equal priority Team Claims would otherwise run first);</li>
 *     <li>player login / logout run <em>before</em> OPAC's own login / logout handling
 *     ({@link EventPriority#HIGH}), like Fabric's {@code ServerPlayConnectionEvents.JOIN}/
 *     {@code DISCONNECT}, which fire before OPAC's {@code PlayerList} mixin hooks. Login is
 *     deferred by one tick inside {@link TeamClaimsCommon#onPlayerLoggedIn} anyway.</li>
 * </ul>
 * OPAC posts its v2 {@link OPACServerAddonRegisterEvent} from its ServerAboutToStartEvent
 * listener, i.e. before {@link ServerStartedEvent}, as {@link TeamClaimsCommon} requires.
 */
public final class TeamClaimsNeoForge {

    private static boolean initialized = false;

    private TeamClaimsNeoForge() {}

    /**
     * Called from the {@code OpenPartiesAndClaimsNeoForge} constructor (both dists). Idempotent.
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.NORMAL, OPACServerAddonRegisterEvent.class,
                event -> TeamClaimsCommon.onAddonRegister(event.getContext()));
        bus.addListener(EventPriority.LOW, ServerStartedEvent.class,
                event -> TeamClaimsCommon.onServerStarted(event.getServer()));
        bus.addListener(EventPriority.LOW, ServerStoppingEvent.class,
                event -> TeamClaimsCommon.onServerStopping(event.getServer()));
        bus.addListener(EventPriority.LOW, ServerTickEvent.Post.class,
                event -> TeamClaimsCommon.onServerTickEnd(event.getServer()));
        bus.addListener(EventPriority.HIGH, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TeamClaimsCommon.onPlayerLoggedIn(player);
            }
        });
        bus.addListener(EventPriority.HIGH, PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TeamClaimsCommon.onPlayerLoggedOut(player);
            }
        });
        bus.addListener(EventPriority.LOW, RegisterCommandsEvent.class,
                event -> TeamClaimsCommon.registerCommands(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));

        TeamClaimsCommon.LOGGER.info("Team Claims integration initialized (NeoForge)");
    }
}
