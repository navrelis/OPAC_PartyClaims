package xaero.pac.teamclaims.gametest.mixin;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.net.Proxy;

/**
 * Test-only workaround, never shipped in any production jar. Loader-neutral (vanilla + Mixin
 * only), so it lives in the shared Common/src/gametest dir that each loader compiles into its own
 * test-only gametest source set; the mixin config that applies it stays per loader (on Fabric:
 * opac_teamclaims_gametest.mixins.json of the opac_teamclaims_gametest mod).
 * <p>
 * Vanilla's {@code GameTestServer} builds its {@code Services} record with a null
 * {@code GameProfileCache} (it never calls {@code Services.create(...)}, since a headless test
 * server has no real player accounts to cache). Stock (pre-existing, non-Team-Claims) OPAC code
 * in {@code xaero.pac.common.server.claims.player.ServerPlayerClaimInfoManager#onAdd} dereferences
 * {@code MinecraftServer#getProfileCache()} unconditionally, which NPEs the moment a claim-info
 * entry is created on such a server. Production code is intentionally left untouched -- a real
 * dedicated or integrated server always has a non-null profile cache, so this only ever matters
 * for our own headless boot test.
 * <p>
 * This mixin intercepts {@code getProfileCache()} and, only when the real return value is null,
 * substitutes a lazily-built, per-server-instance {@link GameProfileCache}, mirroring exactly how
 * vanilla's {@code Services.create(YggdrasilAuthenticationService, File)} builds the real one
 * (same {@code GameProfileRepository} source, same {@code usercache.json} file name, resolved
 * under this server's own directory). {@link GameProfileCache#get(java.util.UUID)} (the only
 * method OPAC calls here) is a pure in-memory map lookup with no network I/O -- verified by
 * decompiling the Mojang-mapped 1.21.1 class -- so an empty, freshly constructed cache is safe to
 * hand back synchronously; it will just report "no cached name" for every UUID, same as it would
 * for any UUID the real cache hasn't seen yet.
 */
@Mixin(MinecraftServer.class)
public class MixinGameTestProfileCache {

    @Unique
    private GameProfileCache teamClaimsGametest$fakeProfileCache;

    @Inject(method = "getProfileCache", at = @At("RETURN"), cancellable = true)
    private void teamClaimsGametest$fakeProfileCacheOnGameTestServer(CallbackInfoReturnable<GameProfileCache> cir) {
        if (cir.getReturnValue() != null) {
            return; // real server (dedicated/integrated): already has a real cache, do nothing
        }
        if (teamClaimsGametest$fakeProfileCache == null) {
            MinecraftServer self = (MinecraftServer) (Object) this;
            GameProfileRepository profileRepository =
                    new YggdrasilAuthenticationService(Proxy.NO_PROXY).createProfileRepository();
            File usercache = self.getServerDirectory().resolve("usercache.json").toFile();
            teamClaimsGametest$fakeProfileCache = new GameProfileCache(profileRepository, usercache);
        }
        cir.setReturnValue(teamClaimsGametest$fakeProfileCache);
    }
}
