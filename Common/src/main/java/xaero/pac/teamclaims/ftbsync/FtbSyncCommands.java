package xaero.pac.teamclaims.ftbsync;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.teamclaims.TeamClaimsCommon;

import javax.annotation.Nullable;
import java.util.List;

/**
 * {@code /teamclaims ftbsync status} and {@code /teamclaims ftbsync resync}, both for permission level 2.
 * <p>
 * {@code status} says whether the FTB Teams sync runs (and if not, why), how many parties are linked to an FTB
 * Teams party, and what is waiting to be synced. {@code resync} runs the full reconciliation right away and says
 * how much it changed. Both also work from the console. No FTB class is referenced here.
 */
public final class FtbSyncCommands {

    private static final String KEY = "gui.xaero_pac_team_claims_ftbsync_";
    /** The status command lists at most this many pending entries. */
    private static final int MAX_LISTED_PENDING = 10;

    private FtbSyncCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> ftbSyncNode() {
        return Commands.literal("ftbsync")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(FtbSyncCommands::executeStatus))
                .then(Commands.literal("resync").executes(FtbSyncCommands::executeResync));
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        FtbTeamsSync sync = TeamClaimsCommon.getFtbTeamsSync();
        if (sync == null) {
            send(source, TeamClaimsCommon.getFtbTeamsSyncOffReason(), ChatFormatting.YELLOW);
            return 0;
        }
        boolean running = sync.isRunning();
        send(source, sync.getStateKey(), running ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        send(source, KEY + "status_pairs", ChatFormatting.GRAY, sync.getLinkedPairCount());
        List<FtbTeamsSync.PendingView> pending = sync.getPending();
        if (pending.isEmpty()) {
            send(source, KEY + "status_pending_none", ChatFormatting.GRAY);
        } else {
            send(source, KEY + "status_pending", ChatFormatting.YELLOW, pending.size());
            for (int i = 0; i < pending.size() && i < MAX_LISTED_PENDING; i++) {
                FtbTeamsSync.PendingView entry = pending.get(i);
                send(source, KEY + "status_pending_entry", ChatFormatting.GRAY, entry.label(),
                        localized(source, entry.reasonKey(), entry.reasonArgs()));
            }
            if (pending.size() > MAX_LISTED_PENDING)
                send(source, KEY + "status_pending_more", ChatFormatting.GRAY, pending.size() - MAX_LISTED_PENDING);
        }
        return running ? 1 : 0;
    }

    private static int executeResync(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        FtbTeamsSync sync = TeamClaimsCommon.getFtbTeamsSync();
        if (sync == null) {
            send(source, TeamClaimsCommon.getFtbTeamsSyncOffReason(), ChatFormatting.YELLOW);
            return 0;
        }
        long changes = sync.resync();
        if (changes < 0) {
            send(source, sync.getStateKey(), ChatFormatting.YELLOW);
            return 0;
        }
        send(source, KEY + "resync_done", ChatFormatting.GREEN, changes, sync.getLinkedPairCount(), sync.getPendingCount());
        return 1;
    }

    private static void send(CommandSourceStack source, String key, ChatFormatting style, Object... args) {
        MutableComponent message = localized(source, key, args).withStyle(style);
        source.sendSuccess(() -> message, false);
    }

    /** The message in the language of the source: translated by a client that has the mod, by the server otherwise. */
    private static MutableComponent localized(CommandSourceStack source, String key, Object... args) {
        IServerData<?, ?> serverData = ServerData.from(source.getServer());
        @Nullable ServerPlayer player = source.getPlayer();
        return serverData == null ? Component.translatable(key, args) : serverData.getAdaptiveLocalizer().getFor(player, key, args);
    }
}
